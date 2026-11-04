# QR-04 (i) Round 02 detailed test cases

Application and PR HEAD: `12cc8b70855567bcdc9532ceb263e5111f8c48a3`. Verified integrated main: `c6358dc5a770bc68e72da43f31cf397f5071965e`. Round 02, 2026-10-29.

Environment: Windows PowerShell, real Edge, native Vite qr-demo development mode and real HTTP/CORS Java controller/service with synthetic authentication/payment repositories unless a case explicitly states CI real SQL. All data is dedicated synthetic demo data. This is not a live SQL browser PASS.

Case status totals: 45 PASS, 1 FAIL, 2 BLOCKED, 2 NOT RUN.

The master and identical report copy are retained locally. The master is untracked, not falsely described as ignored by project policy. Source unit tests are committed in PR #167. Hashes are in test-cases/sha256-verification.json. Physical device and audio coverage are not inferred from emulation.

## QR04-R2-TC-001 - Safe working-tree inventory, no user modifications lost

- ID: QR04-R2-TC-001
- Requirement: Safe working-tree inventory, no user modifications lost.
- Priority: P0
- Normal/boundary/abnormal: abnormal
- Preconditions: Existing modified manifests, ignored evidence, private audit and unrelated untracked files.
- Test steps: Inventory Git and 1593 file hashes before switching. Back up both manifests privately. Rehash after implementation and compare the private audit backup.
- Expected: All unrelated files unchanged; audit append has a matching original backup.
- Actual: 1592 identical files; one authorized audit append backed up; zero missing files.
- Status: PASS
- Evidence: source-review/final-preservation-and-scope.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3; baseline 6591c88cdaba811fe3e87c9bb39231f6df4daf5d; main c6358dc5a770bc68e72da43f31cf397f5071965e
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-002 - Local main fast-forward exactly matches fetched origin/main

- ID: QR04-R2-TC-002
- Requirement: Local main fast-forward exactly matches fetched origin/main.
- Priority: P0
- Normal/boundary/abnormal: normal
- Preconditions: Primary checkout; safe switch with existing manifest edits retained.
- Test steps: Fetch main; switch main; pull --ff-only origin main; fetch again; compare both SHAs.
- Expected: Local main equals fetched remote before application edits.
- Actual: Both equal c6358dc5a770bc68e72da43f31cf397f5071965e at 12:36:47 UTC.
- Status: PASS
- Evidence: source-review/main-sync.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3; baseline 6591c88cdaba811fe3e87c9bb39231f6df4daf5d; main c6358dc5a770bc68e72da43f31cf397f5071965e
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-003 - Existing PR branch includes verified latest main commits

- ID: QR04-R2-TC-003
- Requirement: Existing PR branch includes verified latest main commits.
- Priority: P0
- Normal/boundary/abnormal: normal
- Preconditions: Actual PR branch read from GitHub metadata.
- Test steps: Switch back to feat/qr-04-demo-session-confirmation. Check merge-base --is-ancestor and incoming diff. Fetch again before final handoff.
- Expected: Latest main ancestor; normal integration if needed; no history rewrite.
- Actual: Main already ancestor of 6591c88cdaba811fe3e87c9bb39231f6df4daf5d; no conflicts or redundant merge; final fetch unchanged.
- Status: PASS
- Evidence: source-review/main-sync.json; ci-evidence/final-remote-head.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3; baseline 6591c88cdaba811fe3e87c9bb39231f6df4daf5d; main c6358dc5a770bc68e72da43f31cf397f5071965e
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-004 - Baseline compares approved Home branding against QR-04

- ID: QR04-R2-TC-004
- Requirement: Baseline compares approved Home branding against QR-04.
- Priority: P1
- Normal/boundary/abnormal: normal
- Preconditions: Fresh pre-correction compiled source, not old assumed design.
- Test steps: Open Home and missing-token phone route in Edge at 390px in both themes. Compare computed mark radius, typeface and wordmark transform.
- Expected: Record actual Home authority and any divergence before correction.
- Actual: Home square italic white mark/uppercase wordmark; phone rounded normal Segoe UI mark and mixed-case wordmark.
- Status: PASS
- Evidence: source-review/baseline-brand.json; screenshots/before-round-01/
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3; baseline 6591c88cdaba811fe3e87c9bb39231f6df4daf5d; main c6358dc5a770bc68e72da43f31cf397f5071965e
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-005 - Old logo root cause documented by path and state

