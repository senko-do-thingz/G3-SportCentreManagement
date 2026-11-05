CREATE TABLE class_training_notice (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_class_training_notice PRIMARY KEY,
    class_id BIGINT NOT NULL REFERENCES sport_class(id),
    coach_id BIGINT NOT NULL REFERENCES coach_profile(user_id),
    notice_type VARCHAR(20) NOT NULL,
    title NVARCHAR(150) NOT NULL,
    content NVARCHAR(2000) NOT NULL,
    due_date DATE NULL,
    status VARCHAR(20) NOT NULL CONSTRAINT df_class_training_notice_status DEFAULT 'PUBLISHED',
    operation_id UNIQUEIDENTIFIER NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    created_at DATETIME2(0) NOT NULL,
    cancelled_at DATETIME2(0) NULL,
    CONSTRAINT uq_class_training_notice_operation UNIQUE(coach_id,operation_id),
    CONSTRAINT ck_class_training_notice_type CHECK(notice_type IN ('HOMEWORK','ANNOUNCEMENT')),
    CONSTRAINT ck_class_training_notice_due CHECK((notice_type='HOMEWORK' AND due_date IS NOT NULL) OR (notice_type='ANNOUNCEMENT' AND due_date IS NULL)),
    CONSTRAINT ck_class_training_notice_status CHECK((status='PUBLISHED' AND cancelled_at IS NULL) OR (status='CANCELLED' AND cancelled_at IS NOT NULL))
);
CREATE INDEX ix_class_training_notice_audience ON class_training_notice(class_id,status,created_at);
