package com.iuims.registrar.service.curriculum;
import com.iuims.registrar.domain.curriculum.TransferCreditGradePort;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Student;

import com.iuims.registrar.service.forms.RegFormEventService;
import com.iuims.registrar.service.forms.StudentDocumentTrailService;
import com.iuims.registrar.service.support.StudentProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CreditGradeServiceApprovalWorkflowTest {

    private JdbcTemplate db;
    private CreditGradeService service;
    private TransferCreditGradePort transferCreditGradePort;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:creditwf" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        db = new JdbcTemplate(dataSource);

        db.execute("CREATE TABLE students (student_number VARCHAR(100) PRIMARY KEY, real_name VARCHAR(150))");
        db.execute("CREATE TABLE sys_users (user_id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100), real_name VARCHAR(150), role VARCHAR(50))");
        db.execute("CREATE TABLE courses (course_id INT PRIMARY KEY, course_code VARCHAR(100))");
        db.execute("CREATE TABLE curriculum_courses (curriculum_id INT NOT NULL, course_id INT NOT NULL)");
        db.execute("CREATE TABLE course_equivalencies (course_id INT NOT NULL, equivalent_course_id INT NOT NULL)");
        db.execute("CREATE TABLE student_curriculum_assignments (assignment_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_number VARCHAR(100), curriculum_id INT, program_code VARCHAR(100), is_current TINYINT(1), assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE grades (id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id VARCHAR(100), course_id INT, remarks VARCHAR(50), registrar_final_grade DECIMAL(5,2), semestral_grade DECIMAL(5,2), status VARCHAR(40))");
        db.execute("CREATE TABLE tentative_credited_subject (tentative_credit_id BIGINT PRIMARY KEY, posting_status VARCHAR(32), posted_by VARCHAR(100), posted_at TIMESTAMP NULL, posted_grade_id INT NULL)");
        db.execute("CREATE TABLE applicant_credit_accreditation_lines (line_id BIGINT PRIMARY KEY, registrar_decision_status VARCHAR(32), registrar_decided_by VARCHAR(100), registrar_decided_at TIMESTAMP NULL, registrar_decision_note VARCHAR(500), registrar_posted_grade_id INT NULL)");

        db.update("INSERT INTO students (student_number, real_name) VALUES ('2026-1001', 'Clarissa Reyes')");
        db.update("INSERT INTO sys_users (username, real_name, role) VALUES ('2026-1001', 'Clarissa Reyes', 'Student')");
        db.update("INSERT INTO sys_users (username, real_name, role) VALUES ('dean.one', 'Dean One', 'Dean')");
        db.update("INSERT INTO sys_users (username, real_name, role) VALUES ('registrar.one', 'Registrar One', 'Registrar')");
        db.update("INSERT INTO sys_users (username, real_name, role) VALUES ('registrar.two', 'Registrar Two', 'Registrar')");
        db.update("INSERT INTO courses (course_id, course_code) VALUES (101, 'AECO 11')");
        db.update("INSERT INTO courses (course_id, course_code) VALUES (202, 'HUM 101')");
        db.update("INSERT INTO course_equivalencies (course_id, equivalent_course_id) VALUES (101, 202)");
        db.update("INSERT INTO course_equivalencies (course_id, equivalent_course_id) VALUES (202, 101)");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id) VALUES (9001, 101)");
        db.update("INSERT INTO student_curriculum_assignments (student_number, curriculum_id, program_code, is_current) VALUES ('2026-1001', 9001, 'BSIT', 1)");

        StudentProfileService studentProfileService = new StudentProfileService(db);
        RegFormEventService regFormEventService = new RegFormEventService(db, studentProfileService);
        StudentDocumentTrailService trailService = new StudentDocumentTrailService(db, studentProfileService);
        StudentCurriculumService studentCurriculumService = new StudentCurriculumService(db, regFormEventService);
        transferCreditGradePort = mock(TransferCreditGradePort.class);
        service = new CreditGradeService(db, studentCurriculumService, regFormEventService, trailService, transferCreditGradePort);
        service.ensureSchema();
    }

    @Test
    void submitRequestStoresPendingRowAndMirrorsTrail() {
        CreditGradeService.CreditRequestActionResult result = service.submitCreditRequest(
            "2026-1001",
            101,
            1.75,
            "Prior College",
            "TOR 2024",
            "dean.one",
            "Dean");

        assertThat(result.ok()).isTrue();
        assertThat(result.requestId()).isNotNull();

        Map<String, Object> request = db.queryForMap(
            "SELECT status, source_school, note, requested_by_role FROM transfer_credit_requests WHERE request_id = ?",
            result.requestId());
        assertThat(request.get("status")).isEqualTo("PENDING");
        assertThat(request.get("source_school")).isEqualTo("Prior College");
        assertThat(request.get("note")).isEqualTo("TOR 2024");
        assertThat(request.get("requested_by_role")).isEqualTo("Dean");

        Integer regFormCount = db.queryForObject(
            "SELECT COUNT(*) FROM student_reg_form_events WHERE student_number = '2026-1001' AND event_type = 'TRANSFER_CREDIT_REQUESTED'",
            Integer.class);
        assertThat(regFormCount).isEqualTo(1);

        Integer trailCount = db.queryForObject(
            "SELECT COUNT(*) FROM student_document_events WHERE student_number = '2026-1001' AND event_type = 'TRANSFER_CREDIT_REQUESTED'",
            Integer.class);
        assertThat(trailCount).isEqualTo(1);
    }

    @Test
    void approveRequestPostsCreditAndMarksRequestApproved() {
        CreditGradeService.CreditRequestActionResult created = service.submitCreditRequest(
            "2026-1001",
            101,
            1.50,
            "Prior College",
            "Evaluator matched syllabus",
            "dean.one",
            "Dean");

        CreditGradeService.CreditRequestActionResult approved =
            service.approveCreditRequest(created.requestId(), "registrar.two", "Registrar");

        assertThat(approved.ok()).isTrue();

        Map<String, Object> request = db.queryForMap(
            "SELECT status, approved_by FROM transfer_credit_requests WHERE request_id = ?",
            created.requestId());
        assertThat(request.get("status")).isEqualTo("APPROVED");
        assertThat(request.get("approved_by")).isEqualTo("registrar.two");

        verify(transferCreditGradePort, times(1)).saveTransferCredit(
            eq("2026-1001"),
            eq(101),
            eq("Clarissa Reyes"),
            any(String.class),
            eq(BigDecimal.valueOf(1.5)));
    }

    @Test
    void submitRequestAllowsEquivalentCourseMappedToAssignedCurriculum() {
        CreditGradeService.CreditRequestActionResult result = service.submitCreditRequest(
            "2026-1001",
            202,
            1.75,
            "Prior College",
            "Equivalent humanities course",
            "dean.one",
            "Dean");

        assertThat(result.ok()).isTrue();
        Map<String, Object> request = db.queryForMap(
            "SELECT course_id, course_code, status FROM transfer_credit_requests WHERE request_id = ?",
            result.requestId());
        assertThat(request.get("course_id")).isEqualTo(202);
        assertThat(request.get("course_code")).isEqualTo("HUM 101");
        assertThat(request.get("status")).isEqualTo("PENDING");
    }

    @Test
    void approveRequestMarksTentativeEnrollmentSourcePosted() {
        db.update("INSERT INTO tentative_credited_subject (tentative_credit_id, posting_status) VALUES (501, 'READY_FOR_POSTING')");
        db.update("""
            INSERT INTO transfer_credit_requests
                (request_id, student_number, curriculum_id, course_id, course_code, numeric_grade, status,
                 requested_by, requested_by_role, source_system, source_table, source_row_id)
            VALUES (77, '2026-1001', 9001, 101, 'AECO 11', 1.50, 'PENDING',
                    'dean.one', 'Dean', 'ENROLLMENT3', 'tentative_credited_subject', '501')
            """);

        CreditGradeService.CreditRequestActionResult approved =
            service.approveCreditRequest(77L, "registrar.two", "Registrar");

        assertThat(approved.ok()).isTrue();
        Map<String, Object> source = db.queryForMap(
            "SELECT posting_status, posted_by FROM tentative_credited_subject WHERE tentative_credit_id = 501");
        assertThat(source.get("posting_status")).isEqualTo("POSTED");
        assertThat(source.get("posted_by")).isEqualTo("registrar.two");
    }

    @Test
    void rejectRequestLeavesAuditTrail() {
        CreditGradeService.CreditRequestActionResult created = service.submitCreditRequest(
            "2026-1001",
            101,
            null,
            "Prior College",
            null,
            "dean.one",
            "Dean");

        CreditGradeService.CreditRequestActionResult rejected =
            service.rejectCreditRequest(created.requestId(), "registrar.two", "Registrar", "Course description does not match.");

        assertThat(rejected.ok()).isTrue();

        Map<String, Object> request = db.queryForMap(
            "SELECT status, rejected_by, rejection_reason FROM transfer_credit_requests WHERE request_id = ?",
            created.requestId());
        assertThat(request.get("status")).isEqualTo("REJECTED");
        assertThat(request.get("rejected_by")).isEqualTo("registrar.two");
        assertThat(request.get("rejection_reason")).isEqualTo("Course description does not match.");

        Integer trailCount = db.queryForObject(
            "SELECT COUNT(*) FROM student_document_events WHERE student_number = '2026-1001' AND event_type = 'TRANSFER_CREDIT_REJECTED'",
            Integer.class);
        assertThat(trailCount).isEqualTo(1);
    }

    @Test
    void rejectRequestMarksApplicantPacketLineRejected() {
        db.update("INSERT INTO applicant_credit_accreditation_lines (line_id) VALUES (88)");
        db.update("""
            INSERT INTO transfer_credit_requests
                (request_id, student_number, curriculum_id, course_id, course_code, status,
                 requested_by, requested_by_role, source_system, source_table, source_row_id)
            VALUES (78, '2026-1001', 9001, 101, 'AECO 11', 'PENDING',
                    'dean.one', 'Dean', 'ENROLLMENT3', 'applicant_credit_accreditation_lines', '88')
            """);

        CreditGradeService.CreditRequestActionResult rejected =
            service.rejectCreditRequest(78L, "registrar.two", "Registrar", "Course description does not match.");

        assertThat(rejected.ok()).isTrue();
        Map<String, Object> source = db.queryForMap(
            "SELECT registrar_decision_status, registrar_decided_by, registrar_decision_note FROM applicant_credit_accreditation_lines WHERE line_id = 88");
        assertThat(source.get("registrar_decision_status")).isEqualTo("REJECTED");
        assertThat(source.get("registrar_decided_by")).isEqualTo("registrar.two");
        assertThat(source.get("registrar_decision_note")).isEqualTo("Course description does not match.");
    }

    @Test
    void submitRequestRejectsNonDeanOriginator() {
        CreditGradeService.CreditRequestActionResult result = service.submitCreditRequest(
            "2026-1001",
            101,
            1.75,
            "Prior College",
            "TOR 2024",
            "registrar.one",
            "Registrar");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("must be submitted by the Enrollment Dean");
    }

    @Test
    void approveRequestRejectsNonDeanOriginatedRow() {
        CreditGradeService.CreditRequestActionResult created = service.submitCreditRequest(
            "2026-1001",
            101,
            1.50,
            "Prior College",
            "Evaluator matched syllabus",
            "dean.one",
            "Dean");
        db.update(
            "UPDATE transfer_credit_requests SET requested_by = ?, requested_by_role = ? WHERE request_id = ?",
            "registrar.one",
            "Registrar",
            created.requestId());

        CreditGradeService.CreditRequestActionResult approved =
            service.approveCreditRequest(created.requestId(), "registrar.two", "Registrar");

        assertThat(approved.ok()).isFalse();
        assertThat(approved.message()).contains("Dean-submitted");
    }
}
