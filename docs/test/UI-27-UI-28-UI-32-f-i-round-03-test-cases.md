# UI-27 UI-28 UI-32 Round 03 executable test cases

Status: RE-VERIFIED in Round 03. Snapshot: 2026-10-28T11:00:49.985308+00:00.

Branch: fix/ui-27-28-32-home-alignment. HEAD: 5cd6c7762e80ba3baae31660ad916df61d8b9af1. Base: e00686b1280212a6715670ebca5c26be70e0397f.
PR: https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/158. CI: https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/runs/40652587981.
Source changes: NONE. NO CODE CHANGE REQUIRED. Independent CODEOWNER approval and manual merge pending.

This requested repository-level file is a local, untracked verification deliverable. The ignored/private
audit DOCX is never staged. No source fix or approved tracked-document change justifies a new PR commit.
Existing docs/tests/FE-11_test_cases.md and FE-12_test_cases.md are preserved. The current PR technical
specification remains at D:\G3-SportCentreManagement\SWP-ui-27-28-32\frontend\docs\UI-27-UI-28-UI-32-home-alignment-test-cases.md.

Preconditions: Edge/Chromium156, approved unchanged source, production preview5191 and same-source dev
catalogue5187; synthetic seeded roles and intercepted API endpoints. Financial tests are FIXTURE/MOCK,
not live backend transactions. No Docker or live payment/refund is used. Executed means RE-EXECUTED IN
ROUND 03; archive-only checks are explicitly distinguished below. Reflow720 is separate from native zoom.

Owned targets: UI-27 /member/payments/:paymentId including53 single visit; UI-28 corresponding /status;
UI-32 /reception/refunds and /reception/refunds/:refundId?memberId=2. The two isolation routes are excluded
from redesign. A retained Appearance select there is not a failed target conversion.

Evidence root: D:\G3-SportCentreManagement\SWP\tasks\UI-27, UI-28, UI-32 (f) (i)\reports\integration-f-i\round-03. Each short evidence filename below resolves there. Defect/status details for
the failed exploratory probe and offline backend are in the Round 03 visual-and-functional review.

