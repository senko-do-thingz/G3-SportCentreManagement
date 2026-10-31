IF OBJECT_ID(N'dbo.simulated_qr_session', N'U') IS NULL
    OR OBJECT_ID(N'dbo.payment', N'U') IS NULL
    THROW 51040, 'Simulated QR settlement requires V34 simulated_qr_session and V14 payment tables.', 1;

IF OBJECT_ID(N'dbo.simulated_payment_transaction', N'U') IS NOT NULL
    OR COL_LENGTH(N'dbo.payment', N'settlement_type') IS NOT NULL
    OR COL_LENGTH(N'dbo.payment', N'settlement_provider') IS NOT NULL
    OR COL_LENGTH(N'dbo.payment', N'simulated_transaction_id') IS NOT NULL
    THROW 51040, 'Simulated QR settlement objects already exist.', 1;
GO

CREATE TABLE dbo.simulated_payment_transaction (
    id BIGINT IDENTITY(1,1) NOT NULL,
    transaction_code VARCHAR(40) NOT NULL,
    payment_id BIGINT NOT NULL,
    session_id UNIQUEIDENTIFIER NOT NULL,
    member_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    bank_code VARCHAR(32) NOT NULL,
    settlement_type VARCHAR(20) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    settled_at_utc DATETIME2(7) NOT NULL,
    created_at_utc DATETIME2(7) NOT NULL CONSTRAINT df_simulated_tx_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_simulated_payment_transaction PRIMARY KEY (id),
    CONSTRAINT ux_simulated_tx_code UNIQUE (transaction_code),
    CONSTRAINT ux_simulated_tx_payment UNIQUE (payment_id),
    CONSTRAINT ux_simulated_tx_session UNIQUE (session_id),
    CONSTRAINT fk_simulated_tx_payment FOREIGN KEY (payment_id) REFERENCES dbo.payment(id),
    CONSTRAINT fk_simulated_tx_session FOREIGN KEY (session_id) REFERENCES dbo.simulated_qr_session(id),
    CONSTRAINT fk_simulated_tx_member FOREIGN KEY (member_id) REFERENCES dbo.member_profile(user_id),
    CONSTRAINT ck_simulated_tx_amount CHECK (amount > 0),
    CONSTRAINT ck_simulated_tx_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_simulated_tx_bank CHECK (bank_code IN ('VIETCOMBANK_DEMO','BIDV_DEMO','TECHCOMBANK_DEMO','MB_BANK_DEMO')),
    CONSTRAINT ck_simulated_tx_type CHECK (settlement_type = 'SIMULATED'),
    CONSTRAINT ck_simulated_tx_provider CHECK (provider = 'SPORTIFY_SIMULATOR'),
    CONSTRAINT ck_simulated_tx_status CHECK (status IN ('SUCCEEDED','FAILED','CANCELLED'))
);

ALTER TABLE dbo.payment ADD
    settlement_type VARCHAR(20) NULL,
    settlement_provider VARCHAR(40) NULL,
    simulated_transaction_id BIGINT NULL;
GO

ALTER TABLE dbo.payment ADD
    CONSTRAINT fk_payment_simulated_tx FOREIGN KEY (simulated_transaction_id) REFERENCES dbo.simulated_payment_transaction(id),
    CONSTRAINT ck_payment_settlement_type CHECK (settlement_type IS NULL OR settlement_type IN ('ACTUAL','SIMULATED')),
    CONSTRAINT ck_payment_settlement_provider CHECK (settlement_provider IS NULL OR settlement_provider IN ('SPORTIFY_SIMULATOR','DESK_CASH','MANUAL_TRANSFER','GATEWAY')),
    CONSTRAINT ck_payment_simulated_consistency CHECK (
        (settlement_type = 'SIMULATED' AND settlement_provider = 'SPORTIFY_SIMULATOR' AND simulated_transaction_id IS NOT NULL)
        OR ((settlement_type IS NULL OR settlement_type = 'ACTUAL') AND simulated_transaction_id IS NULL)
    );

