-- =============================================================================
-- Seed miscellaneous / other fees (+ tuition row) for every program through
-- the curriculum lifecycle, linked to each matching academic_terms row.
--
-- Term code convention (registrar / enrollment TermParserUtil):
--   term_code = {semester}1{AY_start}{AY_end}  (calendar semester, not year level)
--   Example: 1120262027 = 1st sem A.Y. 2026-2027; student Y2 → SL_1220262027
-- Run once in MySQL Workbench (entire file), after steps 1–3 below:
--   1) db/fix
--   2) capss-demo-required/01_setup/01_fresh_demo_bootstrap.sql
--   3) capss-demo-required/01_setup/02_bsit_full_align_term_and_curriculum.sql
-- See handoff/05-demo-guides/README_DEMO_SQL.md for full demo order.
--
-- Safe to re-run: uses NOT EXISTS guards (no duplicate fee_code per scope).
-- Adjust amounts in section 1 (templates) before running in production.
-- =============================================================================
USE eacdb;

SET @fee_tpl_year  = 1;
SET @fee_tpl_sem   = 1;

-- -----------------------------------------------------------------------------
-- 0. Optional: ensure standard lifecycle academic_terms exist (demo / greenfield)
--    Skip if you already manage terms in Registrar.
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO academic_terms
    (term_code, term_name, academic_year, semester_number, start_date, end_date, status, is_active)
SELECT
    CONCAT(sem.n, '1', ay.start_y, ay.end_y) AS term_code,
    CONCAT('A.Y. ', ay.start_y, '-', ay.end_y, ' - ',
           IF(sem.n = 1, '1st Semester', '2nd Semester')) AS term_name,
    CONCAT(ay.start_y, '-', ay.end_y) AS academic_year,
    sem.n AS semester_number,
    DATE(CONCAT(ay.start_y, IF(sem.n = 1, '-08-01', '-01-15'))) AS start_date,
    DATE(CONCAT(IF(sem.n = 1, ay.start_y, ay.end_y), IF(sem.n = 1, '-12-15', '-05-30'))) AS end_date,
    'INACTIVE' AS status,
    0 AS is_active
FROM (SELECT 1 AS n UNION SELECT 2) sem
CROSS JOIN (
    SELECT 2024 AS start_y, 2025 AS end_y UNION
    SELECT 2025, 2026 UNION
    SELECT 2026, 2027 UNION
    SELECT 2027, 2028
) ay;

-- -----------------------------------------------------------------------------
-- 1. Parsed academic terms (semester + year level from term_code)
-- -----------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_term_parsed;
CREATE TEMPORARY TABLE tmp_term_parsed AS
SELECT
    at.term_id,
    at.term_code,
    at.term_name,
    COALESCE(
        NULLIF(at.semester_number, 0),
        CAST(LEFT(at.term_code, 1) AS UNSIGNED)
    ) AS semester_number,
    yl.n AS year_level
FROM academic_terms at
CROSS JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) yl
WHERE at.term_code REGEXP '^[12]1[0-9]{8}$'
  AND CAST(LEFT(at.term_code, 1) AS UNSIGNED) IN (1, 2);

-- -----------------------------------------------------------------------------
-- 2. Lifecycle slots per program (curriculum-driven, else Y1–Y4 × Sem 1–2)
-- -----------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_program_slots;
CREATE TEMPORARY TABLE tmp_program_slots AS
SELECT DISTINCT
    p.program_id,
    p.program_code,
    cc.year_level,
    cc.semester_number
FROM programs p
JOIN curriculum_templates ct
    ON ct.program_id = p.program_id AND ct.is_active = 1
JOIN curriculum_courses cc ON cc.curriculum_id = ct.curriculum_id
WHERE COALESCE(p.active_status, 1) = 1
  AND cc.year_level BETWEEN 1 AND 4
  AND cc.semester_number IN (1, 2);

INSERT INTO tmp_program_slots (program_id, program_code, year_level, semester_number)
SELECT p.program_id, p.program_code, yl.n, sem.n
FROM programs p
CROSS JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) yl
CROSS JOIN (SELECT 1 AS n UNION SELECT 2) sem
WHERE COALESCE(p.active_status, 1) = 1
  AND NOT EXISTS (
      SELECT 1 FROM tmp_program_slots s WHERE s.program_id = p.program_id
  );

