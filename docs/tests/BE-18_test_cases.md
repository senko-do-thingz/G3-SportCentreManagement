# Test Cases: BE-18 Receptionist Verifies and Confirms Payments

## Overview
- **Task ID**: BE-18
- **Feature Flow**: Flow 3 (Payments, Invoices, Refunds and Reports)
- **Screens**: F3-04 Payment Requests, F3-05 Verify and Record Payment, F3-06 Payment Confirmed and Receipt, S3-Mismatch Payment Needs Review
- **Branch**: `feat/f3-confirm-payment`
- **Priority**: P1 (core flow demo)
- **Depends on**: BE-17 (pending payments, merged)
- **Objective**: Let RECEPTIONIST and MANAGER list payment requests (PENDING first), confirm a payment when the amount received matches (payment SUCCESS, card or package ACTIVE with recomputed dates, one PAID invoice with its line, activity log), answer 409 PAYMENT_AMOUNT_MISMATCH without changing anything but a note and a PAYMENT_AMOUNT_MISMATCH log, refuse a second confirm or fail with 409 PAYMENT_NOT_PENDING, and mark a payment FAILED with a reason. Review round 1: the FE-05 card and package activate endpoints issue the same receipt. Review round 2: every path that changes a payment and its card or registration locks the payment row first.
- **Environment Note**: Maven Central and the SQL Server image could not be downloaded in the environment that wrote this change (initial commit, review rounds 1 and 2), so nothing below was executed there. The unit and MockMvc tests run with `mvn test` on a developer machine; the Testcontainers tests need Docker and run in GitHub Actions CI. Update the Execution Status column after the first run.

---

## 1. API Contract

### 1.1 GET /api/v1/payments (RECEPTIONIST, MANAGER)

| Parameter | Type | Default | Notes |
|---|---|---|---|
| status | PENDING, SUCCESS, FAILED, REFUNDED | all | Unknown value -> 400 |
| method | CASH, CREDIT_CARD, BANK_TRANSFER, MOMO, VNPAY, ZALOPAY | all | Unknown value -> 400 |
| search | text | none | Member full name, member code, payment code, reference code (contains, case-insensitive). `query` is accepted as another name (FE-12) |
| from, to | ISO date | none | Inclusive dates on `created_at`; from after to -> 400 |
| page | int | 0 | Below 0 -> 400 |
| size | int | 20 | 1 to 100, otherwise 400 |

Order: PENDING rows first, oldest first; then every other row newest first.
Answer: `{content, page, size, totalElements, totalPages}`; each row is a `StaffPaymentResponse`:
`id, code, status, amount, method, referenceCode, paymentReference (same value), notes, paymentTime, memberId, memberCode, memberFullName, itemType (SPORT_PACKAGE or MEMBERSHIP_CARD), itemReferenceId, itemCode, itemDescription, itemStatus, originalPrice, discount, confirmedByUserId, confirmedByName, invoiceId, invoiceNumber, createdAt, updatedAt`.

### 1.2 PUT /api/v1/payments/{id}/confirm (RECEPTIONIST, MANAGER)

Body: `{"method": "BANK_TRANSFER", "referenceCode": "FT2610140001", "amountReceived": 380000, "note": "Checked bank app"}`.
Aliases accepted: `paymentMethod` for `method`, `paymentReference` for `referenceCode`.

| Case | Status | Body |
|---|---|---|
| Amount matches | 200 | `StaffPaymentResponse` with `status: SUCCESS`, `invoiceId`, `invoiceNumber`, `itemStatus: ACTIVE` |
| `method` or `amountReceived` missing, amount 0 or below | 400 | `message: Validation Error`, `details.<field>` |
| No `referenceCode` with a method other than CASH | 400 | `Reference code is required for payment method BANK_TRANSFER` |
| Payment linked to no card and no registration | 400 | `... is not linked to a membership card or a package registration` |
| Payment not found | 404 | |
| Amount differs | 409 | `code: PAYMENT_AMOUNT_MISMATCH` |
| Payment not PENDING | 409 | `code: PAYMENT_NOT_PENDING` |
| A second confirm or fail at the same moment (row lock, then status check) | 409 | `code: PAYMENT_NOT_PENDING` |
| Version check fails inside the call (backstop) | 409 | `code: PAYMENT_CONCURRENT_UPDATE` |
| MEMBER or COACH (controller or service) | 403 | |
| No token | 401 | |

