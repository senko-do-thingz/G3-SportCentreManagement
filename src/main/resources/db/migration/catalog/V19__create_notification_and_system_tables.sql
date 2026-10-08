-- ============================================================================
-- Migration: V19__create_notification_and_system_tables.sql
-- Description: Creates tables for system notifications, announcements,
--              operational settings, member sport interests, and coach sports.
-- ============================================================================

-- Step 1: Notifications and announcements
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'notification')
BEGIN
    CREATE TABLE [notification] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [user_id] BIGINT NOT NULL,
        [title] NVARCHAR(200) NOT NULL,
        [content] NVARCHAR(1000) NOT NULL,
        [type] VARCHAR(50) NOT NULL,
        [is_read] BIT NOT NULL CONSTRAINT [df_notification_is_read] DEFAULT 0,
        [created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_notification_created_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_notification] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_notification_user] FOREIGN KEY ([user_id]) REFERENCES [user_account]([id])
    );

    CREATE NONCLUSTERED INDEX [ix_notification_user_unread] ON [notification]([user_id], [is_read]);
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'announcement')
BEGIN
    CREATE TABLE [announcement] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [title] NVARCHAR(200) NOT NULL,
        [content] NVARCHAR(2000) NOT NULL,
        [target_role] VARCHAR(30) NULL,
        [is_published] BIT NOT NULL CONSTRAINT [df_announcement_published] DEFAULT 1,
        [published_at] DATETIME2(0) NOT NULL CONSTRAINT [df_announcement_published_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_announcement] PRIMARY KEY CLUSTERED ([id])
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'system_setting')
BEGIN
    CREATE TABLE [system_setting] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [setting_key] VARCHAR(100) NOT NULL,
        [setting_value] NVARCHAR(MAX) NOT NULL,
        [description] NVARCHAR(500) NULL,
        CONSTRAINT [pk_system_setting] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [uq_system_setting_key] UNIQUE ([setting_key])
    );
END;

-- Step 2: Multi-sport relationship tables
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'member_sport_interest')
BEGIN
    CREATE TABLE [member_sport_interest] (
        [member_id] BIGINT NOT NULL,
        [sport_id] BIGINT NOT NULL,
        CONSTRAINT [pk_member_sport_interest] PRIMARY KEY CLUSTERED ([member_id], [sport_id]),
        CONSTRAINT [fk_member_sport_interest_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id]),
        CONSTRAINT [fk_member_sport_interest_sport] FOREIGN KEY ([sport_id]) REFERENCES [sport]([id])
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'coach_sport')
BEGIN
    CREATE TABLE [coach_sport] (
        [coach_id] BIGINT NOT NULL,
        [sport_id] BIGINT NOT NULL,
        CONSTRAINT [pk_coach_sport] PRIMARY KEY CLUSTERED ([coach_id], [sport_id]),
        CONSTRAINT [fk_coach_sport_coach] FOREIGN KEY ([coach_id]) REFERENCES [coach_profile]([user_id]),
        CONSTRAINT [fk_coach_sport_sport] FOREIGN KEY ([sport_id]) REFERENCES [sport]([id])
    );
END;
