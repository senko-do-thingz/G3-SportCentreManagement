# Existing schema reuse inventory

All table facts are SOURCE_CONFIRMED static declarations at the [baseline](01-scope-and-source-baseline.md), not deployed tables. Recount: 27 standard SQL migrations plus 2 demo files, 62 permanent CREATE TABLE occurrences with 62 distinct normalized names, 27 Java @Entity declarations. Latest standard version is V33 (user_avatar); demo V28/V29 share the global version sequence. No V7, V8, V24 or V25 is inferred to exist. No Flow 4 JPA entity, repository, service or training write controller was found in the current Java inventory. DB deployment and actual row distributions are UNVERIFIED_RUNTIME.

SQL object qualification is separate from migration directories. `identity` and `catalog` are Flyway resource folders, not evidence of SQL schemas with those names. V16 uses unqualified `[table]` names and current JPA mappings omit a schema; neither proves a runtime login's default schema. Finance [V31](../../backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql#L9) explicitly creates `dbo.cash_coverage`, `dbo.cash_capture_ticket` and `dbo.cash_event`. No new training SQL schema, default-schema configuration or schema-level authorization grant is selected. Future physical approval must resolve qualification against actual disposable catalog metadata before generating DDL/FKs.

## Reproducible inventory method

`git ls-files backend/src/main/resources/db` selects tracked SQL; remove block/line comments, enumerate permanent CREATE TABLE identifiers, and enumerate versions from V-number filenames. Table variables, views, sequences, triggers and indexes are excluded. No DROP TABLE declaration was found. Conditional IF NOT EXISTS declarations still count as declarations. Java census uses tracked @Entity files, separately from SQL. [source-inventory.json](source-inventory.json) records every path/version/created name and LF-normalized SHA-256; `python -m unittest discover -s scripts/ci -p 'test_nx04*.py'` verifies current content. These bounded lexical counts cannot certify dynamic SQL or a live schema; unsupported future syntax requires manual review.

| Migration | File | Location | Permanent CREATE TABLE declarations |
| --- | --- | --- | --- |
| V1 | [V1__init_identity_schema.sql](../../backend/src/main/resources/db/migration/identity/V1__init_identity_schema.sql) | standard | 4 |
| V2 | [V2__add_identity_tables.sql](../../backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql) | standard | 5 |
| V3 | [V3__seed_identity_data.sql](../../backend/src/main/resources/db/migration/identity/V3__seed_identity_data.sql) | standard | 0 |
| V4 | [V4__seed_manager_account.sql](../../backend/src/main/resources/db/migration/identity/V4__seed_manager_account.sql) | standard | 0 |
| V5 | [V5__create_catalog_membership_tables.sql](../../backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql) | standard | 9 |
| V6 | [V6__seed_catalog_data.sql](../../backend/src/main/resources/db/migration/catalog/V6__seed_catalog_data.sql) | standard | 0 |
| V9 | [V9__add_registration_sequence.sql](../../backend/src/main/resources/db/migration/catalog/V9__add_registration_sequence.sql) | standard | 0 |
| V10 | [V10__context_refresh_schema.sql](../../backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql) | standard | 8 |
| V11 | [V11__add_refunded_status.sql](../../backend/src/main/resources/db/migration/catalog/V11__add_refunded_status.sql) | standard | 0 |
| V12 | [V12__align_sport_package_seed_with_figma.sql](../../backend/src/main/resources/db/migration/catalog/V12__align_sport_package_seed_with_figma.sql) | standard | 0 |
| V13 | [V13__add_session_minutes_to_sport_package.sql](../../backend/src/main/resources/db/migration/catalog/V13__add_session_minutes_to_sport_package.sql) | standard | 0 |
| V14 | [V14__create_payment_and_invoice_tables.sql](../../backend/src/main/resources/db/migration/catalog/V14__create_payment_and_invoice_tables.sql) | standard | 3 |
| V15 | [V15__create_class_waitlist_table.sql](../../backend/src/main/resources/db/migration/catalog/V15__create_class_waitlist_table.sql) | standard | 1 |
| V16 | [V16__create_training_and_attendance_tables.sql](../../backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql) | standard | 8 |
| V17 | [V17__create_support_and_ai_tables.sql](../../backend/src/main/resources/db/migration/catalog/V17__create_support_and_ai_tables.sql) | standard | 7 |
| V18 | [V18__create_workout_recommendation_tables.sql](../../backend/src/main/resources/db/migration/catalog/V18__create_workout_recommendation_tables.sql) | standard | 8 |
| V19 | [V19__create_notification_and_system_tables.sql](../../backend/src/main/resources/db/migration/catalog/V19__create_notification_and_system_tables.sql) | standard | 5 |
| V20 | [V20__align_schema_with_topic.sql](../../backend/src/main/resources/db/migration/catalog/V20__align_schema_with_topic.sql) | standard | 0 |
| V21 | [V21__payment_pending_adjustments.sql](../../backend/src/main/resources/db/migration/catalog/V21__payment_pending_adjustments.sql) | standard | 0 |
| V22 | [V22__add_upgrade_tracking_to_member_card.sql](../../backend/src/main/resources/db/migration/catalog/V22__add_upgrade_tracking_to_member_card.sql) | standard | 0 |
| V23 | [V23__add_session_cancellation_and_schedule_indexes.sql](../../backend/src/main/resources/db/migration/catalog/V23__add_session_cancellation_and_schedule_indexes.sql) | standard | 0 |
| V26 | [V26__add_front_desk_queue_indexes.sql](../../backend/src/main/resources/db/migration/catalog/V26__add_front_desk_queue_indexes.sql) | standard | 0 |
| V27 | [V27__refund_request_payment_link.sql](../../backend/src/main/resources/db/migration/catalog/V27__refund_request_payment_link.sql) | standard | 0 |
| V28 | [V28__seed_demo_accounts.sql](../../backend/src/main/resources/db/demo/V28__seed_demo_accounts.sql) | demo opt-in | 0 |
| V29 | [V29__seed_demo_classes_and_sessions.sql](../../backend/src/main/resources/db/demo/V29__seed_demo_classes_and_sessions.sql) | demo opt-in | 0 |
| V30 | [V30__create_finance_report_views.sql](../../backend/src/main/resources/db/migration/catalog/V30__create_finance_report_views.sql) | standard | 0 |
| V31 | [V31__cash_event_cutover.sql](../../backend/src/main/resources/db/migration/catalog/V31__cash_event_cutover.sql) | standard | 3 |
| V32 | [V32__seed_remaining_sport_facilities.sql](../../backend/src/main/resources/db/migration/catalog/V32__seed_remaining_sport_facilities.sql) | standard | 0 |
| V33 | [V33__create_user_avatar.sql](../../backend/src/main/resources/db/migration/identity/V33__create_user_avatar.sql) | standard | 1 |

