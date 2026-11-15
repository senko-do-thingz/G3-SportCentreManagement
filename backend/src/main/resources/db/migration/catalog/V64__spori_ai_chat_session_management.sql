-- ============================================================================
-- Migration: V64__spori_ai_chat_session_management.sql
-- Description: Adds is_archived, is_deleted, and updated_at columns to ai_conversation table.
-- ============================================================================

IF COL_LENGTH(N'dbo.ai_conversation', N'is_archived') IS NULL
BEGIN
    ALTER TABLE [dbo].[ai_conversation]
    ADD [is_archived] BIT NOT NULL CONSTRAINT [df_ai_conv_is_archived] DEFAULT 0;
END;

IF COL_LENGTH(N'dbo.ai_conversation', N'is_deleted') IS NULL
BEGIN
    ALTER TABLE [dbo].[ai_conversation]
    ADD [is_deleted] BIT NOT NULL CONSTRAINT [df_ai_conv_is_deleted] DEFAULT 0;
END;

IF COL_LENGTH(N'dbo.ai_conversation', N'updated_at') IS NULL
BEGIN
    ALTER TABLE [dbo].[ai_conversation]
    ADD [updated_at] DATETIME2(0) NOT NULL CONSTRAINT [df_ai_conv_updated_at] DEFAULT SYSDATETIME();
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'ix_ai_conv_user_status'
      AND object_id = OBJECT_ID(N'dbo.ai_conversation', N'U')
)
BEGIN
    CREATE NONCLUSTERED INDEX [ix_ai_conv_user_status]
        ON [dbo].[ai_conversation] ([user_id], [is_deleted], [is_archived], [updated_at] DESC);
END;
