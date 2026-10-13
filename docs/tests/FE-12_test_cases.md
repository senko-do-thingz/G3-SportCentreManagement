# Test Cases - Task FE-12 (Flow 3): Receptionist Payment Requests, Verify and Record Payment, Receipt, Refunds (F3-04 to F3-07)

## 1. Overview
- Task ID: FE-12
- Flow: Flow 3 - Payment and report management (Receptionist)
- Branch: feat/f3-reception-payments (from main at 6e82388)
- Owner: Phat
- Priority: P1
- Depends on: FE-00 (on main), BE-18 and BE-21 (not on main yet, see 1.1)
- Figma: `context/Flow 3 - Payment and report managmen/Flow 3 - Receptionist/` (F3-04, F3-05, F3-06, F3-07, S3-Mismatch, S3-Receipt, S3-RefundSubmitted, S3-RefundApproved, S3-RefundDone)
- Routes: `/reception/payments` (F3-04), `/reception/payments/:paymentId` (F3-05, S3-Mismatch, F3-06), `/reception/payments/:paymentId/receipt` (S3-Receipt), `/reception/refunds` (F3-07), `/reception/refunds/:refundId?memberId=` (refund states)
- Acceptance criteria:
  - AC1: Confirm button stays disabled until the checkbox is ticked and required fields are filled.
  - AC2: Mismatch shows the review state and keeps the payment pending.
  - AC3: After confirm, the member's card or package shows ACTIVE.
  - AC4: Refund states follow the Figma.
  - AC5 (role guard, same rule as FE-05): a MEMBER cannot open these routes.

### 1.1 Backend contract used
| Call | On main | Contract used by the frontend |
|---|---|---|
| `GET /api/v1/payments?status=&query=&page=&size=` | no, BE-18 | Page (`PageResponse` or Spring Page; a plain array is read as one page) of PaymentResponse plus `memberFullName`, `itemCode`. Empty filters are left out of the URL; "All statuses" sends no status |
| `GET /api/v1/payments/{id}` | yes (BE-17) | PaymentResponse; `id`, `code`, `status` required. Optional BE-18 fields: `memberFullName`, `itemCode`, `paymentReference` |
| `PUT /api/v1/payments/{id}/confirm` | no, BE-18 | Body `{paymentMethod, paymentReference, amountReceived, note}`; `paymentReference` null only for CASH. Answer: PaymentResponse with status SUCCESS; the linked card or registration becomes ACTIVE and an invoice is issued |
| 409 of confirm | BE-18 must agree | Body `{status: 409, message, code: "PAYMENT_AMOUNT_MISMATCH", details: {amountDue, amountReceived}}`. The code may also appear in the message. Payment stays PENDING. `details` is optional; without it the typed amount is shown |
| `PUT /api/v1/payments/{id}/fail` | no, BE-18 | Body `{reason}`; answer PaymentResponse with status FAILED and the reason in `notes` |
| `GET /api/v1/invoices/by-payment/{paymentId}` | no, BE-18 | `{id, invoiceNumber, paymentId, paymentMethod, paymentReference, paymentTime, memberId, memberCode, memberFullName, subtotalAmount, discountAmount, totalAmount, status, issueDate, lines[]}`; `id` and `invoiceNumber` required |
| `GET /api/v1/membership-cards/member/{id}`, `GET /api/v1/packages/registrations/member/{id}` | yes | Find the item whose `paymentId` is the confirmed payment and show its real status (AC3) |
| `GET /api/v1/members?query=` | yes | Member search for a refund |
| `GET /api/v1/refunds/quote?registrationId=` | no, BE-21 | `{registrationId, registrationCode, memberId, memberCode, memberFullName, serviceName or packageName, paymentCode, invoiceNumber, paymentStatus, paidAmount, priorRefundAmount, suggestedAmount, maxRefundable, eligible, ineligibleReason}`; `registrationId` and a numeric `suggestedAmount` required; `eligible` defaults to true, `maxRefundable` to `suggestedAmount` |
| `POST /api/v1/refunds` | yes; `amountRequested` BE-21 | Body `{packageRegistrationId, amountRequested, reason}`; answer RefundResponse (PENDING) |
| `GET /api/v1/refunds/pending` | yes, MANAGER only on main; BE-21 opens it to RECEPTIONIST | List of RefundResponse. On main a receptionist gets 403: the queue shows the error with Try again and the stat card shows "-" "Not available" |
| `GET /api/v1/refunds/member/{memberId}` | yes | List of RefundResponse, all statuses; also used to open one refund (there is no `GET /refunds/{id}`) |
| `PUT /api/v1/refunds/{id}/complete` | yes (no body); `refundReference` BE-21 | Body `{refundReference}`; answer RefundResponse COMPLETED with `refundReference`. On main the body is ignored |

