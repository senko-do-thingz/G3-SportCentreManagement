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
| TC-09 | Desk receipt ownership and status | Unit | Exact payment-to-invoice association enforced: an invoice with null, missing or mismatched paymentId shows "Receipt not found" error state and cannot be printed; a CANCELLED invoice cannot be printed | Pass |
| TC-10 | Print | Print: PDF | One A4 page; hero, sidebar and actions not printed; unverifiable/mismatch receipts cannot be printed | Pass (1 page each) |
| TC-11 | Light, dark and responsive viewports (G-02) | Visual check: `/reception/payments/{id}` pending (ID 41), mismatch (ID 44) and desk receipt (`/reception/payments/41/receipt`) at 390, 768 and 1440 px in light and dark | Hero readable, form usable, amount mismatch alert prominent, desk receipt centered, no page-level horizontal scroll | Pass |

## G-02 Responsive Matrix (Verify, Mismatch & Desk Receipt)

| Screen / State | Viewport | Theme | Key Elements Checked | Result |
|----------------|----------|-------|----------------------|--------|
| Payment Verify (Pending #41) | 390 x 844 | Light | Amount due, payment method, confirm checkbox & button accessible | Pass |
| Payment Verify (Pending #41) | 390 x 844 | Dark | Contrast on fields and action buttons meets WCAG AA | Pass |
| Payment Verify (Pending #41) | 768 x 1024 | Light | Form grid balanced, customer info clear | Pass |
| Payment Verify (Pending #41) | 768 x 1024 | Dark | Surface and field styling consistent | Pass |
| Payment Verify (Pending #41) | 1440 x 900 | Light | Desktop verify layout, actions aligned | Pass |
| Payment Verify (Pending #41) | 1440 x 900 | Dark | Full desktop view, high contrast dark theme | Pass |
| Payment Needs Review (#44) | 390 x 844 | Light | "Payment Needs Review" alert, due (810,000 VND) & received (800,000 VND) amounts distinct, outstanding (10,000 VND) | Pass |
| Payment Needs Review (#44) | 390 x 844 | Dark | Warning banner high contrast, confirmation button disabled | Pass |
| Payment Needs Review (#44) | 768 x 1024 | Light | Mismatch alert prominently framed with monetary comparison | Pass |
| Payment Needs Review (#44) | 768 x 1024 | Dark | Dark theme alert readable, no accidental submission path | Pass |
| Payment Needs Review (#44) | 1440 x 900 | Light | Full mismatch overview, discrepancy reason & amounts visible | Pass |
| Payment Needs Review (#44) | 1440 x 900 | Dark | Desktop dark mismatch state, Return to verification accessible | Pass |
| Desk Receipt View (#41) | 390 x 844 | Light | Receipt details, issue date, Print button accessible | Pass |
| Desk Receipt View (#41) | 390 x 844 | Dark | High contrast dark receipt card | Pass |
| Desk Receipt View (#41) | 1440 x 900 | Light | Centered receipt card, clean layout | Pass |
| Desk Receipt View (#41) | 1440 x 900 | Dark | Desktop dark mode receipt | Pass |

## Desk Receipt A4 Print-to-PDF Verification
- **Generated File**: `frontend/docs/screenshots/ui-31/reception-receipt-a4.pdf`
- **Page Count**: Exactly 1 page verified (`preferCSSPageSize: true`, 8.27 x 11.69 inches).
- **Extracted Content**: Invoice `INV-1041` (Payment #41), Total amount `810,000 VND` (Subtotal `900,000 VND`, Discount `90,000 VND`), Item `Basketball Academy (12 sessions)`, Member `Bao Nguyen` (`MEM-0003`), Issue date `2026-10-25`.
- **Excluded Elements**: `@media print` eliminates navigation, sidebar, hero banner, and action buttons. Zero 403 or Access Denied screens.

Branch refreshed and merged on `feat/ui-30`, `feat/ui-28`, and latest `main`.
