# Shared demo data: coaches and receptionists

Two SQL scripts give every team member the same staff accounts and coach cards on the Home page.
They are run by hand, once per database, in SQL Server Management Studio (SSMS). They are not Flyway
migrations, so ordinary `dev` and `prod` startup never adds these people automatically.

| Order | File | What it does |
|---|---|---|
| 1 | `01-staff-accounts.sql` | Creates 6 coaches (one per sport) and 2 receptionists, fills each coach's headline, short bio, years, specialties and one certificate (not yet verified), and leaves all existing accounts unchanged. |
| 2 | `02-coach-photos.sql` | Points each coach's photo at the image files in `frontend/public/images/coaches/` (Unsplash License stock portraits, 640 x 640, demo only). The files are part of the repository, so every machine shows the same photos without internet. Skip it if the team uploads real photos with Edit coach profile. |

The photo files are added to the repository once by one person with `download-coach-photos.ps1`
(PowerShell, from the repository root: `powershell -File scripts\demo-data\download-coach-photos.ps1 -ApprovedDemoAssets`),
then committed. Everyone else gets them with `git pull`.

Password for all eight accounts: `SportifyDemo2026!` (the same demo password as the README).

## How to run

1. Obtain explicit owner approval for a disposable database named `sportify_demo`. Never use a real/shared database.
   Provision it through the approved disposable setup; these instructions do not authorize migrations or data writes.
2. In that isolated database only, an authorized operator must opt in for the current SSMS session with
   `EXEC sys.sp_set_session_context @key=N'sportify_demo_seed_approved', @value=1;`.
   Both SQL scripts reject any other database and reject sessions without this opt-in.
3. In SSMS open `01-staff-accounts.sql`, choose the approved isolated database `sportify_demo`, press F5. The last result grid shows 8 rows:
   6 `COACH` rows with headline, years, sport and `certificates = 1`, and 2 `RECEPTIONIST` rows.
4. Open `02-coach-photos.sql`, press F5. The last grid shows coaches with existing bundled photo paths; existing custom photos are preserved.
5. Reload the Home page to inspect the approved demo data. The downloader preserves existing files and requires explicit approval for missing demo assets.

`01-staff-accounts.sql` now stops if any listed email already exists, before writing profiles. It does not refresh
or hide existing accounts. `02-coach-photos.sql` fills only null photos for the exact listed email identities with an
active COACH account and public coach profile. It never replaces existing media. Replays are fail-closed or preserve
existing photos; inspect any partial fixture manually instead of broad repair. No script was executed for BE-HOME-114.

The historical catalog V32 migration is still part of ordinary Flyway locations. These demo guards do not change
that history or prove its physical inventory is approved. Owner/DBA review is required before deploying V32 to a
new real/shared environment. Do not edit an applied migration, run Flyway repair, delete rows, or invent inventory.

## If something fails

- "A phone number in this script already belongs to another account": change that phone number in the `@Staff`
  table at the top of `01-staff-accounts.sql` and run it again.
- "Role COACH or RECEPTIONIST is missing": start the backend once, then run the script again.
- Vietnamese names look broken in SSMS: reopen the file with File > Open > File..., Open With..., "SQL Server Query
  Editor with Encoding", and choose Unicode (UTF-8).

## Accounts

| Name | E-mail | Role | Sport |
|---|---|---|---|
| Nguyễn Minh Khoa | khoa.nguyen@sportifycenter.vn | Coach | Badminton |
| Trần Thu Hà | ha.tran@sportifycenter.vn | Coach | Swimming |
| Lê Quốc Bảo | bao.le@sportifycenter.vn | Coach | Football |
| Phạm Hoàng Long | long.pham@sportifycenter.vn | Coach | Basketball |
| Võ Ngọc Trâm | tram.vo@sportifycenter.vn | Coach | Volleyball |
| Đặng Gia Huy | huy.dang@sportifycenter.vn | Coach | Tennis |
| Hoàng Mai Anh | mai.hoang@sportifycenter.vn | Receptionist | - |
| Bùi Thanh Tùng | tung.bui@sportifycenter.vn | Receptionist | - |

The people and certificates are invented for the class demo. Replace them, and use real photos only with each
person's consent, before any real use.
