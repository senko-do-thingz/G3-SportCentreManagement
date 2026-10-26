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
| TC-10 | Browser tab visibility resume and copy verification | Synthetic visibilitychange event harness with independent clipboard inspection | Visibility change on simulated tab resume triggers exactly one poll refresh; polling stops cleanly after SUCCESS; copied reference independently verified via clipboard interception matching exact casing and string SP-52-TAYLOR; all mandatory criteria determine PASS/FAIL | Pass |

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

## TC-10 Tab Resume, Clipboard Display/Copy Consistency & Browser Negative Controls

Executed via headless Chromium test harness (`frontend/scripts/generate-all-evidence.mjs --only ui-28`):
- **Execution Mode**: `synthetic-visibilitychange-event-harness` (simulated tab hide/resume via `document.hidden` / `visibilityState` DOM events; native OS/browser background tab throttling is marked `NOT_RUN`).
- **Initial Polls**: Exactly 1 request observed on initial pending view (`GET /payments/52`, `initialRequests: 1`) in production preview build (no dev StrictMode double-mounting).
- **Tab Hidden (`visibilityState = 'hidden'`)**: Exactly 0 background polling requests sent while tab is hidden (`requestsWhileHidden: 0`).
- **Tab Resumed (`visibilityState = 'visible'`)**: Exactly 1 immediate refresh request sent upon tab foregrounding (`resumeImmediateRequests: 1`).
- **Status Transition**: Backend response transition to `SUCCESS` updates status to "Payment confirmed".
- **Polling Termination**: Confirmed halted (0 additional requests across 2000 ms window; final total requests observed: 2; `pollingTerminatedAfterSuccess: true`).
- **Displayed Content vs. Copied Content (UI28-R6-02)**:
  - Independently extracted the rendered transfer content text from the DOM element (`.copy-value__text`) located directly beside the Copy button (`renderedTransferContent: 'SP-52-TAYLOR'`).
  - Intercepted the actual text written to the clipboard via `navigator.clipboard.writeText` (`interceptedClipboardValue: 'SP-52-TAYLOR'`).
  - Strictly asserted 1:1 identity between the rendered DOM text and intercepted clipboard text (`renderedTransferContent === interceptedClipboardValue === 'SP-52-TAYLOR'`, `renderedMatchesIntercepted: true`).
  - Formally distinguished identifiers: internal `paymentCode` is `PAY-0052`, whereas bank transfer syntax is `transferContent: 'SP-52-TAYLOR'`.
- **Browser-Level Negative Controls (UI28-R6-01)**:
  - *Browser Negative Control 1 (Controlled mutation in DOM)*: Injected clipboard mutation (`SP-52-MUTATED-FAIL`) causes the actual `assertCopyAcceptance` assertion to fail (`mutationCausesAssertionFailure: true`), with verified non-destructive cleanup restoring genuine copy handler behavior.
  - *Browser Negative Control 2 (Real disabled-button interaction in DOM)*: Set `btn.disabled = true; btn.setAttribute('aria-disabled', 'true')` in DOM, executed real click interaction; verified 0 clipboard writes produced (`disabledClickPreventedWrites: true`), with `assertCopyAcceptance(null, renderedTransferContent)` failing as expected, followed by non-destructive cleanup re-enabling the control.
  - *Unit Predicate Checks*: Segregated pure-function assertions tested against mutated string, missing button object, disabled button object, and empty/null clipboard writes.
  - *Positive Button Usability*: Verified the enabled button passes usability checks in DOM before executing the genuine positive copy flow.
- **PASS/FAIL Enforcement**: Every mandatory assertion (initial count === 1, zero hidden polls, exactly 1 resume poll, total polls === 2, polling termination, rendered transfer content extracted, rendered matches intercepted exact, rendered/intercepted match fixture, browser mutation failure, browser disabled failure, positive copy acceptance, payment confirmed status, unit predicates) contributes directly to `outcome: 'PASS'`.
- **Regenerated Visual Evidence**: All 12 screenshots regenerated with the final `SP-52-TAYLOR` fixture and confirmed untruncated.
- **Evidence Log**: Captured in `frontend/docs/screenshots/ui-28/tab-resume-log.json`.

Branch refreshed and merged with latest `main` (including UI-07 #131, UI-27, UI-29, UI-30, and UI-31 #128).