CREATE UNIQUE NONCLUSTERED INDEX ux_payment_simulated_tx
    ON dbo.payment(simulated_transaction_id)
    WHERE simulated_transaction_id IS NOT NULL;
GO

CREATE TRIGGER dbo.tr_simulated_tx_immutable
ON dbo.simulated_payment_transaction
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM inserted) OR EXISTS (SELECT 1 FROM deleted)
        THROW 51041, 'Simulated payment transactions are immutable audit records.', 1;
END;
GO

ALTER TRIGGER dbo.tr_simulated_qr_lifecycle
ON dbo.simulated_qr_session
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted) AND NOT EXISTS (SELECT 1 FROM inserted)
        THROW 51034, 'Simulated QR sessions are retained for audit and cannot be deleted.', 1;
    IF EXISTS (SELECT 1 FROM inserted) AND NOT EXISTS (SELECT 1 FROM deleted)
    BEGIN
        IF EXISTS (SELECT 1 FROM inserted WHERE state <> 'PENDING' OR active <> 1 OR version <> 0)
            THROW 51034, 'New simulated QR sessions must start as active PENDING version 0.', 1;
        INSERT dbo.simulated_qr_transition(session_id,version,from_state,to_state,changed_at_utc)
        SELECT id,0,NULL,state,state_changed_at_utc FROM inserted;
        RETURN;
    END;
    IF EXISTS (
        SELECT 1 FROM inserted i JOIN deleted d ON d.id = i.id
        WHERE i.payment_id <> d.payment_id OR i.owner_id <> d.owner_id OR i.bank_code <> d.bank_code
            OR i.amount <> d.amount OR i.currency <> d.currency OR i.token_verifier <> d.token_verifier
            OR i.request_id <> d.request_id OR i.created_at_utc <> d.created_at_utc OR i.expires_at_utc <> d.expires_at_utc
            OR i.version <> d.version + 1
            OR NOT (
                (d.state = 'PENDING' AND i.state = 'AUTHORIZED_PENDING_SETTLEMENT' AND i.active = 1 AND d.expires_at_utc > i.state_changed_at_utc)
                OR (d.state = 'PENDING' AND i.state IN ('CANCELED','EXPIRED') AND i.active = 0)
                OR (d.state = 'AUTHORIZED_PENDING_SETTLEMENT' AND i.state = 'CANCELED' AND i.active = 0)
                OR (d.state IN ('PENDING','AUTHORIZED_PENDING_SETTLEMENT') AND i.state = 'SETTLED' AND i.active = 0 AND d.expires_at_utc > i.state_changed_at_utc)
            )
    )
        THROW 51034, 'Invalid simulated QR session mutation or state transition.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted i
        WHERE i.state = 'SETTLED'
          AND NOT EXISTS (
              SELECT 1
              FROM dbo.simulated_payment_transaction tx
              JOIN dbo.payment p ON p.id = tx.payment_id AND p.simulated_transaction_id = tx.id
              WHERE tx.session_id = i.id
                AND tx.payment_id = i.payment_id
                AND tx.member_id = i.owner_id
                AND tx.amount = i.amount
                AND tx.status = 'SUCCEEDED'
                AND p.payment_status = 'SUCCESS'
                AND p.settlement_type = 'SIMULATED'
                AND p.settlement_provider = 'SPORTIFY_SIMULATOR'
          )
    )
        THROW 51034, 'Simulated QR session cannot transition to SETTLED without a matching settled payment and simulated transaction.', 1;
    INSERT dbo.simulated_qr_transition(session_id,version,from_state,to_state,changed_at_utc)
    SELECT i.id,i.version,d.state,i.state,i.state_changed_at_utc FROM inserted i JOIN deleted d ON d.id = i.id;
