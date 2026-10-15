-- ============================================================================
-- Migration: V16__create_training_and_attendance_tables.sql
-- Description: Creates tables for training curriculums, attendance tracking,
--              attendance corrections, skill metrics, and coach feedback.
-- ============================================================================

-- Step 1: Training plans
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'session_plan')
BEGIN
    CREATE TABLE [session_plan] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [class_id] BIGINT NOT NULL,
        [coach_id] BIGINT NOT NULL,
        [title] NVARCHAR(150) NOT NULL,
        [description] NVARCHAR(1000) NULL,
        [is_template] BIT NOT NULL CONSTRAINT [df_session_plan_is_template] DEFAULT 0,
        [created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_session_plan_created_at] DEFAULT SYSDATETIME(),
        [updated_at] DATETIME2(0) NOT NULL CONSTRAINT [df_session_plan_updated_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_session_plan] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_session_plan_class] FOREIGN KEY ([class_id]) REFERENCES [sport_class]([id]),
        CONSTRAINT [fk_session_plan_coach] FOREIGN KEY ([coach_id]) REFERENCES [coach_profile]([user_id])
    );

    CREATE NONCLUSTERED INDEX [ix_session_plan_class] ON [session_plan]([class_id]);
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'session_plan_step')
BEGIN
    CREATE TABLE [session_plan_step] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [plan_id] BIGINT NOT NULL,
        [step_order] INT NOT NULL,
        [title] NVARCHAR(150) NOT NULL,
        [duration_minutes] INT NOT NULL,
        [instructions] NVARCHAR(1000) NULL,
        CONSTRAINT [pk_session_plan_step] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_session_plan_step_plan] FOREIGN KEY ([plan_id]) REFERENCES [session_plan]([id]) ON DELETE CASCADE,
        CONSTRAINT [ck_session_plan_step_duration] CHECK ([duration_minutes] > 0)
    );

    CREATE NONCLUSTERED INDEX [ix_session_plan_step_plan] ON [session_plan_step]([plan_id], [step_order]);
END;

-- Step 2: Attendance tracking
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'attendance_record')
BEGIN
    CREATE TABLE [attendance_record] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [session_id] BIGINT NOT NULL,
        [member_id] BIGINT NOT NULL,
        [booking_id] BIGINT NULL,
        [status] VARCHAR(20) NOT NULL,
        [marked_at] DATETIME2(0) NOT NULL CONSTRAINT [df_attendance_marked_at] DEFAULT SYSDATETIME(),
        [marked_by] BIGINT NOT NULL,
        [notes] NVARCHAR(500) NULL,
        CONSTRAINT [pk_attendance_record] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [uq_attendance_session_member] UNIQUE ([session_id], [member_id]),
        CONSTRAINT [fk_attendance_session] FOREIGN KEY ([session_id]) REFERENCES [class_session]([id]),
        CONSTRAINT [fk_attendance_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id]),
        CONSTRAINT [fk_attendance_booking] FOREIGN KEY ([booking_id]) REFERENCES [booking]([id]),
        CONSTRAINT [fk_attendance_marked_by] FOREIGN KEY ([marked_by]) REFERENCES [user_account]([id]),
        CONSTRAINT [ck_attendance_status] CHECK ([status] IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED'))
    );

    CREATE NONCLUSTERED INDEX [ix_attendance_session] ON [attendance_record]([session_id]);
    CREATE NONCLUSTERED INDEX [ix_attendance_member] ON [attendance_record]([member_id]);
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'attendance_correction')
BEGIN
    CREATE TABLE [attendance_correction] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [attendance_record_id] BIGINT NOT NULL,
        [previous_status] VARCHAR(20) NOT NULL,
        [new_status] VARCHAR(20) NOT NULL,
        [reason] NVARCHAR(500) NOT NULL,
        [corrected_by] BIGINT NOT NULL,
        [created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_attendance_corr_created_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_attendance_correction] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_attendance_corr_record] FOREIGN KEY ([attendance_record_id]) REFERENCES [attendance_record]([id]),
        CONSTRAINT [fk_attendance_corr_user] FOREIGN KEY ([corrected_by]) REFERENCES [user_account]([id])
    );
END;

-- Step 3: Skill evaluations and coach feedback
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'skill_metric')
BEGIN
    CREATE TABLE [skill_metric] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [sport_id] BIGINT NOT NULL,
        [name] NVARCHAR(100) NOT NULL,
        [description] NVARCHAR(500) NULL,
        [max_score] INT NOT NULL CONSTRAINT [df_skill_metric_max_score] DEFAULT 10,
        CONSTRAINT [pk_skill_metric] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_skill_metric_sport] FOREIGN KEY ([sport_id]) REFERENCES [sport]([id])
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'session_result')
BEGIN
    CREATE TABLE [session_result] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [session_id] BIGINT NOT NULL,
        [member_id] BIGINT NOT NULL,
        [notes] NVARCHAR(1000) NULL,
        [created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_session_result_created_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_session_result] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [uq_session_result_session_member] UNIQUE ([session_id], [member_id]),
        CONSTRAINT [fk_session_result_session] FOREIGN KEY ([session_id]) REFERENCES [class_session]([id]),
        CONSTRAINT [fk_session_result_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id])
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'session_result_score')
BEGIN
    CREATE TABLE [session_result_score] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [result_id] BIGINT NOT NULL,
        [metric_id] BIGINT NOT NULL,
        [score] DECIMAL(5,2) NOT NULL,
        CONSTRAINT [pk_session_result_score] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_session_score_result] FOREIGN KEY ([result_id]) REFERENCES [session_result]([id]) ON DELETE CASCADE,
        CONSTRAINT [fk_session_score_metric] FOREIGN KEY ([metric_id]) REFERENCES [skill_metric]([id])
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'coach_feedback')
BEGIN
    CREATE TABLE [coach_feedback] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [session_id] BIGINT NOT NULL,
        [coach_id] BIGINT NOT NULL,
        [member_id] BIGINT NOT NULL,
        [rating] INT NOT NULL,
        [comments] NVARCHAR(1000) NOT NULL,
        [strengths] NVARCHAR(500) NULL,
        [areas_for_improvement] NVARCHAR(500) NULL,
        [created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_coach_feedback_created_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_coach_feedback] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_coach_feedback_session] FOREIGN KEY ([session_id]) REFERENCES [class_session]([id]),
        CONSTRAINT [fk_coach_feedback_coach] FOREIGN KEY ([coach_id]) REFERENCES [coach_profile]([user_id]),
        CONSTRAINT [fk_coach_feedback_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id]),
        CONSTRAINT [ck_coach_feedback_rating] CHECK ([rating] BETWEEN 1 AND 5)
    );

    CREATE NONCLUSTERED INDEX [ix_coach_feedback_member] ON [coach_feedback]([member_id]);
    CREATE NONCLUSTERED INDEX [ix_coach_feedback_session] ON [coach_feedback]([session_id]);
END;