### 1.3 PUT /api/v1/payments/{id}/fail (RECEPTIONIST, MANAGER)

Body: `{"reason": "No transfer found"}` (required, up to 255 characters). The linked card or registration is cancelled (for a registration the reason is stored in `cancel_reason`), the payment becomes FAILED, the reason is appended to the notes. Cancelling the item on fail is pending team confirmation. 409 `PAYMENT_NOT_PENDING` when the payment is not PENDING.

### 1.4 GET /api/v1/invoices/by-payment/{paymentId} (MEMBER owner, RECEPTIONIST, MANAGER)

Answer: `id, invoiceNumber, status, issueDate, paymentId, paymentCode, paymentMethod, paymentReference, paymentTime, memberId, memberCode, memberFullName, subtotalAmount, discountAmount, taxAmount, totalAmount, lines[{id, itemType, itemReferenceId, description, quantity, unitPrice, lineTotal}]`. 404 when no invoice exists or the member does not own the payment.

### 1.5 PUT /api/v1/membership-cards/{id}/activate and PUT /api/v1/packages/registrations/{id}/activate (FE-05)

Request unchanged: `{"paymentMethod": "CASH"}` or no body. No reference is required and the amount received is the payment amount. After activation the same `PaymentVerificationService.recordPaymentConfirmed` as confirm issues one PAID invoice and writes one PAYMENT_CONFIRMED row, so `GET /api/v1/invoices/by-payment/{id}` answers 200 for counter payments too.

---

## 2. Test Suite Summary

| Test Class | Category | Tests | Execution Status | Description |
|---|---|---|---|---|
| `PaymentVerificationServiceImplTest` | Service unit test (Mockito) | 19 | Not run yet | Row lock, invoice amounts and line, activation per item type without receipt duplication, invoice invariant subtotal - discount = total, mismatch note and log, 409 codes (NOT_PENDING, CONCURRENT_UPDATE, INVOICE_ALREADY_ISSUED), 403 for non-staff on confirm and fail, reason passed to cancelRegistration, list mapping, invoice visibility |
| `PaymentPricingConsistencyTest` | Service unit test (Mockito) | 3 | Not run yet | `GET /payments/{id}` and `GET /payments` show the same item name, original price and discount (package, package with a paid amount different from the payment, card upgrade) |
| `PaymentVerificationControllerTest` | WebMvc MockMvc test | 15 methods, 17 runs | Not run yet | Roles 200/403/401, service AccessDeniedException as 403, JSON aliases, 409 body codes, bean validation 400, list parameters and defaults |
| `PaymentVerificationIntegrationTest` | Testcontainers integration test | 14 | Not run yet (CI only) | Real SQL Server: activation dates, invoice rows, committed mismatch note and log, double confirm, confirm vs confirm, confirm vs fail, activate endpoint vs confirm and member cancel vs confirm races, version check, fail reasons, FE-05 activate endpoints issue the receipt, list order and search |
| `MembershipCardServiceImplTest` (existing, changed) | Service unit test | +4 new (1 in round 1, 3 in round 2), 1 assertion added | Not run yet | activateCard calls recordPaymentConfirmed; activateCardForPaymentConfirmation does not; activate and cancel lock the payment before reading the card; no lock without a payment |
| `SportPackageServiceImplTest` (existing, changed) | Service unit test | +5 new (3 in round 1, 2 in round 2), 1 assertion added | Not run yet | activateRegistration calls recordPaymentConfirmed; the confirmation variant does not; cancel reason stored or defaulted; activate and cancel lock the payment before reading the registration |
| `PaymentServiceImplTest`, `PaymentServiceTransactionMandatoryTest` (existing) | Service unit test | 40 and 4 | Not run yet | Constructor takes `PaymentItemResolver`; new `lockPayment` (found, not found, MANDATORY outside a transaction) |
| `PaymentControllerTest`, `GlobalExceptionHandlerTest`, `MembershipCardControllerTest`, `RefreshedCatalogAuthorizationTest` (existing) | Regression | | Not run yet | 409 bodies carry `code` only when set; activate endpoints keep their contract |

---

## 3. Detailed Test Cases

### 3.1 Acceptance criteria

