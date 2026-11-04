# QR-04 Round 01 executable test cases

Generated 2026-10-29T11:56:22.791330+00:00. Source HEAD: 6591c88cdaba811fe3e87c9bb39231f6df4daf5d. Base main: d8a56c5bc22ccebae3d67ed82f6ed8afbe57ba36.

All browser accounts/payment values are synthetic. Real SQL tests use a new isolated database. PASS means executed evidence; PARTIAL preserves a split boundary; BLOCKED is an environmental release gate; NOT RUN is unexecuted. No case calls a real settlement API.

Reproduction: run backend unit/controller/configuration cases from backend with mvn -B -Dtest=<named class> test. Run frontend cases from frontend with npm test -- src/components/qr/DemoSessionPanel.test.tsx src/pages/member/demoConfirmation.test.tsx. SQL integration requires the explicit isolated database URL/Docker setup documented in frontend/docs/QR-04-demo-session-confirmation.md; do not run fixture writes against a shared database. The exact final browser command is in logs/final-ui-browser-command.json. Append --extra-only with a new output directory for negative/loading/clipboard cases. Current test server is stopped to revoke screenshot capabilities; restart only the documented isolated fixture or authorized live-SQL environment.

## QR04-TC-01 - Latest origin/main is fetched, fast-forwarded, verified exactly before QR-04 branch creation

- Level: Git/evidence
- Priority: P0
- Classification: Normal
- Requirement IDs: Section 1 / synchronization
- Setup: Use the primary repository preservation inventory and recorded baseline environment; never discard or stash user files.
- Runner/assertion: Git preservation preflight
- Input: Existing dirty primary checkout and origin/main
- Steps: Record all local paths/hashes; fetch; switch main; pull --ff-only origin main; compare main/origin SHAs before branch creation. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Both refs equal the recorded base; every preserved hash matches before feature branch creation.
- Actual result: Exact fetch/switch/ff-only pull/equality completed before branch; 263 hashes preserved.
- Evidence: logs/main-sync.json; logs/branch-created.json
- Run status: PASS

## QR04-TC-02 - A dirty or divergent checkout blocks safely without deleting or modifying user data

- Level: Git/evidence
- Priority: P0
- Classification: Abnormal
- Requirement IDs: Section 1 / synchronization
- Setup: Use the primary repository preservation inventory and recorded baseline environment; never discard or stash user files.
- Runner/assertion: Git preservation preflight
- Input: A hypothetical overlapping dirty edit or divergent main
- Steps: In a disposable authorized environment, attempt the same non-forced switch/ff-only pull and stop on conflict; do not reproduce by editing user data. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Git blocks the unsafe operation and all user bytes survive. This negative setup was NOT RUN.
- Actual result: No synchronization conflict occurred. Preservation passed; no destructive artificial conflict was introduced.
- Evidence: baseline-and-main-sync.md
- Run status: NOT RUN

## QR04-TC-03 - Latest QR-01/02/03 and newly merged UI behavior remains present

- Level: Git/evidence
- Priority: P0
- Classification: Normal
- Requirement IDs: Section 1 / synchronization
- Setup: Use the primary repository preservation inventory and recorded baseline environment; never discard or stash user files.
- Runner/assertion: Baseline QR/Home focused suites; final CI frontend suite
- Input: Merged QR-01/02/03 and latest shared UI
- Steps: Run the recorded baseline suites; inspect incoming main paths; run final integration-focused suites and final CI. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: 94 baseline QR and 116 Home-related tests pass; latest integrated regression and final CI pass.
- Actual result: Merged QR code and newest Home/media inspected; baseline QR 94 and Home-related 116 passed.
- Evidence: logs/baseline-frontend-focused.log; logs/baseline-home-member.log
- Run status: PASS

