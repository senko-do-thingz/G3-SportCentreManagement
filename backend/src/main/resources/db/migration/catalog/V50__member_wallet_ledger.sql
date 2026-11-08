-- MW-03: internal Sportify Wallet. One wallet per Member with separate ACTUAL (staff-verified real money) and
-- SIMULATED (demo only) pools, and an append-only ledger. Every balance change is one ledger row with a unique
-- operation key, so a replayed request can never credit or debit twice. Additive only.
CREATE TABLE dbo.member_wallet (
    id                    BIGINT IDENTITY(1,1) NOT NULL,
    member_id             BIGINT         NOT NULL,
    actual_available      DECIMAL(14,2)  NOT NULL CONSTRAINT df_wallet_actual_available DEFAULT 0,
    actual_reserved       DECIMAL(14,2)  NOT NULL CONSTRAINT df_wallet_actual_reserved DEFAULT 0,
    simulated_available   DECIMAL(14,2)  NOT NULL CONSTRAINT df_wallet_simulated_available DEFAULT 0,
    simulated_reserved    DECIMAL(14,2)  NOT NULL CONSTRAINT df_wallet_simulated_reserved DEFAULT 0,
    created_at            DATETIME2      NOT NULL,
    updated_at            DATETIME2      NOT NULL,
    version               BIGINT         NOT NULL CONSTRAINT df_wallet_version DEFAULT 0,
    CONSTRAINT pk_member_wallet PRIMARY KEY (id),
    CONSTRAINT ux_member_wallet_member UNIQUE (member_id),
    CONSTRAINT fk_member_wallet_member FOREIGN KEY (member_id) REFERENCES dbo.member_profile(user_id),
    CONSTRAINT ck_member_wallet_non_negative CHECK (actual_available >= 0 AND actual_reserved >= 0
        AND simulated_available >= 0 AND simulated_reserved >= 0),
    CONSTRAINT ck_member_wallet_whole_vnd CHECK (actual_available = ROUND(actual_available, 0)
        AND actual_reserved = ROUND(actual_reserved, 0) AND simulated_available = ROUND(simulated_available, 0)
        AND simulated_reserved = ROUND(simulated_reserved, 0))
);

CREATE TABLE dbo.wallet_ledger_entry (
    id               BIGINT IDENTITY(1,1) NOT NULL,
    wallet_id        BIGINT         NOT NULL,
    operation_key    VARCHAR(80)    NOT NULL,
    entry_type       VARCHAR(30)    NOT NULL,
    pool             VARCHAR(10)    NOT NULL,
    amount           DECIMAL(14,2)  NOT NULL,
    available_after  DECIMAL(14,2)  NOT NULL,
    reserved_after   DECIMAL(14,2)  NOT NULL,
    source_type      VARCHAR(30)    NOT NULL,
    source_id        BIGINT         NOT NULL,
    description      NVARCHAR(200)  NOT NULL,
    created_by       BIGINT         NULL,
    created_at       DATETIME2      NOT NULL,
    CONSTRAINT pk_wallet_ledger_entry PRIMARY KEY (id),
    CONSTRAINT ux_wallet_ledger_operation UNIQUE (operation_key),
    CONSTRAINT fk_wallet_ledger_wallet FOREIGN KEY (wallet_id) REFERENCES dbo.member_wallet(id),
    CONSTRAINT fk_wallet_ledger_actor FOREIGN KEY (created_by) REFERENCES dbo.user_account(id),
    CONSTRAINT ck_wallet_ledger_type CHECK (entry_type IN ('TOPUP_CREDIT', 'PURCHASE_DEBIT', 'COMPENSATION_CREDIT',
        'WITHDRAWAL_RESERVE', 'WITHDRAWAL_RELEASE', 'WITHDRAWAL_PAYOUT')),
    CONSTRAINT ck_wallet_ledger_pool CHECK (pool IN ('ACTUAL', 'SIMULATED')),
    -- Demo money can never pay for a real purchase.
    CONSTRAINT ck_wallet_ledger_purchase_actual CHECK (entry_type <> 'PURCHASE_DEBIT' OR pool = 'ACTUAL'),
    CONSTRAINT ck_wallet_ledger_amount CHECK (amount > 0 AND amount = ROUND(amount, 0)),
    CONSTRAINT ck_wallet_ledger_after CHECK (available_after >= 0 AND reserved_after >= 0)
);

CREATE INDEX ix_wallet_ledger_wallet ON dbo.wallet_ledger_entry(wallet_id, id DESC);
CREATE INDEX ix_wallet_ledger_source ON dbo.wallet_ledger_entry(source_type, source_id);
GO

CREATE TRIGGER dbo.tr_wallet_ledger_immutable ON dbo.wallet_ledger_entry AFTER UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    THROW 51060, 'Wallet ledger entries are append-only.', 1;
END;
GO