Refund states (AC4): PENDING -> Refund Request Submitted ("{code} / Awaiting approval", Original payment, Requested refund, Status Pending approval). APPROVED -> Refund Approved ("{code} / Approved - not yet refunded", Approved amount, Original receipt, Return method, reference field, Record completed refund). COMPLETED -> Refund Completed ("Refund receipt / {code}", Amount returned, Refund reference, Status "Refunded / Member notified"). REJECTED -> Refund Rejected with the manager note.

FE-05 changes: the front desk confirmation now uses `PUT /payments/{id}/confirm`. `receptionApi.activateCard`, `receptionApi.activateRegistration` and `confirmCheck` are removed; TC-FE05-019 and TC-FE05-038 are retired, TC-FE05-035 and TC-FE05-047 are updated (section 3.2).

### 1.2 How to run (PowerShell)
```powershell
cd frontend
npm test
npm run lint -- --max-warnings 0
npx tsc -b
npm run build
```

### 1.3 Result summary (2026-10-14)
| Group | Cases | Result |
|---|---|---|
| Automated, rules (`src/pages/reception/receptionPaymentsModel.test.ts`) | 14 | 14 PASSED |
| Automated, screens (`src/pages/reception/receptionPayments.test.tsx`) | 17 (TC-FE12-050 runs for 5 routes, 21 runs) | 21 PASSED |
| Automated, FE-05 cases updated by this task | 2 | 2 PASSED |
| Automated, all other files (FE-00 to FE-05) | 395 | 395 PASSED |
| Lint (0 warnings), `tsc -b`, build | 3 | 3 PASSED |
| Manual against the real backend (section 4) | 12 | NOT RUN (need BE-18 and BE-21) |

Total: 432 automated tests in 22 files, 432 passed, 0 failed. All automated cases run against a fake backend that follows 1.1; they are not verified on main until BE-18 and BE-21 are merged.

## 2. Automated cases - rules (`receptionPaymentsModel.test.ts`)
| ID | Case | Expected | Result |
|---|---|---|---|
| TC-FE12-001 | List URL parameters: empty, unknown status, padded query, page 2, bad page; write back | PENDING, page 0 defaults; query trimmed; defaults left out of the URL | PASSED |
| TC-FE12-002 | Status helpers and the amount due stat | ALL sends no status; SUCCESS reads Paid with the PAID tone; only PENDING rows are summed | PASSED |
| TC-FE12-003 | Transfer form: valid, empty, decimal, zero, long note, unknown method | Reference and amount required; whole VND above 0; note at most 500; method message | PASSED |
| TC-FE12-004 | Cash and the checkbox | Cash needs no reference; Confirm needs the checkbox; method preset from the payment | PASSED |
| TC-FE12-005 | Confirm body | Trimmed text; cash with no reference sends null; note null when empty | PASSED |
| TC-FE12-006 | Amount check | empty, match (810.000), short, over (1,000,000); difference -10.000 | PASSED |
| TC-FE12-007 | Mismatch detection | 409 with code or message -> mismatch; other 409, 400 or plain Error -> not | PASSED |
| TC-FE12-008 | Mismatch amounts | Typed amount by default; backend details win; negative outstanding when paid too much | PASSED |
| TC-FE12-009 | Paid item | Registration or card with the payment id; null when none | PASSED |
| TC-FE12-010 | Refund form | Amount required, whole, above 0, at most the refundable balance; reason required, at most 500 | PASSED |
| TC-FE12-011 | Refund stages and labels | PENDING submitted, APPROVED approved, COMPLETED done, REJECTED rejected; original payment and amount helpers; detail path | PASSED |
| TC-FE12-012 | Reference, refundable registrations, order | Reference required, at most 100; only ACTIVE or EXPIRED with a payment; newest first | PASSED |
| TC-FE12-013 | Refund quote reader | Numeric string amount read; defaults for eligible and maxRefundable; missing registrationId or suggestedAmount refused | PASSED |
| TC-FE12-014 | ApiError body fields | `code` null without one; raw `details` kept beside the field errors | PASSED |

