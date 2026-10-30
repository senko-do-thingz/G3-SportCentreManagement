# QR-04 durable simulated payment foundation

QR-04 records confirmation intent only. Payment remains PENDING, no receipt is issued, no entitlement is activated, and no cash or revenue event is inserted. The paid state in the existing Java/SQL domain is SUCCESS, not an enum named PAID. QR-05 must reconcile that mapping with its business contract before enabling settlement.

## Release and compatibility

The default `app.payment.simulator.enabled=false` is independent of the retired `app.demo.qr.enabled` setting. Both an explicit true simulator property and the `qr-simulator` profile are required. `prod` and `production` exclude the service even when the property is true. Use this profile only for a designated isolated simulation environment. This phase does not support mixing actual and simulated payments in one deployment. QR-05 must add a server-enforced cohort policy before any mixed environment can settle.

`GET /api/v1/payments/simulated-qr/capability` requires an authenticated Member and returns `enabled`, `settlementEnabled=false`, and `contractVersion=1`. The frontend fails closed on missing/invalid capability responses. The old VITE_QR_PAYMENT_DEMO build flag remains an additional protective UI gate; it grants no backend capability. Default production builds cannot activate this route.

The old `/api/v1/demo/qr/sessions` API returns authenticated Members HTTP 410 with SIMULATOR_REISSUE_REQUIRED. Old process-local tokens have no SQL row and cannot be converted. The existing `/demo/qr/confirm#token=...` browser route is retained to avoid breaking QR-01/02/03 navigation. It clears the fragment using replace navigation, keeps the token only in component memory, and uses the new authenticated API. The separate QR-03 bank display QR remains VERSION=2, MODE=NON_PAYABLE.

## API contract

All session operations require a currently ACTIVE Member, bearer authentication and current Payment ownership. Tokens never replace identity. Responses use no-store and no-referrer headers. Missing or unavailable resources use a generic denial; malformed JSON has a sanitized error. Datastore errors return 503 and never manufacture intent or payment success.

- POST `/api/v1/payments/simulated-qr/sessions`: `{paymentId, bankCode, requestId}`. requestId is a client UUID. Extra amount, owner and currency properties have no authority. The response returns one raw 256-bit token in the creation URL fragment only. Only a SHA-256 verifier is stored.
- GET `.../sessions/{id}`: owner-bound durable state, never a raw token.
- POST `.../sessions/preview`: `{token}`; no confirmation side effect. Stale eligibility or amount revokes an active session.
- POST `.../sessions/confirm-intent`: `{token}`; records AUTHORIZED_PENDING_SETTLEMENT only. Retries before expiry return the same intent without another transition.
- POST `.../sessions/{id}/cancel`: idempotent cancellation of PENDING or AUTHORIZED_PENDING_SETTLEMENT. A terminal state never returns to PENDING.

Creation retries with the same owner/requestId return 409 SIMULATOR_REISSUE_REQUIRED without revealing/recovering the original token. Starting a new request replaces any active session for that Payment/owner, including an earlier intent. A timed-out creation may have committed: restart explicitly with a new requestId. SQL uniqueness is the final arbiter. Concurrent database conflicts/connection failures return a bounded 503 retry response; no unbounded server retry loop runs.

Five creates and sixty session/token operations per Member per SQL-clock window of sixty seconds are persisted in SQL. Failed token attempts consume budget. A new process does not reset that budget. Account rows serialize initial budget insertion and budget updates; session transactions use Payment -> session lock order.

## Durable model and migration

V34 is additive. It creates `simulated_qr_session`, `simulated_qr_transition` and `simulated_qr_rate_limit`; it does not alter historical payment methods, financial enums, reporting views or cash triggers. Session snapshots include owner, Payment, display bank, authoritative amount, VND currency, verifier, creation request and UTC times. The current domain has no per-Payment currency column: VND is an explicit server-side domain contract, not a client value.

