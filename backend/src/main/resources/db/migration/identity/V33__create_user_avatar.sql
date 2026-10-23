-- ==========================================
-- Uploaded profile photos (manager "Upload photo" for coaches).
-- One photo per account, stored in the database so no file server or shared folder is needed.
-- user_account.avatar_url points to GET /api/v1/users/{id}/avatar?v=<upload time> when a photo is uploaded.
-- ==========================================
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'user_avatar')
BEGIN
    CREATE TABLE [user_avatar] (
        user_id BIGINT NOT NULL,
        content_type NVARCHAR(30) NOT NULL,
        size_bytes INT NOT NULL,
        data VARBINARY(MAX) NOT NULL,
        updated_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
        CONSTRAINT pk_user_avatar PRIMARY KEY (user_id),
        CONSTRAINT fk_user_avatar_user FOREIGN KEY (user_id) REFERENCES [user_account](id) ON DELETE CASCADE,
        CONSTRAINT ck_user_avatar_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
        CONSTRAINT ck_user_avatar_size CHECK (size_bytes > 0 AND size_bytes <= 2097152)
    );
END;
