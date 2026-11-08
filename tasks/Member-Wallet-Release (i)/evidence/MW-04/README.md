# MW-04 evidence - QR wallet top-up verification

- Branch: `fix/flow-3-mw-04-topup` (stacked on `fix/flow-3-mw-03-wallet-core`)
- Captured: 2026-11-08 16:33 Asia/Bangkok
- Browser: Google Chrome via Playwright (channel `chrome`), viewport 1440x900, light theme
- Backend: jar built from this branch, profiles `dev,demo`, port 18080, throwaway database `sportify_mw_evidence_1794130003` (fresh V1-V51 plus demo seeds). Not the shared `sportify` database.
- Receiving account: SYNTHETIC. Bank BIN 970436, account `0011009990001`, name `SPORTIFYDEMOCENTER`, passed as JVM properties for this run only, with a copy of the repository bank directory whose timestamp was refreshed. No real account, no money moved. Demo top-ups enabled with `-Dapp.wallet.simulated-topups-enabled=true` (default is off).
- Frontend: local Vite dev server on port 3000
- Fixture: synthetic BE-07 accounts `mai.standard@example.com` (MEMBER) and `lan.reception@example.com` (RECEPTIONIST); bank references such as `FT26110800123` are invented
- Capture script: `ev.py` + `mw04.py` (kept outside the repository)

| File | Role and route | Steps | Expected | Observed |
|---|---|---|---|---|
| `screenshots/01-member-topup-qr-pending.png` | MEMBER `/member/wallet/topup` | Request 200,000 VND | VietQR with transfer note `SPW1`, status Pending review, wallet not credited | As expected |
| `screenshots/02-receptionist-review-before-confirm.png` | RECEPTIONIST `/reception/wallet/topups` | Review TU-000001, type received amount and bank reference | Amount is typed, not prefilled | As expected |
| `screenshots/03-receptionist-confirmed.png` | RECEPTIONIST `/reception/wallet/topups` | Confirm, filter Confirmed | CONFIRMED by Lan with reference | As expected |
| `screenshots/04-member-wallet-credited.png` | MEMBER `/member/wallet` | Open My Wallet | 200,000 VND available, one Top-up ledger row | As expected |

## API checks made in the same run

| Check | Result |
|---|---|
| Member balance while the top-up is PENDING | 0 |
| Second confirmation of TU-000001 | 409 `TOPUP_NOT_PENDING`; balance stays 200,000 with 1 ledger entry |
| Confirm another request with 90,000 received for 100,000 | 409 `TOPUP_AMOUNT_MISMATCH` |
| Confirm with a bank reference already used | 409 `TOPUP_DUPLICATE_REFERENCE` |
| Member calling the Receptionist confirm endpoint | 403 |
| Final balance | 200,000 VND |
