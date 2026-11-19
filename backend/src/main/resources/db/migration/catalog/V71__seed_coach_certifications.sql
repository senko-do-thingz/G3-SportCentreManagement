-- Seed chung chi chuan cua trung tam cho cac Coach hien co theo mon the thao phu trach (idempotent).
IF OBJECT_ID(N'dbo.coach_certification', N'U') IS NULL
    OR OBJECT_ID(N'dbo.coach_profile', N'U') IS NULL
    OR OBJECT_ID(N'dbo.coach_sport', N'U') IS NULL
    OR OBJECT_ID(N'dbo.sport', N'U') IS NULL
    THROW 51071, 'V71 requires coach_certification, coach_profile, coach_sport and sport tables.', 1;
GO

DECLARE @DefaultCerts TABLE (
    sport_code NVARCHAR(30) NOT NULL,
    cert_name NVARCHAR(150) NOT NULL,
    issuer NVARCHAR(150) NOT NULL,
    issued_on DATE NOT NULL
);

INSERT INTO @DefaultCerts (sport_code, cert_name, issuer, issued_on)
VALUES
    (N'FOOTBALL', N'AFC C Coaching License', N'Asian Football Confederation', '2022-06-15'),
    (N'BADMINTON', N'BWF Level 2 Coaching Certificate', N'Badminton World Federation', '2022-08-20'),
    (N'BASKETBALL', N'FIBA Coaching Certificate', N'International Basketball Federation', '2023-03-10'),
    (N'VOLLEYBALL', N'FIVB Level 1 Coaching Certificate', N'International Volleyball Federation', '2023-04-12'),
    (N'SWIMMING', N'ASCA Swimming Coach Certification', N'American Swimming Coaches Association', '2022-05-18'),
    (N'TENNIS', N'ITF Level 1 Coaching Certificate', N'International Tennis Federation', '2023-01-25');

-- Gan chung chi da xac minh cho cac Coach dang giang day tung mon neu chua ton tai ban ghi tuong ung
INSERT INTO dbo.coach_certification (coach_id, sport_id, name, issuer, issued_on, is_verified)
SELECT DISTINCT
    cp.user_id,
    s.id,
    dc.cert_name,
    dc.issuer,
    dc.issued_on,
    1
FROM dbo.coach_profile cp
JOIN dbo.coach_sport cs ON cs.coach_id = cp.user_id
JOIN dbo.sport s ON s.id = cs.sport_id
JOIN @DefaultCerts dc ON UPPER(s.code) = dc.sport_code
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.coach_certification existing
    WHERE existing.coach_id = cp.user_id
      AND LOWER(existing.name) = LOWER(dc.cert_name)
);
GO
