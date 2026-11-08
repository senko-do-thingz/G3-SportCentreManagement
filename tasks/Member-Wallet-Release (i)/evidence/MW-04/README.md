# MW-04 evidence - QR wallet top-up verification (re-captured after CODEOWNER review)

- Code under test: `a797f5ae` (branch `fix/flow-3-mw-04-topup`, base `main` after #250 and #253 were merged). The jar was built from this commit.
- Captured: 2026-11-09 00:15 Asia/Bangkok. Replaces the screenshots captured on 2026-11-08 before the review fixes.
- Browser: Google Chrome 157 driven by Playwright (`playwright-core`), headless, viewport 1440x900, light theme
- Backend: jar on port 18080, profiles `dev,demo`, JDK 25, throwaway database `sportify_mw_evidence_1794157867` on local SQL Server (fresh V1-V51 plus demo seeds). Not the shared `sportify` database. Dropped after capture.
- Receiving account: SYNTHETIC. BIN 970436, account `0011009990001`, name `SPORTIFYDEMOCENTER`, passed as command-line properties for this run only, with a copy of the repository bank directory whose `capturedAt` was refreshed. No real account, no money moved. Demo (SIMULATED) top-ups left at the default (off).
- Screenshot 08 comes from the same database after restarting the backend with `live-instructions-enabled=false` (no verified receiving account).
- Frontend: Vite dev server on port 3000 from the same commit, `VITE_API_BASE_URL=http://localhost:18080/api/v1`
- Fixture: synthetic BE-07 accounts `mai.standard@example.com` (MEMBER) and `lan.reception@example.com` (RECEPTIONIST); bank references such as `FT26110900101` are invented
- Capture script: `capture.cjs` (kept outside the repository)

| File | Role and route | Steps | Expected | Observed |
|---|---|---|---|---|
| `screenshots/01-member-topup-qr-pending.png` | MEMBER `/member/wallet/topup` | Request 200,000 VND | VietQR to the synthetic account, note `SPW1`, Pending review, wallet not credited | As expected |
| `screenshots/02-member-second-topup-refused-one-in-flight.png` | MEMBER `/member/wallet/topup` | Request 50,000 VND while TU-000001 is pending | Refused: one in-flight wallet operation | As expected |
| `screenshots/03-receptionist-confirmed.png` | RECEPTIONIST `/reception/wallet/topups` | Review TU-000001, type amount and bank reference, confirm | `TU-000001 confirmed` | As expected |
| `screenshots/04-receptionist-lost-response-shows-committed-outcome.png` | RECEPTIONIST `/reception/wallet/topups` | Confirm TU-000002; the request reaches the server and commits, the response is dropped on the way back (Playwright `route.fetch()` then `route.abort()`) | Page re-reads the request and shows it confirmed once; no second confirm request is sent | As expected (1 confirm request) |
| `screenshots/05-late-payment-refused-while-another-topup-pending.png` | RECEPTIONIST, filter Cancelled by member | Member cancelled TU-000003, then created TU-000004; Review late payment on TU-000003 with note | Refused: another wallet request (TU-000004) in progress; balance unchanged | As expected |
| `screenshots/06-late-payment-confirmed-after-other-request-resolved.png` | RECEPTIONIST, filter Confirmed | Reject TU-000004, then confirm the late payment TU-000003 with note | TU-000003 CONFIRMED by Lan | As expected |
| `screenshots/07-member-wallet-credited-once-per-request.png` | MEMBER `/member/wallet` | Open My Wallet | 350,000 VND available, three Top-up ledger rows (200,000 + 100,000 + 50,000) | As expected |
| `screenshots/08-nonpayable-guard-bank-topup-disabled.png` | MEMBER `/member/wallet/topup` (no verified account) | Open the page | Warning shown, Create button disabled | As expected |

## API checks made in the same run (as the signed-in user)

| Check | Result |
|---|---|
| Member balance while TU-000001 is PENDING | 0 |
| Second confirmation of TU-000001 | 409 `TOPUP_NOT_PENDING` |
| Confirm requests sent by the page during the lost-response case | 1 |
| Balance after the refused late confirmation (TU-000004 pending) | 300,000 (unchanged) |
| Final wallet | 350,000 VND available, 3 ledger entries |
| `POST /member/wallet/topups` ACTUAL with no verified receiving account | 409 `TOPUP_NOT_PAYABLE` |
| `GET /member/wallet/topups` `realTopUpsAvailable` with no verified account | `false` |
