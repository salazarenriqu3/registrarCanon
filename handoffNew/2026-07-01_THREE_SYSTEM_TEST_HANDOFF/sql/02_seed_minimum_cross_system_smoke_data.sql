USE eacdb;

SET @demo_reference := '3SYS-SMOKE-0001';
SET @demo_email := 'three.system.smoke@example.com';

INSERT INTO applicants (
    reference_number,
    applicant_status,
    enrollment_type,
    term_year,
    first_name,
    last_name,
    middle_name,
    last_school,
    course_taken,
    program1,
    program2,
    email,
    mobile,
    remarks,
    created_at
)
SELECT
    @demo_reference,
    'QUALIFIED FOR ENROLLMENT',
    'Regular',
    '2026-2027_1st',
    'Three',
    'System',
    'Smoke',
    'EAC Demo Senior High',
    'STEM',
    'BSIT',
    'BSCPE',
    @demo_email,
    '09170000001',
    'THREE SYSTEM SMOKE TEST APPLICANT',
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM applicants WHERE reference_number = @demo_reference
);

SET @demo_department_id := (
    SELECT department_id
    FROM departments
    ORDER BY department_id
    LIMIT 1
);

INSERT INTO courses (
    course_code,
    course_title,
    department_id,
    credit_units,
    lec_units,
    lab_units,
    component_type,
    course_family_code,
    active_status
)
SELECT
    'DEMOCP-LEC',
    'Demo Computer Programming',
    @demo_department_id,
    2,
    2,
    0,
    'LEC',
    'DEMOCP',
    1
WHERE @demo_department_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM courses WHERE course_code = 'DEMOCP-LEC');

INSERT INTO courses (
    course_code,
    course_title,
    department_id,
    credit_units,
    lec_units,
    lab_units,
    component_type,
    course_family_code,
    active_status
)
SELECT
    'DEMOCP-LAB',
    'Demo Computer Programming',
    @demo_department_id,
    1,
    0,
    1,
    'LAB',
    'DEMOCP',
    1
WHERE @demo_department_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM courses WHERE course_code = 'DEMOCP-LAB');

SET @demo_lec_id := (SELECT course_id FROM courses WHERE course_code = 'DEMOCP-LEC' LIMIT 1);

UPDATE courses
SET parent_course_id = @demo_lec_id
WHERE course_code IN ('DEMOCP-LEC', 'DEMOCP-LAB')
  AND @demo_lec_id IS NOT NULL;

SELECT
    'SEEDED_MINIMUM_CROSS_SYSTEM_SMOKE_DATA' AS status,
    @demo_reference AS applicant_reference,
    @demo_lec_id AS demo_course_family_parent_id;