- ID: QR04-R2-TC-005
- Requirement: Old logo root cause documented by path and state.
- Priority: P1
- Normal/boundary/abnormal: abnormal
- Preconditions: Pre-correction source 6591c88cdaba811fe3e87c9bb39231f6df4daf5d.
- Test steps: Inspect DemoConfirmationPage.tsx lines 18 and 39 and QrPaymentDemoPage.tsx line 20. Trace generic NotFoundPage to StatusPage and scoped CSS.
- Expected: Every old appearance traced to its actual composition.
- Actual: Three sources identified: phone common wrapper, phone disabled generic status, desktop disabled duplicate generic status.
- Status: PASS
- Evidence: 02-branding-root-cause-and-source-of-truth.md
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3; baseline 6591c88cdaba811fe3e87c9bb39231f6df4daf5d; main c6358dc5a770bc68e72da43f31cf397f5071965e
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-006 - QR desktop default bank uses current approved logo

- ID: QR04-R2-TC-006
- Requirement: QR desktop default bank uses current approved logo.
- Priority: P1
- Normal/boundary/abnormal: normal
- Preconditions: Owner fixture login; payment 1; Vietcombank.
- Test steps: Open /member/payments/1/qr-demo at 390, 768 and 1440px in light and dark. Inspect brand and two QR categories.
- Expected: Single approved square mark/italic uppercase wordmark; DEMO notices visible.
- Actual: All six primary Vietcombank images and computed brand checks pass.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json; screenshots/screenshot-manifest.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-007 - QR desktop other three banks retain approved logo

- ID: QR04-R2-TC-007
- Requirement: QR desktop other three banks retain approved logo.
- Priority: P1
- Normal/boundary/abnormal: normal
- Preconditions: Owner fixture; authorized payment 1.
- Test steps: Select BIDV, Techcombank and MB. Start each link session. Review each bank at 390, 768 and 1440px in both themes.
- Expected: Brand consistent; selected demo bank and data QR update.
- Actual: 18 primary bank screenshots use approved brand with no overflow.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json; screenshots/screenshot-manifest.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-008 - Desktop QR-03 bank-aware payload remains V2 NON_PAYABLE

- ID: QR04-R2-TC-008
- Requirement: Desktop QR-03 bank-aware payload remains V2 NON_PAYABLE.
- Priority: P0
- Normal/boundary/abnormal: normal
- Preconditions: Actual rendered QR image, four demo banks.
- Test steps: Capture data QR pixels and decode using ZXing. Compare full strings including VERSION=2, PAYMENT_ID=1 and bank code. Repeat payment 2 transition.
- Expected: Canonical plain text with NON_PAYABLE and no bank transfer account or EMV payload.
- Actual: All four exact payloads pass; payment 2 yields PAYMENT_ID=2.
- Status: PASS
- Evidence: functional/logs/qr04-browser-results.json; browser-logs/branding-matrix.json; qr-decode/
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-009 - Desktop QR-04 app-link payload remains distinct from bank QR

- ID: QR04-R2-TC-009
- Requirement: Desktop QR-04 app-link payload remains distinct from bank QR.
- Priority: P0
- Normal/boundary/abnormal: normal
- Preconditions: Active synthetic in-memory session; reachable LAN Vite.
- Test steps: Decode confirmation QR pixels for each bank. Parse origin, path and fragment. Compare against data QR text.
- Expected: Allowlisted app /demo/qr/confirm link with token fragment; distinct QR-03 data QR.
- Actual: All four app links decode to 192.168.31.32:5188; sanitized receipts retained and sessions later revoked.
- Status: PASS
- Evidence: functional/logs/qr04-browser-results.json; browser-logs/branding-matrix.json; source-review/session-revocation.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-010 - Mobile authentication gate uses current brand

- ID: QR04-R2-TC-010
- Requirement: Mobile authentication gate uses current brand.
- Priority: P1
- Normal/boundary/abnormal: normal
- Preconditions: Independent unauthenticated phone browser context.
- Test steps: Open actual decoded link without auth at 320, 390 and 768px in both themes. Inspect sign-in form and disclosure.
- Expected: Current brand; owner sign-in required; no demo snapshot disclosed.
- Actual: Six primary AUTH_REQUIRED images show correct brand and accessible sign-in labels.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json; functional/logs/qr04-browser-results.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-011 - Mobile pending confirmation page uses current brand

- ID: QR04-R2-TC-011
- Requirement: Mobile pending confirmation page uses current brand.
- Priority: P1
- Normal/boundary/abnormal: normal
- Preconditions: Owner authenticated through actual fixture HTTP login; pending BIDV session.
- Test steps: Open fresh link at 320, 390 and 768px in both themes without clicking Confirm.
- Expected: Current brand, PENDING, explicit Confirm/Cancel/Refresh actions and DEMO ONLY notice.
- Actual: Six primary PENDING images pass; scanning does not change state.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json; functional/logs/qr04-browser-results.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-012 - Mobile confirmed screen uses current brand

