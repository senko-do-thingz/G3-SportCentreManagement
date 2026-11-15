-- Migration V65: Ngan chan trung lich day cua Huan luyen vien (Double-booking) bang Database Trigger
-- Ap dung tren bang class_session cho cac thao tac INSERT va UPDATE

CREATE TRIGGER trg_prevent_coach_double_booking
ON class_session
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    -- Bo qua neu khong co thay doi nao lien quan den coach_id, session_date, start_time, end_time, hoac status
    IF NOT EXISTS (
        SELECT 1
        FROM inserted i
        LEFT JOIN deleted d ON d.id = i.id
        WHERE d.id IS NULL
           OR i.coach_id <> d.coach_id
           OR (i.coach_id IS NOT NULL AND d.coach_id IS NULL)
           OR (i.coach_id IS NULL AND d.coach_id IS NOT NULL)
           OR i.session_date <> d.session_date
           OR i.start_time <> d.start_time
           OR i.end_time <> d.end_time
           OR i.status <> d.status
    )
        RETURN;

    -- 1. Kiem tra xung dot voi cac ban ghi da co san trong bang class_session
    IF EXISTS (
        SELECT 1
        FROM class_session s
        INNER JOIN inserted i ON s.coach_id = i.coach_id
                              AND s.session_date = i.session_date
                              AND s.id <> i.id
        WHERE i.coach_id IS NOT NULL
          AND i.status IN ('DRAFT', 'PUBLISHED')
          AND s.status IN ('DRAFT', 'PUBLISHED')
          AND s.start_time < i.end_time
          AND s.end_time > i.start_time
    )
    BEGIN
        THROW 51065, 'Error: Coach already has an overlapping teaching session.', 1;
    END;

    -- 2. Kiem tra xung dot noi bo giua cac ban ghi trong cung mot batch duoc chen/cap nhat (inserted)
    IF EXISTS (
        SELECT 1
        FROM inserted a
        INNER JOIN inserted b ON a.coach_id = b.coach_id
                              AND a.session_date = b.session_date
                              AND (
                                  (a.id IS NOT NULL AND b.id IS NOT NULL AND a.id < b.id)
                                  OR (a.id IS NULL AND a.start_time < b.start_time)
                              )
        WHERE a.coach_id IS NOT NULL
          AND a.status IN ('DRAFT', 'PUBLISHED')
          AND b.status IN ('DRAFT', 'PUBLISHED')
          AND a.start_time < b.end_time
          AND a.end_time > b.start_time
    )
    BEGIN
        THROW 51065, 'Error: Overlapping teaching sessions detected for the same coach in the same batch.', 1;
    END;
END;
GO
