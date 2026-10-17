# FE-05 reception regression coverage

This supplement uses the integrated BE-05, BE-19 and FE-12 source contracts. The proposed
contracts in FE-05_test_cases.md section 1.1 are historical and are superseded here.
Live acceptance and SQL Server integration validation still require a development environment
and the required GitHub CI jobs. Passing HTTP transport tests alone does not close acceptance.

## Current contracts

- Overview: `MemberOverviewResponse` has `profile`, `currentCard`, `pendingCardRequest`,
  `activePackageRegistrations`, `pendingPackageRegistrations`, and `recentCheckIns`.
  Card/package summaries omit payment links, rank and discounts. FE-05 validates the overview
  and reads the existing staff member detail endpoints for pricing, history and payment links.
- Pending queues return Spring pages of `FrontDeskCardResponse` or `FrontDeskPackageResponse`.
  FE-05 follows the pages and joins each request by member and item id to its staff detail DTO.
  Requests already cancelled or paid at the second read are removed. Missing payments have no
  confirmation link. A failed detail lookup is an error with Retry.
- Creation accepts only full name, phone and email. The phone accepts 8-15 digits and an optional
  leading plus. Optional training/profile fields are not collected because BE-05 cannot save them.
  The returned temporary password stays in component memory, is masked initially, and is discarded
  when the dialog closes, the route unmounts, or the authenticated account changes.
- Desk sales send canonical `referenceCode`. `paymentReference` remains a valid backend alias
  used by FE-12. Cash allows change; other methods require exact payment. A zero-fee desk upgrade
  omits `amountReceived`, creates no payment, and asks staff to collect nothing.
- Paid sales verify `SUCCESS` and member ownership through GET `/payments/{id}`. FE-12 opens
  the authoritative receipt and service status without another confirmation.
- Check-in POST keeps ALLOWED/DENIED semantics. GET now accepts `date`, `memberId`, or both;
  missing filters and invalid dates return 400. Date ranges use centre-local midnight inclusive
  and the next midnight exclusive, newest first with id as tie-breaker. Existing member-only
  queries and staff authorization are preserved. Unknown-code feedback never becomes a history row.
- Reception routes retain the existing RECEPTIONIST workspace restriction. MEMBER, COACH and
  MANAGER cannot open that workspace; MANAGER can call the staff backend APIs as before.

## Executable regressions

| Area | Test source | Coverage |
|---|---|---|
| Overview and queue contracts | frontend/src/api/receptionApi.test.ts | Canonical DTOs, active/pending/expired detail history, empty collections, recent check-ins, malformed/missing profiles, 404, all pending pages, per-member join, missing payment, cancelled stale request |
| Creation transport and session safety | frontend/src/api/receptionApi.test.ts | Exact create body, separate one-time credential result, 400/403/409/500, stale account response rejection |
| Reception screens | frontend/src/pages/reception/receptionScreens.test.tsx | Real adapter plus fake HTTP transport; create/reveal/clear, duplicate phone/email, field validation, account switch, profile/handoff, cash change, transfer mismatch, card renewal, zero-fee upgrade, re-fetch, pending ordering, paid receipt, duplicate-submit guard, uncertain network/500/payment state, ALLOWED/DENIED/unknown scan, list 400/500 and Retry, focus, route restrictions |
| Models | frontend/src/pages/reception/receptionModel.test.ts | BE-05 form validation, centre date boundary, amount validation/change, tier upgrades/renewals, canonical overview reader, ordering and paths |
| Date HTTP and role contracts | backend/src/test/java/com/sportify/catalog/controller/CheckInControllerTest.java | Date-only and combined filters, legacy member query, missing/invalid filters, MEMBER/COACH denial and MANAGER/RECEPTIONIST access |
| Date service | backend/src/test/java/com/sportify/catalog/service/CheckInServiceTest.java | Centre-local half-open range, denial mapping, member filter, SQL date bounds, existing scan and duplicate-booking logic |
| SQL date query | backend/src/test/java/com/sportify/catalog/service/CheckInDateIntegrationTest.java | SQL Server boundary inclusion/exclusion, descending id tie-break, member/all-member queries; requires Docker CI |
| Existing integrations | Existing FE-12 payment/refund, member and authentication suites | Run unchanged in the complete frontend suite; backend payment and creation suites run in Maven package |

## Real environment checks still required

1. Use authorized synthetic development accounts and a SQL Server database with current migrations.
2. Search, create and securely hand off the one-time credential. Verify an actual 409 for each
   duplicate contact field. Close/refresh and switch accounts; credentials must not survive.
3. Sell packages and card upgrades/renewals. Inspect payment SUCCESS, invoice PAID, item ACTIVE
   and cash change. Failed transactions must leave no partial payment, item or invoice rows.
4. Confirm a pending online request through FE-12. Return to the profile and verify the actual
   item status. Exercise stale, cancelled, missing-payment, 403 and 404 paths.
5. Check in a booked member, repeat the scan, and scan an unknown code. Only persisted responses
   appear in history. Verify centre midnight boundaries and a real server error with Retry.
6. Check keyboard operation, modal focus, scan focus, browser Back/Refresh, and widths 375, 768
   and 1440 pixels. Live responsive appearance and persisted outcomes are not established by jsdom.

Frontend guards prevent duplicate in-flight submissions and block repeats after ambiguous failures
within the form. They do not provide cross-tab or server-side purchase idempotency.
