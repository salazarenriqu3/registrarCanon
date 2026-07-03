USE eacdb;
SET SQL_SAFE_UPDATES = 0;

-- =============================================================================
-- FULL REGISTRAR FEATURE DEMO DATASET
-- Purpose:
-- - make the registrar demo repeatable after fresh bootstrap
-- - seed named students for profile, add-subject, transfer credit, shift,
--   withdrawal, scholarship, and overpayment flows
-- - add real admission-view files for Maria (2026-1001)
-- - room the specific scheduling blocks used in the live presentation
-- Safe to re-run on a disposable demo database.
-- =============================================================================

CREATE TABLE IF NOT EXISTS student_installment_plan (
    plan_id INT AUTO_INCREMENT PRIMARY KEY,
    student_number VARCHAR(100) NOT NULL,
    term_id INT NOT NULL,
    installment_number TINYINT NOT NULL,
    due_months_offset INT NOT NULL DEFAULT 1,
    installment_label VARCHAR(80) NOT NULL,
    UNIQUE KEY uk_student_term_inst (student_number, term_id, installment_number),
    KEY idx_sip_student_term (student_number, term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS student_reg_form_events (
    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_number VARCHAR(100) NOT NULL,
    archive_key VARCHAR(80) NULL,
    event_type VARCHAR(60) NOT NULL,
    purpose VARCHAR(160) NOT NULL,
    related_request_id BIGINT NULL,
    remarks VARCHAR(500) NULL,
    triggered_by VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_srfe_student (student_number, created_at),
    KEY idx_srfe_archive (archive_key, created_at),
    KEY idx_srfe_type (event_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE academic_term_policies
    ADD COLUMN IF NOT EXISTS midterm_exam_date DATE NULL;

ALTER TABLE applicants
    ADD COLUMN IF NOT EXISTS enrollment_type VARCHAR(30) NULL,
    ADD COLUMN IF NOT EXISTS qualification_expires_at DATETIME NULL;

ALTER TABLE student_ledger
    ADD COLUMN IF NOT EXISTS sl_term_year VARCHAR(30) NULL;

SET @pw_demo := '$2a$10$/l9Hb.SsSN5IBm7xyF/t4uen1KPG6uqBTxkF1hfczWNf9apIcOCKK';
SET @term_id := 1;
SET @term_code := 'SL_1120242025';
SET @bsit_curriculum_id := (
    SELECT MAX(ct.curriculum_id)
    FROM curriculum_templates ct
    JOIN programs p ON p.program_id = ct.program_id
    WHERE p.program_code = 'BSIT' AND ct.is_active = 1
);
SET @bscpe_curriculum_id := (
    SELECT MAX(ct.curriculum_id)
    FROM curriculum_templates ct
    JOIN programs p ON p.program_id = ct.program_id
    WHERE p.program_code = 'BSCPE' AND ct.is_active = 1
);

-- 0. Clean prior feature-demo state for repeatability.
DELETE FROM student_withdrawal_request_lines
WHERE request_id IN (
    SELECT request_id
    FROM student_withdrawal_requests
    WHERE student_number IN (
        '2026-1001',
        'ADDCLS-2026-001',
        'TTRNS-2026-001',
        'TSHFT-2026-001',
        'OVRPAY-2026-001',
        'SPRINT-DEMO-2026-001'
    )
);

DELETE FROM student_withdrawal_requests
WHERE student_number IN (
    '2026-1001',
    'ADDCLS-2026-001',
    'TTRNS-2026-001',
    'TSHFT-2026-001',
    'OVRPAY-2026-001',
    'SPRINT-DEMO-2026-001'
);

DELETE FROM student_installment_plan
WHERE student_number IN (
    '2026-1001',
    'ADDCLS-2026-001',
    'TTRNS-2026-001',
    'TSHFT-2026-001',
    'OVRPAY-2026-001',
    'SPRINT-DEMO-2026-001'
);

DELETE FROM student_overpay_dispositions
WHERE student_id IN (
    '2026-1001',
    'ADDCLS-2026-001',
    'TTRNS-2026-001',
    'TSHFT-2026-001',
    'OVRPAY-2026-001',
    'SPRINT-DEMO-2026-001'
);

DELETE FROM student_reg_form_events
WHERE student_number IN (
    '2026-1001',
    'ADDCLS-2026-001',
    'TTRNS-2026-001',
    'TSHFT-2026-001',
    'OVRPAY-2026-001',
    'SPRINT-DEMO-2026-001'
);

DELETE FROM student_document_events
WHERE student_number IN (
        '2026-1001',
        'ADDCLS-2026-001',
        'TTRNS-2026-001',
        'TSHFT-2026-001',
        'OVRPAY-2026-001',
        'SPRINT-DEMO-2026-001'
    )
   OR reference_number IN (
        'DEMO-SANTOS-001',
        'TTRNS-REF-001',
        'TSHFT-REF-001',
        'OVRPAY-REF-001'
    );

DELETE FROM grades
WHERE student_id IN (
    '2026-1001',
    'ADDCLS-2026-001',
    'TTRNS-2026-001',
    'TSHFT-2026-001',
    'OVRPAY-2026-001',
    'SPRINT-DEMO-2026-001'
);

DELETE FROM student_enlistments
WHERE student_id IN (
    '2026-1001',
    'ADDCLS-2026-001',
    'TTRNS-2026-001',
    'TSHFT-2026-001',
    'OVRPAY-2026-001',
    'SPRINT-DEMO-2026-001'
);

DELETE FROM student_ledger
WHERE student_id IN (
    '2026-1001',
    'ADDCLS-2026-001',
    'TTRNS-2026-001',
    'TSHFT-2026-001',
    'OVRPAY-2026-001',
    'SPRINT-DEMO-2026-001'
);

DELETE FROM student_curriculum_assignments
WHERE student_number IN (
    '2026-1001',
    'ADDCLS-2026-001',
    'TTRNS-2026-001',
    'TSHFT-2026-001',
    'OVRPAY-2026-001',
    'SPRINT-DEMO-2026-001'
);

DELETE FROM eac_application_logs
WHERE ref_no IN ('DEMO-SANTOS-001');

-- 1. Ensure the withdrawal demo stays requestable from the current date.
INSERT INTO academic_term_policies (term_id, inc_expiration_date, midterm_exam_date)
VALUES (@term_id, NULL, '2099-12-31')
ON DUPLICATE KEY UPDATE
    midterm_exam_date = VALUES(midterm_exam_date);

INSERT INTO enrollment_settings (setting_key, setting_value, description) VALUES
('drop_penalty_days_half', '7', 'Days before 50% withdrawal charge'),
('drop_penalty_days_full', '21', 'Days before 100% withdrawal charge'),
('drop_penalty_first_week_percent', '25', 'First-week withdrawal charge percent'),
('drop_penalty_half_percent', '50', 'Penalty percent between thresholds')
ON DUPLICATE KEY UPDATE
    setting_value = VALUES(setting_value),
    description = VALUES(description);

-- 2. Add deterministic room assignments for the scheduling blocks used in the demo.
INSERT INTO rooms (room_code, building_name, capacity, room_type, active_status) VALUES
('REG-A101', 'Registrar Demo Wing', 45, 'Lecture', 1),
('REG-A102', 'Registrar Demo Wing', 45, 'Lecture', 1),
('REG-A103', 'Registrar Demo Wing', 45, 'Lecture', 1),
('REG-A104', 'Registrar Demo Wing', 45, 'Lecture', 1),
('REG-A201', 'Registrar Demo Wing', 45, 'Lecture', 1),
('REG-A202', 'Registrar Demo Wing', 45, 'Lecture', 1),
('REG-A203', 'Registrar Demo Wing', 45, 'Lecture', 1),
('REG-A204', 'Registrar Demo Wing', 45, 'Lecture', 1),
('REG-LAB1', 'Registrar Demo Wing', 35, 'Computer Lab', 1),
('REG-LAB2', 'Registrar Demo Wing', 35, 'Computer Lab', 1),
('REG-LAB3', 'Registrar Demo Wing', 35, 'Computer Lab', 1),
('REG-HALL', 'Registrar Demo Wing', 80, 'Activity', 1)
ON DUPLICATE KEY UPDATE
    building_name = VALUES(building_name),
    capacity = VALUES(capacity),
    room_type = VALUES(room_type),
    active_status = VALUES(active_status);

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A101'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'AECO 11'
  AND cs.day_of_week = 1
  AND cs.start_time = '07:30:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A102'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'AUS0 11'
  AND cs.day_of_week = 1
  AND cs.start_time = '09:00:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-HALL'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'NSTP101'
  AND ((cs.day_of_week = 1 AND cs.start_time = '10:30:00')
    OR (cs.day_of_week = 3 AND cs.start_time = '14:30:00'));

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-LAB1'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'UPR1 11'
  AND cs.day_of_week = 1
  AND cs.start_time = '13:00:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A103'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'AHU1 11'
  AND cs.day_of_week = 2
  AND cs.start_time = '07:30:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-LAB2'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'CC101'
  AND cs.day_of_week = 2
  AND cs.start_time = '09:00:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'GYM-A'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'PE1 11'
  AND cs.day_of_week = 2
  AND cs.start_time = '10:30:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A104'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'GE101'
  AND ((cs.day_of_week = 2 AND cs.start_time = '13:00:00')
    OR (cs.day_of_week = 4 AND cs.start_time = '09:00:00'));

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-HALL'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'ANS1 11'
  AND cs.day_of_week = 3
  AND cs.start_time = '07:30:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-LAB3'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'CC102'
  AND cs.day_of_week = 3
  AND cs.start_time = '09:00:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'GYM-A'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'PE101'
  AND cs.day_of_week = 3
  AND cs.start_time = '10:30:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A101'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'ARPH 11'
  AND cs.day_of_week = 4
  AND cs.start_time = '07:30:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A102'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'SMMW 11'
  AND cs.day_of_week = 4
  AND cs.start_time = '10:30:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A201'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'ASS1011'
  AND cs.day_of_week = 5
  AND cs.start_time = '07:30:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A202'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'GE102'
  AND cs.day_of_week = 5
  AND cs.start_time = '09:00:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-LAB1'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSIT-1-1-A'
  AND c.course_code = 'UCO1 11'
  AND cs.day_of_week = 5
  AND cs.start_time = '10:30:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A201'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSCPE-1-1-A'
  AND c.course_code = 'AECO 11'
  AND cs.day_of_week = 1
  AND cs.start_time = '07:30:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A202'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSCPE-1-1-A'
  AND c.course_code = 'AUS0 11'
  AND cs.day_of_week = 1
  AND cs.start_time = '09:00:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-HALL'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSCPE-1-1-A'
  AND c.course_code = 'ANS1 11'
  AND ((cs.day_of_week = 1 AND cs.start_time = '17:00:00')
    OR (cs.day_of_week = 3 AND cs.start_time = '07:30:00'));

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A203'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSCPE-1-1-A'
  AND c.course_code = 'ASS1011'
  AND ((cs.day_of_week = 1 AND cs.start_time = '17:00:00')
    OR (cs.day_of_week = 5 AND cs.start_time = '07:30:00'));

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A203'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSCPE-1-1-A'
  AND c.course_code = 'AHU1 11'
  AND cs.day_of_week = 2
  AND cs.start_time = '07:30:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'GYM-A'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSCPE-1-1-A'
  AND c.course_code = 'PE1 11'
  AND cs.day_of_week = 2
  AND cs.start_time = '09:00:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-LAB2'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSCPE-1-1-A'
  AND c.course_code = 'SCH411'
  AND cs.day_of_week = 3
  AND cs.start_time = '09:00:00';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A204'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSCPE-1-1-A'
  AND c.course_code = 'SMMW 11'
  AND ((cs.day_of_week = 3 AND cs.start_time = '14:30:00')
    OR (cs.day_of_week = 4 AND cs.start_time = '09:00:00'));

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A201'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSCPE-1-1-A'
  AND c.course_code = 'ARPH 11'
  AND ((cs.day_of_week = 4 AND cs.start_time = '07:30:00')
    OR (cs.day_of_week = 5 AND cs.start_time = '16:00:00'));

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-LAB3'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'BSCPE-1-1-A'
  AND c.course_code = 'UPLD11'
  AND cs.day_of_week = 5
  AND cs.start_time = '09:00:00';

-- Irregular/open-section rows used by the add-class and transferee demos.
UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-LAB2'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'IRREG-A'
  AND c.course_code = 'CC101';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-LAB3'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'IRREG-A'
  AND c.course_code = 'CC102';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A104'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'IRREG-A'
  AND c.course_code = 'GE101';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A202'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'IRREG-A'
  AND c.course_code = 'GE102';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'GYM-A'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'IRREG-A'
  AND c.course_code IN ('PE101', 'PE1 11');

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-HALL'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'IRREG-A'
  AND c.course_code IN ('NSTP101', 'ANS1 11');

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A201'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'IRREG-A'
  AND c.course_code IN ('AECO 11', 'ARPH 11', 'AHU1 11', 'ASS1011');

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-A102'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'IRREG-A'
  AND c.course_code = 'SMMW 11';

UPDATE class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
JOIN rooms r ON r.room_code = 'REG-LAB1'
SET cs.room_id = r.room_id
WHERE s.term_id = @term_id
  AND s.section_code = 'IRREG-A'
  AND c.course_code IN ('UCO1 11', 'UPR1 11');

-- 3. Applicant bridge for Maria / 2026-1001.
INSERT INTO applicants (
    reference_number,
    applicant_status,
    application_status,
    term_year,
    first_name,
    last_name,
    middle_name,
    email,
    mobile,
    sex,
    program1,
    program2,
    academic_level,
    application_track,
    enrollment_type,
    email_verified,
    remarks,
    form138_path,
    form138_verified,
    good_moral_path,
    good_moral_verified,
    psa_birth_cert_path,
    psa_birth_cert_verified,
    id_picture_path,
    id_picture_verified,
    other_doc_path,
    other_doc_verified,
    created_at,
    updated_at
) VALUES (
    'DEMO-SANTOS-001',
    'QUALIFIED FOR ENROLLMENT',
    'QUALIFIED FOR ENROLLMENT',
    @term_code,
    'Maria',
    'Santos',
    'Reyes',
    'maria.santos@demo.eac.edu.ph',
    '09171234567',
    'Female',
    'BSIT',
    'BSCPE',
    'COLLEGE',
    'REGULAR',
    'REGULAR',
    1,
    'Canonical applicant bridge record for the Registrar feature demo dataset.',
    'DEMO-SANTOS-001-form138.svg',
    1,
    'DEMO-SANTOS-001-good-moral.svg',
    1,
    'DEMO-SANTOS-001-psa-birth-cert.svg',
    1,
    'DEMO-SANTOS-001-id-picture.svg',
    1,
    'DEMO-SANTOS-001-other-doc.svg',
    1,
    NOW(),
    NOW()
) ON DUPLICATE KEY UPDATE
    applicant_status = VALUES(applicant_status),
    application_status = VALUES(application_status),
    term_year = VALUES(term_year),
    first_name = VALUES(first_name),
    last_name = VALUES(last_name),
    middle_name = VALUES(middle_name),
    email = VALUES(email),
    mobile = VALUES(mobile),
    sex = VALUES(sex),
    program1 = VALUES(program1),
    program2 = VALUES(program2),
    academic_level = VALUES(academic_level),
    application_track = VALUES(application_track),
    enrollment_type = VALUES(enrollment_type),
    email_verified = VALUES(email_verified),
    remarks = VALUES(remarks),
    form138_path = VALUES(form138_path),
    form138_verified = VALUES(form138_verified),
    good_moral_path = VALUES(good_moral_path),
    good_moral_verified = VALUES(good_moral_verified),
    psa_birth_cert_path = VALUES(psa_birth_cert_path),
    psa_birth_cert_verified = VALUES(psa_birth_cert_verified),
    id_picture_path = VALUES(id_picture_path),
    id_picture_verified = VALUES(id_picture_verified),
    other_doc_path = VALUES(other_doc_path),
    other_doc_verified = VALUES(other_doc_verified),
    updated_at = NOW();

INSERT INTO eac_application_logs (ref_no, action, performed_by, remarks, log_timestamp) VALUES
('DEMO-SANTOS-001', 'SUBMITTED', 'anonymousUser', 'Initial application submission.', DATE_SUB(NOW(), INTERVAL 12 DAY)),
('DEMO-SANTOS-001', 'EMAIL VERIFIED', 'anonymousUser', 'Student verified their email address.', DATE_SUB(NOW(), INTERVAL 11 DAY)),
('DEMO-SANTOS-001', 'DOC VERIFIED', 'admission.encoder', 'Verified form138', DATE_SUB(NOW(), INTERVAL 10 DAY)),
('DEMO-SANTOS-001', 'DOC VERIFIED', 'admission.encoder', 'Verified goodMoral', DATE_SUB(NOW(), INTERVAL 10 DAY)),
('DEMO-SANTOS-001', 'DOC VERIFIED', 'admission.encoder', 'Verified psaBirthCert', DATE_SUB(NOW(), INTERVAL 9 DAY)),
('DEMO-SANTOS-001', 'DOC VERIFIED', 'admission.encoder', 'Verified idPicture', DATE_SUB(NOW(), INTERVAL 9 DAY)),
('DEMO-SANTOS-001', 'QUALIFIED FOR ENROLLMENT', 'admission.admin', 'Applicant completed the admission gate and is ready for registrar-side profile viewing.', DATE_SUB(NOW(), INTERVAL 8 DAY));

-- 4. Student/user records.
INSERT INTO sys_users (
    username, password, real_name, first_name, last_name, middle_name,
    role, program_code, year_level, semester, term_year, reference_number,
    student_type, enrollment_status_type, scholarship_type, admission_status,
    is_active, status, scholarship_amount, scholarship_approved
) VALUES
('2026-1001', @pw_demo, 'Maria Reyes Santos', 'Maria', 'Santos', 'Reyes',
 'Student', 'BSIT', 1, 1, @term_code, 'DEMO-SANTOS-001',
 'Regular', 'REGULAR', 'NONE', 'ENROLLED', 1, 'ACTIVE', 0.00, 0),
('ADDCLS-2026-001', @pw_demo, 'Noel Addclass Demo', 'Noel', 'Addclass', 'Demo',
 'Student', 'BSIT', 1, 1, @term_code, NULL,
 'Regular', 'IRREGULAR', 'NONE', 'ENROLLED', 1, 'ACTIVE', 0.00, 0),
('TTRNS-2026-001', @pw_demo, 'Tara Transfer Demo', 'Tara', 'Transfer', 'Demo',
 'Student', 'BSIT', 2, 1, @term_code, 'TTRNS-REF-001',
 'Transferee', 'IRREGULAR', 'NONE', 'ENROLLED', 1, 'ACTIVE', 0.00, 0),
('TSHFT-2026-001', @pw_demo, 'Shane Shift Demo', 'Shane', 'Shift', 'Demo',
 'Student', 'BSCPE', 1, 1, @term_code, 'TSHFT-REF-001',
 'Regular', 'REGULAR', 'NONE', 'ENROLLED', 1, 'ACTIVE', 0.00, 0),
('OVRPAY-2026-001', @pw_demo, 'Olive Overpay Demo', 'Olive', 'Overpay', 'Demo',
 'Student', 'BSIT', 1, 1, @term_code, 'OVRPAY-REF-001',
 'Regular', 'REGULAR', 'NONE', 'ENROLLED', 1, 'ACTIVE', 0.00, 0),
('SPRINT-DEMO-2026-001', @pw_demo, 'Sprint Demo Student', 'Sprint', 'Demo', 'Student',
 'Student', 'BSIT', 1, 1, @term_code, NULL,
 'Regular', 'REGULAR', 'NONE', 'ENROLLED', 1, 'ACTIVE', 0.00, 0)
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
    reference_number = VALUES(reference_number),
    student_type = VALUES(student_type),
    enrollment_status_type = VALUES(enrollment_status_type),
    scholarship_type = VALUES(scholarship_type),
    admission_status = VALUES(admission_status),
    is_active = VALUES(is_active),
    status = VALUES(status),
    scholarship_amount = VALUES(scholarship_amount),
    scholarship_approved = VALUES(scholarship_approved);

INSERT INTO students (
    student_number, user_id, reference_number,
    first_name, last_name, middle_name, real_name,
    email, mobile, program_code, year_level, semester, term_year,
    student_type, enrollment_status_type, admission_status, status, is_active,
    enrollment_blocked, role
) VALUES
('2026-1001', (SELECT user_id FROM sys_users WHERE username = '2026-1001'), 'DEMO-SANTOS-001',
 'Maria', 'Santos', 'Reyes', 'Maria Reyes Santos',
 'maria.santos@demo.eac.edu.ph', '09171234567', 'BSIT', 1, 1, @term_code,
 'Regular', 'REGULAR', 'ENROLLED', 'ACTIVE', 1, 0, 'STUDENT'),
('ADDCLS-2026-001', (SELECT user_id FROM sys_users WHERE username = 'ADDCLS-2026-001'), NULL,
 'Noel', 'Addclass', 'Demo', 'Noel Addclass Demo',
 'noel.addclass@demo.eac.edu.ph', '09170001001', 'BSIT', 1, 1, @term_code,
 'Regular', 'IRREGULAR', 'ENROLLED', 'ACTIVE', 1, 0, 'STUDENT'),
('TTRNS-2026-001', (SELECT user_id FROM sys_users WHERE username = 'TTRNS-2026-001'), 'TTRNS-REF-001',
 'Tara', 'Transfer', 'Demo', 'Tara Transfer Demo',
 'tara.transfer@demo.eac.edu.ph', '09170001002', 'BSIT', 2, 1, @term_code,
 'Transferee', 'IRREGULAR', 'ENROLLED', 'ACTIVE', 1, 0, 'STUDENT'),
('TSHFT-2026-001', (SELECT user_id FROM sys_users WHERE username = 'TSHFT-2026-001'), 'TSHFT-REF-001',
 'Shane', 'Shift', 'Demo', 'Shane Shift Demo',
 'shane.shift@demo.eac.edu.ph', '09170001003', 'BSCPE', 1, 1, @term_code,
 'Regular', 'REGULAR', 'ENROLLED', 'ACTIVE', 1, 0, 'STUDENT'),
('OVRPAY-2026-001', (SELECT user_id FROM sys_users WHERE username = 'OVRPAY-2026-001'), 'OVRPAY-REF-001',
 'Olive', 'Overpay', 'Demo', 'Olive Overpay Demo',
 'olive.overpay@demo.eac.edu.ph', '09170001004', 'BSIT', 1, 1, @term_code,
 'Regular', 'REGULAR', 'ENROLLED', 'ACTIVE', 1, 0, 'STUDENT'),
('SPRINT-DEMO-2026-001', (SELECT user_id FROM sys_users WHERE username = 'SPRINT-DEMO-2026-001'), NULL,
 'Sprint', 'Demo', 'Student', 'Sprint Demo Student',
 'sprint.withdraw@demo.eac.edu.ph', '09170001005', 'BSIT', 1, 1, @term_code,
 'Regular', 'REGULAR', 'ENROLLED', 'ACTIVE', 1, 0, 'STUDENT')
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    reference_number = VALUES(reference_number),
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

-- 5. Curriculum assignments.
INSERT INTO student_curriculum_assignments
    (student_number, curriculum_id, program_code, assignment_type, reason, is_current)
VALUES
('2026-1001', @bsit_curriculum_id, 'BSIT', 'ADMISSION', 'Baseline registrar demo student linked to admission.', 1),
('ADDCLS-2026-001', @bsit_curriculum_id, 'BSIT', 'REGISTRAR_PROFILE', 'Subject-add demo student.', 1),
('TTRNS-2026-001', @bsit_curriculum_id, 'BSIT', 'TRANSFEREE', 'Transferee demo student for TOR crediting.', 1),
('TSHFT-2026-001', @bscpe_curriculum_id, 'BSCPE', 'DEFAULT', 'Pre-shift demo student.', 1),
('OVRPAY-2026-001', @bsit_curriculum_id, 'BSIT', 'DEFAULT', 'Overpayment demo student.', 1),
('SPRINT-DEMO-2026-001', @bsit_curriculum_id, 'BSIT', 'REGISTRAR_PROFILE', 'Withdrawal demo student.', 1);

-- 6. Current enlisted loads.
INSERT INTO student_enlistments
    (student_id, course_id, section_id, enlistment_status, enlisted_date)
VALUES
('2026-1001', 101, 4950, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 12 DAY)),
('2026-1001', 102, 4951, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 12 DAY)),
('2026-1001', 103, 4952, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 11 DAY)),
('2026-1001', 105, 4954, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 11 DAY)),
('2026-1001', 214, 4964, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 10 DAY)),
('2026-1001', 1236, 4970, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 10 DAY)),

