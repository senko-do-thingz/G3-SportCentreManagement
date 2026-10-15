-- ============================================================================
-- Migration: V14__create_payment_and_invoice_tables.sql
-- Description: Creates payment, invoice, and invoice_line tables, adds foreign
--              key payment_id to member_card, sport_package_registration, and
--              refund_request, and creates sequences for payments and invoices.
-- ============================================================================

-- Step 1: Sequences
IF NOT EXISTS (SELECT 1 FROM sys.sequences WHERE name = 'seq_payment_code')
BEGIN
    CREATE SEQUENCE seq_payment_code START WITH 1000 INCREMENT BY 1;
END;

IF NOT EXISTS (SELECT 1 FROM sys.sequences WHERE name = 'seq_invoice_number')
BEGIN
    CREATE SEQUENCE seq_invoice_number START WITH 1000 INCREMENT BY 1;
END;

-- Step 2: Table payment
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'payment')
BEGIN
    CREATE TABLE [payment] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [payment_code] VARCHAR(30) NOT NULL,
        [member_id] BIGINT NOT NULL,
        [amount] DECIMAL(12,2) NOT NULL,
        [payment_method] VARCHAR(30) NOT NULL,
        [payment_status] VARCHAR(20) NOT NULL,
        [reference_code] VARCHAR(100) NULL,
        [notes] NVARCHAR(500) NULL,
        [payment_time] DATETIME2(0) NOT NULL CONSTRAINT [df_payment_payment_time] DEFAULT SYSDATETIME(),
        [version] INT NOT NULL CONSTRAINT [df_payment_version] DEFAULT 0,
        [created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_payment_created_at] DEFAULT SYSDATETIME(),
        [updated_at] DATETIME2(0) NOT NULL CONSTRAINT [df_payment_updated_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_payment] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [uq_payment_code] UNIQUE ([payment_code]),
        CONSTRAINT [fk_payment_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id]),
        CONSTRAINT [ck_payment_amount] CHECK ([amount] > 0),
        CONSTRAINT [ck_payment_method] CHECK ([payment_method] IN ('CASH', 'CREDIT_CARD', 'BANK_TRANSFER', 'MOMO', 'VNPAY', 'ZALOPAY')),
        CONSTRAINT [ck_payment_status] CHECK ([payment_status] IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'))
    );

    CREATE NONCLUSTERED INDEX [ix_payment_member_id] ON [payment]([member_id]);
    CREATE NONCLUSTERED INDEX [ix_payment_payment_time] ON [payment]([payment_time]);
    CREATE NONCLUSTERED INDEX [ix_payment_status] ON [payment]([payment_status]);
END;

-- Step 3: Add payment_id foreign keys to existing tables
IF NOT EXISTS (
    SELECT 1 FROM sys.columns c JOIN sys.tables t ON t.object_id = c.object_id
    WHERE t.name = 'member_card' AND c.name = 'payment_id'
)
BEGIN
    ALTER TABLE [member_card]
    ADD [payment_id] BIGINT NULL
        CONSTRAINT [fk_member_card_payment] FOREIGN KEY REFERENCES [payment]([id]);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.columns c JOIN sys.tables t ON t.object_id = c.object_id
    WHERE t.name = 'sport_package_registration' AND c.name = 'payment_id'
)
BEGIN
    ALTER TABLE [sport_package_registration]
    ADD [payment_id] BIGINT NULL
        CONSTRAINT [fk_package_reg_payment] FOREIGN KEY REFERENCES [payment]([id]);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.columns c JOIN sys.tables t ON t.object_id = c.object_id
    WHERE t.name = 'refund_request' AND c.name = 'payment_id'
)
BEGIN
    ALTER TABLE [refund_request]
    ADD [payment_id] BIGINT NULL
        CONSTRAINT [fk_refund_request_payment] FOREIGN KEY REFERENCES [payment]([id]);
END;

-- Step 4: Table invoice
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'invoice')
BEGIN
    CREATE TABLE [invoice] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [invoice_number] VARCHAR(30) NOT NULL,
        [payment_id] BIGINT NOT NULL,
        [member_id] BIGINT NOT NULL,
        [subtotal_amount] DECIMAL(12,2) NOT NULL,
        [discount_amount] DECIMAL(12,2) NOT NULL CONSTRAINT [df_invoice_discount] DEFAULT 0,
        [tax_amount] DECIMAL(12,2) NOT NULL CONSTRAINT [df_invoice_tax] DEFAULT 0,
        [total_amount] DECIMAL(12,2) NOT NULL,
        [status] VARCHAR(20) NOT NULL,
        [issue_date] DATETIME2(0) NOT NULL CONSTRAINT [df_invoice_issue_date] DEFAULT SYSDATETIME(),
        [created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_invoice_created_at] DEFAULT SYSDATETIME(),
        [updated_at] DATETIME2(0) NOT NULL CONSTRAINT [df_invoice_updated_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_invoice] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [uq_invoice_number] UNIQUE ([invoice_number]),
        CONSTRAINT [fk_invoice_payment] FOREIGN KEY ([payment_id]) REFERENCES [payment]([id]),
        CONSTRAINT [fk_invoice_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id]),
        CONSTRAINT [ck_invoice_amounts] CHECK ([total_amount] >= 0 AND [subtotal_amount] >= 0),
        CONSTRAINT [ck_invoice_status] CHECK ([status] IN ('ISSUED', 'PAID', 'CANCELLED', 'REFUNDED'))
    );

    CREATE NONCLUSTERED INDEX [ix_invoice_payment_id] ON [invoice]([payment_id]);
    CREATE NONCLUSTERED INDEX [ix_invoice_member_id] ON [invoice]([member_id]);
    CREATE NONCLUSTERED INDEX [ix_invoice_issue_date] ON [invoice]([issue_date]);
END;

-- Step 5: Table invoice_line
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'invoice_line')
BEGIN
    CREATE TABLE [invoice_line] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [invoice_id] BIGINT NOT NULL,
        [item_type] VARCHAR(30) NOT NULL,
        [item_reference_id] BIGINT NOT NULL,
        [description] NVARCHAR(255) NOT NULL,
        [quantity] INT NOT NULL CONSTRAINT [df_invoice_line_quantity] DEFAULT 1,
        [unit_price] DECIMAL(12,2) NOT NULL,
        [line_total] DECIMAL(12,2) NOT NULL,
        [created_at] DATETIME2(0) NOT NULL CONSTRAINT [df_invoice_line_created_at] DEFAULT SYSDATETIME(),
        CONSTRAINT [pk_invoice_line] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_invoice_line_invoice] FOREIGN KEY ([invoice_id]) REFERENCES [invoice]([id]) ON DELETE CASCADE,
        CONSTRAINT [ck_invoice_line_quantity] CHECK ([quantity] > 0),
        CONSTRAINT [ck_invoice_line_item_type] CHECK ([item_type] IN ('SPORT_PACKAGE', 'MEMBERSHIP_CARD', 'CLASS_DROP_IN', 'PENALTY_FEE'))
    );

    CREATE NONCLUSTERED INDEX [ix_invoice_line_invoice_id] ON [invoice_line]([invoice_id]);
END;
