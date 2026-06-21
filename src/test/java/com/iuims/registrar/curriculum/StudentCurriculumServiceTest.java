package com.iuims.registrar.curriculum;
import com.iuims.registrar.academic.AcademicGradingService;
import com.iuims.registrar.core.GradeOutcomeSql;
import com.iuims.registrar.admission.ApplicantStatusSyncService;
import com.iuims.registrar.admission.FinanceAdmissionService;
import com.iuims.registrar.curriculum.CurriculumSeederService;
import com.iuims.registrar.curriculum.StudentCurriculumService;
import com.iuims.registrar.core.EnlistmentSchemaService;
import com.iuims.registrar.faculty.FacultyLoadService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.finance.TermFeeAdminService;
import com.iuims.registrar.core.DatabaseSetupService;
import com.iuims.registrar.jaypee.JaypeeIntegrationService;
import com.iuims.registrar.core.PolicySettings;
import com.iuims.registrar.core.SqlGenerator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StudentCurriculumServiceTest {

    private JdbcTemplate db;
    private StudentCurriculumService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:studentcurriculum" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        db = new JdbcTemplate(dataSource);
        db.execute("""
            CREATE TABLE programs (
                program_id INT AUTO_INCREMENT PRIMARY KEY,
                program_code VARCHAR(20) NOT NULL,
                program_name VARCHAR(100) NULL,
                active_status TINYINT NOT NULL DEFAULT 1
            )
            """);
        db.execute("""
            CREATE TABLE curriculum_templates (
                curriculum_id INT AUTO_INCREMENT PRIMARY KEY,
                program_id INT NOT NULL,
                curriculum_name VARCHAR(100) NULL,
                academic_year VARCHAR(20) NULL,
                version_number INT NOT NULL DEFAULT 1,
                is_active TINYINT NOT NULL DEFAULT 0
            )
            """);
        db.execute("""
            CREATE TABLE students (
                student_number VARCHAR(100) PRIMARY KEY,
                program_code VARCHAR(100) NULL
            )
            """);

        service = new StudentCurriculumService(db, null);
        service.ensureSchema();
    }

    @Test
    void resolveOrAssignDoesNotCreateImplicitAssignmentWhenStudentHasNoAssignment() {
        seedProgramWithTwoCurricula();
        db.update("INSERT INTO students (student_number, program_code) VALUES ('2026-0001', 'BSIT')");

        Integer curriculumId = service.resolveOrAssignCurrentCurriculum("2026-0001");

        assertThat(curriculumId).isNull();
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_curriculum_assignments WHERE student_number = '2026-0001'",
            Integer.class)).isZero();
    }

    @Test
    void resolveOrAssignKeepsExistingStudentCurriculumWhenProgramDefaultChanges() {
        seedProgramWithTwoCurricula();
        db.update("INSERT INTO students (student_number, program_code) VALUES ('2026-0002', 'BSIT')");
        service.assignCurriculum("2026-0002", 1, "NEW_ENTRANT", "Started under 2026 catalog.");

        Integer curriculumId = service.resolveOrAssignCurrentCurriculum("2026-0002");

        assertThat(curriculumId).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_curriculum_assignments WHERE student_number = '2026-0002' AND is_current = 1",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void assignCurriculumKeepsOnlyLatestAssignmentCurrent() {
        seedProgramWithTwoCurricula();
        db.update("INSERT INTO students (student_number, program_code) VALUES ('2026-0003', 'BSIT')");

        service.assignCurriculum("2026-0003", 1, "NEW_ENTRANT", "Initial catalog.");
        service.assignCurriculum("2026-0003", 2, "PROGRAM_SHIFT", "Registrar reassignment.");

        assertThat(service.findCurrentCurriculumId("2026-0003")).isEqualTo(2);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_curriculum_assignments WHERE student_number = '2026-0003' AND is_current = 1",
            Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_curriculum_assignments WHERE student_number = '2026-0003' AND is_current = 0",
            Integer.class)).isEqualTo(1);
    }

    private void seedProgramWithTwoCurricula() {
        db.update("INSERT INTO programs (program_id, program_code, program_name, active_status) VALUES (1, 'BSIT', 'BSIT Program', 1)");
        db.update("INSERT INTO curriculum_templates (curriculum_id, program_id, curriculum_name, academic_year, version_number, is_active) VALUES (1, 1, 'BSIT 2026 Curriculum', '2026-2027', 1, 0)");
        db.update("INSERT INTO curriculum_templates (curriculum_id, program_id, curriculum_name, academic_year, version_number, is_active) VALUES (2, 1, 'BSIT 2027 Curriculum', '2027-2028', 2, 1)");
    }
}



