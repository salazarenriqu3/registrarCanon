package com.iuims.registrar.academic;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

/** Validates schedule resource conflicts within one academic term. */
public final class ScheduleConflictValidator {

    private static final int DEFAULT_PREVIEW_LIMIT = 50;

    public record ConflictPreview(List<Map<String, Object>> conflicts, boolean truncated) {}
    public record RepairResult(
        int roomAssignmentsCleared,
        int duplicateRowsDeleted,
        int sectionOverlapRowsDeleted,
        int facultySectionsCleared,
        int facultyScheduleRowsCleared
    ) {
        public boolean changed() {
            return roomAssignmentsCleared > 0
                || duplicateRowsDeleted > 0
                || sectionOverlapRowsDeleted > 0
                || facultySectionsCleared > 0
                || facultyScheduleRowsCleared > 0;
        }
    }

    private final JdbcTemplate db;

    public ScheduleConflictValidator(JdbcTemplate db) {
        this.db = db;
    }

    public String validateNewSlot(int sectionId, Integer facultyId, Integer roomId,
                                  int dayOfWeek, LocalTime startTime, LocalTime endTime) {
        List<Map<String, Object>> sections = db.queryForList(
            "SELECT term_id, section_code FROM class_sections WHERE section_id = ?", sectionId);
        if (sections.isEmpty()) {
            return "Section not found.";
        }

        int termId = ((Number) sections.get(0).get("term_id")).intValue();
        if (roomId != null) {
            List<Map<String, Object>> rooms = db.queryForList(
                "SELECT room_id FROM rooms WHERE room_id = ? AND COALESCE(active_status, 1) = 1 LIMIT 1",
                roomId);
            if (rooms.isEmpty()) {
                return "Room not found or inactive.";
            }
        }
        if (facultyId != null) {
            List<Map<String, Object>> facultyRows = db.queryForList(
                "SELECT faculty_id FROM faculty WHERE faculty_id = ? AND COALESCE(active_status, 1) = 1 LIMIT 1",
                facultyId);
            if (facultyRows.isEmpty()) {
                return "Faculty not found or inactive.";
            }
        }
        String overlapSql =
            "SELECT cs.section_id, cs.section_code FROM class_schedules sch " +
            "JOIN class_sections cs ON cs.section_id = sch.section_id " +
            "WHERE cs.term_id = ? AND sch.day_of_week = ? " +
            "AND sch.start_time < ? AND sch.end_time > ? ";

        List<Map<String, Object>> sameSection = db.queryForList(
            overlapSql + "AND cs.section_id = ? LIMIT 1",
            termId, dayOfWeek, endTime, startTime, sectionId);
        if (!sameSection.isEmpty()) {
            return "Section " + sectionCode(sameSection.get(0)) + " already has an overlapping schedule.";
        }

        if (roomId != null) {
            List<Map<String, Object>> roomConflicts = db.queryForList(
                overlapSql + "AND sch.room_id = ? LIMIT 1",
                termId, dayOfWeek, endTime, startTime, roomId);
            if (!roomConflicts.isEmpty()) {
                return "Room is already in use by section " + sectionCode(roomConflicts.get(0)) + ".";
            }
        }

        if (facultyId != null) {
            List<Map<String, Object>> facultyConflicts = db.queryForList(
                overlapSql + "AND cs.faculty_id = ? LIMIT 1",
                termId, dayOfWeek, endTime, startTime, facultyId);
            if (!facultyConflicts.isEmpty()) {
                return "Faculty is already assigned to section " + sectionCode(facultyConflicts.get(0))
                    + " at that time.";
            }
        }
        return null;
    }

    public String validateFacultyAssignment(int sectionId, int facultyId) {
        List<Map<String, Object>> targetSection = db.queryForList(
            "SELECT cs.term_id, cs.section_code, " +
            "CASE WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "  THEN c.coordinator_equivalent_units ELSE c.credit_units END AS load_units " +
            "FROM class_sections cs " +
            "JOIN courses c ON c.course_id = cs.course_id " +
            "WHERE cs.section_id = ?",
            sectionId);
        if (targetSection.isEmpty()) {
            return "Section not found.";
        }

        List<Map<String, Object>> targetSlots = db.queryForList(
            "SELECT cs.term_id, sch.day_of_week, sch.start_time, sch.end_time " +
            "FROM class_sections cs JOIN class_schedules sch ON sch.section_id = cs.section_id " +
            "WHERE cs.section_id = ?", sectionId);

        for (Map<String, Object> slot : targetSlots) {
            List<Map<String, Object>> conflicts = db.queryForList(
                "SELECT other.section_code FROM class_schedules sch " +
                "JOIN class_sections other ON other.section_id = sch.section_id " +
                "WHERE other.term_id = ? AND other.faculty_id = ? AND other.section_id <> ? " +
                "AND sch.day_of_week = ? AND sch.start_time < ? AND sch.end_time > ? LIMIT 1",
                slot.get("term_id"), facultyId, sectionId, slot.get("day_of_week"),
                slot.get("end_time"), slot.get("start_time"));
            if (!conflicts.isEmpty()) {
                return "Faculty is already assigned to section " + sectionCode(conflicts.get(0))
                    + " during one of this section's schedule slots.";
            }
        }

        int termId = ((Number) targetSection.get(0).get("term_id")).intValue();
        int sectionLoadUnits = numberValue(targetSection.get(0).get("load_units"));
        return validateFacultyLoadCap(facultyId, termId, sectionLoadUnits, sectionId);
    }

