# Test Cases: BE-18 Receptionist Verifies and Confirms Payments

## Overview
- **Task ID**: BE-18
- **Feature Flow**: Flow 3 (Payments, Invoices, Refunds and Reports)
- **Screens**: F3-04 Payment Requests, F3-05 Verify and Record Payment, F3-06 Payment Confirmed and Receipt, S3-Mismatch Payment Needs Review
- **Branch**: `feat/f3-confirm-payment`
- **Priority**: P1 (core flow demo)
- **Depends on**: BE-17 (pending payments, merged)
- **Objective**: Let RECEPTIONIST and MANAGER list payment requests (PENDING first), confirm a payment when the amount received matches (payment SUCCESS, card or package ACTIVE with recomputed dates, one PAID invoice with its line, activity log), answer 409 PAYMENT_AMOUNT_MISMATCH without changing anything but a note, refuse a second confirmation with 409, and mark a payment FAILED with a reason.
- **Environment Note**: Maven Central and the SQL Server image could not be downloaded in the environment that wrote this change, so nothing below was executed there. The unit and MockMvc tests run with `mvn test` on a developer machine; the Testcontainers tests need Docker and run in GitHub Actions CI. Update the Execution Status column after the first run.

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
| Concurrent confirmation lost the version check | 409 | `The resource was modified by another request. Please reload and try again.` |
| MEMBER or COACH | 403 | |
| No token | 401 | |

### 1.3 PUT /api/v1/payments/{id}/fail (RECEPTIONIST, MANAGER)

Body: `{"reason": "No transfer found"}` (required, up to 255 characters). The linked card or registration is cancelled, the payment becomes FAILED, the reason is appended to the notes. 409 `PAYMENT_NOT_PENDING` when the payment is not PENDING.

### 1.4 GET /api/v1/invoices/by-payment/{paymentId} (MEMBER owner, RECEPTIONIST, MANAGER)

Answer: `id, invoiceNumber, status, issueDate, paymentId, paymentCode, paymentMethod, paymentReference, paymentTime, memberId, memberCode, memberFullName, subtotalAmount, discountAmount, taxAmount, totalAmount, lines[{id, itemType, itemReferenceId, description, quantity, unitPrice, lineTotal}]`. 404 when no invoice exists or the member does not own the payment.

---

## 2. Test Suite Summary

| Test Class | Category | Tests | Execution Status | Description |
|---|---|---|---|---|
| `PaymentVerificationServiceImplTest` | Service unit test (Mockito) | 13 | Not run yet | Invoice amounts and line, activation per item type, mismatch note, 409 codes, reference rule, fail cancels the item, list mapping and parameter checks, invoice visibility |
| `PaymentVerificationControllerTest` | WebMvc MockMvc test | 15 (with parameterized cases) | Not run yet | Roles 200/403/401, JSON aliases, 409 body code, bean validation 400, list parameters and defaults |
| `PaymentVerificationIntegrationTest` | Testcontainers integration test | 8 | Not run yet (CI only) | Real SQL Server: activation dates, invoice rows, committed mismatch note, double and concurrent confirmation, version check, fail, list order and search |
| `PaymentServiceImplTest` (existing) | Service unit test | 38 | Regression | Note appending moved to `PaymentNotes`; the four appendNote tests must still pass unchanged |
| `PaymentControllerTest`, `GlobalExceptionHandlerTest` (existing) | Regression | | Regression | 409 bodies now carry `code` only when the exception has one |

---

## 3. Detailed Test Cases

### 3.1 Acceptance criteria

| ID | Acceptance criterion | Steps | Expected | Covered by |
|---|---|---|---|---|
| TC-BE18-01 | Confirm with the right amount (package) | PENDING payment 380000 for a registration requested 3 days ago (original 400000, 5% discount); confirm BANK_TRANSFER, reference, 380000 | Payment SUCCESS, payment_time set, reference stored, confirmed_by_user_id = receptionist; registration ACTIVE, start = today, end = today + 29; one invoice PAID, subtotal 400000, discount 20000, tax 0, total 380000; one line SPORT_PACKAGE, reference id = registration id, unit price and line total 400000; one activity_log PAYMENT_CONFIRMED | Unit `confirm_ExactAmount_Registration_ActivatesAndIssuesInvoice`, IT `confirmRegistration_ExactAmount` |
| TC-BE18-02 | Confirm with the right amount (card) | PENDING card request 1200000, no current card; confirm 1200000.00 | Card ACTIVE, start = today, end = today + 12 months - 1 day; invoice discount 0, total 1200000; line MEMBERSHIP_CARD | IT `confirmCard_ExactAmount` |
| TC-BE18-03 | Card upgrade invoice | Upgrade request paying 1500000 for a 2000000 tier; confirm CASH 1500000 without reference | Line unit price 2000000, discount 500000 (upgrade credit), total 1500000, description ends with "upgrade, credit for the current card applied"; reference stays empty | Unit `confirm_ExactAmount_CardUpgrade_InvoiceShowsCredit` |
| TC-BE18-04 | Wrong amount -> 409 PAYMENT_AMOUNT_MISMATCH, nothing else changes | Confirm 350000 for a 380000 payment | 409 with code PAYMENT_AMOUNT_MISMATCH; payment PENDING, payment_time null, reference null; notes end with "Amount mismatch: received 350000, expected 380000.00 ..." and the note; registration PENDING_PAYMENT; no invoice; no activity_log row; a later confirm with 380000 succeeds | Unit `confirm_AmountMismatch_KeepsPendingAndAppendsNote`, Controller `confirm_Mismatch_Returns409WithCode`, IT `confirm_AmountMismatch_CommitsNoteOnly` |
| TC-BE18-05 | Confirming twice -> 409 | Confirm, then confirm the same payment again | Second call 409 code PAYMENT_NOT_PENDING; still one invoice | Unit `confirm_NotPending_Conflict`, Controller `confirm_NotPending_Returns409`, IT `confirm_Twice_Conflict` |
| TC-BE18-06 | Two receptionists at the same time | Two threads confirm the same payment at once | Exactly one succeeds, the other gets 409 (status check or version check); one invoice | IT `concurrentConfirm_OnlyOneSucceeds` |
| TC-BE18-07 | @Version optimistic lock | Load the payment, confirm it in another transaction, save the stale copy | ObjectOptimisticLockingFailureException (409 through the handler); one invoice | IT `staleVersion_IsRejected` |
| TC-BE18-08 | Bank transfer without reference -> 400 | Confirm BANK_TRANSFER with blank reference | 400 "Reference code is required ..."; the payment is not read | Unit `confirm_BankTransferWithoutReference_BadRequest`, Controller `confirm_MissingReference_Returns400` |
| TC-BE18-09 | MEMBER and COACH get 403 | Call list, confirm and fail as MEMBER, then as COACH | 403 for each call; service never called | Controller `nonStaff_Returns403` (MEMBER, COACH) |
| TC-BE18-10 | Unit and Testcontainers tests pass | `mvn test` locally, CI for integration tests | All green | Section 2 |

