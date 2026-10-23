/*
 * Sportify Center - profile photos for the six demo coaches.
 * The image files live in the repository: frontend/public/images/coaches/*.jpg (Unsplash License stock portraits,
 * demo only; download them once with download-coach-photos.ps1). The web app serves them, so no internet is needed.
 * Isolated sportify_demo only, after 01-staff-accounts.sql and explicit session approval. Existing photos are preserved.
 * Replace with real staff photos (with their consent) before any real use.
 */
SET NOCOUNT OFF;
SET XACT_ABORT ON;

-- Fail closed: an explicit session opt-in is required even in the dedicated isolated demo database.
IF DB_NAME() <> N'sportify_demo'
   OR ISNULL(TRY_CAST(SESSION_CONTEXT(N'sportify_demo_seed_approved') AS INT), 0) <> 1
    THROW 51400, 'Demo scripts require sportify_demo and explicit session approval; never use shared data.', 1;
IF @@TRANCOUNT <> 0
    THROW 51402, 'Run demo setup outside an existing transaction; caller work must be preserved.', 1;


UPDATE u SET avatar_url = p.url, updated_at = SYSDATETIME()
FROM user_account u JOIN (VALUES
    (N'khoa.nguyen@sportifycenter.vn', N'/images/coaches/khoa-nguyen.jpg'),
    (N'ha.tran@sportifycenter.vn', N'/images/coaches/ha-tran.jpg'),
    (N'bao.le@sportifycenter.vn', N'/images/coaches/bao-le.jpg'),
    (N'long.pham@sportifycenter.vn', N'/images/coaches/long-pham.jpg'),
    (N'tram.vo@sportifycenter.vn', N'/images/coaches/tram-vo.jpg'),
    (N'huy.dang@sportifycenter.vn', N'/images/coaches/huy-dang.jpg')
) AS p(email, url) ON LOWER(u.email) = p.email
JOIN [role] r ON r.id = u.role_id AND r.code = N'COACH'
JOIN coach_profile cp ON cp.user_id = u.id AND cp.is_public = 1
WHERE u.status = N'ACTIVE' AND u.avatar_url IS NULL;

SELECT full_name, email, avatar_url FROM user_account WHERE avatar_url LIKE N'/images/coaches/%' ORDER BY full_name;
