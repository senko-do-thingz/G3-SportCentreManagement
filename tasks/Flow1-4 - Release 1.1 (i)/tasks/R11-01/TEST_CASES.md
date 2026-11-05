# R11-01 - Test cases and acceptance evidence

Task: Receptionist Support Requests desktop F1-16
Scope: minimum desktop Release 1.1, with actual backend permission boundaries. No mobile, Flow 5/6 or AI Audit Logs.

| ID | Test scenario | Expected result | Evidence |
|---|---|---|---|
| R11-01-TC-01 | Authorized GET list/detail and status filter match backend response. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-01-TC-02 | POST with valid UUID succeeds once; same UUID + same body stays idempotent; changed body rejected. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-01-TC-03 | Missing/invalid header or Member ID rejected by backend. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-01-TC-04 | OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED succeeds with notes; forbidden transitions rejected. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-01-TC-05 | Disabled, unknown or synthetic member handling follows backend contract. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-01-TC-06 | Coach, Member, anonymous cannot access Reception API or route; token switching cannot display stale tickets. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-01-TC-07 | Double-click and request/network error do not yield duplicate visible rows. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |

## Universal negative controls

- Direct API authorization is evaluated by backend status, not merely frontend hiding.
- Wrong role, disabled account, missing token and account-switch leakage are tested when relevant.
- Invalid IDs, absent data, overlapping requests and stale responses must not produce false success.
- No test uses or mutates shared/production databases. Report exact owned fixture and teardown policy.
- If any case is outside the approved requirement, mark NOT_APPLICABLE with rationale; do not silently skip.

## Required report fields

Record case IDs, execution method, base/HEAD SHA, environment, real counts, pass/fail/skip, links to exact GitHub workflows, SQL-backed evidence where applicable, and unresolved issue references.
