USE eacdb;

SELECT 'DATABASE' AS check_name, DATABASE() AS value;

SELECT
    'CURRENT_TERM_SETTING' AS check_name,
    setting_value AS value
FROM system_settings
WHERE setting_key = 'CURRENT_ACADEMIC_TERM';

SELECT
    'ACTIVE_TERM_ROW' AS check_name,
    term_id,
    term_name,
    academic_year,
    semester_number,
    status,
    is_active
FROM academic_terms
WHERE term_id = 1
   OR term_code = (SELECT setting_value FROM system_settings WHERE setting_key = 'CURRENT_ACADEMIC_TERM' LIMIT 1)
ORDER BY term_id
LIMIT 5;

SELECT
    'CORE_TABLE_COUNTS' AS check_name,
    (SELECT COUNT(*) FROM programs) AS programs,
    (SELECT COUNT(*) FROM curriculum_templates) AS curricula,
    (SELECT COUNT(*) FROM courses) AS courses,
    (SELECT COUNT(*) FROM class_sections) AS sections,
    (SELECT COUNT(*) FROM class_schedules) AS schedules,
    (SELECT COUNT(*) FROM applicants) AS applicants,
    (SELECT COUNT(*) FROM students) AS students,
    (SELECT COUNT(*) FROM sys_users) AS sys_users;

SELECT
    'REGISTRAR_ACCOUNTS' AS check_name,
    username,
    role,
    is_active,
    status
FROM sys_users
WHERE username IN ('admin', 'registrar.main', 'registrar.records', 'registrar.scholar', 'registrar.schedule', 'prof.cruz')
ORDER BY username;

SELECT
    'ADMISSION_ACCOUNTS' AS check_name,
    username,
    role,
    enabled
FROM users
WHERE username IN ('admin-adms', 'encoder-adms')
ORDER BY username;