-- -----------------------------------------------------------------------------
-- 3. Bootstrap Y1S1 fee template for any program that has none yet
-- -----------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_tuition_rate;
CREATE TEMPORARY TABLE tmp_tuition_rate AS
SELECT
    p.program_id,
    p.program_code,
    CASE
        WHEN UPPER(p.program_code) IN ('BSN') OR UPPER(p.program_code) LIKE '%NURS%' THEN 1773.00
        WHEN UPPER(p.program_code) LIKE '%COMM%' THEN 991.00
        WHEN UPPER(p.program_code) LIKE '%BIO%' OR UPPER(p.program_code) LIKE '%PSYCH%' THEN 1293.00
        WHEN UPPER(p.program_code) LIKE '%EDUC%' OR UPPER(p.program_code) LIKE '%TCP%' THEN 1238.00
        WHEN UPPER(p.program_code) LIKE '%CRIM%' THEN 1483.00
        WHEN UPPER(p.program_code) LIKE '%MIDW%' THEN 1012.00
        WHEN UPPER(p.program_code) LIKE '%MEDT%' OR UPPER(p.program_code) LIKE '%PHARM%'
          OR UPPER(p.program_code) LIKE '%RAD%' THEN 1227.00
        WHEN UPPER(p.program_code) LIKE '%OT%' OR UPPER(p.program_code) LIKE '%OCCU%' THEN 1633.00
        WHEN UPPER(p.program_code) LIKE '%DENT%' THEN 1806.00
        WHEN UPPER(p.program_code) LIKE '%HM%' OR UPPER(p.program_code) LIKE '%HOSP%' THEN 1307.00
        ELSE 1483.00
    END AS tuition_per_unit
FROM programs p
WHERE COALESCE(p.active_status, 1) = 1;

-- General fees template (Y1S1)
INSERT INTO program_general_fees
    (program_id, term_id, year_level, semester_number,
     tuition_per_unit, lec_fee_per_unit, lab_fee_per_unit, comp_fee_per_unit, is_active)
SELECT
    tr.program_id, NULL, @fee_tpl_year, @fee_tpl_sem,
    tr.tuition_per_unit, tr.tuition_per_unit, 0.00, 200.00, 1
FROM tmp_tuition_rate tr
WHERE NOT EXISTS (
    SELECT 1 FROM program_general_fees pgf
    WHERE pgf.program_id = tr.program_id
      AND pgf.year_level = @fee_tpl_year
      AND pgf.semester_number = @fee_tpl_sem
      AND pgf.term_id IS NULL
);

-- Institutional MISC template (Y1S1) — adjust amounts here
INSERT INTO program_specific_fees
    (program_id, term_id, year_level, semester_number,
     fee_code, fee_name, fee_group, amount, is_required, is_active)
SELECT tr.program_id, NULL, @fee_tpl_year, @fee_tpl_sem, v.fee_code, v.fee_name, 'MISC', v.amount, 1, 1
FROM tmp_tuition_rate tr
CROSS JOIN (
    SELECT 'MISC_REG'      AS fee_code, 'Registration'                 AS fee_name, 876.00  AS amount UNION ALL
    SELECT 'MISC_GUIDANCE', 'Guidance & Counselling',                      439.00 UNION ALL
    SELECT 'MISC_LMS',      'Learning Management System',                  514.00 UNION ALL
    SELECT 'MISC_MED',      'Medical / Dental',                             1006.00 UNION ALL
    SELECT 'MISC_ATHLETIC', 'Athletic Fee',                                  627.00 UNION ALL
    SELECT 'MISC_LIBRARY',  'Library Fee',                                  1389.00 UNION ALL
    SELECT 'MISC_INS',      'Student Accident Insurance',                    225.00 UNION ALL
    SELECT 'MISC_CULT',     'Cultural Fee',                                  306.00 UNION ALL
    SELECT 'MISC_AV',       'Multi-media / Audio Visual',                    906.00 UNION ALL
    SELECT 'MISC_ENERGY',   'Energy Fee',                                    674.00
) v
WHERE NOT EXISTS (
    SELECT 1 FROM program_specific_fees psf
    WHERE psf.program_id = tr.program_id
      AND psf.year_level = @fee_tpl_year
      AND psf.semester_number = @fee_tpl_sem
      AND psf.term_id IS NULL
      AND psf.fee_code = v.fee_code
);

