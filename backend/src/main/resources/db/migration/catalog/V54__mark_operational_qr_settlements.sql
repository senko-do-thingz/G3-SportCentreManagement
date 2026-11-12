-- Only token-verified QR settlements created after this migration receive this marker.
-- Historical simulated transactions remain subject to synthetic-member quarantine.
CREATE TABLE dbo.operational_qr_settlement (
    payment_id BIGINT NOT NULL,
    simulated_transaction_id BIGINT NOT NULL,
    CONSTRAINT pk_operational_qr_settlement PRIMARY KEY (payment_id),
    CONSTRAINT uq_operational_qr_transaction UNIQUE (simulated_transaction_id),
    CONSTRAINT fk_operational_qr_payment FOREIGN KEY (payment_id) REFERENCES dbo.payment(id),
    CONSTRAINT fk_operational_qr_transaction FOREIGN KEY (simulated_transaction_id)
        REFERENCES dbo.simulated_payment_transaction(id)
);
