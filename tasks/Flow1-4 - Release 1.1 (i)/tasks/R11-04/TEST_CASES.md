# R11-04 - Test cases and acceptance evidence

Task: Manager training attendance overview F4-10
Scope: minimum desktop Release 1.1, with actual backend permission boundaries. No mobile, Flow 5/6 or AI Audit Logs.

| ID | Test scenario | Expected result | Evidence |
|---|---|---|---|
| R11-04-TC-01 | Manager GET overview with date filters and a known SQL fixture. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-04-TC-02 | No roster, fully marked roster, partially marked roster and cancelled booking distinctions. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-04-TC-03 | Coach filter, class filter, invalid range and pagination/maximum window guards. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-04-TC-04 | Manager GET detail for published/ended session with stable session identity. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-04-TC-05 | Unauthenticated/other roles return 401/403 as contract. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-04-TC-06 | Read-only overview makes no attendance, balance or audit mutation. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-04-TC-07 | Existing backfill regression. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |

## Universal negative controls

- Direct API authorization is evaluated by backend status, not merely frontend hiding.
- Wrong role, disabled account, missing token and account-switch leakage are tested when relevant.
- Invalid IDs, absent data, overlapping requests and stale responses must not produce false success.
- No test uses or mutates shared/production databases. Report exact owned fixture and teardown policy.
- If any case is outside the approved requirement, mark NOT_APPLICABLE with rationale; do not silently skip.

## Required report fields

Record case IDs, execution method, base/HEAD SHA, environment, real counts, pass/fail/skip, links to exact GitHub workflows, SQL-backed evidence where applicable, and unresolved issue references.
