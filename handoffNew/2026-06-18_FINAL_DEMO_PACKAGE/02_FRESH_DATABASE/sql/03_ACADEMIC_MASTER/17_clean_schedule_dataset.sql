-- =============================================================================
-- CLEAN ACTIVE-TERM SCHEDULE DATASET TO MATCH CURRENT HARDENING RULES
-- - Preserve existing active-term time slots where possible
-- - Remove duplicate / overlapping rows inside the same section
-- - Provision concrete demo rooms and department-matched demo faculty
-- - Backfill any active-term section that still has no schedule or faculty
-- - End with no active-term TBA rooms, no room conflicts, and no faculty gaps
-- Safe to re-run on the demo dataset.
-- =============================================================================
USE eacdb;
SET SQL_SAFE_UPDATES = 0;

SET @active_term := (
    SELECT term_id
    FROM academic_terms
    WHERE is_active = 1
    ORDER BY term_id
    LIMIT 1
);

-- 1. Remove exact duplicate schedule rows on the active term.
DELETE dup
FROM class_schedules keep_row
JOIN class_schedules dup
  ON keep_row.schedule_id < dup.schedule_id
 AND keep_row.section_id = dup.section_id
 AND keep_row.day_of_week <=> dup.day_of_week
 AND keep_row.start_time <=> dup.start_time
 AND keep_row.end_time <=> dup.end_time
JOIN class_sections cs ON cs.section_id = keep_row.section_id
WHERE cs.term_id = @active_term;

-- 2. Remove section-internal overlaps on the active term by keeping the earliest row.
DELETE later_row
FROM class_schedules earlier_row
JOIN class_schedules later_row
  ON earlier_row.schedule_id < later_row.schedule_id
 AND earlier_row.section_id = later_row.section_id
 AND earlier_row.day_of_week = later_row.day_of_week
 AND earlier_row.start_time < later_row.end_time
 AND earlier_row.end_time > later_row.start_time
JOIN class_sections cs ON cs.section_id = earlier_row.section_id
WHERE cs.term_id = @active_term;

-- 3. Provision one concrete demo room per active-term section when missing.
INSERT INTO rooms (room_code, building_name, capacity, room_type, active_status)
SELECT
    CONCAT('DEMO-SEC-', cs.section_id) AS room_code,
    COALESCE(d.department_name, 'Academic Demo Building') AS building_name,
    GREATEST(COALESCE(cs.max_capacity, 40), COALESCE(c.max_students, 0), 40) AS capacity,
    CASE WHEN COALESCE(c.lab_units, 0) > 0 THEN 'Lab' ELSE 'Lecture' END AS room_type,
    1 AS active_status
FROM class_sections cs
JOIN courses c ON c.course_id = cs.course_id
LEFT JOIN departments d ON d.department_id = c.department_id
LEFT JOIN rooms r ON r.room_code = CONCAT('DEMO-SEC-', cs.section_id)
WHERE cs.term_id = @active_term
  AND r.room_id IS NULL;

UPDATE rooms r
JOIN class_sections cs
  ON r.room_code = CONCAT('DEMO-SEC-', cs.section_id)
JOIN courses c ON c.course_id = cs.course_id
LEFT JOIN departments d ON d.department_id = c.department_id
SET r.building_name = COALESCE(d.department_name, r.building_name, 'Academic Demo Building'),
    r.capacity = GREATEST(COALESCE(cs.max_capacity, 40), COALESCE(c.max_students, 0), 40),
    r.room_type = CASE WHEN COALESCE(c.lab_units, 0) > 0 THEN 'Lab' ELSE 'Lecture' END,
    r.active_status = 1
WHERE cs.term_id = @active_term;

-- 4. Provision one department-matched demo faculty member per active-term section when missing.
INSERT INTO faculty (employee_number, first_name, last_name, email, department_id, employment_type, max_teaching_units, active_status)
SELECT
    CONCAT('demo.sec.', cs.section_id) AS employee_number,
    'Demo' AS first_name,
    CONCAT('Faculty ', cs.section_id) AS last_name,
    CONCAT('demo.sec.', cs.section_id, '@eac.edu.ph') AS email,
    COALESCE(c.department_id, p.department_id, 1) AS department_id,
    'FULL_TIME' AS employment_type,
    24 AS max_teaching_units,
    1 AS active_status
FROM class_sections cs
JOIN courses c ON c.course_id = cs.course_id
LEFT JOIN programs p ON p.program_code = SUBSTRING_INDEX(cs.section_code, '-', 1)
LEFT JOIN faculty f ON f.employee_number = CONCAT('demo.sec.', cs.section_id)
WHERE cs.term_id = @active_term
  AND f.faculty_id IS NULL;

UPDATE faculty f
JOIN class_sections cs
  ON f.employee_number = CONCAT('demo.sec.', cs.section_id)
JOIN courses c ON c.course_id = cs.course_id
LEFT JOIN programs p ON p.program_code = SUBSTRING_INDEX(cs.section_code, '-', 1)
SET f.department_id = COALESCE(c.department_id, p.department_id, f.department_id),
    f.employment_type = COALESCE(f.employment_type, 'FULL_TIME'),
    f.max_teaching_units = GREATEST(COALESCE(f.max_teaching_units, 0), 24),
    f.active_status = 1