    public List<Map<String, Object>> findExistingConflicts(int termId) {
        return findExistingConflictPreview(termId, DEFAULT_PREVIEW_LIMIT).conflicts();
    }

    public ConflictPreview findExistingConflictPreview(int termId, int maxResults) {
        int safeLimit = Math.max(2, maxResults);
        int roomLimit = (safeLimit + 1) / 2;
        int facultyLimit = safeLimit / 2;

        List<Map<String, Object>> roomConflicts = db.queryForList(
            "SELECT 'ROOM' AS conflict_type, a.section_code AS section_a, b.section_code AS section_b, " +
            "r.room_code AS resource_name, s1.day_of_week, s1.start_time, s1.end_time " +
            "FROM class_schedules s1 JOIN class_schedules s2 ON s1.schedule_id < s2.schedule_id " +
            "AND s1.day_of_week = s2.day_of_week AND s1.start_time < s2.end_time AND s1.end_time > s2.start_time " +
            "JOIN class_sections a ON a.section_id = s1.section_id " +
            "JOIN class_sections b ON b.section_id = s2.section_id AND b.term_id = a.term_id " +
            "JOIN rooms r ON r.room_id = s1.room_id " +
            "WHERE a.term_id = ? AND s1.room_id IS NOT NULL AND s1.room_id = s2.room_id " +
            "LIMIT ?",
            termId, roomLimit + 1);

        List<Map<String, Object>> facultyConflicts = db.queryForList(
            "SELECT 'FACULTY' AS conflict_type, a.section_code AS section_a, b.section_code AS section_b, " +
            "CONCAT(COALESCE(f.first_name, ''), ' ', COALESCE(f.last_name, '')) AS resource_name, " +
            "s1.day_of_week, s1.start_time, s1.end_time " +
            "FROM class_schedules s1 JOIN class_schedules s2 ON s1.schedule_id < s2.schedule_id " +
            "AND s1.day_of_week = s2.day_of_week AND s1.start_time < s2.end_time AND s1.end_time > s2.start_time " +
            "JOIN class_sections a ON a.section_id = s1.section_id " +
            "JOIN class_sections b ON b.section_id = s2.section_id AND b.term_id = a.term_id " +
            "JOIN faculty f ON f.faculty_id = a.faculty_id " +
            "WHERE a.term_id = ? AND a.faculty_id IS NOT NULL AND a.faculty_id = b.faculty_id " +
            "LIMIT ?",
            termId, facultyLimit + 1);

        boolean truncated = roomConflicts.size() > roomLimit || facultyConflicts.size() > facultyLimit;
        List<Map<String, Object>> preview = new ArrayList<>(safeLimit);
        preview.addAll(roomConflicts.subList(0, Math.min(roomLimit, roomConflicts.size())));
        preview.addAll(facultyConflicts.subList(0, Math.min(facultyLimit, facultyConflicts.size())));
        return new ConflictPreview(List.copyOf(preview), truncated);
    }

