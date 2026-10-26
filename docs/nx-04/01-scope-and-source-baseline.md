# Scope and source baseline

Status: TECHNICAL_DRAFT_READY_FOR_CODEOWNER_REVIEW. All new storage and rights are PROPOSED or BLOCKED_PENDING_APPROVAL. Source baseline: `4cdc0874fde95ae0b1500f0625c1521f428a6bec` fetched from origin/main on 2026-10-26. Branch: `feat/nx-04-f4-data-model`, isolated from the user's checkout. Publication and CI do not approve implementation.

## Authority and read-only workbook review

The operational source is `C:/Users/TIEN DAT/Downloads/Sportify_Flow4Plus_Tasks_Topic4_Revised.xlsm`, SHA-256 `a57a3c6f94bc187cf83191206c76cfdb1c1383c55e331c80a5fe547264e4db24`. Read-only ZIP/XML inspection used Tasks EN!A10:Q10, Tasks VI!A10:Q10 as supporting translation, Requirement Map, Decision Log, API Contracts and QA Gates. Macros were never run, and the workbook was not saved or recalculated.

Tasks EN!J10 assigns persistence and authorization boundary design; K10 requires schema reuse, plan/version/session relationships, attendance uniqueness, training records and audit trails; L10 requires reviewed ERD, permissions, migration tests and rollback approach with no live DB writes; Q10 requires migration approval before coding. Status O10 is Planned and is not a signature. This execution delivers reviewable design and safe static tests.

Official Topic 4 is `FA26_SWP391 (1).xlsx`, SHA-256 `23eecedd6fe6f2d599ae5c0597b52707f54155d798b678d105f8aa2d955ba739`, retained in the local NX-00 references. Project!E5 supplies actor capabilities; G5 classifies Flow 4-6 as optional; F5 preserves required Flow 1-3. English normalized capability mappings below are interpretations, consistent with NX-00, not additional official policy.

| Requirement | Capability | Design coverage | Downstream |
| --- | --- | --- | --- |
| F4-REQ-01 | Coach creates personal or class plans | 03-06 identity, assignment, revision, audience | NX-07 |
| F4-REQ-02 | Coach records session outcomes | 04-06 results, metrics, attribution and corrections | NX-08 |
| F4-REQ-03 | Coach evaluates progress and comments | 06-08 feedback and field privacy | NX-08 |
| F4-REQ-04 | Coach records participation | 02, 04, 08 separate attendance and conditional uniqueness | NX-09 |
| F4-REQ-05 | Member reads own attendance and outcomes | 07 historical entitlement and safe projections | NX-08/09/10 |
| F4-REQ-06 | Member reads own plans and Coach comments | 04, 06, 07 revision/audience and allowlists | NX-07/08/10 |
| F4-REQ-07 | Receptionist records arrival | 02, 07, 08 preserve current check-in | NX-09/10 |

## Prerequisite reconciliation

Reviewed [NX-00 scope](../requirements/nx-00/03-flow-4-6-scope-registry.md) and [decisions](../requirements/nx-00/05-conflicts-decisions-and-open-questions.md); [NX-01 baseline](../nx-01/NX-01-baseline-and-gap-matrix.md), [inventory](../nx-01/NX-01-schema-and-data-model-inventory.md), [Round 3 correction](../nx-01/NX-01-round3-finding-resolution.md) and [pending approvals](../nx-01/NX-01-round3-pending-approvals-and-blockers.md); [NX-02 options](../nx-02/NX-02-plan-result-progress-options.md), [boundary](../nx-02/NX-02-attendance-check-in-boundary.md), [completeness](../nx-02/NX-02-approval-completeness-matrix.md) and [sign-off request](../nx-02/NX-02-sign-off-request.md); [NX-03 operations](../nx-03/NX-03-api-operation-catalogue.md), [DTO draft](../nx-03/NX-03-dto-and-sample-payloads.md), [errors](../nx-03/NX-03-error-taxonomy-and-compatibility.md), [RBAC](../nx-03/NX-03-rbac-and-resource-ownership-matrix.md), [retry profiles](../nx-03/NX-03-validation-retry-profiles.md) and [downstream handoff](../nx-03/NX-03-downstream-nx04-nx07-nx10-handoff.md).

NX-01 Round 3 resolves reporting arithmetic, not source authority or runtime DB availability. NX-02 has 32 required unresolved dimensions. NX-03's technical remediation acceptance preserves missing G-01 signatures and unsigned wire contracts. Historical baseline SHAs in those files are not this baseline. Several old handoff links refer to unpublished reports; no current claim relies on such a link. All reuse claims below were reconciled to current tracked Backend source.

## Evidence classes

