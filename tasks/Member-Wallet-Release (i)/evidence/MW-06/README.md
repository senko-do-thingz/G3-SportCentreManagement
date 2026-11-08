# MW-06 evidence - Member withdrawal requests

- Branch: `fix/flow-3-mw-06-withdrawal` (stacked on `fix/flow-2-mw-05-booking-wallet`)
- Captured: 2026-11-08 23:16 Asia/Bangkok
- Browser: Google Chrome via Playwright (channel `chrome`), viewport 1440x900, light theme
- Backend: jar built from this branch, profiles `dev,demo`, port 18080, throwaway database `sportify_mw_evidence_1794154520` (fresh V1-V53 plus demo seeds). Not the shared `sportify` database. Demo top-ups enabled for this run only.
- Frontend: local Vite dev server on port 3000
- Fixture: Mai funded with a staff-confirmed 1,000,000 VND synthetic top-up; Bao has only a 100,000 VND demo top-up. Payout account `0011001234567` / `TRAN THI MAI` and payout reference `PO26110800777` are invented.
- Capture script: `ev.py` + `mw06.py` (kept outside the repository)

| File | Role and route | Steps | Expected | Observed |
|---|---|---|---|---|
| `screenshots/01-member-payout-account-and-request.png` | MEMBER (Mai) `/member/wallet/withdraw` | Save payout account, request 300,000 with last 4 digits | Account shown masked `****4567`; request held | As expected |
| `screenshots/02-receptionist-approved-not-paid.png` | RECEPTIONIST `/reception/wallet/withdrawals` | Process WD-000001, Approve | "Approved, not yet paid" | As expected |
| `screenshots/03-receptionist-recorded-payout.png` | RECEPTIONIST | Enter payout reference, Record transfer as paid | Paid out with reference | As expected |
| `screenshots/04-member-history-paid-and-rejected.png` | MEMBER (Mai) | Reopen page after a second request was rejected | Paid out and Rejected (funds released) rows | As expected |
| `screenshots/05-demo-funds-cannot-be-paid-to-bank.png` | MEMBER (Bao) | Open withdraw page with demo funds only | Real withdrawal blocked; demo funds shown separately | As expected |

## API checks made in the same run

| Check | Result |
|---|---|
| Balances after request / approve / payout | available 700,000 held 300,000 / unchanged / held 0 |
| Reject second request, then reject again | 200 REJECTED (funds back, available 700,000) / 409 |
| Complete an already completed withdrawal | 409 |
| Bao requests a real withdrawal with only demo funds | 400 `WITHDRAWAL_ACCOUNT_REQUIRED` (and no ACTUAL balance) |
| Demo withdrawal completed with a bank reference / without | 400 / 200 `SIMULATED_COMPLETED` |
| Member list shows account as | `****4567` |
| Member calling approve | 403 |
