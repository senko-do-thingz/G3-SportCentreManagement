# BE-03 manager catalog and safe membership upgrade planning

Publication follow-up: commit and push have subsequently been authorized. `docs/reviews/PR40_publication_status.md` records the resulting publication state. The earlier local-only statements below describe the preparation snapshot and do not replace exact final-head CI evidence.

## Description

Implements manager listing and edits for sport packages and card tiers, preserving purchase snapshots and recording before/after activity logs. Package updates validate durations, counts and 60/90 minute sessions. Tier updates protect STANDARD and serialize edits in deterministic tier order to enforce active-tier price monotonicity.

This local remediation aligns card planning across the backend and member/reception screens. MemberCardResponse now maps tierRank, upgradeRequest and replacesCardId through the existing authorized history endpoints. Frontend upgrades use rank and historical pricePaid from explicit replacement links. Unrelated REPLACED cards cannot affect credit. Missing, orphaned, cyclic, excessive or inconsistent chains produce an unavailable plan without an invented fee, and UI consumers prevent unsafe upgrade submission. Member zero-fee upgrades direct the member to the desk; staff can perform a valid zero-fee upgrade. Purchase and activation remain authoritative when previews become stale.

V22 now requires matching same-member predecessor evidence before marking discounted legacy rows as upgrades. No-predecessor promotions and historical-price purchases remain ordinary purchases. Ambiguity still fails closed. CARD-MIG-NOPRED and executable TC-BE03-70 migration coverage were updated. TC-BE03-71 verifies that a request made while a tier was active can be activated after deactivation at its original requested price. TC-BE03-72 verifies the rank, historical-credit and zero-fee model rules.

Surefire and Failsafe use supported provider properties to disable Vintage auto-addition in 3.6.0, preserving Jupiter integration-tag filtering. Shared CI validates report existence, actual counts, failures/errors/skips, tag isolation and critical suites from `scripts/ci/required-integration-tests.txt`. Constraint errors map to upgrade concurrency only when the intended unique index/constraint is identified; unrelated integrity errors are preserved.

## Local evidence

- Backend unit: 620 tests, 0 failures, 0 errors, 0 skipped; Maven exit 0 and report guards exit 0.
- Frontend: 419 passed, 0 failed/skipped; exit 0. Lint and build exit 0.
- Shared report guard: 15 tests passed; exit 0.
- Failsafe: 122 discovered, 0 executed, 122 skipped because Docker is unavailable. Maven exit 0; both integration guards exit 1. SQL Server integration validation is Blocked.
- Full execution details: `docs/tests/PR40_remediation_test_cases.md` and `docs/reviews/PR40_remediation_completion_report.md`.

These results are from working files based on head `22b762f0c2594580b8243e63c60b8974e8fbd003`, not a new pushed commit. No exact final-source GitHub Actions run exists yet. No historical run or Actions index is presented as final-source CI evidence.

## Pre-Merge Checklist

- [ ] Tests executed and passed in CI on the resulting final head, with exact run URL and artifact totals.
- [ ] Revised SQL Server migration and concurrency suite passed, and staging dry-run/application-history evidence reviewed.
- [ ] Audit tracking policy resolved and intended audit documents available to reviewers.
- [x] Local AI Audit Log and relevant test specifications updated.
- [x] New code, comments and documentation use English and ASCII punctuation.
- [x] Frontend contract change implemented and described.
- [ ] Reviewer governance verified and eligible approval obtained where required.
- [ ] Explicit maintainer acceptance of the remaining unbounded tier-lock wait recorded.
- [ ] Ready-for-review state changed with authorization after readiness requirements are met.

## CI Verification Evidence

Run URL: Pending authorized publication and exact final-head run. Total tests and failures/errors/skipped: Pending final-head CI artifacts. The local results above do not establish remote CI readiness.

## Audit and review artifacts

- `docs/audit/PR40_Remediation_AI_Audit_Log.docx` and `.md`.
- `docs/audit/BE-03_AI_Audit_Log.md` retains prior history and adds a remediation entry; historical DOCX is preserved.
- `docs/reviews/PR40_remediation_modified_files_paths.md` contains every task file and tracking state.
- `docs/tests/BE-03_test_cases.md` and `docs/tests/PR40_remediation_test_cases.md` describe updated behavior and actual status.

Audit documents are ignored and unstaged locally; no force-add or publication has been performed. The normal DOCX renderer is unavailable; all pages have a fallback preview, with Office pagination verification remaining a limitation.

## Known limitations and decisions

Public PR remains open and Draft. Branch protection returned HTTP 401; required owner approvals and bypass rules are unverified. CODEOWNERS was preserved. V22 is new relative to main, but persistent deployment history is unknown and must be checked before deployment. No production-data absence or dry-run counts are claimed.

The existing whole-tier pessimistic lock retains deterministic concurrency behavior and an unbounded wait while holding a connection. Maintainer acceptance is pending. Product decisions on proration, refunds, free online upgrades and future optimistic locking remain open. Suggested squash message: `feat(be-03): add manager catalog and safe membership upgrade planning`. No merge is authorized or performed.

Task ID: BE-03. This replacement body reconciles the preserved local BE-03 description and the supplied ZIP proposal; it is prepared locally and has not been applied to PR40.
