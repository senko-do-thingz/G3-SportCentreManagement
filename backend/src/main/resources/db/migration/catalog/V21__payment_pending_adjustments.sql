-- ============================================================================
-- Migration: V21__payment_pending_adjustments.sql
-- Description:
--   1. Drop default constraint df_payment_payment_time and make payment_time nullable.
--   2. Backfill payment_time to NULL for payments whose status is not SUCCESS.
--   3. Add check constraint ck_payment_success_time (SUCCESS implies payment_time IS NOT NULL).
--   4. Add unique filtered indexes on member_card(payment_id) and
--      sport_package_registration(payment_id) where payment_id IS NOT NULL,
--      guarded by duplicate payment_id checks.
--   5. Add confirmed_by_user_id foreign key on payment.
-- ============================================================================

-- Step 1: Drop default constraint on payment_time and make it nullable
ALTER TABLE [payment] DROP CONSTRAINT [df_payment_payment_time];
ALTER TABLE [payment] ALTER COLUMN [payment_time] DATETIME2(0) NULL;

-- Step 2: Backfill: non-SUCCESS payments must not retain payment_time
UPDATE [payment]
SET [payment_time] = NULL
WHERE [payment_status] <> 'SUCCESS';

-- Step 3: Add check constraint ensuring payment_time is present for SUCCESS payments
ALTER TABLE [payment] ADD CONSTRAINT [ck_payment_success_time]
    CHECK ([payment_status] <> 'SUCCESS' OR [payment_time] IS NOT NULL);

-- Step 4: Add unique filtered index on member_card(payment_id) with duplicate guard
IF EXISTS (
    SELECT [payment_id]
    FROM [member_card]
    WHERE [payment_id] IS NOT NULL
    GROUP BY [payment_id]
    HAVING COUNT(*) > 1
)
BEGIN
    THROW 50000, 'Cannot create unique index ux_member_card_payment: duplicate payment_id detected in member_card', 1;
END;

CREATE UNIQUE NONCLUSTERED INDEX [ux_member_card_payment]
    ON [member_card]([payment_id])
    WHERE [payment_id] IS NOT NULL;

-- Step 5: Add unique filtered index on sport_package_registration(payment_id) with duplicate guard
IF EXISTS (
    SELECT [payment_id]
    FROM [sport_package_registration]
    WHERE [payment_id] IS NOT NULL
    GROUP BY [payment_id]
    HAVING COUNT(*) > 1
)
BEGIN
    THROW 50000, 'Cannot create unique index ux_package_reg_payment: duplicate payment_id detected in sport_package_registration', 1;
END;

CREATE UNIQUE NONCLUSTERED INDEX [ux_package_reg_payment]
    ON [sport_package_registration]([payment_id])
    WHERE [payment_id] IS NOT NULL;

-- Step 6: Add confirmed_by_user_id column on payment
ALTER TABLE [payment] ADD [confirmed_by_user_id] BIGINT NULL;
ALTER TABLE [payment] ADD CONSTRAINT [fk_payment_confirmed_by]
    FOREIGN KEY ([confirmed_by_user_id]) REFERENCES [user_account]([id]);
