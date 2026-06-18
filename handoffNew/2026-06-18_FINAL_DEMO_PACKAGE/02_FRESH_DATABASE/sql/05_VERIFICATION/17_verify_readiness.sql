-- Full post-bootstrap verification. Expect PASS rows below for demo go-live.
USE eacdb;

-- ── Term authority ──────────────────────────────────────────────────────────
SELECT 'ACTIVE TERM' AS check_name, term_id, term_code, term_name, is_active, status
FROM academic_terms WHERE is_active = 1;

SELECT 'CURRENT_ACADEMIC_TERM' AS check_name, setting_value
FROM system_settings WHERE setting_key = 'CURRENT_ACADEMIC_TERM';

-- ── Finance policy gates ────────────────────────────────────────────────────
SELECT 'FINANCE GATES' AS check_name, setting_key, setting_value
FROM system_settings
WHERE setting_key IN (
  'ADMISSION_MIN_PAYMENT', 'DOWNPAYMENT_THRESHOLD', 'DOWNPAYMENT_PERCENT', 'ACCOUNTING_BLOCK_THRESHOLD'
)
ORDER BY setting_key;

SELECT 'INSTALLMENT PLAN ROWS' AS check_name, COUNT(*) AS cnt
FROM term_installment_plan;

SELECT 'ENROLLMENT SETTINGS' AS check_name, COUNT(*) AS cnt
FROM enrollment_settings;

-- ── Fees (active term) ──────────────────────────────────────────────────────
SELECT 'FEE ROWS active term' AS check_name, COUNT(*) AS cnt
FROM program_fee_settings pfs
JOIN academic_terms t ON t.term_id = pfs.term_id AND t.is_active = 1;

SELECT 'FEE GAPS active term' AS check_name,
       SUM(CASE WHEN pfs.fee_setting_id IS NULL
                 OR (COALESCE(pfs.fee_tuition_per_unit,0)=0 AND COALESCE(pfs.fee_lec_per_unit,0)=0 AND COALESCE(pfs.fee_rle_per_unit,0)=0)
            THEN 1 ELSE 0 END) AS unresolved,
       COUNT(*) AS scopes
FROM programs p
CROSS JOIN (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) yl
CROSS JOIN (SELECT 1 n UNION SELECT 2) sem
JOIN academic_terms t ON t.is_active = 1
LEFT JOIN program_fee_settings pfs
  ON pfs.program_id = p.program_id AND pfs.term_id = t.term_id
 AND pfs.year_level = yl.n AND pfs.semester_number = sem.n AND pfs.is_active = 1
WHERE COALESCE(p.active_status, 1) = 1;

-- ── Fees all calendar terms (transition readiness) ──────────────────────────
SELECT 'FEE ROWS BY CALENDAR TERM' AS check_name, at.term_code, COUNT(pfs.fee_setting_id) AS fee_rows
FROM academic_terms at
LEFT JOIN program_fee_settings pfs ON pfs.term_id = at.term_id AND pfs.is_active = 1
WHERE at.term_code REGEXP '^[12]1[0-9]{8}$'
GROUP BY at.term_id, at.term_code
ORDER BY at.term_code;

-- ── Sections & schedules (all calendar terms + active detail) ───────────────
SELECT 'SECTIONS BY CALENDAR TERM' AS check_name, at.term_code,
       SUM(CASE WHEN cs.section_code REGEXP '-[0-9]-[0-9]-[A-D]$' THEN 1 ELSE 0 END) AS block_sections,
       SUM(CASE WHEN cs.section_code = 'IRREG-A' THEN 1 ELSE 0 END) AS irreg_sections,
       SUM(CASE WHEN cs.section_id IS NOT NULL AND sch.schedule_id IS NULL THEN 1 ELSE 0 END) AS tba_sections
FROM academic_terms at
LEFT JOIN class_sections cs ON cs.term_id = at.term_id
LEFT JOIN class_schedules sch ON sch.section_id = cs.section_id
WHERE at.term_code REGEXP '^[12]1[0-9]{8}$'
GROUP BY at.term_id, at.term_code
ORDER BY at.term_code;

SELECT 'BLOCK OFFERINGS active term' AS check_name, COUNT(*) AS cnt
FROM block_offerings bo
JOIN academic_terms t ON t.term_id = bo.term_id AND t.is_active = 1;

SELECT 'BLOCK SECTIONS active term' AS check_name, COUNT(*) AS cnt
FROM class_sections cs
JOIN academic_terms t ON t.term_id = cs.term_id AND t.is_active = 1
WHERE cs.section_code REGEXP '-[0-9]-[0-9]-[A-D]$';

SELECT 'UNSCHEDULED active term' AS check_name, COUNT(*) AS cnt
FROM class_sections cs
JOIN academic_terms t ON t.term_id = cs.term_id AND t.is_active = 1
WHERE NOT EXISTS (SELECT 1 FROM class_schedules sch WHERE sch.section_id = cs.section_id);

-- ── Faculty & grading ───────────────────────────────────────────────────────
SELECT 'FACULTY SEEDED' AS check_name, employee_number, CONCAT(first_name,' ',last_name) AS name
FROM faculty WHERE employee_number LIKE 'prof.%' OR employee_number = 'prof'
ORDER BY employee_number;

SELECT 'PROF CRUZ SECTIONS active term' AS check_name, COUNT(*) AS cnt
FROM class_sections cs
JOIN academic_terms t ON t.term_id = cs.term_id AND t.is_active = 1
JOIN faculty f ON f.faculty_id = cs.faculty_id AND f.employee_number = 'prof.cruz';

SELECT 'GRADING WINDOWS active term' AS check_name, grading_period, override_status
FROM grading_term_windows gtw
JOIN academic_terms t ON t.term_id = gtw.term_id AND t.is_active = 1
ORDER BY grading_period;

-- ── Curriculum ────────────────────────────────────────────────────────────────
SELECT 'ACTIVE PROGRAMS' AS check_name, COUNT(*) AS cnt
FROM programs WHERE COALESCE(active_status, 1) = 1;

SELECT 'PROGRAMS WITH ACTIVE CURRICULUM' AS check_name, COUNT(DISTINCT p.program_id) AS cnt
FROM programs p
JOIN curriculum_templates ct ON ct.program_id = p.program_id AND COALESCE(ct.is_active, 0) = 1
JOIN curriculum_courses cc ON cc.curriculum_id = ct.curriculum_id
WHERE COALESCE(p.active_status, 1) = 1;

-- ── Enrollment contract ─────────────────────────────────────────────────────
SELECT 'ENLISTMENT STATUS COLUMN' AS check_name,
       COUNT(*) AS has_column
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'student_enlistments' AND COLUMN_NAME = 'enlistment_status';

SELECT 'ADMIN USER' AS check_name, username, role, is_active
FROM sys_users WHERE username = 'admin';
