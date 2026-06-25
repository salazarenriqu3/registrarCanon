-- =============================================================================
-- eacdb_cross_system_schema.sql
-- Unified extensions for Admission + Registrar + Enrollment3 on shared eacdb.
--
-- Fresh install: merged into db/fix (run db/fix only — no other patches).
-- Legacy DB: run this file once after an older db/fix (idempotent).
--
-- Replaces:
--   registrar_enrollment_sync_patch.sql (was never shipped — now this file)
--   hotfix_complete_schema_patch.sql (students + VARCHAR migration — in db/fix)
--   05_admission_eacdb_align.sql (admission tables + programs.level)
--   enrollment3/src/main/resources/sql/enrollment_settings_schema.sql
-- =============================================================================
USE eacdb;
SET NAMES utf8mb4;
SET SQL_SAFE_UPDATES = 0;

-- ---------------------------------------------------------------------------
-- 1. sys_users / payments / courses guards (idempotent on partial runs)
-- ---------------------------------------------------------------------------
ALTER TABLE sys_users
    ADD COLUMN IF NOT EXISTS scholarship_amount   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS scholarship_approved TINYINT(1)   NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS section_group        VARCHAR(10)  DEFAULT NULL;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS bank_name    VARCHAR(100) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS check_number VARCHAR(60)  DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS check_date   DATETIME     DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS cashier_name VARCHAR(100) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS date_created DATETIME     DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS onlist    TINYINT(1) GENERATED ALWAYS AS (active_status) STORED,
    ADD COLUMN IF NOT EXISTS lec_units INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS lab_units INT NOT NULL DEFAULT 0;

ALTER TABLE class_schedules
    ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'OPEN';

