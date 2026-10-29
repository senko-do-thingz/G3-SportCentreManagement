# Test Cases - NX-14 Member training plans and NX-15 Member attendance and results

## 1. Scope and provenance
- Tasks: NX-14 "Build member training plans and coach notes UI" (F4-REQ-06) and NX-15 "Build member attendance and training result history" (F4-REQ-05). Owner Truc, Flow 4, wave W2, from the task assignment spreadsheet.
- Branch: feat/member-ui-flow4-training-plans-and-history, based on main 5224d58.
- Routes: /member/training (My Training Plans) and /member/history (Attendance and Results, tabs Attendance and Training results). Both are added to the Member menu.
- Screen IDs: the spreadsheet has no Screen ID column for these tasks; none is invented.

## 2. IMPORTANT: contract status
The Flow 4 API contract (docs/nx-03) is an unsigned draft. Its pages state "IMPLEMENTATION_NOT_AUTHORIZED". For the four Member read operations (F4-PLAN-04, F4-PROGRESS-04, F4-ATTEND-03, F4-RESULT-03) the method and path are TBD_AFTER_APPROVAL, and no field list is approved. The backend tasks NX-07, NX-08, NX-09, NX-10 do not exist in the repository.

Therefore the screens are built against a PROVISIONAL contract that is proposed by this change, kept in one file (frontend/src/api/memberTrainingApi.ts, constant MEMBER_TRAINING_ROUTES and the parsers). The acceptance criterion "Member history matches BE" cannot be verified yet. Open questions for the backend owner are in docs/pr/NX-14-NX-15_contract_gaps_for_BE.md.

In the opt-in manager-preview mode the screens read synthetic data (frontend/src/api/mock/memberTrainingPreview.ts) and show a visible "sample data only" notice.

## 3. Acceptance criteria and verification
| ID | Criterion (from the task rows) | Verification |
|---|---|---|
| NX14-1 | Show assigned plans and coach notes | memberTraining.test.tsx "shows own plans and coach notes" |
| NX14-2 | No-edit member permissions | Same test: no edit/save/delete/add/publish button, no text field, every request is GET |
| NX14-3 | Empty, loading and error states | Tests: empty lists; plan failure keeps notes visible and retries; 403 shown as error, not as empty; malformed answer rejected |
| NX14-4 | Member sees only own approved plan and comments | Parser allowlist drops peer and staff-only fields (memberTrainingApi.test.ts); request paths carry no member id |
| NX15-1 | Show own attendance history and per-session results | Tests for the Attendance and Training results tabs |
| NX15-2 | Reuse session labels and local time rules | Dates and times use bookingDate and bookingTime from My Bookings (test checks 20/10/2026, 09:00 - 10:00) |
| NX15-3 | Cover empty and error cases | Tests: empty attendance, 503 error with retry, no fake empty state on error |
| NX15-4 | No leakage from another member | Allowlist parsers (peer fields dropped); API has no member id parameter; tests assert no member id in any request |
| NX15-5 | Distinguish missing from zero and unrecorded from absent | Tests: recorded 0 shows "0", missing metric shows "Not recorded"; UNRECORDED shows "Not recorded yet" apart from "Absent" |
| NX15-6 | Responsive and accessible | Screenshots at 390/768/1440px light and dark; tab arrow-key navigation checked in browser |

## 4. Automated results
Run from frontend/ on this branch, 2026-10-29.

| Command | Result |
|---|---|
| npm run lint | Passed |
| npx tsc -b | Passed |
| npx vitest run (full suite, final) | 89 files, 1613 tests passed (a first run under heavy machine load showed 16 timing failures in two unrelated public-page test files; they pass alone and in the clean re-run) |
| npx vitest run memberTraining.test.tsx memberTrainingApi.test.ts (after the fixes) | 2 files, 16 tests passed |

New tests (16): frontend/src/api/memberTrainingApi.test.ts (parsers, privacy allowlist, zero vs missing, rejects) and frontend/src/pages/member/memberTraining.test.tsx (both screens, empty/error/403/malformed/paging). Final full-suite run and CI URL for the latest commit: TO BE FILLED after the pipeline runs.

## 5. Visual evidence
Chrome headless (puppeteer-core) against `vite --mode manager-preview`, synthetic data. Folder: docs/tests/evidence/member-training-ui/. Names: `<plans|history|results>_<390|768|1440>_<light|dark>.png`; states `*_empty_*` and `*_error_*` at 1440 and 390; `history_keyboard_focus_1440_<theme>.png`; report.json.
- No horizontal overflow in any capture.
- Pressing ArrowRight on the Attendance tab selects Training results (checked in light and dark).
- A screenshot review found "Arrived -" lines caused by an invalid sample time; fixed so an unreadable arrival time is hidden, with a regression test.

Not covered: contrast ratios were not measured for these screens; no screen-reader pass; Chrome only; no real backend.

## 6. Limitations
Provisional contract, synthetic data, no backend, no Coach-side screens (NX-11 to NX-13) to produce real plans, notes, attendance or results.
