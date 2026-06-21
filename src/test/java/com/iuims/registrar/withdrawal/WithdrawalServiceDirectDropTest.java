package com.iuims.registrar.withdrawal;

import com.iuims.registrar.core.EnlistmentSchemaService;
import com.iuims.registrar.core.GlobalTermService;
import com.iuims.registrar.forms.RegFormEventService;
import com.iuims.registrar.forms.StudentDocumentTrailService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WithdrawalServiceDirectDropTest {

    private JdbcTemplate db;
    private ScholarEnrollmentService enrollmentService;
    private WithdrawalService service;

    @BeforeEach
    void setUp() {
        db = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:withdrawal-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        createSchema();

        enrollmentService = mock(ScholarEnrollmentService.class);
        when(enrollmentService.tuitionRatePerUnit(any())).thenReturn(1000.0);
        doAnswer(invocation -> {
            Long enlistmentId = invocation.getArgument(0, Long.class);
            db.update("DELETE FROM student_enlistments WHERE enlistment_id = ?", new Object[]{enlistmentId});
            return null;
        }).when(enrollmentService).dropSubjectByEnlistmentId(anyLong(), anyDouble(), any());

        GlobalTermService globalTermService = mock(GlobalTermService.class);
        when(globalTermService.getCurrentTermId()).thenReturn(1);
        EnlistmentSchemaService enlistmentSchemaService = mock(EnlistmentSchemaService.class);
        when(enlistmentSchemaService.enlistmentStatusFilter(
            EnlistmentSchemaService.Scope.COMMITTED_ONLY, "se")).thenReturn("");

        service = new WithdrawalService(
            db, enrollmentService, mock(StudentDocumentTrailService.class),
            mock(RegFormEventService.class), globalTermService, enlistmentSchemaService);
    }

    @Test
    void singleSubjectDropCannotRemoveTheLastCurrentTermClass() {
        seedStudentWithSubjects(1);

        assertThatThrownBy(() -> service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "Registrar case 12", "registrar"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("last current-term subject");

        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_enlistments", Integer.class)).isEqualTo(1);
        verify(enrollmentService, never()).dropSubjectByEnlistmentId(anyLong(), anyDouble(), any());
    }

    @Test
    void singleSubjectDropRemovesOnlyTheSelectedClassAndRecordsCompletion() {
        seedStudentWithSubjects(2);

        WithdrawalService.DirectDropResult result = service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "Registrar case 13", "registrar");

        assertThat(result.subjectsDropped()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_enlistments", Integer.class)).isEqualTo(1);
        assertThat(db.queryForMap(
            "SELECT status, withdrawal_scope, subject_count, approval_source " +
                "FROM student_withdrawal_requests WHERE request_id = ?", result.requestId()))
            .containsEntry("STATUS", "APPROVED")
            .containsEntry("WITHDRAWAL_SCOPE", "SINGLE_SUBJECT")
            .containsEntry("SUBJECT_COUNT", 1)
            .containsEntry("APPROVAL_SOURCE", "REGISTRAR_DIRECT");
    }

    @Test
    void fullStudentDropRemovesCurrentLoadAndMarksStudentWithdrawn() {
        seedStudentWithSubjects(2);

        WithdrawalService.DirectDropResult result = service.dropStudentByRegistrar(
            "2026-0001", "TRANSFER", "Registrar case 14", "registrar");

        assertThat(result.subjectsDropped()).isEqualTo(2);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_enlistments", Integer.class)).isZero();
        assertThat(db.queryForObject(
            "SELECT admission_status FROM students WHERE student_number = '2026-0001'", String.class))
            .isEqualTo("WITHDRAWN");
        assertThat(db.queryForObject(
            "SELECT is_active FROM students WHERE student_number = '2026-0001'", Integer.class))
            .isZero();
        assertThat(db.queryForObject(
            "SELECT admission_status FROM sys_users WHERE username = '2026-0001'", String.class))
            .isEqualTo("WITHDRAWN");
        assertThat(db.queryForObject(
            "SELECT applicant_status FROM applicants WHERE reference_number = 'REF-1'", String.class))
            .isEqualTo("WITHDRAWN");
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_withdrawal_request_lines WHERE request_id = ?", Integer.class,
            result.requestId())).isEqualTo(2);
        assertThat(db.queryForMap(
            "SELECT status, withdrawal_scope, subject_count FROM student_withdrawal_requests WHERE request_id = ?",
            result.requestId()))
            .containsEntry("STATUS", "APPROVED")
            .containsEntry("WITHDRAWAL_SCOPE", "FULL_CURRENT_TERM")
            .containsEntry("SUBJECT_COUNT", 2);
    }

    @Test
    void classWithdrawalWaitsForRegistrarAndKeepsOneAuditLine() {
        seedStudentWithSubjects(2);

        long requestId = service.createRequest(
            "2026-0001", 101, "ACADEMIC_LOAD", "Registrar class review", "records.registrar");

        assertThat(db.queryForMap(
            "SELECT status, withdrawal_scope, subject_count, approval_source " +
                "FROM student_withdrawal_requests WHERE request_id = ?", requestId))
            .containsEntry("STATUS", "PENDING_REGISTRAR")
            .containsEntry("WITHDRAWAL_SCOPE", "SINGLE_SUBJECT")
            .containsEntry("SUBJECT_COUNT", 1)
            .containsEntry("APPROVAL_SOURCE", "REGISTRAR_WORKFLOW");
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_withdrawal_request_lines WHERE request_id = ?",
            Integer.class, requestId)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_enlistments", Integer.class)).isEqualTo(2);

        WithdrawalService.DirectDropResult result =
            service.approveAndExecuteRequest(requestId, "chief.registrar");

        assertThat(result.subjectsDropped()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_enlistments", Integer.class)).isEqualTo(1);
        assertThat(db.queryForMap(
            "SELECT status, registrar_approved_by, registrar_approved_at, completed_at " +
                "FROM student_withdrawal_requests WHERE request_id = ?", requestId))
            .containsEntry("STATUS", "APPROVED")
            .containsEntry("REGISTRAR_APPROVED_BY", "chief.registrar");
    }

    @Test
    void fullStudentRequestSnapshotsEveryClassBeforeRegistrarApproval() {
        seedStudentWithSubjects(3);

        long requestId = service.createFullCurrentTermRequest(
            "2026-0001", "TRANSFER", "Registrar full-student review", "records.registrar");

        assertThat(db.queryForMap(
            "SELECT status, withdrawal_scope, subject_count FROM student_withdrawal_requests WHERE request_id = ?",
            requestId))
            .containsEntry("STATUS", "PENDING_REGISTRAR")
            .containsEntry("WITHDRAWAL_SCOPE", "FULL_CURRENT_TERM")
            .containsEntry("SUBJECT_COUNT", 3);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_withdrawal_request_lines WHERE request_id = ?",
            Integer.class, requestId)).isEqualTo(3);

        service.approveAndExecuteRequest(requestId, "chief.registrar");

        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_enlistments", Integer.class)).isZero();
        assertThat(db.queryForObject(
            "SELECT admission_status FROM students WHERE student_number = '2026-0001'", String.class))
            .isEqualTo("WITHDRAWN");
    }

    private void createSchema() {
        db.execute("CREATE TABLE enrollment_settings (setting_key VARCHAR(80) PRIMARY KEY, setting_value VARCHAR(500))");
        db.execute("CREATE TABLE academic_term_policies (term_id INT PRIMARY KEY, midterm_exam_date DATE)");
        db.execute("CREATE TABLE academic_terms (term_id INT PRIMARY KEY, start_date DATE, end_date DATE)");
        db.execute("CREATE TABLE students (student_number VARCHAR(100) PRIMARY KEY, reference_number VARCHAR(100), admission_status VARCHAR(40), status VARCHAR(40) DEFAULT 'ACTIVE', is_active INT DEFAULT 1, enrollment_blocked INT DEFAULT 0)");
        db.execute("CREATE TABLE sys_users (username VARCHAR(100) PRIMARY KEY, admission_status VARCHAR(40), status VARCHAR(40) DEFAULT 'ACTIVE', is_active INT DEFAULT 1, enrollment_blocked INT DEFAULT 0)");
        db.execute("CREATE TABLE applicants (reference_number VARCHAR(100) PRIMARY KEY, applicant_status VARCHAR(40), updated_at TIMESTAMP)");
        db.execute("CREATE TABLE courses (course_id INT PRIMARY KEY, course_code VARCHAR(40), course_title VARCHAR(160), credit_units DECIMAL(5,2))");
        db.execute("CREATE TABLE class_sections (section_id INT PRIMARY KEY, course_id INT, term_id INT, section_code VARCHAR(40))");
        db.execute("CREATE TABLE student_enlistments (enlistment_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id VARCHAR(100), course_id INT, section_id INT, enlisted_date TIMESTAMP)");
        db.update("INSERT INTO academic_term_policies (term_id, midterm_exam_date) VALUES (1, DATE '2099-01-01')");
        db.update("INSERT INTO academic_terms VALUES (1, DATE '2026-01-01', DATE '2099-12-31')");
    }

    private void seedStudentWithSubjects(int subjectCount) {
        db.update("INSERT INTO students (student_number, reference_number, admission_status) VALUES ('2026-0001', 'REF-1', 'ENROLLED')");
        db.update("INSERT INTO sys_users (username, admission_status) VALUES ('2026-0001', 'ENROLLED')");
        db.update("INSERT INTO applicants VALUES ('REF-1', 'ENROLLED', CURRENT_TIMESTAMP)");
        for (int index = 1; index <= subjectCount; index++) {
            int courseId = index;
            int sectionId = 100 + index;
            db.update("INSERT INTO courses VALUES (?, ?, ?, 3)", courseId, "C" + index, "Course " + index);
            db.update("INSERT INTO class_sections VALUES (?, ?, 1, ?)", sectionId, courseId, "S" + index);
            db.update("""
                INSERT INTO student_enlistments (student_id, course_id, section_id, enlisted_date)
                VALUES ('2026-0001', ?, ?, ?)
                """, courseId, sectionId, Timestamp.valueOf(LocalDateTime.now()));
        }
    }
}
