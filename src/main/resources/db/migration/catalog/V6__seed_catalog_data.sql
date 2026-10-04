-- V6__seed_catalog_data.sql
-- Seed data for Module 2: Catalog and Membership.
-- Base: docs/database/10-ddl-sqlserver.md (V2__seed_reference_data.sql section).
-- Idempotent (IF NOT EXISTS / WHERE NOT EXISTS), same style as V3.

-- ==========================================
-- Sports (10-ddl-sqlserver.md)
-- ==========================================
IF NOT EXISTS (SELECT 1 FROM [sport] WHERE code = 'FOOTBALL')
BEGIN
    INSERT INTO [sport] (code, name, venue_type) VALUES ('FOOTBALL', 'Football', 'OUTDOOR');
END

IF NOT EXISTS (SELECT 1 FROM [sport] WHERE code = 'BADMINTON')
BEGIN
    INSERT INTO [sport] (code, name, venue_type) VALUES ('BADMINTON', 'Badminton', 'INDOOR');
END

IF NOT EXISTS (SELECT 1 FROM [sport] WHERE code = 'BASKETBALL')
BEGIN
    INSERT INTO [sport] (code, name, venue_type) VALUES ('BASKETBALL', 'Basketball', 'INDOOR');
END

IF NOT EXISTS (SELECT 1 FROM [sport] WHERE code = 'VOLLEYBALL')
BEGIN
    INSERT INTO [sport] (code, name, venue_type) VALUES ('VOLLEYBALL', 'Volleyball', 'INDOOR');
END

IF NOT EXISTS (SELECT 1 FROM [sport] WHERE code = 'SWIMMING')
BEGIN
    INSERT INTO [sport] (code, name, venue_type) VALUES ('SWIMMING', 'Swimming', 'POOL');
END

IF NOT EXISTS (SELECT 1 FROM [sport] WHERE code = 'TENNIS')
BEGIN
    INSERT INTO [sport] (code, name, venue_type) VALUES ('TENNIS', 'Tennis', 'OUTDOOR');
END

-- ==========================================
-- Age groups (10-ddl-sqlserver.md)
-- ==========================================
IF NOT EXISTS (SELECT 1 FROM [age_group] WHERE code = 'KIDS_6_12')
BEGIN
    INSERT INTO [age_group] (code, label, min_age, max_age, display_order) VALUES ('KIDS_6_12', 'Kids 6-12', 6, 12, 1);
END

IF NOT EXISTS (SELECT 1 FROM [age_group] WHERE code = 'AGES_8_11')
BEGIN
    INSERT INTO [age_group] (code, label, min_age, max_age, display_order) VALUES ('AGES_8_11', 'Ages 8-11', 8, 11, 2);
END

IF NOT EXISTS (SELECT 1 FROM [age_group] WHERE code = 'TEENS_13_17')
BEGIN
    INSERT INTO [age_group] (code, label, min_age, max_age, display_order) VALUES ('TEENS_13_17', 'Teens 13-17', 13, 17, 3);
END

IF NOT EXISTS (SELECT 1 FROM [age_group] WHERE code = 'AGES_16_PLUS')
BEGIN
    INSERT INTO [age_group] (code, label, min_age, max_age, display_order) VALUES ('AGES_16_PLUS', 'Ages 16+', 16, NULL, 4);
END

IF NOT EXISTS (SELECT 1 FROM [age_group] WHERE code = 'ADULTS_18_PLUS')
BEGIN
    INSERT INTO [age_group] (code, label, min_age, max_age, display_order) VALUES ('ADULTS_18_PLUS', 'Adults 18+', 18, NULL, 5);
END

-- ==========================================
-- Membership plans (10-ddl-sqlserver.md; Multi-Sport price 550000 per the doc assumption)
-- tagline / description only where the design doc gives an example (02-catalog-and-membership.md).
-- ==========================================
IF NOT EXISTS (SELECT 1 FROM [membership_plan] WHERE code = 'STARTER')
BEGIN
    INSERT INTO [membership_plan] (code, name, price, duration_days, max_sports, is_featured, status)
    VALUES ('STARTER', 'Starter', 300000.00, 30, 1, 0, 'ACTIVE');
END

IF NOT EXISTS (SELECT 1 FROM [membership_plan] WHERE code = 'MULTI_SPORT')
BEGIN
    INSERT INTO [membership_plan] (code, name, tagline, description, price, duration_days, max_sports, is_featured, status)
    VALUES ('MULTI_SPORT', 'Multi-Sport', '02 / EXPLORE', 'Mix classes across up to three sports.', 550000.00, 30, 3, 1, 'ACTIVE');
END

