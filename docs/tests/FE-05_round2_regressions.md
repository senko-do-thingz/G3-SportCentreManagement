# FE-05 Round 2 desk-charge and queue regressions

This supplements FE-05_regression_test_cases.md and preserves its Round 1 fixes.
Local unit/HTTP tests do not establish SQL Server or authenticated live acceptance.

## Expected charge protocol

Both desk purchase request DTOs accept optional `expectedAmountDue`, nonnegative, at most
10 integer digits and 2 decimal places. Missing/null means the legacy protocol. Explicit zero
means the caller expects no charge. FE-05 sends this field for every free and paid sale.

For a genuine free sale, FE-05 sends `expectedAmountDue: 0` and omits payment method, reference
and amount received. The card/package service computes its authoritative fee and compares it
within the existing sale transaction, before activating the item or recording money/invoice.
The check also covers positive-to-positive and positive-to-zero price changes.

A mismatch returns HTTP 409 with code `DESK_SALE_PRICE_CHANGED` and numeric details
`expectedAmountDue` and `currentAmountDue`. Nothing from the failed sale is committed.
The UI blocks another submit until Refresh prices reloads authoritative member/catalog data.
A matching positive expectation requires an explicit payment method and amount received.
BE-19 still decides cash under/overpayment, exact noncash amounts and required references.
The existing `amountReceived: 0` validation is unchanged; it is not a second free-sale protocol.

Deploy the backend before the updated FE-05 client. An older server that ignores the new field
cannot enforce this protection. Existing clients that omit the expectation keep the legacy
default-to-cash/default-received behavior, as explicitly covered by existing BE-19 tests.
Member online requests, pending payment confirmation, FE-12 receipts/refunds and aliases are
unchanged. The optional expectation does not supply a server idempotency key or lock the catalog
through later price edits; it checks the exact fee used by this sale transaction.

Desk purchase responses add `amountReceived` and `changeAmount`, computed from the validated
payment and request. They are zero for free sales and null on ordinary historical reads. These
are response-only totals; existing cash payment notes retain the received/change evidence.
FE-05 verifies persisted payment SUCCESS, member and charged amount, then uses server totals
for the final change display. It never derives a free-sale refund from hidden input state.
Paid/free selection changes clear the payment form; paid-to-paid choices retain applicable input.

## Bounded pending loads

The existing complete oldest-first queue UX is retained. Its two queue kinds may page together;
one shared load then hydrates at most four member-detail requests concurrently. Member ids are
deduplicated per kind within that load. Nothing is cached across loads or authenticated accounts.

Each page must match its index, supported size, count and expected content length. The first
page fixes the page-count plan; changed metadata, duplicates, malformed or empty intermediate
pages fail explicitly with Retry. There is no arbitrary item ceiling or silent partial truncation.
HTTP failures and absent item details fail the entire load. A detail record that was actually
paid/cancelled is a stale item and is removed, preserving Round 1 behavior.

Caller cancellation and session changes stop pagination and queued hydration and abort peers on
failure. The offset-paged backend cannot guarantee a database snapshot across separate requests.
Same-count reorderings may remain undetectable; refresh remains necessary on busy queues.
Memory and total request count still scale with queue size. No production load-test claim is made.

## Executable coverage

| Test source | Evidence |
|---|---|
| backend/src/test/java/com/sportify/catalog/service/DeskSaleChargeTest.java | Explicit zero/omission, all charge transitions, precision/range, required paid evidence, free/cash totals |
| backend/src/test/java/com/sportify/catalog/controller/DeskSaleContractControllerTest.java | Both DTOs/routes, RECEPTIONIST/MANAGER, stable 409 envelope, positive received validation, legacy omission |
| backend/src/test/java/com/sportify/catalog/service/MembershipCardServiceImplTest.java | Recomputed free-to-paid fee, repeated stale upgrade, unchanged baseline, stable free upgrade and zero collection |
| backend/src/test/java/com/sportify/catalog/service/SportPackageServiceImplTest.java | Free-to-paid fee, changed discount, stable free package and no payment/invoice |
| backend/src/test/java/com/sportify/payment/DeskSaleRollbackIntegrationTest.java | Real SQL state for stale/free/paid/duplicate cases, collection/change, invoice/line/audit absence, profile rollback and post-write invoice/audit failures; requires Docker CI |
| frontend/src/pages/reception/receptionScreens.test.tsx | Both tabs paid-to-free, paid input retention, no free change/receipt, explicit expected zero, price conflict refresh, queue Retry and existing FE-05/FE-12 handoff |
| frontend/src/api/pendingQueue.test.ts | 432 items, multiple pages, repeated member ids, shared worker bound, cancellation/session change, missing detail/retry, peer failure, HTTP errors and paging inconsistencies |

The critical integration manifest retains DeskSaleRollbackIntegrationTest and adds the Round 1
CheckInDateIntegrationTest requirement. Required CI must observe non-skipped SQL suites at the
final published HEAD. Local Docker absence is NOT RUN, not a passing integration result.
