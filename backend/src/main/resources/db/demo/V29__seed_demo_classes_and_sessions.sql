-- BE-15: opt-in demonstration data, never an ordinary development/production migration.
-- Six classes x four weeks x two formats. Dates are anchored to the first execution.
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
    @Resource = 'Sportify:BE-15:demo-classes', @LockMode = 'Exclusive',
    @LockOwner = 'Transaction', @LockTimeout = 10000;
IF @LockResult < 0
    THROW 51500, 'BE-15 could not acquire the demo classes seed lock.', 1;

DECLARE @Now DATETIME2(0) = SYSDATETIME();
DECLARE @Today DATE = CAST(GETDATE() AS DATE);
DECLARE @Classes TABLE (
    ordinal INT PRIMARY KEY, code NVARCHAR(30) UNIQUE, name NVARCHAR(100), sport_code NVARCHAR(30),
    level NVARCHAR(20), coach_email NVARCHAR(255), facility_code NVARCHAR(30), day_offset INT,
    coach_start TIME(0), coach_end TIME(0), self_start TIME(0), self_end TIME(0), capacity INT
);
INSERT INTO @Classes VALUES
    (1, 'DEMO-CL-BAD-BEG', 'Badminton Foundations - Small Group', 'BADMINTON', 'BEGINNER', 'quan.coach@example.com', 'DEMO-BADMINTON', 2, '09:00', '10:30', '11:00', '12:00', 3),
    (2, 'DEMO-CL-BAD-INT', 'Badminton Rally and Footwork', 'BADMINTON', 'INTERMEDIATE', 'quan.coach@example.com', 'DEMO-BADMINTON', 4, '18:00', '19:30', '20:00', '21:00', 3),
    (3, 'DEMO-CL-SWIM-BEG', 'Swimming Water Confidence', 'SWIMMING', 'BEGINNER', 'nam.coach@example.com', 'INDOOR_POOL', 3, '07:00', '08:30', '10:00', '11:00', 8),
    (4, 'DEMO-CL-SWIM-ADV', 'Swimming Endurance and Technique', 'SWIMMING', 'ADVANCED', 'nam.coach@example.com', 'INDOOR_POOL', 6, '09:00', '10:30', '11:00', '12:00', 8),
    (5, 'DEMO-CL-BB-INT', 'Basketball Passing and Team Play', 'BASKETBALL', 'INTERMEDIATE', 'ha.coach@example.com', 'BB_COURT_A', 1, '17:00', '18:30', '19:00', '20:00', 10),
    (6, 'DEMO-CL-BB-ADV', 'Basketball Advanced Game Skills', 'BASKETBALL', 'ADVANCED', 'ha.coach@example.com', 'BB_COURT_B', 7, '09:00', '10:30', '11:00', '12:00', 10);

DECLARE @Packages TABLE (code NVARCHAR(30) PRIMARY KEY, name NVARCHAR(100), format NVARCHAR(20), minutes INT);
INSERT INTO @Packages VALUES
    ('DEMO-PKG-BAD-COACH', 'Demo complimentary badminton coaching', 'COACH_LED', 90),
    ('DEMO-PKG-BAD-SELF', 'Demo complimentary badminton practice', 'SELF_TRAINING', 60);
DECLARE @Registrations TABLE (code NVARCHAR(30) PRIMARY KEY, package_code NVARCHAR(30), email NVARCHAR(255));
INSERT INTO @Registrations VALUES
    ('DEMO-REG-BAD-AN', 'DEMO-PKG-BAD-COACH', 'an.gold@example.com'),
    ('DEMO-REG-BAD-MAI', 'DEMO-PKG-BAD-COACH', 'mai.standard@example.com'),
    ('DEMO-REG-BAD-BAO', 'DEMO-PKG-BAD-COACH', 'bao.nocard@example.com'),
    ('DEMO-REG-SELF-BAO', 'DEMO-PKG-BAD-SELF', 'bao.nocard@example.com');

