-- V3__seed_identity_data.sql

-- Seed Roles if not exist
IF NOT EXISTS (SELECT 1 FROM [role] WHERE code = 'MEMBER')
BEGIN
    INSERT INTO [role] (code, name, description, is_system)
    VALUES ('MEMBER', 'Member', 'Standard user', 1);
END

IF NOT EXISTS (SELECT 1 FROM [role] WHERE code = 'COACH')
BEGIN
    INSERT INTO [role] (code, name, description, is_system)
    VALUES ('COACH', 'Coach', 'Class instructor', 1);
END

IF NOT EXISTS (SELECT 1 FROM [role] WHERE code = 'RECEPTIONIST')
BEGIN
    INSERT INTO [role] (code, name, description, is_system)
    VALUES ('RECEPTIONIST', 'Receptionist', 'Front desk staff', 1);
END

IF NOT EXISTS (SELECT 1 FROM [role] WHERE code = 'MANAGER')
BEGIN
    INSERT INTO [role] (code, name, description, is_system)
    VALUES ('MANAGER', 'Center Manager', 'Administrator', 1);
END

-- Seed Permissions (Selection)
IF NOT EXISTS (SELECT 1 FROM [permission] WHERE code = 'VIEW_OWN_PROFILE')
BEGIN
    INSERT INTO [permission] (code, name, module)
    VALUES ('VIEW_OWN_PROFILE', 'View own profile', 'PROFILE');
END

IF NOT EXISTS (SELECT 1 FROM [permission] WHERE code = 'REGISTER_RENEW_MEMBERSHIP')
BEGIN
    INSERT INTO [permission] (code, name, module)
    VALUES ('REGISTER_RENEW_MEMBERSHIP', 'Register and renew', 'MEMBERSHIP');
END

IF NOT EXISTS (SELECT 1 FROM [permission] WHERE code = 'BOOK_CLASS')
BEGIN
    INSERT INTO [permission] (code, name, module)
    VALUES ('BOOK_CLASS', 'Book class', 'CLASS');
END

IF NOT EXISTS (SELECT 1 FROM [permission] WHERE code = 'RECORD_CLASS_ATTENDANCE')
BEGIN
    INSERT INTO [permission] (code, name, module)
    VALUES ('RECORD_CLASS_ATTENDANCE', 'Record attendance', 'TRAINING');
END

IF NOT EXISTS (SELECT 1 FROM [permission] WHERE code = 'RECORD_PAYMENTS_INVOICES')
BEGIN
    INSERT INTO [permission] (code, name, module)
    VALUES ('RECORD_PAYMENTS_INVOICES', 'Record payments', 'PAYMENT');
END

IF NOT EXISTS (SELECT 1 FROM [permission] WHERE code = 'USE_WORKOUT_RECOMMENDATION')
BEGIN
    INSERT INTO [permission] (code, name, module)
    VALUES ('USE_WORKOUT_RECOMMENDATION', 'Use AI workout', 'AI');
END

IF NOT EXISTS (SELECT 1 FROM [permission] WHERE code = 'CREATE_SUPPORT_REQUEST')
BEGIN
    INSERT INTO [permission] (code, name, module)
    VALUES ('CREATE_SUPPORT_REQUEST', 'Create support request', 'SUPPORT');
END

-- Role-Permission Matrix (Example for MEMBER)
-- Assuming we want to insert them if they are not already there
INSERT INTO [role_permission] (role_id, permission_id)
SELECT r.id, p.id
FROM [role] r, [permission] p
WHERE r.code = 'MEMBER' 
  AND p.code IN ('VIEW_OWN_PROFILE', 'REGISTER_RENEW_MEMBERSHIP', 'BOOK_CLASS', 'USE_WORKOUT_RECOMMENDATION', 'CREATE_SUPPORT_REQUEST')
  AND NOT EXISTS (
      SELECT 1 FROM [role_permission] rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
