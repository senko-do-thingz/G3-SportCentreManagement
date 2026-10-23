# Git baseline and preservation evidence

Repository: `D:\G3-SportCentreManagement\SWP`. Verified origin fetch/push URL: `https://github.com/senko-do-thingz/G3-SportCentreManagement.git`.

Original branch: `feat/fe-home-guest-auth-flow-r3`. Original HEAD: `9deb4fc2a4473db03e9ae334705db8cc14457874`. It was not switched, reset, cleaned, stashed, pulled or rebased. Its tracked and staged diffs were empty initially and remain empty. Initial and final nonblank short branch/status lines match exactly; the initial PowerShell transcript contains extra blank separators.

Initial origin/main ref: `9c54b9f7ebfaa2228ddfe644dc389add7f732657`.

`git -C "D:\G3-SportCentreManagement\SWP" fetch origin main` succeeded with native exit code **0**, started 2026-10-23T16:20:38+07:00, ended 16:20:40+07:00. Fresh fetched baseline: **`c00a3c1a29677a4f069000fa6ff38fe3c970e492`**. Fetch progress on native stderr was wrapped as a PowerShell NativeCommandError record in fetch.txt; this is not a failed Git exit. The separately captured exit status and ref advancement establish success.

`git -C "D:\G3-SportCentreManagement\SWP" worktree add -b docs/nx-00-topic4-audit-r1 "D:\G3-SportCentreManagement\SWP-NX00-audit-round-1" origin/main` succeeded. The unique branch and absent directory were checked before creation. Worktree HEAD was verified equal to fetched origin/main before source review. No conflict or unsafe checkout operation occurred.

Worktree: `D:\G3-SportCentreManagement\SWP-NX00-audit-round-1`. Task branch: `docs/nx-00-topic4-audit-r1`. All project-source review used this worktree or that exact Git baseline. Workbook/prompt inputs and round output remain at the original user-requested paths. NX-00 allocated round 1 once; no previous NX-00 numeric rounds existed. FE report rounds are unrelated and were preserved. No round was reused during this execution.

## Final read-only results

`git ls-remote origin refs/heads/main` still returned `c00a3c1a29677a4f069000fa6ff38fe3c970e492`. The shared local origin/main ref also matches. No main advancement was observed at final inspection; no merge or rebase occurred. This statement is time-bounded to that inspection.

Audit HEAD remains `c00a3c1a29677a4f069000fa6ff38fe3c970e492` and tracked/index diffs are empty. `git status --short --branch` shows only the branch header. `git diff --check` and `git diff --cached --check` returned 0. No unmerged entries or ordinary untracked files exist in the isolated worktree.

The only ignored untracked file there is the intended `docs/audit/NX-00_Topic4_Scope_Audit_R1_AI_Audit_Log.md`. `git check-ignore -v` reports shared `.git/info/exclude:11:docs/audit/`. The log is intentionally invisible to normal status/diff; it exists on disk and matches report 08 byte for byte. No ignore rule, index or tracked audit history was manipulated. `evidence/audit-ignored-files.txt` records the separate ignored-file inventory.

| Final check | Native exit | Output |
| --- | --- | --- |
| originalRoot | 0 | Recorded in evidence/final-state.json |
| originalHead | 0 | Recorded in evidence/final-state.json |
| originalBranch | 0 | Recorded in evidence/final-state.json |
| originalStatus | 0 | Recorded in evidence/final-state.json |
| originalAllUntracked | 0 | Recorded in evidence/final-state.json |
| originalTrackedDiff | 0 | Empty |
| originalStagedDiff | 0 | Empty |
| originalConflicts | 0 | Empty |
| mainRef | 0 | Recorded in evidence/final-state.json |
| remoteMain | 0 | Recorded in evidence/final-state.json |
| auditHead | 0 | Recorded in evidence/final-state.json |
| auditBranch | 0 | Recorded in evidence/final-state.json |
| auditStatus | 0 | Recorded in evidence/final-state.json |
| auditTrackedDiff | 0 | Empty |
| auditStagedDiff | 0 | Empty |
| auditDiffCheck | 0 | Empty |
| auditStagedDiffCheck | 0 | Empty |
| auditAllUntracked | 0 | Empty |
| auditConflicts | 0 | Empty |
| auditIgnoreRule | 0 | Recorded in evidence/final-state.json |
| worktrees | 0 | Recorded in evidence/final-state.json |

