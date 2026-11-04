-- Preserve historical training discussions; only new general intake uses these fields.
ALTER TABLE support_request ADD intake_created_by BIGINT NULL REFERENCES user_account(id),
    intake_booking_id BIGINT NULL REFERENCES booking(id),
    intake_operation_id UNIQUEIDENTIFIER NULL,
    intake_fingerprint CHAR(64) NULL;
GO
CREATE UNIQUE INDEX ux_support_intake_operation ON support_request(intake_created_by,intake_operation_id)
    WHERE intake_created_by IS NOT NULL AND intake_operation_id IS NOT NULL;
GO
ALTER TABLE support_request ADD CONSTRAINT ck_support_intake_receipt CHECK(
    (intake_created_by IS NULL AND intake_operation_id IS NULL AND intake_fingerprint IS NULL)
    OR (intake_created_by IS NOT NULL AND intake_operation_id IS NOT NULL AND intake_fingerprint IS NOT NULL
        AND category IN ('GENERAL','FACILITY','BOOKING','MEMBERSHIP','PAYMENT')));
