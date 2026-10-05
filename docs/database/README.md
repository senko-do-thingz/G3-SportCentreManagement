# Sportify Center - Database Design

This folder contains the proposed database design for **Sportify Center**, a single-location sports center
(six sports: Football, Badminton, Basketball, Volleyball, Swimming, Tennis) with four user roles:
Member, Coach, Receptionist and Center Manager.

The design is derived from the UI mockups in `context/` (Home + Flow 1 to Flow 6, 135 screens after the 10/05 product context refresh).

## Target Stack

| Layer | Choice |
|---|---|
| Language | Java 17+ (LTS) |
| Framework | Spring Boot 3.x |
| Persistence | Spring Data JPA (Hibernate 6) |
| Database | Microsoft SQL Server 2019+ (also works on Azure SQL) |
| Migrations | Flyway (`V1__init_schema.sql`, `V2__seed_reference_data.sql`) |
| Security | Spring Security (role + permission authorities) |

## Document Index

| File | Content |
|---|---|
| [01-identity-and-access.md](01-identity-and-access.md) | Accounts, roles, permissions, member and coach profiles, activity log |
| [02-catalog-and-membership.md](02-catalog-and-membership.md) | Sports, age groups, facilities, membership plans, memberships, check-in |
| [03-classes-and-booking.md](03-classes-and-booking.md) | Classes, scheduled sessions, bookings, waitlist |
| [04-payments-invoices-reports.md](04-payments-invoices-reports.md) | Payments, invoices, revenue reporting views |
| [05-training-attendance-progress.md](05-training-attendance-progress.md) | Session plans, attendance, results, skill scores, coach feedback |
| [06-ai-workout-recommendation.md](06-ai-workout-recommendation.md) | Recommendation rules, AI workout plans, coach review |
| [07-ai-assistant-and-support.md](07-ai-assistant-and-support.md) | AI assistant content, conversations, support requests |
| [08-notifications-and-system.md](08-notifications-and-system.md) | Notifications, announcements, system settings, code sequences |
| [09-business-rules-and-state-machines.md](09-business-rules-and-state-machines.md) | Cross-flow rules, state machines, transactions, scheduled jobs |
| [10-ddl-sqlserver.md](10-ddl-sqlserver.md) | Complete SQL Server DDL, views and seed data |
| [11-jpa-mapping-guide.md](11-jpa-mapping-guide.md) | Java 17 / Spring Boot 3 entity mapping conventions and examples |
| [12-screen-traceability.md](12-screen-traceability.md) | Screen ID -> tables matrix |

## Key Assumptions (from the mockups)

1. **One center only.** "This project has one center. No branch selector is required." (F3-10). There is no `center`
   or `branch` table; center details live in `system_setting`.
2. **One role per account.** Every screen shows a single role per user. Permissions per role are configurable (F1-03).
3. **Membership is sport-based.** A plan defines how many sports a member may choose (`max_sports`) from a pool of
   eligible sports. The chosen sports are stored per membership period.
4. **Membership starts only after payment confirmation** (F1-08, F1-11, F3-06). A pending payment never activates access.
5. **Class booking inside an active plan costs 0 VND** and never creates a payment (F2-07, F3-01).
6. **Center check-in (Receptionist) is different from class attendance (Coach)** (F1-13, F4-01, F4-10).
7. **AI never commits business actions.** The AI assistant does not book or register; AI workout plans become
   effective only after Coach approval (F5-07, F5-11, F6-06).
8. **Revenue counts only unique PAID payments** by paid date; Pending and Failed stay in the ledger (F3-10, F3-12).

## Global Conventions

| Topic | Convention |
|---|---|
| Table names | `snake_case`, singular (`booking`, `class_session`). Reserved words avoided (`user_account`, `sport_class`). |
| Primary key | `id BIGINT IDENTITY(1,1)`. 1:1 profile tables reuse the parent key (`member_profile.user_id`). |
| Business codes | Human readable codes (`MEM-0128`, `REG-1042`, `PAY-1098`, `INV-2026-1099`, `BK-2048`, `REQ-1082`, `CL-204`, `WP-0001`) stored in a separate `UNIQUE` column, generated from SQL Server `SEQUENCE` objects. |
| Text | `NVARCHAR` for every string column (Vietnamese names, plus avoids implicit conversion with the JDBC driver). |
| Enums | `NVARCHAR(20..40)` + `CHECK` constraint, mapped with `@Enumerated(EnumType.STRING)`. |
| Money | `DECIMAL(14,2)` in VND, mapped to `java.math.BigDecimal`. |
| Date / time | `DATE` -> `LocalDate`, `TIME(0)` -> `LocalTime`, `DATETIME2(0)` -> `LocalDateTime` stored in center local time (Asia/Ho_Chi_Minh). |
| Small numbers | `INT` (not `TINYINT`, which is unsigned in SQL Server but signed `Byte` in Java). |
| Booleans | `BIT` named `is_xxx` -> `boolean`. |
| JSON | `NVARCHAR(MAX)` + `CHECK (ISJSON(col) = 1)`, mapped as `String` or Hibernate JSON type. |
| Audit | `created_at`, `updated_at` on most tables; `created_by`, `updated_by` (user id, no FK) on master data. Explicit actor columns (`recorded_by`, `confirmed_by`) are real FKs. |
| Concurrency | `version INT` (`@Version`) on rows edited concurrently (sessions, memberships, payments, bookings). |
| Deletes | No physical delete for business data. Use `status` (`ARCHIVED`, `CANCELLED`, `INACTIVE`). `ON DELETE CASCADE` only for pure composition children. |
| Constraint names | `pk_`, `fk_`, `uq_`, `ck_`, `ix_` (index), `ux_` (unique / filtered unique index). |

