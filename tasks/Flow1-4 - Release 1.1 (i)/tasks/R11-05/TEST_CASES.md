# R11-05 - Test cases and acceptance evidence

Task: Coach learner profile and Member progress F4-05
Scope: minimum desktop Release 1.1, with actual backend permission boundaries. No mobile, Flow 5/6 or AI Audit Logs.

| ID | Test scenario | Expected result | Evidence |
|---|---|---|---|
| R11-05-TC-01 | Valid Coach class/member profile and progress succeeds. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-05-TC-02 | Coach with wrong class/Member combination gets 403/404 per backend contract. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-05-TC-03 | Other roles and anonymous denied by backend. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-05-TC-04 | Empty, partial, multiple-session progress lists display accurate order/status. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-05-TC-05 | Rapid class/member/account switch does not display previous user data. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |
| R11-05-TC-06 | Existing Coach plan/result/feedback and Member own progress regression. | Verified against actual source contract; no unauthorized state change | Command/log/PR CI/browser or SQL as appropriate |

## Universal negative controls

- Direct API authorization is evaluated by backend status, not merely frontend hiding.
- Wrong role, disabled account, missing token and account-switch leakage are tested when relevant.
- Invalid IDs, absent data, overlapping requests and stale responses must not produce false success.
- No test uses or mutates shared/production databases. Report exact owned fixture and teardown policy.
- If any case is outside the approved requirement, mark NOT_APPLICABLE with rationale; do not silently skip.

## Required report fields

Record case IDs, execution method, base/HEAD SHA, environment, real counts, pass/fail/skip, links to exact GitHub workflows, SQL-backed evidence where applicable, and unresolved issue references.
