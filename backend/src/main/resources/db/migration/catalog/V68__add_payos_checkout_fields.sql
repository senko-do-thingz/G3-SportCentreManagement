ALTER TABLE dbo.payment ADD
    payos_payment_link_id VARCHAR(64) NULL,
    payos_checkout_url VARCHAR(2048) NULL,
    payos_qr_code VARCHAR(1500) NULL,
    payos_transaction_reference VARCHAR(100) NULL;
GO

CREATE UNIQUE INDEX uq_payment_payos_payment_link_id
    ON dbo.payment (payos_payment_link_id)
    WHERE payos_payment_link_id IS NOT NULL;
GO

CREATE UNIQUE INDEX uq_payment_payos_transaction_reference
    ON dbo.payment (payos_transaction_reference)
    WHERE payos_transaction_reference IS NOT NULL;
GO

ALTER TABLE dbo.payment ADD CONSTRAINT ck_payment_payos_link_fields
    CHECK ((payos_payment_link_id IS NULL AND payos_checkout_url IS NULL AND payos_qr_code IS NULL)
        OR (payos_payment_link_id IS NOT NULL AND payos_checkout_url IS NOT NULL));
GO
