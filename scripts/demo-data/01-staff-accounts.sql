/*
 * Sportify Center - create six coaches and two receptionists with full coach profiles.
 * Run once in SQL Server Management Studio on database "sportify" (backend migrations already applied).
 * Safe to run again: existing e-mails are not inserted twice; coach profile text is refreshed.
 *
 * Login password for every account: SportifyDemo2026!  (same BCrypt hash as the README demo accounts)
 * The old test coaches "An Nguyen" and "Be Nguyen" are hidden from the Home page (accounts are kept).
 */
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

DECLARE @PasswordHash NVARCHAR(100) = '$2a$10$cYcyDrFeL2hdtC4ajymEROOvLbCjeisVq/hVuALJReMtBxPqTXpga';

DECLARE @Staff TABLE (email NVARCHAR(255), full_name NVARCHAR(100), phone NVARCHAR(20), role_code NVARCHAR(30), sport_code NVARCHAR(30));
INSERT INTO @Staff VALUES
(N'khoa.nguyen@sportifycenter.vn', N'Nguyễn Minh Khoa', N'0903418265', N'COACH', N'BADMINTON'),
(N'ha.tran@sportifycenter.vn',     N'Trần Thu Hà',      N'0938552107', N'COACH', N'SWIMMING'),
(N'bao.le@sportifycenter.vn',      N'Lê Quốc Bảo',      N'0912674390', N'COACH', N'FOOTBALL'),
(N'long.pham@sportifycenter.vn',   N'Phạm Hoàng Long',  N'0977231548', N'COACH', N'BASKETBALL'),
(N'tram.vo@sportifycenter.vn',     N'Võ Ngọc Trâm',     N'0868905213', N'COACH', N'VOLLEYBALL'),
(N'huy.dang@sportifycenter.vn',    N'Đặng Gia Huy',     N'0789314652', N'COACH', N'TENNIS'),
(N'mai.hoang@sportifycenter.vn',   N'Hoàng Mai Anh',    N'0901276845', N'RECEPTIONIST', NULL),
(N'tung.bui@sportifycenter.vn',    N'Bùi Thanh Tùng',   N'0935481920', N'RECEPTIONIST', NULL);

-- Stop early with a clear message instead of a unique-index error.
IF EXISTS (SELECT 1 FROM @Staff s JOIN user_account u ON u.phone = s.phone WHERE LOWER(u.email) <> s.email)
    THROW 51901, 'A phone number in this script already belongs to another account. Change it in @Staff and run again.', 1;
IF EXISTS (SELECT 1 FROM @Staff s LEFT JOIN [role] r ON r.code = s.role_code WHERE r.id IS NULL)
    THROW 51902, 'Role COACH or RECEPTIONIST is missing. Start the backend once so the migrations run.', 1;

INSERT INTO user_account (email, password_hash, full_name, phone, role_id, status)
SELECT s.email, @PasswordHash, s.full_name, s.phone, r.id, 'ACTIVE'
FROM @Staff s JOIN [role] r ON r.code = s.role_code
WHERE NOT EXISTS (SELECT 1 FROM user_account u WHERE LOWER(u.email) = s.email);

DECLARE @Profiles TABLE (email NVARCHAR(255), headline NVARCHAR(150), specialties NVARCHAR(255),
    years_experience INT, bio NVARCHAR(1000), display_order INT);
INSERT INTO @Profiles VALUES
(N'khoa.nguyen@sportifycenter.vn', N'Badminton coach for every level', N'Beginners, Club players, Doubles', 7,
 N'Footwork first, so every shot feels easier. Small groups, personal corrections and match play to finish.', 1),
(N'ha.tran@sportifycenter.vn', N'Swimming coach for kids and adults', N'Afraid of water, Kids, Freestyle', 9,
 N'Calm, small steps from floating to freestyle. Parents get a short progress note after each lesson.', 2),
(N'bao.le@sportifycenter.vn', N'Football coach for teens and adults', N'Teens, Ball control, Small-sided games', 8,
 N'Short drills, then real games. Players learn to pass, move and talk on the pitch.', 3),
(N'long.pham@sportifycenter.vn', N'Basketball coach for team play', N'Teens, Shooting, Team defence', 6,
 N'Shooting form and passing drills mixed with 3-on-3 games. Every player leaves with one skill to practise.', 4),
