-- ============================================================================
-- Migration: V22__add_upgrade_tracking_to_member_card.sql
-- Description:
--   1. Add upgrade_request BIT column to member_card to explicitly record whether
--      a card request was made as an upgrade (instead of inferring from live tier price).
--   2. Add replaces_card_id BIGINT column referencing member_card(id) to record
--      which active card is being upgraded/replaced.
--   3. Idempotently backfill existing cards and deterministic replacement links:
--      - For existing cards (ACTIVE or PENDING_PAYMENT) where tier.duration_months > 0
--        and price_paid < tier.price (legacy upgrade heuristic), set upgrade_request = 1.
--      - For ACTIVE legacy upgrades where replaces_card_id IS NULL: link to the unique
--        REPLACED card of the same member with the exact same end_date.
--      - For PENDING_PAYMENT legacy upgrades where replaces_card_id IS NULL: link to
--        the member's unique current ACTIVE paid card whose term has started.
--      - If any candidate match is missing or ambiguous (more than 1 match),
--        replaces_card_id remains NULL to prevent guessing. Unresolvable legacy rows
--        are handled by the service fallback to tier catalog price.
--   4. Guarded by IF NOT EXISTS, GO separators, and EXEC sp_executesql for T-SQL
--      batch compile safety and idempotency.
-- ============================================================================

-- Step 1: Add upgrade_request column with default 0 if not exists
IF NOT EXISTS (
    SELECT 1 FROM sys.columns c
    JOIN sys.tables t ON t.object_id = c.object_id
    WHERE t.name = 'member_card' AND c.name = 'upgrade_request'
)
BEGIN
    ALTER TABLE [member_card]
    ADD [upgrade_request] BIT NOT NULL
        CONSTRAINT [df_member_card_upgrade_request] DEFAULT 0;
END;
GO

-- Step 2: Add replaces_card_id column and foreign key constraint if not exists
IF NOT EXISTS (
    SELECT 1 FROM sys.columns c
    JOIN sys.tables t ON t.object_id = c.object_id
    WHERE t.name = 'member_card' AND c.name = 'replaces_card_id'
)
BEGIN
    ALTER TABLE [member_card]
    ADD [replaces_card_id] BIGINT NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys fk
    JOIN sys.tables t ON t.object_id = fk.parent_object_id
    WHERE t.name = 'member_card' AND fk.name = 'fk_member_card_replaces_card'
)
BEGIN
    ALTER TABLE [member_card]
    ADD CONSTRAINT [fk_member_card_replaces_card]
        FOREIGN KEY ([replaces_card_id]) REFERENCES [member_card]([id]);
END;
GO

-- Step 3: Backfill legacy upgrade markers and deterministic replaces_card_id links
-- Execute dynamically to prevent compile-time column resolution errors in SQL Server
EXEC sp_executesql N'
    -- 3a. Mark legacy upgrade cards where price_paid was an upgrade difference
    UPDATE mc
    SET mc.[upgrade_request] = 1
    FROM [member_card] mc
    JOIN [membership_card_tier] t ON mc.[tier_id] = t.[id]
    WHERE mc.[status] IN (''ACTIVE'', ''PENDING_PAYMENT'')
      AND t.[duration_months] > 0
      AND mc.[price_paid] < t.[price]
      AND mc.[upgrade_request] = 0;

    -- 3b. Deterministically link ACTIVE legacy upgrades to their unique REPLACED card with identical end_date
    WITH SingleReplacedCard AS (
        SELECT
            mc.[id] AS upgrade_card_id,
            MIN(rc.[id]) AS candidate_card_id
        FROM [member_card] mc
        JOIN [member_card] rc
          ON rc.[member_id] = mc.[member_id]
         AND rc.[end_date] = mc.[end_date]
         AND rc.[status] = ''REPLACED''
         AND rc.[id] <> mc.[id]
        WHERE mc.[status] = ''ACTIVE''
          AND mc.[upgrade_request] = 1
          AND mc.[replaces_card_id] IS NULL
        GROUP BY mc.[id]
        HAVING COUNT(rc.[id]) = 1
    )
    UPDATE mc
    SET mc.[replaces_card_id] = s.candidate_card_id
    FROM [member_card] mc
    JOIN SingleReplacedCard s ON mc.[id] = s.upgrade_card_id;

    -- 3c. Deterministically link PENDING_PAYMENT legacy upgrades to the unique active paid card whose term started
    WITH SingleActiveCard AS (
        SELECT
            mc.[id] AS pending_card_id,
            MIN(ac.[id]) AS candidate_card_id
        FROM [member_card] mc
        JOIN [member_card] ac
          ON ac.[member_id] = mc.[member_id]
         AND ac.[status] = ''ACTIVE''
         AND ac.[id] <> mc.[id]
        JOIN [membership_card_tier] act
          ON ac.[tier_id] = act.[id]
         AND act.[duration_months] > 0
        WHERE mc.[status] = ''PENDING_PAYMENT''
          AND mc.[upgrade_request] = 1
          AND mc.[replaces_card_id] IS NULL
          AND (ac.[start_date] IS NULL OR ac.[start_date] <= CAST(GETDATE() AS DATE))
          AND (ac.[end_date] IS NULL OR ac.[end_date] >= CAST(GETDATE() AS DATE))
        GROUP BY mc.[id]
        HAVING COUNT(ac.[id]) = 1
    )
    UPDATE mc
    SET mc.[replaces_card_id] = s.candidate_card_id
    FROM [member_card] mc
    JOIN SingleActiveCard s ON mc.[id] = s.pending_card_id;
';
GO

-- Step 4: Post-condition validation
EXEC sp_executesql N'
    IF NOT EXISTS (
        SELECT 1 FROM sys.columns c
        JOIN sys.tables t ON t.object_id = c.object_id
        WHERE t.name = ''member_card'' AND c.name = ''upgrade_request''
    )
    BEGIN
        THROW 50001, ''Migration V22 verification failed: upgrade_request column missing on member_card'', 1;
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.columns c
        JOIN sys.tables t ON t.object_id = c.object_id
        WHERE t.name = ''member_card'' AND c.name = ''replaces_card_id''
    )
    BEGIN
        THROW 50001, ''Migration V22 verification failed: replaces_card_id column missing on member_card'', 1;
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys fk
        JOIN sys.tables t ON t.object_id = fk.parent_object_id
        WHERE t.name = ''member_card'' AND fk.name = ''fk_member_card_replaces_card''
    )
    BEGIN
        THROW 50001, ''Migration V22 verification failed: fk_member_card_replaces_card constraint missing'', 1;
    END;
';
GO
