-- Read-only review artifact. The database owner must authorize an exact target before execution.
-- Run in the already-selected target. This script does not select, migrate or alter any database.
SET NOCOUNT ON;
SELECT DB_NAME() AS selected_database, SERVERPROPERTY('ProductVersion') AS sql_version,
       DATABASEPROPERTYEX(DB_NAME(),'Collation') AS collation;
SELECT version,description,checksum,success,installed_on
FROM dbo.flyway_schema_history ORDER BY installed_rank;
SELECT COUNT_BIG(*) AS prior_failed_migrations FROM dbo.flyway_schema_history WHERE success=0;
SELECT name AS table_name FROM sys.tables
WHERE name IN ('session_plan','member_check_in_pass','class_training_notice','support_request','notification');
SELECT OBJECT_NAME(object_id) AS table_name,name AS index_name,is_unique
FROM sys.indexes WHERE name IN ('uq_member_check_in_pass_hash','ux_class_plan_operation',
    'uq_class_training_notice_operation','ux_support_intake_operation','ux_notification_event');
SELECT COUNT_BIG(*) AS duplicate_active_tier_ranks FROM (
    SELECT tier_rank FROM dbo.membership_card_tier WHERE is_active=1 GROUP BY tier_rank HAVING COUNT_BIG(*)>1
) duplicates;
SELECT settlement_type,payment_status,COUNT_BIG(*) AS payment_count
FROM dbo.payment GROUP BY settlement_type,payment_status;