END;
GO

ALTER TRIGGER dbo.tr_cash_collection ON dbo.payment AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @tx BIGINT = CURRENT_TRANSACTION_ID();
    IF EXISTS (
        SELECT 1
        FROM inserted i
        JOIN dbo.cash_capture_ticket t
          ON t.transaction_id = @tx
         AND t.event_type = 'COLLECTION'
         AND (t.source_id = i.id OR t.source_id = 0)
        WHERE i.settlement_type = 'SIMULATED'
    )
    BEGIN
        THROW 51042, 'Simulated payments cannot be captured as actual cash collection events.', 1;
    END;

    IF NOT EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
        WHERE i.payment_status = 'SUCCESS'
          AND ISNULL(i.settlement_type, 'ACTUAL') <> 'SIMULATED'
          AND (d.id IS NULL OR d.payment_status <> 'SUCCESS')) RETURN;
    DECLARE @cutover DATETIME2(7);
    SELECT @cutover = cutover_utc FROM dbo.cash_coverage WITH (HOLDLOCK) WHERE id = 1;
    IF @cutover IS NULL RETURN;
    IF EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
        WHERE i.payment_status = 'SUCCESS'
          AND ISNULL(i.settlement_type, 'ACTUAL') <> 'SIMULATED'
          AND (d.id IS NULL OR d.payment_status <> 'SUCCESS')
          AND NOT EXISTS (SELECT 1 FROM dbo.cash_capture_ticket t
              WHERE t.transaction_id = @tx AND t.event_type = 'COLLECTION'
              AND (t.source_id = i.id OR (t.source_id = 0 AND d.id IS NULL))
              AND t.event_time_utc >= @cutover))
    BEGIN
        THROW 51031, 'Active cash coverage requires an upgraded collection writer', 1;
    END;
    IF EXISTS (SELECT 1 FROM dbo.cash_capture_ticket WHERE transaction_id = @tx AND source_id = 0)
        AND (SELECT COUNT(*) FROM inserted i LEFT JOIN deleted d ON d.id = i.id
            WHERE d.id IS NULL AND i.payment_status = 'SUCCESS' AND ISNULL(i.settlement_type, 'ACTUAL') <> 'SIMULATED') > 1
    BEGIN
        THROW 51031, 'A desk collection ticket covers exactly one new payment', 1;
    END;

    INSERT INTO dbo.cash_event(event_type, payment_id, amount, event_time_utc, provenance,
        original_method, original_item_type, original_sport_id, original_sport_name)
    SELECT 'COLLECTION', i.id, i.amount, t.event_time_utc, 'LIVE_SERVICE', i.payment_method,
        CASE WHEN a.unambiguous = 1 AND r.id IS NOT NULL THEN 'SPORT_PACKAGE'
             WHEN a.unambiguous = 1 AND c.id IS NOT NULL THEN 'MEMBERSHIP_CARD' END,
        CASE WHEN a.unambiguous = 1 THEN s.id END,
        CASE WHEN a.unambiguous = 1 THEN s.name END
    FROM inserted i
    LEFT JOIN deleted d ON d.id = i.id
    JOIN dbo.cash_capture_ticket t ON t.transaction_id = @tx AND t.event_type = 'COLLECTION'
        AND (t.source_id = i.id OR (t.source_id = 0 AND d.id IS NULL))
    LEFT JOIN dbo.sport_package_registration r ON r.payment_id = i.id
    LEFT JOIN dbo.member_card c ON c.payment_id = i.id
    LEFT JOIN dbo.sport_package p ON p.id = r.package_id
    LEFT JOIN dbo.sport s ON s.id = p.sport_id
    CROSS APPLY (SELECT CASE WHEN
        ((r.id IS NOT NULL AND c.id IS NULL AND r.member_id = i.member_id)
          OR (c.id IS NOT NULL AND r.id IS NULL AND c.member_id = i.member_id))
        AND NOT EXISTS (SELECT 1 FROM dbo.invoice v JOIN dbo.invoice_line l ON l.invoice_id = v.id
            WHERE v.payment_id = i.id AND NOT
                ((r.id IS NOT NULL AND l.item_type = 'SPORT_PACKAGE' AND l.item_reference_id = r.id)
                 OR (c.id IS NOT NULL AND l.item_type = 'MEMBERSHIP_CARD' AND l.item_reference_id = c.id)))
        THEN 1 ELSE 0 END AS unambiguous) a
    WHERE i.payment_status = 'SUCCESS'
      AND ISNULL(i.settlement_type, 'ACTUAL') <> 'SIMULATED'
      AND (d.id IS NULL OR d.payment_status <> 'SUCCESS');
    DELETE t FROM dbo.cash_capture_ticket t WHERE t.transaction_id = @tx AND t.event_type = 'COLLECTION'
        AND EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
            WHERE i.payment_status = 'SUCCESS'
              AND ISNULL(i.settlement_type, 'ACTUAL') <> 'SIMULATED'
              AND (d.id IS NULL OR d.payment_status <> 'SUCCESS')
              AND (t.source_id = i.id OR (t.source_id = 0 AND d.id IS NULL)));
