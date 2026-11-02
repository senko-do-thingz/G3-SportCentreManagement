# RECEPTION-D03 - Reception Legacy Settlement Provenance Maintenance - Test Cases

**Workstream:** Reception UI and Receipt Presentation (FE-12 / QR-07 Maintenance)
**Maintenance Scope:** Reception Legacy Settlement Provenance Classification and Receipt Presentation
**Branch:** `fix/reception-legacy-provenance-receipt`
**Base Commit:** `97abe0feebd0448cb87d9f89347ab8af2a320991` (origin/main)
**Target Pull Request Base:** `main`
**Target Acceptance Cases:** QR07-056, QR07-069

---

## 1. Overview and Rationale

In legacy database states or prior API projections, payments recorded as successful (`status: 'SUCCESS'` or invoice `status: 'PAID'`) or refunded (`status: 'REFUNDED'`) may have `settlementType: null` or `'UNKNOWN'`, or missing/contradictory settlement provider and transaction metadata.

Previous behavior in `ReceptionReceiptPage` and `ReceptionPaymentPage`:
1. Presented legacy records with an unqualified hero statement: `Confirmed payment record.`
2. Permitted printing of receipts that visually appeared identical to verified settlement receipts, even when no authoritative bank or simulated settlement record existed.
3. Allowed `REFUNDED` status to unconditionally bypass settlement verification and enable receipt printing (Finding D03-01).
4. Mixed provenance fields independently between payment and invoice using nullish fallback (`??`), which could fabricate a composite verified record from an unverified invoice (Finding D03-02).
5. Accepted `undefined` as valid for `simulatedTransactionId` while rejecting explicit `null` (Observation D03-04).

Remediation implemented:
1. Centralized pure provenance classifier `classifyProvenance()` and cross-source reconciliation helper `reconcileProvenance()` in `frontend/src/pages/reception/receptionProvenanceModel.ts`.
2. Provenance-aware refund policy: `REFUNDED` records inspect underlying settlement provenance; records with NULL, UNKNOWN, or contradictory metadata are classified as `LEGACY_UNVERIFIED` or `INCONSISTENT` and blocked from verified receipt printing (D03-01).
3. Cross-source reconciliation: In `ReceptionPaymentPage.tsx`, missing invoice provenance is never upgraded by payment fields; any material disagreement fails closed as `INCONSISTENT` with receipt printing disabled (D03-02).
4. Cross-source reconciliation for REFUNDED records: `reconcileProvenance()` evaluates underlying settlement type, provider, and simulated transaction ID for refunded payments, blocking receipt printing and flagging `INCONSISTENT` when metadata contradicts across payment and invoice records (D03-05).
5. Provenance-sensitive status badge: In `ReceptionPaymentPage.tsx`, renders `<Badge tone={presentation.statusBadgeTone}>`, ensuring inconsistent/unverified payments are never displayed with verified green success badges (D03-06).
6. Simulated settlement validation: Strictly requires `simulatedTransactionId` to be a valid positive integer (`typeof txId === 'number' && Number.isSafeInteger(txId) && txId > 0`), aligning with backend database check constraint `ck_payment_simulated_consistency` in V36 (D03-04).
7. Visible `Legacy unverified` badge and explicit warning banner on `ReceptionReceiptPage` and `ReceptionPaymentPage`.
8. Print restriction: Verified-receipt printing (`canPrintReceipt: false`) is strictly disabled for `LEGACY_UNVERIFIED` and `INCONSISTENT` records. The `Print receipt` button is disabled, the `.payment-print-receipt` CSS class is suppressed, and an explicit informational alert is shown.
9. Print CSS hardening in `frontend/src/styles/reception.css`: `@media print` rules ensure warning and provenance notices survive native print media with 2px solid border and bold font weight, and unverified cards carry a 2px dashed border to prevent misrepresentation under browser-native Ctrl+P (D03-03).
10. Cross-source reconciliation for REFUNDED ACTUAL records with nullable providers: `reconcileProvenance()` recognizes that under V35/V36, `settlement_provider` is nullable for `ACTUAL` settlements. Two consistently absent provider values are consistent (`canPrintReceipt: true`), while a provider present on only one source or two differing providers remains a material inconsistency (`canPrintReceipt: false`) (D03-07).
11. Receipt card print styling alignment: In `ReceptionPaymentPage.tsx`, the card always carries `.payment-receipt` (and `.payment-print-receipt` when printable), ensuring `@media print` rules for dashed borders on unverified receipts and bordered warning notices apply under native print (D03-07).