('ADDCLS-2026-001', 101, 4950, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 7 DAY)),
('ADDCLS-2026-001', 102, 4951, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 7 DAY)),
('ADDCLS-2026-001', 214, 4964, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 6 DAY)),
('ADDCLS-2026-001', 1236, 4970, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 6 DAY)),

('OVRPAY-2026-001', 101, 4950, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 6 DAY)),
('OVRPAY-2026-001', 214, 4964, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 6 DAY)),

('SPRINT-DEMO-2026-001', 101, 4950, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 3 DAY)),
('SPRINT-DEMO-2026-001', 102, 4951, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 10 DAY)),
('SPRINT-DEMO-2026-001', 103, 4952, 'COMMITTED', DATE_SUB(NOW(), INTERVAL 22 DAY));

-- 7. Academic history / grade rows for COG, TOR, transfer, and shift demos.
INSERT INTO grades
    (student_id, course_id, section_id, final_grade, semestral_grade, remarks, student_name,
     curriculum_year, grade, status, date_recorded, registrar_final_grade, registrar_final_remarks, grade_lock_status)
VALUES
('2026-1001', 205, NULL, 1.75, 1.75, 'PASSED', 'Maria Reyes Santos', 1, 1.75, 'APPROVED',
 DATE_SUB(NOW(), INTERVAL 200 DAY), 1.75, 'PASSED', 'FINALIZED'),
