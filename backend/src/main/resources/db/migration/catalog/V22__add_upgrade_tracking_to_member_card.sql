-- ============================================================================
-- Migration: V22__add_upgrade_tracking_to_member_card.sql
-- Description:
--   1. Add upgrade_request BIT column to member_card to explicitly record whether
--      a card request was made as an upgrade (instead of inferring from live tier price).
--   2. Add replaces_card_id BIGINT column referencing member_card(id) to record
--      which active card is being upgraded/replaced.
--   3. Safely backfill existing PENDING_PAYMENT rows where price_paid < tier.price.
-- ============================================================================

-- Step 1: Add upgrade_request column with default 0
ALTER TABLE [member_card] ADD [upgrade_request] BIT NOT NULL CONSTRAINT [df_member_card_upgrade_request] DEFAULT 0;

-- Step 2: Add replaces_card_id column and foreign key constraint
ALTER TABLE [member_card] ADD [replaces_card_id] BIGINT NULL;
ALTER TABLE [member_card] ADD CONSTRAINT [fk_member_card_replaces_card]
    FOREIGN KEY ([replaces_card_id]) REFERENCES [member_card]([id]);

-- Step 3: Backfill existing PENDING_PAYMENT rows where price_paid was an upgrade difference
UPDATE mc
SET mc.[upgrade_request] = 1
FROM [member_card] mc
JOIN [membership_card_tier] t ON mc.[tier_id] = t.[id]
WHERE mc.[status] = 'PENDING_PAYMENT'
  AND mc.[price_paid] < t.[price];