WHERE cs.term_id = @active_term;

-- 5. Backfill faculty ownership for active-term sections that still have no faculty.
UPDATE class_sections cs
JOIN faculty f ON f.employee_number = CONCAT('demo.sec.', cs.section_id)
SET cs.faculty_id = f.faculty_id,
    cs.section_status = CASE
        WHEN cs.section_status IN ('SUBMITTED', 'PENDING_APPROVAL') THEN cs.section_status
        WHEN cs.section_status IS NULL OR cs.section_status = 'Planning' THEN 'Open'
        ELSE cs.section_status
    END
WHERE cs.term_id = @active_term
  AND cs.faculty_id IS NULL;

-- 6. Backfill schedule rows for active-term sections that still have none.
INSERT INTO class_schedules (section_id, room_id, faculty_id, day_of_week, start_time, end_time, schedule_type, status)
SELECT
    missing.section_id,
    r.room_id,
    f.faculty_id,
    MOD(missing.slot_no - 1, 5) + 1 AS day_of_week,
    CASE FLOOR((missing.slot_no - 1) / 5) MOD 5
        WHEN 0 THEN '07:30:00'
        WHEN 1 THEN '09:00:00'
        WHEN 2 THEN '10:30:00'
        WHEN 3 THEN '13:00:00'
        ELSE '14:30:00'
    END AS start_time,
    CASE FLOOR((missing.slot_no - 1) / 5) MOD 5
        WHEN 0 THEN '09:00:00'
        WHEN 1 THEN '10:30:00'
        WHEN 2 THEN '12:00:00'
        WHEN 3 THEN '14:30:00'
        ELSE '16:00:00'
    END AS end_time,
    CASE WHEN COALESCE(missing.lab_units, 0) > 0 THEN 'Lab' ELSE 'Lecture' END AS schedule_type,
    'OPEN' AS status
FROM (
    SELECT
        cs.section_id,
        c.lab_units,
        ROW_NUMBER() OVER (ORDER BY cs.section_code, c.course_code, cs.section_id) AS slot_no
    FROM class_sections cs
    JOIN courses c ON c.course_id = cs.course_id
    WHERE cs.term_id = @active_term
      AND NOT EXISTS (
          SELECT 1
          FROM class_schedules sch
          WHERE sch.section_id = cs.section_id
      )
) missing
JOIN rooms r ON r.room_code = CONCAT('DEMO-SEC-', missing.section_id)
JOIN faculty f ON f.employee_number = CONCAT('demo.sec.', missing.section_id);

-- 7. Stamp every active-term schedule row with its concrete room and section faculty.
UPDATE class_schedules sch
JOIN class_sections cs ON cs.section_id = sch.section_id
JOIN rooms r ON r.room_code = CONCAT('DEMO-SEC-', cs.section_id)
LEFT JOIN faculty f ON f.faculty_id = cs.faculty_id
SET sch.room_id = r.room_id,
    sch.faculty_id = COALESCE(f.faculty_id, sch.faculty_id),
    sch.status = COALESCE(NULLIF(sch.status, ''), 'OPEN')
WHERE cs.term_id = @active_term;

-- 8. Clear faculty assignments on inactive terms, then restamp schedule faculty from sections.
UPDATE class_sections cs
JOIN academic_terms at ON at.term_id = cs.term_id
SET cs.faculty_id = NULL
WHERE COALESCE(at.is_active, 0) = 0
  AND cs.faculty_id IS NOT NULL;

UPDATE class_schedules sch
JOIN class_sections cs ON cs.section_id = sch.section_id
SET sch.faculty_id = cs.faculty_id;

-- 9. Verification snapshot.
SELECT 'active_term_sections' AS metric, COUNT(*) AS val
FROM class_sections
WHERE term_id = @active_term
UNION ALL
SELECT 'scheduled_active_term_sections', COUNT(DISTINCT cs.section_id)
FROM class_sections cs
JOIN class_schedules sch ON sch.section_id = cs.section_id
WHERE cs.term_id = @active_term
UNION ALL
SELECT 'unscheduled_active_term_sections', COUNT(*)
FROM class_sections cs
WHERE cs.term_id = @active_term
  AND NOT EXISTS (SELECT 1 FROM class_schedules sch WHERE sch.section_id = cs.section_id)
UNION ALL
SELECT 'missing_room_rows_active_term', COUNT(*)
FROM class_schedules sch
JOIN class_sections cs ON cs.section_id = sch.section_id
WHERE cs.term_id = @active_term
  AND sch.room_id IS NULL
UNION ALL
SELECT 'missing_faculty_rows_active_term', COUNT(*)
FROM class_schedules sch
JOIN class_sections cs ON cs.section_id = sch.section_id
WHERE cs.term_id = @active_term
  AND sch.faculty_id IS NULL
UNION ALL
SELECT 'sections_without_faculty_active_term', COUNT(*)
FROM class_sections
WHERE term_id = @active_term
  AND faculty_id IS NULL;

SET SQL_SAFE_UPDATES = 1;