| ID | UI/scope | Precondition | Role | Route | Steps | Expected result | Mode | Viewport/theme | Result | Execution | Evidence/defect |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R3-001 | Repository | Authenticated gh; clean PR worktree | Read-only | PR158/main | Fetch; compare HEAD/base/diff/status | Open exact accepted HEAD; main ancestor; same 22 files | Git/GitHub | N/A | PASS | RE-EXECUTED | repository-state.json |
| R3-002 | UI-27/28/32 Brand | Production preview; role fixtures | Member/Reception | Owned routes below | Compare compact DOM/font/geometry with current Home | Exact Brand; no subtitle; correct 96/72px mark and 30/22px wordmark | FIXTURE/MOCK | All 3 sizes, both themes | PASS | RE-EXECUTED | UI-27-UI-28-UI-32-f-i-round-03-computed-styles.json |
| R3-003 | Header theme | Shared ThemeProvider; live Spring Boot; documented local demo accounts | Member/Reception | Owned routes | Inspect toggle; press Space; navigate Home/Back; reload; change OS | Shared sun/moon; no select; persisted explicit/system semantics | FIXTURE/MOCK initially; REAL BACKEND separately revalidated | All 3 sizes, both themes | PASS | RE-EXECUTED - fixture and real runs are separate | parity.log (fixture); real-backend-validation/results.json (live) |
| R3-004 | Control parity | Approved Home/shared analogues | Member/Reception | Owned routes | Compare actual default/hover/focus/disabled values | 516 direct comparisons; no mismatches; correct semantic exceptions | FIXTURE/MOCK | All 3 sizes, both themes | PASS | RE-EXECUTED | UI-27-UI-28-UI-32-f-i-round-03-computed-styles.json |
| R3-005 | Parity negative controls | Test-only DOM mutation | Member/Reception | Owned routes | Inject legacy select; wrong CTA radius/color; run real predicates; restore | Mutations rejected for all 3 UIs; no mutation in source | FIXTURE/MOCK | Desktop/light | PASS | RE-EXECUTED | UI-27-UI-28-UI-32-f-i-round-03-computed-styles.json |
| R3-006 | UI-27 summary totals | Standard payment52 fixture | MEMBER | /member/payments/52 | Run memberPayments suite; inspect amount breakdown | Original totals/discounts and correct linked payment remain correct | Unit FIXTURE | Desktop/tablet/mobile | PASS | RE-EXECUTED | focused-tests.json |
| R3-007 | UI-27 single visit | Single payment53 fixture | MEMBER | /member/payments/53 | Open single visit; verify summary and options | Single Visit Payment Summary; correct single-session data | Browser FIXTURE | Mobile/light | PASS | RE-EXECUTED | payment-browser-after.json |
| R3-008 | UI-27 method/Continue | Pending payment; unavailable wallet options | MEMBER | /member/payments/52 | Inspect disabled methods; keyboard reach Continue; activate | Unavailable methods disabled; Continue reaches52/status | Browser FIXTURE | All 3 sizes/themes plus reflow720 | PASS | RE-EXECUTED | payment-browser-after.json |
| R3-009 | UI-27 states | Invalid/loading/service-error fixtures | MEMBER | /member/payments/invalid or52 | Open each state and retry path | Accessible error/empty/loading; no wrong payment amounts | FIXTURE/MOCK | Mobile; unit matrix | PASS | RE-EXECUTED | full-tests.json |
| R3-010 | UI-28 transfer/copy | Pending bank transfer | MEMBER | /member/payments/52/status | Copy transfer content; read real browser clipboard | Clipboard exactly equals displayed content; feedback available | Browser FIXTURE | All 3 sizes/both themes | PASS | RE-EXECUTED | UI-27-UI-28-UI-32-f-i-round-03-computed-styles.json |
| R3-011 | UI-28 polling | Pending then SUCCESS fixture | MEMBER | /member/payments/52/status | Refresh to SUCCESS; wait full15.5s; inspect request count | Confirmed state; no further polling after completion | Browser FIXTURE | Desktop/light; unit cleanup tests | PASS | RE-EXECUTED | payment-browser-after.json |
| R3-012 | UI-28 failure/empty/success | Service and invalid-ID variants | MEMBER | /member/payments/:paymentId/status | Run memberPayments suite and browser state matrix | Correct state, navigation, retry and terminal behavior | FIXTURE/MOCK | All 3 sizes/both themes | PASS | RE-EXECUTED | focused-tests.json |
| R3-013 | UI-32 member/queues/table | Member2 and approved refund11 | RECEPTIONIST | /reception/refunds?memberId=2 | Search/select member; open queue; keyboard-scroll table | Correct member context; named scroll region; visible pinned actions; empty queues fit | Browser FIXTURE | All 3 sizes/both themes | PASS | RE-EXECUTED | payment-browser-after.json |
| R3-014 | UI-32 form/eligibility/caps | Quote and ineligible/over-balance variants | RECEPTIONIST | /reception/refunds?memberId=2&registrationId=1052 | Submit missing reason; fill valid reason; run TC-FE12-041 | Required reason; invalid/capped quote cannot POST; one valid submission | Browser/unit FIXTURE | All 3 sizes/both themes | PASS | RE-EXECUTED | focused-tests.json |
| R3-015 | UI-32 payout geometry | Approved refund11 | RECEPTIONIST | /reception/refunds/11?memberId=2 | Verify checkbox; open confirmation; compare real Home informational Modal | Approved16px outer radius;32px close;40px/8px shared actions | Browser FIXTURE | All 3 sizes/both themes | PASS | RE-EXECUTED | UI-27-UI-28-UI-32-f-i-round-03-computed-styles.json |
| R3-016 | UI-32 modal accessibility | Open payout confirmation | RECEPTIONIST | /reception/refunds/11?memberId=2 | Tab/Shift+Tab; Escape; reopen and Cancel | Focus trapped; correct names; trigger focus restored; no cancellation PUT | Browser FIXTURE | All 3 sizes/both themes | PASS | RE-EXECUTED | payment-browser-after.json |
| R3-017 | UI-32 payout safety | Approved fixture; completion endpoint intercepted | RECEPTIONIST | /reception/refunds/11?memberId=2 | Confirm once; inspect completion requests and existing busy/loading guards | Exactly one PUT for confirmed action; disabled/loading and existing idempotency contracts retained | FIXTURE/source review | All 3 sizes/both themes | PASS | RE-EXECUTED | focused-tests.json |
| R3-018 | Auth/role/ownership | Existing fake backend role variants | Member/Reception/other | Role routes | Run complete suite; inspect unchanged API adapters/contracts | Existing guards,403 ownership handling and request contracts preserved | Unit FIXTURE/source review | N/A | PASS | RE-EXECUTED | full-tests.json |
| R3-019 | Responsive/accessibility | Role fixtures; native Chromium | Member/Reception | Owned routes | Check overflow/header/Menu/focus/contrast/hit target | No page overflow/collision; Menu Escape works; theme44px; accessible names | Browser FIXTURE | 1440x900;768x1024;390x844; light/dark | PASS | RE-EXECUTED | parity.log |
| R3-020 | Actual200% zoom | Edge Settings isolated temporary profiles | Guest/Member/Reception | Home and owned routes | Set browser default zoom2; inspect metrics and modal fit | Physical1440x900 becomes CSS720x450; DPR2; scale1; no overflow;8 cases pass | Browser FIXTURE/native zoom | Both themes | PASS | RE-EXECUTED | native-zoom-results.json |
| R3-021 | Member history isolation | Identical fixtures on immutable Round01 and current build | MEMBER | /member/payments | Capture pair at each size/theme; compare exact pixels | 6/6 pairs identical; legacy styling intentionally preserved | Browser FIXTURE | All 3 sizes/both themes | PASS | RE-EXECUTED | isolation-and-raster.json |
| R3-022 | Reception payment isolation | Identical fixtures on immutable Round01 and current build | RECEPTIONIST | /reception/payments | Capture pair at each size/theme; compare exact pixels | 6/6 pairs identical; no redesign requested | Browser FIXTURE | All 3 sizes/both themes | PASS | RE-EXECUTED | isolation-and-raster.json |
| R3-023 | Home reference | Approved main source; same role/data state | GUEST | / | Verify source equality, component style refs and actual after evidence | No target-induced Home source/geometry/brand regression; no redesign | Source/browser FIXTURE | All 3 sizes/both themes | PASS | RE-EXECUTED | home-same-source-controls.json |
| R3-024 | Broad Home diagnostic | All-node used-margin snapshot | GUEST | / | Run broad and same-source controls without relaxing assertions | Strict raw equality may fail; retain exact values; classify with geometry and identical-source evidence | Exploratory FIXTURE | Desktop dark primarily | FAIL | RE-EXECUTED; diagnostic only | home-same-source-controls.json |
| R3-025 | Real backend connectivity | No credentials; no financial mutation | None | GET http://127.0.0.1:8080/api/v1/sports | Perform safe read-only GET | Connection available to verify live read-only path | Real backend read-only | N/A | PASS | RE-EXECUTED LIVE; earlier offline result retained as history | real-backend-validation/results.json; initial real-backend-probe.json is historical |
| R3-026 | Manual screen-reader speech | Assistive technology session | Member/Reception | Owned routes | Manually listen through screens/dialog | Speech order and announcements reviewed by human | Manual | N/A | NOT RUN | NOT RUN | No manual speech session; DOM names/keyboard tested separately |
| R3-027 | Frontend quality | Installed repo toolchain | Developer | frontend | Run full/focused/lint/tsc/build commands below | 1495 full and233 focused pass; lint/type/build exit0 | Unit/build | N/A | PASS | RE-EXECUTED | full-tests.json |
| R3-028 | Current CI/merge status | Authenticated gh; unchanged HEAD/base | Read-only | PR158 | Query actual checks/rules/reviews; verify SHA and freshness | 16 successes; required CI gate success; CODEOWNER approval remains pending | GitHub current status; existing run | N/A | PASS | RE-VERIFIED existing exact-HEAD run | current-pr.json |
| R3-029 | Audit DOCX | Backup and exclusive current-file hash guard | Local private | docs/AI_Audit_Log.docx | Append entry; open with python-docx and Word; render and compare history | 40 prior entries plus header preserved; all 20 prior rendered pages identical; metadata unchanged | Local document verification | 21-page final document | PASS | RE-EXECUTED | audit-append-verification.json |
| R3-030 | Archive integrity | Round 02 manifest and originals | Read-only | All archived evidence | Verify every declared image SHA256 and existence | 427/427 archived image records intact; before/after/isolation labels preserved | Local evidence verification | All archived sizes/themes | PASS | RE-VERIFIED archive; execution Round 02 | archive-verification.json |

