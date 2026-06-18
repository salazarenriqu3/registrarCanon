package com.iuims.registrar.academic;
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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

class GradeOutcomeSemanticsTest {

    private JdbcTemplate db;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:gradeoutcomes" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        db = new JdbcTemplate(dataSource);
    }

    @Test
    void prerequisiteCompletionUsesAcademicRemarksInsteadOfWorkflowStatus() {
        db.execute("""
            CREATE TABLE grades (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                student_id VARCHAR(100) NOT NULL,
                course_id INT NOT NULL,
                status VARCHAR(20) NULL,
                remarks VARCHAR(30) NULL,
                registrar_final_remarks VARCHAR(30) NULL
            )
            """);
        db.update("INSERT INTO grades (student_id, course_id, status, remarks) VALUES ('2026-0001', 100, 'SUBMITTED', 'Passed')");
        db.update("INSERT INTO grades (student_id, course_id, status, remarks) VALUES ('2026-0001', 101, 'PASSED', 'Failed')");
        db.update("INSERT INTO grades (student_id, course_id, status, remarks) VALUES ('2026-0001', 102, 'SUBMITTED', 'INC')");

        JaypeeIntegrationService service = new JaypeeIntegrationService(null, null, null, null, null);
        ReflectionTestUtils.setField(service, "db", db);

        List<Integer> passedCourseIds = ReflectionTestUtils.invokeMethod(
            service, "listPassedCourseIds", List.of("2026-0001"));

        assertThat(passedCourseIds).containsExactly(100);
    }

    @Test
    void scholarshipDiscountIsBlockedByFailedOrIncRemarksOnly() {
        createScholarshipTables();
        db.update("""
            INSERT INTO students
                (student_number, scholarship_approved, scholarship_type, scholarship_amount, discount_percentage)
            VALUES
                ('2026-0001', TRUE, 'ACADEMIC', 0, 0),
                ('2026-0002', TRUE, 'ACADEMIC', 0, 0)
            """);
        db.update("INSERT INTO grades (student_id, course_id, status, remarks) VALUES ('2026-0001', 100, 'SUBMITTED', 'INC')");
        db.update("INSERT INTO grades (student_id, course_id, status, remarks) VALUES ('2026-0002', 100, 'FAILED', 'Passed')");

        ScholarEnrollmentService service = new ScholarEnrollmentService(null, null, null, null, null, null);
        ReflectionTestUtils.setField(service, "db", db);

        Double blocked = ReflectionTestUtils.invokeMethod(service, "computeScholarDiscount", "2026-0001", 12_000.0);
        Double allowed = ReflectionTestUtils.invokeMethod(service, "computeScholarDiscount", "2026-0002", 12_000.0);

        assertThat(blocked).isEqualTo(0.0);
        assertThat(allowed).isEqualTo(12_000.0);
    }

    @Test
    void gradeBasedRenewalRevokesScholarshipUsingFailedRemarks() {
        createScholarshipTables();
        db.execute("""
            CREATE TABLE curriculum_courses (
                curriculum_course_id INT AUTO_INCREMENT PRIMARY KEY,
                course_id INT NOT NULL,
                year_level INT NULL,
                semester_number INT NULL
            )
            """);
        db.update("""
            INSERT INTO students
                (student_number, scholarship_approved, scholarship_type, scholarship_amount, discount_percentage)
            VALUES
                ('2026-0001', TRUE, 'ACADEMIC', 0, 100),
                ('2026-0002', TRUE, 'ACADEMIC', 0, 100)
            """);
        db.update("INSERT INTO curriculum_courses (course_id, year_level, semester_number) VALUES (100, 1, 1)");
        db.update("INSERT INTO grades (student_id, course_id, status, remarks) VALUES ('2026-0001', 100, 'SUBMITTED', 'Failed')");
        db.update("INSERT INTO grades (student_id, course_id, status, remarks) VALUES ('2026-0002', 100, 'FAILED', 'Passed')");

        ScholarEnrollmentService service = new ScholarEnrollmentService(null, null, null, null, null, null);
        ReflectionTestUtils.setField(service, "db", db);

        service.runGradeBasedRenewal(2, 1);

        Map<String, Object> revoked = db.queryForMap("SELECT scholarship_approved, scholarship_type, discount_percentage FROM students WHERE student_number = '2026-0001'");
        Map<String, Object> retained = db.queryForMap("SELECT scholarship_approved, scholarship_type, discount_percentage FROM students WHERE student_number = '2026-0002'");
        assertThat(((Number) revoked.get("scholarship_approved")).intValue()).isZero();
        assertThat(revoked.get("scholarship_type")).isEqualTo("NONE");
        assertThat(((Number) revoked.get("discount_percentage")).doubleValue()).isEqualTo(0.0);
        assertThat(((Number) retained.get("scholarship_approved")).intValue()).isOne();
        assertThat(retained.get("scholarship_type")).isEqualTo("ACADEMIC");
        assertThat(((Number) retained.get("discount_percentage")).doubleValue()).isEqualTo(100.0);
    }

    @Test
    void scholarshipChecksUseRegistrarFinalRemarksBeforeRawRemarks() {
        createScholarshipTables();
        db.execute("""
            CREATE TABLE curriculum_courses (
                curriculum_course_id INT AUTO_INCREMENT PRIMARY KEY,
                course_id INT NOT NULL,
                year_level INT NULL,
                semester_number INT NULL
            )
            """);
        db.update("""
            INSERT INTO students
                (student_number, scholarship_approved, scholarship_type, scholarship_amount, discount_percentage)
            VALUES
                ('2026-0001', 1, 'ACADEMIC', 0, 100),
                ('2026-0002', 1, 'ACADEMIC', 0, 100)
            """);
        db.update("INSERT INTO curriculum_courses (course_id, year_level, semester_number) VALUES (100, 1, 1)");
        db.update("INSERT INTO grades (student_id, course_id, status, remarks, registrar_final_remarks) VALUES ('2026-0001', 100, 'SUBMITTED', 'Failed', 'Passed')");
        db.update("INSERT INTO grades (student_id, course_id, status, remarks, registrar_final_remarks) VALUES ('2026-0002', 100, 'SUBMITTED', 'Passed', 'Failed')");

        ScholarEnrollmentService service = new ScholarEnrollmentService(null, null, null, null, null, null);
        ReflectionTestUtils.setField(service, "db", db);

        service.runGradeBasedRenewal(2, 1);

        Map<String, Object> retained = db.queryForMap("SELECT scholarship_type, discount_percentage FROM students WHERE student_number = '2026-0001'");
        Map<String, Object> revoked = db.queryForMap("SELECT scholarship_type, discount_percentage FROM students WHERE student_number = '2026-0002'");
        assertThat(retained.get("scholarship_type")).isEqualTo("ACADEMIC");
        assertThat(((Number) retained.get("discount_percentage")).doubleValue()).isEqualTo(100.0);
        assertThat(revoked.get("scholarship_type")).isEqualTo("NONE");
        assertThat(((Number) revoked.get("discount_percentage")).doubleValue()).isEqualTo(0.0);
    }

    @Test
    void manualScholarshipGrantAndRevokeSetApprovalFlag() {
        createScholarshipTables();
        db.update("""
            INSERT INTO students
                (student_number, scholarship_approved, scholarship_type, scholarship_amount, discount_percentage)
            VALUES ('2026-0001', 0, 'NONE', 0, 0)
            """);

        ScholarEnrollmentService service = new ScholarEnrollmentService(null, null, null, null, null, null);
        ReflectionTestUtils.setField(service, "db", db);

        assertThat(service.grantExternalScholarship("2026-0001", "BARANGAY", 50.0, "ACTIVE")).isEqualTo("SUCCESS");
        Map<String, Object> granted = db.queryForMap("SELECT scholarship_approved, scholarship_type, discount_percentage FROM students WHERE student_number = '2026-0001'");
        assertThat(((Number) granted.get("scholarship_approved")).intValue()).isOne();
        assertThat(granted.get("scholarship_type")).isEqualTo("BARANGAY");
        assertThat(((Number) granted.get("discount_percentage")).doubleValue()).isEqualTo(50.0);

        assertThat(service.grantExternalScholarship("2026-0001", "NONE", 0.0, "REVOKED")).isEqualTo("SUCCESS");
        Map<String, Object> revoked = db.queryForMap("SELECT scholarship_approved, scholarship_type, discount_percentage FROM students WHERE student_number = '2026-0001'");
        assertThat(((Number) revoked.get("scholarship_approved")).intValue()).isZero();
        assertThat(revoked.get("scholarship_type")).isEqualTo("NONE");
        assertThat(((Number) revoked.get("discount_percentage")).doubleValue()).isEqualTo(0.0);
    }

    @Test
    void manualScholarshipFlatGrantUsesScholarshipAmount() {
        createScholarshipTables();
        db.update("""
            INSERT INTO students
                (student_number, scholarship_approved, scholarship_type, scholarship_amount, discount_percentage)
            VALUES ('2026-0001', 0, 'NONE', 0, 0)
            """);

        ScholarEnrollmentService service = new ScholarEnrollmentService(null, null, null, null, null, null);
        ReflectionTestUtils.setField(service, "db", db);

        assertThat(service.grantExternalScholarship("2026-0001", "BARANGAY", 0.0, 2_500.0, "ACTIVE")).isEqualTo("SUCCESS");

        Map<String, Object> granted = db.queryForMap("SELECT scholarship_approved, scholarship_type, scholarship_amount, discount_percentage FROM students WHERE student_number = '2026-0001'");
        assertThat(((Number) granted.get("scholarship_approved")).intValue()).isOne();
        assertThat(granted.get("scholarship_type")).isEqualTo("BARANGAY");
        assertThat(((Number) granted.get("scholarship_amount")).doubleValue()).isEqualTo(2_500.0);
        assertThat(((Number) granted.get("discount_percentage")).doubleValue()).isEqualTo(0.0);

        Double discount = ReflectionTestUtils.invokeMethod(service, "computeScholarDiscount", "2026-0001", 12_000.0);
        assertThat(discount).isEqualTo(2_500.0);
    }

    @Test
    void scholarshipTypeCatalogCanSaveConfigurableManualGrantDefaults() {
        createScholarshipTables();

        ScholarEnrollmentService service = new ScholarEnrollmentService(null, null, null, null, null, null);
        ReflectionTestUtils.setField(service, "db", db);

        service.saveScholarshipType("city grant", "City Grant", "FLAT", 0.0, 7_500.0, false, true);

        Map<String, Object> type = db.queryForMap("SELECT classification, display_name, discount_mode, default_scholarship_amount, is_active FROM scholarship_types WHERE classification = 'CITY_GRANT'");
        assertThat(type.get("display_name")).isEqualTo("City Grant");
        assertThat(type.get("discount_mode")).isEqualTo("FLAT");
        assertThat(((Number) type.get("default_scholarship_amount")).doubleValue()).isEqualTo(7_500.0);
        assertThat(((Number) type.get("is_active")).intValue()).isOne();
    }

    @Test
    void academicScholarshipEligibilityUsesRegistrarFinalGrades() {
        createScholarshipTables();
        db.execute("""
            CREATE TABLE sys_users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(100),
                real_name VARCHAR(100)
            )
            """);
        db.execute("""
            CREATE TABLE academic_terms (
                term_id INT PRIMARY KEY,
                term_name VARCHAR(100),
                status VARCHAR(20),
                is_active TINYINT DEFAULT 0
            )
            """);
        db.execute("""
            CREATE TABLE class_sections (
                section_id INT PRIMARY KEY,
                term_id INT NOT NULL,
                course_id INT NULL
            )
            """);
        db.execute("""
            CREATE TABLE courses (
                course_id INT PRIMARY KEY,
                credit_units DECIMAL(5,2) NOT NULL
            )
            """);
        db.update("INSERT INTO academic_terms (term_id, term_name, status, is_active) VALUES (15, 'A.Y. 2027-2028 - 2nd Semester', 'ACTIVE', 1)");
        db.update("INSERT INTO class_sections (section_id, term_id) VALUES (501, 15)");
        db.update("INSERT INTO courses (course_id, credit_units) VALUES (100, 15), (101, 12)");
        db.update("""
            INSERT INTO students
                (student_number, real_name, program_code, scholarship_approved, scholarship_type, scholarship_amount, discount_percentage)
            VALUES
                ('2026-0001', 'Eligible Student', 'BSIT', 0, 'NONE', 0, 0),
                ('2026-0002', 'Failed Student', 'BSIT', 0, 'NONE', 0, 0)
            """);
        db.update("""
            INSERT INTO grades
                (student_id, course_id, section_id, status, remarks, semestral_grade, registrar_final_remarks, registrar_final_grade)
            VALUES
                ('2026-0001', 100, 501, 'SUBMITTED', 'Failed', 5.00, 'Passed', 1.50),
                ('2026-0001', 101, 501, 'SUBMITTED', 'Passed', 1.75, NULL, NULL),
                ('2026-0002', 100, 501, 'SUBMITTED', 'Passed', 1.50, 'Failed', 5.00)
            """);

        ScholarEnrollmentService service = new ScholarEnrollmentService(null, null, null, null, null, null);
        ReflectionTestUtils.setField(service, "db", db);

        List<Map<String, Object>> candidates = service.evaluateAcademicScholarshipCandidates(15);

        Map<String, Object> eligible = candidates.stream()
            .filter(row -> row.get("student_number").equals("2026-0001"))
            .findFirst()
            .orElseThrow();
        Map<String, Object> failed = candidates.stream()
            .filter(row -> row.get("student_number").equals("2026-0002"))
            .findFirst()
            .orElseThrow();
        assertThat(eligible.get("eligible")).isEqualTo(true);
        assertThat(eligible.get("gwa_fmt")).isEqualTo("1.63");
        assertThat(failed.get("eligible")).isEqualTo(false);
        assertThat((String) failed.get("reason")).contains("failed grade");
    }

    private void createScholarshipTables() {
        db.execute("""
            CREATE TABLE students (
                student_number VARCHAR(100) PRIMARY KEY,
                real_name VARCHAR(100) NULL,
                program_code VARCHAR(20) NULL,
                scholarship_approved TINYINT NULL,
                scholarship_type VARCHAR(50) NULL,
                scholarship_amount DECIMAL(10,2) NULL,
                discount_percentage DECIMAL(5,2) NULL
            )
            """);
        db.execute("""
            CREATE TABLE grades (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                student_id VARCHAR(100) NOT NULL,
                course_id INT NOT NULL,
                section_id INT NULL,
                status VARCHAR(20) NULL,
                remarks VARCHAR(30) NULL,
                semestral_grade DECIMAL(5,2) NULL,
                registrar_final_grade DECIMAL(5,2) NULL,
                registrar_final_remarks VARCHAR(30) NULL
            )
            """);
    }
}



