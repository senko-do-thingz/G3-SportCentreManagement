-- V10__context_refresh_schema.sql
-- Module: Catalog, Membership Cards, Sport Packages, Sessions, Bookings, and Refunds
-- Implements Refreshed Context (10/05/2026) Architecture:
-- 1. Membership Card Tiers & Member Cards (Discount system)
-- 2. Sport Packages & Sport Package Registrations (Multi-package model)
-- 3. Sport Classes, Class Sessions (Coach-led and Self-training) & Bookings
-- 4. Refund Requests workflow
-- 5. Check-in alignment with today confirmed bookings

-- ==========================================
-- 1. Sequences
-- ==========================================
CREATE SEQUENCE seq_card_code START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE seq_package_reg_code START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE seq_refund_code START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE seq_booking_code START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE seq_class_code START WITH 1000 INCREMENT BY 1;

-- ==========================================
-- 2. membership_card_tier
-- ==========================================
CREATE TABLE [membership_card_tier] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code NVARCHAR(30) NOT NULL,
    name NVARCHAR(50) NOT NULL,
    price DECIMAL(14,2) NOT NULL DEFAULT 0,
    duration_months INT NOT NULL DEFAULT 0,
    discount_percentage INT NOT NULL DEFAULT 0,
    description NVARCHAR(500) NULL,
    is_active BIT NOT NULL DEFAULT 1,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    CONSTRAINT pk_membership_card_tier PRIMARY KEY (id),
    CONSTRAINT uq_membership_card_tier_code UNIQUE (code),
    CONSTRAINT ck_membership_card_tier_price CHECK (price >= 0),
    CONSTRAINT ck_membership_card_tier_duration CHECK (duration_months >= 0),
    CONSTRAINT ck_membership_card_tier_discount CHECK (discount_percentage >= 0 AND discount_percentage <= 100)
);

INSERT INTO [membership_card_tier] (code, name, price, duration_months, discount_percentage, description, is_active)
VALUES
('STANDARD', 'Standard', 0.00, 0, 0, 'Standard membership card. Free permanent membership with 0% discount.', 1),
('GOLD', 'Gold Member', 300000.00, 12, 5, 'Gold tier card valid for 12 months. 5% discount on 30-day and 90-day packages.', 1),
('VIP', 'VIP Member', 600000.00, 12, 10, 'VIP tier card valid for 12 months. 10% discount on 30-day and 90-day packages.', 1);

-- ==========================================
-- 3. member_card
-- ==========================================
CREATE TABLE [member_card] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    card_code NVARCHAR(30) NOT NULL,
    member_id BIGINT NOT NULL,
    tier_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    status NVARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    price_paid DECIMAL(14,2) NOT NULL DEFAULT 0,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    CONSTRAINT pk_member_card PRIMARY KEY (id),
    CONSTRAINT uq_member_card_code UNIQUE (card_code),
    CONSTRAINT fk_member_card_member FOREIGN KEY (member_id) REFERENCES [member_profile](user_id),
    CONSTRAINT fk_member_card_tier FOREIGN KEY (tier_id) REFERENCES [membership_card_tier](id),
    CONSTRAINT ck_member_card_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_member_card_price CHECK (price_paid >= 0)
);

CREATE INDEX ix_member_card_lookup ON [member_card](member_id, status);

-- ==========================================
-- 4. sport_package
-- ==========================================
CREATE TABLE [sport_package] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code NVARCHAR(50) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    sport_id BIGINT NOT NULL,
    training_format NVARCHAR(20) NOT NULL,
    duration_days INT NOT NULL,
    session_count INT NOT NULL,
    price_amount DECIMAL(14,2) NOT NULL,
    description NVARCHAR(500) NULL,
    is_active BIT NOT NULL DEFAULT 1,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    CONSTRAINT pk_sport_package PRIMARY KEY (id),
    CONSTRAINT uq_sport_package_code UNIQUE (code),
    CONSTRAINT fk_sport_package_sport FOREIGN KEY (sport_id) REFERENCES [sport](id),
    CONSTRAINT ck_sport_package_format CHECK (training_format IN ('SELF_TRAINING', 'COACH_LED')),
    CONSTRAINT ck_sport_package_duration CHECK (duration_days > 0),
    CONSTRAINT ck_sport_package_sessions CHECK (session_count > 0),
    CONSTRAINT ck_sport_package_price CHECK (price_amount >= 0)
);

-- Seed representative sport packages for active sports
INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
SELECT 
    s.code + '_SELF_SINGLE',
    s.name + ' - Single Visit (Self-training)',
    s.id,
    'SELF_TRAINING',
    1,
    1,
    50000.00,
    'Single day self-training access for ' + s.name,
    1
