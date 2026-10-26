# Ownership RBAC and privacy matrix

Status: PROPOSED except explicitly OBSERVED_EXISTING arrival and schedule/log rights. D-12 and D-10/G-01 remain pending. This is a deny-by-default design, not a change to source authorization. Four independent checks are necessary: active authenticated account/role, resource relationship, action/lifecycle permission, field projection. A valid FK supplies none of the last three.

## Actor and operation matrix

CONDITIONAL_SCOPED_COACH means no grant until signed current assignment plus action policy. CONDITIONAL_SELF_PROJECTION means own subject plus approved fields and historical entitlement. PENDING_NO_NEW_GRANT preserves existing unrelated rights without granting training access. ADMIN is unverified as an independent role in S09/S19, so its column cannot supply rights.

| Family and action | Coach | Member | Receptionist | Manager | Admin | Guest | Decision dependencies |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F4-PLAN create/edit/publish | CONDITIONAL_SCOPED_COACH | DENY | DENY | PENDING_NO_NEW_GRANT | NO_VERIFIED_ROLE_GRANT | DENY | D-02, D-12, D-10 |
| F4-PLAN current/history read | CONDITIONAL_SCOPED_COACH | CONDITIONAL_SELF_PROJECTION | DENY | PENDING_NO_NEW_GRANT | NO_VERIFIED_ROLE_GRANT | DENY | D-02.HISTORICAL_ACCESS, D-12.HISTORIC_ENTITLEMENT |
| F4-RESULT create/correct | CONDITIONAL_SCOPED_COACH | DENY | DENY | PENDING_NO_NEW_GRANT | NO_VERIFIED_ROLE_GRANT | DENY | D-03, D-12.CORRECTION_RIGHTS, D-10 |
| F4-RESULT current/history read | CONDITIONAL_SCOPED_COACH | CONDITIONAL_SELF_PROJECTION | DENY | PENDING_NO_NEW_GRANT | NO_VERIFIED_ROLE_GRANT | DENY | D-03.RESULT_HISTORY, D-12.FIELD_VISIBILITY |
| F4-ATTEND participation create/correct | CONDITIONAL_SCOPED_COACH | DENY | DENY | PENDING_NO_NEW_GRANT | NO_VERIFIED_ROLE_GRANT | DENY | D-01, D-13, D-12 |
| F4-ATTEND participation/history read | CONDITIONAL_SCOPED_COACH | CONDITIONAL_SELF_PROJECTION | DENY | PENDING_NO_NEW_GRANT | NO_VERIFIED_ROLE_GRANT | DENY | D-13.HISTORY, D-12.HISTORIC_ENTITLEMENT |
| F4-ATTEND existing arrival POST/GET | DENY | DENY | OBSERVED_EXISTING | OBSERVED_EXISTING | NO_VERIFIED_ROLE_GRANT | DENY | Existing S10-S12, D-01 for future linkage only |
| F4-PROGRESS create/correct | CONDITIONAL_SCOPED_COACH | DENY | DENY | PENDING_NO_NEW_GRANT | NO_VERIFIED_ROLE_GRANT | DENY | D-03.COMMENT_VISIBILITY, D-12.CORRECTION_RIGHTS |
| F4-PROGRESS scoped review | CONDITIONAL_SCOPED_COACH | DENY | DENY | PENDING_NO_NEW_GRANT | NO_VERIFIED_ROLE_GRANT | DENY | D-12.RESOURCE_RIGHTS/HISTORIC_ENTITLEMENT |
| F4-PROGRESS own visible feedback | CONDITIONAL_SCOPED_COACH | CONDITIONAL_SELF_PROJECTION | DENY | PENDING_NO_NEW_GRANT | NO_VERIFIED_ROLE_GRANT | DENY | D-03.COMMENT_VISIBILITY, D-12.FIELD_VISIBILITY |

New operations retain NX-03 logical IDs; exact routes, HTTP statuses, enums, headers and wire field names remain unsigned. Read errors must not include write-revision or duplicate-write conditions. Existing arrival keeps its source route aliases and 201/DENIED behavior; no new Member arrival endpoint is inferred. Member-facing attendance is a distinct planned projection.

## Object predicates and enforcement points

