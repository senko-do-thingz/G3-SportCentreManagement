# Test Cases - NX-31 Member assistant entry point, opt-in and route protection

## 1. Scope and provenance
- Task: NX-31 "Integrate AI assistant into authenticated member pages" (Flow 6, F6-REQ-01, FE, owner Truc, P1, wave W5). Predecessors NX-27 (scope and RBAC), NX-29 (service), NX-30 (chat UI) are not in the repository.
- Branch: nx-31-assistant-optin-on-pr185, stacked on PR #185 (feat/plan-comment-attend-result, head 7c42630). Open the pull request against that branch, or against main after #185 is merged.
- Screen ids: none assigned in the spreadsheet.
- PR #185 already provides the header link "Open AI Assistant", the route /member/assistant, a safe `returnTo` parameter and the chat slot (`MemberAssistantPage`, prop `Chat`). This change adds what NX-31 asks for on top of it: an off-by-default feature switch, an explicit opt-in screen with Turn off, and tests for signed-out and wrong-role access.

## 2. Behavior change to know about
With the default build the header link "Open AI Assistant" is now HIDDEN and /member/assistant redirects to the member home, because the switch VITE_ASSISTANT_ENABLED is off. Set VITE_ASSISTANT_ENABLED=true (see frontend/.env.example) to show it. Without this switch the feature would not be opt-in.

## 3. What was added
- frontend/src/config/env.ts: `assistantEnabled()`.
- frontend/src/pages/member/MemberAssistantGate.tsx: route element. Switch off: redirect to /member. Not opted in: consent screen ("Before you start": own account only, separate from booking and payment, optional) with Turn on and Not now. Opted in: the existing `MemberAssistantPage` plus a Turn off button that returns to the page the member came from.
- frontend/src/pages/member/assistantPreference.ts: per-account choice kept in this browser (localStorage key sportify.assistant.optIn.<userId>), guarded for blocked storage. Not sent to any server.
- `MemberAssistantLink` (header link) returns nothing when the switch is off.
- `MemberAssistantPage`, `safeMemberReturnTo` and the chat prop are unchanged.
- No service is called. Booking and payment pages are not touched.

## 4. Acceptance criteria and verification
| ID | Criterion (task row) | Verification |
|---|---|---|
| A1 | Opt-in entry point | Consent screen before the chat; header link only when the switch is on (memberAssistantGate.test.tsx) |
| A2 | Member can open the assistant safely | Turn on opens the chat slot; the choice is stored for that account only; another account sees the consent again |
| A3 | Unauthenticated access tested | Signed-out visitor sees Log in and no request is made (new test; the existing training.flow test covers the older case) |
| A4 | Wrong-role access tested | Coach, Receptionist and Manager see Access denied, no consent screen and no chat (new test, 3 cases) |
| A5 | Booking and payment unchanged | No booking or payment file is modified; the whole suite passes |
| A6 | Return navigation preserved | Not now and Turn off return to the `returnTo` page with its query; unsafe targets fall back to /member (existing safeMemberReturnTo test plus a new page test) |
| A7 | Feature switch | Switch off: no header link, route redirects to Member Dashboard |

One existing test was adapted: training.flow.test.tsx "preserves return navigation when opening the Assistant" now turns the switch on and presets the opt-in, because the link and the chat are no longer shown by default.

## 5. Automated results
From frontend/ on this branch, 2026-11-02.

| Command | Result |
|---|---|
| npm run lint | Passed |
| npx tsc -b | Passed |
| memberAssistantGate, training.flow, TrainingData tests | 3 files, 26 tests passed |
| npx vitest run (full suite) | 95 files, 1722 tests passed |

Baseline before the change on #185: 94 files, 1712 tests passed. CI URL for the latest commit: TO BE FILLED (CI runs only on pull requests).

## 6. Browser evidence
docs/nx-31/qa-tools/assistant-screens.mjs (Chrome headless, API mocked, dev server with VITE_ASSISTANT_ENABLED=true). Images and report.json in docs/nx-31/evidence/ at 390, 768 and 1440px in light and dark.
- Header link present in 6 of 6 runs (the menu button is opened on small screens); consent screen shown in 6 of 6.
- Not now returned to /member/training/plans?view=week in 6 of 6; Turn on showed the chat slot; Turn off returned to the same page in 6 of 6.
- No horizontal overflow in any capture. A Coach opening the route sees Access denied; a signed-out visitor lands on /login.

## 7. Not covered
Axe and contrast were not run on the two new screens; the chat itself (NX-30), the assistant API and its privacy and prompt-injection tests (NX-29, NX-32) do not exist; no real backend; Chrome only. Consent is a browser setting, not a server record.