IF NOT EXISTS (SELECT 1 FROM [membership_plan] WHERE code = 'ALL_ACCESS')
BEGIN
    INSERT INTO [membership_plan] (code, name, price, duration_days, max_sports, is_featured, status)
    VALUES ('ALL_ACCESS', 'All Access', 800000.00, 30, 6, 0, 'ACTIVE');
END

IF NOT EXISTS (SELECT 1 FROM [membership_plan] WHERE code = 'SWIM_STARTER')
BEGIN
    INSERT INTO [membership_plan] (code, name, price, duration_days, max_sports, is_featured, status)
    VALUES ('SWIM_STARTER', 'Swim Starter', 250000.00, 30, 1, 0, 'ACTIVE');
END

IF NOT EXISTS (SELECT 1 FROM [membership_plan] WHERE code = 'BASKETBALL_PASS')
BEGIN
    INSERT INTO [membership_plan] (code, name, price, duration_days, max_sports, is_featured, status)
    VALUES ('BASKETBALL_PASS', 'Basketball Pass', 280000.00, 30, 1, 0, 'ACTIVE');
END

-- ==========================================
-- Plan eligible sports
-- ==========================================
-- General plans (Starter, Multi-Sport, All Access): pool = all 6 sports
INSERT INTO [plan_eligible_sport] (plan_id, sport_id)
SELECT p.id, s.id
FROM [membership_plan] p
CROSS JOIN [sport] s
WHERE p.code IN ('STARTER', 'MULTI_SPORT', 'ALL_ACCESS')
  AND s.code IN ('FOOTBALL', 'BADMINTON', 'BASKETBALL', 'VOLLEYBALL', 'SWIMMING', 'TENNIS')
  AND NOT EXISTS (
      SELECT 1 FROM [plan_eligible_sport] pes WHERE pes.plan_id = p.id AND pes.sport_id = s.id
  );

-- Single-sport plans
INSERT INTO [plan_eligible_sport] (plan_id, sport_id)
SELECT p.id, s.id
FROM [membership_plan] p
CROSS JOIN [sport] s
WHERE p.code = 'SWIM_STARTER' AND s.code = 'SWIMMING'
  AND NOT EXISTS (
      SELECT 1 FROM [plan_eligible_sport] pes WHERE pes.plan_id = p.id AND pes.sport_id = s.id
  );

INSERT INTO [plan_eligible_sport] (plan_id, sport_id)
SELECT p.id, s.id
FROM [membership_plan] p
CROSS JOIN [sport] s
WHERE p.code = 'BASKETBALL_PASS' AND s.code = 'BASKETBALL'
  AND NOT EXISTS (
      SELECT 1 FROM [plan_eligible_sport] pes WHERE pes.plan_id = p.id AND pes.sport_id = s.id
  );

-- ==========================================
-- Plan features (texts taken from the examples in 02-catalog-and-membership.md)
-- ==========================================
INSERT INTO [plan_feature] (plan_id, feature_text, display_order)
SELECT p.id, f.feature_text, f.display_order
FROM [membership_plan] p
CROSS JOIN (VALUES
    (N'Coach-led class booking', 1),
    (N'Personal schedule and progress', 2)
) AS f(feature_text, display_order)
WHERE p.code IN ('STARTER', 'MULTI_SPORT', 'ALL_ACCESS', 'SWIM_STARTER', 'BASKETBALL_PASS')
  AND NOT EXISTS (
      SELECT 1 FROM [plan_feature] pf WHERE pf.plan_id = p.id AND pf.feature_text = f.feature_text
  );

-- ==========================================
-- Facilities (codes / names from 02-catalog-and-membership.md)
-- ==========================================
IF NOT EXISTS (SELECT 1 FROM [facility] WHERE code = 'BB_COURT_A')
BEGIN
    INSERT INTO [facility] (code, name, facility_type, sport_id)
    SELECT 'BB_COURT_A', 'Basketball Court A', 'COURT', s.id FROM [sport] s WHERE s.code = 'BASKETBALL';
END

IF NOT EXISTS (SELECT 1 FROM [facility] WHERE code = 'BB_COURT_B')
BEGIN
    INSERT INTO [facility] (code, name, facility_type, sport_id)
    SELECT 'BB_COURT_B', 'Basketball Court B', 'COURT', s.id FROM [sport] s WHERE s.code = 'BASKETBALL';
END

IF NOT EXISTS (SELECT 1 FROM [facility] WHERE code = 'INDOOR_POOL')
BEGIN
    INSERT INTO [facility] (code, name, facility_type, sport_id)
    SELECT 'INDOOR_POOL', 'Indoor Pool', 'POOL', s.id FROM [sport] s WHERE s.code = 'SWIMMING';
END
