package com.iuims.registrar.service.academic;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RoomMonitoringService {

    public record RoomPage(List<Map<String, Object>> rows,
                           int page,
                           int pageSize,
                           int totalRows,
                           int totalPages) { }

    private static final int WEEKLY_MONITORING_MINUTES = 5 * 10 * 60;

    private final JdbcTemplate db;

    public RoomMonitoringService(JdbcTemplate db) {
        this.db = db;
    }

    public Map<String, Object> summary(int termId) {
        return summary(termId, null);
    }

    public Map<String, Object> summary(int termId, String programCode) {
        String selectedProgram = normalizeProgramCode(programCode);
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Number> incompletes = incompleteCounts(termId, selectedProgram);

        List<Object> roomArgs = new ArrayList<>();
        StringBuilder roomWhere = new StringBuilder(" WHERE r.active_status = 1 ");
        appendRoomProgramExists(roomWhere, roomArgs, selectedProgram, termId);
        Integer totalRooms = db.queryForObject(
            "SELECT COUNT(*) FROM rooms r" + roomWhere,
            Integer.class, roomArgs.toArray());
        List<Object> usedArgs = new ArrayList<>();
        usedArgs.add(termId);
        StringBuilder usedWhere = new StringBuilder("WHERE cs.term_id = ? AND sch.room_id IS NOT NULL ");
        appendSectionProgramFilter(usedWhere, usedArgs, selectedProgram, "bo", "cs");
        Integer usedRooms = db.queryForObject(
            "SELECT COUNT(DISTINCT sch.room_id) " +
                "FROM class_schedules sch " +
                "JOIN class_sections cs ON cs.section_id = sch.section_id " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                usedWhere,
            Integer.class, usedArgs.toArray());
        List<Object> conflictArgs = new ArrayList<>();
        conflictArgs.add(termId);
        conflictArgs.add(termId);
        StringBuilder conflictWhere = new StringBuilder("WHERE csa.term_id = ? AND csb.term_id = ? AND a.room_id IS NOT NULL ");
        appendConflictProgramFilter(conflictWhere, conflictArgs, selectedProgram);
        Integer conflictRooms = db.queryForObject(
            "SELECT COUNT(DISTINCT a.room_id) " +
                "FROM class_schedules a " +
                "JOIN class_schedules b ON a.schedule_id < b.schedule_id " +
                " AND a.room_id = b.room_id " +
                " AND a.day_of_week = b.day_of_week " +
                " AND a.start_time < b.end_time " +
                " AND b.start_time < a.end_time " +
                "JOIN class_sections csa ON csa.section_id = a.section_id " +
                "JOIN class_sections csb ON csb.section_id = b.section_id " +
                "LEFT JOIN block_offerings boa ON boa.block_id = csa.block_id " +
                "LEFT JOIN block_offerings bob ON bob.block_id = csb.block_id " +
                conflictWhere,
            Integer.class, conflictArgs.toArray());
        List<Object> slotArgs = new ArrayList<>();
        slotArgs.add(termId);
        StringBuilder slotWhere = new StringBuilder("WHERE cs.term_id = ? AND sch.room_id IS NOT NULL ");
        appendSectionProgramFilter(slotWhere, slotArgs, selectedProgram, "bo", "cs");
        Integer scheduledSlots = db.queryForObject(
            "SELECT COUNT(*) FROM class_schedules sch " +
                "JOIN class_sections cs ON cs.section_id = sch.section_id " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                slotWhere,
            Integer.class, slotArgs.toArray());

        out.put("total_rooms", totalRooms != null ? totalRooms : 0);
        out.put("used_rooms", usedRooms != null ? usedRooms : 0);
        out.put("unused_rooms", Math.max(0, (totalRooms != null ? totalRooms : 0) - (usedRooms != null ? usedRooms : 0)));
        out.put("conflict_rooms", conflictRooms != null ? conflictRooms : 0);
        out.put("scheduled_slots", scheduledSlots != null ? scheduledSlots : 0);
        out.put("missing_room_rows", incompletes.get("missing_room_rows"));
        out.put("sections_without_schedule", incompletes.get("sections_without_schedule"));
        out.put("sections_without_faculty", incompletes.get("sections_without_faculty"));
        return out;
    }

    public List<Map<String, Object>> listRoomsForTerm(int termId, String search, String building, String roomType) {
        List<Map<String, Object>> rooms = normalizedRows(db.queryForList(
            "SELECT room_id, room_code, building_name, capacity, room_type, active_status " +
                "FROM rooms WHERE active_status = 1 ORDER BY building_name, room_code"));
        Map<Integer, Map<String, Object>> byRoom = new LinkedHashMap<>();
        for (Map<String, Object> room : rooms) {
            int roomId = number(room.get("room_id"));
            Map<String, Object> row = new LinkedHashMap<>(room);
            row.put("slot_count", 0);
            row.put("section_count", 0);
            row.put("scheduled_minutes", 0);
            row.put("conflict_count", 0);
            row.put("utilization_percent", 0);
            row.put("monitor_status", "UNUSED");
            byRoom.put(roomId, row);
        }

        for (Map<String, Object> row : normalizedRows(db.queryForList(
            "SELECT sch.room_id, COUNT(*) AS slot_count, COUNT(DISTINCT sch.section_id) AS section_count " +
                "FROM class_schedules sch " +
                "JOIN class_sections cs ON cs.section_id = sch.section_id " +
                "WHERE cs.term_id = ? AND sch.room_id IS NOT NULL " +
                "GROUP BY sch.room_id",
            termId))) {
            Map<String, Object> room = byRoom.get(number(row.get("room_id")));
            if (room != null) {
                room.put("slot_count", number(row.get("slot_count")));
                room.put("section_count", number(row.get("section_count")));
            }
        }

        for (Map<String, Object> schedule : listRoomSchedules(termId, null)) {
            Object roomIdRaw = schedule.get("room_id");
            if (roomIdRaw == null) continue;
            Map<String, Object> room = byRoom.get(number(roomIdRaw));
            if (room != null) {
                int current = number(room.get("scheduled_minutes"));
                room.put("scheduled_minutes", current + durationMinutes(schedule.get("start_time"), schedule.get("end_time")));
            }
        }

        for (Map<String, Object> row : conflictCountsByRoom(termId)) {
            Map<String, Object> room = byRoom.get(number(row.get("room_id")));
            if (room != null) {
                room.put("conflict_count", number(row.get("conflict_count")));
            }
        }

        List<Map<String, Object>> filtered = new ArrayList<>();
        String q = normalize(search);
        String b = normalize(building);
        String t = normalize(roomType);
        for (Map<String, Object> row : byRoom.values()) {
            int scheduledMinutes = number(row.get("scheduled_minutes"));
            int utilization = WEEKLY_MONITORING_MINUTES == 0 ? 0
                : Math.min(100, Math.round((scheduledMinutes * 100f) / WEEKLY_MONITORING_MINUTES));
            row.put("utilization_percent", utilization);
            row.put("monitor_status", statusFor(row));

            if (!q.isEmpty() && !contains(row.get("room_code"), q) && !contains(row.get("building_name"), q)) continue;
            if (!b.isEmpty() && !normalize(row.get("building_name")).equals(b)) continue;
            if (!t.isEmpty() && !normalize(row.get("room_type")).equals(t)) continue;
            filtered.add(row);
        }
        return filtered;
    }

    public RoomPage listRoomsForTermPage(int termId,
                                          String search,
                                          String building,
                                          String roomType,
                                          int requestedPage,
                                          int requestedSize) {
        return listRoomsForTermPage(termId, search, building, roomType, null, requestedPage, requestedSize);
    }

    public RoomPage listRoomsForTermPage(int termId,
                                          String search,
                                          String building,
                                          String roomType,
                                          String programCode,
                                          int requestedPage,
                                          int requestedSize) {
        int pageSize = Math.min(100, Math.max(1, requestedSize));
        int page = Math.max(1, requestedPage);
        List<Object> args = new ArrayList<>();
        String selectedProgram = normalizeProgramCode(programCode);
        StringBuilder where = new StringBuilder(" WHERE r.active_status = 1 ");
        appendRoomProgramExists(where, args, selectedProgram, termId);
        String query = normalize(search);
        if (!query.isEmpty()) {
            where.append("AND (LOWER(r.room_code) LIKE ? OR LOWER(r.building_name) LIKE ?) ");
            args.add("%" + query + "%");
            args.add("%" + query + "%");
        }
        String normalizedBuilding = normalize(building);
        if (!normalizedBuilding.isEmpty()) {
            where.append("AND LOWER(r.building_name) = ? ");
            args.add(normalizedBuilding);
        }
        String normalizedType = normalize(roomType);
        if (!normalizedType.isEmpty()) {
            where.append("AND LOWER(r.room_type) = ? ");
            args.add(normalizedType);
        }
        Integer total = db.queryForObject("SELECT COUNT(*) FROM rooms r" + where, Integer.class, args.toArray());
        int totalRows = total != null ? total : 0;
        int totalPages = Math.max(1, (int) Math.ceil(totalRows / (double) pageSize));
        page = Math.min(page, totalPages);
        List<Object> statsArgs = new ArrayList<>();
        statsArgs.add(termId);
        StringBuilder statsWhere = new StringBuilder("WHERE cs.term_id = ? AND sch.room_id IS NOT NULL ");
        appendSectionProgramFilter(statsWhere, statsArgs, selectedProgram, "bo", "cs");
        List<Object> pageArgs = new ArrayList<>();
        pageArgs.addAll(statsArgs);
        pageArgs.addAll(args);
        pageArgs.add(pageSize);
        pageArgs.add((page - 1) * pageSize);
        List<Map<String, Object>> rows = normalizedRows(db.queryForList(
            "SELECT r.room_id, r.room_code, r.building_name, r.capacity, r.room_type, r.active_status, " +
                "COALESCE(stats.slot_count, 0) AS slot_count, COALESCE(stats.section_count, 0) AS section_count, " +
                "COALESCE(stats.scheduled_minutes, 0) AS scheduled_minutes " +
                "FROM rooms r LEFT JOIN (SELECT sch.room_id, COUNT(*) AS slot_count, COUNT(DISTINCT sch.section_id) AS section_count, " +
                "COALESCE(SUM(TIMESTAMPDIFF(MINUTE, sch.start_time, sch.end_time)), 0) AS scheduled_minutes " +
                "FROM class_schedules sch JOIN class_sections cs ON cs.section_id = sch.section_id " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                statsWhere +
                "GROUP BY sch.room_id) stats ON stats.room_id = r.room_id" +
                where + " ORDER BY r.building_name, r.room_code LIMIT ? OFFSET ?",
            pageArgs.toArray()));
        for (Map<String, Object> row : rows) {
            int utilization = WEEKLY_MONITORING_MINUTES == 0 ? 0 : Math.min(100,
                Math.round((number(row.get("scheduled_minutes")) * 100f) / WEEKLY_MONITORING_MINUTES));
            row.put("utilization_percent", utilization);
            row.put("conflict_count", 0);
            row.put("monitor_status", number(row.get("slot_count")) == 0 ? "UNUSED" : utilization >= 75 ? "BUSY" : "OK");
        }
        return new RoomPage(rows, page, pageSize, totalRows, totalPages);
    }

    public List<Map<String, Object>> listRoomSchedules(int termId, Integer roomId) {
        return listRoomSchedules(termId, roomId, null);
    }

    public List<Map<String, Object>> listRoomSchedules(int termId, Integer roomId, String programCode) {
        String selectedProgram = normalizeProgramCode(programCode);
        List<Object> params = new ArrayList<>();
        params.add(termId);
        StringBuilder sql = new StringBuilder(
            "SELECT sch.schedule_id, sch.section_id, sch.room_id, sch.day_of_week, sch.start_time, sch.end_time, " +
                "r.room_code, r.building_name, r.room_type, r.capacity, " +
                "cs.section_code, cs.faculty_id AS section_faculty_id, " +
                "c.course_code, c.course_title, d.department_name, " +
                "CONCAT(COALESCE(f.first_name,''), ' ', COALESCE(f.last_name,'')) AS faculty_name " +
                "FROM class_schedules sch " +
                "JOIN class_sections cs ON cs.section_id = sch.section_id " +
                "JOIN courses c ON c.course_id = cs.course_id " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                "LEFT JOIN departments d ON d.department_id = c.department_id " +
                "LEFT JOIN rooms r ON r.room_id = sch.room_id " +
                "LEFT JOIN faculty f ON f.faculty_id = COALESCE(sch.faculty_id, cs.faculty_id) " +
                "WHERE cs.term_id = ?");
        if (roomId != null && roomId > 0) {
            sql.append(" AND sch.room_id = ?");
            params.add(roomId);
        } else {
            sql.append(" AND sch.room_id IS NOT NULL");
        }
        appendSectionProgramFilter(sql, params, selectedProgram, "bo", "cs");
        sql.append(" ORDER BY r.room_code, sch.day_of_week, sch.start_time, cs.section_code, c.course_code");

        List<Map<String, Object>> rows = normalizedRows(db.queryForList(sql.toString(), params.toArray()));
        for (Map<String, Object> row : rows) {
            int day = number(row.get("day_of_week"));
            row.put("day_name", dayName(day));
            row.put("time_range", formatTime(row.get("start_time")) + " - " + formatTime(row.get("end_time")));
            row.put("faculty_name", blankToFallback(row.get("faculty_name"), "Unassigned"));
            row.put("room_code", blankToFallback(row.get("room_code"), "No room"));
        }
        return rows;
    }

    public List<Map<String, Object>> incompleteSchedules(int termId) {
        return incompleteSchedules(termId, null);
    }

    public List<Map<String, Object>> incompleteSchedules(int termId, String programCode) {
        String selectedProgram = normalizeProgramCode(programCode);
        List<Object> firstArgs = new ArrayList<>();
        firstArgs.add(termId);
        StringBuilder firstWhere = new StringBuilder("WHERE cs.term_id = ? ");
        appendSectionProgramFilter(firstWhere, firstArgs, selectedProgram, "bo", "cs");
        List<Object> secondArgs = new ArrayList<>();
        secondArgs.add(termId);
        StringBuilder secondWhere = new StringBuilder("WHERE cs.term_id = ? AND sch.room_id IS NULL ");
        appendSectionProgramFilter(secondWhere, secondArgs, selectedProgram, "bo", "cs");
        List<Object> thirdArgs = new ArrayList<>();
        thirdArgs.add(termId);
        StringBuilder thirdWhere = new StringBuilder("WHERE cs.term_id = ? AND cs.faculty_id IS NULL ");
        appendSectionProgramFilter(thirdWhere, thirdArgs, selectedProgram, "bo", "cs");
        List<Object> args = new ArrayList<>();
        args.addAll(firstArgs);
        args.addAll(secondArgs);
        args.addAll(thirdArgs);
        String sql =
            "SELECT cs.section_id, cs.section_code, c.course_code, c.course_title, 'NO_SCHEDULE' AS issue " +
                "FROM class_sections cs JOIN courses c ON c.course_id = cs.course_id " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                firstWhere + "AND NOT EXISTS (SELECT 1 FROM class_schedules sch WHERE sch.section_id = cs.section_id) " +
            "UNION ALL " +
            "SELECT cs.section_id, cs.section_code, c.course_code, c.course_title, 'MISSING_ROOM' AS issue " +
                "FROM class_schedules sch JOIN class_sections cs ON cs.section_id = sch.section_id JOIN courses c ON c.course_id = cs.course_id " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                secondWhere +
            "UNION ALL " +
            "SELECT cs.section_id, cs.section_code, c.course_code, c.course_title, 'UNASSIGNED_FACULTY' AS issue " +
                "FROM class_sections cs JOIN courses c ON c.course_id = cs.course_id " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                thirdWhere +
            "ORDER BY section_code, course_code, issue";
        return normalizedRows(db.queryForList(sql, args.toArray()));
    }

    public List<Map<String, Object>> listProgramsForFilter(int termId) {
        return db.queryForList(
            "SELECT DISTINCT UPPER(COALESCE(bo.program_code, SUBSTRING_INDEX(cs.section_code, '-', 1))) AS program_code, " +
                "COALESCE(p.program_name, UPPER(COALESCE(bo.program_code, SUBSTRING_INDEX(cs.section_code, '-', 1)))) AS program_name " +
                "FROM class_sections cs " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                "LEFT JOIN programs p ON UPPER(p.program_code) = UPPER(COALESCE(bo.program_code, SUBSTRING_INDEX(cs.section_code, '-', 1))) " +
                "WHERE cs.term_id = ? " +
                "AND COALESCE(bo.program_code, SUBSTRING_INDEX(cs.section_code, '-', 1)) IS NOT NULL " +
                "AND TRIM(COALESCE(bo.program_code, SUBSTRING_INDEX(cs.section_code, '-', 1))) <> '' " +
                "ORDER BY program_code",
            termId);
    }

    public List<String> buildings() {
        return db.queryForList(
            "SELECT DISTINCT building_name FROM rooms WHERE active_status = 1 AND building_name IS NOT NULL ORDER BY building_name",
            String.class);
    }

    public List<String> roomTypes() {
        return db.queryForList(
            "SELECT DISTINCT room_type FROM rooms WHERE active_status = 1 AND room_type IS NOT NULL ORDER BY room_type",
            String.class);
    }

    public String createRoom(String roomCode, String buildingName, Integer capacity, String roomType, Integer activeStatus) {
        try {
            String code = roomCode != null ? roomCode.trim() : "";
            String building = buildingName != null ? buildingName.trim() : "";
            String type = roomType != null ? roomType.trim() : "";
            int cap = capacity != null ? capacity.intValue() : 0;
            int active = activeStatus != null && activeStatus > 0 ? 1 : 0;

            if (code.isBlank()) {
                return "ERROR: Room code is required.";
            }
            if (building.isBlank()) {
                return "ERROR: Building name is required.";
            }
            if (cap <= 0) {
                return "ERROR: Capacity must be greater than zero.";
            }
            if (db.queryForObject("SELECT COUNT(*) FROM rooms WHERE room_code = ?", Integer.class, code) > 0) {
                return "ERROR: Room code already exists.";
            }
            Integer nextId = db.queryForObject("SELECT COALESCE(MAX(room_id), 0) + 1 FROM rooms", Integer.class);
            db.update(
                "INSERT INTO rooms (room_id, room_code, building_name, capacity, room_type, active_status) VALUES (?, ?, ?, ?, ?, ?)",
                nextId, code, building, cap, type.isBlank() ? "Lecture" : type, active);
            return "SUCCESS: Room " + code + " added.";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private Map<String, Number> incompleteCounts(int termId, String programCode) {
        Map<String, Number> out = new HashMap<>();
        List<Object> missingArgs = new ArrayList<>();
        missingArgs.add(termId);
        StringBuilder missingWhere = new StringBuilder("WHERE cs.term_id = ? AND sch.room_id IS NULL ");
        appendSectionProgramFilter(missingWhere, missingArgs, programCode, "bo", "cs");
        List<Object> unscheduledArgs = new ArrayList<>();
        unscheduledArgs.add(termId);
        StringBuilder unscheduledWhere = new StringBuilder("WHERE cs.term_id = ? ");
        appendSectionProgramFilter(unscheduledWhere, unscheduledArgs, programCode, "bo", "cs");
        List<Object> facultyArgs = new ArrayList<>();
        facultyArgs.add(termId);
        StringBuilder facultyWhere = new StringBuilder("WHERE cs.term_id = ? AND cs.faculty_id IS NULL ");
        appendSectionProgramFilter(facultyWhere, facultyArgs, programCode, "bo", "cs");
        out.put("missing_room_rows", db.queryForObject(
            "SELECT COUNT(*) FROM class_schedules sch JOIN class_sections cs ON cs.section_id = sch.section_id " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                missingWhere, Integer.class, missingArgs.toArray()));
        out.put("sections_without_schedule", db.queryForObject(
            "SELECT COUNT(*) FROM class_sections cs " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                unscheduledWhere +
                "AND NOT EXISTS (SELECT 1 FROM class_schedules sch WHERE sch.section_id = cs.section_id)",
            Integer.class, unscheduledArgs.toArray()));
        out.put("sections_without_faculty", db.queryForObject(
            "SELECT COUNT(*) FROM class_sections cs " +
                "LEFT JOIN block_offerings bo ON bo.block_id = cs.block_id " +
                facultyWhere,
            Integer.class, facultyArgs.toArray()));
        return out;
    }

    private List<Map<String, Object>> conflictCountsByRoom(int termId) {
        return normalizedRows(db.queryForList(
            "SELECT a.room_id, COUNT(*) AS conflict_count " +
                "FROM class_schedules a " +
                "JOIN class_schedules b ON a.schedule_id < b.schedule_id " +
                " AND a.room_id = b.room_id " +
                " AND a.day_of_week = b.day_of_week " +
                " AND a.start_time < b.end_time " +
                " AND b.start_time < a.end_time " +
                "JOIN class_sections csa ON csa.section_id = a.section_id " +
                "JOIN class_sections csb ON csb.section_id = b.section_id " +
                "WHERE csa.term_id = ? AND csb.term_id = ? AND a.room_id IS NOT NULL " +
                "GROUP BY a.room_id",
            termId, termId));
    }

    private void appendRoomProgramExists(StringBuilder where, List<Object> args, String programCode, int termId) {
        String selectedProgram = normalizeProgramCode(programCode);
        if (selectedProgram.isBlank()) return;
        where.append("AND EXISTS (")
            .append("SELECT 1 FROM class_schedules schp ")
            .append("JOIN class_sections csp ON csp.section_id = schp.section_id ")
            .append("LEFT JOIN block_offerings bop ON bop.block_id = csp.block_id ")
            .append("WHERE schp.room_id = r.room_id AND csp.term_id = ? ")
            .append("AND (UPPER(bop.program_code) = ? OR UPPER(csp.section_code) LIKE ?)")
            .append(") ");
        args.add(termId);
        args.add(selectedProgram);
        args.add(selectedProgram + "-%");
    }

    private void appendSectionProgramFilter(StringBuilder where, List<Object> args, String programCode, String blockAlias, String sectionAlias) {
        String selectedProgram = normalizeProgramCode(programCode);
        if (selectedProgram.isBlank()) return;
        where.append(" AND (UPPER(")
            .append(blockAlias)
            .append(".program_code) = ? OR UPPER(")
            .append(sectionAlias)
            .append(".section_code) LIKE ?) ");
        args.add(selectedProgram);
        args.add(selectedProgram + "-%");
    }

    private void appendConflictProgramFilter(StringBuilder where, List<Object> args, String programCode) {
        String selectedProgram = normalizeProgramCode(programCode);
        if (selectedProgram.isBlank()) return;
        where.append(" AND (")
            .append("(UPPER(boa.program_code) = ? OR UPPER(csa.section_code) LIKE ?) ")
            .append("OR (UPPER(bob.program_code) = ? OR UPPER(csb.section_code) LIKE ?)")
            .append(") ");
        args.add(selectedProgram);
        args.add(selectedProgram + "-%");
        args.add(selectedProgram);
        args.add(selectedProgram + "-%");
    }

    private String normalizeProgramCode(String programCode) {
        return programCode != null ? programCode.trim().toUpperCase(Locale.ROOT) : "";
    }

    private List<Map<String, Object>> normalizedRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                copy.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            }
            normalized.add(copy);
        }
        return normalized;
    }

    private String statusFor(Map<String, Object> row) {
        if (number(row.get("conflict_count")) > 0) return "CONFLICT";
        if (number(row.get("slot_count")) == 0) return "UNUSED";
        if (number(row.get("utilization_percent")) >= 75) return "BUSY";
        return "OK";
    }

    private int durationMinutes(Object start, Object end) {
        try {
            LocalTime s = parseTime(start);
            LocalTime e = parseTime(end);
            if (s == null || e == null || !s.isBefore(e)) return 0;
            return (int) java.time.Duration.between(s, e).toMinutes();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private LocalTime parseTime(Object raw) {
        if (raw == null) return null;
        if (raw instanceof LocalTime time) return time;
        return LocalTime.parse(raw.toString().substring(0, 8));
    }

    private String formatTime(Object raw) {
        LocalTime time = parseTime(raw);
        if (time == null) return "TBD";
        int hour = time.getHour();
        int displayHour = hour % 12 == 0 ? 12 : hour % 12;
        return String.format("%d:%02d %s", displayHour, time.getMinute(), hour < 12 ? "AM" : "PM");
    }

    private String dayName(int day) {
        return switch (day) {
            case 1 -> "MON";
            case 2 -> "TUE";
            case 3 -> "WED";
            case 4 -> "THU";
            case 5 -> "FRI";
            case 6 -> "SAT";
            case 7 -> "SUN";
            default -> "TBD";
        };
    }

    private boolean contains(Object value, String needle) {
        return normalize(value).contains(needle);
    }

    private String normalize(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
    }

    private String blankToFallback(Object value, String fallback) {
        String normalized = value == null ? "" : value.toString().trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private int number(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null || value.toString().isBlank()) return 0;
        return Integer.parseInt(value.toString());
    }
}
