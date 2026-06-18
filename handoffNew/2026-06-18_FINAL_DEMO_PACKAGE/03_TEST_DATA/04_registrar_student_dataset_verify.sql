USE eacdb;

-- Read-only verification for the Registrar student UAT dataset.
-- Expected core students:
--   2026-1001         Maria Santos     ordinary enrolled-student baseline
--   SCH-UAT-ELIGIBLE  Sofia Scholar    27 completed units, eligible
--   SCH-UAT-LOWUNITS  Liam Low Units   24 completed units, ineligible

SELECT
    s.student_number,
    s.real_name,
    s.program_code,
    s.year_level,
    s.semester,
    s.admission_status,
    s.status,
    s.is_active
FROM students s
WHERE s.student_number IN ('2026-1001', 'SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS')
ORDER BY FIELD(s.student_number, '2026-1001', 'SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS');

SELECT
    s.student_number,
    COUNT(DISTINCT se.enlistment_id) AS committed_subjects,
    COALESCE(SUM(c.credit_units), 0) AS current_units
FROM students s
LEFT JOIN student_enlistments se
    ON se.student_id = s.student_number
   AND se.enlistment_status = 'COMMITTED'
LEFT JOIN courses c ON c.course_id = se.course_id
WHERE s.student_number IN ('2026-1001', 'SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS')
GROUP BY s.student_number
ORDER BY FIELD(s.student_number, '2026-1001', 'SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS');

SELECT
    s.student_number,
    COUNT(DISTINCT g.id) AS passed_subjects,
    COALESCE(SUM(CASE
        WHEN UPPER(COALESCE(g.status, '')) IN ('PASSED', 'APPROVED', 'FINALIZED')
          OR UPPER(COALESCE(g.remarks, '')) = 'PASSED'
          OR UPPER(COALESCE(g.registrar_final_remarks, '')) = 'PASSED'
        THEN c.credit_units ELSE 0 END), 0) AS completed_units
FROM students s
LEFT JOIN grades g ON g.student_id = s.student_number
LEFT JOIN courses c ON c.course_id = g.course_id
WHERE s.student_number IN ('2026-1001', 'SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS')
GROUP BY s.student_number
ORDER BY FIELD(s.student_number, '2026-1001', 'SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS');

SELECT
    sca.student_number,
    sca.curriculum_id,
    sca.program_code,
    sca.assignment_type,
    sca.is_current
FROM student_curriculum_assignments sca
WHERE sca.student_number IN ('2026-1001', 'SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS')
ORDER BY FIELD(sca.student_number, '2026-1001', 'SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS');

SELECT
    student_id,
    COUNT(*) AS ledger_entries,
    COALESCE(SUM(debit), 0) AS total_debit,
    COALESCE(SUM(credit), 0) AS total_credit
FROM student_ledger
WHERE student_id IN ('2026-1001', 'SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS')
GROUP BY student_id
ORDER BY FIELD(student_id, '2026-1001', 'SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS');

SELECT
    CASE
        WHEN (SELECT COUNT(*) FROM students WHERE student_number IN
              ('2026-1001', 'SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS')) = 3
         AND (SELECT COUNT(*) FROM student_enlistments WHERE student_id = '2026-1001'
              AND enlistment_status = 'COMMITTED') > 0
         AND (SELECT COUNT(*) FROM grades WHERE student_id = '2026-1001') > 0
         AND (SELECT COALESCE(SUM(c.credit_units), 0)
              FROM grades g JOIN courses c ON c.course_id = g.course_id
              WHERE g.student_id = 'SCH-UAT-ELIGIBLE') = 27
         AND (SELECT COALESCE(SUM(c.credit_units), 0)
              FROM grades g JOIN courses c ON c.course_id = g.course_id
              WHERE g.student_id = 'SCH-UAT-LOWUNITS') = 24
        THEN 'PASS: Registrar student dataset is ready'
        ELSE 'FAIL: Review the result sets above'
    END AS dataset_status;
