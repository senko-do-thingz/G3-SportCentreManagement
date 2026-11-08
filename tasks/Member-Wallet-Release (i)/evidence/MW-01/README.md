# MW-01 evidence - Member profile and password

- Commit under test: branch `fix/flow-1-mw-01-member-profile` at `origin/main` `e3e9a10` (no application code change in this task)
- Captured: 2026-11-08 15:05-15:08 Asia/Bangkok
- Browser: Google Chrome via Playwright (channel `chrome`), viewport 1440x900, light theme
- Backend: local `mvn spring-boot:run`, profiles `dev,demo`, port 18080, throwaway database `sportify_mw_evidence_1794125105` (fresh V1-V48 plus demo seeds). Not the shared `sportify` database.
- Frontend: local Vite dev server on port 3000
- Fixture: synthetic BE-07 demo members `mai.standard@example.com` and `bao.nocard@example.com`; phone, DOB and address values are invented
- Capture script: `ev.py` + `mw01.py` (kept outside the repository)

| File | Role and route | Steps | Expected | Observed |
|---|---|---|---|---|
| `screenshots/01-profile-editable.png` | MEMBER `/member/profile` | Fill DOB 1995-04-12, phone, goal, level, address | All fields editable | As expected |
| `screenshots/02-profile-persisted-after-reload.png` | MEMBER `/member/profile` | Save changes, reload the page | Values come back from the server | As expected |
| `screenshots/03-invalid-future-dob-rejected.png` | MEMBER `/member/profile` | Enter 2099-01-01, Save | Field error, nothing saved | As expected |
| `screenshots/04-password-changed-relogin.png` | MEMBER `/member/profile` -> `/login` | Change password with the correct current password | Session ends, login page explains why | As expected |

## API checks made in the same run

| Check | Result |
|---|---|
| `PUT /users/me` with `dateOfBirth` 2099-01-01 | 400; stored DOB stays 1995-04-12 |
| Login with the old password after the change | 401 |
| Login with the new password | 200 |