- ID: QR04-R2-TC-012
- Requirement: Mobile confirmed screen uses current brand.
- Priority: P1
- Normal/boundary/abnormal: normal
- Preconditions: Owner pending MB session.
- Test steps: Click Confirm Demo Payment once. Inspect CONFIRMED at 320, 390 and 768px in both themes.
- Expected: Current brand; real-payment-unchanged feedback; no active confirmation action.
- Actual: Six primary CONFIRMED images pass.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json; functional/logs/qr04-browser-results.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-013 - Mobile canceled screen uses current brand

- ID: QR04-R2-TC-013
- Requirement: Mobile canceled screen uses current brand.
- Priority: P1
- Normal/boundary/abnormal: boundary
- Preconditions: Owner pending session.
- Test steps: Cancel session explicitly; inspect CANCELED at all three phone widths in both themes.
- Expected: Current brand; cancellation feedback; no confirmation action.
- Actual: Six primary CANCELED images pass.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json; functional/logs/qr04-browser-results.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-014 - Mobile expired screen uses current brand

- ID: QR04-R2-TC-014
- Requirement: Mobile expired screen uses current brand.
- Priority: P1
- Normal/boundary/abnormal: boundary
- Preconditions: Real server default 300-second TTL, no client clock bypass.
- Test steps: Wait for real expiry; refresh phone; inspect 320, 390 and 768px in both themes. Attempt actual expired confirmation in functional journey.
- Expected: Current brand; EXPIRED; new-session guidance; server denies confirmation.
- Actual: Six primary EXPIRED images pass; functional expired confirmation returns 409. Matrix final independent token-selection assertion was erroneous and retained as a harness failure.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json; functional/logs/qr04-browser-results.json; browser-logs/aggregate-results.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-015 - Invalid-token screen uses current brand

- ID: QR04-R2-TC-015
- Requirement: Invalid-token screen uses current brand.
- Priority: P1
- Normal/boundary/abnormal: abnormal
- Preconditions: Authenticated context and syntactically valid unknown token.
- Test steps: Open invalid capability link at 390px in both themes. Inspect network and unavailable page.
- Expected: Uniform unavailable response; current brand; no payment details.
- Actual: Both primary invalid/unavailable images pass and backend denies access.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-016 - Wrong-owner screen uses current brand without sensitive data

- ID: QR04-R2-TC-016
- Requirement: Wrong-owner screen uses current brand without sensitive data.
- Priority: P0
- Normal/boundary/abnormal: abnormal
- Preconditions: Second independent member context; first owner link.
- Test steps: Open first owner link as other member; inspect 390px light/dark and compare visible body for payment details.
- Expected: Uniform 404/unavailable; no bank, amount or owner information.
- Actual: Both primary wrong-owner images use current brand; functional privacy assertions pass.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json; functional/logs/qr04-browser-results.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-017 - Missing-token/rescan-required screen uses current brand

- ID: QR04-R2-TC-017
- Requirement: Missing-token/rescan-required screen uses current brand.
- Priority: P1
- Normal/boundary/abnormal: abnormal
- Preconditions: Empty fragment or page reload after initial token consumption.
- Test steps: Navigate directly to confirmation route and reload consumed link. Inspect both themes at 390px.
- Expected: No preview/confirm call without valid in-memory token; current brand and rescan guidance.
- Actual: Missing-token unit test and both primary screenshots pass.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json; functional/logs/qr04-browser-results.json; frontend-logs/final-focused.log
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-018 - Desktop refresh/confirmed state uses current brand

- ID: QR04-R2-TC-018
- Requirement: Desktop refresh/confirmed state uses current brand.
- Priority: P1
- Normal/boundary/abnormal: normal
- Preconditions: Desktop and phone owner contexts; completed phone confirmation.
- Test steps: Verify desktop remains pending until manual Refresh Demo Status, then refresh. Inspect 390, 768 and 1440px in both themes.
- Expected: Manual refresh updates demo state only; shared workspace brand remains current.
- Actual: Six primary desktop confirmed images pass; actual refresh journey succeeds.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json; functional/logs/qr04-browser-results.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-019 - Light-theme logo foreground/background passes contrast checks

- ID: QR04-R2-TC-019
- Requirement: Light-theme logo foreground/background passes contrast checks.
- Priority: P1
- Normal/boundary/abnormal: normal
- Preconditions: Computed mark colors from final real browser.
- Test steps: Read white #FFFFFF glyph on #1F4FD8; calculate sRGB relative-luminance contrast and inspect final light screenshots.
- Expected: Mark contrast at least 4.5:1 and approved styling.
- Actual: Mark ratio 6.6283:1. Wordmark gradient measurement remains separately BLOCKED in TC-049.
- Status: PASS
- Evidence: source-review/brand-contrast.json; browser-logs/branding-matrix.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-020 - Dark-theme logo foreground/background passes contrast checks

