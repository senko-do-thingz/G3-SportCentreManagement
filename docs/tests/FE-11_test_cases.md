# Test Cases - Task FE-11 (Flow 3): Member Payment Summary, Instructions and Status, My Payments and Receipts

## 1. Overview
- Task ID: FE-11
- Flow: Flow 3 - Payment and report management (Member)
- Branch: feat/f3-member-payments, rebased on main after PR #41 (FE-05, merge 6e82388)
- Owner: Phat
- Priority: P1
- Depends on: FE-00, BE-17, BE-20 (all on origin/main; this feature branch still needs an approved main update)
- Figma: folder `context/Flow 3 - Payment and report managmen/Flow 3 - Member/`, screens F3-01 (Payment Summary), S3-Single (Single Visit Payment Summary), F3-02 (Payment Instructions and Status), F3-03 (My Payments and Receipts), S3-PaidHistory, S3-CardReceipt, S3-MemberReceipt, S3-SingleReceipt
- Acceptance criteria:
  - AC1: the pending payment page updates by itself when the receptionist confirms.
  - AC2: copy buttons copy the right text.
  - AC3: the receipt prints on one A4 page without navigation.
  - AC4: the history filter works; empty state when there are no payments.

### 1.1 Backend contract used
| Call | State | Use |
|---|---|---|
| `GET /api/v1/payments/{id}` | main (BE-17) | Summary, status page, polling every 15 seconds while PENDING |
| `PUT /api/v1/payments/{id}/method` body `{paymentMethod}` | main (BE-17) | Switches a wallet payment to BANK_TRANSFER; 400 when the payment is not PENDING |
| `GET /api/v1/packages/registrations/my`, `GET /api/v1/membership-cards/my` | main | Item details on the summary (registration code, format, validity, card tier); a failure only hides these rows |
| `GET /api/v1/payments/my?status=&page=0&size=20` | BE-20, verified on origin/main | `PageResponse<MemberPaymentResponse>`: `content`, `page`, `size`, `totalElements`, `totalPages`; status omitted for All; zero-based page; size 1..100; order `createdAt DESC, id DESC` |
| `GET /api/v1/invoices/{id}` | BE-20, verified on origin/main | Receipt: `invoiceNumber`, `issueDate`, `memberFullName`, `memberCode`, `subtotalAmount`, `discountAmount`, `taxAmount`, `totalAmount`, `status`, `paymentCode`, `paymentMethod`, `paymentReference`, `paymentTime`, `lines[]` |
| `MemberPaymentResponse.invoiceId`, `invoiceNumber` | BE-20 history only | Authoritative history receipt links; GET `/payments/{id}` does not contain these fields, so summary/status use their existing history fallback when absent |

Bank transfer instructions are only sent while the payment is PENDING and the method is BANK_TRANSFER (`PaymentServiceImpl.mapToPaymentResponse`); the transfer content is the payment code. MoMo, VNPay and ZaloPay are accepted by the backend but have no online checkout, so the summary shows them disabled with "Not available yet" and Bank transfer is always preselected. Figma F3-01 has no method list at all.

### 1.2 How to run (PowerShell)
```powershell
cd frontend
npm ci
npm run lint -- --max-warnings 0
npx tsc -b
npm test
npm run build
```

### 1.4 Round 2 remediation (2026-10-15)

Verified against origin/main `d79a879f78f7663b8c6df3efaef9bf524aeb04de`, especially BE-20 `MemberPaymentController`, `ReceiptServiceImpl`, `MemberPaymentResponse` and `InvoiceResponse`, and BE-17 `PaymentServiceImpl`.

