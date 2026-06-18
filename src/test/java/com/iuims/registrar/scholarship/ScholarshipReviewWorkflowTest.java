package com.iuims.registrar.scholarship;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ScholarshipReviewWorkflowTest {

    private JdbcTemplate db;
    private ScholarEnrollmentService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:scholarreview" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        db = new JdbcTemplate(dataSource);
        service = new ScholarEnrollmentService(db, null, null, null, null, null) {
            @Override
            public void syncCoreLedgerAssessment(String studentNumber) {
                // Ledger behavior is covered separately; this fixture isolates review-state transitions.
            }
        };
        createFixture();
    }

    @Test
    void approvalDoesNotActivateDiscountUntilPosting() {
        assertThat(service.requestAcademicScholarship("2026-0001", 15, "registrar.one")).isEqualTo("SUCCESS");
        assertThat(reviewStatus()).isEqualTo("PENDING");
        assertThat(scholarshipApproved()).isZero();

        assertThat(service.approveAcademicScholarship("2026-0001", 15, "registrar.two", "Verified")).isEqualTo("SUCCESS");
        assertThat(reviewStatus()).isEqualTo("APPROVED");
        assertThat(scholarshipApproved()).isZero();

        assertThat(service.postAcademicScholarship("2026-0001", 15, "registrar.three")).isEqualTo("SUCCESS");
        assertThat(reviewStatus()).isEqualTo("POSTED");
        assertThat(scholarshipApproved()).isOne();
    }

    @Test
    void rejectedReviewCannotBePosted() {
        assertThat(service.requestAcademicScholarship("2026-0001", 15, "registrar.one")).isEqualTo("SUCCESS");
        assertThat(service.rejectAcademicScholarship("2026-0001", 15, "registrar.two", "Requirements incomplete"))
            .isEqualTo("SUCCESS");

        assertThat(reviewStatus()).isEqualTo("REJECTED");
        assertThat(service.postAcademicScholarship("2026-0001", 15, "registrar.three"))
            .startsWith("ERROR:");
        assertThat(scholarshipApproved()).isZero();
    }

    private String reviewStatus() {
        return db.queryForObject(
            "SELECT status FROM scholarship_review_workflow WHERE student_number = '2026-0001' AND term_id = 15",
            String.class);
    }

    private int scholarshipApproved() {
        Integer value = db.queryForObject(
            "SELECT scholarship_approved FROM students WHERE student_number = '2026-0001'",
            Integer.class);
        return value != null ? value : 0;
    }

    private void createFixture() {
        db.execute("CREATE TABLE system_settings (setting_key VARCHAR(100) PRIMARY KEY, setting_value VARCHAR(100))");
        Map.of(
            "SCHOLARSHIP_MAX_GWA", "1.75",
            "SCHOLARSHIP_MAX_INDIVIDUAL_GRADE", "2.00",
            "SCHOLARSHIP_DEFAULT_DISCOUNT_PERCENT", "100",
            "SCHOLARSHIP_MIN_COMPLETED_UNITS", "27",
            "SCHOLARSHIP_DISQUALIFY_INC", "true",
            "SCHOLARSHIP_DISQUALIFY_FAILED", "true"
        ).forEach((key, value) -> db.update("INSERT INTO system_settings VALUES (?, ?)", key, value));

        db.execute("CREATE TABLE sys_users (user_id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100), real_name VARCHAR(100))");
        db.execute("CREATE TABLE academic_terms (term_id INT PRIMARY KEY, term_name VARCHAR(100), status VARCHAR(20), is_active TINYINT)");
        db.execute("CREATE TABLE courses (course_id INT PRIMARY KEY, credit_units DECIMAL(5,2))");
        db.execute("CREATE TABLE class_sections (section_id INT PRIMARY KEY, term_id INT NOT NULL, course_id INT NOT NULL)");
        db.execute("""
            CREATE TABLE students (
                student_number VARCHAR(100) PRIMARY KEY, real_name VARCHAR(100), program_code VARCHAR(20),
                year_level INT DEFAULT 1, semester INT DEFAULT 1, scholarship_approved TINYINT DEFAULT 0,
                scholarship_type VARCHAR(50), scholarship_amount DECIMAL(10,2), discount_percentage DECIMAL(5,2)
            )
            """);
        db.execute("""
            CREATE TABLE grades (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id VARCHAR(100), course_id INT, section_id INT,
                status VARCHAR(20), remarks VARCHAR(30), semestral_grade DECIMAL(5,2),
                registrar_final_grade DECIMAL(5,2), registrar_final_remarks VARCHAR(30)
            )
            """);

        db.update("INSERT INTO academic_terms VALUES (15, 'A.Y. 2027-2028 - 2nd Semester', 'ACTIVE', 1)");
        db.update("INSERT INTO students (student_number, real_name, program_code, scholarship_type, scholarship_amount, discount_percentage) VALUES ('2026-0001', 'Sofia Scholar', 'BSIT', 'NONE', 0, 0)");
        for (int i = 1; i <= 9; i++) {
            int courseId = 100 + i;
            int sectionId = 500 + i;
            db.update("INSERT INTO courses VALUES (?, 3)", courseId);
            db.update("INSERT INTO class_sections VALUES (?, 15, ?)", sectionId, courseId);
            db.update("INSERT INTO grades (student_id, course_id, section_id, status, remarks, semestral_grade) VALUES ('2026-0001', ?, ?, 'SUBMITTED', 'Passed', 1.50)", courseId, sectionId);
        }
    }
}
