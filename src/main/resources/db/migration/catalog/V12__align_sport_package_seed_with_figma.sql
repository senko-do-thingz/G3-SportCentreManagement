-- ============================================================================
-- Migration V12: Align seeded sport packages with Figma pricing screens
-- Context: Screens F1-02 (pages 1, 2, 3), F1-09, F1-10, F1-02 Overlay
-- Purpose: Provide the full 36 sport package catalog (6 sports x 6 packages).
-- Migration V10 is untouched.
-- Existing seeded rows (7 rows) are updated in place to preserve IDs and FK references.
-- Missing packages (29 rows) are inserted idempotently.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Part 1: Update existing 7 seeded packages in place (preserve IDs & FKs)
-- ----------------------------------------------------------------------------

-- 1. Badminton Self-training Single (PK-019)
UPDATE p
SET p.code = 'PK-019',
    p.name = 'Badminton - Single session',
    p.price_amount = 70000.00,
    p.description = 'Single session self-training access for Badminton (60 min)',
    p.is_active = 1,
    p.updated_at = SYSDATETIME()
FROM [sport_package] p
JOIN [sport] s ON s.id = p.sport_id
WHERE s.code = 'BADMINTON' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 1 AND p.session_count = 1;

-- 2. Badminton Self-training 30-day (PK-020)
UPDATE p
SET p.code = 'PK-020',
    p.name = 'Badminton - 30-day package',
    p.price_amount = 450000.00,
    p.description = '30 days package with 8 self-training sessions for Badminton (60 min each)',
    p.is_active = 1,
    p.updated_at = SYSDATETIME()
FROM [sport_package] p
JOIN [sport] s ON s.id = p.sport_id
WHERE s.code = 'BADMINTON' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 30 AND p.session_count = 8;

-- 3. Badminton Coach-led 30-day (PK-023)
UPDATE p
SET p.code = 'PK-023',
    p.name = 'Badminton - 30-day package (Coach-led)',
    p.price_amount = 850000.00,
    p.description = '30 days package with 8 coach-led sessions for Badminton (90 min each)',
    p.is_active = 1,
    p.updated_at = SYSDATETIME()
FROM [sport_package] p
JOIN [sport] s ON s.id = p.sport_id
WHERE s.code = 'BADMINTON' AND p.training_format = 'COACH_LED' AND p.duration_days = 30 AND p.session_count = 8;

-- 4. Badminton Coach-led 90-day (PK-024)
UPDATE p
SET p.code = 'PK-024',
    p.name = 'Badminton - 90-day package (Coach-led)',
    p.price_amount = 2250000.00,
    p.description = '90 days package with 24 coach-led sessions for Badminton (90 min each)',
    p.is_active = 1,
    p.updated_at = SYSDATETIME()
FROM [sport_package] p
JOIN [sport] s ON s.id = p.sport_id
WHERE s.code = 'BADMINTON' AND p.training_format = 'COACH_LED' AND p.duration_days = 90 AND p.session_count = 24;

-- 5. Swimming Self-training 30-day (PK-014)
UPDATE p
SET p.code = 'PK-014',
    p.name = 'Swimming - 30-day package',
    p.price_amount = 600000.00,
    p.description = '30 days package with 8 self-training sessions for Swimming (60 min each)',
    p.is_active = 1,
    p.updated_at = SYSDATETIME()
FROM [sport_package] p
JOIN [sport] s ON s.id = p.sport_id
WHERE s.code = 'SWIMMING' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 30 AND p.session_count = 8;

-- 6. Swimming Coach-led 30-day (PK-017)
UPDATE p
SET p.code = 'PK-017',
    p.name = 'Swimming - 30-day package (Coach-led)',
    p.price_amount = 1100000.00,
    p.description = '30 days package with 8 coach-led sessions for Swimming (90 min each)',
    p.is_active = 1,
    p.updated_at = SYSDATETIME()
FROM [sport_package] p
JOIN [sport] s ON s.id = p.sport_id
WHERE s.code = 'SWIMMING' AND p.training_format = 'COACH_LED' AND p.duration_days = 30 AND p.session_count = 8;

