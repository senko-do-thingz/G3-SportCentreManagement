/*
 * Sportify Center - profile photos for the six demo coaches.
 * The image files live in the repository: frontend/public/images/coaches/*.jpg (Unsplash License stock portraits,
 * demo only; download them once with download-coach-photos.ps1). The web app serves them, so no internet is needed.
 * Run in SSMS on database "sportify" after 01-staff-accounts.sql. Expect 6 rows in the result grid.
 * Replace with real staff photos (with their consent) before any real use.
 */
SET NOCOUNT OFF;

UPDATE u SET avatar_url = p.url, updated_at = SYSDATETIME()
FROM user_account u JOIN (VALUES
    (N'khoa.nguyen@sportifycenter.vn', N'/images/coaches/khoa-nguyen.jpg'),
    (N'ha.tran@sportifycenter.vn', N'/images/coaches/ha-tran.jpg'),
    (N'bao.le@sportifycenter.vn', N'/images/coaches/bao-le.jpg'),
    (N'long.pham@sportifycenter.vn', N'/images/coaches/long-pham.jpg'),
    (N'tram.vo@sportifycenter.vn', N'/images/coaches/tram-vo.jpg'),
    (N'huy.dang@sportifycenter.vn', N'/images/coaches/huy-dang.jpg')
) AS p(email, url) ON LOWER(u.email) = p.email;

SELECT full_name, email, avatar_url FROM user_account WHERE avatar_url LIKE N'/images/coaches/%' ORDER BY full_name;
