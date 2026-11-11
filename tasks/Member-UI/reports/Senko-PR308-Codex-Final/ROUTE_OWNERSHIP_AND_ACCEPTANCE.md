# PR #308 Route Ownership and Acceptance

Evidence revision: `1e50d919044a655fb84f87b34b376b886c4c5fd9`. All screenshot data is `LOCAL_FIXTURE`; identifiers contain `FIXTURE` and represent no person, bank transfer, or production transaction.

| Objective | Route | #308 source | API dependencies | Accepted states | Automated evidence | Browser evidence |
|---|---|---|---|---|---|---|
| O1 | `/member/bookings` | `MemberBookingsPages.tsx` | `GET /bookings/my` | upcoming, past, empty, loading, retry/error, confirmed/cancelled | `memberBookings.test.tsx`, `round2Recovery.test.tsx` | `bookings-upcoming-*`, `bookings-past-*`, `bookings-error-*` |
| O1 | `/member/bookings/new` | `BookingCreationPages.tsx` | session detail, package registrations | valid selection, ineligible, stale, guarded navigation | `memberBookings.test.tsx` | covered through review evidence |
| O1 | `/member/bookings/review` | `BookingCreationPages.tsx` | `GET /sessions/:id`, `POST /bookings`, status reconciliation | eligible, stale, conflict, ambiguous response, same-tick guard | `memberBookings.test.tsx` | `booking-review-*`, `booking-review-stale-*` |
| O1 | `/member/bookings/confirmed/:bookingId` | `BookingCreationPages.tsx` | booking history reconciliation | API-confirmed success, missing/stale booking, safe return | `memberBookings.test.tsx` | route behavior is covered by automated reconciliation tests |
| O1 | `/member/bookings/:bookingId/cancel` | `MemberBookingsPages.tsx` | `DELETE /bookings/:id`, `GET /bookings/my` | confirmation, late warning, rejected, uncertain, reconciled | `memberBookings.test.tsx` | `booking-cancel-confirm-*`, `booking-cancel-uncertain-*` |
| O1 | `/member/bookings/:bookingId/cancelled` | `MemberBookingsPages.tsx` | booking history reconciliation | verified cancelled state, unverified recovery | `memberBookings.test.tsx` | uncertain-state evidence plus automated verified-state coverage |
| O2 | `/member/check-in-pass` | `CheckInPassPage.tsx` | `POST /member/check-in-pass` | not issued, loading, denied, invalid token, valid QR, expiry/reissue, account switch/unmount | `CheckInPass.test.tsx` | `checkin-not-issued-*`, `checkin-valid-fixture-*` |
| O3 | `/member/wallet` | `MemberWalletPage.tsx` | `GET /member/wallet` | actual/reserved/demo separated, zero, unavailable, ledger/activity states | `memberWallet.test.tsx` | `wallet-overview-*`, `wallet-mobile-smoke-*` |
| O4 | `/member/wallet/topup` | `WalletTopUpPage.tsx` | top-up list/create/cancel, bank catalog | validated amount, real/demo, pending, API instructions, uncertain retry, single-flight create/cancel | `walletTopUp.test.tsx` | `wallet-topup-pending-*` |
| O5 | `/member/wallet/withdraw` | `WalletWithdrawPage.tsx` | wallet, payout account, withdrawal list/create | masked account, last-four confirmation, available/reserved, pending, uncertain retry, synchronous single-flight submit/save | `walletWithdraw.test.tsx` | `wallet-withdraw-pending-*`, `wallet-withdraw-1920x1080-dark.png` |
| O6 | `/member/wallet/history` | `WalletHistoryPage.tsx` | paged wallet history | server pagination/filtering, details, sign/status, reconciliation warning | `walletHistory.test.tsx`, `paymentHistory.test.tsx` | `wallet-history-warning-*` |
| O7 | `/member/payments` | `MyPaymentsPage.tsx` | `GET /payments/my` | pending/settled/simulated, filters, empty/error | `memberPayments.test.tsx`, `paymentsModel.test.ts` | `payments-list-*` |
| O7 | `/member/payments/:paymentId` | `PaymentSummaryPage.tsx` | payment detail | valid detail, unavailable, safe reference/method/amount | `memberPayments.test.tsx` | payment list/status evidence |
| O7 | `/member/payments/:paymentId/status` | `PaymentStatusPage.tsx` | payment detail polling | pending, terminal, error/recovery, timer/request cleanup | `memberPayments.test.tsx` | `payment-status-*` |
| O7 | `/member/payments/:paymentId/qr-demo` | `QrPaymentDemoPage.tsx` | demo capability/status | explicit simulation warning, permitted simulated completion, no revenue claim | `qrPaymentDemo.test.tsx` | `qr-demo-completed-*` |
| O7 | `/member/payments/receipts/:invoiceId` | `ReceiptPage.tsx` | invoice detail | authorized, unavailable/error, printable safe fields | `memberPayments.test.tsx`, CSS isolation test | `receipt-screen-*`, `receipt-print-a4-*` |
| O8 | `/member/profile` | `MyProfilePage.tsx` | `GET/PUT /users/me`, password change | validation, save/reload, password/session semantics, error focus | `myProfile.test.tsx`, `profileValidation.test.ts` | `profile-1440x900-light.png`, `profile-1920x1080-dark.png` |
| O8 | `/member/profile/health` | `HealthAssessmentPage.tsx` | health assessment read/write | opt-in, no-share/share consent, clear/unavailable, advisory copy | `healthAssessment.test.tsx` | `health-consent-off-*` |
| O9 | `/member/membership` | `MyMembershipPage.tsx` | registrations/cards | active/expired/pending, remaining sessions, allowed actions | membership/payment tests | `membership-packages-*` |
| O9 | `/member/membership/cards` | `MembershipCardsPage.tsx` | cards and dynamic tier catalog | no/active/expired card, pending purchase, API-backed prices/benefits | `membershipCards.test.tsx`, `cardsModel.test.ts` | `membership-cards-*` |

## Previous Gemini claims

| Claim | Result | Evidence |
|---|---|---|
| Concept B implementation exists across O1-O9 | **VERIFIED** | Source audit and full route/test matrix. Existing behavior was preserved unless a critical gate required remediation. |
| Booking creation/cancellation reconciles ambiguous outcomes | **VERIFIED** | Source audit plus `memberBookings.test.tsx`; the UI reads server state before another destructive action. |
| Check-in countdown is 300 seconds | **NOT VERIFIED / corrected** | The implementation uses backend `serverTime`/`expiresAt`, `performance.now()`, and a 120-second cap. No 300-second claim remains. |
| 45 screenshots were committed under `Senko-ConceptB-Final` | **NOT VERIFIED** | The folder was absent from the PR diff and the locally found files included repeated hashes and authentication/error views. None were reused. |
| Full regression is green | **VERIFIED for Codex head** | Fresh install, lint, production build, and 127 files / 2,031 tests pass on the #308 remediation commit. |
| Desktop and print evidence is reviewable in GitHub | **VERIFIED for this handoff** | 31 real Chrome PNGs plus SHA-256 manifest are committed in this report directory; one is a non-Member print-isolation smoke and four show #308 inside #309's latest shell. |

Hung-owned shell/router/navigation files were inspected but not modified by #308. The combined result is validated in a disposable worktree; see `FINAL_HANDOFF.md`.
