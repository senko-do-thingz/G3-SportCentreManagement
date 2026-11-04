-- Run only on an explicitly authorized target. This file has no DML or DDL.
SET NOCOUNT ON;
SELECT DB_NAME() AS database_name, SERVERPROPERTY('ProductVersion') AS sql_version;
SELECT version, script, checksum, success FROM dbo.flyway_schema_history ORDER BY installed_rank;
SELECT s.name AS schema_name, t.name AS table_name
FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id ORDER BY s.name,t.name;
SELECT name, is_disabled, is_not_trusted FROM sys.foreign_keys ORDER BY name;
SELECT name, is_disabled, is_not_trusted FROM sys.check_constraints ORDER BY name;
SELECT name, is_disabled FROM sys.triggers WHERE is_ms_shipped=0 ORDER BY name;
SELECT COUNT_BIG(*) AS cash_rows, COALESCE(SUM(amount),0) AS cash_amount FROM dbo.cash_event;
SELECT COUNT_BIG(*) AS actual_paid_rows FROM dbo.vw_paid_payment;
SELECT COUNT_BIG(*) AS simulated_rows FROM dbo.payment WHERE settlement_type='SIMULATED';
