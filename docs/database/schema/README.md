# Sportify Center - Database Schema

SQL Server 2022, managed by Flyway (`ddl-auto: validate`). Generated from migrations V1-V20 on main after PR #6.

- Tables: **58**
- Sequences: **11**
- In use by the Java code now: **26** (5 of them belong to the deprecated membership flow)
- Planned in the Flow 1-3 agent prompts: **4**
- Belong to Flow 1-3 but not planned yet: **6**
- Later flows (Flow 4, 5, 6 and announcements): **22**

## Files

| File | Area | Tables |
|---|---|---|
| [01-identity-and-access.md](01-identity-and-access.md) | Identity and Access | 11 |
| [02-catalog-and-membership.md](02-catalog-and-membership.md) | Catalog and Membership (Flow 1) | 13 |
| [03-classes-and-booking.md](03-classes-and-booking.md) | Classes and Booking (Flow 2) | 4 |
| [04-payments-and-refunds.md](04-payments-and-refunds.md) | Payments, Invoices and Refunds (Flow 3) | 4 |
| [05-training-and-attendance.md](05-training-and-attendance.md) | Training and Attendance (Flow 4) | 8 |
| [06-ai-workout-recommendation.md](06-ai-workout-recommendation.md) | AI Workout Recommendation (Flow 5) | 8 |
| [07-ai-assistant-and-support.md](07-ai-assistant-and-support.md) | AI Assistant and Support (Flow 6, F1-16) | 7 |
| [08-notifications-and-system.md](08-notifications-and-system.md) | Notifications and System | 3 |

## Status legend

| Status | Meaning |
|---|---|
| In use | Mapped by a JPA entity or a @JoinTable and used by the API today |
| In use (legacy, deprecated) | Old membership flow, kept for compatibility, not used by the new Flow 1 |
| Planned for Flow 1-3 (BE-xx) | A task in G3_Agent_Prompts_Flow1-3.xlsx maps it |
| Not planned yet | Part of Flow 1-3 screens but no agent prompt yet |
| Later (Flow 4/5/6) | Schema ready, used when that flow is built |

A column marked "Not mapped in the entity yet" exists in the table but the entity does not read or write it.

## All tables

