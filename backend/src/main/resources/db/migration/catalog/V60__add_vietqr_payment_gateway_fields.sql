IF COL_LENGTH(N'dbo.payment', N'vietqr_order_id') IS NULL
    ALTER TABLE dbo.payment ADD vietqr_order_id VARCHAR(13) NULL;
GO

IF COL_LENGTH(N'dbo.payment', N'vietqr_content') IS NULL
    ALTER TABLE dbo.payment ADD vietqr_content VARCHAR(23) NULL;
GO

IF COL_LENGTH(N'dbo.payment', N'vietqr_qr_code') IS NULL
    ALTER TABLE dbo.payment ADD vietqr_qr_code VARCHAR(1500) NULL;
GO

IF COL_LENGTH(N'dbo.payment', N'vietqr_qr_link') IS NULL
    ALTER TABLE dbo.payment ADD vietqr_qr_link VARCHAR(2048) NULL;
GO

IF COL_LENGTH(N'dbo.payment', N'vietqr_transaction_id') IS NULL
    ALTER TABLE dbo.payment ADD vietqr_transaction_id VARCHAR(100) NULL;
GO

IF COL_LENGTH(N'dbo.payment', N'vietqr_environment') IS NULL
    ALTER TABLE dbo.payment ADD vietqr_environment VARCHAR(12) NULL;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'uq_payment_vietqr_order_id'
      AND object_id = OBJECT_ID(N'dbo.payment', N'U')
)
    CREATE UNIQUE INDEX uq_payment_vietqr_order_id
        ON dbo.payment (vietqr_order_id)
        WHERE vietqr_order_id IS NOT NULL;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'uq_payment_vietqr_transaction_id'
      AND object_id = OBJECT_ID(N'dbo.payment', N'U')
)
    CREATE UNIQUE INDEX uq_payment_vietqr_transaction_id
        ON dbo.payment (vietqr_transaction_id)
        WHERE vietqr_transaction_id IS NOT NULL;
GO
