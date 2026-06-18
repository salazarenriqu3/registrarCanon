-- Copy exact fee rows from term 1120242025 into every other calendar term (2425 S2 through 2728 S2).
-- Ensures term transition / prep_future_ay does not hit empty fee tables.
-- Safe to re-run. Run after 02_materialize_term_fees.sql.
USE eacdb;

SET @source_code = '1120242025';

INSERT INTO program_fee_settings (
    program_id, term_id, year_level, semester_number,
    fee_tuition_per_unit, fee_lec_per_unit, fee_lab_per_unit, fee_comp_per_unit, fee_rle_per_unit,
    fee_misc_registration, fee_misc_library, fee_misc_medical, fee_misc_id, fee_misc_athletic,
    fee_misc_guidance, fee_misc_lms, fee_misc_insurance, fee_misc_cultural, fee_misc_av, fee_misc_energy,
    fee_other_id, fee_other_insurance, fee_other_comp, fee_other_dev,
    fee_other_late_enrollment, fee_other_add_drop, fee_other_installment, is_active
)
SELECT
    src.program_id, tgt.term_id, src.year_level, src.semester_number,
    src.fee_tuition_per_unit, src.fee_lec_per_unit, src.fee_lab_per_unit, src.fee_comp_per_unit, src.fee_rle_per_unit,
    src.fee_misc_registration, src.fee_misc_library, src.fee_misc_medical, src.fee_misc_id, src.fee_misc_athletic,
    src.fee_misc_guidance, src.fee_misc_lms, src.fee_misc_insurance, src.fee_misc_cultural, src.fee_misc_av, src.fee_misc_energy,
    src.fee_other_id, src.fee_other_insurance, src.fee_other_comp, src.fee_other_dev,
    src.fee_other_late_enrollment, src.fee_other_add_drop, src.fee_other_installment, 1
FROM program_fee_settings src
JOIN academic_terms src_t ON src_t.term_id = src.term_id AND src_t.term_code = @source_code
JOIN academic_terms tgt ON tgt.term_code REGEXP '^[12]1[0-9]{8}$' AND tgt.term_id <> src_t.term_id
WHERE src.is_active = 1
  AND (COALESCE(src.fee_tuition_per_unit, 0) > 0 OR COALESCE(src.fee_lec_per_unit, 0) > 0 OR COALESCE(src.fee_rle_per_unit, 0) > 0)
  AND NOT EXISTS (
      SELECT 1 FROM program_fee_settings exact
      WHERE exact.program_id = src.program_id AND exact.term_id = tgt.term_id
        AND exact.year_level = src.year_level AND exact.semester_number = src.semester_number AND exact.is_active = 1
  );

SELECT at.term_code,
       COUNT(pfs.fee_setting_id) AS fee_rows,
       SUM(CASE WHEN COALESCE(pfs.fee_tuition_per_unit,0)=0 AND COALESCE(pfs.fee_lec_per_unit,0)=0 AND COALESCE(pfs.fee_rle_per_unit,0)=0 THEN 1 ELSE 0 END) AS incomplete_primary
FROM academic_terms at
LEFT JOIN program_fee_settings pfs ON pfs.term_id = at.term_id AND pfs.is_active = 1
WHERE at.term_code REGEXP '^[12]1[0-9]{8}$'
GROUP BY at.term_id, at.term_code
ORDER BY at.term_code;
