# Test Cases - FE-12 Receptionist Payments, Receipts and Refunds

## 1. Scope and provenance
- Task: FE-12, review round 3, 2026-10-15.
- Branch: feat/f3-reception-payments; starting HEAD c0e9070fe22bdc3c129a0a2e1b010a23ce640f9c.
- Current main 5176017593263b8223a7de71095d65a24597e2b6 is already an ancestor; no merge or rebase was needed.
- Task specification: G3_WhatToDo_Flow1-3.xlsx, Prompts EN, FE-12 row; acceptance map FE-12-1a to FE-12-4a.
- Screens: F3-04, F3-05, F3-06, F3-07, S3-Mismatch, S3-Receipt, S3-RefundSubmitted, S3-RefundApproved, S3-RefundDone.
- Existing receptionist routes and authentication are preserved. Backend staff authorization is RECEPTIONIST/MANAGER where noted; the reception UI retains its existing RECEPTIONIST role area.
- BE-18 and BE-21 are on main. The remaining dependency is staff-wide refund queue/detail discovery, not the payment endpoints.

## 2. Verified backend compatibility
Paths below are relative to /api/v1. Current Java controllers/DTOs/services are authoritative.

| Operation | Method and path | Request | Response | Backend roles / result |
|---|---|---|---|---|
| Payment list | GET /payments | status, query (alias of search), page, size | PageResponse<StaffPaymentResponse> | RECEPTIONIST/MANAGER; 200; 400 invalid filters |
| Payment detail | GET /payments/{id} | None | PaymentResponse; staff list enrichment is optional in the UI | MEMBER owner, RECEPTIONIST/MANAGER; 200/404 |
| Confirm | PUT /payments/{id}/confirm | paymentMethod, paymentReference, amountReceived, note | StaffPaymentResponse SUCCESS | RECEPTIONIST/MANAGER; 200; 400 validation; 409 coded conflicts |
| Fail | PUT /payments/{id}/fail | reason, nonblank, max 255 | StaffPaymentResponse FAILED | RECEPTIONIST/MANAGER; 200/400/404/409 |
| Receipt | GET /invoices/by-payment/{id} | None | InvoiceResponse with lines, amounts, status and member/payment data | MEMBER owner, RECEPTIONIST/MANAGER; 200/403/404 |
| Fresh card status | GET /membership-cards/member/{id} | None | MemberCardResponse[] | RECEPTIONIST/MANAGER; 200 |
| Fresh registration status | GET /packages/registrations/member/{id} | None | PackageRegistrationResponse[] | RECEPTIONIST/MANAGER; 200 |
| Member search | GET /members | query, page, size | Page<MemberSearchResponse> | RECEPTIONIST/MANAGER; 200 |
| Refund quote | GET /refunds/quote | registrationId | RefundQuoteResponse | MEMBER owner, RECEPTIONIST/MANAGER; 200/400/403/404/409 |
| Create refund | POST /refunds | packageRegistrationId, amountRequested, reason | RefundResponse PENDING | MEMBER owner, RECEPTIONIST/MANAGER; 201/400/403/404/409 |
| Member refunds | GET /refunds/member/{id} | None | RefundResponse[], all statuses, no pagination | RECEPTIONIST/MANAGER; 200 |
| Complete refund | PUT /refunds/{id}/complete | No body | RefundResponse COMPLETED plus cancelledBookings | RECEPTIONIST/MANAGER; 200/400/404/409 |
| Global pending | GET /refunds/pending | None | RefundResponse[] | MANAGER only; reception does not call it |
| Global approved | GET /refunds?status=APPROVED | Not implemented | No contract | BLOCKED: backend approval needed for a global queue |
| Refund by id | GET /refunds/{id} | Not implemented | No contract | Member context is required; unsupported calls are not made |

ConfirmPaymentRequest explicitly aliases paymentMethod -> method and paymentReference -> referenceCode. P1-02 is rejected; no backend DTO change or payload rename is needed. References are required for all methods except CASH. Note max 255, reference max 100, received amount positive with at most 10 integer digits and 2 decimals. Empty note/reference for cash serialize as null.

BE-18 requires exact received amount for all six methods. FE-05/BE-19 desk sales retain cash overpayment/change. Payment mismatches are identified only by HTTP 409 plus PAYMENT_AMOUNT_MISMATCH. The current backend provides the machine-readable code and message, without amount details; the UI preserves its message and uses the entered/loaded amounts when details are absent.

Quotes provide paymentAmount as the cap, suggestedAmount as the default, and unusedSessions for the zero-session guard. They do not provide member identity, prior-refund totals, invoice number, maxRefundable or eligibility flags. Refund creation requires ACTIVE registration and unused sessions; EXPIRED registrations are not offered. Completion accepts no body and does not store a payout reference or expose completedAt.

## 3. Executed automated verification
All frontend screen/transport tests use isolated fetch fixtures matching the source contract. They do not prove a deployed backend or SQL Server integration. Backend tests exercise the actual Java DTO/controller/service implementation with its existing unit/MVC fixtures.

