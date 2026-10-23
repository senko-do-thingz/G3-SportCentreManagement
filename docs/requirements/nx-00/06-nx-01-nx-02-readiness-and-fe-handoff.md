# Readiness gates and FE handoff

Baseline: `c00a3c1a29677a4f069000fa6ff38fe3c970e492`. Repository source paths below refer to the isolated audit worktree, never the older original checkout.

## Current gates

| Gate/task | Assessment | Evidence / remaining prerequisite |
| --- | --- | --- |
| NX-00 Confirm official Topic 4 scope | READY FOR HUMAN REVIEW; not signed | Official row verified; complete registry, gaps, source/code distinctions and integrity evidence. CODEOWNER/business review still pending. |
| NX-01 Audit existing main backend and data model | Technically audit-ready; formal start conditional on accepted NX-00 handoff or explicit audit-only authorization | Tasks EN row 7 depends on NX-00. Current source baseline and reuse/conflict pointers supplied. No NX-01 work product is claimed complete. |
| NX-02 Confirm Flow 4 scenarios and business rules | Not ready for approval or implementation | Tasks EN row 8 depends on NX-00/NX-01; needs NX-01 inventory and human D-01/02/03/04/09/11/12/13 decisions. |
| NX-03 / NX-04 API and DB design | Not ready | Approved NX-02 scenarios, RBAC, lifecycle, linkage and FE agreement first. No proposed route/table here is an approved contract. |
| NX-05 / NX-06 FE maps | Scenario-gated | Tasks EN rows 11/12 depend on NX-02. Existing UI alignment may continue independently, but new training interactions need agreed scenarios. |
| NX-16 notices/homework | Scope review pending | Tasks EN row 22, F4-CAND-08 and D-04; no speculative notification API. |
| NX-20 / NX-24 Flow 5 | Optional decision pending / Member candidate NOT APPROVED | NX-20 row 26 depends on NX-02/NX-19; NX-24 row 30 depends on NX-20/NX-23 and explicit Member feature approval. No automatic Member recommender work. |
| NX-27 Flow 6 | Independent optional decision pending | Tasks EN row 33 depends on NX-02; Order EN places NX-27 on row 31. Join by task ID. No requirement that Flow 5 be adopted first. |

Work Order `Dependency Gates!A6:H13` separates S0 scope/main audit, S1 use cases/contracts, and independent S3/S4 AI decisions. Later test/release gates are planning context and have not been run or satisfied in NX-00. Required Flow 1-3 regression remains a future release requirement, not an authorized test run in this documentation task.

## NX-01 handoff, with limits

Inputs: baseline SHA, source ledger and workbook hashes; official capability registry; D-01..D-14; C-01..C-09; code pointers for arrival persistence, self-training acknowledgement, Coach roster and booking data; V16-V19 artifacts. These make a next audit concrete and reviewable.

The next authorized audit would need a full backend entity/repository/service/controller/DTO/security/test/migration inventory, schema-to-code comparison, and reuse/gap matrix. Confirm application-level enforcement instead of reading schema comments as business rules. Preserve booking/cancellation/check-in/payment behavior, particularly balance and identity/ownership checks. Live database state is NOT VERIFIED and must not be inferred from a migration file. This NX-00 review deliberately stops short of that task and did not start NX-01 or NX-02.

If origin/main advances, NX-01 should record a new baseline and revalidate affected evidence; this audit is pinned to the SHA above and was not rebased or merged. Existing uncertainty is a decision input, not permission to fill gaps with new APIs/tables.

## Information later needed by FE owners

These are handoff needs from the planning lanes, not new assignments or claims of approval.

| Planning owner / lane | Information needed before new implementation | Existing context and constraints |
| --- | --- | --- |
| Truc - Member training (NX-05 and later member UI) | Approved own-plan/history/result/comment visibility; fields, dates, empty/loading/error/forbidden states; publication/correction rules; agreed contracts and access matrix | Booking history is not training history. Member-direct recommender remains F5-CAND-02/NX-24 gated. Preserve approved Home and existing Member Portal patterns. |
| Hung - Coach/Receptionist (NX-06 and later UI) | Plan individual/class scope; current/previous Coach ownership; attendance statuses/corrections/confirmation; result metric/feedback forms; Receptionist arrival versus self-training integration | Reuse existing Coach schedule/roster and staff CheckInPage where approved. Self-training ACK must not be displayed as durable attendance. No duplicate session debit. |
| Phat - shared UI, notices/homework review and later assistant | D-04 scope/channels/triggers; approved cross-role event/recipient rules; Flow 6 knowledge/privacy/retention, support handoff and uncertainty/outage states; agreed contracts | Notifications/support schema is not an implemented delivery/chat service. Existing Home AI availability notice is not proof that chat works. Flow 6 can be deferred independently of Flow 5. |
| Dat - planning/backend coordinator | Signed boundaries, authoritative fields/states/owners, existing-module reuse, approved optional Go decisions and API agreement with the affected FE owners | Repository CODEOWNER review remains required; coordinator identity alone does not approve business policy or grant RBAC. |

Do not infer endpoint URLs from screen documents or start API calls against unapproved contracts. Do not add medical metrics, provider choices, cost/retention values or manager access because they seem convenient. Existing styling, authentication behavior, required-flow rules and unrelated UI work remain untouched.

## Review outcome requested

The registry is ready to review as a source-backed boundary and decision queue. A fully implementable specification still requires human decisions, NX-01 evidence, approved NX-02 use cases, and later signed contracts. Accepting this documentation package does not itself release any optional flow or authorize code/database changes.
