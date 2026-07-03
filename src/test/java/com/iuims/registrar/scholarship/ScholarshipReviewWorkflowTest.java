package com.iuims.registrar.scholarship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.iuims.registrar.core.GlobalTermService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ScholarshipReviewWorkflowTest {

    private JdbcTemplate db;
    private ScholarEnrollmentService service;
    private GlobalTermService globalTermService;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:scholarreview" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        db = new JdbcTemplate(dataSource);
        globalTermService = mock(GlobalTermService.class);
        when(globalTermService.getCurrentStudentTermYear(1)).thenReturn("2025-2026_1st");
        service = new ScholarEnrollmentService(db, null, globalTermService, null, null, null) {
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

    @Test
    void academicEligibilityBlocksGradesThatExceedPeriodCaps() {
        db.update("UPDATE grades SET midterm = 83 WHERE student_id = '2026-0001' AND course_id = 101");

        Map<String, Object> candidate = onlyCandidate();

        assertThat(candidate.get("eligible")).isEqualTo(false);
        assertThat(candidate.get("highest_midterm_fmt")).isEqualTo("2.25");
        assertThat((String) candidate.get("reason")).contains("Midterm grade 2.25 exceeds 2.00");
    }

    @Test
    void academicEligibilityUsesUnitWeightedGwa() {
        db.update("UPDATE courses SET credit_units = 6 WHERE course_id = 109");
        db.update("UPDATE grades SET registrar_final_grade = 2.00, semestral_grade = 2.00 WHERE student_id = '2026-0001' AND course_id = 109");

        Map<String, Object> candidate = onlyCandidate();

        assertThat(candidate.get("eligible")).isEqualTo(true);
        assertThat(candidate.get("gwa_fmt")).isEqualTo("1.60");
        assertThat(candidate.get("completed_units_fmt")).isEqualTo("30");
    }

    @Test
    void academicEligibilityBlocksLatePeNstpForUpperYearStudents() {
        db.update("UPDATE students SET year_level = 3 WHERE student_number = '2026-0001'");
        db.update("UPDATE courses SET course_code = 'PE3 21', course_title = 'PE 3 PATHFit' WHERE course_id = 101");

        Map<String, Object> candidate = onlyCandidate();

        assertThat(candidate.get("eligible")).isEqualTo(false);
        assertThat((String) candidate.get("reason")).contains("PE/NSTP is still being taken in 3rd/4th year");
    }

    @Test
    void findStudentResolvesApplicantReferenceNumberAsUsername() {
        db.execute("""
            CREATE TABLE applicants (
                reference_number VARCHAR(100) PRIMARY KEY,
                first_name VARCHAR(100),
                last_name VARCHAR(100),
                term_year VARCHAR(30),
                program1 VARCHAR(20),
                applicant_status VARCHAR(50)
            )
            """);
        db.update("""
            INSERT INTO applicants (reference_number, first_name, last_name, term_year, program1, applicant_status)
            VALUES ('EAC-0001', 'Maya', 'Santos', '2025-2026_1st', 'BSN', 'QUALIFIED FOR ENROLLMENT')
            """);

        Map<String, Object> found = service.findStudent("EAC-0001");

        assertThat(found).isNotNull();
        assertThat(found.get("username")).isEqualTo("EAC-0001");
        assertThat(found.get("real_name")).isEqualTo("Maya Santos (APPLICANT)");
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

    private Map<String, Object> onlyCandidate() {
        List<Map<String, Object>> candidates = service.evaluateAcademicScholarshipCandidates(15);
        return candidates.stream()
            .filter(row -> row.get("student_number").equals("2026-0001"))
            .findFirst()
            .orElseThrow();
    }

    private void createFixture() {
        db.execute("CREATE TABLE system_settings (setting_key VARCHAR(100) PRIMARY KEY, setting_value VARCHAR(100))");
        Map.of(
            "SCHOLARSHIP_MAX_GWA", "1.75",
            "SCHOLARSHIP_MAX_PRELIM_GRADE", "2.00",
            "SCHOLARSHIP_MAX_MIDTERM_GRADE", "2.00",
            "SCHOLARSHIP_MAX_FINALS_GRADE", "2.00",
            "SCHOLARSHIP_DEFAULT_DISCOUNT_PERCENT", "100",
            "SCHOLARSHIP_MIN_COMPLETED_UNITS", "27",
            "SCHOLARSHIP_DISQUALIFY_INC", "true",
            "SCHOLARSHIP_DISQUALIFY_FAILED", "true"
        ).forEach((key, value) -> db.update("INSERT INTO system_settings VALUES (?, ?)", key, value));

        db.execute("CREATE TABLE sys_users (user_id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100), real_name VARCHAR(100))");
        db.execute("CREATE TABLE academic_terms (term_id INT PRIMARY KEY, term_name VARCHAR(100), status VARCHAR(20), is_active TINYINT)");
        db.execute("CREATE TABLE courses (course_id INT PRIMARY KEY, course_code VARCHAR(30), course_title VARCHAR(120), credit_units DECIMAL(5,2))");
        db.execute("CREATE TABLE class_sections (section_id INT PRIMARY KEY, term_id INT NOT NULL, course_id INT NOT NULL)");
        db.execute("""
            CREATE TABLE students (
                student_number VARCHAR(100) PRIMARY KEY,
                real_name VARCHAR(100),
                program_code VARCHAR(20),
                year_level INT DEFAULT 1,
                semester INT DEFAULT 1,
                scholarship_approved TINYINT DEFAULT 0,
                scholarship_type VARCHAR(50),
                scholarship_amount DECIMAL(10,2),
                discount_percentage DECIMAL(5,2),
                admission_status VARCHAR(40),
                status VARCHAR(40),
                is_active TINYINT DEFAULT 1,
                term_year VARCHAR(40)
            )
            """);
        db.execute("""
            CREATE TABLE grades (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id VARCHAR(100), course_id INT, section_id INT,
                status VARCHAR(20), remarks VARCHAR(30), prelim DECIMAL(5,2), midterm DECIMAL(5,2),
                final_grade DECIMAL(5,2), semestral_grade DECIMAL(5,2),
                registrar_final_grade DECIMAL(5,2), registrar_final_remarks VARCHAR(30)
            )
            """);

        db.update("INSERT INTO academic_terms VALUES (15, 'A.Y. 2027-2028 - 2nd Semester', 'ACTIVE', 1)");
        db.update("INSERT INTO students (student_number, real_name, program_code, scholarship_type, scholarship_amount, discount_percentage) VALUES ('2026-0001', 'Sofia Scholar', 'BSIT', 'NONE', 0, 0)");
        for (int i = 1; i <= 9; i++) {
            int courseId = 100 + i;
            int sectionId = 500 + i;
            db.update("INSERT INTO courses VALUES (?, ?, ?, 3)", courseId, "SCH" + i, "Scholarship Demo Course " + i);
            db.update("INSERT INTO class_sections VALUES (?, 15, ?)", sectionId, courseId);
            db.update("INSERT INTO grades (student_id, course_id, section_id, status, remarks, prelim, midterm, final_grade, semestral_grade) VALUES ('2026-0001', ?, ?, 'SUBMITTED', 'Passed', 1.50, 1.50, 1.50, 1.50)", courseId, sectionId);
        }
    }
}
