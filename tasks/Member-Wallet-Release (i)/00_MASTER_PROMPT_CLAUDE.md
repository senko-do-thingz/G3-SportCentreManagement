# MASTER PROMPT FOR CLAUDE - SPORTIFY MEMBER/WALLET RELEASE

You are Claude working locally with Truc, for CODEOWNER Dat, on `senko-do-thingz/G3-SportCentreManagement`. This prompt authorizes implementation and PR preparation, NOT approval, merging, deployment or uncontrolled migration. Execute **one continuous implementation run for all eight tasks, followed by one consolidated technical review**. Do not ask to be reprompted task-by-task. Do not pause between tasks simply to request a routine review. Continue as far as safely possible, report blockers with concrete evidence. Do not mask failures.

## 0. Load the assignment

Read *all* markdown instructions under `tasks/Member-Wallet-Release (i)/` (or this extracted `Sportify-Member-Wallet-Release/` bundle) before editing. Source of truth for scope: `README_FIRST.md`, `01_BUSINESS_DECISIONS_AND_RULES.md`, `02_EXECUTION_AND_PR_PLAN.md`, `03_DESKTOP_EVIDENCE_AND_REVIEW.md`, and the 8 MW files. Use original TOPIC 4 workbook if present to verify academic flow labels: F1-3 required, F4 optional. User decisions in this bundle explicitly authorize new demo behavior; document differences with older decisions.

## 1. Establish provenance and safe baseline

- Check `git status` and preserve all user/uncommitted work; NEVER discard/stash-without-documentation/force-reset/clean existing changes. If dirty, isolate safely in an approved new worktree or branch without losing user work.
- `git fetch origin --prune` (no branch deletion) and inspect `origin/main`, current PRs, code ownership, open conflicting edits, and project instructions. Record `git rev-parse origin/main`, timestamp, SHA, affected files and changed behavior. Do not assume a 2026-11-08 snapshot stays current.
- Compare current code for account profile, password, payment/QR/refund, booking transaction and capacity guards, financial reporting, notifications, and demo provenance. Produce a short baseline inventory under `tasks/Member-Wallet-Release (i)/reports/baseline.md`.
- Confirm `sportify` is sole configured application DB. Git changes to new Flyway scripts are permitted, but DO NOT apply to protected/shared data without explicit CODEOWNER authorization, restorable backup, and environment clearance. Avoid running the app with automatically applied pending migrations against a shared DB. Use mocks or an owner-approved safe test fixture; if no DB approval, explicitly mark SQL/live E2E BLOCKED, not PASS. No database `clean`, no deleting/re-writing applied migrations, no alternate project database.

## 2. Execute all eight tasks in order

Read and implement MW-01 to MW-08. Each task is **one isolated coherent PR**, named `fix(flow-x): ...` per `02_EXECUTION_AND_PR_PLAN.md`. Keep all UI desktop-only. Implement real endpoints, role checks, persisted changes and unit/integration tests; no fake button-only features.

Use stacked PRs to avoid merging while still supporting dependencies:
- MW-01 branch from latest `origin/main`, PR base `main`.
- MW-02 branch from MW-01, PR base MW-01 branch; repeat through MW-08 (unless some tasks can be safely independent without losing one-PR-per-task diff; if deviating, document a clear dependency graph).
- For each task: implement only its incremental diff, run applicable scoped checks, capture **actual running desktop browser screenshots** in the task evidence path, push the branch and open a PR with task ID, base/head/SHA, tests, screenshots, limitations, and dependency. No task ends without an attempted PR; if access/runtime blocks a PR or screenshots, state the reason and do not label it ready.
- Make no cross-task integration commit that silently broadens a PR. If latest-main changes require safe incorporation into the stack, document Git strategy and preserve ancestry. Never force-push over another developer's work without explicit authorization.
- Keep `fix(flow-x): ...` PR title style. Code/comments/tests/PR body English, ASCII punctuation (`-`, `->`). Do not put `TOPIC4` in PR title.
- Before starting each new task, re-check base branch and dependencies; do not claim independent mergeability of a stacked PR.

## 3. Business invariants that MUST NOT be violated