## QR04-TC-04 - Feature disabled in production profile and default backend configuration

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-01
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrConfigurationTest.independentProfileAndExplicitOptInAreBothRequired; DemoQrDisabledControllerTest
- Input: Default, dev without opt-in, qr-demo opt-in, prod/production mixed profiles
- Steps: Run the profile/property matrix and authenticated requests to all five demo operations under disabled profiles. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Only explicit non-production opt-in creates the service; disabled operations return 404.
- Actual result: Only explicit non-production opt-in creates the service; disabled operations return 404. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-05 - Frontend disabled flag prevents demo access and actions

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-01
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoSessionPanel.test.tsx; demoConfirmation.test.tsx
- Input: VITE_QR_PAYMENT_DEMO disabled; production mode with the flag forced true
- Steps: Render both entry points with feature disabled; open the normal production build confirmation route. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: No session creation or confirmation control/API call is exposed.
- Actual result: No session creation or confirmation control/API call is exposed. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-06 - Authenticated owner creates a session for an eligible pending payment

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-02
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.bindsEveryAllowedBankToImmutableAuthorizedSnapshot
- Input: ACTIVE MEMBER owner, payment 52 PENDING, amount 810000, eligible linked item
- Steps: Create a session with each accepted bank and inspect immutable server response fields. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: PENDING, NON_PAYABLE, DEMO-52, 810000 VND and the selected fake bank are returned with bounded lifetime.
- Actual result: PENDING, NON_PAYABLE, DEMO-52, 810000 VND and the selected fake bank are returned with bounded lifetime. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-07 - Unauthenticated session creation denied

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Abnormal
- Requirement IDs: QR04-FR-02
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrControllerTest.unauthenticatedPostDenied; unauthenticatedReadDenied
- Input: No bearer on create, get, preview, confirm and cancel
- Steps: Invoke every operation without authenticated principal. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: 401; no demo transition or private data disclosure.
- Actual result: 401; no demo transition or private data disclosure. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-08 - Different member cannot create or inspect another owner's session

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Abnormal
- Requirement IDs: QR04-FR-02
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.anotherOwnerCannotReadConfirmCancelOrCreateAgainstThisPayment
- Input: Owner 2 session; MEMBER 3 actor
- Steps: Attempt create/read/confirm/cancel as the other member, then preview as the owner. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Other member is denied generically; owner still sees PENDING.
- Actual result: Other member is denied generically; owner still sees PENDING. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-09 - Invalid/missing/forged payment ID denied

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Abnormal
- Requirement IDs: QR04-FR-02
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.rejectsInvalidIdsAmountsActorsAndLinkedItems; DemoQrControllerTest.invalidCreateBodyHasSanitizedError
- Input: ID 0, nonexistent 999, missing or malformed create fields
- Steps: Submit the controlled invalid IDs/bodies and inspect sanitized response. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: No session for invalid payment; no body/token echo or repository write.
- Actual result: No session for invalid payment; no body/token echo or repository write. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-10 - Paid/refunded/canceled/ineligible payment denied using current domain rules

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Abnormal
- Requirement IDs: QR04-FR-02
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.rejectsIneligibleFinancialStatusAtCreateAndRevalidatesAtConfirm; rejectsInvalidIdsAmountsActorsAndLinkedItems
- Input: SUCCESS/FAILED/REFUNDED payment; ACTIVE card or CANCELLED registration; zero amount
- Steps: Try creation for each invalid state; change eligibility after creating a valid session and try confirm. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Create/confirm denied; pending demo invalidated when eligibility changes; financial status remains the supplied status.
- Actual result: Create/confirm denied; pending demo invalidated when eligibility changes; financial status remains the supplied status. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-11 - Valid bank codes of all four QR-02 banks accepted

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-02
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.bindsEveryAllowedBankToImmutableAuthorizedSnapshot
- Input: VIETCOMBANK_DEMO, BIDV_DEMO, TECHCOMBANK_DEMO, MB_BANK_DEMO
- Steps: Execute all enum cases and inspect selected code and immutable label. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Exactly the four approved fake banks accepted.
- Actual result: Exactly the four approved fake banks accepted. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-12 - Unknown/tampered bank code rejected

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Abnormal
- Requirement IDs: QR04-FR-02
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrControllerTest.invalidCreateBodyHasSanitizedError
- Input: Unknown/tampered bank string and null bank
- Steps: POST create with invalid bank controls. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Sanitized 400 DEMO_INVALID_REQUEST; no usable token.
- Actual result: Sanitized 400 DEMO_INVALID_REQUEST; no usable token. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-13 - Amount/currency/owner supplied by client cannot override server snapshot

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-02
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrControllerTest.realServiceJourneyPreviewConfirmReplayRefreshAndCancelNeverMutateFinancialRepository; DemoQrServiceTest.amountChangeInvalidatesPendingSnapshot
- Input: Valid payment plus forged client owner/amount/currency fields
- Steps: Submit client override fields; inspect server snapshot; subsequently change repository amount and confirm. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Server-derived owner/810000/VND prevail; later amount mismatch cancels demo and denies confirm.
- Actual result: Server-derived owner/810000/VND prevail; later amount mismatch cancels demo and denies confirm. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-14 - CSPRNG-generated token is non-predictable and not derived from payment ID

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-02
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.rawTokensAreAbsentFromStoreAndDebugStringsAndRestartRevokesThem; source DemoQrService.java
- Input: Repeated creation with identical payment/bank
- Steps: Generate five tokens; compare uniqueness; inspect SecureRandom 32-byte generation and URL-safe encoding. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Five distinct opaque tokens; 256-bit CSPRNG construction is verified by source, not inferred from a statistical sample.
- Actual result: Five distinct opaque tokens; 256-bit CSPRNG construction is verified by source, not inferred from a statistical sample. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-15 - Token raw value is not written to logs or persisted in plaintext

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Abnormal
- Requirement IDs: QR04-FR-02
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.rawTokensAreAbsentFromStoreAndDebugStringsAndRestartRevokesThem; DemoQrControllerTest.tokenNeverReturnedByReadsAndInvalidBodyNeverEchoed
- Input: Raw create token; reflected in-memory entry fields; malformed request body
- Steps: Check store fields/debug DTO text/read responses; review new logging/persistence source. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Only SHA-256 verifier retained; no raw token in store/debug/read/error/log code or persistence.
- Actual result: Only SHA-256 verifier retained; no raw token in store/debug/read/error/log code or persistence. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-16 - QR-03 Version 2 data QR unchanged for every bank

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-03
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: qr04-browser-check.py full run; existing QR regression suites
- Input: All four banks; first and second authorized payment IDs
- Steps: Decode each rendered data-QR crop using ZXing; compare exact VERSION=2 string. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: SPORTIFY_DEMO_QR, VERSION=2, correct PAYMENT_ID and fake BANK_CODE, MODE=NON_PAYABLE.
- Actual result: SPORTIFY_DEMO_QR, VERSION=2, correct PAYMENT_ID and fake BANK_CODE, MODE=NON_PAYABLE. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-17 - QR-04 link QR resolves to configured reachable frontend origin

