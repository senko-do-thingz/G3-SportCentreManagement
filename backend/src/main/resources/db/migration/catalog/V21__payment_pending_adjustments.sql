-- ============================================================================
-- Migration: V21__payment_pending_adjustments.sql
-- Description:
--   1. Make payment_time nullable for pending payments.
--   2. Add unique filtered indexes on member_card(payment_id) and
--      sport_package_registration(payment_id) where payment_id IS NOT NULL,
--      with duplicate payment_id safety checks before creation.
--   3. Add confirmed_by_user_id foreign key on payment.
--   4. Ensure ck_payment_amount check constraint (amount > 0).
-- ============================================================================

-- Step 1: Make payment_time nullable
ALTER TABLE [payment] ALTER COLUMN [payment_time] DATETIME2(0) NULL;

-- Step 2: Add unique filtered index on member_card(payment_id) with duplicate guard
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

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_member_card_payment' AND object_id = OBJECT_ID('member_card'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX [ux_member_card_payment]
        ON [member_card]([payment_id])
        WHERE [payment_id] IS NOT NULL;
END;

-- Step 3: Add unique filtered index on sport_package_registration(payment_id) with duplicate guard
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

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_package_reg_payment' AND object_id = OBJECT_ID('sport_package_registration'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX [ux_package_reg_payment]
        ON [sport_package_registration]([payment_id])
        WHERE [payment_id] IS NOT NULL;
END;

-- Step 4: Add confirmed_by_user_id column on payment
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE name = 'confirmed_by_user_id' AND object_id = OBJECT_ID('payment')
)
BEGIN
    ALTER TABLE [payment] ADD [confirmed_by_user_id] BIGINT NULL;
    ALTER TABLE [payment] ADD CONSTRAINT [fk_payment_confirmed_by]
        FOREIGN KEY ([confirmed_by_user_id]) REFERENCES [user_account]([id]);
END;

-- Step 5: Ensure ck_payment_amount exists
IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = 'ck_payment_amount' AND parent_object_id = OBJECT_ID('payment')
)
BEGIN
    ALTER TABLE [payment] ADD CONSTRAINT [ck_payment_amount] CHECK ([amount] > 0);
END;
