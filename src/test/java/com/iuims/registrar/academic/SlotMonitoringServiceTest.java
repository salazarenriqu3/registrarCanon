package com.iuims.registrar.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.iuims.registrar.core.EnlistmentSchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

class SlotMonitoringServiceTest {

    private JdbcTemplate db;
    private SlotMonitoringService service;

    @BeforeEach
    void setUp() {
        db = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:slot-monitoring;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("DROP ALL OBJECTS");
        db.execute("CREATE TABLE academic_terms (term_id INT PRIMARY KEY, term_name VARCHAR(100))");
        db.execute("CREATE TABLE courses (course_id INT PRIMARY KEY, course_code VARCHAR(20), course_title VARCHAR(150))");
        db.execute("CREATE TABLE class_sections (section_id INT PRIMARY KEY, course_id INT, term_id INT, section_code VARCHAR(32), max_capacity INT, section_status VARCHAR(30), block_id INT)");
        db.execute("CREATE TABLE block_offerings (block_id INT PRIMARY KEY, program_code VARCHAR(20), section_group VARCHAR(5))");
        db.execute("CREATE TABLE student_enlistments (enlistment_id INT AUTO_INCREMENT PRIMARY KEY, section_id INT, enlistment_status VARCHAR(20))");

        db.update("INSERT INTO academic_terms VALUES (1, 'A.Y. 2024-2025 - 1st Semester')");
        db.update("INSERT INTO courses VALUES (10, 'BSIT101', 'Intro to IT')");
        db.update("INSERT INTO block_offerings VALUES (99, 'BSIT', 'A')");
        db.update("INSERT INTO class_sections VALUES (100, 10, 1, 'BSIT101-A', 40, 'Open', 99)");
        db.update("INSERT INTO student_enlistments (section_id, enlistment_status) VALUES (100, 'COMMITTED')");
        db.update("INSERT INTO student_enlistments (section_id, enlistment_status) VALUES (100, 'COMMITTED')");
        db.update("INSERT INTO student_enlistments (section_id, enlistment_status) VALUES (100, 'STAGED')");

        AcademicGradingService academicGradingService = org.mockito.Mockito.mock(AcademicGradingService.class);
        when(academicGradingService.closeSection(anyInt())).thenReturn("SUCCESS");

        EnlistmentSchemaService enlistmentSchemaService = new EnlistmentSchemaService(db);
        ReflectionTestUtils.setField(enlistmentSchemaService, "enlistmentStatusColumn", true);
        service = new SlotMonitoringService(db, enlistmentSchemaService, academicGradingService);
    }

    @Test
    void listsCommittedAndStagedCountsSeparately() {
        List<Map<String, Object>> sections = service.listSectionsForTerm(1, "BSIT101");

        assertThat(sections).hasSize(1);
        Map<String, Object> row = sections.get(0);
        assertThat(((Number) row.get("enrolled_count")).intValue()).isEqualTo(2);
        assertThat(((Number) row.get("prereg_count")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("slots_left")).intValue()).isEqualTo(37);
    }

    @Test
    void updatesCapacity() {
        String result = service.updateCapacity(100, 45);

        assertThat(result).isEqualTo("SUCCESS");
        Integer updated = db.queryForObject(
            "SELECT max_capacity FROM class_sections WHERE section_id = 100",
            Integer.class);
        assertThat(updated).isEqualTo(45);
    }
}