- P1-01 confirmed and fixed: keep page metadata, send status/page/size to the backend, use shared Table/Pagination, and preserve backend order. URL parameters survive reload and back/forward. Filter changes push a history entry and reset page to zero. Invalid links are replaced with canonical values; removed pages are replaced by the last valid page. Existing abort/loader tagging prevents late responses from replacing the current page.
- P2-01 rejected as an existing defect: only bank transfer is enabled. Wallet methods remain disabled with an explanation, with no simulated checkout.
- P2-02 confirmed for long content: original print output clipped long identifiers and silently reduced essential text to 7 pt. Wrap identifiers, omit duplicate descriptions and invoice headings in print, retain the payment reference, and use readable 10.5 pt rows. Multi-line receipts use the full width for items. A4 portrait and 15 mm margins remain scoped to member receipts.
- Additional confirmed amount defect: BE-17 accepts two decimals, while member payment formatting/copy rounded them away. All four member payment screens now retain up to two decimals; shared FE-05/FE-12 formatters are unchanged.
- Round 2 automated coverage: 72 FE-11 tests in three files (57 screen/rules tests plus 15 pagination/API cases). See the exported Round 2 Test Results for full-suite counts, command exits and browser evidence.
- Final frontend validation: 491 tests passed in 23 files, zero skipped; lint with zero warnings, TypeScript and production build passed. Edge 155.0.4283.45 printed all three final fixtures (normal, long single-line, six long invoice lines) as one A4 page each with complete text and 10.5 pt essential rows. Native browser history navigation and reload also passed with mocked BE-20 responses.
- Browser print verification: Edge headless, synthetic BE-20 responses, browser PDF output and rendered-page inspection. This does not verify a physical printer, Firefox, or an interactive system print dialog. Finite A4 space cannot guarantee a single sheet for an unbounded number of invoice lines; overflow must remain readable and retain all content.
- No backend changes. The checked-out branch does not contain BE-20 or FE-12; a later approved main update and integration review are required. No main merge/rebase was performed.

### 1.3 Result summary (2026-10-13, after review round 1)
| Group | Cases | Result |
|---|---|---|
| Automated, rules (`src/pages/member/paymentsModel.test.ts`) | 11 (2 new in round 1) | 11 PASSED |
| Automated, screens (`src/pages/member/memberPayments.test.tsx`) | 36 (5 new in round 1, renumbered with no gaps) | 36 PASSED |
| Automated, all other files (FE-00 to FE-05) | 399 (TC-FE03-036 updated) | 399 PASSED |
| Lint (`--max-warnings 0`), `tsc -b`, build | 3 | 3 PASSED |
| Manual E2E against the real backend (section 6) | 13 | Not run (needs BE-20) |

Total: 446 tests in 22 files. No flaky test was seen in this run.

## 2. Automated - rules (paymentsModel.test.ts)
| ID | Case | Expected |
|---|---|---|
| TC-FE11-M01 | isPollingStatus | true only for PENDING |
| TC-FE11-M02 | Status label and badge tone | SUCCESS reads "Paid" with the green tone; unknown code kept |
| TC-FE11-M03 | Method labels | BANK_TRANSFER -> Bank transfer, MOMO -> MoMo, null -> Not chosen |
| TC-FE11-M04 | Status filter from URL | `pending` -> PENDING; `paid` or missing -> All |
| TC-FE11-M05 | Page and size parsing | Nonnegative backend int page; size 1..100; invalid values -> page 0, size 20 |
| TC-FE11-M06 | Copy values and money | Account whitespace removed, transfer content preserved, 810000.00 -> "810000", 810000.25 -> "810000.25", fractional display retained |
| TC-FE11-M07 | Single visit | Registration of one day is a single visit |
| TC-FE11-M08 | Discount percent | 900000 and 90000 -> 10; no discount -> none |
| TC-FE11-M09 | Route ids and paths | Only positive whole numbers; status and receipt paths |
| TC-FE11-M10 | Available methods (new) | Only BANK_TRANSFER is available and is the default; MOMO, VNPAY, ZALOPAY are not |
| TC-FE11-M11 | Retryable errors (new) | Network error (status 0) and 5xx retry; 403, 404, stale session and a malformed answer do not |

