USE eacdb;

-- Focused registrar-only withdrawal UAT cleanup
-- Leaves the base student row intact for future reseeding.

DELETE FROM student_withdrawal_request_lines
WHERE request_id IN (
    SELECT request_id
    FROM student_withdrawal_requests
    WHERE student_number = 'SPRINT-DEMO-2026-001'
);

DELETE FROM student_withdrawal_requests
WHERE student_number = 'SPRINT-DEMO-2026-001';

DELETE FROM student_enlistments
WHERE student_id = 'SPRINT-DEMO-2026-001';

SELECT student_number, real_name, status, is_active
FROM students
WHERE student_number = 'SPRINT-DEMO-2026-001';

SELECT COUNT(*) AS remaining_withdrawal_headers
FROM student_withdrawal_requests
WHERE student_number = 'SPRINT-DEMO-2026-001';

SELECT COUNT(*) AS remaining_enlistments
FROM student_enlistments
WHERE student_id = 'SPRINT-DEMO-2026-001';