-- Resolve business keys only. BE-07 owns accounts, profiles and coach qualifications.
IF EXISTS (SELECT 1 FROM @Classes c LEFT JOIN sport s ON s.code = c.sport_code AND s.is_active = 1 WHERE s.id IS NULL)
    OR NOT EXISTS (SELECT 1 FROM age_group WHERE code = 'ADULTS_18_PLUS' AND is_active = 1)
    OR EXISTS (
        SELECT 1 FROM @Classes c LEFT JOIN user_account u ON u.email = c.coach_email
        LEFT JOIN [role] r ON r.id = u.role_id LEFT JOIN coach_profile cp ON cp.user_id = u.id
        LEFT JOIN sport s ON s.code = c.sport_code
        LEFT JOIN coach_sport cs ON cs.coach_id = cp.user_id AND cs.sport_id = s.id
        WHERE u.id IS NULL OR r.code <> 'COACH' OR u.status <> 'ACTIVE'
            OR cp.user_id IS NULL OR cp.primary_sport_id IS NULL OR cp.primary_sport_id <> s.id OR cs.coach_id IS NULL
    ) OR EXISTS (
        SELECT 1 FROM @Registrations e LEFT JOIN user_account u ON u.email = e.email
        LEFT JOIN [role] r ON r.id = u.role_id LEFT JOIN member_profile m ON m.user_id = u.id
        WHERE u.id IS NULL OR r.code <> 'MEMBER' OR u.status <> 'ACTIVE' OR m.user_id IS NULL
    ) OR NOT EXISTS (
        SELECT 1 FROM user_account u JOIN [role] r ON r.id = u.role_id
        WHERE u.email = 'lan.reception@example.com' AND r.code = 'RECEPTIONIST' AND u.status = 'ACTIVE'
    )
    THROW 51501, 'BE-15 requires BE-07 active demo accounts, profiles, coach sports and the adult age group.', 1;

IF EXISTS (
    SELECT 1 FROM @Classes c LEFT JOIN facility f ON f.code = c.facility_code
    LEFT JOIN sport s ON s.code = c.sport_code
    WHERE (f.id IS NULL AND c.facility_code <> 'DEMO-BADMINTON')
        OR (f.id IS NOT NULL AND (f.is_active <> 1 OR f.sport_id IS NULL OR f.sport_id <> s.id
            OR f.facility_type <> CASE WHEN c.sport_code = 'SWIMMING' THEN 'POOL' ELSE 'COURT' END))
)
    THROW 51501, 'BE-15 requires active sport-compatible INDOOR_POOL, BB_COURT_A and BB_COURT_B facilities.', 1;
IF EXISTS (SELECT 1 FROM facility WHERE code = 'DEMO-BADMINTON'
    AND (name <> 'Demo Badminton Court' OR capacity IS NULL OR capacity <> 12))
    THROW 51502, 'BE-15 reserved badminton facility identity conflicts with existing data.', 1;

-- A persisted common anchor makes direct SQL replay on a later day a no-op, not a rolling reseed.
DECLARE @Anchors TABLE (anchor_date DATE NOT NULL);
INSERT INTO @Anchors SELECT CAST(c.created_at AS DATE) FROM sport_class c JOIN @Classes e ON e.code = c.code;
INSERT INTO @Anchors SELECT r.start_date FROM sport_package_registration r JOIN @Registrations e ON e.code = r.registration_code;
IF (SELECT COUNT(DISTINCT anchor_date) FROM @Anchors) > 1
    THROW 51502, 'BE-15 existing class and registration anchors disagree; use a fresh isolated demo database.', 1;
DECLARE @Anchor DATE = COALESCE((SELECT MIN(anchor_date) FROM @Anchors), @Today);
IF @Anchor > @Today
    THROW 51502, 'BE-15 existing seed anchor is in the future.', 1;

-- Match the scheduling lock order on existing rows: sessions, then coaches, then facilities.
-- Startup is the normal execution path. A live conflicting writer may cause a safe SQL deadlock rollback.
DECLARE @Id BIGINT, @LockedId BIGINT;
DECLARE SessionLocks CURSOR LOCAL FAST_FORWARD FOR
    SELECT s.id FROM class_session s JOIN sport_class c ON c.id = s.class_id JOIN @Classes e ON e.code = c.code ORDER BY s.id;
OPEN SessionLocks;
FETCH NEXT FROM SessionLocks INTO @Id;
WHILE @@FETCH_STATUS = 0
BEGIN
    SELECT @LockedId = id FROM class_session WITH (UPDLOCK, HOLDLOCK) WHERE id = @Id;
    FETCH NEXT FROM SessionLocks INTO @Id;
END;
CLOSE SessionLocks;
DEALLOCATE SessionLocks;
DECLARE CoachLocks CURSOR LOCAL FAST_FORWARD FOR
    SELECT DISTINCT u.id FROM user_account u JOIN @Classes e ON e.coach_email = u.email ORDER BY u.id;