| Check | Result | Passed | Failed | Skipped |
|---|---|---:|---:|---:|
| Targeted FE-12 and FE-05 tests, after initial parser fix | PASS | 119 | 0 | 0 |
| Full frontend suite, 24 files | PASS | 496 | 0 | 0 |
| FE-12 API transport, included in full suite | PASS | 9 | 0 | 0 |
| FE-12 model, included in full suite | PASS | 23 | 0 | 0 |
| FE-12 screens, included in full suite | PASS | 45 | 0 | 0 |
| FE-05 model/screens, included in full suite | PASS | 45 | 0 | 0 |
| Backend PaymentVerificationControllerTest | PASS | 19 | 0 | 0 |
| Backend PaymentVerificationServiceImplTest | PASS | 34 | 0 | 0 |
| Backend RefundQuoteControllerTest | PASS | 17 | 0 | 0 |
| Lint with --max-warnings 0, TypeScript, production build | PASS | N/A | N/A | N/A |

Initial targeted run: 109 passed, 1 failed, 0 skipped. The failing sub-cent parser case was fixed; its assertion was retained. An initial TypeScript attempt ran before dependency installation completed and selected an unavailable local compiler; final checks used the installed project compiler and passed. Full commands, exit codes and warnings are in the local remediation report.

## 4. Regression cases
| Area / cases | Verified expectations |
|---|---|
| TC-FE12-001/002 | Pending default, backend status values, URL filters/search/page and pending total |
| TC-FE12-003/004/005/015 | Required reference, note/reference lengths, exact cash/non-cash amount, checkbox, trimmed serialized aliases/nulls |
| Round 3 method matrix | CASH, BANK_TRANSFER, CREDIT_CARD, MOMO, VNPAY, ZALOPAY: exact accepted; short/over/invalid/missing/sub-cent/over-limit and unchecked blocked |
| Two-decimal transport | Decimal values survive JSON; grouped whole VND still works; 0.001 is rejected; fractional amounts are displayed without whole-VND rounding |
| TC-FE12-006/007/008/016 | Correct difference; invalid due cannot produce NaN; only coded 409 is mismatch; message/details retained |
| TC-FE12-020/021/022 | Payment list, paging, stable URL filters, error with Retry; no receptionist manager-only pending request |
| TC-FE12-030/031/032/033 | Form gating, receipt after success, mismatch display, preserved form and checkbox reset on return |
| TC-FE12-034/035 | Valid fail reason; alias and canonical backend validation fields mapped correctly |
| TC-FE12-036 and round 3 receipt | Invoice loading/error/retry, direct reload fetch, backend totals, separate fresh item status and Print blocked until the invoice loads |
| TC-FE12-037/038/039/048 | Under/over payments never send a confirmation; cash over now requires review; invalid due blocks actions |
| TC-FE12-009/010/011/012/013 | Paid-item matching, positive precision/cap/reason rules, refund stages, ACTIVE-only picker and actual quote mapping |
| TC-FE12-040/041 | Member selection, quote and creation; above-cap, backend eligibility rejection and zero unused sessions cannot submit |
| TC-FE12-042/043/044 | Payout confirmation required; completion without body, refresh to COMPLETED; rejection remains APPROVED; rejected manager note and genuine not-found state |
| TC-FE12-045 | One member fetch; APPROVED only in approved, PENDING only in pending, REJECTED/COMPLETED only in history; detail links retain memberId |
| TC-FE12-046/047/049 | Missing member context explained; supported API failure shown as error, not empty/not-found; Retry recovers |
| TC-FE12-R3-01/02 | Empty member queues; direct/reloaded detail resolves every status from URL memberId |
| Round 3 mutation/race | Duplicate confirm/completion prevented; rejection never shows optimistic completion; late member response ignored after changing context |
| TC-FE12-050 | MEMBER cannot access the five reception routes and sends no payment/refund/invoice request |
| TC-FE05-003/041 | Existing cash overpayment and change behavior remains unchanged and passes |

Changes to prior tests correct unsupported contract assumptions. No test was skipped, disabled or weakened to permit an incorrect backend interaction.

## 5. Manual and pending verification
| Case | Status / next action |
|---|---|
| Real authenticated payment confirmation, mismatch persistence and invoice/activation | NOT RUN against a deployed backend; Java unit/MVC and frontend fixture tests passed |
| Real refund quote/create/manager review/complete/rollback | NOT RUN end to end; no local Docker or SQL Server integration run |
| Staff-wide approved/pending discovery and detail without memberId | BLOCKED pending backend API/security approval; member-scoped workflow is implemented |
| Payout-reference persistence and completion time in refund response | Unsupported by current backend; not claimed by the UI; backend/product approval required if retained as requirements |
| Print preview, responsive layout and keyboard-only browser session | NOT RUN visually; receipt-only print rules added and Print waits for the invoice |
| FE-11 cross-branch merge/runtime | NOT VERIFIED; FE-11 implementation is not in this tree; member/auth/routing source unchanged and current member tests pass |
| Existing FE-12 AI Audit Log | NOT UPDATED: no existing file found; its path was requested. Do not create a duplicate or update another task's log |

The complete local remediation report records the remaining dependency and the exact final recommendation. No commit, push, merge, rebase, reset or history rewrite was performed.
