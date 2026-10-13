# Test Cases - Task FE-11 (Flow 3): Member Payment Summary, Instructions and Status, My Payments and Receipts

## 1. Overview
- Task ID: FE-11
- Flow: Flow 3 - Payment and report management (Member)
- Branch: feat/f3-member-payments (from main after PR #39)
- Owner: Phat
- Priority: P1
- Depends on: FE-00 (main), BE-17 (main), BE-20 (not on main yet, see 1.1)
- Figma: `context/Flow 3 - Payment and report managmen/Flow 3 - Member/` - F3-01 Payment Summary, S3-Single Single Visit Payment Summary, F3-02 Payment Instructions & Status, F3-03 My Payments & Receipts, S3-PaidHistory My Payments - Updated, S3-CardReceipt, S3-MemberReceipt, S3-SingleReceipt
- Acceptance criteria:
  - AC1: the pending payment page updates by itself when the receptionist confirms.
  - AC2: copy buttons copy the right text.
  - AC3: the receipt prints on one A4 page without navigation.
  - AC4: the history filter works; empty state when there are no payments.

### 1.1 Backend contract used
| Call | State | Use |
|---|---|---|
| `GET /api/v1/payments/{id}` | main (BE-17) | Summary, status page, polling every 15 seconds while PENDING |
| `PUT /api/v1/payments/{id}/method` body `{paymentMethod}` | main (BE-17) | Method choice; member may choose BANK_TRANSFER, MOMO, VNPAY, ZALOPAY; 400 when the payment is not PENDING |
| `GET /api/v1/packages/registrations/my`, `GET /api/v1/membership-cards/my` | main | Item details on the summary (registration code, format, validity, card tier); a failure only hides these rows |
| `GET /api/v1/payments/my?status=` | BE-20, assumed | History list; a JSON array or a page `{content: [...]}`; `status` left out for All |
| `GET /api/v1/invoices/{id}` | BE-20, assumed | Receipt: `invoiceNumber`, `issueDate`, `memberFullName`, `memberCode`, `subtotalAmount`, `discountAmount`, `taxAmount`, `totalAmount`, `status`, `paymentCode`, `paymentMethod`, `paymentTime`, `lines[]` |
| `PaymentResponse.invoiceId`, `invoiceNumber` | BE-20, assumed | "View receipt" links; without them a paid payment shows "View details" |

Bank transfer instructions are only sent while the payment is PENDING and the method is BANK_TRANSFER (`PaymentServiceImpl.mapToPaymentResponse`); the transfer content is the payment code.

### 1.2 How to run (PowerShell)
```powershell
cd frontend
npm run lint
npx tsc -b
npm test
npm run build
```

### 1.3 Result summary (2026-10-13)
| Group | Cases | Result |
|---|---|---|
| Automated, rules (`src/pages/member/paymentsModel.test.ts`) | 9 | 9 PASSED |
| Automated, screens (`src/pages/member/memberPayments.test.tsx`) | 31 | 31 PASSED |
| Automated, all other files (FE-00 to FE-04) | 352 (TC-FE03-036 updated, see 5) | 352 PASSED |
| Lint (`--max-warnings 0`), `tsc -b`, build | 3 | 3 PASSED |
| Manual E2E against the real backend (section 6) | 12 | Not run (needs BE-20) |

Total: 392 tests in 20 files.

## 2. Automated - rules (paymentsModel.test.ts)
| ID | Case | Expected |
|---|---|---|
| TC-FE11-M01 | isPollingStatus | true only for PENDING |
| TC-FE11-M02 | Status label and badge tone | SUCCESS reads "Paid" with the green tone; unknown code kept |
| TC-FE11-M03 | Method labels | BANK_TRANSFER -> Bank transfer, MOMO -> MoMo, null -> Not chosen |
| TC-FE11-M04 | Status filter from URL | `pending` -> PENDING; `paid` or missing -> All |
| TC-FE11-M05 | Filter and sort | Only the chosen status; newest first, id breaks ties; input not mutated |
| TC-FE11-M06 | Copy values | "0000 1041 2460" -> "000010412460"; transfer content = payment code; 810000.00 -> "810000" |
| TC-FE11-M07 | Single visit | Registration of one day is a single visit |
| TC-FE11-M08 | Discount percent | 900000 / 90000 -> 10; no discount -> none |
| TC-FE11-M09 | Route ids and paths | Only positive whole numbers; status and receipt paths |

## 3. Automated - Payment Summary (F3-01, S3-Single)
| ID | AC | Case | Expected |
|---|---|---|---|
| TC-FE11-001 | - | Open `/member/payments/52` | Item title, PAY-0052, REG-0040, Coach-led, validity 02 Oct 2026 - 31 Oct 2026, 8 sessions; Original price 900.000, Discount / 10% -90.000, Total due 810.000 VND |
| TC-FE11-002 | - | One day registration | Title "Single Visit Payment Summary"; "0 VND - Single visits excluded" |
| TC-FE11-003 | - | Card payment | Card request CARD-0009, VIP Member |
| TC-FE11-004 | - | Continue with the current method | No PUT; opens Payment Instructions & Status |
| TC-FE11-005 | - | Choose MoMo, Continue | One `PUT /payments/52/method` with `{paymentMethod: "MOMO"}`; status page shows the reference to give at reception |
| TC-FE11-006 | - | PUT answers 400 | Backend message in an alert; stays on the summary |
| TC-FE11-007 | - | Paid payment with an invoice | No method choice; View receipt -> `/member/payments/receipts/7` |
| TC-FE11-008 | - | Sub navigation | Payment summary, Payment status, My payments links |
| TC-FE11-009 | - | Registration and card lists fail | Summary still shows prices; item rows left out |

## 4. Automated - Status (F3-02), My Payments (F3-03), Receipts
| ID | AC | Case | Expected |
|---|---|---|---|
| TC-FE11-010 | AC2 | Copy account number, transfer content, amount | Clipboard gets "0123456789", "PAY-0052", "810000"; toast "Copied amount" |
| TC-FE11-011 | AC2 | Account number with spaces | Shown "0000 1041 2460", copied "000010412460" |
| TC-FE11-012 | AC2 | Clipboard and fallback both fail | Toast "Could not copy the transfer content" |
| TC-FE11-013 | AC1 | PENDING, then SUCCESS at the next check | After 15 s the page shows "Payment confirmed", badge Paid, View receipt; bank details gone; no more requests after 45 s more |
| TC-FE11-014 | AC1 | Still PENDING | No request before 15 s; one request every 15 s |
| TC-FE11-015 | - | Refresh status | Immediate request; FAILED shows "Payment failed", Refresh gone, link to My membership |
| TC-FE11-016 | AC1 | One check fails (500) | Last payment stays, alert "Could not check the status"; next check recovers and the alert goes |
| TC-FE11-017 | - | ZaloPay, no bank details | Reference PAY-0052 with copy, "Choose another method" back to the summary |
| TC-FE11-018 | - | First load 404, Retry | Error with backend message; Retry loads the payment |
| TC-FE11-019 | - | `/member/payments/abc/status` | "The payment link is not valid."; no request |
| TC-FE11-020 | - | History list | Newest first; Pending -> Check status; paid with invoice -> INV-0007 and View receipt; failed -> View details |
| TC-FE11-021 | AC4 | Choose Pending in the filter | Request `/payments/my?status=PENDING`; only the pending row even if the backend ignores the filter; All brings back 3 rows |
| TC-FE11-022 | AC4 | `?status=success` in the URL | Select shows Paid (SUCCESS); request with `status=SUCCESS` |
| TC-FE11-023 | AC4 | No payments | Empty state "No payments yet" with Browse sport packages |
| TC-FE11-024 | AC4 | Filter with no match | "No refunded payments" |
| TC-FE11-025 | - | Page answer `{content: [...]}` | Rows shown |
| TC-FE11-026 | - | List error | Alert with the backend message and Retry |
| TC-FE11-027 | - | Menu | My Payments is active and shows the real screen |
| TC-FE11-030 | AC3 | Receipt INV-0007 | Receipt / date, Issued to, Service, Base price, Discount, Paid total, Payment, Status; Print receipt calls window.print once; button and sub navigation are marked no-print |
| TC-FE11-031 | - | Two invoice lines | Items list with type labels (Membership card, Single visit) |
| TC-FE11-032 | - | Invoice 404 | "We could not load the receipt" with the message |
| TC-FE11-033 | - | `/member/payments/receipts/x1` | "The receipt link is not valid."; no request |

## 5. Changed existing test
- TC-FE03-036 (`sportPackages.test.tsx`): the summary now has a "Payment details" region (reference, status) and an "Order summary" region (amount, method choice) as in Figma F3-01; the bank transfer details moved to the status screen (F3-02). The test checks the new regions and that Bank transfer is preselected.

## 6. Manual E2E against the real backend (after BE-20 is merged)
Precondition: backend with seed data, a member and a receptionist account, Chrome at the member area.

| ID | AC | Steps | Expected |
|---|---|---|---|
| TC-FE11-E01 | - | Register a 30 day package, Continue to payment | Summary shows original price, card discount and total from the backend |
| TC-FE11-E02 | - | Keep Bank transfer, Continue to payment | Status page: bank, account name, account number, transfer content = payment code, amount |
| TC-FE11-E03 | AC2 | Click each Copy button, paste into Notepad | Account number without spaces, payment code, amount as digits only |
| TC-FE11-E04 | AC1 | Leave the status page open; receptionist confirms the payment (`PUT /payments/{id}/confirm`) | Within 15 seconds the page shows Payment confirmed and a toast, without reloading |
| TC-FE11-E05 | AC1 | DevTools Network after E04 | No more `GET /payments/{id}` calls |
| TC-FE11-E06 | - | New request, choose MoMo, Continue | Method saved (Network shows PUT 200); page shows the reference to give at reception |
| TC-FE11-E07 | AC4 | My Payments, filter Pending, Paid, Failed, All | Only rows of that status; URL keeps `?status=` |
| TC-FE11-E08 | AC4 | Member with no payments opens My Payments | Empty state "No payments yet" |
| TC-FE11-E09 | AC3 | Open a receipt, Print receipt, print preview A4 | One page, no header, menu, payment tabs or buttons; receipt fully visible |
| TC-FE11-E10 | AC3 | Ctrl+P on the receipt (browser print) | Same result as E09 |
| TC-FE11-E11 | - | Single visit registration, open its payment | Single Visit Payment Summary with "Single visits excluded" |
| TC-FE11-E12 | - | Open another member's payment id | Backend 404, error state with Retry |
