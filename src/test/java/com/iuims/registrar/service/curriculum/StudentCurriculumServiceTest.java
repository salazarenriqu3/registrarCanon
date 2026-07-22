package com.iuims.registrar.service.curriculum;
import com.iuims.registrar.entity.Program;
import com.iuims.registrar.service.academic.AcademicGradingService;
import com.iuims.registrar.support.GradeOutcomeSql;
import com.iuims.registrar.service.admission.ApplicantStatusSyncService;
import com.iuims.registrar.service.admission.FinanceAdmissionService;
import com.iuims.registrar.service.curriculum.CurriculumSeederService;
import com.iuims.registrar.service.curriculum.StudentCurriculumService;
import com.iuims.registrar.service.support.EnlistmentSchemaService;
import com.iuims.registrar.service.faculty.FacultyLoadService;
import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.service.finance.TermFeeAdminService;
import com.iuims.registrar.service.support.DatabaseSetupService;
import com.iuims.registrar.service.integration.JaypeeIntegrationService;
import com.iuims.registrar.support.PolicySettings;
import com.iuims.registrar.support.SqlGenerator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.iuims.registrar.service.forms.RegFormEventService;
import com.iuims.registrar.domain.forms.RegistrationFormSnapshotException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
        db.execute("CREATE TABLE courses (course_id INT PRIMARY KEY, course_code VARCHAR(40), course_title VARCHAR(160), credit_units INT)");
        db.execute("CREATE TABLE curriculum_courses (curriculum_course_id INT AUTO_INCREMENT PRIMARY KEY, curriculum_id INT, course_id INT, year_level INT, semester_number INT)");
        db.execute("CREATE TABLE grades (id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id VARCHAR(100), course_id INT, remarks VARCHAR(40), registrar_final_remarks VARCHAR(40), semestral_grade DECIMAL(5,2), registrar_final_grade DECIMAL(5,2), grade_lock_reason VARCHAR(80))");
        db.execute("CREATE TABLE course_equivalencies (course_id INT, equivalent_course_id INT)");

        service = new StudentCurriculumService(db, mock(RegFormEventService.class));
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

    @Test
    void snapshotFailureRollsBackCurriculumAssignment() {
        seedProgramWithTwoCurricula();
        db.update("INSERT INTO students (student_number, program_code) VALUES ('2026-0005', 'BSIT')");
        RegFormEventService failingEvents = mock(RegFormEventService.class);
        doThrow(new RegistrationFormSnapshotException("Registration Form version could not be saved."))
            .when(failingEvents).recordEvent(anyString(), anyString(), anyString(), any(), anyString(), anyString());
        StudentCurriculumService failingService = new StudentCurriculumService(db, failingEvents);
        TransactionTemplate transactionTemplate = new TransactionTemplate(
            new DataSourceTransactionManager(db.getDataSource()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
            failingService.assignCurriculum("2026-0005", 1, "REGISTRAR_PROFILE", "test", "registrar")))
            .isInstanceOf(RegistrationFormSnapshotException.class);

        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_curriculum_assignments WHERE student_number = '2026-0005'", Integer.class)).isZero();
    }

    @Test
    void equivalentPassedCourseSatisfiesCurriculumDeficiencyAndCarryOver() {
        seedProgramWithTwoCurricula();
        db.update("INSERT INTO students (student_number, program_code) VALUES ('2026-0004', 'BSIT')");
        service.assignCurriculum("2026-0004", 1, "DEFAULT", "test");
        db.update("INSERT INTO courses (course_id, course_code, course_title, credit_units) VALUES (101, 'IT 101', 'Intro to IT', 3)");
        db.update("INSERT INTO courses (course_id, course_code, course_title, credit_units) VALUES (201, 'CIS 101', 'Computer Information Systems', 3)");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number) VALUES (1, 101, 1, 1)");
        db.update("INSERT INTO course_equivalencies (course_id, equivalent_course_id) VALUES (101, 201)");
        db.update("INSERT INTO course_equivalencies (course_id, equivalent_course_id) VALUES (201, 101)");
        db.update("INSERT INTO grades (student_id, course_id, remarks, registrar_final_remarks) VALUES ('2026-0004', 201, 'PASSED', 'PASSED')");

        assertThat(service.listCurriculumDeficiencies("2026-0004")).isEmpty();
        assertThat(service.listOrphanPassedCredits("2026-0004")).isEmpty();
        assertThat(service.listCarriedOverCredits("2026-0004"))
            .extracting(row -> row.get("matched_course_code"))
            .contains("CIS 101");
    }

    private void seedProgramWithTwoCurricula() {
        db.update("INSERT INTO programs (program_id, program_code, program_name, active_status) VALUES (1, 'BSIT', 'BSIT Program', 1)");
        db.update("INSERT INTO curriculum_templates (curriculum_id, program_id, curriculum_name, academic_year, version_number, is_active) VALUES (1, 1, 'BSIT 2026 Curriculum', '2026-2027', 1, 0)");
        db.update("INSERT INTO curriculum_templates (curriculum_id, program_id, curriculum_name, academic_year, version_number, is_active) VALUES (2, 1, 'BSIT 2027 Curriculum', '2027-2028', 2, 1)");
    }
}



