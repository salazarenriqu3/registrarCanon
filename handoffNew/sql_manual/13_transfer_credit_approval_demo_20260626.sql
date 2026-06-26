-- Transfer credit approval workflow demo helper
-- Date: 2026-06-26
-- Scope: Registrar only

USE eacdb;

-- 1. Inspect existing requests for one demo student
SELECT request_id, student_number, course_code, numeric_grade, source_school,
       status, requested_by, requested_at, approved_by, approved_at,
       rejected_by, rejected_at, rejection_reason
FROM transfer_credit_requests
WHERE student_number = 'SCH-UAT-LOWUNITS'
ORDER BY request_id DESC;

-- 2. Insert one disposable pending request for browser/demo verification
INSERT INTO transfer_credit_requests
    (student_number, curriculum_id, course_id, course_code, numeric_grade, source_school, note, status, requested_by)
SELECT
    'SCH-UAT-LOWUNITS',
    114,
    c.course_id,
    c.course_code,
    1.75,
    'Prior College',
    'Disposable demo request',
    'PENDING',
    'sql_demo'
FROM courses c
WHERE c.course_code = 'ARPH 11'
LIMIT 1;

-- 3. View newest request
SELECT request_id, student_number, course_code, status, requested_by, requested_at
FROM transfer_credit_requests
WHERE student_number = 'SCH-UAT-LOWUNITS'
ORDER BY request_id DESC
LIMIT 1;

-- 4. Cleanup disposable SQL-seeded requests if needed
-- DELETE FROM transfer_credit_requests
-- WHERE student_number = 'SCH-UAT-LOWUNITS'
--   AND requested_by = 'sql_demo';

-- 5. Optional trail cleanup for the same disposable request source
-- DELETE FROM student_reg_form_events
-- WHERE student_number = 'SCH-UAT-LOWUNITS'
--   AND triggered_by = 'sql_demo';
--
-- DELETE FROM student_document_events
-- WHERE student_number = 'SCH-UAT-LOWUNITS'
--   AND actor = 'sql_demo'
--   AND source_table = 'transfer_credit_requests';