- Level: Browser/accessibility
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-03
- Setup: Requires reliable SQL and an allowed reachable deployment origin. Current local SQL reads time out. Do not substitute synthetic repositories for this release gate.
- Runner/assertion: qr04-browser-check.py --backend-mode live-sql --transport native
- Input: Explicit allowlisted reachable deployment origin and rendered link QR
- Steps: Decode application-link crop and open it in another browser against the actual deployment origin. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Configured origin/path opens confirmation page. Native deployment-origin verification is BLOCKED, not proved by proxy.
- Actual result: Configured origin/path decoded and route rendered through explicit proxy; native deployment-origin reachability remains unverified.
- Evidence: cross-device-verification.md
- Run status: BLOCKED

## QR04-TC-18 - Scanning/opening URL without explicit confirm does NOT change session state

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-03
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.previewDoesNotConfirmAndSuccessNeverMutatesFinancialObjectsOrRepositories; full browser flow
- Input: Valid owner link opened before explicit confirmation
- Steps: Open link, sign in and preview; query server state before clicking Confirm. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: PENDING remains; loading/scanning/login never confirms.
- Actual result: PENDING remains; loading/scanning/login never confirms. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-19 - LAN host configuration does not embed unreachable localhost for phones

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-03
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrConfigurationTest.privateLanRequiresExplicitHttpOptInAndAnExactOrigin; evenAnExplicitAllowlistCannotEnableUnreachableLoopbackOrigins
- Input: Canonical private IPv4 HTTP with opt-in; localhost/127.0.0.1/::1 alternatives
- Steps: Run origin matrix; decode generated app link from the configured private IPv4 base. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Explicit private LAN address accepted; unreachable localhost/loopback rejected even if allowlisted.
- Actual result: Explicit private LAN address accepted; unreachable localhost/loopback rejected even if allowlisted. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-20 - Invalid or external public origin rejected, no open redirects

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Abnormal
- Requirement IDs: QR04-FR-03
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrConfigurationTest.rejectsUntrustedOriginsRedirectMaterialAndUnreachableLocalhost
- Input: External non-allowlisted host, userinfo, query, fragment, percent/backslash/dot segments
- Steps: Bind settings with each hostile base URL and start guarded configuration. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Configuration rejected; client/Host header cannot select a redirect origin.
- Actual result: Configuration rejected; client/Host header cannot select a redirect origin. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-21 - Confirmation route handles unauthenticated phone securely

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-04 / QR04-FR-07
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: demoConfirmation.test.tsx retains the fragment only in memory through inline owner sign-in; full browser phone flow
- Input: Signed-out independent context with valid token fragment
- Steps: Open link; verify fragment removed and private summary absent; sign in as owner. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Token survives only component memory; server preview appears after owner auth; no automatic confirm.
- Actual result: Token survives only component memory; server preview appears after owner auth; no automatic confirm. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-22 - Different authenticated member cannot confirm token

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Abnormal
- Requirement IDs: QR04-FR-04 / QR04-FR-07
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrControllerTest.crossOwnerHasSameResponseAsUnknownTokenAndCannotEnumerate; full browser wrong-owner flow
- Input: Valid token owned by another MEMBER
- Steps: Preview/confirm with wrong-owner bearer and compare unknown-token response. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Generic identical 404; no amount/reference/owner data; no transition.
- Actual result: Generic identical 404; no amount/reference/owner data; no transition. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-23 - Expired token denied; boundary at exact expiry timestamp

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Boundary
- Requirement IDs: QR04-FR-05 / financial invariants
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.exactExpiryBoundaryRejectsConfirmAndCannotBeRevivedByCancelOrReplay; oneInstantBeforeExpiryCanConfirmAndConfirmedIsTerminal
- Input: Injected Clock at expiresAt minus 1 ns and exactly expiresAt
- Steps: Confirm each independently created pending session at the two controlled instants. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Before boundary may confirm; exact boundary becomes EXPIRED and returns 409; terminal state cannot revive.
- Actual result: Before boundary may confirm; exact boundary becomes EXPIRED and returns 409; terminal state cannot revive. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-24 - Missing/malformed/revoked token denied

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Abnormal
- Requirement IDs: QR04-FR-04 / QR04-FR-07
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.malformedAndUnknownTokensReturnGenericDenial; demoConfirmation.test.tsx
- Input: Empty, malformed, unknown and restart-revoked token; cleared fragment on reload
- Steps: POST invalid preview/confirm; reload the consumed browser route. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Generic denial; browser requires rescan and never fabricates successful/private state.
- Actual result: Generic denial; browser requires rescan and never fabricates successful/private state. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-25 - Valid owner confirms pending session once

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-05 / financial invariants
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrControllerTest.realServiceJourneyPreviewConfirmReplayRefreshAndCancelNeverMutateFinancialRepository
- Input: Eligible owner and unexpired PENDING token
- Steps: Preview, explicitly confirm once, then GET session. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: One CONFIRMED transition; financial payment object and repositories unchanged.
- Actual result: One CONFIRMED transition; financial payment object and repositories unchanged. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-26 - Double-click / duplicate confirm is idempotent, not double-executed

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Boundary
- Requirement IDs: QR04-FR-05 / financial invariants
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: demoConfirmation.test.tsx confirms only on explicit intent and guards a second tap while the request is pending; controller replay journey
- Input: Two rapid taps and repeated same-owner confirm request
- Steps: Hold first UI request; tap again; resolve first request; replay on server. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Only one UI in-flight call; replay returns stable CONFIRMED with no second action.
- Actual result: Only one UI in-flight call; replay returns stable CONFIRMED with no second action. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-27 - Concurrent confirm vs confirm race permits at most one transition

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Boundary
- Requirement IDs: QR04-FR-05 / financial invariants
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.concurrentConfirmRequestsProduceOneFinanciallyIsolatedTerminalTransition
- Input: Eight latch-coordinated owner confirm requests for one token
- Steps: Release all workers together; await every result; inspect final state and financial interactions. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: At most one state transition, stable terminal result and zero financial writes.
- Actual result: At most one state transition, stable terminal result and zero financial writes. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-28 - Concurrent confirm vs expire race honors server clock

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Boundary
- Requirement IDs: QR04-FR-05 / financial invariants
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.expiryRacingWithAuthorizedReadWinsAtTheTransition
- Input: Clock advances to expiresAt during eligibility repository read
- Steps: Block the controlled read, advance Clock, release read and attempt final transition. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Second server-clock check denies confirmation and yields EXPIRED.
- Actual result: Second server-clock check denies confirmation and yields EXPIRED. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-29 - Confirm after `CANCELED` denied

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Abnormal
- Requirement IDs: QR04-FR-05 / financial invariants
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.canceledIsTerminalAndCancellationIdempotent
- Input: Canceled owner token
- Steps: Cancel twice; attempt confirm and preview. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Cancel remains idempotent; confirm denied with terminal CANCELED.
- Actual result: Cancel remains idempotent; confirm denied with terminal CANCELED. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-30 - Confirm after `CONFIRMED` does not change result or financial state

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Boundary
- Requirement IDs: QR04-FR-05 / financial invariants
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.oneInstantBeforeExpiryCanConfirmAndConfirmedIsTerminal; SQL success/replay case
- Input: Already CONFIRMED owner session
- Steps: Replay confirm and cancel; compare snapshot and financial fingerprints. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: CONFIRMED stays terminal; no financial changes.
- Actual result: CONFIRMED stays terminal; no financial changes. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-31 - `PENDING` session expires deterministically using injected clock

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Boundary
- Requirement IDs: QR04-FR-05 / financial invariants
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.exactExpiryBoundaryRejectsConfirmAndCannotBeRevivedByCancelOrReplay
- Input: Mutable Clock, 300-second default TTL
- Steps: Create session; advance deterministically to exact expiry; read/confirm/cancel. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Server state EXPIRED; browser timer is not the authority.
- Actual result: Server state EXPIRED; browser timer is not the authority. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-32 - New session policy invalidates or handles previous pending link safely

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Boundary
- Requirement IDs: QR04-FR-02
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.replacementCancelsPreviousLinkWithoutChangingItsBank
- Input: Same owner/payment, first bank then second bank
- Steps: Create first pending link then replacement; preview both. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Old link CANCELED with original snapshot; replacement PENDING with new selected bank.
- Actual result: Old link CANCELED with original snapshot; replacement PENDING with new selected bank. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-33 - Bank switch cannot silently reuse mismatched session snapshot

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Boundary
- Requirement IDs: QR04-FR-06 / regression / evidence
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoSessionPanel.test.tsx bank/payment transitions hide old links immediately and revoke their sessions
- Input: Pending link, then bank prop change
- Steps: Render panel, create, rerender with another bank; resolve any late create result. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Old link disappears immediately and cancellation is attempted; no mismatched bank summary.
- Actual result: Old link disappears immediately and cancellation is attempted; no mismatched bank summary. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-34 - Payment ID switch cannot reuse unrelated session

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Boundary
- Requirement IDs: QR04-FR-06 / regression / evidence
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoSessionPanel.test.tsx bank/payment transitions hide old links immediately and revoke their sessions; full browser second-payment flow
- Input: Pending link for first payment, then second authorized payment
- Steps: Navigate/change payment ID; inspect absence of old app link and decode new data QR. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Unrelated old link not reused; rendered data QR carries the new payment ID.
- Actual result: Unrelated old link not reused; rendered data QR carries the new payment ID. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-35 - Desktop manual refresh reads current demo state from backend

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-06 / regression / evidence
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoSessionPanel.test.tsx manual refresh reads CONFIRMED without changing the financial payment; full browser cross-context flow
- Input: Desktop PENDING; phone explicitly confirms on same backend
- Steps: Use desktop Refresh Demo Status after phone confirmation. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Desktop reads CONFIRMED from backend; no localStorage bridge or invented state.
- Actual result: Desktop reads CONFIRMED from backend; no localStorage bridge or invented state. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-36 - Desktop refresh does not update genuine payment status

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-06 / regression / evidence
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoSessionPanel.test.tsx manual refresh reads CONFIRMED without changing the financial payment
- Input: Server demo state CONFIRMED; genuine payment fixture PENDING
- Steps: Refresh desktop; inspect API calls and unchanged payment DTO. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Only demo read endpoint used; financial payment stays PENDING.
- Actual result: Only demo read endpoint used; financial payment stays PENDING. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-37 - Backend financial ledger/payment rows unchanged after demo confirm

