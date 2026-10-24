# UI-05 Test Cases - Guest authentication return navigation

Task row (Tasks EN): regression on guest modal, public browsing, safe returnTo, login and register CTA,
member-only actions; preserve the approved Home and all auth roles. Acceptance: guest not forced for
browsing; no auto booking or payment; role guards still pass.

Scope: after the Home redesign, the coach profile work and the shared UI merges, a guest who starts a
Member action must come back to exactly that destination after Log in or Create account. Automated
cases are in `src/pages/public/guestReturnMatrix.test.tsx` (new) and the existing
`homeGuestAuthFlow.test.tsx`, `homeAuthCtas.test.tsx`, `authScreens.test.tsx` and `auth/roles.test.ts`.

Verified on main `7afe3d2`. No production code change was needed: every case passed.

## New automated cases

| ID | Entry point | Steps | Expected result | Status |
|----|-------------|-------|-----------------|--------|
| UI05-TC-01 | Home hero strip, Membership offers | Guest clicks, chooses Log in, logs in | Lands on `/member/membership/cards`; only POST is `/auth/login` | Passed |
| UI05-TC-02 | Home sports section, View schedule | Same with Log in | Lands on `/member/schedule` | Passed |
| UI05-TC-03 | Home package card, View options | Same with Log in | Lands on `/member/packages/81`; no registration is sent | Passed |
| UI05-TC-04 | Home membership band, View cards | Same with Log in | Lands on `/member/membership/cards` | Passed |
| UI05-TC-05 to 08 | The four actions above | Guest chooses Create account and signs up | Lands on the same destination; only POST is `/auth/register` | Passed |
| UI05-TC-09 | Browser Back after return | Log in from View schedule, then press Back | Goes to Home, the login form is not shown again | Passed |
| UI05-TC-10 to 12 | Staff login with a Member destination | Manager, receptionist and coach log in with `from=/member/packages/81` | Each lands on their own home (`/manager`, `/reception`, `/coach`) | Passed |
| UI05-TC-13 to 17 | Unsafe return state | `//evil...`, `https://evil...`, `/member/%2e%2e/manager`, `/memberx/packages`, `member/packages` | Ignored; Member lands on `/member` | Passed |
| UI05-TC-18 | Session expires on a protected page | Member on `/member/payments?status=SUCCESS`, API and refresh return 401, member logs in again | Sent to Log in, then back to `/member/payments?status=SUCCESS` | Passed |
| UI05-TC-19 | Signed-in Member opens Log in with a destination | Session already active, open `/login` with `from=/member/schedule` | Goes straight to `/member/schedule` | Passed |
| UI05-TC-20 to 23 | Public browsing | Guest opens `/`, `/credits`, `/login`, `/register` | Stays on the page; no `/users/me`, no Authorization header, no write request, no sign-in prompt | Passed |
| UI05-TC-24 to 27 | Guest opens a protected area | Guest opens `/member`, `/reception`, `/manager`, `/coach`, then logs in as a Member | Sent to Log in; after login the Member lands on `/member` (staff areas are not opened) | Passed |
| UI05-TC-28 to 43 | Role guards | Member, receptionist, manager and coach each open all four areas | Own area renders with its Log out action; every other area shows Access denied and keeps the requested address | Passed |

## Acceptance check

| Acceptance criterion | Evidence |
|----------------------|----------|
| Guest not forced for browsing | UI05-TC-20 to 23, plus the existing public discovery test in homeGuestAuthFlow |
| No auto booking or payment | UI05-TC-01 to 08 assert the only write is `/auth/login` or `/auth/register` |
| Role guards still pass | UI05-TC-10 to 12 and UI05-TC-24 to 43 |
| Preserve the approved Home | No production file changed |

## Existing cases re-run (still passing)

- Class finder filters kept through a failed then successful login, and through browser Back (homeGuestAuthFlow).
- Selected package kept across Log in and Create account cross-links and signup (homeGuestAuthFlow).
- Account created but profile load fails: notice shown, return intent kept (homeGuestAuthFlow, homeAuthCtas).
- Direct protected deep links resume with their query (homeGuestAuthFlow).
- Staff see the Member-only notice instead of the guest prompt (homeGuestAuthFlow).
- Role prefix lookalikes and unknown roles rejected by `postLoginPath` (roles.test).

## Results

Lint, tsc, all frontend tests (71 files, 1348 tests) and the production build pass.