- ID: QR04-R2-TC-020
- Requirement: Dark-theme logo foreground/background passes contrast checks.
- Priority: P1
- Normal/boundary/abnormal: normal
- Preconditions: Dark theme active in final browser.
- Test steps: Read mark colors and calculate contrast; compare old pale-blue dark mark with current approved square white-on-blue mark.
- Expected: Same approved mark and at least 4.5:1 contrast.
- Actual: Mark ratio 6.6283:1; no old dark mark. Gradient limitation remains TC-049.
- Status: PASS
- Evidence: source-review/brand-contrast.json; browser-logs/branding-matrix.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-021 - 320px mobile layout has no header overflow

- ID: QR04-R2-TC-021
- Requirement: 320px mobile layout has no header overflow.
- Priority: P1
- Normal/boundary/abnormal: boundary
- Preconditions: 320x844 CSS viewport, light and dark.
- Test steps: Inspect auth, pending, confirmed, canceled and expired states. Compare scrollWidth to innerWidth and brand bounding rectangle.
- Expected: No horizontal overflow or clipped wordmark; actions wrap vertically.
- Actual: Ten primary phone images and bounds checks pass.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json; screenshots/screenshot-manifest.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-022 - 390px mobile layout has no horizontal overflow

- ID: QR04-R2-TC-022
- Requirement: 390px mobile layout has no horizontal overflow.
- Priority: P1
- Normal/boundary/abnormal: boundary
- Preconditions: 390x844 CSS viewport, both themes.
- Test steps: Inspect all normal/negative phone states and desktop cards. Check document scroll width and label wrapping.
- Expected: No horizontal scrolling; DEMO notices remain legible.
- Actual: Primary 390px images and computed bounds pass.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json; screenshots/screenshot-manifest.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-023 - 768px tablet layout remains usable in both themes

- ID: QR04-R2-TC-023
- Requirement: 768px tablet layout remains usable in both themes.
- Priority: P1
- Normal/boundary/abnormal: boundary
- Preconditions: 768px viewport, tablet and desktop routes.
- Test steps: Inspect five phone states, four banks and confirmed desktop state. Read headers, buttons and metadata rows.
- Expected: Usable tablet layout without header or long-label clipping.
- Actual: All required 768px primary image pairs pass.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json; screenshots/screenshot-manifest.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-024 - 1440px desktop layout remains usable in both themes

- ID: QR04-R2-TC-024
- Requirement: 1440px desktop layout remains usable in both themes.
- Priority: P1
- Normal/boundary/abnormal: normal
- Preconditions: 1440px desktop contexts.
- Test steps: Review payment summary, four bank demo cards and manual-refresh confirmed result.
- Expected: Shared workspace navigation and current brand; both QR categories separated.
- Actual: Required primary desktop screenshot pairs pass.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json; screenshots/screenshot-manifest.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-025 - Native 200% zoom preserves header and action visibility

- ID: QR04-R2-TC-025
- Requirement: Native 200% zoom preserves header and action visibility.
- Priority: P1
- Normal/boundary/abnormal: boundary
- Preconditions: Edge native settings zoom 2.0, physical viewport 1440x900.
- Test steps: Set native browser zoom. Assert innerWidth 720, DPR 2 and visualViewport.scale 1. Capture top and scrolled actions with CDP without a clip rectangle.
- Expected: Current brand remains visible; no horizontal overflow; Refresh accessible by scrolling.
- Actual: Four native-cdp images pass visual review. Earlier Playwright clipped/misplaced captures are explicitly excluded.
- Status: PASS
- Evidence: browser-logs/branding-native-cdp.json; screenshots/after-round-02/native-cdp-actions-dark.png
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-026 - Keyboard-only navigation and visible focus remain usable

- ID: QR04-R2-TC-026
- Requirement: Keyboard-only navigation and visible focus remain usable.
- Priority: P1
- Normal/boundary/abnormal: boundary
- Preconditions: 320px owner workspace context with reduced motion.
- Test steps: Focus member menu; Enter opens; Escape closes and restores focus. Tab/focus theme toggle; inspect outline and navigation.
- Expected: Keyboard access, Escape restore and visible focus retained.
- Actual: Both theme receipts pass; visible focus outline is at least 2px.
- Status: PASS
- Evidence: browser-logs/branding-supplement.json; screenshots/after-round-02/keyboard-light.png
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-027 - Axe check for mobile confirmation states

