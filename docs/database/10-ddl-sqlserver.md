# 10 - DDL Script (SQL Server)

> [!NOTE]
> **Superseded by V1-V11:** The DDL and seed scripts below represent initial architectural drafts (`V1__init_schema.sql` and `V2__seed_reference_data.sql`).
> The active production database schema is managed modularly in `src/main/resources/db/migration/`:
> - Identity & Core: `identity/V1__init_identity_schema.sql`, `identity/V2__add_identity_tables.sql`, `identity/V3__seed_identity_data.sql`, `identity/V4__seed_manager_account.sql`
> - Catalog, Membership, Booking & Refunds: `catalog/V5__create_catalog_membership_tables.sql`, `catalog/V6__seed_catalog_data.sql`, `catalog/V9__add_registration_sequence.sql`, `catalog/V10__context_refresh_schema.sql`, `catalog/V11__add_refunded_status.sql`
> The monolithic `membership_plan` (with `max_sports`) and legacy booking definitions below are superseded by the refreshed `sport_package`, `membership_card_tier`, `member_card`, and V10 `booking` schema.

This document provides the historical SQL Server DDL and seed data drafts.
It assumes `SET ANSI_NULLS ON` and `SET QUOTED_IDENTIFIER ON`.

## V1__init_schema.sql (Initial Draft - Superseded by V1-V11)