## 3. Automated - Payment Summary (F3-01, S3-Single)
| ID | AC | Case | Expected |
|---|---|---|---|
| TC-FE11-001 | - | Open `/member/payments/52` | Item title, PAY-0052, REG-0040, Coach-led, validity 02 Oct 2026 - 31 Oct 2026, 8 sessions; Original price 900.000, Discount (10%) -90.000, Total due 810.000 VND |
| TC-FE11-002 | - | One day registration | Title "Single Visit Payment Summary"; "0 VND - Single visits excluded" |
| TC-FE11-003 | - | Card payment | Card request CARD-0009, VIP Member |
| TC-FE11-004 | - | Continue with the current method (Bank transfer) | No PUT; opens Payment Instructions and Status |
| TC-FE11-005 | - | Payment created with MoMo (changed) | Bank transfer preselected; MoMo, VNPay, ZaloPay disabled with the description "Not available yet"; clicking MoMo changes nothing; Continue sends one PUT with `{paymentMethod: "BANK_TRANSFER"}`; status page shows the bank details |
| TC-FE11-006 | - | Wallet payment, PUT answers 400 (changed) | Backend message in an alert; stays on the summary |
| TC-FE11-007 | - | Paid payment with an invoice | No method choice; "Paid with: Bank transfer"; View receipt -> `/member/payments/receipts/7` |
| TC-FE11-008 | - | Failed payment (new) | "Method: Bank transfer"; no "Paid with" |
| TC-FE11-009 | - | Sub navigation | Payment summary, Payment status, My payments links |
| TC-FE11-010 | - | Registration and card lists fail | Summary still shows prices; item rows left out |

## 4. Automated - Payment Instructions and Status (F3-02)
| ID | AC | Case | Expected |
|---|---|---|---|
| TC-FE11-011 | AC2 | Copy account number, transfer content, amount | Clipboard gets "0123456789", "PAY-0052", "810000"; toast "Copied amount" |
| TC-FE11-012 | AC2 | Account number with spaces | Shown "0000 1041 2460", copied "000010412460" |
| TC-FE11-013 | AC2 | Clipboard and fallback both fail | Toast "Could not copy the transfer content" |
| TC-FE11-014 | AC1 | PENDING, then SUCCESS at the next check | After 15 s the page shows "Payment confirmed", badge Paid, View receipt; bank details gone; no more requests in the next 45 s |
| TC-FE11-015 | AC1 | Still PENDING | No request before 15 s; one request every 15 s |
| TC-FE11-016 | - | Refresh status | Immediate request; FAILED shows "Payment failed", Refresh gone, link to My membership |
| TC-FE11-017 | AC1 | One check answers 500 | Last payment stays, alert "Could not check the status"; next check recovers and the alert goes |
| TC-FE11-018 | AC1 | One check answers 403 (new) | Alert "Access denied", text "Automatic checks stopped"; no request in the next 45 s; Refresh status sends one request, the alert goes and polling starts again |
| TC-FE11-019 | - | Unmount (new) | No request after the page is left, even after 60 s |
| TC-FE11-020 | - | Refresh status while an automatic check is running (new) | Two clicks send nothing while the check runs; after it answers, Refresh sends exactly one request |
| TC-FE11-021 | - | ZaloPay, no bank details (changed) | Reference PAY-0052 with copy; text "ZaloPay has no online checkout yet"; "Switch to bank transfer" back to the summary |
| TC-FE11-022 | - | First load 404, Retry | Error with backend message; Retry loads the payment |
| TC-FE11-023 | - | `/member/payments/abc/status` | "The payment link is not valid."; no request |

## 5. Automated - My Payments and Receipts (F3-03, S3-PaidHistory) and receipts
| ID | AC | Case | Expected |
|---|---|---|---|
| TC-FE11-024 | - | History list | Preserve backend order; Pending -> Check status; paid with invoice -> INV-0007 and View receipt; failed -> View details |
| TC-FE11-025 | AC4 | Choose Pending in the filter | Request `/payments/my?status=PENDING&page=0&size=20`; server-filtered content and totals; All brings back 3 rows |
| TC-FE11-026 | AC4 | `?status=success` in the URL | Select shows Paid (SUCCESS); canonical URL; request with `status=SUCCESS`, page and size |
| TC-FE11-027 | AC4 | No payments | Empty state "No payments yet" with Browse sport packages |
| TC-FE11-028 | AC4 | Filter with no match | "No refunded payments" |
| TC-FE11-029 | - | Complete PageResponse | Rows and metadata retained; incomplete metadata rejected by new API cases |
| TC-FE11-030 | - | List error | Alert with the backend message and Retry |
| TC-FE11-031 | - | Menu | My Payments is active and shows "My Payments and Receipts" |
| TC-FE11-032 | AC3 | Receipt INV-0007 | Receipt / date, Issued to, Service, Base price, Discount, Paid total, Payment, Status; Print receipt calls window.print once; button and sub navigation are marked no-print |
| TC-FE11-033 | - | Issued to without a member name (new) | Only the member code; never the signed in account name; "-" when both are missing |
| TC-FE11-034 | - | Two invoice lines | Every item appears once with type, quantity and total; no duplicate Service summary |
| TC-FE11-035 | - | Invoice 404 | "We could not load the receipt" with the message |
| TC-FE11-036 | - | `/member/payments/receipts/x1` | "The receipt link is not valid."; no request |