- ID: QR04-R2-TC-027
- Requirement: Axe check for mobile confirmation states.
- Priority: P1
- Normal/boundary/abnormal: abnormal
- Preconditions: Axe 4.14, full document and main-only scopes distinguished.
- Test steps: Scan every primary state, including header, without disabling rules. Reproduce findings on freshly compiled pre-correction Home and phone.
- Expected: Zero unresolved violations across required route scope.
- Actual: 27 main-scoped scans have zero violations. Broader 94 scans retain 94 shared Brand label-content-name-mismatch findings and four Home heading-order occurrences. These are baseline, not a clean all-rules PASS.
- Status: FAIL
- Evidence: browser-logs/aggregate-results.json; browser-logs/baseline-extended-a11y.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-028 - Reduced-motion behavior and theme toggle remain correct

- ID: QR04-R2-TC-028
- Requirement: Reduced-motion behavior and theme toggle remain correct.
- Priority: P1
- Normal/boundary/abnormal: boundary
- Preconditions: prefers-reduced-motion: reduce, both theme settings.
- Test steps: Use theme toggle; check html data-theme; inspect computed transition duration and media query; repeat keyboard access.
- Expected: Theme persists and reduced motion removes transition.
- Actual: Both themes and reduced-motion checks pass; toggle has 44px target.
- Status: PASS
- Evidence: browser-logs/branding-supplement.json; browser-logs/branding-matrix.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-029 - No obsolete logo asset/markup on all QR-04 routes

- ID: QR04-R2-TC-029
- Requirement: No obsolete logo asset/markup on all QR-04 routes.
- Priority: P1
- Normal/boundary/abnormal: abnormal
- Preconditions: Final source HEAD, 92 primary PNGs reviewed including four excluded capture artifacts.
- Test steps: Search relevant routes and shared composition. Read computed brand styles; inspect all accepted primary screenshots and both disabled routes.
- Expected: Zero old logo appearances in supported journey states.
- Actual: 88 accepted primary images use approved brand; zero legacy appearances. No custom logo or new asset introduced.
- Status: PASS
- Evidence: screenshots/screenshot-manifest.json; source-review/final-preservation-and-scope.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-030 - Latest Home/shared workspace screens are unchanged

- ID: QR04-R2-TC-030
- Requirement: Latest Home/shared workspace screens are unchanged.
- Priority: P1
- Normal/boundary/abnormal: normal
- Preconditions: Verified main and pre-correction source available as Git objects.
- Test steps: Compare Brand, Home, tokens, WorkspaceHeader and workspace.css blobs. Render Home and payment entry in both themes.
- Expected: No shared source changes; approved styles consumed by QR wrapper.
- Actual: Shared sources byte-identical to pre-correction HEAD and latest main; current Home and payment-entry image pairs reviewed.
- Status: PASS
- Evidence: source-review/final-preservation-and-scope.json; browser-logs/branding-matrix.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-031 - QR-01/02/03 tests and payment UI regression pass

- ID: QR04-R2-TC-031
- Requirement: QR-01/02/03 tests and payment UI regression pass.
- Priority: P0
- Normal/boundary/abnormal: normal
- Preconditions: Final frontend installation, existing manifest edits preserved.
- Test steps: Run focused QR/payment/theme suite then full frontend suite, lint, TypeScript, demo and production builds. Compare CI committed-manifest results.
- Expected: No existing QR contract, authorization or payment UI regression.
- Actual: 209 focused and 1638 full frontend tests pass; CI repeats 1638 with npm ci.
- Status: PASS
- Evidence: frontend-logs/final-focused.log; frontend-logs/full-results.json; ci-evidence/ci-summary.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-032 - Link QR scans but does not auto-confirm the session

- ID: QR04-R2-TC-032
- Requirement: Link QR scans but does not auto-confirm the session.
- Priority: P0
- Normal/boundary/abnormal: abnormal
- Preconditions: Pending session and independent unauthenticated/authenticated contexts.
- Test steps: Decode actual QR; open route; sign in; preview; inspect requests and session state before explicit click.
- Expected: Opening, sign-in and preview cannot perform confirmation.
- Actual: Functional request/state assertions pass; explicit Confirm remains required.
- Status: PASS
- Evidence: functional/logs/qr04-browser-results.json; backend-logs/all-unit.log
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-033 - Only correct authenticated owner may confirm

