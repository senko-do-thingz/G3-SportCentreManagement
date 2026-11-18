IF OBJECT_ID(N'dbo.payment', N'U') IS NULL
    OR OBJECT_ID(N'dbo.ck_payment_settlement_provider', N'C') IS NULL
    THROW 51040, 'V69 requires the payment table and ck_payment_settlement_provider constraint.', 1;
GO

ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_settlement_provider;
GO

ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_settlement_provider CHECK (
    settlement_provider IS NULL
    OR settlement_provider IN ('SPORTIFY_SIMULATOR', 'DESK_CASH', 'MANUAL_TRANSFER', 'GATEWAY', 'PAYOS')
);
GO

ALTER TABLE dbo.payment CHECK CONSTRAINT ck_payment_settlement_provider;
GO
