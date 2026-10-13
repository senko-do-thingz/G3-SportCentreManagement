-- ============================================================================
-- Migration: V22__add_upgrade_tracking_to_member_card.sql
-- Description:
--   1. Add upgrade_request BIT column to member_card to explicitly record whether
--      a card request was made as an upgrade (instead of inferring from live tier price).
--   2. Add replaces_card_id BIGINT column referencing member_card(id) to record
--      which active card is being upgraded/replaced.
--   3. Add tier_rank column to membership_card_tier to decouple tier hierarchy
--      from live promotional prices (STANDARD=1, SILVER=2, GOLD=3, VIP=4).
--   4. Add unique filtered index on replaces_card_id for in-force cards to guarantee
--      that no two in-force cards can concurrently replace the same card.
--   5. Idempotently backfill existing cards and deterministic replacement links:
--      - For existing cards (ACTIVE, PENDING_PAYMENT, or REPLACED) where tier.duration_months > 0
--        and price_paid < tier.price (legacy upgrade heuristic), set upgrade_request = 1.
--      - For legacy upgrades in a chain (including A -> B -> C), link each upgrade card
--        to the immediately preceding REPLACED card for the same member sharing the exact end_date.
--      - For PENDING_PAYMENT legacy upgrades where replaces_card_id IS NULL: link to
--        the member's unique current ACTIVE paid card whose term has started.
--      - Ambiguous or missing matches remain NULL and fail closed upon subsequent upgrade attempts.
--   6. Guarded by IF NOT EXISTS, GO separators, and EXEC sp_executesql for T-SQL
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

-- Step 3: Add tier_rank column to membership_card_tier and backfill ranks
IF NOT EXISTS (
    SELECT 1 FROM sys.columns c
    JOIN sys.tables t ON t.object_id = c.object_id
    WHERE t.name = 'membership_card_tier' AND c.name = 'tier_rank'
)
BEGIN
    ALTER TABLE [membership_card_tier]
    ADD [tier_rank] INT NOT NULL
        CONSTRAINT [df_membership_card_tier_rank] DEFAULT 0;
END;
GO

-- Add CHECK constraint on tier_rank >= 0
IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints cc
    JOIN sys.tables t ON t.object_id = cc.parent_object_id
    WHERE t.name = 'membership_card_tier' AND cc.name = 'chk_membership_card_tier_rank'
)
BEGIN
    ALTER TABLE [membership_card_tier]
    ADD CONSTRAINT [chk_membership_card_tier_rank] CHECK ([tier_rank] >= 0);
END;
GO

-- Add unique filtered index on membership_card_tier(tier_rank) WHERE tier_rank > 0
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes i
    JOIN sys.tables t ON t.object_id = i.object_id
    WHERE t.name = 'membership_card_tier' AND i.name = 'ux_membership_card_tier_rank'
)
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX ux_membership_card_tier_rank
    ON [membership_card_tier] (tier_rank)
    WHERE tier_rank > 0;
END;
GO

-- Seed tier_rank for existing known tiers if 0 (STANDARD=1, SILVER=2, GOLD=3, VIP=4)
UPDATE [membership_card_tier] SET [tier_rank] = 1 WHERE [code] = 'STANDARD' AND [tier_rank] = 0;
UPDATE [membership_card_tier] SET [tier_rank] = 2 WHERE [code] = 'SILVER' AND [tier_rank] = 0;
UPDATE [membership_card_tier] SET [tier_rank] = 3 WHERE [code] = 'GOLD' AND [tier_rank] = 0;
UPDATE [membership_card_tier] SET [tier_rank] = 4 WHERE [code] = 'VIP' AND [tier_rank] = 0;
GO

