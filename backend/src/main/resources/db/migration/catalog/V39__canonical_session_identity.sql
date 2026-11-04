-- V38 is reserved by the separate PR185 demo-photo lineage and is not imported.
-- Validate the entire historical population before changing persistent rows.
IF EXISTS (
    SELECT 1 FROM class_session s LEFT JOIN sport p ON p.id=s.sport_id
    WHERE s.session_date IS NULL OR YEAR(s.session_date) NOT BETWEEN 2000 AND 2099
       OR p.code NOT IN ('FOOTBALL','SWIMMING','BADMINTON','VOLLEYBALL','BASKETBALL','TENNIS')
       OR p.code IS NULL
)
    THROW 51039, 'Session identity backfill requires years 2000-2099 and one of the six canonical sport codes. Inspect class_session and sport before retrying.', 1;

SELECT s.id, s.session_date,
       CASE p.code WHEN 'FOOTBALL' THEN 'FB' WHEN 'SWIMMING' THEN 'SW'
         WHEN 'BADMINTON' THEN 'BM' WHEN 'VOLLEYBALL' THEN 'VB'
         WHEN 'BASKETBALL' THEN 'BK' WHEN 'TENNIS' THEN 'TE' END AS sport_code,
       ROW_NUMBER() OVER (PARTITION BY s.session_date,s.sport_id ORDER BY s.start_time,s.id) AS allocation
INTO #session_identity_backfill
FROM class_session s JOIN sport p ON p.id=s.sport_id;

IF EXISTS (SELECT 1 FROM #session_identity_backfill WHERE allocation>99)
    THROW 51040, 'Session identity backfill exceeds 99 rows for a scheduled date and sport, including cancelled rows. Review grouped class_session counts before retrying.', 1;

ALTER TABLE class_session ADD session_code VARCHAR(10) COLLATE Latin1_General_100_BIN2 NULL;
GO
UPDATE s SET session_code=SUBSTRING(CONVERT(CHAR(8),b.session_date,112),7,2)
    +SUBSTRING(CONVERT(CHAR(8),b.session_date,112),5,2)
    +SUBSTRING(CONVERT(CHAR(8),b.session_date,112),3,2)
    +b.sport_code+RIGHT('0'+CONVERT(VARCHAR(2),b.allocation),2)
FROM class_session s JOIN #session_identity_backfill b ON b.id=s.id;
ALTER TABLE class_session ALTER COLUMN session_code VARCHAR(10) COLLATE Latin1_General_100_BIN2 NOT NULL;
ALTER TABLE class_session ADD CONSTRAINT uq_class_session_code UNIQUE(session_code);
ALTER TABLE class_session ADD CONSTRAINT ck_class_session_code CHECK (
    DATALENGTH(session_code)=10 AND YEAR(session_date) BETWEEN 2000 AND 2099
    AND LEFT(session_code,6)=SUBSTRING(CONVERT(CHAR(8),session_date,112),7,2)
       +SUBSTRING(CONVERT(CHAR(8),session_date,112),5,2)+SUBSTRING(CONVERT(CHAR(8),session_date,112),3,2)
    AND SUBSTRING(session_code,7,2) IN ('FB','SW','BM','VB','BK','TE')
    AND RIGHT(session_code,2) LIKE '[0-9][0-9]' AND RIGHT(session_code,2) BETWEEN '01' AND '99'
);

-- Keep the high-water reservation even if a session is cancelled or deleted.
CREATE TABLE session_code_counter (
    session_date DATE NOT NULL,
    sport_code VARCHAR(2) COLLATE Latin1_General_100_BIN2 NOT NULL,
    last_value INT NOT NULL,
    CONSTRAINT pk_session_code_counter PRIMARY KEY(session_date,sport_code),
    CONSTRAINT ck_session_code_counter_date CHECK(YEAR(session_date) BETWEEN 2000 AND 2099),
    CONSTRAINT ck_session_code_counter_sport CHECK(sport_code IN ('FB','SW','BM','VB','BK','TE')),
    CONSTRAINT ck_session_code_counter_value CHECK(last_value BETWEEN 1 AND 99)
);
INSERT INTO session_code_counter(session_date,sport_code,last_value)
SELECT session_date,sport_code,MAX(CONVERT(INT,allocation))
FROM #session_identity_backfill GROUP BY session_date,sport_code;
DROP TABLE #session_identity_backfill;