FROM [sport] s WHERE s.code = 'BADMINTON';

INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
SELECT 
    s.code + '_SELF_30D',
    s.name + ' - 30 Days Pass (Self-training)',
    s.id,
    'SELF_TRAINING',
    30,
    8,
    350000.00,
    '30-day package with 8 self-training sessions for ' + s.name,
    1
FROM [sport] s WHERE s.code = 'BADMINTON';

INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
SELECT 
    s.code + '_COACH_30D',
    s.name + ' - 30 Days Coach-led',
    s.id,
    'COACH_LED',
    30,
    8,
    1000000.00,
    '30-day package with 8 coach-led sessions for ' + s.name,
    1
FROM [sport] s WHERE s.code = 'BADMINTON';

INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
SELECT 
    s.code + '_COACH_90D',
    s.name + ' - 90 Days Coach-led Intensive',
    s.id,
    'COACH_LED',
    90,
    24,
    2700000.00,
    '90-day package with 24 coach-led sessions for ' + s.name,
    1
FROM [sport] s WHERE s.code = 'BADMINTON';

INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
SELECT 
    s.code + '_SELF_30D',
    s.name + ' - 30 Days Pass (Self-training)',
    s.id,
    'SELF_TRAINING',
    30,
    8,
    400000.00,
    '30-day package with 8 self-training sessions for ' + s.name,
    1
FROM [sport] s WHERE s.code = 'SWIMMING';

INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
SELECT 
    s.code + '_COACH_30D',
    s.name + ' - 30 Days Swimming Class',
    s.id,
    'COACH_LED',
    30,
    8,
    1200000.00,
    '30-day package with 8 swimming coach sessions',
    1
FROM [sport] s WHERE s.code = 'SWIMMING';

INSERT INTO [sport_package] (code, name, sport_id, training_format, duration_days, session_count, price_amount, description, is_active)
SELECT 
    s.code + '_COACH_30D',
    s.name + ' - 30 Days Basketball Class',
    s.id,
    'COACH_LED',
    30,
    8,
    1100000.00,
    '30-day package with 8 basketball coach sessions',
    1
FROM [sport] s WHERE s.code = 'BASKETBALL';

-- ==========================================
-- 5. sport_package_registration
-- ==========================================
CREATE TABLE [sport_package_registration] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    registration_code NVARCHAR(30) NOT NULL,
    member_id BIGINT NOT NULL,
    package_id BIGINT NOT NULL,
    channel NVARCHAR(20) NOT NULL DEFAULT 'ONLINE',
    original_price DECIMAL(14,2) NOT NULL,
    discount_percentage INT NOT NULL DEFAULT 0,
    paid_amount DECIMAL(14,2) NOT NULL,
    total_sessions INT NOT NULL,
    remaining_sessions INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status NVARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT',
    activated_at DATETIME2(0) NULL,
    cancelled_at DATETIME2(0) NULL,
    cancel_reason NVARCHAR(255) NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    CONSTRAINT pk_sport_package_reg PRIMARY KEY (id),
    CONSTRAINT uq_sport_package_reg_code UNIQUE (registration_code),
    CONSTRAINT fk_spr_member FOREIGN KEY (member_id) REFERENCES [member_profile](user_id),
    CONSTRAINT fk_spr_package FOREIGN KEY (package_id) REFERENCES [sport_package](id),
    CONSTRAINT fk_spr_created_by FOREIGN KEY (created_by_user_id) REFERENCES [user_account](id),
    CONSTRAINT ck_spr_channel CHECK (channel IN ('ONLINE', 'RECEPTION')),
    CONSTRAINT ck_spr_status CHECK (status IN ('PENDING_PAYMENT', 'ACTIVE', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_spr_sessions CHECK (remaining_sessions >= 0 AND remaining_sessions <= total_sessions),
    CONSTRAINT ck_spr_dates CHECK (end_date >= start_date)
);

CREATE INDEX ix_spr_member_status ON [sport_package_registration](member_id, status, end_date);

-- ==========================================
-- 6. refund_request
-- ==========================================
CREATE TABLE [refund_request] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    refund_code NVARCHAR(30) NOT NULL,
    package_registration_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    amount_requested DECIMAL(14,2) NOT NULL,
    amount_approved DECIMAL(14,2) NULL,
    reason NVARCHAR(500) NOT NULL,
    status NVARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_by BIGINT NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME2(0) NULL,
    manager_note NVARCHAR(500) NULL,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    CONSTRAINT pk_refund_request PRIMARY KEY (id),
    CONSTRAINT uq_refund_code UNIQUE (refund_code),
    CONSTRAINT fk_refund_pkg FOREIGN KEY (package_registration_id) REFERENCES [sport_package_registration](id),
    CONSTRAINT fk_refund_member FOREIGN KEY (member_id) REFERENCES [member_profile](user_id),
    CONSTRAINT fk_refund_req_by FOREIGN KEY (requested_by) REFERENCES [user_account](id),
    CONSTRAINT fk_refund_rev_by FOREIGN KEY (reviewed_by) REFERENCES [user_account](id),
    CONSTRAINT ck_refund_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED')),
    CONSTRAINT ck_refund_amount_req CHECK (amount_requested > 0),
    CONSTRAINT ck_refund_amount_app CHECK (amount_approved IS NULL OR amount_approved >= 0)
);

