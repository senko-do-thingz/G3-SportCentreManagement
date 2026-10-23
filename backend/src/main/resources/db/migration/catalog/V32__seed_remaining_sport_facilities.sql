-- ==========================================
-- Give every sport at least one teaching facility.
-- V6 seeded Basketball Court A, Basketball Court B and Indoor Pool only, so Badminton, Football, Volleyball and
-- Tennis had no facility to schedule sessions on. Badminton gets two courts so two sessions can run at the
-- same time and the facility overlap rule (F2-03) can be shown on separate courts.
-- Idempotent: each row is inserted only when its code is not present and its sport exists.
-- ==========================================
INSERT INTO [facility] (code, name, facility_type, sport_id, capacity, is_active)
SELECT v.code, v.name, v.facility_type, s.id, v.capacity, 1
FROM (VALUES
    ('BD_COURT_1', 'Badminton Court 1', 'COURT', 'BADMINTON', 8),
    ('BD_COURT_2', 'Badminton Court 2', 'COURT', 'BADMINTON', 8),
    ('FB_FIELD', 'Football Field', 'FIELD', 'FOOTBALL', 22),
    ('VB_COURT', 'Volleyball Court', 'COURT', 'VOLLEYBALL', 16),
    ('TN_COURT', 'Tennis Court', 'COURT', 'TENNIS', 6)
) AS v(code, name, facility_type, sport_code, capacity)
JOIN [sport] s ON s.code = v.sport_code
WHERE NOT EXISTS (SELECT 1 FROM [facility] f WHERE f.code = v.code);
