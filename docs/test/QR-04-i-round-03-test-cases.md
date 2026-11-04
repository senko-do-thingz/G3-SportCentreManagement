# QR-04 Round 03 test cases

Execution uses synthetic isolated SQL data only. QR-04 records intent, never settlement. Method-based PASS statuses below refer to downloaded final-head CI results. The Windows final SQL run remains FAIL with four failures and one error from query timeouts; see sql-evidence/local-final-sql-results.json. It is not relabeled PASS. BLOCKED and NOT RUN are not passes.

Counts: PASS=51, FAIL=2, BLOCKED=7, NOT RUN=4.

## QR04-R3-01 Fresh isolated SQL migration

Category: Normal. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Create a uniquely named isolated database; run all migrations from empty schema; validate schema.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Flyway reaches V34 without touching other databases.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-02 Synthetic V33 upgrade preserves financial rows

Category: Regression. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Migrate another isolated database to V33; seed a synthetic legacy SUCCESS CASH payment; fingerprint financial rows; migrate V34.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: All nine financial fingerprints are identical after V34.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-03 Flyway repeat and validation

Category: Boundary. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. After V34, call Flyway validate and migrate again.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Validation succeeds and zero migrations execute.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-04 Transactional migration recovery

Category: Abnormal. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Apply the V34 SQL batches in an explicit transaction on the isolated V33 cohort; roll back; inspect objects and fingerprints.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Simulator tables do not remain; legacy fingerprints match; later normal migration succeeds.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-05 Foreign keys reject missing Payment

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Clone a valid terminal test session with a new UUID and nonexistent payment_id.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: SQL rejects the nonexistent Payment FK; no session/history row commits.

Actual: 6 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-06 Required fields and SQL constraints

Category: Boundary. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Attempt new sessions with NULL owner, amount, currency, verifier, bank and request ID; also exercise invalid-value parameterized SQL cases.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Every invalid row is rejected by SQL.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-07 Authoritative positive amount

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Authenticate owner; POST creation with forged amount=1 against an eligible 810000 Payment; inspect DTO and SQL.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Session amount is 810000 and positive; client amount has no authority.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-08 Authoritative currency

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Send currency=USD in the create body; inspect server snapshot and SQL.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Currency remains the server VND domain value; invalid SQL currency is rejected.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-09 Allowlisted bank

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. POST a non-allowlisted bank; separately attempt a direct invalid bank_code SQL row.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: HTTP 400 and SQL rejection respectively; no valid link is issued.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-10 Hash-only persistence

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Create a link; inspect the session SQL columns and compare their values with the raw test token.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Only a 64-character SHA-256 verifier is persisted; raw token is absent.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-11 256-bit token and verifier

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Decode the creation fragment token as unpadded base64url and inspect verifier shape.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Token decodes to 32 random bytes; verifier has 64 hexadecimal characters.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-12 Token redaction

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Evaluate Created and TokenRequest debug strings; scan retained evidence for live tokens before sealing.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Debug strings redact tokens; logs and JSON contain no live confirmation or bearer tokens.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-surefire-reports\TEST-com.sportify.demoqr.service.DemoQrServiceTest.xml`.

## QR04-R3-13 Eligible owner creates session

Category: Normal. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Log in through real HTTP; create a session for the owned PENDING Payment linked to one pending card.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: HTTP 200; durable PENDING session and creation history are committed.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-14 Other Member cannot create/read/preview

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Log in as a second Member; attempt creation, ID read and token preview against the first owner.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: HTTP 404 without another owner snapshot.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-15 Unauthenticated request denied

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. POST a valid session token without bearer authorization.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: HTTP 401; state remains unchanged.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-16 Non-Member role denied

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Assign synthetic account COACH role; log in through real HTTP; POST creation.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: HTTP 403; no session is created.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-17 Inactive Member denied

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Create a session; deactivate its account in the isolated fixture; reuse the old JWT.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Request is rejected and session stays PENDING.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-18 Unknown Payment and malformed IDs

Category: Abnormal. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Create using a nonexistent positive Payment ID; request a malformed session UUID.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Generic unavailable response; no Payment/session details disclosed.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-19 Already paid Payment denied

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Create intent session, then change isolated fixture Payment to SUCCESS; attempt intent and new create.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Both reject; stale session becomes CANCELED; no simulator settlement occurs.

Actual: 3 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-20 Failed/refunded/canceled eligibility

Category: Abnormal. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Repeat stale-eligibility test for FAILED and REFUNDED; test canceled linked card and orphan Payment.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: All ineligible creates/intents reject and no financial write is produced by simulation.

Actual: 3 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-21 Forged owner/amount/currency ignored

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Submit another owner ID and forged financial values in otherwise valid creation JSON.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Authenticated owner and authoritative Payment determine the snapshot.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-22 Malformed or missing token

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Call preview with NULL, empty and malformed tokens using the Member actor.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Generic denial; persistent budget spent; no verifier lookup for malformed values.

Actual: 5 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-surefire-reports\TEST-com.sportify.demoqr.service.DemoQrServiceTest.xml`.