-- 7. Basketball Coach-led 30-day (PK-011)
UPDATE p
SET p.code = 'PK-011',
    p.name = 'Basketball - 30-day package (Coach-led)',
    p.price_amount = 800000.00,
    p.description = '30 days package with 8 coach-led sessions for Basketball (90 min each)',
    p.is_active = 1,
    p.updated_at = SYSDATETIME()
FROM [sport_package] p
JOIN [sport] s ON s.id = p.sport_id
WHERE s.code = 'BASKETBALL' AND p.training_format = 'COACH_LED' AND p.duration_days = 30 AND p.session_count = 8;


-- ----------------------------------------------------------------------------
-- Part 2: Insert missing 29 packages idempotently
-- ----------------------------------------------------------------------------

-- Football (PK-001 .. PK-006)
IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-001')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'FOOTBALL' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 1 AND p.session_count = 1)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-001', 'Football - Single session', s.id, 'SELF_TRAINING', 1, 1, 80000.00, 'Single session self-training access for Football (60 min)', 1
    FROM [sport] s WHERE s.code = 'FOOTBALL';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-002')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'FOOTBALL' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 30 AND p.session_count = 8)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-002', 'Football - 30-day package', s.id, 'SELF_TRAINING', 30, 8, 500000.00, '30 days package with 8 self-training sessions for Football (60 min each)', 1
    FROM [sport] s WHERE s.code = 'FOOTBALL';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-003')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'FOOTBALL' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 90 AND p.session_count = 24)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-003', 'Football - 90-day package', s.id, 'SELF_TRAINING', 90, 24, 1350000.00, '90 days package with 24 self-training sessions for Football (60 min each)', 1
    FROM [sport] s WHERE s.code = 'FOOTBALL';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-004')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'FOOTBALL' AND p.training_format = 'COACH_LED' AND p.duration_days = 1 AND p.session_count = 1)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-004', 'Football - Single session (Coach-led)', s.id, 'COACH_LED', 1, 1, 150000.00, 'Single session coach-led training for Football (90 min)', 1
    FROM [sport] s WHERE s.code = 'FOOTBALL';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-005')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'FOOTBALL' AND p.training_format = 'COACH_LED' AND p.duration_days = 30 AND p.session_count = 8)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-005', 'Football - 30-day package (Coach-led)', s.id, 'COACH_LED', 30, 8, 900000.00, '30 days package with 8 coach-led sessions for Football (90 min each)', 1
    FROM [sport] s WHERE s.code = 'FOOTBALL';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-006')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'FOOTBALL' AND p.training_format = 'COACH_LED' AND p.duration_days = 90 AND p.session_count = 24)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-006', 'Football - 90-day package (Coach-led)', s.id, 'COACH_LED', 90, 24, 2400000.00, '90 days package with 24 coach-led sessions for Football (90 min each)', 1
    FROM [sport] s WHERE s.code = 'FOOTBALL';
END;

-- Basketball (PK-007 .. PK-012, excluding PK-011 updated above)
IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-007')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'BASKETBALL' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 1 AND p.session_count = 1)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-007', 'Basketball - Single session', s.id, 'SELF_TRAINING', 1, 1, 70000.00, 'Single session self-training access for Basketball (60 min)', 1
    FROM [sport] s WHERE s.code = 'BASKETBALL';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-008')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'BASKETBALL' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 30 AND p.session_count = 8)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-008', 'Basketball - 30-day package', s.id, 'SELF_TRAINING', 30, 8, 450000.00, '30 days package with 8 self-training sessions for Basketball (60 min each)', 1
    FROM [sport] s WHERE s.code = 'BASKETBALL';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-009')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'BASKETBALL' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 90 AND p.session_count = 24)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-009', 'Basketball - 90-day package', s.id, 'SELF_TRAINING', 90, 24, 1200000.00, '90 days package with 24 self-training sessions for Basketball (60 min each)', 1
    FROM [sport] s WHERE s.code = 'BASKETBALL';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-010')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'BASKETBALL' AND p.training_format = 'COACH_LED' AND p.duration_days = 1 AND p.session_count = 1)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-010', 'Basketball - Single session (Coach-led)', s.id, 'COACH_LED', 1, 1, 130000.00, 'Single session coach-led training for Basketball (90 min)', 1
    FROM [sport] s WHERE s.code = 'BASKETBALL';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-012')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'BASKETBALL' AND p.training_format = 'COACH_LED' AND p.duration_days = 90 AND p.session_count = 24)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-012', 'Basketball - 90-day package (Coach-led)', s.id, 'COACH_LED', 90, 24, 2100000.00, '90 days package with 24 coach-led sessions for Basketball (90 min each)', 1
    FROM [sport] s WHERE s.code = 'BASKETBALL';
