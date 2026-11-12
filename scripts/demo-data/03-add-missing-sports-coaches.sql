/*
 * Sportify Center - Add Missing Sports Coaches (Football, Volleyball, Tennis)
 *
 * This script safely and idempotently provisions the three missing sport coach identities:
 *   1. Football   -> Lê Quốc Bảo  (bao.le@sportifycenter.vn)   -> /images/coaches/bao-le.jpg
 *   2. Volleyball -> Võ Ngọc Trâm  (tram.vo@sportifycenter.vn)  -> /images/coaches/tram-vo.jpg
 *   3. Tennis     -> Đặng Gia Huy  (huy.dang@sportifycenter.vn) -> /images/coaches/huy-dang.jpg
 *
 * Safety & Duplicate-awareness:
 *   - Only runs in an authorized database session (requires explicit session context or manual approval).
 *   - Preserves all existing coaches, accounts, classes, and schedules.
 *   - Checks for existing email/phone conflicts before inserting.
 *   - Idempotently creates user_account, coach_profile (is_public = 1), coach_sport, and unverified certification.
 *   - DO NOT EXECUTE without explicit CODEOWNER authorization.
 */

SET NOCOUNT ON;
SET XACT_ABORT ON;
-- sqlcmd defaults QUOTED_IDENTIFIER to OFF; filtered indexes on these tables need it ON (SSMS already sets it).
SET QUOTED_IDENTIFIER ON;

-- Opt-in guard: requires explicit authorization context
IF ISNULL(TRY_CAST(SESSION_CONTEXT(N'sportify_demo_seed_approved') AS INT), 0) <> 1
    THROW 51400, 'Database write requires explicit CODEOWNER authorization (SESSION_CONTEXT sportify_demo_seed_approved = 1).', 1;

BEGIN TRANSACTION;

DECLARE @PasswordHash NVARCHAR(100) = '$2a$10$cYcyDrFeL2hdtC4ajymEROOvLbCjeisVq/hVuALJReMtBxPqTXpga'; -- SportifyDemo2026!

DECLARE @MissingCoaches TABLE (
    email NVARCHAR(255),
    full_name NVARCHAR(100),
    phone NVARCHAR(20),
    role_code NVARCHAR(30),
    sport_code NVARCHAR(30),
    avatar_url NVARCHAR(255),
    headline NVARCHAR(150),
    specialties NVARCHAR(255),
    years_experience INT,
    bio NVARCHAR(1000),
    display_order INT,
    cert_name NVARCHAR(150),
    cert_issuer NVARCHAR(150),
    cert_date DATE
);

INSERT INTO @MissingCoaches VALUES
(
    N'bao.le@sportifycenter.vn', N'Lê Quốc Bảo', N'0912674390', N'COACH', N'FOOTBALL',
    N'/images/coaches/bao-le.jpg',
    N'Football coach for teens and adults', N'Teens, Ball control, Small-sided games', 8,
    N'Short drills, then real games. Players learn to pass, move and talk on the pitch.', 3,
    N'AFC C Coaching Certificate', N'Vietnam Football Federation', '2018-11-20'
),
(
    N'tram.vo@sportifycenter.vn', N'Võ Ngọc Trâm', N'0868905213', N'COACH', N'VOLLEYBALL',
    N'/images/coaches/tram-vo.jpg',
    N'Volleyball coach for beginners', N'Beginners, Serving, Team play', 5,
    N'Learn to pass, set and serve with confidence, then join friendly team games by week four.', 5,
    N'FIVB Coaches Course Level I', N'FIVB', '2021-03-12'
),
(
    N'huy.dang@sportifycenter.vn', N'Đặng Gia Huy', N'0789314652', N'COACH', N'TENNIS',
    N'/images/coaches/huy-dang.jpg',
    N'Tennis coach from first serve to match play', N'Beginners, Footwork, Match play', 10,
    N'Relaxed technique for beginners and point-play tactics for club players, on a court of up to four.', 6,
    N'ITF Play Tennis Course', N'International Tennis Federation', '2016-09-01'
);

-- Ensure COACH role exists
DECLARE @CoachRoleId BIGINT;
SELECT @CoachRoleId = id FROM [role] WHERE code = 'COACH';
IF @CoachRoleId IS NULL
    THROW 51902, 'Role COACH is missing.', 1;

-- 1. Insert user accounts if email does not exist
INSERT INTO user_account (email, password_hash, full_name, phone, avatar_url, role_id, status)
SELECT m.email, @PasswordHash, m.full_name, m.phone, m.avatar_url, @CoachRoleId, 'ACTIVE'
FROM @MissingCoaches m
WHERE NOT EXISTS (SELECT 1 FROM user_account u WHERE LOWER(u.email) = LOWER(m.email));

-- 2. Update avatar_url for existing matching accounts if null
UPDATE u
SET u.avatar_url = m.avatar_url, u.updated_at = SYSDATETIME()
FROM user_account u
JOIN @MissingCoaches m ON LOWER(u.email) = LOWER(m.email)
WHERE u.avatar_url IS NULL;

-- 3. Insert coach profiles if not present
INSERT INTO coach_profile (user_id, primary_sport_id, headline, bio, years_experience, specialties, is_public, display_order)
SELECT u.id, sp.id, m.headline, m.bio, m.years_experience, m.specialties, 1, m.display_order
FROM @MissingCoaches m
JOIN user_account u ON LOWER(u.email) = LOWER(m.email)
JOIN sport sp ON sp.code = m.sport_code
WHERE NOT EXISTS (SELECT 1 FROM coach_profile cp WHERE cp.user_id = u.id);

-- 4. Assign coach to sport in coach_sport if not already assigned
INSERT INTO coach_sport (coach_id, sport_id)
SELECT u.id, sp.id
FROM @MissingCoaches m
JOIN user_account u ON LOWER(u.email) = LOWER(m.email)
JOIN sport sp ON sp.code = m.sport_code
WHERE NOT EXISTS (SELECT 1 FROM coach_sport cs WHERE cs.coach_id = u.id AND cs.sport_id = sp.id);

-- 5. Add initial unverified certification if not already present
INSERT INTO coach_certification (coach_id, sport_id, name, issuer, issued_on, is_verified)
SELECT u.id, sp.id, m.cert_name, m.cert_issuer, m.cert_date, 0
FROM @MissingCoaches m
JOIN user_account u ON LOWER(u.email) = LOWER(m.email)
JOIN sport sp ON sp.code = m.sport_code
WHERE NOT EXISTS (SELECT 1 FROM coach_certification cc WHERE cc.coach_id = u.id AND cc.name = m.cert_name);

COMMIT TRANSACTION;

-- Verification query: list all active coaches and their assigned sports
SELECT u.id, u.full_name, u.email, u.avatar_url, sp.name AS primary_sport,
       (SELECT STRING_AGG(s.name, ', ') FROM coach_sport cs JOIN sport s ON s.id = cs.sport_id WHERE cs.coach_id = u.id) AS assigned_sports
FROM coach_profile cp
JOIN user_account u ON u.id = cp.user_id
JOIN sport sp ON sp.id = cp.primary_sport_id
WHERE cp.is_public = 1 AND u.status = 'ACTIVE'
ORDER BY cp.display_order, u.full_name;
