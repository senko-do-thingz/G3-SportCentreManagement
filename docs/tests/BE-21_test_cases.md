# Test Cases: BE-21 Refunds Linked to Payments

## Overview
- **Task ID**: BE-21
- **Feature Flow**: Flow 3 (Payments, Invoices, Refunds and Reports)
- **Screens**: F3-07 Refund Requests, F3-09 refund part, S3-RefundSubmitted, S3-RefundReview, S3-RefundApproved, S3-RefundRejected, S3-RefundDone
- **Branch**: `feat/f3-refund-payment-link` (based on `feat/f3-confirm-payment`, BE-18; origin/main merged in review round 1, so the PR base should be `main`)
- **Priority**: P2
- **Depends on**: BE-18
- **Objective**: Every new refund is linked to the SUCCESS payment that bought the package registration; a payment can be refunded only once (409); the amount cannot exceed the payment amount; a pro-rated quote is available; completing a refund updates payment, invoice, registration and upcoming bookings in one transaction.
- **Environment Note**: Maven Central and the SQL Server image could not be downloaded in the environment that wrote this change, so nothing below was executed there. Unit and MockMvc tests run with `mvn -B test` on a developer machine; the Testcontainers tests need Docker and run in GitHub Actions CI. Update the Execution Status column after the first run.

---

## 1. API Contract

### 1.1 GET /api/v1/refunds/quote?registrationId= (MEMBER owner, RECEPTIONIST, MANAGER)

Answer `RefundQuoteResponse`:
`registrationId, registrationCode, packageName, paymentId, paymentCode, paymentMethod, paymentAmount, paidAmount, totalSessions, remainingSessions, upcomingBookings, unusedSessions, usedSessions, suggestedAmount`.

- suggestedAmount = paidAmount * unusedSessions / totalSessions, half up, 2 decimals, never above paymentAmount.
- unusedSessions = remainingSessions + upcomingBookings (CONFIRMED, session not started, no allowed check-in), at most totalSessions.
- Every session used -> suggestedAmount 0.00 (200, not an error).

| Case | Status |
|---|---|
| Owner member or staff, ACTIVE registration with SUCCESS payment | 200 |
| registrationId missing | 400 |
| Member who does not own the registration, or COACH | 403 |
| Unknown registration | 404 |
| No payment, or payment not SUCCESS | 409 `PAYMENT_NOT_REFUNDABLE` |
| Payment does not pay only for this registration (another member, also pays a membership card, or an invoice line bills another item) | 409 `PAYMENT_REGISTRATION_MISMATCH` |
| Payment REFUNDED or with a PENDING, APPROVED or COMPLETED refund | 409 `PAYMENT_ALREADY_REFUNDED` |
| Registration not ACTIVE (and no refund on the payment) | 400 |

### 1.2 POST /api/v1/refunds

Body: `{"packageRegistrationId": 50, "reason": "Moving away", "amountRequested": 450000}` (amountRequested optional, default the quote).
Same checks as the quote, then: no unused session -> 400; amountRequested 0 or below, or more than 2 decimals -> 400 (validation); above the payment amount -> 400. Answer 201 with `paymentId` and `paymentCode`.

### 1.3 PUT /api/v1/refunds/{id}/review (MANAGER)

Unchanged, plus: amountApproved must be above 0 and not above the payment amount (400).

### 1.4 PUT /api/v1/refunds/{id}/complete (RECEPTIONIST, MANAGER)

One transaction: payment REFUNDED, invoices REFUNDED, registration REFUNDED, upcoming CONFIRMED bookings CANCELLED with seat released, refund COMPLETED, activity_log REFUND_COMPLETED. Answer includes `cancelledBookings`. Payment already REFUNDED -> 409 `PAYMENT_ALREADY_REFUNDED`, nothing changes.

---

## 2. Unit Tests - `RefundPaymentLinkTest`

