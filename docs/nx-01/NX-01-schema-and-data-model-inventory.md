# NX-01 schema and data model inventory

Report index (`NX-01-report-index.md` - local review reference, not published). Round 2. Audited origin/main `d20833c3c16e4f7d95d12b6df21798fc528bd559`.

Current inventory is **62 SOURCE_CONFIRMED CREATE TABLE declarations**, independently reconciled by identity. ROUND_1_PARSER_RESULT 59 omitted three qualified V31 tables. This is not a live table count. Default Flyway locations contain 27 versioned files; two demo files are opt-in. No database, migration or seed was executed. [Reconciliation](NX-01-inventory-reconciliation.md).

## Migration files and scope


| Version | File | Default location | SHA-256 |
| --- | --- | --- | --- |
| 28 | backend/src/main/resources/db/demo/V28__seed_demo_accounts.sql | False | 3a49ea5ca5a97ebd8639ce1c9511ba8f3c335a9aa57e5029e4047e5083b2d584 |
| 29 | backend/src/main/resources/db/demo/V29__seed_demo_classes_and_sessions.sql | False | a5f8fc611880fdb7719149b39e05dc41d76c19186188da86c50ab0b49dcebc54 |
| 10 | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql | True | e9763e160a6e351b82d0deb492b1107dd175ab3c5f06cf73fcfc6b71f0decc8e |
| 11 | backend/src/main/resources/db/migration/catalog/V11__add_refunded_status.sql | True | 749652487ffbd164184ca8b647b1f99ccec295be1b333455ff5e962beda4d7a2 |
| 12 | backend/src/main/resources/db/migration/catalog/V12__align_sport_package_seed_with_figma.sql | True | 00fd1389281b424aa8b9835faee7faf06f679a4af691af102348e4e4f1084c9c |
| 13 | backend/src/main/resources/db/migration/catalog/V13__add_session_minutes_to_sport_package.sql | True | ec15451de242b81578cf797fb6ca35ebf330a66bd0339d8484dbdd557d45e9c6 |
| 14 | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql | True | 4f7144f367c39666f1fb04d19430f4228ad4e34540bbb58be87ae47e7f565ddc |
| 15 | backend/src/main/resources/db/migration/catalog/V15__create_class_waitlist_table.sql | True | f18eb1d835752fddc5458148010d6100e1fe413ca28e0f660b1517c277285914 |
| 16 | backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql | True | 7f9f55e0395ccb09ae0d2725443942e23c1938ba395ad0dd5d2f684ea3097135 |
| 17 | backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql | True | 4d5f83eec64f3c814ac40378804dd83c56e441809a3d8df3de14bd3d1c4cad04 |
| 18 | backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql | True | 5f2fe24cb0d6234ac8511faea19d84d5e23106e75f03d55aa70813d61fb5e736 |
| 19 | backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql | True | 4de359fdb8898948b13558b32b6f4d3815080f2929893776047be91238bd7e1f |
| 20 | backend/src/main/resources/db/migration/catalog/V20__align_schema_with_topic.sql | True | 2f1dae6de7e04d81f35445165242f53b3382f4ee88aa2117faf5ac33eaa90313 |
| 21 | backend/src/main/resources/db/migration/catalog/V21__payment_pending_adjustments.sql | True | be74f195f5349c1e2882053e04a0b071fe673d5ae31dd53add4f8d90a8d25076 |
| 22 | backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql | True | dad7658c89c0653ce2cb560bc90635154dce25a7caf71fe7a4629895b1143a74 |
| 23 | backend/src/main/resources/db/migration/catalog/V23__add_session_cancellation_and_schedule_indexes.sql | True | af08ff7428e1212c92a8ca8a5ee2a15d0308d76340875afcca077c40e7e7422e |
| 26 | backend/src/main/resources/db/migration/catalog/V26__add_front_desk_queue_indexes.sql | True | db8ac70dfa784bca949121097c0ce53a6a7af0e7156e4e46b992ef0f455c643c |
| 27 | backend/src/main/resources/db/migration/catalog/V27__refund_request_payment_link.sql | True | e56e7fde49767387529da30e6f6e63d9684ac13ace7dbf2e67d070f0edf69a06 |
| 30 | backend/src/main/resources/db/migration/catalog/V30__create_finance_report_views.sql | True | 0c22992f91c358eab312ea025c13bf4c21313216ebd4bc4c8aa210cdc4b06d4c |
| 31 | backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql | True | 6eb7097c5b00ce3ff8efd7723fa9a9019931e140a7cff912b7a13616d05c360a |
| 32 | backend/src/main/resources/db/migration/catalog/V32__seed_remaining_sport_facilities.sql | True | 949a779f8f94c66c580db4832711f8dedfa3a3f33f2dc8a4998c4710569b8bb3 |
| 5 | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql | True | c3f4c3bb1169800161d71826eee5251c9b212ba3fc1571b42b3956fef78fb153 |
| 6 | backend/src/main/resources/db/migration/catalog/V6__seed_catalog_data.sql | True | 913129e48cbb99b679f09ac41aa0977112ff056196ab01c1d62f5b9d05a6859f |
| 9 | backend/src/main/resources/db/migration/catalog/V9__add_registration_sequence.sql | True | 338a1c17ba320f13265d1578f1b12471ee33eba78d37ab34805a3393450f451d |
| 1 | backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql | True | c25c5116a273bdb2c7d4d7cd84c04e4584be20f8f7cb4ec3980f9475b96d033f |
| 2 | backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql | True | 1d42c704fccd2bc1c72d303bf02ca4b821fd0054148bebb317b72ad068d56ff2 |
| 33 | backend/src/main/resources/db/migration/identity/V33__create_user_avatar.sql | True | 681f378e5a0b3a42cbc71f0f4cee049d3d9d982125364dcaee55f29a47669b19 |
| 3 | backend/src/main/resources/db/migration/identity/V3__seed_identity_data.sql | True | 3c3c359008ddd0e2f28cfd2c6ed94c49ead65ef7d689b45f1d616eb5500f7793 |
| 4 | backend/src/main/resources/db/migration/identity/V4__seed_manager_account.sql | True | 58adfddd2b584ddb9d4c4fd525d04730b2e61871c2b57a415992f73e920dede0 |

Absent numeric versions in all discovered V1..V33 files: [7, 8, 24, 25]. Default-location versions additionally exclude opt-in demo V28/V29: [7, 8, 24, 25, 28, 29]. Nonstandard filenames: []. Gaps are source history observations, not proof of a failed Flyway history. Hashes are SHA-256 file hashes, not Flyway CRC checksum claims.

## Table declarations and current mappings


| Schema as declared | Table | Migration and lines | Entity | Conditional context |
| --- | --- | --- | --- | --- |
| <default> | membership_card_tier | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:22-38 | MembershipCardTier | False |
| <default> | member_card | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:49-66 | MemberCard | False |
| <default> | sport_package | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:73-93 | SportPackage | False |
| <default> | sport_package_registration | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:190-219 | SportPackageRegistration | False |
| <default> | refund_request | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:226-250 | RefundRequest | False |
| <default> | sport_class | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:257-272 | SportClass | False |
| <default> | class_session | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:274-296 | ClassSession | False |
| <default> | booking | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:303-323 | Booking | False |
| <default> | payment | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql:22-41 | Payment | True |
| <default> | invoice | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql:82-101 | Invoice | True |
| <default> | invoice_line | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql:111-125 | InvoiceLine | True |
| <default> | waitlist_entry | backend/src/main/resources/db/migration/catalog/V15__create_class_waitlist_table.sql:8-23 | No JPA entity found | True |
| <default> | session_plan | backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql:10-22 | No JPA entity found | True |
| <default> | session_plan_step | backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql:29-39 | No JPA entity found | True |
| <default> | attendance_record | backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql:47-63 | No JPA entity found | True |
| <default> | attendance_correction | backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql:71-82 | No JPA entity found | True |
| <default> | skill_metric | backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql:88-96 | No JPA entity found | True |
| <default> | session_result | backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql:101-111 | No JPA entity found | True |
| <default> | session_result_score | backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql:116-124 | No JPA entity found | True |
| <default> | coach_feedback | backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql:129-144 | No JPA entity found | True |
| <default> | support_request | backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql:16-35 | No JPA entity found | True |
| <default> | support_request_message | backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql:43-52 | No JPA entity found | True |
| <default> | assistant_setting | backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql:60-67 | No JPA entity found | True |
| <default> | assistant_topic | backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql:72-79 | No JPA entity found | True |
| <default> | assistant_quick_prompt | backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql:84-91 | No JPA entity found | True |
| <default> | ai_conversation | backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql:96-106 | No JPA entity found | True |
| <default> | ai_message | backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql:113-122 | No JPA entity found | True |
| <default> | recommendation_rule_set | backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql:16-23 | No JPA entity found | True |
| <default> | recommendation_rule_weight | backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql:28-35 | No JPA entity found | True |
| <default> | workout_plan | backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql:41-56 | No JPA entity found | True |
| <default> | workout_plan_candidate | backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql:63-74 | No JPA entity found | True |
| <default> | workout_plan_week | backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql:79-86 | No JPA entity found | True |
| <default> | workout_plan_day | backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql:91-98 | No JPA entity found | True |
| <default> | workout_plan_exercise | backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql:103-114 | No JPA entity found | True |
| <default> | plan_review | backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql:119-130 | No JPA entity found | True |
| <default> | notification | backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql:10-20 | No JPA entity found | True |
| <default> | announcement | backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql:27-35 | No JPA entity found | True |
| <default> | system_setting | backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql:40-47 | No JPA entity found | True |
| <default> | member_sport_interest | backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql:53-59 | No JPA entity found | True |
| <default> | coach_sport | backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql:64-70 | CoachSport | True |
| dbo | cash_coverage | backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql:9-14 | No JPA entity found | True |
| dbo | cash_capture_ticket | backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql:18-26 | No JPA entity found | True |
| dbo | cash_event | backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql:28-48 | No JPA entity found | True |
| <default> | sport | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:9-24 | Sport | False |
| <default> | age_group | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:29-41 | AgeGroup | False |
| <default> | facility | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:46-61 | Facility | False |
| <default> | membership_plan | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:66-89 | MembershipPlan | False |
| <default> | plan_eligible_sport | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:94-100 | No JPA entity found | False |
| <default> | plan_feature | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:105-112 | PlanFeature | False |
| <default> | membership | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:117-153 | Membership | False |
| <default> | membership_sport | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:166-172 | No JPA entity found | False |
| <default> | check_in | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:177-191 | CheckIn | False |
| <default> | role | backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql:4-12 | Role | False |
| <default> | permission | backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql:15-22 | Permission | False |
| <default> | role_permission | backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql:25-31 | No JPA entity found | False |
| <default> | user_account | backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql:34-52 | UserAccount | False |
| <default> | member_profile | backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql:4-16 | MemberProfile | False |
| <default> | coach_profile | backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql:19-34 | CoachProfile | False |
| <default> | coach_certification | backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql:37-52 | CoachCertification | False |
| <default> | password_reset_token | backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql:55-65 | PasswordResetToken | False |
| <default> | activity_log | backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql:68-82 | ActivityLog | False |
| <default> | user_avatar | backend/src/main/resources/db/migration/identity/V33__create_user_avatar.sql:8-18 | No JPA entity found | True |

