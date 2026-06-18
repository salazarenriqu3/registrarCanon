USE eacdb;

SET @eligible_user_id := (SELECT user_id FROM sys_users WHERE username = 'SCH-UAT-ELIGIBLE' LIMIT 1);
SET @low_units_user_id := (SELECT user_id FROM sys_users WHERE username = 'SCH-UAT-LOWUNITS' LIMIT 1);

SET @cleanup_review_sql := IF(
    EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scholarship_review_workflow'
    ),
    "DELETE FROM scholarship_review_workflow WHERE student_number IN ('SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS')",
    "SELECT 'scholarship_review_workflow not present; skipped' AS cleanup_note"
);
PREPARE cleanup_review_statement FROM @cleanup_review_sql;
EXECUTE cleanup_review_statement;
DEALLOCATE PREPARE cleanup_review_statement;

DELETE FROM student_scholarships
WHERE user_id IN (@eligible_user_id, @low_units_user_id);

DELETE FROM student_enlistments WHERE student_id IN ('SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS');
DELETE FROM grades WHERE student_id IN ('SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS');
DELETE FROM student_curriculum_assignments WHERE student_number IN ('SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS');
DELETE FROM student_ledger WHERE student_id IN ('SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS');
DELETE FROM students WHERE student_number IN ('SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS');
DELETE FROM sys_users WHERE username IN ('SCH-UAT-ELIGIBLE', 'SCH-UAT-LOWUNITS');

DELETE cs
FROM class_sections cs
JOIN courses c ON c.course_id = cs.course_id
WHERE cs.section_code = CONCAT('SCH-DEMO-', c.course_code)
  AND c.course_code IN ('SCH101','SCH102','SCH103','SCH104','SCH105','SCH106','SCH107','SCH108','SCH109');

DELETE FROM courses
WHERE course_code IN ('SCH101','SCH102','SCH103','SCH104','SCH105','SCH106','SCH107','SCH108','SCH109');

DELETE FROM departments WHERE department_code = 'SCHUAT';

SELECT 'Scholarship demo records removed' AS result;
