package com.iuims.registrar.portal;

import com.iuims.registrar.academic.AcademicGradingService;
import com.iuims.registrar.admission.ApplicantDocumentReadService;
import com.iuims.registrar.core.StudentIdentityReleaseService;
import com.iuims.registrar.admission.FinanceAdmissionService;
import com.iuims.registrar.core.StudentProfileService;
import com.iuims.registrar.curriculum.CreditGradeService;
import com.iuims.registrar.curriculum.StudentCurriculumService;
import com.iuims.registrar.faculty.FacultyLoadService;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnrollmentControllerStudentManagerTest {

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
    void studentManagerStillLoadsAddWindowAfterShiftClearsCurrentLoad() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main", "role", "Registrar"));

        Map<String, Object> student = Map.of(
            "username", "2026-0001",
            "user_id", 1,
            "admission_status", "ENROLLED",
            "program_code", "BSIT",
            "year_level", 2,
            "semester", 1
        );

        when(academicService.findStudentByIdOrName("2026-0001")).thenReturn(student);
        when(academicService.getStudentAcademicHistory(1)).thenReturn(new LinkedHashMap<String, List<Map<String, Object>>>());
        when(academicService.getAllTerms()).thenReturn(List.of());
        when(academicService.getActiveTermId()).thenReturn(1);
        when(jaypeeService.getStudentLoad("2026-0001")).thenReturn(List.of());
        when(jaypeeService.listOfferingSchools()).thenReturn(List.of("School of Business"));
        when(jaypeeService.listOfferingPrograms()).thenReturn(List.of(Map.of(
            "program_code", "BSIT",
            "program_name", "BSIT"
        )));
        when(jaypeeService.getGroupedCourseOfferings(any(), any(), any(), any()))
            .thenReturn(List.of(Map.of("course_code", "IT 101", "sections", List.of())));
        when(withdrawalService.listStandardReasons()).thenReturn(List.of(Map.of(
            "reason_code", "ACADEMIC_LOAD",
            "reason_label", "Academic load adjustment"
        )));
        when(withdrawalService.listShiftCleanupReasons()).thenReturn(List.of(Map.of(
            "reason_code", "SHIFTING",
            "reason_label", "Shifting"
        )));
        when(withdrawalService.listStudentRequests("2026-0001")).thenReturn(List.of());
        when(regFormEventService.listStudentEvents("2026-0001")).thenReturn(List.of());
        when(studentCurriculumService.getCurrentAssignment("2026-0001")).thenReturn(Map.of(
            "curriculum_id", 10,
            "program_code", "BSIT",
            "curriculum_name", "BSIT Curriculum",
            "academic_year", "2026-2027",
            "version_number", 1,
            "is_active", 1,
            "assignment_type", "PROGRAM_SHIFT"
        ));
        when(studentCurriculumService.listAssignableCurricula()).thenReturn(List.of(Map.of(
            "curriculum_id", 10,
            "program_code", "BSIT",
            "curriculum_name", "BSIT Curriculum",
            "academic_year", "2026-2027",
            "is_active", 1
        )));
        when(studentCurriculumService.listCurriculumDeficiencies("2026-0001")).thenReturn(List.of());
        when(studentCurriculumService.getShiftCarryOverSummary("2026-0001")).thenReturn(Map.of(
            "carriedOverCount", 0,
            "deficiencyCount", 0,
            "orphanCount", 0
        ));
        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("balance_fmt", "0.00");
        assessment.put("tuition_fee_fmt", "0.00");
        assessment.put("misc_fee_fmt", "0.00");
        assessment.put("balance_forwarded", 0.0);
        assessment.put("balance_forwarded_fmt", "0.00");
        assessment.put("total_assessment_fmt", "0.00");
        assessment.put("total_paid_fmt", "0.00");
        assessment.put("pending_term_credit", 0.0);
        assessment.put("pending_term_credit_fmt", "0.00");
        assessment.put("has_pending_overpay", false);
        assessment.put("has_accounting_block", false);
        assessment.put("accounting_block_threshold_fmt", "0.00");
        when(financeService.calculateAssessment("2026-0001")).thenReturn(assessment);
        when(financeService.getStudentLedger("2026-0001")).thenReturn(List.of());
        when(financePolicyService.buildStudentInstallmentView("2026-0001", 1)).thenReturn(Map.of());

        Model model = new ExtendedModelMap();
        String view = controller.manageStudentSearch(
            "2026-0001", null, null, null, null, model, session);

        assertThat(view).isEqualTo("admin_student_manager");
        assertThat(model.asMap()).containsKey("groupedCourses");
        assertThat(model.asMap().get("hasEnrolledSubjects")).isEqualTo(false);
        assertThat(model.asMap().get("currentCurriculum")).isNotNull();
        assertThat(model.asMap().get("isProgramShifted")).isEqualTo(true);
        assertThat(model.asMap().get("readyForBulkAdd")).isEqualTo(true);
        assertThat(model.asMap().get("bulkEnrollLabel")).isEqualTo("Bulk Add Shifted Curriculum");
        assertThat(model.asMap().get("canSubmitTransferCreditRequests")).isEqualTo(false);
        assertThat(model.asMap().get("withdrawalReasons")).isEqualTo(List.of(Map.of(
            "reason_code", "ACADEMIC_LOAD",
            "reason_label", "Academic load adjustment"
        )));
        assertThat(model.asMap().get("shiftWithdrawalReasons")).isEqualTo(List.of(Map.of(
            "reason_code", "SHIFTING",
            "reason_label", "Shifting"
        )));
        verify(withdrawalService).listStandardReasons();
        verify(withdrawalService).listShiftCleanupReasons();
    }

    @Test
    void studentManagerBulkEnrollAddsEligibleAssignedCurriculumClasses() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main", "role", "Registrar"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(academicService.findStudentByIdOrName("2026-0001")).thenReturn(Map.of(
            "username", "2026-0001",
            "admission_status", "ENROLLED"
        ));
        when(jaypeeService.getCrossSystemAnalyzedOfferings("2026-0001", true)).thenReturn(List.of(
            Map.of(
                "course_id", 101,
                "schedule_id", 301,
                "is_disabled", false
            ),
            Map.of(
                "course_id", 102,
                "schedule_id", 302,
                "is_disabled", true
            )
        ));
        when(jaypeeService.addSubjectCrossSystem("2026-0001", 301, true))
            .thenReturn("SUCCESS: Added");

        String view = controller.adminStudentManagerBlockEnroll("2026-0001", session, redirect);

        assertThat(view).isEqualTo("redirect:/admin/student-manager");
        assertThat(redirect.getFlashAttributes().get("successMessage"))
            .isEqualTo("Bulk added 1 eligible subject(s) from the assigned curriculum.");
        assertThat(redirect.asMap().get("username")).isEqualTo("2026-0001");
    }

    @Test
    void studentManagerBulkEnrollBlocksWithdrawnStudents() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main", "role", "Registrar"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(academicService.findStudentByIdOrName("2026-0001")).thenReturn(Map.of(
            "username", "2026-0001",
            "admission_status", "WITHDRAWN"
        ));

        String view = controller.adminStudentManagerBlockEnroll("2026-0001", session, redirect);

        assertThat(view).isEqualTo("redirect:/admin/student-manager");
        assertThat(redirect.getFlashAttributes().get("errorMessage"))
            .isEqualTo("Withdrawn students cannot be enrolled in subjects. Their history stays under the archive record, and any future student-number reuse must happen through the registrar release workflow.");
        assertThat(redirect.asMap().get("username")).isEqualTo("2026-0001");
    }

    @Test
    void studentManagerHidesPlacementActionsForWithdrawnStudents() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main", "role", "Registrar"));

        Map<String, Object> student = Map.of(
            "username", "2026-0001",
            "user_id", 1,
            "admission_status", "WITHDRAWN",
            "program_code", "BSIT",
            "year_level", 2,
            "semester", 1
        );

        when(academicService.findStudentByIdOrName("2026-0001")).thenReturn(student);
        when(academicService.getStudentAcademicHistory(1)).thenReturn(new LinkedHashMap<String, List<Map<String, Object>>>());
        when(academicService.getAllTerms()).thenReturn(List.of());
        when(academicService.getActiveTermId()).thenReturn(1);
        when(jaypeeService.getStudentLoad("2026-0001")).thenReturn(List.of());
        when(jaypeeService.listOfferingSchools()).thenReturn(List.of("School of Business"));
        when(jaypeeService.listOfferingPrograms()).thenReturn(List.of(Map.of(
            "program_code", "BSIT",
            "program_name", "BSIT"
        )));
        when(studentCurriculumService.getCurrentAssignment("2026-0001")).thenReturn(Map.of(
            "curriculum_id", 10,
            "program_code", "BSIT",
            "curriculum_name", "BSIT Curriculum",
            "academic_year", "2026-2027",
            "version_number", 1,
            "is_active", 1,
            "assignment_type", "PROGRAM_SHIFT"
        ));
        when(studentCurriculumService.listAssignableCurricula()).thenReturn(List.of(Map.of(
            "curriculum_id", 10,
            "program_code", "BSIT",
            "curriculum_name", "BSIT Curriculum",
            "academic_year", "2026-2027",
            "is_active", 1
        )));
        when(studentCurriculumService.listCurriculumDeficiencies("2026-0001")).thenReturn(List.of());
        when(studentCurriculumService.getShiftCarryOverSummary("2026-0001")).thenReturn(Map.of(
            "carriedOverCount", 0,
            "deficiencyCount", 0,
            "orphanCount", 0
        ));
        when(financeService.calculateAssessment("2026-0001")).thenReturn(assessmentForWithdrawnStudent());
        when(financeService.getStudentLedger("2026-0001")).thenReturn(List.of());
        when(financePolicyService.buildStudentInstallmentView("2026-0001", 1)).thenReturn(Map.of());

        Model model = new ExtendedModelMap();
        String view = controller.manageStudentSearch(
            "2026-0001", null, null, null, null, model, session);

        assertThat(view).isEqualTo("admin_student_manager");
        assertThat(model.asMap().get("isWithdrawnStudent")).isEqualTo(true);
        assertThat(model.asMap().get("hasEnrolledSubjects")).isEqualTo(false);
        assertThat(model.asMap()).doesNotContainKey("groupedCourses");
        assertThat(model.asMap().get("canAddSubjects")).isEqualTo(false);
    }

    @Test
    void withdrawnStudentCannotBeReassignedToCurriculum() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main", "role", "Registrar"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(academicService.findStudentByIdOrName("2026-0001")).thenReturn(Map.of(
            "username", "2026-0001",
            "admission_status", "WITHDRAWN"
        ));

        String view = controller.assignStudentCurriculum("2026-0001", 10, "Registrar correction", redirect, session);

        assertThat(view).isEqualTo("redirect:/admin/student-manager");
        assertThat(redirect.getFlashAttributes().get("errorMessage"))
            .isEqualTo("Withdrawn students cannot be reassigned to a curriculum.");
        verify(studentCurriculumService, org.mockito.Mockito.never()).assignCurriculum(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString());
    }

    private Map<String, Object> assessmentForWithdrawnStudent() {
        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("balance_fmt", "0.00");
        assessment.put("tuition_fee_fmt", "0.00");
        assessment.put("misc_fee_fmt", "0.00");
        assessment.put("balance_forwarded", 0.0);
        assessment.put("balance_forwarded_fmt", "0.00");
        assessment.put("total_assessment_fmt", "0.00");
        assessment.put("total_paid_fmt", "0.00");
        assessment.put("pending_term_credit", 0.0);
        assessment.put("pending_term_credit_fmt", "0.00");
        assessment.put("has_pending_overpay", false);
        assessment.put("has_accounting_block", false);
        assessment.put("accounting_block_threshold_fmt", "0.00");
        return assessment;
    }
}