- Level: SQL integration
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-05 / financial invariants
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrDatabaseIntegrationTest.successfulLiveHttpConfirmationChangesOnlyDemoStateAndEveryFinancialTableIsIdentical
- Input: Fresh isolated SQL database with seeded synthetic owner/payment and seven-table fingerprint
- Steps: Log in over HTTP; create/preview/confirm/replay; recompute all financial-table fingerprints. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Demo CONFIRMED; before/after fingerprints identical for every financial table.
- Actual result: Actual SQL success/replay left all seven financial-table fingerprints identical.
- Evidence: logs/backend-live-sql-integration-third.log
- Run status: PASS

## QR04-TC-38 - Financial rows unchanged after cancel/expire/error

- Level: SQL integration
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-05 / financial invariants
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrDatabaseIntegrationTest.crossOwnerAndCancellationAreEnforcedAcrossRealHttpRequestsWithNoFinancialWrites; exactExpiryOverRealHttpLeavesAllFinancialRowsIdentical
- Input: Wrong owner, canceled token and controlled exact-expiry token in isolated SQL fixture
- Steps: Invoke negative/terminal HTTP controls; compare seven-table fingerprints after each scenario. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Denials/terminal states correct; financial rows unchanged. All three SQL cases passed in final CI.
- Actual result: All three final-head SQL integration cases passed: success/replay, cancel/cross-owner and controlled exact expiry; seven financial table fingerprints unchanged.
- Evidence: logs/ci-summary.json; ci/40867095440/backend-integration-test-log-extract.log
- Run status: PASS

