-- =============================================================================
-- SEED ALL CLASS SCHEDULES (no TBA)
-- Run on eacdb after class_sections exist.
-- Safe to re-run: replaces schedules for all sections; upserts rooms/faculty.
-- Final pass gives each section a concrete demo room and department-assigned
-- demo faculty member so room/faculty monitoring starts conflict-free.
-- =============================================================================
USE eacdb;
SET SQL_SAFE_UPDATES = 0;

SET @dept_id = COALESCE(
    (SELECT department_id FROM programs WHERE program_code = 'BSIT' LIMIT 1),
    (SELECT department_id FROM departments ORDER BY department_id LIMIT 1),
    1
);

-- ── 1. Demo rooms ───────────────────────────────────────────────────────────
INSERT INTO rooms (room_code, building_name, capacity, room_type, active_status)
SELECT v.room_code, v.building_name, v.capacity, v.room_type, 1
FROM (
    SELECT 'IT-301' AS room_code, 'IT Building' AS building_name, 40 AS capacity, 'Lecture' AS room_type
    UNION ALL SELECT 'IT-302', 'IT Building', 40, 'Lecture'
    UNION ALL SELECT 'IT-LAB1', 'IT Building', 30, 'Lab'
    UNION ALL SELECT 'IT-LAB2', 'IT Building', 30, 'Lab'
    UNION ALL SELECT 'ENG-201', 'Engineering Bldg', 45, 'Lecture'
    UNION ALL SELECT 'ENG-202', 'Engineering Bldg', 45, 'Lecture'
    UNION ALL SELECT 'ENG-LAB1', 'Engineering Bldg', 25, 'Lab'
    UNION ALL SELECT 'GEN-101', 'Main Building', 50, 'Lecture'
    UNION ALL SELECT 'GEN-102', 'Main Building', 50, 'Lecture'
    UNION ALL SELECT 'GYM-A', 'Gymnasium', 60, 'Activity'
) v
WHERE NOT EXISTS (SELECT 1 FROM rooms r WHERE r.room_code = v.room_code);

-- ── 2. Demo faculty (from existing Faculty logins) ──────────────────────────
UPDATE sys_users SET role = 'Faculty', is_active = 1, status = 'ACTIVE'
WHERE username IN ('prof', 'faculty');

INSERT INTO faculty (employee_number, first_name, last_name, email, department_id, employment_type, max_teaching_units, active_status)
SELECT v.emp, v.fn, v.ln, v.email, @dept_id, 'FULL_TIME', 24, 1
FROM (
    SELECT 'prof' AS emp, 'Professor' AS fn, 'Demo' AS ln, 'prof@school.edu.ph' AS email
    UNION ALL SELECT 'faculty', 'Faculty', 'Demo', 'faculty@school.edu.ph'
) v
WHERE NOT EXISTS (SELECT 1 FROM faculty f WHERE f.employee_number = v.emp);

-- ── 3. Wipe old schedules (full refresh) ────────────────────────────────────
DELETE FROM class_schedules;

-- ── 4. One conflict-free slot per section within each block (term + section_code)
INSERT INTO class_schedules (section_id, room_id, faculty_id, day_of_week, start_time, end_time, schedule_type, status)
SELECT
    slot.section_id,
    (SELECT MIN(room_id) FROM rooms) AS room_id,
    (SELECT MIN(faculty_id) FROM faculty) AS faculty_id,
    MOD(slot.slot_no - 1, 5) + 1 AS day_of_week,
    CASE FLOOR((slot.slot_no - 1) / 5) MOD 5
        WHEN 0 THEN '07:30:00'
        WHEN 1 THEN '09:00:00'
        WHEN 2 THEN '10:30:00'
        WHEN 3 THEN '13:00:00'
        ELSE '14:30:00'
    END AS start_time,
    CASE FLOOR((slot.slot_no - 1) / 5) MOD 5
        WHEN 0 THEN '09:00:00'
        WHEN 1 THEN '10:30:00'
        WHEN 2 THEN '12:00:00'
        WHEN 3 THEN '14:30:00'
        ELSE '16:00:00'
    END AS end_time,
    CASE WHEN COALESCE(slot.lab_units, 0) > 0 THEN 'Lab' ELSE 'Lecture' END AS schedule_type,
    'OPEN'
FROM (
    SELECT
        cs.section_id,
        cs.course_id,
        c.lab_units,
        ROW_NUMBER() OVER (
            PARTITION BY cs.term_id, cs.section_code
            ORDER BY c.course_code, cs.section_id
        ) AS slot_no
    FROM class_sections cs
    JOIN courses c ON c.course_id = cs.course_id
) slot;

-- ── 5. Second weekly meeting for 3-unit lecture courses (realistic contact hours)
INSERT INTO class_schedules (section_id, room_id, faculty_id, day_of_week, start_time, end_time, schedule_type, status)
SELECT
    cs.section_id,
    (SELECT MIN(room_id) FROM rooms) AS room_id,
    (SELECT MIN(faculty_id) FROM faculty) AS faculty_id,
    MOD(cs.section_id + c.course_id + COALESCE(cs.term_id, 0), 5) + 1 AS day_of_week,
    '17:30:00' AS start_time,
    '19:00:00' AS end_time,
    'Lecture',
    'OPEN'
