-- Add the bundled, credited portraits to the three public demonstration coaches.
-- Preserve any photo that a manager has already assigned in an existing demo database.
UPDATE u
SET avatar_url = photos.avatar_url
FROM user_account u
JOIN (VALUES
    ('nam.coach@example.com', '/images/coaches/long-pham.jpg'),
    ('ha.coach@example.com', '/images/coaches/tram-vo.jpg'),
    ('quan.coach@example.com', '/images/coaches/bao-le.jpg')
) photos(email, avatar_url) ON LOWER(u.email) = photos.email
JOIN [role] r ON r.id = u.role_id AND r.code = 'COACH'
WHERE u.avatar_url IS NULL OR LTRIM(RTRIM(u.avatar_url)) = '';
