# Sportify Member Profile, Wallet, Withdrawals, and Receptionist Inbox - Claude/Truc Assignment

Owner: CODEOWNER (Dat). Implementation owner: Truc with Claude.
Repository: `senko-do-thingz/G3-SportCentreManagement`.
Assignment date: 2026-11-08. Target: committee demonstration on desktop/localhost, not public production launch.

## Read order

1. `00_MASTER_PROMPT_CLAUDE.md` - paste this to Claude; it is the *single* master run instruction.
2. `01_BUSINESS_DECISIONS_AND_RULES.md` - binding owner decisions, integrity boundaries and assumptions.
3. `02_EXECUTION_AND_PR_PLAN.md` - 8 tasks, 8 PRs, stacked dependency model.
4. `03_DESKTOP_EVIDENCE_AND_REVIEW.md` - required screenshots, tests, PR evidence, combined review.
5. `tasks/MW-01.md` through `tasks/MW-08.md` - one task per PR, in specified order.
6. `04_FINAL_HANDOFF_TEMPLATE.md` - end-of-run consolidated review.
7. `05_POWERSHELL_SETUP.md` - local extraction/copy/start examples.
8. `06_GROUP_ANNOUNCEMENT_VI.md` - summary to send teammates.

## Non-negotiable

- Work from the *latest `origin/main`*; record exact SHA and changed upstream at start, and reconcile once more before final review. Never silently use an archived ZIP or stale checkout.
- **One implementation run + one comprehensive review phase**, not one prompt/review conversation for each task. Implementation still requires validation and PR creation for each task.
- 8 small focused PRs (1 task == 1 PR), each with actual desktop UI screenshot evidence attached or committed and linked in PR description. No synthetic/AI-generated screenshots as evidence.
- CODEOWNER alone decides approve and merge. Claude/Truc MUST NOT approve, merge, enable auto-merge, delete branches, or bypass a required review.
- Source code, source comments, migrations, technical artifacts, commit messages and PR descriptions are in **English**. `06_GROUP_ANNOUNCEMENT_VI.md` is a Vietnamese communication artifact only.
- One SQL Server DB: `sportify`. No separate wallet DB, no alternate active datasource. Never run database clean, reset, destructive data migration, unapproved Flyway application, or real-money operations.
- Desktop only. Do not do mobile redesign. No AI Audit Log review is required. Only document what was actually tested.
- External banking integrations, auto bank transfer, webhook, real-money e-wallet services, SMTP and native push are out of scope.

## Notes from repository inspection (NOT a substitute for current main)

Snapshot from the GitHub code search on 2026-11-08: `e3e9a10bf981a4109c75e51d07c341b3a99e94d5`. Re-fetch, do not pin work to this SHA.
- `frontend/src/pages/member/MyProfilePage.tsx`: editable name/DOB/phone/address/goals/level + change password already exists.
- `frontend/src/api/memberApi.ts`: `PUT /users/me`, change password request already exists.
- `backend/src/main/java/com/sportify/identity/entity/MemberProfile.java`: no dedicated health questionnaire in inspected model.
- `backend/src/main/java/com/sportify/catalog/service/RefundService.java`: existing package-refund workflow; do not replace with wallet withdrawal.
- `docs/training/operational-notifications.md`: Member notification system; reuse patterns for Receptionist inbox.
- `docs/database/03-classes-and-booking.md`: booking consumes package session credits, capacity guarded; never automatically add monetary refund for a failed booking of already-paid *package*.
- Current wallet payment labels might exist as disabled placeholders. They do not prove wallet balance/top-up/withdrawal exists.

If source or approved requirements differ, inventory exact discrepancies, preserve stronger existing protections, note scope decisions in consolidated handoff and ask CODEOWNER only for genuine conflicting requirements that cannot be safely implemented.
