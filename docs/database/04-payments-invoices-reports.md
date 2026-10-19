# 04 - Payments, Invoices and Reports

Screens covered: F3-01 Payment Summary, F3-02 Payment Instructions & Status, F3-03 My Payments & Receipts,
F3-04 Payment Requests, F3-05 Verify & Record Payment, F3-06 Payment Confirmed & Receipt,
F3-07 Refund Requests, F3-08 Financial Overview, F3-09 Transactions & Refund Approval, F3-10 Revenue Reports.

## ERD

```mermaid
erDiagram
    member_profile ||--o{ payment : "pays"
    payment ||--o{ member_card : "pays for (member_card.payment_id)"
    payment ||--o{ sport_package_registration : "pays for (payment_id)"
    payment ||--o{ invoice : "issues"
    invoice ||--|{ invoice_line : "contains"
    payment ||--o{ refund_request : "target of"
    sport_package_registration ||--o{ refund_request : "refunded from"
    user_account ||--o{ refund_request : "requests / reviews"
```

Implemented in migration V14 (payment, invoice, invoice_line, payment_id on member_card,
sport_package_registration and refund_request). `refund_request` is from V10/V11.
JPA entities `Payment`, `Invoice` and `InvoiceLine` map these tables (BE-16). V21 made `payment_time` nullable
and added `confirmed_by_user_id` (BE-17).

## Design Decisions

