package com.iuims.registrar.controller;
import com.iuims.registrar.entity.Student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iuims.registrar.service.admission.FinanceAdmissionService;
import com.iuims.registrar.service.support.GlobalTermService;
import com.iuims.registrar.service.support.EnlistmentSchemaService;
import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.jdbc.core.JdbcTemplate;

class ScholarControllerTest {

    private ScholarEnrollmentService scholarService;
    private FinanceAdmissionService financeService;
    private GlobalTermService globalTermService;
    private EnlistmentSchemaService enlistmentSchemaService;
    private JdbcTemplate db;
    private ScholarController controller;

    @BeforeEach
    void setUp() {
        scholarService = mock(ScholarEnrollmentService.class);
        financeService = mock(FinanceAdmissionService.class);
        globalTermService = mock(GlobalTermService.class);
        enlistmentSchemaService = mock(EnlistmentSchemaService.class);
        db = mock(JdbcTemplate.class);
        controller = new ScholarController(
            scholarService,
            financeService,
            mock(com.iuims.registrar.service.academic.AcademicGradingService.class),
            globalTermService,
            enlistmentSchemaService,
            db
        );
    }

    @Test
    void walkinPageUsesReferenceNumberWhenCanonicalUsernameIsMissing() {
        Map<String, Object> student = new HashMap<>();
        student.put("reference_number", "EAC-0001");
        student.put("year_level", 1);
        student.put("semester", 1);
        student.put("program_code", "BSN");
        student.put("term_year", "2025-2026_1st");
        student.put("admission_status", "ENROLLED");

        when(scholarService.findStudent("EAC-0001")).thenReturn(student);
        when(globalTermService.getCurrentStudentTermYear(1)).thenReturn("2025-2026_1st");
        when(financeService.calculateAssessment("EAC-0001")).thenReturn(new HashMap<>());

        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("currentUser")).thenReturn(Map.of("username", "admin"));
        Model model = new ConcurrentModel();

        String view = controller.walkinPage("EAC-0001", model, session);

        assertThat(view).isEqualTo("admin_scholar_walkin");
        verify(financeService).calculateAssessment("EAC-0001");
        assertThat(model.asMap()).containsKey("student");
        assertThat(model.asMap()).containsKey("withdrawnStudent");
    }

    @Test
    void cashierPageUsesReferenceNumberWhenCanonicalUsernameIsMissing() {
        Map<String, Object> student = new HashMap<>();
        student.put("reference_number", "EAC-0001");
        student.put("year_level", 1);
        student.put("semester", 1);
        student.put("program_code", "BSN");
        student.put("term_year", "2025-2026_1st");
        student.put("admission_status", "ENROLLED");

        when(scholarService.findStudent("EAC-0001")).thenReturn(student);
        when(globalTermService.getCurrentStudentTermYear(1)).thenReturn("2025-2026_1st");
        when(financeService.calculateAssessment("EAC-0001")).thenReturn(new HashMap<>());
        when(scholarService.getAcademicLoad("EAC-0001")).thenReturn(List.of());
        when(scholarService.getAvailableSubjects("BSN", 1, 1, "")).thenReturn(List.of());
        when(scholarService.getOtherSubjects("BSN", 1, 1)).thenReturn(List.of());

        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("currentUser")).thenReturn(Map.of("username", "admin"));
        Model model = new ConcurrentModel();

        String view = controller.cashierPage("EAC-0001", model, session);

        assertThat(view).isEqualTo("admin_scholar_cashier");
        verify(financeService).calculateAssessment("EAC-0001");
        verify(scholarService).getAcademicLoad("EAC-0001");
        assertThat(model.asMap()).containsKey("enlistedSubjects");
    }
}