## Flow 4 scaffold and gap audit (S04)

| Existing table | Keys, references and constraints | Reuse and missing protections |
| --- | --- | --- |
| session_plan | BIGINT identity PK; mandatory class_id -> sport_class.id and coach_id -> coach_profile.user_id; class index; title required; template BIT default 0 | Class plan content only. Cannot represent personal-only target, explicit publication, immutable version, recipients or exact session-use revision |
| session_plan_step | identity PK; plan_id FK with ON DELETE CASCADE; duration_minutes > 0; nonunique (plan_id, step_order) index | Ordered instructions; duplicate/negative order not prevented; published content/history must not share destructive mutable rows |
| attendance_record | identity PK; mandatory session/member/marked_by FKs; nullable booking FK; UNIQUE(session_id, member_id); PRESENT/ABSENT/LATE/EXCUSED check; member/session indexes | Existing current-row candidate. Booking FK proves existence only, not matching member/session. marked_by is any user FK, not Coach authorization; no stale guard |
| attendance_correction | identity PK; attendance record and user FKs; mandatory previous/new status/reason | Correction scaffold. No status CHECK on previous/new fields, prior revision token, chain uniqueness, immutability or correction-window enforcement |
| skill_metric | identity PK; sport FK; max_score INT default 10 | Possible governed dictionary. No approved unit/range/version, unique metric name, positive max_score check or steward workflow |
| session_result | identity PK; session/member FKs; UNIQUE(session_id, member_id); optional notes | Session outcome identity. Missing Coach/actor attribution, publication, revision history, booking consistency, field classification and concurrency |
| session_result_score | identity PK; result FK ON DELETE CASCADE; metric FK; score DECIMAL(5,2) NOT NULL | Numeric detail. No UNIQUE(result_id, metric_id), range check or sport-match constraint; zero is not missing; no approved metric meaning |
| coach_feedback | identity PK; session, Coach and Member FKs; rating 1..5; comments required; optional strengths/areas_for_improvement; member/session indexes | Evaluation scaffold. No uniqueness, correction lineage, audience visibility or private/public separation. Existing rating bound is not approved business policy |