- **One payment row, linked from what it pays for.** `member_card`, `sport_package_registration` and `refund_request` hold a nullable `payment_id`. A payment does not point back to the item; `invoice_line.item_type` + `item_reference_id` say what was bought.
- **Payment requests and verification (F3-04, F3-05).** A `PENDING` payment is created when a package or card is requested online. The receptionist checks the money, records `reference_code`, and sets `payment_status = 'SUCCESS'`. The linked card or package is then activated (`PENDING_PAYMENT -> ACTIVE`).
- **Card discount on invoices.** A Gold (5%) or VIP (10%) discount is computed at package registration and shown in `invoice.discount_amount` and the invoice lines. Single visits get no discount.
- **Refund Request Workflow (F3-07, F3-09).** Receptionist or member files a request, the Center Manager approves or rejects it with a note, approved refunds become `COMPLETED` on payout, and the payment becomes `REFUNDED` (rules under `refund_request`, BE-21).
- **Invoice = snapshot.** An invoice is issued for a successful payment. `invoice.payment_id` is indexed but not unique.
- **Revenue is computed, not stored.** BE-22 reports read `dbo.vw_paid_payment` (V30), filtered by `payment_time` and current `SUCCESS` status. `PENDING`, `FAILED` and `REFUNDED` payments stay in the ledger but are not paid revenue. Completing even a partial BE-21 refund removes the whole original payment from paid revenue; this metric is not gross receipts or net recognized revenue.
- **Front desk verification (BE-18).** `GET /api/v1/payments` lists payment requests for RECEPTIONIST and MANAGER, `PENDING` first (oldest first), then the rest newest first; filters `status`, `method`, `search` (member name, member code, payment code, reference code; `query` is accepted as another name), `from` and `to` (inclusive dates on `created_at`), `page`, `size` (1 to 100, default 20).
- **Confirm (F3-05, F3-06).** `PUT /api/v1/payments/{id}/confirm` with `method`, `referenceCode` (required for every method except `CASH`, otherwise 400), `amountReceived`, `note`. In one transaction that starts by locking the payment row (`PaymentRepository.findByIdForUpdate`, PESSIMISTIC_WRITE): `reference_code` is stored, the linked card (`MembershipCardService.activateCard`) or registration (`SportPackageService.activateRegistration`) becomes `ACTIVE` with dates recomputed from the confirmation day, the payment becomes `SUCCESS` with `payment_time` now and `confirmed_by_user_id`, then `PaymentVerificationService.recordPaymentConfirmed` issues an `invoice` (`PAID`) with one `invoice_line` and writes `activity_log` `PAYMENT_CONFIRMED`. The receipt is read with `GET /api/v1/invoices/by-payment/{paymentId}` or `GET /api/v1/invoices/{id}` (staff, or the member who owns the payment; see BE-20 below).
- **Invoice amounts.** The line is written at the price before discount, from `PaymentItemResolver`, using only values stored when the payment request was made, never the current catalog price: `sport_package_registration.original_price` for a package; for a card, `member_card.price_paid` plus, for an upgrade (`upgrade_request = 1`), the upgrade credit = the `price_paid` of the replaced card (`replaces_card_id`) and of every card it replaced in turn, which is the base BE-17 subtracted from the tier price. A new card or renewal has no discount. When an upgrade's chain cannot be read (no `replaces_card_id`, a missing price, a chain longer than the BE-17 limit), no credit is claimed. A tier or package price edited after the request changes nothing on the invoice. `subtotal_amount` is that price, `discount_amount` is the card discount (package) or the upgrade credit (card upgrade, the line description says so), `tax_amount` is 0 and `total_amount` equals `payment.amount`. If `subtotal - discount` does not equal `payment.amount` (the item's `paid_amount` or `price_paid` differs from the payment: corrupted data), the API answers 409 `PAYMENT_ITEM_AMOUNT_MISMATCH` and nothing is written.
- **Counter activation (FE-05).** `PUT /api/v1/membership-cards/{id}/activate` and `PUT /api/v1/packages/registrations/{id}/activate` keep their request (`paymentMethod` only, no reference needed; the amount received is the payment amount) and call the same `recordPaymentConfirmed` after activation, so a payment confirmed through either path has exactly one invoice and one `PAYMENT_CONFIRMED` row.
- **Amount mismatch (S3-Mismatch).** When `amountReceived` differs from `payment.amount` the API answers 409 with `code: PAYMENT_AMOUNT_MISMATCH`. The payment stays `PENDING`; a note with both amounts, the method, the reference and the staff user id is appended to `payment.notes` and an `activity_log` row `PAYMENT_AMOUNT_MISMATCH` is written (details: `amount`, `amountReceived`, `method`, `referenceCode`, `note`), both in the transaction that answers 409 and is committed. No activation, no invoice, no `PAYMENT_CONFIRMED` row. The same payment can be confirmed later with the right amount.
- **Double confirmation.** Confirm, fail and the card and package activate and cancel endpoints all lock the payment row before the card or registration (lock order rule in docs/database/09), so a second call waits for the first to commit and then sees the current state: confirm and fail answer 409 `PAYMENT_NOT_PENDING`, activate and cancel answer 400 because the item is no longer PENDING_PAYMENT. `payment.version` stays as a backstop for a write that skips the lock: a failed version check inside confirm or fail answers 409 `PAYMENT_CONCURRENT_UPDATE`. At most one invoice is issued per payment (`INVOICE_ALREADY_ISSUED` otherwise).
- **Fail.** `PUT /api/v1/payments/{id}/fail` with `reason` (required, up to 255 characters): the linked card or registration request is cancelled (`CANCELLED`, the reason is also stored in `sport_package_registration.cancel_reason`), the payment becomes `FAILED` with the reason appended to `notes`, and `activity_log` gets `PAYMENT_FAILED`. Cancelling the item on fail is pending team confirmation. The member requests the item again, which creates a new payment row.
- **Desk sale (BE-19, F1-13, F1-14).** When RECEPTIONIST or MANAGER sells a card (`POST /api/v1/membership-cards/purchase`) or a package (`POST /api/v1/packages/registrations`, channel `RECEPTION`), the request may carry `paymentMethod` (alias `method`; `CASH` when omitted), `referenceCode` (aliases `paymentReference`, `reference`; required for every method except `CASH`) and `amountReceived` (the price when omitted). In the same transaction a `payment` row is created already `SUCCESS` (`payment_time` now, `confirmed_by_user_id`, `reference_code`), linked through `payment_id`, the item is `ACTIVE`, and `PaymentVerificationService.recordPaymentConfirmed` (the BE-18 receipt step) issues the `PAID` invoice and writes `PAYMENT_CONFIRMED`. Cash may be more than the price: the change is written in `payment.notes`. Less money, or a non-cash amount that differs, answers 409 `PAYMENT_AMOUNT_MISMATCH` and nothing is saved. A sale with nothing to pay (STANDARD tier, zero fee upgrade, 100 percent discount) creates no payment. The response carries `paymentId`, `paymentCode`, `invoiceId` and `invoiceNumber` so the desk screen opens the receipt.
- **Visibility (BE-17, BE-18, BE-20).** A member reads only their own payments (`GET /api/v1/payments/{id}`, `PUT /api/v1/payments/{id}/method`); another member's payment answers 404 (BE-17 contract, unchanged). A member reads only the invoices of their own payments (`GET /api/v1/invoices/{id}`, `GET /api/v1/invoices/by-payment/{paymentId}`); another member's answers 403 (BE-20 acceptance criterion; BE-18 answered 404). Staff read every payment and invoice.
- **Desk sale atomicity (BE-19).** The payment, the card or registration, the invoice with its line and the `PAYMENT_CONFIRMED` row are written in one transaction; if any step fails, none of them is kept.
- **Invoice totals.** `subtotal_amount` equals the sum of `invoice_line.line_total`, and `total_amount = subtotal_amount - discount_amount + tax_amount` (tax is 0 today).
- **Not stored:** the amount received is not a column. A confirmed payment always received exactly `amount`, so `PAYMENT_CONFIRMED` details hold the amount, method, reference, item and invoice number; a different amount received is kept only in the `PAYMENT_AMOUNT_MISMATCH` details.
- **One pricing rule.** `PaymentItemResolver` gives the item name, original price and discount for `GET /api/v1/payments/{id}`, `GET /api/v1/payments` and the invoice: original price = registration `original_price`, or card `price_paid` + upgrade credit (see Invoice amounts); discount = original price - registration `paid_amount` or card `price_paid`, never below zero.
- **List date filters.** `from` and `to` are inclusive days on `payment.created_at`. Dates outside 0001-01-01 to 9999-12-31 (the `datetime2` range) answer 400; `to = 9999-12-31` leaves the upper bound open instead of sending an out of range value.
- **My payments and receipts (BE-20, F3-03, S3-PaidHistory, receipt screens).** `GET /api/v1/payments/my?status=&page=&size=` (MEMBER only, 403 for staff): the signed-in member's payments, newest first (`created_at` then `id`, descending), `size` 1 to 100 (default 20). Each row has code, item (type, code, description, original price and discount from the one pricing rule above), amount, method, status, `time` (`payment_time` once confirmed, otherwise `created_at`) and `invoiceId` / `invoiceNumber` once a receipt exists. `GET /api/v1/invoices/{id}` and `GET /api/v1/invoices/by-payment/{paymentId}` return the invoice header (number, issue date, member name and code, payment method and reference, subtotal, discount, tax, total, status) and its lines in insert order. Staff read every invoice; a member reads only the invoices of their own payments and gets 403 for another member's invoice or for the invoice of another member's payment (BE-20 acceptance criterion); an unknown id, or a payment with no invoice yet, is 404. Reading a receipt re-checks the Invoice totals rule above; a stored invoice that breaks it is still returned as recorded (it is the legal record) and logged as a warning.