### 5.1 Changed existing test
- TC-FE03-036 (`sportPackages.test.tsx`): the summary has a "Payment details" region (reference, status) and an "Order summary" region (amount, method choice) as in Figma F3-01; the bank transfer details are on the status screen (F3-02).

### 5.2 Round 2 regression coverage

`paymentHistory.test.tsx` adds 15 cases: 45 payments across first/middle/last pages; filter resets; back/forward; status/page/size bookmarks; four invalid query combinations; out-of-range and empty correction; error/retry/loading; abort and late response; exact server order and invoice association; three malformed metadata responses. Each request fetches only one page.

`memberPayments.test.tsx` adds 10 cases: summary 403/404 privacy, fractional summary/copy, FAILED/REFUNDED polling stops, in-flight abort, cross-tab member switch with a late private response, exact invoice reference/decimals/items, and receipt ownership 403. Existing 36 screen tests remain in place. `paymentsModel.test.ts` retains 11 cases and replaces obsolete client sorting coverage with backend URL limits.

## 6. Manual E2E against a deployed backend with BE-20
Precondition: backend with seed data, a member and a receptionist account, Chrome at the member area.

These 13 live-backend/manual cases were not executed in Round 2. Browser PDF checks used synthetic responses; backend contracts were inspected at the exact origin/main revision above.

| ID | AC | Steps | Expected |
|---|---|---|---|
| TC-FE11-E01 | - | Register a 30 day package, Continue to payment | Summary shows original price, card discount and total from the backend |
| TC-FE11-E02 | - | Bank transfer is preselected, wallets are greyed out with "Not available yet"; Continue to payment | Status page: bank, account name, account number, transfer content (the payment code), amount |
| TC-FE11-E03 | AC2 | Click each Copy button, paste into Notepad | Account number without spaces, exact payment code, plain ungrouped amount retaining any decimal part |
| TC-FE11-E04 | AC1 | Leave the status page open; receptionist confirms the payment (`PUT /payments/{id}/confirm`) | Within 15 seconds the page shows Payment confirmed and a toast, without reloading |
| TC-FE11-E05 | AC1 | DevTools Network after E04 | No more `GET /payments/{id}` calls |
| TC-FE11-E06 | AC1 | Sign in as another member and open the first member's status URL | 404 shown once, automatic checks stopped, no repeated requests |
| TC-FE11-E07 | AC4 | More than 20 payments; navigate pages, filter Pending, Paid, Failed, All; browser back/forward and reload | Server-filtered pages; URL keeps status/page/size; status changes reset page |
| TC-FE11-E08 | AC4 | Member with no payments opens My Payments | Empty state "No payments yet" |
| TC-FE11-E09 | AC3 | Open a receipt, Print receipt, print preview | One A4 page, no header, menu, payment tabs or buttons; receipt fully visible |
| TC-FE11-E10 | AC3 | Ctrl+P on the receipt (browser print) | Same result as E09 |
| TC-FE11-E11 | AC3 | Reception: print the FE-05 receipt (`/reception/payments/{id}`) | Unchanged by FE-11: the member identity block still prints, page size and margins are the browser default |
| TC-FE11-E12 | - | Single visit registration, open its payment | Single Visit Payment Summary with "Single visits excluded" |
| TC-FE11-E13 | - | Open another member's payment id | Backend 404, error state with Retry |
