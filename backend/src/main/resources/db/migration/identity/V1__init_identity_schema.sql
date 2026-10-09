-- V1__init_identity_schema.sql

-- 1. role table
CREATE TABLE role (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    code NVARCHAR(30) NOT NULL UNIQUE,
    name NVARCHAR(50) NOT NULL,
    description NVARCHAR(255) NULL,
    is_system BIT NOT NULL DEFAULT 1,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL
);

-- 2. permission table
CREATE TABLE permission (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    code NVARCHAR(60) NOT NULL UNIQUE,
    name NVARCHAR(100) NOT NULL,
    description NVARCHAR(255) NULL,
    module NVARCHAR(30) NOT NULL,
    display_order INT NOT NULL DEFAULT 0
);

-- 3. role_permission table
CREATE TABLE role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE
);

-- 4. user_account table
CREATE TABLE user_account (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    email NVARCHAR(255) NOT NULL UNIQUE,
    password_hash NVARCHAR(100) NOT NULL,
    full_name NVARCHAR(100) NOT NULL,
    phone NVARCHAR(20) NULL,
    date_of_birth DATE NULL,
    address NVARCHAR(255) NULL,
    avatar_url NVARCHAR(500) NULL,
    role_id BIGINT NOT NULL,
    status NVARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at DATETIME2(0) NULL,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_account_role FOREIGN KEY (role_id) REFERENCES role(id)
);

CREATE UNIQUE NONCLUSTERED INDEX ux_user_account_phone ON user_account(phone) WHERE phone IS NOT NULL;
CREATE INDEX ix_user_account_role_status ON user_account(role_id, status);
