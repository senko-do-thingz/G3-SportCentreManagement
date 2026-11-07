-- Single-use refresh token rotation persistence (DEF-001 / NEW-005)
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'user_refresh_token')
BEGIN
    CREATE TABLE [user_refresh_token] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [user_id] BIGINT NOT NULL,
        [token_hash] VARCHAR(64) NOT NULL,
        [expires_at] DATETIME2 NOT NULL,
        [consumed_at] DATETIME2 NULL,
        [revoked_at] DATETIME2 NULL,
        [created_at] DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT [pk_user_refresh_token] PRIMARY KEY ([id]),
        CONSTRAINT [fk_user_refresh_token_user] FOREIGN KEY ([user_id]) REFERENCES [user_account]([id]) ON DELETE CASCADE,
        CONSTRAINT [uq_user_refresh_token_hash] UNIQUE ([token_hash])
    );

    CREATE INDEX [idx_user_refresh_token_user_id] ON [user_refresh_token]([user_id]);
    CREATE INDEX [idx_user_refresh_token_expires_at] ON [user_refresh_token]([expires_at]);
END;