```sql
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

-- ==========================================
-- Sequences
-- ==========================================

CREATE SEQUENCE seq_member_code START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_registration_code START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE seq_payment_code START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE seq_invoice_number START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE seq_booking_code START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE seq_support_request_code START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE seq_class_code START WITH 100 INCREMENT BY 1;
CREATE SEQUENCE seq_workout_plan_code START WITH 1 INCREMENT BY 1;

-- ==========================================
-- 01. Identity and Access
-- ==========================================

CREATE TABLE [role] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code NVARCHAR(30) NOT NULL,
    name NVARCHAR(50) NOT NULL,
    description NVARCHAR(255) NULL,
    is_system BIT NOT NULL DEFAULT 1,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    CONSTRAINT pk_role PRIMARY KEY (id),
    CONSTRAINT uq_role_code UNIQUE (code)
);

CREATE TABLE [permission] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code NVARCHAR(60) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    description NVARCHAR(255) NULL,
    module NVARCHAR(30) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT pk_permission PRIMARY KEY (id),
    CONSTRAINT uq_permission_code UNIQUE (code)
);

CREATE TABLE [role_permission] (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    CONSTRAINT pk_role_permission PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES [role](id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES [permission](id) ON DELETE CASCADE
);

CREATE TABLE [user_account] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    email NVARCHAR(255) NOT NULL,
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
    CONSTRAINT pk_user_account PRIMARY KEY (id),
    CONSTRAINT uq_user_account_email UNIQUE (email),
    CONSTRAINT fk_user_account_role FOREIGN KEY (role_id) REFERENCES [role](id)
);

CREATE UNIQUE NONCLUSTERED INDEX ux_user_account_phone ON [user_account](phone) WHERE phone IS NOT NULL;
CREATE INDEX ix_user_account_role_status ON [user_account](role_id, status);

CREATE TABLE [member_profile] (
    user_id BIGINT NOT NULL,
    member_code NVARCHAR(20) NOT NULL,
    main_goal NVARCHAR(30) NULL,
    current_level NVARCHAR(20) NOT NULL DEFAULT 'BEGINNER',
    joined_on DATE NOT NULL DEFAULT CAST(SYSDATETIME() AS DATE),
    staff_note NVARCHAR(500) NULL,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    CONSTRAINT pk_member_profile PRIMARY KEY (user_id),
    CONSTRAINT fk_member_profile_user FOREIGN KEY (user_id) REFERENCES [user_account](id) ON DELETE CASCADE,
    CONSTRAINT uq_member_profile_code UNIQUE (member_code)
);

CREATE TABLE [sport] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code NVARCHAR(30) NOT NULL,
    name NVARCHAR(50) NOT NULL,
    venue_type NVARCHAR(20) NOT NULL,
    description NVARCHAR(500) NULL,
    image_url NVARCHAR(500) NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active BIT NOT NULL DEFAULT 1,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    CONSTRAINT pk_sport PRIMARY KEY (id),
    CONSTRAINT uq_sport_code UNIQUE (code),
    CONSTRAINT uq_sport_name UNIQUE (name)
);

CREATE TABLE [member_sport_interest] (
    member_id BIGINT NOT NULL,
    sport_id BIGINT NOT NULL,
    CONSTRAINT pk_member_sport_interest PRIMARY KEY (member_id, sport_id),
    CONSTRAINT fk_msi_member FOREIGN KEY (member_id) REFERENCES [member_profile](user_id) ON DELETE CASCADE,
    CONSTRAINT fk_msi_sport FOREIGN KEY (sport_id) REFERENCES [sport](id)
);

CREATE TABLE [coach_profile] (
    user_id BIGINT NOT NULL,
    primary_sport_id BIGINT NULL,
    headline NVARCHAR(150) NULL,
    bio NVARCHAR(1000) NULL,
    years_experience INT NULL,
    specialties NVARCHAR(255) NULL,
    is_public BIT NOT NULL DEFAULT 1,
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    CONSTRAINT pk_coach_profile PRIMARY KEY (user_id),
    CONSTRAINT fk_coach_profile_user FOREIGN KEY (user_id) REFERENCES [user_account](id) ON DELETE CASCADE,
    CONSTRAINT fk_coach_profile_sport FOREIGN KEY (primary_sport_id) REFERENCES [sport](id),
    CONSTRAINT ck_coach_exp CHECK (years_experience >= 0)
);

CREATE TABLE [coach_sport] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    coach_id BIGINT NOT NULL,
    sport_id BIGINT NOT NULL,
    max_level NVARCHAR(20) NULL,
    CONSTRAINT pk_coach_sport PRIMARY KEY (id),
    CONSTRAINT fk_cs_coach FOREIGN KEY (coach_id) REFERENCES [coach_profile](user_id) ON DELETE CASCADE,
    CONSTRAINT fk_cs_sport FOREIGN KEY (sport_id) REFERENCES [sport](id),
    CONSTRAINT uq_coach_sport UNIQUE (coach_id, sport_id)
);

CREATE TABLE [coach_certification] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    coach_id BIGINT NOT NULL,
    sport_id BIGINT NULL,
    name NVARCHAR(150) NOT NULL,
    issuer NVARCHAR(150) NULL,
    issued_on DATE NULL,
    expires_on DATE NULL,
    document_url NVARCHAR(500) NULL,
    is_verified BIT NOT NULL DEFAULT 0,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT pk_coach_certification PRIMARY KEY (id),
    CONSTRAINT fk_cc_coach FOREIGN KEY (coach_id) REFERENCES [coach_profile](user_id) ON DELETE CASCADE,
    CONSTRAINT fk_cc_sport FOREIGN KEY (sport_id) REFERENCES [sport](id),
    CONSTRAINT ck_cc_dates CHECK (expires_on IS NULL OR issued_on IS NULL OR expires_on >= issued_on)
);

CREATE TABLE [password_reset_token] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash NVARCHAR(100) NOT NULL,
    expires_at DATETIME2(0) NOT NULL,
    used_at DATETIME2(0) NULL,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT pk_password_reset_token PRIMARY KEY (id),
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES [user_account](id) ON DELETE CASCADE,
    CONSTRAINT uq_prt_hash UNIQUE (token_hash)
);

CREATE TABLE [activity_log] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    actor_user_id BIGINT NULL,
    action NVARCHAR(60) NOT NULL,
    entity_type NVARCHAR(40) NOT NULL,
    entity_id BIGINT NULL,
    entity_code NVARCHAR(40) NULL,
    summary NVARCHAR(255) NULL,
    details NVARCHAR(MAX) NULL,
    ip_address NVARCHAR(45) NULL,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT pk_activity_log PRIMARY KEY (id),
    CONSTRAINT fk_al_actor FOREIGN KEY (actor_user_id) REFERENCES [user_account](id),
    CONSTRAINT ck_al_details_json CHECK (details IS NULL OR ISJSON(details) = 1)
);

CREATE INDEX ix_activity_log_created ON [activity_log](created_at DESC);
CREATE INDEX ix_activity_log_entity ON [activity_log](entity_type, entity_id);
CREATE INDEX ix_activity_log_actor ON [activity_log](actor_user_id, created_at);

-- (Similar definitions follow for Module 2-8 tables, abbreviated here for length...
-- Refer to individual module files for full column specs.)

```

## V2__seed_reference_data.sql