FROM class_sections cs
JOIN courses c ON c.course_id = cs.course_id
WHERE COALESCE(c.lab_units, 0) = 0
  AND COALESCE(c.lec_units, c.credit_units, 0) >= 3
  AND (SELECT COUNT(*) FROM class_schedules s WHERE s.section_id = cs.section_id) = 1
  AND MOD(cs.section_id + c.course_id, 3) = 0;

-- ── 6. Keep sections open (faculty assignment: run seed_faculty_professors_and_grading.sql next)
-- Per-section demo rooms and faculty assignments.
-- This deliberately provisions enough concrete demo resources instead of
-- leaving TBA rows or overloading one sample room/faculty across the dataset.
INSERT INTO rooms (room_code, building_name, capacity, room_type, active_status)
SELECT
    CONCAT('DEMO-SEC-', cs.section_id),
    COALESCE(d.department_name, 'Academic Demo Building'),
    GREATEST(COALESCE(cs.max_capacity, 40), COALESCE(c.max_students, 0), 40),
    CASE WHEN COALESCE(c.lab_units, 0) > 0 THEN 'Lab' ELSE 'Lecture' END,
    1
FROM class_sections cs
JOIN courses c ON c.course_id = cs.course_id
LEFT JOIN departments d ON d.department_id = c.department_id
WHERE NOT EXISTS (
    SELECT 1 FROM rooms r WHERE r.room_code = CONCAT('DEMO-SEC-', cs.section_id)
);

INSERT INTO faculty (employee_number, first_name, last_name, email, department_id, employment_type, max_teaching_units, active_status)
SELECT
    CONCAT('demo.sec.', cs.section_id),
    'Demo',
    CONCAT('Faculty ', cs.section_id),
    CONCAT('demo.sec.', cs.section_id, '@eac.edu.ph'),
    COALESCE(c.department_id, @dept_id),
    'FULL_TIME',
    24,
    1
FROM class_sections cs
JOIN courses c ON c.course_id = cs.course_id
WHERE NOT EXISTS (
    SELECT 1 FROM faculty f WHERE f.employee_number = CONCAT('demo.sec.', cs.section_id)
);

UPDATE class_sections cs
JOIN courses c ON c.course_id = cs.course_id
JOIN faculty f ON f.employee_number = CONCAT('demo.sec.', cs.section_id)
SET cs.faculty_id = f.faculty_id,
    cs.section_status = CASE
        WHEN cs.section_status IN ('SUBMITTED', 'PENDING_APPROVAL') THEN cs.section_status
        ELSE 'Open'
    END;

UPDATE class_schedules sch
JOIN class_sections cs ON cs.section_id = sch.section_id
JOIN rooms r ON r.room_code = CONCAT('DEMO-SEC-', cs.section_id)
SET sch.room_id = r.room_id,
    sch.faculty_id = cs.faculty_id;

UPDATE class_sections cs
SET cs.section_status = 'Open'
WHERE cs.section_status IS NULL OR cs.section_status = 'Planning';

-- ── 7. Verify ───────────────────────────────────────────────────────────────
SELECT 'sections_total' AS metric, COUNT(*) AS val FROM class_sections
UNION ALL
SELECT 'sections_with_schedule', COUNT(DISTINCT cs.section_id)
FROM class_sections cs
JOIN class_schedules sch ON sch.section_id = cs.section_id
UNION ALL
SELECT 'sections_still_tba', COUNT(*)
FROM class_sections cs
WHERE NOT EXISTS (SELECT 1 FROM class_schedules sch WHERE sch.section_id = cs.section_id)
UNION ALL
SELECT 'schedule_rows', COUNT(*) FROM class_schedules;

SELECT 'missing_room_rows' AS metric, COUNT(*) AS val
FROM class_schedules
WHERE room_id IS NULL
UNION ALL
SELECT 'missing_faculty_rows', COUNT(*)
FROM class_schedules
WHERE faculty_id IS NULL
UNION ALL
SELECT 'sections_without_faculty', COUNT(*)
FROM class_sections
WHERE faculty_id IS NULL;

SELECT cs.section_code, c.course_code,
       GROUP_CONCAT(
           CONCAT(
               CASE sch.day_of_week WHEN 1 THEN 'MON' WHEN 2 THEN 'TUE' WHEN 3 THEN 'WED'
                   WHEN 4 THEN 'THU' WHEN 5 THEN 'FRI' WHEN 6 THEN 'SAT' ELSE 'SUN' END,
               ' ', TIME_FORMAT(sch.start_time, '%H:%i'), '-', TIME_FORMAT(sch.end_time, '%H:%i'),
               ' ', COALESCE(r.room_code, ''), ' ',
               COALESCE(CONCAT(f.first_name, ' ', f.last_name), '')
           )
           ORDER BY sch.day_of_week, sch.start_time SEPARATOR ' | '
       ) AS schedule
FROM class_sections cs
JOIN courses c ON c.course_id = cs.course_id
JOIN class_schedules sch ON sch.section_id = cs.section_id
LEFT JOIN rooms r ON r.room_id = sch.room_id
LEFT JOIN faculty f ON f.faculty_id = sch.faculty_id
WHERE cs.section_code IN ('BSCPE-1-2-A', 'BSIT-1-2-A')
GROUP BY cs.section_code, c.course_code
ORDER BY cs.section_code, c.course_code
LIMIT 20;

SET SQL_SAFE_UPDATES = 1;