## QR04-R3-23 Non-enumerating owner error

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Compare wrong-owner valid-token and unknown-token responses for preview and confirm-intent.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Same HTTP 404 response body.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-24 Exact expiry boundary denied

Category: Boundary. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Insert a valid pending test session whose expires_at_utc equals its SQL @now; attempt intent after insertion.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: No intent is accepted at or beyond the deadline; state becomes EXPIRED.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-25 Expired session never resurrects

Category: Boundary. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Read/cancel the expired session; attempt direct SQL return to PENDING.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: State stays EXPIRED and SQL resurrection is rejected.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-26 Canceled session cannot confirm

Category: Abnormal. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Cancel a session twice, then POST confirm-intent.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Idempotent cancellation; HTTP 409 on intent; no duplicate history.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-27 Replacement revokes old token

Category: Normal. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Create twice for one Payment/owner with different request UUIDs; try the prior token.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Previous session is CANCELED; only latest is active; old token cannot record intent.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-28 Cancellation/intent race

Category: Concurrency. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Release independent service instances simultaneously to cancel and record intent for one SQL session.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: SQL serializes operations; final state CANCELED; no resurrection or duplicate intent.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-29 Two-node creation policy

Category: Concurrency. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Use two separately constructed services with shared SQL; release concurrent creates.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Both requests serialize and exactly one active session remains.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-30 Two-node intent policy

Category: Concurrency. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Release concurrent confirm-intent operations from two service instances.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: One intent transition and one immutable history entry; both observe the same result.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-31 Duplicate HTTP intent retry

Category: Boundary. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. POST confirm-intent twice with the same authenticated token before expiry.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Same AUTHORIZED_PENDING_SETTLEMENT result and two history rows total including creation.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-32 Expiry retains history

Category: Regression. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Expire, read and cancel a session; inspect its history and retention constraints.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Creation and EXPIRED history remain; no cleanup deletes them.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-33 Restart durability

Category: Regression. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Reconstruct independent service/store objects; preview pending session, record intent, reconstruct again. Also run the separate real-process restart probe.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Persisted state survives reconstruction; actual process evidence is recorded separately.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-34 Connection failure and recovery

Category: Abnormal. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Point an independent SQL store at closed loopback port 1; attempt intent; reconnect with the healthy store.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Failure is surfaced, no intent is invented, and recovered session remains PENDING.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-35 Stale eligibility aborts intent

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Change pending Payment eligibility after creation; confirm using its previously valid token.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Generic denial and persisted CANCELED session.

Actual: 3 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-36 Stale amount aborts intent

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Change authoritative fixture amount after creation; confirm the old token.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Intent is rejected, session is canceled, original immutable snapshot remains.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-37 Changed Payment owner denied

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Move the isolated fixture Payment to another Member after creation; preview/confirm with original owner.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Both reject without disclosure or settlement.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-38 Payment remains pending

Category: Regression. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Fingerprint Payment before create/preview/intent/retry/cancel; compare after.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Payment status, amount, version, payment_time and all row bytes represented by fingerprint remain identical.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-39 Invoices and cash ledger unchanged

Category: Regression. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Fingerprint all nine financial/lifecycle tables around session operations.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: All fingerprints match exactly.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-40 Backend capability OFF denies bypass

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Use authenticated MVC negative tests with qr-simulator and property false; use production profile with property true; probe a real disabled backend when available.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Session endpoints absent regardless of frontend flag; settlement capability always false.

Actual: Real disabled backend: 8/8 checks PASS, including authenticated capability false/settlement false, five absent session operations, unauthenticated denial and unchanged PENDING Payment. Final-head CI disabled/profile tests also pass.