## QR04-TC-39 - Backend demo disabled even when frontend flag is force-enabled

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Abnormal
- Requirement IDs: QR04-FR-01
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrDisabledControllerTest and DemoQrProductionControllerTest
- Input: Frontend flag forced true, backend default/production config
- Steps: Authenticate and invoke every backend demo route in disabled/production context. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: 404 independent of frontend UI flag.
- Actual result: 404 independent of frontend UI flag. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-40 - REST errors do not reveal another member's payment information

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Abnormal
- Requirement IDs: QR04-FR-04 / QR04-FR-07
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrControllerTest.crossOwnerHasSameResponseAsUnknownTokenAndCannotEnumerate; tokenNeverReturnedByReadsAndInvalidBodyNeverEchoed
- Input: Cross-owner/unknown token; malformed JSON with secret-like values
- Steps: Compare exact response bodies and headers across denied requests. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Generic codes, no secret/private data echo; no-store and no-referrer.
- Actual result: Generic codes, no secret/private data echo; no-store and no-referrer. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-41 - Session store resets safely across demo server restart

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Boundary
- Requirement IDs: QR04-FR-02
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoQrServiceTest.rawTokensAreAbsentFromStoreAndDebugStringsAndRestartRevokesThem
- Input: Token created in first service instance; new service instance with empty store
- Steps: Attempt preview on the restarted instance; stop owned browser server after evidence run. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Old token rejected; destroyed process revokes screenshot link capabilities.
- Actual result: Old token rejected; destroyed process revokes screenshot link capabilities. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-42 - Broken API/timeout offline states show non-misleading UI

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Abnormal
- Requirement IDs: QR04-FR-04 / QR04-FR-07
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: DemoSessionPanel.test.tsx reports creation failure without inventing a usable QR; demoConfirmation.test.tsx; extra browser faults
- Input: Actual preview request deferred then forwarded; actual request aborted offline
- Steps: Inspect loading state; release real response; rescan and abort preview; inspect error and private summary. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: No confirm during loading; failure hides private amount and success; explicit retry/read required.
- Actual result: No confirm during loading; failure hides private amount and success; explicit retry/read required. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-43 - Keyboard-only and screen reader semantics for mobile form