-- Step 4: Backfill legacy upgrade markers and deterministic replaces_card_id links
-- Execute dynamically to prevent compile-time column resolution errors in SQL Server
EXEC sp_executesql N'
    -- 4a. Best-effort heuristic: mark legacy upgrade cards where price_paid was an upgrade difference (< tier.price).
    -- Rows where replaces_card_id cannot be deterministically resolved remain NULL and fail closed on subsequent upgrade.
    UPDATE mc
    SET mc.[upgrade_request] = 1
    FROM [member_card] mc
    JOIN [membership_card_tier] t ON mc.[tier_id] = t.[id]
    WHERE mc.[status] IN (''ACTIVE'', ''PENDING_PAYMENT'', ''REPLACED'')
      AND t.[duration_months] > 0
      AND mc.[price_paid] < t.[price]
      AND mc.[upgrade_request] = 0;

    -- 4b. Deterministically link upgrade cards in an upgrade chain (including A -> B -> C).
    -- Each upgrade card is linked ONLY when exactly one eligible predecessor candidate exists.
    -- Candidates already referenced as replaces_card_id are excluded so deeper chains (A -> B -> C)
    -- resolve unambiguously without mislinking ambiguous multiple predecessors.
    -- If zero or several candidates exist, replaces_card_id remains NULL (fail-closed).
    DECLARE @chain_iter INT = 0;
    DECLARE @chain_rows INT = 1;
    WHILE @chain_rows > 0 AND @chain_iter < 20
    BEGIN
        SET @chain_iter = @chain_iter + 1;

        WITH SinglePredecessor AS (
            SELECT
                mc.[id] AS upgrade_card_id,
                MIN(rc.[id]) AS candidate_card_id
            FROM [member_card] mc
            JOIN [member_card] rc
              ON rc.[member_id] = mc.[member_id]
             AND rc.[end_date] = mc.[end_date]
             AND rc.[status] = ''REPLACED''
             AND rc.[id] < mc.[id]
            WHERE mc.[status] IN (''ACTIVE'', ''REPLACED'')
              AND mc.[upgrade_request] = 1
              AND mc.[replaces_card_id] IS NULL
              AND rc.[id] NOT IN (
                  SELECT [replaces_card_id]
                  FROM [member_card]
                  WHERE [replaces_card_id] IS NOT NULL
              )
            GROUP BY mc.[id]
            HAVING COUNT(rc.[id]) = 1
        )
        UPDATE mc
        SET mc.[replaces_card_id] = sp.candidate_card_id
        FROM [member_card] mc
        JOIN SinglePredecessor sp ON mc.[id] = sp.upgrade_card_id;

        SET @chain_rows = @@ROWCOUNT;
    END;

    -- 4c. Deterministically link PENDING_PAYMENT legacy upgrades to the unique active paid card whose term started
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
          AND (ac.[start_date] IS NULL OR ac.[start_date] <= mc.[start_date])
          AND (ac.[end_date] IS NULL OR ac.[end_date] >= mc.[start_date])
        GROUP BY mc.[id]
        HAVING COUNT(ac.[id]) = 1
    )
    UPDATE mc
    SET mc.[replaces_card_id] = s.candidate_card_id
    FROM [member_card] mc
    JOIN SingleActiveCard s ON mc.[id] = s.pending_card_id;
';
GO

-- Step 5: Clean up / fail-safe pre-check: ensure no duplicate replaces_card_id exist on in-force cards before index creation
DECLARE @cleared_count INT = 0;
IF EXISTS (
    SELECT replaces_card_id
    FROM [member_card]
    WHERE [status] IN ('ACTIVE', 'PENDING_PAYMENT') AND [replaces_card_id] IS NOT NULL
    GROUP BY [replaces_card_id]
    HAVING COUNT(*) > 1
)
BEGIN
    WITH DuplicateInForce AS (
        SELECT [id], ROW_NUMBER() OVER (PARTITION BY [replaces_card_id] ORDER BY [id] DESC) AS rn
        FROM [member_card]
        WHERE [status] IN ('ACTIVE', 'PENDING_PAYMENT') AND [replaces_card_id] IS NOT NULL
    )
    UPDATE [member_card]
    SET [replaces_card_id] = NULL
    WHERE [id] IN (SELECT [id] FROM DuplicateInForce WHERE rn > 1);

    SET @cleared_count = @@ROWCOUNT;
END;

-- Add unique filtered index on replaces_card_id for in-force cards
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes i
    JOIN sys.tables t ON t.object_id = i.object_id
    WHERE t.name = 'member_card' AND i.name = 'ux_member_card_replaces_in_force'
)
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX ux_member_card_replaces_in_force
    ON [member_card] (replaces_card_id)
    WHERE status IN ('ACTIVE', 'PENDING_PAYMENT') AND replaces_card_id IS NOT NULL;
END;

-- Step 6: Post-condition validation
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

    IF NOT EXISTS (
        SELECT 1 FROM sys.columns c
        JOIN sys.tables t ON t.object_id = c.object_id
        WHERE t.name = ''membership_card_tier'' AND c.name = ''tier_rank''
    )
    BEGIN
        THROW 50001, ''Migration V22 verification failed: tier_rank column missing on membership_card_tier'', 1;
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.check_constraints cc
        JOIN sys.tables t ON t.object_id = cc.parent_object_id
        WHERE t.name = ''membership_card_tier'' AND cc.name = ''chk_membership_card_tier_rank''
    )
    BEGIN
        THROW 50001, ''Migration V22 verification failed: chk_membership_card_tier_rank constraint missing'', 1;
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        JOIN sys.tables t ON t.object_id = i.object_id
        WHERE t.name = ''membership_card_tier'' AND i.name = ''ux_membership_card_tier_rank''
    )
    BEGIN
        THROW 50001, ''Migration V22 verification failed: ux_membership_card_tier_rank index missing'', 1;
    END;

    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        JOIN sys.tables t ON t.object_id = i.object_id
        WHERE t.name = ''member_card'' AND i.name = ''ux_member_card_replaces_in_force''
    )
    BEGIN
        THROW 50001, ''Migration V22 verification failed: ux_member_card_replaces_in_force index missing'', 1;
    END;
';

-- Step 7: Diagnostics output for operator visibility
DECLARE @unresolved_count INT;
SELECT @unresolved_count = COUNT(*)
FROM [member_card]
WHERE [upgrade_request] = 1 AND [replaces_card_id] IS NULL;

PRINT 'Migration V22 diagnostics:';
PRINT '  - Upgrade cards with NULL replaces_card_id (unresolved baseline): ' + CAST(@unresolved_count AS VARCHAR(10));
PRINT '  - Duplicate in-force links cleared by Step 5: ' + CAST(@cleared_count AS VARCHAR(10));
GO