## Tables

### `payment` (V14)

| Column | Type | Null | Key / Default | Description |
|---|---|---|---|---|
| id | BIGINT | No | PK, IDENTITY | |
| payment_code | VARCHAR(30) | No | UQ `uq_payment_code` | From `seq_payment_code` (starts at 1000) |
| member_id | BIGINT | No | FK -> member_profile.user_id | Paying member |
| amount | DECIMAL(12,2) | No | CHECK > 0 | Amount to pay |
| payment_method | VARCHAR(30) | No | CHECK | `CASH`, `CREDIT_CARD`, `BANK_TRANSFER`, `MOMO`, `VNPAY`, `ZALOPAY` |
| payment_status | VARCHAR(20) | No | CHECK | `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED` |
| reference_code | VARCHAR(100) | Yes | | Bank, card slip or e-wallet reference, recorded at confirmation (BE-18) |
| notes | NVARCHAR(500) | Yes | | Staff notes joined with " \| ", oldest dropped first; holds mismatch and failure notes |
| payment_time | DATETIME2(0) | Yes (V21) | | Set when the payment becomes `SUCCESS` (`ck_payment_success_time`), drives the revenue period |
| confirmed_by_user_id | BIGINT | Yes (V21) | FK -> user_account.id | Staff user who confirmed the payment |
| version | INT | No | 0 | Optimistic lock, stops a double confirmation |
| created_at, updated_at | DATETIME2(0) | No | SYSDATETIME() | Audit |

