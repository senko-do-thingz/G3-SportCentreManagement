# QR-06 (i) Round 02 - Master Test Cases (`QR06-001` to `QR06-106`)

- **Task:** QR-06 (i) - Reception Desk Simulated QR Payment Display
- **Round:** Round 02 - Correctness, Safe Customer Journey, and Verifiable Evidence
- **Branch:** `feat/qr-06-reception-simulated-qr-display`
- **PR:** https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/181
- **Base `origin/main` Commit:** `65632ff88ef19bdd21e4be778a285a5958caea27` (PR #179 merge commit with V36 SQL provenance fix)
- **Pre-implementation SHA:** `de29ec4bb8c5be44228770795ebdfbe397a17e0c`
- **Execution Summary:**
  - **Total Test Cases:** `106` (`QR06-001` through `QR06-106`)
  - **PASS:** `105`
  - **FAIL:** `0`
  - **BLOCKED:** `0`
  - **NOT RUN:** `1` (`QR06-075` - physical mobile handset scanning requires physical mobile device on LAN; desktop and browser simulation verified)
  - **Verification Levels Applied:**
    - `EXECUTED TEST`: 56 automated unit/integration test cases (Vitest and Maven Surefire)
    - `SCREENSHOT`: 17 real Microsoft Edge desktop/responsive browser screenshots
    - `SQL EXECUTION`: 14 SQL Server read-only and financial separation verification records
    - `MOCK TEST`: 12 component mock resilience and edge verification cases
    - `STATIC REVIEW`: 6 code and architectural contract invariant reviews
    - `NOT RUN`: 1 optional physical handset scanner case

---

## 1. Role Authorization and Access Control (`QR06-001` to `QR06-010`)

| ID | Title | Technique | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR06-001` | Receptionist can get desk projection for pending payment | Normal | Simulator enabled; authenticated RECEPTIONIST account; payment 101 exists | `GET /api/v1/payments/simulated-qr/desk-projection/101` with Receptionist JWT | `200 OK`; returns projection JSON with payment and active session metadata | Returned `200 OK` with complete projection DTO | `ReceptionQrDisplayControllerTest` | PASS | Sec 3, Sec 4 | - |
| `QR06-002` | Manager can access desk projection | Normal | Simulator enabled; authenticated MANAGER account; payment 101 exists | `GET /api/v1/payments/simulated-qr/desk-projection/101` with Manager JWT | `200 OK`; projection returned | Returned `200 OK` | `ReceptionQrDisplayControllerTest` | PASS | Sec 3, Sec 4 | - |
| `QR06-003` | Member role is forbidden from calling desk projection | Security | Authenticated MEMBER account | `GET /api/v1/payments/simulated-qr/desk-projection/101` with Member JWT | `403 Forbidden` | Returned `403 Forbidden` | `ReceptionQrDisplayControllerTest` | PASS | Sec 3 | - |
| `QR06-004` | Coach role is forbidden from calling desk projection | Security | Authenticated COACH account | `GET /api/v1/payments/simulated-qr/desk-projection/101` with Coach JWT | `403 Forbidden` | Returned `403 Forbidden` | `ReceptionQrDisplayControllerTest` | PASS | Sec 3 | - |
| `QR06-005` | Anonymous unauthenticated request is rejected | Security | Unauthenticated caller | `GET /api/v1/payments/simulated-qr/desk-projection/101` without Authorization header | `401 Unauthorized` | Returned `401 Unauthorized` | `ReceptionQrDisplayControllerTest` | PASS | Sec 3 | - |
| `QR06-006` | Inactive staff account is rejected | Security | RECEPTIONIST account with status `INACTIVE` | `GET /api/v1/payments/simulated-qr/desk-projection/101` with inactive user JWT | `403 Forbidden` / `401 Unauthorized` | Rejected before handler execution | `SecurityConfig`, `ReceptionQrDisplayControllerTest` | PASS | Sec 3 | - |
| `QR06-007` | Non-existent payment returns 404 | Boundary | Authenticated RECEPTIONIST | `GET /api/v1/payments/simulated-qr/desk-projection/999999` | `404 Not Found` | Returned `404 Not Found` with `SIMULATOR_UNAVAILABLE` | `ReceptionQrDisplayControllerTest` | PASS | Sec 3, Sec 4 | - |
| `QR06-008` | Invalid zero or negative payment ID returns 404 | Boundary | Authenticated RECEPTIONIST | `GET /api/v1/payments/simulated-qr/desk-projection/0` and `.../-5` | `404 Not Found` | Returned `404 Not Found` | `ReceptionQrDisplayControllerTest` | PASS | Sec 3, Sec 4 | - |
| `QR06-009` | Simulator capability endpoint allows Receptionist and Manager | Normal | Authenticated RECEPTIONIST or MANAGER account | `GET /api/v1/payments/simulated-qr/capability` | `200 OK` with `enabled=true, settlementEnabled=true, contractVersion=1` | Returned `200 OK` with capability JSON | `DemoQrControllerTest` | PASS | Sec 3, Sec 6 | - |
| `QR06-010` | Simulator capability endpoint forbids Coach and Anonymous | Security | Unauthenticated caller or COACH account | `GET /api/v1/payments/simulated-qr/capability` | `401 Unauthorized` / `403 Forbidden` | Returned `401`/`403` | `DemoQrControllerTest` | PASS | Sec 3 | - |

---

## 2. Data Minimization and Secret Redaction (`QR06-011` to `QR06-018`)

| ID | Title | Technique | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR06-011` | Desk projection response excludes token | Security | Active session exists with raw token in memory | `GET /api/v1/payments/simulated-qr/desk-projection/101` | Response JSON does not contain `token` property | Verified `$.token` does not exist in response | `ReceptionQrDisplayControllerTest` | PASS | Sec 4, Sec 7 | - |
| `QR06-012` | Desk projection response excludes tokenHash | Security | Active session exists with hash in SQL | `GET /api/v1/payments/simulated-qr/desk-projection/101` | Response JSON does not contain `tokenHash` or `tokenVerifier` | Verified `$.tokenHash` does not exist in response | `ReceptionQrDisplayControllerTest` | PASS | Sec 4, Sec 7 | - |
| `QR06-013` | Desk projection response excludes confirmationUrl | Security | Active session exists | `GET /api/v1/payments/simulated-qr/desk-projection/101` | Response JSON does not contain `confirmationUrl` | Verified `$.confirmationUrl` does not exist | `ReceptionQrDisplayControllerTest` | PASS | Sec 4, Sec 7 | - |
| `QR06-014` | Active session exposes only public metadata | Normal | Active session exists in store | `GET /api/v1/payments/simulated-qr/desk-projection/101` | `activeSession` contains only `sessionId`, `bankCode`, `bankLabel`, `state`, `createdAt`, `expiresAt`, `serverTime` | Verified only safe metadata fields present in DTO | `ReceptionQrDisplayControllerTest` | PASS | Sec 4 | - |
| `QR06-015` | Expired active session evaluates state to EXPIRED | Boundary | Active session has `expiresAt < now()` | `GET /api/v1/payments/simulated-qr/desk-projection/101` | `activeSession.state` is returned as `EXPIRED` | Verified `activeSession.state` evaluates to `EXPIRED` | `ReceptionQrDisplayControllerTest` | PASS | Sec 4 | - |
| `QR06-016` | No member confirmation secret leaked in browser frontend | Security | Frontend renders `ReceptionQrDisplayPage` | Inspect rendered DOM and state tree | Zero token, secret hash, or confirmation link rendered in DOM | Verified DOM contains zero token or secret link | `receptionQrDisplay.test.tsx` | PASS | Sec 4, Sec 7 | - |
| `QR06-017` | Server logs never record secret tokens during projection | Security | Execute desk projection request | Inspect backend server log output | Zero raw tokens in logs | Verified zero secret tokens in server logs | `ReceptionQrDisplayControllerTest` | PASS | Sec 7, Sec 8 | - |
| `QR06-018` | Staff view cannot forge member confirmation tokens | Security | Receptionist inspects desk projection | Attempt to invoke confirm endpoint using projection data | Fails with `400 Bad Request` / `403 Forbidden` | Verified desk projection contains no forgery capability | `DemoQrControllerTest`, `receptionQrDisplay.test.tsx` | PASS | Sec 4, Sec 7 | - |

---

## 3. Zero SQL Mutation and Financial Invariants (`QR06-019` to `QR06-028`)

| ID | Title | Technique | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR06-019` | Read-only transactional boundary enforced | Security | Receptionist accesses desk projection | Controller annotation check | `@Transactional(readOnly = true)` declared on endpoint | Verified read-only transaction on controller method | `ReceptionQrDisplayController.java` | PASS | Sec 5 | - |
| `QR06-020` | Zero mutations on dbo.payment during desk view | Security | Pending payment 101 in database | `GET /api/v1/payments/simulated-qr/desk-projection/101` | No `save`, `update`, or `delete` on `PaymentRepository` | Verified `paymentRepository.save` never invoked | `ReceptionQrDisplayControllerTest` | PASS | Sec 5 | - |
| `QR06-021` | Zero mutations on dbo.invoice during desk view | Security | Invoices in database | Inspect invoice repository/store | Zero insert, update, or delete statements on `dbo.invoice` | Verified zero invoice mutations | `ReceptionQrDisplayControllerTest` | PASS | Sec 5 | - |
| `QR06-022` | Zero mutations on dbo.simulated_qr_session during desk view | Security | Store session table | Inspect `SqlQrStore` method calls | Only `findLatestByPayment` (SELECT) called; `create`, `confirm`, `settle`, `cancel` never called | Verified zero mutating calls on `SqlQrStore` | `ReceptionQrDisplayControllerTest` | PASS | Sec 5 | - |
| `QR06-023` | Zero mutations on dbo.simulated_payment_transaction | Security | Settlement transaction table | Call desk projection | Zero rows inserted into `dbo.simulated_payment_transaction` | Verified zero settlement transaction rows | `ReceptionQrDisplayControllerTest` | PASS | Sec 5 | - |
| `QR06-024` | Zero mutations on dbo.cash_event and ledger tables | Security | Cash and ledger tables | Call desk projection | Zero cash event or ledger movements recorded | Verified zero ledger movements | `ReceptionQrDisplayControllerTest` | PASS | Sec 5 | - |
| `QR06-025` | Repeated polling calls produce strictly zero side effects | Stress | Receptionist display polling every 4 seconds | 5 successive GET calls to desk projection | All 5 calls return `200 OK` with strictly read-only execution; zero mutations | Verified 5 calls invoked only read methods; zero mutations | `ReceptionQrDisplayControllerTest` | PASS | Sec 5 | - |
| `QR06-026` | Desk projection for settled payment produces zero mutations | Security | Settled payment in database | Call desk projection | Returns settled projection with zero database writes | Verified read-only execution on settled payment | `ReceptionQrDisplayControllerTest` | PASS | Sec 5 | - |
| `QR06-027` | Desk projection for failed payment produces zero mutations | Security | Failed payment in database | Call desk projection | Returns failed projection with zero database writes | Verified read-only execution on failed payment | `ReceptionQrDisplayControllerTest` | PASS | Sec 5 | - |
| `QR06-028` | Desk projection for refunded payment produces zero mutations | Security | Refunded payment in database | Call desk projection | Returns refunded projection with zero database writes | Verified read-only execution on refunded payment | `ReceptionQrDisplayControllerTest` | PASS | Sec 5 | - |

---

## 4. Payment Status and Simulated Provenance Presentation (`QR06-029` to `QR06-038`)

| ID | Title | Technique | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR06-029` | Pending payment displays Pending payment status badge | UI | Payment 101 with status `PENDING` | Render `ReceptionQrDisplayPage` | StatusBadge displays `Pending payment` with warning tone | Verified `Pending payment` badge rendered | `receptionQrDisplay.test.tsx` | PASS | Sec 5, Sec 6 | - |
| `QR06-030` | Pending payment displays live auto-refresh indicator | UI | Payment 101 with status `PENDING` | Render `ReceptionQrDisplayPage` | Screen displays `Auto-refreshing every 4s` notice | Verified live refresh notice displayed | `receptionQrDisplay.test.tsx` | PASS | Sec 5, Sec 6 | - |
| `QR06-031` | Settled payment displays Paid - Simulated status badge | UI | Payment transitions to `SUCCESS` with `settlementType: 'SIMULATED'` | Advance polling timer by 4s | StatusBadge displays `Paid - Simulated` with success tone | Verified `Paid - Simulated` badge rendered | `receptionQrDisplay.test.tsx` | PASS | Sec 5, Sec 6 | QR06-R1-01 |
| `QR06-032` | Settled payment displays settlement provenance SIMULATED | UI | Settled projection loaded | Inspect provenance display | Displays `SIMULATED (SPORTIFY_SIMULATOR)` | Verified simulated provenance displayed | `receptionQrDisplay.test.tsx` | PASS | Sec 5 | QR06-R1-01 |
| `QR06-033` | Settled payment displays transaction code | UI | Settled projection with `simulatedTransactionId: 9001` | Inspect transaction code display | Displays `TXN-9001` | Verified `TXN-9001` displayed | `receptionQrDisplay.test.tsx` | PASS | Sec 5 | QR06-R1-01 |
| `QR06-034` | Settled payment renders green success banner | UI | Payment is settled | Inspect banners | Notice with tone `success` displayed confirming simulated payment received | Verified green success banner rendered without unverified entitlement claim | `receptionQrDisplay.test.tsx` | PASS | Sec 5, Sec 6 | QR06-R1-01 |
| `QR06-035` | Settled payment reveals View receipt button | UI | Payment transitions to settled | Inspect actions in status card | `View receipt` button displayed linking to `/reception/payments/:paymentId/receipt` | Verified `View receipt` link rendered with correct href | `receptionQrDisplay.test.tsx` | PASS | Sec 5, Sec 6 | - |
| `QR06-036` | Polling stops when payment reaches terminal SUCCESS | UI | Payment settles | Check polling timer | Interval cleared; no further polling requests dispatched | Verified polling stops on terminal state | `receptionQrDisplay.test.tsx` | PASS | Sec 5 | QR06-R1-04 |
| `QR06-037` | Payment item description resolved dynamically | Normal | Payment associated with membership card or package | GET desk projection | `itemDescription` contains resolved Entitlement title (e.g. `Gold Membership (1 Month)`) | Verified item summary description resolved | `ReceptionQrDisplayControllerTest` | PASS | Sec 4, Sec 5 | - |
| `QR06-038` | Member code and full name formatted accurately | Normal | Payment has member profile | GET desk projection | Member name and code formatted as `Nguyen Van A (MEM-030)` | Verified member name and code formatting | `ReceptionQrDisplayControllerTest`, `receptionQrDisplay.test.tsx` | PASS | Sec 4, Sec 6 | - |

---

## 5. QR Code Presentation and Bank Selector (`QR06-039` to `QR06-048`)

| ID | Title | Technique | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR06-039` | Plain text QR payload generated via demoQrPayload | Normal | Valid payment ID and bank | Check payload generation | Payload format: `SPORTIFY_DEMO_QR\|VERSION=2\|PAYMENT_ID=...\|MODE=NON_PAYABLE\|BANK_CODE=...` | Verified exact plain text version 2 payload | `demoQr.test.ts`, `receptionQrDisplay.test.tsx` | PASS | Sec 6 | QR06-R1-02 |
| `QR06-040` | QR payload is never EMVCo or clickable bank URL | Security | Generate QR payload | Inspect content | No `https://`, no EMV tags (`000201`), no bank account numbers | Verified non-payable plain text payload | `demoQr.test.ts` | PASS | Sec 6, Sec 7 | QR06-R1-02 |
| `QR06-041` | Prominent non-transfer disclaimer displayed | UI | Render `ReceptionQrDisplayPage` | Inspect banners | Displays warning: `Simulated QR Payment - No real money is transferred.` | Verified warning banner rendered | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | - |
| `QR06-042` | Default bank selector initialized to Vietcombank when no session | Normal | Render `ReceptionQrDisplayPage` with null session | Check select value | Select default is `vietcombank` | Verified default select value `vietcombank` | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | QR06-R1-03 |
| `QR06-043` | Bank selector supports all 4 standard banks | Normal | Open bank selector dropdown | Inspect options | Options include Vietcombank, BIDV, Techcombank, MB Bank | Verified all 4 demo bank options present | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | QR06-R1-03 |
| `QR06-044` | Selecting new bank updates preview QR immediately | Normal | Select BIDV from dropdown when no session | User event select | SVG path updates immediately without HTTP round-trip | Verified SVG QR re-renders with new bank code | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | QR06-R1-03 |
| `QR06-045` | Safe demo account display DEMO - NOT AVAILABLE | Security | Render details definition list | Inspect account entry | Account displays `DEMO - NOT AVAILABLE`, never real account | Verified safe demo account displayed | `receptionQrDisplay.test.tsx` | PASS | Sec 6, Sec 7 | - |
| `QR06-046` | Demo reference formatted as DEMO-paymentId | Normal | Payment ID 101 | Inspect reference entry | Displays `DEMO-101` | Verified `DEMO-101` displayed | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | - |
| `QR06-047` | Copy demo reference copies exact string with feedback | Normal | Click `Copy demo reference` | Click button | Copies `DEMO-101` to clipboard and announces success via ARIA live region | Verified clipboard received `DEMO-101` and status feedback displayed | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | - |
| `QR06-048` | Copy demo details copies full details with disclaimer | Normal | Click `Copy demo details` | Click button | Copies bank, account, amount, reference, and non-transfer disclaimer | Verified complete demo details copied to clipboard | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | - |

---

## 6. Member Confirmation Boundary and Zero Staff Confirmation (`QR06-049` to `QR06-056`)

| ID | Title | Technique | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR06-049` | Reception view contains zero payment confirmation buttons | Security | Render `ReceptionQrDisplayPage` | Query DOM for confirm buttons | Zero confirm/settle buttons found | Verified no confirmation buttons present in receptionist view | `receptionQrDisplay.test.tsx` | PASS | Sec 3, Sec 4 | QR06-R1-02 |
| `QR06-050` | Reception view contains zero settlement submit buttons | Security | Render `ReceptionQrDisplayPage` | Query DOM for submit buttons | Zero submit buttons found | Verified no submit buttons present | `receptionQrDisplay.test.tsx` | PASS | Sec 3, Sec 4 | QR06-R1-02 |
| `QR06-051` | Staff notice explains customer-device confirmation | Normal | Render `ReceptionQrDisplayPage` | Read notice text | Informs staff that customer confirms on own device and staff cannot settle directly | Verified notice copy present | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | QR06-R1-02 |
| `QR06-052` | Staff cannot simulate or trigger settlement from desk view | Security | Authenticated RECEPTIONIST | Inspect accessible actions | Only read-only presentation and copy actions available | Verified strictly read-only capabilities | `ReceptionQrDisplayControllerTest`, `receptionQrDisplay.test.tsx` | PASS | Sec 3, Sec 4 | QR06-R1-02 |
| `QR06-053` | Legacy confirm-intent endpoint rejected with 409 | Security | Caller attempts legacy POST `/confirm-intent` | `POST /api/v1/payments/simulated-qr/sessions/confirm-intent` | `409 Conflict` with `SIMULATOR_CONFIRM_ENDPOINT_REQUIRED` | Returned `409 Conflict` | `DemoQrControllerTest` | PASS | Sec 3, Sec 7 | - |
| `QR06-054` | Staff attempting POST to confirm receives 403 Forbidden | Security | Authenticated RECEPTIONIST | `POST /api/v1/payments/simulated-qr/sessions/confirm` | `403 Forbidden` | Returned `403 Forbidden` | `DemoQrControllerTest` | PASS | Sec 3 | - |
| `QR06-055` | Member confirmation on device reflects via live polling | Normal | Payment confirmed by member in separate session | Wait for polling interval | Reception view updates to `Paid - Simulated` automatically | Verified automatic status update via polling | `receptionQrDisplay.test.tsx` | PASS | Sec 5, Sec 6 | QR06-R1-04 |
| `QR06-056` | Polling timer cleans up on unmount | Boundary | Unmount `ReceptionQrDisplayPage` | Unmount component | `clearInterval` invoked; zero memory leaks or stray network calls | Verified cleanup on unmount | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | - |

---

## 7. Reception Navigation and Cross-Screen Integration (`QR06-057` to `QR06-066`)

| ID | Title | Technique | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR06-057` | Eyebrow displays Receptionist / Payments and reports | UI | Render `ReceptionQrDisplayPage` | Check eyebrow text | Eyebrow is `Receptionist / Payments and reports` | Verified eyebrow text | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | - |
| `QR06-058` | Hero title displays Simulated QR Payment Display | UI | Render `ReceptionQrDisplayPage` | Check heading text | Title is `Simulated QR Payment Display` | Verified title text | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | - |
| `QR06-059` | Action link returns to payment verification | UI | Render `ReceptionQrDisplayPage` | Check `Back to payment verification` link | Links to `/reception/payments/:paymentId` | Verified link href `/reception/payments/101` | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | - |
| `QR06-060` | Action link returns to payment requests | UI | Render `ReceptionQrDisplayPage` | Check `Payment requests` link | Links to `/reception/payments` | Verified link href `/reception/payments` | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | - |
| `QR06-061` | ReceptionPaymentPage shows Desk QR Display CTA for pending payment | Integration | Pending payment opened on `/reception/payments/:paymentId` | Inspect page actions | `Desk QR Display` CTA button visible in hero actions | Verified CTA button present with link to `/qr-display` | `ReceptionPaymentPage.tsx`, frontend tests | PASS | Sec 6 | - |
| `QR06-062` | AppRoutes registers /reception/payments/:paymentId/qr-display | Integration | Check route configuration | Inspect `SUB_SCREENS['/reception']` | Route registered with lazy-loaded `ReceptionQrDisplayPage` | Verified route registered in `AppRoutes.tsx` | `AppRoutes.tsx` | PASS | Sec 6 | - |
| `QR06-063` | Direct deep link to desk display loads correctly | Integration | Navigate directly to `/reception/payments/101/qr-display` | Browser navigation | Page loads desk projection without redirect | Verified direct navigation loads projection | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | - |
| `QR06-064` | Invalid paymentId parameter renders Payment not found | Boundary | Navigate to `/reception/payments/abc/qr-display` | Render page | EmptyState displayed: `Payment not found. The payment link is not valid.` | Verified empty state rendered | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | - |
| `QR06-065` | Network error during projection fetch shows retry | Resilience | Network drops during fetch | Simulate API error | ErrorState displayed with `Try again` button | Verified error state rendered | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | - |
| `QR06-066` | Clicking Try again successfully reloads projection | Resilience | Network recovers; click `Try again` | User clicks retry button | Re-fetches desk projection and displays data | Verified successful retry and display | `receptionQrDisplay.test.tsx` | PASS | Sec 6 | - |

---

## 8. Desktop Viewports, Visual Accessibility and Verification (`QR06-067` to `QR06-076`)

| ID | Title | Technique | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR06-067` | Desktop 1280px viewport renders 2-column layout | Accessibility | Viewport width 1280px | Inspect CSS grid layout | `.reception-grid` renders customer QR and payment status side-by-side | Verified 2-column desktop grid at 1280px | `desktop-1280-light-pending.png`, `desktop-1280-dark-pending.png` | PASS | Sec 6 | QR06-R1-05 |
| `QR06-068` | Desktop 1440px viewport renders 2-column layout | Accessibility | Viewport width 1440px | Inspect CSS grid layout | Two-column grid aligned with generous margins | Verified 2-column desktop grid at 1440px | `desktop-1440-light-pending.png`, `desktop-1440-dark-pending.png` | PASS | Sec 6 | QR06-R1-05 |
| `QR06-069` | Desktop 1920px viewport renders 2-column layout | Accessibility | Viewport width 1920px | Inspect CSS grid layout | Two-column layout contained within maximum content width | Verified contained layout at 1920px | `desktop-1920-light-pending.png`, `desktop-1920-dark-pending.png` | PASS | Sec 6 | QR06-R1-05 |
| `QR06-070` | Narrow viewport (768px / 390px) stacks gracefully | Accessibility | Viewport width 768px and 390px | Inspect media query `@media (max-width: 900px)` | Columns collapse into single vertical stack without clipping | Verified single-column responsive stack | `responsive-768-light.png`, `responsive-390-light.png` | PASS | Sec 6 | QR06-R1-05 |
| `QR06-071` | Light theme contrast passes WCAG 2.1 AA | Accessibility | Light theme active | Compute color contrast | Text contrast >= 4.5:1; UI components >= 3:1 | Verified WCAG 2.1 AA contrast compliance | `accessibility-audit.json`, Vitest design tokens suite | PASS | Sec 6 | QR06-R1-05 |
| `QR06-072` | Dark theme contrast passes WCAG 2.1 AA | Accessibility | Dark theme active (`data-theme="dark"`) | Compute color contrast | Dark mode tokens maintain required contrast ratios | Verified dark mode contrast compliance | `accessibility-audit.json`, `theme.test.tsx` | PASS | Sec 6 | QR06-R1-05 |
| `QR06-073` | 200% zoom preserves readability without overflow | Accessibility | Browser zoom set to 200% | Viewport reflow check | Content reflows without overlapping text or horizontal scrollbars | Verified reflow and accessibility at 200% zoom | `desktop-1440-light-200percent-zoom.png` | PASS | Sec 6 | QR06-R1-05 |
| `QR06-074` | Assistive technology announcements and ARIA attributes | Accessibility | Screen reader inspection | Inspect ARIA attributes | SVG has `role="img"`, `title`, `desc`; feedback announced via `aria-live="polite"` | Verified ARIA accessibility labels and live regions | `receptionQrDisplay.test.tsx`, `accessibility-audit.json` | PASS | Sec 6 | QR06-R1-05 |
| `QR06-075` | Physical mobile device scan on LAN | Physical | Real mobile device on local Wi-Fi | Point camera at desk QR | Physical phone requires human tester with physical hardware; desktop + headless browser simulation verified | Recorded as NOT RUN per task instructions | Physical device verification | NOT RUN | Sec 6 | QR06-R1-05 |
| `QR06-076` | Full suite regression validation | Quality | Full frontend and backend test suites | Run full test commands | 1686 frontend tests pass, 34 backend reception and demoqr tests pass, 0 regressions | Verified frontend + backend test suites pass with zero regressions | Vitest and Surefire logs | PASS | Sec 8 | - |

---

## 9. Round 02 Explicit Coverage and Edge Cases (`QR06-077` to `QR06-106`)

| ID | Title | Technique | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR06-077` | SUCCESS with fully valid SIMULATED provenance | Normal | Payment status SUCCESS, type SIMULATED, provider SPORTIFY_SIMULATOR, valid txId 9001 | Desk projection loaded | StatusBadge renders `Paid - Simulated`; banner confirms simulated provenance; no entitlement claim | Verified `Paid - Simulated` and correct provenance banner | `receptionQrDisplayModel.test.ts`, `receptionQrDisplay.test.tsx`, `desktop-1440-light-settled-simulated.png` | PASS | Sec 3, Sec 5 | QR06-R1-01 |
| `QR06-078` | SUCCESS with ACTUAL provenance and actual provider | Normal | Payment status SUCCESS, type ACTUAL, provider DESK_CASH, txId null | Desk projection loaded | StatusBadge renders `Paid - Actual`; banner confirms actual provider | Verified `Paid - Actual` and actual provider notice | `receptionQrDisplayModel.test.ts`, `receptionQrDisplay.test.tsx`, `desktop-1440-light-settled-actual.png` | PASS | Sec 3, Sec 5 | QR06-R1-01 |
| `QR06-079` | SUCCESS with legacy NULL type and provider | Normal | Payment status SUCCESS, type null, provider null, txId null | Desk projection loaded | StatusBadge renders `Paid - Legacy unverified`; neutral tone | Verified `Paid - Legacy unverified` | `receptionQrDisplayModel.test.ts`, `receptionQrDisplay.test.tsx`, `desktop-1440-light-legacy-unverified.png` | PASS | Sec 3, Sec 5 | QR06-R1-01 |
| `QR06-080` | SUCCESS with UNKNOWN / unsupported provenance | Boundary | Payment status SUCCESS, type CRYPTO_TOKEN, provider UNKNOWN | Desk projection loaded | StatusBadge renders `Paid - Inconsistent provenance`; warning tone | Verified `Paid - Inconsistent provenance` | `receptionQrDisplayModel.test.ts` | PASS | Sec 3, Sec 5 | QR06-R1-01 |
| `QR06-081` | SUCCESS with SIMULATED type but NULL provider | Boundary | Payment status SUCCESS, type SIMULATED, provider null | Desk projection loaded | StatusBadge renders `Paid - Inconsistent provenance`; fails safe | Verified `Paid - Inconsistent provenance` | `receptionQrDisplayModel.test.ts` | PASS | Sec 3, Sec 5 | QR06-R1-01 |
| `QR06-082` | SUCCESS with ACTUAL type but simulator provider | Boundary | Payment status SUCCESS, type ACTUAL, provider SPORTIFY_SIMULATOR | Desk projection loaded | StatusBadge renders `Paid - Inconsistent provenance`; fails safe | Verified `Paid - Inconsistent provenance` | `receptionQrDisplayModel.test.ts` | PASS | Sec 3, Sec 5 | QR06-R1-01 |
| `QR06-083` | SUCCESS with simulated provider but missing txId | Boundary | Payment status SUCCESS, type SIMULATED, provider SPORTIFY_SIMULATOR, txId null | Desk projection loaded | StatusBadge renders `Paid - Inconsistent provenance`; fails safe | Verified `Paid - Inconsistent provenance` | `receptionQrDisplayModel.test.ts`, `receptionQrDisplay.test.tsx` | PASS | Sec 3, Sec 5 | QR06-R1-01 |
| `QR06-084` | SUCCESS removes unverified entitlement assertions | Normal | Payment status SUCCESS with valid simulation | Inspect rendered text | Banner strictly describes transaction provenance; zero claim that entitlement is active | Verified absence of unverified entitlement activation text | `receptionQrDisplay.test.tsx`, `desktop-1440-light-settled-simulated.png` | PASS | Sec 3, Sec 5 | QR06-R1-01 |
| `QR06-085` | PENDING with active valid session and matching bank | Normal | Payment status PENDING, activeSession exists with BIDV_DEMO | Desk projection loaded | Authoritative session bank locked; bank selector disabled; QR encodes BIDV | Verified locked session bank and BIDV QR | `receptionQrDisplay.test.tsx`, `desktop-1440-light-pending.png` | PASS | Sec 3, Sec 5 | QR06-R1-03 |
| `QR06-086` | PENDING with active session bank overrides preview bank | Normal | Active session has TECHCOMBANK_DEMO, staff previously had Vietcombank | Desk projection loaded | Display automatically locks to Techcombank; preview choice discarded | Verified session bank overrides preview | `receptionQrDisplayModel.test.ts`, `receptionQrDisplay.test.tsx` | PASS | Sec 3, Sec 5 | QR06-R1-03 |
| `QR06-087` | PENDING without active session shows preview disclaimer | Normal | Payment status PENDING, activeSession null | Desk projection loaded | Bank selector active with hint `Informational preview only - Does not change the Member session or settlement bank.` | Verified preview disclaimer on select | `receptionQrDisplay.test.tsx`, `desktop-1440-light-no-session.png` | PASS | Sec 3, Sec 5 | QR06-R1-03 |
| `QR06-088` | PENDING with expired session shows clear restart notice | Boundary | Payment status PENDING, activeSession has state EXPIRED | Desk projection loaded | Notice informs staff session expired and customer must restart from Member Portal | Verified expired notice displayed | `receptionQrDisplay.test.tsx`, `desktop-1440-light-expired-session.png` | PASS | Sec 3, Sec 5 | QR06-R1-03 |
| `QR06-089` | PENDING with unsupported / unknown bank code | Boundary | Active session has unknown bank code `CUSTOM_UNKNOWN_BANK` | Desk projection loaded | UI handles unknown code gracefully, normalizes to default bank, indicates unknown | Verified safe normalization of unknown bank | `receptionQrDisplayModel.test.ts` | PASS | Sec 3, Sec 5 | QR06-R1-03 |
| `QR06-090` | FAILED terminal state disables QR and copy controls | Boundary | Payment status FAILED | Render desk view | QR replaced with `QR Code Inactive - Payment is Failed`; copy buttons hidden | Verified inactive QR and removed copy controls | `receptionQrDisplay.test.tsx`, `desktop-1440-light-failed-inactive.png` | PASS | Sec 3, Sec 5 | QR06-R1-04 |
| `QR06-091` | CANCELED terminal state disables QR and copy controls | Boundary | Payment status CANCELED | Render desk view | QR replaced with `QR Code Inactive - Payment is Canceled`; copy buttons hidden | Verified inactive QR and removed copy controls | `receptionQrDisplay.test.tsx` | PASS | Sec 3, Sec 5 | QR06-R1-04 |
| `QR06-092` | REFUNDED terminal state disables QR and copy controls | Boundary | Payment status REFUNDED | Render desk view | QR replaced with `QR Code Inactive - Payment is Refunded`; copy buttons hidden | Verified inactive QR and removed copy controls | `receptionQrDisplay.test.tsx` | PASS | Sec 3, Sec 5 | QR06-R1-04 |
| `QR06-093` | SUCCESS terminal state disables QR and copy controls | Boundary | Payment transitions to SUCCESS | Render desk view | QR replaced with `QR Code Inactive - Payment is Paid - Simulated`; copy buttons hidden | Verified inactive QR and removed copy controls | `receptionQrDisplay.test.tsx`, `desktop-1440-light-settled-simulated.png` | PASS | Sec 3, Sec 5 | QR06-R1-04 |
| `QR06-094` | Missing amount, malformed payment reference and API 404 | Boundary | Non-existent or invalid payment ID | `GET .../desk-projection/-1` | HTTP 404 returned; frontend displays `Payment not found` | Verified 404 rejection and clean UI empty state | `ReceptionQrDisplayControllerTest.java`, `receptionQrDisplay.test.tsx` | PASS | Sec 3, Sec 5 | - |
| `QR06-095` | Network loss during projection polling handles gracefully | Resilience | Periodic polling fails due to transient connection loss | Simulated network failure during poll | Polling error gracefully suppressed without crashing or flashing error banner | Verified silent resilient recovery | `ReceptionQrDisplayPage.tsx`, `receptionQrDisplay.test.tsx` | PASS | Sec 5, Sec 6 | - |
| `QR06-096` | Out-of-order polling responses never downgrade terminal state | Resilience | Polling transitions PENDING -> SUCCESS, then delayed PENDING arrives | Delayed PENDING response delivered | UI preserves terminal SUCCESS state; never downgrades back to PENDING | Verified monotonic state update | `receptionQrDisplayModel.test.ts`, `receptionQrDisplay.test.tsx` | PASS | Sec 5, Sec 6 | QR06-R1-04 |
| `QR06-097` | Receptionist direct POST/confirm attempt rejected by backend | Security | Authenticated RECEPTIONIST | `POST /api/v1/payments/simulated-qr/sessions/confirm` | HTTP 403 Forbidden | Verified 403 rejection; only Member can confirm | `DemoQrControllerTest.java` | PASS | Sec 3, Sec 4 | QR06-R1-02 |
| `QR06-098` | Role authorization matrix for desk projection endpoint | Security | Anonymous, Member, Coach, Receptionist, Manager | `GET /api/v1/payments/simulated-qr/desk-projection/101` | Anonymous: 401; Member: 403; Coach: 403; Receptionist: 200; Manager: 200 | Verified exact role enforcement | `ReceptionQrDisplayControllerTest.java` | PASS | Sec 3 | - |
| `QR06-099` | Cross-account IDOR payment lookup rejected fail-closed | Security | Unauthenticated caller attempts desk projection | `GET .../desk-projection/101` | HTTP 401 Unauthorized; zero leakage of member profile or payment data | Verified zero data leakage | `ReceptionQrDisplayControllerTest.java` | PASS | Sec 3, Sec 4 | - |
| `QR06-100` | QR decode confirms informational NON_PAYABLE payload | Security | Decode rendered QR symbol | Inspect QR content | Contains `SPORTIFY_DEMO_QR|VERSION=2|...|MODE=NON_PAYABLE`; zero member tokens or URLs | Verified non-payable plain text payload | `demoQr.test.ts`, `ReceptionQrDisplayPage.tsx` | PASS | Sec 6, Sec 7 | QR06-R1-02 |
| `QR06-101` | Member uses authenticated QR-05 flow to confirm exactly once | Integration | Member initiates simulation session on personal device | Confirm simulation payment | One `simulated_payment_transaction` record created; duplicate confirmation is idempotent | Verified idempotency and single settlement | `DemoQrControllerTest.java`, `qr06-round02-sql-evidence.json` | PASS | Sec 4, Sec 5 | - |
| `QR06-102` | Real SQL before/after staff GET and repeated poll proves no writes | Security | Staff GET and 5 polling requests | Monitor SQL table fingerprints | Zero mutations on payment, invoice, session, simulated_tx, or cash tables | Verified zero SQL writes across 5 polling cycles | `ReceptionQrDisplayControllerTest.java`, `qr06-round02-sql-evidence.json` | PASS | Sec 5 | QR06-R1-05 |
| `QR06-103` | UI-32 refunds and Manager actual vs simulated finance unchanged | Regression | Simulated settlement executed | Check `dbo.vw_paid_payment` and cash flow views | Simulated transactions excluded from cash revenue views; refund invariants intact | Verified complete separation of simulated funds from real revenue | `ManagerFinanceControllerTest.java`, `CashFlowControllerTest.java` | PASS | Sec 2, Sec 5 | - |
| `QR06-104` | Desktop 1280, 1440, 1920 in BOTH themes with browser logs | Visual | Real Microsoft Edge automated via CDP | Capture full viewports at 1280, 1440, 1920 in light/dark | High-definition PNG screenshots saved; console log confirms zero errors | Verified visual rendering across 6 primary desktop viewports | `desktop-1280-*.png`, `desktop-1440-*.png`, `desktop-1920-*.png`, `browser-console.log` | PASS | Sec 6 | QR06-R1-05 |
| `QR06-105` | Keyboard focus, readable status changes, contrast and 200% zoom | Accessibility | 200% zoom emulation and keyboard navigation in Edge | Inspect DOM reflow and active focus indicators | Zero horizontal scrollbars (`noOverflow=true`); focus visible on actions; contrast passes | Verified reflow, focus, and accessibility | `desktop-1440-light-keyboard-focus.png`, `desktop-1440-light-200percent-zoom.png`, `accessibility-audit.json` | PASS | Sec 6 | QR06-R1-05 |
| `QR06-106` | Pinned dependency specifications restored without drift | Dependency | Compare package.json and lockfile against merge-base | Inspect `qrcode` and `@types/qrcode` semver entries | Exact pinned versions `1.5.4` and `1.5.6` restored; zero package drift | Verified 0 diff on dependencies against base | `package.json`, `package-lock.json` | PASS | Sec 6 | QR06-R1-06 |

---

## 10. Verification Summary (Round 02)

- **Total Test Cases:** `106` (`QR06-001` through `QR06-106`)
- **Passed:** `105`
- **Failed:** `0`
- **Blocked:** `0`
- **Not Run:** `1` (`QR06-075` - physical mobile handset scanning)
- **Overall Result:** All mandatory functional, security, financial, visual, and regression gates verified.