| ID | Acceptance criterion | Steps | Expected | Covered by |
|---|---|---|---|---|
| TC-BE18-01 | Confirm with the right amount (package) | PENDING payment 380000 for a registration requested 3 days ago (original 400000, 5% discount); confirm BANK_TRANSFER, reference, 380000 | Payment SUCCESS, payment_time set, reference stored, confirmed_by_user_id = receptionist; registration ACTIVE, start = today, end = today + 29; one invoice PAID, subtotal 400000, discount 20000, tax 0, total 380000; one line SPORT_PACKAGE, unit price and line total 400000; one PAYMENT_CONFIRMED (no amountReceived in details) | Unit `confirm_ExactAmount_Registration_ActivatesAndIssuesInvoice`, IT `confirmRegistration_ExactAmount` |
| TC-BE18-02 | Confirm with the right amount (card) | PENDING card request 1200000; confirm 1200000.00 | Card ACTIVE, start = today, end = today + 12 months - 1 day; invoice discount 0, total 1200000; line MEMBERSHIP_CARD | IT `confirmCard_ExactAmount` |
| TC-BE18-03 | Card upgrade invoice | Upgrade request paying 1500000 for a 2000000 tier; confirm CASH 1500000 without reference | Line unit price 2000000, discount 500000, total 1500000, description ends with "upgrade, credit for the current card applied" | Unit `confirm_ExactAmount_CardUpgrade_InvoiceShowsCredit` |
| TC-BE18-04 | Wrong amount -> 409 PAYMENT_AMOUNT_MISMATCH, nothing else changes | Confirm 350000 for a 380000 payment | 409 PAYMENT_AMOUNT_MISMATCH; payment PENDING, payment_time, reference and confirmed_by null; note appended; registration PENDING_PAYMENT with the same dates, activated_at null; no invoice; exactly one PAYMENT_AMOUNT_MISMATCH row (amount, amountReceived, method, referenceCode, note); no PAYMENT_CONFIRMED; a later confirm with 380000 succeeds | Unit `confirm_AmountMismatch_KeepsPendingAndRecordsTrace`, Controller `confirm_Mismatch_Returns409WithCode`, IT `confirm_AmountMismatch_CommitsNoteOnly` |
| TC-BE18-05 | Confirming twice -> 409 | Confirm, then confirm again | 409 PAYMENT_NOT_PENDING; one invoice | Unit `confirm_NotPending_Conflict`, Controller `confirm_NotPending_Returns409`, IT `confirm_Twice_Conflict` |
| TC-BE18-06 | Two receptionists confirm at once | Two threads confirm the same payment | One succeeds; the other gets ConflictException PAYMENT_NOT_PENDING (no other exception accepted); one invoice | IT `concurrentConfirm_OnlyOneSucceeds` |
| TC-BE18-07 | Confirm and fail at once | One thread confirms, one fails the same card payment | One succeeds, the other gets ConflictException PAYMENT_NOT_PENDING; the data matches the winner (SUCCESS, ACTIVE, one invoice) or (FAILED, CANCELLED, no invoice) | IT `concurrentConfirmAndFail_OnlyOneSucceeds` |
| TC-BE18-08 | @Version backstop | Stale copy saved after another confirmation; flush failure inside the service | ObjectOptimisticLockingFailureException for the stale save; inside the service 409 PAYMENT_CONCURRENT_UPDATE | IT `staleVersion_IsRejected`, Unit `fail_OptimisticLockOnFlush_ConcurrentUpdateCode`, Controller `confirm_ConcurrentUpdate_Returns409WithCode` |
| TC-BE18-09 | Bank transfer without reference -> 400 | Confirm BANK_TRANSFER with blank reference | 400; the payment is not read | Unit `confirm_BankTransferWithoutReference_BadRequest`, Controller `confirm_MissingReference_Returns400` |
| TC-BE18-10 | MEMBER and COACH get 403 | List, confirm, fail as MEMBER and as COACH; member actor reaching the service | 403; service refuses with AccessDeniedException | Controller `nonStaff_Returns403`, `confirm_ServiceDenies_Returns403`; Unit `confirm_MemberActor_Refused`, `fail_MemberActor_Refused` |
| TC-BE18-11 | Unit and Testcontainers tests pass | `mvn -B test` locally, CI for integration tests | All green, no skipped tests | Section 2 |

### 3.2 One confirmation path (review round 1)

