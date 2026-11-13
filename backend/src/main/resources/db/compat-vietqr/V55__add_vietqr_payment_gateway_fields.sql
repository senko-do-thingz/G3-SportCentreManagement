ALTER TABLE dbo.payment ADD
    vietqr_order_id VARCHAR(13) NULL,
    vietqr_content VARCHAR(23) NULL,
    vietqr_qr_code VARCHAR(1500) NULL,
    vietqr_qr_link VARCHAR(2048) NULL,
    vietqr_transaction_id VARCHAR(100) NULL,
    vietqr_environment VARCHAR(12) NULL;
GO

CREATE UNIQUE INDEX uq_payment_vietqr_order_id
    ON dbo.payment (vietqr_order_id)
    WHERE vietqr_order_id IS NOT NULL;
GO

CREATE UNIQUE INDEX uq_payment_vietqr_transaction_id
    ON dbo.payment (vietqr_transaction_id)
    WHERE vietqr_transaction_id IS NOT NULL;
GO
