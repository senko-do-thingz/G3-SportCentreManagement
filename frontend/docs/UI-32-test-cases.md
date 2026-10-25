# UI-32 - Refund request and review screens: test cases

Scope: Refund Requests (F3-07) and the refund states S3-RefundSubmitted, S3-RefundApproved, S3-RefundDone and the
rejected state in the Home theme. Money and eligibility come from BE-21; no API or backend change.

## How each case is run

| Kind | Where | Runs in CI |
|------|-------|------------|
| Unit (Vitest) | `src/pages/reception/receptionPayments.test.tsx`, "FE-12 Refund Requests" block (existing TC-FE12-040 to 049 plus the UI-32 cases) and "round 3 integration regressions" | Yes (`npm test`) |
| Manual | Steps below | No |

## Cases

| ID | Case | How | Expected | Result |
|----|------|-----|----------|--------|
| TC-01 | Home theme heading | Unit | `h1` inside the shared `PaymentHero` on Refund Requests and every refund state | Pass |
| TC-02 | Money from the API | Unit (TC-FE12-040, 041) | Amount prefilled with the quote's suggested amount; at most the payment amount; more never reaches POST /refunds | Pass |
| TC-03 | Eligibility from the API | Unit (TC-FE12-041 and the zero unused sessions case) | Ineligible quote shows Not eligible with the reason, fields and Submit disabled | Pass |
| TC-04 | Request, approved and paid are distinct | Unit | Progress list: PENDING marks Requested, APPROVED marks Approved, COMPLETED marks Refunded, REJECTED shows Requested then Rejected; one current step each | Pass |
| TC-05 | Irreversible payout confirms first | Unit (TC-FE12-042 and UI-32 case) | Record completed refund opens "Record the refund payout?" with the approved amount, member and refund code; nothing is sent until "Yes, record ..."; Cancel sends nothing | Pass |
| TC-06 | No double payout | Unit (duplicate completion clicks) | Double clicks on both buttons send one PUT /refunds/{id}/complete; a rejection never claims completion | Pass |
| TC-07 | Errors | Unit (TC-FE12-043, 047, 049) | A failed completion stays on Refund Approved with the backend message | Pass |
| TC-08 | Light, dark and responsive viewports (G-02) | Visual check: `/reception/refunds` and refund detail states in light and dark at 390, 768 and 1440 px | Hero, progress chips and confirmation modal readable, no page-level horizontal scroll | Pass |
| TC-09 | Modal keyboard navigation, Escape and focus | Keyboard test: open irreversible payout modal, press Escape or Cancel | Focus trapped inside modal; Escape dismisses modal without sending PUT; Cancel button dismisses without action; Confirm button sends single PUT | Pass |

## G-02 Responsive Matrix (Refund Progress & Confirmation Modal)

| Screen / State | Viewport | Theme | Key Elements Checked | Result |
|----------------|----------|-------|----------------------|--------|
| Refund progress | 390 x 844 | Light | Progress chips wrap cleanly, no text clipping | Pass |
| Refund progress | 390 x 844 | Dark | Dark theme contrast on step chips meets WCAG AA | Pass |
| Refund progress | 768 x 1024 | Light | Balanced grid, hero readable | Pass |
| Refund progress | 768 x 1024 | Dark | Dark surface and approved badge clear | Pass |
| Refund progress | 1440 x 900 | Light | Desktop stage view, actions aligned | Pass |
| Refund progress | 1440 x 900 | Dark | Desktop stage view, dark mode | Pass |
| Second-confirmation modal | 390 x 844 | Light | Modal fits phone screen, warning copy fully readable | Pass |
| Second-confirmation modal | 390 x 844 | Dark | Dark modal backdrop, clear action buttons | Pass |
| Second-confirmation modal | 1440 x 900 | Light | Centered dialog, amount & refund code prominent | Pass |
| Long member name & refund ID | 390 x 844 | Light | Long member name wraps cleanly, no overflow | Pass |

Note: BE-21 allows one refund per payment, so the quote has no prior refund amount and the refundable limit is the
payment amount (`toRefundQuote`). Frontend ref guarantees local single-submit; backend concurrency and idempotency verification is recorded as integration gate QA-01.
Branch refreshed and merged on `feat/ui-31` (PR #128).