All V16 creation/update defaults use SYSDATETIME and DATETIME2(0). Only the two child cascades above occur in V16; other V16 FKs have no declared delete cascade. Identity profile -> user cascades in S08 can interact with these blocking FKs. Do not assume account deletion cascades through training history.

## Existing contexts and identity

S05 supplies class_session: optional class/facility/Coach, required sport/date/TIME(0) schedule, capacity/booked_count and COACH_LED/SELF_TRAINING scaffold. sport_class.coach_id added in S06 is an optional class default, while class_session.coach_id is the observed teaching ownership target (S15/S16). A class default does not override a reassigned session. Booking (S05/S31) has session/member FKs, optional package registration/membership, unique booking_code and confirmed/cancelled states. S06's ux_booking_active enforces one CONFIRMED member/session pair; it allows a later new booking after cancellation. No separate class enrollment ledger was found in the inspected model; booking is not proof of permanent class membership.

MemberProfile and CoachProfile use @MapsId (S20/S21): canonical profile ID is user_account.id, not an independent staff/member ID. user_account has one role_id; S19 creates ROLE_ + role.code authority. S09 seeds MEMBER, COACH, RECEPTIONIST and MANAGER. ADMIN has no verified independent seeded role or Flow 4 grant; Manager's display description Administrator is not ADMIN evidence. role_permission rows are not equivalent to enforced object-level predicates. S22 considers only Receptionist/Manager staff for booking. No tenant discriminator was found in this scope; never invent tenant IDs. If multi-center partitioning is later adopted it needs a new signed scope and keys/predicates.

## Arrival and finance compatibility

S07 creates check_in; S05 adds nullable booking_id and package_registration_id. It retains required member and recorded_by FKs, optional legacy membership, ALLOWED/DENIED CHECK, note/denial_reason and member/time indexes. No check_in unique constraint/index on an ALLOWED booking was found. S10 serializes the arrival path with the session lock (S17), refreshes mutable context, checks previous ALLOWED, then saves both allowed and denied attempts and an audit event within @Transactional. The source path is an application guard; unrelated direct writers could bypass it.

S11 exposes POST/GET `/api/v1/check-ins` and `/api/v1/check-in`, Manager/Receptionist only. POST returns 201 for a saved DENIED business outcome as well as ALLOWED; missing member throws the existing not-found exception. Today's confirmed booking, matching requested booking when supplied, active date-valid package and no prior ALLOWED are checked. It does not implement Coach attendance or an attendance lateness cutoff. Without explicit booking selection it may choose the next unchecked booking on retry. GET accepts memberId and/or date, uses a center-local half-open day range (9999-12-31 has an open upper bound), and remains unpaged. Preserve these contracts.

S13 consumes a package session and reserves capacity during booking, under a session-first lock plus conditional updates; cancellation restores through existing services and disallows cancellation after ALLOWED arrival. S10 performs no package deduction. S14 self-training attendance returns an explicit persistence-pending acknowledgement without saving attendance or deducting sessions. It cannot be backfilled as participation. Coach roster reads (S15/S16) reload an active actor and constrain both initial access and the PII query by current session Coach. Manager's broad roster read is an existing schedule permission only.

## Audit, profiles and environment

S23 serializes caller-provided details, nullable actor/entity IDs, summary, IP and created time (S24). REQUIRED versus REQUIRES_NEW is caller-selectable; JSON serialization failure drops details. No general redaction, correlation ID column, before/after columns, immutable ledger or exactly-once guarantee is evidenced. Before/after would require an approved details allowlist or new model. S25 permits Manager activity-log reads; that is not a new training-note read grant. Source privacy risk is caller oversharing into generic details.

S01 uses Hibernate validate, open-in-view false, Flyway enabled with identity/catalog locations, clean-disabled true, out-of-order false and validate-on-migrate true. Default profile dev does not authorize use of its local datasource. S02 adds demo location only on explicit opt-in; S03 rejects prod+demo. Production suppresses SQL logging. S27 uses SQL Server Testcontainers under integration tags; no authorized disposable non-Docker SQL Server was established. S28 separates unit Surefire from integration Failsafe. SQL Server migration/integration runtime is NOT_RUN_APPROVAL_BLOCKED here; H2 is not a substitute. S29 runs Python CI guard tests on the no-Docker unit job and permits workflow_dispatch; feature branch push alone is outside its push branch filter.