OPEN CoachLocks;
FETCH NEXT FROM CoachLocks INTO @Id;
WHILE @@FETCH_STATUS = 0
BEGIN
    SELECT @LockedId = user_id FROM coach_profile WITH (UPDLOCK, HOLDLOCK) WHERE user_id = @Id;
    FETCH NEXT FROM CoachLocks INTO @Id;
END;
CLOSE CoachLocks;
DEALLOCATE CoachLocks;

INSERT INTO facility (code, name, facility_type, sport_id, capacity, is_active)
SELECT 'DEMO-BADMINTON', 'Demo Badminton Court', 'COURT', id, 12, 1 FROM sport
WHERE code = 'BADMINTON' AND NOT EXISTS (SELECT 1 FROM facility WITH (UPDLOCK, HOLDLOCK) WHERE code = 'DEMO-BADMINTON');
DECLARE FacilityLocks CURSOR LOCAL FAST_FORWARD FOR
    SELECT DISTINCT f.id FROM facility f JOIN @Classes e ON e.facility_code = f.code ORDER BY f.id;
OPEN FacilityLocks;
FETCH NEXT FROM FacilityLocks INTO @Id;
WHILE @@FETCH_STATUS = 0
BEGIN
    SELECT @LockedId = id FROM facility WITH (UPDLOCK, HOLDLOCK) WHERE id = @Id;
    FETCH NEXT FROM FacilityLocks INTO @Id;
END;
CLOSE FacilityLocks;
DEALLOCATE FacilityLocks;

IF EXISTS (
    SELECT 1 FROM @Classes e JOIN sport_class c ON c.code = e.code JOIN sport s ON s.code = e.sport_code
    JOIN user_account u ON u.email = e.coach_email JOIN age_group a ON a.code = 'ADULTS_18_PLUS'
    WHERE c.name <> e.name OR c.sport_id <> s.id OR c.level <> e.level OR c.max_members <> e.capacity
        OR c.is_active <> 1 OR c.age_group_id IS NULL OR c.age_group_id <> a.id
        OR (c.coach_id IS NOT NULL AND c.coach_id <> u.id)
)
    THROW 51502, 'BE-15 reserved class identity conflicts with existing class data.', 1;
-- Only an absent class-coach association on an otherwise exact reserved class is repairable.
UPDATE c SET coach_id = u.id FROM sport_class c JOIN @Classes e ON e.code = c.code
JOIN user_account u ON u.email = e.coach_email WHERE c.coach_id IS NULL;
INSERT INTO sport_class (code, name, sport_id, age_group_id, coach_id, level, max_members, is_active, created_at)
SELECT e.code, e.name, s.id, a.id, u.id, e.level, e.capacity, 1, CAST(@Anchor AS DATETIME2(0))
FROM @Classes e JOIN sport s ON s.code = e.sport_code JOIN user_account u ON u.email = e.coach_email
CROSS JOIN age_group a WHERE a.code = 'ADULTS_18_PLUS'
AND NOT EXISTS (SELECT 1 FROM sport_class c WITH (UPDLOCK, HOLDLOCK) WHERE c.code = e.code);

DECLARE @Sessions TABLE (
    ordinal INT, week_no INT, class_id BIGINT, sport_id BIGINT, session_date DATE,
    start_time TIME(0), end_time TIME(0), facility_id BIGINT, coach_id BIGINT NULL,
    training_type NVARCHAR(20), capacity INT, session_id BIGINT NULL,
    PRIMARY KEY (ordinal, week_no, training_type)
);
INSERT INTO @Sessions (ordinal, week_no, class_id, sport_id, session_date, start_time, end_time,
    facility_id, coach_id, training_type, capacity)
SELECT e.ordinal, w.week_no, c.id, s.id, DATEADD(DAY, e.day_offset + 7 * w.week_no, @Anchor),
    v.start_time, v.end_time, f.id, CASE WHEN v.training_type = 'COACH_LED' THEN u.id END, v.training_type, e.capacity
FROM @Classes e JOIN sport_class c ON c.code = e.code JOIN sport s ON s.code = e.sport_code
JOIN user_account u ON u.email = e.coach_email JOIN facility f ON f.code = e.facility_code
CROSS JOIN (VALUES (0), (1), (2), (3)) w(week_no)
CROSS APPLY (VALUES ('COACH_LED', e.coach_start, e.coach_end), ('SELF_TRAINING', e.self_start, e.self_end)) v(training_type, start_time, end_time);
IF (SELECT COUNT(*) FROM @Sessions) <> 48
    THROW 51501, 'BE-15 could not resolve all 48 planned session dependencies.', 1;