-- ---------------------------------------------------------------------------
-- 2. Canonical students profile (Enrollment3 + Registrar)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS students (
    student_number          VARCHAR(100) NOT NULL PRIMARY KEY,
    user_id                 INT          DEFAULT NULL,
    reference_number        VARCHAR(100) DEFAULT NULL,
    first_name              VARCHAR(100) DEFAULT NULL,
    last_name               VARCHAR(100) DEFAULT NULL,
    middle_name             VARCHAR(100) DEFAULT NULL,
    real_name               VARCHAR(200) DEFAULT NULL,
    email                   VARCHAR(100) DEFAULT NULL,
    mobile                  VARCHAR(50)  DEFAULT NULL,
    street                  VARCHAR(255) DEFAULT NULL,
    city                    VARCHAR(100) DEFAULT NULL,
    province                VARCHAR(100) DEFAULT NULL,
    program_code            VARCHAR(100) DEFAULT NULL,
    year_level              INT          NOT NULL DEFAULT 1,
    semester                INT          NOT NULL DEFAULT 1,
    term_year               VARCHAR(50)  DEFAULT NULL,
    student_type            VARCHAR(50)  DEFAULT NULL,
    enrollment_status_type  VARCHAR(50)  DEFAULT NULL,
    admission_status        VARCHAR(50)  DEFAULT NULL,
    scholarship_type        VARCHAR(50)  DEFAULT NULL,
    scholarship_approved    TINYINT(1)   NOT NULL DEFAULT 0,
    scholarship_amount      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    discount_percentage     DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    section_group           VARCHAR(10)  DEFAULT NULL,
    status                  VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    is_active               TINYINT(1)   NOT NULL DEFAULT 1,
    enrollment_blocked      TINYINT(1)   NOT NULL DEFAULT 0,
    enrollment_start_time   DATETIME     DEFAULT NULL,
    last_school             VARCHAR(255) DEFAULT NULL,
    course_taken            VARCHAR(255) DEFAULT NULL,
    form138_path            VARCHAR(255) DEFAULT NULL,
    good_moral_path         VARCHAR(255) DEFAULT NULL,
    psa_birth_cert_path     VARCHAR(255) DEFAULT NULL,
    id_picture_path         VARCHAR(255) DEFAULT NULL,
    password                VARCHAR(255) DEFAULT NULL,
    role                    VARCHAR(30)  NOT NULL DEFAULT 'STUDENT'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scholarship_types (
    type_id                         INT AUTO_INCREMENT PRIMARY KEY,
    classification                  VARCHAR(50) NOT NULL UNIQUE,
    display_name                    VARCHAR(100) DEFAULT NULL,
    discount_mode                   VARCHAR(20)  NOT NULL DEFAULT 'PERCENT',
    default_discount_percentage     DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    default_scholarship_amount      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    is_internal                     TINYINT(1) DEFAULT 0,
    requires_id                     TINYINT(1) DEFAULT 1,
    is_active                       TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE scholarship_types
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(100) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS discount_mode VARCHAR(20) NOT NULL DEFAULT 'PERCENT',
    ADD COLUMN IF NOT EXISTS default_discount_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS default_scholarship_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS is_internal TINYINT(1) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS requires_id TINYINT(1) DEFAULT 1,
    ADD COLUMN IF NOT EXISTS is_active TINYINT(1) NOT NULL DEFAULT 1;

INSERT INTO scholarship_types
    (classification, display_name, discount_mode, default_discount_percentage, default_scholarship_amount, is_internal, requires_id, is_active)
VALUES
    ('ACADEMIC', 'Academic Scholarship', 'FULL', 100.00, 0.00, 1, 1, 1),
    ('BARANGAY', 'Barangay Scholarship', 'PERCENT', 50.00, 0.00, 0, 1, 1),
    ('LGU', 'LGU Scholarship', 'PERCENT', 50.00, 0.00, 0, 1, 1),
    ('ATHLETE', 'Athlete Scholarship', 'FULL', 100.00, 0.00, 1, 1, 1),
    ('EMPLOYEE_DEPENDENT', 'Employee Dependent', 'PERCENT', 50.00, 0.00, 0, 1, 1),
    ('OTHER', 'Other / Miscellaneous', 'FLAT', 0.00, 0.00, 0, 1, 1)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    discount_mode = VALUES(discount_mode),
    default_discount_percentage = VALUES(default_discount_percentage),
    default_scholarship_amount = VALUES(default_scholarship_amount),
    is_internal = VALUES(is_internal),
    requires_id = VALUES(requires_id),
    is_active = VALUES(is_active);

-- ---------------------------------------------------------------------------
-- 3. Grades — registrar period columns on canonical grades table
-- ---------------------------------------------------------------------------
ALTER TABLE grades
    ADD COLUMN IF NOT EXISTS prelim          DECIMAL(5,2) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS midterm         DECIMAL(5,2) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS final_grade     DECIMAL(5,2) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS semestral_grade DECIMAL(5,2) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS remarks         VARCHAR(30)  DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS previous_grade  VARCHAR(20)  DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS section_id      INT          DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS student_name    VARCHAR(100) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS curriculum_year INT          DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS registrar_final_grade   DECIMAL(5,2) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS registrar_final_remarks VARCHAR(30) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS grade_lock_status       VARCHAR(30) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS grade_lock_reason       VARCHAR(80) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS registrar_finalized_at  TIMESTAMP NULL;

-- ---------------------------------------------------------------------------
-- 4. programs.level (Admission portal tabs)
-- ---------------------------------------------------------------------------
SET @has_prog_level := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'programs' AND COLUMN_NAME = 'level'
);
SET @sql_prog_level := IF(@has_prog_level = 0,
    'ALTER TABLE programs ADD COLUMN level VARCHAR(32) NULL', 'SELECT 1');
PREPARE stmt_pl FROM @sql_prog_level; EXECUTE stmt_pl; DEALLOCATE PREPARE stmt_pl;

UPDATE programs SET level = 'COLLEGE'
WHERE level IS NULL OR TRIM(level) = ''
   OR UPPER(TRIM(level)) IN ('COLLEGE', 'REGULAR');

-- ---------------------------------------------------------------------------
-- 4b. applicants � align with Admission Student.java (add + widen TEXT)
-- Canonical: db/applicants_align_student_entity.sql
-- ---------------------------------------------------------------------------
-- =====================================================================
-- Admission: align `applicants` with JPA Student / Applicant
--
-- Canonical copy for CAPSS deployments: registrar/db/05_admission_eacdb_align.sql
-- (section 3). When changing Student.java column set, update BOTH files together.
-- =====================================================================
-- Run on the SAME database Spring uses (e.g. `eacdb`), after picking it:
--
--     mysql -h HOST -u USER -pPASSWORD eacdb < 03_applicants_align_student_entity.sql
--
-- Uses DATABASE() — you MUST select the DB (e.g. `mysql … eacdb < this_file` or `USE eacdb;` first).
-- Safe to re-run:
--   1) Adds columns that exist on Student.java but often missing from legacy dumps.
--   2) For columns mapped as @Column(columnDefinition = "TEXT"), widens existing
--      VARCHAR / CHAR columns to TEXT (matches Student.java column definitions).
--
-- DOES NOT shorten `reference_number` to VARCHAR(32). If Hibernate complains after this,
-- investigate duplicates / length manually before ALTER-ing.
--
-- Drops `middle_namena` if present (wrong name; duplicates `middle_name_na` semantics and blocked INSERT).
-- =====================================================================

DROP PROCEDURE IF EXISTS admission_add_applicants_column_if_missing;
DROP PROCEDURE IF EXISTS admission_widen_applicants_text_column_if_needed;

