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
        return null;
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

    private String sectionCode(Map<String, Object> row) {
        Object value = row.get("section_code");
        return value == null ? "(unnamed)" : String.valueOf(value);
    }
}
