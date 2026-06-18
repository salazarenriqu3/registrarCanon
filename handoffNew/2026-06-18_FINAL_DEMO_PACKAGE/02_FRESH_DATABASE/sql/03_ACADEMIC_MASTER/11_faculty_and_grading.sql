-- =============================================================================
-- Named professors + course-based assignment + grading windows + grade backfill
-- Run after seed_all_class_schedules.sql (or any section seed).
-- Safe to re-run.
--
-- Logins (password 1234):
--   prof.cruz     Maria Cruz      — BSIT programming / IT lab
--   prof.mendoza  Juan Mendoza    — BSCPE / engineering
--   prof.garcia   Ana Garcia      — Gen Ed / humanities
--   prof.santos   Leo Santos      — Math / statistics
--   prof.reyes    Rosa Reyes      — PE / wellness
--   prof.licuanan Paolo Licuanan  — Networking / databases
--   prof          Professor Demo  — legacy alias → Cruz (same classes as cruz)
-- =============================================================================
USE eacdb;
SET SQL_SAFE_UPDATES = 0;

SET @pwd = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EhsLkT/Oh.reShMkwpfpJ2';
SET @dept_id = COALESCE(
    (SELECT department_id FROM programs WHERE program_code = 'BSIT' LIMIT 1),
    (SELECT department_id FROM departments ORDER BY department_id LIMIT 1),
    1
);
SET @active_term_id = (
    SELECT term_id FROM academic_terms WHERE status = 'ACTIVE' OR is_active = 1
    ORDER BY term_id DESC LIMIT 1
);

-- ── 1. Faculty accounts (sys_users + faculty) ───────────────────────────────
INSERT INTO sys_users (username, password, real_name, first_name, last_name, role, is_active, status)
SELECT v.username, @pwd, v.real_name, v.fn, v.ln, 'Faculty', 1, 'ACTIVE'
FROM (
    SELECT 'prof.cruz' AS username, 'Maria Cruz' AS real_name, 'Maria' AS fn, 'Cruz' AS ln
    UNION ALL SELECT 'prof.mendoza', 'Juan Mendoza', 'Juan', 'Mendoza'
    UNION ALL SELECT 'prof.garcia', 'Ana Garcia', 'Ana', 'Garcia'
    UNION ALL SELECT 'prof.santos', 'Leo Santos', 'Leo', 'Santos'
    UNION ALL SELECT 'prof.reyes', 'Rosa Reyes', 'Rosa', 'Reyes'
    UNION ALL SELECT 'prof.licuanan', 'Paolo Licuanan', 'Paolo', 'Licuanan'
) v
WHERE NOT EXISTS (SELECT 1 FROM sys_users su WHERE su.username = v.username);

UPDATE sys_users SET role = 'Faculty', is_active = 1, status = 'ACTIVE', password = @pwd
WHERE username IN ('prof', 'prof.cruz', 'prof.mendoza', 'prof.garcia', 'prof.santos', 'prof.reyes', 'prof.licuanan', 'faculty');

INSERT INTO faculty (employee_number, first_name, last_name, email, department_id, employment_type, max_teaching_units, active_status)
SELECT v.emp, v.fn, v.ln, v.email, @dept_id, 'FULL_TIME', 24, 1
FROM (
    SELECT 'prof.cruz' AS emp, 'Maria' AS fn, 'Cruz' AS ln, 'mcruz@eac.edu.ph' AS email
    UNION ALL SELECT 'prof.mendoza', 'Juan', 'Mendoza', 'jmendoza@eac.edu.ph'
    UNION ALL SELECT 'prof.garcia', 'Ana', 'Garcia', 'agarcia@eac.edu.ph'
    UNION ALL SELECT 'prof.santos', 'Leo', 'Santos', 'lsantos@eac.edu.ph'
    UNION ALL SELECT 'prof.reyes', 'Rosa', 'Reyes', 'rreyes@eac.edu.ph'
    UNION ALL SELECT 'prof.licuanan', 'Paolo', 'Licuanan', 'plicuanan@eac.edu.ph'
    UNION ALL SELECT 'prof', 'Maria', 'Cruz', 'prof@eac.edu.ph'
) v
WHERE NOT EXISTS (SELECT 1 FROM faculty f WHERE f.employee_number = v.emp);

UPDATE faculty SET first_name = 'Maria', last_name = 'Cruz', email = 'prof@eac.edu.ph', active_status = 1
WHERE employee_number = 'prof';

SET @fac_cruz     = (SELECT faculty_id FROM faculty WHERE employee_number = 'prof.cruz' LIMIT 1);
SET @fac_mendoza  = (SELECT faculty_id FROM faculty WHERE employee_number = 'prof.mendoza' LIMIT 1);
SET @fac_garcia   = (SELECT faculty_id FROM faculty WHERE employee_number = 'prof.garcia' LIMIT 1);
SET @fac_santos   = (SELECT faculty_id FROM faculty WHERE employee_number = 'prof.santos' LIMIT 1);
SET @fac_reyes    = (SELECT faculty_id FROM faculty WHERE employee_number = 'prof.reyes' LIMIT 1);
SET @fac_licuanan = (SELECT faculty_id FROM faculty WHERE employee_number = 'prof.licuanan' LIMIT 1);
SET @fac_prof     = (SELECT faculty_id FROM faculty WHERE employee_number = 'prof' LIMIT 1);

