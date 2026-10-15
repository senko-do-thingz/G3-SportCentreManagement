-- BE-07: isolated demonstration databases only, enabled explicitly by the demo profile.
-- Join a caller transaction, or open one for independent logical replay.
-- Insert missing rows only; conflicting identities or scenarios fail without overwriting data.
SET XACT_ABORT ON;
DECLARE @OwnTransaction BIT = 0;
IF @@TRANCOUNT = 0
BEGIN
    BEGIN TRANSACTION;
    SET @OwnTransaction = 1;
END;
BEGIN TRY

DECLARE @LockResult INT;
EXEC @LockResult = sys.sp_getapplock
    @Resource = 'Sportify:BE-07:demo-accounts', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 10000;
IF @LockResult < 0
    THROW 51070, 'BE-07 could not acquire the demo seed lock.', 1;

DECLARE @PasswordHash NVARCHAR(100) = '$2a$10$cYcyDrFeL2hdtC4ajymEROOvLbCjeisVq/hVuALJReMtBxPqTXpga';
DECLARE @StartDate DATE = '20261001';
DECLARE @GoldEndDate DATE = '20270930';
DECLARE @Accounts TABLE (
    row_no INT PRIMARY KEY, email NVARCHAR(255) NOT NULL UNIQUE,
    full_name NVARCHAR(100) NOT NULL, role_code NVARCHAR(30) NOT NULL,
    sport_code NVARCHAR(30) NULL, card_tier NVARCHAR(30) NULL
);
INSERT INTO @Accounts (row_no, email, full_name, role_code, sport_code, card_tier)
VALUES
    (1, 'lan.reception@example.com', 'Nguyen Thi Lan', 'RECEPTIONIST', NULL, NULL),
    (2, 'quan.coach@example.com', 'Tran Minh Quan', 'COACH', 'BADMINTON', NULL),
    (3, 'nam.coach@example.com', 'Le Hoang Nam', 'COACH', 'SWIMMING', NULL),
    (4, 'ha.coach@example.com', 'Pham Thu Ha', 'COACH', 'BASKETBALL', NULL),
    (5, 'an.gold@example.com', 'Nguyen Van An', 'MEMBER', NULL, 'GOLD'),
    (6, 'mai.standard@example.com', 'Tran Thi Mai', 'MEMBER', NULL, 'STANDARD'),
    (7, 'bao.nocard@example.com', 'Le Quoc Bao', 'MEMBER', NULL, NULL);

IF EXISTS (SELECT 1 FROM @Accounts a LEFT JOIN [role] r ON r.code = a.role_code WHERE r.id IS NULL)
    THROW 51071, 'BE-07 requires the seeded RECEPTIONIST, COACH and MEMBER roles.', 1;
IF EXISTS (
    SELECT 1 FROM @Accounts a LEFT JOIN sport s ON s.code = a.sport_code AND s.is_active = 1
    WHERE a.sport_code IS NOT NULL AND s.id IS NULL
)
    THROW 51071, 'BE-07 requires active BADMINTON, SWIMMING and BASKETBALL sports.', 1;
IF NOT EXISTS (
    SELECT 1 FROM membership_card_tier WHERE code = 'GOLD' AND is_active = 1
        AND duration_months = 12 AND price = 300000.00 AND discount_percentage = 5
) OR NOT EXISTS (
    SELECT 1 FROM membership_card_tier WHERE code = 'STANDARD' AND is_active = 1
        AND duration_months = 0 AND price = 0 AND discount_percentage = 0
)
    THROW 51071, 'BE-07 requires the original active GOLD and STANDARD tier terms.', 1;

-- Verify existing identities before inserting any account. Preserve hashes and personal fields.
IF EXISTS (
    SELECT 1 FROM @Accounts a JOIN user_account u ON LOWER(u.email) = a.email
    JOIN [role] r ON r.id = u.role_id
    WHERE r.code <> a.role_code OR u.full_name <> a.full_name OR u.status <> 'ACTIVE'
) OR EXISTS (
    SELECT a.email FROM @Accounts a JOIN user_account u ON LOWER(u.email) = a.email
    GROUP BY a.email HAVING COUNT(*) > 1
)
    THROW 51072, 'BE-07 demo email conflicts with an existing identity, role or account status.', 1;
IF EXISTS (
    SELECT 1 FROM @Accounts a JOIN user_account u ON LOWER(u.email) = a.email
    JOIN coach_profile p ON p.user_id = u.id LEFT JOIN sport s ON s.id = p.primary_sport_id
    WHERE a.role_code <> 'COACH' OR s.code IS NULL OR s.code <> a.sport_code
) OR EXISTS (
    SELECT 1 FROM @Accounts a JOIN user_account u ON LOWER(u.email) = a.email
    JOIN member_profile p ON p.user_id = u.id
    WHERE a.role_code <> 'MEMBER' OR LEN(LTRIM(RTRIM(p.member_code))) = 0
)
    THROW 51072, 'BE-07 found a conflicting existing demo profile.', 1;

DECLARE @Cards TABLE (email NVARCHAR(255) PRIMARY KEY, card_code NVARCHAR(30), tier_code NVARCHAR(30),
    end_date DATE NULL, price_paid DECIMAL(14,2));
