# CODEOWNER Release 1.1 merge and demonstration gates

Latest-main follow-up: main advanced to c9c50da94423f8540de328855f9a7efd0878b1f1 and was incorporated as cb4565d21dadada24ce15082d57d706121cce5ad. Round-002 records the fresh frontend/desktop results and final submitted-head CI after that integration. Historical round-001 results remain bound to their recorded SHAs. Owner-only review/post-merge gates remain unchecked.

A source review or a green workflow alone does not authorize a merge/deployment.

Candidate update: R11-00 accepted; source CI 41988848995 passed for head 9184e9132946b78d85d958de9800da03433b2fad and integration merge 8d62a3aec5c9affcce695b2753dca23593e36973. Local full verification passed 2268 unit, 1040 native SQL and 1942 frontend cases with zero failures/skips; affected final fixes were rerun. The final desktop SQL/browser rerun passed 35 checks and restart readback. R11-11/reports/round-001 contains the complete packet. Gate B/D review and post-merge items remain CODEOWNER checks; final submitted-head checks are attached to PR #217.

## Gate A - provenance and scope

- [x] R11-00 records actual source SHA, original Topic 4 requirements file, exact Figma assets and current implementation gaps.
- [x] No duplicate feature already on `main` or owned by active teammate PR.
- [x] Desktop-only scope and deferred T6/7/8 decisions remain intact.
- [x] No Flow 5/6, AI Audit Log, LLM, mobile or payment expansion.

## Gate B - each PR

- [ ] PR diff is task-scoped, reviewed for authorization, API contract and semantic regressions.
- [ ] Source branch based on current main; conflicts resolved without losing teammate changes.
- [ ] Frontend lint/type/test/build all pass at current HEAD when frontend is changed.
- [ ] Backend unit/SQL integration and Flyway guards pass at current HEAD when backend changes.
- [ ] Report includes negative cases and real SQL data evidence where required.
- [ ] CODEOWNER explicitly approves and manually squash-merges each eligible PR.

## Gate C - functionality

- [x] F1-16 Receptionist support create/detail/filter/update and negative permissions work.
- [x] Operations report from/to, live counters, booking definitions and revenue remain authoritative.
- [x] Flow 2 booking, reschedule Session ID and credit invariants preserved.
- [x] Manager attendance overview + detail work if approved for this release.
- [x] Coach progress for owned class member and cross-coach denial work if approved.
- [x] Four role routes render real pages, no relevant placeholder or stale menu.

## Gate D - final integrated main

- [ ] Capture final `main` commit SHA after all merges.
- [ ] Required GitHub CI gate passes on that SHA. Explicitly distinguish a PR head test from merged main.
- [ ] Run 10-15 minute four-role desktop rehearsal with a disposable, known fixture, capture actual outcomes and data counts.
- [ ] README/TODO/CHANGELOG and requirement matrix match the final SHA.
- [ ] No secrets/PII/raw dumps in committed evidence.
- [ ] CODEOWNER signs GO_FOR_DESKTOP_DEMO, GO_WITH_KNOWN_GAPS, or NO_GO; deployment is a different decision.

## Known acceptable non-blockers under user's scope

- No waitlist, dynamic RBAC, mobile UI/tests, AI Audit Log or live AI integration.
- Frontend appearance is sufficient if coherent with current desktop design tokens; avoid cosmetic refactor for its own sake.
- No new production finance/provider functionality. Required report and support interactions must work.

## Not acceptable

- Missing authorization on new endpoints, leakage across Coach/Member or fake data marketed as SQL-persisted.
- New migration/version conflicts, breaking booking/payment behavior, silent failed tests, or direct writes to protected database.
- Claiming 100 percent based solely on UI routing or CI compile status.