IF EXISTS (
    SELECT 1 FROM @Sessions e JOIN class_session s ON s.class_id = e.class_id AND s.session_date = e.session_date
        AND s.training_type = e.training_type AND s.start_time = e.start_time
    GROUP BY e.ordinal, e.week_no, e.training_type HAVING COUNT(*) > 1
) OR EXISTS (
    SELECT 1 FROM @Sessions e JOIN class_session s ON s.class_id = e.class_id AND s.session_date = e.session_date
        AND s.training_type = e.training_type AND s.start_time = e.start_time
    WHERE s.sport_id <> e.sport_id OR s.end_time <> e.end_time OR s.capacity <> e.capacity OR s.status <> 'PUBLISHED'
        OR s.facility_id IS NULL OR s.facility_id <> e.facility_id
        OR (s.coach_id IS NULL AND e.coach_id IS NOT NULL) OR (s.coach_id IS NOT NULL AND e.coach_id IS NULL)
        OR s.coach_id <> e.coach_id
)
    THROW 51502, 'BE-15 existing session identity is duplicated or conflicts with the planned schedule.', 1;
UPDATE e SET session_id = s.id FROM @Sessions e JOIN class_session s ON s.class_id = e.class_id
    AND s.session_date = e.session_date AND s.training_type = e.training_type AND s.start_time = e.start_time;
IF EXISTS (SELECT 1 FROM @Sessions WHERE session_id IS NULL AND session_date <= @Today)
    THROW 51505, 'BE-15 cannot restore missing past/today sessions; use a fresh isolated demo database.', 1;

-- Strict BE-10 overlap: adjacent boundaries allowed; DRAFT and PUBLISHED block, null coaches do not.
IF EXISTS (
    SELECT 1 FROM @Sessions a JOIN @Sessions b ON a.session_date = b.session_date
        AND a.start_time < b.end_time AND b.start_time < a.end_time
        AND (a.ordinal <> b.ordinal OR a.week_no <> b.week_no OR a.training_type <> b.training_type)
    WHERE a.facility_id = b.facility_id OR (a.coach_id IS NOT NULL AND a.coach_id = b.coach_id)
) OR EXISTS (
    SELECT 1 FROM @Sessions e JOIN class_session s ON s.session_date = e.session_date
        AND s.start_time < e.end_time AND e.start_time < s.end_time
        AND s.status IN ('DRAFT', 'PUBLISHED') AND (e.session_id IS NULL OR s.id <> e.session_id)
    WHERE s.facility_id = e.facility_id OR (e.coach_id IS NOT NULL AND s.coach_id = e.coach_id)
)
    THROW 51503, 'BE-15 coach or facility overlaps an existing DRAFT/PUBLISHED session; no dates were shifted.', 1;
INSERT INTO class_session (class_id, sport_id, session_date, start_time, end_time, facility_id, coach_id,
    training_type, capacity, booked_count, status)
SELECT class_id, sport_id, session_date, start_time, end_time, facility_id, coach_id, training_type, capacity, 0, 'PUBLISHED'
FROM @Sessions WHERE session_id IS NULL;
UPDATE e SET session_id = s.id FROM @Sessions e JOIN class_session s ON s.class_id = e.class_id
    AND s.session_date = e.session_date AND s.training_type = e.training_type AND s.start_time = e.start_time;

-- Explicit complimentary packages follow the supported zero-price staff/RECEPTION path.
-- They grant demo entitlements, not sales: no payment, receipt, invoice or monetary audit is fabricated.
IF EXISTS (
    SELECT 1 FROM @Packages e JOIN sport_package p ON p.code = e.code JOIN sport s ON s.code = 'BADMINTON'
    WHERE p.name <> e.name OR p.sport_id <> s.id OR p.training_format <> e.format OR p.duration_days <> 30
        OR p.session_count <> 8 OR p.session_minutes <> e.minutes OR p.price_amount <> 0 OR p.is_active <> 1
        OR p.description IS NULL OR p.description <> 'BE-15 complimentary demo entitlement; no payment.'
)
    THROW 51502, 'BE-15 reserved complimentary package conflicts with existing data.', 1;
