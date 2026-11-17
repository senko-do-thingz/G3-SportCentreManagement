-- ==========================================
-- Set physical capacity for basketball courts and indoor pool facilities.
-- V6 seeded Basketball Court A, Basketball Court B, and Indoor Pool without capacity (NULL).
-- This migration assigns their designed physical capacities:
-- Basketball Court A & B: 10 members
-- Indoor Pool: 15 members
-- ==========================================

UPDATE [facility]
SET [capacity] = 10
WHERE [code] IN ('BB_COURT_A', 'BB_COURT_B')
  AND ([capacity] IS NULL OR [capacity] <> 10);

UPDATE [facility]
SET [capacity] = 15
WHERE [code] = 'INDOOR_POOL'
  AND ([capacity] IS NULL OR [capacity] <> 15);
