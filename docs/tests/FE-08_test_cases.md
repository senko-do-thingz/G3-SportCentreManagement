# FE-08 test specification

| Area | Cases / expected behavior | Test file |
| --- | --- | --- |
| Read contracts | Member paths, AbortSignal, all pages, failure and duplicate-page rejection, incomplete detail payload | `frontend/src/api/memberSessionsApi.test.ts` |
| URL/date model | Typed enums/IDs, bad input defaults, page reset, self-training clears coach, safe local return targets, Vietnam date and UTC calendar arithmetic | `frontend/src/pages/member/scheduleFilters.test.ts` |
| Eligibility | All seven backend reasons, already-booked precedence, valid usable registration, unknown/null/inconsistent payload blocks Book | `frontend/src/pages/member/sessionEligibility.test.ts` |
| Calendar | Seven day groups, exact week/day range, sport/format requests, no staff lookup, error/retry/empty distinction | `frontend/src/pages/member/memberSchedule.test.tsx` |
| Search | URL restoration, combined server filters, preserved return query, reversed date range blocks requests, coach on later lookup page, unknown selected coach retained | `frontend/src/pages/member/findClasses.test.tsx` |
| Detail | Each reason-specific view, package query keys, no write or dead booking route, self-training hides coach even with ID, hidden-session 404 | `frontend/src/pages/member/sessionDetail.test.tsx` |
| Access | Anonymous login redirect and all three staff roles denied on each of the three browse routes before session requests | `frontend/src/pages/member/sessionDetail.test.tsx` |
| Profile | Lazy load, no private email, actual certificate status, null experience, expired certificate, private-profile failure isolation, Escape and focus restoration | `frontend/src/pages/member/sessionDetail.test.tsx` |
| Preview | Read-only fixtures, each special reason, coachless self-training, empty/error modes, no POST handler | `frontend/src/api/mock/memberBrowsePreview.test.ts` |
| Regression | Existing FE-06, FE-07, member catalog/payment and receptionist suites pass | Full `npm run test` |

Run from `frontend`: `npm run test`, `npm run lint`, `npm run build`. The verified full suite has 670 tests across 41 files. See `frontend/docs/FE-08-verification.md` for exact routes, preview setup, browser evidence, intentional Figma differences and pending live checks.

The user waived additional mobile work during implementation. Final acceptance is desktop-focused; responsive CSS remains, but full 375px sign-off is not claimed. Booking/payment mutation tests belong to later tasks; FE-08 must not create bookings, payments or package registrations.
