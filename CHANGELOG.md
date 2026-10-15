# Changelog

## 2026-10-15 - Financial overview, transactions and revenue reports (BE-22, Flow 3)

Branch `feat/f3-revenue-reports`, based on `feat/f3-confirm-payment` (BE-18). Owner: Truc. Priority: P2. Depends on BE-18. Screens F3-08, F3-09, F3-10 and S3-Export.

### Added
- Migration `V25__create_finance_report_views.sql`: views `vw_payment_ledger` (every payment with member, invoice, item and sport) and `vw_paid_payment` (SUCCESS only, the revenue source), index `ix_payment_status_time`. V23 and V24 are taken by `feat/f2-sessions-schedule` and `feat/f3-refund-payment-link`.
- `ManagerFinanceController` (MANAGER only, other roles 403):
  - `GET /api/v1/manager/finance/overview?month=yyyy-MM`: paid revenue, paid transactions, average payment, pending amount, refunded amount, comparison with the previous month.
  - `GET /api/v1/manager/finance/trend?months=6`: revenue per month (1 to 24 months), zero months included.
  - `GET /api/v1/manager/finance/transactions?status=&method=&type=&from=&to=&search=&page=&size=`: ledger with every status.
  - `GET /api/v1/manager/finance/revenue?from=&to=&groupBy=ITEM_TYPE|METHOD|SPORT`: totals with share percent that add up to exactly 100.00.
  - `GET /api/v1/manager/finance/revenue/export?from=&to=`: CSV, UTF-8 with BOM, header row, CRLF, formula-safe text cells.
- `FinanceReportService` / `FinanceReportServiceImpl`, `FinanceReportRepository` (native SQL with NamedParameterJdbcTemplate), DTOs in `payment.dto.finance`.
- Tests: `FinanceReportServiceImplTest` (19 tests, 24 runs), `ManagerFinanceControllerTest` (11 tests, 20 runs), `FinanceReportIntegrationTest` (6, Testcontainers, known payments in March 2001); test cases in `docs/tests/BE-22_test_cases.md`.

### Changed
- `docs/database/04`: the planned view is now V25; endpoint rules; revenue by item type sums `payment.amount`, not `invoice_line.line_total` (a line holds the price before the card discount since BE-18).

### Notes
- Revenue counts only SUCCESS payments by `payment_time`; a payment later REFUNDED leaves revenue, and the refund is shown in `refundedAmount` (COMPLETED refund requests by `updated_at`, since `refund_request` has no `completed_at`).
- Excel with a Vietnamese regional format uses ";" as list separator and may put every column in A on double-click; the manual check in the test cases shows the import path.
- Not verified in the authoring environment (Maven Central and the SQL Server image were blocked there). Run `mvn -B test` before merging; integration tests run in CI.

### CI fix 1
- "Project conventions and workflow lint" failed: `scripts/ci/check-conventions.sh` rejects accented characters in added Java lines, and the tests held Vietnamese names as plain text. The Vietnamese test data in `FinanceReportIntegrationTest`, `FinanceReportServiceImplTest` and `ManagerFinanceControllerTest` is now written with Unicode escapes; the values and the assertions are unchanged. No production code changed.
## 2026-10-15 - Refunds linked to payments (BE-21, Flow 3)

Branch `feat/f3-refund-payment-link`, based on `feat/f3-confirm-payment` (BE-18). Owner: Truc. Priority: P2. Depends on BE-18. Screens F3-07, F3-09 (refund part), S3-RefundSubmitted, S3-RefundReview, S3-RefundApproved, S3-RefundRejected, S3-RefundDone.

### Added
- `GET /api/v1/refunds/quote?registrationId=` (owner MEMBER, RECEPTIONIST, MANAGER): suggested amount = paid amount * unused sessions / total sessions (half up, 2 decimals, never above the payment amount). Unused sessions = remaining sessions + upcoming CONFIRMED bookings of the registration without an allowed check-in. Every session used gives 0.00.
- `POST /api/v1/refunds` accepts an optional `amountRequested` (default: the quote), validated above 0, at most 2 decimals and not above the payment amount.
- `PaymentRefundService.markRefunded`: payment SUCCESS -> REFUNDED with a note, PAID or ISSUED invoices -> REFUNDED. 409 codes `PAYMENT_ALREADY_REFUNDED` and `PAYMENT_NOT_REFUNDABLE`.
- Migration `V24__refund_request_payment_link.sql`: filtered unique index `ux_refund_request_payment_live` (one PENDING, APPROVED or COMPLETED refund per payment) with a duplicate guard, and an index on `package_registration_id`. V23 is taken by `feat/f2-sessions-schedule`.
- Audit action `REFUND_COMPLETED`; `RefundResponse` gains `paymentId`, `paymentCode` and, on complete, `cancelledBookings`.
- Tests: `RefundPaymentLinkTest` (22 tests, 24 runs), `PaymentRefundServiceImplTest` (4 tests, 5 runs), `RefundQuoteControllerTest` (9 tests, 11 runs), `RefundPaymentLinkIntegrationTest` (4, Testcontainers); test cases in `docs/tests/BE-21_test_cases.md`.

### Changed
- Submitting a refund requires a SUCCESS payment linked to the registration and stores `refund_request.payment_id`. A second refund on the same payment answers 409 `PAYMENT_ALREADY_REFUNDED`, also after approval when the registration is already REFUNDED; a REJECTED request does not block a new one. A race on the same payment is caught by the unique index and mapped to the same 409.
- Review: `amountApproved` must be above 0 and not above the payment amount. Approval still moves the registration to REFUNDED (kept as it was).
- Complete runs in one transaction: payment REFUNDED, invoices REFUNDED, registration REFUNDED (if not already), upcoming CONFIRMED bookings of the registration CANCELLED with the seat released (sessions are not returned to the refunded registration), refund COMPLETED, REFUND_COMPLETED activity log. Legacy refunds without a payment complete without the payment step.
- Lock order follows BE-18: submit locks the payment before reading the registration; review and complete lock the payment, then the refund row.
- The pro-rated amount multiplies before dividing (the old code rounded the fraction to 4 decimals first, which could be off by a few dong).
- `RefundServiceImplTest`: fixtures give the registration a SUCCESS payment and stub the new locked reads; assertions are unchanged.
- `docs/database/04`: refund rules.

### Notes
- Not verified in the authoring environment (Maven Central and the SQL Server image were blocked there). Run `mvn -B test` before merging; integration tests run in CI.
- Existing `refund_request` rows keep `payment_id` NULL (no backfill), so the one-refund rule applies to refunds created from now on.

### Review round 1 (PR #58)
- Merged origin/main into the branch (BE-18 final, BE-19, BE-20 and BE-10 with V23); conflicts were only BE-18 files already on main, resolved to main, and the BE-21 change set was applied unchanged on top. The PR base should be `main`.
- P0-01 (V24 extra END): not confirmed. V24 has one BEGIN and one END in the same IF ... BEGIN THROW END; form as the applied V21; it was never merged or deployed. V24 is unchanged; `FlywayMigrationTest` runs it on SQL Server in CI.
- P1-02: refund completion now follows the BE-08 and BE-10 lock order. It locks the sessions of the upcoming bookings in ascending id order with `ClassSessionRepository.findForUpdate`, re-reads the bookings under those locks, cancels each session's bookings with one conditional UPDATE (`BookingRepository.cancelConfirmedSessionBookings`) and releases the seats with one atomic UPDATE (`ClassSessionRepository.releaseSeats`, never below 0). The registration is written after the sessions and bookings. If the bookings change between the read and the update, the call answers 409 `REFUND_BOOKINGS_CHANGED` and rolls back.
- P1-03: quote, submit, approve and complete check that the payment pays only for the registration: it is the registration's `payment_id`, it belongs to the same member, no membership card uses it, and every invoice line of the payment bills this registration (SPORT_PACKAGE). Otherwise 409 `PAYMENT_REGISTRATION_MISMATCH`. No schema change.
- P2-01: `BookingRepository.findRefundableUpcomingBookings` returns booking and session ids of the upcoming bookings with the ALLOWED check-in filter as NOT EXISTS, in one query; the per-booking `CheckInRepository` call is gone from the refund service.
- Tests: `RefundPaymentLinkTest` +11 tests (R1-U01 to R1-U11, with U04 and U20 rewritten for the set-based query and the bulk updates), `RefundPaymentLinkIntegrationTest` +5 SQL Server tests (I05 to I09, including two concurrency regressions); the class is added to `scripts/ci/required-integration-tests.txt`. `RefundServiceImplTest` fixtures adapted, assertions unchanged.
- Not executed in the authoring environment (Maven Central blocked there): counts must come from `mvn -B test` and the GitHub Actions run.

## 2026-10-15 - My payments and receipts (BE-20, Flow 3)