| Table | File | Status |
|---|---|---|
| `activity_log` | [01-identity-and-access](01-identity-and-access.md#activity_log) | In use |
| `age_group` | [02-catalog-and-membership](02-catalog-and-membership.md#age_group) | In use |
| `ai_conversation` | [07-ai-assistant-and-support](07-ai-assistant-and-support.md#ai_conversation) | Later (Flow 6) |
| `ai_message` | [07-ai-assistant-and-support](07-ai-assistant-and-support.md#ai_message) | Later (Flow 6) |
| `announcement` | [08-notifications-and-system](08-notifications-and-system.md#announcement) | Later (notifications) |
| `assistant_quick_prompt` | [07-ai-assistant-and-support](07-ai-assistant-and-support.md#assistant_quick_prompt) | Later (Flow 6) |
| `assistant_setting` | [07-ai-assistant-and-support](07-ai-assistant-and-support.md#assistant_setting) | Later (Flow 6) |
| `assistant_topic` | [07-ai-assistant-and-support](07-ai-assistant-and-support.md#assistant_topic) | Later (Flow 6) |
| `attendance_correction` | [05-training-and-attendance](05-training-and-attendance.md#attendance_correction) | Later (Flow 4) |
| `attendance_record` | [05-training-and-attendance](05-training-and-attendance.md#attendance_record) | Later (Flow 4) |
| `booking` | [03-classes-and-booking](03-classes-and-booking.md#booking) | In use |
| `check_in` | [02-catalog-and-membership](02-catalog-and-membership.md#check_in) | In use |
| `class_session` | [03-classes-and-booking](03-classes-and-booking.md#class_session) | In use |
| `coach_certification` | [01-identity-and-access](01-identity-and-access.md#coach_certification) | In use |
| `coach_feedback` | [05-training-and-attendance](05-training-and-attendance.md#coach_feedback) | Later (Flow 4) |
| `coach_profile` | [01-identity-and-access](01-identity-and-access.md#coach_profile) | In use |
| `coach_sport` | [01-identity-and-access](01-identity-and-access.md#coach_sport) | Planned for Flow 1-3 (BE-01) |
| `facility` | [02-catalog-and-membership](02-catalog-and-membership.md#facility) | In use |
| `invoice` | [04-payments-and-refunds](04-payments-and-refunds.md#invoice) | Planned for Flow 1-3 (BE-16) |
| `invoice_line` | [04-payments-and-refunds](04-payments-and-refunds.md#invoice_line) | Planned for Flow 1-3 (BE-16) |
| `member_card` | [02-catalog-and-membership](02-catalog-and-membership.md#member_card) | In use |
| `member_profile` | [01-identity-and-access](01-identity-and-access.md#member_profile) | In use |
| `member_sport_interest` | [01-identity-and-access](01-identity-and-access.md#member_sport_interest) | Not planned yet: Flow 1 (sports a member likes) |
| `membership` | [02-catalog-and-membership](02-catalog-and-membership.md#membership) | In use (legacy, deprecated) |
| `membership_card_tier` | [02-catalog-and-membership](02-catalog-and-membership.md#membership_card_tier) | In use |
| `membership_plan` | [02-catalog-and-membership](02-catalog-and-membership.md#membership_plan) | In use (legacy, deprecated) |
| `membership_sport` | [02-catalog-and-membership](02-catalog-and-membership.md#membership_sport) | In use (legacy, deprecated) |
| `notification` | [08-notifications-and-system](08-notifications-and-system.md#notification) | Not planned yet: Flow 1-3 (booking and payment notifications) |
| `password_reset_token` | [01-identity-and-access](01-identity-and-access.md#password_reset_token) | In use |
| `payment` | [04-payments-and-refunds](04-payments-and-refunds.md#payment) | Planned for Flow 1-3 (BE-16) |
| `permission` | [01-identity-and-access](01-identity-and-access.md#permission) | In use |
| `plan_eligible_sport` | [02-catalog-and-membership](02-catalog-and-membership.md#plan_eligible_sport) | In use (legacy, deprecated) |
| `plan_feature` | [02-catalog-and-membership](02-catalog-and-membership.md#plan_feature) | In use (legacy, deprecated) |
| `plan_review` | [06-ai-workout-recommendation](06-ai-workout-recommendation.md#plan_review) | Later (Flow 5) |
| `recommendation_rule_set` | [06-ai-workout-recommendation](06-ai-workout-recommendation.md#recommendation_rule_set) | Later (Flow 5) |
| `recommendation_rule_weight` | [06-ai-workout-recommendation](06-ai-workout-recommendation.md#recommendation_rule_weight) | Later (Flow 5) |
| `refund_request` | [04-payments-and-refunds](04-payments-and-refunds.md#refund_request) | In use |
| `role` | [01-identity-and-access](01-identity-and-access.md#role) | In use |
| `role_permission` | [01-identity-and-access](01-identity-and-access.md#role_permission) | In use |
| `session_plan` | [05-training-and-attendance](05-training-and-attendance.md#session_plan) | Later (Flow 4) |
| `session_plan_step` | [05-training-and-attendance](05-training-and-attendance.md#session_plan_step) | Later (Flow 4) |
| `session_result` | [05-training-and-attendance](05-training-and-attendance.md#session_result) | Later (Flow 4) |
| `session_result_score` | [05-training-and-attendance](05-training-and-attendance.md#session_result_score) | Later (Flow 4) |
| `skill_metric` | [05-training-and-attendance](05-training-and-attendance.md#skill_metric) | Later (Flow 4) |
| `sport` | [02-catalog-and-membership](02-catalog-and-membership.md#sport) | In use |
| `sport_class` | [03-classes-and-booking](03-classes-and-booking.md#sport_class) | In use |
| `sport_package` | [02-catalog-and-membership](02-catalog-and-membership.md#sport_package) | In use |
| `sport_package_registration` | [02-catalog-and-membership](02-catalog-and-membership.md#sport_package_registration) | In use |
| `support_request` | [07-ai-assistant-and-support](07-ai-assistant-and-support.md#support_request) | Not planned yet: Flow 1 (F1-16 Support Requests) |
| `support_request_message` | [07-ai-assistant-and-support](07-ai-assistant-and-support.md#support_request_message) | Not planned yet: Flow 1 (F1-16 Support Requests) |
| `system_setting` | [08-notifications-and-system](08-notifications-and-system.md#system_setting) | Not planned yet: Flow 3 (bank transfer settings) |
| `user_account` | [01-identity-and-access](01-identity-and-access.md#user_account) | In use |
| `waitlist_entry` | [03-classes-and-booking](03-classes-and-booking.md#waitlist_entry) | Not planned yet: Flow 2 (waitlist when a session is full) |
| `workout_plan` | [06-ai-workout-recommendation](06-ai-workout-recommendation.md#workout_plan) | Later (Flow 5) |
| `workout_plan_candidate` | [06-ai-workout-recommendation](06-ai-workout-recommendation.md#workout_plan_candidate) | Later (Flow 5) |
| `workout_plan_day` | [06-ai-workout-recommendation](06-ai-workout-recommendation.md#workout_plan_day) | Later (Flow 5) |
| `workout_plan_exercise` | [06-ai-workout-recommendation](06-ai-workout-recommendation.md#workout_plan_exercise) | Later (Flow 5) |
| `workout_plan_week` | [06-ai-workout-recommendation](06-ai-workout-recommendation.md#workout_plan_week) | Later (Flow 5) |

## Sequences

| Sequence | Start | Created in |
|---|---|---|
| `seq_member_code` | 1 | V2 |
| `seq_registration_code` | 1000 | V9 |
| `seq_card_code` | 1000 | V10 |
| `seq_package_reg_code` | 1000 | V10 |
| `seq_refund_code` | 1000 | V10 |
| `seq_booking_code` | 1000 | V10 |
| `seq_class_code` | 1000 | V10 |
| `seq_payment_code` | 1000 | V14 |
| `seq_invoice_number` | 1000 | V14 |
| `seq_support_request_code` | 1000 | V17 |
| `seq_workout_plan_code` | 1000 | V18 |

## Migrations

| Version | File |
|---|---|
| V1 | `V1__init_identity_schema.sql` |
| V2 | `V2__add_identity_tables.sql` |
| V3 | `V3__seed_identity_data.sql` |
| V4 | `V4__seed_manager_account.sql` |
| V5 | `V5__create_catalog_membership_tables.sql` |
| V6 | `V6__seed_catalog_data.sql` |
| V9 | `V9__add_registration_sequence.sql` |
| V10 | `V10__context_refresh_schema.sql` |
| V11 | `V11__add_refunded_status.sql` |
| V12 | `V12__align_sport_package_seed_with_figma.sql` |
| V13 | `V13__add_session_minutes_to_sport_package.sql` |
| V14 | `V14__create_payment_and_invoice_tables.sql` |
| V15 | `V15__create_class_waitlist_table.sql` |
| V16 | `V16__create_training_and_attendance_tables.sql` |
| V17 | `V17__create_support_and_ai_tables.sql` |
| V18 | `V18__create_workout_recommendation_tables.sql` |
| V19 | `V19__create_notification_and_system_tables.sql` |
| V20 | `V20__align_schema_with_topic.sql` |

Versions V7 and V8 do not exist (skipped numbers). New migrations use the next free number (V21 and up).
