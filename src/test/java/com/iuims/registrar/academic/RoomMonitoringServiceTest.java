package com.iuims.registrar.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class RoomMonitoringServiceTest {

    private JdbcTemplate db;
    private RoomMonitoringService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:room-monitoring;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(dataSource);
        db.execute("DROP ALL OBJECTS");
        db.execute("CREATE TABLE rooms (room_id INT PRIMARY KEY, room_code VARCHAR(50), building_name VARCHAR(80), capacity INT, room_type VARCHAR(40), active_status INT DEFAULT 1)");
        db.execute("CREATE TABLE departments (department_id INT PRIMARY KEY, department_name VARCHAR(80))");
        db.execute("CREATE TABLE courses (course_id INT PRIMARY KEY, course_code VARCHAR(30), course_title VARCHAR(120), department_id INT)");
        db.execute("CREATE TABLE faculty (faculty_id INT PRIMARY KEY, first_name VARCHAR(50), last_name VARCHAR(50), active_status INT DEFAULT 1)");
        db.execute("CREATE TABLE class_sections (section_id INT PRIMARY KEY, term_id INT, section_code VARCHAR(50), faculty_id INT, course_id INT)");
        db.execute("CREATE TABLE class_schedules (schedule_id INT AUTO_INCREMENT PRIMARY KEY, section_id INT, day_of_week INT, start_time TIME, end_time TIME, room_id INT, faculty_id INT)");

        db.update("INSERT INTO rooms VALUES (1, 'IT-301', 'IT Building', 40, 'Lecture', 1)");
        db.update("INSERT INTO rooms VALUES (2, 'IT-LAB1', 'IT Building', 30, 'Lab', 1)");
        db.update("INSERT INTO rooms VALUES (3, 'GEN-101', 'Main Building', 50, 'Lecture', 1)");
        db.update("INSERT INTO departments VALUES (10, 'Computer Studies')");
        db.update("INSERT INTO courses VALUES (100, 'UDIT 11', 'Introduction to IT', 10)");
        db.update("INSERT INTO courses VALUES (101, 'UCP1 11', 'Computer Programming 1', 10)");
        db.update("INSERT INTO courses VALUES (102, 'GE 11', 'General Education', 10)");
        db.update("INSERT INTO faculty VALUES (200, 'Ada', 'Lovelace', 1)");
        db.update("INSERT INTO faculty VALUES (201, 'Grace', 'Hopper', 1)");
        db.update("INSERT INTO class_sections VALUES (300, 20, 'BSIT-1-A', 200, 100)");
        db.update("INSERT INTO class_sections VALUES (301, 20, 'BSIT-1-B', 201, 101)");
        db.update("INSERT INTO class_sections VALUES (302, 20, 'BSIT-1-C', NULL, 102)");
        db.update("INSERT INTO class_sections VALUES (303, 20, 'BSIT-1-D', 200, 102)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (300, 1, '09:00:00', '10:30:00', 1, 200)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (301, 1, '10:00:00', '11:30:00', 1, 201)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (302, 2, '13:00:00', '14:30:00', NULL, NULL)");

        service = new RoomMonitoringService(db);
    }

    @Test
    void summarizesRoomUsageConflictsAndIncompleteSchedules() {
        Map<String, Object> summary = service.summary(20);

        assertThat(summary.get("total_rooms")).isEqualTo(3);
        assertThat(summary.get("used_rooms")).isEqualTo(1L);
        assertThat(summary.get("conflict_rooms")).isEqualTo(1L);
        assertThat(summary.get("missing_room_rows")).isEqualTo(1);
        assertThat(summary.get("sections_without_schedule")).isEqualTo(1);
        assertThat(summary.get("sections_without_faculty")).isEqualTo(1);
    }

    @Test
    void listsRoomsWithConflictStatusAndUtilization() {
        List<Map<String, Object>> rooms = service.listRoomsForTerm(20, "IT", "IT Building", null);

        assertThat(rooms).hasSize(2);
        Map<String, Object> conflictRoom = rooms.stream()
            .filter(row -> row.get("room_code").equals("IT-301"))
            .findFirst()
            .orElseThrow();

        assertThat(conflictRoom.get("slot_count")).isEqualTo(2);
        assertThat(conflictRoom.get("conflict_count")).isEqualTo(1);
        assertThat(conflictRoom.get("monitor_status")).isEqualTo("CONFLICT");
        assertThat((Integer) conflictRoom.get("utilization_percent")).isGreaterThan(0);
    }

    @Test
    void listsSchedulesForASelectedRoom() {
        List<Map<String, Object>> schedules = service.listRoomSchedules(20, 1);

        assertThat(schedules).hasSize(2);
        assertThat(schedules.get(0).get("room_code")).isEqualTo("IT-301");
        assertThat(schedules.get(0).get("day_name")).isEqualTo("MON");
        assertThat(schedules.get(0).get("faculty_name")).isEqualTo("Ada Lovelace");
    }

    @Test
    void allRoomScheduleRowsExcludeMissingRoomAssignments() {
        List<Map<String, Object>> schedules = service.listRoomSchedules(20, null);

        assertThat(schedules).hasSize(2);
        assertThat(schedules).extracting(row -> row.get("room_code")).doesNotContain("No room");
    }

    @Test
    void exposesIncompleteScheduleExceptions() {
        List<Map<String, Object>> issues = service.incompleteSchedules(20);

        assertThat(issues).extracting(row -> row.get("issue"))
            .contains("MISSING_ROOM", "NO_SCHEDULE", "UNASSIGNED_FACULTY");
    }

    @Test
    void canCreateAdditionalRoomsForScheduling() {
        String result = service.createRoom("IT-303", "IT Building", 45, "Lecture", 1);

        assertThat(result).startsWith("SUCCESS:");
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM rooms WHERE room_code = 'IT-303'",
            Integer.class)).isEqualTo(1);
    }
}