## Reproduction commands

Run from D:/G3-SportCentreManagement/SWP-ui-27-28-32/frontend.

```powershell
npm test -- --reporter=default --reporter=json --outputFile='D:/G3-SportCentreManagement/SWP/tasks/UI-27, UI-28, UI-32 (f) (i)/reports/integration-f-i/round-03/full-tests.json'
npm test -- src/pages/member/memberPayments.test.tsx src/pages/reception/receptionPayments.test.tsx src/pages/public/HomePage.test.tsx src/layouts/PaymentWorkspaceHeader.test.tsx src/theme/ThemeToggle.test.tsx src/theme/theme.test.tsx src/components/ui/ModalScrollLock.test.tsx src/components/ui/components.test.tsx src/styles/rawColors.test.ts --reporter=default --reporter=json --outputFile='D:/G3-SportCentreManagement/SWP/tasks/UI-27, UI-28, UI-32 (f) (i)/reports/integration-f-i/round-03/focused-tests.json'
npm run lint
npx tsc -b
npm run build -- --outDir 'D:/G3-SportCentreManagement/SWP/tasks/UI-27, UI-28, UI-32 (f) (i)/reports/integration-f-i/round-03/production-build'
python 'D:/G3-SportCentreManagement/SWP/tasks/UI-27, UI-28, UI-32 (f) (i)/reports/integration-f-i/round-03/helpers/payment-home-check.py' --round round-03 --parity --base http://127.0.0.1:5191 --reference-dev-base http://127.0.0.1:5187 --out 'D:/G3-SportCentreManagement/SWP/tasks/UI-27, UI-28, UI-32 (f) (i)/reports'
python 'D:/G3-SportCentreManagement/SWP/tasks/UI-27, UI-28, UI-32 (f) (i)/reports/integration-f-i/round-03/helpers/functional.py' --round round-03 --base http://127.0.0.1:5191 --out 'D:/G3-SportCentreManagement/SWP/tasks/UI-27, UI-28, UI-32 (f) (i)/reports'
python 'D:/G3-SportCentreManagement/SWP/tasks/UI-27, UI-28, UI-32 (f) (i)/reports/integration-f-i/round-03/helpers/payment-native-zoom-check.py' --base http://127.0.0.1:5191 --out 'D:/G3-SportCentreManagement/SWP/tasks/UI-27, UI-28, UI-32 (f) (i)/reports'
python 'D:/G3-SportCentreManagement/SWP/tasks/UI-27, UI-28, UI-32 (f) (i)/reports/integration-f-i/round-03/isolation-and-raster.py'
```

