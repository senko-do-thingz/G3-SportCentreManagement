# Canonical Session ID contract

Session ID means the persisted ten-character business code, for example
031126FB01. Numeric class_session.id remains an internal surrogate for existing
foreign keys. No date, sport or allocation is synthesized in the browser.
See [storage and allocation](../database/11-session-identity.md) for the date
window, counter locking, cancellation non-reuse and permanent-date policy.

## Compatibility matrix

| Response or input | Canonical public identity | Preserved numeric compatibility |
| --- | --- | --- |
| Session browse/detail | sessionId string | id numeric, deprecated for new navigation |
| Manager create/calendar/detail | sessionId string | id numeric |
| Coach calendar | sessionId string | id numeric |
| Coach/Reception training session | sessionId string | id numeric |
| Member attendance/result nested session | sessionId string | id numeric |
| Booking response and confirmation | sessionCode string | Existing sessionId numeric retained unchanged |
| Member class participation | sessionCode string | Existing sessionId numeric retained unchanged |
| Manager backfill candidate | sessionCode string | Existing sessionId numeric retained unchanged |
| Schedule conflict and existing catalog suggestion projection | sessionCode string | Existing sessionId numeric retained unchanged |
| Booking, attendance and result legacy writes | Code selection/lookup before existing write | Existing numeric sessionId or bookingId input remains valid |
| New result route | sessionId string in path | Request contains memberId, notes and scores only |
| Operational audit | sessionCode, or Manager snapshot sessionId string | Existing surrogate entity ID and numeric reference fields retained |

The existing numeric sessionId keys cannot be changed to strings in place
without breaking deployed v1 consumers. These named exceptions therefore expose
the same persisted public identity through additive sessionCode, with the old
numeric key explicitly deprecated. New contracts use sessionId string. The UI
labels only the business code as Session ID and does not display numeric keys
as its replacement. No legacy route is removed and no forced removal date or
retention change is approved here.

The frontend models public SessionId as a branded string, separate from numeric
session surrogates, bookings and classes. Public lookup boundaries validate
length, uppercase sport mapping, actual calendar date and allocation 01-99.
An older server omitting an additive identity field is shown as unavailable;
the frontend never invents an identity from other fields. Numeric route support
remains a compatibility path during rollout.

## Code-based routes

All paths below are prefixed /api/v1. Path codes are case-sensitive uppercase
identities. Invalid syntax/date returns 400. Unknown or invisible objects return
404 without private record contents. Existing authentication and role scopes
apply before lookup. Anonymous requests are 401; unauthorized roles are 403.

| Method and path | Scope and behavior |
| --- | --- |
| GET /sessions/by-code/{sessionId} | Member, Reception, Manager; same visibility and member-bound eligibility as numeric detail |
| GET /manager/sessions/by-code/{sessionId} | Manager calendar/detail including cancelled sessions |
| PUT /manager/sessions/by-code/{sessionId} | Existing locked edit operation; permanent date/ID preserved |
| PUT /manager/sessions/by-code/{sessionId}/cancel | Existing cancellation operation; code reservation retained |
| GET /coach/sessions/by-code/{sessionId}/roster | Coach sees assigned session only; Manager retains existing scope; foreign Coach gets 404 |
| GET /coach/training/sessions/by-code/{sessionId}/members | Existing assigned-Coach training roster policy |
| GET /reception/training/sessions/by-code/{sessionId}/members | Existing Reception session roster policy |
| POST /coach/training/sessions/by-code/{sessionId}/results | Existing result validation/locking, member ownership and attendance rules; resolves the persisted code internally |

No endpoint above creates bookings, transfers bookings, grants a role, changes
package credit or enables Manager backfill. Existing numeric routes remain
available with their original authorization and financial behavior.

## Roster and schedule projections

Training session rows include persisted identity, sport, assigned Coach, facility,
status and actual confirmed-booking/marked-attendance counts. Counts come from
one aggregate SQL query joined to the session projection, not browser guesses
or a request for every session. Existing attendanceDateOpen remains derived
from the server center calendar. Session history projections join only authorized
member records and do not expose another member's private history.

New identity fields use to-one joins or scalar projections. They do not introduce
per-session repository calls. Code lookup uses the unique persisted index and
then applies the same existing authorization/visibility checks. Legacy numeric
booking IDs remain authoritative for attendance writes; selecting a code does
not allow switching the booking's owner/session in the request.

The catalog suggestion change is only an additional identity field on an existing
projection. No matching/scoring, AI generation, provider, recommendation logic
or deferred HOT-07/HOT-08/HOT-09 feature is implemented or altered.
