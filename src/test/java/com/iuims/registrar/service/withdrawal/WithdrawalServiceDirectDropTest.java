package com.iuims.registrar.service.withdrawal;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Program;
import com.iuims.registrar.entity.Student;

import com.iuims.registrar.service.support.EnlistmentSchemaService;
import com.iuims.registrar.service.support.GlobalTermService;
import com.iuims.registrar.service.support.StudentProfileService;
import com.iuims.registrar.service.finance.TermFeeAdminService;
import com.iuims.registrar.service.forms.RegFormEventService;
import com.iuims.registrar.service.forms.StudentDocumentTrailService;
import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WithdrawalServiceDirectDropTest {

    private static final String DEMO_USER = "demo.registrar";

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
        doAnswer(invocation -> {
            Long enlistmentId = invocation.getArgument(0, Long.class);
            db.update("DELETE FROM student_enlistments WHERE enlistment_id = ?", new Object[]{enlistmentId});
            return null;
        }).when(enrollmentService).dropSubjectByEnlistmentId(anyLong(), anyBoolean());
        doAnswer(invocation -> null)
            .when(enrollmentService).postFlatDropCharge(any(), anyDouble(), any());

        GlobalTermService globalTermService = mock(GlobalTermService.class);
        when(globalTermService.getCurrentTermId()).thenReturn(1);
        EnlistmentSchemaService enlistmentSchemaService = mock(EnlistmentSchemaService.class);
        when(enlistmentSchemaService.enlistmentStatusFilter(
            EnlistmentSchemaService.Scope.COMMITTED_ONLY, "se")).thenReturn("");
        StudentProfileService studentProfileService = new StudentProfileService(db);
        TermFeeAdminService termFeeAdminService = mock(TermFeeAdminService.class);
        when(termFeeAdminService.resolveProgramId(any())).thenReturn(1);
        when(termFeeAdminService.getFeeRatesForScope(anyInt(), any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            Map<String, Double> rates = new HashMap<>();
            rates.put("TUITION_PER_UNIT", 1000.0);
            rates.put("LEC_FEE_PER_UNIT", 1000.0);
            rates.put("LAB_FEE_PER_UNIT", 200.0);
            rates.put("COMP_FEE_PER_UNIT", 500.0);
            rates.put("RLE_FEE_PER_UNIT", 700.0);
            rates.put("OTHER_ADD_DROP", 250.0);
            return rates;
        });

        db.update("INSERT INTO enrollment_settings (setting_key, setting_value) VALUES ('subject_drop_flat_fee', '250.00')");
        db.update("INSERT INTO enrollment_settings (setting_key, setting_value) VALUES ('shift_cleanup_flat_fee', '175.00')");

        service = new WithdrawalService(
            db, enrollmentService, mock(StudentDocumentTrailService.class),
            mock(RegFormEventService.class), globalTermService, enlistmentSchemaService,
            studentProfileService, termFeeAdminService);
        service.ensureSchema();
    }

    @Test
    void singleSubjectDropCanRemoveTheLastCurrentTermClassWithoutWithdrawingStudent() {
        seedStudentWithSubjects(1);

        WithdrawalService.DirectDropResult result = service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "Registrar case 12", "registrar");

        assertThat(result.subjectsDropped()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_enlistments", Integer.class)).isZero();
        assertThat(db.queryForObject(
            "SELECT admission_status FROM students WHERE student_number = '2026-0001'", String.class))
            .isEqualTo("ENROLLED");
        assertThat(db.queryForObject(
            "SELECT is_active FROM students WHERE student_number = '2026-0001'", Integer.class))
            .isEqualTo(1);
        assertThat(db.queryForMap(
            "SELECT status, withdrawal_scope, subject_count, approval_source " +
                "FROM student_withdrawal_requests WHERE request_id = ?", result.requestId()))
            .containsEntry("STATUS", "APPROVED")
            .containsEntry("WITHDRAWAL_SCOPE", "SINGLE_SUBJECT")
            .containsEntry("SUBJECT_COUNT", 1)
            .containsEntry("APPROVAL_SOURCE", "REGISTRAR_DIRECT");
    }

    @Test
    void labSubjectDropUsesLabRateWhenCourseHasLabUnits() {
        seedStudentWithCustomSubject(1, "LAB-101", "Laboratory Course", 2, 2, 1, "LAB", 0);

        WithdrawalService.DirectDropResult result = service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "Lab component check", DEMO_USER);

        assertThat(result.subjectsDropped()).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT estimated_charge FROM student_withdrawal_requests WHERE request_id = ?",
            Double.class, result.requestId()))
            .isEqualTo(250.0);
    }

    @Test
    void singleSubjectDropRejectsStudentsWhoHaveNotReachedEnrolledStatus() {
        db.update("INSERT INTO students (student_number, reference_number, admission_status) VALUES ('2026-0001', 'REF-1', 'ADMITTED')");

        assertThatThrownBy(() -> service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "Admitted guard check", DEMO_USER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Current-term subject drop is available only for ENROLLED students.");
    }

    @Test
    void singleSubjectDropRejectsAccountingBlockedStudents() {
        seedStudentWithSubjects(1);
        when(enrollmentService.hasAccountingBlock("2026-0001")).thenReturn(true);

        assertThatThrownBy(() -> service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "Accounting block guard", DEMO_USER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Enrollment is blocked until the prior-term forwarded balance is settled at Cashier first.");
    }

    @Test
    void singleSubjectDropRejectsStudentsWithPendingOverpaymentDisposition() {
        seedStudentWithSubjects(1);
        when(enrollmentService.hasUnresolvedPendingCredit("2026-0001")).thenReturn(true);

        assertThatThrownBy(() -> service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "Pending credit guard", DEMO_USER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Enrollment is blocked until the prior-term overpayment is resolved.");
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
        assertThat(db.queryForMap(
            "SELECT status, completed_at FROM student_withdrawal_request_lines WHERE request_id = ?",
            result.requestId()))
            .containsEntry("STATUS", "APPROVED");
    }

    @Test
    void singleSubjectDropBundlesEnrolledCorequisitesIntoOneTransaction() {
        seedStudentWithSubjects(2);
        db.update("INSERT INTO course_corequisites (course_id, corequisite_course_id) VALUES (1, 2)");
        db.update("INSERT INTO course_corequisites (course_id, corequisite_course_id) VALUES (2, 1)");

        WithdrawalService.DirectDropResult result = service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "Bundled lab/lec drop", DEMO_USER);

        assertThat(result.subjectsDropped()).isEqualTo(2);
        assertThat(result.totalCharge()).isEqualTo(250.0);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_enlistments", Integer.class)).isZero();
        assertThat(db.queryForMap(
            "SELECT status, withdrawal_scope, subject_count FROM student_withdrawal_requests WHERE request_id = ?",
            result.requestId()))
            .containsEntry("STATUS", "APPROVED")
            .containsEntry("WITHDRAWAL_SCOPE", "SINGLE_SUBJECT")
            .containsEntry("SUBJECT_COUNT", 2);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_withdrawal_request_lines WHERE request_id = ?",
            Integer.class, result.requestId())).isEqualTo(2);
        verify(enrollmentService, org.mockito.Mockito.times(2)).dropSubjectByEnlistmentId(anyLong(), anyDouble(), any());
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
        assertThat(db.queryForMap(
            "SELECT archived_student_number, archive_status, admission_status FROM student_identity_archive WHERE archive_key = (" +
                "SELECT archive_key FROM students WHERE student_number = '2026-0001')"))
            .containsEntry("ARCHIVED_STUDENT_NUMBER", "2026-0001")
            .containsEntry("ARCHIVE_STATUS", "WITHDRAWN_RECORD")
            .containsEntry("ADMISSION_STATUS", "WITHDRAWN");
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
    void directClassWithdrawalArchivesAsCompletedAndKeepsOneAuditLine() {
        seedStudentWithSubjects(2);

        WithdrawalService.DirectDropResult result = service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "Registrar class review", DEMO_USER);

        assertThat(db.queryForMap(
            "SELECT status, withdrawal_scope, subject_count, approval_source " +
                "FROM student_withdrawal_requests WHERE request_id = ?", result.requestId()))
            .containsEntry("STATUS", "APPROVED")
            .containsEntry("WITHDRAWAL_SCOPE", "SINGLE_SUBJECT")
            .containsEntry("SUBJECT_COUNT", 1)
            .containsEntry("APPROVAL_SOURCE", "REGISTRAR_DIRECT");
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_withdrawal_request_lines WHERE request_id = ?",
            Integer.class, result.requestId())).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_enlistments", Integer.class)).isEqualTo(1);
    }

    @Test
    void firstWeekWithdrawalCharges25PercentAndRefunds75Percent() {
        seedStudentWithSubjects(2, 0);

        long requestId = service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "First week policy", DEMO_USER).requestId();

        assertPolicyLine(requestId, "FLAT", 100.0, 250.0);
    }

    @Test
    void secondAndThirdWeekWithdrawalCharges50PercentAndRefunds50Percent() {
        seedStudentWithSubjects(2, 8);

        long requestId = service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "Third week policy", DEMO_USER).requestId();

        assertPolicyLine(requestId, "FLAT", 100.0, 250.0);
    }

    @Test
    void afterThreeWeeksWithdrawalChargesFullTuitionAndRefundsNothing() {
        seedStudentWithSubjects(2, 21);

        long requestId = service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "Past three weeks policy", DEMO_USER).requestId();

        assertPolicyLine(requestId, "FLAT", 100.0, 250.0);
    }

    @Test
    void directFullStudentWithdrawalSnapshotsEveryClassAndMarksStudentWithdrawn() {
        seedStudentWithSubjects(3);

        WithdrawalService.DirectDropResult result = service.dropStudentByRegistrar(
            "2026-0001", "TRANSFER", "Registrar full-student review", DEMO_USER);

        assertThat(db.queryForMap(
            "SELECT status, withdrawal_scope, subject_count FROM student_withdrawal_requests WHERE request_id = ?",
            result.requestId()))
            .containsEntry("STATUS", "APPROVED")
            .containsEntry("WITHDRAWAL_SCOPE", "FULL_CURRENT_TERM")
            .containsEntry("SUBJECT_COUNT", 3);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_withdrawal_request_lines WHERE request_id = ?",
            Integer.class, result.requestId())).isEqualTo(3);

        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_enlistments", Integer.class)).isZero();
        assertThat(db.queryForObject(
            "SELECT admission_status FROM students WHERE student_number = '2026-0001'", String.class))
            .isEqualTo("WITHDRAWN");
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_identity_archive WHERE archived_student_number = '2026-0001'",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void fullStudentDropStillWorksWhenCurrentLoadWasAlreadyCleared() {
        seedStudentWithSubjects(2);
        db.update("DELETE FROM student_enlistments WHERE student_id = '2026-0001'");

        WithdrawalService.DirectDropResult result = service.dropStudentByRegistrar(
            "2026-0001", "TRANSFER", "Withdraw after shift cleanup", DEMO_USER);

        assertThat(result.subjectsDropped()).isZero();
        assertThat(result.totalCharge()).isZero();
        assertThat(result.scope()).isEqualTo("FULL_CURRENT_TERM");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_enlistments", Integer.class)).isZero();
        assertThat(db.queryForObject(
            "SELECT admission_status FROM students WHERE student_number = '2026-0001'", String.class))
            .isEqualTo("WITHDRAWN");
        assertThat(db.queryForObject(
            "SELECT is_active FROM students WHERE student_number = '2026-0001'", Integer.class))
            .isZero();
        assertThat(db.queryForMap(
            "SELECT status, withdrawal_scope, subject_count, approval_source, section_id, course_id, estimated_charge " +
                "FROM student_withdrawal_requests WHERE request_id = ?", result.requestId()))
            .containsEntry("STATUS", "APPROVED")
            .containsEntry("WITHDRAWAL_SCOPE", "FULL_CURRENT_TERM")
            .containsEntry("SUBJECT_COUNT", 0)
            .containsEntry("APPROVAL_SOURCE", "REGISTRAR_DIRECT")
            .containsEntry("SECTION_ID", 0)
            .containsEntry("COURSE_ID", 0)
            .containsEntry("ESTIMATED_CHARGE", java.math.BigDecimal.ZERO.setScale(2));
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_withdrawal_request_lines WHERE request_id = ?",
            Integer.class, result.requestId())).isZero();
    }

    @Test
    void shiftCleanupClearsAllCurrentTermClassesWithoutWithdrawingStudent() {
        seedStudentWithSubjects(3);

        WithdrawalService.DirectDropResult result = service.clearCurrentTermLoadForProgramShift(
            "2026-0001", "Shift from BSIT to BSCS", DEMO_USER);

        assertThat(result.subjectsDropped()).isEqualTo(3);
        assertThat(result.scope()).isEqualTo("SHIFT_PROGRAM_CLEANUP");
        assertThat(result.totalCharge()).isEqualTo(175.0);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_enlistments", Integer.class)).isZero();
        assertThat(db.queryForObject(
            "SELECT admission_status FROM students WHERE student_number = '2026-0001'", String.class))
            .isEqualTo("ENROLLED");
        assertThat(db.queryForObject(
            "SELECT is_active FROM students WHERE student_number = '2026-0001'", Integer.class))
            .isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT admission_status FROM sys_users WHERE username = '2026-0001'", String.class))
            .isEqualTo("ENROLLED");
        assertThat(db.queryForObject(
            "SELECT applicant_status FROM applicants WHERE reference_number = 'REF-1'", String.class))
            .isEqualTo("ENROLLED");
        assertThat(db.queryForMap(
            "SELECT status, withdrawal_scope, subject_count, approval_source, reason_code " +
                "FROM student_withdrawal_requests WHERE request_id = ?", result.requestId()))
            .containsEntry("STATUS", "APPROVED")
            .containsEntry("WITHDRAWAL_SCOPE", "SHIFT_PROGRAM_CLEANUP")
            .containsEntry("SUBJECT_COUNT", 3)
            .containsEntry("APPROVAL_SOURCE", "REGISTRAR_SHIFT")
            .containsEntry("REASON_CODE", "SHIFTING");
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_withdrawal_request_lines WHERE request_id = ?",
            Integer.class, result.requestId())).isEqualTo(3);
        verify(enrollmentService, org.mockito.Mockito.times(3)).dropSubjectByEnlistmentId(anyLong(), anyBoolean());
        verify(enrollmentService).postFlatDropCharge("2026-0001", 175.0, "Registrar shift cleanup charge");
    }

    @Test
    void demoRegistrarCanExecuteSingleCourseWithdrawalImmediately() {
        seedStudentWithSubjects(2);

        WithdrawalService.DirectDropResult result = service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "Demo registrar flow", DEMO_USER);

        assertThat(result.subjectsDropped()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_enlistments", Integer.class)).isEqualTo(1);
        assertThat(db.queryForMap(
            "SELECT status, requested_by, registrar_approved_by, approval_source " +
                "FROM student_withdrawal_requests WHERE request_id = ?", result.requestId()))
            .containsEntry("STATUS", "APPROVED")
            .containsEntry("REQUESTED_BY", DEMO_USER)
            .containsEntry("REGISTRAR_APPROVED_BY", DEMO_USER)
            .containsEntry("APPROVAL_SOURCE", "REGISTRAR_DIRECT");
    }

    @Test
    void withdrawnStudentCannotBeWithdrawnOrShiftedAgain() {
        db.update("INSERT INTO students (student_number, reference_number, admission_status, status, is_active, enrollment_blocked) VALUES ('2026-0001', 'REF-1', 'WITHDRAWN', 'WITHDRAWN', 0, 1)");
        db.update("INSERT INTO sys_users (username, admission_status, status, is_active, enrollment_blocked) VALUES ('2026-0001', 'WITHDRAWN', 'INACTIVE', 0, 1)");
        db.update("INSERT INTO applicants VALUES ('REF-1', 'WITHDRAWN', CURRENT_TIMESTAMP)");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            service.dropStudentByRegistrar("2026-0001", "TRANSFER", "Already withdrawn", DEMO_USER)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Student is already withdrawn from school.");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            service.clearCurrentTermLoadForProgramShift("2026-0001", "SHIFTING", "Already withdrawn", DEMO_USER)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Student is already withdrawn from school.");
    }

    private void createSchema() {
        db.execute("CREATE TABLE enrollment_settings (setting_key VARCHAR(80) PRIMARY KEY, setting_value VARCHAR(500))");
        db.execute("CREATE TABLE withdrawal_reasons (reason_code VARCHAR(40) PRIMARY KEY, reason_label VARCHAR(160), is_active INT DEFAULT 1, sort_order INT DEFAULT 100)");
        db.execute("CREATE TABLE system_settings (setting_key VARCHAR(80) PRIMARY KEY, setting_value VARCHAR(500))");
        db.execute("CREATE TABLE academic_term_policies (term_id INT PRIMARY KEY, inc_expiration_date DATE, midterm_exam_date DATE, updated_at TIMESTAMP)");
        db.execute("CREATE TABLE academic_terms (term_id INT PRIMARY KEY, term_code VARCHAR(32), term_name VARCHAR(160), start_date DATE, end_date DATE)");
        db.execute("CREATE TABLE grading_term_windows (window_id BIGINT AUTO_INCREMENT PRIMARY KEY, term_id INT, grading_period VARCHAR(20), start_date DATE, end_date DATE, override_status VARCHAR(20), updated_at TIMESTAMP)");
        db.execute("CREATE TABLE students (student_number VARCHAR(100) PRIMARY KEY, reference_number VARCHAR(100), admission_status VARCHAR(40), status VARCHAR(40) DEFAULT 'ACTIVE', is_active INT DEFAULT 1, enrollment_blocked INT DEFAULT 0)");
        db.execute("CREATE TABLE sys_users (username VARCHAR(100) PRIMARY KEY, admission_status VARCHAR(40), status VARCHAR(40) DEFAULT 'ACTIVE', is_active INT DEFAULT 1, enrollment_blocked INT DEFAULT 0)");
        db.execute("CREATE TABLE applicants (reference_number VARCHAR(100) PRIMARY KEY, applicant_status VARCHAR(40), updated_at TIMESTAMP)");
        db.execute("CREATE TABLE courses (" +
            "course_id INT PRIMARY KEY, course_code VARCHAR(40), course_title VARCHAR(160), " +
            "credit_units DECIMAL(5,2), lec_units DECIMAL(5,2) DEFAULT 0, lab_units DECIMAL(5,2) DEFAULT 0, " +
            "component_type VARCHAR(10) DEFAULT 'SINGLE')");
        db.execute("CREATE TABLE course_corequisites (course_id INT, corequisite_course_id INT)");
        db.execute("CREATE TABLE class_sections (section_id INT PRIMARY KEY, course_id INT, term_id INT, section_code VARCHAR(40))");
        db.execute("CREATE TABLE student_enlistments (enlistment_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id VARCHAR(100), course_id INT, section_id INT, enlisted_date TIMESTAMP)");
        db.update("INSERT INTO academic_term_policies (term_id, midterm_exam_date) VALUES (1, DATE '2099-01-01')");
        db.update("INSERT INTO withdrawal_reasons (reason_code, reason_label, is_active, sort_order) VALUES ('SHIFTING', 'Program shifting', 1, 1)");
        db.update("INSERT INTO academic_terms VALUES (1, '1120262027', 'A.Y. 2026-2027 - 1st Semester', DATE '2026-01-01', DATE '2099-12-31')");
        db.update("INSERT INTO grading_term_windows (term_id, grading_period, start_date, end_date, override_status, updated_at) VALUES (1, 'MIDTERM', DATE '2099-01-01', DATE '2099-01-01', 'AUTO', CURRENT_TIMESTAMP)");
    }

    @Test
    void currentTermSubjectDropUsesConfiguredMidtermEndAndAllowsFutureWindow() {
        seedStudentWithSubjects(1);

        WithdrawalService.DirectDropResult result = service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "Future deadline check", DEMO_USER);

        assertThat(result.subjectsDropped()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_enlistments", Integer.class)).isZero();
    }

    @Test
    void currentTermSubjectDropBlocksWhenMidtermEndHasPassed() {
        db.update("UPDATE grading_term_windows SET end_date = DATE '2026-01-01' WHERE term_id = 1 AND grading_period = 'MIDTERM'");
        seedStudentWithSubjects(1);

        assertThatThrownBy(() -> service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "Past deadline check", DEMO_USER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("A.Y. 2026-2027 - 1st Semester")
            .hasMessageContaining("grading_term_windows.MIDTERM_END");
    }

    @Test
    void currentTermSubjectDropFailsWhenNoMidtermEndIsConfigured() {
        db.update("DELETE FROM grading_term_windows WHERE term_id = 1 AND grading_period = 'MIDTERM'");
        db.update("DELETE FROM system_settings WHERE setting_key = 'MIDTERM_END'");
        seedStudentWithSubjects(1);

        assertThatThrownBy(() -> service.dropSubjectByRegistrar(
            "2026-0001", 101, "ACADEMIC_LOAD", "Missing deadline config", DEMO_USER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Subject drop deadline is not configured")
            .hasMessageContaining("A.Y. 2026-2027 - 1st Semester");
    }

    private void seedStudentWithSubjects(int subjectCount) {
        seedStudentWithSubjects(subjectCount, 0);
    }

    private void assertPolicyLine(long requestId, String bucket, double chargePercent, double estimatedCharge) {
        Map<String, Object> row = db.queryForMap(
            "SELECT timing_bucket, charge_percent, estimated_charge " +
                "FROM student_withdrawal_request_lines WHERE request_id = ?", requestId);
        assertThat(row.get("TIMING_BUCKET")).isEqualTo(bucket);
        assertThat(((Number) row.get("CHARGE_PERCENT")).doubleValue()).isEqualTo(chargePercent);
        assertThat(((Number) row.get("ESTIMATED_CHARGE")).doubleValue()).isEqualTo(estimatedCharge);
    }

    private void seedStudentWithSubjects(int subjectCount, int daysEnrolled) {
        db.update("INSERT INTO students (student_number, reference_number, admission_status) VALUES ('2026-0001', 'REF-1', 'ENROLLED')");
        db.update("INSERT INTO sys_users (username, admission_status) VALUES ('2026-0001', 'ENROLLED')");
        db.update("INSERT INTO applicants VALUES ('REF-1', 'ENROLLED', CURRENT_TIMESTAMP)");
        Timestamp enlistedAt = Timestamp.valueOf(LocalDateTime.now().minusDays(daysEnrolled));
        for (int index = 1; index <= subjectCount; index++) {
            int courseId = index;
            int sectionId = 100 + index;
            db.update("INSERT INTO courses (course_id, course_code, course_title, credit_units, lec_units, lab_units, component_type) " +
                "VALUES (?, ?, ?, 3, 3, 0, 'LEC')", courseId, "C" + index, "Course " + index);
            db.update("INSERT INTO class_sections VALUES (?, ?, 1, ?)", sectionId, courseId, "S" + index);
            db.update("""
                INSERT INTO student_enlistments (student_id, course_id, section_id, enlisted_date)
                VALUES ('2026-0001', ?, ?, ?)
                """, courseId, sectionId, enlistedAt);
        }
    }

    private void seedStudentWithCustomSubject(int subjectCount, String courseCode, String courseTitle,
                                              int creditUnits, int lectureUnits, int labUnits,
                                              String componentType, int daysEnrolled) {
        db.update("INSERT INTO students (student_number, reference_number, admission_status) VALUES ('2026-0001', 'REF-1', 'ENROLLED')");
        db.update("INSERT INTO sys_users (username, admission_status) VALUES ('2026-0001', 'ENROLLED')");
        db.update("INSERT INTO applicants VALUES ('REF-1', 'ENROLLED', CURRENT_TIMESTAMP)");
        Timestamp enlistedAt = Timestamp.valueOf(LocalDateTime.now().minusDays(daysEnrolled));
        for (int index = 1; index <= subjectCount; index++) {
            int courseId = index;
            int sectionId = 100 + index;
            db.update("INSERT INTO courses (course_id, course_code, course_title, credit_units, lec_units, lab_units, component_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                courseId, courseCode, courseTitle, creditUnits, lectureUnits, labUnits, componentType);
            db.update("INSERT INTO class_sections VALUES (?, ?, 1, ?)", sectionId, courseId, "S" + index);
            db.update("""
                INSERT INTO student_enlistments (student_id, course_id, section_id, enlisted_date)
                VALUES ('2026-0001', ?, ?, ?)
                """, courseId, sectionId, enlistedAt);
        }
    }
}
