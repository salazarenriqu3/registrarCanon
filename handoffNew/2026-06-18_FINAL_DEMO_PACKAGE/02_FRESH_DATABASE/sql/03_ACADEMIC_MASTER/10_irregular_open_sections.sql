-- =============================================================================
-- IRREGULAR / OPEN sections (non-block) for manual irregular enlistment
-- Run AFTER seed_all_program_block_sections_calendar.sql, BEFORE seed_all_class_schedules.sql
--
-- Block pattern (regular only):  BSIT-1-2-A  →  PROGRAM-YEAR-SEM-GROUP
-- Irregular pattern (this seed):  IRREG-A     →  dedicated open section per course/term
--
-- Enrollment cashier irregular mode excludes block sections and lists IRREG-A rows.
-- Safe to re-run.
-- =============================================================================
USE eacdb;
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

INSERT INTO class_sections (course_id, term_id, section_code, max_capacity, section_status, semester_number)
SELECT
    blk.course_id,
    blk.term_id,
    'IRREG-A',
    40,
    'Open',
    MIN(blk.semester_number)
FROM class_sections blk
JOIN academic_terms at ON at.term_id = blk.term_id
WHERE blk.section_code REGEXP '^[A-Z]+-[0-9]+-[0-9]+-[A-Z]$'
  AND at.term_code REGEXP '^[12]1[0-9]{8}$'
  AND NOT EXISTS (
      SELECT 1
      FROM class_sections ir
      WHERE ir.course_id = blk.course_id
        AND ir.term_id = blk.term_id
        AND ir.section_code = 'IRREG-A'
  )
GROUP BY blk.course_id, blk.term_id;

SELECT at.term_code,
       COUNT(DISTINCT CASE WHEN cs.section_code = 'IRREG-A' THEN cs.section_id END) AS irreg_sections,
       COUNT(DISTINCT CASE WHEN cs.section_code REGEXP '^[A-Z]+-[0-9]+-[0-9]+-[A-Z]$' THEN cs.section_id END) AS block_sections
FROM academic_terms at
LEFT JOIN class_sections cs ON cs.term_id = at.term_id
WHERE at.term_code REGEXP '^[12]1[0-9]{8}$'
GROUP BY at.term_id, at.term_code
ORDER BY at.term_code;
