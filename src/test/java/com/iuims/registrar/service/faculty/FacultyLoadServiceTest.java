package com.iuims.registrar.service.faculty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

class FacultyLoadServiceTest {

    private JdbcTemplate db;
    private FacultyLoadService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:faculty-loads;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(dataSource);
        db.execute("DROP ALL OBJECTS");
        db.execute("CREATE TABLE courses (course_id INT PRIMARY KEY, credit_units INT, is_coordinator_based INT DEFAULT 0, coordinator_equivalent_units INT NULL)");
        db.execute("CREATE TABLE faculty (faculty_id INT PRIMARY KEY, first_name VARCHAR(50), last_name VARCHAR(50), active_status INT DEFAULT 1, max_teaching_units INT)");
        db.execute("CREATE TABLE class_sections (section_id INT PRIMARY KEY, term_id INT, section_code VARCHAR(50), faculty_id INT, course_id INT)");
        db.execute("CREATE TABLE class_schedules (schedule_id INT AUTO_INCREMENT PRIMARY KEY, section_id INT, day_of_week INT, start_time TIME, end_time TIME, room_id INT, faculty_id INT)");

        service = new FacultyLoadService();
        ReflectionTestUtils.setField(service, "db", db);

        db.update("INSERT INTO courses VALUES (11, 3, 0, NULL)");
        db.update("INSERT INTO courses VALUES (12, 3, 0, NULL)");
        db.update("INSERT INTO faculty VALUES (300, 'Alan', 'Turing', 1, 3)");
        db.update("INSERT INTO class_sections VALUES (4, 10, 'BSIT-1-D', NULL, 12)");
        db.update("INSERT INTO class_sections VALUES (5, 10, 'BSIT-1-E', 300, 11)");
    }

    @Test
    void doesNotDoubleCountTheTargetSectionWhenCheckingCap() {
        assertThat(service.wouldExceedUnitCap(300, 10, 5)).isFalse();
    }

    @Test
    void blocksDirectAssignmentWhenTheFacultyWouldBeOverloaded() {
        assertThatThrownBy(() -> service.assignFacultyToSection(4, 300))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceed max load");
    }

    @Test
    void flagsSuspiciousTermWhenOneFacultyOwnsTheWholeTerm() {
        db.update("DELETE FROM class_sections");
        db.update("DELETE FROM class_schedules");
        db.update("INSERT INTO faculty VALUES (301, 'Grace', 'Hopper', 1, 6)");
        db.update("INSERT INTO courses VALUES (21, 3, 0, NULL)");
        for (int i = 1; i <= 30; i++) {
            db.update(
                "INSERT INTO class_sections (section_id, term_id, section_code, faculty_id, course_id) VALUES (?, 20, ?, 301, 21)",
                100 + i, "IRREG-" + i);
            db.update(
                "INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (?, 1, '09:00:00', '10:00:00', 1, 301)",
                100 + i);
        }

        var audit = service.getTermAssignmentAudit(20);

        assertThat(audit.get("suspicious_assignment_concentration")).isEqualTo(true);
        assertThat(audit.get("top_faculty_assigned_sections")).isEqualTo(30);
        assertThat(audit.get("mirrored_schedule_rows")).isEqualTo(30);
    }

    @Test
    void clearsSuspiciousAssignmentsFromSectionsAndSchedules() {
        db.update("DELETE FROM class_sections");
        db.update("DELETE FROM class_schedules");
        db.update("INSERT INTO faculty VALUES (302, 'Katherine', 'Johnson', 1, 6)");
        db.update("INSERT INTO courses VALUES (22, 3, 0, NULL)");
        for (int i = 1; i <= 28; i++) {
            db.update(
                "INSERT INTO class_sections (section_id, term_id, section_code, faculty_id, course_id) VALUES (?, 30, ?, 302, 22)",
                200 + i, "OPEN-" + i);
            db.update(
                "INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id, faculty_id) VALUES (?, 2, '10:00:00', '11:00:00', 2, 302)",
                200 + i);
        }

        var result = service.repairSuspiciousTermAssignments(30);

        assertThat(result.get("repaired")).isEqualTo(true);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM class_sections WHERE term_id = 30 AND faculty_id IS NOT NULL",
            Integer.class)).isZero();
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM class_schedules WHERE faculty_id IS NOT NULL",
            Integer.class)).isZero();
    }
}
