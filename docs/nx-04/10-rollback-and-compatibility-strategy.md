# Rollback and compatibility strategy

Status: PROPOSED, BLOCKED_PENDING_APPROVAL for runtime recovery. NX-04 writes no schema/data, so its present rollback is a normal revert of documentation/tests on the task branch if CODEOWNER requests it. No database recovery is needed for this delivery.

## Candidate application and schema recovery

| Failure point | Proposed response | Evidence and approval before execution |
| --- | --- | --- |
| Additive schema succeeds, new application fails before new writes | Stop feature rollout and roll back application to a verified compatible reader/writer; leave additive objects in place | Old application + expanded schema validation/rehearsal, release owner approval; no destructive schema rollback |
| New history rows already exist | Disable further training writes, preserve new records and access controls; assess old app behavior before application rollback | Version/projection compatibility and no bypass/dual writer; business/privacy owner reviews any inaccessible history |
| Migration fails before commit | Inspect actual schema and Flyway history, verify rollback rather than assume it | SQL Server engine/config, statement transaction support, catalog inspection and captured failure; disposable failure rehearsal |
| Migration partially applies | Hold deployment; reconcile exact object definitions/data; issue reviewed fresh forward corrective migration | CODEOWNER-approved correction/version; history repair only after explicit schema reconciliation and owner review |
| New constraint rejects real data | Hold rollout; retain data and classify conflicts; approved forward fix/backfill or revised future constraint | Measured conflict populations, approved history/identity disposition; no deleting conflicting rows to force green |
| Ownership/field leak or double financial effect detected | Stop affected new flow, preserve attributable minimized evidence, invoke existing incident/release process | Privacy/finance and environment owner triage; NX-04 does not authorize live compensating financial writes |
| Destructive recovery requested | Escalate to environment/data owner, test recovery on disposable copy first | Verified backup/PITR capability, RPO/RTO and restore drill, exact target and separate destructive recovery approval; none evidenced here |

Flyway is not an automatic application-data undo mechanism. SQL Server transactional failure may roll back eligible statements in a particular transaction; that does not undo a successfully applied version or guarantee rollback of all partial effects. Do not edit/delete applied migrations or invoke clean. Review Flyway transaction behavior against the actual engine/configuration before implementing recovery; [Flyway transaction handling](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/migration-transaction-handling) is supporting vendor guidance. Backup/PITR, online restore and downtime capabilities are UNVERIFIED_RUNTIME, so no timed restore promise is made.

## Compatibility contract

Keep existing arrival aliases, role annotations, business denial result, history filters and center-local date interpretation (S10-S12/S26). Keep booking-time package consumption, confirmed booking uniqueness, capacity/lock order, cancellation restoration and cancellation-after-ALLOWED restriction (S06/S13/S35). Training writes cannot create/cancel bookings, deduct/restore package sessions, invoice, charge, refund or generate cash events. Self-training ACK stays explicitly non-persistent until an independently approved implementation replaces it.

Schema expansion precedes dependent mappings. Hibernate validate must pass on the approved new mapping plus expanded schema; old mappings/readers must also be rehearsed. New non-null columns must not break old writes through missing defaults/backfill; defaults cannot fabricate business attribution. Avoid changing legacy table/column names, narrowing text/precision, changing existing checks/FKs/cascades or removing allowed states without a separately approved compatibility plan. No training schema change may silently alter public catalog `/api/v1/plans` membership-plan semantics.

Backward API compatibility includes no new generic error categories for old arrival GET, no write tokens on read-only endpoints, and no remapping existing 201/DENIED to speculative contract errors. Future training error codes/statuses and Member history projections wait for D-10/G-01. Preserve unsigned decision labels in downstream documentation so technical CI success is not mistaken for adoption.

## Compensation limits

Corrections are attributable forward history, not a rollback that deletes prior content. A bad observation may be amended only through the signed correction authority/window/visibility policy. Retention/erasure is a separate D-03/D-12 decision. If a new flow incorrectly changed money, stop it and request finance-owner remediation through existing reviewed services; do not directly update package balances or cash tables. Every recovery action requires target-specific authority and reproducible disposable tests before live use.
