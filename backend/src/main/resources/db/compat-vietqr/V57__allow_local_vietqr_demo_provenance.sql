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
