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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnrollmentControllerPrintCorPdfTest {

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
    void printCorReturnsInlinePdf() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main"));

        Map<String, Object> student = Map.of(
            "username", "24-1-00001",
            "program_code", "DM",
            "year_level", 1,
            "student_type", "New Student"
        );
        List<Map<String, Object>> load = List.of(Map.of(
            "course_code", "DM101",
            "description", "Anatomy",
            "pretty_schedule", "MON 08:00 AM-09:00 AM R101",
            "section", "DM-1-1-A",
            "units", 3
        ));
        Map<String, Object> finance = Map.of(
            "tuition_fee", 12000.0,
            "total_assessment", 18000.0,
            "total_paid", 3000.0,
            "balance", 15000.0
        );
        Map<String, Object> snapshot = Map.of("referenceNumber", "24-1-00001");
        byte[] pdf = "%PDF-test".getBytes();

        when(academicService.findStudentByIdOrName("24-1-00001")).thenReturn(student);
        when(jaypeeService.getStudentLoad("24-1-00001")).thenReturn(load);
        when(financeService.calculateAssessment("24-1-00001")).thenReturn(finance);
        when(academicService.getCurrentTermLabel()).thenReturn("First Semester, A.Y. 2024-2025");
        when(registrationFormPdfService.buildSnapshot(student, load, finance, "First Semester, A.Y. 2024-2025", "registrar.main"))
            .thenReturn(snapshot);
        when(registrationFormPdfService.renderSnapshot(snapshot)).thenReturn(pdf);

        ResponseEntity<byte[]> response = controller.printCor("24-1-00001", session);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
            .contains("inline; filename=\"registration-form-24-1-00001.pdf\"");
        assertThat(response.getBody()).isEqualTo(pdf);
        verify(regFormVersionService).saveCurrentPrintVersion(
            eq("24-1-00001"),
            eq("Current registration form printed"),
            eq("Registrar generated a current registration form snapshot from the live registrar view."),
            eq("registrar.main"),
            eq(snapshot));
        verify(documentTrailService).recordStudentEvent(
            eq("24-1-00001"),
            eq("STUDENT"),
            eq("REGISTRATION_FORM"),
            eq("PRINTED"),
            eq("Registration Form printed"),
            eq("Registrar generated registration form PDF aligned to the admission pre-registration format."),
            eq("registrar.main"),
            eq(null),
            eq("print_cor"),
            eq("24-1-00001"));
    }

    @Test
    void printCorRedirectsToLoginWhenSessionMissing() {
        ResponseEntity<byte[]> response = controller.printCor("24-1-00001", new MockHttpSession());

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/login");
    }
}
