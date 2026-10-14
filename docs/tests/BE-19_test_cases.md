# Test Cases: BE-19 Payment at the Desk

## Overview
- **Task ID**: BE-19
- **Feature Flow**: Flow 3 (payments), used by Flow 1 screens F1-13 Payment Handoff and F1-14 Register and Renew
- **Branch**: `feat/f3-desk-payment` (built on BE-18, `feat/f3-confirm-payment`)
- **Priority**: P1 (core flow demo)
- **Depends on**: BE-18 (receipt step `PaymentVerificationService.recordPaymentConfirmed`)
- **Objective**: When RECEPTIONIST or MANAGER sells a membership card or a package at the desk (channel RECEPTION), record the money at once: a SUCCESS payment linked to the item, the item ACTIVE, one PAID invoice, and the payment id and invoice id in the response. A member sees only their own payments and invoices; another member gets 403.
- **Environment Note**: Maven Central is blocked in the authoring environment, so these tests run in GitHub Actions CI on the pull request (unit tests without Docker, integration tests with Testcontainers SQL Server 2022). Update the Execution Status column after the CI run.

---

## 1. API Contract

### 1.1 POST /api/v1/packages/registrations (desk sale, channel RECEPTION)

Body: `{"memberId": 100, "packageId": 10, "channel": "RECEPTION", "startDate": "2026-10-14", "paymentMethod": "BANK_TRANSFER", "paymentReference": "FT2610140001", "amountReceived": 400000}`

### 1.2 POST /api/v1/membership-cards/purchase (desk sale by staff)

Body: `{"memberId": 100, "tierId": 2, "paymentMethod": "CASH", "amountReceived": 1500000}`

### 1.3 Payment fields (both endpoints)

| Field | Aliases | Default | Rule |
|---|---|---|---|
| `paymentMethod` | `method` | `CASH` | Any method at the desk |
| `referenceCode` | `paymentReference`, `reference` | none | Required for every method except `CASH` (400 otherwise), at most 100 characters |
| `amountReceived` | none | the price | Greater than zero, at most 10 integer digits and 2 decimals; `CASH` may be more (change noted), less answers 409; non-cash must equal the price |

### 1.4 Response additions

`MemberCardResponse` and `PackageRegistrationResponse`: `paymentId`, `paymentCode` (existing), `invoiceId`, `invoiceNumber` (new). Set after a desk sale and after an activation that issued the invoice.

### 1.5 Errors

| Case | Status | Body |
|---|---|---|
| Reference missing for a non-cash method | 400 | `message: Reference code is required for payment method ...` |
| Less money than the price, or a non-cash amount that differs | 409 | `code: PAYMENT_AMOUNT_MISMATCH`, nothing saved |
| Member reads another member's payment or invoice | 403 | `Access Denied` |

---

## 2. Test Cases

