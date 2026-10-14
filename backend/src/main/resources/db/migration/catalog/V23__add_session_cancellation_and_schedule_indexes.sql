-- Manager session scheduling and cancellation. Existing rows remain unchanged.
IF COL_LENGTH('class_session', 'cancel_reason') IS NULL
    ALTER TABLE class_session ADD cancel_reason NVARCHAR(255) NULL;

IF COL_LENGTH('class_session', 'cancelled_at') IS NULL
    ALTER TABLE class_session ADD cancelled_at DATETIME2(0) NULL;

IF COL_LENGTH('class_session', 'cancelled_by_user_id') IS NULL
    ALTER TABLE class_session ADD cancelled_by_user_id BIGINT NULL;

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_class_session_cancelled_by')
    ALTER TABLE class_session ADD CONSTRAINT fk_class_session_cancelled_by
        FOREIGN KEY (cancelled_by_user_id) REFERENCES user_account(id);

-- Detect equivalent leading key columns, including differently named indexes.
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes i
    JOIN sys.index_columns a ON a.object_id = i.object_id AND a.index_id = i.index_id AND a.key_ordinal = 1
    JOIN sys.index_columns b ON b.object_id = i.object_id AND b.index_id = i.index_id AND b.key_ordinal = 2
    WHERE i.object_id = OBJECT_ID('class_session') AND i.is_disabled = 0 AND i.has_filter = 0
      AND COL_NAME(i.object_id, a.column_id) = 'coach_id'
      AND COL_NAME(i.object_id, b.column_id) = 'session_date'
)
    CREATE INDEX ix_class_session_coach_date ON class_session(coach_id, session_date)
        INCLUDE (start_time, end_time, status);

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes i
    JOIN sys.index_columns a ON a.object_id = i.object_id AND a.index_id = i.index_id AND a.key_ordinal = 1
    JOIN sys.index_columns b ON b.object_id = i.object_id AND b.index_id = i.index_id AND b.key_ordinal = 2
    WHERE i.object_id = OBJECT_ID('class_session') AND i.is_disabled = 0 AND i.has_filter = 0
      AND COL_NAME(i.object_id, a.column_id) = 'facility_id'
      AND COL_NAME(i.object_id, b.column_id) = 'session_date'
)
    CREATE INDEX ix_class_session_facility_date ON class_session(facility_id, session_date)
        INCLUDE (start_time, end_time, status);

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes i
    JOIN sys.index_columns a ON a.object_id = i.object_id AND a.index_id = i.index_id AND a.key_ordinal = 1
    JOIN sys.index_columns b ON b.object_id = i.object_id AND b.index_id = i.index_id AND b.key_ordinal = 2
    WHERE i.object_id = OBJECT_ID('booking') AND i.is_disabled = 0 AND i.has_filter = 0
      AND COL_NAME(i.object_id, a.column_id) = 'session_id'
      AND COL_NAME(i.object_id, b.column_id) = 'status'
)
    CREATE INDEX ix_booking_session_status ON booking(session_id, status) INCLUDE (package_registration_id);
