# FINAL CODEOWNER HANDOFF - Member Wallet Release

Status of this file: FINAL for the implementation run (all eight tasks have a PR). It replaces the interim version written
after the CODEOWNER review of MW-01 to MW-04 and keeps its findings.

Run date/time/timezone: 2026-11-08 to 2026-11-09, Asia/Bangkok
Repository / initial origin/main SHA: `senko-do-thingz/G3-SportCentreManagement` / `e3e9a10bf981a4109c75e51d07c341b3a99e94d5`
Latest origin/main SHA at review: `88208809` (includes the CODEOWNER merges of #250 and #253)
Target DB: `sportify` / migration authorization status: NOT AUTHORIZED. V49 to V53 have not been applied to `sportify`.
Evidence used throwaway databases `sportify_mw_evidence_<epoch>` (created per run, dropped afterwards); integration tests
used the `DisposableUnifyDatabase` fixture on local SQL Server and Testcontainers in CI.
Local browser/JDK/SQL environment: Chrome via Playwright 1440x900, JDK 25, local SQL Server; CI uses Testcontainers SQL Server 2022.

## PR stack

```
main <- #250 MW-01 + MW-02 (merged by CODEOWNER)
     <- #253 MW-03         (merged by CODEOWNER)
     <- #255 MW-04 fix/flow-3-mw-04-topup                (open, base main)
          <- #258 MW-05 fix/flow-2-mw-05-booking-wallet  (base MW-04 branch)
               <- #259 MW-06 fix/flow-3-mw-06-withdrawal
                    <- #260 MW-07 fix/flow-1-mw-07-reception-inbox
                         <- #261 MW-08 fix/flow-3-mw-08-member-history
```

- #251 (MW-02) was merged on GitHub into the MW-01 branch at 2026-11-08 09:13:55 UTC (merge commit `fcbafd8e`) before
  CODEOWNER review, by the implementing account. This did not follow the plan. `main` was not changed; MW-02 was then
  reviewed as part of #250.
- The CODEOWNER changed MW-03 and MW-04 during review (release pins, compensation rule, verified receiving account,
  single in-flight wallet operation). These changes were merged forward into #258 to #261 with ordinary merges, no
  force-push, and MW-05/MW-06 were adapted to them (see findings).
- Release pins: V49 (#250), V50 (#253), V51 (#255), V52 (#258), V53 (#259): UnifyPreflight, the three release check
  scripts, NX-04 inventory, `docs/db-unify/schema-fingerprint.txt` and `RECONCILIATION.md` addenda. The V52 and V53
  fingerprints were computed by `UnifyReconciliationIntegrationTest` on a disposable local fixture after confirming the
  same test reproduces the CI V51 value on the CODEOWNER's MW-04 branch.

## One-run status

| Task | PR URL | Base | Head (at handoff) | Desktop screenshots | Tests/CI | Status | Blocker |
|---|---|---|---|---|---|---|---|
| MW-01 | https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/250 | `main` | merged | `evidence/MW-01/` (4) | CI green | MERGED by CODEOWNER | - |
| MW-02 | https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/251 | MW-01 branch | `c847497c` | `evidence/MW-02/` (3) | via #250 | MERGED (see note above) | - |
| MW-03 | https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/253 | `main` | merged | `evidence/MW-03/` (2) | CI green | MERGED by CODEOWNER | - |
| MW-04 | https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/255 | `main` | CODEOWNER-maintained | `evidence/MW-04/` (4, before the review fix) | CODEOWNER rerun | Awaiting CODEOWNER re-review | Recapture after the fix belongs to #255 |
| MW-05 | https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/258 | MW-04 branch | see PR | `evidence/MW-05/` (4) | local PASS, CI on latest head | READY_FOR_CODEOWNER_REVIEW | Depends on #255 |
| MW-06 | https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/259 | MW-05 branch | see PR | `evidence/MW-06/` (5) | local PASS, CI on latest head | READY_FOR_CODEOWNER_REVIEW | Depends on #258 |
| MW-07 | https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/260 | MW-06 branch | see PR | `evidence/MW-07/` (3, recaptured under the in-flight rule) | local PASS, CI on latest head | READY_FOR_CODEOWNER_REVIEW | Depends on #259 |
| MW-08 | https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/261 | MW-07 branch | see PR | `evidence/MW-08/` (4, recaptured, includes cross-flow E2E) | local PASS, CI on latest head | READY_FOR_CODEOWNER_REVIEW | Depends on #260 |

## Cross-flow findings

- Source/requirements discrepancies: MW-01 needed no code (profile and password already met the task). MW-05: the
  repository has no separately priced seat, so the wallet settles existing card/package purchases and booking keeps using
  package credits; no compensation entry is ever created by MW-05.
- Profile + password security: unchanged; existing tests (79) pass.
- Health confidentiality and Coach scope: raw answers only to the Member; assigned Coach sees the outcome only when
  shared; Receptionist 403, unrelated Coach 404.
- Real vs simulated top-up segregation: ACTUAL and SIMULATED pools; demo top-ups only with
  `app.wallet.simulated-topups-enabled=true`; demo funds cannot pay (service and DB CHECK) or be paid to a bank (demo
  withdrawals end `SIMULATED_COMPLETED` only).
- Real-money top-ups need a verified receiving account (CODEOWNER rule on #255).
- One in-flight wallet operation per Member (`wallet_pending_operation`, CODEOWNER rule on #255): MW-05 wallet payment
  calls `requireNone`; MW-06 withdrawal claims the slot at request and releases it on complete, reject or fail (this
  replaced the earlier limit of three open withdrawals). Lock order everywhere: `member_wallet`, then the operation row,
  then the slot, then the ledger.
- Ledger invariant and double-credit prevention: wallet row lock, unique operation keys (`TOPUP-`, `PAY-`, `WD-RES-`,
  `WD-REL-`, `WD-PAY-`), append-only trigger, `payment_wallet_settlement` unique per payment and ledger entry.
- Booking capacity + package credit vs wallet charge: booking code does not touch the wallet; the last-seat race is
  covered by `BookingRulesIntegrationTest.twoMembersCompeteForLastSeat...` (31/31 pass).
- Withdrawal reserves, Receptionist approval, completion: reserve at request, APPROVED moves no money, COMPLETED needs a
  unique payout reference, reject/fail release once (tested, including concurrency).
- Notification read != resolved: inbox state is read live from the source row; one notice per task and Receptionist via
  the unique `(user_id, event_key)` index; no account numbers in notices.
- Member transactions and wallet history: Payments & Receipts (existing, now shows Sportify Wallet) plus Wallet activity
  with filters, paging and a ledger reconciliation flag; cross-flow E2E reconciled (available 1,250,000 as expected).
- Non-regression: top-ups and withdrawals create no `payment`, `invoice`, `cash_event` or `refund_request` rows. A
  wallet-paid purchase is a real SUCCESS payment with method WALLET, so the existing cash trigger records it once at
  purchase time; staff confirmation, desk sales and method changes cannot select WALLET.
- SQL migrations and backup/rollback plan: V49 to V53 are additive. Applying them to `sportify` needs CODEOWNER
  authorization and a verified native backup first; rollback is restore from that backup (ledger rows are append-only).

## Test truth table

| Test | Executed? | Actual outcome | Evidence | Blocker if not run |
|---|---|---|---|---|
| Frontend lint/tsc/build | Yes, local on MW-08 (full stack) | lint 0, build 0 | local | |
| Frontend unit tests | Yes, local on MW-08 | 123 files / 1996 tests PASS | vitest | |
| Backend unit tests | Yes, local on MW-08 | 2424 run, 0 failures | surefire | |
| SQL integration tests | Yes, local disposable fixture (UNIFY_TEST_SQL_PORT) | Wallet ledger 16, top-up 15, payment 6, withdrawal 6, inbox 4, history 2, health 6, payment verification 25, booking rules 31, Unify 7, release migration 2, demo 59 + 60: PASS | failsafe | Full CI suite runs on each PR head |
| Real desktop browser acceptance | Yes, MW-01 to MW-08 | PASS | `evidence/MW-0x/` | |
| Cross-flow wallet/booking/reception E2E | Yes (MW-08 run) | reconciled, in-flight rule enforced, other member 0 rows | `evidence/MW-08/README.md` | |
| Concurrent / idempotent requests | Yes (integration) | ledger debits, top-up confirms, wallet pay, withdrawal requests, inbox sync | failsafe | |
| Finance report simulated provenance checks | Partly | no payment/invoice/cash_event from wallet operations; demo funds never pay | integration tests | No change to report queries was needed |

## CODEOWNER decisions and review outcome

- PRs ready for review: #258, #259, #260, #261 (stacked on #255).
- PRs needing changes, with exact fix: none known on #258 to #261 at handoff; #255 awaits the CODEOWNER's own re-review.
- Blocked PRs and cause: none blocked by CI after the pin and rule updates. Migration of `sportify` blocked pending
  authorization.
- Exact proposed merge order (do NOT merge): #255 -> #258 -> #259 -> #260 -> #261, retargeting each base to `main` after
  its parent merges.
- Commands to reproduce (from `backend/`, SQL Server reachable, values from `.env`):
  `UNIFY_TEST_SQL_PORT=1433 mvn -DskipUnitTests=true -Dit.test=WalletLedgerIntegrationTest,WalletTopUpIntegrationTest,WalletPaymentIntegrationTest,WalletWithdrawalIntegrationTest,ReceptionInboxIntegrationTest,WalletHistoryIntegrationTest -Dfailsafe.failIfNoSpecifiedTests=false verify`;
  `mvn test`; from `frontend/`: `npm run lint`, `npm run build`, `npx vitest run`.
- No approval/merge/deployment performed by the agent.
