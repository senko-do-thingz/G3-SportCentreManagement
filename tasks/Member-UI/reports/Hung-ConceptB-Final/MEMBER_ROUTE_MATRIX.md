# Member Route Matrix

All routes below remain within the authenticated Member role shell unless marked otherwise. Manager, Coach, and Receptionist route behavior is outside this change.

| Route | Screen | Data or behavior | Ownership in this change |
| --- | --- | --- | --- |
| /member | MemberDashboardPage | Existing member data, with empty, loading, and error states | Presentation and shell integration |
| /member/training | MemberTrainingHubPage | Existing memberTrainingApi records and retryable source errors | New Training Hub landing |
| /member/training/classes | MemberClassesPage | Existing enrolled-class experience | Reachable from hub navigation |
| /member/training/classes/:id | MemberClassDetailPage | Existing member class details | Existing screen |
| /member/training/questions | ClassQuestionsPage | Existing questions for a human coach | Existing screen |
| /member/training/plans | TrainingPlansPage | Existing assigned plans | Existing screen |
| /member/training/plans/:id | TrainingPlanDetailPage | Existing plan details | Existing screen |
| /member/training/feedback | CoachFeedbackPage | Existing coach feedback | Existing screen |
| /member/training/attendance | AttendancePage | Existing attendance history | Existing screen |
| /member/training/results | TrainingResultsPage | Existing training results | Existing screen |
| /member/training/results/:id | TrainingResultDetailPage | Existing result details | Existing screen |
| /member/training/recommendations | ClassRecommendationsPage | Existing recommendations and preferences | Existing screen |
| /member/training/recommendations/classes/:classId | SuitableSessionsPage | Existing suitable session details | Existing screen |
| /member/training/goals | MemberTrainingGoalsPage | Existing member training preferences | Existing screen |
| /member/assistant | MemberAssistantPage | Existing consent-gated center consultation; query question is only a prefill | Entry dialog and route handoff |
| /member/support | MemberSupportPage | Existing support request API | Route moved into MemberLayout outlet; accessible presentation |
| /member/bookings | MemberBookingsPage | Existing bookings API | Route moved into MemberLayout outlet |
| /member/bookings/new | BookingOptionsPage | Existing booking flow | Existing screen |
| /member/bookings/review | BookingReviewPage | Existing booking review | Existing screen |
| /member/bookings/confirmed/:bookingId | BookingConfirmedPage | Existing booking confirmation | Existing screen |
| /member/bookings/:bookingId/cancel | BookingCancellationPage | Existing cancellation flow | Existing screen |
| /member/bookings/:bookingId/cancelled | BookingCancelledPage | Existing cancellation result | Existing screen |
| /member/profile | MyProfilePage | Existing profile API and form | Existing screen; not redesigned |
| /member/packages | SportPackagesPage | Existing package catalog | Existing screen; not redesigned |
| /member/membership | MyMembershipPage | Existing membership and package data | Existing screen; not redesigned |
| /member/wallet | Member wallet screen | Existing wallet API | Existing screen; not redesigned |
| /member/payments | Member payments screen | Existing payment API | Existing screen; not redesigned |

The shell keeps the existing role authorization wrapper in AppRoutes.tsx. No backend route or authorization policy was changed.
