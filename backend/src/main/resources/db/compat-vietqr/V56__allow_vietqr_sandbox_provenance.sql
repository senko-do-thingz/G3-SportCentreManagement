IF COL_LENGTH(N'dbo.payment', N'vietqr_environment') IS NULL
    THROW 51040, 'V56 requires VietQR payment fields from V55.', 1;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = 'ck_payment_simulated_consistency'
      AND parent_object_id = OBJECT_ID(N'dbo.payment', N'U')
)
    THROW 51040, 'V56 requires ck_payment_simulated_consistency on dbo.payment.', 1;
GO

IF EXISTS (
    SELECT 1 FROM dbo.payment
    WHERE NOT (
        CASE
            WHEN settlement_type = 'SIMULATED'
                 AND settlement_provider = 'SPORTIFY_SIMULATOR'
                 AND simulated_transaction_id IS NOT NULL
            THEN 1
            WHEN settlement_type = 'SIMULATED'
                 AND settlement_provider = 'GATEWAY'
                 AND simulated_transaction_id IS NULL
                 AND vietqr_environment = 'SANDBOX'
            THEN 1
            WHEN (settlement_type = 'ACTUAL' OR settlement_type IS NULL)
                 AND simulated_transaction_id IS NULL
                 AND (settlement_provider IS NULL OR settlement_provider <> 'SPORTIFY_SIMULATOR')
            THEN 1
            ELSE 0
        END = 1
    )
)
    THROW 51044, 'Existing payment records violate the simulated settlement provenance invariant.', 1;
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
            WHEN settlement_type = 'SIMULATED'
                 AND settlement_provider = 'GATEWAY'
                 AND simulated_transaction_id IS NULL
                 AND vietqr_environment = 'SANDBOX'
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