Run command: `See sql-evidence/actual-http-capability-off.json and final CI controller/configuration suites`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\sql-evidence\actual-http-capability-off.json`.

## QR04-R3-41 Old API/token invalidation

Category: Regression. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. POST an old demo token to the retired API while the simulator is enabled.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: HTTP 410 SIMULATOR_REISSUE_REQUIRED; no legacy token conversion.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-42 QR03 NON_PAYABLE unchanged

Category: Regression. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Decode the rendered legacy bank display QR in real Edge and run QR-01/02/03 regression tests.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: VERSION=2/MODE=NON_PAYABLE data remains non-payable and distinct from the app link.

Actual: Actual Edge decoded the existing bank display QR as VERSION=2/MODE=NON_PAYABLE. The separate application QR also decoded. Final CI includes the QR frontend regressions.

Run command: `python frontend/scripts/qr04-browser-check.py (exact arguments in browser-logs/live-sql-edge-run-result.json)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\browser-logs\qr04-round03-browser-results.json`.

## QR04-R3-43 Fragment-only token and removal

Category: Security. Status: BLOCKED.

1. Establish the isolated fixture and authenticated role described below.
2. Decode application QR; open as authenticated owner; inspect browser location and redacted evidence.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Token appears only in creation URL fragment, is removed on load and is invalidated before screenshot packaging.

Actual: Application QR decoded and fragment-clearing unit regressions pass. The mobile preview stalled; SQL token cleanup returned 503/access failure. Three QR-bearing screenshots were withheld privately, so screenshot invalidation is not falsely certified.

Run command: `python frontend/scripts/qr04-browser-check.py (exact arguments in browser-logs/live-sql-edge-run-result.json)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\browser-logs\screenshot-token-safety.json`.

## QR04-R3-44 Shared Brand and both themes

Category: Regression. Status: BLOCKED.

1. Establish the isolated fixture and authenticated role described below.
2. Inspect real Edge desktop/mobile pending, intent, canceled and unavailable screenshots in light and dark.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Approved shared Brand/WorkspaceHeader preserved; no PAID success wording.

Actual: Seven real Edge checks passed: JWT login, capability, two QR decoders and three responsive widths. Mobile preview then timed out. Active light/dark state coverage is incomplete; no healthy CI browser endpoint was available. Browser API transport was an unmodified loopback proxy, not native CORS/TLS proof.

Run command: `python frontend/scripts/qr04-browser-check.py (exact arguments in browser-logs/live-sql-edge-run-result.json)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\browser-logs\qr04-round03-browser-results.json`.

## QR04-R3-45 Responsive widths and native zoom

Category: Boundary. Status: BLOCKED.

1. Establish the isolated fixture and authenticated role described below.
2. Inspect 390/768/1440 CSS pixel widths and native browser 200 percent zoom.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: No horizontal overflow; native zoom must be independently exercised, not inferred from CSS scaling.

Actual: No overflow at 390/768/1440 in the captured light pending state. Native 200 percent production zoom/scroll measurements passed in both themes, but captured explanatory text was clipped; there is no visual PASS. Active simulator zoom and remaining themes/states were blocked by SQL availability.

Run command: `See browser-logs/native-zoom-production-results.json and browser execution record`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\browser-logs\native-zoom-production-results.json`.

## QR04-R3-46 Keyboard and full-document accessibility

Category: Security. Status: FAIL.

1. Establish the isolated fixture and authenticated role described below.
2. Use Enter on intent; inspect labels/focus and run axe on document including header in all captured states; exercise Escape and screen reader separately.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: No hidden active-flow defects; full-document failures and unrun screen-reader/native checks remain explicit.

Actual: Full-document axe found a serious target-size violation on shared .home-theme at 1440. All three scans had incomplete gradient color-contrast checks (4/6/11 nodes). Shared Brand accessible-name mismatch was not reproduced in captured states. Keyboard, Escape, screen-reader and reduced-motion coverage is incomplete; no WCAG PASS.

Run command: `python frontend/scripts/qr04-browser-check.py (exact arguments in browser-logs/live-sql-edge-run-result.json)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\browser-logs\qr04-round03-browser-results.json`.

## QR04-R3-47 Member/Home/payment regressions

Category: Regression. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Run full Vitest plus existing backend unit and SQL CI suites on final source; inspect Member/payment/Home and UI-27/28/32 outcomes.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: All mandatory regression suites execute without failures or hidden skips.

Actual: Final-head CI full frontend suite: 1642 tests PASS, zero failed/pending. Backend unit 1962 and SQL integration 797 also PASS with zero skipped. Windows full-suite aborts are retained separately and are not relabeled.

Run command: `CI 40923430563: npm ci; npm run lint; npm run test -- --reporter=default --reporter=json --outputFile=vitest-results.json; npm run build`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-frontend-test-reports\vitest-results.json`.

## QR04-R3-48 Staff/shared header regressions

Category: Regression. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Run staff workspace, shared WorkspaceHeader, route and money-format regressions in the full frontend suite.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Existing staff and shared shell behavior is preserved.

Actual: Final-head CI full frontend suite: 1642 tests PASS, zero failed/pending. Backend unit 1962 and SQL integration 797 also PASS with zero skipped. Windows full-suite aborts are retained separately and are not relabeled.

Run command: `CI 40923430563: npm ci; npm run lint; npm run test -- --reporter=default --reporter=json --outputFile=vitest-results.json; npm run build`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-frontend-test-reports\vitest-results.json`.

