USE eacdb;

SELECT
    'ACTIVE_TERM' AS check_name,
    ss.setting_value AS current_term_code,
    at.term_id,
    at.term_name,
    at.academic_year,
    at.semester_number,
    at.status,
    at.is_active
FROM system_settings ss
LEFT JOIN academic_terms at
    ON at.term_code = ss.setting_value
WHERE ss.setting_key = 'CURRENT_ACADEMIC_TERM';

SELECT
    'SMOKE_APPLICANT' AS check_name,
    reference_number,
    applicant_status,
    program1,
    term_year
FROM applicants
WHERE reference_number = '3SYS-SMOKE-0001';

SELECT
    'LEC_LAB_COMPONENTS' AS check_name,
    course_family_code,
    GROUP_CONCAT(course_code ORDER BY component_type SEPARATOR ', ') AS component_codes,
    SUM(credit_units) AS total_units,
    SUM(lec_units) AS lec_units,
    SUM(lab_units) AS lab_units
FROM courses
WHERE course_family_code IN ('DEMOCP')
GROUP BY course_family_code;

SELECT
    'CURRENT_TERM_SECTION_HEALTH' AS check_name,
    COUNT(*) AS active_term_sections,
    SUM(CASE WHEN cs.faculty_id IS NULL THEN 1 ELSE 0 END) AS missing_faculty_sections
FROM class_sections cs
WHERE cs.term_id = 1;

SELECT
    'CURRENT_TERM_SCHEDULE_HEALTH' AS check_name,
    COUNT(*) AS schedule_rows,
    SUM(CASE WHEN room_id IS NULL THEN 1 ELSE 0 END) AS missing_room_rows
FROM class_schedules sch
JOIN class_sections cs ON cs.section_id = sch.section_id
WHERE cs.term_id = 1;

SELECT
    'WITHDRAWAL_GOVERNANCE_TABLES' AS check_name,
    (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'student_number_release_registry') AS release_registry_exists,
    (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'student_identity_archive') AS identity_archive_exists,
    (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'student_archive_custody_events') AS custody_events_exists;

SELECT
    'GRADE_GOVERNANCE_TABLES' AS check_name,
    (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'grades') AS grades_exists,
    (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'grade_record_events') AS grade_events_exists,
    (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'grade_change_requests') AS grade_change_requests_exists;

SELECT
    'PRE_REG_SORT_ORDER_COLUMN' AS check_name,
    COUNT(*) AS column_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'applicant_pre_reg_subject_lines'
  AND column_name = 'sort_order';