('2026-1001', 212, NULL, 1.50, 1.50, 'PASSED', 'Maria Reyes Santos', 1, 1.50, 'APPROVED',
 DATE_SUB(NOW(), INTERVAL 190 DAY), 1.50, 'PASSED', 'FINALIZED'),

('TTRNS-2026-001', 101, NULL, 1.75, 1.75, 'PASSED', 'Tara Transfer Demo', 1, 1.75, 'APPROVED',
 DATE_SUB(NOW(), INTERVAL 300 DAY), 1.75, 'PASSED', 'FINALIZED'),
('TTRNS-2026-001', 103, NULL, 2.00, 2.00, 'PASSED', 'Tara Transfer Demo', 1, 2.00, 'APPROVED',
 DATE_SUB(NOW(), INTERVAL 295 DAY), 2.00, 'PASSED', 'FINALIZED'),

('TSHFT-2026-001', 205, NULL, 1.75, 1.75, 'PASSED', 'Shane Shift Demo', 1, 1.75, 'APPROVED',
 DATE_SUB(NOW(), INTERVAL 220 DAY), 1.75, 'PASSED', 'FINALIZED'),
('TSHFT-2026-001', 214, NULL, 1.25, 1.25, 'PASSED', 'Shane Shift Demo', 1, 1.25, 'APPROVED',
 DATE_SUB(NOW(), INTERVAL 215 DAY), 1.25, 'PASSED', 'FINALIZED');