## QR04-R3-49 Authorization guard negative control

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Temporarily remove the Member/ACTIVE service guard in the same checkout; run its targeted security test; restore exact source bytes and rerun.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Security test FAILS under mutation and passes after restoration; no mutant is committed.

Actual: Deliberately disabled Member/ACTIVE guard made inactiveAndWrongRole fail (expected exit 1). Exact original bytes restored. Final-head CI authorization tests pass.

Run command: `See security-negative-control command/result artifacts and final-head CI DemoQrServiceTest`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\sql-evidence\security-negative-control-result.json`.

## QR04-R3-50 SQL uniqueness negative control

Category: Concurrency. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Within an isolated rollback-only transaction remove the active-session unique index, insert a duplicate, run the invariant assertion, then roll back.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Invariant assertion FAILS under mutation and SQL index is restored by rollback.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-51 Real HTTP JWT with real SQL

Category: Normal. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Start actual Spring Boot random-port server using isolated SQL, log in and complete authenticated intent lifecycle.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Real HTTP/JWT, Flyway, JPA and SQL writes execute with financial isolation.

Actual: 1 matching final-head CI executed result(s), zero failures/errors/skips. This records the CI execution; the separately retained Windows timeout failures are not relabeled PASS.

Run command: `CI run 40923430563: mvn -B -ntp verify -DskipUnitTests=true -Dci.cash.profile=false (SQL); mvn -B -ntp test (unit)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-failsafe-reports\TEST-com.sportify.demoqr.DemoQrDatabaseIntegrationTest.xml`.

## QR04-R3-52 Real Edge against SQL backend

Category: Normal. Status: BLOCKED.

1. Establish the isolated fixture and authenticated role described below.
2. Launch actual Edge against compiled assets; forward API responses unchanged from the healthy isolated SQL backend; exercise desktop/mobile intent.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Actual SQL-backed browser behavior passes; transport/native-device limitations are explicit.

Actual: Seven real Edge checks passed: JWT login, capability, two QR decoders and three responsive widths. Mobile preview then timed out. Active light/dark state coverage is incomplete; no healthy CI browser endpoint was available. Browser API transport was an unmodified loopback proxy, not native CORS/TLS proof.

Run command: `python frontend/scripts/qr04-browser-check.py (exact arguments in browser-logs/live-sql-edge-run-result.json)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\browser-logs\qr04-round03-browser-results.json`.

## QR04-R3-53 Private artifacts excluded from PR

Category: Security. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Inspect staged diff, exact PR patch and tracked paths for audit, tasks, screenshots, env, backup and cache files.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: No private audit/report/secret/cache artifacts appear in commit or PR diff.

Actual: Exact final-head source manifest and patch contain only 35 reviewed source/test/migration/documentation paths. Audit, tasks/reports, environment files, private QA and original user changes are excluded.

Run command: `git diff --name-only origin/main...HEAD; git ls-tree -r HEAD; exact Git blob/source manifest inspection`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\source-review\source-manifest.json`.

## QR04-R3-54 Required CI on exact final HEAD

Category: Regression. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Fetch main again; push normal reviewed commit; inspect required/all CI checks for that exact PR head SHA.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Required checks green with actual mandatory SQL execution; no self-approval or merge.

Actual: Final head 900048596a203f307645042b09820377942fd38d: 16 checks SUCCESS, none failed/pending/skipped/canceled. Required CI gate: 1/1 SUCCESS. Mandatory real SQL suite ran 33 cases, zero skipped. PR stays draft and unmerged.

Run command: `gh pr checks 167 --repo senko-do-thingz/G3-SportCentreManagement --required --json name,state,link; gh run view 40923430563`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\ci-evidence\final-pr-checks.json`.

## QR04-R3-55 Master/copy SHA-256 equality

Category: Regression. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Write this master and byte-identical report copy; independently hash each file and compare.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Both SHA-256 values are identical and recorded; mismatch fails evidence sealing.

Actual: Python independently computes SHA-256 for the master and copied bytes and asserts equality; separate PowerShell Get-FileHash verification is recorded after creation.

Run command: `python build-test-cases.py; Get-FileHash -Algorithm SHA256 for each exact master/copy path`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\test-cases\test-case-hashes.json`.

## QR04-R3-56 Windows final SQL execution

Category: Abnormal. Status: FAIL.

1. Establish the isolated fixture and authenticated role described below.
2. Execute the real integration class on the final source against the guarded isolated SQLEXPRESS database; inspect every JUnit case.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: All 33 execute without failure/error/skip; timeouts remain FAIL.

Actual: Final Windows SQL execution on the final source: 33 tests, four failures and one error, zero skipped. Failures were 503/query timeouts. This local run remains FAIL despite the separately green final-head CI suite.

