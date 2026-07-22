-- 2026-07-01
-- Registrar legacy lecture/laboratory course migration.
--
-- Purpose:
--   Convert older mixed lecture/laboratory catalog rows into the split
--   component model used by the current registrar canon.
--
-- Behavior:
--   - Creates BASE-LEC and BASE-LAB component rows when they do not already exist.
--   - Archives the legacy mixed source row so it remains available for history.
--   - Re-homes curriculum and prerequisite links onto the component rows.
--
-- Notes:
--   - This script is safe to run repeatedly.
--   - It intentionally does not rewrite historical section/enlistment/grade rows.
--     Those records remain tied to the archived legacy row for audit continuity.

DROP TEMPORARY TABLE IF EXISTS tmp_legacy_lec_lab_split;
DROP TEMPORARY TABLE IF EXISTS tmp_legacy_curriculum_refs;
DROP TEMPORARY TABLE IF EXISTS tmp_legacy_prereq_refs;

CREATE TEMPORARY TABLE tmp_legacy_lec_lab_split (
    source_course_id INT PRIMARY KEY,
    source_course_code VARCHAR(40) NOT NULL,
    source_course_title VARCHAR(150) NOT NULL,
    source_department_id INT NOT NULL,
    source_total_units INT NOT NULL,
    source_lecture_units INT NOT NULL,
    source_lab_units INT NOT NULL,
    source_active_status TINYINT(1) NOT NULL,
    family_code VARCHAR(40) NOT NULL,
    lec_code VARCHAR(40) NOT NULL,
    lab_code VARCHAR(40) NOT NULL,
    lec_course_id INT NULL,
    lab_course_id INT NULL
) ENGINE=MEMORY;

INSERT INTO tmp_legacy_lec_lab_split (
    source_course_id,
    source_course_code,
    source_course_title,
    source_department_id,
    source_total_units,
    source_lecture_units,
    source_lab_units,
    source_active_status,
    family_code,
    lec_code,
    lab_code
)
SELECT
    c.course_id,
    c.course_code,
    c.course_title,
    COALESCE(c.department_id, (SELECT d.department_id FROM departments d ORDER BY d.department_id LIMIT 1)),
    c.credit_units,
    COALESCE(c.lec_units, 0),
    COALESCE(c.lab_units, 0),
    COALESCE(c.active_status, 1),
    TRIM(REPLACE(REPLACE(REPLACE(REPLACE(c.course_code, '-LEC', ''), '-LAB', ''), ' LEC', ''), ' LAB', '')),
    CONCAT(TRIM(REPLACE(REPLACE(REPLACE(REPLACE(c.course_code, '-LEC', ''), '-LAB', ''), ' LEC', ''), ' LAB', '')), '-LEC'),
    CONCAT(TRIM(REPLACE(REPLACE(REPLACE(REPLACE(c.course_code, '-LEC', ''), '-LAB', ''), ' LEC', ''), ' LAB', '')), '-LAB')
FROM courses c
WHERE COALESCE(c.lec_units, 0) > 0
  AND COALESCE(c.lab_units, 0) > 0
  AND COALESCE(c.component_type, 'SINGLE') NOT IN ('LEC', 'LAB');

CREATE TEMPORARY TABLE tmp_legacy_curriculum_refs AS
SELECT
    cc.curriculum_id,
    cc.year_level,
    cc.semester_number,
    cc.is_required,
    t.source_course_id,
    t.lec_code,
    t.lab_code
FROM curriculum_courses cc
JOIN tmp_legacy_lec_lab_split t ON t.source_course_id = cc.course_id;

CREATE TEMPORARY TABLE tmp_legacy_prereq_refs AS
SELECT
    cp.course_id AS owner_course_id,
    cp.prerequisite_course_id AS related_course_id,
    'COURSE' AS relation_side,
    t.source_course_id,
    t.lec_code,
    t.lab_code
FROM course_prerequisites cp
JOIN tmp_legacy_lec_lab_split t ON t.source_course_id = cp.course_id
UNION ALL
SELECT
    cp.course_id AS owner_course_id,
    cp.prerequisite_course_id AS related_course_id,
    'PREREQ' AS relation_side,
    t.source_course_id,
    t.lec_code,
    t.lab_code
FROM course_prerequisites cp
JOIN tmp_legacy_lec_lab_split t ON t.source_course_id = cp.prerequisite_course_id;

INSERT IGNORE INTO courses (
    course_code,
    course_title,
    department_id,
    credit_units,
    lec_units,
    lab_units,
    component_type,
    course_family_code,
    parent_course_id,
    description,
    active_status,
    onlist
)
SELECT
    t.lec_code,
    t.source_course_title,
    t.source_department_id,
    t.source_lecture_units,
    t.source_lecture_units,
    0,
    'LEC',
    t.family_code,
    NULL,
    CONCAT('Legacy mixed course split from ', t.source_course_code),
    t.source_active_status,
    t.source_active_status
FROM tmp_legacy_lec_lab_split t;