-- 7b. Grade-governance demo rows for registrar approvals and reporting.
INSERT INTO grade_change_requests
    (grade_id, student_name, course_code, faculty_name, request_type, requested_grade, reason, status, request_date)
VALUES
(
    (SELECT id FROM grades WHERE student_id = '2026-1001' AND course_id = 205 ORDER BY id DESC LIMIT 1),
    'Maria Reyes Santos',
    'CC101',
    'Prof. Cruz',
    'FINAL_GRADE_CORRECTION',
    '1.50',
    'Demo pending correction request for registrar approvals.',
    'PENDING',
    DATE_SUB(NOW(), INTERVAL 2 DAY)
);

INSERT INTO grade_record_events
    (grade_id, student_id, student_name, course_id, course_code, term_label, action_type, lifecycle_status,
     actor, actor_role, reason, component_before, component_after, official_grade_before, official_grade_after,
     official_remarks_before, official_remarks_after, grade_lock_status_before, grade_lock_status_after, created_at)
VALUES
(
    (SELECT id FROM grades WHERE student_id = '2026-1001' AND course_id = 205 ORDER BY id DESC LIMIT 1),
    '2026-1001',
    'Maria Reyes Santos',
    205,
    'CC101',
    'Historical / No section',
    'GRADE_CLASS_POSTED',
    'FINALIZED',
    'registrar.main',
    'Registrar',
    'Baseline seeded official posting event.',
    'P:95.00 / M:94.00 / F:96.00',
    'P:95.00 / M:94.00 / F:96.00',
    1.75,
    1.75,
    'PASSED',
    'PASSED',
    'FINALIZED',
    'FINALIZED',
    DATE_SUB(NOW(), INTERVAL 180 DAY)
),
(
    (SELECT id FROM grades WHERE student_id = '2026-1001' AND course_id = 205 ORDER BY id DESC LIMIT 1),
    '2026-1001',
    'Maria Reyes Santos',
    205,
    'CC101',
    'Historical / No section',
    'GRADE_CHANGE_REQUESTED',
    'FINALIZED',
    'prof.cruz',
    'Faculty',
    'Demo pending correction request for registrar approvals.',
    'P:95.00 / M:94.00 / F:96.00',
    'P:95.00 / M:94.00 / F:96.00',
    1.75,
    1.75,
    'PASSED',
    'PASSED',
    'FINALIZED',
    'FINALIZED',
    DATE_SUB(NOW(), INTERVAL 2 DAY)
);