Run command: `See sql-evidence/foundation-verify-attempt-06-result.json and .log`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\sql-evidence\local-final-sql-results.json`.

## QR04-R3-57 Actual JVM restart probe

Category: Regression. Status: BLOCKED.

1. Establish the isolated fixture and authenticated role described below.
2. Create PENDING and intent sessions in SQL; stop only the owned JVM; restart the same source and attempt authenticated reads.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Both durable states are read after a real process restart before their deadlines.

Actual: Before restart, real authenticated SQL HTTP create and intent operations passed. After stopping/restarting the owned JVM, reads timed out and SQL later refused the isolated database. Actual process-restart acceptance is not certified; CI object reconstruction is separate evidence.

Run command: `Private synthetic-fixture capture-restart.py before/after; sanitized results retained under sql-evidence`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\sql-evidence\actual-restart-before.json`.

## QR04-R3-58 Physical phone scan

Category: Regression. Status: NOT RUN.

1. Establish the isolated fixture and authenticated role described below.
2. Scan the application QR with a physical phone over the allowed HTTPS origin; authenticate and record intent.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Physical device completes the intent-only flow with no financial mutation.

Actual: No physical phone run was performed. Edge mobile viewport is not physical-device proof.

Run command: `Not run: physical phone scan`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\07-browser-accessibility-and-visual-review.md`.

## QR04-R3-59 Screen reader verification

Category: Regression. Status: NOT RUN.

1. Establish the isolated fixture and authenticated role described below.
2. Use an actual screen reader on pending, unavailable, intent and canceled states in both themes.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Labels, status announcements, focus and navigation are understandable.

Actual: No actual screen reader execution was performed. Axe and JSDOM do not substitute for this check.

Run command: `Not run: actual screen reader`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\07-browser-accessibility-and-visual-review.md`.

## QR04-R3-60 Native HTTPS and CORS positive flow

Category: Security. Status: NOT RUN.

1. Establish the isolated fixture and authenticated role described below.
2. Open the configured HTTPS origin without Playwright interception; call the backend from that origin and reject disallowed origins.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Native certificate trust and actual CORS behavior pass without proxy inference.

Actual: The browser used virtual HTTPS with unmodified API forwarding to loopback. Native certificate trust/CORS positive deployment flow was not exercised.

Run command: `Not run: native configured-origin HTTPS/CORS flow`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\07-browser-accessibility-and-visual-review.md`.

## QR04-R3-61 Private audit render and visual gate

Category: Regression. Status: BLOCKED.

1. Establish the isolated fixture and authenticated role described below.
2. Append the authorized audit while preserving prior content; use the packaged renderer; inspect every generated page PNG.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Audit append and backup verify; every page is visually checked before a visual PASS.

Actual: The private audit append is performed with original backup/hash and prefix preservation. Packaged runtime has no LibreOffice bundle; renderer/PNG inspection gate remains BLOCKED. See final private-audit-verification.json for actual renderer result. No audit text or rendering is copied into reports.

Run command: `Bundled Node artifact marker; bundled Python private audit editor; skill render_docx.py with bundled-only converter PATH`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\09-private-audit-status.md`.

## QR04-R3-62 Windows full Vitest execution

Category: Regression. Status: BLOCKED.

1. Establish the isolated fixture and authenticated role described below.
2. Run the complete frontend suite on Windows and retain actual command completion or timeout.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Complete execution without failure, timeout or hidden skipped tests.

Actual: Windows full Vitest attempts timed out/aborted; focused runs and final-head CI execution are recorded separately. No local full-suite PASS is claimed.

Run command: `See ci-evidence/frontend-vitest-attempt-01.log and frontend-vitest-attempt-02-result.json`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\08-regression-and-ci.md`.

## QR04-R3-63 Reduced motion browser verification

Category: Regression. Status: NOT RUN.

1. Establish the isolated fixture and authenticated role described below.
2. Enable native prefers-reduced-motion and exercise all active confirmation states in the real browser.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Motion respects the preference and focus remains visible.

Actual: No completed real-browser reduced-motion pass was captured after the SQL-backed path stalled.

Run command: `Not run: real-browser reduced-motion full-state exercise`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\07-browser-accessibility-and-visual-review.md`.

## QR04-R3-64 Native 200 percent production zoom

Category: Boundary. Status: PASS.

1. Establish the isolated fixture and authenticated role described below.
2. Load the normal production-disabled page in real Edge; use browser-native zoom APIs to set and independently read 200 percent in light/dark.
3. Compare the result with the exact expected behavior and retain the evidence.

Expected: Native zoom reads 2; viewport changes from 1440 to 720 CSS pixels with no overflow in either theme.

Actual: Native zoom/scroll measurements only: real Edge chrome.tabs.setZoom/getZoom returned 2 in both themes. DPR changed 1 to 2; viewport 1440 to 720 CSS px; scroll width stayed 720. Two token-free screenshots retained. Visual inspection found clipped explanatory text, so overall responsive/visual acceptance is not PASS; see cases 45/46 and report 07.

