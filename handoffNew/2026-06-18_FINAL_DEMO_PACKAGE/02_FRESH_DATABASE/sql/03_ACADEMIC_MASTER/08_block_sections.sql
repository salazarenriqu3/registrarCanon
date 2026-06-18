-- =============================================================================
-- Block sections (PROGRAM-Y-S-A) for ALL active programs, ALL calendar terms
-- Safe to re-run: inserts only missing (course_id, term_id, section_code) rows.
-- Run AFTER: registrar/db/demo_scripts/00_upsert_academic_terms_calendar.sql
-- =============================================================================
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;
USE eacdb;

INSERT INTO class_sections (course_id, term_id, section_code, max_capacity, section_status, semester_number)
SELECT DISTINCT
    c.course_id,
    at.term_id,
    CONCAT(p.program_code, '-', cc.year_level, '-', cc.semester_number, '-A'),
    40,
    'Open',
    cc.semester_number
FROM curriculum_courses cc
JOIN curriculum_templates ct ON cc.curriculum_id = ct.curriculum_id
JOIN programs p ON ct.program_id = p.program_id
JOIN courses c ON cc.course_id = c.course_id
JOIN academic_terms at
    ON at.term_code REGEXP '^[12]1[0-9]{8}$'
   AND at.semester_number = cc.semester_number
WHERE COALESCE(p.active_status, 1) = 1
  AND COALESCE(ct.is_active, 0) = 1
  AND COALESCE(c.onlist, 1) = 1
  AND NOT EXISTS (
      SELECT 1
      FROM class_sections cs
      WHERE cs.course_id = c.course_id
        AND cs.term_id = at.term_id
        AND cs.section_code = CONCAT(p.program_code, '-', cc.year_level, '-', cc.semester_number, '-A')
  );

SELECT at.term_code,
       COUNT(DISTINCT cs.section_id) AS block_sections,
       COUNT(DISTINCT p.program_code) AS programs_with_sections
FROM academic_terms at
LEFT JOIN class_sections cs ON cs.term_id = at.term_id
LEFT JOIN programs p ON cs.section_code LIKE CONCAT(p.program_code, '-%')
WHERE at.term_code REGEXP '^[12]1[0-9]{8}$'
GROUP BY at.term_id, at.term_code
ORDER BY at.term_code;
