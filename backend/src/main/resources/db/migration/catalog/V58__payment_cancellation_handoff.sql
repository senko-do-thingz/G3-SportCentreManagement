ALTER TABLE dbo.payment ADD submitted_for_verification_at datetime2 NULL;
ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_status;
ALTER TABLE dbo.payment ADD CONSTRAINT ck_payment_status CHECK (payment_status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED', 'CANCELLED'));
