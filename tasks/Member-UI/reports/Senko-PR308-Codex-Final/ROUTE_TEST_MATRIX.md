# PR #308 Route Test Matrix

## Automated runs

| Scope | Command | Result |
|---|---|---|
| Focused remediation | `npm test -- --run src/pages/member/walletWithdraw.test.tsx src/pages/member/walletTopUp.test.tsx src/styles/memberCommerceIsolation.test.ts` | PASS: 3 files, 18 tests, 0 failed, 0 skipped |
| Full #308 frontend | `npm test -- --run` | PASS: 127 files, 2,031 tests, 0 failed, 0 skipped; 735.26 s |
| Lint | `npm run lint` | PASS: 0 errors |
| Production build | `npm run build` | PASS: 330 modules; existing >500 kB chunk advisory only |
| Patch hygiene | `git diff --check` | PASS |
| Browser capture | `node tasks/Member-UI/reports/Senko-PR308-Codex-Final/capture-browser-evidence.mjs` | PASS: 27 #308 Chrome PNGs plus 4 joint #308/#309 PNGs; DOM overflow gate on desktop; non-Member print isolation gate |

## Scenario coverage

| Gate | Result | Evidence |
|---|---|---|
| Booking list filters, empty/error/retry | PASS | `memberBookings.test.tsx`, three list screenshots |
| Booking stale/conflict/rapid submit/ambiguous response | PASS | `memberBookings.test.tsx`; source audit confirms synchronous guard and reconciliation |
| Cancellation rapid submit and ambiguous 408/5xx result | PASS | `memberBookings.test.tsx`; uncertain browser screenshot |
| Check-in denied/invalid/valid/expiry/rapid click/account switch/unmount | PASS | `CheckInPass.test.tsx`; safe fixture QR contains no production secret |
| Wallet real/reserved/demo separation and reconciliation | PASS | `memberWallet.test.tsx`, `walletHistory.test.tsx`; wallet/history screenshots |
| Top-up validation, pending, idempotency, same-tick create/cancel | PASS | expanded `walletTopUp.test.tsx` |
| Withdrawal validation, last four, pending, idempotency, same-tick submit | PASS | expanded `walletWithdraw.test.tsx` |
| Payout-account same-tick save | PASS | expanded `walletWithdraw.test.tsx` |
| Payment polling terminal/unmount/session cleanup | PASS | `memberPayments.test.tsx` source and regression suite |
| Simulated QR never represented as real revenue | PASS | `qrPaymentDemo.test.tsx`, demo screenshot |
| Receipt screen/A4 and scoped print CSS | PASS | receipt screenshots, `memberCommerceIsolation.test.ts` |
| Non-Member print unaffected by Member selector | PASS | source-level selector regression plus `/login` print browser smoke |
| Profile/password/session isolation | PASS | `myProfile.test.tsx`, `profileValidation.test.ts` |
| Health consent/privacy | PASS | `healthAssessment.test.tsx`, consent-off screenshot |
| Membership/card dynamic catalog and state | PASS | `membershipCards.test.tsx`, `cardsModel.test.ts` |

## Latest PR #309 integration run

- Clean local merge of #308 code SHA with PR #309 SHA `e1c0ffe6c9f2c0ee89a58151a813d04f44102111`: PASS, no conflicts.
- `npm run lint`: PASS.
- `npm run build`: PASS, 333 modules; existing large-chunk advisory.
- `npm test -- --run`: PASS, 128 files / 2,039 tests / 0 failed / 0 skipped, 391.78 s.
- An earlier intentionally parallelized run produced two Testing Library wait timeouts under concurrent lint/build/Chrome load (126/128 files and 2,033/2,035 tests passed). Both files immediately passed alone (2 files / 15 tests), and the full suite then passed when rerun without oversubscription. No source change was used to clear them.

The final GitHub CI is recorded in `FINAL_HANDOFF.md` after the final push.
