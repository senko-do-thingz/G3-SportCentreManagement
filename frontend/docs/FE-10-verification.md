# FE-10 Receptionist and Coach Verification

Run these checks with the frontend and a backend version that includes BE-13 and BE-14. Sign in with seeded accounts for the RECEPTIONIST, COACH and MEMBER roles. The frontend tests use an isolated fake API; they do not establish live backend availability.

From `frontend`, run:

```text
npm test
npm run lint
npm run build
```

## Receptionist: member bookings and assisted booking

1. Sign in as RECEPTIONIST and open `/reception/bookings`. Confirm the member search is available and booking-history requests do not run before a member is selected.
2. Search for a known member. Confirm the selected member and search query remain in the URL. Search for a no-match query and confirm a clear empty state. Make `/members` fail and confirm the error and retry action appear.
3. Open the selected member's history. Check upcoming/past, status filters, paging, booking details, and empty results. Confirm changing the member or filters does not show the previous member's bookings.
4. Cancel a cancellable booking. Confirm the dialog identifies the booking; cancel the dialog and verify no DELETE is sent. Confirm the cancellation and verify the page reports success only after the API returns `CANCELLED`, then refreshes history. Simulate a cutoff/server error and confirm the booking remains visible with the backend message.
5. Choose “Book for member”. Select a session, check eligibility for the selected member, and select an eligible package. Confirm ineligible/full/already-booked states cannot submit. Submit once and verify the request sends the explicit `memberId`, `sessionId`, and `packageRegistrationId`; double-click and confirm only one request is sent.
6. Complete a booking and verify the confirmation page uses the returned booking details and the return link preserves member/search context. Simulate 409/400/5xx responses and confirm no success state is shown and entered context is retained.

## Coach: teaching schedule and class roster

1. Sign in as COACH and open `/coach`. Confirm loading, schedule error/retry, no-assigned-sessions empty state, and populated weekly schedule. Navigate previous/current/next week and verify dates use `dd/MM/yyyy` and times use Vietnam time.
2. Confirm schedule requests use `GET /coach/sessions?from=...&to=...` without a caller-supplied coach ID. Check the week metrics against the response and verify past sessions are not presented as the next session.
3. Open `/coach/roster` with no selected session. Select one from the current coach's schedule. Confirm roster loading, API error/retry, no-confirmed-bookings empty state and populated roster states.
4. Try a session ID that is not in the coach's loaded schedule. Confirm no roster request is issued and the page explains that the session is unavailable. Confirm the server still enforces ownership for direct API requests.
5. Verify roster rows contain member code/name, package and booking time, but never member phone. Print and confirm the printable output contains the selected session and roster without the application navigation.

## Role guards and layout

1. Sign in as COACH and open `/reception/bookings`; expect the access-denied route and no receptionist data request.
2. Sign in as RECEPTIONIST and open `/coach` and `/coach/roster`; expect access denied and no coach schedule/roster request.
3. Check the staff screens at desktop and narrow viewport widths. Confirm the tables remain usable without the page overflowing horizontally and forms/actions remain reachable by keyboard.

## Scope notes

- The implementation uses only the FE-10-listed member, session, booking, eligibility, and coach-session/roster APIs. No backend code, endpoint, schema or seed data was changed.
- FE-10 does not include a one-time-visit booking endpoint. The assisted booking uses the package-registration contract exposed by BE-13.
- BE-14 roster records do not include member level or goal; those fields are intentionally omitted rather than fabricated. The response's `memberPhone` is not displayed.
- The booking API response does not confirm that a notification was sent, so the confirmation screen does not claim notification delivery.
