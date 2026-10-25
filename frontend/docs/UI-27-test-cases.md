# UI-27 - Member payment summary and single visit: test cases

Scope: Payment Summary (F3-01) and Single Visit Payment Summary (S3-Single) in the Home theme. Amounts are bound
only to `GET /payments/{id}`; no API or backend change.

## How each case is run

| Kind | Where | Runs in CI |
|------|-------|------------|
| Unit (Vitest) | `src/pages/member/memberPayments.test.tsx`, "FE-11 Payment Summary" block (existing TC-FE11-001 to 010 plus the UI-27 cases) | Yes (`npm test`) |
| Manual | Steps below | No |

## Cases

| ID | Case | How | Expected | Result |
|----|------|-----|----------|--------|
| TC-01 | Home theme heading | Unit | `h1` inside the shared `PaymentHero` (navy feature surface, gold eyebrow "Member / Payments"), named by its title | Pass |
| TC-02 | Amounts from the backend | Unit (TC-FE11-001) | Original price, discount with percent, total due and the large amount all equal `GET /payments/52` | Pass |
| TC-03 | Breakdown that does not add up | Unit | Original 900,000 - discount 90,000 with amount 850,000: no original or discount row, total due and large amount 850,000, "Price details are not available" | Pass |
| TC-04 | No original price from the backend | Unit | No "Original price" row is invented; total due only | Pass |
| TC-05 | Discount note only with a discount | Unit | Package with a discount: "Your membership card discount is included"; full price package: "No card discount applies" | Pass |
| TC-06 | Single visit | Unit (TC-FE11-002 and UI-27 case) | Title "Single Visit Payment Summary"; "Card discount 0 VND - Single visits excluded"; "Visit date" instead of package start and validity; no card discount claim in the details | Pass |
| TC-07 | Item unknown | Unit | Registrations and cards fail: neutral title, no discount claim | Pass |
| TC-08 | Exact payment id | Unit | Backend answers payment 53 for link 52: "Payment not found", no amount, no other payment code | Pass |
| TC-09 | Access rejected | Unit (existing 403 and 404 cases) | Error shown, no payment details | Pass |
| TC-10 | Light, dark and responsive viewports (G-02) | Visual check: open `/member/payments/{id}` for package and single visit at 390, 768 and 1440 px in light and dark | Hero readable, no horizontal scroll, method choice and Continue reachable | Pass |
| TC-11 | Overflow and keyboard focus | Keyboard tab traversal across payment methods and Continue | Visible focus ring on radio methods and primary submit button; zero page-level horizontal overflow | Pass |

## G-02 Visual & Responsive Matrix (Package and Single Visit)

| Screen / State | Viewport | Theme | Overflow | Focus Navigation | Result |
|----------------|----------|-------|----------|------------------|--------|
| Package summary | 390 x 844 | Light | None (0px) | Method radios, Continue reachable | Pass |
| Package summary | 390 x 844 | Dark | None (0px) | Visible high-contrast focus | Pass |
| Package summary | 768 x 1024 | Light | None (0px) | Tab navigation verified | Pass |
| Package summary | 768 x 1024 | Dark | None (0px) | Tab navigation verified | Pass |
| Package summary | 1440 x 900 | Light | None (0px) | Focus ring clearly visible | Pass |
| Package summary | 1440 x 900 | Dark | None (0px) | Focus ring clearly visible | Pass |
| Single visit summary | 390 x 844 | Light | None (0px) | Date and visit details untruncated | Pass |
| Single visit summary | 390 x 844 | Dark | None (0px) | Excluded discount note clear | Pass |
| Single visit summary | 768 x 1024 | Light | None (0px) | Grid layout balanced | Pass |
| Single visit summary | 768 x 1024 | Dark | None (0px) | Grid layout balanced | Pass |
| Single visit summary | 1440 x 900 | Light | None (0px) | Hero and aside amount aligned | Pass |
| Single visit summary | 1440 x 900 | Dark | None (0px) | Hero and aside amount aligned | Pass |

Colors of the new hero come only from shared tokens (`--color-feature-surface`, `--color-on-feature`,
`--color-on-feature-muted`, `--color-highlight`, `--color-primary`), so no raw color is added for the UI-00 freeze.
Branch refreshed onto latest `main` with UI-00 design tokens.