| ID | Case | Expected | Covered by |
|---|---|---|---|
| TC-BE18-12 | FE-05 card activate endpoint | `PUT /membership-cards/{id}/activate` with `{"paymentMethod":"CASH"}` -> 200 ACTIVE; payment SUCCESS by CASH; one PAID invoice with one MEMBERSHIP_CARD line; one PAYMENT_CONFIRMED | IT `activateCardEndpoint_IssuesReceipt`, Unit `activateCard_WithPendingPayment_CallsMarkPaymentSuccess` |
| TC-BE18-13 | FE-05 package activate endpoint | Same for `PUT /packages/registrations/{id}/activate`; `GET /invoices/by-payment/{id}` -> 200 with one line | IT `activateRegistrationEndpoint_IssuesReceipt`, Unit `activateRegistration_WithPendingPayment_CallsMarkPaymentSuccess` |
| TC-BE18-14 | Confirm does not issue the receipt twice | Activation variant used by confirm does not call recordPaymentConfirmed | Unit `activateCardForPaymentConfirmation_ActivatesWithoutIssuingTheReceipt`, `activateRegistrationForPaymentConfirmation_ActivatesWithoutIssuingTheReceipt` |
| TC-BE18-15 | Second invoice refused | recordPaymentConfirmed with an invoice already present | 409 INVOICE_ALREADY_ISSUED, nothing saved | Unit `recordPaymentConfirmed_InvoiceAlreadyIssued_Conflict` |
| TC-BE18-16 | Receipt only for SUCCESS | recordPaymentConfirmed on a PENDING payment | IllegalStateException | Unit `recordPaymentConfirmed_NotSuccess_IllegalState` |

### 3.2b Lock order (review round 2)

| ID | Case | Expected | Covered by |
|---|---|---|---|
| TC-BE18-36 | Activate and cancel lock the payment first | Order: read payment id, lockPayment, read card or registration, change the payment | Unit `activateCard_LocksThePaymentBeforeReadingTheCard`, `cancelCard_LocksThePaymentBeforeReadingTheCard`, `activateRegistration_LocksThePaymentBeforeReadingTheRegistration`, `cancelRegistration_LocksThePaymentBeforeReadingTheRegistration` |
| TC-BE18-37 | Item without payment | No payment lock, old behaviour | Unit `cancelCard_WithoutPayment_TakesNoPaymentLock` |
| TC-BE18-38 | FE-05 activate endpoint and confirm on the same card at once | Exactly one 200; the other 400 (card already ACTIVE) or 409 (PAYMENT_NOT_PENDING) with a message, never 500; payment SUCCESS, card ACTIVE, one invoice, one PAYMENT_CONFIRMED | IT `concurrentActivateEndpointAndConfirm_NoDeadlock` |
| TC-BE18-39 | MEMBER cancels the card request while a receptionist confirms | Exactly one 200, the other 400 or 409, never 500; either CANCELLED + FAILED + no invoice, or ACTIVE + SUCCESS + one invoice | IT `concurrentMemberCancelAndConfirm_ConsistentOutcome` |
| TC-BE18-40 | Invoice invariant | Resolver amounts that break original price - discount = payment amount -> IllegalStateException, no invoice, no log | Unit `recordPaymentConfirmed_InconsistentAmounts_IllegalStateAndNoInvoice` |
| TC-BE18-41 | lockPayment | Uses the lock query; 404 when missing; MANDATORY outside a transaction | Unit `lockPayment_Found_ReturnsLockedPayment`, `lockPayment_NotFound_Throws`, `lockPayment_OutsideTransaction_ThrowsIllegalTransactionStateException` |

### 3.3 Validation and edge cases

| ID | Case | Expected | Covered by |
|---|---|---|---|
| TC-BE18-17 | CASH without reference | Passes the reference rule | Unit `confirm_CashWithoutReference_PassesReferenceCheck` |
| TC-BE18-18 | Missing method, amountReceived 0 | 400 with `details.method` and `details.amountReceived` | Controller `confirm_InvalidBody_Returns400` |
| TC-BE18-19 | FE-12 field names | `paymentMethod`, `paymentReference` accepted | Controller `confirm_AcceptsAliasFieldNames` |
| TC-BE18-20 | Payment linked to nothing | 400, payment stays PENDING | Unit `confirm_NoLinkedItem_BadRequest` |
| TC-BE18-21 | Without token | 401 | Controller `confirm_Anonymous_Returns401` |
| TC-BE18-22 | Response field `paymentReference` | Same value as `referenceCode` | Controller `confirm_AsStaff_Returns200` |

