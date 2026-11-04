-- Read-only inventory. Run separately on EACH specifically authorized snapshot/source.
-- No protected database read is authorized by the presence of this file.
SET NOCOUNT ON;
SELECT DB_NAME() AS source_database, SYSUTCDATETIME() AS observed_at_utc,
       'METADATA_ESTIMATES_NOT_EXACT_ROW_RECONCILIATION' AS count_quality;
SELECT DB_NAME() AS source_database, s.name AS schema_name, t.name AS table_name,
       (SELECT COUNT(*) FROM sys.columns c WHERE c.object_id=t.object_id) AS column_count,
       COALESCE(p.estimated_rows,0) AS estimated_rows
FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id
OUTER APPLY (SELECT SUM(rows) AS estimated_rows FROM sys.partitions
             WHERE object_id=t.object_id AND index_id IN (0,1)) p
ORDER BY s.name,t.name;
SELECT s.name AS schema_name,t.name AS table_name,c.column_id,c.name AS column_name,
       TYPE_NAME(c.user_type_id) AS type_name,c.max_length,c.precision,c.scale,c.is_nullable
FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id
JOIN sys.columns c ON c.object_id=t.object_id ORDER BY s.name,t.name,c.column_id;
IF OBJECT_ID(N'dbo.flyway_schema_history',N'U') IS NOT NULL
    SELECT installed_rank,version,description,script,checksum,success
    FROM dbo.flyway_schema_history ORDER BY installed_rank;
ELSE SELECT 'MISSING_FLYWAY_HISTORY' AS inventory_gap;
