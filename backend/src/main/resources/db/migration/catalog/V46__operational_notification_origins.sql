ALTER TABLE notification ADD event_key VARCHAR(120) NULL, origin_session_id BIGINT NULL,
    origin_revision BIGINT NULL;
GO
CREATE UNIQUE INDEX ux_notification_event ON notification(user_id,event_key) WHERE event_key IS NOT NULL;
CREATE INDEX ix_notification_session_origin ON notification(origin_session_id,origin_revision);