PENDING -> AUTHORIZED_PENDING_SETTLEMENT means confirmation intent received, payment not yet completed. Both active states can become CANCELED or EXPIRED. SETTLED is reserved and unreachable: the V34 lifecycle trigger rejects every transition to it. Session snapshots and transition history are immutable; history is retained on expiry. SQL filtered uniqueness enforces one active session per Payment/owner. Each actual transition increments version and appends one history row. SQL Server SYSUTCDATETIME is authoritative across nodes; authorization checks expiry again inside the SQL UPDATE, including after Payment lock waits. Both active states expire, including an intent that QR-05 has not settled.

Read and write paths lock the existing Payment before the session. Eligibility requires PENDING Payment, positive amount, matching owner, and exactly one owned card or registration still PENDING_PAYMENT. No financial writer is injected. Normal accounting and activation services remain unchanged.

Deploy V34 using normal Flyway after snapshot/backup of the designated environment. Validate a fresh isolated database and a V33 synthetic legacy cohort before rollout. SQL Server transactional DDL can be rolled back before migration commit; the integration suite dry-runs that recovery. After commit, disable the capability and roll application code back while retaining these additive tables and immutable history. Do not use Flyway clean, delete schema history, rewrite old migrations, or drop tables to recover. Investigate unexpected pre-existing objects/checksum failures rather than repairing them blindly.

## QR-05 settlement handoff

Before any successful write is enabled, QR-05 needs a new additive migration for immutable settlement provenance and an immutable `simulated_payment_transaction`. At minimum enforce a unique successful settlement per Payment, for example `CREATE UNIQUE INDEX ux_simulated_payment_success ON dbo.simulated_payment_transaction(payment_id) WHERE status='SUCCEEDED'`, plus unique session and idempotency references, Payment/Member/session FKs, positive authoritative amount/currency, provider SPORTIFY_SIMULATOR and settlement_type SIMULATED. Existing unknown records must remain unknown; never backfill SIMULATED or actual receipt provenance without evidence.

One transaction must lock Payment -> session -> linked entitlement, validate the current owner/account/cohort/token/deadline/PENDING status and exact amount/currency, insert one immutable simulated settlement, apply the legitimate activation/invoice/audit effects once, update the existing paid domain status with immutable provenance, and transition the session to SETTLED. Retry must read the already committed outcome. A competing manual/actual payment must win once, without duplicate settlements or downstream events. All partial failures roll back together.

Do not call the existing markPaymentSuccess path unchanged: CashCaptureService and V31 tr_cash_collection currently classify SUCCESS as a collection under active coverage. QR-05 must explicitly separate simulated settlement before changing Payment. V30 vw_paid_payment includes all SUCCESS rows; manager revenue, exports, ledgers, receipts, refunds and history must distinguish SIMULATED before the first write. Actual cash/reconciliation must not increase. Legacy records cannot silently be classified as actual cash. A payment-method enum alone cannot solve these semantics.

QR-05 owns the final release wording, supported route/build migration, receipts/history, reporting separation, real SQL/browser settlement E2E, recovery and kill-switch verification. CODEOWNER approval and manual merge remain required. QR-04 is not a completed payment feature.

## Validation and evidence

The required SQL suite is DemoQrDatabaseIntegrationTest, registered in scripts/ci/required-integration-tests.txt. It uses only a new isolated QR04_TEST_DB_URL matching the guarded name or a disposable CI SQL Server container. Missing database access fails the suite rather than skipping it. Local Windows runs do not use Docker. The browser script uses real Edge, compiled assets and unchanged HTTP responses from that SQL backend; its report explicitly identifies the proxy transport boundary. Full-document axe findings are recorded, including shared Brand issues, without changing the approved shared design or declaring a partial scan compliant.

Private Round 03 reports, screenshots, evidence, test master and AI Audit Log remain local. They must never be included in the PR patch. Only the CODEOWNER may approve or merge.
