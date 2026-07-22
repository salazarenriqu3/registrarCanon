package com.iuims.registrar.controller;
import com.iuims.registrar.entity.Student;

import com.iuims.registrar.service.academic.AcademicGradingService;
import com.iuims.registrar.service.admission.ApplicantDocumentReadService;
import com.iuims.registrar.service.admission.FinanceAdmissionService;
import com.iuims.registrar.service.support.StudentIdentityReleaseService;
import com.iuims.registrar.service.support.StudentProfileService;
import com.iuims.registrar.service.curriculum.CreditGradeService;
import com.iuims.registrar.service.curriculum.StudentCurriculumService;
import com.iuims.registrar.service.finance.FinancePolicyService;
import com.iuims.registrar.service.finance.OverpayDispositionService;
import com.iuims.registrar.service.finance.TermFeeAdminService;
import com.iuims.registrar.service.forms.RegFormEventService;
import com.iuims.registrar.service.forms.RegFormVersionService;
import com.iuims.registrar.service.forms.RegistrationFormPdfService;
import com.iuims.registrar.service.forms.StudentArchiveCustodyService;
import com.iuims.registrar.service.forms.StudentDocumentTrailService;
import com.iuims.registrar.service.integration.JaypeeIntegrationService;
import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.service.withdrawal.WithdrawalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class EnrollmentControllerRegFormHistoryTest {

    private AcademicGradingService academicService;
    private JaypeeIntegrationService jaypeeService;
    private FinanceAdmissionService financeService;
    private ScholarEnrollmentService scholarEnrollmentService;
    private StudentCurriculumService studentCurriculumService;
    private CreditGradeService creditGradeService;
    private FinancePolicyService financePolicyService;
    private TermFeeAdminService termFeeAdminService;
    private OverpayDispositionService overpayDispositionService;
    private WithdrawalService withdrawalService;
    private RegFormEventService regFormEventService;
    private RegFormVersionService regFormVersionService;
    private RegistrationFormPdfService registrationFormPdfService;
    private StudentArchiveCustodyService archiveCustodyService;
    private StudentDocumentTrailService documentTrailService;
    private StudentProfileService studentProfileService;
    private StudentIdentityReleaseService studentIdentityReleaseService;
    private ApplicantDocumentReadService applicantDocumentReadService;

    private EnrollmentController controller;

    @BeforeEach
    void setUp() {
        academicService = mock(AcademicGradingService.class);
        jaypeeService = mock(JaypeeIntegrationService.class);
        financeService = mock(FinanceAdmissionService.class);
        scholarEnrollmentService = mock(ScholarEnrollmentService.class);
        studentCurriculumService = mock(StudentCurriculumService.class);
        creditGradeService = mock(CreditGradeService.class);
        financePolicyService = mock(FinancePolicyService.class);
        termFeeAdminService = mock(TermFeeAdminService.class);
        overpayDispositionService = mock(OverpayDispositionService.class);
        withdrawalService = mock(WithdrawalService.class);
        regFormEventService = mock(RegFormEventService.class);
        regFormVersionService = mock(RegFormVersionService.class);
        registrationFormPdfService = mock(RegistrationFormPdfService.class);
        archiveCustodyService = mock(StudentArchiveCustodyService.class);
        documentTrailService = mock(StudentDocumentTrailService.class);
        studentProfileService = mock(StudentProfileService.class);
        studentIdentityReleaseService = mock(StudentIdentityReleaseService.class);
        applicantDocumentReadService = mock(ApplicantDocumentReadService.class);

        controller = new EnrollmentController(
            academicService,
            jaypeeService,
            financeService,
            scholarEnrollmentService,
            studentCurriculumService,
            creditGradeService,
            financePolicyService,
            termFeeAdminService,
            overpayDispositionService,
            withdrawalService,
            regFormEventService,
            regFormVersionService,
            registrationFormPdfService,
            archiveCustodyService,
            documentTrailService,
            studentProfileService,
            studentIdentityReleaseService,
            applicantDocumentReadService
        );
    }

    @Test
    void regFormHistoryAddsSavedVersionsAndEventTrailToModel() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main"));
        Model model = new ExtendedModelMap();

        when(regFormVersionService.versionSummary("2026-0001", "CURRENT_PRINT", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 17)))
            .thenReturn(Map.of("total_versions", 2, "touched_students", 1, "latest_version_at", "2026-07-17 10:00:00"));
        when(regFormVersionService.listRecentVersions("2026-0001", "CURRENT_PRINT", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 17), 25))
            .thenReturn(List.of(Map.of("version_id", 9L, "student_number", "2026-0001")));
        when(regFormEventService.eventTypeSummary()).thenReturn(List.of(Map.of("event_type", "CURRENT_PRINT", "event_count", 2)));
        when(regFormEventService.historySummary(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 17)))
            .thenReturn(Map.of("total_events", 3));
        when(regFormEventService.listRecentEvents("2026-0001", "CURRENT_PRINT", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 17), 25))
            .thenReturn(List.of(Map.of("event_id", 11L, "student_number", "2026-0001")));

        String view = controller.regFormHistory(
            "2026-0001",
            "current_print",
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 17),
            25,
            model,
            session);

        assertThat(view).isEqualTo("admin_reg_form_history");
        assertThat(model.asMap().get("pageSubtitle"))
            .isEqualTo("Saved registrar registration form versions plus the supporting audit trail.");
        assertThat(model.asMap().get("versions")).isEqualTo(List.of(Map.of("version_id", 9L, "student_number", "2026-0001")));
        assertThat(model.asMap().get("events")).isEqualTo(List.of(Map.of("event_id", 11L, "student_number", "2026-0001")));
    }

    @Test
    void printSavedVersionUsesStoredSnapshotPdf() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main"));

        Map<String, Object> snapshot = Map.of("referenceNumber", "2026-0001");
        when(regFormVersionService.findVersion(7L)).thenReturn(Map.of(
            "version_id", 7L,
            "student_number", "2026-0001",
            "snapshot", snapshot
        ));
        when(academicService.findStudentByIdOrName("2026-0001")).thenReturn(Map.of(
            "username", "2026-0001",
            "admission_status", "ENROLLED"
        ));
        when(financeService.calculateAssessment("2026-0001")).thenReturn(Map.of(
            "balance_forwarded_remaining", 0.0,
            "has_accounting_block", false
        ));
        when(registrationFormPdfService.renderSnapshot(snapshot)).thenReturn("%PDF-version".getBytes());

        ResponseEntity<byte[]> response = controller.printSavedRegFormVersion(7L, session);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
            .contains("registration-form-version-7-2026-0001.pdf");
        verify(documentTrailService).recordStudentEvent(
            eq("2026-0001"),
            eq("STUDENT"),
            eq("REGISTRATION_FORM"),
            eq("VERSION_PRINTED"),
            eq("Historical Registration Form version printed"),
            eq("Registrar printed saved registration form version #7 from immutable registrar snapshot storage."),
            eq("registrar.main"),
            eq(null),
            eq("student_reg_form_versions"),
            eq("7"));
    }

    @Test
    void missingSavedVersionRedirectsBackToHistory() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(regFormVersionService.findVersion(99L)).thenReturn(null);

        String view = controller.regFormVersionView(99L, session, redirect);

        assertThat(view).isEqualTo("redirect:/admin/reg-form-history");
        assertThat(redirect.getFlashAttributes().get("errorMessage"))
            .isEqualTo("Saved registration form version not found.");
    }

    @Test
    void savedVersionRouteRedirectsToTheSinglePdfFormat() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main"));
        when(regFormVersionService.findVersion(8L)).thenReturn(Map.of(
            "version_id", 8L,
            "student_number", "2026-0001",
            "snapshot", Map.of("programCode", "BSIT")));

        String view = controller.regFormVersionView(8L, session, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/admin/reg-form-history/version/8/print");
    }

    @Test
    void savedVersionRoutesRenderStoredSnapshotAndHandleMissingVersions() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main"));
        Map<String, Object> storedSnapshot = Map.of("programCode", "BSIT", "subjects", List.of());
        when(regFormVersionService.findVersion(10L)).thenReturn(Map.of(
            "version_id", 10L,
            "student_number", "2026-0001",
            "snapshot", storedSnapshot));
        when(regFormVersionService.findVersion(999L)).thenReturn(null);
        when(academicService.findStudentByIdOrName("2026-0001")).thenReturn(Map.of(
            "username", "2026-0001",
            "admission_status", "ENROLLED"));
        when(financeService.calculateAssessment("2026-0001")).thenReturn(Map.of(
            "balance_forwarded_remaining", 0.0,
            "has_accounting_block", false));
        when(registrationFormPdfService.renderSnapshot(storedSnapshot)).thenReturn("%PDF-saved".getBytes());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/admin/reg-form-history/version/10").session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/reg-form-history/version/10/print"));
        mockMvc.perform(get("/admin/reg-form-history/version/10/print").session(session))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mockMvc.perform(get("/admin/reg-form-history/version/999").session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/reg-form-history"));
    }
}
