-- ============================================================================
-- Migration: V15__create_class_waitlist_table.sql
-- Description: Creates waitlist_entry table for class sessions at full capacity.
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'waitlist_entry')
BEGIN
    CREATE TABLE [waitlist_entry] (
        [id] BIGINT IDENTITY(1,1) NOT NULL,
        [session_id] BIGINT NOT NULL,
        [member_id] BIGINT NOT NULL,
        [status] VARCHAR(20) NOT NULL CONSTRAINT [df_waitlist_status] DEFAULT 'WAITING',
        [joined_at] DATETIME2(0) NOT NULL CONSTRAINT [df_waitlist_joined_at] DEFAULT SYSDATETIME(),
        [offered_at] DATETIME2(0) NULL,
        [offer_expires_at] DATETIME2(0) NULL,
        [promoted_booking_id] BIGINT NULL,
        [cancelled_at] DATETIME2(0) NULL,
        CONSTRAINT [pk_waitlist_entry] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [fk_waitlist_session] FOREIGN KEY ([session_id]) REFERENCES [class_session]([id]),
        CONSTRAINT [fk_waitlist_member] FOREIGN KEY ([member_id]) REFERENCES [member_profile]([user_id]),
        CONSTRAINT [fk_waitlist_promoted_booking] FOREIGN KEY ([promoted_booking_id]) REFERENCES [booking]([id]),
        CONSTRAINT [ck_waitlist_status] CHECK ([status] IN ('WAITING', 'OFFERED', 'PROMOTED', 'EXPIRED', 'CANCELLED'))
    );

    CREATE NONCLUSTERED INDEX [ix_waitlist_session_status_joined] ON [waitlist_entry]([session_id], [status], [joined_at]);
    CREATE NONCLUSTERED INDEX [ix_waitlist_member_status] ON [waitlist_entry]([member_id], [status]);
END;
