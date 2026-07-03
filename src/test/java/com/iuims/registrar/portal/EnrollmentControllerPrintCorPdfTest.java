package com.iuims.registrar.portal;

import com.iuims.registrar.academic.AcademicGradingService;
import com.iuims.registrar.admission.ApplicantDocumentReadService;
import com.iuims.registrar.admission.FinanceAdmissionService;
import com.iuims.registrar.core.StudentIdentityReleaseService;
import com.iuims.registrar.core.StudentProfileService;
import com.iuims.registrar.curriculum.CreditGradeService;
import com.iuims.registrar.curriculum.StudentCurriculumService;
import com.iuims.registrar.finance.FinancePolicyService;
import com.iuims.registrar.finance.OverpayDispositionService;
import com.iuims.registrar.finance.TermFeeAdminService;
import com.iuims.registrar.forms.RegFormEventService;
import com.iuims.registrar.forms.RegistrationFormPdfService;
import com.iuims.registrar.forms.StudentArchiveCustodyService;
import com.iuims.registrar.forms.StudentDocumentTrailService;
import com.iuims.registrar.jaypee.JaypeeIntegrationService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.withdrawal.WithdrawalService;
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
        byte[] pdf = "%PDF-test".getBytes();

        when(academicService.findStudentByIdOrName("24-1-00001")).thenReturn(student);
        when(jaypeeService.getStudentLoad("24-1-00001")).thenReturn(load);
        when(financeService.calculateAssessment("24-1-00001")).thenReturn(finance);
        when(academicService.getCurrentTermLabel()).thenReturn("First Semester, A.Y. 2024-2025");
        when(registrationFormPdfService.render(student, load, finance, "First Semester, A.Y. 2024-2025", "registrar.main"))
            .thenReturn(pdf);

        ResponseEntity<byte[]> response = controller.printCor("24-1-00001", session);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
            .contains("inline; filename=\"registration-form-24-1-00001.pdf\"");
        assertThat(response.getBody()).isEqualTo(pdf);
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
