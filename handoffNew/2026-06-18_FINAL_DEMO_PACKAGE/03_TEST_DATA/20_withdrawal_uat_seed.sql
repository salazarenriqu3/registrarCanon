USE eacdb;

-- Focused registrar-only withdrawal UAT seed
-- Canonical target student: SPRINT-DEMO-2026-001
-- Active term: 1120242025 (term_id = 1)

INSERT INTO enrollment_settings (setting_key, setting_value, description) VALUES
('drop_penalty_days_half', '7', 'Days before 50% withdrawal charge'),
('drop_penalty_days_full', '21', 'Days before 100% withdrawal charge'),
('drop_penalty_first_week_percent', '25', 'First-week withdrawal charge percent'),
('drop_penalty_half_percent', '50', 'Penalty percent between thresholds')
ON DUPLICATE KEY UPDATE
    setting_value = VALUES(setting_value),
    description = VALUES(description);

UPDATE students
SET
    term_year = 'SL_1120242025',
    semester = 1,
    year_level = 1,
    admission_status = 'ENROLLED',
    enrollment_status_type = 'REGULAR',
    status = 'ACTIVE',
    is_active = 1,
    enrollment_blocked = 0
WHERE student_number = 'SPRINT-DEMO-2026-001';

UPDATE sys_users
SET
    term_year = 'SL_1120242025',
    semester = 1,
    year_level = 1,
    admission_status = 'ENROLLED',
    status = 'ACTIVE',
    is_active = 1,
    enrollment_blocked = 0
WHERE username = 'SPRINT-DEMO-2026-001';

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

DELETE FROM student_withdrawal_request_lines
WHERE request_id IN (
    SELECT request_id
    FROM student_withdrawal_requests
    WHERE student_number = 'SPRINT-DEMO-2026-001'
);

DELETE FROM student_withdrawal_requests
WHERE student_number = 'SPRINT-DEMO-2026-001';

-- Existing live sections:
-- 1001 = CC101-A, first-week case: 25% charge / 75% refund
-- 1002 = CC102-A, second-third-week case: 50% charge / 50% refund
-- 1003 = GE101-A, past-three-weeks case: 100% charge / 0% refund
INSERT INTO student_enlistments
    (student_id, course_id, section_id, enlistment_status, enlisted_date)
VALUES
    ('SPRINT-DEMO-2026-001', 101, 1001, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 3 DAY)),
    ('SPRINT-DEMO-2026-001', 102, 1002, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 10 DAY)),
    ('SPRINT-DEMO-2026-001', 103, 1003, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 22 DAY));

SELECT student_number, real_name, program_code, year_level, term_year, admission_status, status, is_active
FROM students
WHERE student_number = 'SPRINT-DEMO-2026-001';

SELECT se.enlistment_id, se.student_id, se.course_id, c.course_code, se.section_id, cs.section_code,
       se.enlistment_status, se.enlisted_date,
       DATEDIFF(CURDATE(), DATE(se.enlisted_date)) AS demo_days_enrolled,
       cs.term_id
FROM student_enlistments se
JOIN courses c ON c.course_id = se.course_id
JOIN class_sections cs ON cs.section_id = se.section_id
WHERE se.student_id = 'SPRINT-DEMO-2026-001'
ORDER BY se.enlistment_id;