## 3. Automated cases - screens (`receptionPayments.test.tsx`)
Signed in as RECEPTIONIST with a fake backend that follows 1.1; today is 2026-10-14.

| ID | AC | Case | Steps | Expected | Result |
|---|---|---|---|---|---|
| TC-FE12-020 | - | Payment Requests list (F3-04) | Open `/reception/payments` | `GET /payments?status=PENDING&page=0&size=10`; rows REG-1041 Taylor Nguyen 810.000 VND with "Review payment" to `/reception/payments/41`; stats Pending requests 12, Amount due 1.310.000 VND, Refund requests 1; "Showing 1-10 of 12" | PASSED |
| TC-FE12-021 | - | Filters and paging | Status All, search REG-1041, page 2 | Calls `/payments?page=0&size=10`, `/payments?query=REG-1041&page=0&size=10`, `/payments?query=REG-1041&page=1&size=10` | PASSED |
| TC-FE12-022 | - | List error | GET /payments answers 500 | Backend message and Try again | PASSED |
| TC-FE12-030 | AC1 | Confirm disabled rules | Tick, type amount, type reference, clear reference, switch to Cash, untick | Disabled until checkbox + amount + reference (non-cash); Cash enabled without reference; untick disables; no PUT | PASSED |
| TC-FE12-031 | AC3 | Confirm success | Transfer 810.000 with reference and note, tick, Confirm | PUT body as in 1.1; "Payment Confirmed and Receipt"; Receipt INV-1041, "Bank transfer / BANK-1041-20261002", "-90.000 VND", Open receipt link; Service updated shows Active, REG-1041, "8 available" | PASSED |
| TC-FE12-032 | AC2 | 409 PAYMENT_AMOUNT_MISMATCH | 800.000, tick, Confirm | "Payment Needs Review": due 810.000, received 800.000, outstanding 10.000, "Registration remains Pending payment"; only one PUT; Return to verification keeps 800000 and unticks the checkbox | PASSED |
| TC-FE12-033 | AC2 | Amount does not match button | Type 900000, click "Amount does not match" | Panel says above the amount due; review shows "Received above the amount due" 90.000; no PUT | PASSED |
| TC-FE12-034 | - | Mark as failed | Open dialog, submit empty, then with a reason | "Reason is required" and no call; then PUT fail body `{reason}`, "Payment Failed" with the reason | PASSED |
| TC-FE12-035 | - | Backend field error | Confirm answers 400 `details.paymentReference` | Message under Reference code, still on the verify screen | PASSED |
| TC-FE12-036 | - | Receipt (S3-Receipt) | Open `/reception/payments/41/receipt` | "Receipt INV-1041", "INV-1041 / 02 Oct 2026", "Taylor Nguyen / MB-0246", 810.000 VND, Paid, Back to the payment | PASSED |
| TC-FE12-040 | AC4 | Create refund | Search Taylor, Select, Start refund for REG-1040, submit empty reason, then with reason | Quote `registrationId=10`; amount prefilled 150000; Eligible for review; reason required; POST body as in 1.1; "Refund Request Submitted", "RF-1041 / Awaiting approval", INV-1040, Pending approval | PASSED |
| TC-FE12-041 | AC4 | Refund limits | Amount 200000 for a 150000 balance; then an ineligible quote | Message "more than the refundable balance", no POST; Submit disabled, reason shown, Not eligible | PASSED |
| TC-FE12-042 | AC4 | Approved -> Completed | Open APPROVED refund, type reference, Record completed refund | Button disabled until the reference; PUT body `{refundReference}`; "Refund Completed", "Refund receipt / RF-1040", reference, "Refunded / Member notified" | PASSED |
| TC-FE12-043 | AC4 | Complete error | Complete answers 400 | Message in the form, still "Refund Approved" | PASSED |
| TC-FE12-044 | AC4 | Rejected and unknown refund | REJECTED refund; then id 99 | "Refund Rejected" with manager note; "Refund not found" | PASSED |
| TC-FE12-045 | AC4 | Queues | `/reception/refunds?memberId=2` | Member table: APPROVED row with "Record refund" to `/reception/refunds/7?memberId=2`, COMPLETED row "Refunded"; pending table lists Pending approval | PASSED |
| TC-FE12-050 | AC5 | Role guard | MEMBER opens the 5 routes | Access denied, no payment, refund or invoice request | PASSED (5 runs) |