Exact commands/output are in `evidence/final-state.json`; initial checks in `initial-git-state.txt`; fetch in `fetch.txt` and `fetch-result.json`; worktree creation in `worktree-create.txt`, all under evidence/. These files were normalized only for ASCII text encoding within this newly allocated round; source inputs were not altered.

## Original checkout local changes: complete final list

Short status, unchanged from start:

```text
## feat/fe-home-guest-auth-flow-r3...origin/feat/fe-home-guest-auth-flow-r3
?? "and secure password changes\357\200\242"
?? "earch and filtering\357\200\242"
?? report/
```

Complete normal untracked file list from `git ls-files --others --exclude-standard` (49 paths; Git quotes non-ASCII filenames using escapes):

```text
"and secure password changes\357\200\242"
"earch and filtering\357\200\242"
report/sportify_home_browser.xlsx
report/sportify_home_build.xlsx
report/sportify_home_focused.xlsx
report/sportify_home_full.xlsx
report/sportify_home_lint.xlsx
report/sportify_home_r2_baseline.xlsx
report/sportify_home_r2_browser.xlsx
report/sportify_home_r2_build.xlsx
report/sportify_home_r2_focused.xlsx
report/sportify_home_r2_full.xlsx
report/sportify_home_r2_lint.xlsx
report/swp_pr103_artifact_seal.xlsx
report/swp_pr103_auth_diagnostic_utc.xlsx
report/swp_pr103_build_utc.xlsx
report/swp_pr103_evidence_check.xlsx
report/swp_pr103_frontend_tests_utc.xlsx
report/swp_pr103_frontend_tests_vietnam.xlsx
report/swp_pr103_lint_utc.xlsx
report/swp_pr103_reproduction_utc.xlsx
report/swp_pr103_reproduction_vietnam.xlsx
report/swp_pr103_targeted_utc.xlsx
report/swp_pr103_targeted_vietnam.xlsx
report/swp_pr103_typecheck_utc.xlsx
report/swp_pr103_whitespace_utc.xlsx
report/swp_round2_browser.xlsx
report/swp_round2_build.xlsx
report/swp_round2_contract_build.xlsx
report/swp_round2_contract_evidence_check.xlsx
report/swp_round2_contract_frontend_tests.xlsx
report/swp_round2_contract_lint.xlsx
report/swp_round2_contract_targeted.xlsx
report/swp_round2_contract_typecheck.xlsx
report/swp_round2_contract_whitespace.xlsx
report/swp_round2_conventions.xlsx
report/swp_round2_cross_module.xlsx
report/swp_round2_evidence_check.xlsx
report/swp_round2_frontend_tests.xlsx
report/swp_round2_guard_tests.xlsx
report/swp_round2_lint.xlsx
report/swp_round2_maven.xlsx
report/swp_round2_migrations.xlsx
report/swp_round2_recovery_browser.xlsx
report/swp_round2_reproduction.xlsx
report/swp_round2_targeted.xlsx
report/swp_round2_typecheck.xlsx
report/swp_round2_unit_report_guard.xlsx
report/swp_round2_whitespace.xlsx
```

The two unrelated root files have exactly the initial SHA-256 values, rechecked in validation-results.json. Their filenames are JSON-escaped to retain exact identity without non-ASCII text. Root report/ paths are preserved; they were not edited by this task. Only the new NX-00 round was authored under the requested report root; ignored historical task files are not falsely described as absent because Git omits them. The audit log is the only newly authored file inside the separate worktree.

No stage, commit, push, PR, merge, deploy, delete, production/test/schema/seed/database modification or dependency installation occurred. Source files match their reviewed hashes and tracked trees are unchanged. Git metadata changed only through the authorized fetch and safe branch/worktree creation. This is an activity/evidence account, not a claim that Git status alone proves the entire prior history.