END;
GO

ALTER TRIGGER dbo.tr_cash_refund ON dbo.refund_request AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM inserted i
        LEFT JOIN dbo.payment p ON p.id = i.payment_id
        LEFT JOIN dbo.sport_package_registration r ON r.id = i.package_registration_id
        LEFT JOIN dbo.payment rp ON rp.id = r.payment_id
        WHERE i.status IN ('PENDING', 'APPROVED', 'COMPLETED')
          AND (p.settlement_type = 'SIMULATED' OR rp.settlement_type = 'SIMULATED')
    )
    BEGIN
        THROW 51043, 'Simulated payments cannot trigger actual cash refund requests or payouts.', 1;
    END;

    IF NOT EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
        WHERE i.status = 'COMPLETED' AND (d.id IS NULL OR d.status <> 'COMPLETED')) RETURN;
    DECLARE @cutover DATETIME2(7);
    SELECT @cutover = cutover_utc FROM dbo.cash_coverage WITH (HOLDLOCK) WHERE id = 1;
    IF @cutover IS NULL RETURN;
    DECLARE @tx BIGINT = CURRENT_TRANSACTION_ID();
    IF EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
        WHERE i.status = 'COMPLETED' AND (d.id IS NULL OR d.status <> 'COMPLETED')
        AND NOT EXISTS (SELECT 1 FROM dbo.cash_capture_ticket t WHERE t.transaction_id = @tx
            AND t.event_type = 'REFUND_PAYOUT' AND t.source_id = i.id AND t.event_time_utc >= @cutover))
    BEGIN
        THROW 51031, 'Active cash coverage requires an upgraded refund writer', 1;
    END;
    INSERT INTO dbo.cash_event(event_type, payment_id, refund_request_id, amount, event_time_utc, provenance,
        original_method, original_item_type, original_sport_id, original_sport_name)
    SELECT 'REFUND_PAYOUT', i.payment_id, i.id, COALESCE(i.amount_approved, i.amount_requested),
        t.event_time_utc, 'LIVE_SERVICE', original.original_method, original.original_item_type,
        original.original_sport_id, original.original_sport_name
    FROM inserted i LEFT JOIN deleted d ON d.id = i.id
    JOIN dbo.cash_capture_ticket t ON t.transaction_id = @tx AND t.event_type = 'REFUND_PAYOUT' AND t.source_id = i.id
    LEFT JOIN dbo.cash_event original ON original.event_type = 'COLLECTION' AND original.payment_id = i.payment_id
    WHERE i.status = 'COMPLETED' AND (d.id IS NULL OR d.status <> 'COMPLETED');
    DELETE t FROM dbo.cash_capture_ticket t WHERE t.transaction_id = @tx AND t.event_type = 'REFUND_PAYOUT'
        AND EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
            WHERE i.id = t.source_id AND i.status = 'COMPLETED' AND (d.id IS NULL OR d.status <> 'COMPLETED'));