## Module Overview

| Module | Tables | Main flows |
|---|---|---|
| Identity and access | `role`, `permission`, `role_permission`, `user_account`, `member_profile`, `member_sport_interest`, `coach_profile`, `coach_sport`, `coach_certification`, `password_reset_token`, `activity_log` | Flow 1 |
| Catalog and membership | `sport`, `age_group`, `facility`, `membership_plan`, `plan_eligible_sport`, `plan_feature`, `membership`, `membership_sport`, `sport_package`, `membership_card_tier`, `member_card`, `sport_package_registration`, `refund_request`, `check_in` | Flow 1, Home, Flow 3 |
| Classes and booking | `sport_class`, `class_session`, `booking`, `waitlist_entry` | Flow 2 |
| Payments and reports | `payment`, `invoice`, `invoice_line` + views | Flow 1, Flow 3 |
| Training and progress | `session_plan`, `session_plan_step`, `attendance_record`, `attendance_correction`, `skill_metric`, `session_result`, `session_result_score`, `coach_feedback` + views | Flow 4 |
| AI workout recommendation | `recommendation_rule_set`, `recommendation_rule_weight`, `workout_plan`, `workout_plan_candidate`, `workout_plan_week`, `workout_plan_day`, `workout_plan_exercise`, `plan_review` | Flow 5 |
| AI assistant and support | `assistant_setting`, `assistant_topic`, `assistant_quick_prompt`, `ai_conversation`, `ai_message`, `support_request`, `support_request_message` | Flow 6, Flow 1 |
| Notifications and system | `notification`, `announcement`, `system_setting` | All |

Total: **58 tables** (including V10 and V11 refreshed catalog tables), **3 reporting views**, **8 sequences**.

## High Level ERD

```mermaid
erDiagram
    role ||--o{ user_account : "assigned to"
    role ||--o{ role_permission : "grants"
    permission ||--o{ role_permission : "granted by"
    user_account ||--o| member_profile : "is a"
    user_account ||--o| coach_profile : "is a"
    membership_plan ||--o{ membership : "defines"
    member_profile ||--o{ membership : "holds"
    membership ||--o{ membership_sport : "covers"
    sport ||--o{ membership_sport : "covered by"
    membership ||--o{ payment : "paid by"
    payment ||--o| invoice : "produces"
    sport ||--o{ sport_class : "categorizes"
    age_group ||--o{ sport_class : "targets"
    sport_class ||--o{ class_session : "scheduled as"
    coach_profile ||--o{ class_session : "teaches"
    facility ||--o{ class_session : "hosts"
    class_session ||--o{ booking : "reserved by"
    member_profile ||--o{ booking : "makes"
    class_session ||--o{ waitlist_entry : "queues"
    booking ||--o| attendance_record : "results in"
    attendance_record ||--o| session_result : "scored in"
    class_session ||--o{ coach_feedback : "receives"
    member_profile ||--o{ workout_plan : "requests"
    recommendation_rule_set ||--o{ workout_plan : "ranks"
    workout_plan ||--o{ plan_review : "reviewed in"
    user_account ||--o{ ai_conversation : "starts"
    ai_conversation ||--o{ ai_message : "contains"
    ai_conversation ||--o{ support_request : "escalates to"
    member_profile ||--o{ support_request : "opens"
```

## Open Questions and Inconsistencies Found in the Mockups

These do not block the schema (the design handles both cases), but the team should confirm them.

| # | Observation | Impact on design |
|---|---|---|
| 1 | Multi-Sport price is 550,000 VND in F1-07, F1-08, F1-11, F1-12 but 300,000 VND in F3-03 to F3-08 and in the revenue report. | Price is snapshotted on `membership.price_amount` and `payment.amount_due`, so either value works. Confirm the official price for seed data. |
| 2 | Goal labels differ: "Improve fitness" (Home, F1-06), "Improve health" (F5-02), "Fitness" (F4-09). | One enum `IMPROVE_FITNESS` with display label "Improve health and fitness". |
| 3 | Age group for members is shown as "Adults 18+" (F1-06) while classes use "Ages 16+". | Member age group is not stored; eligibility is computed from `date_of_birth` against the class `age_group` range. |
| 4 | F5-10 Coach Review Queue lists a plan with status "Not submitted". | Queue should only list `plan_review.status = 'PENDING'`. Draft plans are not visible to coaches. |
| 5 | Revenue report shows a plan "Basketball Pass" and "Swim Starter" that are not on the public plan pages. | Supported as single-sport plans with a fixed eligible sport (`plan_eligible_sport`). |
| 6 | Home shows recurring times ("Tue and Thu - 17:00") while F2-03 schedules one session date. | v1 stores individual `class_session` rows. A recurrence pattern table is listed as a future extension. |
| 7 | "Membership cards" menu (older F1 variant) has no dedicated screen. | Treated as the list of `membership` rows. |
