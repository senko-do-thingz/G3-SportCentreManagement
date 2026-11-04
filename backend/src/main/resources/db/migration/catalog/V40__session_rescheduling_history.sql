-- Retain every issued identity, including retired aliases and deleted-session tombstones.
ALTER TABLE class_session ADD schedule_revision BIGINT NOT NULL
    CONSTRAINT df_class_session_schedule_revision DEFAULT 0;
CREATE TABLE session_code_history (
    session_code VARCHAR(10) COLLATE Latin1_General_100_BIN2 NOT NULL,
    session_id BIGINT NULL,
    original_session_id BIGINT NOT NULL,
    issued_session_date DATE NOT NULL,
    issued_at_utc DATETIME2 NOT NULL CONSTRAINT df_session_code_history_issued DEFAULT SYSUTCDATETIME(),
    retired_at_utc DATETIME2 NULL,
    issued_by_user_id BIGINT NULL,
    retired_by_user_id BIGINT NULL,
    retirement_reason NVARCHAR(500) NULL,
    CONSTRAINT pk_session_code_history PRIMARY KEY(session_code),
    CONSTRAINT fk_session_code_history_session FOREIGN KEY(session_id) REFERENCES class_session(id) ON DELETE SET NULL
);
CREATE INDEX ix_session_code_history_session ON session_code_history(session_id);
INSERT INTO session_code_history(session_code,session_id,original_session_id,issued_session_date)
SELECT session_code,id,id,session_date FROM class_session;
GO
CREATE TRIGGER tr_class_session_identity_history ON class_session AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id
        WHERE d.id IS NULL OR i.session_code<>d.session_code) RETURN;
    DECLARE @lock INT;
    EXEC @lock=sys.sp_getapplock @Resource='sportify.session-identity', @LockMode='Exclusive',
        @LockOwner='Transaction', @LockTimeout=10000;
    IF @lock<0 THROW 51041, 'Session identity allocation is busy. Retry the complete transaction.', 1;
    IF EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id
        JOIN session_code_history h ON h.session_code=i.session_code
        WHERE d.id IS NULL OR i.session_code<>d.session_code)
        THROW 51042, 'A public Session ID can never be reassigned, including to its original session.', 1;
    UPDATE h SET retired_at_utc=SYSUTCDATETIME()
    FROM session_code_history h JOIN deleted d ON d.session_code=h.session_code
    JOIN inserted i ON i.id=d.id WHERE i.session_code<>d.session_code;
    INSERT INTO session_code_history(session_code,session_id,original_session_id,issued_session_date)
    SELECT i.session_code,i.id,i.id,i.session_date FROM inserted i LEFT JOIN deleted d ON d.id=i.id
    WHERE d.id IS NULL OR i.session_code<>d.session_code;
    -- Direct imports and isolated SQL fixtures must also advance the durable high-water mark.
    SELECT i.session_date,SUBSTRING(i.session_code,7,2) sport_code,
        MAX(CONVERT(INT,RIGHT(i.session_code,2))) last_value
    INTO #issued FROM inserted i LEFT JOIN deleted d ON d.id=i.id
    WHERE d.id IS NULL OR i.session_code<>d.session_code
    GROUP BY i.session_date,SUBSTRING(i.session_code,7,2);
    UPDATE c SET last_value=x.last_value FROM session_code_counter c JOIN #issued x
        ON x.session_date=c.session_date AND x.sport_code=c.sport_code WHERE x.last_value>c.last_value;
    INSERT INTO session_code_counter(session_date,sport_code,last_value)
    SELECT x.session_date,x.sport_code,x.last_value FROM #issued x
    WHERE NOT EXISTS(SELECT 1 FROM session_code_counter c WITH(UPDLOCK,HOLDLOCK)
        WHERE c.session_date=x.session_date AND c.sport_code=x.sport_code);
END;
