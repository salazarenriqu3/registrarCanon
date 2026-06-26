package com.iuims.registrar.curriculum;

import com.iuims.registrar.forms.RegFormEventService;
import com.iuims.registrar.forms.StudentDocumentTrailService;
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
        db.execute("CREATE TABLE sys_users (user_id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100), real_name VARCHAR(150))");
        db.execute("CREATE TABLE courses (course_id INT PRIMARY KEY, course_code VARCHAR(100))");
        db.execute("CREATE TABLE curriculum_courses (curriculum_id INT NOT NULL, course_id INT NOT NULL)");
        db.execute("CREATE TABLE student_curriculum_assignments (assignment_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_number VARCHAR(100), curriculum_id INT, program_code VARCHAR(100), is_current TINYINT(1), assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE grades (id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id VARCHAR(100), course_id INT, remarks VARCHAR(50), registrar_final_grade DECIMAL(5,2), semestral_grade DECIMAL(5,2), status VARCHAR(40))");

        db.update("INSERT INTO students (student_number, real_name) VALUES ('2026-1001', 'Clarissa Reyes')");
        db.update("INSERT INTO sys_users (username, real_name) VALUES ('2026-1001', 'Clarissa Reyes')");
        db.update("INSERT INTO courses (course_id, course_code) VALUES (101, 'AECO 11')");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id) VALUES (9001, 101)");
        db.update("INSERT INTO student_curriculum_assignments (student_number, curriculum_id, program_code, is_current) VALUES ('2026-1001', 9001, 'BSIT', 1)");

        RegFormEventService regFormEventService = new RegFormEventService(db);
        StudentDocumentTrailService trailService = new StudentDocumentTrailService(db);
        StudentCurriculumService studentCurriculumService = new StudentCurriculumService(db, regFormEventService);
        transferCreditGradePort = mock(TransferCreditGradePort.class);
        service = new CreditGradeService(db, studentCurriculumService, regFormEventService, trailService, transferCreditGradePort);
    }

    @Test
    void submitRequestStoresPendingRowAndMirrorsTrail() {
        CreditGradeService.CreditRequestActionResult result = service.submitCreditRequest(
            "2026-1001",
            101,
            1.75,
            "Prior College",
            "TOR 2024",
            "registrar.one");

        assertThat(result.ok()).isTrue();
        assertThat(result.requestId()).isNotNull();

        Map<String, Object> request = db.queryForMap(
            "SELECT status, source_school, note FROM transfer_credit_requests WHERE request_id = ?",
            result.requestId());
        assertThat(request.get("status")).isEqualTo("PENDING");
        assertThat(request.get("source_school")).isEqualTo("Prior College");
        assertThat(request.get("note")).isEqualTo("TOR 2024");

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
            "registrar.one");

        CreditGradeService.CreditRequestActionResult approved =
            service.approveCreditRequest(created.requestId(), "registrar.two");

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
    void rejectRequestLeavesAuditTrail() {
        CreditGradeService.CreditRequestActionResult created = service.submitCreditRequest(
            "2026-1001",
            101,
            null,
            "Prior College",
            null,
            "registrar.one");

        CreditGradeService.CreditRequestActionResult rejected =
            service.rejectCreditRequest(created.requestId(), "registrar.two", "Course description does not match.");

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
}
