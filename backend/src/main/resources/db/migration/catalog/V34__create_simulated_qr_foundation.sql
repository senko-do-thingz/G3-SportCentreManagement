-- Additive, intent-only foundation. No payment or cash accounting rows are changed.
CREATE TABLE dbo.simulated_qr_session (
    id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_simulated_qr_session PRIMARY KEY,
    payment_id BIGINT NOT NULL CONSTRAINT fk_simulated_qr_payment REFERENCES dbo.payment(id),
    owner_id BIGINT NOT NULL CONSTRAINT fk_simulated_qr_owner REFERENCES dbo.member_profile(user_id),
    bank_code VARCHAR(30) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    token_verifier CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    request_id UNIQUEIDENTIFIER NOT NULL,
    created_at_utc DATETIME2(7) NOT NULL,
    expires_at_utc DATETIME2(7) NOT NULL,
    state VARCHAR(40) NOT NULL,
    state_changed_at_utc DATETIME2(7) NOT NULL,
    active BIT NOT NULL,
    version BIGINT NOT NULL CONSTRAINT df_simulated_qr_version DEFAULT 0,
    CONSTRAINT uq_simulated_qr_verifier UNIQUE(token_verifier),
    CONSTRAINT uq_simulated_qr_request UNIQUE(owner_id, request_id),
    CONSTRAINT ck_simulated_qr_amount CHECK(amount > 0),
    CONSTRAINT ck_simulated_qr_currency CHECK(currency = 'VND'),
    CONSTRAINT ck_simulated_qr_bank CHECK(bank_code IN ('VIETCOMBANK_DEMO','BIDV_DEMO','TECHCOMBANK_DEMO','MB_BANK_DEMO')),
    CONSTRAINT ck_simulated_qr_verifier CHECK(token_verifier NOT LIKE '%[^0-9a-f]%'),
    CONSTRAINT ck_simulated_qr_time CHECK(expires_at_utc > created_at_utc AND state_changed_at_utc >= created_at_utc),
    CONSTRAINT ck_simulated_qr_state CHECK(
        (active = 1 AND state IN ('PENDING','AUTHORIZED_PENDING_SETTLEMENT')) OR
        (active = 0 AND state IN ('CANCELED','EXPIRED','SETTLED'))),
    CONSTRAINT ck_simulated_qr_version CHECK(version >= 0)
);
CREATE UNIQUE INDEX ux_simulated_qr_active ON dbo.simulated_qr_session(payment_id, owner_id) WHERE active = 1;
CREATE INDEX ix_simulated_qr_expiry ON dbo.simulated_qr_session(active, expires_at_utc) INCLUDE(payment_id, owner_id);

CREATE TABLE dbo.simulated_qr_transition (
    session_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT fk_simulated_qr_transition REFERENCES dbo.simulated_qr_session(id),
    version BIGINT NOT NULL,
    from_state VARCHAR(40) NULL,
    to_state VARCHAR(40) NOT NULL,
    changed_at_utc DATETIME2(7) NOT NULL,
    CONSTRAINT pk_simulated_qr_transition PRIMARY KEY(session_id, version)
);

-- One persisted budget per member; the member row serializes first insertion across nodes.
CREATE TABLE dbo.simulated_qr_rate_limit (
    owner_id BIGINT NOT NULL CONSTRAINT pk_simulated_qr_rate PRIMARY KEY
        CONSTRAINT fk_simulated_qr_rate_owner REFERENCES dbo.member_profile(user_id),
    create_window_utc DATETIME2(7) NOT NULL,
    create_count INT NOT NULL,
    access_window_utc DATETIME2(7) NOT NULL,
    access_count INT NOT NULL,
    CONSTRAINT ck_simulated_qr_rate CHECK(create_count BETWEEN 0 AND 5 AND access_count BETWEEN 0 AND 60)
);
GO

CREATE TRIGGER dbo.tr_simulated_qr_history_immutable ON dbo.simulated_qr_transition AFTER UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    THROW 51034, 'Simulator transition history is append only', 1;
END;
GO

CREATE TRIGGER dbo.tr_simulated_qr_lifecycle ON dbo.simulated_qr_session AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id WHERE i.id IS NULL)
        THROW 51034, 'Simulator sessions must be retained', 1;
    IF EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
        WHERE d.id IS NULL AND (i.state <> 'PENDING' OR i.version <> 0 OR i.active <> 1))
        THROW 51034, 'Simulator sessions must start pending', 1;
    IF EXISTS (
        SELECT id,payment_id,owner_id,bank_code,amount,currency,token_verifier,request_id,created_at_utc,expires_at_utc FROM deleted
        EXCEPT
        SELECT id,payment_id,owner_id,bank_code,amount,currency,token_verifier,request_id,created_at_utc,expires_at_utc FROM inserted)
        THROW 51034, 'Simulator session snapshots are immutable', 1;
    IF EXISTS (SELECT 1 FROM inserted i JOIN deleted d ON d.id = i.id
        WHERE i.version <> d.version + 1 OR i.state_changed_at_utc < d.state_changed_at_utc
            OR NOT ((d.state = 'PENDING' AND i.state IN ('AUTHORIZED_PENDING_SETTLEMENT','CANCELED','EXPIRED'))
                OR (d.state = 'AUTHORIZED_PENDING_SETTLEMENT' AND i.state IN ('CANCELED','EXPIRED'))))
        THROW 51034, 'Invalid simulator transition; settlement requires QR-05', 1;
    INSERT dbo.simulated_qr_transition(session_id,version,from_state,to_state,changed_at_utc)
        SELECT i.id,i.version,d.state,i.state,i.state_changed_at_utc FROM inserted i LEFT JOIN deleted d ON d.id=i.id;
END;
GO

CREATE TRIGGER dbo.tr_simulated_qr_history_source ON dbo.simulated_qr_transition AFTER INSERT AS
BEGIN
    SET NOCOUNT ON;
    IF TRIGGER_NESTLEVEL(OBJECT_ID(N'dbo.tr_simulated_qr_lifecycle'), 'AFTER', 'DML') = 0
        THROW 51034, 'Simulator history must originate in a session transition', 1;
END;
GO
