# MW-03 evidence - Internal wallet ledger and overview

- Branch: `fix/flow-3-mw-03-wallet-core` (stacked on `fix/flow-1-mw-02-health`)
- Captured: 2026-11-08 15:36 Asia/Bangkok
- Browser: Google Chrome via Playwright (channel `chrome`), viewport 1440x900, light theme
- Backend: jar built from this branch, profiles `dev,demo`, port 18080, throwaway database `sportify_mw_evidence_1794126895` (fresh V1-V50 plus demo seeds). Not the shared `sportify` database.
- Frontend: local Vite dev server on port 3000
- Fixture: synthetic BE-07 demo accounts `mai.standard@example.com` (MEMBER) and `lan.reception@example.com` (RECEPTIONIST)
- Capture script: `ev.py` + `mw03.py` (kept outside the repository)

| File | Role and route | Steps | Expected | Observed |
|---|---|---|---|---|
| `screenshots/01-wallet-overview-zero-balance.png` | MEMBER `/member/wallet` | Open My Wallet for the first time | Wallet created at zero, empty activity, demo notice | As expected |
| `screenshots/02-receptionist-denied-member-wallet.png` | RECEPTIONIST `/member/wallet` | Open the Member wallet route | Access denied | As expected |

MW-03 adds no way to fund a wallet; top-up arrives in MW-04. Insufficient funds and concurrency are proven by integration tests, not by a screenshot.

## API checks made in the same run

| Check | Result |
|---|---|
| Member `GET /member/wallet` on first use | 200, availableBalance 0, activityCount 0 |
| Receptionist / Coach `GET /member/wallet` | 403 / 403 |
| `GET /member/wallet/ledger?size=500` | 400 |
| Attempted manual credit: `POST /member/wallet/credit`, `/member/wallet/topup-direct`, `POST /member/wallet` | 404, 404, 405 (no credit endpoint exists) |
