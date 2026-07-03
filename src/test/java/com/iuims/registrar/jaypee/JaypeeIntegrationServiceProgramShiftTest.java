package com.iuims.registrar.jaypee;

import com.iuims.registrar.admission.ApplicantStatusSyncService;
import com.iuims.registrar.core.EnlistmentSchemaService;
import com.iuims.registrar.curriculum.StudentCurriculumService;
import com.iuims.registrar.forms.RegFormEventService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.withdrawal.WithdrawalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JaypeeIntegrationServiceProgramShiftTest {

    private JdbcTemplate db;
    private StudentCurriculumService studentCurriculumService;
    private WithdrawalService withdrawalService;
    private JaypeeIntegrationService service;

    @BeforeEach
    void setUp() {
        db = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:jaypee-shift-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("CREATE TABLE programs (program_code VARCHAR(40) PRIMARY KEY, active_status INT)");
        db.execute("CREATE TABLE students (student_number VARCHAR(100) PRIMARY KEY, program_code VARCHAR(40), year_level INT, semester INT, term_year VARCHAR(40), student_type VARCHAR(40), admission_status VARCHAR(40), status VARCHAR(40), is_active INT, enrollment_blocked INT DEFAULT 0)");
        db.execute("CREATE TABLE sys_users (username VARCHAR(100) PRIMARY KEY, program_code VARCHAR(40), year_level INT, semester INT, term_year VARCHAR(40), student_type VARCHAR(40), admission_status VARCHAR(40), status VARCHAR(40), is_active INT)");

        db.update("INSERT INTO programs VALUES ('BSIT', 1), ('BSCS', 1)");
        db.update("INSERT INTO students (student_number, program_code, year_level, semester, term_year, student_type, admission_status, status, is_active, enrollment_blocked) VALUES ('2026-0001', 'BSIT', 1, 1, 'SL2026202711', 'Regular', 'ENROLLED', 'ACTIVE', 1, 0)");
        db.update("INSERT INTO sys_users VALUES ('2026-0001', 'BSIT', 1, 1, 'SL2026202711', 'Regular', 'ENROLLED', 'ACTIVE', 1)");

        studentCurriculumService = mock(StudentCurriculumService.class);
        when(studentCurriculumService.findDefaultCurriculumId("BSCS")).thenReturn(77);
        when(studentCurriculumService.curriculumBelongsToProgram(77, "BSCS")).thenReturn(true);
        when(studentCurriculumService.getShiftCarryOverSummary("2026-0001")).thenReturn(Map.of(
            "carriedOverCount", 2,
            "orphanCount", 1,
            "deficiencyCount", 5));

        withdrawalService = mock(WithdrawalService.class);
        when(withdrawalService.clearCurrentTermLoadForProgramShift(eq("2026-0001"), anyString(), eq("registrar")))
            .thenReturn(new WithdrawalService.DirectDropResult(123L, 4, 0.0, "SHIFT_PROGRAM_CLEANUP"));

        EnlistmentSchemaService enlistmentSchemaService = mock(EnlistmentSchemaService.class);
        when(enlistmentSchemaService.hasEnlistmentStatusColumn()).thenReturn(false);

        service = new JaypeeIntegrationService(
            db,
            mock(ScholarEnrollmentService.class),
            enlistmentSchemaService,
            mock(ApplicantStatusSyncService.class),
            studentCurriculumService,
            mock(RegFormEventService.class),
            withdrawalService);
    }

    @Test
    void programShiftClearsCurrentLoadWithoutUsingSchoolWithdrawalState() {
        String result = service.shiftStudentProgram(
            "2026-0001", "BSCS", 2, 1, null, "Student requested BSCS shift");

        assertThat(result).startsWith("SUCCESS:");
        assertThat(result).contains("Cleared 4 enrolled");
        assertThat(db.queryForMap(
            "SELECT program_code, year_level, semester, student_type, admission_status, status, is_active " +
                "FROM students WHERE student_number = '2026-0001'"))
            .containsEntry("PROGRAM_CODE", "BSCS")
            .containsEntry("YEAR_LEVEL", 2)
            .containsEntry("SEMESTER", 1)
            .containsEntry("STUDENT_TYPE", "Irregular")
            .containsEntry("ADMISSION_STATUS", "ENROLLED")
            .containsEntry("STATUS", "ACTIVE")
            .containsEntry("IS_ACTIVE", 1);
        verify(withdrawalService).clearCurrentTermLoadForProgramShift(
            eq("2026-0001"), anyString(), eq("registrar"));
        verify(studentCurriculumService).assignCurriculum(
            eq("2026-0001"), eq(77), eq("PROGRAM_SHIFT"), anyString());
    }

    @Test
    void withdrawnStudentCannotBeShifted() {
        db.update("UPDATE students SET admission_status = 'WITHDRAWN', status = 'WITHDRAWN', is_active = 0, enrollment_blocked = 1 WHERE student_number = '2026-0001'");

        String result = service.shiftStudentProgram(
            "2026-0001", "BSCS", 2, 1, null, "Student requested BSCS shift");

        assertThat(result).isEqualTo("ERROR: Withdrawn or inactive students cannot be shifted.");
        verify(withdrawalService, never()).clearCurrentTermLoadForProgramShift(
            eq("2026-0001"), anyString(), eq("registrar"));
    }
}
