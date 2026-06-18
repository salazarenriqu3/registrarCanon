-- =============================================================================
-- SCHEMA MIGRATION: Reconcile eacdb with Updated Registrar Codebase
-- Generated: 2026-06-05
-- Purpose:   Bring the live eacdb database in sync with all architectural
--            changes from Phase 3 (Refactoring), Phase 4 (MCP), and
--            Phase 6 (Fee Settings Simplification).
-- =============================================================================
-- HOW TO RUN:
--   mysql -u root -p eacdb < schema_migration_001.sql
-- Or run block-by-block in MySQL Workbench.
-- All statements are idempotent (safe to re-run).
-- =============================================================================

USE eacdb;

-- =========================================================
-- SECTION 1: NEW TABLE — program_fee_settings
-- =========================================================
-- Replaces: program_general_fees + program_specific_fees (EAV)
-- Used by:  TermFeeAdminService, ScholarEnrollmentService
-- Note:     0.00 = fee not applicable, > 0 = fee is applied & computed
-- =========================================================

CREATE TABLE IF NOT EXISTS program_fee_settings (
    fee_setting_id          INT AUTO_INCREMENT PRIMARY KEY,

    -- Scope
    program_id              INT NOT NULL,
    term_id                 INT NULL,           -- NULL = fallback template for all terms
    year_level              INT NULL,
    semester_number         INT NULL,

    -- Core per-unit academic rates
    fee_tuition_per_unit    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_lec_per_unit        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_lab_per_unit        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_comp_per_unit       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_rle_per_unit        DECIMAL(10,2) NOT NULL DEFAULT 0.00,

    -- Flat miscellaneous charges
    fee_misc_registration   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_misc_library        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_misc_medical        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_misc_id             DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_misc_athletic       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_misc_guidance       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_misc_lms            DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_misc_insurance      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_misc_cultural       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_misc_av             DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_misc_energy         DECIMAL(10,2) NOT NULL DEFAULT 0.00,

    -- Other flat charges
    fee_other_id            DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_other_insurance     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_other_comp          DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_other_dev           DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_other_late_enrollment DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_other_add_drop      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fee_other_installment   DECIMAL(10,2) NOT NULL DEFAULT 0.00,

    is_active               TINYINT(1) NOT NULL DEFAULT 1,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_pfs_scope (program_id, year_level, semester_number, term_id),
    CONSTRAINT fk_pfs_program FOREIGN KEY (program_id) REFERENCES programs(program_id) ON DELETE CASCADE
);

-- =========================================================
-- SECTION 2: NEW TABLE — term_transition_audit
-- =========================================================
-- Used by:  AcademicGradingService.triggerTermTransition()
-- Purpose:  Audit log for every term transition attempt (success or failure)
-- =========================================================

