# Integrity uniqueness and concurrency

Status: PROPOSED for new enforcement; SOURCE_CONFIRMED existing invariants are explicitly identified. The matrices distinguish database integrity, service authorization and financial behavior. D-13.UNIQUENESS must select business identity before a new index is named or coded.

| ID | Invariant and evidence | Candidate enforcement | Dependencies and tests |
| --- | --- | --- | --- |
| INV-01 | Arrival, booking and participation are independent; S10/S13/S14 | Do not create check_in, booking, payment or a package adjustment when writing Coach participation | D-01, D-13.FINANCIAL_INVARIANTS; TC-031..034 |
| INV-02 | Existing attendance unique pair is session/member in S04 | Preserve current UNIQUE; if selected as identity, serialize create with constraint conflict handling; per-booking alternative needs reviewed data conversion | D-13.UNIQUENESS; TC-006..008 |
| INV-03 | Independent FKs do not bind booking to attendance Member/session | Recheck booking.member/session and chosen eligibility in mutation transaction; optional composite FK is a candidate only after approved key design | D-13.ELIGIBILITY; TC-001..004,029 |
| INV-04 | Initial CREATE has no previous target revision | Atomic identity guard, reject/resolve competing create with no silent update; transport replay identity separate from natural uniqueness | D-10, D-13.UNIQUENESS; TC-007,008,011 |
| INV-05 | Existing-target correction must not overwrite intervening change | Compare expected current token atomically; retain prior/current values and actor/reason in one transaction; no timestamp-only lock | D-03.CORRECTION_MECHANISM, D-13.HISTORY; TC-009,010,012 |
| INV-06 | Member/session/Coach binding must be true at every write | Principal/role reload plus repeated scope predicate after selected parent locks; no client actor override | D-12.RESOURCE_RIGHTS/REASSIGNMENT; TC-017,019,020 |
| INV-07 | Previously used plan content must not change through later edit | New preserved revision and exact session-use FK; current pointer switch atomic; publication audience separately checked | D-02.REVISION_LIFECYCLE/PUBLISH_AUTHORITY; TC-037..040 |
| INV-08 | Reschedule/cancellation/reassignment must preserve history and privacy | Invalidate future eligibility as signed; retain prior context; no inferred absence, deletion, transfer of private notes or second refund | D-02.SESSION_RELINK, D-13.LIFECYCLE/HISTORY; TC-035,036,041 |
| INV-09 | No extra package deduction, charge, refund, invoice or financial event | Training service cannot invoke consumeSession/restoreSession/cash/payment paths; verify durable balances/events before and after future training tests | Existing S13/S35; D-13.FINANCIAL_INVARIANTS; TC-031..034 |
| INV-10 | Missing, NULL and zero have distinct meanings | Explicit metric validation and NOT NULL where required; no default score or fabricated ABSENT | D-03.METRICS/VALIDATION, D-13.STATE_SEMANTICS; TC-005,013,014,042 |
| INV-11 | Time provenance cannot be silently rewritten | Keep local schedule and arrival behavior; choose UTC/offset precision for new mutation instants; reject ambiguous conversion pending policy | D-10, D-13.TIMING_BOUNDARIES; TC-057..060 |
| INV-12 | Deletes, retention, defaults and exports do not imply permission | No new cascades or purge jobs; classify narrative fields; approved history projections and scoped privacy operations | D-03.RETENTION, D-12.PRIVACY_OPERATIONS/AUDIT_ACCESS; TC-025,026,044,048 |

TC suffixes refer to full NX04-TC identifiers in [test specifications](11-test-specifications-and-negative-controls.md).

## Candidate uniqueness models

| Model | Candidate identity/predicate | Existing conflict and migration question |
| --- | --- | --- |
| Current attendance per Member/session | (session_id, member_id), already declared | Best reuse candidate; cancelling and rebooking the same session does not create a second participation identity. D-13.UNIQUENESS must confirm that meaning |
| Current attendance per booking | booking_id for non-null booked cases; separate approved identity for unbooked cases | Rebooking may create several rows for one session/Member, conflicts with existing unique pair; nullable UNIQUE alone mishandles multiple unlinked records |
| Stable participation parent plus revisions | Existing pair on parent, unique parent+revision and expected-current head | Reuses parent and extends correction chain; new revision details/indexes require approval |
| Several immutable attendance event rows plus current marker | Candidate filtered unique current pair with history elsewhere or in same table | Requires state/current-marker definition, backfill and duplicates analysis; cannot coexist casually with existing unfiltered pair UNIQUE |
| Result values | Candidate (revision_id, metric_version_id) | Existing score allows duplicate metrics per result and no metric versions; dictionary design must precede new uniqueness |
| Plan revision/recipient/session use | Candidate identity+sequence; assignment+member; session-use uniqueness TBD | No single-plan-per-session rule, recipient permanence or template uniqueness invented |

## Transaction and race timelines

CREATE A/B: both resolve the same currently authorized scope, then contend on the selected parent/identity guard. Only the approved number of authoritative current rows may commit. The loser gets the reviewed duplicate/concurrent outcome; it does not overwrite the winner. A retry token, if approved, must be scoped to actor/resource/operation and body meaning. No identical-response or expiry promise is selected. Existing arrival behaves differently: attempts can both persist, with a later DENIED business result.

CORRECT A/B: both read revision R; B commits R+1 with its audit lineage. A's expected R compare must affect zero current rows and leave R+1 unchanged. Scope/active-account checks occur again, even if A authored R. Correction reason and current value must commit together; failed required history persistence must not leave an unaudited current value (PROPOSED consistency rule, D-13.HISTORY). A new generic audit entry alone cannot preserve full version content.

PUBLISH/edit: selected identity/current revision lock or optimistic compare must make publication and audience/session-use association coherent. A content edit racing publication cannot mutate the preserved published revision. New attendees must not receive old private content just because a roster changed; snapshot versus dynamic policy remains unsigned.

Existing booking/check-in operations use session-first locking (S10/S13/S17), then refresh/revalidate booking/package state. Future attendance must coordinate its selected eligibility check with cancellation/reassignment under a reviewed lock/optimistic protocol; merely checking before entering a transaction is insufficient. No verified database isolation setting, deadlock rate or lock duration is claimed. Disposable SQL Server tests must exercise both lock orders, rollback and concurrent changes before implementation is accepted. Database constraints protect existence/uniqueness, not current Coach ownership or Member field privacy.
