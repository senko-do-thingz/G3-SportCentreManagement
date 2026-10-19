-- BE-22: forward-only finance reporting after normal V27 and opt-in demo V28/V29.
-- Historical finance V25/V28 are not part of the supported current-main cohort.
-- Reject unexpected objects before any DDL; Flyway owns the migration transaction.
IF OBJECT_ID(N'dbo.payment', N'U') IS NULL
    OR OBJECT_ID(N'dbo.invoice', N'U') IS NULL
    OR OBJECT_ID(N'dbo.invoice_line', N'U') IS NULL
    OR OBJECT_ID(N'dbo.member_profile', N'U') IS NULL
    OR OBJECT_ID(N'dbo.user_account', N'U') IS NULL
    OR OBJECT_ID(N'dbo.sport_package_registration', N'U') IS NULL
    OR OBJECT_ID(N'dbo.sport_package', N'U') IS NULL
    OR OBJECT_ID(N'dbo.sport', N'U') IS NULL
    OR OBJECT_ID(N'dbo.member_card', N'U') IS NULL
    OR OBJECT_ID(N'dbo.membership_card_tier', N'U') IS NULL
BEGIN
    THROW 51030, 'BE-22 requires the current-main tables in the dbo schema', 1;
END;

IF OBJECT_ID(N'dbo.vw_payment_ledger') IS NOT NULL
    OR OBJECT_ID(N'dbo.vw_paid_payment') IS NOT NULL
    OR EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID(N'dbo.payment') AND name = N'ix_payment_status_time')
BEGIN
    THROW 51030, 'BE-22 finance objects already exist; verify the database migration cohort', 1;
END;
GO

CREATE VIEW [dbo].[vw_payment_ledger] AS
SELECT p.id AS payment_id,
       p.payment_code,
       p.payment_status,
       p.payment_method,
       p.amount,
       p.reference_code,
       p.payment_time,
       p.created_at,
       COALESCE(p.payment_time, p.created_at) AS ledger_time,
       p.member_id,
       mp.member_code,
       ua.full_name AS member_name,
       inv.invoice_id,
       inv.invoice_number,
       CASE WHEN r.id IS NOT NULL THEN 'SPORT_PACKAGE'
            WHEN c.id IS NOT NULL THEN 'MEMBERSHIP_CARD'
            ELSE il.item_type END AS item_type,
       COALESCE(r.registration_code, c.card_code) AS item_code,
       CASE WHEN r.id IS NOT NULL THEN sp.name
            WHEN c.id IS NOT NULL THEN t.name + N' Card'
            ELSE il.description END AS item_name,
       sp.sport_id,
       s.name AS sport_name
FROM [dbo].[payment] p
JOIN [dbo].[member_profile] mp ON mp.user_id = p.member_id
JOIN [dbo].[user_account] ua ON ua.id = p.member_id
OUTER APPLY (
    SELECT TOP 1 i.id AS invoice_id, i.invoice_number
    FROM [dbo].[invoice] i WHERE i.payment_id = p.id ORDER BY i.id
) inv
LEFT JOIN [dbo].[sport_package_registration] r ON r.payment_id = p.id
LEFT JOIN [dbo].[sport_package] sp ON sp.id = r.package_id
LEFT JOIN [dbo].[sport] s ON s.id = sp.sport_id
LEFT JOIN [dbo].[member_card] c ON c.payment_id = p.id
LEFT JOIN [dbo].[membership_card_tier] t ON t.id = c.tier_id
OUTER APPLY (
    SELECT TOP 1 l.item_type, l.description
    FROM [dbo].[invoice_line] l WHERE l.invoice_id = inv.invoice_id ORDER BY l.id
) il;
GO

CREATE VIEW [dbo].[vw_paid_payment] AS
SELECT payment_id, payment_code, payment_time, CAST(payment_time AS DATE) AS paid_on,
       amount, payment_method, member_id, member_code, member_name, invoice_number,
       item_type, item_code, item_name, sport_id, sport_name
FROM [dbo].[vw_payment_ledger]
WHERE payment_status = 'SUCCESS';
GO

CREATE NONCLUSTERED INDEX [ix_payment_status_time]
    ON [dbo].[payment]([payment_status], [payment_time])
    INCLUDE ([amount], [payment_method], [member_id]);
GO
