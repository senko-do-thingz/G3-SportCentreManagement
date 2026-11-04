# QR01 round01 executable test cases

Date: 2026-10-28T13:12:55.810408+00:00. Owner: Senko (Dat). Task: QR-01 (i), round-01.
Approved main: `608a0f9ac1cd83beaaf5c46378ac66016e0c06bb`. Final HEAD: `385c2b6a5385fa9d142d818fc7661eab4edde55e`.
Branch: `feat/qr-01-demo-qr-card`. Implementation checkout: `D:\G3-SportCentreManagement\SWP-qr-01`.
PR: https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/160. CI: https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/runs/40691512011.

## Setup and commands

Use the isolated checkout, not unrelated original work. Follow the explicit enablement and three preview commands in the implementation/test-results reports. Install browser QA requirements with `python -m pip install -r scripts/requirements-qr01-check.txt`. Run `python scripts/qr01-browser-check.py --base http://127.0.0.1:5195 --normal-base http://127.0.0.1:5196 --before-base http://127.0.0.1:5194 --out <reports>` from frontend. For focused/full checks use the exact commands and exit codes in QR-01-i-round-01-test-results.md. `<reports>` is the round-01 task report directory.

All populated payment records and browser sessions are explicit FIXTURE/MOCK data. The supplemental live helper performs auth/read-only operations only. Never make a financial mutation to populate a test. No banking app or production account is needed. Source-test paths below are relative to frontend; evidence paths are relative to the report directory.

## QR01-001

- Role: MEMBER.
- Route: `/member/payments/52`.
- Setup: Explicit demo build; pending fixture amount 810000.
- Steps: Open the summary; inspect enabled Bank transfer, disabled existing wallets, primary Continue and separate demo entry.
- Expected: Existing options and primary CTA remain; demo entry is visible only for pending payment in enabled demo scope.
- Actual: Entry present in all six viewport/theme pairs; MoMo remains disabled; terminal statuses hide entry.
- Result: PASS.
- Execution mode: FIXTURE/MOCK.
- Evidence: logs/browser-results.json; logs/focused-tests.json.

## QR01-002

- Role: MEMBER.
- Route: `/member/payments/52 -> /member/payments/52/qr-demo`.
- Setup: Same fixture; record every API method.
- Steps: Tab to the demo Link; verify focus ring; press Enter; return via Back.
- Expected: Scoped card opens and Back works without submitting method or mutating payment.
- Actual: Card opened and summary restored; only GET/OPTIONS observed; real Continue remained enabled.
- Result: PASS.
- Execution mode: FIXTURE/MOCK.
- Evidence: logs/browser-results.json; logs/full-tests-final.json.

## QR01-003

- Role: MEMBER.
- Route: `/member/payments/52/qr-demo`.
- Setup: Inject synthetic private bank fields; enable screenshot decoder.
- Steps: Decode actual SVG screenshots; test negative/fractional/unsafe/injection IDs; inspect payload grammar.
- Expected: Exact SPORTIFY_DEMO_QR NON_PAYABLE plain text; no bank account, amount, URL, EMV or member data.
- Actual: 8/8 symbols decoded exact fixed grammar. Unsafe inputs rejected. No private fixture fields shown.
- Result: PASS.
- Execution mode: UNIT plus FIXTURE/MOCK.
- Evidence: logs/browser-results.json; src/components/qr/demoQr.test.ts.

## QR01-004

- Role: MEMBER.
- Route: `/member/payments/52/qr-demo`.
- Setup: Set authorized fixture ID52 amount731234 and unrelated price/discount.
- Steps: Render card; inspect displayed ID/amount and fake labels.
- Expected: Uses DTO ID52 and 731.234 VND, without price recalculation; fake account only.
- Actual: DTO authority unit integration passes; browser fixture amount810000 displays 810.000 VND and ID52.
- Result: PASS.
- Execution mode: UNIT plus FIXTURE/MOCK.
- Evidence: logs/full-tests-final.json; screenshots/QR-01-i-round-01-after-light-desktop-1440-demo-card-fixture.png.

## QR01-005

- Role: ANONYMOUS, RECEPTIONIST, MEMBER.
- Route: `/member/payments/52/qr-demo`.
- Setup: Use no session, Reception role, or protected API403 respectively.
- Steps: Navigate directly; inspect payment requests, private data and QR.
- Expected: Existing role/login guards remain; denied Member read shows safe error without payment data.
- Actual: No payment request for anonymous/Reception; 403 error has no QR or amount. Real cross-member record is not exercised.
- Result: PASS.
- Execution mode: FIXTURE/MOCK.
- Evidence: logs/browser-results.json; screenshots/QR-01-i-round-01-after-dark-mobile-390-forbidden-fixture.png.

## QR01-006

- Role: MEMBER.
- Route: `/member/payments/invalid/qr-demo and /member/payments/52/qr-demo`.
- Setup: Invalid IDs, stalled loading,503,404,mismatch,malformed amount,terminal states.
- Steps: Navigate in each mode; wait for state; inspect no QR; use safe navigation/retry control where available.
- Expected: Meaningful loading/error/empty states; invalid IDs do not fetch payment; no false success.
- Actual: All final unit/browser checks pass; six state captures and two invalid-ID captures exist.
- Result: PASS.
- Execution mode: UNIT plus FIXTURE/MOCK.
- Evidence: logs/browser-results.json; logs/full-tests-final.json.

