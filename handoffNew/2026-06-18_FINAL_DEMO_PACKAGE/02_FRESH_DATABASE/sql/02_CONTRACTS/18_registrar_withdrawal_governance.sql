-- Registrar-only withdrawal governance (2026-06-22)
-- Supports one-class and full-student withdrawal with per-class audit lines.

CREATE TABLE IF NOT EXISTS withdrawal_reasons (
    reason_code VARCHAR(40) PRIMARY KEY,
    reason_label VARCHAR(160) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 100
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO withdrawal_reasons (reason_code, reason_label, sort_order) VALUES
('ACADEMIC_LOAD', 'Academic load adjustment', 10),
('SCHEDULE_CONFLICT', 'Schedule conflict', 20),
('MEDICAL', 'Medical / health reason', 30),
('FINANCIAL', 'Financial reason', 40),
('SHIFTING', 'Shifting', 45),
('TRANSFER', 'Transfer / change of school', 50),
('OTHER', 'Other reason', 100)
ON DUPLICATE KEY UPDATE reason_label = VALUES(reason_label), sort_order = VALUES(sort_order);

CREATE TABLE IF NOT EXISTS student_withdrawal_requests (
    request_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_number VARCHAR(100) NOT NULL,
    archive_key VARCHAR(80) NULL,
    section_id INT NOT NULL,
    course_id INT NOT NULL,
    term_id INT NULL,
    reason_code VARCHAR(40) NOT NULL,
    remarks VARCHAR(500) NULL,
    requested_on DATE NULL,
    enlisted_at TIMESTAMP NULL,
    days_enrolled_at_request INT NULL,
    timing_bucket VARCHAR(40) NULL,
    charge_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    estimated_charge DECIMAL(12,2) NOT NULL DEFAULT 0,
    deadline_blocked TINYINT(1) NOT NULL DEFAULT 0,
    policy_note VARCHAR(255) NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING_REGISTRAR',
    requested_by VARCHAR(100) NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dean_approved_by VARCHAR(100) NULL,
    dean_approved_at TIMESTAMP NULL,
    registrar_approved_by VARCHAR(100) NULL,
    registrar_approved_at TIMESTAMP NULL,
    rejected_by VARCHAR(100) NULL,
    rejected_at TIMESTAMP NULL,
    rejection_reason VARCHAR(500) NULL,
    completed_at TIMESTAMP NULL,
    withdrawal_scope VARCHAR(30) NOT NULL DEFAULT 'SINGLE_SUBJECT',
    subject_count INT NOT NULL DEFAULT 1,
    approval_source VARCHAR(40) NULL,
    CONSTRAINT fk_swr_reason FOREIGN KEY (reason_code) REFERENCES withdrawal_reasons(reason_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE student_withdrawal_requests
    ADD COLUMN IF NOT EXISTS withdrawal_scope VARCHAR(30) NOT NULL DEFAULT 'SINGLE_SUBJECT',
    ADD COLUMN IF NOT EXISTS subject_count INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS approval_source VARCHAR(40) NULL,
    ADD COLUMN IF NOT EXISTS archive_key VARCHAR(80) NULL;

CREATE TABLE IF NOT EXISTS student_withdrawal_request_lines (
    line_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    student_number VARCHAR(100) NOT NULL,
    archive_key VARCHAR(80) NULL,
    section_id INT NOT NULL,
    course_id INT NOT NULL,
    requested_on DATE NULL,
    enlisted_at TIMESTAMP NULL,
    days_enrolled_at_request INT NULL,
    timing_bucket VARCHAR(40) NULL,
    charge_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    estimated_charge DECIMAL(12,2) NOT NULL DEFAULT 0,
    policy_note VARCHAR(255) NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING_REGISTRAR',
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_swrl_request FOREIGN KEY (request_id) REFERENCES student_withdrawal_requests(request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

UPDATE student_withdrawal_requests
SET status = 'PENDING_REGISTRAR',
    approval_source = COALESCE(approval_source, 'REGISTRAR_WORKFLOW')
WHERE request_id >= 0 AND status = 'PENDING_DEAN';

UPDATE student_withdrawal_request_lines
SET status = 'PENDING_REGISTRAR'
WHERE line_id >= 0 AND status = 'PENDING_DEAN';

ALTER TABLE student_withdrawal_requests
    MODIFY COLUMN status VARCHAR(40) NOT NULL DEFAULT 'PENDING_REGISTRAR';

ALTER TABLE student_withdrawal_request_lines
    MODIFY COLUMN status VARCHAR(40) NOT NULL DEFAULT 'PENDING_REGISTRAR';

ALTER TABLE student_withdrawal_request_lines
    ADD COLUMN IF NOT EXISTS archive_key VARCHAR(80) NULL;

CREATE TABLE IF NOT EXISTS student_identity_archive (
    archive_key VARCHAR(80) NOT NULL PRIMARY KEY,
    archived_student_number VARCHAR(100) NOT NULL,
    reference_number VARCHAR(100) NULL,
    first_name VARCHAR(100) NULL,
    middle_name VARCHAR(100) NULL,
    last_name VARCHAR(100) NULL,
    real_name VARCHAR(200) NULL,
    email VARCHAR(150) NULL,
    mobile VARCHAR(50) NULL,
    program_code VARCHAR(100) NULL,
    year_level INT NULL,
    semester INT NULL,
    term_year VARCHAR(50) NULL,
    student_type VARCHAR(50) NULL,
    admission_status VARCHAR(50) NULL,
    status VARCHAR(50) NULL,
    archive_status VARCHAR(50) NOT NULL DEFAULT 'WITHDRAWN_RECORD',
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_by VARCHAR(100) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE student_identity_archive
    ADD COLUMN IF NOT EXISTS reference_number VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS middle_name VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS last_name VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS real_name VARCHAR(200) NULL,
    ADD COLUMN IF NOT EXISTS email VARCHAR(150) NULL,
    ADD COLUMN IF NOT EXISTS mobile VARCHAR(50) NULL,
    ADD COLUMN IF NOT EXISTS program_code VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS year_level INT NULL,
    ADD COLUMN IF NOT EXISTS semester INT NULL,
    ADD COLUMN IF NOT EXISTS term_year VARCHAR(50) NULL,
    ADD COLUMN IF NOT EXISTS student_type VARCHAR(50) NULL,
    ADD COLUMN IF NOT EXISTS admission_status VARCHAR(50) NULL,
    ADD COLUMN IF NOT EXISTS status VARCHAR(50) NULL,
    ADD COLUMN IF NOT EXISTS archive_status VARCHAR(50) NOT NULL DEFAULT 'WITHDRAWN_RECORD',
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS archived_by VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS student_number_release_registry (
    released_student_number VARCHAR(100) NOT NULL PRIMARY KEY,
    archive_key VARCHAR(80) NOT NULL,
    release_status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    released_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_by VARCHAR(100) NULL,
    release_note VARCHAR(500) NULL,
    reissued_reference_number VARCHAR(100) NULL,
    reissued_student_number VARCHAR(100) NULL,
    reissued_at TIMESTAMP NULL,
    reissued_by VARCHAR(100) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE student_number_release_registry
    ADD COLUMN IF NOT EXISTS archive_key VARCHAR(80) NOT NULL,
    ADD COLUMN IF NOT EXISTS release_status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN IF NOT EXISTS released_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS released_by VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS release_note VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS reissued_reference_number VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS reissued_student_number VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS reissued_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS reissued_by VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_swr_status ON student_withdrawal_requests (status);
CREATE INDEX IF NOT EXISTS idx_swr_student ON student_withdrawal_requests (student_number);
CREATE INDEX IF NOT EXISTS idx_swr_archive ON student_withdrawal_requests (archive_key);
CREATE INDEX IF NOT EXISTS idx_swr_section ON student_withdrawal_requests (student_number, section_id, status);
CREATE INDEX IF NOT EXISTS idx_sia_student_number ON student_identity_archive (archived_student_number);
CREATE INDEX IF NOT EXISTS idx_sia_reference ON student_identity_archive (reference_number);
CREATE INDEX IF NOT EXISTS idx_sia_real_name ON student_identity_archive (real_name);
CREATE INDEX IF NOT EXISTS idx_snrr_archive ON student_number_release_registry (archive_key);
CREATE INDEX IF NOT EXISTS idx_snrr_status_released ON student_number_release_registry (release_status, released_at);

SELECT 'REGISTRAR_WITHDRAWAL_GOVERNANCE' AS contract_name, 'OK' AS result, NOW() AS applied_at;