- Member identity and password: reuse existing `PUT /users/me` and password-change behavior; only expand missing portions. Account ACTIVE != active paid entitlement.
- Health questionnaire is voluntary and *non-diagnostic*. Prewritten questions, skip allowed, no invented medical clearance, protected health field access, consent/privacy, no raw sensitive answers in logs/PR screenshots.
- Wallet is internal demo stored in `sportify` with proper ledger and idempotency; simulated money must stay separate from genuine transactions, revenue and refunds; never convert simulated funds into real bank payout.
- Booking currently spends package session credits; an unsuccessful booking generally means *no monetary refund*. If a wallet charge specifically associated with a booking has been committed and its seat allocation failed, perform an exactly-once wallet compensation. If booking and debit are one atomic transaction, roll both back, no spurious refund entry. If booking uses an already-paid package, preserve package entitlement and no new wallet credit.
- QR top-up is manual staff confirmation after checking evidence (or an explicit SIMULATED demo confirmation, labelled). No automatic bank arrival inference from QR rendering.
- Withdrawal is a request from an eligible wallet balance; Receptionist approves/rejects; approval is NOT proof of bank transfer. Completion requires separate confirmed payout/simulated completion step. Reserve funds atomically, release on reject/failure, never allow more than available or double payout.
- Receptionist inbox contains actionable work items, not a generic email service. Read/unread state != resolved business action. One real event per source transition, role-scoped access, no duplicate alerts or staff double-action.
- Member wallet history and *full financial transaction history* must include top-up, wallet payment, wallet compensation, withdrawal and existing eligible Cash/Bank/QR payments, with status, amount, timestamp, codes, direction and source. No cross-member leakage or sensitive bank details.
- Preserve three existing real payment channels `CASH`, `BANK_TRANSFER`, QR; wallet is an INTERNAL settlement mechanism authorized by the new CODEOWNER decision, not a new external financial gateway. No extra payment gateways added.

## 4. Required engineering/test gates per PR

- Server-side validation and authorization; revalidate at submission, not only at initial page load. Negative tests: another user's records, same request replay, parallel actions, insufficient funds, full capacity, pending/non-confirmed QR, cancellation/restoration, and active/inactive role changes.
- Enforce financial operations with database transactions, lock/compare-and-update constraints and append-only ledger entries. Avoid frontend-only balance math; never change ledger history in place.
- Run target repo frontend lint/typecheck/test/build and backend unit/integration checks that can safely execute; include actual commands and result counts. Do not invent test counts or pass claims. Existing CI must be reviewed but green CI alone is not proof of E2E.
- Browser acceptance: screenshots from a **real local running Desktop UI** at >= 1280 px viewport, preferably 1440x900. At least two meaningful screenshots per PR (functional state plus outcome/error/permission state). Use synthetic/test data, redact account numbers, do not fabricate/edit screenshots to hide defects. Record exact commit, route, role, viewport, test fixture, capture method. Store under `tasks/Member-Wallet-Release (i)/evidence/MW-XX/` with `README.md`; link in PR body.
- Keep captures relevant to task: even backend-heavy MW-03 must expose working wallet overview/empty state and protected error/authorization state.
- If browser cannot run, mark PR `BLOCKED_EVIDENCE`; do not substitute Figma/mockups or claim Desktop UI verified.

## 5. One consolidated review after all implementation PRs are opened

Review **all eight PRs in one batch**, not task-by-task user prompting. Compare each PR against intended base and latest `origin/main`; inspect diffs, migrations, dependency chain, tests, UX screenshot proof, payment invariants and conflicts. Re-run focused cross-flow E2E if allowed. Independently review risky wallet/reconciliation scenarios including concurrency, retry/idempotency, money provenance, bank ownership, insufficient balance, canceled or expired order, read/resolved notification distinction, and cross-member access.

Write one full handoff at `tasks/Member-Wallet-Release (i)/reports/FINAL_CODEOWNER_HANDOFF.md` with eight PR numbers/URLs, actual latest HEAD/base SHAs, screenshots links, tests, CI, security/data assessment, open defects, merge order and precise status per PR: `READY_FOR_CODEOWNER_REVIEW`, `CHANGES_REQUIRED`, or `BLOCKED`. Only CODEOWNER can approve/merge; do not submit an APPROVE review on behalf of CODEOWNER. If some tasks blocked, still submit factual consolidated partial report. Do not wait silently for a reply; finish available work and report.

## 6. Final message to Truc and CODEOWNER

Summarize created PRs, evidence locations and known gaps; list exact commands needed to validate locally. Do not present a PR as merged. Do not claim a real payout, real banking sync, health diagnosis or production security certification. Keep all code and PR documentation in English.