INSERT IGNORE INTO courses (
    course_code,
    course_title,
    department_id,
    credit_units,
    lec_units,
    lab_units,
    component_type,
    course_family_code,
    parent_course_id,
    description,
    active_status,
    onlist
)
SELECT
    t.lab_code,
    t.source_course_title,
    t.source_department_id,
    t.source_lab_units,
    0,
    t.source_lab_units,
    'LAB',
    t.family_code,
    NULL,
    CONCAT('Legacy mixed course split from ', t.source_course_code),
    t.source_active_status,
    t.source_active_status
FROM tmp_legacy_lec_lab_split t;

UPDATE tmp_legacy_lec_lab_split t
SET
    t.lec_course_id = (
        SELECT c.course_id
        FROM courses c
        WHERE c.course_code COLLATE utf8mb4_unicode_ci = t.lec_code COLLATE utf8mb4_unicode_ci
        LIMIT 1
    ),
    t.lab_course_id = (
        SELECT c.course_id
        FROM courses c
        WHERE c.course_code COLLATE utf8mb4_unicode_ci = t.lab_code COLLATE utf8mb4_unicode_ci
        LIMIT 1
    );

UPDATE courses c
JOIN tmp_legacy_lec_lab_split t
  ON c.course_id IN (t.lec_course_id, t.lab_course_id)
SET c.parent_course_id = t.lec_course_id;

UPDATE courses c
JOIN tmp_legacy_lec_lab_split t ON t.source_course_id = c.course_id
SET
    c.component_type = 'LEGACY',
    c.course_family_code = t.family_code,
    c.parent_course_id = NULL,
    c.active_status = 0,
    c.onlist = 0;

DELETE cc
FROM curriculum_courses cc
JOIN tmp_legacy_lec_lab_split t ON t.source_course_id = cc.course_id;

INSERT IGNORE INTO curriculum_courses (
    curriculum_id,
    course_id,
    year_level,
    semester_number,
    is_required
)
SELECT
    t.curriculum_id,
    s.lec_course_id,
    t.year_level,
    t.semester_number,
    t.is_required
FROM tmp_legacy_curriculum_refs t
JOIN tmp_legacy_lec_lab_split s ON s.source_course_id = t.source_course_id
WHERE s.lec_course_id IS NOT NULL;

INSERT IGNORE INTO curriculum_courses (
    curriculum_id,
    course_id,
    year_level,
    semester_number,
    is_required
)
SELECT
    t.curriculum_id,
    s.lab_course_id,
    t.year_level,
    t.semester_number,
    t.is_required
FROM tmp_legacy_curriculum_refs t
JOIN tmp_legacy_lec_lab_split s ON s.source_course_id = t.source_course_id
WHERE s.lab_course_id IS NOT NULL;

DELETE cp
FROM course_prerequisites cp
JOIN tmp_legacy_lec_lab_split t
  ON cp.course_id = t.source_course_id
  OR cp.prerequisite_course_id = t.source_course_id;

INSERT IGNORE INTO course_prerequisites (course_id, prerequisite_course_id)
SELECT
    t.lec_course_id,
    p.related_course_id
FROM tmp_legacy_lec_lab_split t
JOIN tmp_legacy_prereq_refs p
  ON p.source_course_id = t.source_course_id
 AND p.relation_side = 'COURSE'
WHERE t.lec_course_id IS NOT NULL;

INSERT IGNORE INTO course_prerequisites (course_id, prerequisite_course_id)
SELECT
    t.lab_course_id,
    p.related_course_id
FROM tmp_legacy_lec_lab_split t
JOIN tmp_legacy_prereq_refs p
  ON p.source_course_id = t.source_course_id
 AND p.relation_side = 'COURSE'
WHERE t.lab_course_id IS NOT NULL;

INSERT IGNORE INTO course_prerequisites (course_id, prerequisite_course_id)
SELECT
    p.owner_course_id,
    t.lec_course_id
FROM tmp_legacy_lec_lab_split t
JOIN tmp_legacy_prereq_refs p
  ON p.source_course_id = t.source_course_id
 AND p.relation_side = 'PREREQ'
WHERE t.lec_course_id IS NOT NULL;

INSERT IGNORE INTO course_prerequisites (course_id, prerequisite_course_id)
SELECT
    p.owner_course_id,
    t.lab_course_id
FROM tmp_legacy_lec_lab_split t
JOIN tmp_legacy_prereq_refs p
  ON p.source_course_id = t.source_course_id
 AND p.relation_side = 'PREREQ'
WHERE t.lab_course_id IS NOT NULL;

SELECT
    COUNT(*) AS legacy_mixed_courses_found,
    SUM(CASE WHEN lec_course_id IS NOT NULL THEN 1 ELSE 0 END) AS lec_components_ready,
    SUM(CASE WHEN lab_course_id IS NOT NULL THEN 1 ELSE 0 END) AS lab_components_ready
FROM tmp_legacy_lec_lab_split;
