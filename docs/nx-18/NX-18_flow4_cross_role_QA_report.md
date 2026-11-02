# NX-18 Flow 4 frontend cross-role QA report (round 2, after CODEOWNER review of PR #174)

Task: NX-18 "Run Flow 4 frontend regression and accessibility checks" (Flow 4, F4-REQ-01..07, FE, owner Truc, with Hung and Phat, wave W3). Depends on NX-11, NX-12, NX-13 (Hung), NX-14, NX-15 (Truc). Status of the task proposal: requires team sign-off.

**NX-18 IS NOT COMPLETE.** The automated run currently FAILS (exit code 1) and several parts are BLOCKED. Nothing in this report claims completion.

Date: 2026-11-02. Tree under test: main 646bd14 (includes PR #175, the logo label fix, merged 2026-10-30) + PR #168 (reception front desk UI, merged into a scratch tree, not into this branch) + the fixes of this branch.

## 1. What the review asked and what was done

| Review item | Status |
|---|---|
| The runner must exit with a failure code when required checks fail | Done. `flow4-qa.mjs` exits 1 on any required failure or missing capture and 0 only when all required checks pass. The rules are in `evaluate.mjs` |
| Demonstrate it with a negative-control test | Done, two levels. (a) `evaluate.test.mjs`: 17 unit tests, each breaks one rule on purpose and expects a failure. (b) `run-negative-controls.mjs`: in a real browser the clean run exits 0 and each of 8 injected defects exits 1 (evidence/negative-control) |
| Rerun the integrated checks after #168 and #175 | Done on main (with #175) plus #168. Result: FAIL, see section 3 |
| Keep unavailable backend and cross-role workflows BLOCKED, do not report NX-18 complete | Done, section 5. The runner prints the BLOCKED list on every run |
| Remove tracked private audit material | Done. The AI audit log was force-added against the "AI Audit Logs are kept locally only" rule of .gitignore; it is no longer tracked on this branch. Earlier commits still contain it in the history |
| Updated evidence | Done: evidence/integrated-rerun and evidence/negative-control. The earlier after-fix folder was removed because it mixed in screens from another branch |

## 2. Scope actually tested

Only screens that exist on main (with #168):

| Role | Screen | Route |
|---|---|---|
| Coach | Teaching schedule | /coach |
| Coach | Class roster | /coach/roster |
| Receptionist | Member search | /reception?q=an |
| Receptionist | Check-in | /reception/check-in |

Each in light and dark at 390, 768 and 1440 px (24 captures), plus empty, error (503) and forbidden (403) answers at 390 and 1440 px in both themes (48 state checks). API answers are mocked in the browser.

Not tested here: Member training screens (not on main; PR #185 is checked by a separate run), NX-11 to NX-13 screens (not built), any write flow.

## 3. Result of the integrated run: FAIL (exit code 1)

Command: `node docs/nx-18/qa-tools/flow4-qa.mjs <outDir>` against a dev server of the tree above. Output: evidence/integrated-rerun/runner-output.txt, data in report.json.

| Check | Result |
|---|---|
| Horizontal overflow | none in 24 of 24 (Check-in at 390px passes after fix NX18-D1) |
| One h1, no redirect to login or 403 | all pass |
| Browser console errors | 0 |
| Keyboard: visible focus ring on every stop | 0 failures |
| Empty, error and forbidden states | all 48 pass |
| axe serious or critical | **16 FAIL**: rule label-content-name-mismatch on the Menu button at 390 and 768 px (4 screens x 2 widths x 2 themes); none at 1440 px |

The 16 failures have one cause, defect NX18-D4 below.

## 4. Defects

| ID | Severity | Finding | Status |
|---|---|---|---|
| NX18-D1 | Blocking on mobile | Check-in overflowed horizontally at 390px (bare 1fr grid column) | Fixed in this branch (reception.css), regression test |
| NX18-D2 | Serious | The scrolling Today's check-ins table was not keyboard reachable | Fixed (named scroll region), regression test |
| NX18-D3 | Serious (WCAG 2.5.3) | Logo link name did not contain its visible text. PR #175 changed the label to "Sportify Sports center home" but axe still failed: the logo letter S was real text inside an aria-hidden span and still counted as visible text, and the two words were rendered without a space | Fixed in this branch: the letter is drawn by CSS and a text space separates the words (Brand.tsx, components.css). `Brand.test.tsx` fails without the fix. After this, 0 logo failures in the run |
| NX18-D4 | Serious (WCAG 2.5.3) | On narrow screens the header Menu button has visible text Menu but aria-label "Open Coach workspace navigation", so the name does not contain the visible word. Component: WorkspaceHeader.tsx (shared shell) | OPEN, not fixed here. Changing the label breaks tests that look the button up by its current name on PR #185 (training.flow.test.tsx), so it needs a decision from the owner of the shared header (Phat). Suggested name: start with the visible word, for example "Menu: open Coach workspace navigation", and update the lookups together |

## 5. BLOCKED (never counted as passed)

- NX-11, NX-12, NX-13: Coach plan editor, outcomes and feedback, attendance UI are not on main.
- Member training screens (NX-14, NX-15) are not on main; see the separate run on PR #185.
- FE/BE contract compatibility: docs/nx-03 is an unsigned draft; no run against a backend.
- Cross-role workflows (a coach records an outcome and the member sees it) need the backend.
- Sign-off by Hung and Phat.
- Not done: screen reader pass, Firefox and Safari, real devices, slow network; axe finds only part of the accessibility problems; the keyboard check measures focus visibility, not logical order.

## 6. How to reproduce

```
npm install --no-save puppeteer-core axe-core      (outside the project dependencies)
cd frontend && npx vite --port 5175                 (tree under test)
node docs/nx-18/qa-tools/flow4-qa.mjs <outDir>      exit 0 = required checks passed, 1 = failed
node docs/nx-18/qa-tools/run-negative-controls.mjs <outDir>     exit 0 = every injected defect was caught
node --test docs/nx-18/qa-tools/evaluate.test.mjs   unit negative control
```

Exit code 0 of the runner never means NX-18 is done; it only means the required automated checks passed. The BLOCKED list is printed every time.

## 7. Checklist for the full run (when NX-11 to NX-13 and the backend exist)

Light and dark at 390, 768 and 1440 px: role access, loading, empty, error with retry, 403, malformed answer, privacy (no other member data, no edit control for members), keyboard order and focus, screen reader headings and live regions, layout without horizontal scroll, date and time wording, regression against main, contract fields against the signed contract.

## 8. Sign-off table

| Role | Screens | Automated run | Contract | Human sign-off |
|---|---|---|---|---|
| Coach | schedule, roster | FAIL at 390 and 768 px (D4) | BLOCKED | pending |
| Reception | member search, check-in | FAIL at 390 and 768 px (D4) | BLOCKED | pending |
| Member | training screens | not on main | BLOCKED | pending |
| Coach and Reception | NX-11 to NX-13 screens | not built | BLOCKED | pending |
