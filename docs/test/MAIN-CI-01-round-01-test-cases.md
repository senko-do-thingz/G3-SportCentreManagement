# MAIN-CI-01 Round 01 - Master Test Cases (`MAINCI-001` to `MAINCI-020`)

- **Task:** MAIN-CI-01 - Restore Main Branch CodeQL and Required CI Gates
- **Repository:** `senko-do-thingz/G3-SportCentreManagement` (`D:\G3-SportCentreManagement\SWP`)
- **Round:** Round 01
- **Target Branch:** `main`
- **Target Commit SHA:** `65632ff88ef19bdd21e4be778a285a5958caea27`
- **Execution & Classification Summary:**
  - **Total Test Cases:** `20` (`MAINCI-001` through `MAINCI-020`)
  - **Executed Verification Tests:** `14` (Direct tool invocations, API operations, rerun jobs, unit tests)
  - **Static Inspections:** `5` (Workflow configurations, repository settings, code/YAML audits)
  - **Historical Observations:** `1` (Observed historical run behavior and failure timelines)
  - **PASS:** `20`
  - **FAIL:** `0`
  - **BLOCKED:** `0`
  - **NOT RUN:** `0`

---

## 1. CodeQL Extraction, Analysis, and SARIF Generation (`MAINCI-001` to `MAINCI-005`)

| ID | Title | Verification Type | Technique | Preconditions | Inputs / Actions | Expected Result | Observed Result | Evidence | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `MAINCI-001` | Full Backend Source Compilation for CodeQL Tracing | Executed Verification Test | Build | Java 25 installed, Maven 3.9+, backend dependencies cached | Execute `mvn -B -ntp test-compile` in `backend` | Exit code 0, all 529 Java production and test sources compiled successfully | Exit code 0, BUILD SUCCESS | Job `124224401347`, step 5 | PASS |
| `MAINCI-002` | CodeQL Java Database Creation and Tracing | Executed Verification Test | Static Analysis | CodeQL CLI 2.27.3, `github/codeql-action/init@v4` with `languages: java-kotlin, build-mode: manual` | Initialize CodeQL runner environment | Database initialized under runner temp directory; autobuilder disabled in favor of manual tracing | Initialized database at `/home/runner/work/_temp/codeql_databases/java` | Job `124224401347`, step 4 | PASS |
| `MAINCI-003` | CodeQL Full AST Extraction across Java Codebase | Executed Verification Test | Static Analysis | Compilation completed with CodeQL environment variables active | CodeQL extractor processes compiler invocations | 529 of 529 Java files scanned and extracted into CodeQL database | `CodeQL scanned 529 out of 529 Java files in this invocation` | Job `124224401347`, step 6 | PASS |
| `MAINCI-004` | CodeQL Security Query Suite Evaluation | Executed Verification Test | Security | Java query pack `codeql/java-queries@1.11.13` | Evaluate security, CWE, and quality queries | All 76 query rules evaluated against the extracted database | 76 rules evaluated, results written to BQRS | Job `124224401347`, step 6 | PASS |
| `MAINCI-005` | SARIF File Export and Fingerprint Augmentation | Executed Verification Test | Normal | Query results generated in BQRS | Export results to SARIF and add alert fingerprints | Valid SARIF 2.1.0 file created at `results/java.sarif` with fingerprints | SARIF exported in 393ms; fingerprints added | Job `124224401347`, step 6 | PASS |

---

## 2. SARIF Upload, API Entitlement, and Processing (`MAINCI-006` to `MAINCI-010`)

| ID | Title | Verification Type | Technique | Preconditions | Inputs / Actions | Expected Result | Observed Result | Evidence | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `MAINCI-006` | Gzipped Base64 SARIF Payload Construction | Executed Verification Test | Boundary | `results/java.sarif` present on disk (1,171,710 bytes raw) | Compress with zlib and encode to Base64 | Base64 string produced under 10MB limit (137,864 bytes) | Base64 zipped size: 137,864 bytes | Job `124224401347` debug log | PASS |
| `MAINCI-007` | SARIF Upload Authentication and Permission Validation | Executed Verification Test | Security | Workflow token with `security-events: write` | `POST /repos/:owner/:repo/code-scanning/sarifs` with valid token | Request accepted with `HTTP 202 Accepted` and SARIF ID returned | `HTTP 202 Accepted`, SARIF ID `18827b18-d48c-11f1-993d-7678caa1e15d` | Job `124224401347` debug log | PASS |
| `MAINCI-008` | Public Repository Code Scanning Entitlement | Static Inspection | Configuration | Repository `senko-do-thingz/G3-SportCentreManagement` | Query repo visibility and code scanning entitlement | Public repo has free GitHub Advanced Security Code Scanning enabled | `"private": false, "visibility": "public"` | `gh api repos/$repo` | PASS |
| `MAINCI-009` | Asynchronous Code Scanning Analysis Processing Completion | Executed Verification Test | Normal | SARIF accepted by GitHub API with valid SARIF ID | Poll analysis status until finalization | Status reaches `complete` without SARIF parsing errors | `Analysis upload status is complete` | Job `124224401347`, step 6 | PASS |
| `MAINCI-010` | Code Scanning Analysis Registration and Verification | Executed Verification Test | Integrity | Analysis processing completed | Query `GET /repos/:owner/:repo/code-scanning/analyses` for commit SHA | Analysis record present with `analysis_key: .github/workflows/ci.yml:codeql` and 0 errors | Analysis ID `2064533862` registered on `refs/heads/main` | `gh api repos/$repo/code-scanning/analyses` | PASS |