- Level: Browser/accessibility
- Priority: P1
- Classification: Normal
- Requirement IDs: QR04-FR-04 / QR04-FR-07
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: qr04-browser-check.py full/extra axe and keyboard assertions
- Input: Owner sign-in/mobile actions/terminal/error states
- Steps: Tab through controls; activate explicit actions; inspect focus, roles, labels and live regions; run axe. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Keyboard/focus/labels verified; zero axe violations/incomplete. Physical screen-reader audio NOT RUN.
- Actual result: Keyboard/labels/focus/live-region/axe semantics verified. Physical screen-reader audio NOT RUN.
- Evidence: accessibility-and-visual-review.md
- Run status: PASS

## QR04-TC-44 - Responsive, themes, zoom and reduced-motion checks

- Level: Browser/accessibility
- Priority: P1
- Classification: Boundary
- Requirement IDs: QR04-FR-04 / QR04-FR-07
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: qr04-browser-check.py full/extra; tooling/check-theme-control.py
- Input: 320/390/768/1440 widths, light/dark, reduced motion, native Edge 200 percent zoom
- Steps: Capture each viewport/theme; inspect overflow/targets; verify native zoom metrics and theme icon colors. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: No overflow; usable controls; native zoom metrics recorded; icon contrast exceeds 3:1 and target is 44 by 44.
- Actual result: Four widths, both themes, reduced motion, no overflow, native 200 percent zoom and final theme icon contrast/target verified.
- Evidence: final-ui-browser/; final-ui-browser-extra/; final-ui-theme-control/results.json
- Run status: PASS

## QR04-TC-45 - Production build/config and no-dependency-on-real-bank verification

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Abnormal
- Requirement IDs: QR04-FR-01
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: Final CI security/dependency/build jobs; source mutation review; normal production browser build
- Input: Normal production mode even with frontend flag true; new source/imports/dependencies
- Steps: Build production and qr-demo; open production route; scan financial/provider imports and dependency diff. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Normal production cannot confirm; no real bank/provider integration or new dependency; separate backend guards pass.
- Actual result: Normal production cannot confirm; no real bank/provider integration or new dependency; separate backend guards pass. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-46 - QR-01/02/03 tests and currently merged Home/Member regression pass

