-- Activate A.Y. 2024-2025 — 1st Semester as the demo/open term.
-- Run after calendar terms + section seeds. Safe to re-run.
USE eacdb;

UPDATE academic_terms SET status = 'INACTIVE', is_active = 0
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
    semester_number = VALUES(semester_number);

UPDATE academic_terms SET is_active = 0, status = 'INACTIVE';
UPDATE academic_terms SET is_active = 1, status = 'ACTIVE' WHERE term_code = '1120242025';

UPDATE system_settings SET setting_value = '1120242025' WHERE setting_key = 'CURRENT_ACADEMIC_TERM';
INSERT INTO system_settings (setting_key, setting_value)
VALUES ('CURRENT_ACADEMIC_TERM', '1120242025')
ON DUPLICATE KEY UPDATE setting_value = '1120242025';

SELECT 'ACTIVE TERM' AS check_name, term_id, term_code, term_name, is_active, status
FROM academic_terms WHERE is_active = 1;
