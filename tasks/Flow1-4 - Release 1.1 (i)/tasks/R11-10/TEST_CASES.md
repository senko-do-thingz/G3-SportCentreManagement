# R11-10 - Test cases and acceptance evidence

Task: Requirements traceability and four-role committee demo
Scope: minimum desktop Release 1.1, with actual backend permission boundaries. No mobile, Flow 5/6 or AI Audit Logs.

| ID | Test scenario | Expected result | Evidence |
|---|---|---|---|
| R11-10-TC-01 | Walkthrough Manager, Receptionist, Member, Coach with separate tokens. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-10-TC-02 | Create/read support, filter/report, class/Coach progress/attendance. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-10-TC-03 | Negative role cross-access and private Member checks. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-10-TC-04 | Compare selected report counts to real API and SQL. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-10-TC-05 | Reload/restart readback for persisted records, no double-debit. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |

## Universal negative controls

- Direct API authorization is evaluated by backend status, not merely frontend hiding.
- Wrong role, disabled account, missing token and account-switch leakage are tested when relevant.
- Invalid IDs, absent data, overlapping requests and stale responses must not produce false success.
- No test uses or mutates shared/production databases. Report exact owned fixture and teardown policy.
- If any case is outside the approved requirement, mark NOT_APPLICABLE with rationale; do not silently skip.

## Required report fields

Record case IDs, execution method, base/HEAD SHA, environment, real counts, pass/fail/skip, links to exact GitHub workflows, SQL-backed evidence where applicable, and unresolved issue references.
