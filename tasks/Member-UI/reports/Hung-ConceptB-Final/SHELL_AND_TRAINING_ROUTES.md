# Shell and Training Routes

## Shared header and role isolation

- MemberLayout continues to use the existing WorkspaceHeader and PaymentWorkspaceHeader components.
- Member-specific navigation and presentation are isolated in frontend/src/layouts/MemberLayout.tsx and frontend/src/layouts/memberConceptB.css.
- Staff and Receptionist shells are not reworked by this task.
- The Member navigation exposes Dashboard, My bookings, My schedule, My membership, My packages, Training progress, My wallet, My payments, My profile, and Support.
- Training destinations are grouped under the Training Hub local navigation.

## Responsive navigation

- At desktop widths, the Member navigation is a persistent workspace sidebar.
- Below the 960 px shell breakpoint, the navigation is a drawer controlled from the shared header.
- Opening the drawer moves focus into navigation and locks body scrolling. Escape closes it and restores focus to the menu control.
- At narrow mobile widths, the header context breadcrumb is hidden to reserve space for the brand and account controls.
- Browser capture metrics at a 390 by 844 viewport: document scroll width 375 px, header context display none, header height 111 px, and menu aria-expanded false in the closed state. The measured document width does not exceed the viewport.

## Training routes

- /member/training is the Training Hub landing page.
- TrainingNavigation provides an Overview link and direct links to classes, coach questions, plans, feedback, attendance, results, recommendations, and training preferences.
- The existing AppRoutes entries remain the source of truth for those pages. The hub links do not create new backend endpoints.
- /member/training/plans remains directly accessible for existing bookmarks and links.

## Regression coverage

- MemberLayout.test.tsx covers shell isolation with Receptionist styles loaded, Member-only links, mobile drawer focus and Escape handling, assistant suggestion handoff, and shared theme controls.
- MemberTrainingHubPage.test.tsx covers real training records, reachable destinations, honest empty states, and retry after an API failure.
- training.flow.test.tsx covers existing training route navigation and Member role boundaries.
