-- Step 1: STAGED vs COMMITTED enlistment (preview vs official billing).
-- Safe to re-run. Existing rows become COMMITTED (legacy official loads).

USE eacdb;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'student_enlistments'
      AND COLUMN_NAME = 'enlistment_status'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE student_enlistments ADD COLUMN enlistment_status VARCHAR(16) NOT NULL DEFAULT ''COMMITTED'' AFTER section_id',
    'SELECT ''enlistment_status already exists'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE student_enlistments SET enlistment_status = 'COMMITTED'
WHERE enlistment_status IS NULL OR TRIM(enlistment_status) = '';
