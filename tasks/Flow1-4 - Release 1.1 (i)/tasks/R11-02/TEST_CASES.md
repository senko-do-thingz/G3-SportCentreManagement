# R11-02 - Test cases and acceptance evidence

Task: Manager operations report desktop
Scope: minimum desktop Release 1.1, with actual backend permission boundaries. No mobile, Flow 5/6 or AI Audit Logs.

| ID | Test scenario | Expected result | Evidence |
|---|---|---|---|
| R11-02-TC-01 | Two date ranges produce independently verified report responses. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-02-TC-02 | Current active subset and historical registration wording do not confuse counts. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-02-TC-03 | upcoming/completed not summed into total. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-02-TC-04 | Empty interval shows zeros correctly. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-02-TC-05 | Invalid range, backend 400, backend 500, rapid date switch and cancellation handled. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-02-TC-06 | 403 direct API for Member/Coach/Receptionist. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-02-TC-07 | Revenue filter and export regression remain green. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |

## Universal negative controls

- Direct API authorization is evaluated by backend status, not merely frontend hiding.
- Wrong role, disabled account, missing token and account-switch leakage are tested when relevant.
- Invalid IDs, absent data, overlapping requests and stale responses must not produce false success.
- No test uses or mutates shared/production databases. Report exact owned fixture and teardown policy.
- If any case is outside the approved requirement, mark NOT_APPLICABLE with rationale; do not silently skip.

## Required report fields

Record case IDs, execution method, base/HEAD SHA, environment, real counts, pass/fail/skip, links to exact GitHub workflows, SQL-backed evidence where applicable, and unresolved issue references.
