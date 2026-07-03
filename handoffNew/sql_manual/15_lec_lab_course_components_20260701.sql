-- 2026-07-01
-- Registrar lecture/laboratory component support.
-- Safe to run on an existing MariaDB eacdb before starting the registrar app.

USE eacdb;

ALTER TABLE courses
    MODIFY COLUMN course_code VARCHAR(40) NOT NULL;

ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS component_type VARCHAR(10) NOT NULL DEFAULT 'SINGLE',
    ADD COLUMN IF NOT EXISTS course_family_code VARCHAR(40) NULL,
    ADD COLUMN IF NOT EXISTS parent_course_id INT NULL;

UPDATE courses
SET component_type = CASE
    WHEN COALESCE(lec_units, 0) > 0 AND COALESCE(lab_units, 0) = 0 THEN 'LEC'
    WHEN COALESCE(lab_units, 0) > 0 AND COALESCE(lec_units, 0) = 0 THEN 'LAB'
    ELSE COALESCE(NULLIF(component_type, ''), 'SINGLE')
END;

UPDATE courses
SET course_family_code = TRIM(
    REPLACE(REPLACE(REPLACE(REPLACE(course_code, '-LEC', ''), '-LAB', ''), ' LEC', ''), ' LAB', '')
)
WHERE course_family_code IS NULL OR course_family_code = '';

CREATE INDEX IF NOT EXISTS idx_courses_family ON courses (course_family_code);
CREATE INDEX IF NOT EXISTS idx_courses_component ON courses (component_type);

SELECT
    component_type,
    COUNT(*) AS course_count
FROM courses
GROUP BY component_type
ORDER BY component_type;
