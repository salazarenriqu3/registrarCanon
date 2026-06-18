USE eacdb;

SELECT 'active_term' AS check_name, term_id, term_code, status
FROM academic_terms
WHERE is_active = 1;

SELECT 'term_setting' AS check_name, setting_value
FROM system_settings
WHERE setting_key = 'CURRENT_ACADEMIC_TERM';

SELECT 'enlistment_status_column' AS check_name, COUNT(*) AS result
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'student_enlistments'
  AND COLUMN_NAME = 'enlistment_status';

SELECT 'active_programs' AS check_name, COUNT(*) AS result
FROM programs
WHERE COALESCE(active_status, 1) = 1;

SELECT 'active_curricula' AS check_name, COUNT(*) AS result
FROM curriculum_templates
WHERE COALESCE(is_active, 0) = 1;

SELECT 'active_term_sections' AS check_name, COUNT(*) AS result
FROM class_sections cs
JOIN academic_terms at ON at.term_id = cs.term_id
WHERE at.is_active = 1;

SELECT 'active_term_schedules' AS check_name, COUNT(*) AS result
FROM class_schedules sch
JOIN class_sections cs ON cs.section_id = sch.section_id
JOIN academic_terms at ON at.term_id = cs.term_id
WHERE at.is_active = 1;

SELECT 'active_term_exact_fee_rows' AS check_name, COUNT(*) AS result
FROM program_fee_settings pfs
JOIN academic_terms at ON at.term_id = pfs.term_id
WHERE at.is_active = 1 AND pfs.is_active = 1;

SELECT 'prof_cruz_sections' AS check_name, COUNT(*) AS result
FROM class_sections cs
JOIN academic_terms at ON at.term_id = cs.term_id AND at.is_active = 1
JOIN faculty f ON f.faculty_id = cs.faculty_id
WHERE f.employee_number = 'prof.cruz';

SELECT 'scholarship_candidates' AS check_name, student_number, real_name
FROM students
WHERE student_number IN ('SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS')
ORDER BY student_number;
