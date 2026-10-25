# UI-28 - Payment instructions and status: test cases

Scope: Payment Instructions and Status (F3-02) in the Home theme; bank transfer details with copy buttons, status
polling and payment ownership. S3-Mismatch ("Payment Needs Review") is a reception screen and is covered by UI-31.
No API or backend change.

## How each case is run

| Kind | Where | Runs in CI |
|------|-------|------------|
| Unit (Vitest) | `src/pages/member/memberPayments.test.tsx`, "FE-11 Payment Instructions and Status" block (existing TC-FE11-011 to 023 plus the UI-28 cases) | Yes (`npm test`) |
| Manual | Steps below | No |

## Cases

| ID | Case | How | Expected | Result |
|----|------|-----|----------|--------|
| TC-01 | Home theme heading | Unit | `h1` inside the shared `PaymentHero` | Pass |
| TC-02 | Copy exact reference | Unit (TC-FE11-011 and UI-28 case) | Transfer content copied exactly as shown (inner spaces and case kept, outer spaces trimmed); account number without spaces; amount plain | Pass |
| TC-03 | Pending updates when confirmed | Unit (TC-FE11-014, 015) | Checked every 15 s while PENDING; SUCCESS shows "Payment confirmed" and View receipt; polling stops | Pass |
| TC-04 | Background tab | Unit | `visibilitychange` to visible while PENDING checks at once; nothing is sent once paid | Pass |
| TC-05 | No leaked other payments | Unit | First answer for another id: error, no bank details, other code never shown; a later answer for another id keeps the right payment, shows the error and stops checking | Pass |
| TC-06 | Other account in another tab | Unit (existing) | An old account's answer after a switch is dropped | Pass |
| TC-07 | Errors | Unit (TC-FE11-017, 018, 022) | 5xx keeps the payment and retries; 4xx stops checks with Refresh; first load error shows Retry | Pass |
| TC-08 | Paid without invoice yet | Unit | "The receipt appears in My payments once it is issued", no receipt link | Pass |
| TC-09 | Light, dark and responsive viewports (G-02) | Visual check: `/member/payments/{id}/status` for pending bank transfer and confirmed SUCCESS states | Hero readable, bank account, reference, amount and copy buttons fully untruncated, no horizontal scroll | Pass |
| TC-10 | Real-browser tab visibility resume and copy verification | Browser tab hide/resume and clipboard copy test | Visibility change on tab resume triggers one poll refresh; polling stops cleanly after SUCCESS; copied reference preserves exact casing and characters | Pass |

## G-02 Responsive Matrix (Pending Transfer & Confirmed States)

| Screen / State | Viewport | Theme | Key Elements Checked | Result |
|----------------|----------|-------|----------------------|--------|
| Pending transfer | 390 x 844 | Light | Reference, account & amount untruncated; Copy buttons responsive | Pass |
| Pending transfer | 390 x 844 | Dark | QR code & instructions high contrast, no clipping | Pass |
| Pending transfer | 768 x 1024 | Light | Balanced grid, hero readable | Pass |
| Pending transfer | 768 x 1024 | Dark | Dark theme contrast meets WCAG AA | Pass |
| Pending transfer | 1440 x 900 | Light | Desktop layout, copy feedback visible | Pass |
| Pending transfer | 1440 x 900 | Dark | Desktop layout, dark surface | Pass |
| Confirmed (SUCCESS) | 390 x 844 | Light | "Payment confirmed", View receipt button accessible | Pass |
| Confirmed (SUCCESS) | 390 x 844 | Dark | Confirmed status badge and receipt navigation clear | Pass |
| Confirmed (no invoice) | 390 x 844 | Light | Safe messaging "The receipt appears in My payments once issued" | Pass |
| Confirmed (no invoice) | 1440 x 900 | Dark | No broken link, polling completely stopped | Pass |

Branch retested and merged onto `feat/ui-27` and latest `main`.