END;

-- Swimming (PK-013 .. PK-018, excluding PK-014 and PK-017 updated above)
IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-013')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'SWIMMING' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 1 AND p.session_count = 1)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-013', 'Swimming - Single session', s.id, 'SELF_TRAINING', 1, 1, 90000.00, 'Single session self-training access for Swimming (60 min)', 1
    FROM [sport] s WHERE s.code = 'SWIMMING';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-015')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'SWIMMING' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 90 AND p.session_count = 24)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-015', 'Swimming - 90-day package', s.id, 'SELF_TRAINING', 90, 24, 1620000.00, '90 days package with 24 self-training sessions for Swimming (60 min each)', 1
    FROM [sport] s WHERE s.code = 'SWIMMING';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-016')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'SWIMMING' AND p.training_format = 'COACH_LED' AND p.duration_days = 1 AND p.session_count = 1)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-016', 'Swimming - Single session (Coach-led)', s.id, 'COACH_LED', 1, 1, 180000.00, 'Single session coach-led training for Swimming (90 min)', 1
    FROM [sport] s WHERE s.code = 'SWIMMING';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-018')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'SWIMMING' AND p.training_format = 'COACH_LED' AND p.duration_days = 90 AND p.session_count = 24)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-018', 'Swimming - 90-day package (Coach-led)', s.id, 'COACH_LED', 90, 24, 2970000.00, '90 days package with 24 coach-led sessions for Swimming (90 min each)', 1
    FROM [sport] s WHERE s.code = 'SWIMMING';
END;

-- Badminton (PK-019 .. PK-024, excluding PK-019, PK-020, PK-023, PK-024 updated above)
IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-021')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'BADMINTON' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 90 AND p.session_count = 24)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-021', 'Badminton - 90-day package', s.id, 'SELF_TRAINING', 90, 24, 1200000.00, '90 days package with 24 self-training sessions for Badminton (60 min each)', 1
    FROM [sport] s WHERE s.code = 'BADMINTON';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-022')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'BADMINTON' AND p.training_format = 'COACH_LED' AND p.duration_days = 1 AND p.session_count = 1)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-022', 'Badminton - Single session (Coach-led)', s.id, 'COACH_LED', 1, 1, 140000.00, 'Single session coach-led training for Badminton (90 min)', 1
    FROM [sport] s WHERE s.code = 'BADMINTON';
END;

