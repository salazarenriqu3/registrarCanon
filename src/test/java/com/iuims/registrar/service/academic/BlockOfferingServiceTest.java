package com.iuims.registrar.service.academic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class BlockOfferingServiceTest {

    private JdbcTemplate db;
    private BlockOfferingService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:block-offerings;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(dataSource);
        db.execute("DROP ALL OBJECTS");
        db.execute("CREATE TABLE courses (course_id INT PRIMARY KEY, course_code VARCHAR(20), credit_units INT, is_coordinator_based INT DEFAULT 0, coordinator_equivalent_units INT NULL, active_status INT DEFAULT 1, onlist INT DEFAULT 1)");
        db.execute("CREATE TABLE faculty (faculty_id INT PRIMARY KEY, first_name VARCHAR(50), last_name VARCHAR(50), active_status INT DEFAULT 1, max_teaching_units INT)");
        db.execute("CREATE TABLE curriculum_courses (curriculum_course_id INT AUTO_INCREMENT PRIMARY KEY, curriculum_id INT, course_id INT, year_level INT, semester_number INT)");
        db.execute("CREATE TABLE class_sections (section_id INT AUTO_INCREMENT PRIMARY KEY, course_id INT, term_id INT, section_code VARCHAR(50), faculty_id INT, max_capacity INT DEFAULT 40, section_status VARCHAR(30), semester_number INT, block_id INT)");
        db.execute("CREATE TABLE class_schedules (schedule_id INT AUTO_INCREMENT PRIMARY KEY, section_id INT, day_of_week INT, start_time TIME, end_time TIME, room_id INT, faculty_id INT)");
        db.execute("CREATE TABLE block_offerings (block_id INT PRIMARY KEY, term_id INT, program_code VARCHAR(32), year_level INT, semester_number INT, section_group VARCHAR(10), max_capacity INT, faculty_id INT, curriculum_id INT, block_status VARCHAR(20))");

        service = new BlockOfferingService(db);

        db.update("INSERT INTO courses VALUES (11, 'IT101', 3, 0, NULL, 1, 1)");
        db.update("INSERT INTO courses VALUES (12, 'IT102', 3, 0, NULL, 1, 1)");
        db.update("INSERT INTO faculty VALUES (300, 'Alan', 'Turing', 1, 3)");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number) VALUES (88, 12, 1, 1)");
        db.update("INSERT INTO class_sections (course_id, term_id, section_code, faculty_id, max_capacity, section_status, semester_number, block_id) VALUES (11, 10, 'EXISTING', 300, 40, 'Open', 1, NULL)");
        db.update("INSERT INTO block_offerings VALUES (99, 10, 'BSIT', 1, 1, 'A', 40, 300, 88, 'Open')");
    }

    @Test
    void rematerializeBlockRejectsFacultyOverloadBeforeCreatingSections() {
        String result = service.rematerializeBlock(99);

        assertThat(result).contains("ERROR: Faculty assignment would exceed max load");
        Integer created = db.queryForObject(
            "SELECT COUNT(*) FROM class_sections WHERE section_code = 'BSIT-1-1-A'",
            Integer.class);
        assertThat(created).isZero();
    }

    @Test
    void irregularAccessPrecedenceFollowsClassThenBlockThenProgramPolicy() {
        db.update(
            "INSERT INTO class_sections (course_id, term_id, section_code, faculty_id, max_capacity, section_status, semester_number, block_id) " +
                "VALUES (12, 10, 'BSIT-1-1-A', NULL, 40, 'Open', 1, 99)");
        Integer sectionId = db.queryForObject(
            "SELECT section_id FROM class_sections WHERE course_id = 12 AND section_code = 'BSIT-1-1-A'",
            Integer.class);

        assertThat(service.resolveSectionIrregularAccess(sectionId).open()).isFalse();

        service.setProgramYearSemesterIrregularAccess(10, "BSIT", 1, 1, true);
        assertThat(service.resolveSectionIrregularAccess(sectionId))
            .extracting(
                BlockOfferingService.IrregularAccessDecision::open,
                BlockOfferingService.IrregularAccessDecision::stateSource)
            .containsExactly(true, "PROGRAM_YEAR_SEM_OPEN");

        service.setBlockIrregularAccessOverride(99, "CLOSED");
        assertThat(service.resolveSectionIrregularAccess(sectionId))
            .extracting(
                BlockOfferingService.IrregularAccessDecision::open,
                BlockOfferingService.IrregularAccessDecision::stateSource)
            .containsExactly(false, "BLOCK_OVERRIDE_CLOSED");

        service.setClassIrregularAccessOverride(sectionId, "OPEN");
        assertThat(service.resolveSectionIrregularAccess(sectionId))
            .extracting(
                BlockOfferingService.IrregularAccessDecision::open,
                BlockOfferingService.IrregularAccessDecision::stateSource)
            .containsExactly(true, "CLASS_OVERRIDE_OPEN");
    }
}