Indexes: `ix_payment_member_id (member_id)`, `ix_payment_payment_time (payment_time)`, `ix_payment_status (payment_status)`.

### `invoice` (V14)

| Column | Type | Null | Key / Default | Description |
|---|---|---|---|---|
| id | BIGINT | No | PK, IDENTITY | |
| invoice_number | VARCHAR(30) | No | UQ `uq_invoice_number` | From `seq_invoice_number` |
| payment_id | BIGINT | No | FK -> payment.id | Payment that produced the invoice |
| member_id | BIGINT | No | FK -> member_profile.user_id | |
| subtotal_amount | DECIMAL(12,2) | No | CHECK >= 0 | Before discount and tax |
| discount_amount | DECIMAL(12,2) | No | 0 | Card discount |
| tax_amount | DECIMAL(12,2) | No | 0 | |
| total_amount | DECIMAL(12,2) | No | CHECK >= 0 | |
| status | VARCHAR(20) | No | CHECK | `ISSUED`, `PAID`, `CANCELLED`, `REFUNDED` |
| issue_date | DATETIME2(0) | No | SYSDATETIME() | |
| created_at, updated_at | DATETIME2(0) | No | SYSDATETIME() | Audit |

Indexes: `ix_invoice_payment_id`, `ix_invoice_member_id`, `ix_invoice_issue_date`.

### `invoice_line` (V14)

| Column | Type | Null | Key / Default | Description |
|---|---|---|---|---|
| id | BIGINT | No | PK, IDENTITY | |
| invoice_id | BIGINT | No | FK -> invoice.id (cascade) | |
| item_type | VARCHAR(30) | No | CHECK | `SPORT_PACKAGE`, `MEMBERSHIP_CARD`, `CLASS_DROP_IN`, `PENALTY_FEE` |
| item_reference_id | BIGINT | No | | Id of the package registration, card, etc. (no FK) |
| description | NVARCHAR(255) | No | | |
| quantity | INT | No | 1, CHECK > 0 | |
| unit_price | DECIMAL(12,2) | No | | |
| line_total | DECIMAL(12,2) | No | | |
| created_at | DATETIME2(0) | No | SYSDATETIME() | |

Index: `ix_invoice_line_invoice_id`.

## Reporting Views (BE-22, V30)

`V30__create_finance_report_views.sql` is a new forward-only migration after normal V27 and opt-in demo V28/V29. All older migrations remain unchanged. It requires current-main tables in `dbo` and explicitly fails before DDL if either finance view or the named payment index already exists. Historical finance V25/V28 are not added to main. Flyway validation, clean protection and ordered migration remain enabled.

- `dbo.vw_payment_ledger`: one row per payment, every status; `ledger_time = COALESCE(payment_time, created_at)`. The member comes from the payment's member FK. Unique filtered payment links prevent package/card join fanout. The earliest invoice and its earliest line are chosen with ordered `TOP 1` applies, so multiple invoices/lines do not multiply payment amounts. Item attribution prefers the linked package, then card, then first invoice line. Sport is nullable.
- `dbo.vw_paid_payment`: the ledger restricted to current `payment_status = 'SUCCESS'`, with payment-time and paid-date columns. Revenue sums payment amounts once, never invoice line totals. The view is a current-state report: refund completion can change previously reported months.
- `ix_payment_status_time`: `(payment_status, payment_time)` including amount, method and member ID. Each view has its own SQL Server `GO` batch.

Manager-only endpoints under `/api/v1/manager/finance`:

