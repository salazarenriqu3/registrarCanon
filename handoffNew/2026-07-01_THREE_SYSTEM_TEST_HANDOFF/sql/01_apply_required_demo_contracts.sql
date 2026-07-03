USE eacdb;

-- Admission / Enrollment pre-registration subject lines must be orderable.
ALTER TABLE applicant_pre_reg_subject_lines
    ADD COLUMN IF NOT EXISTS sort_order INT NULL;

UPDATE applicant_pre_reg_subject_lines
SET sort_order = COALESCE(sort_order, line_order, 0)
WHERE sort_order IS NULL;

CREATE INDEX IF NOT EXISTS idx_aprsl_snapshot_sort
    ON applicant_pre_reg_subject_lines (snapshot_id, sort_order);

-- Registrar LEC/LAB component metadata.
ALTER TABLE courses
    MODIFY COLUMN course_code VARCHAR(40) NOT NULL;

ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS component_type VARCHAR(10) NOT NULL DEFAULT 'SINGLE',
    ADD COLUMN IF NOT EXISTS course_family_code VARCHAR(40) NULL,
    ADD COLUMN IF NOT EXISTS parent_course_id INT NULL;

UPDATE courses
SET component_type = CASE
    WHEN COALESCE(lec_units, 0) > 0 AND COALESCE(lab_units, 0) = 0 THEN 'LEC'
    WHEN COALESCE(lab_units, 0) > 0 AND COALESCE(lec_units, 0) = 0 THEN 'LAB'
    ELSE COALESCE(NULLIF(component_type, ''), 'SINGLE')
END;

UPDATE courses
SET course_family_code = TRIM(
    REPLACE(REPLACE(REPLACE(REPLACE(course_code, '-LEC', ''), '-LAB', ''), ' LEC', ''), ' LAB', '')
)
WHERE course_family_code IS NULL OR course_family_code = '';

CREATE INDEX IF NOT EXISTS idx_courses_family ON courses (course_family_code);
CREATE INDEX IF NOT EXISTS idx_courses_component ON courses (component_type);

-- Registrar governance tables expected by current withdrawal/document/grade flows.
CREATE TABLE IF NOT EXISTS student_number_release_registry (
    release_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    released_student_number VARCHAR(100) NOT NULL,
    archive_key VARCHAR(120) NULL,
    release_status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    released_by VARCHAR(100) NULL,
    release_note VARCHAR(255) NULL,
    released_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reissued_to_reference VARCHAR(100) NULL,
    reissued_at TIMESTAMP NULL,
    UNIQUE KEY uq_snrr_number (released_student_number),
    KEY idx_snrr_status (release_status)
);

CREATE TABLE IF NOT EXISTS grade_record_events (
    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    grade_id BIGINT NOT NULL,
    request_id BIGINT NULL,
    student_id VARCHAR(100) NULL,
    student_name VARCHAR(100) NULL,
    course_id INT NULL,
    course_code VARCHAR(40) NULL,
    section_id INT NULL,
    section_code VARCHAR(50) NULL,
    term_id INT NULL,
    term_label VARCHAR(40) NULL,
    action_type VARCHAR(60) NOT NULL,
    lifecycle_status VARCHAR(30) NOT NULL,
    actor VARCHAR(100) NULL,
    actor_role VARCHAR(50) NULL,
    reason VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_gre_student_created (student_id, created_at),
    KEY idx_gre_term_action (term_id, action_type, created_at)
);

SELECT 'APPLIED_REQUIRED_DEMO_CONTRACTS' AS status;
