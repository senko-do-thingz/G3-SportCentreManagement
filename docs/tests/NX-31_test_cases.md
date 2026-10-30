# Test Cases - NX-31 Member assistant entry points and route protection

## 1. Scope and provenance
- Task: NX-31 "Integrate AI assistant into authenticated member pages" (Flow 6, F6-REQ-01, FE, owner Truc, priority P1, wave W5). Predecessors: NX-27 (scope and RBAC, Dat), NX-30 (accessible chat UI, Phat), plus NX-29 (service, Dat) through NX-30.
- Branch: nx-31-member-assistant-entry-points, based on main e45b6d3.
- Screen ids: none assigned in the spreadsheet.
- Route: /member/assistant (under the existing Member area). Entry points: a card on the Member Dashboard (side column) and one at the end of My Profile.

## 2. IMPORTANT: what is and is not built
None of the predecessors exist: NX-27, NX-29 and NX-30 are not in the repository or on GitHub, and the "F6 member route/API contract" is not defined (the task row says no endpoints are specified and it needs sign-off). Flow 6 is optional and its scope is unsigned.

So this change delivers only the part that does not depend on them:
- an opt-in entry card, off by default (VITE_ASSISTANT_ENABLED must be exactly "true"; with the default the app shows no entry point and the route redirects to the member home);
- an explicit consent screen before anything opens, stored per account in this browser only;
- route protection through the existing role gate, plus return navigation to the page the member came from;
- an empty slot (AssistantHost) where the chat UI of Phat (NX-30) goes. The slot calls no service and shows "The assistant chat is not available yet".

Nothing talks to a backend. Data access ("respect member data access") is therefore enforced by NX-27 and NX-29 later; here the page only states the policy and never sends any member data.

## 3. Acceptance criteria and verification
| ID | Criterion (task row) | Verification |
|---|---|---|
| A1 | Opt-in entry points | Test "shows an opt-in card on My Profile ...": card text and Learn more link; flag off hides it |
| A2 | Member can open the assistant safely | Consent screen first; chat slot only after Turn on; choice stored per account; a second account sees the consent again |
| A3 | Unauthenticated access tested | Test "sends a signed-out visitor to Log in"; browser check: /member/assistant goes to /login |
| A4 | Wrong-role access tested | Tests for Coach, Receptionist, Manager: Access denied, no assistant heading; browser check for Coach |
| A5 | Existing booking and payment flows unchanged | Entry placed only on Dashboard and My Profile (source scan test); no change to booking or payment files; the whole existing suite passes |
| A6 | Return navigation preserved | Back and Not now links return to the origin page (router state); Turn off returns there too; unsafe targets fall back to /member (unit and page tests) |
| A7 | Feature can be switched off | Test with VITE_ASSISTANT_ENABLED=false: no entry, route redirects to Member Dashboard |

## 4. Automated results
From frontend/ on this branch, 2026-10-30.

| Command | Result |
|---|---|
| npm run lint | Passed |
| npx tsc -b | Passed |
| npx vitest run src/pages/member/assistant | 1 file, 12 tests passed |
| npx vitest run (full suite) | 90 files, 1654 tests passed (12 new) |

CI URL for the latest commit: TO BE FILLED (CI runs only on pull requests).

## 5. Browser evidence
docs/nx-31/qa-tools/assistant-screens.mjs (Chrome headless, API answers mocked, dev server started with VITE_ASSISTANT_ENABLED=true). Images and report.json in docs/nx-31/evidence/: profile with the entry card, consent screen, assistant on, at 390, 768 and 1440px in light and dark; Coach denied; signed-out redirect.
- 6 of 6 runs showed the entry card; the consent screen opened; Back returned to My Profile; Turn on opened the chat slot; Turn off returned to My Profile and hid the card.
- No horizontal overflow in any capture.
- A Coach opening the route sees "Access denied"; a signed-out visitor lands on /login.

## 6. Not covered
Accessibility measurements (axe, contrast) were not run on these two new screens. The chat itself, its keyboard behavior and live region announcements belong to NX-30. The real assistant API, prompt-injection and privacy tests belong to NX-29 and NX-32. No real backend. Chrome only.
