# QR-07 (i) - Post-FE-07 Integration Test Cases Record

**Workstream:** QR-07 (i) Security, Regression and Browser Acceptance  
**Milestone:** Post-FE-07 Merge Integration Verification  
**Branch:** `qa/qr-07-security-regression-browser-acceptance`  
**Evaluated HEAD:** `8db6e27b38dbbc0136ae4c763c6819d4de7b0375`  
**Base Commit:** `97abe0feebd0448cb87d9f89347ab8af2a320991` (origin/main)  
**Main CI Run ID:** `41335728035` (Conclusion: `SUCCESS`)  
**Browser Acceptance Run ID:** `41335727856` (Conclusion: `SUCCESS`)  

---

## 1. Status Overview

Following the merge of FE-07 PR #187 (commit `97abe0feebd0448cb87d9f89347ab8af2a320991`), `origin/main` was merged into PR #182 (commit `8db6e27b38dbbc0136ae4c763c6819d4de7b0375`).

Both required GitHub Actions workflows completed with `success`:
- Main CI Run `41335728035`: 14/14 jobs passed (Frontend checks and build: SUCCESS; CI gate: SUCCESS).
- QR-07 Browser Acceptance Run `41335727856`: 132/132 browser assertions passed.

This enables the closure of `QR07-111` and `QR07-114`:

| Metric | Pre-Integration (Round 11) | Post-Integration | Transition Details |
| :--- | :--- | :--- | :--- |
| **PASS** | 110 | **112** | `QR07-111` and `QR07-114` transitioned to PASS |
| **FAIL** | 6 | **4** | `QR07-013`, `QR07-056`, `QR07-065`, `QR07-115` remain |
| **NOT RUN** | 4 | **4** | `QR07-040`, `QR07-069`, `QR07-090`, `QR07-096` remain |
| **BLOCKED** | 0 | **0** | External CI blocker eliminated |
| **TOTAL** | 120 | **120** | Exact 120 canonical case identities preserved |

---

## 2. Transitioned Case Details

### `QR07-111`: Full frontend tests, lint, typecheck and build pass
- **Status:** **PASS**
- **Previous Status:** FAIL (due to Vitest failure in `managerSchedule.test.tsx:119` on Sunday reference date).
- **Verification Evidence:** Main CI Run `41335728035`, Job `Frontend checks and build`.
  - Lint: 0 errors.
  - Vitest: 91/91 test files passed, 1,688/1,688 tests passed.
  - Build: Production client assets compiled successfully.

### `QR07-114`: All required CodeQL/security/CI gates pass on exact HEAD
- **Status:** **PASS**
- **Previous Status:** FAIL (due to overall Main CI failure).
- **Verification Evidence:** Main CI Run `41335728035`, Job `CI gate`.
  - Conclusion: `success`.
  - All 14 workflow jobs succeeded.

---

## 3. Preserved Non-PASS Cases (Awaiting CODEOWNER Decisions)

### FAIL Cases (4)
1. `QR07-013`: Informational Bank Transfer Static QR Verification (visual check passes; decode decision needed).
2. `QR07-056`: Unknown / NULL Provenance Transaction Badge (default display verified; badge decision needed).
3. `QR07-065`: Simulated Payment Exclusion from Revenue CSV Exports (financial exclusion passes; test criteria clarification needed).
4. `QR07-115`: Static Security Analysis (CodeQL Triage) (pre-existing alerts #1 and #4 verified outside PR #182; risk acceptance needed).

### NOT RUN Cases (4)
5. `QR07-040`: Stateless JWT Invalidation upon Logout (client clearance passes; server revocation decision needed).
6. `QR07-069`: Legacy NULL Provenance Receipt Re-issuance (requirement deferral needed).
7. `QR07-090`: Server-Observed Correlation ID Propagation (client trace passes; server log ingestion decision needed).
8. `QR07-096`: Native 200% Browser Zoom Responsive Usability (viewport reflow passes; headless native zoom exception needed).