-- OTHER template (all programs)
INSERT INTO program_specific_fees
    (program_id, term_id, year_level, semester_number,
     fee_code, fee_name, fee_group, amount, is_required, is_active)
SELECT tr.program_id, NULL, @fee_tpl_year, @fee_tpl_sem, v.fee_code, v.fee_name, 'OTHER', v.amount, 1, 1
FROM tmp_tuition_rate tr
CROSS JOIN (
    SELECT 'OTHER_ID'  AS fee_code, 'Identification Card' AS fee_name, 150.00 AS amount UNION ALL
    SELECT 'OTHER_INS', 'Insurance',                              400.00
) v
WHERE NOT EXISTS (
    SELECT 1 FROM program_specific_fees psf
    WHERE psf.program_id = tr.program_id
      AND psf.year_level = @fee_tpl_year
      AND psf.semester_number = @fee_tpl_sem
      AND psf.term_id IS NULL
      AND psf.fee_code = v.fee_code
);

-- OTHER — IT / CS / CPE lab-style programs (Computer + Developmental)
INSERT INTO program_specific_fees
    (program_id, term_id, year_level, semester_number,
     fee_code, fee_name, fee_group, amount, is_required, is_active)
SELECT tr.program_id, NULL, @fee_tpl_year, @fee_tpl_sem, v.fee_code, v.fee_name, 'OTHER', v.amount, 1, 1
FROM tmp_tuition_rate tr
CROSS JOIN (
    SELECT 'OTHER_COMP' AS fee_code, 'Computer Hands-On' AS fee_name, 13930.00 AS amount UNION ALL
    SELECT 'OTHER_DEV',  'Developmental Fees',                   4632.00
) v
WHERE (
    UPPER(tr.program_code) LIKE '%IT%'
    OR UPPER(tr.program_code) LIKE '%CS%'
    OR UPPER(tr.program_code) LIKE '%CPE%'
    OR UPPER(tr.program_code) IN ('BSIT', 'BSCS', 'BSCPE')
)
AND NOT EXISTS (
    SELECT 1 FROM program_specific_fees psf
    WHERE psf.program_id = tr.program_id
      AND psf.year_level = @fee_tpl_year
      AND psf.semester_number = @fee_tpl_sem
      AND psf.term_id IS NULL
      AND psf.fee_code = v.fee_code
);

-- -----------------------------------------------------------------------------
-- 4. Propagate Y1S1 template → every lifecycle slot (term_id NULL)
-- -----------------------------------------------------------------------------
INSERT INTO program_general_fees
    (program_id, term_id, year_level, semester_number,
     tuition_per_unit, lec_fee_per_unit, lab_fee_per_unit, comp_fee_per_unit, is_active)
SELECT
    slot.program_id, NULL, slot.year_level, slot.semester_number,
    src.tuition_per_unit, src.lec_fee_per_unit, src.lab_fee_per_unit, src.comp_fee_per_unit, 1
FROM tmp_program_slots slot
JOIN program_general_fees src
    ON src.program_id = slot.program_id
   AND src.year_level = @fee_tpl_year
   AND src.semester_number = @fee_tpl_sem
   AND src.term_id IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM program_general_fees pgf
    WHERE pgf.program_id = slot.program_id
      AND pgf.year_level = slot.year_level
      AND pgf.semester_number = slot.semester_number
      AND pgf.term_id IS NULL
);

INSERT INTO program_specific_fees
    (program_id, term_id, year_level, semester_number,
     fee_code, fee_name, fee_group, amount, is_required, is_active)
SELECT
    slot.program_id, NULL, slot.year_level, slot.semester_number,
    src.fee_code, src.fee_name, src.fee_group, src.amount, src.is_required, 1
FROM tmp_program_slots slot
JOIN program_specific_fees src
    ON src.program_id = slot.program_id
   AND src.year_level = @fee_tpl_year
   AND src.semester_number = @fee_tpl_sem
   AND src.term_id IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM program_specific_fees psf
    WHERE psf.program_id = slot.program_id
      AND psf.year_level = slot.year_level
      AND psf.semester_number = slot.semester_number
      AND psf.term_id IS NULL
      AND psf.fee_code = src.fee_code
);

