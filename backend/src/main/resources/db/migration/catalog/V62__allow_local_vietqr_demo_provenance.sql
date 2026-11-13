IF COL_LENGTH(N'dbo.payment', N'vietqr_environment') IS NULL
    THROW 51040, 'V62 requires VietQR payment fields from V60.', 1;
GO

IF OBJECT_ID(N'dbo.ck_payment_simulated_consistency', N'C') IS NULL
    THROW 51040, 'V62 requires ck_payment_simulated_consistency on dbo.payment.', 1;
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
                 AND settlement_provider IN ('GATEWAY', 'LOCAL_DEMO')
                 AND simulated_transaction_id IS NULL
                 AND vietqr_environment = 'SANDBOX'
            THEN 1
            WHEN (settlement_type = 'ACTUAL' OR settlement_type IS NULL)
                 AND simulated_transaction_id IS NULL
                 AND (settlement_provider IS NULL OR settlement_provider NOT IN ('SPORTIFY_SIMULATOR', 'LOCAL_DEMO'))
            THEN 1
            ELSE 0
        END = 1
    )
)
    THROW 51044, 'Existing payment records violate the simulated settlement provenance invariant.', 1;
GO

ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_simulated_consistency;
GO

ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_simulated_consistency CHECK (
    CASE
        WHEN settlement_type = 'SIMULATED' AND settlement_provider = 'SPORTIFY_SIMULATOR'
             AND simulated_transaction_id IS NOT NULL THEN 1
        WHEN settlement_type = 'SIMULATED' AND settlement_provider IN ('GATEWAY', 'LOCAL_DEMO')
             AND simulated_transaction_id IS NULL AND vietqr_environment = 'SANDBOX' THEN 1
        WHEN (settlement_type = 'ACTUAL' OR settlement_type IS NULL)
             AND simulated_transaction_id IS NULL
             AND (settlement_provider IS NULL OR settlement_provider NOT IN ('SPORTIFY_SIMULATOR', 'LOCAL_DEMO')) THEN 1
        ELSE 0
    END = 1
);
GO

ALTER TABLE dbo.payment CHECK CONSTRAINT ck_payment_simulated_consistency;
GO