-- ── 2. Assign professor by subject / block (class_sections + schedules) ─────
UPDATE class_sections cs
JOIN courses c ON c.course_id = cs.course_id
SET cs.faculty_id = CASE
    WHEN cs.section_code LIKE 'BSIT%' THEN @fac_cruz
    WHEN cs.section_code LIKE 'BSCPE%' THEN @fac_mendoza
    WHEN c.course_code LIKE 'PE%' OR c.course_code LIKE 'GYM%' OR c.course_code LIKE 'SPH%' THEN @fac_reyes
    WHEN c.course_code LIKE 'SMMW%' OR c.course_code LIKE 'MATH%' OR c.course_code LIKE 'STAT%' THEN @fac_santos
    WHEN c.course_code REGEXP '^(UDS|UDB)' THEN @fac_licuanan
    WHEN c.course_code REGEXP '^(UCP|UDIT|UDEM|UDBM|UDCS|SMST|UPR)' THEN @fac_cruz
    WHEN c.course_code REGEXP '^(APC|UEDA|AET|ANS|ACW|ECE|CPE)' THEN @fac_mendoza
    WHEN c.course_code REGEXP '^(AUS|ARPH|UCED|STS|AEN|ETH|FIL|ENGL|HUM|UHC)' THEN @fac_garcia
    ELSE @fac_garcia
END,
cs.section_status = CASE
    WHEN cs.section_status IN ('SUBMITTED', 'PENDING_APPROVAL') THEN cs.section_status
    ELSE 'Open'
END;

UPDATE class_schedules sch
JOIN class_sections cs ON cs.section_id = sch.section_id
SET sch.faculty_id = cs.faculty_id
WHERE cs.faculty_id IS NOT NULL;

-- ── 3. Open grading windows (active term) ───────────────────────────────────
UPDATE grading_term_windows
SET start_date = '2026-01-01',
    end_date = '2026-12-31',
    override_status = 'FORCE_OPEN',
    updated_at = NOW()
WHERE term_id = @active_term_id;

INSERT INTO grading_term_windows (term_id, grading_period, start_date, end_date, override_status, updated_at)
SELECT @active_term_id, v.period, '2026-01-01', '2026-12-31', 'FORCE_OPEN', NOW()
FROM (
    SELECT 'PRELIM' AS period
    UNION ALL SELECT 'MIDTERM'
    UNION ALL SELECT 'FINAL'
) v
WHERE @active_term_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM grading_term_windows gtw
      WHERE gtw.term_id = @active_term_id AND gtw.grading_period = v.period
  );

INSERT INTO system_settings (setting_key, setting_value)
SELECT v.k, v.v
FROM (
    SELECT 'PRELIM_START' AS k, '2026-01-01' AS v
    UNION ALL SELECT 'PRELIM_END', '2026-12-31'
    UNION ALL SELECT 'MIDTERM_START', '2026-01-01'
    UNION ALL SELECT 'MIDTERM_END', '2026-12-31'
    UNION ALL SELECT 'FINAL_START', '2026-01-01'
    UNION ALL SELECT 'FINAL_END', '2026-12-31'
    UNION ALL SELECT 'PRELIM_OVERRIDE', 'FORCE_OPEN'
    UNION ALL SELECT 'MIDTERM_OVERRIDE', 'FORCE_OPEN'
    UNION ALL SELECT 'FINAL_OVERRIDE', 'FORCE_OPEN'
) v
ON DUPLICATE KEY UPDATE setting_value = v.v;

-- ── 4. Backfill DRAFT grade rows for all committed enlistments ──────────────
INSERT INTO grades (student_id, course_id, section_id, student_name, status, date_recorded)
SELECT
    se.student_id,
    se.course_id,
    se.section_id,
    COALESCE(
        NULLIF(TRIM(CONCAT(COALESCE(st.last_name, ''), ', ', COALESCE(st.first_name, ''))), ','),
        NULLIF(TRIM(st.real_name), ''),
        se.student_id
    ),
    'DRAFT',
    NOW()
FROM student_enlistments se
LEFT JOIN students st ON st.student_number = se.student_id
WHERE NOT EXISTS (
    SELECT 1 FROM grades g
    WHERE g.student_id = se.student_id
      AND g.section_id = se.section_id
);

-- ── 5. Verify ───────────────────────────────────────────────────────────────
SELECT 'faculty_roster' AS check_name, employee_number, CONCAT(first_name, ' ', last_name) AS name
FROM faculty
WHERE employee_number LIKE 'prof%'
ORDER BY employee_number;

SELECT 'sections_by_professor' AS check_name,
       f.employee_number,
       CONCAT(f.first_name, ' ', f.last_name) AS professor,
       COUNT(*) AS sections
FROM class_sections cs
JOIN faculty f ON f.faculty_id = cs.faculty_id
WHERE cs.term_id = @active_term_id
GROUP BY f.faculty_id, f.employee_number, f.first_name, f.last_name
ORDER BY sections DESC;

SELECT 'unscheduled_sections' AS check_name, COUNT(*) AS cnt
FROM class_sections cs
WHERE NOT EXISTS (SELECT 1 FROM class_schedules sch WHERE sch.section_id = cs.section_id);

SELECT 'enlisted_without_grades' AS check_name, COUNT(*) AS cnt
FROM student_enlistments se
WHERE NOT EXISTS (
    SELECT 1 FROM grades g WHERE g.student_id = se.student_id AND g.section_id = se.section_id
);

SELECT 'grading_windows' AS check_name, grading_period, override_status, start_date, end_date
FROM grading_term_windows
WHERE term_id = @active_term_id;

SELECT 'prof_cruz_active_classes' AS check_name, COUNT(*) AS cnt
FROM class_sections cs
JOIN faculty f ON f.faculty_id = cs.faculty_id
WHERE f.employee_number IN ('prof.cruz', 'prof')
  AND cs.term_id = @active_term_id;

SET SQL_SAFE_UPDATES = 1;
