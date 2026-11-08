-- MW-04: wallet top-up requests. Creating a request (and showing its QR) only records PENDING; a Receptionist
-- confirms after checking bank evidence, which credits the wallet exactly once through the ledger.
-- SIMULATED requests are demo-only and credit the separate SIMULATED pool. Additive only.
CREATE TABLE dbo.wallet_topup (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    topup_code          AS ('TU-' + RIGHT('000000' + CAST(id AS VARCHAR(20)), 6)) PERSISTED,
    transfer_reference  AS ('SPW' + CAST(id AS VARCHAR(20))) PERSISTED,
    member_id           BIGINT         NOT NULL,
    mode                VARCHAR(10)    NOT NULL,
    amount              DECIMAL(14,2)  NOT NULL,
    status              VARCHAR(15)    NOT NULL,
    idempotency_key     VARCHAR(80)    NULL,
    beneficiary_bin     VARCHAR(10)    NULL,
    beneficiary_account VARCHAR(30)    NULL,
    beneficiary_name    NVARCHAR(60)   NULL,
    received_amount     DECIMAL(14,2)  NULL,
    bank_reference      VARCHAR(60)    NULL,
    review_note         NVARCHAR(300)  NULL,
    reviewed_by         BIGINT         NULL,
    reviewed_at         DATETIME2      NULL,
    ledger_entry_id     BIGINT         NULL,
    created_at          DATETIME2      NOT NULL,
    updated_at          DATETIME2      NOT NULL,
    version             BIGINT         NOT NULL CONSTRAINT df_wallet_topup_version DEFAULT 0,
    CONSTRAINT pk_wallet_topup PRIMARY KEY (id),
    CONSTRAINT fk_wallet_topup_member FOREIGN KEY (member_id) REFERENCES dbo.member_profile(user_id),
    CONSTRAINT fk_wallet_topup_reviewer FOREIGN KEY (reviewed_by) REFERENCES dbo.user_account(id),
    CONSTRAINT fk_wallet_topup_ledger FOREIGN KEY (ledger_entry_id) REFERENCES dbo.wallet_ledger_entry(id),
    CONSTRAINT ck_wallet_topup_mode CHECK (mode IN ('ACTUAL', 'SIMULATED')),
    CONSTRAINT ck_wallet_topup_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_wallet_topup_amount CHECK (amount >= 10000 AND amount <= 50000000 AND amount = ROUND(amount, 0)),
    -- A confirmed top-up always names its reviewer, the received amount and the single ledger credit.
    CONSTRAINT ck_wallet_topup_confirmed CHECK (status <> 'CONFIRMED' OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL
        AND ledger_entry_id IS NOT NULL AND received_amount = amount)),
    -- Real money needs the bank's own reference; demo money never carries one.
    CONSTRAINT ck_wallet_topup_reference CHECK ((mode = 'ACTUAL' AND (status <> 'CONFIRMED' OR bank_reference IS NOT NULL))
        OR (mode = 'SIMULATED' AND bank_reference IS NULL))
);

CREATE UNIQUE INDEX ux_wallet_topup_code ON dbo.wallet_topup(topup_code);
CREATE UNIQUE INDEX ux_wallet_topup_bank_reference ON dbo.wallet_topup(bank_reference) WHERE bank_reference IS NOT NULL;
CREATE UNIQUE INDEX ux_wallet_topup_ledger ON dbo.wallet_topup(ledger_entry_id) WHERE ledger_entry_id IS NOT NULL;
CREATE UNIQUE INDEX ux_wallet_topup_idempotency ON dbo.wallet_topup(member_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_wallet_topup_status ON dbo.wallet_topup(status, created_at);
CREATE INDEX ix_wallet_topup_member ON dbo.wallet_topup(member_id, id DESC);