DELIMITER $$

CREATE PROCEDURE admission_add_applicants_column_if_missing(
    IN p_col VARCHAR(64),
    IN p_ddl VARCHAR(512)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'applicants'
          AND COLUMN_NAME = p_col
    ) THEN
        SET @ddl_stmt = CONCAT('ALTER TABLE `applicants` ADD COLUMN `', p_col, '` ', p_ddl);
        PREPARE stmt_adm_add FROM @ddl_stmt;
        EXECUTE stmt_adm_add;
        DEALLOCATE PREPARE stmt_adm_add;
    END IF;
END$$

-- Widen VARCHAR/CHAR where entity expects unrestricted text (fewer validate mismatches).
CREATE PROCEDURE admission_widen_applicants_text_column_if_needed(IN p_col VARCHAR(64))
BEGIN
    DECLARE v_type VARCHAR(64);
    SELECT DATA_TYPE INTO v_type
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'applicants'
      AND COLUMN_NAME = p_col;
    IF v_type IS NOT NULL AND v_type IN ('varchar', 'char') THEN
        SET @ddl_wide = CONCAT(
            'ALTER TABLE `applicants` MODIFY COLUMN `', p_col,
            '` TEXT NULL');
        PREPARE stmt_wide FROM @ddl_wide;
        EXECUTE stmt_wide;
        DEALLOCATE PREPARE stmt_wide;
    END IF;
END$$

DELIMITER ;