### 3.2 FE-05 cases changed by this task (`receptionScreens.test.tsx`, `receptionModel.test.ts`)
| ID | Change | Result |
|---|---|---|
| TC-FE05-035 | Handoff now ends on Verify and Record Payment: Cash, 810000, tick, Confirm -> `PUT /payments/41/confirm` body `{paymentMethod: "CASH", paymentReference: null, amountReceived: 810000, note: null}`; "Payment Confirmed and Receipt"; Back to member profile shows the After Payment notice | PASSED |
| TC-FE05-047 | Pending tab row link opens Verify and Record Payment | PASSED |
| TC-FE05-019, TC-FE05-038 | Retired: the item check is no longer needed because the backend confirms by payment id | - |

## 4. Manual cases (real backend, after BE-18 and BE-21)
| ID | Case | Steps | Expected | Result |
|---|---|---|---|---|
| M01 | List and filter | Log in as receptionist, open Payment requests, filter Paid, search a member code | Matching rows, paging works | NOT RUN |
| M02 | Confirm a transfer | Member registers online; desk reviews, bank transfer with reference, exact amount, ticks, confirms | Payment SUCCESS, registration ACTIVE, invoice issued, receipt shows invoice number | NOT RUN |
| M03 | Confirm cash | Same with cash and no reference | SUCCESS, reference empty on the receipt | NOT RUN |
| M04 | Amount mismatch | Confirm with 10.000 less | Payment Needs Review; payment stays PENDING in the list | NOT RUN |
| M05 | Duplicate transfer reference | Confirm another payment with an already used reference | Backend error shown, no SUCCESS | NOT RUN |
| M06 | Mark as failed | Mark a pending payment failed with a reason | FAILED, removed from the pending list | NOT RUN |
| M07 | Card payment | Member requests a VIP card online; desk confirms | Service updated shows the card Active | NOT RUN |
| M08 | Print receipt | Open receipt, Print receipt | Print preview without the action buttons | NOT RUN |
| M09 | Create refund | Find member, start refund on a paid registration | Suggested amount from the quote; refund PENDING | NOT RUN |
| M10 | Manager approves | Manager approves in FE-13 or Swagger; desk opens the refund | Refund Approved state | NOT RUN |
| M11 | Complete refund | Type the transfer reference, record | COMPLETED, payment REFUNDED (BE-21) | NOT RUN |
| M12 | Role guard | Log in as a member and open `/reception/payments` | Access denied page | NOT RUN |