---

## 2. Test Execution Matrix

| Test ID | Category | Scenario Description | Expected Outcome | Execution Result |
| :--- | :--- | :--- | :--- | :--- |
| **TC-D03-PROV-01** | Unit Model | Pure classification: `VALID_SIMULATED` with positive txId | `provenanceType: 'VALID_SIMULATED'`, `canPrintReceipt: true`, status label `'Paid - Simulated'`, tone `'success'` | **PASS** |
| **TC-D03-PROV-02** | Unit Model | Simulated txId negative control: undefined, null, or zero (D03-04) | `classification: 'INCONSISTENT'`, `canPrintReceipt: false`, status label `'Paid - Inconsistent provenance'` | **PASS** |
| **TC-D03-PROV-03** | Unit Model | Pure classification: `ACTUAL` (`DESK_CASH`, `MANUAL_TRANSFER`, etc.) | `provenanceType: 'ACTUAL'`, `canPrintReceipt: true`, status label `'Paid'`, tone `'success'` | **PASS** |
| **TC-D03-PROV-04** | Unit Model | Pure classification: `LEGACY_UNVERIFIED` (`null` settlementType) | `provenanceType: 'LEGACY_UNVERIFIED'`, `canPrintReceipt: false`, status label `'Paid - Legacy unverified'`, tone `'neutral'` | **PASS** |
| **TC-D03-PROV-05** | Unit Model | Pure classification: `LEGACY_UNVERIFIED` (`UNKNOWN` settlementType) | `provenanceType: 'LEGACY_UNVERIFIED'`, `canPrintReceipt: false`, status label `'Paid - Legacy unverified'`, tone `'neutral'` | **PASS** |
| **TC-D03-PROV-06** | Unit Model | Contradictory `ACTUAL` with `SPORTIFY_SIMULATOR` | `provenanceType: 'INCONSISTENT'`, `canPrintReceipt: false`, status label `'Paid - Inconsistent provenance'`, tone `'danger'` | **PASS** |
| **TC-D03-PROV-07** | Unit Model | Contradictory `SIMULATED` with non-simulator provider | `provenanceType: 'INCONSISTENT'`, `canPrintReceipt: false`, status label `'Paid - Inconsistent provenance'`, tone `'danger'` | **PASS** |
| **TC-D03-PROV-08** | Unit Model | Contradictory null settlementType with simulated txId | `provenanceType: 'INCONSISTENT'`, `canPrintReceipt: false` | **PASS** |
| **TC-D03-REF-01** | Refund Model | REFUNDED with verified ACTUAL settlement | `classification: 'REFUNDED'`, `canPrintReceipt: true`, status label `'Refunded'`, provider label `'ACTUAL (DESK_CASH)'` | **PASS** |
| **TC-D03-REF-02** | Refund Model | REFUNDED with verified SIMULATED settlement | `classification: 'REFUNDED'`, `canPrintReceipt: true`, status label `'Refunded'`, simulation warning retained | **PASS** |
| **TC-D03-REF-03** | Refund Model | REFUNDED with NULL settlement provenance (D03-01) | `classification: 'LEGACY_UNVERIFIED'`, `canPrintReceipt: false`, status label `'Refunded - Legacy unverified'` | **PASS** |
| **TC-D03-REF-04** | Refund Model | REFUNDED with UNKNOWN settlement provenance (D03-01) | `classification: 'LEGACY_UNVERIFIED'`, `canPrintReceipt: false`, status label `'Refunded - Legacy unverified'` | **PASS** |
| **TC-D03-REF-05** | Refund Model | REFUNDED with contradictory provenance (D03-01) | `classification: 'INCONSISTENT'`, `canPrintReceipt: false`, status label `'Refunded - Inconsistent provenance'` | **PASS** |
| **TC-D03-REF-06** | Refund Reconcile | Payment REFUNDED/ACTUAL vs invoice REFUNDED/SIMULATED (D03-05) | `classification: 'INCONSISTENT'`, `canPrintReceipt: false`, status label `'Refunded - Inconsistent provenance'` | **PASS** |
| **TC-D03-REF-07** | Refund Reconcile | Payment REFUNDED/ACTUAL/provider-A vs invoice REFUNDED/ACTUAL/provider-B (D03-05) | `classification: 'INCONSISTENT'`, `canPrintReceipt: false`, status label `'Refunded - Inconsistent provenance'` | **PASS** |
| **TC-D03-REF-08** | Refund Reconcile | Payment REFUNDED/SIMULATED/txId-101 vs invoice REFUNDED/SIMULATED/txId-202 (D03-05) | `classification: 'INCONSISTENT'`, `canPrintReceipt: false`, status label `'Refunded - Inconsistent provenance'` | **PASS** |
| **TC-D03-REF-09** | Refund Reconcile | Legacy/UNKNOWN REFUNDED on either side with verified provenance (D03-05) | `classification: 'INCONSISTENT'`, `canPrintReceipt: false`, status label `'Refunded - Inconsistent provenance'` | **PASS** |
| **TC-D03-REF-10** | Refund Reconcile | REFUNDED ACTUAL with missing invoice provider (D03-05) | `classification: 'INCONSISTENT'`, `canPrintReceipt: false`, status label `'Refunded - Inconsistent provenance'` | **PASS** |
| **TC-D03-REF-11** | Refund Reconcile | Consistent verified REFUNDED ACTUAL across sources (D03-05) | `classification: 'REFUNDED'`, `canPrintReceipt: true`, status label `'Refunded'`, tone `'neutral'` | **PASS** |
| **TC-D03-REF-12** | Refund Reconcile | Consistent verified REFUNDED SIMULATED across sources (D03-05) | `classification: 'REFUNDED'`, `canPrintReceipt: true`, status label `'Refunded'`, simulation banner retained | **PASS** |
| **TC-D03-REF-13** | Refund Reconcile | REFUNDED ACTUAL with both providers null (D03-07) | `classification: 'REFUNDED'`, `canPrintReceipt: true`, status label `'Refunded'`, tone `'neutral'`, provider `'ACTUAL'` | **PASS** |
| **TC-D03-REF-14** | Refund Reconcile | REFUNDED ACTUAL with payment provider null and invoice non-null (D03-07) | `classification: 'INCONSISTENT'`, `canPrintReceipt: false`, status label `'Refunded - Inconsistent provenance'` | **PASS** |
| **TC-D03-REF-15** | Refund Reconcile | REFUNDED ACTUAL with matching normalized provider names (D03-07) | `classification: 'REFUNDED'`, `canPrintReceipt: true`, status label `'Refunded'`, provider `'ACTUAL (DESK_CASH)'` | **PASS** |
| **TC-D03-REC-01** | Reconciliation | Absent or loading invoice disables receipt printing | `canPrintReceipt: false`, reflects payment status safely | **PASS** |
| **TC-D03-REC-02** | Reconciliation | Invoice NULL provenance vs Payment ACTUAL (D03-02) | `classification: 'INCONSISTENT'`, `canPrintReceipt: false`, cross-source upgrade blocked | **PASS** |
| **TC-D03-REC-03** | Reconciliation | Invoice UNKNOWN provenance vs Payment ACTUAL (D03-02) | `classification: 'INCONSISTENT'`, `canPrintReceipt: false`, cross-source upgrade blocked | **PASS** |
| **TC-D03-REC-04** | Reconciliation | Invoice ACTUAL vs Payment SIMULATED (D03-02) | `classification: 'INCONSISTENT'`, `canPrintReceipt: false`, cross-source mismatch blocked | **PASS** |
| **TC-D03-REC-05** | Reconciliation | Invoice SIMULATED vs Payment ACTUAL (D03-02) | `classification: 'INCONSISTENT'`, `canPrintReceipt: false`, cross-source mismatch blocked | **PASS** |
| **TC-D03-REC-06** | Reconciliation | Conflicting ACTUAL providers (DESK_CASH vs MANUAL) | `classification: 'INCONSISTENT'`, `canPrintReceipt: false` | **PASS** |
| **TC-D03-REC-07** | Reconciliation | Conflicting simulatedTransactionId across sources | `classification: 'INCONSISTENT'`, `canPrintReceipt: false` | **PASS** |
| **TC-D03-REC-08** | Reconciliation | Consistent verified ACTUAL across both sources | `classification: 'ACTUAL'`, `canPrintReceipt: true`, status label `'Paid'` | **PASS** |
| **TC-D03-REC-09** | Reconciliation | Consistent verified SIMULATED across both sources | `classification: 'VALID_SIMULATED'`, `canPrintReceipt: true`, status label `'Paid - Simulated'` | **PASS** |
| **TC-D03-REC-10** | Reconciliation | Consistent legacy NULL records across both sources | `classification: 'LEGACY_UNVERIFIED'`, `canPrintReceipt: false`, status label `'Paid - Legacy unverified'` | **PASS** |
| **TC-D03-RCPT-01** | Reception Receipt | Null settlementType displays `Paid - Legacy unverified` | Explicit warning banner rendered; hero does not claim confirmed record; print disabled; `.payment-print-receipt` absent | **PASS** |
| **TC-D03-RCPT-02** | Reception Receipt | UNKNOWN settlementType displays `Paid - Legacy unverified` | Explicit warning banner rendered; print button disabled; `.payment-print-receipt` absent | **PASS** |
| **TC-D03-RCPT-03** | Reception Receipt | Inconsistent metadata displays `Paid - Inconsistent provenance` | Warning notice rendered; print button disabled; `.payment-print-receipt` absent | **PASS** |
| **TC-D03-RCPT-04** | Reception Receipt | Genuine ACTUAL settlement preserves verified receipt | Status `'Paid'`, provider `'ACTUAL (DESK_CASH)'`, print enabled, `.payment-print-receipt` present | **PASS** |
| **TC-D03-RCPT-05** | Reception Receipt | Genuine SIMULATED settlement preserves simulated receipt | Status `'Paid - Simulated'`, warning banner rendered, print enabled, `.payment-print-receipt` present | **PASS** |
| **TC-D03-RCPT-06** | Reception Receipt | REFUNDED with NULL provenance blocks printing (D03-01) | Status `'Refunded - Legacy unverified'`, warning notice rendered, print button disabled | **PASS** |
| **TC-D03-RCPT-07** | Reception Receipt | REFUNDED with UNKNOWN provenance blocks printing (D03-01) | Status `'Refunded - Legacy unverified'`, print button disabled | **PASS** |
| **TC-D03-RCPT-08** | Reception Receipt | REFUNDED with genuine ACTUAL allows verified refund receipt | Status `'Refunded'`, provider `'ACTUAL (DESK_CASH)'`, print button enabled | **PASS** |
| **TC-D03-PAYM-01** | Reception Payment | Null settlementType displays `Paid - Legacy unverified` with `badge--neutral` | Warning notice displayed; print disabled; badge has `badge--neutral`, not `badge--success` | **PASS** |
| **TC-D03-PAYM-02** | Reception Payment | Inconsistent provenance displays `Paid - Inconsistent provenance` with `badge--warning` (D03-06) | Badge tone is `badge--warning`, not `badge--success`; print button disabled | **PASS** |
| **TC-D03-PAYM-03** | Reception Payment | REFUNDED with NULL provenance blocks printing with `badge--neutral` (D03-01) | Status `'Refunded - Legacy unverified'`, print button disabled, warning alert displayed | **PASS** |
| **TC-D03-PAYM-04** | Reception Payment | Invoice NULL vs Payment ACTUAL blocks printing with `badge--warning` (D03-02/D03-06) | Status `'Paid - Inconsistent provenance'`, badge has `badge--warning`, print disabled | **PASS** |
| **TC-D03-PAYM-05** | Reception Payment | Invoice UNKNOWN vs Payment ACTUAL blocks printing (D03-02) | Status `'Paid - Inconsistent provenance'`, print button disabled | **PASS** |
| **TC-D03-PAYM-06** | Reception Payment | Invoice ACTUAL vs Payment SIMULATED blocks printing (D03-02) | Status `'Paid - Inconsistent provenance'`, print button disabled | **PASS** |
| **TC-D03-PAYM-07** | Reception Payment | Consistent ACTUAL across both sources enables printing with `badge--success` | Status `'Paid'`, badge has `badge--success`, print button enabled, `.payment-print-receipt` present | **PASS** |
| **TC-D03-PAYM-08** | Reception Payment | REFUNDED ACTUAL vs SIMULATED mismatch blocks printing with `badge--warning` (D03-05/D03-06) | Status `'Refunded - Inconsistent provenance'`, badge has `badge--warning`, print disabled | **PASS** |
| **TC-D03-PAYM-09** | Reception Payment | REFUNDED conflicting providers blocks printing with `badge--warning` (D03-05/D03-06) | Status `'Refunded - Inconsistent provenance'`, badge has `badge--warning`, print disabled | **PASS** |
| **TC-D03-PAYM-10** | Reception Payment | REFUNDED conflicting simulated txIds blocks printing with `badge--warning` (D03-05/D03-06) | Status `'Refunded - Inconsistent provenance'`, badge has `badge--warning`, print disabled | **PASS** |
| **TC-D03-PAYM-11** | Reception Payment | REFUNDED Legacy/UNKNOWN vs verified blocks printing with `badge--warning` (D03-05/D03-06) | Status `'Refunded - Inconsistent provenance'`, badge has `badge--warning`, print disabled | **PASS** |
| **TC-D03-PAYM-12** | Reception Payment | Consistent verified REFUNDED ACTUAL enables printing with `badge--neutral` (D03-05/D03-06) | Status `'Refunded'`, badge has `badge--neutral`, print enabled, `.payment-print-receipt` present | **PASS** |
| **TC-D03-PAYM-13** | Reception Payment | Consistent verified REFUNDED SIMULATED enables printing, retains banner (D03-05/D03-06) | Status `'Refunded'`, badge has `badge--neutral`, simulation banner visible, print enabled | **PASS** |
| **TC-D03-PAYM-14** | Reception Payment | Consistent verified SIMULATED payment renders `badge--success` (D03-06) | Status `'Paid - Simulated'`, badge has `badge--success`, print enabled, `.payment-print-receipt` present | **PASS** |
| **TC-D03-PAYM-15** | Reception Payment | REFUNDED ACTUAL with both providers null enables printing (D03-07) | Status `'Refunded'`, print button enabled, card has `.payment-receipt` and `.payment-print-receipt` | **PASS** |
| **TC-D03-PAYM-16** | Reception Receipt | Cross-screen consistency for REFUNDED ACTUAL with null provider (D03-07) | Status `'Refunded'`, print button enabled, card has `.payment-receipt` and `.payment-print-receipt` | **PASS** |
| **TC-D03-PAYM-17** | Reception Payment | REFUNDED ACTUAL with one provider null blocks printing and styles card (D03-07) | Status `'Refunded - Inconsistent provenance'`, print button disabled, card has `.payment-receipt` | **PASS** |
| **TC-D03-CSS-01** | Print Styles | CSS print media query regression check (D03-03) | `.payment-receipt .notice` and `.payment-print-receipt .notice` include `display: block !important`, `border: 2px solid currentColor !important`, `font-weight: 700 !important`; unverified receipts carry dashed border | **PASS** |
| **TC-D03-REG-01** | Reception Payments Suite | Existing Reception payments regression (`receptionPayments.test.tsx`) | 70/70 tests pass without regression | **PASS** |
| **TC-D03-REG-02** | Desk QR Suite | Existing Desk QR display regression (`receptionQrDisplay.test.tsx`) | 15/15 tests pass without regression | **PASS** |
| **TC-D03-FULL-01** | Full Frontend Suite | Full Vitest test suite (`npm test`) | 93/93 test files pass, 1753/1753 tests pass | **PASS** |
| **TC-D03-LINT-01** | Lint and Typecheck | ESLint (`npm run lint`) | 0 lint errors | **PASS** |
| **TC-D03-BLD-01** | Build Verification | TypeScript compilation and Vite production build (`npm run build`) | Build succeeds with zero errors | **PASS** |

