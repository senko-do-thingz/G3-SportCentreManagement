# Diff Ownership

| Area | Changed paths | Responsibility |
| --- | --- | --- |
| Member shell | frontend/src/layouts/MemberLayout.tsx; frontend/src/layouts/memberConceptB.css | Member-only shell, navigation drawer, assistant entry, responsive styling |
| Member navigation | frontend/src/layouts/navigation.ts; frontend/src/pages/member/TrainingNavigation.tsx | Grouped workspace links and local training navigation |
| Dashboard | frontend/src/pages/member/MemberDashboardPage.tsx | Concept B presentation using existing member data |
| Training Hub | frontend/src/pages/member/MemberTrainingHubPage.tsx; frontend/src/routes/AppRoutes.tsx | New training landing screen, route wiring |
| Support and assistant handoff | frontend/src/pages/member/MemberSupportPage.tsx; frontend/src/pages/member/MemberAssistantPage.tsx | Existing support flow presentation and safe assistant navigation |
| Shell regression tests | frontend/src/layouts/MemberLayout.test.tsx; frontend/src/auth/auth.flow.test.tsx | Header, role shell, mobile drawer, assistant handoff, and login integration |
| Training regression tests | frontend/src/pages/member/MemberTrainingHubPage.test.tsx; frontend/src/pages/member/training.flow.test.tsx | Training Hub data states and route integration |
| Navigation label test updates | frontend/src/pages/member/memberPayments.test.tsx; frontend/src/pages/member/memberWallet.test.tsx; frontend/src/pages/member/membershipCards.test.tsx | Align assertions to the new visible Member navigation labels |
| Review evidence | tasks/Member-UI/reports/Hung-ConceptB-Final/* | ASCII-only route, scope, privacy, diff, and screenshot evidence |

No backend, database, shared staff page, Receptionist page, or Member commerce page implementation path is changed.