- Level: Unit/controller/security + browser
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-06 / regression / evidence
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: Final CI frontend/unit/integration suites; baseline and current focused regressions
- Input: Latest integrated main with QR-01/02/03, Home, Member and manager/auth presentation updates
- Steps: Run full CI and focused compatibility suites; inspect preserved QR payload/helper/shared UI files. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: 1637 frontend, 1980 backend unit and 767 integration tests pass with zero failures/errors/skips.
- Actual result: 1637 frontend, 1980 backend unit and 767 integration tests pass with zero failures/errors/skips. Verified by the named source assertions and recorded automated run; hardware/deployment limitations remain as stated.
- Evidence: logs/backend-qr04-release.log; logs/frontend-qr04-rescan.log; final-ui-browser/logs/qr04-browser-results.json; final-ui-browser-extra/logs/qr04-browser-results.json; logs/ci-summary.json
- Run status: PASS

## QR04-TC-47 - Real-backend two-context demo flow; record physical phone NOT RUN if unavailable

- Level: Browser/accessibility
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-06 / regression / evidence
- Setup: Requires reliable SQL and an allowed reachable deployment origin. Current local SQL reads time out. Do not substitute synthetic repositories for this release gate.
- Runner/assertion: qr04-browser-check.py --backend-mode live-sql
- Input: Two independent real browser contexts sharing one actual SQL-backed demo server
- Steps: Desktop creates; phone-sized context signs in/previews/explicitly confirms; desktop manually refreshes; compare financial fingerprints. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Live-SQL browser flow required. Fixture-backed two-context journey passes; this fully unmocked gate is BLOCKED. Physical phone NOT RUN.
- Actual result: Actual demo state crosses contexts with declared account/payment fixtures; unmocked SQL browser and physical phone remain unverified.
- Evidence: cross-device-verification.md
- Run status: BLOCKED

## QR04-TC-48 - Actual screenshots decode both QR types as expected

- Level: Browser/accessibility
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-03
- Setup: Enable only the isolated dev/demo test server; use the known fixture owner and a legitimate pending payment, with financial fingerprints/read-only assertions available.
- Runner/assertion: qr04-browser-check.py full/extra ZXing decode checks
- Input: Actual rendered data-QR and application-link QR screenshot crops
- Steps: Decode each crop independently; compare exact V2 bank/payment content and allowlisted app-link origin/path. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: 11 final-head crops decode correctly; secret text masked and screenshot tokens revoked before handoff.
- Actual result: 11 QR crops decoded; all V2 bank payloads and separate app-link origins checked.
- Evidence: final-ui-browser/decoded-qr/; final-ui-browser-extra/decoded-qr/; final-ui-browser/logs/qr04-browser-results.json
- Run status: PASS

## QR04-TC-49 - Repo and final PR exclude audit DOCX, .env, secrets, local backups

- Level: Git/evidence
- Priority: P0
- Classification: Abnormal
- Requirement IDs: QR04-FR-06 / regression / evidence
- Setup: Use final committed source HEAD and generated local evidence. Do not stage generated evidence, audit documents or unrelated files.
- Runner/assertion: Final targeted PR diff and preservation/privacy verification
- Input: PR 167 files, index, status, audit/env/report/dependency exclusions
- Steps: Compare PR diff with 27-file allowlist; inspect Git index and every preserved hash; scan report tree for prohibited audit binaries. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Only authorized source/tests/operational docs committed; all 263 pre-existing hashes match; private audit stays local.
- Actual result: Targeted diff/privacy checks exclude audit, environment, backup, manifest and unrelated files.
- Evidence: git-and-ci-summary.md; logs/source-inventory.json
- Run status: PASS

## QR04-TC-50 - Test-case master and report copy have exactly equal SHA-256

- Level: Git/evidence
- Priority: P1
- Classification: Boundary
- Requirement IDs: QR04-FR-06 / regression / evidence
- Setup: Use final committed source HEAD and generated local evidence. Do not stage generated evidence, audit documents or unrelated files.
- Runner/assertion: Independent PowerShell Get-FileHash -Algorithm SHA256
- Input: docs/test master and report test-cases copy
- Steps: Read both final files independently; compare full SHA-256 and bytes; retain receipt. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Both complete 64-character digests equal; identical bytes.
- Actual result: Master/report copy are generated from identical bytes; independent sidecar records recomputed hashes.
- Evidence: logs/test-case-copy-sha256.json
- Run status: PASS

