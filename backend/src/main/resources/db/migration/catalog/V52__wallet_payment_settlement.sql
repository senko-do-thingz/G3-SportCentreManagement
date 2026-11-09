-- MW-05: an existing pending purchase (membership card or sport package) can be settled from the Member's ACTUAL
-- wallet balance. WALLET is an internal settlement method, not an external payment channel. Each wallet-settled
-- payment links to exactly one wallet debit. Additive: the method list is extended, nothing is removed.
ALTER TABLE dbo.payment DROP CONSTRAINT ck_payment_method;
GO

ALTER TABLE dbo.payment WITH CHECK ADD CONSTRAINT ck_payment_method
    CHECK (payment_method IN ('CASH', 'CREDIT_CARD', 'BANK_TRANSFER', 'MOMO', 'VNPAY', 'ZALOPAY', 'WALLET'));
GO

CREATE TABLE dbo.payment_wallet_settlement (
    payment_id       BIGINT     NOT NULL,
    ledger_entry_id  BIGINT     NOT NULL,
    created_at       DATETIME2  NOT NULL,
    CONSTRAINT pk_payment_wallet_settlement PRIMARY KEY (payment_id),
    CONSTRAINT ux_payment_wallet_settlement_ledger UNIQUE (ledger_entry_id),
    CONSTRAINT fk_payment_wallet_settlement_payment FOREIGN KEY (payment_id) REFERENCES dbo.payment(id),
    CONSTRAINT fk_payment_wallet_settlement_ledger FOREIGN KEY (ledger_entry_id) REFERENCES dbo.wallet_ledger_entry(id)
);
GO
