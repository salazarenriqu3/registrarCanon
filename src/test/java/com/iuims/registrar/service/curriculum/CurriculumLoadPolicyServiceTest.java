package com.iuims.registrar.service.curriculum;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Program;

import com.iuims.registrar.service.support.YearLevelLoadPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurriculumLoadPolicyServiceTest {

    private JdbcTemplate db;
    private CurriculumLoadPolicyService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:curriculum-load-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
            "sa",
            "");
        db = new JdbcTemplate(dataSource);
        createSchema();
        service = new CurriculumLoadPolicyService(db, new StudentCurriculumService(db, null));
    }

    @Test
    void resolvesMaxUnitsFromAssignedCurriculumOnly() {
        seedProgram("BSIT", 1);
        seedCurriculum(10, 1);
        seedCurriculum(20, 1);
        seedStudent("2026-0001", "BSIT", 1, 1, 10);
        seedCurriculumTermUnits(10, 1, 1, "3", "3", "3", "3", "3", "3", "3", "3", "3");
        seedCurriculumTermUnits(10, 4, 1, "3");
        seedCurriculumTermUnits(20, 1, 1, "6", "6", "6");

        CurriculumLoadPolicyService.StudentLoadPolicy policy = service.resolveForStudent("2026-0001");

        assertThat(policy.curriculumId()).isEqualTo(10);
        assertThat(policy.baseUnits()).isEqualByComparingTo("27");
        assertThat(policy.effectiveMaxUnits()).isEqualByComparingTo("27");
    }

    @Test
    void graduatingStudentGetsExplicitOverloadAllowance() {
        seedProgram("BSCRIM", 2);
        seedCurriculum(30, 2);
        seedStudent("2026-0002", "BSCRIM", 4, 1, 30);
        seedCurriculumTermUnits(30, 1, 1, "3");
        seedCurriculumTermUnits(30, 4, 1, "3", "3", "3", "3", "3", "3");

        CurriculumLoadPolicyService.StudentLoadPolicy policy = service.resolveForStudent("2026-0002");

        assertThat(policy.graduating()).isTrue();
        assertThat(policy.baseUnits()).isEqualByComparingTo("18");
        assertThat(policy.graduatingExtraUnits()).isEqualByComparingTo("6");
        assertThat(policy.effectiveMaxUnits()).isEqualByComparingTo("24");
    }

    @Test
    void missingAssignmentFailsFast() {
        db.update("INSERT INTO students (student_number, program_code, year_level, semester) VALUES ('2026-0003', 'BSIT', 1, 1)");

        assertThatThrownBy(() -> service.resolveForStudent("2026-0003"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No curriculum assigned");
    }

    @Test
    void missingCurriculumRowsForCurrentTermFailsFast() {
        seedProgram("BSIT", 1);
        seedCurriculum(10, 1);
        seedStudent("2026-0004", "BSIT", 2, 1, 10);
        seedCurriculumTermUnits(10, 1, 1, "3");

        assertThatThrownBy(() -> service.resolveForStudent("2026-0004"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no course rows");
    }

    @Test
    void classifiesAgainstCurriculumBaseAndEffectiveMaximum() {
        seedProgram("BSIT", 1);
        seedCurriculum(10, 1);
        seedStudent("2026-0005", "BSIT", 1, 1, 10);
        seedCurriculumTermUnits(10, 1, 1, "3", "3", "3", "3", "3", "3");
        seedCurriculumTermUnits(10, 4, 1, "3");

        assertThat(service.classifyForStudent("2026-0005", new BigDecimal("15")))
            .isEqualTo(YearLevelLoadPolicyService.LoadStanding.UNDERLOAD);
        assertThat(service.classifyForStudent("2026-0005", new BigDecimal("18")))
            .isEqualTo(YearLevelLoadPolicyService.LoadStanding.REGULAR);
        assertThat(service.classifyForStudent("2026-0005", new BigDecimal("21")))
            .isEqualTo(YearLevelLoadPolicyService.LoadStanding.OVERLOAD);
    }

    private void createSchema() {
        db.execute("CREATE TABLE students (student_number VARCHAR(100) PRIMARY KEY, program_code VARCHAR(20), year_level INT, semester INT)");
        db.execute("CREATE TABLE programs (program_id INT PRIMARY KEY, program_code VARCHAR(20), program_name VARCHAR(100))");
        db.execute("CREATE TABLE curriculum_templates (curriculum_id INT PRIMARY KEY, program_id INT, curriculum_name VARCHAR(100), approval_status VARCHAR(20), lifecycle_status VARCHAR(20), is_active TINYINT)");
        db.execute("CREATE TABLE courses (course_id INT PRIMARY KEY, course_code VARCHAR(30), credit_units DECIMAL(5,2))");
        db.execute("CREATE TABLE curriculum_courses (curriculum_course_id INT AUTO_INCREMENT PRIMARY KEY, curriculum_id INT, course_id INT, year_level INT, semester_number INT)");
        db.execute("CREATE TABLE student_curriculum_assignments (assignment_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_number VARCHAR(100), curriculum_id INT, program_code VARCHAR(100), assignment_type VARCHAR(40), reason VARCHAR(255), is_current TINYINT, assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
    }

    private void seedProgram(String programCode, int programId) {
        db.update("INSERT INTO programs (program_id, program_code, program_name) VALUES (?, ?, ?)",
            programId, programCode, programCode + " Program");
    }

    private void seedCurriculum(int curriculumId, int programId) {
        db.update("INSERT INTO curriculum_templates (curriculum_id, program_id, curriculum_name, approval_status, lifecycle_status, is_active) VALUES (?, ?, ?, 'Approved', 'CURRENT', 1)",
            curriculumId, programId, "Curriculum " + curriculumId);
    }

    private void seedStudent(String studentNumber, String programCode, int yearLevel, int semester, int curriculumId) {
        db.update("INSERT INTO students (student_number, program_code, year_level, semester) VALUES (?, ?, ?, ?)",
            studentNumber, programCode, yearLevel, semester);
        db.update("INSERT INTO student_curriculum_assignments (student_number, curriculum_id, program_code, assignment_type, is_current) VALUES (?, ?, ?, 'DEFAULT', 1)",
            studentNumber, curriculumId, programCode);
    }

    private void seedCurriculumTermUnits(int curriculumId, int yearLevel, int semester, String... units) {
        for (String unit : units) {
            Integer courseId = db.queryForObject("SELECT COALESCE(MAX(course_id), 0) + 1 FROM courses", Integer.class);
            db.update("INSERT INTO courses (course_id, course_code, credit_units) VALUES (?, ?, ?)",
                courseId, "C" + courseId, new BigDecimal(unit));
            db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number) VALUES (?, ?, ?, ?)",
                curriculumId, courseId, yearLevel, semester);
        }
    }
}
