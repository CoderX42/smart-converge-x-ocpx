-- V94: restore the delivery-attempt sequence invariant required by S10.
--
-- Historical terminal jobs could be re-opened with attempt_count reset to zero.
-- That allowed a later result to reuse attempt_no=1 and prevented V91 from
-- installing the unique (job_id,attempt_no) constraint. This repair preserves
-- every audit row, records changed historical numbers, deterministically
-- renumbers only jobs with duplicate attempt numbers, and then installs the
-- missing database guard. It never rewrites scx_schema_migration.

SET @scx_v94_lock := GET_LOCK('scx_v94_delivery_attempt_integrity',30);

DROP TEMPORARY TABLE IF EXISTS scx_v94_attempt_renumber;
CREATE TEMPORARY TABLE scx_v94_attempt_renumber (
  attempt_id VARCHAR(32) NOT NULL PRIMARY KEY,
  job_id VARCHAR(32) NOT NULL,
  original_attempt_no INT NOT NULL,
  repaired_attempt_no INT NOT NULL,
  KEY idx_scx_v94_attempt_job (job_id,repaired_attempt_no)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

INSERT INTO scx_v94_attempt_renumber
  (attempt_id,job_id,original_attempt_no,repaired_attempt_no)
SELECT ranked.id,ranked.job_id,ranked.attempt_no,ranked.repaired_attempt_no
FROM (
  SELECT attempt_row.id,attempt_row.job_id,attempt_row.attempt_no,
         ROW_NUMBER() OVER (
           PARTITION BY attempt_row.job_id
           ORDER BY attempt_row.create_time,attempt_row.id
         ) AS repaired_attempt_no
  FROM scx_ocpx_delivery_attempt attempt_row
  JOIN (
    SELECT job_id
    FROM scx_ocpx_delivery_attempt
    GROUP BY job_id
    HAVING COUNT(*)<>COUNT(DISTINCT attempt_no)
  ) duplicate_job ON duplicate_job.job_id=attempt_row.job_id
) ranked;

DROP PROCEDURE IF EXISTS scx_v94_preflight;
DELIMITER $$
CREATE PROCEDURE scx_v94_preflight()
BEGIN
  DECLARE invalid_count INT DEFAULT 0;
  DECLARE named_index_rows INT DEFAULT 0;
  DECLARE named_index_non_unique INT DEFAULT 1;
  DECLARE named_index_columns VARCHAR(255) DEFAULT '';

  IF @scx_v94_lock<>1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V94 could not acquire the delivery-attempt repair lock';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM information_schema.tables
  WHERE table_schema=DATABASE() AND table_type='BASE TABLE'
    AND table_name IN ('scx_ocpx_delivery_job','scx_ocpx_delivery_attempt');
  IF invalid_count<>2 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V94 requires the canonical OCPX delivery job and attempt tables';
  END IF;

  SELECT COUNT(*),COALESCE(MIN(non_unique),1),
         COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ','),'')
    INTO named_index_rows,named_index_non_unique,named_index_columns
  FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='scx_ocpx_delivery_attempt'
    AND index_name='uk_scx_ocpx_delivery_attempt_no';
  IF named_index_rows<>0 AND
     (named_index_non_unique<>0 OR named_index_columns<>'job_id,attempt_no') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V94 found an incompatible delivery-attempt index with the canonical name';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM scx_ocpx_delivery_attempt
  WHERE attempt_no<1;
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V94 found non-positive delivery attempt numbers';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM scx_ocpx_delivery_job job_row
  JOIN (SELECT DISTINCT job_id FROM scx_v94_attempt_renumber) target
    ON target.job_id=job_row.id
  WHERE job_row.status='PROCESSING'
     OR (job_row.lease_until IS NOT NULL AND job_row.lease_until>=NOW(3));
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V94 refuses to renumber an active or leased delivery job';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM scx_ocpx_delivery_job job_row
  JOIN (
    SELECT job_id,MAX(repaired_attempt_no) AS repaired_max
    FROM scx_v94_attempt_renumber
    GROUP BY job_id
  ) target ON target.job_id=job_row.id
  WHERE job_row.attempt_count>target.repaired_max;
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V94 found a delivery job count newer than its repairable audit sequence';
  END IF;
END$$
DELIMITER ;

CALL scx_v94_preflight();
DROP PROCEDURE scx_v94_preflight;

