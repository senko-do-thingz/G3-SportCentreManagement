-- Retire Wallet data and schema from both existing and freshly migrated databases.
-- Earlier V50-V53 files must remain unchanged: Flyway verifies their installed checksums.
-- Every statement in this version runs in Flyway's SQL Server migration transaction.
-- The seven Wallet tables, their ledger, and Wallet-funded payment history are erased.
-- Member accounts, bookings, cards, registrations and non-Wallet payments are preserved.

-- Preserve SQL Server rowcount reporting for JDBC update counts.

DECLARE @wallet_payments TABLE (id BIGINT PRIMARY KEY);
INSERT INTO @wallet_payments (id)
SELECT id FROM dbo.payment WHERE payment_method = 'WALLET';

DECLARE @wallet_invoices TABLE (id BIGINT PRIMARY KEY);
INSERT INTO @wallet_invoices (id)
SELECT id FROM dbo.invoice WHERE payment_id IN (SELECT id FROM @wallet_payments);

DECLARE @wallet_refunds TABLE (id BIGINT PRIMARY KEY);
INSERT INTO @wallet_refunds (id)
SELECT id FROM dbo.refund_request WHERE payment_id IN (SELECT id FROM @wallet_payments);

-- A Wallet transaction must not also be a QR transaction. Stop instead of touching QR records.
IF EXISTS (SELECT 1 FROM dbo.simulated_qr_session q JOIN @wallet_payments w ON w.id = q.payment_id)
   OR EXISTS (SELECT 1 FROM dbo.simulated_payment_transaction t JOIN @wallet_payments w ON w.id = t.payment_id)
   OR EXISTS (SELECT 1 FROM dbo.operational_qr_settlement q JOIN @wallet_payments w ON w.id = q.payment_id)
    THROW 51063, 'Wallet removal stopped: a Wallet payment is linked to a QR settlement.', 1;

-- Remove Wallet-only notifications and payment audit history without deleting other users' events.
DELETE FROM dbo.notification
WHERE event_key LIKE 'WALLET[_]%'
   OR title LIKE N'%wallet%' OR content LIKE N'%wallet%';

DELETE FROM dbo.activity_log
WHERE (entity_type = 'PAYMENT' AND entity_id IN (SELECT id FROM @wallet_payments))
   OR (entity_type = 'INVOICE' AND entity_id IN (SELECT id FROM @wallet_invoices))
   OR (entity_type IN ('REFUND', 'REFUND_REQUEST') AND entity_id IN (SELECT id FROM @wallet_refunds))
   OR entity_type LIKE 'WALLET%'
   OR action LIKE 'WALLET[_]%'
   OR summary LIKE N'%wallet%' OR details LIKE N'%wallet%';

-- Financial append-only events, if any, are exclusively removed for Wallet-funded payments
-- and their refunds. The trigger is restored before the migration can commit.
IF EXISTS (
    SELECT 1 FROM dbo.cash_event e
    WHERE e.payment_id IN (SELECT id FROM @wallet_payments)
       OR e.original_method = 'WALLET'
       OR EXISTS (SELECT 1 FROM dbo.refund_request r
                  JOIN @wallet_payments w ON w.id = r.payment_id
                  WHERE r.id = e.refund_request_id)
)
BEGIN
    DISABLE TRIGGER dbo.tr_cash_event_immutable ON dbo.cash_event;
    DELETE e FROM dbo.cash_event e
    WHERE e.payment_id IN (SELECT id FROM @wallet_payments)
       OR e.original_method = 'WALLET'
       OR EXISTS (SELECT 1 FROM dbo.refund_request r
                  JOIN @wallet_payments w ON w.id = r.payment_id
                  WHERE r.id = e.refund_request_id);
    ENABLE TRIGGER dbo.tr_cash_event_immutable ON dbo.cash_event;
END;

DELETE FROM dbo.cash_capture_ticket
WHERE (event_type = 'COLLECTION' AND source_id IN (SELECT id FROM @wallet_payments))
   OR (event_type = 'REFUND_PAYOUT' AND source_id IN
       (SELECT id FROM dbo.refund_request WHERE payment_id IN (SELECT id FROM @wallet_payments)));

-- Remove refund and invoice records of Wallet payments only. Invoice lines are ON DELETE CASCADE.
DELETE FROM dbo.refund_request WHERE payment_id IN (SELECT id FROM @wallet_payments);
DELETE FROM dbo.invoice WHERE payment_id IN (SELECT id FROM @wallet_payments);

-- Preserve a member's active card/package and existing bookings, but detach the retired payment.
UPDATE dbo.member_card SET payment_id = NULL WHERE payment_id IN (SELECT id FROM @wallet_payments);
UPDATE dbo.sport_package_registration SET payment_id = NULL
WHERE payment_id IN (SELECT id FROM @wallet_payments);

-- Remove child Wallet-specific tables before their parent ledger/wallet tables.
DROP TABLE IF EXISTS dbo.wallet_withdrawal;
DROP TABLE IF EXISTS dbo.wallet_topup;
DROP TABLE IF EXISTS dbo.wallet_pending_operation;
DROP TABLE IF EXISTS dbo.member_payout_account;
DROP TABLE IF EXISTS dbo.payment_wallet_settlement;
DROP TABLE IF EXISTS dbo.wallet_ledger_entry;
DROP TABLE IF EXISTS dbo.member_wallet;

-- A Wallet-funded payment is not a valid payment history entry after retirement.
DELETE FROM dbo.payment WHERE id IN (SELECT id FROM @wallet_payments);

-- Reject future WALLET values at the SQL boundary as well as in Java.
ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_method;
ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_method
    CHECK (payment_method IN ('CASH', 'CREDIT_CARD', 'BANK_TRANSFER', 'MOMO', 'VNPAY', 'ZALOPAY'));

IF EXISTS (SELECT 1 FROM dbo.payment WHERE payment_method = 'WALLET')
    THROW 51063, 'Wallet removal left a WALLET payment behind.', 1;
IF EXISTS (SELECT 1 FROM dbo.notification
    WHERE event_key LIKE 'WALLET[_]%' OR title LIKE N'%wallet%' OR content LIKE N'%wallet%')
    THROW 51063, 'Wallet removal left Wallet notifications behind.', 1;
IF EXISTS (SELECT 1 FROM sys.tables WHERE schema_id = SCHEMA_ID('dbo')
    AND name IN ('member_wallet','wallet_ledger_entry','wallet_topup',
                 'wallet_pending_operation','payment_wallet_settlement',
                 'member_payout_account','wallet_withdrawal'))
    THROW 51063, 'Wallet removal left Wallet tables behind.', 1;
