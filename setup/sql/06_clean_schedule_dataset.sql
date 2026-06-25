-- =============================================================================
-- CLEAN SCHEDULE DATASET TO MATCH CURRENT HARDENING RULES
-- - Clear room assignments so seeded schedules become TBA instead of conflicting
-- - Remove duplicate or overlapping extra rows within the same section
-- - Clear inactive-term faculty assignments and resync schedule faculty
-- Safe to re-run on the demo dataset.
-- =============================================================================
USE eacdb;
SET SQL_SAFE_UPDATES = 0;

-- 1. Make seeded room assignments TBA unless rooms are assigned deliberately later.
UPDATE class_schedules
SET room_id = NULL
WHERE room_id IS NOT NULL;

-- 2. Remove exact duplicate schedule rows for the same section/day/time window.
DELETE dup
FROM class_schedules keep_row
JOIN class_schedules dup
  ON keep_row.schedule_id < dup.schedule_id
 AND keep_row.section_id = dup.section_id
 AND keep_row.day_of_week <=> dup.day_of_week
 AND keep_row.start_time <=> dup.start_time
 AND keep_row.end_time <=> dup.end_time;

-- 3. Remove remaining section-internal overlaps by keeping the earliest row.
DELETE later_row
FROM class_schedules earlier_row
JOIN class_schedules later_row
  ON earlier_row.schedule_id < later_row.schedule_id
 AND earlier_row.section_id = later_row.section_id
 AND earlier_row.day_of_week = later_row.day_of_week
 AND earlier_row.start_time < later_row.end_time
 AND earlier_row.end_time > later_row.start_time;

-- 4. Clear faculty assignments on inactive terms.
-- Active-term assignments are curated separately; other terms should stay
-- unassigned until the scheduler deliberately assigns faculty later.
UPDATE class_sections cs
JOIN academic_terms at ON at.term_id = cs.term_id
SET cs.faculty_id = NULL
WHERE COALESCE(at.is_active, 0) = 0
  AND cs.faculty_id IS NOT NULL;

-- 5. Mirror faculty assignment from class_sections into schedule rows.
UPDATE class_schedules sch
JOIN class_sections cs ON cs.section_id = sch.section_id
SET sch.faculty_id = cs.faculty_id;

-- 6. Verification snapshots.
SELECT 'room_conflicts_remaining' AS check_name, COUNT(*) AS cnt
FROM class_schedules s1
JOIN class_schedules s2
  ON s1.schedule_id < s2.schedule_id
 AND s1.day_of_week = s2.day_of_week
 AND s1.start_time < s2.end_time
 AND s1.end_time > s2.start_time
JOIN class_sections a ON a.section_id = s1.section_id
JOIN class_sections b ON b.section_id = s2.section_id AND b.term_id = a.term_id
WHERE s1.room_id IS NOT NULL
  AND s1.room_id = s2.room_id;

SELECT 'section_self_overlaps_remaining' AS check_name, COUNT(*) AS cnt
FROM class_schedules s1
JOIN class_schedules s2
  ON s1.schedule_id < s2.schedule_id
 AND s1.section_id = s2.section_id
 AND s1.day_of_week = s2.day_of_week
 AND s1.start_time < s2.end_time
 AND s1.end_time > s2.start_time;

SELECT 'assigned_schedule_rows' AS check_name, COUNT(*) AS cnt
FROM class_schedules
WHERE faculty_id IS NOT NULL;

SELECT 'inactive_term_assigned_sections_remaining' AS check_name, COUNT(*) AS cnt
FROM class_sections cs
JOIN academic_terms at ON at.term_id = cs.term_id
WHERE COALESCE(at.is_active, 0) = 0
  AND cs.faculty_id IS NOT NULL;

SELECT 'faculty_conflicts_remaining' AS check_name, COUNT(*) AS cnt
FROM class_schedules s1
JOIN class_schedules s2
  ON s1.schedule_id < s2.schedule_id
 AND s1.day_of_week = s2.day_of_week
 AND s1.start_time < s2.end_time
 AND s1.end_time > s2.start_time
JOIN class_sections a ON a.section_id = s1.section_id
JOIN class_sections b ON b.section_id = s2.section_id AND b.term_id = a.term_id
WHERE s1.faculty_id IS NOT NULL
  AND s1.faculty_id = s2.faculty_id;

SET SQL_SAFE_UPDATES = 1;
