# MW-05 evidence - Wallet settlement with safe booking

- Branch: `fix/flow-2-mw-05-booking-wallet` (stacked on `fix/flow-3-mw-04-topup`, with latest `origin/main` 6dbbcfa merged in)
- Captured: 2026-11-08 22:59 Asia/Bangkok
- Browser: Google Chrome via Playwright (channel `chrome`), viewport 1440x900, light theme
- Backend: jar built from this branch, profiles `dev,demo`, port 18080, throwaway database `sportify_mw_evidence_1794153496` (fresh V1-V52 plus demo seeds). Not the shared `sportify` database. Synthetic receiving account as in MW-04.
- Frontend: local Vite dev server on port 3000
- Fixture: Mai's wallet was funded with a staff-confirmed 2,000,000 VND synthetic top-up through the real MW-04 API; Mai and Bao each registered the existing "Badminton - 30-day package" (450,000 VND) online, which creates a PENDING payment.
- Capture script: `ev.py` + `mw05.py` (kept outside the repository)

| File | Role and route | Steps | Expected | Observed |
|---|---|---|---|---|
| `screenshots/01-pay-with-wallet-option.png` | MEMBER (Mai) `/member/payments/1` | Open the pending package payment | Wallet option with real balance; bank transfer still offered | As expected |
| `screenshots/02-wallet-paid-receipt.png` | MEMBER (Mai) receipt | Pay 450,000 VND from wallet | Receipt INV-1000, method Sportify Wallet, reference WALLET-2 | As expected |
| `screenshots/03-wallet-debited-once.png` | MEMBER (Mai) `/member/wallet` | Open My Wallet | 1,550,000 VND available, one Wallet payment row | As expected |
| `screenshots/04-insufficient-wallet-no-charge.png` | MEMBER (Bao) `/member/payments/{id}` | Open pending payment with an empty wallet | Button disabled, "Nothing will be charged" | As expected |

## API checks made in the same run

| Check | Result |
|---|---|
| Replay `POST /member/wallet/payments/1` | 200 with `replay: true`; balance stays 1,550,000 |
| Payment after wallet settlement | SUCCESS, method WALLET |
| Bao pays with an empty wallet | 400 `WALLET_INSUFFICIENT_FUNDS`; payment stays PENDING |
| Mai tries to pay Bao's payment | 404 |

## Booking race

Booking a session keeps using package session credits; no seat is sold for money and the booking code does not touch the wallet. The last-seat race is covered by the existing `BookingRulesIntegrationTest.twoMembersCompeteForLastSeat_onlyOne201AndOne409WithoutOrphansOrExtraDebit`, run on this branch (31/31 pass). The demo seed has no two members with credits competing for one free seat, so the race was not repeated in the browser.
