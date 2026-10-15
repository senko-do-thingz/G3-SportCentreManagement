-- ============================================================================
-- Migration: V17__create_support_and_ai_tables.sql
-- Description: Creates tables for customer support requests, AI conversation
--              histories, topics, and prompt chips.
-- ============================================================================

-- Step 1: Support request sequence
IF NOT EXISTS (SELECT 1 FROM sys.sequences WHERE name = 'seq_support_request_code')
BEGIN
    CREATE SEQUENCE seq_support_request_code START WITH 1000 INCREMENT BY 1;
END;

-- Step 2: Support tickets
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'support_request')
BEGIN
    CREATE TABLE [support_request] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [request_code] VARCHAR(30) NOT NULL,
        [member_id] BIGINT NOT NULL,
        [subject] NVARCHAR(200) NOT NULL,
        [category] VARCHAR(50) NOT NULL,
        [description] NVARCHAR(2000) NOT NULL,
        [priority] VARCHAR(20) NOT NULL CONSTRAINT [df_support_req_priority] DEFAULT 'MEDIUM',
        [status] VARCHAR(20) NOT NULL CONSTRAINT [df_support_req_status] DEFAULT 'OPEN',
        [assigned_staff_id] BIGINT NULL,
        [resolution_notes] NVARCHAR(2000) NULL,
        [created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_support_req_created_at] DEFAULT SYSDATETIME(),
        [updated_at] DATETIME2(0) NOT NULL CONSTRAINT [df_support_req_updated_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_support_request] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [uq_support_request_code] UNIQUE ([request_code]),
        CONSTRAINT [fk_support_req_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id]),
        CONSTRAINT [fk_support_req_staff] FOREIGN KEY ([assigned_staff_id]) REFERENCES [user_account]([id]),
        CONSTRAINT [ck_support_req_priority] CHECK ([priority] IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
        CONSTRAINT [ck_support_req_status] CHECK ([status] IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'))
    );

    CREATE NONCLUSTERED INDEX [ix_support_req_member] ON [support_request]([member_id]);
    CREATE NONCLUSTERED INDEX [ix_support_req_status] ON [support_request]([status]);
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'support_request_message')
BEGIN
    CREATE TABLE [support_request_message] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [request_id] BIGINT NOT NULL,
        [sender_id] BIGINT NOT NULL,
        [message_body] NVARCHAR(2000) NOT NULL,
        [created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_support_msg_created_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_support_request_message] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_support_msg_request] FOREIGN KEY ([request_id]) REFERENCES [support_request]([id]) ON DELETE CASCADE,
        CONSTRAINT [fk_support_msg_sender] FOREIGN KEY ([sender_id]) REFERENCES [user_account]([id])
    );

    CREATE NONCLUSTERED INDEX [ix_support_msg_request] ON [support_request_message]([request_id], [created_at]);
END;

-- Step 3: AI Assistant configuration and conversations
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'assistant_setting')
BEGIN
    CREATE TABLE [assistant_setting] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [setting_key] VARCHAR(100) NOT NULL,
        [setting_value] NVARCHAR(MAX) NOT NULL,
        [description] NVARCHAR(500) NULL,
        CONSTRAINT [pk_assistant_setting] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [uq_assistant_setting_key] UNIQUE ([setting_key])
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'assistant_topic')
BEGIN
    CREATE TABLE [assistant_topic] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [title] NVARCHAR(150) NOT NULL,
        [category] VARCHAR(50) NOT NULL,
        [content] NVARCHAR(2000) NOT NULL,
        [is_active] BIT NOT NULL CONSTRAINT [df_assistant_topic_active] DEFAULT 1,
        CONSTRAINT [pk_assistant_topic] PRIMARY KEY CLUSTERED ([id])
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'assistant_quick_prompt')
BEGIN
    CREATE TABLE [assistant_quick_prompt] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [topic_id] BIGINT NULL,
        [prompt_text] NVARCHAR(255) NOT NULL,
        [display_order] INT NOT NULL CONSTRAINT [df_quick_prompt_order] DEFAULT 0,
        CONSTRAINT [pk_assistant_quick_prompt] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_quick_prompt_topic] FOREIGN KEY ([topic_id]) REFERENCES [assistant_topic]([id]) ON DELETE SET NULL
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'ai_conversation')
BEGIN
    CREATE TABLE [ai_conversation] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [user_id] BIGINT NOT NULL,
        [title] NVARCHAR(200) NULL,
        [rating] INT NULL,
        [feedback_notes] NVARCHAR(500) NULL,
        [created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_ai_conv_created_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_ai_conversation] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_ai_conv_user] FOREIGN KEY ([user_id]) REFERENCES [user_account]([id]),
        CONSTRAINT [ck_ai_conv_rating] CHECK ([rating] IS NULL OR [rating] BETWEEN 1 AND 5)
    );

    CREATE NONCLUSTERED INDEX [ix_ai_conv_user] ON [ai_conversation]([user_id]);
END;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'ai_message')
BEGIN
    CREATE TABLE [ai_message] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [conversation_id] BIGINT NOT NULL,
        [sender_type] VARCHAR(20) NOT NULL,
        [content] NVARCHAR(MAX) NOT NULL,
        [sent_at] DATETIME2(0) NOT NULL CONSTRAINT [df_ai_msg_sent_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_ai_message] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_ai_msg_conversation] FOREIGN KEY ([conversation_id]) REFERENCES [ai_conversation]([id]) ON DELETE CASCADE,
        CONSTRAINT [ck_ai_msg_sender] CHECK ([sender_type] IN ('USER', 'ASSISTANT', 'SYSTEM'))
    );

    CREATE NONCLUSTERED INDEX [ix_ai_msg_conversation] ON [ai_message]([conversation_id], [sent_at]);
END;