- ID: QR04-R2-TC-033
- Requirement: Only correct authenticated owner may confirm.
- Priority: P0
- Normal/boundary/abnormal: abnormal
- Preconditions: Distinct member identities and real JWT/security filters in fixture HTTP.
- Test steps: Try anonymous access and foreign member confirmation; then explicitly confirm as correct active owner.
- Expected: 401/404 for unauthorized paths; correct owner changes only in-memory demo state.
- Actual: Fixture browser, controller/unit and separate real-SQL CI owner checks pass.
- Status: PASS
- Evidence: functional/logs/qr04-browser-results.json; backend-logs/all-unit.log; ci-evidence/ci-summary.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-034 - Wrong owner and malformed tokens denied without leakage

- ID: QR04-R2-TC-034
- Requirement: Wrong owner and malformed tokens denied without leakage.
- Priority: P0
- Normal/boundary/abnormal: abnormal
- Preconditions: Unknown, malformed and foreign-owner capabilities.
- Test steps: Send malformed/unknown preview and confirm requests; inspect uniform responses, missing-token UI, and no private snapshot.
- Expected: Uniform unavailable responses and no owner/amount disclosure.
- Actual: Unit/security/browser checks pass; phone wrong-owner and invalid pages do not disclose payment fields.
- Status: PASS
- Evidence: functional/logs/qr04-browser-results.json; browser-logs/branding-matrix.json; backend-logs/all-unit.log
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-035 - Expiry and canceled/replayed token denied

- ID: QR04-R2-TC-035
- Requirement: Expiry and canceled/replayed token denied.
- Priority: P0
- Normal/boundary/abnormal: boundary
- Preconditions: Default 300-second TTL and canceled/replaced sessions.
- Test steps: Expire actual session; confirm canceled token; replace bank session; retry terminal operations. Also retry confirmed owner request.
- Expected: Expired/canceled confirmation returns 409; unknown/revoked unavailable; confirmed owner retry is idempotent with no new operation.
- Actual: Functional real expiry 409 passes; unit terminal/concurrency cases pass; confirmed owner replay 200 CONFIRMED is an idempotent read, not a second settlement.
- Status: PASS
- Evidence: functional/logs/qr04-browser-results.json; browser-logs/branding-supplement.json; backend-logs/all-unit.log
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-036 - Concurrency/double-confirmation is safe and idempotent

- ID: QR04-R2-TC-036
- Requirement: Concurrency/double-confirmation is safe and idempotent.
- Priority: P0
- Normal/boundary/abnormal: boundary
- Preconditions: Same session token and competing confirm/cancel/expiry operations.
- Test steps: Run DemoQrService concurrent race tests and frontend duplicate-submit guards in full suites. Inspect synchronized transition and post-read deadline sampling.
- Expected: At most one terminal transition; no financial write or second action.
- Actual: All relevant unit tests pass; backend remains synchronized and unchanged in Round 02.
- Status: PASS
- Evidence: backend-logs/all-unit.log; frontend-logs/full-results.json; source-review/PR-167-final-HEAD.patch
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-037 - Unmocked SQL-backed browser demo confirmation succeeds with isolated data

- ID: QR04-R2-TC-037
- Requirement: Unmocked SQL-backed browser demo confirmation succeeds with isolated data.
- Priority: P0
- Normal/boundary/abnormal: abnormal
- Preconditions: Healthy isolated non-production SQL database required; fixture is not an acceptable substitute.
- Test steps: Open isolated SQL database with the saved read-only probe. If healthy, start real repositories/backend and two browser contexts.
- Expected: Combined browser-to-live-SQL auth, lifecycle and before/after fingerprint run completes.
- Actual: BLOCKED before real SQL-backed backend startup: metadata query timeout and isolated database open/login failure. Earlier state was RECOVERY_PENDING. No combined SQL browser run claimed.
- Status: BLOCKED
- Evidence: sql-evidence/final-read-only-probe.json; 06-real-sql-browser-e2e-and-limits.md
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-038 - Financial table snapshots remain unchanged after demo confirm

- ID: QR04-R2-TC-038
- Requirement: Financial table snapshots remain unchanged after demo confirm.
- Priority: P0
- Normal/boundary/abnormal: normal
- Preconditions: Separate CI SQL Server 2022 integration environment, not local browser fixture.
- Test steps: Run DemoQrDatabaseIntegrationTest real HTTP/JWT/Flyway/SQL tests; compare canonical SHA-256 of all rows in seven financial tables before/after owner confirmation and cancellation.
- Expected: Payment stays PENDING; all seven table fingerprints identical.
- Actual: Three CI QR SQL tests pass with zero skips, within 767 integration tests. Combined local browser variant remains BLOCKED under TC-037.
- Status: PASS
- Evidence: ci-evidence/40877397382/backend-integration-test-log-extract.log; source-review/PR-167-final-HEAD.patch
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-039 - DOCX updated privately and render/PNG visual review completed