-- 8. Finance/ledger context.
INSERT INTO student_ledger
    (student_id, transaction_type, description, debit, credit, sl_term_year)
VALUES
('2026-1001', 'TUITION_ASSESSMENT', 'Baseline tuition assessment', 18000.00, 0.00, @term_code),
('2026-1001', 'MISC_ASSESSMENT', 'Baseline miscellaneous assessment', 4500.00, 0.00, @term_code),
('2026-1001', 'OTHER_ASSESSMENT', 'Baseline other fees', 1000.00, 0.00, @term_code),
('2026-1001', 'PAYMENT', 'Admission and initial term payment', 0.00, 12000.00, @term_code),

('ADDCLS-2026-001', 'TUITION_ASSESSMENT', 'Initial tuition assessment', 12000.00, 0.00, @term_code),
('ADDCLS-2026-001', 'MISC_ASSESSMENT', 'Initial miscellaneous assessment', 3000.00, 0.00, @term_code),
('ADDCLS-2026-001', 'PAYMENT', 'Initial payment', 0.00, 7000.00, @term_code),

('OVRPAY-2026-001', 'TUITION_ASSESSMENT', 'Overpayment demo tuition', 5000.00, 0.00, @term_code),
('OVRPAY-2026-001', 'PAYMENT', 'Cashier overpayment demo', 0.00, 6500.00, @term_code),

