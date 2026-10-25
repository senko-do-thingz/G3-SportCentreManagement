# NX-02 security and privacy assessment

Internal local record. Round 3. Audited origin/main: `f8959749bd2e887d7038da2843faeede5e8e0ab1`.

Status: TECHNICAL_DOCUMENTATION_REMEDIATION_COMPLETE - FORMAL_SIGN_OFF_PENDING. BUSINESS_SIGN_OFF=PENDING; FORMAL_ACCEPTANCE=ON_HOLD. All deliverables require manual CODEOWNER review. New policy examples are PROPOSED and SPECIFIED_NOT_EXECUTED.

## Assets and trust boundaries

Personal plans, per-session attendance/results, assessments, restricted notes and their correction history are private training data. A shared class plan is a different audience from each member's outcomes. Authenticate through the current principal (S09/S10), then evaluate current resource and field entitlements. S05 fresh active-account lookup and assignment recheck are useful patterns; they do not already enforce future training privileges.

| Threat | Exposure path | Proposed control | Evidence and tests |
| --- | --- | --- | --- |
| IDOR | Change memberId/sessionId/classId/planId, filter or history cursor | Server derives subject; authorizes every resource, row, total and field | PM-17..23, TC-32..37, TC-50..52 |
| Former Coach access | Original author continues after reassignment | Separate historical reads/corrections from current writes, deny unspecified rights | S05, D-12, TC-08/36/38 |
| Role escalation | Client role/owner or broad Manager/Admin assumption | Use current trusted role; explicit rights only; Manager existing roster/arrival exceptions stay bounded | S02/S05/S09/S17, TC-30/31/39/52 |
| Draft/private-note disclosure | Shared plan response, hidden UI field, logs, exports or cache | Field/state projection before response; separate classified feedback; no peer metadata | D-02/D-12, TC-03/19/33/50 |
| Lost correction/audit | Stale forms, duplicate retries, audit save failure | Revision-aware accountable changes and explicit consistency policy | S12/S16, D-13, TC-13/24/25/53 |
| Ineligible participation | Withdrawn booking, missing roster or cancelled session cached at form load | Refresh and revalidate assignment/eligibility at submission | S05/S06/S07, TC-10/20/22/26/54 |
| Narrative overcollection | Real health/training details placed in examples/logs | Minimum approved fields, synthetic fixtures, metadata-only routine logs | D-03/D-12, TC-40 |
| Stale token/cache | Deactivated or reassigned user sees private history | Current account/role check and cache partition/revalidation | S09, TC-35/51 |
| Financial abuse | Attendance triggers extra debit/refund or bypasses cancellation | Keep plan/result/attendance financially neutral and preserve existing service rules | S07, TC-41/42/55 |

## Decisions requiring human privacy authority

Dat must name the product/privacy/legal decision owner for purpose, collection necessity, classified notes, legitimate staff access, retention, export/deletion/correction process and audit reason visibility. Options and tradeoffs are D-03/D-12 and the plan/result/progress report. No legal conclusion, consent mechanism or retention period is approved here. Business training observations must not silently expand into medical profiling or AI-provider inputs. AI-related data use stays deferred to NX-20/NX-27/D-14.

PROPOSED: deny unauthorized reads without existence leakage; do not print raw narratives, tokens or credentials; isolate cached data by principal and entitlement; log only minimum identifiers/outcomes and restrict audit access. Generic ActivityLog supports event persistence but does not prove tamper resistance or retention. Review creation/correction atomicity and access to old/current reason text before implementation. No system penetration, JWT runtime, live database or production-personal-data check was executed.

## Required policy dimensions in this round

Full decision approval = verifiable signed strategic selection AND every required subdecision approved or explicitly deferred by a signed scope exception. Strategic A/B/C recommendations are not adopted policy. See [approval completeness](NX-02-approval-completeness-matrix.md) for all 32 required dimensions, selected options UNRESOLVED, owners, evidence and downstream blockers. No duration, threshold, permission or signed exception is invented.
