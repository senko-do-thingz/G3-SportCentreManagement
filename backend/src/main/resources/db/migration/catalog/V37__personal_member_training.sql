-- Extend existing booking/session training for the user's confirmed personal-plan requirement.
-- No class enrollment/withdrawal workflow is introduced.
-- Legacy plans remain unassigned; never infer their owner from attendance or class membership.
ALTER TABLE [session_plan] ADD [member_id] BIGINT NULL, [booking_id] BIGINT NULL;
GO
ALTER TABLE [session_plan] ADD CONSTRAINT [fk_session_plan_member]
    FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id]);
ALTER TABLE [session_plan] ADD CONSTRAINT [fk_session_plan_booking]
    FOREIGN KEY ([booking_id]) REFERENCES [booking]([id]);
ALTER TABLE [session_plan] ADD CONSTRAINT [uq_session_plan_owner] UNIQUE ([id], [member_id]);
CREATE INDEX [ix_session_plan_member] ON [session_plan]([member_id], [created_at]);

ALTER TABLE [coach_feedback] DROP CONSTRAINT [fk_coach_feedback_session];
DROP INDEX [ix_coach_feedback_session] ON [coach_feedback];
ALTER TABLE [coach_feedback] ALTER COLUMN [session_id] BIGINT NULL;
ALTER TABLE [coach_feedback] ADD CONSTRAINT [fk_coach_feedback_session]
    FOREIGN KEY ([session_id]) REFERENCES [class_session]([id]);
CREATE INDEX [ix_coach_feedback_session] ON [coach_feedback]([session_id]);
ALTER TABLE [coach_feedback] ADD [plan_id] BIGINT NULL;
GO
ALTER TABLE [coach_feedback] ADD CONSTRAINT [fk_coach_feedback_personal_plan]
    FOREIGN KEY ([plan_id], [member_id]) REFERENCES [session_plan]([id], [member_id]);
ALTER TABLE [coach_feedback] ADD CONSTRAINT [ck_coach_feedback_target]
    CHECK ([plan_id] IS NOT NULL OR [session_id] IS NOT NULL);
CREATE INDEX [ix_coach_feedback_plan] ON [coach_feedback]([plan_id], [member_id], [created_at]);