-- -----------------------------------------------------------------------------
-- 5. Link fees to each academic term (term_id set) where sem/yl match slot
-- -----------------------------------------------------------------------------
INSERT INTO program_general_fees
    (program_id, term_id, year_level, semester_number,
     tuition_per_unit, lec_fee_per_unit, lab_fee_per_unit, comp_fee_per_unit, is_active)
SELECT
    slot.program_id, tp.term_id, slot.year_level, slot.semester_number,
    src.tuition_per_unit, src.lec_fee_per_unit, src.lab_fee_per_unit, src.comp_fee_per_unit, 1
FROM tmp_program_slots slot
JOIN tmp_term_parsed tp
    ON tp.year_level = slot.year_level
   AND tp.semester_number = slot.semester_number
JOIN program_general_fees src
    ON src.program_id = slot.program_id
   AND src.year_level = slot.year_level
   AND src.semester_number = slot.semester_number
   AND src.term_id IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM program_general_fees pgf
    WHERE pgf.program_id = slot.program_id
      AND pgf.year_level = slot.year_level
      AND pgf.semester_number = slot.semester_number
      AND pgf.term_id <=> tp.term_id
);

INSERT INTO program_specific_fees
    (program_id, term_id, year_level, semester_number,
     fee_code, fee_name, fee_group, amount, is_required, is_active)
SELECT
    slot.program_id, tp.term_id, slot.year_level, slot.semester_number,
    src.fee_code, src.fee_name, src.fee_group, src.amount, src.is_required, 1
FROM tmp_program_slots slot
JOIN tmp_term_parsed tp
    ON tp.year_level = slot.year_level
   AND tp.semester_number = slot.semester_number
JOIN program_specific_fees src
    ON src.program_id = slot.program_id
   AND src.year_level = slot.year_level
   AND src.semester_number = slot.semester_number
   AND src.term_id IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM program_specific_fees psf
    WHERE psf.program_id = slot.program_id
      AND psf.year_level = slot.year_level
      AND psf.semester_number = slot.semester_number
      AND psf.term_id <=> tp.term_id
      AND psf.fee_code = src.fee_code
);

-- -----------------------------------------------------------------------------
-- 6. Verification
-- -----------------------------------------------------------------------------
SELECT 'program_general_fees rows' AS metric, COUNT(*) AS cnt FROM program_general_fees
UNION ALL
SELECT 'program_specific_fees rows', COUNT(*) FROM program_specific_fees
UNION ALL
SELECT 'programs with MISC on Y2S1 (term NULL)', COUNT(DISTINCT psf.program_id)
FROM program_specific_fees psf
WHERE psf.fee_group = 'MISC' AND psf.year_level = 2 AND psf.semester_number = 1 AND psf.term_id IS NULL;

SELECT p.program_code, psf.year_level, psf.semester_number,
       COUNT(DISTINCT psf.fee_code) AS fee_lines,
       SUM(CASE WHEN psf.fee_group = 'MISC' THEN psf.amount ELSE 0 END) AS misc_total,
       SUM(CASE WHEN psf.fee_group = 'OTHER' THEN psf.amount ELSE 0 END) AS other_total
FROM program_specific_fees psf
JOIN programs p ON p.program_id = psf.program_id
WHERE psf.term_id IS NULL
GROUP BY p.program_code, psf.year_level, psf.semester_number
ORDER BY p.program_code, psf.year_level, psf.semester_number;

SELECT tp.term_code, tp.year_level, tp.semester_number, p.program_code,
       COUNT(DISTINCT psf.fee_code) AS term_linked_fee_lines
FROM tmp_term_parsed tp
JOIN program_specific_fees psf ON psf.term_id = tp.term_id
JOIN programs p ON p.program_id = psf.program_id
GROUP BY tp.term_code, tp.year_level, tp.semester_number, p.program_code
ORDER BY tp.term_code, p.program_code
LIMIT 40;

DROP TEMPORARY TABLE IF EXISTS tmp_term_parsed;
DROP TEMPORARY TABLE IF EXISTS tmp_program_slots;
DROP TEMPORARY TABLE IF EXISTS tmp_tuition_rate;
