-- MW-06: Member payout account and wallet withdrawal requests. A request reserves funds through the ledger at once;
-- a Receptionist approves or rejects it; APPROVED is not a transfer. COMPLETED needs a separate step with a unique
-- payout reference (real money) and SIMULATED_COMPLETED is the only end state for demo funds. Additive only.
CREATE TABLE dbo.member_payout_account (
    member_id       BIGINT         NOT NULL,
    bank_bin        VARCHAR(10)    NOT NULL,
    bank_name       NVARCHAR(100)  NOT NULL,
    account_number  VARCHAR(30)    NOT NULL,
    account_holder  NVARCHAR(100)  NOT NULL,
    updated_at      DATETIME2      NOT NULL,
    version         BIGINT         NOT NULL CONSTRAINT df_payout_account_version DEFAULT 0,
    CONSTRAINT pk_member_payout_account PRIMARY KEY (member_id),
    CONSTRAINT fk_payout_account_member FOREIGN KEY (member_id) REFERENCES dbo.member_profile(user_id),
    CONSTRAINT ck_payout_account_number CHECK (LEN(account_number) BETWEEN 6 AND 19 AND account_number NOT LIKE '%[^0-9]%'),
    CONSTRAINT ck_payout_account_bin CHECK (LEN(bank_bin) = 6 AND bank_bin NOT LIKE '%[^0-9]%')
);

CREATE TABLE dbo.wallet_withdrawal (
    id                 BIGINT IDENTITY(1,1) NOT NULL,
    withdrawal_code    AS ('WD-' + RIGHT('000000' + CAST(id AS VARCHAR(20)), 6)) PERSISTED,
    member_id          BIGINT         NOT NULL,
    pool               VARCHAR(10)    NOT NULL,
    amount             DECIMAL(14,2)  NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    idempotency_key    VARCHAR(80)    NULL,
    bank_bin           VARCHAR(10)    NULL,
    bank_name          NVARCHAR(100)  NULL,
    account_number     VARCHAR(30)    NULL,
    account_holder     NVARCHAR(100)  NULL,
    reserve_entry_id   BIGINT         NULL,
    release_entry_id   BIGINT         NULL,
    payout_entry_id    BIGINT         NULL,
    review_note        NVARCHAR(300)  NULL,
    reviewed_by        BIGINT         NULL,
    reviewed_at        DATETIME2      NULL,
    payout_reference   VARCHAR(60)    NULL,
    completed_by       BIGINT         NULL,
    completed_at       DATETIME2      NULL,
    created_at         DATETIME2      NOT NULL,
    updated_at         DATETIME2      NOT NULL,
    version            BIGINT         NOT NULL CONSTRAINT df_wallet_withdrawal_version DEFAULT 0,
    CONSTRAINT pk_wallet_withdrawal PRIMARY KEY (id),
    CONSTRAINT fk_withdrawal_member FOREIGN KEY (member_id) REFERENCES dbo.member_profile(user_id),
    CONSTRAINT fk_withdrawal_reserve FOREIGN KEY (reserve_entry_id) REFERENCES dbo.wallet_ledger_entry(id),
    CONSTRAINT fk_withdrawal_release FOREIGN KEY (release_entry_id) REFERENCES dbo.wallet_ledger_entry(id),
    CONSTRAINT fk_withdrawal_payout FOREIGN KEY (payout_entry_id) REFERENCES dbo.wallet_ledger_entry(id),
    CONSTRAINT fk_withdrawal_reviewer FOREIGN KEY (reviewed_by) REFERENCES dbo.user_account(id),
    CONSTRAINT fk_withdrawal_completer FOREIGN KEY (completed_by) REFERENCES dbo.user_account(id),
    CONSTRAINT ck_withdrawal_pool CHECK (pool IN ('ACTUAL', 'SIMULATED')),
    CONSTRAINT ck_withdrawal_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED', 'FAILED', 'SIMULATED_COMPLETED')),
    CONSTRAINT ck_withdrawal_amount CHECK (amount >= 10000 AND amount <= 50000000 AND amount = ROUND(amount, 0)),
    -- Real money always goes to a snapshot of the confirmed payout account; demo money never claims a bank payout.
    CONSTRAINT ck_withdrawal_account CHECK (pool = 'SIMULATED' OR (bank_bin IS NOT NULL AND account_number IS NOT NULL AND account_holder IS NOT NULL)),
    CONSTRAINT ck_withdrawal_end_state CHECK ((pool = 'ACTUAL' AND status <> 'SIMULATED_COMPLETED') OR (pool = 'SIMULATED' AND status <> 'COMPLETED')),
    CONSTRAINT ck_withdrawal_completed CHECK (status <> 'COMPLETED' OR (payout_reference IS NOT NULL AND payout_entry_id IS NOT NULL
        AND completed_by IS NOT NULL AND completed_at IS NOT NULL)),
    CONSTRAINT ck_withdrawal_simulated_completed CHECK (status <> 'SIMULATED_COMPLETED' OR (payout_reference IS NULL
        AND payout_entry_id IS NOT NULL AND completed_by IS NOT NULL)),
    CONSTRAINT ck_withdrawal_released CHECK (status NOT IN ('REJECTED', 'FAILED') OR release_entry_id IS NOT NULL)
);

CREATE UNIQUE INDEX ux_withdrawal_code ON dbo.wallet_withdrawal(withdrawal_code);
CREATE UNIQUE INDEX ux_withdrawal_reserve ON dbo.wallet_withdrawal(reserve_entry_id) WHERE reserve_entry_id IS NOT NULL;
CREATE UNIQUE INDEX ux_withdrawal_release ON dbo.wallet_withdrawal(release_entry_id) WHERE release_entry_id IS NOT NULL;
CREATE UNIQUE INDEX ux_withdrawal_payout ON dbo.wallet_withdrawal(payout_entry_id) WHERE payout_entry_id IS NOT NULL;
CREATE UNIQUE INDEX ux_withdrawal_payout_reference ON dbo.wallet_withdrawal(payout_reference) WHERE payout_reference IS NOT NULL;
CREATE UNIQUE INDEX ux_withdrawal_idempotency ON dbo.wallet_withdrawal(member_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_withdrawal_status ON dbo.wallet_withdrawal(status, created_at);
CREATE INDEX ix_withdrawal_member ON dbo.wallet_withdrawal(member_id, id DESC);
