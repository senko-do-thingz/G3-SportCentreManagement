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
| TC-08 | Light, dark and responsive viewports (G-02) | Visual check: `/member/payments` and receipt screens at 390, 768 and 1440 px in light and dark | Hero readable, table and filters usable, receipt details untruncated, no horizontal scroll | Pass |

## G-02 Responsive Matrix (Payment List & Receipts)

| Screen / State | Viewport | Theme | Key Elements Checked | Result |
|----------------|----------|-------|----------------------|--------|
| Payment list (table & filters) | 390 x 844 | Light | Filters collapse cleanly, table card scrolls horizontally without page spill | Pass |
| Payment list (table & filters) | 390 x 844 | Dark | Contrast on status badges meets WCAG AA | Pass |
| Payment list (pagination) | 768 x 1024 | Light | Page navigation and server pagination intact | Pass |
| Payment list (pagination) | 768 x 1024 | Dark | Dark feature hero aligned | Pass |
| Payment list (desktop) | 1440 x 900 | Light | Full table view, filter bar aligned | Pass |
| Payment list (desktop) | 1440 x 900 | Dark | Full table view, dark theme surface | Pass |
| Member Receipt | 390 x 844 | Light | Receipt summary, date, total and Print button untruncated | Pass |
| Member Receipt | 390 x 844 | Dark | Dark theme readable | Pass |
| Member Receipt | 1440 x 900 | Light | Desktop receipt card centered, action buttons reachable | Pass |
| Member Receipt | 1440 x 900 | Dark | High contrast dark receipt | Pass |

## A4 Print-to-PDF Verification

| Scenario | Dimensions & Margin | Print Elements Included | Elements Hidden | Page Count |
|----------|---------------------|-------------------------|-----------------|------------|
| Short receipt (1 line item) | A4 (210 x 297 mm), 15 mm | Invoice number, issue date, member name, total due, status | `.payment-hero`, navbar, back button, print action | Exactly 1 page |
| Long receipt (8 lines, long ref) | A4 (210 x 297 mm), 15 mm | All 8 lines, breakdown, subtotal, discount, total | `.payment-hero`, navbar, back button, print action | Exactly 1 page |
| Dark-to-print conversion | A4 standard | Forced high-contrast black/white print layout | Dark background, colorful shadows | Clean white page |

Branch refreshed and merged on `feat/ui-27` and latest `main`.