('SPRINT-DEMO-2026-001', 'TUITION_ASSESSMENT', 'Withdrawal demo tuition', 9000.00, 0.00, @term_code),
('SPRINT-DEMO-2026-001', 'MISC_ASSESSMENT', 'Withdrawal demo miscellaneous', 2000.00, 0.00, @term_code),
('SPRINT-DEMO-2026-001', 'PAYMENT', 'Withdrawal demo initial payment', 0.00, 6000.00, @term_code);

-- 9. Registration-form history baseline.
INSERT INTO student_reg_form_events
    (student_number, event_type, purpose, related_request_id, remarks, triggered_by, created_at)
VALUES
('2026-1001', 'CURRICULUM_ASSIGNED', 'Current curriculum assigned', NULL,
 'Assigned BSIT current curriculum for registrar demo baseline.', 'admin', DATE_SUB(NOW(), INTERVAL 9 DAY)),
('2026-1001', 'ENROLLMENT_ACTIVATED', 'First subject added and student activated', NULL,
 'Baseline student activated for registrar demo.', 'admin', DATE_SUB(NOW(), INTERVAL 8 DAY)),
('2026-1001', 'BLOCK_ENROLL_COMPLETED', 'Registrar block enrollment completed', NULL,
 'Baseline active-term load was staged for registration-form and print checks.', 'admin', DATE_SUB(NOW(), INTERVAL 8 DAY)),
