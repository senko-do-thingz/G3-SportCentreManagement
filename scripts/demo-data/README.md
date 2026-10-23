# Shared demo data: coaches and receptionists

Two SQL scripts give every team member the same staff accounts and coach cards on the Home page.
They are run by hand, once per database, in SQL Server Management Studio (SSMS). They are not Flyway
migrations, so ordinary `dev` and `prod` startup never adds these people automatically.

| Order | File | What it does |
|---|---|---|
| 1 | `01-staff-accounts.sql` | Creates 6 coaches (one per sport) and 2 receptionists, fills each coach's headline, short bio, years, specialties and one certificate (not yet verified), and hides the old test coaches "An Nguyen" and "Be Nguyen" if they exist. |
| 2 | `02-coach-photos.sql` | Points each coach's photo at the image files in `frontend/public/images/coaches/` (Unsplash License stock portraits, 640 x 640, demo only). The files are part of the repository, so every machine shows the same photos without internet. Skip it if the team uploads real photos with Edit coach profile. |

The photo files are added to the repository once by one person with `download-coach-photos.ps1`
(PowerShell, from the repository root: `powershell -ExecutionPolicy Bypass -File scripts\demo-data\download-coach-photos.ps1`),
then committed. Everyone else gets them with `git pull`.

Password for all eight accounts: `SportifyDemo2026!` (the same demo password as the README).

## How to run

1. Start the backend once so all migrations are applied (V32 courts, V33 photo table).
2. In SSMS open `01-staff-accounts.sql`, choose database `sportify`, press F5. The last result grid shows 8 rows:
   6 `COACH` rows with headline, years, sport and `certificates = 1`, and 2 `RECEPTIONIST` rows.
3. Open `02-coach-photos.sql`, press F5. The last grid shows 6 coaches with a `/images/coaches/...` photo path.
4. Reload the Home page: Train with the best shows the six coaches, three at a time.

Both scripts can be run again safely: existing e-mails are not inserted twice and coach text is refreshed.

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
