# Consolidated Review - Receptionist Concept A (RUI-00 to RUI-11)

PR: #305 (Draft). Reviewed code commit: `ccf05195`. Reviewer: Claude for Truc (not CODEOWNER; this is not an approval).

Method for every area: read the full diff for the page, confirmed the change is presentation only (no API call,
request body, guard or state transition added or removed), ran the page's existing unit tests in the full suite,
and checked the rendered page in Chrome against a FIXTURE API (see SCREENSHOT_MANIFEST.md).

Result legend: PASS = verified by the evidence listed. NOT VERIFIED = not exercised against a real backend.
No area was exercised against a real backend: E2E against the `sportify` database is NOT VERIFIED (blocked, see
FINAL_HANDOFF.md). Business invariants below are therefore verified at the UI and unit-test level only.

## Acceptance checklist

| RUI | Area | Diff is presentation only | Unit test evidence (all pass in full suite) | Browser (fixture) | Real backend |
|---|---|---|---|---|---|
| 00 | Foundation | PASS: new scoped CSS and 4 layout helpers, no data access | receptionConcept.test.ts (scope, square corners, ASCII), designTokens, rawColors | all shots | NOT VERIFIED |
| 01 | Action inbox | PASS | receptionInbox.test.tsx: Mark as read keeps task open and never calls topup confirm; counts; empty state | 01_* (populated, empty, error, dark, 390px) | NOT VERIFIED |
| 02 | Support | PASS | supportRequests.test.tsx: intake idempotency key, filters, reply required before resolve, notes stay internal, Member blocked | 02_support_queue | NOT VERIFIED |
| 03 | Member search | PASS | receptionScreens.test.tsx TC-FE05-030 to 037: results, empty refused, not found, create member, auth failure not fabricated | 03_* (default, results, no results) | NOT VERIFIED |
| 04 | Register and renew | PASS | receptionScreens.test.tsx TC-FE05-040 to 049: double submit guard, transfer needs reference and exact amount, pending never shown as paid | 04_register_renew | NOT VERIFIED |
| 05 | Check-in | PASS | receptionScreens.test.tsx TC-FE05-050+: POST once, verdict as returned, today list reload | 05_* (today, empty) | NOT VERIFIED |
| 06 | Assisted booking | PASS | receptionBookingScreens.test.tsx (25): cancellation once, member switch during pending, auth failures visible | 06_bookings_search | NOT VERIFIED |
| 07 | Payment requests | PASS | receptionPayments.test.tsx TC-FE12-020, UI-30: paging, backend order, one action per row, mismatch only for pending | 07_payment_requests | NOT VERIFIED |
| 08 | Verify, QR, receipt | PASS | receptionPayments, receptionQrDisplay (16), receptionLegacyProvenance (26): exact amount, no double confirm, QR labeled simulated, legacy receipts not printable | 08_* (light, dark). QR and receipt not captured | NOT VERIFIED |
| 09 | Refunds | PASS | receptionPayments.test.tsx refund cases: request is pending, approval and payout separate | 09_refund_requests | NOT VERIFIED |
| 10 | Wallet top-ups | PASS | member/walletTopUp.test.tsx (reception cases): typed amount and reference, duplicate shown as conflict, lost response re-read once, late payment needs note | 10_* (desktop, 390px) | NOT VERIFIED |
| 11 | Wallet withdrawals | PASS | member/walletWithdraw.test.tsx: approval says not paid yet, payout needs reference, balance check | 11_wallet_withdrawals | NOT VERIFIED |

Business invariants (one pending transaction, no negative balance, idempotent confirmation, top-up is not revenue,
revenue only on confirmed payment) live in the backend. This PR changes no backend file, request or guard, so it
cannot weaken them; they were not re-tested end to end here.

## Defect register

| ID | Severity | Area | Problem | Status |
|---|---|---|---|---|
| DEF-RUI-001 | Medium | 01, 10, 11 | Inbox open/unread counts are computed by the server over the newest 100 notices, and wallet lists return the newest 100 rows, but the stat labels read like center-wide totals. Steps: more than 100 notices; "Open tasks" undercounts. Source: ReceptionInboxService.java line 82, WalletTopUpService.java line 177. | FIXED in `b7d2bbe2`: labels say "(newest 100 notices)" / "(newest 100)"; test added |
| DEF-RUI-002 | Low | 00 (all heroes) | Dark theme: hero gradient ended in pale `--color-primary`, washing out the banner. Evidence: earlier dark capture. | FIXED in `fa07d801`: dark override with navy tokens; 01_inbox_dark.png |
| DEF-RUI-003 | Low | 05 | Arrival pass reader: input, button and help text touched. | FIXED in `ccf05195`: scoped spacing; 05_checkin_today.png |
| DEF-RUI-004 | Low, pre-existing, out of scope | 11 | WalletWithdrawalQueuePage `run()` guards with React state (`busy`), not a synchronous ref, so two clicks in the same frame could send two approve/complete calls. The backend state transition is expected to reject the second, but that was NOT VERIFIED here. Not changed by this PR. | OPEN, reported for CODEOWNER; suggest a ref guard like CheckInPassReader |
| DEF-RUI-005 | Info, pre-existing | 08 | "Amount received" shows the amount due as a grey placeholder. It is not a value (the field is empty and must be typed), but it can be mistaken for one at a glance. | OPEN, design note only |

No defect found that changes money, booking, check-in or support behavior.
