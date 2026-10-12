-- ============================================================================
-- Migration: V21__payment_pending_adjustments.sql
-- Description:
--   1. Make payment_time nullable for pending payments.
--   2. Add unique filtered indexes on member_card(payment_id) and
--      sport_package_registration(payment_id) where payment_id IS NOT NULL.
-- ============================================================================

-- Step 1: Make payment_time nullable
ALTER TABLE [payment] ALTER COLUMN [payment_time] DATETIME2(0) NULL;

-- Step 2: Add unique filtered index on member_card(payment_id)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_member_card_payment' AND object_id = OBJECT_ID('member_card'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX [ux_member_card_payment]
        ON [member_card]([payment_id])
        WHERE [payment_id] IS NOT NULL;
END;

-- Step 3: Add unique filtered index on sport_package_registration(payment_id)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_package_reg_payment' AND object_id = OBJECT_ID('sport_package_registration'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX [ux_package_reg_payment]
        ON [sport_package_registration]([payment_id])
        WHERE [payment_id] IS NOT NULL;
END;
