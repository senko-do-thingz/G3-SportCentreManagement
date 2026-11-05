# Release 1.1 requirement traceability

Source: original `TOPIC 4.xlsx`, Project!A5:G5. Workbook SHA256: 188f8aa5bc04c606e2b3d2488ac1acac3d8eacacbe1611430fda41f406e1ade7. Project!E5 contains the actor requirements, F5 requires Flows 1-3 and G5 makes Flow 4 optional. CODEOWNER included Flow 4 and accepted R11-00. The 30 rows below translate the non-AI actor bullets; they are local trace IDs, not invented official worksheet IDs. Optional AI bullets remain excluded.

All routes and endpoints are relative to the frontend root and `/api/v1`, respectively. Controller names refer to backend source under `com/sportify`. Existing behavior is regression scope; the four missing desktop readers/workflows are the new implementation. Verification must be read from the exact candidate SHA reports, not from source presence alone.

| ID | Actor / workbook feature | Desktop route | API / controller | Tests and release task |
|---|---|---|---|---|
| M01 | Manager manages users, members, coaches and staff | /manager | /manager/users; ManagerUserController | ReleaseOnboardingIntegrationTest; manager user page tests |
| M02 | Manager manages classes, sports, facilities and schedules | /manager/classes, /manager/schedule | ManagerClassController, ManagerSessionController, SportController, FacilityController | ReleaseCatalogIntegrationTest; manager schedule tests |
| M03 | Manager assigns Coaches to classes | /manager/classes | /manager/classes; ManagerClassController | Manager class editor tests; SessionReschedulingIntegrationTest |
| M04 | Manager reads members, enrollment and revenue by period | /manager/reports | /manager/reports/operations; OperationsReportController; existing /manager/finance/revenue | TrainingWorkflowIntegrationTest RELEASE09; operationsSummary.test.tsx; R11-02 |
| M05 | Manager manages packages, fees and terms | /manager/packages, /manager/cards | ManagerPackageController, ManagerCardTierController | ReleaseTierIntegrationTest; package/card tests |
| M06 | Manager role-based system access | /manager and role-guarded areas | ManagerUserController; method security | TrainingWorkflowIntegrationTest RELEASE16; R11-06 fixed-role decision |
| M07 | Manager sees important action history | /manager/activity | ActivityLogController | Existing activity log tests; no AI Audit Log deliverable |
| C01 | Coach sees teaching schedule and class roster | /coach, /coach/roster | CoachScheduleController | Coach schedule and roster tests; RELEASE05 SQL regression |
| C02 | Coach sees learner profile and goals | /coach/training/progress | /coach/training/classes/{classId}/members/{memberId}/profile | RELEASE07/12 SQL; coachProgress.test.tsx; R11-05 |
| C03 | Coach creates individual or class plans | /coach/training/plans; class learning pages | CoachTrainingController, ClassPlanController | staffTraining.flow.test.tsx; RELEASE10 SQL |
| C04 | Coach records training results | /coach/training/results | CoachTrainingController result writes | staffTraining.flow.test.tsx; TrainingWorkflowIntegrationTest |
| C05 | Coach evaluates progress and records feedback | /coach/training/progress, /coach/training/plans | /coach/training/classes/{classId}/members/{memberId}/progress; existing plan feedback POST | RELEASE12 SQL; coachProgress.test.tsx; R11-05 |
| C06 | Coach records session attendance | /coach/training/attendance | CoachTrainingController attendance writes | attendanceQuickStatuses.flow.test.tsx; R07/R09 SQL |
| C07 | Coach sends homework or notices | /coach/training/questions; class learning pages | ClassNoticeController, ClassLearningController | RELEASE13 SQL; classLearning.flow.test.tsx |
| U01 | Member registers and updates profile | /register, /member/profile | /auth/register, UserController | Existing auth/profile tests; ReleaseOnboardingIntegrationTest |
| U02 | Member views and buys/renews packages | /member/packages, /member/membership | SportPackageController, MembershipCardController | Existing package/card tests; ReleaseTierIntegrationTest |
| U03 | Member views classes and schedules | /member/classes, /member/schedule | /sessions; SessionController | Existing browse/schedule tests; RELEASE05 SQL |
| U04 | Member books or cancels a class | /member/bookings | BookingController | Existing booking/cancellation tests; credit regression SQL |
| U05 | Member sees personal schedule and Coach details | /member/schedule, session details | BookingController, CoachPublicProfileController | Existing schedule/session tests; RELEASE15 SQL |
| U06 | Member sees own attendance and results | /member/training/attendance, /member/training/results | MemberTrainingController, self /member/training/progress | training.flow.test.tsx; RELEASE12 and ownership SQL |
| U07 | Member sees plans and Coach feedback | /member/training/plans, /member/training/feedback | MemberTrainingController | training.flow.test.tsx; TrainingData.test.tsx; training SQL |
| U08 | Member receives schedule and expiry notifications | Existing notification UI | MemberNotificationController | RELEASE15 SQL; existing notification tests |
| R01 | Reception searches and views members | /reception | /members, FrontDeskMemberOverviewController | receptionScreens.test.tsx; ReleaseOnboardingIntegrationTest |
| R02 | Reception registers a new member | /reception/register | POST /members | receptionScreens.test.tsx; onboarding SQL |
| R03 | Reception registers/renews packages and cards | /reception/register | SportPackageController, MembershipCardController | Reception desk-sale tests; tier/onboarding SQL |
| R04 | Reception checks package status and validity | /reception member overview | FrontDeskMemberOverviewController | receptionScreens.test.tsx; canonical validity regression |
| R05 | Reception records arrival | /reception/check-in | CheckInController, MemberCheckInPassController | RELEASE11 SQL; existing check-in tests |
| R06 | Reception books or assists cancellation | /reception/bookings | BookingController | receptionBookingScreens.test.tsx; booking SQL |
| R07 | Reception records payment and exports receipt | /reception/payments | PaymentVerificationController, InvoiceController | receptionPayments.test.tsx; finance/payment SQL |
| R08 | Reception receives and records support requests | /reception/support | /reception/support-requests list/detail/create/status; ReceptionSupportController | RELEASE14/16 SQL; supportRequests.test.tsx; R11-01 |

Included optional Manager attendance: Figma F4-10 and S4-ManagerDetails -> `/manager/training/attendance` -> ManagerAttendanceReadController -> ManagerAttendanceReadServiceTest, ManagerAttendanceReadControllerTest, TrainingWorkflowIntegrationTest R11 methods and managerAttendanceOverview.test.tsx (R11-04).

Actual design references: `context/Flow 1 - User and membership management/Flow 1 - Receptionist/F1-16 - Support Requests.png` and its Overlay; `context/Flow 4 - Training and attendence management/Flow 4 - Manager/F4-10 - Attendance Overview.png`, `S4-ManagerDetails - Session Attendance Details.png`; Coach `F4-05 - Member Progress.png`, `S4-Profile - Member Profile & Goals.png`, `S4-ProgressSaved - Progress Feedback Updated.png`. No fake escalation API, aggregate progress write or attendance history is inferred from an image.

Scope decisions: fixed four roles, no dynamic grant; Waitlist deferred; legacy membership remains deprecated. No new migration, production finance behavior, mobile acceptance or Flow 5-6 implementation. Cross-Coach learner denial preserves 404 and wrong-role method access preserves 403. Final merged-main and deployment acceptance remain CODEOWNER actions after review.
