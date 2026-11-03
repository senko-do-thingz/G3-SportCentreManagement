# Canonical Session ID

The public Session ID is a persisted ten-character business identifier such as
031126FB01. class_session.id remains the BIGINT surrogate primary key. Booking,
attendance, result, cancellation and other foreign keys continue to reference it.

## Representation and allocation

The format is ddMMyy + sport code + two-digit allocation, using scheduled center
calendar dates, not browser dates or creation timestamps. The approved mapping is
FOOTBALL -> FB, SWIMMING -> SW, BADMINTON -> BM, VOLLEYBALL -> VB,
BASKETBALL -> BK and TENNIS -> TE. Canonical sport.code supplies the mapping;
free-text class names never determine it.

Supported scheduled years are 2000 through 2099 inclusive. This explicit window
prevents two centuries from sharing the same public identifier. Allocation is
creation order within each scheduled date/sport, independent of class, Coach,
facility and start time. Allocation 01 through 99 is available. A 100th allocation
fails before creating a session; it is not wrapped or expanded to another format.

session_code_counter has an indexed primary key (session_date, sport_code) and
keeps last_value. SessionCodeAllocator requires an existing transaction and first
takes an exclusive transaction-owned SQL application lock on the database-scoped
sportify.session-identity resource. This serializes the small allocation section
across date/sport groups and avoids adjacent missing-range deadlocks in batches.
The lock stays held through the complete creation/batch commit or rollback.
When JDBC has not started its implicit transaction yet, an ordinary counter-table
read starts it before EXEC requests a transaction-owned application lock. That
read takes no retained update/range lock. Negative application-lock return codes
raise a runtime conflict so the enclosing Spring transaction rolls back.
It then reads the counter using UPDLOCK,HOLDLOCK, including a missing-key range. Counter update,
session insertion and weekly batch work share the Manager service transaction.
All batch dates are sorted. The public code has a database NOT NULL and UNIQUE
constraint, with date-prefix and format checks as a final safeguard.

Cancellation does not decrement the counter. Removing a session does not remove
its independent reservation high-water mark. There is no counter-delete API or
cascade. A rolled-back transaction has issued no successful public identifier:
an explicit retry can reserve its rolled-back number. Lock/deadlock failures use
the existing Manager conflict handling and rollback. Application-lock refusal
returns a clear conflict before touching a counter. Retry the complete transaction
after a confirmed rollback; do not automatically retry
an uncertain successful create and risk a second session.

SQL Server range-lock semantics are documented in the
[Microsoft locking guide](https://learn.microsoft.com/en-us/sql/relational-databases/sql-server-transaction-locking-and-row-versioning-guide?view=sql-server-ver17).
Transaction-owned application-lock requirements and return codes are documented
by [sp_getapplock](https://learn.microsoft.com/en-us/sql/relational-databases/system-stored-procedures/sp-getapplock-transact-sql?view=sql-server-ver17).

## Existing rows and forward-only migration

V39__canonical_session_identity.sql validates all existing rows, including
CANCELLED sessions, before changing persistent rows or schema. Unsupported sport,
unsupported year and more than 99 rows in a date/sport group stop migration with
actionable diagnostics. Existing rows are ranked by scheduled date, sport,
start_time and numeric id. Ties use numeric id deterministically. The resulting
codes and group high-water marks are then stored once. Later time/Coach/facility
edits never reorder historical allocations.

Main includes catalog V37. The separately reviewed PR #185 and PR #193 heads
both contain demo V38 for photo assignment. Earlier review evidence recorded
the prior PR #193 V37 conflict; its current head has moved that script to V38.
V39 avoids importing either excluded copy. Earlier migration bytes are unchanged.
Future integration of those PRs still requires owner review of their migration
lineage; the unused version 38 is not permission to enable demo/photo work.

V39 is the current deployment candidate. Applying demo-photo V38 later to a
database already migrated through V39 may violate Flyway's default version
ordering when the demo location is enabled. Before any later photo integration,
inspect the actual target Flyway history and every database where V38 may already
be installed. If V38 has never been installed, an approved new forward version
may be needed. If it is already installed anywhere, review that lineage
separately. Do not renumber an installed script or use repair, clean, baseline,
outOfOrder, or history edits to bypass this decision. This round does not change
V39 or import either excluded photo migration.

See the [release decision and deployment gates](../release/session-identity-release.md).

Run this migration only through the approved forward upgrade on a compatible
database. Never use Flyway repair, clean, baseline, migration rewrites or history
edits. sportify_demo and sportify_qr_demo are not safe test targets. Integration
fixtures use fresh dedicated databases or CI SQL Server containers.

## Identity edits

Issued Session IDs are immutable. All session-date changes are rejected,
including unbooked sessions. The least disruptive alternative is the existing
controlled cancellation and creation of a replacement session, with a new ID.
Bookings are never silently moved to it. This avoids an unsigned reallocation
workflow and preserves references already shared with users.

Allowed Coach, facility, start/end time, capacity and status changes retain the
ID and existing authorization, resource locks, booking/package restrictions and
audit behavior. Completed/cancelled sessions retain the existing edit restrictions.
No package credit, attendance cutoff/correction, Manager backfill setting,
result permission or financial rule changes as part of identity allocation.
