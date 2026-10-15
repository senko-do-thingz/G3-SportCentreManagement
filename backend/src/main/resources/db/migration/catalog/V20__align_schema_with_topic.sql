-- ============================================================================
-- Migration V20: align the implemented tables with the topic requirements
--   1. member_card can wait for payment (PENDING_PAYMENT) and be replaced by an upgrade (REPLACED)
--   2. sport_class.coach_id: the coach in charge of a class (topic: assign a coach to each class)
--   3. booking.cancelled_by_user_id: who cancelled (member, receptionist or manager)
--   4. ux_booking_active: one confirmed booking per member and session, even under concurrent requests
-- Runs after V13-V19 (feat/figma-schema-sync). Only columns, checks and indexes change; no table is created.
-- New columns are nullable and not yet mapped by the entities, so ddl-auto=validate keeps passing.
-- ============================================================================

-- 1. member_card status
ALTER TABLE [member_card] DROP CONSTRAINT [ck_member_card_status];
ALTER TABLE [member_card] ADD CONSTRAINT [ck_member_card_status]
    CHECK ([status] IN ('PENDING_PAYMENT', 'ACTIVE', 'EXPIRED', 'CANCELLED', 'REPLACED'));

-- 2. coach in charge of a class; new sessions copy it into class_session.coach_id
ALTER TABLE [sport_class] ADD coach_id BIGINT NULL;
GO
ALTER TABLE [sport_class] ADD CONSTRAINT [fk_sc_coach] FOREIGN KEY (coach_id) REFERENCES [coach_profile](user_id);
CREATE INDEX [ix_sport_class_coach] ON [sport_class](coach_id);

-- 3. who cancelled a booking (null = cancelled by the system, e.g. the session was cancelled)
ALTER TABLE [booking] ADD cancelled_by_user_id BIGINT NULL;
GO
ALTER TABLE [booking] ADD CONSTRAINT [fk_bk_cancelled_by] FOREIGN KEY (cancelled_by_user_id) REFERENCES [user_account](id);

-- 4. no double booking (a member may book again after cancelling)
CREATE UNIQUE NONCLUSTERED INDEX [ux_booking_active]
    ON [booking](member_id, session_id)
    WHERE status = 'CONFIRMED';
