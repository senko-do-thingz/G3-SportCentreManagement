-- V37__create_user_session_and_refresh_token.sql
-- Server-side auth session tracking, refresh token rotation, and replay detection.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'user_session')
BEGIN
    CREATE TABLE [user_session] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [session_id] NVARCHAR(64) NOT NULL,
        [user_id] BIGINT NOT NULL,
        [refresh_token_hash] NVARCHAR(64) NOT NULL,
        [created_at] DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
        [last_accessed_at] DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
        [expires_at] DATETIME2(0) NOT NULL,
        [revoked_at] DATETIME2(0) NULL,
        [revoked_reason] NVARCHAR(100) NULL,
        CONSTRAINT [pk_user_session] PRIMARY KEY ([id]),
        CONSTRAINT [fk_user_session_user] FOREIGN KEY ([user_id]) REFERENCES [user_account]([id]) ON DELETE CASCADE,
        CONSTRAINT [uq_user_session_id] UNIQUE ([session_id])
    );

    CREATE NONCLUSTERED INDEX [ix_user_session_user_active]
        ON [user_session] ([user_id], [revoked_at], [expires_at]);

    CREATE NONCLUSTERED INDEX [ix_user_session_refresh_hash]
        ON [user_session] ([refresh_token_hash]);
END;
