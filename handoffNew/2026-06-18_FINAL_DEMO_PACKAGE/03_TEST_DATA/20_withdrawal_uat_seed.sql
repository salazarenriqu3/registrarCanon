USE eacdb;

-- Focused registrar-only withdrawal UAT seed
-- Canonical target student: SPRINT-DEMO-2026-001
-- Active term: 1120242025 (term_id = 1)

SET @pw_demo := '$2a$10$/l9Hb.SsSN5IBm7xyF/t4uen1KPG6uqBTxkF1hfczWNf9apIcOCKK';
SET @term_code := 'SL_1120242025';
SET @bsit_curriculum_id := (
    SELECT MAX(ct.curriculum_id)
    FROM curriculum_templates ct
    JOIN programs p ON p.program_id = ct.program_id
    WHERE p.program_code = 'BSIT' AND ct.is_active = 1
);

INSERT INTO enrollment_settings (setting_key, setting_value, description) VALUES
('drop_penalty_days_half', '7', 'Days before 50% withdrawal charge'),
('drop_penalty_days_full', '21', 'Days before 100% withdrawal charge'),
('drop_penalty_first_week_percent', '25', 'First-week withdrawal charge percent'),
('drop_penalty_half_percent', '50', 'Penalty percent between thresholds')
ON DUPLICATE KEY UPDATE
    setting_value = VALUES(setting_value),
    description = VALUES(description);

INSERT INTO academic_term_policies (term_id, inc_expiration_date, midterm_exam_date)
VALUES (1, NULL, '2099-12-31')
ON DUPLICATE KEY UPDATE
    midterm_exam_date = VALUES(midterm_exam_date);

INSERT INTO sys_users (
    username, password, real_name, first_name, last_name, middle_name,
    role, program_code, year_level, semester, term_year,
    student_type, enrollment_status_type, scholarship_type, admission_status,
    is_active, status, scholarship_amount, scholarship_approved, enrollment_blocked
) VALUES (
    'SPRINT-DEMO-2026-001', @pw_demo, 'Sprint Demo Student', 'Sprint', 'Demo', 'Student',
    'Student', 'BSIT', 1, 1, @term_code,
    'Regular', 'REGULAR', 'NONE', 'ENROLLED',
    1, 'ACTIVE', 0.00, 0, 0
)
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    real_name = VALUES(real_name),
    first_name = VALUES(first_name),
    last_name = VALUES(last_name),
    middle_name = VALUES(middle_name),
    role = VALUES(role),
    program_code = VALUES(program_code),
    year_level = VALUES(year_level),
    semester = VALUES(semester),
    term_year = VALUES(term_year),
    student_type = VALUES(student_type),
    enrollment_status_type = VALUES(enrollment_status_type),
    scholarship_type = VALUES(scholarship_type),
    admission_status = VALUES(admission_status),
    is_active = VALUES(is_active),
    status = VALUES(status),
    scholarship_amount = VALUES(scholarship_amount),
    scholarship_approved = VALUES(scholarship_approved),
    enrollment_blocked = VALUES(enrollment_blocked);

INSERT INTO students (
    student_number, user_id, first_name, last_name, middle_name, real_name,
    email, mobile, program_code, year_level, semester, term_year,
    student_type, enrollment_status_type, admission_status, status, is_active,
    enrollment_blocked, role
) VALUES (
    'SPRINT-DEMO-2026-001',
    (SELECT user_id FROM sys_users WHERE username = 'SPRINT-DEMO-2026-001'),
    'Sprint', 'Demo', 'Student', 'Sprint Demo Student',
    'sprint.withdraw@demo.eac.edu.ph', '09170001005', 'BSIT', 1, 1, @term_code,
    'Regular', 'REGULAR', 'ENROLLED', 'ACTIVE', 1, 0, 'STUDENT'
)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    first_name = VALUES(first_name),
    last_name = VALUES(last_name),
    middle_name = VALUES(middle_name),
    real_name = VALUES(real_name),
    email = VALUES(email),
    mobile = VALUES(mobile),
    program_code = VALUES(program_code),
    year_level = VALUES(year_level),
    semester = VALUES(semester),
    term_year = VALUES(term_year),
    student_type = VALUES(student_type),
    enrollment_status_type = VALUES(enrollment_status_type),
    admission_status = VALUES(admission_status),
    status = VALUES(status),
    is_active = VALUES(is_active),
    enrollment_blocked = VALUES(enrollment_blocked),
    role = VALUES(role);

UPDATE students
SET
    term_year = @term_code,
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
    term_year = @term_code,
    semester = 1,
    year_level = 1,
    admission_status = 'ENROLLED',
    status = 'ACTIVE',
    is_active = 1,
    enrollment_blocked = 0
WHERE username = 'SPRINT-DEMO-2026-001';

DELETE FROM student_curriculum_assignments
WHERE student_number = 'SPRINT-DEMO-2026-001';

INSERT INTO student_curriculum_assignments
    (student_number, curriculum_id, program_code, assignment_type, reason, is_current)
VALUES
    ('SPRINT-DEMO-2026-001', @bsit_curriculum_id, 'BSIT', 'REGISTRAR_PROFILE',
     'Focused withdrawal UAT baseline.', 1);

DELETE FROM student_enlistments
WHERE student_id = 'SPRINT-DEMO-2026-001';

DELETE FROM student_ledger
WHERE student_id = 'SPRINT-DEMO-2026-001'
  AND (
      transaction_type = 'DROP_PENALTY'
      OR transaction_type = 'PAYMENT'
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

INSERT INTO student_ledger
    (student_id, transaction_type, description, debit, credit, sl_term_year)
VALUES
    ('SPRINT-DEMO-2026-001', 'TUITION_ASSESSMENT', 'Withdrawal demo tuition', 9000.00, 0.00, @term_code),
    ('SPRINT-DEMO-2026-001', 'MISC_ASSESSMENT', 'Withdrawal demo miscellaneous', 2000.00, 0.00, @term_code),
    ('SPRINT-DEMO-2026-001', 'PAYMENT', 'Withdrawal demo initial payment', 0.00, 6000.00, @term_code);

-- Existing active-term sections:
-- 4950 = CC101, first-week case: 25% charge / 75% refund
-- 4951 = CC102, second-third-week case: 50% charge / 50% refund
-- 4952 = GE101, past-three-weeks case: 100% charge / 0% refund
INSERT INTO student_enlistments
    (student_id, course_id, section_id, enlistment_status, enlisted_date)
VALUES
    ('SPRINT-DEMO-2026-001', 101, 4950, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 3 DAY)),
    ('SPRINT-DEMO-2026-001', 102, 4951, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 10 DAY)),
    ('SPRINT-DEMO-2026-001', 103, 4952, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 22 DAY));

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