Run command: `python browser-logs/native-zoom-probe.py production (with private synthetic runtime configuration)`.

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\browser-logs\native-zoom-production-results.json`.


# CODEOWNER request changes follow-up execution

Original 64 results above are historical and unchanged. The following 32 cases record new execution on source 4c6d2e4f98f5536908321d5639ddc1e24c8ffd8d. No old FAIL, BLOCKED or NOT RUN was relabeled. Follow-up counts: {"BLOCKED": 1, "FAIL": 1, "NOT RUN": 2, "PASS": 28}. Combined historical and follow-up counts: {"BLOCKED": 8, "FAIL": 3, "NOT RUN": 6, "PASS": 79}.

## QR04-R3-65 Locate both original manifest mismatches

Category: Regression. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Compare every old manifest entry with current file bytes and retain the changed files and expected hashes.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Exactly the delayed health JSON/text outputs are identified.

Actual: Two mismatches identified and preserved; delayed writer caused SQL memory-error output to replace the bounded-probe outputs.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\manifest-mismatch-investigation.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\manifest-mismatch-investigation.json`.

## QR04-R3-66 Independently verify the replacement manifest

Category: Regression. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Wait for every writer, seal the finalized package, then verify every entry from a fresh process and repeat with PowerShell.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Zero mismatches for the final manifest; no late writer changes any entry.

Actual: Independent verification receipt records zero mismatches.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\manifest-independent-verification.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\manifest-independent-verification.json`.

## QR04-R3-67 Preserve original local user artifacts

Category: Regression. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Compare original user frontend manifest, test masters and personal report hashes; retain the authorized private audit backup.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Original local changes and personal artifacts remain unchanged and unstaged.

Actual: Prior complete 31494-file preservation is retained; original critical files rechecked after this review.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\preservation-review.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\preservation-review.json`.

## QR04-R3-68 Safely synchronize existing branch and PR

Category: Regression. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Fetch origin/main in the existing checkout; verify ancestry and live PR head; inspect staged/private paths.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Same branch/checkout/PR, latest main incorporated, no private artifacts committed or approval/merge.

Actual: Final source manifest and PR state match the final head; only narrow verification/header changes were committed.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\final-pr-state.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\final-pr-state.json`.

## QR04-R3-69 Fresh healthy isolated CI SQL migration

Category: Normal. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Start the dedicated SQL Server CI service; create a unique synthetic database; boot the real application and inspect numeric Flyway maximum.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: V34 is applied in the fresh isolated database; no everyday data is accessed.

Actual: Dedicated CI verification PASS on exact reviewed head.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\ci-final\browser\verification.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\ci-final\browser\verification.json`.

## QR04-R3-70 Real SQL-backed native Edge browser

Category: Normal. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Use compiled simulator assets and real HTTPS API sockets; execute create, preview, intent, refresh and cancellation without interception.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Both themes complete the real SQL-backed browser flow.

Actual: Local final 28 functional checks PASS; independent fresh CI browser verification also PASS. 

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\native-browser-attempt-02\browser-logs\qr04-round03-browser-results.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\native-browser-attempt-02\browser-logs\qr04-round03-browser-results.json`.

## QR04-R3-71 Real process restart preserves PENDING

Category: Regression. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Create a SQL session, stop its actual JVM, launch a new JVM and read the same session.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: State remains PENDING and verifier works after process replacement.

Actual: Windows and fresh CI actual-JVM restart checks PASS.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\ci-final\browser\verification.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\ci-final\browser\verification.json`.

## QR04-R3-72 Real process restart preserves intent

Category: Regression. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Record AUTHORIZED_PENDING_SETTLEMENT; replace the JVM; read and preview the persisted session before its deadline.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Intent survives with no paid transition; canceled replay is rejected.

Actual: Windows actual restart evidence and independent CI checks PASS.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\actual-process-restart-after.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\actual-process-restart-after.json`.

## QR04-R3-73 Financial boundary across browser and restart

Category: Security. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Fingerprint all nine financial tables before and after browser/restart/intent/retry/cancel; inspect Payment status.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: All rows unchanged; Payments remain PENDING and no active captured token remains.

Actual: Fresh CI compares complete SQL SHA-256 fingerprints, verifies PENDING Payments and zero active sessions.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\ci-final\browser\verification.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\ci-final\browser\verification.json`.

## QR04-R3-74 Mobile light and dark intent lifecycle

Category: Regression. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Use 390px actual Edge viewports, open decoded application links, record intent, refresh cancellation and inspect unavailable states.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Every state is readable in both themes and never advertises paid success.

