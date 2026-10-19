-- Prospective only. Installation is inactive; an upgraded writer activates using its application Clock.
IF OBJECT_ID(N'dbo.cash_coverage') IS NOT NULL OR OBJECT_ID(N'dbo.cash_event') IS NOT NULL
    OR OBJECT_ID(N'dbo.cash_capture_ticket') IS NOT NULL
BEGIN
    THROW 51031, 'Unexpected cash reporting objects; investigate migration cohort', 1;
END;

CREATE TABLE dbo.cash_coverage (
    id INT NOT NULL CONSTRAINT pk_cash_coverage PRIMARY KEY,
    cutover_utc DATETIME2(7) NULL,
    source VARCHAR(30) NOT NULL,
    CONSTRAINT ck_cash_coverage_singleton CHECK (id = 1 AND source = 'LIVE_SERVICE')
);
INSERT INTO dbo.cash_coverage(id, cutover_utc, source) VALUES (1, NULL, 'LIVE_SERVICE');

-- Tickets belong to a database transaction, not a pooled session. Source 0 means one new desk payment.
CREATE TABLE dbo.cash_capture_ticket (
    transaction_id BIGINT NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    event_time_utc DATETIME2(7) NOT NULL,
    CONSTRAINT pk_cash_capture_ticket PRIMARY KEY(transaction_id, event_type, source_id),
    CONSTRAINT ck_cash_ticket_kind CHECK (event_type IN ('COLLECTION', 'REFUND_PAYOUT')),
    CONSTRAINT ck_cash_ticket_source CHECK (source_id > 0 OR (source_id = 0 AND event_type = 'COLLECTION'))
);

CREATE TABLE dbo.cash_event (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_cash_event PRIMARY KEY,
    event_type VARCHAR(20) NOT NULL,
    payment_id BIGINT NULL CONSTRAINT fk_cash_event_payment REFERENCES dbo.payment(id),
    refund_request_id BIGINT NULL CONSTRAINT fk_cash_event_refund REFERENCES dbo.refund_request(id),
    amount DECIMAL(14,2) NOT NULL,
    event_time_utc DATETIME2(7) NOT NULL,
    recorded_at_utc DATETIME2(7) NOT NULL CONSTRAINT df_cash_recorded DEFAULT SYSUTCDATETIME(),
    provenance VARCHAR(30) NOT NULL,
    original_method VARCHAR(30) NULL,
    original_item_type VARCHAR(30) NULL,
    original_sport_id BIGINT NULL,
    original_sport_name NVARCHAR(100) NULL,
    CONSTRAINT ck_cash_amount CHECK (amount > 0),
    CONSTRAINT ck_cash_provenance CHECK (provenance = 'LIVE_SERVICE'),
    CONSTRAINT ck_cash_type CHECK (
        (event_type = 'COLLECTION' AND payment_id IS NOT NULL AND refund_request_id IS NULL)
        OR (event_type = 'REFUND_PAYOUT' AND refund_request_id IS NOT NULL)),
    CONSTRAINT ck_cash_sport CHECK ((original_sport_id IS NULL AND original_sport_name IS NULL)
        OR (original_sport_id IS NOT NULL AND original_sport_name IS NOT NULL))
);
CREATE UNIQUE INDEX ux_cash_collection ON dbo.cash_event(payment_id) WHERE event_type = 'COLLECTION';
CREATE UNIQUE INDEX ux_cash_payout ON dbo.cash_event(refund_request_id) WHERE event_type = 'REFUND_PAYOUT';
CREATE INDEX ix_cash_time ON dbo.cash_event(event_time_utc, id) INCLUDE(event_type, amount);
GO

CREATE TRIGGER dbo.tr_cash_coverage_immutable ON dbo.cash_coverage AFTER UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM dbo.cash_coverage WHERE id = 1)
        OR EXISTS (SELECT 1 FROM deleted WHERE cutover_utc IS NOT NULL)
        OR EXISTS (SELECT 1 FROM inserted WHERE cutover_utc IS NULL)
    BEGIN
        THROW 51031, 'Cash coverage activation is immutable', 1;
    END;
END;
GO

CREATE TRIGGER dbo.tr_cash_event_immutable ON dbo.cash_event AFTER UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    THROW 51031, 'Cash events are append only', 1;
END;
GO

CREATE TRIGGER dbo.tr_cash_event_source ON dbo.cash_event AFTER INSERT AS
BEGIN
    SET NOCOUNT ON;
    IF TRIGGER_NESTLEVEL(OBJECT_ID(N'dbo.tr_cash_collection'), 'AFTER', 'DML') = 0
        AND TRIGGER_NESTLEVEL(OBJECT_ID(N'dbo.tr_cash_refund'), 'AFTER', 'DML') = 0
    BEGIN
        THROW 51031, 'Cash events must originate in an atomic source transition', 1;
    END;
END;
GO

CREATE TRIGGER dbo.tr_cash_collection ON dbo.payment AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
        WHERE i.payment_status = 'SUCCESS' AND (d.id IS NULL OR d.payment_status <> 'SUCCESS')) RETURN;
    DECLARE @cutover DATETIME2(7);
    -- This lock is held until commit, coordinating inactive writes with first activation.
    SELECT @cutover = cutover_utc FROM dbo.cash_coverage WITH (HOLDLOCK) WHERE id = 1;
    IF @cutover IS NULL RETURN;
    DECLARE @tx BIGINT = CURRENT_TRANSACTION_ID();
    IF EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
        WHERE i.payment_status = 'SUCCESS' AND (d.id IS NULL OR d.payment_status <> 'SUCCESS')
        AND NOT EXISTS (SELECT 1 FROM dbo.cash_capture_ticket t
            WHERE t.transaction_id = @tx AND t.event_type = 'COLLECTION'
            AND (t.source_id = i.id OR (t.source_id = 0 AND d.id IS NULL))
            AND t.event_time_utc >= @cutover))
    BEGIN
        THROW 51031, 'Active cash coverage requires an upgraded collection writer', 1;
    END;
    IF EXISTS (SELECT 1 FROM dbo.cash_capture_ticket WHERE transaction_id = @tx AND source_id = 0)
        AND (SELECT COUNT(*) FROM inserted i LEFT JOIN deleted d ON d.id = i.id
            WHERE d.id IS NULL AND i.payment_status = 'SUCCESS') > 1
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
    WHERE i.payment_status = 'SUCCESS' AND (d.id IS NULL OR d.payment_status <> 'SUCCESS');
    DELETE t FROM dbo.cash_capture_ticket t WHERE t.transaction_id = @tx AND t.event_type = 'COLLECTION'
        AND EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
            WHERE i.payment_status = 'SUCCESS' AND (d.id IS NULL OR d.payment_status <> 'SUCCESS')
                AND (t.source_id = i.id OR (t.source_id = 0 AND d.id IS NULL)));
END;
GO

CREATE TRIGGER dbo.tr_cash_refund ON dbo.refund_request AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
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