| ID | Test | Expected | Execution Status |
|---|---|---|---|
| BE21-U01 | Quote, 0 of 10 sessions used, paid 900000 | 900000.00, used 0 | Not run |
| BE21-U02 | Quote, 5 of 10 used | 450000.00, used 5 | Not run |
| BE21-U03 | Quote, all 10 used | 0.00, used 10 | Not run |
| BE21-U04 | Quote, remaining 3, the query returns one upcoming booking (checked-in ones are filtered in SQL, see BE21-I07) | upcoming 1, unused 4, 360000.00 | Not run |
| BE21-U05 | Quote, 1 of 3 unused, paid 100000 | 33333.33 (half up) | Not run |
| BE21-U06 | Quote, paid amount above payment amount | Capped at 900000.00 | Not run |
| BE21-U07 | Quote, other member | 403 | Not run |
| BE21-U08 | Quote, null registrationId | 400 | Not run |
| BE21-U09 | Submit, default amount | payment linked on the saved row, 450000.00, payment locked before the registration is read | Not run |
| BE21-U10 | Submit, amount equal to the payment | 900000.00 accepted | Not run |
| BE21-U11 | Submit, amount 0.01 above the payment | 400, nothing saved | Not run |
| BE21-U12 | Submit, payment has a live refund | 409 PAYMENT_ALREADY_REFUNDED, nothing saved | Not run |
| BE21-U13 | Submit, payment REFUNDED and registration REFUNDED | 409 (not 400) | Not run |
| BE21-U14 | Submit, live statuses | PENDING, APPROVED, COMPLETED (REJECTED excluded) | Not run |
| BE21-U15 | Submit, registration without payment | 409 PAYMENT_NOT_REFUNDABLE | Not run |
| BE21-U16 | Submit, payment PENDING | 409 PAYMENT_NOT_REFUNDABLE | Not run |
| BE21-U17 | Submit, all sessions used | 400 | Not run |
| BE21-U18 | Review, approve above the payment | 400 | Not run |
| BE21-U19 | Review, approve | Payment locked, then refund locked; registration REFUNDED; approved = requested | Not run |
| BE21-U20 | Complete | markRefunded called, registration REFUNDED, upcoming booking cancelled by the actor with reason (one conditional UPDATE), one seat released atomically, sessions not returned, REFUND_COMPLETED logged with the booking id, order payment lock, refund lock, payment update, session lock, booking update, seat release | Not run |
| BE21-U21 | Complete, registration still ACTIVE | Becomes REFUNDED with reason | Not run |
| BE21-U22 | Complete, payment already REFUNDED | 409, refund stays APPROVED, no booking change, no log | Not run |
| BE21-U23 | Complete, refund not APPROVED | 400, payment untouched | Not run |
| BE21-U24 | Complete, legacy refund without payment | COMPLETED, no payment call | Not run |

## 3. Unit Tests - `PaymentRefundServiceImplTest`

| ID | Test | Expected | Execution Status |
|---|---|---|---|
| BE21-P01 | SUCCESS payment with a PAID and a CANCELLED invoice | Payment REFUNDED with note, PAID invoice REFUNDED, CANCELLED invoice untouched | Not run |
| BE21-P02 | Payment REFUNDED | 409 PAYMENT_ALREADY_REFUNDED, nothing saved | Not run |
| BE21-P03 | Payment PENDING, FAILED | 409 PAYMENT_NOT_REFUNDABLE | Not run |
| BE21-P04 | Unknown payment | 404 | Not run |

## 4. Existing Tests - `RefundServiceImplTest` (fixtures adapted, assertions unchanged)

| ID | Test | Change | Execution Status |
|---|---|---|---|
| BE21-E01 | submitRefund_AsOwner_Success_CalculatesProRatedAmount | Registration gets a SUCCESS payment of 1000000; still 500000.00 | Not run |
| BE21-E02 | submitRefund_AsDifferentMember_ThrowsAccessDeniedException | Payment lock stubs added; still 403 | Not run |
| BE21-E03 | reviewRefund_Approve_SetsStatusApprovedAndPackageStatusRefunded | Refund read through findByIdForUpdate; still APPROVED and REFUNDED | Not run |
| BE21-E04 | reviewRefund_Reject_SetsStatusRejected | Same; still REJECTED and registration ACTIVE | Not run |
| BE21-E05 | RefreshedCatalogAuthorizationTest refund cases | No change | Not run |

