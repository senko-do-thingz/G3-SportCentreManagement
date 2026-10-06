-- ============================================================================
-- Migration: V13__add_session_minutes_to_sport_package.sql
-- Description: Add session_minutes column to sport_package table and backfill
--              duration for all 36 Figma sport packages (60 min self-training,
--              90 min coach-led).
-- ============================================================================

-- Step 1: Add session_minutes column with default 60 and allowed values check
IF NOT EXISTS (
    SELECT 1 FROM sys.columns c
    JOIN sys.tables t ON t.object_id = c.object_id
    WHERE t.name = 'sport_package' AND c.name = 'session_minutes'
)
BEGIN
    ALTER TABLE [sport_package]
    ADD [session_minutes] SMALLINT NOT NULL
        CONSTRAINT [df_sport_package_session_minutes] DEFAULT 60
        CONSTRAINT [ck_sport_package_session_minutes] CHECK ([session_minutes] IN (60, 90));
END;
GO

-- Step 2: Backfill session_minutes to match Figma screens F1-02
-- Execute dynamically to prevent compile-time column resolution errors in SQL Server
EXEC sp_executesql N'
    UPDATE [sport_package]
    SET [session_minutes] = 60
    WHERE [training_format] = ''SELF_TRAINING'';

    UPDATE [sport_package]
    SET [session_minutes] = 90
    WHERE [training_format] = ''COACH_LED'';
';
GO

-- Step 3: Post-condition validation
EXEC sp_executesql N'
    DECLARE @self_60_count INT;
    SELECT @self_60_count = COUNT(*)
    FROM [sport_package]
    WHERE [training_format] = ''SELF_TRAINING'' AND [session_minutes] = 60;

    IF @self_60_count <> 18
    BEGIN
        THROW 50001, ''Migration V13 verification failed: expected 18 self-training packages with session_minutes = 60'', 1;
    END;

    DECLARE @coach_90_count INT;
    SELECT @coach_90_count = COUNT(*)
    FROM [sport_package]
    WHERE [training_format] = ''COACH_LED'' AND [session_minutes] = 90;

    IF @coach_90_count <> 18
    BEGIN
        THROW 50001, ''Migration V13 verification failed: expected 18 coach-led packages with session_minutes = 90'', 1;
    END;
';
GO