CREATE INDEX ix_refund_request_status ON [refund_request](status);

-- ==========================================
-- 7. sport_class & class_session
-- ==========================================
CREATE TABLE [sport_class] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code NVARCHAR(30) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    sport_id BIGINT NOT NULL,
    age_group_id BIGINT NULL,
    level NVARCHAR(20) NOT NULL DEFAULT 'BEGINNER',
    max_members INT NOT NULL DEFAULT 20,
    is_active BIT NOT NULL DEFAULT 1,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    CONSTRAINT pk_sport_class PRIMARY KEY (id),
    CONSTRAINT uq_sport_class_code UNIQUE (code),
    CONSTRAINT fk_sc_sport FOREIGN KEY (sport_id) REFERENCES [sport](id),
    CONSTRAINT fk_sc_age_group FOREIGN KEY (age_group_id) REFERENCES [age_group](id)
);

CREATE TABLE [class_session] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    class_id BIGINT NULL,
    sport_id BIGINT NOT NULL,
    session_date DATE NOT NULL,
    start_time TIME(0) NOT NULL,
    end_time TIME(0) NOT NULL,
    facility_id BIGINT NULL,
    coach_id BIGINT NULL,
    training_type NVARCHAR(20) NOT NULL DEFAULT 'COACH_LED',
    capacity INT NOT NULL DEFAULT 20,
    booked_count INT NOT NULL DEFAULT 0,
    status NVARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    CONSTRAINT pk_class_session PRIMARY KEY (id),
    CONSTRAINT fk_cs_class FOREIGN KEY (class_id) REFERENCES [sport_class](id),
    CONSTRAINT fk_cs_sport FOREIGN KEY (sport_id) REFERENCES [sport](id),
    CONSTRAINT fk_cs_facility FOREIGN KEY (facility_id) REFERENCES [facility](id),
    CONSTRAINT fk_cs_coach FOREIGN KEY (coach_id) REFERENCES [coach_profile](user_id),
    CONSTRAINT ck_cs_training_type CHECK (training_type IN ('COACH_LED', 'SELF_TRAINING')),
    CONSTRAINT ck_cs_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED'))
);

CREATE INDEX ix_class_session_date_status ON [class_session](session_date, status);

-- ==========================================
-- 8. booking
-- ==========================================
CREATE TABLE [booking] (
    id BIGINT IDENTITY(1,1) NOT NULL,
    booking_code NVARCHAR(30) NOT NULL,
    session_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    package_registration_id BIGINT NULL,
    membership_id BIGINT NULL,
    status NVARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    booked_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    cancelled_at DATETIME2(0) NULL,
    cancel_reason NVARCHAR(255) NULL,
    created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NULL,
    CONSTRAINT pk_booking PRIMARY KEY (id),
    CONSTRAINT uq_booking_code UNIQUE (booking_code),
    CONSTRAINT fk_bk_session FOREIGN KEY (session_id) REFERENCES [class_session](id),
    CONSTRAINT fk_bk_member FOREIGN KEY (member_id) REFERENCES [member_profile](user_id),
    CONSTRAINT fk_bk_pkg FOREIGN KEY (package_registration_id) REFERENCES [sport_package_registration](id),
    CONSTRAINT fk_bk_membership FOREIGN KEY (membership_id) REFERENCES [membership](id),
    CONSTRAINT ck_bk_status CHECK (status IN ('CONFIRMED', 'CANCELLED'))
);

CREATE INDEX ix_booking_member_session ON [booking](member_id, session_id, status);

-- ==========================================
-- 9. check_in alterations for booking & package linkage
-- ==========================================
ALTER TABLE [check_in] ADD booking_id BIGINT NULL;
ALTER TABLE [check_in] ADD package_registration_id BIGINT NULL;
ALTER TABLE [check_in] ADD CONSTRAINT fk_check_in_booking FOREIGN KEY (booking_id) REFERENCES [booking](id);
ALTER TABLE [check_in] ADD CONSTRAINT fk_check_in_pkg FOREIGN KEY (package_registration_id) REFERENCES [sport_package_registration](id);