| ID | Requirement | Scenario | Input | Expected Result | Level | Execution Status |
|---|---|---|---|---|---|---|
| TC-BE19-001 | Desk payment | Bank transfer, exact amount | 400.000, BANK_TRANSFER, reference " FT2610140002 " | SUCCESS payment, reference trimmed, payment_time set, confirmed by the receptionist, note "Paid at the desk" | Unit (`recordDeskPayment_BankTransferExact_CreatesSuccessPayment`) | Pending CI |
| TC-BE19-002 | Change | Cash more than the price | 380.000, CASH, received 500.000 | SUCCESS, no reference, note "Cash received 500000, change 120000.00" | Unit (`recordDeskPayment_CashMore_NotesTheChange`) | Pending CI |
| TC-BE19-003 | Defaults | No method, no amount received | 380.000 | CASH, SUCCESS | Unit (`recordDeskPayment_Defaults_CashAndExactAmount`) | Pending CI |
| TC-BE19-004 | Short cash | Cash less than the price | 380.000, received 300.000 | 409 PAYMENT_AMOUNT_MISMATCH, nothing saved | Unit (`recordDeskPayment_CashShort_MismatchAndNothingSaved`) | Pending CI |
| TC-BE19-005 | Non-cash exact | Card slip with more money | 380.000, CREDIT_CARD, received 400.000 | 409, message says only cash can be more | Unit (`recordDeskPayment_NonCashDifferent_Mismatch`) | Pending CI |
| TC-BE19-006 | Reference | Bank transfer, blank reference | BANK_TRANSFER, "  " | 400, nothing saved | Unit (`recordDeskPayment_TransferWithoutReference_BadRequest`) | Pending CI |
| TC-BE19-007 | Staff only | Member as actor | member user | 403 AccessDeniedException | Unit (`recordDeskPayment_MemberActor_Forbidden`) | Pending CI |
| TC-BE19-008 | Card desk sale wiring | Receptionist sells Gold by transfer | tier 2, BANK_TRANSFER, FT123, 200.000 | Card ACTIVE, paymentId 900, invoiceId 70, invoiceNumber INV-0070; no pending payment created | Unit (`purchaseCard_AtDesk_TakesTheMoneyAndReturnsTheReceipt`) | Pending CI |
| TC-BE19-009 | Online unchanged | Member requests a card | tier 2 | PENDING_PAYMENT, no desk payment, no invoice | Unit (`purchaseCard_Online_TakesNoDeskPayment`) | Pending CI |
| TC-BE19-010 | Package desk sale wiring | Receptionist sells a package in cash | package 10, CASH, 400.000 | ACTIVE, paymentId 900, invoiceId 71, invoiceNumber INV-0071 | Unit (`registerPackage_AtDesk_TakesTheMoneyAndReturnsTheReceipt`) | Pending CI |
| TC-BE19-011 | Staff online request | Receptionist, channel ONLINE | package 10 | PENDING_PAYMENT, no desk payment | Unit (`registerPackage_ByStaffForOnlineChannel_TakesNoDeskPayment`) | Pending CI |
| TC-BE19-012 | 403 payment | Other member reads a payment | GET /payments/10 | AccessDeniedException | Unit (`PaymentServiceImplTest.getPayment_OtherMember_ThrowsAccessDenied`) | Pending CI |
| TC-BE19-013 | 403 payment method | Other member changes a payment method | PUT /payments/10/method | AccessDeniedException | Unit (`PaymentServiceImplTest.updatePaymentMethod_OtherMember_ThrowsAccessDenied`) | Pending CI |
| TC-BE19-014 | 403 HTTP | Other member, controller | GET /payments/1, PUT /payments/1/method | 403 Forbidden | WebMvc (`PaymentControllerTest`) | Pending CI |
| TC-BE19-015 | 403 invoice | Other member reads an invoice | GET /invoices/by-payment/1 | AccessDeniedException | Unit (`PaymentVerificationServiceImplTest.invoiceByPayment_Visibility`) | Pending CI |
| TC-BE19-016 | End to end, package | Desk package sale by transfer through the API | POST /packages/registrations with paymentReference alias | 200 ACTIVE; payment SUCCESS with reference, time, confirmed by; registration linked; one invoice; one PAYMENT_CONFIRMED; totals add up | Integration (`deskPackageSale_ByTransfer_RecordsPaymentAndReceipt`) | Pending CI |
| TC-BE19-017 | End to end, card | Desk card sale in cash with change, `method` alias | POST /membership-cards/purchase, 1.500.000 for 1.200.000 | 200 ACTIVE; SUCCESS 1.200.000; note with change 300000.00; totals add up | Integration (`deskCardSale_CashWithChange_RecordsPaymentAndReceipt`) | Pending CI |
| TC-BE19-018 | End to end, short cash | 300.000 cash for a 400.000 package | POST /packages/registrations | 409 PAYMENT_AMOUNT_MISMATCH; no registration and no payment for the member | Integration (`deskSale_ShortCash_ConflictAndNothingCreated`) | Pending CI |
| TC-BE19-019 | End to end, visibility | Owner and other member read the payment and invoice | GET /payments/{id}, GET /invoices/by-payment/{id} | Owner 200 for both (status SUCCESS, one line); other member 403 for both | Integration (`memberSeesOnlyOwnPaymentAndInvoice`) | Pending CI |
| TC-BE19-020 | Invoice totals | Every desk sale invoice | invoice and its lines | subtotal = sum of line totals; total = subtotal - discount + tax | Integration (`assertInvoiceTotalsAddUp` in TC-BE19-016 and 017) | Pending CI |

---

## 3. Manual Checks Against FE-05 (after merge)

| ID | Scenario | Steps | Expected |
|---|---|---|---|
| M01 | Desk package sale, cash | Register and Renew, sell a 30-day package, give more cash | ACTIVE registration, receipt link opens the invoice, change shown |
| M02 | Desk package sale, transfer | Same with bank transfer and a reference | Reference stored on the payment and shown on the receipt |
| M03 | Desk card sale | Sell a Gold card | ACTIVE card, receipt link |
| M04 | Short cash | Give less cash than the price | Error with PAYMENT_AMOUNT_MISMATCH, nothing created |
