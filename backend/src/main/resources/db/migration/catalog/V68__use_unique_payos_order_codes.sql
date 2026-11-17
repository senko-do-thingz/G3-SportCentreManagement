IF OBJECT_ID(N'dbo.payment', N'U') IS NULL
    OR COL_LENGTH(N'dbo.payment', N'payos_payment_link_id') IS NULL
    THROW 51041, 'V68 requires the PayOS payment-link columns.', 1;
GO

ALTER TABLE dbo.payment ADD payos_order_code BIGINT NULL;
GO

UPDATE dbo.payment
SET payos_order_code = id
WHERE payos_payment_link_id IS NOT NULL;
GO

IF EXISTS (SELECT 1 FROM dbo.payment WHERE payos_order_code <= 0 OR payos_order_code > 2147483647)
    THROW 51042, 'Existing PayOS order codes must fit the provider integer range.', 1;
GO

CREATE UNIQUE INDEX uq_payment_payos_order_code
    ON dbo.payment (payos_order_code)
    WHERE payos_order_code IS NOT NULL;
GO

ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_payos_order_code_range
    CHECK (payos_order_code IS NULL OR (payos_order_code > 0 AND payos_order_code <= 2147483647));
GO

ALTER TABLE dbo.payment CHECK CONSTRAINT ck_payment_payos_order_code_range;
GO
