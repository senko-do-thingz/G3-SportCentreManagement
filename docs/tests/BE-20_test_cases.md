# Test Cases: BE-20 My Payments and Receipts

## Overview
- **Task ID**: BE-20
- **Feature Flow**: Flow 3 (Payments, Invoices, Refunds and Reports)
- **Screens**: F3-03 My Payments and Receipts, S3-PaidHistory, S3-CardReceipt, S3-MemberReceipt, S3-SingleReceipt
- **Branch**: `feat/f3-my-payments` (based on `feat/f3-confirm-payment`, BE-18)
- **Priority**: P1 (core flow demo)
- **Depends on**: BE-18 (invoices are issued when a payment is confirmed)
- **Objective**: A member lists their own payments newest first with the invoice number once confirmed, and reads a receipt (header and lines) by invoice id or by payment id. Staff read every receipt. Another member gets 403. Invoice totals follow total = sum of line totals - discount + tax.
- **Environment Note**: Maven Central and the SQL Server image could not be downloaded in the environment that wrote this change, so nothing below was executed there. Unit and MockMvc tests run with `mvn -B test` on a developer machine; the Testcontainers tests need Docker and run in GitHub Actions CI. Update the Execution Status column after the first run.

---

## 1. API Contract

### 1.1 GET /api/v1/payments/my (MEMBER only)

| Parameter | Type | Default | Notes |
|---|---|---|---|
| status | PENDING, SUCCESS, FAILED, REFUNDED | all | Unknown value -> 400 |
| page | int | 0 | Below 0 -> 400 |
| size | int | 20 | 1 to 100, otherwise 400 |

Order: newest first (`created_at` descending, then `id` descending).
Answer: `{content, page, size, totalElements, totalPages}`; each row is a `MemberPaymentResponse`:
`id, code, status, amount, method, itemType, itemReferenceId, itemCode, itemDescription, originalPrice, discount, time, paymentTime, createdAt, memberId, memberCode, invoiceId, invoiceNumber`.
`time` = `paymentTime` when set, otherwise `createdAt`. `invoiceId` and `invoiceNumber` are null until the receipt exists.
RECEPTIONIST, MANAGER, COACH -> 403. No token -> 401.

### 1.2 GET /api/v1/invoices/{id} and GET /api/v1/invoices/by-payment/{paymentId} (MEMBER, RECEPTIONIST, MANAGER)

Answer `InvoiceResponse`:
`id, invoiceNumber, status, issueDate, paymentId, paymentCode, paymentMethod, paymentReference, paymentTime, memberId, memberCode, memberFullName, subtotalAmount, discountAmount, taxAmount, totalAmount, lines[]`; each line `id, itemType, itemReferenceId, description, quantity, unitPrice, lineTotal`, in insert order.

| Case | Status |
|---|---|
| Owner member or staff | 200 |
| Member who does not own the invoice or payment | 403 |
| COACH or any other role | 403 |
| Unknown invoice id or payment id | 404 |
| Payment exists but has no invoice yet (PENDING, FAILED) | 404 |
| No token | 401 |

Totals rule: `totalAmount` = sum of `lines[].lineTotal` - `discountAmount` + `taxAmount`, and `subtotalAmount` = sum of `lines[].lineTotal`.

---

## 2. Unit Tests - `ReceiptServiceImplTest`

| ID | Test | Expected | Execution Status |
|---|---|---|---|
| BE20-U01 | My payments, no filter | Own payments only, in repository order (newest first); confirmed row has item, original price, discount, invoice id and number; pending row has null invoice and `time` = createdAt; sort is createdAt desc, id desc | Not run |
| BE20-U02 | My payments, status PENDING | Uses the member and status query only | Not run |
| BE20-U03 | My payments, empty history | Empty page, totalElements 0 | Not run |
| BE20-U04 | Size 0, -1, 101 | BusinessRuleException (400), repository not called | Not run |
| BE20-U05 | Page -1 | BusinessRuleException (400) | Not run |
| BE20-U06 | My payments as RECEPTIONIST | AccessDeniedException (403) | Not run |
| BE20-U07 | Invoice by id, owner | Header fields, payment method and reference, member name and code, lines; subtotal = sum of lines; lines - discount + tax = total | Not run |
| BE20-U08 | Invoice by id, RECEPTIONIST and MANAGER | 200 for another member's invoice | Not run |
| BE20-U09 | Invoice by id, other member | AccessDeniedException (403) | Not run |
| BE20-U10 | Invoice by id, COACH with the same user id as the owner | AccessDeniedException (403) | Not run |
| BE20-U11 | Invoice by id, unknown id | ResourceNotFoundException (404) | Not run |
| BE20-U12 | Invoice with two lines saved out of order, with tax | Lines sorted by id; lines - discount + tax = total | Not run |
| BE20-U13 | Invoice by payment, owner and staff | 200 | Not run |
| BE20-U14 | Invoice by payment, other member | 403, invoice table not read (existence of a receipt is not revealed) | Not run |
| BE20-U15 | Invoice by payment, PENDING payment | 404 "No invoice" | Not run |
| BE20-U16 | Invoice by payment, unknown payment | 404 | Not run |
| BE20-U17 | Null actor on every call | 403, no repository call | Not run |

