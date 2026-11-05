# Manager operations report

`GET /api/v1/manager/reports/operations?from=2026-11-01&to=2026-11-30` is Manager-only.
It returns Member registration counts, class-session booking counts and the existing
authoritative revenue breakdown for the same inclusive center-local date range.
The finance service supplies the existing defaults, 366-day bound and supported date limits.

`registeredMembers` counts current MEMBER-role accounts created in the period.
`currentlyEnabledMembersRegistered` is the currently ACTIVE subset of those accounts;
it does not claim their historical status at the end of the selected period.
Synthetic reservations, known simulator provenance and reserved demo email identities
are excluded from operational counts. Actual revenue retains the existing finance semantics.

`classBookings` counts booking rows by `booked_at`, only for sessions linked to a class.
It does not count membership package registrations or standalone self-training bookings.
`confirmed` and `cancelled` reflect the current booking status. `upcoming` and `completed`
are subsets of confirmed bookings: published sessions from the current instant onward,
and sessions explicitly marked COMPLETED, respectively. An unmarked past session is
never silently called completed. These subsets are not an additional partition of total.
Rescheduling changes the session facts and Session ID, not the number of booking rows.

No new finance calculation, migration, speculative index or historical data rewrite is introduced.
