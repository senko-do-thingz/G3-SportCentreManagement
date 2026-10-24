# UI-31 - Verify payment and receipt: test cases

Scope: Verify and Record Payment (F3-05), Payment Needs Review (S3-Mismatch), Payment Confirmed and Receipt (F3-06)
and the desk receipt (S3-Receipt) in the Home theme. Transaction atomicity stays in BE-18; no API or backend change.

## How each case is run

| Kind | Where | Runs in CI |
|------|-------|------------|
| Unit (Vitest) | `src/pages/reception/receptionPayments.test.tsx`, "FE-12 Verify and Record Payment", "review round 1 amount rules", "round 3 integration regressions" and "UI-31 Verify payment and receipt" blocks | Yes (`npm test`) |
| Print (Chrome `Page.printToPDF`, author's machine) | Payment Confirmed page and desk receipt | No |
| Manual | Steps below | No |

## Cases

| ID | Case | How | Expected | Result |
|----|------|-----|----------|--------|
| TC-01 | Home theme heading | Unit | `h1` inside the shared `PaymentHero` with Payment requests (and Member profile) in the hero | Pass |
| TC-02 | Exact amount for every method | Unit (TC-FE12-030, 037 to 039 and round 3) | Confirm stays disabled until the amount equals the amount due and the checkbox is ticked; cash above the amount is not accepted | Pass |
| TC-03 | Mismatch | Unit (TC-FE12-032, 033) | 409 PAYMENT_AMOUNT_MISMATCH shows Payment Needs Review with due, received and outstanding; payment stays pending | Pass |
| TC-04 | Wrong amount never shown as success | Unit | A 200 answer that is not SUCCESS, or SUCCESS for another payment id, shows "The payment was not confirmed" and no success message; a SUCCESS with another amount received gives no success message | Pass (fails on the previous page) |
| TC-05 | Inconsistent paid payment | Unit | SUCCESS with amount received or invoice total different from the amount due: title Payment Needs Review, Needs review badge, alert with both amounts, Print disabled, no Open receipt, not printable | Pass (fails on the previous page) |
| TC-06 | Double submit prevented | Unit (existing double click and UI-31 case) | Two submits in the same tick send one PUT /payments/{id}/confirm | Pass (fails on the previous page) |
| TC-07 | Receipt follows success | Unit (TC-FE12-031, 036 and round 3) | After SUCCESS the invoice of GET /invoices/by-payment/{id} is shown; Print waits for the real invoice | Pass |
| TC-08 | No invented receipt fields | Unit | No base price from the total, no discount or service when the invoice has none | Pass |
| TC-09 | Desk receipt ownership and status | Unit | An invoice of another payment is not shown; a CANCELLED invoice cannot be printed | Pass |
| TC-10 | Print | Print: PDF | One A4 page; hero, sidebar and actions not printed | Pass (1 page each) |
| TC-11 | Light, dark and 390 px | Manual: `/reception/payments/{id}` pending and paid | Hero readable, form usable, no page-level horizontal scroll | Checked by the author with fixed API data; reviewer to confirm |