CREATE TABLE IF NOT EXISTS term_transition_audit (
    audit_id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    requested_term_code     VARCHAR(32) NULL,
    target_db_term_code     VARCHAR(32) NULL,
    target_term_id          INT NULL,
    success                 TINYINT(1) NOT NULL DEFAULT 0,
    advanced_count          INT NOT NULL DEFAULT 0,
    forwarded_debt_count    INT NOT NULL DEFAULT 0,
    error_message           VARCHAR(500) NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- SECTION 3: DATA MIGRATION — program_general_fees → program_fee_settings
-- =========================================================
-- Migrates all core academic rate rows from the old wide table.
-- Only migrates is_active=1 rows.
-- =========================================================

INSERT INTO program_fee_settings (
    program_id, term_id, year_level, semester_number,
    fee_tuition_per_unit, fee_lec_per_unit, fee_lab_per_unit,
    fee_comp_per_unit, fee_rle_per_unit, is_active
)
SELECT
    pgf.program_id,
    pgf.term_id,
    pgf.year_level,
    pgf.semester_number,
    COALESCE(pgf.tuition_per_unit,  0.00),
    COALESCE(pgf.lec_fee_per_unit,  0.00),
    COALESCE(pgf.lab_fee_per_unit,  0.00),
    COALESCE(pgf.comp_fee_per_unit, 0.00),
    COALESCE(pgf.rle_fee_per_unit,  0.00),
    pgf.is_active
FROM program_general_fees pgf
WHERE pgf.is_active = 1
-- Avoid duplicates if migration is re-run
ON DUPLICATE KEY UPDATE
    fee_tuition_per_unit = VALUES(fee_tuition_per_unit),
    fee_lec_per_unit     = VALUES(fee_lec_per_unit),
    fee_rle_per_unit     = VALUES(fee_rle_per_unit);

-- =========================================================
-- SECTION 4: DATA MIGRATION — program_specific_fees → program_fee_settings
-- =========================================================
-- Migrates each known fee_code from EAV rows into the correct column.
-- Must be run AFTER Section 3 (core rows must already exist to UPDATE).
-- Uses UPDATE with JOIN to patch in each specific fee code.
-- =========================================================

-- MISC_REG → fee_misc_registration
UPDATE program_fee_settings pfs
JOIN program_specific_fees psf
    ON  pfs.program_id      = psf.program_id
    AND pfs.year_level      = psf.year_level
    AND pfs.semester_number = psf.semester_number
    AND (pfs.term_id = psf.term_id OR (pfs.term_id IS NULL AND psf.term_id IS NULL))
    AND psf.fee_code        = 'MISC_REG'
    AND psf.is_active       = 1
SET pfs.fee_misc_registration = psf.amount;

-- MISC_GUIDANCE → fee_misc_guidance
UPDATE program_fee_settings pfs
JOIN program_specific_fees psf
    ON  pfs.program_id = psf.program_id AND pfs.year_level = psf.year_level
    AND pfs.semester_number = psf.semester_number
    AND (pfs.term_id = psf.term_id OR (pfs.term_id IS NULL AND psf.term_id IS NULL))
    AND psf.fee_code = 'MISC_GUIDANCE' AND psf.is_active = 1
SET pfs.fee_misc_guidance = psf.amount;

-- MISC_LMS → fee_misc_lms
UPDATE program_fee_settings pfs
JOIN program_specific_fees psf
    ON  pfs.program_id = psf.program_id AND pfs.year_level = psf.year_level
    AND pfs.semester_number = psf.semester_number
    AND (pfs.term_id = psf.term_id OR (pfs.term_id IS NULL AND psf.term_id IS NULL))
    AND psf.fee_code = 'MISC_LMS' AND psf.is_active = 1
SET pfs.fee_misc_lms = psf.amount;

-- MISC_MED → fee_misc_medical
UPDATE program_fee_settings pfs
JOIN program_specific_fees psf
    ON  pfs.program_id = psf.program_id AND pfs.year_level = psf.year_level
    AND pfs.semester_number = psf.semester_number
    AND (pfs.term_id = psf.term_id OR (pfs.term_id IS NULL AND psf.term_id IS NULL))
    AND psf.fee_code = 'MISC_MED' AND psf.is_active = 1
SET pfs.fee_misc_medical = psf.amount;

-- MISC_ATHLETIC → fee_misc_athletic
UPDATE program_fee_settings pfs
JOIN program_specific_fees psf
    ON  pfs.program_id = psf.program_id AND pfs.year_level = psf.year_level
    AND pfs.semester_number = psf.semester_number
    AND (pfs.term_id = psf.term_id OR (pfs.term_id IS NULL AND psf.term_id IS NULL))
    AND psf.fee_code = 'MISC_ATHLETIC' AND psf.is_active = 1
SET pfs.fee_misc_athletic = psf.amount;

-- MISC_LIBRARY → fee_misc_library
UPDATE program_fee_settings pfs
JOIN program_specific_fees psf
    ON  pfs.program_id = psf.program_id AND pfs.year_level = psf.year_level
    AND pfs.semester_number = psf.semester_number
    AND (pfs.term_id = psf.term_id OR (pfs.term_id IS NULL AND psf.term_id IS NULL))
    AND psf.fee_code = 'MISC_LIBRARY' AND psf.is_active = 1
SET pfs.fee_misc_library = psf.amount;

-- MISC_INS → fee_misc_insurance
UPDATE program_fee_settings pfs
JOIN program_specific_fees psf
    ON  pfs.program_id = psf.program_id AND pfs.year_level = psf.year_level
    AND pfs.semester_number = psf.semester_number
    AND (pfs.term_id = psf.term_id OR (pfs.term_id IS NULL AND psf.term_id IS NULL))
    AND psf.fee_code = 'MISC_INS' AND psf.is_active = 1
SET pfs.fee_misc_insurance = psf.amount;

-- MISC_CULT → fee_misc_cultural
UPDATE program_fee_settings pfs
JOIN program_specific_fees psf
    ON  pfs.program_id = psf.program_id AND pfs.year_level = psf.year_level
    AND pfs.semester_number = psf.semester_number
    AND (pfs.term_id = psf.term_id OR (pfs.term_id IS NULL AND psf.term_id IS NULL))
    AND psf.fee_code = 'MISC_CULT' AND psf.is_active = 1
SET pfs.fee_misc_cultural = psf.amount;

-- MISC_AV → fee_misc_av
UPDATE program_fee_settings pfs
JOIN program_specific_fees psf
    ON  pfs.program_id = psf.program_id AND pfs.year_level = psf.year_level
    AND pfs.semester_number = psf.semester_number
    AND (pfs.term_id = psf.term_id OR (pfs.term_id IS NULL AND psf.term_id IS NULL))
    AND psf.fee_code = 'MISC_AV' AND psf.is_active = 1
SET pfs.fee_misc_av = psf.amount;

-- MISC_ENERGY → fee_misc_energy
UPDATE program_fee_settings pfs
JOIN program_specific_fees psf
    ON  pfs.program_id = psf.program_id AND pfs.year_level = psf.year_level
    AND pfs.semester_number = psf.semester_number
    AND (pfs.term_id = psf.term_id OR (pfs.term_id IS NULL AND psf.term_id IS NULL))
    AND psf.fee_code = 'MISC_ENERGY' AND psf.is_active = 1
SET pfs.fee_misc_energy = psf.amount;

-- OTHER_ID → fee_other_id
UPDATE program_fee_settings pfs
JOIN program_specific_fees psf
    ON  pfs.program_id = psf.program_id AND pfs.year_level = psf.year_level
    AND pfs.semester_number = psf.semester_number
    AND (pfs.term_id = psf.term_id OR (pfs.term_id IS NULL AND psf.term_id IS NULL))
    AND psf.fee_code = 'OTHER_ID' AND psf.is_active = 1
SET pfs.fee_other_id = psf.amount;

-- OTHER_INS → fee_other_insurance
UPDATE program_fee_settings pfs
JOIN program_specific_fees psf
    ON  pfs.program_id = psf.program_id AND pfs.year_level = psf.year_level
    AND pfs.semester_number = psf.semester_number
    AND (pfs.term_id = psf.term_id OR (pfs.term_id IS NULL AND psf.term_id IS NULL))
    AND psf.fee_code = 'OTHER_INS' AND psf.is_active = 1
SET pfs.fee_other_insurance = psf.amount;

-- OTHER_COMP → fee_other_comp
UPDATE program_fee_settings pfs
JOIN program_specific_fees psf
    ON  pfs.program_id = psf.program_id AND pfs.year_level = psf.year_level
    AND pfs.semester_number = psf.semester_number
    AND (pfs.term_id = psf.term_id OR (pfs.term_id IS NULL AND psf.term_id IS NULL))
    AND psf.fee_code = 'OTHER_COMP' AND psf.is_active = 1
SET pfs.fee_other_comp = psf.amount;

-- OTHER_DEV → fee_other_dev
UPDATE program_fee_settings pfs
JOIN program_specific_fees psf
    ON  pfs.program_id = psf.program_id AND pfs.year_level = psf.year_level
    AND pfs.semester_number = psf.semester_number
    AND (pfs.term_id = psf.term_id OR (pfs.term_id IS NULL AND psf.term_id IS NULL))
    AND psf.fee_code = 'OTHER_DEV' AND psf.is_active = 1
SET pfs.fee_other_dev = psf.amount;

-- =========================================================
-- SECTION 5: VERIFICATION QUERIES
-- Run these after applying the migration to confirm success.
-- =========================================================

-- Check row counts match (should be >= program_general_fees count)
SELECT
    (SELECT COUNT(*) FROM program_general_fees WHERE is_active=1)  AS old_general_rows,
    (SELECT COUNT(*) FROM program_fee_settings  WHERE is_active=1)  AS new_unified_rows;

-- Spot-check: verify tuition migrated correctly for program 1, Y1S1
SELECT
    pgf.program_id, pgf.tuition_per_unit AS old_tuition,
    pfs.fee_tuition_per_unit             AS new_tuition
FROM program_general_fees pgf
JOIN program_fee_settings pfs
    ON  pgf.program_id      = pfs.program_id
    AND pgf.year_level      = pfs.year_level
    AND pgf.semester_number = pfs.semester_number
    AND (pgf.term_id = pfs.term_id OR (pgf.term_id IS NULL AND pfs.term_id IS NULL))
WHERE pgf.is_active = 1
LIMIT 10;

-- Spot-check: verify misc fees migrated for program 1, Y1S1
SELECT
    fee_setting_id, program_id, term_id, year_level, semester_number,
    fee_misc_registration, fee_misc_library, fee_misc_medical,
    fee_misc_guidance, fee_misc_lms, fee_misc_energy
FROM program_fee_settings
WHERE program_id = 1 AND year_level = 1 AND semester_number = 1
LIMIT 5;

-- Confirm term_transition_audit table exists
SELECT COUNT(*) AS audit_table_ok FROM information_schema.tables
WHERE table_schema='eacdb' AND table_name='term_transition_audit';

-- =========================================================
-- SECTION 6: POST-MIGRATION — Code Updates Required
-- =========================================================
-- The following fee codes exist in the database but were NOT
-- included in the initial KNOWN_FEES list in TermFeeAdminService.
-- You MUST update the Java code to add these columns:
--
--   fee_misc_guidance, fee_misc_lms, fee_misc_insurance,
--   fee_misc_cultural, fee_misc_av, fee_misc_energy,
--   fee_other_id, fee_other_insurance, fee_other_comp, fee_other_dev
--
-- Update:
--   1. ProgramFeeSetting.java  — add @Column field + getter/setter + getFee()/setFee() cases
--   2. TermFeeAdminService.java — add codes to KNOWN_FEES list + listFeeTypesForAdmin()
-- =========================================================

-- =============================================================================
-- END OF MIGRATION
-- =============================================================================
