package com.iuims.registrar.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ScheduleConflictValidatorTest {

    private JdbcTemplate db;
    private ScheduleConflictValidator validator;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:schedule-conflicts;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(dataSource);
        db.execute("DROP ALL OBJECTS");
        db.execute("CREATE TABLE class_sections (section_id INT PRIMARY KEY, term_id INT, section_code VARCHAR(50), faculty_id INT)");
        db.execute("CREATE TABLE class_schedules (schedule_id INT AUTO_INCREMENT PRIMARY KEY, section_id INT, day_of_week INT, start_time TIME, end_time TIME, room_id INT, faculty_id INT)");
        db.execute("CREATE TABLE rooms (room_id INT PRIMARY KEY, room_code VARCHAR(50))");
        db.execute("CREATE TABLE faculty (faculty_id INT PRIMARY KEY, first_name VARCHAR(50), last_name VARCHAR(50))");
        validator = new ScheduleConflictValidator(db);

        db.update("INSERT INTO rooms VALUES (5, 'LAB-5')");
        db.update("INSERT INTO faculty VALUES (100, 'Ada', 'Lovelace')");
        db.update("INSERT INTO faculty VALUES (200, 'Grace', 'Hopper')");
        db.update("INSERT INTO class_sections VALUES (1, 10, 'BSIT-1-A', 100)");
        db.update("INSERT INTO class_sections VALUES (2, 10, 'BSIT-1-B', 200)");
        db.update("INSERT INTO class_sections VALUES (3, 20, 'BSIT-1-C', 100)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (1, 1, '09:00:00', '10:30:00', 5, 100)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (3, 1, '09:00:00', '10:30:00', 5, 100)");
    }

    @Test
    void rejectsRoomOverlapWithinSameTerm() {
        String result = validator.validateNewSlot(
            2, 200, 5, 1, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThat(result).isEqualTo("Room is already in use by section BSIT-1-A.");
    }

    @Test
    void rejectsFacultyOverlapWithinSameTerm() {
        String result = validator.validateNewSlot(
            2, 100, null, 1, LocalTime.of(9, 30), LocalTime.of(10, 0));

        assertThat(result).isEqualTo("Faculty is already assigned to section BSIT-1-A at that time.");
    }

    @Test
    void permitsAdjacentTimesAndTentativeRoom() {
        String result = validator.validateNewSlot(
            2, 200, null, 1, LocalTime.of(10, 30), LocalTime.of(12, 0));

        assertThat(result).isNull();
    }

    @Test
    void ignoresResourceUseFromAnotherTerm() {
        db.update("DELETE FROM class_schedules WHERE section_id = 1");

        String result = validator.validateNewSlot(
            2, 100, 5, 1, LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertThat(result).isNull();
    }

    @Test
    void rejectsFacultyAssignmentThatWouldConflict() {
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id) VALUES (2, 1, '10:00:00', '11:00:00', NULL)");

        String result = validator.validateFacultyAssignment(2, 100);

        assertThat(result).isEqualTo(
            "Faculty is already assigned to section BSIT-1-A during one of this section's schedule slots.");
    }

    @Test
    void reportsConflictsAlreadyStoredForTheTerm() {
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (2, 1, '10:00:00', '11:00:00', 5, 200)");

        List<Map<String, Object>> conflicts = validator.findExistingConflicts(10);

        assertThat(conflicts).extracting(row -> row.get("conflict_type")).containsExactly("ROOM");
        assertThat(conflicts.get(0).get("resource_name")).isEqualTo("LAB-5");
    }

    @Test
    void capsTheConflictPreviewBeforeItReachesThePage() {
        db.update("INSERT INTO class_sections VALUES (4, 10, 'BSIT-1-D', 100)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (2, 1, '09:30:00', '10:00:00', 5, 200)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (4, 1, '09:15:00', '10:15:00', 5, 100)");

        ScheduleConflictValidator.ConflictPreview preview =
            validator.findExistingConflictPreview(10, 2);

        assertThat(preview.conflicts()).hasSize(2);
        assertThat(preview.conflicts()).extracting(row -> row.get("conflict_type"))
            .containsExactly("ROOM", "FACULTY");
        assertThat(preview.truncated()).isTrue();
    }
}
