-- ==========================================
-- V25: BE-22 finance reports (F3-08 Financial Overview, F3-09 Transactions, F3-10 Revenue Reports, S3-Export)
-- vw_payment_ledger: one row per payment, every status, with member, invoice, item and sport.
-- vw_paid_payment:   the revenue source; SUCCESS payments only, filtered by payment_time.
-- The item of a payment is the package registration or the membership card that points to it
-- (both payment_id columns are unique since V21); otherwise the first invoice line says what was sold.
-- V23 (feat/f2-sessions-schedule) and V24 (feat/f3-refund-payment-link) are taken, so this starts at V25.
-- ==========================================

CREATE VIEW [vw_payment_ledger] AS
SELECT p.id                                    AS payment_id,
       p.payment_code,
       p.payment_status,
       p.payment_method,
       p.amount,
       p.reference_code,
       p.payment_time,
       p.created_at,
       COALESCE(p.payment_time, p.created_at)  AS ledger_time,
       p.member_id,
       mp.member_code,
       ua.full_name                            AS member_name,
       inv.invoice_id,
       inv.invoice_number,
       CASE
           WHEN r.id IS NOT NULL THEN 'SPORT_PACKAGE'
           WHEN c.id IS NOT NULL THEN 'MEMBERSHIP_CARD'
           ELSE il.item_type
       END                                     AS item_type,
       COALESCE(r.registration_code, c.card_code) AS item_code,
       CASE
           WHEN r.id IS NOT NULL THEN sp.name
           WHEN c.id IS NOT NULL THEN t.name + N' Card'
           ELSE il.description
       END                                     AS item_name,
       sp.sport_id,
       s.name                                  AS sport_name
FROM [payment] p
JOIN [member_profile] mp ON mp.user_id = p.member_id
JOIN [user_account] ua ON ua.id = p.member_id
OUTER APPLY (
    SELECT TOP 1 i.id AS invoice_id, i.invoice_number
    FROM [invoice] i
    WHERE i.payment_id = p.id
    ORDER BY i.id
) inv
LEFT JOIN [sport_package_registration] r ON r.payment_id = p.id
LEFT JOIN [sport_package] sp ON sp.id = r.package_id
LEFT JOIN [sport] s ON s.id = sp.sport_id
LEFT JOIN [member_card] c ON c.payment_id = p.id
LEFT JOIN [membership_card_tier] t ON t.id = c.tier_id
OUTER APPLY (
    SELECT TOP 1 l.item_type, l.description
    FROM [invoice_line] l
    WHERE l.invoice_id = inv.invoice_id
    ORDER BY l.id
) il;
GO

-- Columns of the planned view in docs/database/04, plus the item and sport used by the revenue reports.
CREATE VIEW [vw_paid_payment] AS
SELECT payment_id,
       payment_code,
       payment_time,
       CAST(payment_time AS DATE) AS paid_on,
       amount,
       payment_method,
       member_id,
       member_code,
       member_name,
       invoice_number,
       item_type,
       item_code,
       item_name,
       sport_id,
       sport_name
FROM [vw_payment_ledger]
WHERE payment_status = 'SUCCESS';
GO

-- The revenue reports filter SUCCESS payments by payment_time.
CREATE NONCLUSTERED INDEX [ix_payment_status_time]
    ON [payment]([payment_status], [payment_time])
    INCLUDE ([amount], [payment_method], [member_id]);
GO