| GET route | Query and result |
|---|---|
| `/overview` | Optional `month=yyyy-MM`; current/previous paid totals, average, pending by created time, completed refunds separately, nullable change percentage when previous paid revenue is zero |
| `/trend` | `months` 1..24, default 6; ascending calendar months ending now, including zero months |
| `/transactions` | Optional status/method/type/from/to/search; zero-based page, size 1..100 (default 20); literal escaped contains search; stable ledger-time/id descending order |
| `/revenue` | Inclusive from/to, default current month through today; at most 366 days; groupBy ITEM_TYPE (default), METHOD or SPORT; rounded shares sum to 100.00 when total is positive |
| `/revenue/export` | Same date bounds/range; at most 50000 actual rows; UTF-8 BOM, CRLF, quoted text, formula protection, numeric amounts; attachment `revenue_<from>_<to>.csv` |

Finance dates must be 1900-01-01 through 9999-12-30; overview accepts 1900-01 through 9999-11. Inclusive end dates become exclusive next-day bounds without overflowing SQL Server DATETIME2. Application and audit clocks use Vietnam local time. These bounds do not change BE-18's existing list rules.

Completed refund totals use approved amount, or requested amount for legacy rows without approval. Period attribution prefers the earliest `REFUND_COMPLETED` activity log, then legacy updated/reviewed/created timestamps. Approval alone is not payout. Finance never changes payment/refund state. See [BE-22 test cases](../tests/BE-22_test_cases.md) for pending SQL Server execution gates.

### `refund_request`

Front desk refund filing and Manager review/approval workflow (F3-07, F3-09).

| Column | Type | Null | Key / Default | Description |
|---|---|---|---|---|
| id | BIGINT | No | PK, IDENTITY | |
| refund_code | NVARCHAR(30) | No | UQ | `REF-1000` |
| payment_id | BIGINT | Yes | FK -> payment.id, UQ filtered (V27) | Original payment (added in V14, set on every new refund since BE-21). `ux_refund_request_payment_live`: one row per payment with status `PENDING`, `APPROVED` or `COMPLETED` |
| package_registration_id | BIGINT | No | FK -> sport_package_registration.id | Package being refunded |
| member_id | BIGINT | No | FK -> member_profile.user_id | Member receiving refund |
| amount_requested | DECIMAL(14,2) | No | CHECK > 0 | Amount requested |
| amount_approved | DECIMAL(14,2) | Yes | CHECK >= 0 | Amount approved by Manager |
| reason | NVARCHAR(500) | No | | Reason for refund |
| status | NVARCHAR(20) | No | `PENDING` | `PENDING`, `APPROVED`, `REJECTED`, `COMPLETED` |
| requested_by | BIGINT | No | FK -> user_account.id | Member or Receptionist |
| reviewed_by | BIGINT | Yes | FK -> user_account.id | Center Manager |
| reviewed_at | DATETIME2(0) | Yes | | Review timestamp |
| manager_note | NVARCHAR(500) | Yes | | Manager decision note |
| created_at, updated_at | DATETIME2(0) | | | Audit |

Refund rules (BE-21, F3-07, F3-09 refund part, S3-RefundSubmitted to S3-RefundDone):

