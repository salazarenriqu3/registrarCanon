-- =============================================================================
-- Calendar academic terms (registrar + enrollment TermParserUtil)
--
-- DB term_code:  {semester}1{AY_start}{AY_end}     e.g. 1120242025, 2120242025
-- Student term_year (SL_): SL_{sem}{yearLevel}{AY_start}{AY_end}
--   e.g. Y1 1st sem A.Y. 2024-2025 → SL_1120242025  (maps to term_code 1120242025)
--
-- One row per school calendar semester (not per curriculum year-level slot).
-- Run before BSIT class-section seed and capss-demo-required/01_setup/03_seed_program_fees_full_lifecycle.sql.
-- Safe to re-run: upserts by term_code; does not change which term is ACTIVE.
-- =============================================================================
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;
USE eacdb;

-- Retire legacy per-slot codes (sem + yearLevel + years, e.g. 1220242025 = Y2 slot)
UPDATE academic_terms
SET status = 'INACTIVE',
    is_active = 0
WHERE term_code REGEXP '^[12][2-4][0-9]{8}$';

INSERT INTO academic_terms
    (term_code, term_name, academic_year, semester_number, start_date, end_date, status, is_active)
VALUES
    ('1120242025', 'A.Y. 2024-2025 - 1st Semester', '2024-2025', 1, '2024-08-01', '2024-12-15', 'INACTIVE', 0),
    ('2120242025', 'A.Y. 2024-2025 - 2nd Semester', '2024-2025', 2, '2025-01-15', '2025-05-30', 'INACTIVE', 0),
    ('1120252026', 'A.Y. 2025-2026 - 1st Semester', '2025-2026', 1, '2025-08-01', '2025-12-15', 'INACTIVE', 0),
    ('2120252026', 'A.Y. 2025-2026 - 2nd Semester', '2025-2026', 2, '2026-01-15', '2026-05-30', 'INACTIVE', 0),
    ('1120262027', 'A.Y. 2026-2027 - 1st Semester', '2026-2027', 1, '2026-08-01', '2026-12-15', 'INACTIVE', 0),
    ('2120262027', 'A.Y. 2026-2027 - 2nd Semester', '2026-2027', 2, '2027-01-15', '2027-05-30', 'INACTIVE', 0),
    ('1120272028', 'A.Y. 2027-2028 - 1st Semester', '2027-2028', 1, '2027-08-01', '2027-12-15', 'INACTIVE', 0),
    ('2120272028', 'A.Y. 2027-2028 - 2nd Semester', '2027-2028', 2, '2028-01-15', '2028-05-30', 'INACTIVE', 0)
ON DUPLICATE KEY UPDATE
    term_name = VALUES(term_name),
    academic_year = VALUES(academic_year),
    semester_number = VALUES(semester_number),
    start_date = VALUES(start_date),
    end_date = VALUES(end_date);

-- Normalize SL_* stored in CURRENT_ACADEMIC_TERM to canonical DB code when possible
UPDATE system_settings ss
SET setting_value = CONCAT(
        SUBSTRING(ss.setting_value, 4, 1),
        '1',
        SUBSTRING(ss.setting_value, 6, 4),
        SUBSTRING(ss.setting_value, 10, 4)
    )
WHERE ss.setting_key = 'CURRENT_ACADEMIC_TERM'
  AND ss.setting_value LIKE 'SL\_%'
  AND LENGTH(ss.setting_value) >= 13
  AND EXISTS (
      SELECT 1 FROM academic_terms at
      WHERE at.term_code = CONCAT(
          SUBSTRING(ss.setting_value, 4, 1), '1',
          SUBSTRING(ss.setting_value, 6, 4),
          SUBSTRING(ss.setting_value, 10, 4)
      )
  );

SELECT term_id, term_code, term_name, academic_year, semester_number, status, is_active
FROM academic_terms
WHERE term_code REGEXP '^[12]1[0-9]{8}$'
ORDER BY term_code;
