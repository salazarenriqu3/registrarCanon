-- 2026-07-22
-- Registrar required/reference data seed.
--
-- Run manually against the selected registrar database after schema creation.
-- This script intentionally does not choose a database with USE <db_name>.

INSERT INTO system_settings (setting_key, setting_value)
VALUES
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
    ('SCHOLARSHIP_DISQUALIFY_FAILED', 'true')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO withdrawal_reasons (reason_code, reason_label, sort_order)
VALUES
    ('ACADEMIC_LOAD', 'Academic load adjustment', 10),
    ('CLASS_DROP', 'Class drop', 15),
    ('SCHEDULE_CONFLICT', 'Schedule conflict', 20),
    ('MEDICAL', 'Medical / health reason', 30),
    ('FINANCIAL', 'Financial reason', 40),
    ('SHIFTING', 'Shifting', 45),
    ('TRANSFER', 'Transfer / change of school', 50),
    ('OTHER', 'Other reason', 100)
ON DUPLICATE KEY UPDATE
    reason_label = VALUES(reason_label),
    sort_order = VALUES(sort_order),
    is_active = 1;

INSERT INTO enrollment_settings (setting_key, setting_value, description)
VALUES
    ('downpayment_amount', '3000', 'Fixed downpayment (legacy mirror)'),
    ('downpayment_percent', '0', 'Percent of assessment (legacy mirror)'),
    ('max_units_regular', '27', 'Legacy max units; assigned curriculum is authoritative in Registrar'),
    ('max_units_graduating_bonus', '6', 'Legacy graduating bonus; assigned curriculum policy is authoritative'),
    ('enrollment_session_minutes', '15', 'Session timeout'),
    ('drop_penalty_days_half', '7', '50% withdrawal charge after first week'),
    ('drop_penalty_days_full', '21', '100% withdrawal charge after three weeks'),
    ('drop_penalty_first_week_percent', '25', 'First-week withdrawal charge percent'),
    ('drop_penalty_half_percent', '50', 'Half withdrawal charge percent'),
    ('rle_hours_per_unit', '51', 'RLE hours per unit')
ON DUPLICATE KEY UPDATE
    setting_value = VALUES(setting_value),
    description = VALUES(description);

INSERT INTO term_installment_plan (term_id, installment_number, due_months_offset, installment_label)
SELECT NULL, 1, 1, '1st Installment'
WHERE NOT EXISTS (
    SELECT 1 FROM term_installment_plan WHERE term_id IS NULL AND installment_number = 1
);

INSERT INTO term_installment_plan (term_id, installment_number, due_months_offset, installment_label)
SELECT NULL, 2, 2, '2nd Installment'
WHERE NOT EXISTS (
    SELECT 1 FROM term_installment_plan WHERE term_id IS NULL AND installment_number = 2
);

INSERT INTO term_installment_plan (term_id, installment_number, due_months_offset, installment_label)
SELECT NULL, 3, 3, '3rd Installment'
WHERE NOT EXISTS (
    SELECT 1 FROM term_installment_plan WHERE term_id IS NULL AND installment_number = 3
);

INSERT INTO year_level_load_policies (year_level, minimum_units, maximum_units)
VALUES
    (1, 0, 27),
    (2, 0, 27),
    (3, 0, 27),
    (4, 0, 27)
ON DUPLICATE KEY UPDATE
    minimum_units = VALUES(minimum_units),
    maximum_units = VALUES(maximum_units);

INSERT INTO scholarship_types
    (classification, display_name, discount_mode, default_discount_percentage,
     default_scholarship_amount, is_internal, requires_id, is_active)
VALUES
    ('ACADEMIC', 'Academic Scholarship', 'FULL', 100.0, 0.0, 1, 1, 1)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    discount_mode = VALUES(discount_mode),
    default_discount_percentage = VALUES(default_discount_percentage),
    default_scholarship_amount = VALUES(default_scholarship_amount),
    is_internal = VALUES(is_internal),
    requires_id = VALUES(requires_id),
    is_active = VALUES(is_active);
