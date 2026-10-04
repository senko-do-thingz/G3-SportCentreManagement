-- V5__create_catalog_membership_tables.sql
-- Module 2: Catalog and Membership
-- Source of truth: docs/database/02-catalog-and-membership.md, docs/database/README.md (global conventions)
-- Enum columns use NVARCHAR + CHECK (README "Enums" convention).

-- ==========================================
-- 1. sport
-- ==========================================
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
    CONSTRAINT uq_sport_name UNIQUE (name),
    CONSTRAINT ck_sport_venue_type CHECK (venue_type IN ('INDOOR', 'OUTDOOR', 'POOL'))
);

-- ==========================================
-- 2. age_group
-- ==========================================
CREATE TABLE [age_group] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code NVARCHAR(30) NOT NULL,
    label NVARCHAR(50) NOT NULL,
    min_age INT NOT NULL,
    max_age INT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active BIT NOT NULL DEFAULT 1,
    CONSTRAINT pk_age_group PRIMARY KEY (id),
    CONSTRAINT uq_age_group_code UNIQUE (code),
    CONSTRAINT ck_age_group_min_age CHECK (min_age >= 0),
    CONSTRAINT ck_age_group_max_age CHECK (max_age IS NULL OR max_age >= min_age)
);

-- ==========================================
-- 3. facility
-- ==========================================
CREATE TABLE [facility] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code NVARCHAR(30) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    facility_type NVARCHAR(20) NOT NULL,
    sport_id BIGINT NULL,
    capacity INT NULL,
    is_active BIT NOT NULL DEFAULT 1,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    CONSTRAINT pk_facility PRIMARY KEY (id),
    CONSTRAINT uq_facility_code UNIQUE (code),
    CONSTRAINT fk_facility_sport FOREIGN KEY (sport_id) REFERENCES [sport](id),
    CONSTRAINT ck_facility_type CHECK (facility_type IN ('COURT', 'POOL', 'FIELD', 'HALL', 'STUDIO')),
    CONSTRAINT ck_facility_capacity CHECK (capacity IS NULL OR capacity > 0)
);

-- ==========================================
-- 4. membership_plan ("Membership packages")
-- ==========================================
CREATE TABLE [membership_plan] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code NVARCHAR(30) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    tagline NVARCHAR(100) NULL,
    description NVARCHAR(500) NULL,
    price DECIMAL(14,2) NOT NULL,
    duration_days INT NOT NULL,
    max_sports INT NOT NULL,
    is_featured BIT NOT NULL DEFAULT 0,
    status NVARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    created_by BIGINT NULL,   -- user id, no FK (README audit convention for master data)
    updated_by BIGINT NULL,   -- user id, no FK
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT pk_membership_plan PRIMARY KEY (id),
    CONSTRAINT uq_membership_plan_code UNIQUE (code),
    CONSTRAINT ck_membership_plan_price CHECK (price >= 0),
    CONSTRAINT ck_membership_plan_duration CHECK (duration_days > 0),
    CONSTRAINT ck_membership_plan_max_sports CHECK (max_sports >= 1),
    CONSTRAINT ck_membership_plan_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
);

-- ==========================================
-- 5. plan_eligible_sport (pool of sports a plan allows)
-- ==========================================
CREATE TABLE [plan_eligible_sport] (
    plan_id BIGINT NOT NULL,
    sport_id BIGINT NOT NULL,
    CONSTRAINT pk_plan_eligible_sport PRIMARY KEY (plan_id, sport_id),
    CONSTRAINT fk_pes_plan FOREIGN KEY (plan_id) REFERENCES [membership_plan](id) ON DELETE CASCADE,
    CONSTRAINT fk_pes_sport FOREIGN KEY (sport_id) REFERENCES [sport](id)
);

-- ==========================================
-- 6. plan_feature (bullet list on plan cards)
-- ==========================================
CREATE TABLE [plan_feature] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    plan_id BIGINT NOT NULL,
    feature_text NVARCHAR(150) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT pk_plan_feature PRIMARY KEY (id),
    CONSTRAINT fk_pf_plan FOREIGN KEY (plan_id) REFERENCES [membership_plan](id) ON DELETE CASCADE
);