(N'tram.vo@sportifycenter.vn', N'Volleyball coach for beginners', N'Beginners, Serving, Team play', 5,
 N'Learn to pass, set and serve with confidence, then join friendly team games by week four.', 5),
(N'huy.dang@sportifycenter.vn', N'Tennis coach from first serve to match play', N'Beginners, Footwork, Match play', 10,
 N'Relaxed technique for beginners and point-play tactics for club players, on a court of up to four.', 6);

INSERT INTO coach_profile (user_id, primary_sport_id, is_public, display_order)
SELECT u.id, sp.id, 1, 0
FROM @Staff s JOIN user_account u ON LOWER(u.email) = s.email JOIN sport sp ON sp.code = s.sport_code
WHERE s.role_code = N'COACH' AND NOT EXISTS (SELECT 1 FROM coach_profile p WHERE p.user_id = u.id);

UPDATE cp SET headline = p.headline, specialties = p.specialties, years_experience = p.years_experience,
    bio = p.bio, is_public = 1, display_order = p.display_order, updated_at = SYSDATETIME()
FROM coach_profile cp JOIN user_account u ON u.id = cp.user_id JOIN @Profiles p ON p.email = LOWER(u.email);

INSERT INTO coach_sport (coach_id, sport_id)
SELECT u.id, sp.id
FROM @Staff s JOIN user_account u ON LOWER(u.email) = s.email JOIN sport sp ON sp.code = s.sport_code
WHERE s.role_code = N'COACH' AND NOT EXISTS (SELECT 1 FROM coach_sport cs WHERE cs.coach_id = u.id AND cs.sport_id = sp.id);

DECLARE @Certs TABLE (email NVARCHAR(255), sport_code NVARCHAR(30), name NVARCHAR(150), issuer NVARCHAR(150), issued_on DATE);
INSERT INTO @Certs VALUES
(N'khoa.nguyen@sportifycenter.vn', N'BADMINTON', N'Coach Education Level 1', N'Badminton World Federation', '2019-06-15'),
(N'ha.tran@sportifycenter.vn', N'SWIMMING', N'Swimming Instructor and Lifeguard Certificate', N'Ho Chi Minh City Swimming Federation', '2017-04-10'),
(N'bao.le@sportifycenter.vn', N'FOOTBALL', N'AFC C Coaching Certificate', N'Vietnam Football Federation', '2018-11-20'),
(N'long.pham@sportifycenter.vn', N'BASKETBALL', N'Basketball Coaching Certificate Level 1', N'Vietnam Basketball Federation', '2020-08-05'),
(N'tram.vo@sportifycenter.vn', N'VOLLEYBALL', N'FIVB Coaches Course Level I', N'FIVB', '2021-03-12'),
(N'huy.dang@sportifycenter.vn', N'TENNIS', N'ITF Play Tennis Course', N'International Tennis Federation', '2016-09-01');

INSERT INTO coach_certification (coach_id, sport_id, name, issuer, issued_on, is_verified)
SELECT u.id, sp.id, c.name, c.issuer, c.issued_on, 0
FROM @Certs c JOIN user_account u ON LOWER(u.email) = c.email
JOIN coach_profile cp ON cp.user_id = u.id
LEFT JOIN sport sp ON sp.code = c.sport_code
WHERE NOT EXISTS (SELECT 1 FROM coach_certification x WHERE x.coach_id = u.id AND x.name = c.name);

-- Hide the old test coaches from the Home page (set back to 1 to show them again).
UPDATE cp SET is_public = 0
FROM coach_profile cp JOIN user_account u ON u.id = cp.user_id
WHERE u.full_name IN (N'An Nguyen', N'Be Nguyen');

COMMIT TRANSACTION;

-- Check: eight staff accounts; six public coaches with headline, years, sport and one certificate.
SELECT u.full_name, u.email, u.phone, r.code AS role, u.status, cp.headline, cp.years_experience, cp.is_public,
       (SELECT STRING_AGG(sp.name, ', ') FROM coach_sport cs JOIN sport sp ON sp.id = cs.sport_id WHERE cs.coach_id = u.id) AS sports,
       (SELECT COUNT(*) FROM coach_certification x WHERE x.coach_id = u.id) AS certificates
FROM user_account u JOIN [role] r ON r.id = u.role_id LEFT JOIN coach_profile cp ON cp.user_id = u.id
WHERE r.code IN (N'COACH', N'RECEPTIONIST')
ORDER BY r.code, cp.is_public DESC, cp.display_order, u.full_name;
