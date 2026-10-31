IF OBJECT_ID(N'dbo.payment', N'U') IS NULL
    THROW 51040, 'V36 requires dbo.payment table.', 1;

IF OBJECT_ID(N'dbo.simulated_payment_transaction', N'U') IS NULL
    THROW 51040, 'V36 requires dbo.simulated_payment_transaction table from V35.', 1;

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = 'ck_payment_simulated_consistency'
      AND parent_object_id = OBJECT_ID(N'dbo.payment', N'U')
)
    THROW 51040, 'V36 requires existing ck_payment_simulated_consistency constraint on dbo.payment.', 1;
GO

IF EXISTS (
    SELECT 1
    FROM dbo.payment
    WHERE NOT (
        CASE
            WHEN settlement_type = 'SIMULATED'
                 AND settlement_provider = 'SPORTIFY_SIMULATOR'
                 AND simulated_transaction_id IS NOT NULL
            THEN 1
            WHEN (settlement_type = 'ACTUAL' OR settlement_type IS NULL)
                 AND simulated_transaction_id IS NULL
                 AND (settlement_provider IS NULL OR settlement_provider <> 'SPORTIFY_SIMULATOR')
            THEN 1
            ELSE 0
        END = 1
    )
)
BEGIN
    THROW 51044, 'Existing payment records violate the strict simulated settlement provenance invariant.', 1;
END;
GO

ALTER TABLE dbo.payment
    DROP CONSTRAINT ck_payment_simulated_consistency;
GO

ALTER TABLE dbo.payment WITH CHECK
    ADD CONSTRAINT ck_payment_simulated_consistency CHECK (
        CASE
            WHEN settlement_type = 'SIMULATED'
                 AND settlement_provider = 'SPORTIFY_SIMULATOR'
                 AND simulated_transaction_id IS NOT NULL
            THEN 1
            WHEN (settlement_type = 'ACTUAL' OR settlement_type IS NULL)
                 AND simulated_transaction_id IS NULL
                 AND (settlement_provider IS NULL OR settlement_provider <> 'SPORTIFY_SIMULATOR')
            THEN 1
            ELSE 0
        END = 1
    );
GO

ALTER TABLE dbo.payment CHECK CONSTRAINT ck_payment_simulated_consistency;
GO
