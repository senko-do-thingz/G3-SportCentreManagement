-- ============================================================================
-- Migration V11: Add REFUNDED status to sport_package_registration check constraint
-- Context Refresh alignment for screen F4-10, F4-11, F4-12 refund workflow
-- ============================================================================

ALTER TABLE [sport_package_registration] DROP CONSTRAINT [ck_spr_status];
ALTER TABLE [sport_package_registration] ADD CONSTRAINT [ck_spr_status] CHECK ([status] IN ('PENDING_PAYMENT', 'ACTIVE', 'EXPIRED', 'CANCELLED', 'REFUNDED'));