Branch `feat/f3-my-payments-r1`, based on main (`5075010`, BE-18 from PR #60 and BE-19 from PR #62). Replaces PR #56, whose branch carried the superseded BE-18 commits. Owner: Truc. Priority: P1. Depends on BE-18. Screens F3-03, S3-PaidHistory, S3-CardReceipt, S3-MemberReceipt and S3-SingleReceipt. Provides the BE-20 endpoints FE-11 (`feat/f3-member-payments`) already calls; the response shapes match the contract FE-11 assumed.

### Added
- `GET /api/v1/payments/my?status=&page=&size=` (MEMBER only): the signed-in member's payments, newest first (`created_at`, then `id`), page size 1 to 100 (default 20). Each row: id, code, status, amount, method, item type, reference id, code and description, original price, discount, `time` (payment time once confirmed, otherwise request time), `paymentTime`, `createdAt`, member id and code, `invoiceId` and `invoiceNumber` (null until the receipt is issued). Staff get 403.
- `GET /api/v1/invoices/{id}` (MEMBER, RECEPTIONIST, MANAGER): invoice header (number, issue date, status, member name and code, payment code, method, reference and time, subtotal, discount, tax, total) and lines in insert order.
- `ReceiptService` / `ReceiptServiceImpl`, `MemberPaymentController`, `MemberPaymentResponse`, `InvoiceResponseMapper`; `PaymentRepository.findByMemberId` and `findByMemberIdAndPaymentStatus` with paging.
- Tests: `ReceiptServiceImplTest` (19 tests, 22 runs), `ReceiptControllerTest` (12 tests, 16 runs), `ReceiptIntegrationTest` (4, Testcontainers); test cases in `docs/tests/BE-20_test_cases.md`.

### Changed
- `GET /api/v1/invoices/by-payment/{paymentId}` moved from `PaymentVerificationService` to `ReceiptService`. A member asking for the invoice of another member's payment, or for another member's invoice by id, gets 403 (BE-18 answered 404), as the approved BE-20 acceptance criterion requires. Staff and owner answers are unchanged. `GET /api/v1/payments/{id}` and `PUT /api/v1/payments/{id}/method` still answer 404 for another member (BE-17, not changed). The moved BE-18 unit test lives in `ReceiptServiceImplTest`.
- Reading a receipt re-checks total = sum of line totals - discount + tax (and subtotal = sum of line totals). Invoices are already validated when issued (BE-18), so a stored invoice that breaks the rule is returned as recorded and logged as a warning.
- `docs/database/04`: BE-20 endpoints; Visibility now separates payments (404) from invoices (403); the BE-18 pricing rules from PR #60 are kept as they are. `docs/database/09`: the desk sale visibility row is split the same way.

### Review round 1 (PR #56)
- Rebased: the branch starts from the latest main and carries only the BE-20 change of `eb5609e`; every BE-18 and BE-19 change on main is kept.
- `PaymentVerificationIntegrationTest.memberSeesOnlyOwnPaymentAndInvoice` (BE-19) now expects 403 for another member on `/invoices/by-payment/{paymentId}`; its 404 check for `/payments/{id}` is unchanged.
- `com.sportify.payment.ReceiptIntegrationTest` added to `scripts/ci/required-integration-tests.txt`, so CI fails if it does not run.
- `ReceiptServiceImplTest`: two tests for the totals check on read (a stored invoice that does not add up is returned unchanged and logs one warning; one that adds up logs none).

### Notes
- No migration.
- Maven Central and the SQL Server image are blocked in the authoring environment; unit tests are run locally with `mvn -B test` and the Testcontainers tests in GitHub Actions CI. Expected against main `5075010`: unit 874 (837 - 1 moved + 38), integration 194 (190 + 4).

## 2026-10-14 - Payment at the desk (BE-19, Flow 3)

Branch `feat/f3-desk-payment`. Owner: Truc. Priority: P1. Depends on BE-18 (merged in PR #60; main is merged into this branch). Screens F1-13 Payment Handoff and F1-14 Register and Renew. Provides the BE-19 contract FE-05 already sends.

### Added
- Desk sale records the money at once: `POST /api/v1/membership-cards/purchase` and `POST /api/v1/packages/registrations` (channel `RECEPTION`) by RECEPTIONIST or MANAGER accept `paymentMethod` (alias `method`, `CASH` when omitted), `referenceCode` (aliases `paymentReference`, `reference`; required except for `CASH`) and `amountReceived` (the price when omitted). A `SUCCESS` payment is created and linked, the item is `ACTIVE`, and the BE-18 receipt step `PaymentVerificationService.recordPaymentConfirmed` issues the `PAID` invoice and writes `PAYMENT_CONFIRMED`.
- Cash may be more than the price (the change is written in the payment notes); less money, or a non-cash amount that differs, answers 409 `PAYMENT_AMOUNT_MISMATCH` and nothing is saved. Nothing to pay (STANDARD tier, zero fee upgrade, full discount) creates no payment.
- `MemberCardResponse` and `PackageRegistrationResponse` carry `invoiceId` and `invoiceNumber` (with the existing `paymentId`) after a desk sale or an activation, so the screen opens the receipt (F1-13 State After Payment).
- `PaymentService.recordDeskPayment` and `PaymentVerificationService.recordDeskPayment`; tests in `PaymentVerificationServiceImplTest` (+7), `MembershipCardServiceImplTest` (+2), `SportPackageServiceImplTest` (+2) and `PaymentVerificationIntegrationTest` (+4); test cases in `docs/tests/BE-19_test_cases.md`.

### Changed
- Reference validation of confirm and desk sale shares one helper; payment amount validation of online and desk payments shares one helper.
- `docs/database/04` and `09`: desk sale rules, visibility, invoice totals rule.
- CI fix: the desk sale integration tests expect 201 Created (the purchase and registration endpoints answer 201), and the test cleanup deletes every row of the members it created, so a failing test can no longer leave sports, packages or tiers behind for other test classes.

### Review round 1
- P1-01: `DeskSaleRollbackIntegrationTest` (SQL Server) forces the receipt step to fail after the payment and the card or registration were written (invoice save for a package and a card sale, and the PAYMENT_CONFIRMED audit write after the invoice and its line were saved) and checks that no payment, card, registration, invoice, invoice line or activity_log row is left.
- P1-02: the 403 change for another member's payment, payment method change and invoice is reverted. No approved specification asked for it, and BE-17 and BE-18 document 404 (the resource is not revealed); the responses are 404 again and the BE-17 and BE-18 tests are restored unchanged.
- P2-01: HTTP integration tests for a non-cash desk sale with a different amount (bank transfer with more for a package, credit card with less for a card): 409 `PAYMENT_AMOUNT_MISMATCH` and no item, payment, invoice, invoice line or activity_log row.
- `PaymentVerificationIntegrationTest` and `DeskSaleRollbackIntegrationTest` are added to `scripts/ci/required-integration-tests.txt`, so CI fails if they do not run.
- Merged main (final BE-18 from PR #60 and BE-10 from PR #51); the BE-19 change set is applied unchanged on top of it.
- CI fix (PR #62): the three `DeskSaleRollbackIntegrationTest` tests failed in their setup with `detached entity passed to persist: UserAccount`, because `newMember` saved the account and the `@MapsId` profile in two transactions. Both are now saved in one transaction, as in `PaymentVerificationIntegrationTest`. Test helper only; no production code or assertion changed.

## 2026-10-14 - Receptionist verifies and confirms payments (BE-18, Flow 3)

Branch `feat/f3-confirm-payment`. Owner: Truc. Priority: P1. Depends on BE-17 (merged). Screens F3-04, F3-05, F3-06 and S3-Mismatch. Provides the BE-18 part of what FE-12 (`feat/f3-reception-payments`) needs; FE-12 is also blocked by BE-21.

### Added
- `GET /api/v1/payments` (RECEPTIONIST, MANAGER): payment requests with filters `status`, `method`, `search` (`query` also accepted), `from`, `to`, `page`, `size` (max 100); PENDING first, oldest first, then newest first; each row has member name and code, item type, code and status, original price, discount and invoice number.
- `PUT /api/v1/payments/{id}/confirm`: `method`, `referenceCode` (required except for CASH, 400 otherwise), `amountReceived`, `note`. Matching amount -> payment SUCCESS with payment_time, card or registration ACTIVE with dates recomputed from today, one PAID invoice with one line (price before discount, card discount or upgrade credit as `discount_amount`, total = payment amount), activity_log PAYMENT_CONFIRMED. Different amount -> 409 `PAYMENT_AMOUNT_MISMATCH`, payment stays PENDING, only a note is committed. Not PENDING -> 409 `PAYMENT_NOT_PENDING`; a concurrent second confirmation fails on the payment `@Version` (409).
- `PUT /api/v1/payments/{id}/fail` with `reason`: cancels the linked card or package request, payment FAILED with the reason in the notes, activity_log PAYMENT_FAILED.
- `GET /api/v1/invoices/by-payment/{paymentId}`: receipt with lines, for staff or the member who owns the payment.
- `PaymentVerificationService`, `PaymentVerificationController`, `InvoiceController`, `PaymentSpecifications`, DTOs `ConfirmPaymentRequest`, `FailPaymentRequest`, `StaffPaymentResponse`, `InvoiceResponse`, `InvoiceLineResponse`, `PaymentAmountMismatchException`; audit actions `PAYMENT_CONFIRMED`, `PAYMENT_FAILED`.
- Tests: `PaymentVerificationServiceImplTest` (13), `PaymentVerificationControllerTest` (15), `PaymentVerificationIntegrationTest` (8, Testcontainers); test cases in `docs/tests/BE-18_test_cases.md`.

### Changed
- `ConflictException` takes an optional `code`; the 409 body includes `code` only when one is set (existing 409 bodies are unchanged).
- Note appending moved from `PaymentServiceImpl` to `PaymentNotes` so both services keep the same 500 character rule; behaviour unchanged.
- `PaymentRepository` is a `JpaSpecificationExecutor`; `MemberCardRepository`, `SportPackageRegistrationRepository` and `InvoiceRepository` get batch `findByPaymentIdIn` lookups for the list.
- `docs/database/04` and `09`: verification endpoints, invoice amount rule, mismatch and double confirmation rules, payment state machine, V21 columns of `payment`.

### Notes
- No migration: `amount_received` is not a column (a confirmed payment always received exactly its amount); a different amount is kept only in the PAYMENT_AMOUNT_MISMATCH log details.
- Not verified in the authoring environment (Maven Central and Docker images were blocked there). Run `mvn -B test` before merging; integration tests run in CI.
- Failing a payment cancels the request it paid for, so the member asks again and a new payment row is created (state machine "Retry (creates new row)").

### Review round 1
- The card and package activate endpoints (FE-05) now issue the same receipt as confirm through `PaymentVerificationService.recordPaymentConfirmed`: one PAID invoice and one PAYMENT_CONFIRMED row per confirmed payment; their request is unchanged.
- Confirm and fail lock the payment row first (`findByIdForUpdate`), so the second caller always gets 409 `PAYMENT_NOT_PENDING`; a version check failure inside them answers 409 `PAYMENT_CONCURRENT_UPDATE`. Non-staff actors in the service get 403.
- Mismatch also writes an activity_log row PAYMENT_AMOUNT_MISMATCH. One `PaymentItemResolver` gives the item name, original price and discount for `GET /payments/{id}`, `GET /payments` and the invoice. The fail reason is stored in `sport_package_registration.cancel_reason`.

### Review round 2
- The card and package activate and cancel endpoints also lock the payment row before the card or registration, so every path uses one lock order and cannot deadlock with confirm or fail. Invoice amounts come from `PaymentItemResolver` and must satisfy subtotal - discount = total.
- Review round 3: a card's original price is never below its `price_paid` snapshot, so a tier price lowered after the request no longer blocks confirmation; an item price that differs from the payment answers 409 `PAYMENT_ITEM_AMOUNT_MISMATCH` instead of 500.
- CI fix: `PaymentVerificationIntegrationTest` failed to compile (ambiguous `assertThat` on `tx().execute(...)`); the invoice count is read through an `int` helper `invoiceCount`.
- Rebased on main after PR #40 (manager catalog, BE-03) as one commit: conflicts in `MembershipCardServiceImpl`, `SportPackageServiceImpl` and their unit tests resolved by keeping the PR #40 upgrade rules (rank based, `upgradeRequest`) and the BE-18 lock order and receipt step; the integration test tier now gets a unique `tierRank`, and `ManagerCatalogIntegrationTest` cleanup deletes the invoices that card activation now issues before their payments.
- CI fix 2: `PaymentVerificationIntegrationTest` deletes the rows it creates after each test (the SQL Server container is shared, and its leftover sports, packages, ranked tiers and the member code MEM-0001 broke 9 catalog and member tests); its member codes now use the `BE18-MEM-` prefix.

### Review round 4
- Invoice pricing uses only values stored at request time: a card's original price is `price_paid` plus, for an upgrade, the `price_paid` of the replaced card chain (the credit BE-17 priced it with); the current tier price is no longer read, so a tier price edited after the request cannot create a discount. Packages keep their registration snapshots. Applies to the invoice, `GET /payments` and `GET /payments/{id}`.
- `GET /payments`: `from` and `to` outside 0001-01-01 to 9999-12-31 answer 400; `to=9999-12-31` leaves the upper bound open instead of failing with 500.
- Merged main (BE-10, PR #51): `ConflictException` keeps both the optional `code` and the optional `details`.

## 2026-10-14 - Manager Session Schedule and Coach Assignment (BE-10, Flow 2)

### Added

- Manager session calendar with inclusive date ranges, class/coach/facility filters, deterministic ordering and grouped confirmed booking counts.
- Single and weekly DRAFT or PUBLISHED session creation, coach qualification checks, class capacity bounds and complete coach/facility conflict details in the existing 409 error envelope. Weekly batches reject every insertion if any requested date conflicts.
- Pessimistic coach/facility resource locks for concurrent scheduling, booking creation and cancellation that lock the session row before booking checks, and session updates that exclude themselves from overlap checks, preserve the cached booking counter and check a new date against booked package validity.
- Future-session cancellation with manager/reason metadata, bulk booking cancellation, capped session-credit restoration only to ACTIVE package registrations and per-registration package booking counts in one audited transaction. Package consumption uses a conditional database decrement across different sessions; individual and manager cancellation restore credits atomically within the purchased cap. Existing registration balances cannot be overwritten by stale JPA entity saves. The manager calendar requires both date bounds and returns the existing 400 envelope for missing or malformed bounds.
- V23 cancellation metadata and resource/date and booking/session indexes, SQL Server integration coverage registered in the required-suite manifest, and test cases in `docs/tests/BE-10_test_cases.md`.

## 2026-10-13 - Sport Packages & Pricing and Manager Catalog (BE-03, Flow 1)

Branch `feat/f1-manager-catalog`. Owner: Dat (Senko). Priority: P1. Depends on: none. Rebased on `origin/main` (commit 6e82388). Execution counts and current validation status are recorded per run and revision in `docs/tests/PR40_remediation_test_cases.md`; revised SQL Server coverage still requires Docker.

PR40 remediation adds mapped `tierRank`, `upgradeRequest` and `replacesCardId` fields to card responses. Frontend previews validate the explicit replacement chain, retain historical paid snapshots, block unverifiable plans, and use rank-based classification. Member zero-fee upgrades are blocked; staff-assisted zero-fee upgrades remain supported. V22 now requires matching predecessor evidence before marking discounted legacy purchases. Surefire and Failsafe retain Jupiter tag selection with Vintage auto-addition disabled, and shared report guards use a required-suite manifest. Current evidence and pending merge requirements are in `docs/reviews/PR40_remediation_completion_report.md`.

### Added
- Manager Sport Packages API: `PUT /api/v1/packages/{id}` (manager list under `GET /api/v1/manager/packages`): dynamic filtering by `sportId`, `format`, `active`; update price, durationDays, sessionCount, sessionMinutes (60 or 90), active status; audit logging (`PACKAGE_UPDATED`) recording before and after snapshots.
- Manager Card Tiers API: `PUT /api/v1/membership-cards/tiers/{id}` (manager list under `GET /api/v1/manager/card-tiers`): update price, durationMonths, discountPercentage (0-100), description, active status; invariant guards preventing alteration or deactivation of the STANDARD tier; audit logging (`CARD_TIER_UPDATED`).
- Explicit tier ranking via `tier_rank` column on `membership_card_tier` (STANDARD=1, SILVER=2, GOLD=3, VIP=4) in migration V22, completely decoupling upgrade hierarchy from promotional live catalog prices.
- Unique filtered index `ux_member_card_replaces_in_force` on `member_card(replaces_card_id)` for active and pending cards, preventing concurrent double-replacement of in-force cards.
- Upgrade chain migration backfill (V22 Step 4b) supporting multi-step upgrade chains (A to B to C): identifies candidate predecessor pairs sharing member and end_date, linking only strictly 1-to-1 matches where an upgrade card has exactly one candidate (`HAVING COUNT(cp.candidate_card_id) = 1`) and that candidate is claimed by exactly one upgrade card (`HAVING COUNT(upgrade_card_id) = 1`); ambiguous predecessors claimed by two upgrade cards remain unlinked (`replaces_card_id = NULL`) to fail closed.
- Pessimistic write locking on tier table rows during tier updates (`tierRepository.findAllForUpdate()`, ordered deterministically `ORDER BY id` without a custom timeout) to sequentially serialize concurrent price adjustments and enforce price monotonicity. In SQL Server, the dialect supports NOWAIT (which fails immediately) but does not support a per-query bounded wait (`supportsWait() == false`); Option 2 (sequential unbounded wait) was retained because a fail-fast response was not requested. Basic request validations (null checks, bounds) execute before lock acquisition, failing fast with HTTP 400 before checking tier existence. The target tier is resolved from `findAllForUpdate()` post-lock to prevent stale entity state in `activity_log` before snapshots. Catching `PessimisticLockingFailureException` maps database or driver-level lock failures (such as deadlock victim selection) and chains the root cause in `BusinessRuleException` (does not imply a timeout); known limitation: unbounded wait holds a database connection while waiting. Verified by unit tests (including Mockito `InOrder` post-lock snapshot assertion and pre-lock validation checks) and deterministic concurrent transaction integration test `TC-BE03-69` polling SQL Server DMVs (`sys.dm_os_waiting_tasks` / `sys.dm_exec_requests`) for lock wait.

### Contract Changes Affecting Frontend
- **Upgrade Fee Calculation Contract Change (`src/pages/member/cardsModel.ts` on `main`)**:
  - On `main`, frontend `planCard` calculates upgrade price as `targetTier.price - currentTier.livePrice`.
  - In BE-03, the backend computes upgrade fee as `max(0, targetTier.price - cumulativeBasePaid)` (where `cumulativeBasePaid` is the actual cumulative amount paid by the member toward the current active term across the replacement chain, retaining historical pricePaid snapshots).
  - An upgrade is strictly determined by explicit tier rank (`targetTier.tierRank > currentTier.tierRank`), NOT by live price comparison (`targetTier.price > currentTier.price`). Consequently, price promotions or price adjustments cannot flip or invert upgrade rules.
  - Price monotonicity rule for active paid tiers: When updating a paid tier, its price must be strictly increasing with tier_rank compared to the nearest lower-ranked and nearest higher-ranked active paid tier. STANDARD tier is excluded.
  - Zero-fee upgrade policy (temporary fail-closed pending PO approval): If the calculated upgrade fee is <= 0, self-service online upgrade attempts fail closed (HTTP 400 directing member to the front desk) to prevent irreversible automated zero-fee tier upgrades; staff-assisted purchases at the desk remain permitted to process zero-fee upgrades immediately.
  - If a legacy card's upgrade chain cannot be resolved, automated upgrade fails closed (HTTP 400 with a message directing the member to the front desk).
  - Open PO questions: free upgrade policy, refund policy, proration, and optimistic concurrency locking for manager catalog edits.
  - Frontend `planCard` in `src/pages/member/cardsModel.ts` should be updated to mirror this contract by using cumulative base price from the active card's payment history, comparing explicit tier ranks rather than live catalog prices, and directing fee <= 0 requests to the front desk.

## 2026-10-13 - Receptionist Member Search, Register and Renew, Check-in (FE-05, Flow 1)

Branch `feat/f1-reception-screens`. Owner: Phat. Priority: P1. Depends on FE-00 (merged), BE-05 and BE-19 (not merged yet, see Notes). Rebased on main after FE-04. Verified after review round 1 with `npm run lint -- --max-warnings 0`, `tsc -b`, `npm test` (399 tests in 20 files: 47 for FE-05, 352 existing for FE-00 to FE-04) and `npm run build`.

### Added
- Member Search and Profile (F1-13) at `/reception`: search by name, phone or email (`GET /members`, query kept in the URL), a single result opens directly, member overview (`GET /members/{id}/overview`) with card, expiry, discount, card requests waiting for payment and sport registrations (pending first), Renew package and Check-in links.
- Member Not Found state and Create Member overlay (`POST /members`) with client checks, field errors from the backend and the 409 duplicate message.
- Payment Handoff overlay for pending online requests, opening the front desk payment confirmation; After Payment notice on the profile once confirmed.
- Register and Renew (F1-14) at `/reception/register` with tabs Sport package, Membership card and Pending online requests. Desk sale with payment method, reference and amount received (BE-19), fee summary with the card discount, change for cash, renewal start after the current term, card term preview that mirrors `planCard` (new, renew, upgrade for the difference, blocked upgrade), and a receipt card with "View receipt".
- Pending online requests tab (`GET /membership-cards/pending`, `GET /packages/registrations/pending`), oldest first, each opening the confirmation screen.
- Payment confirmation and receipt at `/reception/payments/:paymentId`: `GET /payments/{id}`, Confirm payment through `PUT /membership-cards/{id}/activate` or `PUT /packages/registrations/{id}/activate` with the method used, Print receipt. FE-12 extends this screen.
- Check-in (F1-15) at `/reception/check-in`: member code or card scan, `POST /check-ins`, Recorded and Rejected states with the backend reason, today's list (`GET /check-ins?date=`), field cleared and focused for the next scan.
- `src/api/receptionApi.ts`, `src/pages/reception/*`, `src/styles/reception.css`; routes registered in `src/routes/AppRoutes.tsx`.

### Notes
- BE-05 and BE-19 are not on main. The contract assumed by the frontend is listed in `docs/tests/FE-05_test_cases.md` section 1.1 (one overview shape). On main a desk sale is ACTIVE with no payment record; the screen says so instead of showing a receipt.
- Out of scope: F1-16 Support Requests (P3) and Edit profile from the desk (no endpoint).

### Review round 1
- Desk sale result decided from the payment: ACTIVE with paymentId null (main without BE-19) warns that no payment record exists; receipt only with a payment. Overview reloaded after each sale.
- Card plan reuses FE-04 `planCard` (desk wording, blocked and unknown not sellable); duplicate card rules and `CardTier` removed. `parseAmount` accepts only whole VND.
- Only 404 "Member not found" reads as a missing member; overview contract pinned to one shape with list checks. Confirm screen checks that the item belongs to the payment and waits for it. After Payment notice through router state.
- Check-in: unknown code shown as Rejected "Member not found"; today's list without the BE-05 date filter shows a notice and this screen's check-ins.
- `useResource` moved to `src/hooks/useResource.ts`, `toIsoDate` to `src/utils/date.ts`; single-result selection replaces the history entry.

### Review round 2
- Check-in shows Rejected "Member not found" only for that 404 message; any other 404 is an error notice. Comments call the overview shape and the 404 message proposed until BE-05 confirms them.

## 2026-10-13 - Membership Cards and My Membership (FE-04, Flow 1)

Branch `feat/f1-member-cards`, stacked on `feat/f1-member-packages` (FE-03) and `feat/f1-member-dashboard` (FE-02), both not merged yet: merge FE-02, then FE-03, then FE-04. Owner: Phat. Priority: P1. Depends on FE-00 and BE-17. Verified with `npm run lint`, `tsc -b`, `npm test` (338 tests in 18 files, 40 new) and `npm run build`.

### Added
- Membership Cards (F1-11) at `/member/membership/cards`: `GET /membership-cards/tiers` and `GET /membership-cards/my`; each tier shows price, validity, discount, perks and description; the tier in force is marked Active (Standard is marked Current when there is no paid card), a queued renewal shows "Starts", a pending request shows "Pending payment".
- Tier buttons follow `MembershipCardServiceImpl.planCard`: same tier as the last card -> Renew card (from the day after it ends), dearer tier -> Upgrade card (pay the difference, keep the end date; blocked with the reason while a renewal is queued), cheaper tier -> Buy after current card (starts after the last card ends), no paid card -> Get card.
- Renew Membership Card overlay: card fee, upgrade credit, amount to pay, validity, discount, start and end date computed before Confirm; `POST /membership-cards/purchase` with `{tierId}` then the payment summary `/member/payments/{paymentId}`. Backend errors (for example a request already waiting for payment) are shown in the overlay as is.
- My Membership and Sport Packages (F1-12) at `/member/membership` with tabs Sport packages and Membership card (`?tab=card`): registrations from `GET /packages/registrations/my` with the states Pending Payment (Pay now), Active (Book a session, Renew package) and Expired (Renew package, View payment); cards from `GET /membership-cards/my` with Pay now and Cancel request for pending requests only (`PUT /membership-cards/{id}/cancel`, then the list reloads).
- `src/api/cardsApi.ts`, `src/pages/member/cardsModel.ts`, tier fixtures in `src/test/memberFixtures.ts`, styles in `src/styles/member.css`.

### Changed
- Dashboard quick link "My membership card" opens the Membership card tab (`MEMBER_PATHS.membershipCard`); TC-FE02 quick link assertion updated.

### Review round 1
- Must fix: overlay title "{tier name} card" (reads "VIP Member card" with the V10 names); tier fixtures use the exact V10 values; tier description no longer shown; Renew card only on the latest ended paid card when nothing is in force or queued (`renewableCardId`); a 400 or 404 on cancel and a 400 on purchase reload the data and keep the backend message; one "today" per page, the plan of the tier button is passed to the overlay.
- Should fix: "Choose card" for the unknown plan; `MEMBER_PATHS.membershipCards` replaces `MEMBERSHIP_CARDS_PATH` and `MEMBERSHIP_CARD_TAB_PATH`; footer "Confirm and continue" then "Cancel"; Payment row removed from "How your card works"; both tabpanels rendered (inactive one hidden); cardsModel comment states the UTC+7 assumption. 352 tests pass (14 new).

### Notes
- An ACTIVE card or registration past its end date is shown as Expired until the nightly jobs (F1-B3, F1-B4) exist.
- The backend has no perks field on `CardTierResponse`; perks are built from the discount rule. Figma F1-11 says a mid-term change to a cheaper tier is not available; the task and the backend allow it as a card that starts later, which is what the screen does.
- Activation recomputes the dates on the day the payment is confirmed; the pending card says so.

## 2026-10-13 - Sport Packages and Registration (FE-03, Flow 1)

Branch `feat/f1-member-packages`, stacked on `feat/f1-member-dashboard` (FE-02, not merged yet): merge FE-02 first. Owner: Phat. Priority: P1. Depends on FE-00 and BE-17. Verified with `npm run lint -- --max-warnings 0`, `tsc -b`, `npm test` (286 tests in 16 files, 31 new) and `npm run build`.

### Added
- Sport Packages (F1-09) at `/member/packages`: `GET /sports` and `GET /packages`, filters by sport, training type and duration (kept in the URL query), cards with duration, sessions, minutes per session and the price after the member's card discount (STANDARD 0%, GOLD 5%, VIP 10%; single visit never discounted, as in the backend).
- Package Detail and Registration (F1-10) at `/member/packages/:packageId`: package info, start date picker (today or later), price breakdown (original, discount percent and amount, total), expiry date; `POST /packages/registrations` with channel ONLINE.
- Payment Handoff overlay: registration and payment references and the amount from the backend answer, "Continue to payment" to `/member/payments/{paymentId}`.
- Read only Payment Summary (F3-01) at `/member/payments/:paymentId` from `GET /payments/{id}` (BE-17): reference, status, original price, discount, amount, method, bank transfer details. It replaces the FE-02 placeholder; FE-11 adds the method change and status screens.
- `src/api/packagesApi.ts`, `src/pages/member/packagesModel.ts`, styles in `src/styles/member.css`.

### Changed
- `requireNavItem` and its test removed: the payment route no longer reuses the My Payments menu entry.
- FE-02 test TC-FE02-023 expects the real "Payment Summary" heading.

### Review round 1
- B1 `sessionsLabel` ("1 session"), B2 `isDiscountEligible` and `cardDiscountText` now say "% off packages longer than a single visit" (same rule as the prices), B3 a pending registration of the same package blocks Confirm and links to its payment, B4 the real 400 "Start date cannot be in the past" goes under the date field and "today" is read at submit.
- M1 prices in integer cents (matches BigDecimal HALF_UP), M2 "Checking your card discount" while cards load, M3 unknown URL filters ignored, M4 `src/api/paymentsApi.ts`. 297 tests pass.

### Review round 2
- B3b Confirm registration waits for `GET /packages/registrations/my` ("Checking your registrations"); N1 eligible prices show "-" while the card loads; first commit reworded to `feat(fe-03)`. 298 tests pass.

### Notes
- Single visit cards open the detail page ("View package") like the others; Figma F1-09 shows "Find a session" for them, which needs the schedule screen (FE-08).
- The discount preview uses the card in force today, like the backend; the overlay shows the amount the backend actually charged.

## 2026-10-13 - Member Dashboard and My Profile (FE-02, Flow 1)

Branch `feat/f1-member-dashboard`. Owner: Phat. Priority: P1. Depends on FE-01 (merged) and BE-02 (not merged yet, see Open Questions). Verified with `npm run lint -- --max-warnings 0`, `tsc -b`, `npm test` and `npm run build`, plus browser checks in Chromium against a mock of the member API. After review round 1: 242 tests in 15 files (30 added in round 1). After review round 2: 255 tests in 15 files (13 added in round 2). After review round 3: 257 tests in 15 files.

### Review Fixes (Round 1)
- B1 (superseded by R2-B1) Change password never signs out: new per-request option `skipAuthRefresh` in `api/client.ts` (a 401 is thrown as a normal ApiError, no refresh, no retry, no sign out; every other request unchanged). `memberApi.changePassword` uses it; a 401 is shown under Current password.
- B2 No field detection from message text: `passwordFailure` decides by HTTP status (401) and backend field keys (`details.currentPassword`, `newPassword`, `confirmPassword`); a 400 without details goes above the form.
- B3 Phone is optional on My Profile (as in `RegisterRequest`): pattern checked only when entered, sent as null when empty, `required` removed.
- B4 Used up packages: new model helpers `canBook`, `isExhausted`, `remainingSessionCount`, `hasNoSessionLimit`. A package with 0 sessions left shows "No sessions left" and only Renew package; the hero offers Book a session only when a package can be booked. Only `totalSessions === null` means "No session limit"; a total of 0 is "0 of 0 sessions remaining" and exhausted.
- M1 "View all packages" kept on `/member/membership` after checking `layouts/navigation.ts` and Figma F1-12 (its default tab "Sport packages" lists "My registrations"); covered by a test.
- M2 New hero variants `pay` ("Complete your payment to start training." with Pay now to the first pending item, or "View pending payments" to the block) and `renew` (nothing to book or pay).
- M3 FE-00 and FE-01 tests reverted to main; the account switch notice gets `aria-label="Account switched"` and the 5 assertions query `getByRole('status', { name: 'Account switched' })`.
- M4 `useMemberResource`: documented stable `load`, answers tagged by loader and attempt (loading after reload or a new loader, never old data), StaleSessionError ends in the error state with Retry.
- m1 Date of birth must be before today ("Date of birth must be before today"); the picker max is yesterday.
- m2 maxLength and length messages use `FULL_NAME_MAX_LENGTH`, `ADDRESS_MAX_LENGTH`, `PHONE_INPUT_MAX_LENGTH`.
- m3 `MemberCard.tierCode` typed `string | null`; a missing code counts as a paid card (documented and tested).
- m4 Progress bar `aria-valuenow` clamped to 0..total like the bar width.
- m5 One announcement per success: inline notice no longer a live region; the toast announces.
- m6 Dashboard Retry reloads bookings only when they failed.
- m7 `AppRoutes.tsx` children of `<Routes>` re-indented; `requireNavItem` throws when the My Payments menu entry is missing instead of dropping the route.
- m8 Pending card amount stays `pricePaid`: `MembershipCardServiceImpl.java` lines 110 and 112 pass the same `plan.price()` to the card (`pricePaid`) and to `createPendingPayment` (payment `amount`).

### Review Fixes (Round 2)
- R2-B1 `skipAuthRefresh` removed (nothing else used it) and replaced by `authFailure: 'refresh-then-throw'` in `api/client.ts`. A 401 refreshes the token once with the shared single-flight refresh and retries once; a 401 on the retry is thrown as ApiError(401) without signing out and is shown under Current password; a rejected refresh token still signs out; a network or server error during the refresh is thrown without signing out. An expired access token no longer looks like a wrong password. Default behavior of every other request unchanged.
- R2-M1 New hero variant `first-package` ("Choose your first sport package.", with the card discount when a card is in force) for a member with a paid card who never had a package; `renew` only when the member had packages (ACTIVE or EXPIRED) that are now used up or ended. `heroVariant` takes a facts object including `hasHadPackage`.
- R2-m1 Active packages stat keeps counting the packages of the list; when some have no sessions left the hint reads "<n> can be booked - <m> with no sessions left".
- R2-m2 `requireNavItem` kept at module load in `AppRoutes.tsx`; documented that it runs in every build and that a missing entry fails `navigation.test.ts` and every test that renders the app.
- Verified: 255 tests in 15 files (13 added in round 2), lint, `tsc -b` and build green.

### Review Fixes (Round 3)
- R3-m1 The discount rule in the dashboard text is documented in `docs/database/09-business-rules-and-state-machines.md` lines 149 to 153 (30-day and 90-day packages only), so the wording stays. New helper `cardDiscountText` is used by both the tier stat hint and the first-package hero, so both read "<n>% off 30 day and 90 day packages". The backend code only excludes packages of 1 day or less; listed as an open question.
- R3-doc New open question for BE-02: does `PUT /users/me/password` revoke existing refresh tokens? If it does, the member is signed out at the next refresh after a successful change, and the frontend should then tell the member and send them to login right after the change.
- Verified: 257 tests in 15 files (2 added, 1 changed in round 3), lint, `tsc -b` and build green.

### Added
- Member Dashboard (F1-07) at `/member`: greeting hero, membership tier stat, active packages and upcoming sessions stats, pending payments with a "Pay now" link to `/member/payments/{paymentId}`, active sport packages with remaining sessions, end date and progress bar, upcoming bookings, profile card and quick links.
- The two Figma states: "New Standard Member" (no paid card and no package) and "After Purchase" (any paid card or package, including one waiting for payment). The state is exposed as `data-state` on the page.
- My Profile (F1-08) at `/member/profile`: view and edit full name, date of birth, phone, training goal, experience level and address (`PUT /users/me`); read only login email; change password form (`PUT /users/me/password`) with live password rules. Both forms show a success message and backend errors (under the field for 400 details, above the form otherwise).
- `src/api/memberApi.ts`: typed calls for `GET /users/me`, `GET /membership-cards/my`, `GET /packages/registrations/my`, `GET /bookings/my?when=UPCOMING`, `PUT /users/me`, `PUT /users/me/password`. List answers that are not JSON arrays are reported as errors.
- Route `/member/payments/:paymentId` that shows the My Payments placeholder until FE-11 builds the payment summary (F3-01), so "Pay now" never opens a 404 page.
- `src/styles/member.css` for the two screens.

### Changed
- `UserProfile` gains the optional BE-02 fields `dateOfBirth`, `address`, `mainGoal`, `currentLevel`, `memberCode`.
- `PasswordRules` moved from `RegisterPage.tsx` to `src/components/PasswordRules.tsx` (same markup) and is reused by the change password form.
- FE-00 and FE-01 tests that read the account switch notice now query it by its accessible name (see Review Fixes M3), because the real dashboard also has loading spinners with role status.

### Open Questions
- BE-02 contract assumed by the frontend: `PUT /users/me` body `{fullName, phone, dateOfBirth, address, mainGoal, currentLevel}` (phone and the optional fields may be null) returning `UserProfileResponse` (or 204); `PUT /users/me/password` body `{currentPassword, newPassword}`. Preferred for a wrong current password: 400 with `details.currentPassword`; a 401 is also handled: since review round 2 the client refreshes once, retries once and shows a second 401 under Current password without signing out.
- Interested sports (Figma F1-08) and the Notifications and Recommended for you cards (Figma F1-07) are not built: no endpoint exists yet.
- Upcoming bookings are filtered again on the client until BE-12 adds `when=UPCOMING`.

## 2026-10-12 - Pending Payments for Online Requests and Payment Summary (BE-17, Flow 3)

Branch `feat/f3-pending-payments`. Status: Implemented and hardened after Round 7 Code Review. Verified locally with `mvn -B -ntp test "-Dtest=!*IntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"` (521 tests run, 0 failures, 0 errors, 4 skipped via `@Testcontainers(disabledWithoutDocker = true)`: FlywayMigrationTest, PermissionRepositoryTest, RoleRepositoryTest, UserAccountRepositoryTest). Integration tests verified in CI via GitHub Actions (Run ID 38335867540, 615 tests run, 0 failures, 0 errors, 0 skipped).

### Review Fixes & Hardening (Round 7)
- D1 Test Documentation Integrity: Audited `docs/tests/BE-17_test_cases.md` end-to-end; removed all phantom method names and non-existent test references; aligned test suite counts with Surefire reports; reconciled ADMIN exclusion consistently with code and security rules; added missing test cases from rounds 5 and 6.
- M-B Early confirmedBy Validation: Moved non-null and non-staff validation for `confirmedBy` in `PaymentServiceImpl.markPaymentSuccess` before repository `findById` and entity mutation; strengthened unit tests to assert repository methods are never called and entity remains unmutated upon exception.
- M-A Migration V21 History & Checksum Clarification: Verified via path-filtered git log that V21 changed between pushed commits (`f59fb53`, `79a95c2`, `d2ca75d`); documented consequences for developer databases and explicit recovery options (recreate vs repair).
- M-C Commit Traceability: Documented the full commit and CI execution chain for rounds 6 and 7 in the AI Audit Log and PR description draft.
- Minor Fixes: [m1] handled empty trimmed note segment in `PaymentServiceImpl.appendNote` to prevent leading delimiter; [m2] imported `java.util.Comparator` normally and cleaned up redundant fallback in `resolveInvoiceSummary`; [m3] documented `SecurityUtils` case-insensitive parsing; [m4] documented `isMember` scope refactor.

### Review Fixes & Hardening (Round 6)
- K1 Audit Log & Evidence: Synchronized AI audit log with code commit and CI run evidence, regenerated delivery artifacts cleanly from HEAD, and replaced all stale line references.
- K2 Ownership & Shared PK Design: Documented shared primary key design (`@MapsId user_id`) across ownership checks in `PaymentServiceImpl`, `MembershipCardServiceImpl`, and `SportPackageServiceImpl`. Added integration test `memberProfilePrimaryKey_EqualsUserAccountId_ByDesign` in `PaymentPendingIntegrationTest` asserting `profile.getId() == profile.getUserAccount().getId()`. Fixed integration test cleanup to delete user explicitly by user id.
- M1 Time-Source Tests: Added plain unit test `AuditConfigTest` parameterized over 4 timezones using `Clock.fixed`. Replaced weak 5-second tolerance integration test with exact 0-second assertions in dedicated `PaymentAuditingFixedClockIntegrationTest` using `@TestConfiguration` with an isolated `@Primary` fixed Clock at a whole-second instant (`America/New_York`). Split `PaymentEntityMappingTest.testLifecycleDefaults` by moving `Invoice` and `InvoiceLine` callback checks into `InvoiceEntityMappingTest` and `InvoiceLineEntityMappingTest`.
- M2 Notes Truncation Preserving Newest Notes: Re-engineered `PaymentServiceImpl.appendNote` to guarantee the newest appended segment is preserved intact whenever it is <= 500 characters, dropping or trimming oldest content first with surrogate-pair safety. Added unit tests for 495-character existing note plus staff note, single 600-character note cut safely, and null/blank inputs.
- M3 Auditing Scope Clarification: Documented that `auditingDateTimeProvider` is a global DateTimeProvider backed by the Clock bean; currently used by BaseAuditableEntity (Facility, Membership, MembershipPlan, Sport, CoachProfile, MemberProfile, Role, UserAccount), ActivityLog, CoachCertification, PasswordResetToken, and Payment.
- M4 Migration Safety: Documented that `df_payment_payment_time` was explicitly named in `V14__create_payment_and_invoice_tables.sql`, making the DROP CONSTRAINT deterministic. Confirmed V21 was already pushed on the branch and applied in CI, avoiding checksum mutation.
- M5 Role Consistency: Clarified that unseeded `ADMIN` is excluded by `SecurityUtils.isStaff` and rejected with 403 Forbidden on `PaymentController`. Added WebMvcTest slice tests in `PaymentControllerTest` proving ADMIN access is forbidden.
- M6 Defensive Check for confirmedBy: Enforced in `PaymentServiceImpl.markPaymentSuccess` that non-null `confirmedBy` must satisfy `SecurityUtils.isStaff(confirmedBy)`, throwing `BusinessRuleException` otherwise. Added unit tests for staff, non-staff, and null confirmedBy.
- Minor Hardening: [m1] test cleanup helper with null guards, [m2] deterministic lowest-id sorting in `resolveInvoiceSummary` with unit test, [m3] payment amount scale (<= 2 decimal places) and integer digits (<= 10) validation in `createPendingPayment` with unit tests, [m4] removed placeholder text from `docs/tests/BE-17_test_cases.md`.

### Review Fixes & Hardening (Round 5)
- R5-1 Clock-Backed JPA Auditing: Configured `@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")` in dedicated `AuditConfig` backed by the global `Clock` bean; currently used by BaseAuditableEntity (Facility, Membership, MembershipPlan, Sport, CoachProfile, MemberProfile, Role, UserAccount), ActivityLog, CoachCertification, PasswordResetToken, and Payment. Added `@EntityListeners(AuditingEntityListener.class)` with `@CreatedDate` on `createdAt` and `@LastModifiedDate` on `updatedAt` on `Payment.java`. Removed `@PrePersist` and `@PreUpdate` callbacks from `Payment.java`. Removed manual `createdAt` and `updatedAt` assignments in `PaymentServiceImpl` while keeping `paymentTime = LocalDateTime.now(clock)`. Added integration test `markPaymentSuccess_AuditingTimestamps_ConsistentWithClockAndPaymentTime` in `PaymentPendingIntegrationTest` asserting `abs(updated_at - payment_time) <= 5 seconds` and `created_at <= updated_at` (superseded in Round 6 by exact 0s assertions in `PaymentAuditingFixedClockIntegrationTest`).
- R5-2 Documentation Alignment: Updated CHANGELOG M1, audit item 9, audit section 10 M1, and Open Question 5 to describe the final Clock-backed JPA auditing mechanism on `Payment`.

### Review Fixes & Hardening (Round 4)
- Blocker 1 Configuration Restore: Restored the base configuration in `backend/.env.example` from `origin/main` (retaining `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`) and appended unquoted `SPORTIFY_PAYMENT_*` properties with comments explaining they are required for non-dev profiles. Extended `CorsPropertiesConfigTest` with `envExample_shouldContainAllRequiredKeysAndNoDoubleQuotes`.
- Major 6 Lifecycle Fallbacks: Restored `@PrePersist` and `@PreUpdate` callbacks on `Payment.java` as a safety net fallback populating timestamps when null and refreshing `updatedAt` on update, while services continue setting deterministic timestamps via `Clock`. Reverted manual builder lines in `PaymentRepositoryIntegrationTest` and updated `PaymentEntityMappingTest.testLifecycleDefaults`.
- Minor 7 Activation Validation: Updated `activateCard` and `activateRegistration` to throw `BusinessRuleException` (400) if `actualMethod` is specified but no payment is linked to the entity. Added unit tests for both cases.
- Minor 8 Role Lookup Fail-Fast: Updated `optimisticLockConflict_ThrowsObjectOptimisticLockingFailureException` and helper in `PaymentPendingIntegrationTest` to fail fast via `orElseThrow` if the seeded `MEMBER` role is missing, avoiding test fixture role table mutations.
- Documentation Accuracy: Corrected claims regarding skipped tests to cite `@Testcontainers(disabledWithoutDocker = true)` in `AbstractIntegrationTest`, clarified `docs/ci-cd.md` resides on branch `chore/ci-cd-pipeline`, and marked Section 3 of the audit log as historical.

### Review Fixes & Hardening (Round 3)
- B1 Card Plan & Upgrade Rule Reversion: Reverted upgrade check in `MembershipCardServiceImpl.planCard` to `tier.getPrice().compareTo(current.getTier().getPrice()) > 0`. Purchasing an equal-price tier now starts the day after the current card ends, priced at full tier price, without replacing the active card. Added unit tests for both online and desk equal-tier switches.
- B2 Cash Recording on Activation: `activateCard` and `activateRegistration` accept optional `ActivatePaymentRequest { paymentMethod }`. Counter staff can specify actual payment method (including `CASH` and `CREDIT_CARD`) when confirming payment, which delegates to `PaymentService.markPaymentSuccess(paymentId, confirmedBy, actualMethod)`. In `updatePaymentMethod`, staff can assign any payment method, whereas members remain restricted to `ONLINE_ALLOWED_PAYMENT_METHODS`. Desk walk-in direct sales (card `paidAtDesk`, registration via `RECEPTION`) do not record payment rows in this PR and are tracked as `TODO-BE17-COUNTER-PAYMENT`.
- B3 Audit and Consistency Corrections:
  - V21 Migration: Removed redundant `ck_payment_amount` (pre-existing in V14). Dropped `df_payment_payment_time` default constraint, added backfill setting `payment_time = NULL` for non-SUCCESS payments, and added CHECK constraint `ck_payment_success_time` enforcing `payment_status <> 'SUCCESS' OR payment_time IS NOT NULL`.
  - Non-owner 404: `cancelCard` throws `ResourceNotFoundException` (404) for non-owners, concealing card existence and matching `cancelRegistration`. Added `MembershipCardControllerTest` proving 404 response.
  - Concurrency: Clarified `@Version private Integer version` pre-existed on `Payment`. Replaced tautological mock test with a real integration test in `PaymentPendingIntegrationTest` asserting `ObjectOptimisticLockingFailureException`, and added MockMvc test confirming HTTP 409 conflict.
  - Catalog DTOs: Reverted all whitespace-only and formatting changes against `origin/main`.
- M1 Single Time Source & Auditing: Configured Spring Data JPA auditing with `@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")` in dedicated `AuditConfig` backed by the `Clock` bean, with `@EntityListeners(AuditingEntityListener.class)`, `@CreatedDate` on `createdAt`, and `@LastModifiedDate` on `updatedAt` on `Payment.java`. Removed `@PrePersist` and `@PreUpdate` callbacks from `Payment.java`. `PaymentServiceImpl` sets `paymentTime` via `Clock` while `createdAt` and `updatedAt` are managed automatically by JPA auditing in the configured time zone. Added integration test in `PaymentPendingIntegrationTest` asserting `updated_at` and `payment_time` differ by at most 5 seconds.
- M2 V21 Completion: Added integration tests verifying `ck_payment_success_time` rejects SUCCESS with null `payment_time`, and native SQL insert of PENDING leaves `payment_time` NULL due to dropped default constraint.
- M3 History Ordering: Replaced `findByMemberIdOrderByPaymentTimeDesc` with `findByMemberIdOrderByCreatedAtDesc` across repository and callers. Added integration test verifying newest pending payment is ordered first.
- M4 Data Masking: Actors in notes and cancelReason recorded by user ID only (e.g. "staff user id 12") instead of full names. Masked internal `notes` to null for non-staff in `PaymentResponse`. Set default item description to `"Payment " + paymentCode` instead of internal notes. Added MockMvc tests verifying member receives null notes while staff receives notes.
- M5 Service Hardening: Added `@Transactional(propagation = Propagation.MANDATORY)` on `markPaymentSuccess` and `markPaymentFailed`. Enforced validation rejecting null payment IDs and non-PENDING payments with `BusinessRuleException`. `markPaymentFailed` appends failure reason instead of overwriting notes. `truncateNotes` protects against surrogate pair splitting.
- M6 Role Consistency: Removed unseeded `ADMIN` role from `SecurityUtils.isStaff` and all `@PreAuthorize` annotations added by this PR. Replaced hardcoded role string equality in services with `SecurityUtils.isMember` and `SecurityUtils.isStaff`.
- M7 Configuration Documentation: Extended the existing `backend/.env.example` (restored base configuration from origin/main and appended `SPORTIFY_PAYMENT_*` bank transfer properties without double quotes). Removed redundant `SPORTIFY_*` keys from `application-test.yml`.
- Minor: Removed unused `createPendingPayment(UserAccount, ...)` overload. In `createRegistration`, moved `paidAmount <= 0` guard before sequence increment. Validated actor is non-null in `getPayment` and `updatePaymentMethod`.

### Open Questions
- `TODO-BE17-DISCOUNT`: Handling of 100% discount / free orders vs `ck_payment_amount > 0` constraint.
- `TODO-BE17-COUNTER-PAYMENT`: Counter desk walk-in direct sales recording payment rows.
- Semantics of `FAILED` for user cancellation: Consider dedicated `CANCELLED` status.
- Architectural layering: `SecurityUtils` in `core.common` depending on `identity.entity.UserAccount`.
- Single time source for other domain entities (`Invoice`, `InvoiceLine`, `MemberCard`) using `Clock`.
- Module coupling: `catalog.dto.ActivatePaymentRequest` depending on `payment.entity.PaymentMethod`.

### Docs and Test Artifacts
- `docs/tests/BE-17_test_cases.md`: Complete test specifications and traceability matrix.
- `docs/audit/BE-17_AI_Audit_Log.docx`: AI Audit Log document synchronized with markdown copy.
- `docs/audit/BE-17_AI_Audit_Log.md`: Markdown copy of the AI Audit Log including Section 12 resolution.

## 2026-10-12 - Log in and Sign up screens (FE-01, Flow 1)

Branch `feat/f1-auth-screens`. Status: Implemented and verified with `npm test` (115 tests, 47 new), `npm run lint`, `tsc -b` and `npm run build`, plus 6 browser checks against a mock of the auth API. Manual checks against the real backend (docs/tests/FE-01_test_cases.md section 4) must be run before merging. No backend change.

### Screens (`frontend/src/pages/public/`)
- `LoginPage.tsx` (Figma F1-05): password Show or Hide, Remember me, Forgot password (explains that the front desk resets passwords, the backend has no reset endpoint), clear messages for wrong credentials (401) and for a locked or inactive account (403), sample account box in development builds only.
- `RegisterPage.tsx` (Figma F1-06): full name, phone, email, password with live rules and strength, confirm password, accept terms. Calls `POST /auth/register`, signs the new member in with the returned tokens and opens `/member`. Duplicate email (409) and backend validation details (400) are shown under their fields. If the account is created but the profile cannot be loaded, the user is sent to Log in with the email prefilled instead of submitting the form twice.
- `AuthShell.tsx`: shared two column card with the photo hero from the Figma (`src/assets/auth-hero.jpg`), same 519 / 619 split as the design.
- Both forms: Enter submits, every control is disabled while sending, the focus moves to the first invalid field.

### Auth (`frontend/src/auth/`, `frontend/src/api/tokenStorage.ts`)
- `passwordRules.ts`: same rules as `StrongPasswordValidator` (8 to 64 UTF-16 units, at most 72 UTF-8 bytes, one letter, one digit) and the same message.
- `registerValidation.ts`: client checks with the `RegisterRequest` messages; phone separators are removed before sending. The phone is required on the form (the backend accepts it empty).
- `AuthProvider`: `login(credentials, { remember })` and new `register(request)`; `AccountCreatedError` for the created-but-not-signed-in case.
- `tokenStorage`: a session that is not remembered lives in sessionStorage (this tab only); a remembered one in localStorage as before. The default is unchanged.

### UI kit (`frontend/src/components/ui/`)
- `PasswordInput` (Show or Hide button), `Checkbox`, `Input` gains a `trailing` slot and merges an extra `aria-describedby`. Both shown on `/ui-kit`.

### Docs
- `docs/tests/FE-01_test_cases.md`: 47 automated cases, 6 browser checks, 14 manual cases.
- `docs/audit/FE-01_AI_Audit_Log.docx`.
- `frontend/README.md`: Remember me, `register`, password rules, new components.

### FE-01 review fixes (round 2)
Same branch, new commits on top of the FE-01 commit. Verified with `npm test` (141 tests, 26 new), `npm run lint -- --max-warnings 0`, `tsc -b`, `npm run build` and 7 browser checks; each fix was broken once on purpose and caught by a test. No backend change.
- R2: rule "one account per browser". A login or logout still clears both storage areas; a tab that loses its session this way now shows "You were signed out because another account signed in or out in this browser." on its login page (never after a logout in the same tab). A tab with its own session without Remember me also drops it (`tokenStorage.endTabSession`). JSDoc, README and audit log now describe this exactly.
- R3: the sample account box shows only with `VITE_SHOW_SAMPLE_ACCOUNT=true` and both `VITE_SAMPLE_ACCOUNT_EMAIL` and `VITE_SAMPLE_ACCOUNT_PASSWORD` (read through `config/env.ts`, documented in README and `.env.example`). No credentials in the code.
- R4: "Passwords do not match" is recomputed when either password field changes.
- R5: backend validation errors for keys without an input are shown above the form on both pages.
- R6: only the 409 "Email is already registered" (`EMAIL_TAKEN_MESSAGE`) goes under Email; other 409 messages go above the form.
- R7: the hero photo covers the full height of the column on both screens.
- R8: focus after an error through a state counter and an effect (`utils/useFocusFirstInvalid.ts`); `utils/focus.ts` removed.
- R9: the email and notice passed to /login are read once and removed from history.
- R10: the top progress label is "Meets requirements" instead of "Strong".
- R11: the rules list id comes from `useId`, `Checkbox` merges a caller's `aria-describedby`, unused `tokenStorage.isPersistent()` removed.
- Docs: `docs/tests/FE-01_test_cases.md` (cases TC-FE01-048 to 073, manual section rewritten with PowerShell steps and SQL), `docs/audit/FE-01_AI_Audit_Log.docx` (sections 9 and 10).

### FE-01 review fixes (round 3)
Same branch, new commits. Verified with `npm test` (157 tests, 16 new), `npm run lint -- --max-warnings 0`, `tsc -b`, `npm run build` and 8 browser checks. The 13 guards of this round were each broken once on purpose and caught by a test. No backend change.
- N1: a switch to another account is never silent. When another tab signs in as a different user, this tab opens the home page of the new user's role (never the 403 page) and every layout shows "You are now signed in as <name> because another account signed in in this browser." (dismissible). The role area is mounted again for the new user, so no page or form of the previous user survives. The same user logging in again, or an anonymous tab, shows nothing. The API client drops answers of requests that started before a session change (`StaleSessionError`) and never retries them with the new account's token. The round 2 decision to switch without a notice is reverted.
- N3: the sign up form and its field errors live in one state, updated functionally (`applyFieldChange`).
- N4: test for the focus after a server error on Sign up.
- N5: the sample account box needs `import.meta.env.DEV`; a production build never shows it and does not contain the values.
- N6: with an empty password every rule is shown as not met.
- N7: the login history cleanup keeps the query string and hash.
- N8: `sqlcmd -C` in the manual steps, with the SSMS alternative.
- Docs: test cases TC-FE01-074 to 089, browser check B08, manual cases M19 and M20; README Remember me section.

## 2026-10-12 - Frontend foundation: routing, API client, auth, layouts (FE-00, Shared)

Branch `feat/fe-foundation`. Status: Implemented and verified with `npm test` (68 tests after review round 3), `npm run lint` and `npm run build`, plus 25 browser checks against a mock of the auth API. Manual checks against the real backend (docs/tests/FE-00_test_cases.md section 3) must be run before merging. Builds on the Vite + React + TypeScript scaffold from PR #13; no backend change.

### API client (`frontend/src/api/`)
- `client.ts`: fetch wrapper with base URL from `VITE_API_BASE_URL` (default `http://localhost:8080/api/v1`), JSON request and response handling, query string builder, `Authorization: Bearer` header.
- On 401 the client calls `POST /auth/refresh` once and retries the request once. Parallel 401 responses share one refresh call, because the backend rotates the refresh token on every refresh. A rejected refresh (400, 401, 403) clears the tokens and notifies the auth context (logout); a network or server error keeps the session.
- Session epoch in `tokenStorage`: a refresh or failure that belongs to an older session (the user logged out or logged in again meanwhile) never writes or clears the tokens of the current session.
- `errors.ts`: `ApiError` with `status`, `message` and `fieldErrors` (from the `details` map of validation errors). Status 0 means the server could not be reached; tokens are kept in that case.
- `authApi.ts` (login, register, me), `types.ts` (`UserProfile`, `AuthTokens`, `Role`, `PageResponse`, `SpringPage`), `page.ts` (`normalizePage` for `GET /members`, which still returns a raw Spring page), `tokenStorage.ts` (localStorage, guarded with try/catch).

### Auth and routing (`frontend/src/auth/`, `frontend/src/routes/`)
- `AuthProvider` and `useAuth`: login, logout, current user from `GET /users/me`, role, session restore after reload, login and logout followed across browser tabs.
- `RequireAuth` (redirects to `/login` and remembers the requested page) and `RequireRole(roles)` (shows the 403 page in place).
- After login: back to the requested page when it belongs to the user's role, otherwise the role home (`/member`, `/reception`, `/manager`, `/coach`).
- `AppRoutes.tsx`: public routes (`/login`, `/register` placeholder for FE-01, `/ui-kit`), one protected area per role generated from `layouts/navigation.ts`, 403 and 404 pages. Later tasks register real screens in the `SCREENS` map.

### Layouts and UI (`frontend/src/layouts/`, `frontend/src/components/`, `frontend/src/styles/`)
- `PublicLayout`, `MemberLayout` (top bar and tabs, Figma F1-07), `StaffLayout` (side navigation, breadcrumb, Figma F1-01, F1-15, F2-13) with a collapsible menu below 960 px.
- Menus per role with placeholder pages naming the task and Figma screens that will replace them.
- Shared UI kit: Button, Input, Select, Modal (focus trap, focus restore, Escape for the top modal only, backdrop), Table with Pagination, Badge and StatusBadge, Toast, Spinner and PageLoader, EmptyState, ErrorState, Card, StatCard, PageHeader. All shown at `/ui-kit`.
- Design tokens in `styles/tokens.css` sampled from the Figma screens (primary `#2169ba`, navy `#102e50`, accent `#ffad66`, page `#f5f8fc`).
- Formatters in `utils/format.ts`: `formatMoney` (`1.350.000 VND`), `formatDate` (`04 Jul 2027`, LocalDate read without time zone shift), `formatDateTime`, `formatTime`, `humanizeCode`, `initials`.
- Log in page (Figma F1-05) working end to end; FE-01 finishes sign up and the final copy.

### Tooling
- Added dev dependencies `vitest`, `jsdom`, `@testing-library/react`, `@testing-library/dom`, `@testing-library/user-event`, `@testing-library/jest-dom`; scripts `npm test` and `npm run test:watch`.
- `vite.config.ts`: dev server pinned to port 5173 (`strictPort`) to match the backend CORS default; Vitest config.
- Added `frontend/.env.example`; removed the Vite starter assets (`App.css`, `assets/`, `public/icons.svg`).
- `frontend/README.md`: how to call the API, use the auth context, add a screen and use the UI kit.

### Docs
- `docs/tests/FE-00_test_cases.md`: 70 automated cases (68 tests plus build and lint), 25 browser checks, 16 manual cases for the real backend.
- `docs/audit/FE-00_AI_Audit_Log.docx`.

### Review round 2
- Step 1 (report only, no backend change): with the current backend a missing, expired or malformed access token on a protected endpoint returns 401, never 403 (JwtAuthenticationFilter.java line 64 catches JwtException, SecurityConfig.java lines 51-58 send anonymous requests to CustomAuthenticationEntryPoint, which sets 401 at line 21). Two latent backend risks are written up in the AI audit log with proposed fixes: an AccessDeniedException for an anonymous user would become 403 in GlobalExceptionHandler.java lines 99-101 if @PreAuthorize is ever added to a permitAll URL, and a token of a deleted user escapes the JWT filter as UsernameNotFoundException.
- Session restore: new auth status `error`. A network error, 5xx or 429 while loading the profile keeps the tokens and shows a full page Retry screen (`SessionErrorPage`) instead of sending the user to `/login`. Only 401 or 403 after the refresh attempt, or a rejected refresh, end the session. `RequireAuth` and `HomeRedirect` handle the new status.
- Refresh: 404 from `/auth/refresh` (user no longer exists) now ends the session like 400, 401 and 403.
- Cross tab refresh: the refresh runs inside a Web Locks API lock (`navigator.locks.request`) with a fallback when it is missing. Inside the lock the stored tokens are read again and reused when another tab already refreshed. A rejected refresh reads storage again and keeps the session when another tab stored a newer refresh token. A rejected refresh ends the session while still holding the lock, so a waiting tab does not resend the same token.
- Storage events: a new `sportify.sessionId` key marks a login. Other tabs reload the profile only when it changes (login or logout elsewhere), not on token rotation, and a late profile answer from an older change is ignored.
- `/ui-kit` is registered only when `import.meta.env.DEV` is true and loaded lazily, so a production build contains neither the route nor the page (it shows 404).
- `package.json` `engines.node` set to `^22.13.0 || ^24.0.0 || >=26.0.0` (the range shared by Vite 8, Vitest 5, ESLint 10 and jsdom), new `frontend/.nvmrc` (24), README documents the Node requirement and the fixed port 5173 (`strictPort`).
- Table keeps the previous rows visible (dimmed, `aria-busy`) with a spinner overlay while loading another page.
- Toast messages are announced once: two permanent live regions (assertive for errors, polite for the rest), no role on each toast.
- Log in form checks email (required, basic format) and password (required) before sending; backend field errors still take precedence.
- Modal focus trap ignores hidden elements (`hidden`, `aria-hidden="true"`, `inert`, `display: none`, `visibility: hidden`) and pulls focus back into the dialog when it moved outside.
- 22 new tests (65 in total, 6 files); each new guard was broken on purpose once and its tests failed (15 of 15).

### Review round 3
- Refresh timeout: `POST /auth/refresh` runs while the tab holds the cross tab lock, so it now has a timeout of 15000 ms (constant `REFRESH_TIMEOUT_MS` in `frontend/src/api/client.ts`, AbortController and setTimeout, timer always cleared). A timeout behaves like a network error: `ApiError` status 0, tokens kept, no logout, and the lock is released so tabs waiting for it continue. Normal requests and caller supplied abort signals are unchanged.
- 3 new tests with fake timers (68 in total); the guard was broken on purpose three times and the tests failed each time.
- Text rules: the AI audit log row about the Node version requirement is now written in words. The bullet glyphs of the audit log list definitions (above code point 127) were replaced with a plain hyphen. In the BE-09 entry of this changelog, four arrows in prose were replaced with the word "gives" (wording only, meaning unchanged). A scan of the audit log, `frontend/README.md`, `docs/tests/FE-00_test_cases.md`, this changelog, `FE-00_CHANGES.md` and every file under `frontend/src` finds no character above code point 127 and no arrow or vertical bar pair in prose.

### Follow-ups
- Run the manual cases in docs/tests/FE-00_test_cases.md section 3 against the real backend before merging.
- Tokens are stored in localStorage (no httpOnly cookie support in the backend); never render untrusted HTML.
- Backend hardening proposals from review round 2 (R1: return 401 instead of 403 for anonymous AccessDeniedException; R2: catch UsernameNotFoundException in JwtAuthenticationFilter) are for the backend team to decide.
- The backend has no logout endpoint, so logout only drops the tokens on this device; issued refresh tokens stay valid until they expire (7 days).
- `GET /members` returns a raw Spring page; switching it to `PageResponse` would let screens drop `normalizePage`.

## 2026-10-11 - Class management for the manager (BE-09, Flow 2)

Branch `feat/f2-class-management`. Status: Implemented, unit-verified, integration tests pending Docker. Not built or run against a real DB here: run mvn clean verify before merging (Testcontainers tests need Docker).

### Endpoints
- `GET /api/v1/manager/classes?sportId=&level=&coachId=&active=&search=&page=&size=`: paged class list (`PageResponse<ManagerClassResponse>`) with sportId, level, coachId, active, case-insensitive search (name, code, or coach name), and paging. Role `MANAGER`.
- `GET /api/v1/manager/classes/{id}`: single class detail with `upcomingSessions`. Role `MANAGER`.
- `POST /api/v1/manager/classes`: creates a sport class (201). Body: `name`, `sportId`, `ageGroupId`, `level`, `maxMembers`, `coachId`. Generates code `CL-%04d` using `seq_class_code`. Validates coach is active and teaches sport. Role `MANAGER`.
- `PUT /api/v1/manager/classes/{id}`: updates class `name`, `sportId`, `ageGroupId`, `level`, `maxMembers`, `coachId`. Validates coach teaches sport (even if coach unchanged). If sport changes, rejects with 400 if class has upcoming sessions. Recomputes `upcomingSessions` using single clock read. Role `MANAGER`.
- `PATCH /api/v1/manager/classes/{id}/status`: toggles class active status. Deactivating a class with confirmed future bookings returns 400. Reuses `upcomingSessions` using single clock read. Role `MANAGER`.
- `GET /api/v1/age-groups`: dropdown options (`List<AgeGroupResponse>`) ordered by displayOrder then id. Roles `MANAGER`, `RECEPTIONIST`.
- `GET /api/v1/coaches?sportId=`: active coach options (`List<CoachOptionResponse>`) filtered optionally by sportId. Roles `MANAGER`, `RECEPTIONIST`.

### Backend
- Repositories and audit:
  - `SportClassRepository`: added `findWithDetailsById(Long)` and overrode `findAll(Specification, Pageable)` with `@EntityGraph(attributePaths = {"sport", "ageGroup", "coach", "coach.userAccount"})` to eliminate N+1 queries without unsafe criteria Fetch-to-Join casting (`getNextClassCodeSequence()` already existed on main).
  - `AgeGroupRepository`: added `findByIsActiveTrueOrderByDisplayOrderAscIdAsc()`.
  - `BookingRepository`: added `countUpcomingByClass(...)`.
  - `ClassSessionRepository`: added `countUpcomingByClassIds(...)`.
  - `CoachProfileRepository`: added `findWithAccountById(...)`, `findActiveCoaches()`, and `findActiveCoachesBySport(Long)`.
  - `CoachSportRepository`: added `findByIdCoachIdIn(...)` (`existsById` is inherited from `JpaRepository`).
  - `AuditAction`: added `CLASS_CREATED`, `CLASS_UPDATED`, `CLASS_STATUS_CHANGED`.
  - Parameter cast in upcoming queries: cast `:now` parameter to SQL Server `time` (`s.startTime > cast(:now as time)`) in `ClassSessionRepository.countUpcomingByClassIds` and `BookingRepository.countUpcomingByClass` because mssql-jdbc defaults `sendTimeAsDatetime=true` and sends `LocalTime` as `datetime`, which SQL Server rejects when comparing against a `TIME(0)` column.
- Query specifications and helper:
  - `SearchUtils`: shared helper escaping `\`, `%`, `_`, and `[` with `LIKE_ESCAPE = '\\'`. Reused across `SportClassSpecifications` and `UserSpecifications`.
  - `SportClassSpecifications`: criteria specifications for `sportId`, `level`, `coachId`, `active`, and `search` using `root.join(...)` and `SearchUtils.escapeLike`.
- Services and logic:
  - `ManagerClassServiceImpl`: implements class listing, detail, creation, update, and deactivation guard. Single `LocalDateTime.now(clock)` read per transaction reused across validation and response mapping.
  - `AgeGroupServiceImpl` and `CoachDirectoryServiceImpl`: dropdown services.
  - Role-based security on `/api/v1/manager/classes/**` (MANAGER) and `/api/v1/age-groups`, `/api/v1/coaches` (MANAGER, RECEPTIONIST).

### Tests
- `SearchUtilsTest`: unit tests verifying literal escaping for `\`, `%`, `_`, `[`, empty string, and null inputs.
- `UserSpecificationsTest`: unit tests verifying pattern construction including `\[test]`.
- `ManagerClassServiceImplTest`: unit tests covering create with `CL-xxxx`, coach qualification failure (400), coach unchanged on PUT but no longer qualified (400), sport change blocked with upcoming sessions (400), deactivation blocked with confirmed future booking (400), deactivation allowed without confirmed booking, upcoming sessions count logic, and single clock read. All null assertions use `assertNull`.
- `AgeGroupServiceImplTest` and `CoachDirectoryServiceImplTest`: unit tests for lookup services.
- `ManagerClassControllerTest`, `AgeGroupControllerTest`, `CoachControllerTest`: WebMvc controller slice tests for request validation, authorization matrix (401, 403 for non-manager), and status codes.
- `ManagerClassIntegrationTest`: Testcontainers integration test extending `AbstractIntegrationTest` (following `ManagerUserIntegrationTest` pattern). Covers list with sportId/level/coachId/active/search/paging; create gives CL-xxxx; coach not teaching the sport gives 400; deactivate with a confirmed future booking gives 400; a past session and a today-before-now session are not counted; non-manager gives 403. Clean up only rows it created.

### Follow-ups
- `sport_class.is_active` is not enforced by session creation or booking yet.
- Check-then-act races (e.g. concurrent bookings or session scheduling during class deactivation).
- No `@Version` on `sport_class` (no optimistic locking).

## 2026-10-10 - Payment, Invoice and InvoiceLine entities (BE-16)

Branch `feat/f3-payment-entities`. Status: Implemented, unit-verified, integration tests pending Docker. Not built or run here: run `cd backend && mvn clean verify` before merging (Testcontainers tests need Docker).

### Backend
- Mapped V14 tables (`payment`, `invoice`, `invoice_line`) to JPA entities `Payment`, `Invoice`, and `InvoiceLine` with exact database column types:
  - `Payment`: `amount` as `BigDecimal` (`precision = 12, scale = 2`), optimistic locking `version` annotated with `@Version` (`Integer`), `paymentCode` (`VARCHAR(30)` unique, not null), `paymentMethod` (`PaymentMethod`), `paymentStatus` (`PaymentStatus`), `referenceCode` (`VARCHAR(100)`), `notes` (`NVARCHAR(500)`), `paymentTime`, `createdAt`, and `updatedAt`.
  - `Invoice`: `invoiceNumber` (`VARCHAR(30)` unique, not null), `subtotalAmount`, `discountAmount`, `taxAmount`, and `totalAmount` as `BigDecimal` (`precision = 12, scale = 2`), `status` (`InvoiceStatus`), lazy `@ManyToOne` to `Payment` and `MemberProfile`, bidirectional `addLine(InvoiceLine)`, `issueDate`, `createdAt`, and `updatedAt`.
  - `InvoiceLine`: `itemType` (`InvoiceItemType`), `itemReferenceId` (`BIGINT`), `description` (`NVARCHAR(255)`), `quantity` (`INT`, default 1), `unitPrice` and `lineTotal` as `BigDecimal` (`precision = 12, scale = 2`), `createdAt`, and lazy `@ManyToOne` to `Invoice`.
- Enums stored as STRING: `PaymentMethod` (CASH, CREDIT_CARD, BANK_TRANSFER, MOMO, VNPAY, ZALOPAY), `PaymentStatus` (PENDING, SUCCESS, FAILED, REFUNDED), `InvoiceStatus` (ISSUED, PAID, CANCELLED, REFUNDED), `InvoiceItemType` (SPORT_PACKAGE, MEMBERSHIP_CARD, CLASS_DROP_IN, PENALTY_FEE).
- Formatted sequence codes in `CodeFormatter`: `formatPaymentCode(long)` (`PAY-%04d`) and `formatInvoiceNumber(long)` (`INV-%04d`).
- Repositories: `PaymentRepository` (native query `getNextPaymentCodeSequence()` for `seq_payment_code`), `InvoiceRepository` (native query `getNextInvoiceNumberSequence()` for `seq_invoice_number`), and `InvoiceLineRepository`.
- Foreign key mappings on dependent entities: mapped `payment_id` on `MemberCard`, `SportPackageRegistration`, and `RefundRequest` as nullable lazy `@ManyToOne Payment`.

### Tests
- `CodeFormatterTest`: unit tests for `formatPaymentCode` (0, 1, 42, 1000, 99999) and `formatInvoiceNumber` (0, 1, 42, 1000, 99999).
- `PaymentEntityMappingTest`: reflection tests verifying exact table and column annotations, data types, precision/scale, lengths, `@Version`, enum string storage, foreign key definitions, and lifecycle defaults.
- `PaymentDdlValidateIntegrationTest`: integration test for context startup with Hibernate `ddl-auto: validate` against database (not executed here; requires Docker).
- `PaymentRepositoryIntegrationTest`: Testcontainers repository integration test verifying persistence of payment and invoice with 2 lines, read-back assertion, sequence generation, check constraint enforcement (`ck_payment_status`, `ck_payment_method`, `ck_payment_amount`, `ck_invoice_status`, `ck_invoice_amounts`, `ck_invoice_line_item_type`, `ck_invoice_line_quantity`), optimistic locking version increments, stale copy optimistic locking failure, and foreign key linking (not executed here; requires Docker).

## 2026-10-10 - Map V20 columns: class coach and booking cancel actor (BE-08, Flow 2)

Branch `feat/f2-map-v20-columns`.

### Backend
- `SportClass`: mapped `coach` relationship (`ManyToOne CoachProfile`, column `coach_id`, nullable, lazy).
- `Booking`: mapped `cancelledBy` relationship (`ManyToOne UserAccount`, column `cancelled_by_user_id`, nullable, lazy).
- `BookingServiceImpl`:
  - `cancelBooking`: stores the cancellation actor on `booking.cancelledBy` (member, receptionist, or manager).
  - `mapToBookingResponse`: populates `cancelledByName` and `cancelledByRole` from `booking.cancelledBy`.
  - `createBooking`: runs duplicate confirmed booking check before session capacity check; catches duplicate confirmed booking on unique index `ux_booking_active` and throws `ConflictException` (HTTP 409) with clear message "Member already has a confirmed booking for this session". Pre-check also throws `ConflictException`.
  - Extracted `DUPLICATE_BOOKING_MESSAGE` constant and case-insensitive check for `ux_booking_active`.
- `BookingResponse`: added `cancelledByName` and `cancelledByRole` fields.
- `ConflictException`: maintains `extends RuntimeException`; mapped to HTTP 409 Conflict via `GlobalExceptionHandler`.
- `GlobalExceptionHandler`: extracted `DUPLICATE_BOOKING_MESSAGE` constant; maps `ux_booking_active` unique constraint violations case-insensitively to HTTP 409 Conflict with message "Member already has a confirmed booking for this session".

### Tests
- `BookingServiceImplTest`: updated tests to assert `ConflictException` on duplicate booking, added unit test verifying duplicate booking on a full session throws 409 before capacity check, and verified member, receptionist, and manager cancellation actors (31 tests total).
- `BookingControllerTest`: added WebMvc tests covering cancellation actor response and duplicate booking 409 conflict handling.
- `V20EntityMappingTest`: added reflection-based unit tests validating JPA mapping annotations (`@ManyToOne(fetch = LAZY)`, `@JoinColumn`).
- `BookingV20IntegrationTest`: added integration tests against SQL Server validating cancellation actor persistence for RECEPTIONIST, MEMBER, and MANAGER, `SportClass` coach persistence (with and without coach), duplicate booking throwing `ConflictException`, and cancel-then-rebook behavior. Not run locally: run `cd backend && mvn clean verify` before merging (the Testcontainers test needs Docker); they will be verified by the GitHub Actions CI run on the pull request.

## 2026-10-10 - Manager user and staff account management (BE-01, F1-01)

Branch `feat/f1-manager-user-management`. API behind screen F1-01 "User Management" and the overlay "Add Staff Account". Not built or run here: run `cd backend && mvn clean verify` before merging (the Testcontainers test needs Docker).

### Endpoints (role MANAGER only, other roles get 403)
- `GET /api/v1/manager/users?role=&status=&search=&page=&size=`: paged list (`PageResponse`) with id, full name, email, phone, role, status and created date, newest first. `search` matches name, email, phone or member code (case-insensitive, `%` and `_` are matched literally). Page size is capped at 100.
- `POST /api/v1/manager/users`: creates a RECEPTIONIST, COACH or MANAGER account (201). Body: `fullName`, `email`, `phone`, `temporaryPassword` (StrongPassword), `role`, `sportIds`.
  - A COACH also gets a `coach_profile` row (the first sport id is the primary sport) and one `coach_sport` row per sport id. At least one active sport is required; other roles must not send sports.
  - MEMBER accounts cannot be created here (400).
  - Duplicate email or phone returns 409. A weak password returns 400 with the field error under `details.temporaryPassword`.
- `PATCH /api/v1/manager/users/{id}/status`: body `{"status": "ACTIVE"}` or `{"status": "INACTIVE"}`. A manager cannot deactivate their own account (400). Setting the status an account already has changes nothing and writes no log row.

### Backend
- New `ManagerUserController`, `ManagerUserService` and `ManagerUserServiceImpl`, `ManagerUserMapper`, DTOs (`StaffAccountCreateRequest`, `UserStatusUpdateRequest`, `ManagerUserResponse`) and `UserStatus` enum.
- New `CoachSport` entity and `CoachSportRepository` for the existing `coach_sport` table (no migration needed).
- `UserRepository` now also extends `JpaSpecificationExecutor`; filters live in `UserSpecifications` (role is loaded in the same query, no N+1).
- `JwtAuthenticationFilter` no longer authenticates a request when the account is disabled, so deactivating a user also stops the access token that is still valid (up to 15 minutes). Login and token refresh were already refused for INACTIVE accounts.
- Activity log: `USER_CREATED` on create and `USER_STATUS_CHANGED` (from and to) on a status change, written through `AuditService` with the manager as actor. Passwords are never logged.

### Tests
- `ManagerUserServiceImplTest` (unit), `ManagerUserControllerTest` (WebMvc, includes the 401 and 403 matrix), `InactiveUserLoginTest`, `UserSpecificationsTest`.
- `ManagerUserIntegrationTest` (Testcontainers): list, filter, search and paging against SQL Server, coach rows, activity log rows, inactive user cannot log in.

### Known gaps
- The temporary password is not forced to change at first login (there is no column for it yet).
- The Add Staff overlay also shows "Skills" and "Certificate" fields; they are not part of this task.

## 2026-10-09 - CORS for the React frontend (BE-00)

Branch `feat/be-cors-config`.

### Backend
- `SecurityConfig`: enabled CORS (`http.cors`) with a `CorsConfigurationSource` bean.
  - Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS. Headers: Authorization, Content-Type. Credentials off. Max age 3600 seconds.
  - Preflight `OPTIONS` requests are answered before authorization and are also permitted explicitly, so they need no token.
  - Any origin that is not in the allow list is rejected with 403 (simple and preflight requests).
- New property `app.cors.allowed-origins` (comma separated):
  - `application.yml`: `${CORS_ALLOWED_ORIGINS:}` (empty = no cross-origin request is accepted).
  - `application-dev.yml`: defaults to `http://localhost:5173,http://localhost:3000`.
  - `application-prod.yml`: reads `CORS_ALLOWED_ORIGINS` from the environment, no localhost default.
- `.env.example`: documented `CORS_ALLOWED_ORIGINS`.

### Tests
- `SecurityConfigCorsTest` (WebMvcTest): allowed origins, unknown origin, preflight on a protected endpoint without a token, allowed methods, headers and max age, rejected method and header.
- `SecurityConfigCorsEmptyOriginsTest` (WebMvcTest): an empty allow list rejects every origin.
- `SecurityConfigCorsSourceTest`: property parsing and fixed policy.
- `CorsPropertiesConfigTest`: where the property is defined (yml files and `.env.example`).

### Deploy note
- Production must set `CORS_ALLOWED_ORIGINS` (for example `https://app.example.com`). Without it the browser frontend cannot call the API.

## 2026-10-09 - Repository split into backend/ and frontend/

### Structure
- Moved the Spring Boot project into `backend/` (`src`, `pom.xml`, `.env.example`, `docker-compose.yml`, `scaffold.ps1`, `scripts`). Git keeps the file history (renames).
- New `frontend/` folder for the React app (created by task FE-00), with a README.
- `docs/`, `context/`, `.github/`, `README.md`, `CHANGELOG.md` stay at the root.

### What changes for every member
- Run every Maven command inside `backend/`: `cd backend`, then `mvn clean verify` or `mvn spring-boot:run`.
- Move your `.env` from the root to `backend/.env` (the app reads `.env` from the folder it runs in).
- CI builds and tests `backend/`.

### Also in this change
- `TODO.md`: next tasks for Flow 1-3.
- `docs/database/schema/`: reference of all 58 tables generated from migrations V1-V20.
- `.gitignore`: `node_modules/`, `dist/`, `.vite/` for the frontend.
- `docs/AI_Audit_Log.docx`: entries for 2026-10-08 and 2026-10-09.

## 2026-10-08 - Membership cards, end dates, schema V20

Built on top of `feat/figma-schema-sync` (V13-V19). Merge that branch first. Not yet built or tested by the team: run `mvn clean verify` (unit tests, and the Testcontainers tests when Docker is running) before merging.

### Database
- **New migration `catalog/V20__align_schema_with_topic.sql`** (runs after V19; only columns, checks and indexes; `ddl-auto: validate` keeps passing):
  - `member_card.status` also allows `PENDING_PAYMENT` and `REPLACED`.
  - `sport_class.coach_id` (FK `coach_profile`): coach in charge of a class.
  - `booking.cancelled_by_user_id` (FK `user_account`): who cancelled a booking.
  - `ux_booking_active (member_id, session_id) WHERE status = 'CONFIRMED'`: no double booking. Fails if a dev database already holds duplicate confirmed bookings; cancel the extra one first.

### Membership cards (`MembershipCardServiceImpl`)
- `CardStatus` gains `PENDING_PAYMENT` and `REPLACED`.
- A member's online card request is `PENDING_PAYMENT` and gives no discount until paid. A purchase by a receptionist or manager is paid at the desk and is `ACTIVE` at once.
- New endpoints:
  - `PUT /api/v1/membership-cards/{id}/activate` (RECEPTIONIST, MANAGER): confirms the payment; dates are recomputed from the payment date.
  - `PUT /api/v1/membership-cards/{id}/cancel` (MEMBER for their own request, RECEPTIONIST, MANAGER): cancels an unpaid request.
- One card per member:
  - A second request is refused while one waits for payment.
  - Same tier: renewal from the day after the current card ends.
  - Dearer tier while a card is valid: upgrade from today that keeps the current end date. The member pays only the price difference, and the old card becomes `REPLACED` when the payment is confirmed.
  - Cheaper tier: starts the day after the current card ends, full price.
  - An upgrade is refused while a renewal is already paid.
- A card that has not started yet gives no discount. Card tiers that are not on sale cannot be bought.
- Card end date = start + months - 1 day (was one day too long).

### Sport packages (`SportPackageServiceImpl`)
- End date = start + days - 1 day, at registration and at activation (a 30-day package from 1 Nov ends 30 Nov; a single visit ends the same day).

### Tests
- `MembershipCardServiceImplTest`: 16 new tests (pending request, desk purchase, one pending request, inactive tier, upgrade online and at the desk, cheaper tier, upgrade after renewal, activation, upgrade activation, ended card, already active, cancel own / other member / paid card, discount before start). 2 existing tests updated for the new status and renewal dates.
- `SportPackageServiceImplTest`: 2 new tests (30-day and single-visit end dates), 1 updated (activation recomputes 30 days counting today).
- `RefreshedCatalogAuthorizationTest`: 4 new tests for who may activate or cancel a card.
- `V20SchemaIntegrationTest` (new, Testcontainers): V20 applied, new card statuses, unknown status rejected, new columns, double booking rejected, rebooking after cancel allowed.

### Documentation (`docs/database`)
- 02, 03: `member_card` statuses, `member_card.payment_id` (V14), `sport_class.coach_id`, `booking.cancelled_by_user_id`, `ux_booking_active`.
- 04 rewritten to match the real V14 tables: `payment` (`payment_status` = PENDING, SUCCESS, FAILED, REFUNDED; methods CASH, CREDIT_CARD, BANK_TRANSFER, MOMO, VNPAY, ZALOPAY), `invoice`, `invoice_line`. The planned view `vw_paid_payment` and the report queries now use these columns.
- 09: member card state machine with PENDING_PAYMENT and REPLACED, payment state machine uses SUCCESS, card renewal and upgrade rules, a "Rules vs. Code" table, planned 7-day job.
- README, 12, 13: V1-V20. ERD: new columns on `sport_class` and `booking`.
- `docs/AI_Audit_Log.docx`: entries for 2026-10-08.

### Not done
- Scheduled job that cancels unpaid card requests after 7 days.
- Mapping `sport_class.coach_id`, `booking.cancelled_by_user_id` and `member_card.payment_id` in the entities.
- Creating a `payment` row when a card is activated (V14 table has no entity yet).
- Legacy `MembershipServiceImpl` still computes `end = start + duration` (deprecated flow, untouched).