- **Quote.** `GET /api/v1/refunds/quote?registrationId=` (owner MEMBER, RECEPTIONIST, MANAGER): suggested amount = `paid_amount` * unused sessions / `total_sessions`, rounded half up to 2 decimals, never above the payment amount. Unused sessions = `remaining_sessions` + CONFIRMED bookings of the registration on sessions that have not started and have no allowed check-in (booking already took one session; completion cancels it). Every session used -> suggested 0.00.
- **Submit.** `POST /api/v1/refunds` with optional `amountRequested` (default: the quote). Order of service checks: registration exists (404), member owns it (403), it has a payment (409 `PAYMENT_NOT_REFUNDABLE`), the payment pays only for this registration (409 `PAYMENT_REGISTRATION_MISMATCH`), the payment has no live refund and is not `REFUNDED` (409 `PAYMENT_ALREADY_REFUNDED`), the payment is `SUCCESS` (409 `PAYMENT_NOT_REFUNDABLE`), the registration is `ACTIVE` (400), there is an unused session (400), `amountRequested` is above 0 and not above the payment amount (400). Request validation first rejects an explicit non-positive amount or more than 2 decimals. `refund_request.payment_id` is set. The payment row is locked first; the filtered unique index catches a race (409 `PAYMENT_ALREADY_REFUNDED`).
- **Review.** Approving moves the registration to `REFUNDED`. The resolved `amountApproved` (explicit or requested amount) must be above 0, have at most 2 decimal places after trailing zeros are removed, and, for a refund with a payment, not exceed the payment amount (400). It is stored at scale 2 without rounding. For a legacy refund with no payment, it must not exceed the registration `paid_amount` when that amount is known. The HTTP request also enforces at most 12 integer digits and 2 fractional digits. Payment row, then refund row, are locked first.
- **Complete.** `PUT /api/v1/refunds/{id}/complete` in one transaction: payment `SUCCESS` -> `REFUNDED` with a note, every `PAID` or `ISSUED` invoice of the payment -> `REFUNDED`, registration `REFUNDED` (kept if already), upcoming CONFIRMED bookings of the registration (not checked in) -> `CANCELLED` with `cancelled_by_user_id`, reason `Refund completed: <code>` and the session seat released (sessions are not returned to the refunded registration), refund `COMPLETED`, `activity_log` `REFUND_COMPLETED`. Any failure rolls all of it back. A payment already `REFUNDED` -> 409 `PAYMENT_ALREADY_REFUNDED`. Legacy refunds without `payment_id` complete without the payment step.
- **One refund per payment.** A `REJECTED` request does not count, so the member may ask again after a rejection.
- **Payment of the registration (review round 1, P1-03).** Quote, submit, approve and complete answer 409 `PAYMENT_REGISTRATION_MISMATCH` unless the payment is the registration's `payment_id`, belongs to the same member, is not also the `payment_id` of a `member_card`, and every `invoice_line` of its invoices is `SPORT_PACKAGE` with `item_reference_id` = the registration id.
- **Lock order of completion (review round 1, P1-02).** payment, refund_request, class_session rows of the upcoming bookings in ascending id order (same lock as BE-08 booking and BE-10 session cancellation), their booking rows, sport_package_registration. Bookings are re-read under the session locks and cancelled with one conditional UPDATE per session; `booked_count` is released with one atomic UPDATE per session, never below 0. If the bookings change between that read and the update, 409 `REFUND_BOOKINGS_CHANGED` and everything rolls back.

### Report query examples

These examples follow the actual one-payment reporting grain. Date parameters below are start-inclusive/end-exclusive timestamps, not unescaped user SQL fragments.

```sql
SELECT COALESCE(SUM(amount), 0) AS paid_revenue, COUNT_BIG(*) AS paid_transactions
FROM dbo.vw_paid_payment
WHERE payment_time >= @from AND payment_time < @toExclusive;

SELECT YEAR(payment_time) AS year, MONTH(payment_time) AS month,
       SUM(amount) AS revenue, COUNT_BIG(*) AS paid_count
FROM dbo.vw_paid_payment
WHERE payment_time >= @from AND payment_time < @toExclusive
GROUP BY YEAR(payment_time), MONTH(payment_time)
ORDER BY year, month;

SELECT item_type, SUM(amount) AS revenue, COUNT_BIG(*) AS paid_count
FROM dbo.vw_paid_payment
WHERE payment_time >= @from AND payment_time < @toExclusive
GROUP BY item_type
ORDER BY revenue DESC, item_type;

SELECT * FROM dbo.vw_payment_ledger
WHERE ledger_time >= @from AND ledger_time < @toExclusive
ORDER BY ledger_time DESC, payment_id DESC
OFFSET @offset ROWS FETCH NEXT @pageSize ROWS ONLY;
```

The repository binds enum/date/search parameters and escapes LIKE wildcards. CSV performs a pre-count, then selects at most 50001 rows and rejects an actual stream beyond 50000 before returning a successful response. This handles committed inserts between count and stream without returning a truncated success. Reports are separate reads and do not promise a single snapshot across concurrent requests. CSV is implemented in BE-22; browser print/save-PDF remains a frontend concern.