## QR04-TC-51 - Same-tab fragment/rescan discards the old payment summary

- Level: Git/evidence
- Priority: P0
- Classification: Boundary
- Requirement IDs: QR04-FR-04 / QR04-FR-07
- Setup: Use final committed source HEAD and generated local evidence. Do not stage generated evidence, audit documents or unrelated files.
- Runner/assertion: demoConfirmation.test.tsx consumes a new fragment in the same tab and removes the previous payment summary
- Input: Old DEMO-52 summary then new valid fragment for DEMO-53 in same mounted tab
- Steps: Navigate to new fragment; verify old summary removed before resolving new preview. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Fresh owner/token generation; new summary only; no implicit confirmation.
- Actual result: Focused rescan regression removes DEMO-52 and loads DEMO-53 without confirming either.
- Evidence: logs/frontend-qr04-rescan.log
- Run status: PASS

## QR04-TC-52 - Clipboard errors clear old success and ignore late canceled-session completion

- Level: Git/evidence
- Priority: P0
- Classification: Boundary
- Requirement IDs: QR04-FR-04 / QR04-FR-07
- Setup: Use final committed source HEAD and generated local evidence. Do not stage generated evidence, audit documents or unrelated files.
- Runner/assertion: DemoSessionPanel.test.tsx clipboard regressions; extra browser CDP permission denial
- Input: Successful copy then denied copy; late clipboard promise after cancellation
- Steps: Copy successfully; deny browser clipboard permission and retry; separately resolve held copy after cancel. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Old success cleared; accessible error shown; late completion cannot publish stale success.
- Actual result: Focused tests cover success clearing and late completion; actual browser permission denial is recorded at final HEAD.
- Evidence: logs/frontend-qr04-rescan.log; final-ui-browser-extra/
- Run status: PASS

## QR04-TC-53 - Capacity overflow and deterministic terminal cleanup are enforced

- Level: Git/evidence
- Priority: P0
- Classification: Boundary
- Requirement IDs: QR04-FR-05 / financial invariants
- Setup: Use final committed source HEAD and generated local evidence. Do not stage generated evidence, audit documents or unrelated files.
- Runner/assertion: DemoQrServiceTest.boundedStoreRejectsExcessSessionsAndCleansOldTerminalEntries
- Input: 200 owners times five creates, next owner create, then Clock plus 901 seconds
- Steps: Fill 1000 slots; try overflow; advance controlled clock and retry. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Overflow denied with capacity error; old terminal/expired slots cleaned and new create succeeds.
- Actual result: 1000-session bound rejects excess use and controlled-clock cleanup recovers capacity.
- Evidence: logs/backend-qr04-release.log
- Run status: PASS

## QR04-TC-54 - Private audit DOCX is rendered and all pages visually verified

- Level: Git/evidence
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-06 / regression / evidence
- Setup: Use only docs/audit for source, backup, draft, tools and rendering output. Keep original document byte-identical.
- Runner/assertion: Private document render workflow
- Input: Updated private DOCX and preserved original/backup
- Steps: Run canonical renderer; if available rasterize PDF and visually inspect every page; record exact tool failures privately. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: Render plus PNG review required. Zero pages available: BLOCKED and UNVERIFIED, never fabricated PASS.
- Actual result: Private DOCX updated as UNVERIFIED; zero rendered pages; original preserved.
- Evidence: logs/audit-verification-status.json
- Run status: BLOCKED

## QR04-TC-55 - Required GitHub CI passes on the final source HEAD

- Level: Git/evidence
- Priority: P0
- Classification: Normal
- Requirement IDs: QR04-FR-06 / regression / evidence
- Setup: Use final committed source HEAD and generated local evidence. Do not stage generated evidence, audit documents or unrelated files.
- Runner/assertion: gh pr checks 167 and exact-head workflow run 40867095440
- Input: PR head 6591c88cdaba811fe3e87c9bb39231f6df4daf5d
- Steps: Compare local/remote/PR HEAD; collect actual job/test logs; check required CI gate and branch rules. Retain the result; compare demo state and unchanged financial state where relevant.
- Expected result: All 15 workflow jobs and required CI gate succeed on exact source HEAD; no self-approval or merge.
- Actual result: Final required checks are reported only from the exact-head GitHub snapshot.
- Evidence: logs/ci-summary.json
- Run status: PASS