## QR01-007

- Role: MEMBER.
- Route: `/member/payments/52 and /member/payments/52/qr-demo`.
- Setup: 1440x900,768x1024,390x844; light and dark.
- Steps: Open entry/card in each condition; inspect layout and overflow; decode QR; repeat native browser200%.
- Expected: No horizontal overflow or cropped QR; visible labels; actual QR sharp/scannable.
- Actual: Six paired sizes/themes pass; native zoom CSS720x450/DPR2 in both themes fits and decodes.
- Result: PASS.
- Execution mode: FIXTURE/MOCK real Chromium.
- Evidence: logs/browser-results.json; screenshots/QR-01-i-round-01-after-dark-mobile-390-demo-card-fixture.png.

## QR01-008

- Role: MEMBER keyboard user.
- Route: `/member/payments/52 and /member/payments/52/qr-demo`.
- Setup: Real Chromium; computed contrast helper.
- Steps: Tab/Enter entry; inspect focus, h1/h2, SVG title/description and visible warnings; measure effective text colors.
- Expected: Keyboard usable; meaningful non-payable description; text contrast at least4.5:1.
- Actual: Visible focus/Enter/back checks pass;12 text samples pass, minimum4.984:1. Full screen-reader certification not performed.
- Result: PASS.
- Execution mode: FIXTURE/MOCK plus computed styles.
- Evidence: logs/contrast-results.json; logs/browser-results.json.

## QR01-009

- Role: MEMBER.
- Route: `/; /member/payments/52/status`.
- Setup: Approved main build and final QR build; bank-transfer fixture.
- Steps: Compare shared Brand/theme computed values to Home; toggle/reload/navigate; open UI-28.
- Expected: Canonical brand/theme unchanged and persistent; existing bank-transfer status remains functional.
- Actual: 18 Home comparisons match; six theme persistence checks pass; existing UI-28 screen captured.
- Result: PASS.
- Execution mode: FIXTURE/MOCK plus regression unit.
- Evidence: logs/browser-results.json; screenshots/QR-01-i-round-01-after-light-mobile-390-existing-ui-28-fixture.png.

## QR01-010

- Role: MEMBER.
- Route: `/member/payments/52/qr-demo`.
- Setup: Intercept/record all network; separately serve normal production with flagtrue.
- Steps: Open demo, inspect requests and decoded text; try direct route/entry in normal production.
- Expected: No bank/service call or financial mutation; ordinary production hides entry and refuses payment read on direct demo route.
- Actual: All fixture API methods GET/OPTIONS; no payment provider host requested; production guard passes despite flagtrue.
- Result: PASS.
- Execution mode: FIXTURE/MOCK actual production builds.
- Evidence: logs/browser-results.json; screenshots/QR-01-i-round-01-after-light-mobile-390-normal-production-disabled-fixture.png.

## QR01-011

- Role: DEVELOPER.
- Route: `frontend directory`.
- Setup: Install pinned lockfile; use documented Node/npm.
- Steps: Run focused/full tests, ESLint, TypeScript, normal/demo builds and dependency audit; inspect final CI HEAD.
- Expected: Required checks succeed without weakening tests.
- Actual: 160 focused and1524 full tests pass; lint/TS/build/audit exit0. CI status in dedicated report.
- Result: PASS.
- Execution mode: LOCAL COMMANDS plus GITHUB ACTIONS.
- Evidence: QR-01-i-round-01-test-results.md; QR-01-i-round-01-ci-report.md.

## QR01-012

- Role: DEVELOPER.
- Route: `docs/test and local docs audit`.
- Setup: Completed master; local audit backup and render QA.
- Steps: Copy test Markdown bytes to report test-cases; compareSHA256; verify audit remains exclusively under docs and absent from Git/reports.
- Expected: Both Markdown hashes equal; audit append verified privately; no audit artifacts in report or commit.
- Actual: SHA256 proof records byte equality; audit verification status is PASS.
- Result: PASS.
- Execution mode: LOCAL DOCUMENTATION.
- Evidence: logs/test-case-copy-sha256.json; logs/audit-verification-status.json.

## QR01-LIVE01

- Role: actual demo MEMBER.
- Route: `http://localhost:8080/api/v1`.
- Setup: Existing local Spring Boot backend and documented development account; no financial writes.
- Steps: Run sanitized read-only helper; authenticate, read self, absent payment and pending list; never create a payment.
- Expected: Live auth/read contracts verified; populated card only if an authorized pending record already exists.
- Actual: 5 final requests pass; pendingCount0; financialWrites0. Populated live card NOT RUN, backend runtime SHA not attested.
- Result: PARTIAL.
- Execution mode: LIVE BACKEND auth/read-only; not live financial processing.
- Evidence: logs/live-read-only.json; logs/live-read-only-final.log.

## Copy integrity and privacy

The original completed Markdown is stored under local docs/test. A byte-identical copy is placed under reports/round-01/test-cases. SHA-256 values and file lengths are recorded in logs/test-case-copy-sha256.json after writing both files. The audit document and every audit backup, PDF and page image remain local under docs, excluded from Git and this report. This Markdown does not reproduce audit history.
