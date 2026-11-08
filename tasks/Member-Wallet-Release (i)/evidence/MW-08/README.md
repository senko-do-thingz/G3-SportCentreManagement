# MW-08 evidence - Member payment and wallet history (and cross-flow E2E)

- Branch: `fix/flow-3-mw-08-member-history` (stacked on `fix/flow-1-mw-07-reception-inbox`)
- Captured: 2026-11-09 00:04 Asia/Bangkok
- Browser: Google Chrome via Playwright (channel `chrome`), viewport 1440x900, light theme
- Backend: jar built from this branch (all eight tasks), profiles `dev,demo`, port 18080, throwaway database `sportify_mw_evidence_1794157432` (fresh V1-V53 plus demo seeds). Not the shared `sportify` database. Synthetic receiving account and demo top-ups enabled for this run only.
- Frontend: local Vite dev server on port 3000
- Capture script: `ev.py` + `mw08.py` (kept outside the repository)

## Cross-flow scenario on one synthetic member (Mai), all through the real APIs

1. Real top-up 2,000,000 confirmed by Receptionist Lan; a second confirmation answered 409.
2. Top-up 50,000 left PENDING.
3. Demo top-up 40,000 confirmed (demo pool).
4. Package (450,000) paid from the wallet; replay answered `replay: true` with no second debit.
5. Second package (450,000) paid by BANK_TRANSFER confirmed at reception.
6. Payout account saved; withdrawal 300,000 requested and approved (held, not paid); withdrawal 200,000 requested and rejected (released).

| Check | Result |
|---|---|
| Wallet activity rows | 6 |
| Available / held from the API | 1,250,000 / 300,000 (expected 2,000,000 - 450,000 - 300,000 = 1,250,000) |
| `reconciled` (stored balance equals the sum of ACTUAL ledger rows) | true |
| Another member (An) reading wallet history | 0 rows |

| File | Role and route | Expected | Observed |
|---|---|---|---|
| `screenshots/01-payments-and-receipts-with-wallet-and-bank.png` | MEMBER `/member/payments` | Bank transfer and Sportify Wallet payments with receipts; link to Wallet activity | As expected |
| `screenshots/02-wallet-activity-timeline.png` | MEMBER `/member/wallet/history` | Top-ups, wallet payment, withdrawals with statuses and balance after; ledger match message | As expected |
| `screenshots/03-wallet-activity-detail-and-statuses.png` | MEMBER | Detail of WD-000001: masked account, real money; "Approved - not paid yet"; pending top-up "not credited" with no balance change | As expected |
| `screenshots/04-filtered-topups-with-pending-and-demo.png` | MEMBER | Type filter Top-ups: confirmed, pending and Demo rows | As expected |

Note: for a demo row, "Balance after" is the demo pool balance; the row is labelled Demo.
