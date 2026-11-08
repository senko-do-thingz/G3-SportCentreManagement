# MW-02 evidence - Optional member health assessment

- Branch: `fix/flow-1-mw-02-health` (stacked on `fix/flow-1-mw-01-member-profile`)
- Captured: 2026-11-08 15:23 Asia/Bangkok
- Browser: Google Chrome via Playwright (channel `chrome`), viewport 1440x900, light theme
- Backend: jar built from this branch, profiles `dev,demo`, port 18080, throwaway database `sportify_mw_evidence_1794126131` (fresh V1-V49 plus demo seeds). Not the shared `sportify` database.
- Frontend: local Vite dev server on port 3000
- Fixture: synthetic BE-07 demo accounts. Answers are invented test values, not real health data.
- Capture script: `ev.py` + `mw02.py` (kept outside the repository)

| File | Role and route | Steps | Expected | Observed |
|---|---|---|---|---|
| `screenshots/01-questionnaire-synthetic-answers.png` | MEMBER (An) `/member/profile/health` | Choose synthetic answers, tick Coach sharing | Five fixed questions, Yes/No/Prefer not to answer, Skip | As expected |
| `screenshots/02-saved-outcome-and-privacy.png` | MEMBER (An) `/member/profile/health` | Save, reload | Advisory note "Coach conversation recommended", disclaimer, privacy rules, sharing On | As expected |
| `screenshots/03-assigned-coach-summary-only.png` | COACH (Quan) `/coach/training/progress?classId=1&memberId=<An>` | Open An in the assigned class | Only the note and consultation request, no answers | As expected |

## API checks made in the same run

| Check | Result |
|---|---|
| Assigned Coach `GET .../health-summary` response keys | `consultationRequested, disclaimer, memberId, outcome, shared, updatedAt` (no answers) |
| Unrelated Coach (Nam) on the same summary | 404 |
| Receptionist (Lan) `GET /member/health-assessment` | 403 |
| Member `PUT` with answer `MAYBE` | 400 |
