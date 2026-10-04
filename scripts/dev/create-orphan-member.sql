-- scripts/dev/create-orphan-member.sql
-- Local development only
-- Password is "password"

DECLARE @Email VARCHAR(255) = 'orphan1@example.com';

IF NOT EXISTS (SELECT 1 FROM [user_account] WHERE email = @Email)
BEGIN
    DECLARE @MemberRoleId BIGINT;
    SELECT @MemberRoleId = id FROM [role] WHERE code = 'MEMBER';

    INSERT INTO [user_account] (email, password_hash, full_name, phone, role_id, status)
    VALUES (@Email, '$2a$10$r04DnhAenmRQ8iLdET.LNOkQtSM0dcx/gGI1nT.toluFdfvhSbJBS', 'Orphan Member', NULL, @MemberRoleId, 'ACTIVE');
END

-- Verification
SELECT * FROM [user_account] WHERE email = @Email;
SELECT * FROM [member_profile] WHERE user_id = (SELECT id FROM [user_account] WHERE email = @Email);
