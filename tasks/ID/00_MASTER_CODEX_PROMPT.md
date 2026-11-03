> Policy supersession, 2026-11-03: The CODEOWNER decision in PR #203 comment
> https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/203#issuecomment-6606819198
> authorizes eligible future session rescheduling with date-based public ID
> regeneration, durable aliases and unchanged numeric relationships/bookings/credits.
> It replaces the original permanent-date and cancel/replacement restrictions below.
> See docs/database/11-session-identity.md for the current implemented contract.

# MASTER CODEX EXECUTION - ID

You are the implementation Agent for the post-merge Sportify Center session identity and attendance UX refactor. Execute task folders 01 through 07 in order, without requiring interim CODEOWNER review, and submit one consolidated handoff at completion. Review your own changes for bugs and security between phases.

## Baseline and isolation
Repository: senko-do-thingz/G3-SportCentreManagement
Verified merged PR: #194, merge commit 9a438acbb8fa274d50c93fdb97659fde7b5d59b3
Task folder: D:\G3-SportCentreManagement\SWP-HOTFIX\tasks\ID
Implementation worktree: D:\G3-SportCentreManagement\SWP-ID-EXEC
Feature branch: feat/ID-session-identity-attendance-ui

1. Protect uncommitted changes in every existing worktree. Never run git clean, reset --hard, checkout --force or stash in another Agent's worktree.
2. Fetch origin/main before coding. Ensure your feature branch is based on the most recent main; if main has advanced, merge/rebase only your isolated feature branch safely with evidence. Do not change main.
3. Task prompts are in 01 through 07. Read all prompts first and complete them in sequence.
4. Existing numeric class_session.id remains an internal surrogate PK. Public Session ID means the stable 10-character code ddMMyy + approved two-letter sport code + NN, e.g. 031126FB01. Make it canonical in UI, copy/search, user-visible labels and new API lookups. Preserve safe compatibility for legacy numeric endpoints and foreign-key relationships. Do not implement a frontend-only alias.
5. Change only code and migrations necessary for these tasks. Do not change package credit, attendance date/correction, Manager backfill default, result permissions, or financial invariants.
6. Migration safety: new forward-only catalog migration version after inspecting current migrations on origin/main, #185 and #193. Never edit installed V37 or previous files. Never run Flyway repair, clean, baseline, or manipulate schema history. Test on an isolated SQL Server schema/database compatible with this branch. The QR database has a different applied V37 and must not be targeted.
7. Implement a real feature, not only a specification. If a detail is undecidable, choose the least disruptive behavior and record the exact rationale in a decision log. Do not invent CODEOWNER approval for changing booked session dates or migrating bookings.
8. Verify source and tests locally using PowerShell/Java 25/npm, no local Docker assumption. Trigger or obtain full GitHub CI on the exact feature HEAD, and explicitly distinguish unavailable local SQL test conditions from authoritative CI results.
9. For each task, write to task-root\NN\reports\round-01 (or the next unused round-NN). Include CHANGED_FILES.md, TEST_CASES.md, IMPLEMENTATION_REPORT.md, and AI_AUDIT_LOG.docx. Preserve all prior rounds. Audit logs are factual, English, and include tools used, change inventory and test evidence.
10. Produce task-root\07\reports\round-XX\FINAL_CODEOWNER_HANDOFF.md plus release evidence index and PR comparison. Request a single comprehensive CODEOWNER review after ALL phases have completed. If CI fails, fix it in additional report rounds before handoff.
11. Commit and push ONLY to feat/ID-session-identity-attendance-ui. Open a PR to main when possible, but NEVER approve or merge your own PR. The CODEOWNER alone decides merge.
12. Preserve PR #185/#193 scope separation. Do not add demo-coach-photo migrations. AI features remain deferred.
13. Code, tests, comments and technical reports must be 100 percent English with ASCII punctuation only. Use hyphens and ->, not em dashes or Unicode arrows. No emoji.
14. No vague assertions like "all tests pass" without exact commands, test counts, exit status, SHA and supporting artifact paths. Never invent screenshots or database executions.

## Mandatory cross-task acceptance
- Six approved sport codes: FB, SW, BM, VB, BK, TE.
- Stable unique canonical Session ID, allocation safe under concurrency, cancellation non-reuse, deterministic backfill.
- All four roles show Session ID and where/with whom session happens, subject to authorization.
- Attendance session list highlights actionable sessions; Member roster replaces member dropdown.
- Compact attendance note and explicit audited correction reason; saved result restrictions preserved.
- Global frontend text date dd/MM/yyyy, 24-hour HH:mm, center timezone Asia/Ho_Chi_Minh; ISO on API and native controls as needed.
- Desktop/light/dark/mobile screenshots, accessibility, E2E and regression evidence. All required CI green at exact HEAD.

Do not stop between task folders. Start implementation now and return ONE consolidated report after completion.