SET @scx_v94_original_column_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='scx_ocpx_delivery_attempt'
    AND column_name='original_attempt_no'
);
SET @scx_v94_original_column_ddl := IF(
  @scx_v94_original_column_exists=0,
  'ALTER TABLE scx_ocpx_delivery_attempt ADD COLUMN original_attempt_no INT NULL COMMENT ''Original sequence before forward integrity repair'' AFTER attempt_no',
  'SELECT 1'
);
PREPARE scx_v94_original_column_stmt FROM @scx_v94_original_column_ddl;
EXECUTE scx_v94_original_column_stmt;
DEALLOCATE PREPARE scx_v94_original_column_stmt;

START TRANSACTION;

UPDATE scx_ocpx_delivery_attempt attempt_row
JOIN scx_v94_attempt_renumber repair ON repair.attempt_id=attempt_row.id
SET attempt_row.original_attempt_no=IF(
      repair.original_attempt_no<>repair.repaired_attempt_no,
      COALESCE(attempt_row.original_attempt_no,repair.original_attempt_no),
      attempt_row.original_attempt_no
    ),
    attempt_row.attempt_no=repair.repaired_attempt_no;

UPDATE scx_ocpx_delivery_job job_row
JOIN (
  SELECT job_id,MAX(repaired_attempt_no) AS repaired_max
  FROM scx_v94_attempt_renumber
  GROUP BY job_id
) repair ON repair.job_id=job_row.id
SET job_row.attempt_count=repair.repaired_max,
    job_row.update_time=GREATEST(job_row.update_time,NOW(3));

COMMIT;

SET @scx_v94_unique_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='scx_ocpx_delivery_attempt'
    AND index_name='uk_scx_ocpx_delivery_attempt_no'
);
SET @scx_v94_unique_ddl := IF(
  @scx_v94_unique_exists=0,
  'ALTER TABLE scx_ocpx_delivery_attempt ADD UNIQUE KEY uk_scx_ocpx_delivery_attempt_no (job_id,attempt_no)',
  'SELECT 1'
);
PREPARE scx_v94_unique_stmt FROM @scx_v94_unique_ddl;
EXECUTE scx_v94_unique_stmt;
DEALLOCATE PREPARE scx_v94_unique_stmt;

DROP PROCEDURE IF EXISTS scx_v94_verify;
DELIMITER $$
CREATE PROCEDURE scx_v94_verify()
BEGIN
  DECLARE invalid_count INT DEFAULT 0;
  DECLARE index_non_unique INT DEFAULT 1;
  DECLARE index_columns VARCHAR(255) DEFAULT '';

  SELECT COUNT(*) INTO invalid_count
  FROM (
    SELECT job_id,attempt_no
    FROM scx_ocpx_delivery_attempt
    GROUP BY job_id,attempt_no
    HAVING COUNT(*)>1
  ) duplicate_attempt;
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V94 left duplicate delivery attempt numbers';
  END IF;

  SELECT COALESCE(MIN(non_unique),1),
         COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ','),'')
    INTO index_non_unique,index_columns
  FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='scx_ocpx_delivery_attempt'
    AND index_name='uk_scx_ocpx_delivery_attempt_no';
  IF index_non_unique<>0 OR index_columns<>'job_id,attempt_no' THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V94 delivery-attempt unique index verification failed';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM scx_ocpx_delivery_attempt attempt_row
  JOIN scx_v94_attempt_renumber repair ON repair.attempt_id=attempt_row.id
  WHERE attempt_row.attempt_no<>repair.repaired_attempt_no
     OR (repair.original_attempt_no<>repair.repaired_attempt_no AND
         attempt_row.original_attempt_no<>repair.original_attempt_no);
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V94 delivery-attempt audit renumbering verification failed';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM scx_ocpx_delivery_job job_row
  JOIN (
    SELECT job_id,MAX(repaired_attempt_no) AS repaired_max
    FROM scx_v94_attempt_renumber
    GROUP BY job_id
  ) repair ON repair.job_id=job_row.id
  WHERE job_row.attempt_count<>repair.repaired_max;
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V94 delivery job attempt count verification failed';
  END IF;
END$$
DELIMITER ;

CALL scx_v94_verify();
DROP PROCEDURE scx_v94_verify;
DROP TEMPORARY TABLE scx_v94_attempt_renumber;
DO RELEASE_LOCK('scx_v94_delivery_attempt_integrity');