('ADDCLS-2026-001', 'ENROLLMENT_ACTIVATED', 'First subject added and student activated', NULL,
 'Subject-add demo student activated with partial irregular load.', 'admin', DATE_SUB(NOW(), INTERVAL 5 DAY)),
('TTRNS-2026-001', 'CURRICULUM_ASSIGNED', 'Current curriculum assigned', NULL,
 'Transferee demo student aligned to BSIT curriculum for deficiency and TOR checks.', 'admin', DATE_SUB(NOW(), INTERVAL 4 DAY)),
('TSHFT-2026-001', 'CURRICULUM_ASSIGNED', 'Current curriculum assigned', NULL,
 'Pre-shift demo student aligned to BSCPE curriculum.', 'admin', DATE_SUB(NOW(), INTERVAL 4 DAY)),
('OVRPAY-2026-001', 'ENROLLMENT_ACTIVATED', 'First subject added and student activated', NULL,
 'Overpayment demo student activated for finance disposition checks.', 'admin', DATE_SUB(NOW(), INTERVAL 4 DAY));

SELECT 'feature_demo_seed_ready' AS status,
       @bsit_curriculum_id AS bsit_curriculum_id,
       @bscpe_curriculum_id AS bscpe_curriculum_id;

SET SQL_SAFE_UPDATES = 1;