SOURCE_CONFIRMED means directly inspected current source; SOURCE_INFERRED is an engineering inference from that source; HISTORICAL_ONLY is earlier audit evidence; NOT_FOUND means absent within the bounded search; POLICY_PROPOSED requires human approval; UNVERIFIED_RUNTIME means no deployed/disposable SQL Server measurement. SPECIFIED_ONLY and SPECIFIED_NOT_EXECUTED denote test designs, not passing tests. Execution evidence is recorded separately from specifications.

## Current source definition ledger

Links use canonical Git casing (`backend`, although Windows may display `Backend`). Each line is validated against the actual definition by the static guard. [Machine-readable inventory](source-inventory.json) contains versioned SQL fingerprints and JPA paths. It is curated source metadata, not a DB export.

| ID | Path and line | Definition | Classification |
| --- | --- | --- | --- |
| S01 | [backend/src/main/resources/application.yml#L28](../../backend/src/main/resources/application.yml#L28) | `ddl-auto:` | SOURCE_CONFIRMED |
| S02 | [backend/src/main/resources/application-demo.yml#L5](../../backend/src/main/resources/application-demo.yml#L5) | `locations:` | SOURCE_CONFIRMED |
| S03 | [backend/src/main/java/com/sportify/core/config/DemoProfileSafetyConfiguration.java#L10](../../backend/src/main/java/com/sportify/core/config/DemoProfileSafetyConfiguration.java#L10) | `class DemoProfile` | SOURCE_CONFIRMED |
| S04 | [backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql#L10](../../backend/src/main/resources/db/migration/catalog/V16__create_training_and_attendance_tables.sql#L10) | `CREATE TABLE [session_plan]` | SOURCE_CONFIRMED |
| S05 | [backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql#L274](../../backend/src/main/resources/db/migration/catalog/V10__context_refresh_schema.sql#L274) | `CREATE TABLE [class_session]` | SOURCE_CONFIRMED |
| S06 | [backend/src/main/resources/db/migration/catalog/V20__align_schema_with_topic.sql#L6](../../backend/src/main/resources/db/migration/catalog/V20__align_schema_with_topic.sql#L6) | `ux_booking_active` | SOURCE_CONFIRMED |
| S07 | [backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql#L177](../../backend/src/main/resources/db/migration/catalog/V5__create_catalog_membership_tables.sql#L177) | `CREATE TABLE [check_in]` | SOURCE_CONFIRMED |
| S08 | [backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql#L4](../../backend/src/main/resources/db/migration/identity/V2__add_identity_tables.sql#L4) | `CREATE TABLE [member_profile]` | SOURCE_CONFIRMED |
| S09 | [backend/src/main/resources/db/migration/identity/V3__seed_identity_data.sql#L4](../../backend/src/main/resources/db/migration/identity/V3__seed_identity_data.sql#L4) | `code = 'MEMBER'` | SOURCE_CONFIRMED |
| S10 | [backend/src/main/java/com/sportify/catalog/service/impl/CheckInServiceImpl.java#L48](../../backend/src/main/java/com/sportify/catalog/service/impl/CheckInServiceImpl.java#L48) | `public CheckInResponse recordCheckIn` | SOURCE_CONFIRMED |
| S11 | [backend/src/main/java/com/sportify/catalog/controller/CheckInController.java#L20](../../backend/src/main/java/com/sportify/catalog/controller/CheckInController.java#L20) | `@RequestMapping` | SOURCE_CONFIRMED |
| S12 | [backend/src/main/java/com/sportify/catalog/repository/CheckInRepository.java#L22](../../backend/src/main/java/com/sportify/catalog/repository/CheckInRepository.java#L22) | `findForDay` | SOURCE_CONFIRMED |
| S13 | [backend/src/main/java/com/sportify/catalog/service/impl/BookingServiceImpl.java#L74](../../backend/src/main/java/com/sportify/catalog/service/impl/BookingServiceImpl.java#L74) | `public BookingResponse createBooking` | SOURCE_CONFIRMED |
| S14 | [backend/src/main/java/com/sportify/catalog/service/impl/BookingServiceImpl.java#L327](../../backend/src/main/java/com/sportify/catalog/service/impl/BookingServiceImpl.java#L327) | `recordSelfTrainingAttendance` | SOURCE_CONFIRMED |
| S15 | [backend/src/main/java/com/sportify/catalog/service/impl/CoachScheduleServiceImpl.java#L31](../../backend/src/main/java/com/sportify/catalog/service/impl/CoachScheduleServiceImpl.java#L31) | `public class CoachSchedule` | SOURCE_CONFIRMED |
| S16 | [backend/src/main/java/com/sportify/catalog/repository/CoachScheduleReadRepository.java#L46](../../backend/src/main/java/com/sportify/catalog/repository/CoachScheduleReadRepository.java#L46) | `findRoster` | SOURCE_CONFIRMED |
| S17 | [backend/src/main/java/com/sportify/catalog/repository/ClassSessionRepository.java#L67](../../backend/src/main/java/com/sportify/catalog/repository/ClassSessionRepository.java#L67) | `findForUpdate` | SOURCE_CONFIRMED |
| S18 | [backend/src/main/java/com/sportify/identity/security/SecurityConfig.java#L27](../../backend/src/main/java/com/sportify/identity/security/SecurityConfig.java#L27) | `@EnableMethodSecurity` | SOURCE_CONFIRMED |
| S19 | [backend/src/main/java/com/sportify/identity/security/CustomUserDetails.java#L22](../../backend/src/main/java/com/sportify/identity/security/CustomUserDetails.java#L22) | `getAuthorities` | SOURCE_CONFIRMED |
| S20 | [backend/src/main/java/com/sportify/identity/entity/MemberProfile.java#L23](../../backend/src/main/java/com/sportify/identity/entity/MemberProfile.java#L23) | `@MapsId` | SOURCE_CONFIRMED |
| S21 | [backend/src/main/java/com/sportify/identity/entity/CoachProfile.java#L21](../../backend/src/main/java/com/sportify/identity/entity/CoachProfile.java#L21) | `@MapsId` | SOURCE_CONFIRMED |
| S22 | [backend/src/main/java/com/sportify/core/common/SecurityUtils.java#L10](../../backend/src/main/java/com/sportify/core/common/SecurityUtils.java#L10) | `isStaff` | SOURCE_CONFIRMED |
| S23 | [backend/src/main/java/com/sportify/core/audit/impl/AuditServiceImpl.java#L38](../../backend/src/main/java/com/sportify/core/audit/impl/AuditServiceImpl.java#L38) | `public void record` | SOURCE_CONFIRMED |
| S24 | [backend/src/main/java/com/sportify/identity/entity/ActivityLog.java#L18](../../backend/src/main/java/com/sportify/identity/entity/ActivityLog.java#L18) | `class ActivityLog` | SOURCE_CONFIRMED |
| S25 | [backend/src/main/java/com/sportify/identity/controller/ActivityLogController.java#L17](../../backend/src/main/java/com/sportify/identity/controller/ActivityLogController.java#L17) | `@PreAuthorize` | SOURCE_CONFIRMED |
| S26 | [backend/src/main/java/com/sportify/core/config/ClockConfig.java#L14](../../backend/src/main/java/com/sportify/core/config/ClockConfig.java#L14) | `Clock.system` | SOURCE_CONFIRMED |
| S27 | [backend/src/test/java/com/sportify/AbstractIntegrationTest.java#L13](../../backend/src/test/java/com/sportify/AbstractIntegrationTest.java#L13) | `@Tag` | SOURCE_CONFIRMED |
| S28 | [backend/pom.xml#L181](../../backend/pom.xml#L181) | `maven-surefire-plugin` | SOURCE_CONFIRMED |
| S29 | [.github/workflows/ci.yml#L8](../../.github/workflows/ci.yml#L8) | `workflow_dispatch:` | SOURCE_CONFIRMED |
| S30 | [backend/src/main/java/com/sportify/catalog/entity/ClassSession.java#L19](../../backend/src/main/java/com/sportify/catalog/entity/ClassSession.java#L19) | `class ClassSession` | SOURCE_CONFIRMED |
| S31 | [backend/src/main/java/com/sportify/catalog/entity/Booking.java#L17](../../backend/src/main/java/com/sportify/catalog/entity/Booking.java#L17) | `class Booking` | SOURCE_CONFIRMED |
| S32 | [backend/src/main/java/com/sportify/catalog/entity/CheckIn.java#L22](../../backend/src/main/java/com/sportify/catalog/entity/CheckIn.java#L22) | `class CheckIn` | SOURCE_CONFIRMED |
| S33 | [backend/src/main/resources/db/migration/catalog/V23__add_session_cancellation_and_schedule_indexes.sql#L3](../../backend/src/main/resources/db/migration/catalog/V23__add_session_cancellation_and_schedule_indexes.sql#L3) | `ALTER TABLE` | SOURCE_CONFIRMED |
| S34 | [backend/src/main/java/com/sportify/identity/service/impl/ActivityLogServiceImpl.java#L17](../../backend/src/main/java/com/sportify/identity/service/impl/ActivityLogServiceImpl.java#L17) | `class ActivityLogService` | SOURCE_CONFIRMED |
| S35 | [backend/src/main/java/com/sportify/catalog/repository/SportPackageRegistrationRepository.java#L86](../../backend/src/main/java/com/sportify/catalog/repository/SportPackageRegistrationRepository.java#L86) | `consumeSession` | SOURCE_CONFIRMED |