    public RepairResult repairExistingConflicts(int termId) {
        int roomAssignmentsCleared = db.update(
            "UPDATE class_schedules SET room_id = NULL " +
            "WHERE schedule_id IN (" +
            "  SELECT schedule_id FROM (" +
            "    SELECT DISTINCT s2.schedule_id AS schedule_id " +
            "    FROM class_schedules s1 " +
            "    JOIN class_schedules s2 ON s1.schedule_id < s2.schedule_id " +
            "     AND s1.day_of_week = s2.day_of_week " +
            "     AND s1.start_time < s2.end_time " +
            "     AND s1.end_time > s2.start_time " +
            "    JOIN class_sections a ON a.section_id = s1.section_id " +
            "    JOIN class_sections b ON b.section_id = s2.section_id AND b.term_id = a.term_id " +
            "    WHERE a.term_id = ? AND s1.room_id IS NOT NULL AND s1.room_id = s2.room_id" +
            "  ) conflicts" +
            ")",
            termId);

        int duplicateRowsDeleted = db.update(
            "DELETE FROM class_schedules " +
            "WHERE schedule_id IN (" +
            "  SELECT schedule_id FROM (" +
            "    SELECT dup.schedule_id AS schedule_id " +
            "    FROM class_schedules keep_row " +
            "    JOIN class_schedules dup ON keep_row.schedule_id < dup.schedule_id " +
            "     AND keep_row.section_id = dup.section_id " +
            "     AND keep_row.day_of_week = dup.day_of_week " +
            "     AND keep_row.start_time = dup.start_time " +
            "     AND keep_row.end_time = dup.end_time " +
            "    JOIN class_sections cs ON cs.section_id = keep_row.section_id " +
            "    WHERE cs.term_id = ?" +
            "  ) duplicate_rows" +
            ")",
            termId);

        int sectionOverlapRowsDeleted = db.update(
            "DELETE FROM class_schedules " +
            "WHERE schedule_id IN (" +
            "  SELECT schedule_id FROM (" +
            "    SELECT later_row.schedule_id AS schedule_id " +
            "    FROM class_schedules earlier_row " +
            "    JOIN class_schedules later_row ON earlier_row.schedule_id < later_row.schedule_id " +
            "     AND earlier_row.section_id = later_row.section_id " +
            "     AND earlier_row.day_of_week = later_row.day_of_week " +
            "     AND earlier_row.start_time < later_row.end_time " +
            "     AND earlier_row.end_time > later_row.start_time " +
            "    JOIN class_sections cs ON cs.section_id = earlier_row.section_id " +
            "    WHERE cs.term_id = ?" +
            "  ) overlap_rows" +
            ")",
            termId);

        int facultySectionsCleared = db.update(
            "UPDATE class_sections SET faculty_id = NULL " +
            "WHERE section_id IN (" +
            "  SELECT section_id FROM (" +
            "    SELECT DISTINCT b.section_id AS section_id " +
            "    FROM class_schedules s1 " +
            "    JOIN class_schedules s2 ON s1.schedule_id < s2.schedule_id " +
            "     AND s1.day_of_week = s2.day_of_week " +
            "     AND s1.start_time < s2.end_time " +
            "     AND s1.end_time > s2.start_time " +
            "    JOIN class_sections a ON a.section_id = s1.section_id " +
            "    JOIN class_sections b ON b.section_id = s2.section_id AND b.term_id = a.term_id " +
            "    WHERE a.term_id = ? AND a.faculty_id IS NOT NULL AND a.faculty_id = b.faculty_id" +
            "  ) conflicts" +
            ")",
            termId);

        int facultyScheduleRowsCleared = db.update(
            "UPDATE class_schedules SET faculty_id = (" +
            "  SELECT cs.faculty_id FROM class_sections cs WHERE cs.section_id = class_schedules.section_id" +
            ") WHERE section_id IN (" +
            "  SELECT section_id FROM class_sections WHERE term_id = ?" +
            ") AND COALESCE(faculty_id, -1) <> COALESCE((" +
            "  SELECT cs.faculty_id FROM class_sections cs WHERE cs.section_id = class_schedules.section_id" +
            "), -1)",
            termId);

        return new RepairResult(
            roomAssignmentsCleared,
            duplicateRowsDeleted,
            sectionOverlapRowsDeleted,
            facultySectionsCleared,
            facultyScheduleRowsCleared);
    }

    private String sectionCode(Map<String, Object> row) {
        Object value = row.get("section_code");
        return value == null ? "(unnamed)" : String.valueOf(value);
    }

    public String validateFacultyLoadCap(int facultyId, int termId, int additionalUnits, Integer excludedSectionId) {
        if (additionalUnits <= 0) {
            return null;
        }

        List<Map<String, Object>> facultyRows = db.queryForList(
            "SELECT faculty_id FROM faculty WHERE faculty_id = ? AND COALESCE(active_status, 1) = 1 LIMIT 1",
            facultyId);
        if (facultyRows.isEmpty()) {
            return "Faculty not found or inactive.";
        }

        StringBuilder currentLoadSql = new StringBuilder(
            "SELECT COALESCE(SUM(CASE " +
            "  WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "    THEN c.coordinator_equivalent_units ELSE c.credit_units END), 0) " +
            "FROM class_sections cs " +
            "JOIN courses c ON c.course_id = cs.course_id " +
            "WHERE cs.faculty_id = ? AND cs.term_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(facultyId);
        args.add(termId);
        if (excludedSectionId != null) {
            currentLoadSql.append(" AND cs.section_id <> ?");
            args.add(excludedSectionId);
        }

        Integer currentLoad = db.queryForObject(currentLoadSql.toString(), Integer.class, args.toArray());
        Integer maxUnits = db.queryForObject(
            "SELECT max_teaching_units FROM faculty WHERE faculty_id = ?",
            Integer.class, facultyId);

        int load = currentLoad == null ? 0 : currentLoad;
        int max = maxUnits == null ? 18 : maxUnits;
        if (load + additionalUnits > max) {
            return "Faculty assignment would exceed max load (" + load + "/" + max
                + " units; adding " + additionalUnits + " would exceed the cap).";
        }
        return null;
    }

    private int numberValue(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}