- ID: QR04-R2-TC-039
- Requirement: DOCX updated privately and render/PNG visual review completed.
- Priority: P1
- Normal/boundary/abnormal: normal
- Preconditions: Existing private audit retained with backup.
- Test steps: Append Round 02 source/test/security/limitation entry privately. Use canonical renderer; inspect pages and compare historical PNG bytes.
- Expected: DOCX VERIFIED; no audit document or preview in Git or reports.
- Actual: DOCX VERIFIED: 44 pages; compatible LibreOffice 26.8.2 and Poppler; all historical PNGs match reviewed pages and appended page reviewed.
- Status: PASS
- Evidence: 08-ai-audit-verification-status.md
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-040 - Exact-HEAD complete PR patch, report hashes and required CI verified

- ID: QR04-R2-TC-040
- Requirement: Exact-HEAD complete PR patch, report hashes and required CI verified.
- Priority: P0
- Normal/boundary/abnormal: normal
- Preconditions: Remote PR HEAD equals local HEAD; main fetched again.
- Test steps: Export complete base...HEAD diff, compare bytes again, check all 29 paths and diff --check. Read remote CI gate/ruleset; verify test-case hashes and evidence manifest.
- Expected: Exact HEAD patch and equal hashes; CI gate success; no self-approval or merge.
- Actual: Complete 159922-byte patch matches HEAD; 16 remote checks success; evidence hashes verified separately at sealing.
- Status: PASS
- Evidence: source-review/patch-provenance.json; ci-evidence/final-remote-head.json; test-cases/sha256-verification.json; evidence-integrity.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-041 - Normal production profile fails closed

- ID: QR04-R2-TC-041
- Requirement: Normal production profile fails closed.
- Priority: P0
- Normal/boundary/abnormal: abnormal
- Preconditions: Production build with VITE_QR_PAYMENT_DEMO=true; backend production gate tests.
- Test steps: Open desktop and phone demo routes in actual normal-production preview; verify unavailable page and no demo API traffic. Run prod/production profile guard tests.
- Expected: Production cannot expose demo workflow even with frontend flag.
- Actual: Actual desktop/phone disabled images and no-call unit tests pass; backend configuration tests pass.
- Status: PASS
- Evidence: browser-logs/branding-matrix.json; browser-logs/branding-supplement.json; backend-logs/all-unit.log
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-042 - Bank change, cancellation and navigation preserve session boundaries

- ID: QR04-R2-TC-042
- Requirement: Bank change, cancellation and navigation preserve session boundaries.
- Priority: P0
- Normal/boundary/abnormal: boundary
- Preconditions: Owner pending session, switch bank or leave demo route.
- Test steps: Change bank; inspect old session cancellation; navigate away and inspect cleanup attempt. Test disconnected cleanup and server TTL behavior in units.
- Expected: Prior pending session canceled where reachable; server deadline remains authoritative otherwise.
- Actual: Focused frontend and backend tests pass; volatile store and disconnected cleanup limit documented.
- Status: PASS
- Evidence: frontend-logs/final-focused.log; backend-logs/all-unit.log
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-043 - Configured origin allowlist is fail closed

- ID: QR04-R2-TC-043
- Requirement: Configured origin allowlist is fail closed.
- Priority: P0
- Normal/boundary/abnormal: abnormal
- Preconditions: Trusted configured frontend origin; untrusted URL variants.
- Test steps: Run DemoQrSettings and controller configuration tests for request Host, public HTTP, loopback, userinfo, fragments and mismatched allowlist. Inspect link origin from actual QR decoding.
- Expected: Only explicit HTTPS or opted-in private LAN HTTP configuration accepted.
- Actual: Configuration tests pass; decoded native links use approved private LAN origin. Existing CORS/security configuration unchanged.
- Status: PASS
- Evidence: backend-logs/all-unit.log; functional/logs/qr04-browser-results.json; source-review/PR-167-final-HEAD.patch
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-044 - Token secrecy and evidence capability revocation

- ID: QR04-R2-TC-044
- Requirement: Token secrecy and evidence capability revocation.
- Priority: P0
- Normal/boundary/abnormal: abnormal
- Preconditions: Dedicated synthetic test sessions only; reports contain QR image capabilities.
- Test steps: Verify fragment consumed with history replace, absent from storage/reload; inspect redacted receipts. Stop positively identified fixture process and verify no task listeners remain. Scan report text for secret/token values.
- Expected: No real secrets or usable capabilities in delivered evidence.
- Actual: Task fixture and Vite servers stopped; all in-memory capabilities invalidated. Privacy scan receipt records the final text checks.
- Status: PASS
- Evidence: source-review/session-revocation.json; source-review/privacy-and-package-check.json; functional/logs/qr04-browser-results.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-045 - Dependency consistency and existing local modifications preserved

