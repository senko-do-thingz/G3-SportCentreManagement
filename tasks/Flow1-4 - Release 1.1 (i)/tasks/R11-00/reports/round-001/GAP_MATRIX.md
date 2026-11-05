# R11-00 source gap matrix

Audited source: 1827a05b7393eb069ece5ae77c8f65d2a354466a. Date: 2026-11-05.
All implementation labels below describe static source presence, not desktop or database acceptance.
Backend paths below start at backend/src/main/java/com/sportify/; frontend paths start at frontend/src/.

## Provenance

Original workbook: D:/G3-SportCentreManagement/SWP/TOPIC 4.xlsx, SHA256 188f8aa5bc04c606e2b3d2488ac1acac3d8eacacbe1611430fda41f406e1ade7.
Sheet Project: A5 identifies topic 4; C5 names the sports center project; D5 defines the four actors; E5 contains the role feature bullets; F5 requires Flows 1-3; G5 lists optional Flows 4-6.
See topic4-cells.txt for exact cells and escaped source text. English descriptions here are translations, not changes to requirements.
The supplied assessment percentages and 1,928 tests are historical claims. BASELINE_VERIFICATION.md has no immutable inspected SHA, so an exact diff against that assessment cannot be reconstructed.
The original checkout is at 6689165da6c09e2426f3677803a12d562342a32b and is older than fetched main. Main includes merged #194, #203, #206, #211 and correlation propagation #190. Its source is the authority for this audit.

## Five active tasks

| Task / requirement | Status | API and source | Desktop source / gap | Existing tests / minimum next boundary |
|---|---|---|---|---|
| R11-01, E5 Receptionist support intake, Flow 1 | PARTIAL: API IMPLEMENTED, page MISSING | catalog/controller/ReceptionSupportController.java:18-29; catalog/service/ReceptionSupportService.java; GET list/detail, POST with UUID Idempotency-Key, PUT status under /api/v1/reception/support-requests; Member self GET | No /reception/support in routes/AppRoutes.tsx or layouts/navigation.ts; no frontend support client or page | catalog/service/TrainingWorkflowIntegrationTest.java:249-295 and role matrix at 346-377. Add typed client, existing-member search, list/detail/create/status UI only. Reuse server state machine and stable retry key. No CRM or automatic payment changes. |
| R11-02, E5 Manager time-scoped members/bookings/revenue, Flow 3 | PARTIAL: API IMPLEMENTED, operations UI MISSING | payment/controller/OperationsReportController.java; payment/service/OperationsReportService.java; GET /api/v1/manager/reports/operations?from=&to= | pages/manager/ManagerRevenueReportsPage.tsx and api/managerFinanceApi.ts serve revenue and CSV, without operations fields | TrainingWorkflowIntegrationTest.java:514-545; frontend api/managerFinanceApi.test.ts and pages/manager/managerFinanceScreens.test.tsx cover current revenue. Add operations typing and cards to /manager/reports with current dates; preserve CSV and API finance calculations. |
| R11-03, quality verification | PARTIAL | backend/pom.xml separates Surefire unit tests from Failsafe integration tag; .github/workflows/ci.yml requires real SQL and counts | package.json defines lint, test, build; no implementation needed by default | Current main CI run 41968012911 succeeded including CI gate, SQL integration and frontend. Local evidence in TEST_RESULTS.md. Repeat after future feature merges; do not reuse this audit as final release acceptance. |
| R11-04, optional Manager attendance F4-10 | PARTIAL: backfill IMPLEMENTED, overview/detail MISSING | catalog/controller/ManagerTrainingController.java only GET/POST /api/v1/manager/training/attendance/backfill; no Manager overview/detail GET found across controllers | pages/training/ManagerAttendanceBackfillPage.tsx exists; no /manager/training/attendance overview in route map/menu | frontend pages/training/managerBackfill.flow.test.tsx; backend TrainingWorkflowIntegrationTest R09 cases validate backfill, not overview. If selected by CODEOWNER, minimal read-only Manager overview/detail over existing attendance; no backfill semantics change. |
| R11-05, E5 Coach profile/goals/progress F4-05 | PARTIAL: APIs IMPLEMENTED, reader page MISSING | catalog/controller/CoachTrainingController.java profile GET; TrainingProgressController.java:20 progress GET under /api/v1/coach/training/classes/{classId}/members/{memberId}; TrainingProgressService.java | pages/training/CoachTrainingPages.tsx offers plans/results/feedback; api/staffTrainingApi.ts offers classes/candidates/roster but no profile/progress reads; no /coach/training/progress | TrainingWorkflowIntegrationTest.java:170-205; frontend pages/training/staffTraining.flow.test.tsx covers current writes. Add class/member reader and roster link; only actual recorded metrics, scoped feedback and current ownership. |