| Boundary | Current evidence | Proposed enforcement and missing element |
| --- | --- | --- |
| Authentication | S18 JWT chain, authenticated fallback, method security; S19 ACTIVE checks | Resolve current account on sensitive operations as S15 does; recheck role/status at mutation time; no client actor/Coach ID as authority |
| Session Coach ownership | S15 reloads actor and checks session.coach; S16 repeats ownership in PII query | Apply equivalent current session predicate in each training read/write query; require current authorized profile and no reassignment race |
| Personal Coach relationship | No source-backed general Coach-Member assignment ledger found | D-12 must choose evidence mechanism before personal authoring; common class attendance or authorship alone is insufficient |
| Member self scope | S13 derives own member key and denies peer booking mutations | Derive subject from principal for own history; put subject predicate in database query before count/order/page; never filter a broad loaded collection afterward |
| Lifecycle/eligibility | Booking context and status checks exist in S13; no Flow 4 service found | Revalidate selected Member/session/booking consistency, approved timing, correction rights and selected revision in the same transaction |
| Field boundary | S16 uses scalar schedule projections; training field policy absent | Dedicated allowlist DTO/read queries; member_comment separate from staff_note; never expose entities or recipient lists directly |
| Audit read | S25/S34 Manager-only controller/service, source projection repository | Preserve observed log rights but prevent new private payloads in generic details. D-12.AUDIT_ACCESS decides any future protected audit view |

For cross-resource writes, resolve authorized parent context before protected content; after any lock, repeat the current binding check. A broad findAll/findById repository is not an authorization boundary. Internal jobs, service calls and future exports need the same predicates as controllers. SQL connection privileges and FK constraints do not substitute for service authorization. Error responses must hide peer existence consistently under D-10/D-12; numeric 403 versus 404 remains unselected.

## Threat model and adversarial cases

| Threat | Proposed prevention and evidence gap | Policy versus present protection | Tests |
| --- | --- | --- | --- |
| Coach A guesses Coach B's session/result | Principal-derived current Coach predicate in selected resource and mutation; no broad repository bypass | S15/S16 protect schedule reads today; new training policy/enforcement pending D-12 | NX04-TC-017, NX04-TC-027 |
| Member A requests Member B's history/count | Self key in query and projection; same hidden-scope behavior across IDs | S13 pattern observed; training read implementation absent | NX04-TC-018, NX04-TC-028 |
| Expired/reassigned Coach replays loaded object | Reload active account; repeat session assignment after lock; historic right separate | ACTIVE/current schedule source available; historical/correction policy pending | NX04-TC-019, NX04-TC-020 |
| Former class recipient sees future or peer content | Separate revision audience and historical entitlement; no live roster broadcast | D-02.RECIPIENTS/HISTORICAL_ACCESS unresolved | NX04-TC-021, NX04-TC-022 |
| Staff bypass through broad queries | Service defense plus scoped repository projection; Manager has no implicit private-note permission | Existing staff arrival/log rights preserved; new training grants blocked | NX04-TC-023, NX04-TC-024 |
| Unbooked/wrong-Member or wrong-session participation | Matching binding checks plus FKs; reject outside signed eligibility model | FKs exist, composite binding absent; D-13.ELIGIBILITY pending | NX04-TC-003, NX04-TC-029 |
| Late/cancelled/rescheduled context | Preserve evidence; validate selected timing/lifecycle; no automatic absence/refund | Existing cancellation logic separate; D-13 timing/states pending | NX04-TC-035, NX04-TC-036 |
| Concurrent duplicate CREATE | Atomic approved identity guard and unique constraint; do not implement upsert | Existing pair uniqueness; business identity selection pending D-13.UNIQUENESS | NX04-TC-007, NX04-TC-008 |
| Old revision replay | Expected-current match and retained previous revision; unauthorized stale actor cannot overwrite | No V16 guard; D-03/D-10/D-12/D-13 pending | NX04-TC-009, NX04-TC-010 |
| Malicious comment markup or log injection | Store bounded permitted text, parameterize queries, encode at presentation, no executable markup in errors/log summaries | No training write service to verify; content policy pending | NX04-TC-046, NX04-TC-047 |
| Sensitive audit payload exposure | Allowlist event metadata, separate restricted reasons; avoid private narrative in summary/details | S23 has no general redactor; serialization failure can drop details | NX04-TC-048, NX04-TC-049 |

## Historical and field scope

Current assignment, original author, recipient snapshot and previous entitlement are separate facts. None proves indefinite access. D-12.REASSIGNMENT/HISTORIC_ENTITLEMENT/CORRECTION_RIGHTS must select current Coach, former Coach and Member history behaviors. Until implemented after approval, new training resources remain unavailable. Proposed safe implementation behavior when authority cannot be established is denial without revealing private content.

For all four families, [dictionary projection allowlists](06-data-dictionary-and-field-classification.md) define candidate response classes. Results and attendance contain only the authenticated Member's rows; class plans may share curriculum but cannot share recipients' results. Staff-only notes, full reasons, actor IP, internal audit details and peer identities are excluded from Member output unless a specific field policy later approves a subset. Manager/Admin omniscience and Receptionist training reads are not source-backed. All privacy exports/erasure and AI use remain separately gated.
