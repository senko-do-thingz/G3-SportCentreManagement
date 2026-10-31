# QR-07 (i) Round 03 - Master Test Cases (`QR07-001` to `QR07-120`)

- **Task:** QR-07 (i) - Security, Regression and Browser Acceptance
- **Round:** Round 03 - Architecture A CI Multi-Tier Genuine Browser E2E, WCAG AA Contrast Remediation, CDP Shift+Tab Reverse Traversal, Native 200% Zoom Verification, and Fail-Closed Matrix Reconciliation
- **Branch:** `qa/qr-07-security-regression-browser-acceptance`
- **Base `origin/main` Commit:** `6689165da6c09e2426f3677803a12d562342a32b` (PR #181 merge commit)
- **Execution Summary:**
  - **Total Test Cases:** `120` (`QR07-001` through `QR07-120`)
  - **PASS:** `120`
  - **FAIL:** `0`
  - **BLOCKED:** `0`
  - **NOT RUN:** `0`
  - **Verification Types Applied:**
    - `UNIT TEST`: 56 automated unit test cases (Vitest and JUnit 5 Surefire)
    - `SQL CI TEST`: 24 real Microsoft SQL Server 2022 Testcontainers and service container integration assertions (`DemoQrDatabaseIntegrationTest`, `FlywayMigrationTest`, `PaymentVerificationIntegrationTest`, `FinanceReportIntegrationTest`)
    - `CI GENUINE MULTI-TIER E2E`: 8 multi-tier browser -> Spring Boot -> SQL Server 2022 integration acceptance assertions (`.github/workflows/qr07-browser.yml`, `frontend/scripts/qr07-ci-verification.py`)
    - `BROWSER CDP REGRESSION`: 30 authentic Edge browser captures via Chrome DevTools Protocol across two isolated contexts, viewports 1280, 1440, 1920, 390, 768, 720 reflow, and 200% zoom
    - `ACCESSIBILITY AUDIT`: 30 views evaluated by axe-core (WCAG 2.0/2.1 A/AA) recording 0 serious violations; CDP Tab and Shift+Tab keyboard focus navigation
    - `SECURITY / STATIC CHECK`: 11 threat model, SQL check constraint, and CI workflow contract checks

---

## 1. Prerequisites and Configuration (`QR07-001` to `QR07-010`)

| ID | Title | Test Type | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR07-001` | QR-06 PR #181 merged into origin/main | Regression | Remote GitHub repository | `gh pr view 181 --json state,mergedAt,mergeCommit` | PR #181 state is MERGED; merge commit in `origin/main` | State MERGED at 2026-10-31T09:15:35Z, commit `6689165da6c09e2426f3677803a12d562342a32b` | `git log -n 1 6689165` | PASS | Sec 1 | - |
| `QR07-002` | QR-01 to QR-05 routes, services and migrations exist | Regression | Working tree at `origin/main` | Inspect backend controllers, repositories, frontend pages | All QR-01 to QR-05 artifacts present and operational | Controllers, services, and migrations V34/V35/V36 verified | Source tree inventory | PASS | Sec 2 | - |
| `QR07-003` | Applied V35 checksum unchanged and V36 applied | Normal | Flyway catalog migrations | `FlywayMigrationTest`, checksum comparison | V35 checksum matches baseline, V36 applied cleanly | Checksums match; migration test passed | `TEST-com.sportify.FlywayMigrationTest.xml` | PASS | Sec 3, Sec 5 | - |
| `QR07-004` | Production profile rejects all new simulated confirmations | Security | Active profile `prod` | `POST /api/v1/payments/simulated-qr/sessions/confirm` | 403 Forbidden / 404 Not Found; simulation disabled | Rejected: Simulator disabled in production | `DemoQrConfigurationTest` | PASS | Sec 3 | - |
| `QR07-005` | qr-simulator profile alone without enabled flag rejects settlement | Security | Profile `qr-simulator`, flag `app.payment.simulator.enabled=false` | Attempt settlement | Settlement rejected fail-closed | Rejected with `SIMULATOR_UNAVAILABLE` | `DemoQrConfigurationTest` | PASS | Sec 3 | - |
| `QR07-006` | enabled flag alone without qr-simulator profile rejects settlement | Security | Default profile, flag `app.payment.simulator.enabled=true` | Attempt settlement | Settlement rejected fail-closed | Rejected: profile gate missing | `DemoQrConfigurationTest` | PASS | Sec 3 | - |
| `QR07-007` | Both permitted conditions enable synthetic settlement | Normal | Profile `qr-simulator` AND flag `app.payment.simulator.enabled=true` | `GET /api/v1/payments/simulated-qr/capability` | `200 OK` with `enabled=true, settlementEnabled=true` | Returns `200 OK`, capability enabled | `DemoQrControllerTest` | PASS | Sec 3 | - |
| `QR07-008` | Committed simulated history readable when kill switch disabled | Boundary | Flag `app.payment.simulator.enabled=false`; historical DB records | `GET /api/v1/payments/102` | Historical payment and receipt read successfully | Historical records readable; writes blocked | `ReceiptIntegrationTest` | PASS | Sec 3 | - |
| `QR07-009` | Historical V35 to V36 upgrade preserves legitimate data | Regression | SQL Server database | Run V36 migration on V35 schema | V35 records preserved; invalid inserts rejected | Clean upgrade, zero data loss | `TEST-com.sportify.FlywayMigrationTest.xml` | PASS | Sec 5 | - |
| `QR07-010` | Working branch diff has no PR #180 Flow 4 imports | Security | Branch `qa/qr-07-security-regression-browser-acceptance` | `git diff origin/main` | Zero commits or files from branch `feat/phat-flow-4-frontend` | Verified 0 commits imported from PR #180 | `git log --oneline` | PASS | Sec 1, Sec 7 | - |

---

## 2. QR Rendering and Contract (`QR07-011` to `QR07-020`)

| ID | Title | Test Type | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR07-011` | Member payment amount and currency from authoritative values | Normal | Payment 101 in database | `GET /api/v1/payments/101` | Returns amount 810000, currency VND | Authoritative 810,000 VND returned and rendered | `PaymentSummaryPage.tsx`, `memberPayments.test.tsx` | PASS | QR-01 | - |
| `QR07-012` | Member QR confirmation URL origin and path correctly encoded | Normal | Active session created | Inspect `confirmationUrl` | Valid HTTPS URL ending in `/demo/qr/confirm#token=...` | Formatted strictly as permitted origin/path/fragment | `demoQr.test.ts`, `demoQrApi.ts` | PASS | QR-02 | - |
| `QR07-013` | Informational bank QR contains no payable transfer payload | Security | Generate QR payload | Inspect decoded text | Format `SPORTIFY_DEMO_QR&#124;...&#124;MODE=NON_PAYABLE`; no EMVCo | Decoded format verified non-payable | `demoQr.test.ts` | PASS | QR-02, QR-03 | - |
| `QR07-014` | Receptionist projection payload contains no secret token | Security | Active session in SQL | `GET /api/v1/payments/simulated-qr/desk-projection/101` | Response JSON excludes token, tokenHash, confirmationUrl | Verified zero token or secret link in response | `ReceptionQrDisplayControllerTest` | PASS | QR-06 | - |
| `QR07-015` | Four configured display-bank options map to correct codes | Normal | Render bank dropdown | Inspect options | Vietcombank, BIDV, Techcombank, MB Bank present | All 4 demo bank options correctly mapped | `demoBanks.ts`, `DemoQrCard.test.tsx` | PASS | QR-03 | - |
| `QR07-016` | Active session bank code displayed instead of local selector | Normal | Active session with BIDV_DEMO | Render Receptionist and Member QR | Displays BIDV; bank selector locked to active session bank | Display locked to session bank code | `receptionQrDisplayModel.test.ts` | PASS | QR-03, QR-06 | - |
| `QR07-017` | QR payload decoded content matches app display meaning | Normal | Decoded QR string | Verify payment ID, bank code, mode | Payload matches active payment and bank | Matched `PAYMENT_ID=101&#124;BANK_CODE=BIDV_DEMO` | `demoQr.test.ts` | PASS | QR-02 | - |
| `QR07-018` | Invalid confirmation URL not shown as valid session | Abnormal | Malformed hash fragment | Load `/demo/qr/confirm#token=invalid` | Displays "The token is missing or malformed" alert | Rejection alert rendered; no session controls | `demoConfirmation.test.tsx` | PASS | QR-04 | - |
| `QR07-019` | Expired/canceled session cannot be presented as active | Boundary | Session with `expiresAt < now()` | Load confirmation link or projection | Displays EXPIRED state; settlement disabled | State evaluates to EXPIRED; actions disabled | `DemoQrControllerTest`, `ReceptionQrDisplayControllerTest` | PASS | QR-04 | - |
| `QR07-020` | No UI receipt claims external bank funds transferred | Security | Payment settled as SIMULATED | Render receipt | Notice confirms simulation; no bank movement claim | Explicit notice: "No real money is transferred" | `ReceiptPage.tsx`, `receptionReceipt.test.tsx` | PASS | QR-05 | - |

---

## 3. Session Lifecycle and Replay (`QR07-021` to `QR07-030`)

| ID | Title | Test Type | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR07-021` | Eligible owner can create bounded confirmation session | Normal | Member owns pending payment | `POST /api/v1/payments/simulated-qr/sessions` | Session created with 10-minute expiry | Created 200 OK; session TTL = 600s | `DemoQrControllerTest`, `DemoQrServiceTest` | PASS | QR-04 | - |
| `QR07-022` | Anonymous caller cannot create session | Security | Unauthenticated caller | `POST /api/v1/payments/simulated-qr/sessions` | `401 Unauthorized` | 401 Unauthorized returned | `DemoQrControllerTest` | PASS | QR-04 | - |
| `QR07-023` | Cross-member caller cannot create session for other Payment | Security | Member B authenticated; Payment owned by Member A | `POST /api/v1/payments/simulated-qr/sessions` for Payment A | `403 Forbidden` | 403 Forbidden returned | `DemoQrControllerTest` | PASS | QR-04 | - |
| `QR07-024` | Unsupported Payment status cannot create active session | Abnormal | Payment status `FAILED` or `SUCCESS` | `POST /api/v1/payments/simulated-qr/sessions` | `400 Bad Request` | 400 Bad Request returned | `DemoQrControllerTest` | PASS | QR-04 | - |
| `QR07-025` | Expired token fails preview and confirm | Boundary | Token past expiry | `POST /preview` and `POST /confirm` | `400 Bad Request` with `SESSION_EXPIRED` | Expired session rejected fail-closed | `DemoQrControllerTest` | PASS | QR-04 | - |
| `QR07-026` | Canceled session cannot be confirmed | Abnormal | Session canceled by user | `POST /confirm` with canceled token | `400 Bad Request` with `SESSION_CANCELED` | Canceled session rejected fail-closed | `DemoQrControllerTest` | PASS | QR-04 | - |
| `QR07-027` | Duplicate same-session preview remains read-only | Normal | Active session exists | Multiple `POST /preview` calls | Returns session details; zero database writes | Read-only idempotent preview verified | `DemoQrControllerTest` | PASS | QR-04 | - |
| `QR07-028` | Replayed confirmation does not duplicate settlement | Concurrency | Session already SETTLED | Replay `POST /confirm` with same token | Returns existing settled state; zero new transactions | Settled once; no duplicate transactions | `DemoQrDatabaseIntegrationTest` | PASS | QR-04, QR-05 | - |
| `QR07-029` | Backend restart preserves committed session and settlement | Regression | Persistent SQL Server store | Query session and payment after server reboot | All records remain committed and durable | State persisted across restarts | `TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml` | PASS | QR-04 | - |
| `QR07-030` | Stale browser polling response cannot replace newer Payment | Boundary | Client switches payment while poll in flight | Race polling response with selection | Stale response discarded by React key/state | Verified stable state keyed by payment ID | `usePaymentPolling.ts`, `memberPayments.test.tsx` | PASS | QR-04 | - |

---

## 4. Authorization and Privacy (`QR07-031` to `QR07-040`)

| ID | Title | Test Type | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR07-031` | Anonymous read/write QR endpoints reject access | Security | Unauthenticated caller | Calls to session, preview, confirm, projection | `401 Unauthorized` | 401 Unauthorized across all endpoints | `DemoQrControllerTest` | PASS | Sec 3 | - |
| `QR07-032` | Coach cannot confirm Member QR settlement | Security | Authenticated COACH | `POST /api/v1/payments/simulated-qr/sessions/confirm` | `403 Forbidden` | 403 Forbidden returned | `DemoQrControllerTest` | PASS | Sec 3 | - |
| `QR07-033` | Receptionist cannot confirm Member QR settlement | Security | Authenticated RECEPTIONIST | `POST /api/v1/payments/simulated-qr/sessions/confirm` | `403 Forbidden` | 403 Forbidden returned | `DemoQrControllerTest` | PASS | Sec 3 | - |
| `QR07-034` | Manager cannot impersonate Member to confirm | Security | Authenticated MANAGER | `POST /api/v1/payments/simulated-qr/sessions/confirm` | `403 Forbidden` | 403 Forbidden returned | `DemoQrControllerTest` | PASS | Sec 3 | - |
| `QR07-035` | Member B cannot read Member A QR session | Security | Authenticated Member B | `GET /api/v1/payments/simulated-qr/sessions/{id}` (Member A) | `403 Forbidden` | 403 Forbidden returned | `DemoQrControllerTest` | PASS | Sec 3 | - |
| `QR07-036` | Member B cannot confirm Member A QR session | Security | Authenticated Member B | `POST /confirm` with Member A token | `403 Forbidden` | 403 Forbidden returned | `DemoQrControllerTest` | PASS | Sec 3 | - |
| `QR07-037` | Receptionist can access permitted read-only projection only | Normal | Authenticated RECEPTIONIST | `GET /api/v1/payments/simulated-qr/desk-projection/{id}` | `200 OK` projection DTO returned | 200 OK with sanitized projection DTO | `ReceptionQrDisplayControllerTest` | PASS | QR-06 | - |
| `QR07-038` | Receptionist projection rejects unauthorized payment IDs | Boundary | Payment ID 0, negative, or non-existent | `GET /desk-projection/{id}` | `404 Not Found` | 404 Not Found returned | `ReceptionQrDisplayControllerTest` | PASS | QR-06 | - |
| `QR07-039` | Secret token not recorded in application logs or SQL | Security | Execute session flow | Inspect backend logs and SQL dumps | Zero raw tokens in logs or dumps | Sanitized hashes and redacted logs verified | `positive-and-negative-verifier.log` | PASS | Sec 3, QR-02 | - |
| `QR07-040` | Authentication invalidation prevents settlement after logout | Security | Token revoked / user logged out | `POST /confirm` with expired auth header | `401 Unauthorized` | 401 Unauthorized returned | `AuthenticationServiceIntegrationTest` | PASS | Sec 3 | - |

---

## 5. Atomic Settlement and Concurrency (`QR07-041` to `QR07-050`)

| ID | Title | Test Type | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR07-041` | First valid confirmation commits exactly one settlement | Normal | Pending payment 101, active session | `POST /confirm` | Payment transitions to SUCCESS, settlement committed | Exactly 1 settlement transaction committed | `TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-042` | Simulated payment status commits atomically with transaction | Normal | Execution of confirm | Inspect payment and simulated_payment_transaction | Both committed in same database transaction | Atomic commit verified | `TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-043` | Invoice paid transition commits exactly once when applicable | Normal | Payment linked to invoice | Confirm settlement | Invoice transitions to PAID; invoice_line updated | Invoice updated once | `TEST-com.sportify.payment.PaymentVerificationIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-044` | Entitlement activation commits exactly once | Normal | Payment for membership package | Confirm settlement | Member card / package registration activated once | Entitlement activated once | `TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-045` | Audit confirmation event recorded with actor and timestamp | Normal | Settlement confirmed | Query audit trail | Audit entry records actor ID, event type, timestamp | Audit entry recorded accurately | `TEST-com.sportify.core.audit.AuditTransactionIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-046` | Same Payment confirmed twice concurrently settles once | Concurrency | 2 concurrent threads confirm same token | Two simultaneous `POST /confirm` calls | Exactly 1 call succeeds; 2nd receives existing settlement | Concurrency lock guarantees single settlement | `TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-047` | Two backend instances compete for same Payment: one wins | Concurrency | Distributed cluster simulation | Competing confirm calls | Database row lock ensures 1 winner | 1 winner, 0 double-settlements | `DemoQrDatabaseIntegrationTest` | PASS | QR-05 | - |
| `QR07-048` | Cancel/confirm race produces one valid terminal result | Concurrency | Cancel and confirm calls arrive simultaneously | Race cancel vs confirm | Terminal state is either SETTLED or CANCELED, never corrupt | Terminal consistency verified | `DemoQrServiceTest` | PASS | QR-04, QR-05 | - |
| `QR07-049` | Expiry/confirm race enforces server cutoff | Concurrency | Token at exact expiration boundary | Confirm at cutoff | Server cutoff strictly enforced fail-closed | Server clock authoritative; expired rejected | `DemoQrServiceTest` | PASS | QR-04 | - |
| `QR07-050` | Injected SQL failure rolls back all required transitions | Abnormal | Injected downstream failure during settlement transaction | Confirm call encounters DB error | Complete rollback; payment remains PENDING; zero partial mutations | Complete rollback verified in genuine SQL Server test; payment remains PENDING | `DemoQrDatabaseIntegrationTest.injectedDownstreamFailureRollsBackSimulatedTransactionAndPayment`, `invoiceOrAuditCreationFailureRollsBackCompleteSettlementInSql` | PASS | QR-05 | - |

---

## 6. Provenance and SQL Integrity (`QR07-051` to `QR07-060`)

| ID | Title | Test Type | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR07-051` | SIMULATED settlement requires simulator provider and non-null TXN | Security | Table `dbo.payment` V36 | `settlement_type = 'SIMULATED'` | Must have `settlement_provider = 'SPORTIFY_SIMULATOR'` and non-null `simulated_transaction_id` | Enforced by V36 SQL CHECK constraint | `TEST-com.sportify.payment.PaymentDdlValidateIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-052` | SIMULATED with null provider rejected by SQL CHECK | Abnormal | Database connection | `INSERT INTO payment(..., settlement_type='SIMULATED', settlement_provider=NULL)` | Check constraint violation error | Rejected: `CK_payment_settlement_provenance` | `TEST-com.sportify.payment.PaymentDdlValidateIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-053` | SIMULATED with unexpected provider rejected | Abnormal | Database connection | `INSERT INTO payment(..., settlement_type='SIMULATED', settlement_provider='VNPay')` | Check constraint violation error | Rejected: invalid provider for simulation | `TEST-com.sportify.payment.PaymentDdlValidateIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-054` | ACTUAL with simulator provider rejected | Abnormal | Database connection | `INSERT INTO payment(..., settlement_type='ACTUAL', settlement_provider='SPORTIFY_SIMULATOR')` | Check constraint violation error | Rejected: ACTUAL cannot borrow simulator provider | `TEST-com.sportify.payment.PaymentDdlValidateIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-055` | ACTUAL with simulated transaction ID rejected | Abnormal | Database connection | `INSERT INTO payment(..., settlement_type='ACTUAL', simulated_transaction_id=9001)` | Check constraint violation error | Rejected: ACTUAL cannot have simulated_transaction_id | `TEST-com.sportify.payment.PaymentDdlValidateIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-056` | Legacy NULL/unknown provenance labeled unverified | Normal | Historical record with `settlement_type IS NULL` | Render payment receipt/status | Labeled "Paid (unverified)", never "Paid - Simulated" or "ACTUAL" | Correct unverified labeling rendered | `paymentsModel.test.ts`, `ReceiptPage.tsx` | PASS | QR-05 | - |
| `QR07-057` | Valid ACTUAL records continue to work | Regression | Cash desk payment | Process cash payment | Commits with `settlement_type='ACTUAL'` | ACTUAL payment commits and displays cleanly | `TEST-com.sportify.payment.CashEventIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-058` | Valid SIMULATED records continue to work | Regression | Synthetic QR payment | Settle via simulator | Commits with `settlement_type='SIMULATED'` | SIMULATED payment commits and displays cleanly | `TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-059` | V36 rejects malformed inserts under SQL three-valued logic | Boundary | Inserts with various NULL combinations | Test all NULL-safety permutations | Rejects invalid combinations; permits valid | All constraint permutations verified | `TEST-com.sportify.payment.PaymentDdlValidateIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-060` | Fresh migration and upgrade migration pass real SQL Server | Regression | SQL Server 2022 instance | Run Flyway baseline and upgrade | Both succeed with 0 validation errors | Flyway migration test passed | `TEST-com.sportify.FlywayMigrationTest.xml` | PASS | QR-05 | - |

---

## 7. Financial Separation and Refunds (`QR07-061` to `QR07-070`)

| ID | Title | Test Type | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR07-061` | Simulated settlement increments only simulated total once | Normal | Manager financial summary | Settle payment for 810,000 VND | Simulated total += 810,000; Actual total delta = 0 | Simulated total incremented; actual unchanged | `TEST-com.sportify.payment.FinanceReportIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-062` | Actual bank reconciliation total unchanged after simulation | Normal | Bank reconciliation report | Settle simulated payment | Real reconciliation total unchanged | Delta = 0 VND on real bank reconciliation | `TEST-com.sportify.payment.FinanceReportIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-063` | Actual cash_event table unchanged after simulated settlement | Normal | Table `dbo.cash_event` | Settle simulated payment | Zero rows inserted into `dbo.cash_event` | `rowCountDelta = 0`, digest identical before and after | `sql-evidence/qr07-round03-sql-evidence.json` | PASS | QR-05 | - |
| `QR07-064` | Actual received revenue excludes simulated settlement | Normal | Revenue report query | Query verified actual revenue | Excludes `SIMULATED` payments | Revenue report strictly filters `settlement_type = 'ACTUAL'` | `TEST-com.sportify.payment.FinanceReportIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-065` | Finance export preserves explicit SIMULATED provenance | Normal | CSV/JSON finance export | Export transactions | Provenance column clearly marks `SIMULATED` | Explicit `SIMULATED` label in exported records | `TEST-com.sportify.payment.FinanceReportIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-066` | Duplicate confirmation does not increase finance totals | Concurrency | Payment already settled | Second confirmation attempt | No financial totals incremented | Total delta = 0 | `TEST-com.sportify.payment.FinanceReportIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-067` | Real cash payment path still reports ACTUAL correctly | Regression | Front desk cash transaction | Record cash sale | Cash event logged, revenue incremented, provenance ACTUAL | Cash event recorded; revenue incremented | `TEST-com.sportify.payment.CashEventIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-068` | Simulated settlement cannot trigger real-money refund payout | Security | Simulated payment | Request cash refund | Blocked: simulated payment cannot trigger cash disbursement | Blocked with validation error | `RefundPaymentLinkIntegrationTest` | PASS | QR-05 | - |
| `QR07-069` | Legacy unverified transactions excluded from verified receipts | Normal | Historical payment with NULL provenance | Inspect financial reconciliation | Excluded from verified actual receipts | Excluded from verified ledger | `TEST-com.sportify.payment.FinanceMigrationCompatibilityIntegrationTest.xml` | PASS | QR-05 | - |
| `QR07-070` | Wrong payment amount/duplicate transaction blocked safely | Abnormal | Amount mismatch in verification | Attempt confirmation with mismatched amount | Blocked and audited safely | Rejected with amount mismatch | `TEST-com.sportify.payment.PaymentVerificationIntegrationTest.xml` | PASS | QR-05 | - |

---

## 8. Receptionist Read-Only QR-06 (`QR07-071` to `QR07-080`)

| ID | Title | Test Type | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR07-071` | Authorized staff projection reads expected session/payment | Normal | Authenticated RECEPTIONIST | `GET /api/v1/payments/simulated-qr/desk-projection/101` | `200 OK` with payment and session metadata | Returned 200 OK with complete projection DTO | `ReceptionQrDisplayControllerTest` | PASS | QR-06 | - |
| `QR07-072` | Unauthorized staff projection returns 401/403 | Security | Unauthenticated caller or MEMBER role | `GET /api/v1/payments/simulated-qr/desk-projection/101` | 401 Unauthorized / 403 Forbidden | 401 / 403 returned appropriately | `ReceptionQrDisplayControllerTest` | PASS | QR-06 | - |
| `QR07-073` | Projection contains no raw token or confirmation secret | Security | Active session in SQL | `GET /desk-projection/101` | Response JSON excludes token, tokenHash, confirmationUrl | Verified 0 secret tokens in DTO | `ReceptionQrDisplayControllerTest` | PASS | QR-06 | - |
| `QR07-074` | Projection presents PENDING as informational without real claims | Normal | Payment 101 with status PENDING | Render Receptionist display | Warning notice: "Simulated QR Payment - No real money is transferred." | Warning notice rendered | `reception-1440-light-pending.png` | PASS | QR-06 | - |
| `QR07-075` | Projection presents verified SIMULATED success correctly | Normal | Payment transitions to SUCCESS (SIMULATED) | Render Receptionist display | Badge "Paid - Simulated", green banner, TXN-9001 | Status badge and TXN code displayed | `reception-1440-light-settled-simulated.png` | PASS | QR-06 | - |
| `QR07-076` | Projection presents verified ACTUAL success without simulated banner | Normal | Payment 103 with status SUCCESS (ACTUAL) | Render Receptionist display | Badge "Paid", no simulated banner | Badge "Paid" rendered; simulated warning omitted | `reception-1440-light-settled-actual.png` | PASS | QR-06 | - |
| `QR07-077` | Projection presents legacy UNKNOWN as unverified | Normal | Payment 104 with NULL provenance | Render Receptionist display | Badge "Paid (unverified)", notice displayed | Unverified status rendered | `reception-1440-light-legacy-unverified.png` | PASS | QR-06 | - |
| `QR07-078` | Terminal Payment does not display active QR or copy action | Boundary | Payment 105 with status FAILED | Render Receptionist display | Active QR hidden, notice indicates inactive | QR hidden, inactive notice rendered | `reception-1440-light-failed-terminal.png` | PASS | QR-06 | - |
| `QR07-079` | Repeated live SQL-backed GET/poll requests mutate zero rows | Normal | Monitored 12 database tables | 5 consecutive GET /desk-projection calls | All 12 tables have `rowCountDelta = 0` and identical digests | Verified 100% deep equality; zero writes across monitored tables | `sql-evidence/qr07-round03-sql-evidence.json` | PASS | QR-06 | - |
| `QR07-080` | No forced refresh can settle, refund, cancel or change entitlements | Security | Client triggers page reloads and refreshes | Repeated reloads on desk projection | Zero mutation; state unchanged | Read-only idempotent projection | `TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml` | PASS | QR-06 | - |

---

## 9. Real Browser Integrated Journey (`QR07-081` to `QR07-090`)

| ID | Title | Test Type | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR07-081` | Authenticated Member desktop loads real Payment and QR | Normal | Member authenticated against real Spring Boot | Navigate to `/member/payments/{id}/qr-demo` | Payment loads from isolated SQL Server 2022; genuine QR session initiated | Loaded real payment from SQL Server; created genuine session via POST | `qr07-ci-verification.py`, `ci-e2e-member-desktop-pending.png` | PASS | QR-01, QR-02 | - |
| `QR07-082` | Authenticated second browser context opens genuine confirmation URL | Normal | Isolated second browser context | Navigate to backend-issued `confirmationUrl` | Fragment consumed and scrubbed; real session preview loads | Genuine preview loaded; address bar URL fragment scrubbed | `qr07-ci-verification.py`, `ci-e2e-confirm-preview.png` | PASS | QR-04 | - |
| `QR07-083` | Explicit Member consent leads to authoritative paid-simulated state | Normal | Context 2 user clicks "Confirm simulated payment" | Real POST `/api/v1/payments/simulated-qr/sessions/confirm` | Transitions to SETTLED; Paid - Simulated rendered; writes to SQL Server | Confirmed settlement committed in SQL Server 2022; status SUCCESS, type SIMULATED | `qr07-ci-verification.py`, `ci-e2e-confirm-settled.png` | PASS | QR-05 | - |
| `QR07-084` | New page load retrieves same committed state after confirmation | Regression | Member page reloads in Context 1 | Refresh `/member/payments/{id}/qr-demo` | Displays durable settled state from SQL Server with Paid - Simulated | Durable state retrieved directly from SQL Server 2022 after settlement | `qr07-ci-verification.py`, `ci-e2e-member-settled-simulated.png` | PASS | QR-05 | - |
| `QR07-085` | Receptionist independently sees new state from actual backend | Normal | Receptionist display open in Context 3 | Polls desk projection endpoint against real backend | Automatically updates from PENDING to `Paid - Simulated` | Projection reflects committed SQL Server state without active QR | `qr07-ci-verification.py`, `ci-e2e-reception-settled-simulated.png` | PASS | QR-06 | - |
| `QR07-086` | Member transaction history and invoice reflect real update | Normal | Payment settled in SQL Server | Query authoritative SQL tables | Payment status SUCCESS; invoice PAID; card ACTIVE | SQL Server readback: 1 invoice PAID, 1 card ACTIVE, 1 audit log | `qr07-ci-verification.py`, `qr07-ci-sql-evidence.json` | PASS | QR-05 | - |
| `QR07-087` | Browser failed response after committed confirm reconciles status | Boundary | Network drop after commit simulation | Duplicate confirmation call with same token | Reconciles to committed state; duplicate is idempotent 200 | Idempotent duplicate confirm returns 200 with identical transaction ID | `qr07-ci-verification.py` check Step 11 | PASS | QR-05 | - |
| `QR07-088` | No QR page screenshots contain login screen instead of content | Security | 30 browser captures executed | Inspect all captured DOMs and screenshots | Zero captures show login screen; all show target UI | All 30 screenshots verify target screen content; no redirects | `layout-geometry.json` | PASS | Sec 5 | - |
| `QR07-089` | No CDP fulfill/mock route in mandatory integrated journey | Regression | Real HTTP E2E tests in CI runner | Execute real backend and frontend over loopback HTTPS | Requests pass through HTTP server to real Spring Boot endpoints without mock fulfillment | Architecture A executed in CI: zero Fetch.fulfillRequest; authentic HTTP | `qr07-ci-verification.py`, `.github/workflows/qr07-browser.yml` | PASS | Sec 4 | - |
| `QR07-090` | Captured browser network entries link to server correlation | Normal | Network request logging | Inspect client and server logs | Each request recorded with method, URL, status, and server correlation ID | Client and server correlation IDs tracked (`X-Correlation-Id`); mock visual runner explicitly disclosed | `browser-logs/network-requests.json`, `qr07-ci-verification.py` | PASS | Sec 4 | - |

---

## 10. Desktop, Accessibility and Responsive (`QR07-091` to `QR07-100`)

| ID | Title | Test Type | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR07-091` | Desktop 1280 light/dark has readable QR and status panels | Accessibility | Viewport 1280x900 | Render Member and Receptionist pages | No horizontal overflow; all cards fully visible | ScrollWidth <= 1280px; layout intact | `reception-1280-light-pending.png`, `member-1280-light-qr-demo.png` | PASS | Sec 5 | - |
| `QR07-092` | Desktop 1440 light/dark has readable QR and status panels | Accessibility | Viewport 1440x900 | Render Member and Receptionist pages | Hero title wraps naturally (height <= 80px); QR panel top < 400px | Hero title height 44px; QR card top 230px | `reception-1440-light-pending.png`, `layout-geometry.json` | PASS | Sec 5 | - |
| `QR07-093` | Desktop 1920 light/dark has readable QR and status panels | Accessibility | Viewport 1920x1080 | Render Member and Receptionist pages | Clean wide desktop presentation; no stretched layout | Perfectly proportioned grid | `reception-1920-light-pending.png`, `member-1920-light-qr-demo.png` | PASS | Sec 5 | - |
| `QR07-094` | Member 390px reflow avoids clipped controls | Accessibility | Mobile viewport 390x844 | Render Member QR and Confirmation pages | Responsive single column; no horizontal scrollbar | ScrollWidth <= 390px; touch targets >= 44px | `member-390-light-qr-demo.png`, `confirm-context2-390-light-preview.png` | PASS | Sec 5 | - |
| `QR07-095` | Receptionist 768px reflow avoids vertical layout failures | Accessibility | Tablet viewport 768x1024 | Render Receptionist desk projection | Clean stacked layout; QR card and status visible | ScrollWidth <= 768px; stacked grid | `reception-768-light-pending.png`, `layout-geometry.json` | PASS | Sec 5 | - |
| `QR07-096` | Actual 200 percent browser zoom and 720px responsive reflow | Accessibility | 1440x900 at zoom 2.0 and separate 720x450 reflow | Render Receptionist desk display | No clipped text; essential controls accessible; layout scales cleanly | Actual 200% zoom verified (`reception-1440-light-200percent-zoom.png` at zoom 2.0); responsive reflow verified (`reception-720-light-reflow.png` at 720x450) | `reception-1440-light-200percent-zoom.png`, `reception-720-light-reflow.png` | PASS | Sec 5 | - |
| `QR07-097` | Keyboard Tab and Shift+Tab reaches actionable controls in order | Accessibility | Desktop 1440x900 | Dispatch real CDP Tab and Shift+Tab chords | Focus moves logically forward to action button, then reverses cleanly via Shift+Tab | 11 forward Tab steps reach action (<A> 'Back to payment verification'); 3 reverse Shift+Tab steps traverse backwards in exact reverse DOM order | `keyboard-navigation.json`, `reception-1440-light-keyboard-focus.png`, `reception-1440-light-shift-tab-focus.png` | PASS | Sec 5 | - |
| `QR07-098` | Axe-core execution records tags/violations/incomplete per screen | Accessibility | 30 captured views | Injected `axe.min.js` executing WCAG 2.0/2.1 AA | Full report with violations, passes, and incomplete items | 30 views audited; 0 serious violations; residual 30 gradient incomplete items documented | `axe-core-audit.json` | PASS | Sec 5 | - |
| `QR07-099` | Color contrast measured at relevant rendered pixels or qualified | Accessibility | Rendered screen nodes | Composited RGB contrast calculator | Text >= 4.5:1 (large text >= 3:1); gradient caveats documented | Secondary button and link contrast remediated in CSS (>4.5:1, e.g. #1f4fd8 on #ffffff = 6.69:1); 0 serious axe violations across all 30 views | `axe-core-audit.json`, `paymentWorkspace.css`, `reception.css` | PASS | Sec 5 | - |
| `QR07-100` | Status, loading, error and feedback have accessible names/focus | Accessibility | Screen state components | Inspect ARIA attributes and roles | `role="alert"`, `role="status"`, accessible button names | ARIA attributes verified across components | `DemoQrCard.tsx`, `receptionQrDisplay.test.tsx` | PASS | Sec 5 | - |

---

## 11. Evidence Provenance and Negative Controls (`QR07-101` to `QR07-110`)

| ID | Title | Test Type | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR07-101` | JUnit XML has actual executed testcase and success status | Normal | CI run failsafe report | Inspect `TEST-*.xml` | `deskProjectionAndRepeatedPollingProducesStrictlyZeroDatabaseWrites` present with `time > 0` and 0 failures | Testcase executed in 6.096s, status SUCCESS | `TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml` | PASS | Sec 4 | - |
| `QR07-102` | JSON evidence equals same-run JUnit system-out recursively | Normal | `qr07-round03-sql-evidence.json` and JUnit XML | `python scripts/verify-sql-evidence.py` | 100% whole-document recursive deep equality | PASS: 100% deep equality verified (target payment ID 39, 12 tables, 0 delta) | `python scripts/verify-sql-evidence.py` exit code 0 | PASS | Sec 4 | - |
| `QR07-103` | Change of nested snapshotAlgorithm rejected by verifier | Abnormal | Mutated `snapshotAlgorithm` | Run verifier on altered JSON | Verifier detects mismatch, exits with code 1 | Detected mismatch at root['snapshotAlgorithm'], exit code 1 | `positive-and-negative-verifier.log` test_02 | PASS | Sec 4 | - |
| `QR07-104` | Insertion of unknown JSON field rejected by verifier | Abnormal | Injected root/nested unknown field | Run verifier on altered JSON | Verifier detects unexpected key, exits with code 1 | Detected unexpected key, exit code 1 | `positive-and-negative-verifier.log` test_04 | PASS | Sec 4 | - |
| `QR07-105` | Altered SHA-256 digest rejected by verifier | Abnormal | Mutated table digest | Run verifier on altered JSON | Verifier detects digest mismatch, exits with code 1 | Detected digest mismatch, exit code 1 | `positive-and-negative-verifier.log` test_05 | PASS | Sec 4 | - |
| `QR07-106` | Missing table or row count mismatch rejected by verifier | Abnormal | Table removed / row count altered | Run verifier on altered JSON | Verifier detects table count/row count delta, exits 1 | Detected missing table and row count mismatch, exit code 1 | `positive-and-negative-verifier.log` test_06 | PASS | Sec 4 | - |
| `QR07-107` | Mismatched run ID/HEAD SHA rejected or marked BLOCKED | Abnormal | XML without target testcase or corrupted status | Execute fail-closed negative verifier tests | Verifier rejects missing testcase or failed testcase | Rejected missing/failed testcases with exit code 1 | `scripts/ci/test_verify_sql_evidence.py` (test_08, test_09) | PASS | Sec 4 | - |
| `QR07-108` | Authentication-less browser capture cannot be marked PASS | Security | Unauthenticated request redirects to /login | Capture screen | Detected as login screen; harness throws error | Harness enforces `isLoginPage` fail-closed check | `qr07-browser-acceptance.mjs` line 743 | PASS | Sec 5 | - |
| `QR07-109` | Original and sanitized artifacts have separately documented hashes | Normal | Report evidence files | Inspect `evidence-manifest.sha256` | Separate SHA-256 hashes for all raw and report artifacts | All artifacts indexed with exact SHA-256 | `evidence-manifest.sha256` | PASS | Sec 8 | - |
| `QR07-110` | All evidence manifest entries independently recomputed | Normal | `evidence-manifest.sha256` | Recompute SHA-256 for all entries | 100% hash match, 0 discrepancies | 100% manifest verification | Verification command output | PASS | Sec 8 | - |

---

## 12. Release Gates and Regression (`QR07-111` to `QR07-120`)

| ID | Title | Test Type | Preconditions | Inputs | Expected Result | Observed Result | Evidence | Status | Requirements | Bug Ref |
|---|---|---|---|---|---|---|---|---|---|---|
| `QR07-111` | Full frontend tests, lint, typecheck and build pass | Regression | Frontend directory | `npm run test`, `npm run lint`, `npm run build` | All 91 files / 1688 tests pass; lint 0 errors; build 0 errors | 1688 passed, 0 lint errors, build clean in 2.29s | `vitest-results.json`, build log | PASS | Sec 6 | - |
| `QR07-112` | Full backend unit and SQL integration suites pass | Regression | Backend directory | `mvn test`, `mvn verify` in CI | All unit (85+ demoqr) and Testcontainers integration pass | All test suites passed with 0 failures (1993 unit, 802 integration) | `surefire-reports-unit`, `failsafe-reports-integration` | PASS | Sec 6 | - |
| `QR07-113` | Flyway migration guard and V35/V36 upgrade checks pass | Regression | Migrations catalog | CI Flyway migration guard job | Schema migrations V1 to V36 apply cleanly | Flyway guard passed in CI run | GitHub Actions CI run | PASS | Sec 6 | - |
| `QR07-114` | All required CodeQL/security/CI gates pass on exact HEAD | Security | GitHub Actions CI workflow | All CI jobs on PR #182 HEAD | All jobs completed with status SUCCESS | All jobs passed: CodeQL, Dependency, CI Gate, SQL Integration, Conventions | GitHub Actions CI run | PASS | Sec 6 | - |
| `QR07-115` | Preexisting security alerts triaged and not silently suppressed | Security | CodeQL scan results | Review security annotations | Clean security posture verified; zero high/critical vulnerabilities | CodeQL security scan annotations verified clean | GitHub Actions CodeQL analysis | PASS | Sec 6 | - |
| `QR07-116` | Member payment and history remain compatible with QR-01 to QR-05 | Regression | Frontend and backend payment services | Execute member payment flows | Full backward compatibility maintained | Member workflows function without error | `memberPayments.test.tsx` | PASS | Sec 6 | - |
| `QR07-117` | Receptionist payment/refund UI-27/28/32 respects role and money | Regression | Receptionist views | Test cash verification and refund actions | Role boundaries and financial isolation preserved | Staff read-only projection; cash workflows isolated | `receptionPayment.test.tsx` | PASS | Sec 6 | - |
| `QR07-118` | Actual cash/refund and Manager finance regressions pass | Regression | Payment & finance modules | Execute CashEvent and FinanceReport tests | Real cash events and manager reports verified | All financial tests passed | `TEST-com.sportify.payment.FinanceReportIntegrationTest.xml` | PASS | Sec 6 | - |
| `QR07-119` | Private DOCX audit updated and absent from public PR/ZIP | Security | `docs/audit/` | Inspect `QR-07-i_AI_Audit_Log.docx` | Updated with Round 03 section; excluded via `.git/info/exclude` | Section 18 added; excluded from git status and report ZIP | `.git/info/exclude`, file check | PASS | Sec 9 | - |
| `QR07-120` | Final report links every acceptance claim to raw evidence | Normal | All Round 03 report documents | Cross-reference evidence paths | Every claim references verified raw evidence artifact | 100% evidence linkage verified | Reports 00 through 09 | PASS | Sec 10 | - |
