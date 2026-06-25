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

DELETE FROM student_ledger
WHERE student_id = 'SPRINT-DEMO-2026-001'
  AND (
      transaction_type = 'DROP_PENALTY'
      OR (transaction_type = 'REFUND' AND description LIKE 'Withdrawn Subject:%')
      OR transaction_type IN (
          'TUITION_ASSESSMENT', 'MISC_ASSESSMENT', 'OTHER_ASSESSMENT', 'RLE_ASSESSMENT'
      )
  );

DELETE FROM student_document_events
WHERE student_number = 'SPRINT-DEMO-2026-001'
  AND document_type = 'WITHDRAWAL';

DELETE FROM student_reg_form_events
WHERE student_number = 'SPRINT-DEMO-2026-001'
  AND event_type LIKE 'WITHDRAWAL%';

SELECT student_number, real_name, status, is_active
FROM students
WHERE student_number = 'SPRINT-DEMO-2026-001';

SELECT COUNT(*) AS remaining_withdrawal_headers
FROM student_withdrawal_requests
WHERE student_number = 'SPRINT-DEMO-2026-001';

SELECT COUNT(*) AS remaining_enlistments
FROM student_enlistments
WHERE student_id = 'SPRINT-DEMO-2026-001';