INSERT INTO sport_package (code, name, sport_id, training_format, duration_days, session_count, session_minutes, price_amount, description, is_active)
SELECT e.code, e.name, s.id, e.format, 30, 8, e.minutes, 0, 'BE-15 complimentary demo entitlement; no payment.', 1
FROM @Packages e CROSS JOIN sport s WHERE s.code = 'BADMINTON'
AND NOT EXISTS (SELECT 1 FROM sport_package p WITH (UPDLOCK, HOLDLOCK) WHERE p.code = e.code);
IF EXISTS (
    SELECT 1 FROM @Registrations e JOIN sport_package_registration r ON r.registration_code = e.code
    JOIN user_account u ON u.email = e.email JOIN sport_package p ON p.code = e.package_code
    JOIN user_account actor ON actor.email = 'lan.reception@example.com'
    WHERE r.member_id <> u.id OR r.package_id <> p.id OR r.channel <> 'RECEPTION' OR r.original_price <> 0
        OR r.paid_amount <> 0 OR r.discount_percentage <> 0 OR r.total_sessions <> 8
        OR r.start_date <> @Anchor OR r.end_date <> DATEADD(DAY, 29, @Anchor)
        OR r.status NOT IN ('ACTIVE', 'EXPIRED') OR r.activated_at IS NULL
        OR r.created_by_user_id <> actor.id OR r.payment_id IS NOT NULL
)
    THROW 51502, 'BE-15 reserved registration ownership, terms or entitlement state conflicts.', 1;
INSERT INTO sport_package_registration (registration_code, member_id, package_id, channel, original_price,
    discount_percentage, paid_amount, total_sessions, remaining_sessions, start_date, end_date, status, activated_at, created_by_user_id)
SELECT e.code, u.id, p.id, 'RECEPTION', 0, 0, 0, 8, 8, @Anchor, DATEADD(DAY, 29, @Anchor), 'ACTIVE', @Now, actor.id
FROM @Registrations e JOIN user_account u ON u.email = e.email JOIN sport_package p ON p.code = e.package_code
JOIN user_account actor ON actor.email = 'lan.reception@example.com'
WHERE NOT EXISTS (SELECT 1 FROM sport_package_registration r WITH (UPDLOCK, HOLDLOCK) WHERE r.registration_code = e.code);

DECLARE @Bookings TABLE (code NVARCHAR(30) PRIMARY KEY, ordinal INT, registration_code NVARCHAR(30),
    session_id BIGINT NULL, registration_id BIGINT NULL, member_id BIGINT NULL, missing BIT NOT NULL DEFAULT 1);
INSERT INTO @Bookings (code, ordinal, registration_code) VALUES
    ('DEMO-BK-FULL-AN', 1, 'DEMO-REG-BAD-AN'),
    ('DEMO-BK-FULL-MAI', 1, 'DEMO-REG-BAD-MAI'),
    ('DEMO-BK-FULL-BAO', 1, 'DEMO-REG-BAD-BAO'),
    ('DEMO-BK-NEAR-AN', 2, 'DEMO-REG-BAD-AN'),
    ('DEMO-BK-NEAR-MAI', 2, 'DEMO-REG-BAD-MAI');
UPDATE e SET session_id = s.session_id, registration_id = r.id, member_id = r.member_id
FROM @Bookings e JOIN @Sessions s ON s.ordinal = e.ordinal AND s.week_no = 0 AND s.training_type = 'COACH_LED'
JOIN sport_package_registration r ON r.registration_code = e.registration_code;
IF EXISTS (SELECT 1 FROM @Bookings WHERE session_id IS NULL OR registration_id IS NULL OR member_id IS NULL)
    THROW 51501, 'BE-15 could not resolve all booking and entitlement dependencies.', 1;
IF EXISTS (
    SELECT 1 FROM @Bookings e JOIN booking b ON b.booking_code = e.code
    WHERE b.session_id <> e.session_id OR b.member_id <> e.member_id OR b.package_registration_id IS NULL
        OR b.package_registration_id <> e.registration_id OR b.membership_id IS NOT NULL
) OR EXISTS (
    SELECT 1 FROM @Bookings e JOIN booking b ON b.session_id = e.session_id AND b.member_id = e.member_id AND b.status = 'CONFIRMED'
    WHERE NOT EXISTS (SELECT 1 FROM booking own_booking WHERE own_booking.booking_code = e.code)
)
    THROW 51504, 'BE-15 reserved booking identity conflicts or an alternate confirmed booking already occupies it.', 1;