Actual: Final native run completes both themes, 14 screenshots and document scans. 

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\native-browser-attempt-02\browser-logs\qr04-round03-browser-results.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\native-browser-attempt-02\browser-logs\qr04-round03-browser-results.json`.

## QR04-R3-75 Anonymous owner login in mobile flow

Category: Security. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Open a valid fragment link in an anonymous browser, verify no owner details, submit the synthetic owner form and await real preview.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Details stay hidden before login; authentication retains only the in-memory token.

Actual: Advanced attempt 02 executed real login successfully; failed locator attempt 01 remains retained.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\advanced-native-browser-attempt-02.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\advanced-native-browser-attempt-02.json`.

## QR04-R3-76 Fragment clearing and rescan after reload

Category: Security. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Open the valid link, inspect URL hash, reload, verify reissue/unavailable behavior, then explicitly rescan.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Fragment removed; reload loses the in-memory token; explicit rescan restores preview.

Actual: Advanced checks pass in both themes.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\advanced-native-browser-attempt-02.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\advanced-native-browser-attempt-02.json`.

## QR04-R3-77 Native HTTPS transport

Category: Security. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Use actual TLS servers and native Edge sockets with the temporary certificate pin and host mapping scoped only to QA.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Native browser accesses frontend/API over HTTPS without response interception or global trust-store changes.

Actual: Windows and independent CI browser runs use native TLS. Public DNS/public-CA deployment is not inferred.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\ci-final\browser\browser-results.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\ci-final\browser\browser-results.json`.

## QR04-R3-78 Allowed browser CORS origin

Category: Security. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Send authenticated request with the exact allowed Origin and fetch through native cross-origin Edge.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Exact allow-origin response and successful authenticated fetch.

Actual: Server response and native browser fetch both PASS.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\advanced-native-browser-attempt-02.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\advanced-native-browser-attempt-02.json`.

## QR04-R3-79 Rejected browser CORS origin

Category: Security. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Send a disallowed Origin and perform native browser fetch/preflight from that HTTPS host.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Server rejects 403 and browser blocks reading the response.

Actual: Actual backend rejection and Edge TypeError checks PASS.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\advanced-native-browser-attempt-02.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\advanced-native-browser-attempt-02.json`.

## QR04-R3-80 ThemeToggle is no longer obscured

Category: Regression. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Reproduce overlap, rebuild the existing menu breakpoint change and measure/axe the header at seven widths including 1540/1541.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: No navigation overlap and zero target-size violations.

Actual: All seven widths pass after fix; original 1440 failure is retained.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\header-after-fix.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\header-after-fix.json`.

## QR04-R3-81 Responsive 390 768 1440 in both themes

Category: Boundary. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Capture each real desktop pending page from scroll position zero in both themes; inspect document overflow and visual layout.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: No horizontal overflow, readable controls, shared approved Brand.

Actual: Native final run passes each width/theme; captures use stable scroll origin. 

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\native-browser-attempt-02\browser-logs\qr04-round03-browser-results.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\native-browser-attempt-02\browser-logs\qr04-round03-browser-results.json`.

## QR04-R3-82 Full-document axe without hidden header

Category: Security. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Run axe on document including header for pending, intent, canceled and unavailable states in both themes.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: No confirmed violations; incomplete findings remain explicit and are measured separately.

Actual: Fourteen final local scans have zero confirmed violations; CI scans also pass. This is not a WCAG certification.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\native-browser-attempt-02\browser-logs\qr04-round03-browser-results.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\native-browser-attempt-02\browser-logs\qr04-round03-browser-results.json`.

## QR04-R3-83 Measure gradient contrast items

Category: Security. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Capture actual rendered backgrounds for axe-incomplete text; use original sRGB foreground and ancestor opacity, text Range bounds and WCAG luminance thresholds.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Every sampled visible text background meets the applicable 3 or 4.5 threshold.

Actual: 54 text nodes pass across eight width/theme scenes; raw axe incomplete findings remain retained.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\rendered-gradient-contrast.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\rendered-gradient-contrast.json`.

## QR04-R3-84 Production native 200 percent capture

Category: Boundary. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Use native Edge setZoom/getZoom=2 with an unemulated window; capture the full physical surface and inspect text/ThemeToggle.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Complete text visible, no horizontal clipping, correct native zoom.

Actual: DOM bounds and full-surface captures pass both themes; prior partial-surface images remain historical.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\native-zoom-diagnostic.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\native-zoom-diagnostic.json`.

## QR04-R3-85 Active native 200 percent pending and intent

Category: Boundary. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Record actual native zoom 2 in both themes; inspect pending/intent text ranges and full-surface images.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Text fits horizontally, fragment cleared, intent remains unpaid.

Actual: Advanced native checks pass for pending and intent in both themes.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\advanced-native-browser-attempt-02.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\advanced-native-browser-attempt-02.json`.

