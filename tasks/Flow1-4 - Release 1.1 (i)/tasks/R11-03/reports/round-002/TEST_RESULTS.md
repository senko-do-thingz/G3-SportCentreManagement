# R11-03 round-002 test results

- Round: round-002, follow-up to round-001 after main advanced during final verification.
- Latest integrated main: c9c50da94423f8540de328855f9a7efd0878b1f1 (PR #212).
- Verified combined application snapshot: cb4565d21dadada24ce15082d57d706121cce5ad.
- Branch: feat/release-1-1-desktop.
- Consolidated PR: https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/217.
- Status at report authoring: local verification PASS; final submitted-head CI verification required before handoff.

Main PR #212 was merged into the isolated feature branch with no conflict. Its five Reception payment workspace files were preserved. No PR was merged by the agent. Existing unrelated original-workspace edit docs/nx-04/source-inventory.json and accepted R11-00 reports remain untouched.

## Verification

| Check | Source and actual result |
|---|---|
| Full local frontend | cb4565d21dadada24ce15082d57d706121cce5ad: 1943/1943 tests, 112 files; zero failures/skips; exit 0 |
| Local lint / TypeScript / build | Same application snapshot; all exit 0; frontend-exits.json under R11-11 round-002 |
| Four-role Edge 1440x900 | Same clean source snapshot; 35/35 PASS; real API and owned SQL; browser/ under R11-10 round-002 |
| Owned backend restart | PASS; Coach result and CLOSED support request retained |
| Backend source equivalence | No backend/src or POM change since fully verified 9184e9132946b78d85d958de9800da03433b2fad |
| Previous full local native SQL | 1040 tests, zero failures/errors/skips at d17c785b6d6871dd766c55c9ff77e5d0c8893557; final affected cases rerun and full updated-source CI passed |
| Prior full CI | Head 8536d83ca17597b3ab565207bfc4e7d9bacc0122 and merge 1dc6e8878ca963da72a462fdcc92357f75c4d080: 2268 unit, 1040 SQL and 1942 frontend; zero failures/errors/skips |
| Combined-candidate CI | https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/runs/41997764307; final result must be read from exact-SHA workflow metadata, not inferred from prior runs |

The increase from 1942 to 1943 frontend cases is the payment workspace test inherited from main PR #212. No test was removed or skipped. The final submitted report commit changes documentation/evidence only; its required PR checks and generated submitted-head-evidence.json must be verified before final handoff. At report-authoring time, the combined-source frontend/unit/security/migration/Compose jobs are green and SQL integration is still running. A pending job is not a pass.

Commands from frontend/: npm run lint; npx tsc -b; npm test -- --reporter=default --reporter=json --outputFile=<round-002/frontend-tests.json>; npm run build. All four exit codes were captured separately. From repository root: python frontend/scripts/db-unify-check.py --database <new owned timestamped name> --out <round-002/browser> --jar <previously verified unchanged backend JAR> --release11. The JAR SHA-256 is in browser-results.json. Credentials were supplied privately through environment variables and are not in reports.

Raw local logs/JSON remain in R11-11 round-002; committed browser summaries and nine screenshots are in R11-10 round-002. Final CI artifact XML and workflow/job metadata are saved with the submitted-head supplement after the last commit. No historical failed attempt was deleted or relabeled.
## Limits and remaining owner actions

The product limits from R11-11 round-001/KNOWN_GAPS.md remain unchanged: bounded attendance reads, existing support list contract, actual recorded progress only, fixed roles and deferred scope. There is no known unresolved release-blocking feature defect. CODEOWNER review/approval, merge, post-merge main verification and release/deployment decisions remain owner actions. The agent performs none of those actions.

Desktop only. No mobile, AI Audit Log, Flow 5-6 implementation, Waitlist, dynamic RBAC, legacy removal, financial calculation or migration change. Docker remains unavailable locally; native SQL and exact-candidate CI evidence are distinguished. No protected/shared application database was accessed or reset. Only new owned fixture sportify_unify_test_20261105155228_r11main was created for this follow-up. No existing process was stopped; only harness-owned backend/frontend children were shut down.