- ID: QR04-R2-TC-045
- Requirement: Dependency consistency and existing local modifications preserved.
- Priority: P1
- Normal/boundary/abnormal: boundary
- Preconditions: Pre-existing caret edits to qrcode 1.5.4 and types 1.5.6 manifest declarations.
- Test steps: Compare both working-tree files with private preflight hashes; inspect exact PR path list; compare committed-manifest CI npm ci build/test.
- Expected: No silent incorporation, discard or dependency drift in PR.
- Actual: Both local files unchanged; no manifest in PR diff; committed dependencies install/build/test in CI.
- Status: PASS
- Evidence: source-review/final-preservation-and-scope.json; ci-evidence/ci-summary.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-046 - Loading/offline/error feedback works in actual Vite StrictMode

- ID: QR04-R2-TC-046
- Requirement: Loading/offline/error feedback works in actual Vite StrictMode.
- Priority: P1
- Normal/boundary/abnormal: abnormal
- Preconditions: Native Vite development app and real HTTP fixture; injected request hold/failure only.
- Test steps: Hold one or two StrictMode preview requests, inspect loading, then fail requests and inspect error/refresh. Deny actual clipboard permission and inspect accessible alert.
- Expected: No implicit confirmation; clear accessible loading/error feedback; correct brand.
- Actual: 40 supplemental functional checks pass. Original one-request assumption failed and was corrected with retained attempt logs, without weakening confirmation or axe assertions.
- Status: PASS
- Evidence: functional-extra-dev/logs/qr04-browser-results.json; browser-logs/strict-mode-harness-correction.json; browser-logs/branding-matrix.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-047 - Round 1 integrity and compact Round 2 evidence package

- ID: QR04-R2-TC-047
- Requirement: Round 1 integrity and compact Round 2 evidence package.
- Priority: P1
- Normal/boundary/abnormal: abnormal
- Preconditions: Original Round 1 manifest and unrelated ignored files retained.
- Test steps: Rehash all 745 Round 1 manifest entries and relevant preflight files. Inspect Round 2 files for installed caches, binaries and audit artifacts.
- Expected: Round 1 unchanged; Round 2 contains review evidence only.
- Actual: 745 original entries match; no user data lost; final package/privacy receipt reports forbidden-artifact scan.
- Status: PASS
- Evidence: source-review/final-preservation-and-scope.json; source-review/privacy-and-package-check.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-048 - Physical phone on LAN

- ID: QR04-R2-TC-048
- Requirement: Physical phone on LAN.
- Priority: P2
- Normal/boundary/abnormal: boundary
- Preconditions: A physical device on the approved private LAN is required.
- Test steps: Open an actual scanned link on physical phone, authenticate independently and confirm demo.
- Expected: Physical-device interaction recorded honestly.
- Actual: No physical phone available; two independent real Edge contexts exercised phone viewports.
- Status: NOT RUN
- Evidence: 06-real-sql-browser-e2e-and-limits.md
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-049 - Shared header wordmark numeric contrast across gradient

- ID: QR04-R2-TC-049
- Requirement: Shared header wordmark numeric contrast across gradient.
- Priority: P1
- Normal/boundary/abnormal: boundary
- Preconditions: Approved gradient/backdrop header preserved.
- Test steps: Run full-document axe contrast analysis and inspect computed styles/PNG. Distinguish measurable mark colors from gradient wordmark background.
- Expected: Complete numerical contrast evidence for wordmark across all backdrop samples.
- Actual: Axe returns color-contrast INCOMPLETE in all 94 full-document scans. Visual legibility and mark ratio reviewed; numerical gradient conformance not established.
- Status: BLOCKED
- Evidence: source-review/brand-contrast.json; browser-logs/aggregate-results.json
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.

## QR04-R2-TC-050 - Screen-reader audio interaction

- ID: QR04-R2-TC-050
- Requirement: Screen-reader audio interaction.
- Priority: P2
- Normal/boundary/abnormal: boundary
- Preconditions: Configured assistive technology and audio observation required.
- Test steps: Read auth, pending, error and terminal announcements using a screen reader and verify spoken order and feedback.
- Expected: Actual audio/assistive-technology receipt.
- Actual: Semantic/keyboard/axe tests executed; no screen-reader audio session performed.
- Status: NOT RUN
- Evidence: 07-regression-tests-and-accessibility.md
- HEAD/commit: 12cc8b70855567bcdc9532ceb263e5111f8c48a3
- Notes: PASS applies only to the stated environment and observations. An unresolved broader accessibility finding and the combined SQL browser gate remain open; CODEOWNER approval is not implied.
