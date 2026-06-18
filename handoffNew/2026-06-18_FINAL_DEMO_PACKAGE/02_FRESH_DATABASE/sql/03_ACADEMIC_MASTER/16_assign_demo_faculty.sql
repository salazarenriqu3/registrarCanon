-- Demo grading: assign all active-term class sections to prof.cruz + keep windows open.
-- Safe to re-run. Optional for production; recommended for lifecycle / UAT demos.
USE eacdb;

SET @fac_cruz = (SELECT faculty_id FROM faculty WHERE employee_number = 'prof.cruz' LIMIT 1);
SET @term_id  = (SELECT term_id FROM academic_terms WHERE is_active = 1 LIMIT 1);

UPDATE class_sections cs
SET cs.faculty_id = @fac_cruz
WHERE cs.term_id = @term_id AND @fac_cruz IS NOT NULL;

UPDATE class_schedules sch
JOIN class_sections cs ON cs.section_id = sch.section_id
SET sch.faculty_id = cs.faculty_id
WHERE cs.term_id = @term_id;

UPDATE grading_term_windows
SET start_date = '2026-01-01', end_date = '2026-12-31', override_status = 'FORCE_OPEN', updated_at = NOW()
WHERE term_id = @term_id;

SELECT 'PROF CRUZ SECTIONS' AS check_name, COUNT(*) AS section_count
FROM class_sections cs
JOIN faculty f ON f.faculty_id = cs.faculty_id
WHERE cs.term_id = @term_id AND f.employee_number = 'prof.cruz';
