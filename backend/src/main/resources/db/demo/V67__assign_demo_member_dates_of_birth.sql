-- Give the three public demonstration members an adult date of birth.
-- Self-service card and package purchases and age-grouped class bookings require a known date of birth,
-- and every demo class uses the ADULTS_18_PLUS age group.
-- Preserve any date of birth that a member has already entered in an existing demo database.
UPDATE u
SET date_of_birth = birthdays.date_of_birth
FROM user_account u
JOIN (VALUES
    ('an.gold@example.com', CAST('19950412' AS DATE)),
    ('mai.standard@example.com', CAST('19980903' AS DATE)),
    ('bao.nocard@example.com', CAST('20010115' AS DATE))
) birthdays(email, date_of_birth) ON LOWER(u.email) = birthdays.email
JOIN [role] r ON r.id = u.role_id AND r.code = 'MEMBER'
WHERE u.date_of_birth IS NULL;