## 3. Web Layer Tests - `ReceiptControllerTest` (MockMvc, real SecurityConfig)

| ID | Test | Expected | Execution Status |
|---|---|---|---|
| BE20-C01 | GET /payments/my as MEMBER | 200, page body with content fields and invoice number | Not run |
| BE20-C02 | GET /payments/my?status=PENDING&page=2&size=5 | Values passed to the service | Not run |
| BE20-C03 | GET /payments/my?status=DONE | 400, service not called | Not run |
| BE20-C04 | GET /payments/my?size=500 | 400 | Not run |
| BE20-C05 | GET /payments/my as RECEPTIONIST, MANAGER, COACH | 403, service not called | Not run |
| BE20-C06 | GET /payments/my without token | 401 | Not run |
| BE20-C07 | GET /invoices/55 as MEMBER, RECEPTIONIST, MANAGER | 200 with header, amounts, status and lines | Not run |
| BE20-C08 | GET /invoices/55, service says other member | 403 | Not run |
| BE20-C09 | GET /invoices/999 | 404 | Not run |
| BE20-C10 | GET /invoices/55 as COACH | 403, service not called | Not run |
| BE20-C11 | GET /invoices/by-payment/1 | 200 | Not run |
| BE20-C12 | GET /invoices/by-payment/1, other member | 403 | Not run |

## 4. Integration Tests - `ReceiptIntegrationTest` (SQL Server, Testcontainers)

| ID | Test | Expected | Execution Status |
|---|---|---|---|
| BE20-I01 | Member A has two payments, member B one; A's first payment is confirmed through BE-18 | /payments/my for A: 2 rows, newest first, confirmed row has invoiceId and INV- number and the package name; ?status=PENDING gives only the newer one | Not run |
| BE20-I02 | Receipt of a confirmed payment | Owner and receptionist get 200; subtotal = sum of lines; lines - discount + tax = total = 380000; member B gets 403 on /invoices/{id} and /invoices/by-payment/{paymentId} | Not run |
| BE20-I03 | Not found cases | Unknown invoice id 404; by-payment of a PENDING payment 404 | Not run |
| BE20-I04 | Routing and staff | /payments/my as RECEPTIONIST 403; GET /payments/{id} still answers next to /payments/my | Not run |

## 5. Regression

| ID | Test | Expected | Execution Status |
|---|---|---|---|
| BE20-R01 | `PaymentVerificationIntegrationTest` activate registration endpoint | Receptionist still reads /invoices/by-payment/{id} with 1 line | Not run |
| BE20-R02 | `PaymentVerificationServiceImplTest` | Passes without the moved invoice-by-payment test | Not run |
| BE20-R03 | `PaymentControllerTest` | GET /payments/{id} and PUT /payments/{id}/method unchanged | Not run |

## 6. Manual Demo Steps (Postman or the FE-11 screens)

1. Sign in as a member, request a package (F2 or FE-05 flow), note the payment id.
2. GET /api/v1/payments/my -> the payment is first, status PENDING, invoiceId null.
3. GET /api/v1/invoices/by-payment/{paymentId} -> 404 (no receipt yet).
4. Sign in as a receptionist, PUT /api/v1/payments/{paymentId}/confirm with the exact amount.
5. As the member, GET /api/v1/payments/my -> status SUCCESS, invoiceId and invoiceNumber set.
6. GET /api/v1/invoices/{invoiceId} -> header and lines; check lines - discount + tax = total.
7. Sign in as a second member, GET /api/v1/invoices/{invoiceId} -> 403.
