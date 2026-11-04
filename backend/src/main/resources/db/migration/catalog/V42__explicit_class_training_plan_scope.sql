-- Legacy unassigned plans remain individual and never grant class-wide visibility.
ALTER TABLE session_plan ADD plan_scope VARCHAR(12) NOT NULL
    CONSTRAINT df_session_plan_scope DEFAULT 'INDIVIDUAL',
    operation_id UNIQUEIDENTIFIER NULL,
    request_fingerprint VARCHAR(64) NULL;
GO
ALTER TABLE session_plan ADD CONSTRAINT ck_session_plan_scope CHECK (
    plan_scope='INDIVIDUAL' OR (plan_scope='CLASS' AND member_id IS NULL AND booking_id IS NULL
        AND is_template=0 AND operation_id IS NOT NULL AND request_fingerprint IS NOT NULL));
CREATE UNIQUE INDEX ux_session_plan_coach_operation ON session_plan(coach_id,operation_id)
    WHERE operation_id IS NOT NULL;
