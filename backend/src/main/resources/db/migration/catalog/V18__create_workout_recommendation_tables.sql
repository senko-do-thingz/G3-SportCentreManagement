-- ============================================================================
-- Migration: V18__create_workout_recommendation_tables.sql
-- Description: Creates tables for AI workout recommendation rule sets,
--              workout plans, exercises, and coach reviews.
-- ============================================================================

-- Step 1: Sequence for workout plan codes
IF NOT EXISTS (SELECT 1 FROM sys.sequences WHERE name = 'seq_workout_plan_code')
BEGIN
    CREATE SEQUENCE seq_workout_plan_code START WITH 1000 INCREMENT BY 1;
END;

-- Step 2: Recommendation rule sets
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'recommendation_rule_set')
BEGIN
    CREATE TABLE [recommendation_rule_set] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [name] NVARCHAR(100) NOT NULL,
        [description] NVARCHAR(500) NULL,
        [is_active] BIT NOT NULL CONSTRAINT [df_rec_rule_set_active] DEFAULT 1,
        [created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_rec_rule_set_created_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_recommendation_rule_set] PRIMARY KEY CLUSTERED ([id])
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'recommendation_rule_weight')
BEGIN
    CREATE TABLE [recommendation_rule_weight] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [rule_set_id] BIGINT NOT NULL,
        [parameter_name] VARCHAR(100) NOT NULL,
        [weight] DECIMAL(5,2) NOT NULL,
        CONSTRAINT [pk_recommendation_rule_weight] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_rec_rule_weight_set] FOREIGN KEY ([rule_set_id]) REFERENCES [recommendation_rule_set]([id]) ON DELETE CASCADE
    );
END;

-- Step 3: Workout plans and candidates
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'workout_plan')
BEGIN
    CREATE TABLE [workout_plan] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [plan_code] VARCHAR(30) NOT NULL,
        [member_id] BIGINT NOT NULL,
        [name] NVARCHAR(150) NOT NULL,
        [goal] VARCHAR(50) NOT NULL,
        [difficulty_level] VARCHAR(30) NOT NULL,
        [duration_weeks] INT NOT NULL,
        [status] VARCHAR(20) NOT NULL CONSTRAINT [df_workout_plan_status] DEFAULT 'DRAFT',
        [created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_workout_plan_created_at] DEFAULT SYSDATETIME(),
        [updated_at] DATETIME2(0) NOT NULL CONSTRAINT [df_workout_plan_updated_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_workout_plan] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [uq_workout_plan_code] UNIQUE ([plan_code]),
        CONSTRAINT [fk_workout_plan_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id]),
        CONSTRAINT [ck_workout_plan_weeks] CHECK ([duration_weeks] > 0)
    );

    CREATE NONCLUSTERED INDEX [ix_workout_plan_member] ON [workout_plan]([member_id]);
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'workout_plan_candidate')
BEGIN
    CREATE TABLE [workout_plan_candidate] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [member_id] BIGINT NOT NULL,
        [goal] VARCHAR(50) NOT NULL,
        [fitness_level] VARCHAR(30) NOT NULL,
        [score] DECIMAL(5,2) NOT NULL,
        [suggested_plan_id] BIGINT NULL,
        [created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_workout_candidate_created_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_workout_plan_candidate] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_workout_candidate_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id]),
        CONSTRAINT [fk_workout_candidate_plan] FOREIGN KEY ([suggested_plan_id]) REFERENCES [workout_plan]([id])
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'workout_plan_week')
BEGIN
    CREATE TABLE [workout_plan_week] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [plan_id] BIGINT NOT NULL,
        [week_number] INT NOT NULL,
        [focus] NVARCHAR(150) NULL,
        CONSTRAINT [pk_workout_plan_week] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_workout_week_plan] FOREIGN KEY ([plan_id]) REFERENCES [workout_plan]([id]) ON DELETE CASCADE
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'workout_plan_day')
BEGIN
    CREATE TABLE [workout_plan_day] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [week_id] BIGINT NOT NULL,
        [day_number] INT NOT NULL,
        [focus] NVARCHAR(150) NULL,
        CONSTRAINT [pk_workout_plan_day] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_workout_day_week] FOREIGN KEY ([week_id]) REFERENCES [workout_plan_week]([id]) ON DELETE CASCADE
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'workout_plan_exercise')
BEGIN
    CREATE TABLE [workout_plan_exercise] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [day_id] BIGINT NOT NULL,
        [name] NVARCHAR(150) NOT NULL,
        [sets] INT NOT NULL,
        [reps] INT NOT NULL,
        [rest_seconds] INT NOT NULL CONSTRAINT [df_exercise_rest] DEFAULT 60,
        [video_url] VARCHAR(500) NULL,
        [is_completed] BIT NOT NULL CONSTRAINT [df_exercise_completed] DEFAULT 0,
        CONSTRAINT [pk_workout_plan_exercise] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_workout_exercise_day] FOREIGN KEY ([day_id]) REFERENCES [workout_plan_day]([id]) ON DELETE CASCADE
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'plan_review')
BEGIN
    CREATE TABLE [plan_review] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [plan_id] BIGINT NOT NULL,
        [coach_id] BIGINT NOT NULL,
        [status] VARCHAR(20) NOT NULL CONSTRAINT [df_plan_review_status] DEFAULT 'PENDING',
        [feedback] NVARCHAR(2000) NULL,
        [reviewed_at] DATETIME2(0) NULL,
        CONSTRAINT [pk_plan_review] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_plan_review_plan] FOREIGN KEY ([plan_id]) REFERENCES [workout_plan]([id]),
        CONSTRAINT [fk_plan_review_coach] FOREIGN KEY ([coach_id]) REFERENCES [coach_profile]([user_id]),
        CONSTRAINT [ck_plan_review_status] CHECK ([status] IN ('PENDING', 'APPROVED', 'MODIFIED', 'REJECTED'))
    );

    CREATE NONCLUSTERED INDEX [ix_plan_review_coach] ON [plan_review]([coach_id], [status]);
END;