## Per-flow coverage outside the four new pages

All rows refer to Project!E5. Grouped bullets retain the original actor rather than inventing finer official requirement IDs.

| Flow / actor and workbook feature | Static status | API controller sources | Page / route evidence and existing tests |
|---|---|---|---|
| 1 Manager manages users, coaches, staff | IMPLEMENTED | identity/controller/ManagerUserController.java, ManagerCoachProfileController.java | pages/manager; /manager/users; controller tests and ReleaseTierIntegrationTest |
| 1 Manager packages, fees, terms | IMPLEMENTED | catalog/controller/ManagerPackageController.java, ManagerCardTierController.java | /manager/packages, manager card screens; ReleaseTierIntegrationTest |
| 1 Manager role access | IMPLEMENTED fixed roles; DEFERRED dynamic grants | ManagerUserController.java and method security | Existing role guards. E5 does not explicitly require editing an existing role. R11-06 requires CODEOWNER interpretation/signoff. |
| 1 Manager important action history | IMPLEMENTED | identity/controller/ActivityLogController.java | Existing Manager activity page; no AI Audit Log work required |
| 1 Member signup/profile/package purchase or renewal | IMPLEMENTED | identity/controller/AuthenticationController.java, UserController.java; catalog/controller/SportPackageController.java | Member account, profile, package routes; api and page tests, ReleaseOnboardingIntegrationTest |
| 1 Reception member search, registration, renewals, validity | IMPLEMENTED | identity/controller/MemberController.java; catalog/controller/FrontDeskMemberOverviewController.java, MembershipCardController.java | /reception/members, register/renew screens; receptionScreens.test.tsx, ReleaseOnboardingIntegrationTest |
| 1 Reception arrival attendance | IMPLEMENTED | catalog/controller/CheckInController.java, MemberCheckInPassController.java | /reception/check-in; receptionScreens.test.tsx |
| 1 Reception support requests | PARTIAL | ReceptionSupportController.java | R11-01 above |
| 2 Manager classes, sports, facilities, schedule and Coach assignment | IMPLEMENTED | catalog/controller/ManagerClassController.java, ManagerSessionController.java, SportController.java, FacilityController.java | Manager class/session/catalog routes; ReleaseCatalogIntegrationTest, SessionReschedulingIntegrationTest |
| 2 Coach teaching schedule and class roster | IMPLEMENTED | catalog/controller/CoachScheduleController.java, CoachTrainingController.java | /coach/schedule and current attendance roster; staffTraining.flow.test.tsx |
| 2 Member class schedule, booking/cancellation, own schedule/Coach info | IMPLEMENTED | catalog/controller/SessionController.java, BookingController.java; identity/controller/CoachPublicProfileController.java | Member booking/schedule/detail routes; receptionBookingScreens.test.tsx and backend booking/session tests |
| 2 Reception assisted enrollment/cancellation | IMPLEMENTED | catalog/controller/BookingController.java | /reception/bookings and assisted flow; api/receptionBookingsApi.test.ts, pages/reception/receptionBookingScreens.test.tsx |
| 3 Reception payment recording and invoices | IMPLEMENTED | payment/controller/PaymentVerificationController.java, InvoiceController.java | Reception payment workspace; receptionPayments.test.tsx; FinanceReportIntegrationTest |
| 3 Manager time-scoped operations reporting | PARTIAL | payment/controller/OperationsReportController.java, ManagerFinanceController.java | Revenue only at /manager/reports; R11-02 above |
| 4 Coach basic member profile/goals and progress | PARTIAL | CoachTrainingController.java, TrainingProgressController.java | R11-05 above |
| 4 Coach individual/class plans, results, feedback and attendance | IMPLEMENTED | CoachTrainingController.java, ClassPlanController.java | /coach/training/plans, results, attendance; staffTraining.flow.test.tsx, TrainingWorkflowIntegrationTest |
| 4 Coach notices/homework | IMPLEMENTED | ClassNoticeController.java, ClassLearningController.java | Existing class learning/question UI; classLearning.flow.test.tsx |
| 4 Member attendance/results/plans/feedback and notifications | IMPLEMENTED | MemberTrainingController.java, TrainingProgressController.java, MemberNotificationController.java | pages/member/TrainingPages.tsx; training.flow.test.tsx, TrainingData.test.tsx. Standalone progress aggregation reader availability does not prove it is consumed by UI. |
| 4 Manager attendance overview/detail | MISSING (backfill is separate) | No matching overview/detail Manager read contract found | R11-04 above; optional desktop choice requires CODEOWNER inclusion decision |
| Waitlist / dynamic RBAC / legacy removal | DEFERRED | Existing schema/legacy code retained | R11-06/07/08 are decision-only; no implementation authorized |
| Flow 5-6 and AI requirements in E5 | DEFERRED by user scope | Not audited for completeness | No implementation, mobile acceptance or AI Audit Log work in R11-00 |

