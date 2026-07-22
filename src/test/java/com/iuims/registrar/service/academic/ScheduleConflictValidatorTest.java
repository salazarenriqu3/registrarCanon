package com.iuims.registrar.service.academic;

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
        db.execute("CREATE TABLE courses (course_id INT PRIMARY KEY, course_code VARCHAR(50), credit_units INT, is_coordinator_based INT DEFAULT 0, coordinator_equivalent_units INT NULL)");
        db.execute("CREATE TABLE class_sections (section_id INT PRIMARY KEY, term_id INT, section_code VARCHAR(50), faculty_id INT, course_id INT, block_id INT NULL)");
        db.execute("CREATE TABLE class_schedules (schedule_id INT AUTO_INCREMENT PRIMARY KEY, section_id INT, day_of_week INT, start_time TIME, end_time TIME, room_id INT, faculty_id INT)");
        db.execute("CREATE TABLE rooms (room_id INT PRIMARY KEY, room_code VARCHAR(50), active_status INT DEFAULT 1)");
        db.execute("CREATE TABLE faculty (faculty_id INT PRIMARY KEY, first_name VARCHAR(50), last_name VARCHAR(50), active_status INT DEFAULT 1, max_teaching_units INT)");
        validator = new ScheduleConflictValidator(db);

        db.update("INSERT INTO courses VALUES (11, 'AECO11', 3, 0, NULL)");
        db.update("INSERT INTO courses VALUES (12, 'ANS111', 3, 0, NULL)");
        db.update("INSERT INTO courses VALUES (13, 'MATH11', 3, 0, NULL)");
        db.update("INSERT INTO rooms VALUES (5, 'LAB-5', 1)");
        db.update("INSERT INTO faculty VALUES (100, 'Ada', 'Lovelace', 1, 18)");
        db.update("INSERT INTO faculty VALUES (200, 'Grace', 'Hopper', 1, 18)");
        db.update("INSERT INTO faculty VALUES (300, 'Alan', 'Turing', 1, 3)");
        db.update("INSERT INTO class_sections VALUES (1, 10, 'BSIT-1-A', 100, 11, NULL)");
        db.update("INSERT INTO class_sections VALUES (2, 10, 'BSIT-1-B', 200, 12, NULL)");
        db.update("INSERT INTO class_sections VALUES (3, 20, 'BSIT-1-C', 100, 11, NULL)");
        db.update("INSERT INTO class_sections VALUES (4, 10, 'BSIT-1-D', NULL, 12, NULL)");
        db.update("INSERT INTO class_sections VALUES (5, 10, 'BSIT-1-E', 300, 13, NULL)");
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
    void rejectsSameBlockCohortOverlapAcrossDifferentSubjects() {
        db.update("INSERT INTO class_sections VALUES (6, 10, 'NNAD-1-1-C', 100, 11, 900)");
        db.update("INSERT INTO class_sections VALUES (7, 10, 'NNAD-1-1-C', 200, 12, 900)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (6, 1, '07:30:00', '08:30:00', NULL, 100)");

        String result = validator.validateNewSlot(
            7, 200, null, 1, LocalTime.of(7, 45), LocalTime.of(8, 15));

        assertThat(result).isEqualTo(
            "Block cohort NNAD-1-1-C already has another subject at that time (AECO11).");
    }

    @Test
    void allowsOverlapForDifferentBlocksWhenRoomAndFacultyAreDifferent() {
        db.update("INSERT INTO class_sections VALUES (6, 10, 'NNAD-1-1-C', 100, 11, 900)");
        db.update("INSERT INTO class_sections VALUES (7, 10, 'NNAD-1-1-D', 200, 12, 901)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (6, 1, '07:30:00', '08:30:00', NULL, 100)");

        String result = validator.validateNewSlot(
            7, 200, null, 1, LocalTime.of(7, 45), LocalTime.of(8, 15));

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
    void rejectsFacultyAssignmentThatWouldExceedLoadCap() {
        String result = validator.validateFacultyAssignment(4, 300);

        assertThat(result).isEqualTo(
            "Faculty assignment would exceed max load (3/3 units; adding 3 would exceed the cap).");
    }

    @Test
    void reportsConflictsAlreadyStoredForTheTerm() {
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (2, 1, '10:00:00', '11:00:00', 5, 200)");

        List<Map<String, Object>> conflicts = validator.findExistingConflicts(10);

        assertThat(conflicts).extracting(row -> row.get("conflict_type")).containsExactly("ROOM");
        assertThat(conflicts.get(0).get("resource_name")).isEqualTo("LAB-5");
    }

    @Test
    void reportsStoredBlockCohortConflictsInDiagnostics() {
        db.update("INSERT INTO class_sections VALUES (6, 10, 'NNAD-1-1-C', 100, 11, 900)");
        db.update("INSERT INTO class_sections VALUES (7, 10, 'NNAD-1-1-C', 200, 12, 900)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (6, 1, '07:30:00', '08:30:00', NULL, 100)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (7, 1, '07:45:00', '08:15:00', NULL, 200)");

        List<Map<String, Object>> conflicts = validator.findExistingConflicts(10);

        assertThat(conflicts).extracting(row -> row.get("conflict_type")).contains("BLOCK_COHORT");
        assertThat(conflicts.stream()
            .filter(row -> "BLOCK_COHORT".equals(row.get("conflict_type")))
            .findFirst()
            .orElseThrow())
            .containsEntry("resource_name", "NNAD-1-1-C");
    }

    @Test
    void capsTheConflictPreviewBeforeItReachesThePage() {
        db.update("INSERT INTO class_sections VALUES (6, 10, 'BSIT-1-F', 100, 11, NULL)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (2, 1, '09:30:00', '10:00:00', 5, 200)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (6, 1, '09:15:00', '10:15:00', 5, 100)");

        ScheduleConflictValidator.ConflictPreview preview =
            validator.findExistingConflictPreview(10, 2);

        assertThat(preview.conflicts()).hasSize(2);
        assertThat(preview.conflicts()).extracting(row -> row.get("conflict_type"))
            .containsExactly("ROOM", "FACULTY");
        assertThat(preview.truncated()).isTrue();
    }

    @Test
    void rejectsUnknownRoomBeforeCreatingAConflictFreeSchedule() {
        String result = validator.validateNewSlot(
            4, 200, 999, 1, LocalTime.of(11, 0), LocalTime.of(12, 0));

        assertThat(result).isEqualTo("Room not found or inactive.");
    }

    @Test
    void repairsExistingRoomFacultyAndSectionOverlapConflicts() {
        db.update("UPDATE class_sections SET faculty_id = 100 WHERE section_id = 2");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (2, 1, '09:30:00', '10:15:00', 5, 100)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (1, 1, '09:15:00', '09:45:00', NULL, 100)");

        ScheduleConflictValidator.RepairResult result = validator.repairExistingConflicts(10);

        assertThat(result.changed()).isTrue();
        assertThat(result.roomAssignmentsCleared()).isGreaterThan(0);
        assertThat(result.sectionOverlapRowsDeleted()).isGreaterThan(0);
        assertThat(result.facultySectionsCleared()).isGreaterThan(0);

        assertThat(db.queryForObject(
            "SELECT room_id FROM class_schedules WHERE section_id = 2 AND start_time = '09:30:00'",
            Integer.class)).isNull();
        assertThat(db.queryForObject(
            "SELECT faculty_id FROM class_sections WHERE section_id = 2",
            Integer.class)).isNull();
        assertThat(db.queryForObject(
            "SELECT faculty_id FROM class_schedules WHERE section_id = 2 AND start_time = '09:30:00'",
            Integer.class)).isNull();
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM class_schedules WHERE section_id = 1 AND day_of_week = 1",
            Integer.class)).isEqualTo(1);
        assertThat(validator.findExistingConflicts(10)).isEmpty();
    }

    @Test
    void repairIsNoOpWhenStoredSchedulesAreAlreadyClean() {
        db.update("DELETE FROM class_schedules");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (1, 1, '09:00:00', '10:30:00', 5, 100)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (2, 2, '09:00:00', '10:30:00', 5, 200)");

        ScheduleConflictValidator.RepairResult result = validator.repairExistingConflicts(10);

        assertThat(result.changed()).isFalse();
        assertThat(result.facultyScheduleRowsCleared()).isZero();
    }
}