---

## 3. Automated and Manual Test Evidence

### Unit Model Evidence
- File: `frontend/src/pages/reception/receptionProvenanceModel.test.ts`
- Tests: 38 tests, 38 passed (23ms)
- Verifies classification and cross-source reconciliation across normal, abnormal, null, unknown, inconsistent, and refunded scenarios, including cross-source refunded contradictions (D03-05) and nullable ACTUAL refund provider parity under V35/V36 (D03-07).

### Reception Component Integration Evidence
- File: `frontend/src/pages/reception/receptionLegacyProvenance.test.tsx`
- Tests: 26 tests, 26 passed (2051ms)
- Verifies DOM rendering on `ReceptionReceiptPage` and `ReceptionPaymentPage`, including hero description, warning notices, disabled print buttons, cross-source disagreement, refunded edge cases, badge tone class assertions (`badge--warning`, `badge--neutral`, `badge--success`) (D03-06), provider-null consistency and print card classes (`payment-receipt`, `payment-print-receipt`) (D03-07), and print CSS rules.

### Existing Regression Suites
- File: `frontend/src/pages/reception/receptionPayments.test.tsx`: 70 tests passed.
- File: `frontend/src/pages/reception/receptionQrDisplay.test.tsx`: 15 tests passed.
- Total Reception test coverage: 149 tests across 4 test suites passing.

### Full Frontend Suite
- Command: `npm test`
- Outcome: 93 test files passed, 1753 tests passed, 0 failures.
- Command: `npm run lint`
- Outcome: 0 errors.
- Command: `npm run build`
- Outcome: `tsc -b && vite build` succeeded.
