USE eacdb;

SELECT
    'LEC_LAB_MIGRATION_HEALTH' AS check_name,
    (SELECT COUNT(*)
     FROM courses
     WHERE COALESCE(lec_units, 0) > 0
       AND COALESCE(lab_units, 0) > 0
       AND COALESCE(component_type, 'SINGLE') NOT IN ('LEC', 'LAB', 'LEGACY')
    ) AS legacy_mixed_rows_remaining,
    (SELECT COUNT(*)
     FROM courses
     WHERE component_type = 'LEGACY'
       AND COALESCE(lec_units, 0) > 0
       AND COALESCE(lab_units, 0) > 0
    ) AS archived_legacy_rows,
    (SELECT COUNT(*) FROM courses WHERE component_type = 'LEC') AS lec_rows,
    (SELECT COUNT(*) FROM courses WHERE component_type = 'LAB') AS lab_rows,
    (SELECT COUNT(DISTINCT course_family_code)
     FROM courses
     WHERE component_type IN ('LEC', 'LAB')
    ) AS split_family_count;

SELECT
    course_family_code,
    GROUP_CONCAT(CONCAT(course_code, ' [', component_type, ']') ORDER BY component_type, course_code SEPARATOR ', ') AS component_rows,
    SUM(credit_units) AS total_units
FROM courses
WHERE course_family_code IS NOT NULL
  AND component_type IN ('LEC', 'LAB')
GROUP BY course_family_code
HAVING SUM(CASE WHEN component_type = 'LEC' THEN 1 ELSE 0 END) > 0
   AND SUM(CASE WHEN component_type = 'LAB' THEN 1 ELSE 0 END) > 0
ORDER BY course_family_code;
