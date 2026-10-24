# UI-29 - My payments, history and receipts: test cases

Scope: My Payments and Receipts (F3-03, S3-PaidHistory) and the receipts (S3-CardReceipt, S3-MemberReceipt,
S3-SingleReceipt) in the Home theme; A4 print. No API or backend change.

## How each case is run

| Kind | Where | Runs in CI |
|------|-------|------------|
| Unit (Vitest) | `src/pages/member/memberPayments.test.tsx`, "My Payments and Receipts" and "Receipts" blocks (existing TC-FE11-024 to 036 plus the UI-29 cases) | Yes (`npm test`) |
| Print (Chrome `Page.printToPDF`, author's machine) | Receipt with one line, and with eight long lines, a long invoice number, name and bank reference, app in dark theme | No |
| Manual | Steps below | No |

## Cases

| ID | Case | How | Expected | Result |
|----|------|-----|----------|--------|
| TC-01 | Home theme headings | Unit | History and receipt `h1` inside the shared `PaymentHero` | Pass |
| TC-02 | No invented invoice fields | Unit | Invoice without subtotal, discount and lines: no Base price, Discount or Service row; total from the invoice | Pass |
| TC-03 | Real zero discount kept | Unit | Invoice with `discountAmount: 0` shows "Discount 0 VND" | Pass |
| TC-04 | Member ownership | Unit (existing) | 403 for another member's receipt shows nothing of it; "Issued to" comes only from the invoice | Pass |
| TC-05 | History pagination and filters | Unit (TC-FE11-024 to 030) | Server paging with page and size in the URL, status filter sent as `?status=`, empty states | Pass |
| TC-06 | Printable one-page A4 | Print: PDF with the receipt `@page` (A4, 15 mm) | One page for both receipts; hero, navigation and buttons not printed; light colors from a dark-theme session | Pass (1 page each) |
| TC-07 | Hero hidden when printing | Unit (stylesheet) | `@media print` hides `.receipt-page .payment-hero` | Pass |
| TC-08 | Light, dark and 390 px | Manual: `/member/payments` and a receipt | Hero readable, table and filters usable, no horizontal scroll | Checked by the author with fixed API data; reviewer to confirm |
