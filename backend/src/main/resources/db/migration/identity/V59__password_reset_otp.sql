-- SQL Server compiles a batch before running it, so the new column must exist before the filtered index is created.
ALTER TABLE dbo.password_reset_token ADD failed_attempts int NOT NULL CONSTRAINT df_password_reset_failed_attempts DEFAULT 0;
ALTER TABLE dbo.password_reset_token ADD reset_token_hash varchar(64) NULL;
GO
CREATE UNIQUE INDEX uq_password_reset_authorization ON dbo.password_reset_token(reset_token_hash) WHERE reset_token_hash IS NOT NULL;
