package com.iuims.registrar.service.curriculum;
import com.iuims.registrar.entity.Program;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

class ProgramServiceTest {

    private JdbcTemplate db;
    private ProgramService service;

    @BeforeEach
    void setUp() {
        db = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:program-builder;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("DROP ALL OBJECTS");
        db.execute("CREATE TABLE departments (department_id INT PRIMARY KEY, department_code VARCHAR(20), department_name VARCHAR(150))");
        db.execute("CREATE TABLE programs (program_id INT AUTO_INCREMENT PRIMARY KEY, program_code VARCHAR(20) UNIQUE, program_name VARCHAR(150), department_id INT, school_name VARCHAR(100), duration_years INT NOT NULL DEFAULT 4, active_status INT NOT NULL DEFAULT 1, level VARCHAR(32))");
        db.execute("CREATE TABLE curriculum_templates (curriculum_id INT PRIMARY KEY, program_id INT, curriculum_name VARCHAR(100), academic_year VARCHAR(20), version_number INT, approval_status VARCHAR(20), is_active INT)");
        db.execute("CREATE TABLE student_curriculum_assignments (assignment_id INT AUTO_INCREMENT PRIMARY KEY, student_number VARCHAR(100), program_code VARCHAR(20), assignment_type VARCHAR(40), reason VARCHAR(255), is_current INT, assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.update("INSERT INTO departments VALUES (1, 'SCS', 'School of Computer Studies')");

        service = new ProgramService();
        ReflectionTestUtils.setField(service, "db", db);
    }

    @Test
    void savesProgramWithDurationAndDepartment() {
        Integer programId = service.saveProgram(null, "BSIT", "Bachelor of Science in IT", 1, "EAC", 4, true);

        Map<String, Object> saved = db.queryForMap("SELECT * FROM programs WHERE program_id = ?", programId);
        assertThat(saved.get("PROGRAM_CODE")).isEqualTo("BSIT");
        assertThat(saved.get("DURATION_YEARS")).isEqualTo(4);
        assertThat(saved.get("ACTIVE_STATUS")).isEqualTo(1);
        assertThat(saved.get("LEVEL")).isEqualTo("COLLEGE");
    }

    @Test
    void locksProgramCodeAfterCreate() {
        Integer programId = service.saveProgram(null, "BSCS", "Computer Science", 1, null, 4, true);

        assertThatThrownBy(() -> service.saveProgram(programId, "BSCPE", "Renamed", 1, null, 4, true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Program code cannot be changed");
    }

    @Test
    void blocksDeleteWhenCurriculumExists() {
        Integer programId = service.saveProgram(null, "BSIT", "Information Technology", 1, null, 4, true);
        db.update("INSERT INTO curriculum_templates VALUES (10, ?, 'BSIT 2026', '2026-2027', 1, 'Approved', 1)", programId);

        assertThatThrownBy(() -> service.deleteUnusedProgram(programId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already used");
    }

    @Test
    void listActiveProgramsReturnsOnlyActiveRecords() {
        service.saveProgram(null, "BSIT", "Information Technology", 1, null, 4, true);
        Integer inactiveId = service.saveProgram(null, "BSCS", "Computer Science", 1, null, 4, false);
        service.setActiveStatus(inactiveId, false);

        assertThat(service.listActivePrograms()).hasSize(1);
        assertThat(service.listActivePrograms().get(0).get("program_code")).isEqualTo("BSIT");
    }

    @Test
    void pagesProgramsAndCalculatesUsageSummaryWithoutLoadingEveryRow() {
        Integer bsitId = service.saveProgram(null, "BSIT", "Information Technology", 1, null, 4, true);
        service.saveProgram(null, "BSCS", "Computer Science", 1, null, 4, true);
        service.saveProgram(null, "BSBA", "Business Administration", 1, null, 4, false);
        db.update("INSERT INTO curriculum_templates VALUES (10, ?, 'BSIT 2026', '2026-2027', 1, 'Approved', 1)", bsitId);
        db.update("INSERT INTO student_curriculum_assignments (student_number, program_code, assignment_type, is_current) VALUES ('20260001', 'BSCS', 'CURRENT', 1)");

        ProgramService.ProgramPage firstPage = service.listProgramsPage(null, null, "all", 1, 2);

        assertThat(firstPage.rows()).hasSize(2);
        assertThat(firstPage.page()).isEqualTo(1);
        assertThat(firstPage.pageSize()).isEqualTo(2);
        assertThat(firstPage.totalRows()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.summary())
            .containsEntry("total", 3)
            .containsEntry("active", 2)
            .containsEntry("inactive", 1)
            .containsEntry("used", 2);
    }

    @Test
    void editAndStatusUpdatesNormalizeLevelToCollege() {
        Integer programId = service.saveProgram(null, "BSIT", "Information Technology", 1, null, 4, true);
        db.update("UPDATE programs SET level = NULL WHERE program_id = ?", programId);

        service.saveProgram(programId, "BSIT", "Information Technology Updated", 1, null, 4, true);
        assertThat(db.queryForObject("SELECT level FROM programs WHERE program_id = ?", String.class, programId))
            .isEqualTo("COLLEGE");

        db.update("UPDATE programs SET level = 'REGULAR' WHERE program_id = ?", programId);
        service.setActiveStatus(programId, false);
        assertThat(db.queryForMap("SELECT active_status, level FROM programs WHERE program_id = ?", programId))
            .containsEntry("ACTIVE_STATUS", 0)
            .containsEntry("LEVEL", "COLLEGE");
    }
}