INSERT INTO @Cards (email, card_code, tier_code, end_date, price_paid)
VALUES ('an.gold@example.com', 'DEMO-CARD-GOLD', 'GOLD', @GoldEndDate, 300000.00),
       ('mai.standard@example.com', 'DEMO-CARD-STANDARD', 'STANDARD', NULL, 0.00);

-- Do not add a second card or take over an existing code. No-card means no physical row at all.
IF EXISTS (
    SELECT 1 FROM @Accounts a JOIN user_account u ON LOWER(u.email) = a.email
    JOIN member_card c ON c.member_id = u.id LEFT JOIN @Cards expected ON expected.email = a.email
    WHERE expected.email IS NULL OR c.card_code <> expected.card_code
) OR EXISTS (
    SELECT 1 FROM @Cards expected JOIN member_card c ON c.card_code = expected.card_code
    JOIN user_account u ON u.id = c.member_id JOIN membership_card_tier t ON t.id = c.tier_id
    WHERE LOWER(u.email) <> expected.email OR t.code <> expected.tier_code
        OR c.status <> 'ACTIVE' OR c.start_date <> @StartDate
        OR (c.end_date IS NULL AND expected.end_date IS NOT NULL)
        OR (c.end_date IS NOT NULL AND expected.end_date IS NULL)
        OR c.end_date <> expected.end_date OR c.price_paid <> expected.price_paid
        OR c.payment_id IS NOT NULL OR c.upgrade_request <> 0 OR c.replaces_card_id IS NOT NULL
)
    THROW 51072, 'BE-07 found a conflicting demo card code or membership scenario.', 1;

INSERT INTO user_account (email, password_hash, full_name, role_id, status)
SELECT a.email, @PasswordHash, a.full_name, r.id, 'ACTIVE'
FROM @Accounts a JOIN [role] r ON r.code = a.role_code
WHERE NOT EXISTS (SELECT 1 FROM user_account u WITH (UPDLOCK, HOLDLOCK) WHERE LOWER(u.email) = a.email);

INSERT INTO coach_profile (user_id, primary_sport_id, headline, years_experience, is_public)
SELECT u.id, s.id, 'Demonstration coach - ' + s.name, 3, 1
FROM @Accounts a JOIN user_account u ON LOWER(u.email) = a.email JOIN sport s ON s.code = a.sport_code
WHERE a.role_code = 'COACH'
    AND NOT EXISTS (SELECT 1 FROM coach_profile p WITH (UPDLOCK, HOLDLOCK) WHERE p.user_id = u.id);

INSERT INTO coach_sport (coach_id, sport_id)
SELECT u.id, s.id FROM @Accounts a JOIN user_account u ON LOWER(u.email) = a.email
JOIN sport s ON s.code = a.sport_code
WHERE a.role_code = 'COACH' AND NOT EXISTS (
    SELECT 1 FROM coach_sport cs WITH (UPDLOCK, HOLDLOCK) WHERE cs.coach_id = u.id AND cs.sport_id = s.id
);

-- Allocate through the existing sequence, matching CodeFormatter's MEM-%04d convention.
-- Do not consume a number for an existing profile, reseed the sequence, or reserve future numbers.
DECLARE @Row INT = 5;
WHILE @Row <= 7
BEGIN
    DECLARE @MemberId BIGINT;
    SELECT @MemberId = u.id FROM @Accounts a JOIN user_account u ON LOWER(u.email) = a.email WHERE a.row_no = @Row;
    IF NOT EXISTS (SELECT 1 FROM member_profile WITH (UPDLOCK, HOLDLOCK) WHERE user_id = @MemberId)
    BEGIN
        DECLARE @NextCode BIGINT = NEXT VALUE FOR seq_member_code;
        DECLARE @Digits VARCHAR(20) = CAST(@NextCode AS VARCHAR(20));
        IF @NextCode < 1 OR LEN(@Digits) > 16
            THROW 51073, 'BE-07 member sequence is outside the supported member code range.', 1;
        DECLARE @MemberCode NVARCHAR(20) = 'MEM-' + CASE WHEN LEN(@Digits) < 4
            THEN RIGHT('0000' + @Digits, 4) ELSE @Digits END;
        IF EXISTS (SELECT 1 FROM member_profile WHERE member_code = @MemberCode)
            THROW 51073, 'BE-07 member sequence conflicts with an existing member code.', 1;
        INSERT INTO member_profile (user_id, member_code, current_level, joined_on)
        VALUES (@MemberId, @MemberCode, 'BEGINNER', @StartDate);
    END;
    SET @Row = @Row + 1;
END;

-- Imported demonstration entitlement snapshots, not sales: payment_id is legitimately nullable
-- (V14 and MembershipCardServiceImpl's legacy no-payment path). Do not fabricate receipts or payments.
-- Card codes occupy a separate namespace from seq_card_code / CARD-%04d.
INSERT INTO member_card (card_code, member_id, tier_id, start_date, end_date, status, price_paid)
SELECT expected.card_code, u.id, t.id, @StartDate, expected.end_date, 'ACTIVE', expected.price_paid
FROM @Cards expected JOIN user_account u ON LOWER(u.email) = expected.email
JOIN membership_card_tier t ON t.code = expected.tier_code
WHERE NOT EXISTS (
    SELECT 1 FROM member_card c WITH (UPDLOCK, HOLDLOCK) WHERE c.card_code = expected.card_code
);

IF @OwnTransaction = 1
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @OwnTransaction = 1 AND XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
