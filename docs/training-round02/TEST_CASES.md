# Class learning Round 02 test cases

## SQL Server service integration

Run: `mvn -DskipUnitTests=true -Dit.test=TrainingWorkflowIntegrationTest verify` with Docker available. The test also accepts TRAINING_TEST_DB_URL/USER/PASSWORD only for a fresh database named sportify_training_test_ followed by fourteen digits. Never point it at a shared database. Tests use real Spring transaction proxies, JDBC/JPA and Flyway; no H2 or mocked training service.

| Test ID | Scenario and acceptance |
|---|---|
| R2-01 | Plan and feedback ownership; foreign coach/member denied; actor audit rows persisted. |
| R2-02 | Wrong member and cancelled booking cannot receive a new plan. |
| R2-03 | Absent/unmarked results rejected; valid result immediate own read; duplicate/conflicting correction rejected with no partial correction history. |
| R2-04 | Clock before start, at start, one second before end, exactly end and after end; actual Vietnam timezone conversion. |
| R2-05 | Early same-day coach attendance allowed; correction records prior/new state, actor and reason; wrong coach denied. |
| R2-06 | Transaction rollback removes both created plan and its audit event. |
| R2-07 | Existing self-training endpoint persists exactly once; two calls leave real linked package credits unchanged. |
| R2-08 | Class details, question/reply persistence and member/coach privacy. |
| R2-09 | Concurrent results: one success, one conflict, one durable result. |
| R2-10 | Result holds the session lock; conflicting correction waits then rejects and rolls back. |
| R2-11 | Absence correction wins first; result waits then rejects. |
| R2-12 | Actual BookingService cancellation wins first; stale plan creation waits then rejects. |
| R2-13 | Plan commits before actual cancellation; old personal plan remains readable. |
| R2-14 | SQL constraint failure after plan insert rolls the partial plan back. |
| R2-15 | A booking from another class cannot be used for assignment. |
| R2-16 | Apply V37 DDL in a transaction and roll back; then migrate forward/replay and preserve legacy rows. |
| R2-17 | Reception and self-training attendance at before/start/before-end/end/after times stay flexible. |
| R2-18 | Self-training day boundary uses Vietnam calendar day even when UTC is on the previous day. |

## Authorization and UI regression

- R2-API-01: member class/question/guidance reads return 401 without authentication, 403 to another role, 2xx to Member.
- R2-API-02: ask/reply routes enforce role and use actor ID from principal; coach inbox is role scoped.
- R2-API-03: blank subject/body/reply is a field validation error; service is not called.
- R2-API-04: coach attendance/correction rejects anonymous and Member/Manager/Reception roles; Coach is allowed.
- R2-FE-01: member class information, own attendance and assessment link.
- R2-FE-02: new class question submission and private conversation display.
- R2-FE-03: server-saved results hide the duplicate submission action.
- R2-FE-04: coach attendance invokes the coach API, not a reception endpoint.
- R2-FE-05: catalog assistance explicitly says AI is not configured and calls no provider.
- Existing training lifecycle/role/result/attendance tests remain in the suite.

## Manual acceptance script

Not claimed as browser-executed. Use separate Coach, Member and Reception sessions with approved fixtures.

1. Member with no booked class: My classes shows its empty state and Find a class link.
2. Member with a class: open Training > My classes > View class. Check class metadata, only own session statuses, cancelled labeling, result links and Ask coach link.
3. Create a supplementary practice question. Wrong member/coach URLs must not reveal it. Assigned coach opens Member questions, replies, and the Member reloads the thread.
4. Coach opens Session attendance, picks an assigned session/booking and saves Present or Late. Retry errors preserve form inputs. Correcting a record requires a reason.
5. Try result before session end: reject with a useful error. At/after end save a result for Present/Late. Reload: already-saved state is shown and duplicate action absent.
6. Try changing attendance with a result to Absent/Excused: reject; status/result/correction/audit histories remain consistent. Present-to-Late with a reason remains possible.
7. Open Assistant. Confirm explicit no-AI message. Search a sport/level and inspect real catalog matches; ask the coach for exercise guidance.
8. Check loading, empty, API failure/retry and wrong-role states; keyboard and 375px layout remain manual review items until browser evidence is attached.

## Verification integrity

No CI gate, failure assertion or skip policy is weakened. Local evidence is separate from GitHub checks on the final submitted SHA. CODEOWNER approval and schema-policy confirmation remain required even when all technical tests pass.
