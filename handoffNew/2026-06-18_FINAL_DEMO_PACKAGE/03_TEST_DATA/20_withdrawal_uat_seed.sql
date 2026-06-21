USE eacdb;

-- Focused registrar-only withdrawal UAT seed
-- Canonical target student: SPRINT-DEMO-2026-001
-- Active term: 1120242025 (term_id = 1)

UPDATE students
SET
    term_year = '1120242025',
    semester = 1,
    year_level = 1,
    admission_status = 'ENROLLED',
    enrollment_status_type = 'REGULAR',
    status = 'ACTIVE',
    is_active = 1
WHERE student_number = 'SPRINT-DEMO-2026-001';

DELETE FROM student_enlistments
WHERE student_id = 'SPRINT-DEMO-2026-001';

DELETE FROM student_withdrawal_request_lines
WHERE request_id IN (
    SELECT request_id
    FROM student_withdrawal_requests
    WHERE student_number = 'SPRINT-DEMO-2026-001'
);

DELETE FROM student_withdrawal_requests
WHERE student_number = 'SPRINT-DEMO-2026-001';

-- Existing live sections:
-- 1001 = CC101-A
-- 1002 = CC102-A
-- 1003 = GE101-A
INSERT INTO student_enlistments
    (student_id, course_id, section_id, enlistment_status, enlisted_date)
VALUES
    ('SPRINT-DEMO-2026-001', 101, 1001, 'COMMITTED', NOW()),
    ('SPRINT-DEMO-2026-001', 102, 1002, 'COMMITTED', NOW()),
    ('SPRINT-DEMO-2026-001', 103, 1003, 'COMMITTED', NOW());

SELECT student_number, real_name, program_code, year_level, term_year, admission_status, status, is_active
FROM students
WHERE student_number = 'SPRINT-DEMO-2026-001';

SELECT se.enlistment_id, se.student_id, se.course_id, c.course_code, se.section_id, cs.section_code,
       se.enlistment_status, cs.term_id
FROM student_enlistments se
JOIN courses c ON c.course_id = se.course_id
JOIN class_sections cs ON cs.section_id = se.section_id
WHERE se.student_id = 'SPRINT-DEMO-2026-001'
ORDER BY se.enlistment_id;