END;
GO

ALTER VIEW [dbo].[vw_payment_ledger]
AS
SELECT
    p.[id]                                          AS [payment_id],
    p.[payment_code]                                AS [payment_code],
    p.[payment_status]                              AS [payment_status],
    p.[payment_method]                              AS [payment_method],
    p.[amount]                                      AS [amount],
    p.[reference_code]                              AS [reference_code],
    p.[payment_time]                                AS [payment_time],
    p.[created_at]                                  AS [created_at],
    COALESCE(p.[payment_time], p.[created_at])      AS [ledger_time],
    p.[member_id]                                   AS [member_id],
    mp.[member_code]                                AS [member_code],
    u.[full_name]                                   AS [member_name],
    inv.[id]                                        AS [invoice_id],
    inv.[invoice_number]                            AS [invoice_number],
    COALESCE(line_item.[item_type],
        CASE
            WHEN reg.[id] IS NOT NULL AND card.[id] IS NULL THEN 'SPORT_PACKAGE'
            WHEN card.[id] IS NOT NULL AND reg.[id] IS NULL THEN 'MEMBERSHIP_CARD'
        END)                                        AS [item_type],
    COALESCE(reg.[registration_code], card.[card_code]) AS [item_code],
    COALESCE(pkg.[name], tier.[name], line_item.[description]) AS [item_name],
    sp.[id]                                         AS [sport_id],
    sp.[name]                                       AS [sport_name],
    p.[settlement_type]                             AS [settlement_type],
    p.[settlement_provider]                         AS [settlement_provider],
    p.[simulated_transaction_id]                    AS [simulated_transaction_id]
FROM [dbo].[payment] p
INNER JOIN [dbo].[member_profile] mp ON mp.[user_id] = p.[member_id]
INNER JOIN [dbo].[user_account] u ON u.[id] = mp.[user_id]
OUTER APPLY (
    SELECT TOP 1 i.[id], i.[invoice_number]
    FROM [dbo].[invoice] i
    WHERE i.[payment_id] = p.[id]
    ORDER BY i.[id] ASC
) inv
OUTER APPLY (
    SELECT TOP 1 il.[item_type], il.[description]
    FROM [dbo].[invoice_line] il
    WHERE il.[invoice_id] = inv.[id]
    ORDER BY il.[id] ASC
) line_item
LEFT JOIN [dbo].[sport_package_registration] reg ON reg.[payment_id] = p.[id]
LEFT JOIN [dbo].[sport_package] pkg ON pkg.[id] = reg.[package_id]
LEFT JOIN [dbo].[sport] sp ON sp.[id] = pkg.[sport_id]
LEFT JOIN [dbo].[member_card] card ON card.[payment_id] = p.[id]
LEFT JOIN [dbo].[membership_card_tier] tier ON tier.[id] = card.[tier_id];
GO

ALTER VIEW [dbo].[vw_paid_payment]
AS
SELECT
    l.[payment_id],
    l.[payment_code],
    l.[payment_time],
    CAST(l.[payment_time] AS DATE) AS [paid_on],
    l.[amount],
    l.[payment_method],
    l.[member_id],
    l.[member_code],
    l.[member_name],
    l.[invoice_number],
    l.[item_type],
    l.[item_code],
    l.[item_name],
    l.[sport_id],
    l.[sport_name]
FROM [dbo].[vw_payment_ledger] l
WHERE l.[payment_status] = 'SUCCESS'
  AND l.[payment_time] IS NOT NULL
  AND ISNULL(l.[settlement_type], 'ACTUAL') <> 'SIMULATED';
GO
