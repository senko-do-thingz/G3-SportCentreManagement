# Test Cases: BE-21 Refunds Linked to Payments

## Overview
- **Task ID**: BE-21
- **Feature Flow**: Flow 3 (Payments, Invoices, Refunds and Reports)
- **Screens**: F3-07 Refund Requests, F3-09 refund part, S3-RefundSubmitted, S3-RefundReview, S3-RefundApproved, S3-RefundRejected, S3-RefundDone
- **Branch**: `feat/f3-refund-payment-link` (based on `feat/f3-confirm-payment`, BE-18)
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
| BE21-U04 | Quote, remaining 3, two upcoming bookings, one checked in | upcoming 1, unused 4, 360000.00 | Not run |
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
| BE21-U20 | Complete | markRefunded called, registration REFUNDED, upcoming booking CANCELLED by the actor with reason, seat 5 to 4, sessions not returned, REFUND_COMPLETED logged, order payment lock, refund lock, payment update, booking update | Not run |
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
