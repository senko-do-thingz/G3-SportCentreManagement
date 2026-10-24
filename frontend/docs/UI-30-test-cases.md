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
| TC-04 | Pending and needs-review indicators | Unit | Status column on every row: Needs review for a PENDING payment whose notes hold the BE-18 "Amount mismatch:" note, Pending payment, Paid, Failed; a mismatch note on a paid payment is not flagged | Pass |
| TC-05 | No duplicate action | Unit | Exactly one link per row: Review payment for pending (primary when it needs review), Open otherwise | Pass |
| TC-06 | Stat cards from real data | Unit | Pending requests from `totalElements`, amount due of the pending rows on the page, needs review on the page; the old placeholder "Refund requests -" card is gone | Pass |
| TC-07 | Only authorised staff | Unit (TC-FE12-050 and UI-30 case) | MEMBER and COACH see Access denied and no payment request is sent; the backend also requires RECEPTIONIST or MANAGER | Pass |
| TC-08 | Light, dark and 390 px | Manual: `/reception/payments` | Hero readable; the table scrolls inside its card on a phone; no page-level horizontal scroll | Checked by the author with fixed API data; reviewer to confirm |