### 3.2 Validation and edge cases

| ID | Case | Expected | Covered by |
|---|---|---|---|
| TC-BE18-11 | CASH without reference | Passes the reference rule | Unit `confirm_CashWithoutReference_PassesReferenceCheck` |
| TC-BE18-12 | Missing method, amountReceived 0 | 400 Validation Error with `details.method` and `details.amountReceived` | Controller `confirm_InvalidBody_Returns400` |
| TC-BE18-13 | FE-12 field names `paymentMethod`, `paymentReference` | Accepted, bound to method and referenceCode | Controller `confirm_AcceptsAliasFieldNames` |
| TC-BE18-14 | Payment linked to nothing | 400, payment stays PENDING | Unit `confirm_NoLinkedItem_BadRequest` |
| TC-BE18-15 | Member actor reaching the service | Refused before any read | Unit `confirm_MemberActor_Refused` |
| TC-BE18-16 | Without token | 401 | Controller `confirm_Anonymous_Returns401` |
| TC-BE18-17 | Response field `paymentReference` | Same value as `referenceCode` | Controller `confirm_AsStaff_Returns200` |

### 3.3 Fail

| ID | Case | Expected | Covered by |
|---|---|---|---|
| TC-BE18-18 | Fail a pending card payment | Card CANCELLED, payment FAILED, notes end with "Payment failed, checked by user id N: No transfer found", activity_log PAYMENT_FAILED with the reason | Unit `fail_PendingCard_CancelsCardAndRecordsReason`, IT `failCard_CancelsRequest` |
| TC-BE18-19 | Fail a payment that is not PENDING | 409 PAYMENT_NOT_PENDING, nothing cancelled | Unit `fail_NotPending_Conflict` |
| TC-BE18-20 | Blank reason | 400 with `details.reason` | Controller `fail_BlankReason_Returns400` |
| TC-BE18-21 | Fail as receptionist | 200 with status FAILED | Controller `fail_AsStaff_Returns200` |

### 3.4 List and receipt

| ID | Case | Expected | Covered by |
|---|---|---|---|
| TC-BE18-22 | PENDING first | No PENDING row after the first non-PENDING row | IT `list_PendingFirst` |
| TC-BE18-23 | Search by member code with status and dates | Finds the member's PENDING payment with member name and item type | IT `list_PendingFirst`, Controller `list_PassesFilters` |
| TC-BE18-24 | Row mapping | Member name and code, item type, code and name, original price 400000, discount 20000, no invoice yet | Unit `list_MapsRows` |
| TC-BE18-25 | Defaults | page 0, size 20 | Controller `list_Defaults` |
| TC-BE18-26 | Size 101, from after to, unknown status | 400 | Unit `list_InvalidParameters_BadRequest`, Controller `list_UnknownStatus_Returns400` |
| TC-BE18-27 | Invoice by payment | Staff read it with its line; another member gets 404 | Unit `invoiceByPayment_Visibility` |

---

## 4. Manual Checks (Swagger, Postman or the FE-12 screens)

1. Log in as a member, request a package online (`POST /api/v1/packages/registrations`), note the `paymentId`.
2. Log in as receptionist, `GET /api/v1/payments?status=PENDING` -> the request is on top with member name and item code.
3. `PUT /api/v1/payments/{id}/confirm` with an amount 10000 lower -> 409 `PAYMENT_AMOUNT_MISMATCH`; `GET /api/v1/payments/{id}` -> still PENDING, notes show the mismatch.
4. Confirm again with the right amount and a reference -> 200, `invoiceNumber` set; the member's package is ACTIVE.
5. `GET /api/v1/invoices/by-payment/{id}` -> subtotal, discount and total add up; one line.
6. Confirm again -> 409 `PAYMENT_NOT_PENDING`.
7. `activity_log` has one PAYMENT_CONFIRMED row for the payment code.

## 5. How to Run

```powershell
cd backend
mvn -B "-Dtest=PaymentVerification*Test,PaymentServiceImplTest,PaymentControllerTest,GlobalExceptionHandlerTest" test
mvn -B test
```

The integration tests skip themselves without Docker (`@Testcontainers(disabledWithoutDocker = true)`); CI runs them.
