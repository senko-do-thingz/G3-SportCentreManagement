-- V4__seed_manager_account.sql
IF NOT EXISTS (SELECT 1 FROM [user_account] WHERE email = 'manager@sportify.com')
BEGIN
    DECLARE @ManagerRoleId BIGINT;
    SELECT @ManagerRoleId = id FROM [role] WHERE code = 'MANAGER';

    INSERT INTO [user_account] (email, password_hash, full_name, role_id, status)
    VALUES ('manager@sportify.com', '$2a$10$r04DnhAenmRQ8iLdET.LNOkQtSM0dcx/gGI1nT.toluFdfvhSbJBS', 'System Manager', @ManagerRoleId, 'ACTIVE');
END
