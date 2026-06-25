-- Demo grading: assign a curated active-term faculty mix and keep windows open.
-- Safe to re-run on the demo database. This clears only the demo faculty mix for
-- the active term, then restores a bounded set of grading-ready assignments.
USE eacdb;

SET @term_id = (SELECT term_id FROM academic_terms WHERE is_active = 1 LIMIT 1);

UPDATE class_schedules sch
JOIN class_sections cs ON cs.section_id = sch.section_id
JOIN faculty f ON f.faculty_id = sch.faculty_id
SET sch.faculty_id = NULL
WHERE cs.term_id = @term_id
  AND f.employee_number IN ('prof.cruz', 'prof.mendoza', 'prof.garcia', 'prof.santos');

UPDATE class_sections cs
JOIN faculty f ON f.faculty_id = cs.faculty_id
SET cs.faculty_id = NULL
WHERE cs.term_id = @term_id
  AND f.employee_number IN ('prof.cruz', 'prof.mendoza', 'prof.garcia', 'prof.santos');

DROP TEMPORARY TABLE IF EXISTS demo_faculty_map;
CREATE TEMPORARY TABLE demo_faculty_map (
    section_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci NOT NULL,
    course_code VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci NOT NULL,
    faculty_emp VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci NOT NULL
);

INSERT INTO demo_faculty_map (section_code, course_code, faculty_emp) VALUES
    ('BSIT-1-1-A',  'CC101',   'prof.cruz'),
    ('BSIT-1-1-A',  'PE101',   'prof.cruz'),
    ('BSIT-1-1-A',  'UCO1 11', 'prof.cruz'),
    ('BSIT-1-1-A',  'UPR1 11', 'prof.cruz'),
    ('BSCPE-1-1-A', 'AUS0 11', 'prof.cruz'),
    ('BSCPE-1-1-A', 'AHU1 11', 'prof.cruz'),
    ('BSIT-1-1-A',  'CC102',   'prof.mendoza'),
    ('BSIT-1-1-A',  'NSTP101', 'prof.mendoza'),
    ('BSIT-1-1-A',  'ARPH 11', 'prof.mendoza'),
    ('BSCPE-1-1-A', 'ASS1011', 'prof.mendoza'),
    ('BSCPE-1-1-A', 'AECO 11', 'prof.mendoza'),
    ('BSIT-1-1-A',  'GE101',   'prof.garcia'),
    ('BSIT-1-1-A',  'ANS1 11', 'prof.garcia'),
    ('BSCPE-1-1-A', 'UPLD11',  'prof.garcia'),
    ('BSCPE-1-1-A', 'PE1 11',  'prof.garcia'),
    ('BSIT-1-1-A',  'GE102',   'prof.santos'),
    ('BSIT-1-1-A',  'AHU1 11', 'prof.santos'),
    ('BSIT-1-1-A',  'PE1 11',  'prof.santos'),
    ('BSIT-1-1-A',  'SMMW 11', 'prof.santos'),
    ('BSCPE-1-1-A', 'SCH411',  'prof.santos');

UPDATE class_sections cs
JOIN courses c ON c.course_id = cs.course_id
JOIN demo_faculty_map map
  ON map.section_code COLLATE utf8mb4_uca1400_ai_ci = cs.section_code COLLATE utf8mb4_uca1400_ai_ci
 AND map.course_code COLLATE utf8mb4_uca1400_ai_ci = c.course_code COLLATE utf8mb4_uca1400_ai_ci
JOIN faculty f
  ON f.employee_number COLLATE utf8mb4_uca1400_ai_ci = map.faculty_emp COLLATE utf8mb4_uca1400_ai_ci
SET cs.faculty_id = f.faculty_id
WHERE cs.term_id = @term_id;

UPDATE class_schedules sch
JOIN class_sections cs ON cs.section_id = sch.section_id
JOIN courses c ON c.course_id = cs.course_id
JOIN demo_faculty_map map
  ON map.section_code COLLATE utf8mb4_uca1400_ai_ci = cs.section_code COLLATE utf8mb4_uca1400_ai_ci
 AND map.course_code COLLATE utf8mb4_uca1400_ai_ci = c.course_code COLLATE utf8mb4_uca1400_ai_ci
SET sch.faculty_id = cs.faculty_id
WHERE cs.term_id = @term_id;

UPDATE grading_term_windows
SET start_date = '2026-01-01', end_date = '2026-12-31', override_status = 'FORCE_OPEN', updated_at = NOW()
WHERE term_id = @term_id;

SELECT 'DEMO FACULTY MIX' AS check_name, f.employee_number, COUNT(*) AS section_count
FROM class_sections cs
JOIN faculty f ON f.faculty_id = cs.faculty_id
WHERE cs.term_id = @term_id
  AND f.employee_number IN ('prof.cruz', 'prof.mendoza', 'prof.garcia', 'prof.santos')
GROUP BY f.employee_number
ORDER BY f.employee_number;

SELECT 'PROF CRUZ SECTIONS' AS check_name, COUNT(*) AS section_count
FROM class_sections cs
JOIN faculty f ON f.faculty_id = cs.faculty_id
WHERE cs.term_id = @term_id AND f.employee_number = 'prof.cruz';
