# MW-07 evidence - Receptionist action inbox

- Branch: `fix/flow-1-mw-07-reception-inbox` (stacked on `fix/flow-3-mw-06-withdrawal`)
- Captured: 2026-11-08 23:45 Asia/Bangkok
- Browser: Google Chrome via Playwright (channel `chrome`), viewport 1440x900, light theme
- Backend: jar built from this branch, profiles `dev,demo`, port 18080, throwaway database `sportify_mw_evidence_1794156136` (fresh V1-V53 plus demo seeds). Not the shared `sportify` database. No new migration in this task.
- Frontend: local Vite dev server on port 3000
- Fixture: Mai (synthetic) created top-up TU-000001 (500,000 VND, pending), a second confirmed top-up, a payout account and withdrawal WD-000001 (300,000 VND, pending) through the real APIs.
- Capture script: `ev.py` + `mw07.py` (kept outside the repository)

| File | Role and route | Steps | Expected | Observed |
|---|---|---|---|---|
| `screenshots/01-inbox-unread-badge-and-open-tasks.png` | RECEPTIONIST (Lan) `/reception/inbox` | Open Inbox | Top bar badge 2, two unread open tasks with Open task links | As expected |
| `screenshots/02-read-but-still-open.png` | RECEPTIONIST | Mark the top-up notice as read | Notice read, task still "Open - PENDING"; badge drops to 1 | As expected |
| `screenshots/03-resolved-after-confirmation.png` | RECEPTIONIST | Confirm TU-000001 on its own screen, show all notices | Top-up notice "Resolved - CONFIRMED"; withdrawal still open | As expected |

## API checks made in the same run

| Check | Result |
|---|---|
| Notices for TU-000001 after 4 inbox polls | 1 |
| Top-up status after marking its notice read | PENDING (read does not resolve) |
| Open / unread open after confirming the top-up | 1 / 1 (only the withdrawal) |
| Member calling `GET /reception/inbox` | 403 |
| Any 9+ digit number (account number) in notice text | none |
