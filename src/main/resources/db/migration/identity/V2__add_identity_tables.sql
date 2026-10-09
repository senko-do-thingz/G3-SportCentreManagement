-- V2__add_identity_tables.sql

-- 1. member_profile
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

-- 2. coach_profile
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
    CONSTRAINT ck_coach_exp CHECK (years_experience >= 0)
    -- fk_coach_profile_sport is omitted because sport table is in catalog module
);

-- 3. coach_certification
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
    CONSTRAINT ck_cc_dates CHECK (expires_on IS NULL OR issued_on IS NULL OR expires_on >= issued_on)
    -- fk_cc_sport is omitted because sport table is in catalog module
);

-- 4. password_reset_token
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

-- 5. activity_log
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

-- Sequences
CREATE SEQUENCE seq_member_code START WITH 1 INCREMENT BY 1;
