# UI-30 - Reception payment requests queue: test cases

Scope: Payment Requests (F3-04) in the Home theme: queue table, status filter, search, pagination and
pending / needs-review indicators. No API or backend change.

## How each case is run

| Kind | Where | Runs in CI |
|------|-------|------------|
| Unit (Vitest) | `src/pages/reception/receptionPayments.test.tsx`, "FE-12 Payment Requests" and "FE-12 access" blocks (existing TC-FE12-020 to 022 and 050 plus the UI-30 cases) | Yes (`npm test`) |
| Manual | Steps below | No |

## Cases

| ID | Case | How | Expected | Result |
|----|------|-----|----------|--------|
| TC-01 | Home theme heading | Unit | `h1` inside the shared `PaymentHero`, Refund requests link in the hero | Pass |
| TC-02 | Sorted correctly | Unit | Rows keep the backend order (BE-18 sorts PENDING first); the page never reorders a server page | Pass |
| TC-03 | Filtered correctly | Unit (TC-FE12-021) | Status, search and page go to `GET /payments` and stay in the URL; All statuses sends no status | Pass |
| TC-04 | Pending and needs-review indicators | Unit | Status column on every row: Needs review for a PENDING payment matching the machine-generated BE-18 amount mismatch note contract (`Amount mismatch: received <amount>...`), Pending payment, Paid, Failed; non-machine free-text notes containing 'Amount mismatch:' or mismatch notes on paid payments are not flagged | Pass |
| TC-05 | No duplicate action | Unit | Exactly one link per row: Review payment for pending (primary when it needs review), Open otherwise | Pass |
| TC-06 | Stat cards from real data | Unit | Pending requests from `totalElements`, amount due of the pending rows on the page, needs review on the page; the old placeholder "Refund requests -" card is gone | Pass |
| TC-07 | Only authorised staff | Unit (TC-FE12-050 and UI-30 case) | MEMBER and COACH see Access denied and no payment request is sent; the backend also requires RECEPTIONIST or MANAGER | Pass |
| TC-08 | Light, dark and responsive viewports (G-02) | Visual check: `/reception/payments` at 390, 768 and 1440 px in light and dark | Hero readable, stat cards populated, Needs review badge visible, table scrolls cleanly inside card on phone without page-level spill | Pass |

## G-02 Responsive Matrix (Reception Payment Requests Queue)

| Screen / State | Viewport | Theme | Key Elements Checked | Result |
|----------------|----------|-------|----------------------|--------|
| Payment requests queue | 390 x 844 | Light | Stat cards wrap, table scrolls in container, Needs review badge visible | Pass |
| Payment requests queue | 390 x 844 | Dark | Contrast on status badges meets WCAG AA | Pass |
| Payment requests queue | 768 x 1024 | Light | Hero links, search filter, table rows aligned | Pass |
| Payment requests queue | 768 x 1024 | Dark | Dark theme surface, stat cards readable | Pass |
| Payment requests queue | 1440 x 900 | Light | Full desktop queue, Review payment primary CTA | Pass |
| Payment requests queue | 1440 x 900 | Dark | Desktop dark mode, no horizontal scroll | Pass |

## Authenticated Fixture & Role Execution Note
- **Authenticated Identity**: Role-aware `RECEPTIONIST` context applied across all screenshot runs (`/api/v1/users/me` returning role `RECEPTIONIST`). Zero 403 Access Denied screens encountered.
- **Queue Assertions**: Table populated with representative pending payments, verified amount mismatch note indicator (`Needs review`), and real stat counts.

Branch refreshed and merged on `feat/ui-29` and latest `main`.
