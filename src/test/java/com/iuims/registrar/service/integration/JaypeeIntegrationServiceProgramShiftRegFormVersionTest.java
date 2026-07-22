package com.iuims.registrar.service.integration;
import com.iuims.registrar.entity.Program;

import com.iuims.registrar.service.admission.ApplicantStatusSyncService;
import com.iuims.registrar.service.support.EnlistmentSchemaService;
import com.iuims.registrar.service.curriculum.StudentCurriculumService;
import com.iuims.registrar.service.forms.RegFormEventService;
import com.iuims.registrar.domain.forms.RegistrationFormSnapshotException;
import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.service.withdrawal.WithdrawalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JaypeeIntegrationServiceProgramShiftRegFormVersionTest {

    private JdbcTemplate db;
    private StudentCurriculumService curriculumService;
    private WithdrawalService withdrawalService;
    private RegFormEventService regFormEventService;
    private JaypeeIntegrationService service;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:jaypee-program-shift-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        db.execute("CREATE TABLE programs (program_id INT PRIMARY KEY, program_code VARCHAR(40), active_status INT)");
        db.execute("CREATE TABLE students (student_number VARCHAR(100) PRIMARY KEY, program_code VARCHAR(40), year_level INT, semester INT, term_year VARCHAR(40), student_type VARCHAR(40), admission_status VARCHAR(40), enrollment_blocked INT DEFAULT 0, is_active INT DEFAULT 1)");
        db.execute("CREATE TABLE sys_users (username VARCHAR(100) PRIMARY KEY, program_code VARCHAR(40), year_level INT, semester INT, term_year VARCHAR(40), student_type VARCHAR(40), enrollment_status_type VARCHAR(40))");
        db.execute("CREATE TABLE student_enlistments (enlistment_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id VARCHAR(100), course_id INT, section_id INT)");

        db.update("INSERT INTO programs (program_id, program_code, active_status) VALUES (1, 'BSCS', 1)");
        db.update("INSERT INTO students (student_number, program_code, year_level, semester, term_year, student_type, admission_status) VALUES ('2026-0001', 'BSIT', 1, 1, 'SL2026202711', 'Regular', 'ENROLLED')");
        db.update("INSERT INTO sys_users (username, program_code, year_level, semester, term_year, student_type) VALUES ('2026-0001', 'BSIT', 1, 1, 'SL2026202711', 'Regular')");

        curriculumService = mock(StudentCurriculumService.class);
        withdrawalService = mock(WithdrawalService.class);
        regFormEventService = mock(RegFormEventService.class);
        EnlistmentSchemaService enlistmentSchemaService = mock(EnlistmentSchemaService.class);
        when(enlistmentSchemaService.hasEnlistmentStatusColumn()).thenReturn(false);
        when(curriculumService.curriculumBelongsToProgram(901, "BSCS")).thenReturn(true);
        when(curriculumService.getShiftCarryOverSummary("2026-0001")).thenReturn(Map.of(
            "carriedOverCount", 0,
            "orphanCount", 0,
            "deficiencyCount", 0));
        when(withdrawalService.clearCurrentTermLoadForCompositeProgramShift(anyString(), anyString(), anyString()))
            .thenReturn(new WithdrawalService.DirectDropResult(71L, 0, 0.0, "SHIFT_PROGRAM_CLEANUP"));
        when(regFormEventService.recordEvent(anyString(), anyString(), anyString(), any(), any(), anyString()))
            .thenAnswer(invocation -> {
                String eventType = invocation.getArgument(1, String.class);
                db.update("INSERT INTO student_reg_form_events (event_type) VALUES (?)", eventType);
                Long eventId = db.queryForObject("SELECT MAX(event_id) FROM student_reg_form_events", Long.class);
                db.update("INSERT INTO student_reg_form_versions (source_event_id, source_event_type) VALUES (?, ?)", eventId, eventType);
                return eventId;
            });
        db.execute("CREATE TABLE student_reg_form_events (event_id BIGINT AUTO_INCREMENT PRIMARY KEY, event_type VARCHAR(60))");
        db.execute("CREATE TABLE student_reg_form_versions (version_id BIGINT AUTO_INCREMENT PRIMARY KEY, source_event_id BIGINT, source_event_type VARCHAR(60))");

        service = new JaypeeIntegrationService(
            db,
            mock(ScholarEnrollmentService.class),
            enlistmentSchemaService,
            mock(ApplicantStatusSyncService.class),
            curriculumService,
            regFormEventService,
            withdrawalService);
    }

    @Test
    void successfulProgramShiftCreatesOnlyTheFinalProgramShiftVersion() {
        String result = transactionTemplate.execute(status -> service.shiftStudentProgram(
            "2026-0001", "BSCS", 2, 1, 901, "Academic realignment"));

        assertThat(result).startsWith("SUCCESS:");
        assertThat(db.queryForObject("SELECT program_code FROM students WHERE student_number = '2026-0001'", String.class))
            .isEqualTo("BSCS");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_reg_form_events WHERE event_type = 'PROGRAM_SHIFT'", Integer.class))
            .isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_reg_form_events WHERE event_type IN ('CURRICULUM_ASSIGNED', 'SHIFT_LOAD_CLEARED')", Integer.class))
            .isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_reg_form_versions WHERE source_event_type = 'PROGRAM_SHIFT'", Integer.class))
            .isEqualTo(1);
        verify(curriculumService).assignCurriculumForProgramShift(
            "2026-0001", 901, "Academic realignment", "registrar");
        verify(withdrawalService).clearCurrentTermLoadForCompositeProgramShift(
            eq("2026-0001"), anyString(), eq("registrar"));
        verify(regFormEventService, times(1)).recordEvent(
            eq("2026-0001"), eq("PROGRAM_SHIFT"), anyString(), any(), anyString(), eq("registrar"));
    }

    @Test
    void postMutationFailureRollsBackTheProgramShift() {
        doThrow(new IllegalStateException("cleanup failed"))
            .when(withdrawalService).clearCurrentTermLoadForCompositeProgramShift(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
            service.shiftStudentProgram("2026-0001", "BSCS", 2, 1, 901, "Academic realignment")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Program shift was not completed. No changes were committed.");

        assertThat(db.queryForObject("SELECT program_code FROM students WHERE student_number = '2026-0001'", String.class))
            .isEqualTo("BSIT");
    }

    @Test
    void snapshotFailureRollsBackTheProgramShift() {
        doThrow(new RegistrationFormSnapshotException("Registration Form version could not be saved."))
            .when(regFormEventService).recordEvent(anyString(), anyString(), anyString(), any(), any(), anyString());

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
            service.shiftStudentProgram("2026-0001", "BSCS", 2, 1, 901, "Academic realignment")))
            .isInstanceOf(RegistrationFormSnapshotException.class)
            .hasMessage("Registration Form version could not be saved.");

        assertThat(db.queryForObject("SELECT program_code FROM students WHERE student_number = '2026-0001'", String.class))
            .isEqualTo("BSIT");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_reg_form_events", Integer.class)).isZero();
    }
}
