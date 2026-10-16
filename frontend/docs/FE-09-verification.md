# FE-09 Member Booking Verification

Date: 2026-10-17

## Scope

- Member booking options, review, confirmation, upcoming and past booking history, cancellation confirmation and cancelled result.
- Existing FE-06 manager activity, FE-07 manager classes/schedule and FE-08 member browsing remain in place.
- Frontend-only change. No backend source, tests, migrations, seed data or API behavior were changed.

## API integration

- `GET /api/v1/sessions/{id}` revalidates session availability and returns eligible package registrations before booking review and again before submit.
- `POST /api/v1/bookings` sends `{ sessionId, packageRegistrationId }` for the explicitly selected eligible registration.
- `GET /api/v1/bookings/my?when=UPCOMING` and `GET /api/v1/bookings/my?when=PAST` power the two history tabs.
- `DELETE /api/v1/bookings/{id}` performs cancellation. The backend remains authoritative for the two-hour cutoff, duplicate booking, capacity, session state and package-credit restoration.
- Error codes including `ALREADY_BOOKED`, `FULL`, `BOOKING_CHANGED`, `NO_SESSIONS_LEFT` and `CANCELLATION_CUTOFF` produce actionable UI messages. Failed requests do not produce success screens.

## Figma alignment and contract notes

- F2-07 presents only matching eligible package registrations, with available session counts before and after booking. An extra payment of 0 VND is shown because this flow reserves one session from an already-paid package.
- F2-08 rechecks eligibility and displays session, coach or self-training format, facility, package, credit change and confirmation action.
- F2-09 shows booking code, session details, remaining package sessions, My Bookings and schedule links, and a downloadable calendar event.
- F2-10 includes Upcoming and Past tabs, confirmed/cancelled states and the cancelling actor. Self-training records do not show a coach.
- S2-Cancel displays the exact two-hour Vietnam-time cutoff and only shows the success state after the DELETE response confirms `CANCELLED`.
- Figma examples showing a separate single-visit purchase are not implemented: the FE-09 booking endpoint accepts an existing eligible package registration, and no single-visit purchase operation is part of this API contract. The UI links to the existing package catalog instead of fabricating a purchase or price.
- Package sessions are reserved when booking is confirmed by the backend. Cancellation restores the reserved session when accepted. The UI does not claim a credit change after a failed request.

## Test evidence

- Focused tests cover API payloads and responses, eligible-registration filtering, Vietnam-time cutoff calculation, calendar event generation, coach-led/self-training rendering, single-submit behavior, conflicts, history filters, cancellation success/failure and role guards.
- `npm run test --prefix frontend`: passed, 45 test files and 698 tests.
- `npm run lint --prefix frontend`: passed.
- `npm run build --prefix frontend`: passed. Vite reports the existing non-blocking warning that the main JavaScript chunk is larger than 500 kB after minification.
- Live API verification requires a running backend and database; this run used mocked HTTP responses.

## Manual test steps

1. Sign in with a MEMBER account with an active, paid sport-package registration that matches a future session's sport and training format and has remaining sessions.
2. Open a session from `/member/schedule` or `/member/classes`, then choose **Book session**. Confirm that Booking options retains the return path and shows eligible packages with remaining counts before and after.
3. Choose a package and continue to Review booking. Verify the session details and package choice, then confirm once. Confirm the booking code, session summary, remaining credits and calendar download.
4. Open **My Bookings**. Confirm the booking appears in Upcoming. Switch to Past and confirm the URL selects the Past tab and the corresponding backend filter is used.
5. Return to Upcoming, choose **Cancel booking**, check the displayed two-hour Vietnam-time cutoff, then confirm. Confirm the result is shown as cancelled only after the backend returns `CANCELLED`; verify it appears in Past with its cancellation actor.
6. Repeat with a self-training session. Confirm no coach block is displayed on options, review, confirmation or history.
7. Exercise a fully booked or already-booked session and a package that becomes ineligible between review and submit. Verify no successful booking state is shown.
8. Attempt cancellation outside the backend cutoff. Verify the returned cutoff message appears and the booking remains confirmed in My Bookings.

Live API verification requires the backend and database to be running with a MEMBER, a matching package registration and available published sessions. This frontend verification uses mocked HTTP responses and does not create backend data.