-- --- Columns added by Admission flows (often absent on older dumps) -------------
CALL admission_add_applicants_column_if_missing('application_track', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('degree_certificate_program', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('enrollment_term', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('school_year_text', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('admission_classification', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('lrn', 'TEXT NULL');

CALL admission_add_applicants_column_if_missing('visa_status', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('period_of_authorized_stay', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('passport_no', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('passport_issue_date', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('passport_expiry_date', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('acr_no', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('acr_issue_date', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('acr_expiry_date', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('crt_no', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('crt_issue_date', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('crt_expiry_date', 'TEXT NULL');

CALL admission_add_applicants_column_if_missing('father_company_address', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('mother_company_address', 'TEXT NULL');

CALL admission_add_applicants_column_if_missing('guardian_age', 'INT NULL');
CALL admission_add_applicants_column_if_missing('guardian_occupation', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('guardian_home_address', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('guardian_landline', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('guardian_mobile', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('guardian_email', 'TEXT NULL');

CALL admission_add_applicants_column_if_missing('undergrad_school_address', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('grad_program_school', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('grad_program_address', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('grad_program_year', 'TEXT NULL');

CALL admission_add_applicants_column_if_missing('privacy_consent_accepted', 'TINYINT(1) NOT NULL DEFAULT 0');
CALL admission_add_applicants_column_if_missing('privacy_consent_by_guardian', 'TINYINT(1) NOT NULL DEFAULT 0');
CALL admission_add_applicants_column_if_missing('declaration_learner_name', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('declaration_accomplished_date', 'TEXT NULL');

CALL admission_add_applicants_column_if_missing('foreign_city_address', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_city_tel', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_provincial_address', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_provincial_tel', 'TEXT NULL');

CALL admission_add_applicants_column_if_missing('foreign_home_house_name', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_home_street', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_home_province', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_home_city', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_home_country', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_home_zip', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_home_tel', 'TEXT NULL');

CALL admission_add_applicants_column_if_missing('foreign_current_house_name', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_current_street', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_current_province', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_current_city', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_current_country', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_current_zip', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_current_tel', 'TEXT NULL');

CALL admission_add_applicants_column_if_missing('foreign_emergency_house_name', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_emergency_street', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_emergency_province', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_emergency_city', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_emergency_country', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_emergency_zip', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_emergency_tel', 'TEXT NULL');

CALL admission_add_applicants_column_if_missing('father_age', 'INT NULL');
CALL admission_add_applicants_column_if_missing('mother_age', 'INT NULL');
CALL admission_add_applicants_column_if_missing('father_landline', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('father_email', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('father_office_tel', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('father_highest_education', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('father_schools_attended', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('mother_landline', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('mother_email', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('mother_office_tel', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('mother_highest_education', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('mother_schools_attended', 'TEXT NULL');

CALL admission_add_applicants_column_if_missing('foreign_siblings_json', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_references_json', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_edu_elementary', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_edu_high_school', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_edu_college', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_edu_post_graduate', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_edu_vocational', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('foreign_certification_accepted', 'TINYINT(1) NOT NULL DEFAULT 0');

CALL admission_add_applicants_column_if_missing('email_verification_token', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('interview_mode', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('interview_venue', 'TEXT NULL');
CALL admission_add_applicants_column_if_missing('reopen_reminder_sent_at', 'DATETIME NULL');

-- Match Student.java STRING fields annotated with TEXT (camelCase → snake_case).
CALL admission_widen_applicants_text_column_if_needed('academic_level');
CALL admission_widen_applicants_text_column_if_needed('admission_classification');
CALL admission_widen_applicants_text_column_if_needed('applicant_status');
CALL admission_widen_applicants_text_column_if_needed('application_track');
CALL admission_widen_applicants_text_column_if_needed('course_taken');
CALL admission_widen_applicants_text_column_if_needed('citizenship');
CALL admission_widen_applicants_text_column_if_needed('civil_status');
CALL admission_widen_applicants_text_column_if_needed('city');
CALL admission_widen_applicants_text_column_if_needed('crt_expiry_date');
CALL admission_widen_applicants_text_column_if_needed('crt_issue_date');
CALL admission_widen_applicants_text_column_if_needed('crt_no');
CALL admission_widen_applicants_text_column_if_needed('declaration_accomplished_date');
CALL admission_widen_applicants_text_column_if_needed('declaration_learner_name');
CALL admission_widen_applicants_text_column_if_needed('degree_certificate_program');
CALL admission_widen_applicants_text_column_if_needed('dob');
CALL admission_widen_applicants_text_column_if_needed('email');
CALL admission_widen_applicants_text_column_if_needed('email_verification_token');
CALL admission_widen_applicants_text_column_if_needed('elementary_address');
CALL admission_widen_applicants_text_column_if_needed('elementary_school');
CALL admission_widen_applicants_text_column_if_needed('elementary_year');
CALL admission_widen_applicants_text_column_if_needed('emergency_contact_mobile');
CALL admission_widen_applicants_text_column_if_needed('emergency_contact_name');
CALL admission_widen_applicants_text_column_if_needed('emergency_contact_relationship');
CALL admission_widen_applicants_text_column_if_needed('enrollment_term');
CALL admission_widen_applicants_text_column_if_needed('extension');
CALL admission_widen_applicants_text_column_if_needed('father_address');
CALL admission_widen_applicants_text_column_if_needed('father_company_address');
CALL admission_widen_applicants_text_column_if_needed('father_contact');
CALL admission_widen_applicants_text_column_if_needed('father_email');
CALL admission_widen_applicants_text_column_if_needed('father_highest_education');
CALL admission_widen_applicants_text_column_if_needed('father_landline');
CALL admission_widen_applicants_text_column_if_needed('father_name');
CALL admission_widen_applicants_text_column_if_needed('father_occupation');
CALL admission_widen_applicants_text_column_if_needed('father_office_tel');
CALL admission_widen_applicants_text_column_if_needed('father_schools_attended');
CALL admission_widen_applicants_text_column_if_needed('first_name');
CALL admission_widen_applicants_text_column_if_needed('foreign_city_address');
CALL admission_widen_applicants_text_column_if_needed('foreign_city_tel');
CALL admission_widen_applicants_text_column_if_needed('foreign_current_city');
CALL admission_widen_applicants_text_column_if_needed('foreign_current_country');
CALL admission_widen_applicants_text_column_if_needed('foreign_current_house_name');
CALL admission_widen_applicants_text_column_if_needed('foreign_current_province');
CALL admission_widen_applicants_text_column_if_needed('foreign_current_street');
CALL admission_widen_applicants_text_column_if_needed('foreign_current_tel');
CALL admission_widen_applicants_text_column_if_needed('foreign_current_zip');
CALL admission_widen_applicants_text_column_if_needed('foreign_edu_college');
CALL admission_widen_applicants_text_column_if_needed('foreign_edu_elementary');
CALL admission_widen_applicants_text_column_if_needed('foreign_edu_high_school');
CALL admission_widen_applicants_text_column_if_needed('foreign_edu_post_graduate');
CALL admission_widen_applicants_text_column_if_needed('foreign_edu_vocational');
CALL admission_widen_applicants_text_column_if_needed('foreign_emergency_city');
CALL admission_widen_applicants_text_column_if_needed('foreign_emergency_country');
CALL admission_widen_applicants_text_column_if_needed('foreign_emergency_house_name');
CALL admission_widen_applicants_text_column_if_needed('foreign_emergency_province');
CALL admission_widen_applicants_text_column_if_needed('foreign_emergency_street');
CALL admission_widen_applicants_text_column_if_needed('foreign_emergency_tel');
CALL admission_widen_applicants_text_column_if_needed('foreign_emergency_zip');
CALL admission_widen_applicants_text_column_if_needed('foreign_home_city');
CALL admission_widen_applicants_text_column_if_needed('foreign_home_country');
CALL admission_widen_applicants_text_column_if_needed('foreign_home_house_name');
CALL admission_widen_applicants_text_column_if_needed('foreign_home_province');
CALL admission_widen_applicants_text_column_if_needed('foreign_home_street');
CALL admission_widen_applicants_text_column_if_needed('foreign_home_tel');
CALL admission_widen_applicants_text_column_if_needed('foreign_home_zip');
CALL admission_widen_applicants_text_column_if_needed('foreign_provincial_address');
CALL admission_widen_applicants_text_column_if_needed('foreign_provincial_tel');
CALL admission_widen_applicants_text_column_if_needed('foreign_references_json');
CALL admission_widen_applicants_text_column_if_needed('foreign_siblings_json');
CALL admission_widen_applicants_text_column_if_needed('form138_path');
CALL admission_widen_applicants_text_column_if_needed('good_moral_path');
CALL admission_widen_applicants_text_column_if_needed('grad_program_address');
CALL admission_widen_applicants_text_column_if_needed('grad_program_school');
CALL admission_widen_applicants_text_column_if_needed('grad_program_year');
CALL admission_widen_applicants_text_column_if_needed('guardian_contact');
CALL admission_widen_applicants_text_column_if_needed('guardian_email');
CALL admission_widen_applicants_text_column_if_needed('guardian_home_address');
CALL admission_widen_applicants_text_column_if_needed('guardian_landline');
CALL admission_widen_applicants_text_column_if_needed('guardian_mobile');
CALL admission_widen_applicants_text_column_if_needed('guardian_name');
CALL admission_widen_applicants_text_column_if_needed('guardian_occupation');
CALL admission_widen_applicants_text_column_if_needed('guardian_relationship');
CALL admission_widen_applicants_text_column_if_needed('id_picture_path');
CALL admission_widen_applicants_text_column_if_needed('interview_date');
CALL admission_widen_applicants_text_column_if_needed('interview_link');
CALL admission_widen_applicants_text_column_if_needed('interview_mode');
CALL admission_widen_applicants_text_column_if_needed('interview_time');
CALL admission_widen_applicants_text_column_if_needed('interview_venue');
CALL admission_widen_applicants_text_column_if_needed('jhs_address');
CALL admission_widen_applicants_text_column_if_needed('jhs_school');
CALL admission_widen_applicants_text_column_if_needed('jhs_year');
CALL admission_widen_applicants_text_column_if_needed('last_name');
CALL admission_widen_applicants_text_column_if_needed('last_school');
CALL admission_widen_applicants_text_column_if_needed('last_school_year');
CALL admission_widen_applicants_text_column_if_needed('landline');
CALL admission_widen_applicants_text_column_if_needed('lrn');
CALL admission_widen_applicants_text_column_if_needed('marriage_cert_path');
CALL admission_widen_applicants_text_column_if_needed('middle_initial');
CALL admission_widen_applicants_text_column_if_needed('middle_name');
CALL admission_widen_applicants_text_column_if_needed('mobile');
CALL admission_widen_applicants_text_column_if_needed('mother_address');
CALL admission_widen_applicants_text_column_if_needed('mother_company_address');
CALL admission_widen_applicants_text_column_if_needed('mother_contact');
CALL admission_widen_applicants_text_column_if_needed('mother_email');
CALL admission_widen_applicants_text_column_if_needed('mother_highest_education');
CALL admission_widen_applicants_text_column_if_needed('mother_landline');
CALL admission_widen_applicants_text_column_if_needed('mother_name');
CALL admission_widen_applicants_text_column_if_needed('mother_occupation');
CALL admission_widen_applicants_text_column_if_needed('mother_office_tel');
CALL admission_widen_applicants_text_column_if_needed('mother_schools_attended');
CALL admission_widen_applicants_text_column_if_needed('monthly_income');
CALL admission_widen_applicants_text_column_if_needed('nationality');
CALL admission_widen_applicants_text_column_if_needed('other_doc_path');
CALL admission_widen_applicants_text_column_if_needed('passport_expiry_date');
CALL admission_widen_applicants_text_column_if_needed('passport_issue_date');
CALL admission_widen_applicants_text_column_if_needed('passport_no');
CALL admission_widen_applicants_text_column_if_needed('period_of_authorized_stay');
CALL admission_widen_applicants_text_column_if_needed('place_of_birth');
CALL admission_widen_applicants_text_column_if_needed('program1');
CALL admission_widen_applicants_text_column_if_needed('program2');
CALL admission_widen_applicants_text_column_if_needed('psa_birth_cert_path');
CALL admission_widen_applicants_text_column_if_needed('province');
CALL admission_widen_applicants_text_column_if_needed('religion');
CALL admission_widen_applicants_text_column_if_needed('school_year_text');
CALL admission_widen_applicants_text_column_if_needed('sex');
CALL admission_widen_applicants_text_column_if_needed('shs_address');
CALL admission_widen_applicants_text_column_if_needed('shs_school');
CALL admission_widen_applicants_text_column_if_needed('shs_track');
CALL admission_widen_applicants_text_column_if_needed('shs_year');
CALL admission_widen_applicants_text_column_if_needed('sibling_order');
CALL admission_widen_applicants_text_column_if_needed('street');
CALL admission_widen_applicants_text_column_if_needed('term_year');
CALL admission_widen_applicants_text_column_if_needed('undergrad_school_address');
CALL admission_widen_applicants_text_column_if_needed('visa_status');
CALL admission_widen_applicants_text_column_if_needed('zip');

-- Accident from bad physical naming: blocks INSERT (NOT NULL, no DEFAULT). Real column is middle_name_na.
SET @has_middle_typo := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'applicants'
      AND COLUMN_NAME = 'middle_namena'
);
SET @sql_drop_typo := IF(@has_middle_typo > 0, 'ALTER TABLE applicants DROP COLUMN middle_namena', 'SELECT 1');
PREPARE stmt_drop_typo FROM @sql_drop_typo;
EXECUTE stmt_drop_typo;
DEALLOCATE PREPARE stmt_drop_typo;

DROP PROCEDURE IF EXISTS admission_add_applicants_column_if_missing;
DROP PROCEDURE IF EXISTS admission_widen_applicants_text_column_if_needed;

-- ---------------------------------------------------------------------------
-- 5. Admission subsystem tables + users.email/enabled
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS school_terms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_year VARCHAR(255) NOT NULL,
    semester VARCHAR(255) NOT NULL,
    code VARCHAR(255) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    current_term TINYINT(1) NOT NULL DEFAULT 0,
    scope VARCHAR(255) NULL,
    UNIQUE KEY uk_school_terms_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS enrollment_track_settings (
    track VARCHAR(32) NOT NULL PRIMARY KEY,
    enrollment_open TINYINT(1) NOT NULL DEFAULT 1,
    closed_message TEXT NULL,
    updated_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_saved_filters (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) DEFAULT NULL,
    filter_name VARCHAR(255) DEFAULT NULL,
    keyword VARCHAR(255) DEFAULT NULL,
    status VARCHAR(255) DEFAULT NULL,
    track_filter VARCHAR(255) DEFAULT NULL,
    created_at DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_admin_saved_filters_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS requirement_upload_definitions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_track VARCHAR(32) NOT NULL,
    slot_key VARCHAR(64) NOT NULL,
    display_label TEXT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    required TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL,
    UNIQUE KEY uk_req_upload_track_slot (application_track, slot_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS data_privacy_policies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    policy_code VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    subtitle VARCHAR(255) DEFAULT NULL,
    body_html LONGTEXT NOT NULL,
    updated_at DATETIME DEFAULT NULL,
    updated_by VARCHAR(255) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_privacy_policy_code (policy_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS application_logs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    applicant_id BIGINT DEFAULT NULL,
    action VARCHAR(255) DEFAULT NULL,
    remarks TEXT,
    performed_by VARCHAR(255) DEFAULT NULL,
    actor_role VARCHAR(255) DEFAULT NULL,
    source_page VARCHAR(255) DEFAULT NULL,
    old_value TEXT,
    new_value TEXT,
    timestamp DATETIME DEFAULT NULL,
    KEY idx_application_logs_applicant (applicant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS student_requirement_files (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    applicant_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    stored_path TEXT NOT NULL,
    verified TINYINT(1) NOT NULL,
    UNIQUE KEY uk_student_req_file (applicant_id, definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS enabled TINYINT(1) NOT NULL DEFAULT 1;

UPDATE users
SET email = CONCAT(username, '@admission.local')
WHERE id IS NOT NULL AND id >= 1 AND (email IS NULL OR TRIM(email) = '');

-- ---------------------------------------------------------------------------
-- 6. Enrollment3 finance / assessment tables
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS enrollment_settings (
    setting_key   VARCHAR(80)  NOT NULL PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    description   VARCHAR(255) DEFAULT NULL,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS term_installment_plan (
    plan_id            INT AUTO_INCREMENT PRIMARY KEY,
    term_id            INT NULL COMMENT 'NULL = default for all terms',
    installment_number TINYINT NOT NULL,
    due_months_offset  INT NOT NULL DEFAULT 1,
    installment_label  VARCHAR(80) NOT NULL,
    UNIQUE KEY uk_term_inst (term_id, installment_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS program_general_fees (
    general_fee_id    INT AUTO_INCREMENT PRIMARY KEY,
    program_id        INT NOT NULL,
    term_id           INT NULL COMMENT 'NULL = default for all terms',
    year_level        TINYINT NOT NULL DEFAULT 1,
    semester_number   TINYINT NOT NULL DEFAULT 1,
    tuition_per_unit  DECIMAL(10,2) DEFAULT NULL,
    lec_fee_per_unit  DECIMAL(10,2) DEFAULT NULL,
    lab_fee_per_unit  DECIMAL(10,2) DEFAULT NULL,
    comp_fee_per_unit DECIMAL(10,2) DEFAULT NULL,
    rle_fee_per_unit  DECIMAL(10,2) DEFAULT NULL,
    is_active         TINYINT(1) NOT NULL DEFAULT 1,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_pgf_program (program_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS program_specific_fees (
    specific_fee_id INT AUTO_INCREMENT PRIMARY KEY,
    program_id        INT NOT NULL,
    term_id           INT NULL,
    year_level        TINYINT NULL,
    semester_number   TINYINT NULL,
    fee_code          VARCHAR(30) NOT NULL,
    fee_name          VARCHAR(150) NOT NULL,
    fee_group         ENUM('MISC','OTHER','ADMISSION','PROGRAM') NOT NULL DEFAULT 'MISC',
    amount            DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    is_required       TINYINT(1) NOT NULL DEFAULT 1,
    is_active         TINYINT(1) NOT NULL DEFAULT 1,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_psf_program (program_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO enrollment_settings (setting_key, setting_value, description) VALUES
('downpayment_amount', '3000', 'Fixed downpayment in PHP when percent is 0'),
('downpayment_percent', '0', 'Percent of total assessment for DP; 0 = use fixed amount'),
('max_units_regular', '24', 'Maximum credit units per term'),
('max_units_graduating_bonus', '6', 'Extra units when in final curriculum year'),
('enrollment_session_minutes', '15', 'Minutes before PENDING enlistment session blocks student'),
('drop_penalty_days_half', '7', 'Days before 50% drop penalty'),
('drop_penalty_days_full', '21', 'Days before 100% drop penalty'),
('drop_penalty_first_week_percent', '25', 'First-week withdrawal charge percent'),
('drop_penalty_half_percent', '50', 'Penalty percent between thresholds'),
('rle_hours_per_unit', '51', 'Clinical/RLE hours per credit unit (BSN)'),
('rle_rate_per_hour', '87', 'RLE hourly rate fallback'),
('fee_fallback_enabled', 'false', 'Use DB fees only when false'),
('default_tuition_per_unit', '1483', 'Last-resort tuition per unit');

INSERT IGNORE INTO term_installment_plan (term_id, installment_number, due_months_offset, installment_label) VALUES
(NULL, 1, 1, '1st Installment'),
(NULL, 2, 2, '2nd Installment'),
(NULL, 3, 3, '3rd Installment');

-- BSIT Y1S1 fee seed (program_id 1)
INSERT IGNORE INTO program_general_fees
    (program_id, term_id, year_level, semester_number, tuition_per_unit, lec_fee_per_unit, lab_fee_per_unit, comp_fee_per_unit, is_active)
VALUES (1, NULL, 1, 1, 1483.00, 1483.00, 0.00, 200.00, 1);

INSERT IGNORE INTO program_specific_fees
    (program_id, term_id, year_level, semester_number, fee_code, fee_name, fee_group, amount, is_required, is_active)
VALUES
(1, NULL, 1, 1, 'MISC_REG', 'Registration', 'MISC', 876.00, 1, 1),
(1, NULL, 1, 1, 'MISC_GUIDANCE', 'Guidance & Counselling', 'MISC', 439.00, 1, 1),
(1, NULL, 1, 1, 'MISC_LMS', 'Learning Management System', 'MISC', 514.00, 1, 1),
(1, NULL, 1, 1, 'MISC_MED', 'Medical / Dental', 'MISC', 1006.00, 1, 1),
(1, NULL, 1, 1, 'MISC_ATHLETIC', 'Athletic Fee', 'MISC', 627.00, 1, 1),
(1, NULL, 1, 1, 'MISC_LIBRARY', 'Library Fee', 'MISC', 1389.00, 1, 1),
(1, NULL, 1, 1, 'OTHER_ID', 'Identification Card', 'OTHER', 150.00, 1, 1),
(1, NULL, 1, 1, 'OTHER_INS', 'Insurance', 'OTHER', 400.00, 1, 1);

-- ---------------------------------------------------------------------------
-- 7. Registrar VPAA + grade change workflow
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vpaa_extensions (
    ext_id      INT AUTO_INCREMENT PRIMARY KEY,
    schedule_id INT,
    faculty_id  INT,
    status      VARCHAR(50) DEFAULT 'PENDING',
    reason      VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS grade_change_requests (
    request_id      INT AUTO_INCREMENT PRIMARY KEY,
    grade_id        BIGINT,
    student_name    VARCHAR(100),
    course_code     VARCHAR(20),
    faculty_name    VARCHAR(100),
    request_type    VARCHAR(40) NOT NULL DEFAULT 'FINAL_GRADE_CORRECTION',
    requested_grade VARCHAR(20),
    requested_prelim DECIMAL(5,2) NULL,
    requested_midterm DECIMAL(5,2) NULL,
    requested_finals DECIMAL(5,2) NULL,
    reason          TEXT,
    status          VARCHAR(30) DEFAULT 'PENDING',
    request_date    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    applied_action  VARCHAR(80) NULL,
    approved_at     TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP VIEW IF EXISTS student_grades;
CREATE OR REPLACE VIEW student_grades AS
SELECT
    g.id              AS grade_id,
    g.section_id      AS schedule_id,
    g.student_name,
    g.student_id,
    g.prelim,
    g.midterm,
    g.final_grade     AS `final`,
    g.semestral_grade,
    g.remarks,
    g.previous_grade,
    COALESCE(g.status, 'DRAFT') AS status
FROM grades g;

-- ---------------------------------------------------------------------------
-- 8. Grading period settings (registrar AcademicGradingService)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grading_term_windows (
    window_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    term_id INT NOT NULL,
    grading_period VARCHAR(20) NOT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    override_status VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_gtw_term_period (term_id, grading_period),
    KEY idx_gtw_term (term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS academic_term_policies (
    term_id INT PRIMARY KEY,
    inc_expiration_date DATE NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO system_settings (setting_key, setting_value) VALUES
('PRELIM_START', '2025-08-01'),
('PRELIM_END', '2025-09-15'),
('MIDTERM_START', '2025-10-01'),
('MIDTERM_END', '2025-11-15'),
('FINAL_START', '2025-12-01'),
('FINAL_END', '2025-12-20'),
('PRELIM_OVERRIDE', ''),
('MIDTERM_OVERRIDE', ''),
('FINAL_OVERRIDE', ''),
('enrollment_open', 'true'),
('ACCOUNTING_BLOCK_THRESHOLD', '100.0'),
('ADMISSION_MIN_PAYMENT', '1000.0'),
('DOWNPAYMENT_THRESHOLD', '3000.0'),
('DOWNPAYMENT_PERCENT', '0'),
('SCHOLARSHIP_MAX_GWA', '1.75'),
('SCHOLARSHIP_MAX_PRELIM_GRADE', '2.00'),
('SCHOLARSHIP_MAX_MIDTERM_GRADE', '2.00'),
('SCHOLARSHIP_MAX_FINALS_GRADE', '2.00'),
('SCHOLARSHIP_DEFAULT_DISCOUNT_PERCENT', '100.0'),
('SCHOLARSHIP_MIN_COMPLETED_UNITS', '27'),
('SCHOLARSHIP_DISQUALIFY_INC', 'true'),
('SCHOLARSHIP_DISQUALIFY_FAILED', 'true');

UPDATE system_settings SET setting_value = 'true' WHERE setting_key = 'enrollment_open';

-- ---------------------------------------------------------------------------
-- 9. Staff logins (Enrollment3 + Registrar) — password: 1234
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_users (username, password, real_name, role, is_active, status) VALUES
('admin',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EhsLkT/Oh.reShMkwpfpJ2', 'System Administrator', 'Admin',   1, 'ACTIVE'),
('cashier', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EhsLkT/Oh.reShMkwpfpJ2', 'Enrollment Cashier',   'Cashier', 1, 'ACTIVE'),
('faculty', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EhsLkT/Oh.reShMkwpfpJ2', 'Faculty Demo',         'Faculty', 1, 'ACTIVE'),
('prof',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EhsLkT/Oh.reShMkwpfpJ2', 'Professor Demo',       'Faculty', 1, 'ACTIVE');

-- ---------------------------------------------------------------------------
-- 10. Verification
-- ---------------------------------------------------------------------------
SELECT 'eacdb_cross_system_schema applied' AS status;

SELECT TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'grades'
  AND COLUMN_NAME IN ('prelim','midterm','final_grade','semestral_grade');

SELECT TABLE_NAME, TABLE_TYPE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('student_grades','enrollment_settings','program_general_fees','school_terms');

SET SQL_SAFE_UPDATES = 1;