UPDATE e SET missing = 0 FROM @Bookings e JOIN booking b ON b.booking_code = e.code;
IF EXISTS (SELECT 1 FROM @Bookings e JOIN class_session s ON s.id = e.session_id
    JOIN sport_package_registration r ON r.id = e.registration_id
    WHERE e.missing = 1 AND (s.session_date <= @Today OR r.status <> 'ACTIVE'))
    THROW 51505, 'BE-15 missing bookings require future sessions and active demo entitlements.', 1;

-- Lock balances after session/resource locks, just as booking/cancellation does.
DECLARE RegistrationLocks CURSOR LOCAL FAST_FORWARD FOR
    SELECT r.id FROM sport_package_registration r JOIN @Registrations e ON e.code = r.registration_code ORDER BY r.id;
OPEN RegistrationLocks;
FETCH NEXT FROM RegistrationLocks INTO @Id;
WHILE @@FETCH_STATUS = 0
BEGIN
    SELECT @LockedId = id FROM sport_package_registration WITH (UPDLOCK, HOLDLOCK) WHERE id = @Id;
    FETCH NEXT FROM RegistrationLocks INTO @Id;
END;
CLOSE RegistrationLocks;
DEALLOCATE RegistrationLocks;

DECLARE @Balances TABLE (registration_id BIGINT PRIMARY KEY, existing_count INT, missing_count INT, final_remaining INT);
INSERT INTO @Balances
SELECT r.id, used.n, needed.n, r.total_sessions - used.n - needed.n
FROM sport_package_registration r JOIN @Registrations e ON e.code = r.registration_code
CROSS APPLY (SELECT COUNT(*) n FROM booking b WHERE b.package_registration_id = r.id AND b.status = 'CONFIRMED') used
CROSS APPLY (SELECT COUNT(*) n FROM @Bookings b WHERE b.registration_id = r.id AND b.missing = 1) needed;
IF EXISTS (SELECT 1 FROM @Balances e JOIN sport_package_registration r ON r.id = e.registration_id
    WHERE e.final_remaining < 0 OR r.remaining_sessions < e.final_remaining
        OR r.remaining_sessions > e.final_remaining + e.missing_count)
    OR EXISTS (
        SELECT 1 FROM booking b JOIN sport_package_registration r ON r.id = b.package_registration_id
        JOIN @Registrations e ON e.code = r.registration_code JOIN sport_package p ON p.id = r.package_id
        JOIN class_session s ON s.id = b.session_id
        WHERE b.member_id <> r.member_id OR p.sport_id <> s.sport_id OR p.training_format <> s.training_type
            OR s.session_date < r.start_date OR s.session_date > r.end_date
    ) OR EXISTS (
        SELECT 1 FROM @Sessions e JOIN class_session s ON s.id = e.session_id
        CROSS APPLY (SELECT COUNT(*) n FROM booking b WHERE b.session_id = s.id AND b.status = 'CONFIRMED') used
        CROSS APPLY (SELECT COUNT(*) n FROM @Bookings b WHERE b.session_id = s.id AND b.missing = 1) needed
        WHERE used.n + needed.n > s.capacity OR s.booked_count < used.n OR s.booked_count > used.n + needed.n
    )
    THROW 51504, 'BE-15 capacity or credit state is inconsistent; ambiguous usage will not be overwritten.', 1;

INSERT INTO booking (booking_code, session_id, member_id, package_registration_id, status, booked_at)
SELECT code, session_id, member_id, registration_id, 'CONFIRMED', @Now FROM @Bookings WHERE missing = 1;
-- Repair only the credit delta attributable to the just-inserted missing fixture bookings.
UPDATE r SET remaining_sessions = e.final_remaining, updated_at = @Now
FROM sport_package_registration r JOIN @Balances e ON e.registration_id = r.id
WHERE e.missing_count > 0 AND r.remaining_sessions <> e.final_remaining;
-- Counts come from real confirmed rows, including any legitimate subsequent demo interaction.
UPDATE s SET booked_count = used.n, updated_at = @Now FROM class_session s JOIN @Sessions e ON e.session_id = s.id
CROSS APPLY (SELECT COUNT(*) n FROM booking b WHERE b.session_id = s.id AND b.status = 'CONFIRMED') used
WHERE s.booked_count <> used.n;

IF @OwnTransaction = 1
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @OwnTransaction = 1 AND XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