The quoted absolute paths resolve to the task reports directories given in the review index. Exact
local evidence adapters and diffs are retained beside the logs. Assertions and financial fixtures
remain unchanged; adapters route evidence to Round 03 and reuse byte-identical original captures.
Production Home/targets use 5191; only the existing development-only shared widget catalogue uses 5187.
All 44 fresh build files and index.html match the served build byte for byte. Six obsolete hashed files
remain inert in the preserved outside-root Round 02 build archive. Initial full-directory equality
assertion failed on those stale files; build-integrity.json documents the precise distinction.


## CODEOWNER additional requirements

Updated 2026-10-28T11:38:22.102906+00:00. Master remains this docs/test file. Identical completed copies are placed in the
integration-f-i and each UI-27/UI-28/UI-32 round-03 report directory, with SHA-256 equality verified in
integration-f-i/round-03/test-case-copy-sha256.json. Audit DOCX, backups and all private render derivatives
remain exclusively under the local repository docs directory and are excluded from shared reports/Git.

### R3-003 / R3-03 real backend revalidation

Result: PASS. This is a new REAL BACKEND execution, not the previous FIXTURE/MOCK run.
Spring Boot health and sports endpoints returned 200. Member and Reception authenticated through the
actual login UI and server-issued sessions; no response was mocked. There were 110
passing checks and 71 observed live responses. Only authentication writes and GETs
were permitted; no financial write was attempted. The guard aborted no request because none was unsafe.

At 1440x900, 768x1024 and 390x844, verify sun/moon names, keyboard Space/Enter, localStorage persistence,
Home navigation, Back/reload, system preference and explicit preference after OS changes. Both themes
passed for all 3 target headers. Real logout/login preserved the preference across Member/Reception.
Theme is client-side state; no nonexistent server theme API is claimed.

Member payment routes deliberately use absent ID 2147483647, receiving genuine 404 responses. Reception
uses the real refund queue route. No populated financial workflow, amount calculation or payout was
re-executed against live data. Those remain separately FIXTURE/MOCK-verified. Screenshots contain only
theme icons; tokens, passwords and profile/financial bodies are not exported.

Reproduce from the dedicated frontend worktree with the approved production build served at
http://localhost:5173 (an existing allowed backend origin), and the existing backend at localhost:8080:

```powershell
python 'D:/G3-SportCentreManagement/SWP/tasks/UI-27, UI-28, UI-32 (f) (i)/reports/integration-f-i/round-03/real-backend-validation/validate-r3-003.py'
```

The script reads documented demo account settings from the local README, uses isolated browser
contexts and writes sanitized evidence only. It never registers accounts, seeds data or calls a
financial mutation. No secret values need to be printed or supplied on the command line.

First live attempt: FAIL in the test setup when system-theme context cloning omitted sessionStorage
authentication. Final attempt: PASS after selecting the application's real Keep me logged in option.
No UI code, assertion threshold or backend setting changed. The failed attempt is retained separately.
Manual screen-reader speech remains NOT RUN. Earlier fixture warnings, Home raster diagnostic warnings
and the build advisory remain documented; the earlier backend-offline statement is superseded here.
