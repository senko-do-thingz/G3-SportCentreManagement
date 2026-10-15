-- ==========================================
-- V27: BE-21 refunds linked to payments (F3-07, F3-09 refund part)
-- One live refund per payment: a payment can have at most one refund request that is
-- PENDING, APPROVED or COMPLETED. A REJECTED request does not count, so the member may ask again.
-- The service checks this first and answers 409 PAYMENT_ALREADY_REFUNDED; this index is the
-- backstop for two requests that race (DataIntegrityViolationException, also 409).
-- Renumbered from V24 because main already contained V26.
-- ==========================================

-- Step 1: guard against existing duplicates before the unique index is created
IF EXISTS (
    SELECT [payment_id]
    FROM [refund_request]
    WHERE [payment_id] IS NOT NULL AND [status] IN ('PENDING', 'APPROVED', 'COMPLETED')
    GROUP BY [payment_id]
    HAVING COUNT(*) > 1
)
BEGIN
    THROW 50000, 'Cannot create unique index ux_refund_request_payment_live: more than one live refund per payment_id in refund_request', 1;
END;

-- Step 2: one live refund per payment
CREATE UNIQUE NONCLUSTERED INDEX [ux_refund_request_payment_live]
    ON [refund_request]([payment_id])
    WHERE [payment_id] IS NOT NULL AND [status] IN ('PENDING', 'APPROVED', 'COMPLETED');

-- Step 3: lookup of the refunds of a registration (quote and submit)
CREATE NONCLUSTERED INDEX [ix_refund_request_registration]
    ON [refund_request]([package_registration_id]);