-- Volleyball (PK-025 .. PK-030)
IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-025')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'VOLLEYBALL' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 1 AND p.session_count = 1)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-025', 'Volleyball - Single session', s.id, 'SELF_TRAINING', 1, 1, 60000.00, 'Single session self-training access for Volleyball (60 min)', 1
    FROM [sport] s WHERE s.code = 'VOLLEYBALL';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-026')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'VOLLEYBALL' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 30 AND p.session_count = 8)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-026', 'Volleyball - 30-day package', s.id, 'SELF_TRAINING', 30, 8, 400000.00, '30 days package with 8 self-training sessions for Volleyball (60 min each)', 1
    FROM [sport] s WHERE s.code = 'VOLLEYBALL';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-027')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'VOLLEYBALL' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 90 AND p.session_count = 24)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-027', 'Volleyball - 90-day package', s.id, 'SELF_TRAINING', 90, 24, 1080000.00, '90 days package with 24 self-training sessions for Volleyball (60 min each)', 1
    FROM [sport] s WHERE s.code = 'VOLLEYBALL';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-028')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'VOLLEYBALL' AND p.training_format = 'COACH_LED' AND p.duration_days = 1 AND p.session_count = 1)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-028', 'Volleyball - Single session (Coach-led)', s.id, 'COACH_LED', 1, 1, 120000.00, 'Single session coach-led training for Volleyball (90 min)', 1
    FROM [sport] s WHERE s.code = 'VOLLEYBALL';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-029')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'VOLLEYBALL' AND p.training_format = 'COACH_LED' AND p.duration_days = 30 AND p.session_count = 8)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-029', 'Volleyball - 30-day package (Coach-led)', s.id, 'COACH_LED', 30, 8, 750000.00, '30 days package with 8 coach-led sessions for Volleyball (90 min each)', 1
    FROM [sport] s WHERE s.code = 'VOLLEYBALL';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-030')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'VOLLEYBALL' AND p.training_format = 'COACH_LED' AND p.duration_days = 90 AND p.session_count = 24)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-030', 'Volleyball - 90-day package (Coach-led)', s.id, 'COACH_LED', 90, 24, 1950000.00, '90 days package with 24 coach-led sessions for Volleyball (90 min each)', 1
    FROM [sport] s WHERE s.code = 'VOLLEYBALL';
END;

-- Tennis (PK-031 .. PK-036)
IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-031')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'TENNIS' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 1 AND p.session_count = 1)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-031', 'Tennis - Single session', s.id, 'SELF_TRAINING', 1, 1, 120000.00, 'Single session self-training access for Tennis (60 min)', 1
    FROM [sport] s WHERE s.code = 'TENNIS';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-032')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'TENNIS' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 30 AND p.session_count = 8)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-032', 'Tennis - 30-day package', s.id, 'SELF_TRAINING', 30, 8, 800000.00, '30 days package with 8 self-training sessions for Tennis (60 min each)', 1
    FROM [sport] s WHERE s.code = 'TENNIS';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-033')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'TENNIS' AND p.training_format = 'SELF_TRAINING' AND p.duration_days = 90 AND p.session_count = 24)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-033', 'Tennis - 90-day package', s.id, 'SELF_TRAINING', 90, 24, 2160000.00, '90 days package with 24 self-training sessions for Tennis (60 min each)', 1
    FROM [sport] s WHERE s.code = 'TENNIS';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-034')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'TENNIS' AND p.training_format = 'COACH_LED' AND p.duration_days = 1 AND p.session_count = 1)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-034', 'Tennis - Single session (Coach-led)', s.id, 'COACH_LED', 1, 1, 220000.00, 'Single session coach-led training for Tennis (90 min)', 1
    FROM [sport] s WHERE s.code = 'TENNIS';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-035')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'TENNIS' AND p.training_format = 'COACH_LED' AND p.duration_days = 30 AND p.session_count = 8)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-035', 'Tennis - 30-day package (Coach-led)', s.id, 'COACH_LED', 30, 8, 1400000.00, '30 days package with 8 coach-led sessions for Tennis (90 min each)', 1
    FROM [sport] s WHERE s.code = 'TENNIS';
END;

IF NOT EXISTS (SELECT 1 FROM [sport_package] WHERE code = 'PK-036')
   AND NOT EXISTS (SELECT 1 FROM [sport_package] p JOIN [sport] s ON s.id = p.sport_id WHERE s.code = 'TENNIS' AND p.training_format = 'COACH_LED' AND p.duration_days = 90 AND p.session_count = 24)
BEGIN
    INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
    SELECT 'PK-036', 'Tennis - 90-day package (Coach-led)', s.id, 'COACH_LED', 90, 24, 3780000.00, '90 days package with 24 coach-led sessions for Tennis (90 min each)', 1
    FROM [sport] s WHERE s.code = 'TENNIS';
END;
