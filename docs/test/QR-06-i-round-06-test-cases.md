# QR-06 (i) Round 06 - Master Verification Test Cases

**Document Identifier:** `TC-QR-06-i-ROUND-06`  
**PR:** https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/181  
**Branch:** `feat/qr-06-reception-simulated-qr-display`  
**Base Commit:** `65632ff88ef19bdd21e4be778a285a5958caea27`  
**Round:** Round 06 - Final Strict Evidence Verifier Gate  

---

## 1. Summary of Test Suites and Execution Modes

| Suite Category | Count | Automated Harness | Scope | Status |
|---|---|---|---|---|
| **Frontend Model & Domain Units** | 20 | Vitest (node environment) | State transitions, financial status mappings, timer & ticks, bank override, provenance safety | 20 PASS |
| **Frontend Component & Render Units** | 15 | Vitest + jsdom + Testing Library | Bounding boxes, layout classes, role gates, banners, notices, retry actions | 15 PASS |
| **Backend Unit & Controller Security** | 34 | JUnit 5 + Spring MockMvc | RBAC role matrix (Receptionist/Manager 200, Member/Coach/Cashier 403, Anon 401), DTO mappings | 34 PASS |
| **SQL Server 2022 Integration Tests** | 14 | Testcontainers (Linux CI runner) | Zero database writes across 12 tables, table SHA-256 digests, manager desk projection access | 14 PASS |
| **CDP Desktop & Responsive Evidence** | 18 | Microsoft Edge (CDP) + Mock UI Fixtures | 18 screenshots (1280, 1440, 1920, 768, 390), 17 unique primary hashes, authentic Tab focus, 200% zoom | 18 PASS |
| **SQL Verifier Positive & Negative Regressions** | 10 | Python `unittest` | 1 genuine positive + 9 strict negative controls (snapshotAlgorithm, description, keys, digests, tables, XML) | 10 PASS |
| **Static Code, Schema & Dependency Checks** | 8 | Git, Flyway, Bash, Python lint | Flyway V35/V36 immutability, zero dependency drift, English ASCII convention, CodeQL analysis | 8 PASS |
| **Physical Phone Camera Handset Scan** | 1 | Manual physical camera on LAN | Physical mobile phone scanning desk projection QR | 1 NOT RUN |
| **Total Test Cases** | **120** | - | Comprehensive QR-06 verification | **119 PASS / 1 NOT RUN** |

---

## 2. Test Execution Breakdown

### 2.1 Verifier Regression Tests (`QR06-107` to `QR06-116`)

| ID | Title | Test Type | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
| `QR06-107` | Verifier genuine positive control | PYTHON TEST | Genuine Round 05 JUnit XML and JSON | Run verifier | 100% whole-document recursive equality; zero semantic discrepancies; exit code 0 | Verified 100% deep equality and exit code 0 | `test_verify_sql_evidence.py`, `positive-and-negative-verifier.log` | PASS |
| `QR06-108` | Verifier rejects changed snapshotAlgorithm | PYTHON TEST | Modified `snapshotAlgorithm` | Run verifier | Full-path discrepancy `root['snapshotAlgorithm']`; exit code 1 | Verified diff reported and exit code 1 | `test_verify_sql_evidence.py`, `positive-and-negative-verifier.log` | PASS |
| `QR06-109` | Verifier rejects changed request description | PYTHON TEST | Modified `executedRequests[0].description` | Run verifier | Full-path discrepancy `root['executedRequests'][0]['description']`; exit code 1 | Verified diff reported and exit code 1 | `test_verify_sql_evidence.py`, `positive-and-negative-verifier.log` | PASS |
| `QR06-110` | Verifier rejects unexpected root or nested key | PYTHON TEST | Injected `unexpectedRootField` and nested key | Run verifier | Full-path discrepancy `Unexpected key at root[...]`; exit code 1 | Verified unexpected key reported and exit code 1 | `test_verify_sql_evidence.py`, `positive-and-negative-verifier.log` | PASS |
| `QR06-111` | Verifier rejects changed table digest | PYTHON TEST | Modified table SHA-256 digest | Run verifier | Full-path discrepancy `root['tables'][0]['sha256DigestBefore']`; exit code 1 | Verified digest mismatch reported and exit code 1 | `test_verify_sql_evidence.py`, `positive-and-negative-verifier.log` | PASS |
| `QR06-112` | Verifier rejects removed or duplicate table | PYTHON TEST | Removed table (11 tables) or duplicate name | Run verifier | Semantic safety failure; table count / uniqueness violation; exit code 1 | Verified count/duplicate failure and exit code 1 | `test_verify_sql_evidence.py`, `positive-and-negative-verifier.log` | PASS |
| `QR06-113` | Verifier rejects malformed JSON in XML | PYTHON TEST | Corrupted JSON payload in XML | Run verifier | ValueError `Malformed JSON`; exit code 1 | Verified parse failure and exit code 1 | `test_verify_sql_evidence.py`, `positive-and-negative-verifier.log` | PASS |
| `QR06-114` | Verifier rejects missing testcase in XML | PYTHON TEST | XML without target testcase name | Run verifier | ValueError `Test case '...' not found in JUnit XML`; exit code 1 | Verified missing testcase rejection and exit code 1 | `test_verify_sql_evidence.py`, `positive-and-negative-verifier.log` | PASS |
| `QR06-115` | Verifier rejects testcase with failure node | PYTHON TEST | Target testcase containing `<failure>` | Run verifier | ValueError `contains a <failure> node`; exit code 1 | Verified failed testcase rejection and exit code 1 | `test_verify_sql_evidence.py`, `positive-and-negative-verifier.log` | PASS |
| `QR06-116` | Verifier rejects marker only in suite-level | PYTHON TEST | Marker in suite `<system-out>`, not testcase | Run verifier | ValueError `No <system-out> found inside testcase`; exit code 1 | Verified suite-root rejection and exit code 1 | `test_verify_sql_evidence.py`, `positive-and-negative-verifier.log` | PASS |

---

## 3. Core QR-06 Cases (`QR06-001` to `QR06-106`)

All 106 test cases from Round 05 remain preserved and passing:
- `QR06-001` - `QR06-035`: Frontend Vitest Model & Component tests (35/35 PASS)
- `QR06-036` - `QR06-056`: Backend Controller Unit tests (21/21 PASS)
- `QR06-057` - `QR06-070`: Backend Integration zero-write tests in Testcontainers (14/14 PASS)
- `QR06-071` - `QR06-074`: Visual geometry, axe audit, unique screenshots (4/4 PASS)
- `QR06-075`: Physical mobile camera scanning (NOT RUN - external phone constraint)
- `QR06-076` - `QR06-106`: Historical round coverage, financial provenance, terminal states, bank locking, layout repair, authentic Tab focus, composited contrast, and pinned dependencies (30/30 PASS).

---

## 4. Verification Totals (Round 06)

- **Total Cases:** 116 (Core 106 + 10 Verifier Regressions)
- **Passed:** 115
- **Failed:** 0
- **Blocked:** 0
- **Not Run:** 1 (`QR06-075`)
- **Success Rate:** 99.14% (100% of executable tests pass)