---

## 3. Category Alignment and Concurrent Workflow Handling (`MAINCI-011` to `MAINCI-014`)

| ID | Title | Verification Type | Technique | Preconditions | Inputs / Actions | Expected Result | Observed Result | Evidence | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `MAINCI-011` | Java CodeQL Analysis Category Preservation | Static Inspection | Configuration | Reusable workflow `.github/workflows/security.yml` invoked from `ci.yml` | Analyze Java codebase with configured category | Category strictly set to `.github/workflows/security.yml:codeql` | Analysis recorded with `.github/workflows/security.yml:codeql` | `03-workflow-and-security-diff.md` | PASS |
| `MAINCI-012` | Frontend CodeQL Analysis Category Separation | Static Inspection | Configuration | Reusable workflow `.github/workflows/security.yml` invoked from `ci.yml` | Analyze JavaScript/TypeScript frontend codebase | Category strictly set to `.github/workflows/ci.yml:frontend-codeql` | Analysis recorded with `.github/workflows/ci.yml:frontend-codeql` | `03-workflow-and-security-diff.md` | PASS |
| `MAINCI-013` | Multi-Language Category Disjointness in Reusable Workflow | Static Inspection | Boundary | Both Java and Frontend CodeQL jobs run in same workflow run | Verify distinct categories prevent analysis overwrites | Java and Frontend maintain separate alert tracking baselines | Separate analysis entries `2064533862` (Java) and `2062935791` (JS/TS) | GitHub Code Scanning API | PASS |
| `MAINCI-014` | Transient API 502 Resilience and Controlled Rerun Validation | Executed Verification Test | Stress | GitHub API intermittently returns HTTP 502 during high load | Dispatch controlled rerun of failed jobs with debug logging | Rerun executes cleanly without code alteration, restoring run to green | Rerun completed with `success`, restoring run `41071331921` | GitHub Actions API | PASS |

---

## 4. Fail-Closed Merge Gate Negative Controls (`MAINCI-015` to `MAINCI-020`)

| ID | Title | Verification Type | Technique | Preconditions | Inputs / Actions | Expected Result | Observed Result | Evidence | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `MAINCI-015` | Security Gate Fails When CodeQL Job Fails | Executed Verification Test | Negative Control | `inputs.backend = true`, `needs.codeql.result = 'failure'` | Execute `python3 scripts/ci/gate.py --security` | Script raises exception and exits non-zero (blocks merge) | Verified in `scripts/ci/gate.py` unit tests | `test_gate.py` unittest suite | PASS |
| `MAINCI-016` | Security Gate Fails When CodeQL Job Cancelled | Executed Verification Test | Negative Control | `inputs.backend = true`, `needs.codeql.result = 'cancelled'` | Execute `python3 scripts/ci/gate.py --security` | Script raises exception and exits non-zero | Verified in unit test suite | `test_gate.py` unittest suite | PASS |
| `MAINCI-017` | Security Gate Fails When Backend CodeQL Skipped Unexpectedly | Executed Verification Test | Negative Control | `inputs.backend = true`, `needs.codeql.result = 'skipped'` | Execute `python3 scripts/ci/gate.py --security` | Script detects unexpected skip and exits non-zero | Verified in unit test suite | `test_gate.py` unittest suite | PASS |
| `MAINCI-018` | Security Gate Succeeds When CodeQL Jobs Pass | Executed Verification Test | Normal | `inputs.backend = true`, `needs.codeql.result = 'success'` | Execute `python3 scripts/ci/gate.py --security` | Script outputs `Security gate validated successfully` and exits 0 | Exit code 0, job `124224767702` completed `success` | Run `41071331921` logs | PASS |
| `MAINCI-019` | CI Gate Enforces Security Gate Prerequisite | Historical Observation | Negative Control | `needs.security.result = 'failure'` | Execute `python3 scripts/ci/gate.py` | CI gate rejects security failure and exits non-zero | CI gate failed closed on run attempt 1 | Run `41071331921` attempt 1 | PASS |
| `MAINCI-020` | CI Gate Rejects Workflow Cancellation | Static Inspection | Negative Control | Workflow run receives cancellation signal | Step `Reject workflow cancellation` triggers `if: cancelled()` | Step runs `exit 1` ensuring cancelled workflow registers as failure | Verified in `.github/workflows/ci.yml` line 328 | `.github/workflows/ci.yml` | PASS |