## QR04-R3-86 Keyboard Enter focus and Escape

Category: Regression. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Focus intent, press Enter and check result region; open member navigation and press Escape.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Result focus moves correctly; menu closes and focus returns to its toggle.

Actual: Both canonical and advanced real Edge keyboard checks PASS.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\native-browser-attempt-02\browser-logs\qr04-round03-browser-results.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\native-browser-attempt-02\browser-logs\qr04-round03-browser-results.json`.

## QR04-R3-87 Reduced motion execution

Category: Regression. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Enable real browser reduced-motion preference and exercise intent states while inspecting running animations.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Preference is active and no running animations remain.

Actual: Canonical preference and advanced no-running-animation checks PASS.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\advanced-native-browser-attempt-02.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\advanced-native-browser-attempt-02.json`.

## QR04-R3-88 Real browser offline recovery

Category: Abnormal. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Make the actual browser offline, refresh state, verify no fake success, restore network and refresh SQL state.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Failure hides stale intent controls; recovery reads the same PENDING session.

Actual: Actual browser offline and recovery checks PASS.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\advanced-native-browser-attempt-02.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\advanced-native-browser-attempt-02.json`.

## QR04-R3-89 Invalidate every packaged QR token

Category: Security. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Track every created browser/restart token, cancel through actual API, confirm terminal state, then package images.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: All captured tokens verified CANCELED or EXPIRED; no active link is packaged.

Actual: Local new captures and fresh CI screenshots have terminal verification. Old unverified captures remain withheld.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\ci-final\browser\verification.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\ci-final\browser\verification.json`.

## QR04-R3-90 Retain final Windows SQL full-run outcome

Category: Regression. Status: FAIL.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Run all 33 QR SQL cases after stopping the owned browser backend; inspect the final local JUnit file.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: All execute without failure/error/skip.

Actual: 33 executed, one failure and two errors from JDBC/read timeouts or 503, zero skipped. This remains FAIL despite separately green CI.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\healthy-sql-final-summary.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\healthy-sql-final-summary.json`.

## QR04-R3-91 Final required CI and browser CI on reviewed head

Category: Regression. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Inspect exact-head required/all checks; download and parse backend/frontend SQL and new browser artifacts; run required-suite guards.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: All checks green with actual essential SQL/browser execution and no hidden skips.

Actual: Final CI summary records actual downloaded results and exact source SHA.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\final-ci-summary.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\final-ci-summary.json`.

## QR04-R3-92 Append private audit and preserve prior content

Category: Regression. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Back up exact current DOCX bytes, append CODEOWNER review operations privately and verify previous paragraphs/tables.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Prior content and backup hashes match; no audit content enters reports or Git.

Actual: Review checkpoint append and preservation verified in non-sensitive metadata.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\private-audit-verification.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\private-audit-verification.json`.

## QR04-R3-93 Render and visually inspect private audit

Category: Regression. Status: BLOCKED.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Render the updated private DOCX using an authorized converter and inspect every page PNG at original resolution.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Every current page visually verified; no audit PDF/PNG/text appears in reports.

Actual: Bundled converter absent; native Word failed. Explicit override for private portable converter is pending; no visual PASS.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\private-audit-verification.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\private-audit-verification.json`.

## QR04-R3-94 Physical phone scan

Category: Regression. Status: NOT RUN.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Use a real phone with a trusted HTTPS test endpoint and record actual scan/login/intent result.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Physical device completes intent-only flow with no financial change.

Actual: No connected phone or operator/environment was available. Browser mobile emulation is not physical-device proof.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\manual-gates.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\manual-gates.json`.

## QR04-R3-95 Spoken screen-reader verification

Category: Regression. Status: NOT RUN.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Use an actual screen reader/operator and record spoken labels, status, focus and navigation across states/themes.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Output is understandable and complete.

Actual: No spoken-output operator/capture was available. Axe/ARIA/keyboard tests do not substitute for this gate.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\manual-gates.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\codeowner-request-changes\manual-gates.json`.

## QR04-R3-96 Exact final source patch and private exclusion

Category: Security. Status: PASS.

1. Establish the isolated fixture and exact final source identified in the evidence.
2. Generate binary/full-index diff from correct merge base to final HEAD; hash actual Git blobs and inspect all changed paths.
3. Compare expected behavior, inspect actual output and retain failures without relabeling.

Expected: Patch/manifest match exact source; user changes, audit, reports and secrets excluded.

Actual: Source manifest proves backend unchanged from approved foundation and application assets unchanged since the tested header build.

Run command: See exact command/result and source references in D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\source-review\source-manifest.json

Evidence: `D:\G3-SportCentreManagement\SWP\tasks\QR-04 (i)\reports\round-03\source-review\source-manifest.json`.

