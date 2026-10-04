-- scripts/dev/create-orphan-member.sql
-- Local development only: create an orphan account (MEMBER role without a member_profile row)
-- to test lazy profile creation upon membership registration.
-- Idempotent, uses NOT EXISTS.

IF NOT EXISTS (SELECT 1 FROM [user_account] WHERE email = 'orphan1@example.com')
BEGIN
    DECLARE @MemberRoleId BIGINT;
    SELECT @MemberRoleId = id FROM [role] WHERE code = 'MEMBER';

    INSERT INTO [user_account] (email, password_hash, full_name, phone, role_id, status)
    VALUES ('orphan1@example.com', '$2a$10$r04DnhAenmRQ8iLdET.LNOkQtSM0dcx/gGI1nT.toluFdfvhSbJBS', 'Orphan Member', '0999999999', @MemberRoleId, 'ACTIVE');
END

-- Verification
SELECT * FROM [user_account] WHERE email = 'orphan1@example.com';
SELECT * FROM [member_profile] WHERE user_id = (SELECT id FROM [user_account] WHERE email = 'orphan1@example.com');