```sql
-- Seed Roles
INSERT INTO [role] (code, name, description, is_system)
VALUES 
('MEMBER', 'Member', 'Standard user', 1),
('COACH', 'Coach', 'Class instructor', 1),
('RECEPTIONIST', 'Receptionist', 'Front desk staff', 1),
('MANAGER', 'Center Manager', 'Administrator', 1);

-- Seed Permissions (Selection)
INSERT INTO [permission] (code, name, module)
VALUES 
('VIEW_OWN_PROFILE', 'View own profile', 'PROFILE'),
('REGISTER_RENEW_MEMBERSHIP', 'Register and renew', 'MEMBERSHIP'),
('BOOK_CLASS', 'Book class', 'CLASS'),
('RECORD_CLASS_ATTENDANCE', 'Record attendance', 'TRAINING'),
('RECORD_PAYMENTS_INVOICES', 'Record payments', 'PAYMENT'),
('USE_WORKOUT_RECOMMENDATION', 'Use AI workout', 'AI'),
('CREATE_SUPPORT_REQUEST', 'Create support request', 'SUPPORT');

-- Role-Permission Matrix (Example for MEMBER)
INSERT INTO [role_permission] (role_id, permission_id)
SELECT r.id, p.id
FROM [role] r, [permission] p
WHERE r.code = 'MEMBER' 
  AND p.code IN ('VIEW_OWN_PROFILE', 'REGISTER_RENEW_MEMBERSHIP', 'BOOK_CLASS', 'USE_WORKOUT_RECOMMENDATION', 'CREATE_SUPPORT_REQUEST');

-- Seed Sports
INSERT INTO [sport] (code, name, venue_type)
VALUES
('FOOTBALL', 'Football', 'OUTDOOR'),
('BADMINTON', 'Badminton', 'INDOOR'),
('BASKETBALL', 'Basketball', 'INDOOR'),
('VOLLEYBALL', 'Volleyball', 'INDOOR'),
('SWIMMING', 'Swimming', 'POOL'),
('TENNIS', 'Tennis', 'OUTDOOR');

-- Seed Age Groups
INSERT INTO [age_group] (code, label, min_age, max_age, display_order)
VALUES
('KIDS_6_12', 'Kids 6-12', 6, 12, 1),
('AGES_8_11', 'Ages 8-11', 8, 11, 2),
('TEENS_13_17', 'Teens 13-17', 13, 17, 3),
('AGES_16_PLUS', 'Ages 16+', 16, NULL, 4),
('ADULTS_18_PLUS', 'Adults 18+', 18, NULL, 5);

-- Seed Membership Plans (Superseded by V10 sport_package & membership_card_tier seed)
INSERT INTO [membership_plan] (code, name, price, duration_days, max_sports, is_featured, status)
VALUES
('STARTER', 'Starter', 300000.00, 30, 1, 0, 'ACTIVE'),
('MULTI_SPORT', 'Multi-Sport', 550000.00, 30, 3, 1, 'ACTIVE'),
('ALL_ACCESS', 'All Access', 800000.00, 30, 6, 0, 'ACTIVE'),
('SWIM_STARTER', 'Swim Starter', 250000.00, 30, 1, 0, 'ACTIVE'),
('BASKETBALL_PASS', 'Basketball Pass', 280000.00, 30, 1, 0, 'ACTIVE');

-- Link all sports to general plans (Starter, Multi-Sport, All Access)
INSERT INTO [plan_eligible_sport] (plan_id, sport_id)
SELECT p.id, s.id
FROM [membership_plan] p, [sport] s
WHERE p.code IN ('STARTER', 'MULTI_SPORT', 'ALL_ACCESS');

-- Link specific sports
INSERT INTO [plan_eligible_sport] (plan_id, sport_id)
SELECT p.id, s.id FROM [membership_plan] p, [sport] s WHERE p.code = 'SWIM_STARTER' AND s.code = 'SWIMMING';

INSERT INTO [plan_eligible_sport] (plan_id, sport_id)
SELECT p.id, s.id FROM [membership_plan] p, [sport] s WHERE p.code = 'BASKETBALL_PASS' AND s.code = 'BASKETBALL';

-- Seed System Settings
INSERT INTO [system_setting] (setting_key, setting_value, value_type)
VALUES
('CENTER_NAME', 'Sportify Center', 'STRING'),
('OPENING_HOURS', '06:00-22:00', 'STRING'),
('CURRENCY', 'VND', 'STRING'),
('TIMEZONE', 'Asia/Ho_Chi_Minh', 'STRING'),
('BOOKING_CANCEL_CUTOFF_HOURS', '2', 'INTEGER'),
('WAITLIST_OFFER_HOURS', '12', 'INTEGER'),
('MEMBERSHIP_EXPIRY_REMINDER_DAYS', '5', 'INTEGER');

-- Seed AI Assistant Settings
INSERT INTO [assistant_setting] (id, welcome_message, escalation_enabled, clarification_enabled, fallback_message, ai_model)
VALUES (1, 'Hello! I am your Sportify AI assistant. How can I help you today?', 1, 1, 'I did not understand that. Would you like to speak to staff?', 'gpt-4o');

```