### 3.4 Fail

| ID | Case | Expected | Covered by |
|---|---|---|---|
| TC-BE18-23 | Fail a pending card payment | Card CANCELLED, payment FAILED, notes end with the reason, PAYMENT_FAILED log | Unit `fail_PendingCard_CancelsCardAndRecordsReason`, IT `failCard_CancelsRequest` |
| TC-BE18-24 | Fail a pending registration payment | Reason stored in `cancel_reason`, registration CANCELLED, payment FAILED | Unit `fail_PendingRegistration_PassesReasonToCancel`, `cancelRegistration_WithReason_StoresTheReasonAsCancelReason`, IT `failRegistration_StoresReason` |
| TC-BE18-25 | Old cancel signature | Blank or no reason keeps "Cancelled by staff user id N" | Unit `cancelRegistration_WithBlankReason_KeepsTheDefaultReason`, existing cancel tests |
| TC-BE18-26 | Fail a payment that is not PENDING | 409 PAYMENT_NOT_PENDING, nothing cancelled | Unit `fail_NotPending_Conflict` |
| TC-BE18-27 | Blank reason | 400 with `details.reason` | Controller `fail_BlankReason_Returns400` |
| TC-BE18-28 | Fail as receptionist | 200 FAILED | Controller `fail_AsStaff_Returns200` |

### 3.5 List, pricing and receipt

| ID | Case | Expected | Covered by |
|---|---|---|---|
| TC-BE18-29 | PENDING first | No PENDING row after the first non-PENDING row | IT `list_PendingFirst` |
| TC-BE18-30 | Search by member code with status and dates | Finds the member's PENDING payment with name and item type | IT `list_PendingFirst`, Controller `list_PassesFilters` |
| TC-BE18-31 | Row mapping | Name, code, item, original price 400000, discount 20000 | Unit `list_MapsRows` |
| TC-BE18-32 | Same pricing on both screens | `GET /payments/{id}` and `GET /payments` agree on name, original price and discount | `PaymentPricingConsistencyTest` (3 tests) |
| TC-BE18-33 | Defaults | page 0, size 20 | Controller `list_Defaults` |
| TC-BE18-34 | Size 101, from after to, unknown status | 400 | Unit `list_InvalidParameters_BadRequest`, Controller `list_UnknownStatus_Returns400` |
| TC-BE18-35 | Invoice by payment | Staff read it; another member 404 | Unit `invoiceByPayment_Visibility` |

---

## 4. Manual Checks (Swagger, Postman or the FE-12 screens)

1. Log in as a member, request a package online (`POST /api/v1/packages/registrations`), note the `paymentId`.
2. Log in as receptionist, `GET /api/v1/payments?status=PENDING` -> the request is on top with member name and item code.
3. `PUT /api/v1/payments/{id}/confirm` with an amount 10000 lower -> 409 `PAYMENT_AMOUNT_MISMATCH`; the payment is still PENDING, its notes show the mismatch and `activity_log` has one PAYMENT_AMOUNT_MISMATCH row.
4. Confirm again with the right amount and a reference -> 200, `invoiceNumber` set; the package is ACTIVE.
5. `GET /api/v1/invoices/by-payment/{id}` -> subtotal - discount = total; one line.
6. Confirm again -> 409 `PAYMENT_NOT_PENDING`.
7. Request a card online, then activate it from the FE-05 counter screen (`PUT /membership-cards/{id}/activate`) -> `GET /api/v1/invoices/by-payment/{paymentId}` answers 200.

## 5. How to Run

```powershell
cd backend
mvn -B "-Dtest=PaymentVerification*Test,PaymentPricingConsistencyTest,PaymentServiceImplTest,PaymentServiceTransactionMandatoryTest,PaymentControllerTest,MembershipCardServiceImplTest,SportPackageServiceImplTest,MembershipCardControllerTest,RefreshedCatalogAuthorizationTest,GlobalExceptionHandlerTest" test
mvn -B test
```

Without Docker the Testcontainers classes are skipped locally (`@Testcontainers(disabledWithoutDocker = true)`), so a local run shows skipped tests. CI has Docker, runs them, and `scripts/ci/check-no-skipped-tests.sh` fails the build if any test is skipped there.