## 5. Web Layer Tests - `RefundQuoteControllerTest`

| ID | Test | Expected | Execution Status |
|---|---|---|---|
| BE21-C01 | GET /refunds/quote as MEMBER, RECEPTIONIST, MANAGER | 200 with quote fields | Not run |
| BE21-C02 | GET /refunds/quote as COACH | 403 | Not run |
| BE21-C03 | GET /refunds/quote without registrationId | 400 | Not run |
| BE21-C04 | GET /refunds/quote, other member | 403 | Not run |
| BE21-C05 | GET /refunds/quote without token | 401 | Not run |
| BE21-C06 | POST /refunds, second refund | 409 with code PAYMENT_ALREADY_REFUNDED | Not run |
| BE21-C07 | POST /refunds, amountRequested 0 or 3 decimals | 400, service not called | Not run |
| BE21-C08 | POST /refunds with amountRequested | 201 with paymentId, paymentCode | Not run |
| BE21-C09 | PUT /refunds/{id}/complete | 200 COMPLETED with cancelledBookings | Not run |

## 6. Integration Tests - `RefundPaymentLinkIntegrationTest` (SQL Server, Testcontainers)

| ID | Test | Expected | Execution Status |
|---|---|---|---|
| BE21-I01 | Full flow: registration paid 380000 through BE-18, remaining 6 of 10, one past and one upcoming booking | Quote unused 7, 266000.00; submit 201 with payment_id stored; second submit 409; approve; complete 200 with 1 cancelled booking; payment, invoice, registration REFUNDED; upcoming booking CANCELLED, past booking CONFIRMED; seat 3 to 2; one REFUND_COMPLETED row; third submit 409 | Not run |
| BE21-I02 | Quote for remaining 10, 5, 0 of 10 | 380000, 190000, 0; used 0, 5, 10 | Not run |
| BE21-I03 | Completion fails at the booking update (database trigger) | Payment SUCCESS, invoice PAID, refund APPROVED, booking CONFIRMED, seat unchanged | Not run |
| BE21-I04 | Other member and amount cap | Quote and submit 403 for another member; amount 380000.01 is 400 | Not run |

## 7. Manual Demo Steps

1. As a member, buy a package and have a receptionist confirm the payment (BE-18).
2. Book one upcoming session with that package.
3. GET /api/v1/refunds/quote?registrationId={id} -> check unused sessions include the booking and the suggested amount.
4. POST /api/v1/refunds -> 201, `paymentId` set. POST again -> 409 PAYMENT_ALREADY_REFUNDED.
5. As manager, PUT /api/v1/refunds/{id}/review with `{"approved": true}`.
6. As receptionist, PUT /api/v1/refunds/{id}/complete -> COMPLETED, cancelledBookings 1.
7. GET /api/v1/payments/{paymentId} -> REFUNDED; GET /api/v1/invoices/by-payment/{paymentId} -> REFUNDED; GET /api/v1/bookings/my -> the upcoming booking is CANCELLED.

## 8. Review Round 1 (PR #58)

Findings verified before any change: P0-01 not confirmed (V24 has one BEGIN and one END, the same IF ... BEGIN THROW END; block as the applied V21, and V24 has never been on main or deployed, so no applied migration is touched); P1-02 confirmed (completion wrote booked_count from a value read without the session lock and locked bookings before sessions, the reverse of BE-08 and BE-10); P1-03 partly confirmed (the payment is reached through `sport_package_registration.payment_id`, but the payment member, a membership card on the same payment and the invoice lines were not checked); P2-01 confirmed (one check-in query per booking).

Lock order of completion: payment, refund_request, class_session rows in ascending id order, their booking rows, sport_package_registration.

