## Description
Partial Flow 4 cross-role QA for NX-18, round 2 after the CODEOWNER review of this PR. **NX-18 is not complete.**

Review items addressed:
- `flow4-qa.mjs` now exits 1 when a required check fails or a capture is missing, 0 only when all required checks pass. The rules are in `evaluate.mjs`; the BLOCKED list is printed on every run.
- Negative control: 17 unit tests (`evaluate.test.mjs`) and a browser run (`run-negative-controls.mjs`) where the clean run exits 0 and each of 8 injected defects exits 1.
- Integrated checks rerun after #175 (merged) and #168 (merged into a scratch tree). Result: FAIL, 16 axe failures, one cause (NX18-D4: header Menu button name on 390 and 768 px). No overflow, console, focus or state failures.
- Unavailable backend, NX-11 to NX-13 and cross-role workflows stay BLOCKED (report section 5).
- The AI audit log is no longer tracked (the .gitignore keeps audit logs local only). It remains in earlier commits of this branch.
- Updated evidence: `docs/nx-18/evidence/integrated-rerun` and `negative-control`.

Fixes kept or added in this branch:
- Check-in overflow at 390px (`minmax(0, 1fr)`) and keyboard-reachable table region, with a regression test.
- NX18-D3: PR #175 changed the logo label but axe still failed (the logo letter was visible text inside an aria-hidden span, and the two words had no space). The letter is now drawn by CSS and a space separates the words; `Brand.test.tsx` fails without it.

Open: NX18-D4 (shared header Menu button) is not fixed here because the label is looked up by name in tests of PR #185; owner decision needed (report section 4).

## Scope
- Task ID: NX-18 (Flow 4, F4-REQ-01..07, owner Truc with Hung and Phat).
- Report: `docs/nx-18/NX-18_flow4_cross_role_QA_report.md`. Tools: `docs/nx-18/qa-tools/`.

## Pre-Merge Checklist
- [ ] Tests executed and passed in CI (include GitHub Actions run link below)
- [x] Database migrations reviewed, non-destructive, and backwards-compatible (none)
- [x] AI Audit Log kept locally as the repository rules require (not tracked); test specifications updated (report, unit and negative-control tests)
- [x] No forbidden symbols (em-dashes, emoji, special symbols) present in added lines (ASCII checked)
- [x] 100% English code, comments, documentation, and commit messages
- [x] Breaking changes identified and documented (none)

## CI Verification Evidence
- **Run URL**: TO BE FILLED after the PR is updated (CI runs on pull requests)
- **Total Tests Run**: local full run 94 files, 1756 tests passed; `node --test` 17 passed; lint and tsc pass
- **Failures / Errors / Skipped**: 0 / 0 / 0 for the project suite. The QA runner itself reports FAIL (16, NX18-D4); that is a finding, not a test failure of the project

## Related Issue / Ticket
- **Task ID**: NX-18