-- ==========================================
-- 7. membership (one registration / membership period)
-- ==========================================
CREATE TABLE [membership] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    registration_code NVARCHAR(20) NOT NULL,
    member_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    registration_type NVARCHAR(20) NOT NULL,
    previous_membership_id BIGINT NULL,
    channel NVARCHAR(20) NOT NULL,
    status NVARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT',
    price_amount DECIMAL(14,2) NOT NULL,
    duration_days INT NOT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    activated_at DATETIME2(0) NULL,
    cancelled_at DATETIME2(0) NULL,
    cancel_reason NVARCHAR(255) NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT pk_membership PRIMARY KEY (id),
    CONSTRAINT uq_membership_registration_code UNIQUE (registration_code),
    CONSTRAINT fk_membership_member FOREIGN KEY (member_id) REFERENCES [member_profile](user_id),
    CONSTRAINT fk_membership_plan FOREIGN KEY (plan_id) REFERENCES [membership_plan](id),
    CONSTRAINT fk_membership_previous FOREIGN KEY (previous_membership_id) REFERENCES [membership](id),
    CONSTRAINT fk_membership_created_by FOREIGN KEY (created_by_user_id) REFERENCES [user_account](id),
    CONSTRAINT ck_membership_registration_type CHECK (registration_type IN ('NEW', 'RENEWAL')),
    CONSTRAINT ck_membership_channel CHECK (channel IN ('ONLINE', 'RECEPTION')),
    CONSTRAINT ck_membership_status CHECK (status IN ('PENDING_PAYMENT', 'SCHEDULED', 'ACTIVE', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_membership_price_amount CHECK (price_amount >= 0),
    CONSTRAINT ck_membership_duration CHECK (duration_days > 0),
    CONSTRAINT ck_membership_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_membership_active_dates CHECK (
        status NOT IN ('SCHEDULED', 'ACTIVE', 'EXPIRED')
        OR (start_date IS NOT NULL AND end_date IS NOT NULL)
    )
);

-- At most one pending registration per member (prevents duplicate charges, F3-02)
CREATE UNIQUE NONCLUSTERED INDEX ux_membership_one_pending
    ON [membership](member_id)
    WHERE status = 'PENDING_PAYMENT';

-- "Current membership" lookups
CREATE INDEX ix_membership_member_status ON [membership](member_id, status, end_date);

-- ==========================================
-- 8. membership_sport (sports selected for a membership period)
-- ==========================================
CREATE TABLE [membership_sport] (
    membership_id BIGINT NOT NULL,
    sport_id BIGINT NOT NULL,
    CONSTRAINT pk_membership_sport PRIMARY KEY (membership_id, sport_id),
    CONSTRAINT fk_ms_membership FOREIGN KEY (membership_id) REFERENCES [membership](id) ON DELETE CASCADE,
    CONSTRAINT fk_ms_sport FOREIGN KEY (sport_id) REFERENCES [sport](id)
);

-- ==========================================
-- 9. check_in (front desk arrival record, F1-13)
-- ==========================================
CREATE TABLE [check_in] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    member_id BIGINT NOT NULL,
    membership_id BIGINT NULL,
    checked_in_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    result NVARCHAR(20) NOT NULL,
    denial_reason NVARCHAR(255) NULL,
    recorded_by BIGINT NOT NULL,
    note NVARCHAR(255) NULL,
    CONSTRAINT pk_check_in PRIMARY KEY (id),
    CONSTRAINT fk_check_in_member FOREIGN KEY (member_id) REFERENCES [member_profile](user_id),
    CONSTRAINT fk_check_in_membership FOREIGN KEY (membership_id) REFERENCES [membership](id),
    CONSTRAINT fk_check_in_recorded_by FOREIGN KEY (recorded_by) REFERENCES [user_account](id),
    CONSTRAINT ck_check_in_result CHECK (result IN ('ALLOWED', 'DENIED'))
);

CREATE INDEX ix_check_in_member ON [check_in](member_id, checked_in_at);
CREATE INDEX ix_check_in_time ON [check_in](checked_in_at);
