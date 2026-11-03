# Session identity release decisions and gates

The ID change inherits the accepted Release 1 behavior from merged PR194. The
[CODEOWNER decision record](https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/194#issuecomment-6596372609)
was posted on 03/11/2026 for reviewed HEAD
20296881c96b120e607ef59ce729a349a7ce5dbc. It records business contracts, not a
GitHub review approval or authorization to merge, deploy or activate a feature.
The ID change preserves those contracts and does not create a new policy.

| Policy | Accepted Release 1 behavior | Separate future decision |
| --- | --- | --- |
| P-01 | Results require a confirmed booking, elapsed session end and Present/Late attendance. Saved results prohibit ordinary correction to Absent/Excused. | Any exceptional privileged, audited reversal workflow. |
| P-03 | One eligible credit per confirmed booking and the existing eligible cancellation refund. Attendance, results, feedback, plans, conversations and backfill add no credits or financial writes. | Any change to these financial rules. |
| P-04 | Member ownership, assigned Coach/Reception operational scope, revocation for a former Coach and least-privilege audited management access. | Historical private conversation transfer to a replacement Coach. |
| P-07 | Canonical training routes and optional UUID idempotency scoped by actor/action/target, matching replay and payload conflict. Existing receipts must be preserved. | Receipt retention, TTL/expiry and behavior after expiry need a separately signed contract. No deletion, truncation, rotation or repurposing is authorized. |
| P-02 | Manager-only Option A scope is accepted, with backfill disabled in Release 1. TOP 200 discovery is accepted only for the disabled feature. | Positive operational cutoff, production activation and production capacity acceptance remain pending. No 48-hour duration is approved. |
| P-05 | PR194 is canonical; PR185/193 and demo-photo migration integration remain excluded. HOT-07/HOT-08/HOT-09 remain deferred. | Any later reconciliation or AI implementation requires separate scope. |

The attendance quick choices change only local selection. The server status
remains visible, Save remains explicit, corrections keep the required reason,
and saved results offer only Present and Late. Backend authorization, attendance
date restrictions, audit rollback, credits and financial data are unchanged.

## Database and deployment gates

Catalog V40 is the deployment candidate, after unchanged V39. Unmerged demo-photo V38 needs a future
Flyway lineage decision before integration after V40. Inspect actual target
history and whether V38 was installed in any environment. A never-installed
script may need an approved new forward version; an already-installed script
requires separate lineage assessment. Do not modify accepted V39, renumber an
installed script, import excluded scripts, enable outOfOrder, run repair/clean/
baseline or rewrite history to bypass ordering. See the
[storage and migration contract](../database/11-session-identity.md).

CI success does not authorize rollout. Before production deployment, the operator
must inspect the actual target flyway_schema_history, validate checksums and
ordering, take and verify a restorable backup, and approve a coordinated
application/database rollback plan. sportify_demo and sportify_qr_demo have
known incompatible V37 histories and are not authorized targets. An old JAR
alone cannot create sessions after V39 adds the required identity column.

Production must explicitly select prod without demo/test/qr-simulator, keep
ATTENDANCE_BACKFILL_ENABLED=false and ATTENDANCE_BACKFILL_WINDOW unset, and keep
simulators disabled. Actual target database preflight, backup verification,
deployment authorization and post-deployment role-based smoke testing remain
external release gates. No production access or deployment is claimed here.


The 2026-11-03 PR #203 CODEOWNER rescheduling decision supersedes the earlier
permanent-date rule. Deploy backend V40 and the matching UI together. The new
registry must retain every current/retired code. Back up before migration and verify
history/counter ownership before enabling writes. Roll back application traffic
using the approved database restore and coordinated application artifact; do not
reverse V40, erase history or run the old immutable-date UI against a mutable
identity backend as a supported configuration. Verify historical by-code links,
unchanged numeric relationships and package balances during smoke testing.
See [session identity storage](../database/11-session-identity.md).