Unqualified names retain <default>; dbo qualification is not silently invented. V30 explicitly requires its supported finance cohort in dbo, but actual default-schema/principal and applied history remain unverified. Conditional-context flags are conservative source hints, not a complete T-SQL control-flow model.

### membership_card_tier

[`backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql`:22-38](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `code NVARCHAR(30) NOT NULL`; `name NVARCHAR(50) NOT NULL`; `price DECIMAL(14,2) NOT NULL DEFAULT 0`; `duration_months INT NOT NULL DEFAULT 0`; `discount_percentage INT NOT NULL DEFAULT 0`; `description NVARCHAR(500) NULL`; `is_active BIT NOT NULL DEFAULT 1`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`; `CONSTRAINT pk_membership_card_tier PRIMARY KEY (id)`; `CONSTRAINT uq_membership_card_tier_code UNIQUE (code)`; `CONSTRAINT ck_membership_card_tier_price CHECK (price >= 0)`; `CONSTRAINT ck_membership_card_tier_duration CHECK (duration_months >= 0)`; `CONSTRAINT ck_membership_card_tier_discount CHECK (discount_percentage >= 0 AND discount_percentage <= 100)`.

### member_card

[`backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql`:49-66](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `card_code NVARCHAR(30) NOT NULL`; `member_id BIGINT NOT NULL`; `tier_id BIGINT NOT NULL`; `start_date DATE NOT NULL`; `end_date DATE NULL`; `status NVARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`; `price_paid DECIMAL(14,2) NOT NULL DEFAULT 0`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`; `CONSTRAINT pk_member_card PRIMARY KEY (id)`; `CONSTRAINT uq_member_card_code UNIQUE (card_code)`; `CONSTRAINT fk_member_card_member FOREIGN KEY (member_id) REFERENCES [member_profile](user_id)`; `CONSTRAINT fk_member_card_tier FOREIGN KEY (tier_id) REFERENCES [membership_card_tier](id)`; `CONSTRAINT ck_member_card_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED'))`; `CONSTRAINT ck_member_card_price CHECK (price_paid >= 0)`.

### sport_package

[`backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql`:73-93](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `code NVARCHAR(50) NOT NULL`; `name NVARCHAR(100) NOT NULL`; `sport_id BIGINT NOT NULL`; `training_format NVARCHAR(20) NOT NULL`; `duration_days INT NOT NULL`; `session_count INT NOT NULL`; `price_amount DECIMAL(14,2) NOT NULL`; `description NVARCHAR(500) NULL`; `is_active BIT NOT NULL DEFAULT 1`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`; `CONSTRAINT pk_sport_package PRIMARY KEY (id)`; `CONSTRAINT uq_sport_package_code UNIQUE (code)`; `CONSTRAINT fk_sport_package_sport FOREIGN KEY (sport_id) REFERENCES [sport](id)`; `CONSTRAINT ck_sport_package_format CHECK (training_format IN ('SELF_TRAINING', 'COACH_LED'))`; `CONSTRAINT ck_sport_package_duration CHECK (duration_days > 0)`; `CONSTRAINT ck_sport_package_sessions CHECK (session_count > 0)`; `CONSTRAINT ck_sport_package_price CHECK (price_amount >= 0)`.

### sport_package_registration

[`backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql`:190-219](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `registration_code NVARCHAR(30) NOT NULL`; `member_id BIGINT NOT NULL`; `package_id BIGINT NOT NULL`; `channel NVARCHAR(20) NOT NULL DEFAULT 'ONLINE'`; `original_price DECIMAL(14,2) NOT NULL`; `discount_percentage INT NOT NULL DEFAULT 0`; `paid_amount DECIMAL(14,2) NOT NULL`; `total_sessions INT NOT NULL`; `remaining_sessions INT NOT NULL`; `start_date DATE NOT NULL`; `end_date DATE NOT NULL`; `status NVARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT'`; `activated_at DATETIME2(0) NULL`; `cancelled_at DATETIME2(0) NULL`; `cancel_reason NVARCHAR(255) NULL`; `created_by_user_id BIGINT NOT NULL`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`; `CONSTRAINT pk_sport_package_reg PRIMARY KEY (id)`; `CONSTRAINT uq_sport_package_reg_code UNIQUE (registration_code)`; `CONSTRAINT fk_spr_member FOREIGN KEY (member_id) REFERENCES [member_profile](user_id)`; `CONSTRAINT fk_spr_package FOREIGN KEY (package_id) REFERENCES [sport_package](id)`; `CONSTRAINT fk_spr_created_by FOREIGN KEY (created_by_user_id) REFERENCES [user_account](id)`; `CONSTRAINT ck_spr_channel CHECK (channel IN ('ONLINE', 'RECEPTION'))`; `CONSTRAINT ck_spr_status CHECK (status IN ('PENDING_PAYMENT', 'ACTIVE', 'EXPIRED', 'CANCELLED'))`; `CONSTRAINT ck_spr_sessions CHECK (remaining_sessions >= 0 AND remaining_sessions <= total_sessions)`; `CONSTRAINT ck_spr_dates CHECK (end_date >= start_date)`.

### refund_request

[`backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql`:226-250](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `refund_code NVARCHAR(30) NOT NULL`; `package_registration_id BIGINT NOT NULL`; `member_id BIGINT NOT NULL`; `amount_requested DECIMAL(14,2) NOT NULL`; `amount_approved DECIMAL(14,2) NULL`; `reason NVARCHAR(500) NOT NULL`; `status NVARCHAR(20) NOT NULL DEFAULT 'PENDING'`; `requested_by BIGINT NOT NULL`; `reviewed_by BIGINT NULL`; `reviewed_at DATETIME2(0) NULL`; `manager_note NVARCHAR(500) NULL`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`; `CONSTRAINT pk_refund_request PRIMARY KEY (id)`; `CONSTRAINT uq_refund_code UNIQUE (refund_code)`; `CONSTRAINT fk_refund_pkg FOREIGN KEY (package_registration_id) REFERENCES [sport_package_registration](id)`; `CONSTRAINT fk_refund_member FOREIGN KEY (member_id) REFERENCES [member_profile](user_id)`; `CONSTRAINT fk_refund_req_by FOREIGN KEY (requested_by) REFERENCES [user_account](id)`; `CONSTRAINT fk_refund_rev_by FOREIGN KEY (reviewed_by) REFERENCES [user_account](id)`; `CONSTRAINT ck_refund_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED'))`; `CONSTRAINT ck_refund_amount_req CHECK (amount_requested > 0)`; `CONSTRAINT ck_refund_amount_app CHECK (amount_approved IS NULL OR amount_approved >= 0)`.

### sport_class

[`backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql`:257-272](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `code NVARCHAR(30) NOT NULL`; `name NVARCHAR(100) NOT NULL`; `sport_id BIGINT NOT NULL`; `age_group_id BIGINT NULL`; `level NVARCHAR(20) NOT NULL DEFAULT 'BEGINNER'`; `max_members INT NOT NULL DEFAULT 20`; `is_active BIT NOT NULL DEFAULT 1`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`; `CONSTRAINT pk_sport_class PRIMARY KEY (id)`; `CONSTRAINT uq_sport_class_code UNIQUE (code)`; `CONSTRAINT fk_sc_sport FOREIGN KEY (sport_id) REFERENCES [sport](id)`; `CONSTRAINT fk_sc_age_group FOREIGN KEY (age_group_id) REFERENCES [age_group](id)`.

### class_session

[`backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql`:274-296](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `class_id BIGINT NULL`; `sport_id BIGINT NOT NULL`; `session_date DATE NOT NULL`; `start_time TIME(0) NOT NULL`; `end_time TIME(0) NOT NULL`; `facility_id BIGINT NULL`; `coach_id BIGINT NULL`; `training_type NVARCHAR(20) NOT NULL DEFAULT 'COACH_LED'`; `capacity INT NOT NULL DEFAULT 20`; `booked_count INT NOT NULL DEFAULT 0`; `status NVARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`; `CONSTRAINT pk_class_session PRIMARY KEY (id)`; `CONSTRAINT fk_cs_class FOREIGN KEY (class_id) REFERENCES [sport_class](id)`; `CONSTRAINT fk_cs_sport FOREIGN KEY (sport_id) REFERENCES [sport](id)`; `CONSTRAINT fk_cs_facility FOREIGN KEY (facility_id) REFERENCES [facility](id)`; `CONSTRAINT fk_cs_coach FOREIGN KEY (coach_id) REFERENCES [coach_profile](user_id)`; `CONSTRAINT ck_cs_training_type CHECK (training_type IN ('COACH_LED', 'SELF_TRAINING'))`; `CONSTRAINT ck_cs_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED'))`.

### booking

[`backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql`:303-323](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `booking_code NVARCHAR(30) NOT NULL`; `session_id BIGINT NOT NULL`; `member_id BIGINT NOT NULL`; `package_registration_id BIGINT NULL`; `membership_id BIGINT NULL`; `status NVARCHAR(20) NOT NULL DEFAULT 'CONFIRMED'`; `booked_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `cancelled_at DATETIME2(0) NULL`; `cancel_reason NVARCHAR(255) NULL`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`; `CONSTRAINT pk_booking PRIMARY KEY (id)`; `CONSTRAINT uq_booking_code UNIQUE (booking_code)`; `CONSTRAINT fk_bk_session FOREIGN KEY (session_id) REFERENCES [class_session](id)`; `CONSTRAINT fk_bk_member FOREIGN KEY (member_id) REFERENCES [member_profile](user_id)`; `CONSTRAINT fk_bk_pkg FOREIGN KEY (package_registration_id) REFERENCES [sport_package_registration](id)`; `CONSTRAINT fk_bk_membership FOREIGN KEY (membership_id) REFERENCES [membership](id)`; `CONSTRAINT ck_bk_status CHECK (status IN ('CONFIRMED', 'CANCELLED'))`.

### payment

[`backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql`:22-41](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[payment_code] VARCHAR(30) NOT NULL`; `[member_id] BIGINT NOT NULL`; `[amount] DECIMAL(12,2) NOT NULL`; `[payment_method] VARCHAR(30) NOT NULL`; `[payment_status] VARCHAR(20) NOT NULL`; `[reference_code] VARCHAR(100) NULL`; `[notes] NVARCHAR(500) NULL`; `[payment_time] DATETIME2(0) NOT NULL CONSTRAINT [df_payment_payment_time] DEFAULT SYSDATETIME()`; `[version] INT NOT NULL CONSTRAINT [df_payment_version] DEFAULT 0`; `[created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_payment_created_at] DEFAULT SYSDATETIME()`; `[updated_at] DATETIME2(0) NOT NULL CONSTRAINT [df_payment_updated_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_payment] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [uq_payment_code] UNIQUE ([payment_code])`; `CONSTRAINT [fk_payment_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id])`; `CONSTRAINT [ck_payment_amount] CHECK ([amount] > 0)`; `CONSTRAINT [ck_payment_method] CHECK ([payment_method] IN ('CASH', 'CREDIT_CARD', 'BANK_TRANSFER', 'MOMO', 'VNPAY', 'ZALOPAY'))`; `CONSTRAINT [ck_payment_status] CHECK ([payment_status] IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'))`.

### invoice

[`backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql`:82-101](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[invoice_number] VARCHAR(30) NOT NULL`; `[payment_id] BIGINT NOT NULL`; `[member_id] BIGINT NOT NULL`; `[subtotal_amount] DECIMAL(12,2) NOT NULL`; `[discount_amount] DECIMAL(12,2) NOT NULL CONSTRAINT [df_invoice_discount] DEFAULT 0`; `[tax_amount] DECIMAL(12,2) NOT NULL CONSTRAINT [df_invoice_tax] DEFAULT 0`; `[total_amount] DECIMAL(12,2) NOT NULL`; `[status] VARCHAR(20) NOT NULL`; `[issue_date] DATETIME2(0) NOT NULL CONSTRAINT [df_invoice_issue_date] DEFAULT SYSDATETIME()`; `[created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_invoice_created_at] DEFAULT SYSDATETIME()`; `[updated_at] DATETIME2(0) NOT NULL CONSTRAINT [df_invoice_updated_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_invoice] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [uq_invoice_number] UNIQUE ([invoice_number])`; `CONSTRAINT [fk_invoice_payment] FOREIGN KEY ([payment_id]) REFERENCES [payment]([id])`; `CONSTRAINT [fk_invoice_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id])`; `CONSTRAINT [ck_invoice_amounts] CHECK ([total_amount] >= 0 AND [subtotal_amount] >= 0)`; `CONSTRAINT [ck_invoice_status] CHECK ([status] IN ('ISSUED', 'PAID', 'CANCELLED', 'REFUNDED'))`.

### invoice_line

[`backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql`:111-125](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[invoice_id] BIGINT NOT NULL`; `[item_type] VARCHAR(30) NOT NULL`; `[item_reference_id] BIGINT NOT NULL`; `[description] NVARCHAR(255) NOT NULL`; `[quantity] INT NOT NULL CONSTRAINT [df_invoice_line_quantity] DEFAULT 1`; `[unit_price] DECIMAL(12,2) NOT NULL`; `[line_total] DECIMAL(12,2) NOT NULL`; `[created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_invoice_line_created_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_invoice_line] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_invoice_line_invoice] FOREIGN KEY ([invoice_id]) REFERENCES [invoice]([id]) ON DELETE CASCADE`; `CONSTRAINT [ck_invoice_line_quantity] CHECK ([quantity] > 0)`; `CONSTRAINT [ck_invoice_line_item_type] CHECK ([item_type] IN ('SPORT_PACKAGE', 'MEMBERSHIP_CARD', 'CLASS_DROP_IN', 'PENALTY_FEE'))`.

### waitlist_entry

[`backend/src/main/resources/db/migration/catalog/V15__create_class_waitlist_table.sql`:8-23](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V15__create_class_waitlist_table.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[session_id] BIGINT NOT NULL`; `[member_id] BIGINT NOT NULL`; `[status] VARCHAR(20) NOT NULL CONSTRAINT [df_waitlist_status] DEFAULT 'WAITING'`; `[joined_at] DATETIME2(0) NOT NULL CONSTRAINT [df_waitlist_joined_at] DEFAULT SYSDATETIME()`; `[offered_at] DATETIME2(0) NULL`; `[offer_expires_at] DATETIME2(0) NULL`; `[promoted_booking_id] BIGINT NULL`; `[cancelled_at] DATETIME2(0) NULL`; `CONSTRAINT [pk_waitlist_entry] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_waitlist_session] FOREIGN KEY ([session_id]) REFERENCES [class_session]([id])`; `CONSTRAINT [fk_waitlist_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id])`; `CONSTRAINT [fk_waitlist_promoted_booking] FOREIGN KEY ([promoted_booking_id]) REFERENCES [booking]([id])`; `CONSTRAINT [ck_waitlist_status] CHECK ([status] IN ('WAITING', 'OFFERED', 'PROMOTED', 'EXPIRED', 'CANCELLED'))`.

### session_plan

[`backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql`:10-22](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[class_id] BIGINT NOT NULL`; `[coach_id] BIGINT NOT NULL`; `[title] NVARCHAR(150) NOT NULL`; `[description] NVARCHAR(1000) NULL`; `[is_template] BIT NOT NULL CONSTRAINT [df_session_plan_is_template] DEFAULT 0`; `[created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_session_plan_created_at] DEFAULT SYSDATETIME()`; `[updated_at] DATETIME2(0) NOT NULL CONSTRAINT [df_session_plan_updated_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_session_plan] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_session_plan_class] FOREIGN KEY ([class_id]) REFERENCES [sport_class]([id])`; `CONSTRAINT [fk_session_plan_coach] FOREIGN KEY ([coach_id]) REFERENCES [coach_profile]([user_id])`.

### session_plan_step

[`backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql`:29-39](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[plan_id] BIGINT NOT NULL`; `[step_order] INT NOT NULL`; `[title] NVARCHAR(150) NOT NULL`; `[duration_minutes] INT NOT NULL`; `[instructions] NVARCHAR(1000) NULL`; `CONSTRAINT [pk_session_plan_step] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_session_plan_step_plan] FOREIGN KEY ([plan_id]) REFERENCES [session_plan]([id]) ON DELETE CASCADE`; `CONSTRAINT [ck_session_plan_step_duration] CHECK ([duration_minutes] > 0)`.

### attendance_record

[`backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql`:47-63](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[session_id] BIGINT NOT NULL`; `[member_id] BIGINT NOT NULL`; `[booking_id] BIGINT NULL`; `[status] VARCHAR(20) NOT NULL`; `[marked_at] DATETIME2(0) NOT NULL CONSTRAINT [df_attendance_marked_at] DEFAULT SYSDATETIME()`; `[marked_by] BIGINT NOT NULL`; `[notes] NVARCHAR(500) NULL`; `CONSTRAINT [pk_attendance_record] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [uq_attendance_session_member] UNIQUE ([session_id], [member_id])`; `CONSTRAINT [fk_attendance_session] FOREIGN KEY ([session_id]) REFERENCES [class_session]([id])`; `CONSTRAINT [fk_attendance_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id])`; `CONSTRAINT [fk_attendance_booking] FOREIGN KEY ([booking_id]) REFERENCES [booking]([id])`; `CONSTRAINT [fk_attendance_marked_by] FOREIGN KEY ([marked_by]) REFERENCES [user_account]([id])`; `CONSTRAINT [ck_attendance_status] CHECK ([status] IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED'))`.

### attendance_correction

[`backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql`:71-82](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[attendance_record_id] BIGINT NOT NULL`; `[previous_status] VARCHAR(20) NOT NULL`; `[new_status] VARCHAR(20) NOT NULL`; `[reason] NVARCHAR(500) NOT NULL`; `[corrected_by] BIGINT NOT NULL`; `[created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_attendance_corr_created_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_attendance_correction] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_attendance_corr_record] FOREIGN KEY ([attendance_record_id]) REFERENCES [attendance_record]([id])`; `CONSTRAINT [fk_attendance_corr_user] FOREIGN KEY ([corrected_by]) REFERENCES [user_account]([id])`.

### skill_metric

[`backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql`:88-96](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[sport_id] BIGINT NOT NULL`; `[name] NVARCHAR(100) NOT NULL`; `[description] NVARCHAR(500) NULL`; `[max_score] INT NOT NULL CONSTRAINT [df_skill_metric_max_score] DEFAULT 10`; `CONSTRAINT [pk_skill_metric] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_skill_metric_sport] FOREIGN KEY ([sport_id]) REFERENCES [sport]([id])`.

### session_result

[`backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql`:101-111](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[session_id] BIGINT NOT NULL`; `[member_id] BIGINT NOT NULL`; `[notes] NVARCHAR(1000) NULL`; `[created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_session_result_created_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_session_result] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [uq_session_result_session_member] UNIQUE ([session_id], [member_id])`; `CONSTRAINT [fk_session_result_session] FOREIGN KEY ([session_id]) REFERENCES [class_session]([id])`; `CONSTRAINT [fk_session_result_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id])`.

### session_result_score

[`backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql`:116-124](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[result_id] BIGINT NOT NULL`; `[metric_id] BIGINT NOT NULL`; `[score] DECIMAL(5,2) NOT NULL`; `CONSTRAINT [pk_session_result_score] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_session_score_result] FOREIGN KEY ([result_id]) REFERENCES [session_result]([id]) ON DELETE CASCADE`; `CONSTRAINT [fk_session_score_metric] FOREIGN KEY ([metric_id]) REFERENCES [skill_metric]([id])`.

### coach_feedback

[`backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql`:129-144](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[session_id] BIGINT NOT NULL`; `[coach_id] BIGINT NOT NULL`; `[member_id] BIGINT NOT NULL`; `[rating] INT NOT NULL`; `[comments] NVARCHAR(1000) NOT NULL`; `[strengths] NVARCHAR(500) NULL`; `[areas_for_improvement] NVARCHAR(500) NULL`; `[created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_coach_feedback_created_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_coach_feedback] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_coach_feedback_session] FOREIGN KEY ([session_id]) REFERENCES [class_session]([id])`; `CONSTRAINT [fk_coach_feedback_coach] FOREIGN KEY ([coach_id]) REFERENCES [coach_profile]([user_id])`; `CONSTRAINT [fk_coach_feedback_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id])`; `CONSTRAINT [ck_coach_feedback_rating] CHECK ([rating] BETWEEN 1 AND 5)`.

### support_request

[`backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql`:16-35](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[request_code] VARCHAR(30) NOT NULL`; `[member_id] BIGINT NOT NULL`; `[subject] NVARCHAR(200) NOT NULL`; `[category] VARCHAR(50) NOT NULL`; `[description] NVARCHAR(2000) NOT NULL`; `[priority] VARCHAR(20) NOT NULL CONSTRAINT [df_support_req_priority] DEFAULT 'MEDIUM'`; `[status] VARCHAR(20) NOT NULL CONSTRAINT [df_support_req_status] DEFAULT 'OPEN'`; `[assigned_staff_id] BIGINT NULL`; `[resolution_notes] NVARCHAR(2000) NULL`; `[created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_support_req_created_at] DEFAULT SYSDATETIME()`; `[updated_at] DATETIME2(0) NOT NULL CONSTRAINT [df_support_req_updated_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_support_request] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [uq_support_request_code] UNIQUE ([request_code])`; `CONSTRAINT [fk_support_req_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id])`; `CONSTRAINT [fk_support_req_staff] FOREIGN KEY ([assigned_staff_id]) REFERENCES [user_account]([id])`; `CONSTRAINT [ck_support_req_priority] CHECK ([priority] IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))`; `CONSTRAINT [ck_support_req_status] CHECK ([status] IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'))`.

### support_request_message

[`backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql`:43-52](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[request_id] BIGINT NOT NULL`; `[sender_id] BIGINT NOT NULL`; `[message_body] NVARCHAR(2000) NOT NULL`; `[created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_support_msg_created_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_support_request_message] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_support_msg_request] FOREIGN KEY ([request_id]) REFERENCES [support_request]([id]) ON DELETE CASCADE`; `CONSTRAINT [fk_support_msg_sender] FOREIGN KEY ([sender_id]) REFERENCES [user_account]([id])`.

### assistant_setting

[`backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql`:60-67](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[setting_key] VARCHAR(100) NOT NULL`; `[setting_value] NVARCHAR(MAX) NOT NULL`; `[description] NVARCHAR(500) NULL`; `CONSTRAINT [pk_assistant_setting] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [uq_assistant_setting_key] UNIQUE ([setting_key])`.

### assistant_topic

[`backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql`:72-79](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[title] NVARCHAR(150) NOT NULL`; `[category] VARCHAR(50) NOT NULL`; `[content] NVARCHAR(2000) NOT NULL`; `[is_active] BIT NOT NULL CONSTRAINT [df_assistant_topic_active] DEFAULT 1`; `CONSTRAINT [pk_assistant_topic] PRIMARY KEY CLUSTERED ([id])`.

### assistant_quick_prompt

[`backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql`:84-91](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[topic_id] BIGINT NULL`; `[prompt_text] NVARCHAR(255) NOT NULL`; `[display_order] INT NOT NULL CONSTRAINT [df_quick_prompt_order] DEFAULT 0`; `CONSTRAINT [pk_assistant_quick_prompt] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_quick_prompt_topic] FOREIGN KEY ([topic_id]) REFERENCES [assistant_topic]([id]) ON DELETE SET NULL`.

### ai_conversation

[`backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql`:96-106](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[user_id] BIGINT NOT NULL`; `[title] NVARCHAR(200) NULL`; `[rating] INT NULL`; `[feedback_notes] NVARCHAR(500) NULL`; `[created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_ai_conv_created_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_ai_conversation] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_ai_conv_user] FOREIGN KEY ([user_id]) REFERENCES [user_account]([id])`; `CONSTRAINT [ck_ai_conv_rating] CHECK ([rating] IS NULL OR [rating] BETWEEN 1 AND 5)`.

### ai_message

[`backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql`:113-122](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[conversation_id] BIGINT NOT NULL`; `[sender_type] VARCHAR(20) NOT NULL`; `[content] NVARCHAR(MAX) NOT NULL`; `[sent_at] DATETIME2(0) NOT NULL CONSTRAINT [df_ai_msg_sent_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_ai_message] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_ai_msg_conversation] FOREIGN KEY ([conversation_id]) REFERENCES [ai_conversation]([id]) ON DELETE CASCADE`; `CONSTRAINT [ck_ai_msg_sender] CHECK ([sender_type] IN ('USER', 'ASSISTANT', 'SYSTEM'))`.

### recommendation_rule_set

[`backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql`:16-23](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[name] NVARCHAR(100) NOT NULL`; `[description] NVARCHAR(500) NULL`; `[is_active] BIT NOT NULL CONSTRAINT [df_rec_rule_set_active] DEFAULT 1`; `[created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_rec_rule_set_created_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_recommendation_rule_set] PRIMARY KEY CLUSTERED ([id])`.

### recommendation_rule_weight

[`backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql`:28-35](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[rule_set_id] BIGINT NOT NULL`; `[parameter_name] VARCHAR(100) NOT NULL`; `[weight] DECIMAL(5,2) NOT NULL`; `CONSTRAINT [pk_recommendation_rule_weight] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_rec_rule_weight_set] FOREIGN KEY ([rule_set_id]) REFERENCES [recommendation_rule_set]([id]) ON DELETE CASCADE`.

### workout_plan

[`backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql`:41-56](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[plan_code] VARCHAR(30) NOT NULL`; `[member_id] BIGINT NOT NULL`; `[name] NVARCHAR(150) NOT NULL`; `[goal] VARCHAR(50) NOT NULL`; `[difficulty_level] VARCHAR(30) NOT NULL`; `[duration_weeks] INT NOT NULL`; `[status] VARCHAR(20) NOT NULL CONSTRAINT [df_workout_plan_status] DEFAULT 'DRAFT'`; `[created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_workout_plan_created_at] DEFAULT SYSDATETIME()`; `[updated_at] DATETIME2(0) NOT NULL CONSTRAINT [df_workout_plan_updated_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_workout_plan] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [uq_workout_plan_code] UNIQUE ([plan_code])`; `CONSTRAINT [fk_workout_plan_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id])`; `CONSTRAINT [ck_workout_plan_weeks] CHECK ([duration_weeks] > 0)`.

### workout_plan_candidate

[`backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql`:63-74](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[member_id] BIGINT NOT NULL`; `[goal] VARCHAR(50) NOT NULL`; `[fitness_level] VARCHAR(30) NOT NULL`; `[score] DECIMAL(5,2) NOT NULL`; `[suggested_plan_id] BIGINT NULL`; `[created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_workout_candidate_created_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_workout_plan_candidate] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_workout_candidate_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id])`; `CONSTRAINT [fk_workout_candidate_plan] FOREIGN KEY ([suggested_plan_id]) REFERENCES [workout_plan]([id])`.

### workout_plan_week

[`backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql`:79-86](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[plan_id] BIGINT NOT NULL`; `[week_number] INT NOT NULL`; `[focus] NVARCHAR(150) NULL`; `CONSTRAINT [pk_workout_plan_week] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_workout_week_plan] FOREIGN KEY ([plan_id]) REFERENCES [workout_plan]([id]) ON DELETE CASCADE`.

### workout_plan_day

[`backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql`:91-98](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[week_id] BIGINT NOT NULL`; `[day_number] INT NOT NULL`; `[focus] NVARCHAR(150) NULL`; `CONSTRAINT [pk_workout_plan_day] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_workout_day_week] FOREIGN KEY ([week_id]) REFERENCES [workout_plan_week]([id]) ON DELETE CASCADE`.

### workout_plan_exercise

[`backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql`:103-114](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[day_id] BIGINT NOT NULL`; `[name] NVARCHAR(150) NOT NULL`; `[sets] INT NOT NULL`; `[reps] INT NOT NULL`; `[rest_seconds] INT NOT NULL CONSTRAINT [df_exercise_rest] DEFAULT 60`; `[video_url] VARCHAR(500) NULL`; `[is_completed] BIT NOT NULL CONSTRAINT [df_exercise_completed] DEFAULT 0`; `CONSTRAINT [pk_workout_plan_exercise] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_workout_exercise_day] FOREIGN KEY ([day_id]) REFERENCES [workout_plan_day]([id]) ON DELETE CASCADE`.

### plan_review

[`backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql`:119-130](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[plan_id] BIGINT NOT NULL`; `[coach_id] BIGINT NOT NULL`; `[status] VARCHAR(20) NOT NULL CONSTRAINT [df_plan_review_status] DEFAULT 'PENDING'`; `[feedback] NVARCHAR(2000) NULL`; `[reviewed_at] DATETIME2(0) NULL`; `CONSTRAINT [pk_plan_review] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_plan_review_plan] FOREIGN KEY ([plan_id]) REFERENCES [workout_plan]([id])`; `CONSTRAINT [fk_plan_review_coach] FOREIGN KEY ([coach_id]) REFERENCES [coach_profile]([user_id])`; `CONSTRAINT [ck_plan_review_status] CHECK ([status] IN ('PENDING', 'APPROVED', 'MODIFIED', 'REJECTED'))`.

### notification

[`backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql`:10-20](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[user_id] BIGINT NOT NULL`; `[title] NVARCHAR(200) NOT NULL`; `[content] NVARCHAR(1000) NOT NULL`; `[type] VARCHAR(50) NOT NULL`; `[is_read] BIT NOT NULL CONSTRAINT [df_notification_is_read] DEFAULT 0`; `[created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_notification_created_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_notification] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [fk_notification_user] FOREIGN KEY ([user_id]) REFERENCES [user_account]([id])`.

### announcement

[`backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql`:27-35](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[title] NVARCHAR(200) NOT NULL`; `[content] NVARCHAR(2000) NOT NULL`; `[target_role] VARCHAR(30) NULL`; `[is_published] BIT NOT NULL CONSTRAINT [df_announcement_published] DEFAULT 1`; `[published_at] DATETIME2(0) NOT NULL CONSTRAINT [df_announcement_published_at] DEFAULT SYSDATETIME()`; `CONSTRAINT [pk_announcement] PRIMARY KEY CLUSTERED ([id])`.

### system_setting

[`backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql`:40-47](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql).

Creation columns/constraints: `[id] BIGINT IDENTITY(1,1) NOT NULL`; `[setting_key] VARCHAR(100) NOT NULL`; `[setting_value] NVARCHAR(MAX) NOT NULL`; `[description] NVARCHAR(500) NULL`; `CONSTRAINT [pk_system_setting] PRIMARY KEY CLUSTERED ([id])`; `CONSTRAINT [uq_system_setting_key] UNIQUE ([setting_key])`.

### member_sport_interest

[`backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql`:53-59](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql).

Creation columns/constraints: `[member_id] BIGINT NOT NULL`; `[sport_id] BIGINT NOT NULL`; `CONSTRAINT [pk_member_sport_interest] PRIMARY KEY CLUSTERED ([member_id], [sport_id])`; `CONSTRAINT [fk_member_sport_interest_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id])`; `CONSTRAINT [fk_member_sport_interest_sport] FOREIGN KEY ([sport_id]) REFERENCES [sport]([id])`.

### coach_sport

[`backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql`:64-70](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql).

Creation columns/constraints: `[coach_id] BIGINT NOT NULL`; `[sport_id] BIGINT NOT NULL`; `CONSTRAINT [pk_coach_sport] PRIMARY KEY CLUSTERED ([coach_id], [sport_id])`; `CONSTRAINT [fk_coach_sport_coach] FOREIGN KEY ([coach_id]) REFERENCES [coach_profile]([user_id])`; `CONSTRAINT [fk_coach_sport_sport] FOREIGN KEY ([sport_id]) REFERENCES [sport]([id])`.

### dbo.cash_coverage

[`backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql`:9-14](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql).

Creation columns/constraints: `id INT NOT NULL CONSTRAINT pk_cash_coverage PRIMARY KEY`; `cutover_utc DATETIME2(7) NULL`; `source VARCHAR(30) NOT NULL`; `CONSTRAINT ck_cash_coverage_singleton CHECK (id = 1 AND source = 'LIVE_SERVICE')`.

### dbo.cash_capture_ticket

[`backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql`:18-26](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql).

Creation columns/constraints: `transaction_id BIGINT NOT NULL`; `event_type VARCHAR(20) NOT NULL`; `source_id BIGINT NOT NULL`; `event_time_utc DATETIME2(7) NOT NULL`; `CONSTRAINT pk_cash_capture_ticket PRIMARY KEY(transaction_id, event_type, source_id)`; `CONSTRAINT ck_cash_ticket_kind CHECK (event_type IN ('COLLECTION', 'REFUND_PAYOUT'))`; `CONSTRAINT ck_cash_ticket_source CHECK (source_id > 0 OR (source_id = 0 AND event_type = 'COLLECTION'))`.

### dbo.cash_event

[`backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql`:28-48](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_cash_event PRIMARY KEY`; `event_type VARCHAR(20) NOT NULL`; `payment_id BIGINT NULL CONSTRAINT fk_cash_event_payment REFERENCES dbo.payment(id)`; `refund_request_id BIGINT NULL CONSTRAINT fk_cash_event_refund REFERENCES dbo.refund_request(id)`; `amount DECIMAL(14,2) NOT NULL`; `event_time_utc DATETIME2(7) NOT NULL`; `recorded_at_utc DATETIME2(7) NOT NULL CONSTRAINT df_cash_recorded DEFAULT SYSUTCDATETIME()`; `provenance VARCHAR(30) NOT NULL`; `original_method VARCHAR(30) NULL`; `original_item_type VARCHAR(30) NULL`; `original_sport_id BIGINT NULL`; `original_sport_name NVARCHAR(100) NULL`; `CONSTRAINT ck_cash_amount CHECK (amount > 0)`; `CONSTRAINT ck_cash_provenance CHECK (provenance = 'LIVE_SERVICE')`; `CONSTRAINT ck_cash_type CHECK (`; `(event_type = 'COLLECTION' AND payment_id IS NOT NULL AND refund_request_id IS NULL)`; `OR (event_type = 'REFUND_PAYOUT' AND refund_request_id IS NOT NULL))`; `CONSTRAINT ck_cash_sport CHECK ((original_sport_id IS NULL AND original_sport_name IS NULL)`; `OR (original_sport_id IS NOT NULL AND original_sport_name IS NOT NULL))`.

### sport

[`backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql`:9-24](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `code NVARCHAR(30) NOT NULL`; `name NVARCHAR(50) NOT NULL`; `venue_type NVARCHAR(20) NOT NULL`; `description NVARCHAR(500) NULL`; `image_url NVARCHAR(500) NULL`; `display_order INT NOT NULL DEFAULT 0`; `is_active BIT NOT NULL DEFAULT 1`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`; `CONSTRAINT pk_sport PRIMARY KEY (id)`; `CONSTRAINT uq_sport_code UNIQUE (code)`; `CONSTRAINT uq_sport_name UNIQUE (name)`; `CONSTRAINT ck_sport_venue_type CHECK (venue_type IN ('INDOOR', 'OUTDOOR', 'POOL'))`.

### age_group

[`backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql`:29-41](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `code NVARCHAR(30) NOT NULL`; `label NVARCHAR(50) NOT NULL`; `min_age INT NOT NULL`; `max_age INT NULL`; `display_order INT NOT NULL DEFAULT 0`; `is_active BIT NOT NULL DEFAULT 1`; `CONSTRAINT pk_age_group PRIMARY KEY (id)`; `CONSTRAINT uq_age_group_code UNIQUE (code)`; `CONSTRAINT ck_age_group_min_age CHECK (min_age >= 0)`; `CONSTRAINT ck_age_group_max_age CHECK (max_age IS NULL OR max_age >= min_age)`.

### facility

[`backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql`:46-61](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `code NVARCHAR(30) NOT NULL`; `name NVARCHAR(100) NOT NULL`; `facility_type NVARCHAR(20) NOT NULL`; `sport_id BIGINT NULL`; `capacity INT NULL`; `is_active BIT NOT NULL DEFAULT 1`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`; `CONSTRAINT pk_facility PRIMARY KEY (id)`; `CONSTRAINT uq_facility_code UNIQUE (code)`; `CONSTRAINT fk_facility_sport FOREIGN KEY (sport_id) REFERENCES [sport](id)`; `CONSTRAINT ck_facility_type CHECK (facility_type IN ('COURT', 'POOL', 'FIELD', 'HALL', 'STUDIO'))`; `CONSTRAINT ck_facility_capacity CHECK (capacity IS NULL OR capacity > 0)`.

### membership_plan

[`backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql`:66-89](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `code NVARCHAR(30) NOT NULL`; `name NVARCHAR(100) NOT NULL`; `tagline NVARCHAR(100) NULL`; `description NVARCHAR(500) NULL`; `price DECIMAL(14,2) NOT NULL`; `duration_days INT NOT NULL`; `max_sports INT NOT NULL`; `is_featured BIT NOT NULL DEFAULT 0`; `status NVARCHAR(20) NOT NULL DEFAULT 'DRAFT'`; `display_order INT NOT NULL DEFAULT 0`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`; `created_by BIGINT NULL,   -- user id, no FK (README audit convention for master data)`; `updated_by BIGINT NULL,   -- user id, no FK`; `version INT NOT NULL DEFAULT 0`; `CONSTRAINT pk_membership_plan PRIMARY KEY (id)`; `CONSTRAINT uq_membership_plan_code UNIQUE (code)`; `CONSTRAINT ck_membership_plan_price CHECK (price >= 0)`; `CONSTRAINT ck_membership_plan_duration CHECK (duration_days > 0)`; `CONSTRAINT ck_membership_plan_max_sports CHECK (max_sports >= 1)`; `CONSTRAINT ck_membership_plan_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))`.

### plan_eligible_sport

[`backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql`:94-100](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql).

Creation columns/constraints: `plan_id BIGINT NOT NULL`; `sport_id BIGINT NOT NULL`; `CONSTRAINT pk_plan_eligible_sport PRIMARY KEY (plan_id, sport_id)`; `CONSTRAINT fk_pes_plan FOREIGN KEY (plan_id) REFERENCES [membership_plan](id) ON DELETE CASCADE`; `CONSTRAINT fk_pes_sport FOREIGN KEY (sport_id) REFERENCES [sport](id)`.

### plan_feature

[`backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql`:105-112](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `plan_id BIGINT NOT NULL`; `feature_text NVARCHAR(150) NOT NULL`; `display_order INT NOT NULL DEFAULT 0`; `CONSTRAINT pk_plan_feature PRIMARY KEY (id)`; `CONSTRAINT fk_pf_plan FOREIGN KEY (plan_id) REFERENCES [membership_plan](id) ON DELETE CASCADE`.

### membership

[`backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql`:117-153](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `registration_code NVARCHAR(20) NOT NULL`; `member_id BIGINT NOT NULL`; `plan_id BIGINT NOT NULL`; `registration_type NVARCHAR(20) NOT NULL`; `previous_membership_id BIGINT NULL`; `channel NVARCHAR(20) NOT NULL`; `status NVARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT'`; `price_amount DECIMAL(14,2) NOT NULL`; `duration_days INT NOT NULL`; `start_date DATE NULL`; `end_date DATE NULL`; `activated_at DATETIME2(0) NULL`; `cancelled_at DATETIME2(0) NULL`; `cancel_reason NVARCHAR(255) NULL`; `created_by_user_id BIGINT NOT NULL`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`; `version INT NOT NULL DEFAULT 0`; `CONSTRAINT pk_membership PRIMARY KEY (id)`; `CONSTRAINT uq_membership_registration_code UNIQUE (registration_code)`; `CONSTRAINT fk_membership_member FOREIGN KEY (member_id) REFERENCES [member_profile](user_id)`; `CONSTRAINT fk_membership_plan FOREIGN KEY (plan_id) REFERENCES [membership_plan](id)`; `CONSTRAINT fk_membership_previous FOREIGN KEY (previous_membership_id) REFERENCES [membership](id)`; `CONSTRAINT fk_membership_created_by FOREIGN KEY (created_by_user_id) REFERENCES [user_account](id)`; `CONSTRAINT ck_membership_registration_type CHECK (registration_type IN ('NEW', 'RENEWAL'))`; `CONSTRAINT ck_membership_channel CHECK (channel IN ('ONLINE', 'RECEPTION'))`; `CONSTRAINT ck_membership_status CHECK (status IN ('PENDING_PAYMENT', 'SCHEDULED', 'ACTIVE', 'EXPIRED', 'CANCELLED'))`; `CONSTRAINT ck_membership_price_amount CHECK (price_amount >= 0)`; `CONSTRAINT ck_membership_duration CHECK (duration_days > 0)`; `CONSTRAINT ck_membership_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)`; `CONSTRAINT ck_membership_active_dates CHECK (`; `status NOT IN ('SCHEDULED', 'ACTIVE', 'EXPIRED')`; `OR (start_date IS NOT NULL AND end_date IS NOT NULL)`; `)`.

### membership_sport

[`backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql`:166-172](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql).

Creation columns/constraints: `membership_id BIGINT NOT NULL`; `sport_id BIGINT NOT NULL`; `CONSTRAINT pk_membership_sport PRIMARY KEY (membership_id, sport_id)`; `CONSTRAINT fk_ms_membership FOREIGN KEY (membership_id) REFERENCES [membership](id) ON DELETE CASCADE`; `CONSTRAINT fk_ms_sport FOREIGN KEY (sport_id) REFERENCES [sport](id)`.

### check_in

[`backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql`:177-191](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `member_id BIGINT NOT NULL`; `membership_id BIGINT NULL`; `checked_in_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `result NVARCHAR(20) NOT NULL`; `denial_reason NVARCHAR(255) NULL`; `recorded_by BIGINT NOT NULL`; `note NVARCHAR(255) NULL`; `CONSTRAINT pk_check_in PRIMARY KEY (id)`; `CONSTRAINT fk_check_in_member FOREIGN KEY (member_id) REFERENCES [member_profile](user_id)`; `CONSTRAINT fk_check_in_membership FOREIGN KEY (membership_id) REFERENCES [membership](id)`; `CONSTRAINT fk_check_in_recorded_by FOREIGN KEY (recorded_by) REFERENCES [user_account](id)`; `CONSTRAINT ck_check_in_result CHECK (result IN ('ALLOWED', 'DENIED'))`.

### role

[`backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql`:4-12](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) PRIMARY KEY`; `code NVARCHAR(30) NOT NULL UNIQUE`; `name NVARCHAR(50) NOT NULL`; `description NVARCHAR(255) NULL`; `is_system BIT NOT NULL DEFAULT 1`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`.

### permission

[`backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql`:15-22](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) PRIMARY KEY`; `code NVARCHAR(60) NOT NULL UNIQUE`; `name NVARCHAR(100) NOT NULL`; `description NVARCHAR(255) NULL`; `module NVARCHAR(30) NOT NULL`; `display_order INT NOT NULL DEFAULT 0`.

### role_permission

[`backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql`:25-31](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql).

Creation columns/constraints: `role_id BIGINT NOT NULL`; `permission_id BIGINT NOT NULL`; `PRIMARY KEY (role_id, permission_id)`; `CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE`; `CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE`.

### user_account

[`backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql`:34-52](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) PRIMARY KEY`; `email NVARCHAR(255) NOT NULL UNIQUE`; `password_hash NVARCHAR(100) NOT NULL`; `full_name NVARCHAR(100) NOT NULL`; `phone NVARCHAR(20) NULL`; `date_of_birth DATE NULL`; `address NVARCHAR(255) NULL`; `avatar_url NVARCHAR(500) NULL`; `role_id BIGINT NOT NULL`; `status NVARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`; `last_login_at DATETIME2(0) NULL`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`; `created_by BIGINT NULL`; `updated_by BIGINT NULL`; `version INT NOT NULL DEFAULT 0`; `CONSTRAINT fk_user_account_role FOREIGN KEY (role_id) REFERENCES role(id)`.

### member_profile

[`backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql`:4-16](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql).

Creation columns/constraints: `user_id BIGINT NOT NULL`; `member_code NVARCHAR(20) NOT NULL`; `main_goal NVARCHAR(30) NULL`; `current_level NVARCHAR(20) NOT NULL DEFAULT 'BEGINNER'`; `joined_on DATE NOT NULL DEFAULT CAST(SYSDATETIME() AS DATE)`; `staff_note NVARCHAR(500) NULL`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`; `CONSTRAINT pk_member_profile PRIMARY KEY (user_id)`; `CONSTRAINT fk_member_profile_user FOREIGN KEY (user_id) REFERENCES [user_account](id) ON DELETE CASCADE`; `CONSTRAINT uq_member_profile_code UNIQUE (member_code)`.

### coach_profile

[`backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql`:19-34](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql).

Creation columns/constraints: `user_id BIGINT NOT NULL`; `primary_sport_id BIGINT NULL`; `headline NVARCHAR(150) NULL`; `bio NVARCHAR(1000) NULL`; `years_experience INT NULL`; `specialties NVARCHAR(255) NULL`; `is_public BIT NOT NULL DEFAULT 1`; `display_order INT NOT NULL DEFAULT 0`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `updated_at DATETIME2(0) NULL`; `CONSTRAINT pk_coach_profile PRIMARY KEY (user_id)`; `CONSTRAINT fk_coach_profile_user FOREIGN KEY (user_id) REFERENCES [user_account](id) ON DELETE CASCADE`; `CONSTRAINT ck_coach_exp CHECK (years_experience >= 0)`.

### coach_certification

[`backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql`:37-52](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `coach_id BIGINT NOT NULL`; `sport_id BIGINT NULL`; `name NVARCHAR(150) NOT NULL`; `issuer NVARCHAR(150) NULL`; `issued_on DATE NULL`; `expires_on DATE NULL`; `document_url NVARCHAR(500) NULL`; `is_verified BIT NOT NULL DEFAULT 0`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `CONSTRAINT pk_coach_certification PRIMARY KEY (id)`; `CONSTRAINT fk_cc_coach FOREIGN KEY (coach_id) REFERENCES [coach_profile](user_id) ON DELETE CASCADE`; `CONSTRAINT ck_cc_dates CHECK (expires_on IS NULL OR issued_on IS NULL OR expires_on >= issued_on)`.

### password_reset_token

[`backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql`:55-65](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `user_id BIGINT NOT NULL`; `token_hash NVARCHAR(100) NOT NULL`; `expires_at DATETIME2(0) NOT NULL`; `used_at DATETIME2(0) NULL`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `CONSTRAINT pk_password_reset_token PRIMARY KEY (id)`; `CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES [user_account](id) ON DELETE CASCADE`; `CONSTRAINT uq_prt_hash UNIQUE (token_hash)`.

### activity_log

[`backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql`:68-82](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql).

Creation columns/constraints: `id BIGINT IDENTITY(1,1) NOT NULL`; `actor_user_id BIGINT NULL`; `action NVARCHAR(60) NOT NULL`; `entity_type NVARCHAR(40) NOT NULL`; `entity_id BIGINT NULL`; `entity_code NVARCHAR(40) NULL`; `summary NVARCHAR(255) NULL`; `details NVARCHAR(MAX) NULL`; `ip_address NVARCHAR(45) NULL`; `created_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `CONSTRAINT pk_activity_log PRIMARY KEY (id)`; `CONSTRAINT fk_al_actor FOREIGN KEY (actor_user_id) REFERENCES [user_account](id)`; `CONSTRAINT ck_al_details_json CHECK (details IS NULL OR ISJSON(details) = 1)`.

### user_avatar

[`backend/src/main/resources/db/migration/identity/V33__create_user_avatar.sql`:8-18](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/d20833c3c16e4f7d95d12b6df21798fc528bd559/backend/src/main/resources/db/migration/identity/V33__create_user_avatar.sql).

Creation columns/constraints: `user_id BIGINT NOT NULL`; `content_type NVARCHAR(30) NOT NULL`; `size_bytes INT NOT NULL`; `data VARBINARY(MAX) NOT NULL`; `updated_at DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()`; `CONSTRAINT pk_user_avatar PRIMARY KEY (user_id)`; `CONSTRAINT fk_user_avatar_user FOREIGN KEY (user_id) REFERENCES [user_account](id) ON DELETE CASCADE`; `CONSTRAINT ck_user_avatar_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp'))`; `CONSTRAINT ck_user_avatar_size CHECK (size_bytes > 0 AND size_bytes <= 2097152)`.

## Later schema operations and separate objects


| Category | File | Line | Name/symbol |
| --- | --- | --- | --- |
| DYNAMIC_EXEC | backend/src/main/resources/db/demo/V28__seed_demo_accounts.sql | 14 | @LockResult |
| TABLE_VARIABLE_DECLARATION | backend/src/main/resources/db/demo/V28__seed_demo_accounts.sql | 23 | @Accounts |
| TABLE_VARIABLE_DECLARATION | backend/src/main/resources/db/demo/V28__seed_demo_accounts.sql | 75 | @Cards |
| DYNAMIC_EXEC | backend/src/main/resources/db/demo/V29__seed_demo_classes_and_sessions.sql | 12 | @LockResult |
| TABLE_VARIABLE_DECLARATION | backend/src/main/resources/db/demo/V29__seed_demo_classes_and_sessions.sql | 20 | @Classes |
| TABLE_VARIABLE_DECLARATION | backend/src/main/resources/db/demo/V29__seed_demo_classes_and_sessions.sql | 33 | @Packages |
| TABLE_VARIABLE_DECLARATION | backend/src/main/resources/db/demo/V29__seed_demo_classes_and_sessions.sql | 37 | @Registrations |
| TABLE_VARIABLE_DECLARATION | backend/src/main/resources/db/demo/V29__seed_demo_classes_and_sessions.sql | 77 | @Anchors |
| TABLE_VARIABLE_DECLARATION | backend/src/main/resources/db/demo/V29__seed_demo_classes_and_sessions.sql | 144 | @Sessions |
| TABLE_VARIABLE_DECLARATION | backend/src/main/resources/db/demo/V29__seed_demo_classes_and_sessions.sql | 229 | @Bookings |
| TABLE_VARIABLE_DECLARATION | backend/src/main/resources/db/demo/V29__seed_demo_classes_and_sessions.sql | 270 | @Balances |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql | 68 | ix_member_card_lookup |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql | 221 | ix_spr_member_status |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql | 252 | ix_refund_request_status |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql | 298 | ix_class_session_date_status |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql | 325 | ix_booking_member_session |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql | 330 | check_in |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql | 331 | check_in |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql | 332 | check_in |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql | 333 | check_in |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V11__add_refunded_status.sql | 6 | sport_package_registration |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V11__add_refunded_status.sql | 7 | sport_package_registration |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V13__add_session_minutes_to_sport_package.sql | 15 | sport_package |
| DYNAMIC_EXEC | backend/src/main/resources/db/migration/catalog/V13__add_session_minutes_to_sport_package.sql | 24 | sp_executesql |
| DYNAMIC_EXEC | backend/src/main/resources/db/migration/catalog/V13__add_session_minutes_to_sport_package.sql | 36 | sp_executesql |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql | 43 | ix_payment_member_id |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql | 44 | ix_payment_payment_time |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql | 45 | ix_payment_status |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql | 54 | member_card |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql | 64 | sport_package_registration |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql | 74 | refund_request |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql | 103 | ix_invoice_payment_id |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql | 104 | ix_invoice_member_id |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql | 105 | ix_invoice_issue_date |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql | 127 | ix_invoice_line_invoice_id |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V15__create_class_waitlist_table.sql | 25 | ix_waitlist_session_status_joined |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V15__create_class_waitlist_table.sql | 26 | ix_waitlist_member_status |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql | 24 | ix_session_plan_class |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql | 41 | ix_session_plan_step_plan |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql | 65 | ix_attendance_session |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql | 66 | ix_attendance_member |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql | 146 | ix_coach_feedback_member |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql | 147 | ix_coach_feedback_session |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql | 37 | ix_support_req_member |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql | 38 | ix_support_req_status |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql | 54 | ix_support_msg_request |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql | 108 | ix_ai_conv_user |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql | 124 | ix_ai_msg_conversation |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql | 58 | ix_workout_plan_member |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql | 132 | ix_plan_review_coach |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql | 22 | ix_notification_user_unread |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V20__align_schema_with_topic.sql | 12 | member_card |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V20__align_schema_with_topic.sql | 13 | member_card |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V20__align_schema_with_topic.sql | 17 | sport_class |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V20__align_schema_with_topic.sql | 19 | sport_class |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V20__align_schema_with_topic.sql | 20 | ix_sport_class_coach |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V20__align_schema_with_topic.sql | 23 | booking |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V20__align_schema_with_topic.sql | 25 | booking |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V20__align_schema_with_topic.sql | 28 | ux_booking_active |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V21__payment_pending_adjustments.sql | 14 | payment |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V21__payment_pending_adjustments.sql | 15 | payment |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V21__payment_pending_adjustments.sql | 23 | payment |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V21__payment_pending_adjustments.sql | 38 | ux_member_card_payment |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V21__payment_pending_adjustments.sql | 54 | ux_package_reg_payment |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V21__payment_pending_adjustments.sql | 59 | payment |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V21__payment_pending_adjustments.sql | 60 | payment |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql | 31 | member_card |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql | 44 | member_card |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql | 55 | member_card |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql | 68 | membership_card_tier |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql | 81 | membership_card_tier |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql | 93 | ux_membership_card_tier_rank |
| DYNAMIC_EXEC | backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql | 108 | sp_executesql |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql | 289 | ux_member_card_replaces_in_force |
| DYNAMIC_EXEC | backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql | 295 | sp_executesql |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V23__add_session_cancellation_and_schedule_indexes.sql | 3 | class_session |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V23__add_session_cancellation_and_schedule_indexes.sql | 6 | class_session |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V23__add_session_cancellation_and_schedule_indexes.sql | 9 | class_session |
| TABLE_ALTER | backend/src/main/resources/db/migration/catalog/V23__add_session_cancellation_and_schedule_indexes.sql | 12 | class_session |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V23__add_session_cancellation_and_schedule_indexes.sql | 24 | ix_class_session_coach_date |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V23__add_session_cancellation_and_schedule_indexes.sql | 35 | ix_class_session_facility_date |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V23__add_session_cancellation_and_schedule_indexes.sql | 46 | ix_booking_session_status |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V26__add_front_desk_queue_indexes.sql | 2 | ix_member_card_queue |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V26__add_front_desk_queue_indexes.sql | 4 | ix_spr_queue |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V27__refund_request_payment_link.sql | 23 | ux_refund_request_payment_live |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V27__refund_request_payment_link.sql | 28 | ix_refund_request_registration |
| VIEW_CREATE | backend/src/main/resources/db/migration/catalog/V30__create_finance_report_views.sql | 27 | dbo.vw_payment_ledger |
| VIEW_CREATE | backend/src/main/resources/db/migration/catalog/V30__create_finance_report_views.sql | 69 | dbo.vw_paid_payment |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V30__create_finance_report_views.sql | 77 | ix_payment_status_time |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql | 49 | ux_cash_collection |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql | 50 | ux_cash_payout |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql | 51 | ix_cash_time |
| TRIGGER_CREATE | backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql | 54 | dbo.tr_cash_coverage_immutable |
| TRIGGER_CREATE | backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql | 66 | dbo.tr_cash_event_immutable |
| TRIGGER_CREATE | backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql | 73 | dbo.tr_cash_event_source |
| TRIGGER_CREATE | backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql | 84 | dbo.tr_cash_collection |
| TRIGGER_CREATE | backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql | 141 | dbo.tr_cash_refund |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql | 156 | ux_membership_one_pending |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql | 161 | ix_membership_member_status |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql | 193 | ix_check_in_member |
| INDEX_CREATE | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql | 194 | ix_check_in_time |
| INDEX_CREATE | backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql | 54 | ux_user_account_phone |
| INDEX_CREATE | backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql | 55 | ix_user_account_role_status |
| INDEX_CREATE | backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql | 84 | ix_activity_log_created |
| INDEX_CREATE | backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql | 85 | ix_activity_log_entity |
| INDEX_CREATE | backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql | 86 | ix_activity_log_actor |

No static DROP TABLE or sp_rename declaration was found by the two scans. Under the assumptions that all supported migrations execute successfully, conditional guards permit expected creation, and default schema matches the required cohort, the candidate end-of-migration permanent table-name set is 62. This is STATIC_INFERRED and does not prove a live effective schema, prior database contents, constraints or checksum validity.

## Entity mapping cross-check


| Entity | Declared table/schema | Matching CREATE | Outcome |
| --- | --- | --- | --- |
| AgeGroup | age_group/<default> | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:29 | SOURCE_NAME_MATCH |
| Booking | booking/<default> | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:303 | SOURCE_NAME_MATCH |
| CheckIn | check_in/<default> | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:177 | SOURCE_NAME_MATCH |
| ClassSession | class_session/<default> | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:274 | SOURCE_NAME_MATCH |
| Facility | facility/<default> | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:46 | SOURCE_NAME_MATCH |
| MemberCard | member_card/<default> | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:49 | SOURCE_NAME_MATCH |
| Membership | membership/<default> | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:117 | SOURCE_NAME_MATCH |
| MembershipCardTier | membership_card_tier/<default> | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:22 | SOURCE_NAME_MATCH |
| MembershipPlan | membership_plan/<default> | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:66 | SOURCE_NAME_MATCH |
| PlanFeature | plan_feature/<default> | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:105 | SOURCE_NAME_MATCH |
| RefundRequest | refund_request/<default> | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:226 | SOURCE_NAME_MATCH |
| Sport | sport/<default> | backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql:9 | SOURCE_NAME_MATCH |
| SportClass | sport_class/<default> | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:257 | SOURCE_NAME_MATCH |
| SportPackage | sport_package/<default> | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:73 | SOURCE_NAME_MATCH |
| SportPackageRegistration | sport_package_registration/<default> | backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql:190 | SOURCE_NAME_MATCH |
| ActivityLog | activity_log/<default> | backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql:68 | SOURCE_NAME_MATCH |
| CoachCertification | coach_certification/<default> | backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql:37 | SOURCE_NAME_MATCH |
| CoachProfile | coach_profile/<default> | backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql:19 | SOURCE_NAME_MATCH |
| CoachSport | coach_sport/<default> | backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql:64 | SOURCE_NAME_MATCH |
| MemberProfile | member_profile/<default> | backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql:4 | SOURCE_NAME_MATCH |
| PasswordResetToken | password_reset_token/<default> | backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql:55 | SOURCE_NAME_MATCH |
| Permission | permission/<default> | backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql:15 | SOURCE_NAME_MATCH |
| Role | role/<default> | backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql:4 | SOURCE_NAME_MATCH |
| UserAccount | user_account/<default> | backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql:34 | SOURCE_NAME_MATCH |
| Invoice | invoice/<default> | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql:82 | SOURCE_NAME_MATCH |
| InvoiceLine | invoice_line/<default> | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql:111 | SOURCE_NAME_MATCH |
| Payment | payment/<default> | backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql:22 | SOURCE_NAME_MATCH |

The complete entity/table reconciliation (`evidence/entity-table-reconciliation.json` - local review reference, not published) retains each source path. Cash tables are JDBC/trigger reporting context, not missing training entities. Absence of JPA mappings for future V16-V19 tables is intentional scaffolding, not proof of live schema drift.

## Repository query inventory

| Repository | Declared query/mutation symbols | Evidence |
| --- | --- | --- |
| AgeGroupRepository | findByCode, findByIsActiveTrueOrderByDisplayOrderAscIdAsc | EV-268 (`NX-01-evidence-index.md#ev-268` - local review reference, not published) |
| BookingRepository | countBySessionIdAndStatus, getSessionId, getBookingCount, findByBookingCode, getBookingId, getSessionId, yet, lock | EV-269 (`NX-01-evidence-index.md#ev-269` - local review reference, not published) |
| CheckInRepository | findTop5ByMemberIdOrderByCheckedInAtDescIdDesc, findByMemberIdOrderByCheckedInAtDesc, existsByBookingIdAndResult | EV-270 (`NX-01-evidence-index.md#ev-270` - local review reference, not published) |
| ClassSessionRepository | findBySessionDateAndStatus, findBySportIdAndSessionDateAndStatus, findAll, Lock, Modifying, between, yet, getClassId, getSessionCount | EV-271 (`NX-01-evidence-index.md#ev-271` - local review reference, not published) |
| CoachScheduleReadRepository | getId, getCoachId | EV-272 (`NX-01-evidence-index.md#ev-272` - local review reference, not published) |
| FacilityRepository | Lock, findByCode | EV-273 (`NX-01-evidence-index.md#ev-273` - local review reference, not published) |
| MemberCardRepository | Modifying, Modifying, findByStatus, findFirstByMemberIdAndStatusOrderByCreatedAtAscIdAsc, findByCardCode, findByMemberIdOrderByCreatedAtDesc, findByMemberIdAndStatus, findByPaymentId, payment, locked | EV-274 (`NX-01-evidence-index.md#ev-274` - local review reference, not published) |
| MembershipCardTierRepository | findByCode, findByIsActiveTrue, Lock | EV-275 (`NX-01-evidence-index.md#ev-275` - local review reference, not published) |
| MembershipPlanRepository | existsByCodeIgnoreCase, findByStatusOrderByDisplayOrderAscIdAsc, findByIdAndStatus, findWithDetailsById, findWithDetailsByIdIn | EV-276 (`NX-01-evidence-index.md#ev-276` - local review reference, not published) |
| MembershipRepository | existsByMemberIdAndStatus, findAllByMemberIdOrderByStartDateDesc, findByStatusAndStartDateLessThanEqual, findByStatusAndEndDateLessThan | EV-277 (`NX-01-evidence-index.md#ev-277` - local review reference, not published) |
| RefundRequestRepository | findByRefundCode, findByMemberIdOrderByCreatedAtDesc, findByStatusOrderByCreatedAtAsc, statuses, locked, Lock | EV-278 (`NX-01-evidence-index.md#ev-278` - local review reference, not published) |
| SessionCancellationRepository | query, update, update, CancellationResult, copyOf | EV-279 (`NX-01-evidence-index.md#ev-279` - local review reference, not published) |
| SportClassRepository | findByCode, findBySportIdAndIsActiveTrue, findWithDetailsById, findAll | EV-280 (`NX-01-evidence-index.md#ev-280` - local review reference, not published) |
| SportPackageRegistrationRepository | Modifying, Modifying, findByStatus, findByRegistrationCode, findByMemberIdOrderByStartDateDesc, findByPaymentId, locked, Transactional, Transactional, getStatus, getStartDate, getEndDate | EV-281 (`NX-01-evidence-index.md#ev-281` - local review reference, not published) |
| SportPackageRepository | findByCode, findByIsActiveTrue, findBySportIdAndIsActiveTrue, findBySportIdAndTrainingFormatAndIsActiveTrue | EV-282 (`NX-01-evidence-index.md#ev-282` - local review reference, not published) |
| SportRepository | findByIsActiveTrueOrderByDisplayOrderAscIdAsc | EV-283 (`NX-01-evidence-index.md#ev-283` - local review reference, not published) |
| ActivityLogRepository | Inherited/custom implementation; inspect source | EV-284 (`NX-01-evidence-index.md#ev-284` - local review reference, not published) |
| CoachCertificationRepository | findByCoachIdOrderByIdAsc | EV-285 (`NX-01-evidence-index.md#ev-285` - local review reference, not published) |
| CoachProfileRepository | Lock, findWithAccountById | EV-286 (`NX-01-evidence-index.md#ev-286` - local review reference, not published) |
| CoachSportRepository | countByIdCoachId, findByIdCoachIdIn | EV-287 (`NX-01-evidence-index.md#ev-287` - local review reference, not published) |
| MemberProfileRepository | findByMemberCode | EV-288 (`NX-01-evidence-index.md#ev-288` - local review reference, not published) |
| PasswordResetTokenRepository | findByTokenHash | EV-289 (`NX-01-evidence-index.md#ev-289` - local review reference, not published) |
| PermissionRepository | findByCode | EV-290 (`NX-01-evidence-index.md#ev-290` - local review reference, not published) |
| RoleRepository | findByCode | EV-291 (`NX-01-evidence-index.md#ev-291` - local review reference, not published) |
| UserRepository | EntityGraph, existsByEmailIgnoreCase, existsByPhone | EV-292 (`NX-01-evidence-index.md#ev-292` - local review reference, not published) |
| CashFlowRepository | queryForObject, valueOf, queryForObject, totals, Group, query, Totals, getLong, MapSqlParameterSource, toLocalDateTime | EV-293 (`NX-01-evidence-index.md#ev-293` - local review reference, not published) |
| FinanceReportRepository | reports, totals, totals, totals, query, IllegalArgumentException, query, MapSqlParameterSource, ledgerWhere, queryForObject, MapSqlParameterSource, ledgerWhere, addValue, addValue, query, query, queryForObject, Totals, MapSqlParameterSource, add, addValue, add, addValue, add, addValue, add, addValue, add, addValue, add, addValue, isEmpty, replace, getLong, FinanceTransactionResponse | EV-294 (`NX-01-evidence-index.md#ev-294` - local review reference, not published) |
| InvoiceLineRepository | findByInvoiceId, findByItemTypeAndItemReferenceId, item | EV-295 (`NX-01-evidence-index.md#ev-295` - local review reference, not published) |
| InvoiceRepository | findByInvoiceNumber, findByPaymentId, findByPaymentIdIn, findByMemberIdOrderByIssueDateDesc, findByStatus | EV-296 (`NX-01-evidence-index.md#ev-296` - local review reference, not published) |
| PaymentRepository | findByPaymentCode, lock, findByMemberIdOrderByCreatedAtDesc, member, status, findByPaymentStatus | EV-297 (`NX-01-evidence-index.md#ev-297` - local review reference, not published) |

## Flow 4 to 6 schema and policy gaps

V16 uses class_id for session_plan, not member_id/session_id; it lacks version/publication and personal assignment. Steps have a nonunique plan/order index and positive duration. attendance_record has unique session/member and nullable booking_id; independent FKs do not enforce a matching booking/member/session triple. Results have a unique session/member key, no attendance FK or author, and scores lack per-result/metric uniqueness and approved range enforcement. Feedback rating is bounded 1..5 but publication/private-note fields are absent. These remain source design gaps, not exposed runtime vulnerabilities.

V17 conversation/message and support definitions do not implement user isolation, retention, retrieval or handoff. V18 workout_plan is a second plan family with no approved bridge to V16; review states are DDL, not a functioning approval policy. V19 notification/unread storage does not implement delivery, recipient authorization or homework. Plan/message/score cascades require approved retention semantics before writers are added.

Existing MemberProfile/CoachProfile MapsId identities, class/session coach distinction, current booking-time debit, cancellation and check-in locks are preserved. Arrival check-in is separate from coach attendance; the self-training route remains ACK-only. No source/schema/data change resolved these open decisions. Source detail is in the gap matrix and semantic spot checks.
