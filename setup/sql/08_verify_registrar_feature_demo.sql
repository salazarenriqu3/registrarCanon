USE eacdb;

SELECT student_number, real_name, program_code, year_level, semester, student_type,
       enrollment_status_type, admission_status, term_year
FROM students
WHERE student_number IN (
    '2026-1001',
    'ADDCLS-2026-001',
    'TTRNS-2026-001',
    'TSHFT-2026-001',
    'OVRPAY-2026-001',
    'SPRINT-DEMO-2026-001',
    'SCH-UAT-ELIGIBLE',
    'SCH-UAT-LOWUNITS'
)
ORDER BY student_number;

SELECT student_id, COUNT(*) AS committed_subjects, COALESCE(SUM(c.credit_units), 0) AS committed_units
FROM student_enlistments se
JOIN courses c ON c.course_id = se.course_id
WHERE se.student_id IN ('2026-1001', 'ADDCLS-2026-001', 'OVRPAY-2026-001', 'SPRINT-DEMO-2026-001')
  AND se.enlistment_status = 'COMMITTED'
GROUP BY student_id
ORDER BY student_id;

SELECT student_id, COUNT(*) AS history_rows,
       COALESCE(SUM(CASE
           WHEN UPPER(COALESCE(remarks, '')) = 'PASSED'
             OR UPPER(COALESCE(status, '')) IN ('APPROVED', 'FINALIZED', 'PASSED')
             OR UPPER(COALESCE(registrar_final_remarks, '')) = 'PASSED'
           THEN 1 ELSE 0 END), 0) AS passed_rows
FROM grades
WHERE student_id IN ('2026-1001', 'TTRNS-2026-001', 'TSHFT-2026-001')
GROUP BY student_id
ORDER BY student_id;

SELECT reference_number, applicant_status, application_status, program1,
       form138_path, good_moral_path, psa_birth_cert_path, id_picture_path, other_doc_path
FROM applicants
WHERE reference_number = 'DEMO-SANTOS-001';

SELECT ref_no, COUNT(*) AS log_count
FROM eac_application_logs
WHERE ref_no = 'DEMO-SANTOS-001'
GROUP BY ref_no;

SELECT student_number, COUNT(*) AS reg_form_events
FROM student_reg_form_events
WHERE student_number IN ('2026-1001', 'ADDCLS-2026-001', 'TTRNS-2026-001', 'TSHFT-2026-001', 'OVRPAY-2026-001')
GROUP BY student_number
ORDER BY student_number;

SELECT s.section_code, COUNT(*) AS schedule_rows,
       SUM(CASE WHEN cs.room_id IS NULL THEN 1 ELSE 0 END) AS roomless_rows
FROM class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
WHERE s.term_id = 1
  AND s.section_code IN ('BSIT-1-1-A', 'BSCPE-1-1-A')
GROUP BY s.section_code
ORDER BY s.section_code;

SELECT c.course_code, COUNT(*) AS schedule_rows,
       SUM(CASE WHEN cs.room_id IS NULL THEN 1 ELSE 0 END) AS roomless_rows
FROM class_schedules cs
JOIN class_sections s ON s.section_id = cs.section_id
JOIN courses c ON c.course_id = s.course_id
WHERE s.term_id = 1
  AND s.section_code = 'IRREG-A'
  AND c.course_code IN ('CC101', 'CC102', 'GE101', 'GE102', 'PE101', 'NSTP101', 'UCO1 11', 'UPR1 11')
GROUP BY c.course_code
ORDER BY c.course_code;

SELECT student_id, COALESCE(SUM(debit), 0) AS total_debit, COALESCE(SUM(credit), 0) AS total_credit
FROM student_ledger
WHERE student_id IN ('2026-1001', 'ADDCLS-2026-001', 'OVRPAY-2026-001', 'SPRINT-DEMO-2026-001')
GROUP BY student_id
ORDER BY student_id;

SELECT CASE
    WHEN (SELECT COUNT(*) FROM students WHERE student_number IN (
            '2026-1001', 'ADDCLS-2026-001', 'TTRNS-2026-001',
            'TSHFT-2026-001', 'OVRPAY-2026-001', 'SPRINT-DEMO-2026-001'
         )) = 6
     AND (SELECT COUNT(*) FROM applicants WHERE reference_number = 'DEMO-SANTOS-001'
          AND form138_path IS NOT NULL AND good_moral_path IS NOT NULL
          AND psa_birth_cert_path IS NOT NULL AND id_picture_path IS NOT NULL) = 1
     AND (SELECT COUNT(*) FROM student_enlistments WHERE student_id = '2026-1001'
          AND enlistment_status = 'COMMITTED') >= 5
     AND (SELECT COUNT(*) FROM student_enlistments WHERE student_id = 'ADDCLS-2026-001'
          AND enlistment_status = 'COMMITTED') >= 4
     AND (SELECT COUNT(*) FROM student_enlistments WHERE student_id = 'SPRINT-DEMO-2026-001'
          AND enlistment_status = 'COMMITTED') = 3
     AND (SELECT COUNT(*) FROM grades WHERE student_id IN ('2026-1001', 'TTRNS-2026-001', 'TSHFT-2026-001')) >= 6
     AND (SELECT COUNT(*) FROM student_reg_form_events WHERE student_number = '2026-1001') >= 3
     AND (SELECT COUNT(*) FROM eac_application_logs WHERE ref_no = 'DEMO-SANTOS-001') >= 5
     AND (SELECT COUNT(*) FROM class_schedules cs
          JOIN class_sections s ON s.section_id = cs.section_id
          WHERE s.term_id = 1 AND s.section_code IN ('BSIT-1-1-A', 'BSCPE-1-1-A') AND cs.room_id IS NULL) = 0
    THEN 'PASS: Full registrar feature demo dataset is ready'
    ELSE 'FAIL: Review the result sets above'
END AS dataset_status;
