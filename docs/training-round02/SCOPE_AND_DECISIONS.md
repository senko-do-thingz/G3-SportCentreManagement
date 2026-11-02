# Class learning scope and decision register

## Authority and approval status

Source: the user's 2026-11-02 class-learning request and CODEOWNER Round 01 findings F-01 through F-09. This request authorizes implementation work; it is not a CODEOWNER merge approval. The NX-04 baseline remains PROPOSED/BLOCKED_PENDING_APPROVAL. Do not rewrite that historical approval record to manufacture approval for V37.

User-selected policy on 2026-11-02: reject conflicting attendance corrections once a result exists; keep attendance time flexible and reject results before the session ends. This selection awaits the explicit CODEOWNER policy confirmation required in F-01/F-02.

## Minimum implemented behavior

- Coaches record attendance for sessions currently assigned to them. Existing reception and Manager access on the legacy self-training endpoint is preserved. Reception training routes retain their existing role boundary.
- PRESENT/LATE participation is required for a result. Once a result exists, ABSENT/EXCUSED corrections are rejected atomically. Notes and transitions between PRESENT and LATE remain auditable corrections with a reason. This is the proposed rejection policy, not result invalidation.
- Result availability starts at the session end instant in the center Clock timezone (Asia/Ho_Chi_Minh). Before/start/before-end requests are rejected. End/after-end requests are accepted when other eligibility conditions hold.
- Reception/coach attendance can be recorded before the start time on the session date, or retrospectively. Future-day attendance is rejected. The legacy self-training endpoint remains same-calendar-day only, with flexible time within that day. No blanket ban on early arrival is introduced.
- Members see class name, sport, level, capacity, coach, and their own booked-session attendance/results. Cancelled bookings are displayed as cancelled, never automatically absent. No other member roster is exposed.
- Members with a confirmed class booking can ask its assigned coach about supplementary practice. Questions and replies persist through existing support_request/support_request_message tables. Each read/write checks member ownership or the current assigned coach.
- Coaches can reply with practice guidance and assign supplementary personal-plan steps through the existing plan flow. No generated exercise is automatically assigned.
- A saved result is projected in the coach roster. The form excludes members with a result and prevents another submission after a successful save. Database checks remain authoritative.

## AI decision

User confirmation: no provider key exists; do not fake AI. No AI calls, synthetic AI responses, provider package or secret is introduced. Assistant entry offers labeled catalog search and access to the coach question flow. Model-generated class recommendations, automatic consultation and exercise generation remain unavailable until a provider, approved data-access contract and credentials are supplied. Catalog search is not an AI recommendation system.

## Schema and compatibility

V37 is unchanged in Round 02. It adds nullable personal-plan ownership/booking links and plan-linked feedback while preserving legacy rows. No new migration or enrollment model is introduced. Questions reuse V17 tables; audit uses activity_log. Applied migrations must never be edited or dropped from a deployed database.

V37 release approval is still pending CODEOWNER/schema-owner reference. Local SQL tests validate transactional DDL dry-run rollback, forward migration, replay and preservation of a legacy plan. These tests prove technical behavior, not approval. Application rollback must retain V37 columns and data; do not execute destructive downgrade DDL. Restore a reviewed application version compatible with the additive columns and keep a database backup for operational recovery.
