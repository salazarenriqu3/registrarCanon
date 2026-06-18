-- =============================================================================
-- Block offerings parent rows + link class_sections.block_id for block-coded sections.
-- Run AFTER: seed_all_program_block_sections_calendar.sql
-- Safe to re-run.
-- =============================================================================
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;
USE eacdb;

CREATE TABLE IF NOT EXISTS block_offerings (
    block_id INT AUTO_INCREMENT PRIMARY KEY,
    term_id INT NOT NULL,
    program_code VARCHAR(32) NOT NULL,
    year_level TINYINT NOT NULL,
    semester_number TINYINT NOT NULL,
    section_group VARCHAR(10) NOT NULL DEFAULT 'A',
    max_capacity INT NOT NULL DEFAULT 40,
    faculty_id INT NULL,
    curriculum_id INT NULL,
    block_status VARCHAR(20) NOT NULL DEFAULT 'Open',
    UNIQUE KEY uk_block_scope (term_id, program_code, year_level, semester_number, section_group),
    KEY idx_block_term (term_id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

ALTER TABLE block_offerings CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;

SET @has_block_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'class_sections' AND COLUMN_NAME = 'block_id'
);
SET @ddl := IF(@has_block_col = 0,
    'ALTER TABLE class_sections ADD COLUMN block_id INT NULL, ADD KEY idx_cs_block (block_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO block_offerings (
    term_id, program_code, year_level, semester_number, section_group,
    max_capacity, curriculum_id, block_status
)
SELECT DISTINCT
    cs.term_id,
    SUBSTRING_INDEX(cs.section_code, '-', 1) AS program_code,
    CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(cs.section_code, '-', 2), '-', -1) AS UNSIGNED) AS year_level,
    CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(cs.section_code, '-', 3), '-', -1) AS UNSIGNED) AS semester_number,
    SUBSTRING_INDEX(cs.section_code, '-', -1) AS section_group,
    40,
    (
        SELECT ct.curriculum_id
        FROM curriculum_templates ct
        JOIN programs p ON p.program_id = ct.program_id
        WHERE p.program_code = SUBSTRING_INDEX(cs.section_code, '-', 1)
          AND COALESCE(ct.is_active, 0) = 1
        ORDER BY ct.version_number DESC, ct.curriculum_id DESC
        LIMIT 1
    ),
    'Open'
FROM class_sections cs
WHERE cs.section_code REGEXP '^[A-Z0-9]+-[0-9]+-[0-9]+-[A-Z]$';

UPDATE class_sections cs
JOIN block_offerings bo
  ON bo.term_id = cs.term_id
 AND bo.program_code COLLATE utf8mb4_uca1400_ai_ci = SUBSTRING_INDEX(cs.section_code, '-', 1) COLLATE utf8mb4_uca1400_ai_ci
 AND bo.year_level = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(cs.section_code, '-', 2), '-', -1) AS UNSIGNED)
 AND bo.semester_number = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(cs.section_code, '-', 3), '-', -1) AS UNSIGNED)
 AND bo.section_group COLLATE utf8mb4_uca1400_ai_ci = SUBSTRING_INDEX(cs.section_code, '-', -1) COLLATE utf8mb4_uca1400_ai_ci
SET cs.block_id = bo.block_id
WHERE cs.section_code REGEXP '^[A-Z0-9]+-[0-9]+-[0-9]+-[A-Z]$'
  AND (cs.block_id IS NULL OR cs.block_id <> bo.block_id);

SELECT bo.term_id,
       COUNT(*) AS block_offerings,
       SUM((SELECT COUNT(*) FROM class_sections cs WHERE cs.block_id = bo.block_id)) AS linked_sections
FROM block_offerings bo
GROUP BY bo.term_id
ORDER BY bo.term_id;
