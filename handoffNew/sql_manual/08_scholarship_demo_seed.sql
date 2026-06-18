USE eacdb;

-- Scholarship demo seed.
--
-- Purpose:
-- - Creates two demo students with official grade rows for the active term.
-- - The eligible student has 27 completed units and grades within policy.
-- - The ineligible student has only 24 completed units.
-- - This lets Registrar demo Scholarship Eligibility without building or running
--   the full faculty grading/finalization workflow.

SET @term_id := (
    SELECT term_id
    FROM academic_terms
    WHERE is_active = 1 OR UPPER(COALESCE(status, '')) = 'ACTIVE'
    ORDER BY term_id DESC
    LIMIT 1
);

SET @term_id := COALESCE(@term_id, (SELECT term_id FROM academic_terms ORDER BY term_id DESC LIMIT 1));

INSERT IGNORE INTO system_settings (setting_key, setting_value)
VALUES ('SCHOLARSHIP_MIN_COMPLETED_UNITS', '27');

INSERT INTO system_settings (setting_key, setting_value)
VALUES ('SCHOLARSHIP_MIN_COMPLETED_UNITS', '27')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO departments (department_code, department_name)
SELECT 'SCHUAT', 'Scholarship Demo Department'
WHERE NOT EXISTS (
    SELECT 1 FROM departments WHERE department_code = 'SCHUAT'
);

SET @department_id := (
    SELECT department_id
    FROM departments
    WHERE department_code = 'SCHUAT'
    ORDER BY department_id
    LIMIT 1
);

INSERT IGNORE INTO sys_users (username, password, real_name, role, is_active, program_code, year_level, semester, admission_status)
VALUES
('SCH-UAT-ELIGIBLE', NULL, 'Sofia Scholar', 'Student', 1, 'BSIT', 2, 1, 'ENROLLED'),
('SCH-UAT-LOWUNITS', NULL, 'Liam Low Units', 'Student', 1, 'BSIT', 2, 1, 'ENROLLED');

INSERT IGNORE INTO students (
    student_number, user_id, first_name, last_name, real_name, program_code,
    year_level, semester, term_year, student_type, enrollment_status_type, admission_status,
    scholarship_approved, scholarship_type, discount_percentage
)
SELECT
    u.username, u.user_id,
    CASE WHEN u.username = 'SCH-UAT-ELIGIBLE' THEN 'Sofia' ELSE 'Liam' END,
    CASE WHEN u.username = 'SCH-UAT-ELIGIBLE' THEN 'Scholar' ELSE 'Low Units' END,
    u.real_name, 'BSIT',
    2, 1, '2025-2026_1st', 'Regular', 'ENROLLED', 'ENROLLED',
    0, 'NONE', 0
FROM sys_users u
WHERE u.username IN ('SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS')
ON DUPLICATE KEY UPDATE
    real_name = VALUES(real_name),
    program_code = VALUES(program_code),
    year_level = VALUES(year_level),
    semester = VALUES(semester),
    admission_status = VALUES(admission_status);

INSERT IGNORE INTO courses (course_code, course_title, credit_units, department_id, active_status, onlist)
VALUES
('SCH101', 'Scholarship Demo Course 1', 3, @department_id, 1, 1),
('SCH102', 'Scholarship Demo Course 2', 3, @department_id, 1, 1),
('SCH103', 'Scholarship Demo Course 3', 3, @department_id, 1, 1),
('SCH104', 'Scholarship Demo Course 4', 3, @department_id, 1, 1),
('SCH105', 'Scholarship Demo Course 5', 3, @department_id, 1, 1),
('SCH106', 'Scholarship Demo Course 6', 3, @department_id, 1, 1),
('SCH107', 'Scholarship Demo Course 7', 3, @department_id, 1, 1),
('SCH108', 'Scholarship Demo Course 8', 3, @department_id, 1, 1),
('SCH109', 'Scholarship Demo Course 9', 3, @department_id, 1, 1);

INSERT INTO class_sections (course_id, term_id, section_code, max_capacity, section_status, semester_number)
SELECT c.course_id, @term_id, CONCAT('SCH-DEMO-', c.course_code), 40, 'Closed', 1
FROM courses c
WHERE c.course_code IN ('SCH101','SCH102','SCH103','SCH104','SCH105','SCH106','SCH107','SCH108','SCH109')
AND NOT EXISTS (
    SELECT 1
    FROM class_sections cs
    WHERE cs.course_id = c.course_id
      AND cs.term_id = @term_id
      AND cs.section_code = CONCAT('SCH-DEMO-', c.course_code)
);

INSERT INTO grades (
    student_id, student_name, section_id, course_id,
    prelim, midterm, final_grade, semestral_grade,
    registrar_final_grade, registrar_final_remarks, status, remarks
)
SELECT
    'SCH-UAT-ELIGIBLE', 'Sofia Scholar', cs.section_id, c.course_id,
    1.50, 1.50, 1.50, 1.50,
    1.50, 'PASSED', 'APPROVED', 'PASSED'
FROM courses c
JOIN class_sections cs ON cs.course_id = c.course_id
WHERE c.course_code IN ('SCH101','SCH102','SCH103','SCH104','SCH105','SCH106','SCH107','SCH108','SCH109')
  AND cs.term_id = @term_id
  AND cs.section_code = CONCAT('SCH-DEMO-', c.course_code)
  AND NOT EXISTS (
      SELECT 1
      FROM grades g
      WHERE g.student_id = 'SCH-UAT-ELIGIBLE'
        AND g.section_id = cs.section_id
  );

INSERT INTO grades (
    student_id, student_name, section_id, course_id,
    prelim, midterm, final_grade, semestral_grade,
    registrar_final_grade, registrar_final_remarks, status, remarks
)
SELECT
    'SCH-UAT-LOWUNITS', 'Liam Low Units', cs.section_id, c.course_id,
    1.50, 1.50, 1.50, 1.50,
    1.50, 'PASSED', 'APPROVED', 'PASSED'
FROM courses c
JOIN class_sections cs ON cs.course_id = c.course_id
WHERE c.course_code IN ('SCH101','SCH102','SCH103','SCH104','SCH105','SCH106','SCH107','SCH108')
  AND cs.term_id = @term_id
  AND cs.section_code = CONCAT('SCH-DEMO-', c.course_code)
  AND NOT EXISTS (
      SELECT 1
      FROM grades g
      WHERE g.student_id = 'SCH-UAT-LOWUNITS'
        AND g.section_id = cs.section_id
  );

SELECT
    'Scholarship demo seed complete' AS result,
    @term_id AS term_id,
    'SCH-UAT-ELIGIBLE should be eligible with 27 units' AS eligible_case,
    'SCH-UAT-LOWUNITS should be blocked by completed units' AS blocked_case;