## Contract constraints and decisions

Support categories and bounded DTO fields come from ReceptionSupportService, not the image. OPEN -> IN_PROGRESS or CLOSED; IN_PROGRESS -> RESOLVED or CLOSED; RESOLVED -> CLOSED; CLOSED terminal. Resolve/close requires a meaningful note. Same intent retry retains its UUID; changed payload with old UUID conflicts. The F1-16 overlay's Escalate to Manager button has no server action: omit pending an explicit business contract decision.

Operations registeredMembers counts registrations in the selected interval. currentlyEnabledMembersRegistered is the currently enabled subset, not enabled-at-period-end. Bookings use booked_at and class-linked sessions. upcoming/completed are subsets of confirmed, not extra additive buckets. Preserve reserved/simulated data exclusions and finance validation/defaults.

Coach progress returns learner, chronological sessions with metric IDs/scales, scoped feedback and interpretation. Attendance status is attached to recorded outcomes, not a complete independent attendance ledger. Do not claim full attendance history from it. Wrong role is denied by method security; another Coach's class is deliberately hidden as 404 through learnerProfile, not necessarily 403. Reuse existing plan feedback/result writes; no separate aggregate progress-save API exists.

Attendance Figma defines rate as (Present + Late) / confirmed bookings for completed sessions, excluding cancelled bookings and center-cancelled sessions. It has period/sport/format controls while the prompt asks date/class/Coach. CODEOWNER must settle filter and denominator details before adding a contract. Do not count missing records as ABSENT without a business decision; handle zero denominator explicitly.

## Overlap and allocation

See open-prs.json and pr-overlaps.txt for all nine PR authors, branch names and head SHAs. PR #192's file list exceeds the 100-file embedded limit; pr192-all-files.txt is the fully paginated list and includes AppRoutes.tsx, navigation.ts, CoachTrainingPages.tsx and staffTrainingApi.ts. Treat both #191 and #192 as broad stale-baseline overlap, #180/#173 as training/shared routing overlap, and #178/#176 as excluded assistant changes touching shared routes. #189 changes auth/session behavior and needs regression coordination. #174 changes Reception check-in/QA; #212 changes payment UI. No branch was modified, rebased or merged, and no author was contacted.

No inspected PR file list shows a dedicated Reception support page, operations client/page, Manager overview or Coach progress page. File overlap is a coordination risk, not proof of a textual merge conflict. CODEOWNER owns allocation; branch authors do not establish business ownership approval.

Recommended order: approve the R11-00 baseline, allocate R11-01 and R11-02 with shared-route coordination, run R11-03 at the resulting integrated SHA, then decide whether to include R11-04/05. Stop here: none of these later tasks has been started.

## Migration and runtime safety

See migrations.txt: current source ends at V46. application.yml uses canonical sportify by default, Flyway identity/catalog locations, validate-on-migrate=true, clean-disabled=true, out-of-order=false and Hibernate validate. No database connection, migration, seed, repair, baseline, clean or application server was run against a protected database. Maven tests use their own Testcontainers paths; no native DB environment was configured by this task. Docker absence prevents local SQL acceptance. No migration is proposed for the four UI gaps by this audit.
