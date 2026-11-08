# FINAL CODEOWNER HANDOFF - Member Wallet Release

Status of this file: INTERIM (MW-01 to MW-04 only; MW-05 to MW-08 have no PR yet). Updated after the CODEOWNER review
of 2026-11-08.

Run date/time/timezone: 2026-11-08, Asia/Bangkok
Repository / initial origin/main SHA: `senko-do-thingz/G3-SportCentreManagement` / `e3e9a10bf981a4109c75e51d07c341b3a99e94d5`
Latest origin/main SHA at review: `6dbbcfa9` (merged into the stack, no force-push)
Target DB: `sportify` / migration authorization status: NOT AUTHORIZED. V49, V50 and V51 have not been applied to `sportify`. Evidence used throwaway databases `sportify_mw_evidence_*`.
Local browser/JDK/SQL environment: Chrome via Playwright 1440x900, JDK 25, SQL Server (throwaway databases); CI uses Testcontainers SQL Server 2022.

## PR stack and the #251 merge event

```
main <- #250 fix/flow-1-mw-01-member-profile (MW-01 + MW-02)
          <- #253 fix/flow-3-mw-03-wallet-core (MW-03)
               <- #255 fix/flow-3-mw-04-topup (MW-04)
```

- #251 (MW-02, `fix/flow-1-mw-02-health`) had `fix/flow-1-mw-01-member-profile` as its base. The author merged it on
  GitHub into that branch (not into `main`) at 2026-11-08 09:13:55 UTC, merge commit `fcbafd8e`, before any CODEOWNER
  review. The plan required separate CODEOWNER-reviewed PRs and no unapproved merges, so this merge did not follow the
  plan. The reason is not recorded in the branch history; the author has been asked to confirm it on #250. `main` was
  not changed.
- Effect: #250's diff against `main` is MW-01 plus MW-02, including migration
  `catalog/V49__member_health_assessment.sql`. #253's base was moved from the merged MW-02 branch to
  `fix/flow-1-mw-01-member-profile`.
- Release pins: V49 on #250, V50 on #253, V51 on #255 (UnifyPreflight, CI release checks, NX-04 inventory, schema
  fingerprint from the CI fixture, `docs/db-unify/RECONCILIATION.md` addenda).

## One-run status

| Task | PR URL | Base | Head | Desktop screenshots | Tests/CI | Status | Blocker |
|---|---|---|---|---|---|---|---|
| MW-01 | https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/250 | `main` | see PR | `evidence/MW-01/` (4) | CI green at `6a785f60` | READY_FOR_CODEOWNER_REVIEW | Combined diff with MW-02, documented above |
| MW-02 | https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/251 (merged into #250's branch) | MW-01 branch | `c847497c` | `evidence/MW-02/` (3) | Covered by #250 CI | Reviewed as part of #250 | Unapproved branch merge, see above |
| MW-03 | https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/253 | MW-01 branch | see PR | `evidence/MW-03/` (2) | CI rerun after review fix | READY_FOR_CODEOWNER_REVIEW (conditional) | Depends on #250 |
| MW-04 | https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/255 | MW-03 branch | see PR | `evidence/MW-04/` (4, captured before the review fix) | CI rerun after review fix | CHANGES_REQUIRED -> fixes pushed, awaiting re-review | Screenshots need recapture after the fix |
| MW-05 | - | - | - | - | - | NOT STARTED | Must use `WalletPendingOperations.requireNone` and compensation rules below |
| MW-06 | - | - | - | - | - | NOT STARTED | Must claim the single pending slot for a withdrawal |
| MW-07 | - | - | - | - | - | NOT STARTED | |
| MW-08 | - | - | - | - | - | NOT STARTED | |

## Cross-flow findings

- Profile + password security: unchanged on `main`; existing tests cover it (MW-01).
- Health confidentiality and Coach scope: raw answers only to the Member; Coach sees the outcome only when shared (MW-02).
- Real vs simulated top-up segregation: separate ACTUAL and SIMULATED pools; simulated top-ups only when
  `app.wallet.simulated-topups-enabled=true`.
- Real-money top-ups need a verified receiving account: creation is refused without one (`TOPUP_NOT_PAYABLE`), the
  account is stored on the request, confirmation re-checks it, and `ck_wallet_topup_beneficiary` refuses an ACTUAL row
  without it (CODEOWNER blocker 1 on #255).
- One in-flight wallet operation per Member: `wallet_pending_operation` (PK `member_id`) is shared by top-up, wallet
  purchase and withdrawal; a filtered unique index allows one PENDING top-up per Member (CODEOWNER blocker 2 on #255).
- Lock order: `member_wallet` row, then the operation row (`wallet_topup`), then the pending slot, then the ledger
  (CODEOWNER item 3 on #255).
- Ledger invariant and double-credit prevention: wallet row lock, unique operation key, append-only trigger.
  `COMPENSATION_CREDIT` must name one earlier `PURCHASE_DEBIT` of the same wallet and pool and can never exceed it
  (CODEOWNER note on #253).
- Late and unmatched transfers: `reports/MW-04-unmatched-payments.md` (on #255). A member-cancelled real-money request
  can be confirmed as a late payment with a note; unmatched money is never credited (CODEOWNER item 4 on #255).
- Booking capacity, withdrawals, notifications, transaction history: MW-05 to MW-08, not started.
- Non-regression: wallet code creates no `payment`, `invoice` or `cash_event` rows (tested).
- SQL migrations and backup/rollback plan: V49 to V51 are additive. Applying them to `sportify` needs CODEOWNER
  authorization and a verified native backup first.

## Test truth table

| Test | Executed? | Actual outcome | Evidence | Blocker if not run |
|---|---|---|---|---|
| Frontend lint/tsc/build | Yes (CI) | PASS on #250/#253; rerun pending on #255 | Actions | |
| Frontend unit tests | Yes (local + CI) | top-up tests 7/7 PASS locally after the fix | vitest | |
| Backend unit tests | Yes (local + CI) | PASS | surefire | |
| SQL integration tests | CI only (no Docker on the agent machine) | Rerun pending for #253/#255 after the fix | Actions | |
| Real desktop browser acceptance | MW-01..MW-04 by the author before the review fix | PASS at the time | `evidence/MW-0x/` | MW-04 recapture needs a throwaway DB at V51 |
| Cross-flow wallet/booking/reception E2E | No | - | - | MW-05 not started |
| Concurrent / idempotent requests | Yes (integration) | see #253/#255 CI | Actions | |
| Finance report simulated provenance checks | Partly | Wallet writes no payment/invoice/cash_event rows | `WalletLedgerIntegrationTest` | |

## CODEOWNER decisions and review outcome

- PRs ready for review: #250 (combined diff documented), #253 (conditional on #250).
- PRs needing changes, with exact fix: #255, four items fixed in the follow-up commit; awaiting re-review and new
  screenshots.
- Blocked PRs and cause: none blocked by CI. Migration of `sportify` blocked pending authorization.
- Exact proposed merge order (do NOT merge): #250 -> #253 -> #255.
- Commands to reproduce: `mvn -Dit.test=WalletLedgerIntegrationTest,WalletTopUpIntegrationTest,MemberHealthIntegrationTest verify`
  (needs Docker), `npx vitest run src/pages/member`.
- No approval/merge/deployment performed by agent.