### 8.1 Unit Tests - `RefundPaymentLinkTest`

| ID | Test | Expected | Execution Status |
|---|---|---|---|
| BE21-R1-U01 | P2-01: quote with three upcoming bookings | One booking query (`findRefundableUpcomingBookings`), no other booking repository call | Not run |
| BE21-R1-U02 | P1-02: complete with bookings on sessions 305, 301, 305 and an ACTIVE registration | Sessions locked 301 then 305; per session one cancel UPDATE then one seat release (1 and 2); registration saved last; bookings read twice (before and after the locks) | Not run |
| BE21-R1-U03 | P1-02: a booking cancelled by its member before the session lock | Skipped on the re-read; 1 booking cancelled, 1 seat released | Not run |
| BE21-R1-U04 | P1-02: the cancel UPDATE changes fewer rows than read (check-in in between) | 409 `REFUND_BOOKINGS_CHANGED`, no seat released, no log, refund stays APPROVED | Not run |
| BE21-R1-U05 | P1-02: re-read shows a booking on a session that was not locked | 409 `REFUND_BOOKINGS_CHANGED`, that session not locked, nothing cancelled | Not run |
| BE21-R1-U06 | P1-03: complete when the registration is paid by another payment | 409 `PAYMENT_REGISTRATION_MISMATCH`, payment not refunded, bookings not read | Not run |
| BE21-R1-U07 | P1-03: quote when the payment belongs to another member | 409 `PAYMENT_REGISTRATION_MISMATCH` | Not run |
| BE21-R1-U08 | P1-03: submit when the payment also pays a membership card | 409 `PAYMENT_REGISTRATION_MISMATCH`, nothing saved | Not run |
| BE21-R1-U09 | P1-03: submit when an invoice line of the payment bills another item | 409 `PAYMENT_REGISTRATION_MISMATCH`, nothing saved | Not run |
| BE21-R1-U10 | P1-03: submit checks the card link and the invoice lines with this registration id and SPORT_PACKAGE | Both checks called with payment 70 and registration 50 | Not run |
| BE21-R1-U11 | P1-03: approve when the payment also pays a membership card | 409 `PAYMENT_REGISTRATION_MISMATCH`, registration stays ACTIVE and is not saved | Not run |

`RefundServiceImplTest`: fixtures only (new repository mocks, the payment gets its member); assertions unchanged.

### 8.2 Integration Tests - `RefundPaymentLinkIntegrationTest` (SQL Server, Testcontainers)

| ID | Test | Expected | Execution Status |
|---|---|---|---|
| BE21-I05 | P1-02 regression: another transaction holds the session lock (UPDLOCK, HOLDLOCK) and takes a seat while the refund completes | The refund waits for the session lock (seen in sys.dm_exec_requests); booked_count 5 + 1 - 1 = 5; booking CANCELLED | Not run |
| BE21-I06 | P1-02 regression: another transaction plays BE-08 cancelBooking on the same booking (session lock, booking update, seat release) while the refund completes | No deadlock and no error; refund COMPLETED with 0 cancelled bookings; booked_count 5 to 4 (released once); the booking keeps the member's cancel reason | Not run |
| BE21-I07 | P2-01: one upcoming booking with an ALLOWED check-in and one with a DENIED check-in | Quote upcoming 1, unused 9; complete cancels only the DENIED one; its seat 3 to 2, the other seat unchanged | Not run |
| BE21-I08 | P1-03: an extra invoice line on the payment bills a membership card | Quote and submit 409 `PAYMENT_REGISTRATION_MISMATCH`; no refund row; payment SUCCESS | Not run |
| BE21-I09 | P1-03: the linked payment belongs to another member | Quote 409 `PAYMENT_REGISTRATION_MISMATCH` | Not run |

`RefundPaymentLinkIntegrationTest` is added to `scripts/ci/required-integration-tests.txt`, so CI fails if it does not run. The migration itself is executed on SQL Server by `FlywayMigrationTest` and by every integration test class (Flyway runs at context start).
