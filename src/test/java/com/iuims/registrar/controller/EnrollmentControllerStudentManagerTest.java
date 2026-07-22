package com.iuims.registrar.controller;
import com.iuims.registrar.entity.Program;
import com.iuims.registrar.entity.Student;

import com.iuims.registrar.service.academic.AcademicGradingService;
import com.iuims.registrar.service.admission.ApplicantDocumentReadService;
import com.iuims.registrar.service.support.StudentIdentityReleaseService;
import com.iuims.registrar.service.admission.FinanceAdmissionService;
import com.iuims.registrar.service.support.StudentProfileService;
import com.iuims.registrar.service.curriculum.CreditGradeService;
import com.iuims.registrar.service.curriculum.StudentCurriculumService;
import com.iuims.registrar.service.faculty.FacultyLoadService;
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

    @Test
    void adminEnrollmentHubInitialLoadPublishesSafeDefaults() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main", "role", "Registrar"));

        List<Map<String, Object>> withdrawalReasons = List.of(Map.of(
            "reason_code", "ACADEMIC_LOAD",
            "reason_label", "Academic load adjustment"
        ));
        List<Map<String, Object>> shiftReasons = List.of(Map.of(
            "reason_code", "SHIFTING",
            "reason_label", "Shifting"
        ));
        List<Map<String, Object>> assignableCurricula = List.of(Map.of(
            "curriculum_id", 10,
            "program_code", "BSIT",
            "curriculum_name", "BSIT Curriculum"
        ));

        when(withdrawalService.listStandardReasons()).thenReturn(withdrawalReasons);
        when(withdrawalService.listShiftCleanupReasons()).thenReturn(shiftReasons);
        when(studentCurriculumService.listAssignableCurricula()).thenReturn(assignableCurricula);

        Model model = new ExtendedModelMap();
        String view = controller.adminEnrollmentHub(null, null, null, null, null, model, session);

        assertThat(view).isEqualTo("admin_enrollment");
        assertThat(model.asMap().get("searchedUsername")).isEqualTo("");
        assertThat(model.asMap().get("searchAttempted")).isEqualTo(false);
        assertThat(model.asMap().get("canModifySubjectLoad")).isEqualTo(false);
        assertThat(model.asMap().get("canManageEnrollmentActions")).isEqualTo(false);
        assertThat(model.asMap().get("hasOutstandingBalance")).isEqualTo(false);
        assertThat(model.asMap().get("hasPendingOverpay")).isEqualTo(false);
        assertThat(model.asMap().get("hasEnrolledSubjects")).isEqualTo(false);
        assertThat(model.asMap().get("isTransferee")).isEqualTo(false);
        assertThat(model.asMap().get("isGraduating")).isEqualTo(false);
        assertThat(model.asMap().get("studentLoad")).isEqualTo(List.of());
        assertThat(model.asMap().get("groupedCourses")).isEqualTo(List.of());
        assertThat(model.asMap().get("profileAssignableCurricula")).isEqualTo(List.of());
        assertThat(model.asMap().get("withdrawalReasons")).isEqualTo(withdrawalReasons);
        assertThat(model.asMap().get("shiftWithdrawalReasons")).isEqualTo(shiftReasons);
        assertThat(model.asMap().get("assignableCurricula")).isEqualTo(assignableCurricula);
        assertThat(model.asMap().get("offeringSchools")).isEqualTo(List.of());
        assertThat(model.asMap().get("offeringPrograms")).isEqualTo(List.of());
        assertThat(model.asMap().get("selectedOfferingSchool")).isEqualTo("__DEFAULT__");
        assertThat(model.asMap().get("selectedOfferingProgram")).isEqualTo("__ALL__");
        assertThat(model.asMap().get("offeringQ")).isEqualTo("");
    }

    @Test
    void adminEnrollmentHubLookupMissKeepsExplicitSearchState() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main", "role", "Registrar"));

        when(withdrawalService.listStandardReasons()).thenReturn(List.of());
        when(withdrawalService.listShiftCleanupReasons()).thenReturn(List.of());
        when(studentCurriculumService.listAssignableCurricula()).thenReturn(List.of());
        when(academicService.findStudentByIdOrName("NO-SUCH-STUDENT")).thenReturn(null);

        Model model = new ExtendedModelMap();
        String view = controller.adminEnrollmentHub("  NO-SUCH-STUDENT  ", null, null, null, null, model, session);

        assertThat(view).isEqualTo("admin_enrollment");
        assertThat(model.asMap().get("searchedUsername")).isEqualTo("NO-SUCH-STUDENT");
        assertThat(model.asMap().get("searchAttempted")).isEqualTo(true);
        assertThat(model.asMap().get("searchError")).isEqualTo("Student not found.");
        assertThat(model.asMap().get("canModifySubjectLoad")).isEqualTo(false);
        assertThat(model.asMap().get("canManageEnrollmentActions")).isEqualTo(false);
        assertThat(model.asMap().get("groupedCourses")).isEqualTo(List.of());
    }

    @Test
    void adminEnrollmentHubAdmittedStudentStaysLockedForSubjectLoadActions() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main", "role", "Registrar"));

        when(withdrawalService.listStandardReasons()).thenReturn(List.of());
        when(withdrawalService.listShiftCleanupReasons()).thenReturn(List.of());
        when(studentCurriculumService.listAssignableCurricula()).thenReturn(List.of());
        when(academicService.findStudentByIdOrName("2026-0002")).thenReturn(Map.of(
            "username", "2026-0002",
            "admission_status", "ADMITTED"
        ));
        when(financeService.calculateAssessment("2026-0002")).thenReturn(assessmentForWithdrawnStudent());
        when(jaypeeService.getStudentLoad("2026-0002")).thenReturn(List.of());
        when(jaypeeService.listOfferingSchools()).thenReturn(List.of("School of Business"));
        when(jaypeeService.listOfferingPrograms()).thenReturn(List.of());
        when(financeService.getStudentLedger("2026-0002")).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String view = controller.adminEnrollmentHub("2026-0002", null, null, null, null, model, session);

        assertThat(view).isEqualTo("admin_enrollment");
        assertThat(model.asMap().get("canModifySubjectLoad")).isEqualTo(false);
        assertThat(model.asMap().get("canEnroll")).isEqualTo(false);
        assertThat(model.asMap().get("groupedCourses")).isEqualTo(List.of());
    }

    @Test
    void adminEnrollmentHubFoundStudentPublishesRenderSafeActionModel() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main", "role", "Registrar"));

        Map<String, Object> student = new LinkedHashMap<>();
        student.put("username", "2026-0001");
        student.put("user_id", 1);
        student.put("year_level", 2);
        student.put("semester", 1);
        student.put("program_code", "BSIT");
        student.put("admission_status", "ENROLLED");
        student.put("student_type", "Continuing");

        List<Map<String, Object>> assignableCurricula = List.of(
            Map.of(
                "curriculum_id", 10,
                "program_code", "BSIT",
                "curriculum_name", "BSIT Curriculum",
                "academic_year", "2026-2027",
                "is_active", 1
            ),
            Map.of(
                "curriculum_id", 11,
                "program_code", "BSBA",
                "curriculum_name", "BSBA Curriculum",
                "academic_year", "2026-2027",
                "is_active", 1
            )
        );
        List<Map<String, Object>> groupedCourses = List.of(Map.of(
            "course_id", 201,
            "course_code", "IT 102",
            "description", "Programming 2",
            "reason_msg", "",
            "is_disabled", false,
            "sections", List.of(Map.of(
                "section_id", 301,
                "section_code", "IT102-A",
                "pretty_schedule", "MWF 9:00-10:00",
                "slots_left", 12,
                "is_disabled", false
            ))
        ));
        List<Map<String, Object>> load = List.of(Map.of(
            "course_code", "IT 101",
            "pretty_schedule", "MWF 8:00-9:00",
            "units", 3,
            "schedule_id", 99
        ));
        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("balance_forwarded", 0.0);
        assessment.put("has_accounting_block", false);
        assessment.put("has_pending_overpay", false);
        assessment.put("pending_term_credit", 0.0);

        when(withdrawalService.listStandardReasons()).thenReturn(List.of());
        when(withdrawalService.listShiftCleanupReasons()).thenReturn(List.of());
        when(studentCurriculumService.listAssignableCurricula()).thenReturn(assignableCurricula);
        when(academicService.findStudentByIdOrName("2026-0001")).thenReturn(student);
        when(financeService.calculateAssessment("2026-0001")).thenReturn(assessment);
        when(jaypeeService.getStudentLoad("2026-0001")).thenReturn(load);
        when(jaypeeService.listOfferingSchools()).thenReturn(List.of("School of Business"));
        when(jaypeeService.listOfferingPrograms()).thenReturn(List.of(Map.of(
            "program_code", "BSIT",
            "program_name", "BSIT"
        )));
        when(jaypeeService.getGroupedCourseOfferings("2026-0001", "__DEFAULT__", "__ALL__", ""))
            .thenReturn(groupedCourses);
        when(studentCurriculumService.getCurrentAssignment("2026-0001")).thenReturn(Map.of(
            "curriculum_id", 10,
            "program_code", "BSIT",
            "curriculum_name", "BSIT Curriculum",
            "academic_year", "2026-2027",
            "assignment_type", "CURRENT"
        ));
        when(academicService.getDynamicMaxUnits(1)).thenReturn(27);
        when(academicService.isGraduatingStudent("2026-0001")).thenReturn(false);
        when(financeService.getStudentLedger("2026-0001")).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String view = controller.adminEnrollmentHub("2026-0001", null, null, null, null, model, session);

        assertThat(view).isEqualTo("admin_enrollment");
        assertThat(model.asMap().get("searchedUsername")).isEqualTo("2026-0001");
        assertThat(model.asMap().get("searchAttempted")).isEqualTo(true);
        assertThat(model.asMap().get("canModifySubjectLoad")).isEqualTo(true);
        assertThat(model.asMap().get("canManageEnrollmentActions")).isEqualTo(true);
        assertThat(model.asMap().get("canEnroll")).isEqualTo(true);
        assertThat(model.asMap().get("hasEnrolledSubjects")).isEqualTo(true);
        assertThat(model.asMap().get("isTransferee")).isEqualTo(true);
        assertThat(model.asMap().get("currentCurriculum")).isNotNull();
        assertThat((List<?>) model.asMap().get("profileAssignableCurricula")).hasSize(1);
        assertThat(model.asMap().get("groupedCourses")).isEqualTo(groupedCourses);
        assertThat(model.asMap().get("offeringSchools")).isEqualTo(List.of("School of Business"));
        assertThat(model.asMap().get("offeringPrograms")).isEqualTo(List.of(Map.of(
            "program_code", "BSIT",
            "program_name", "BSIT"
        )));
        assertThat(model.asMap().get("totalUnits")).isEqualTo(3);
        assertThat(model.asMap().get("maxUnits")).isEqualTo(27);
        assertThat(String.valueOf(model.asMap().get("enrollmentCashierUrl"))).contains("keyword=2026-0001");
    }

    @Test
    void adminEnrollmentHubSlicesLedgerPreviewToNewestFiveRows() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main", "role", "Registrar"));

        Map<String, Object> student = new LinkedHashMap<>();
        student.put("username", "2026-1000");
        student.put("user_id", 42);
        student.put("year_level", 2);
        student.put("semester", 1);
        student.put("program_code", "BSIT");
        student.put("admission_status", "ENROLLED");
        student.put("student_type", "Continuing");

        when(withdrawalService.listStandardReasons()).thenReturn(List.of());
        when(withdrawalService.listShiftCleanupReasons()).thenReturn(List.of());
        when(studentCurriculumService.listAssignableCurricula()).thenReturn(List.of());
        when(academicService.findStudentByIdOrName("2026-1000")).thenReturn(student);
        when(financeService.calculateAssessment("2026-1000")).thenReturn(assessmentForWithdrawnStudent());
        when(jaypeeService.getStudentLoad("2026-1000")).thenReturn(List.of());
        when(jaypeeService.listOfferingSchools()).thenReturn(List.of());
        when(jaypeeService.listOfferingPrograms()).thenReturn(List.of());
        when(jaypeeService.getGroupedCourseOfferings("2026-1000", "__DEFAULT__", "__ALL__", ""))
            .thenReturn(List.of());
        when(studentCurriculumService.getCurrentAssignment("2026-1000")).thenReturn(Map.of(
            "curriculum_id", 10,
            "program_code", "BSIT",
            "curriculum_name", "BSIT Curriculum",
            "academic_year", "2026-2027",
            "assignment_type", "CURRENT"
        ));
        when(academicService.getDynamicMaxUnits(2)).thenReturn(27);
        when(academicService.isGraduatingStudent("2026-1000")).thenReturn(false);

        List<Map<String, Object>> ledgerRows = new java.util.ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            ledgerRows.add(Map.of(
                "transaction_date", "2026-07-0" + i,
                "transaction_type", "TYPE-" + i,
                "description", "Ledger row " + i,
                "debit", (double) i,
                "credit", (double) (i * 2),
                "running_balance", (double) (100 - i)
            ));
        }
        when(financeService.getStudentLedger("2026-1000")).thenReturn(ledgerRows);
        when(financePolicyService.buildStudentInstallmentView("2026-1000", 1)).thenReturn(Map.of());

        Model model = new ExtendedModelMap();
        String view = controller.adminEnrollmentHub("2026-1000", null, null, null, null, model, session);

        assertThat(view).isEqualTo("admin_enrollment");
        assertThat((List<?>) model.asMap().get("ledgerPreview")).hasSize(5);
        assertThat(((List<Map<String, Object>>) model.asMap().get("ledgerPreview")).get(0).get("description"))
            .isEqualTo("Ledger row 2");
        assertThat(((List<Map<String, Object>>) model.asMap().get("ledgerPreview")).get(4).get("description"))
            .isEqualTo("Ledger row 6");
    }

    @Test
    void adminProcessEnrollmentRejectsStudentsWhoAreNotYetEnrolled() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main", "role", "Registrar"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(academicService.findStudentByIdOrName("2026-0003")).thenReturn(Map.of(
            "username", "2026-0003",
            "admission_status", "ADMITTED"
        ));

        String view = controller.adminProcessEnrollment("2026-0003", 301, session, redirect);

        assertThat(view).isEqualTo("redirect:/admin/enrollment");
        assertThat(redirect.getFlashAttributes().get("errorMessage"))
            .isEqualTo("Current-term subject add/drop is available only for ENROLLED students.");
        assertThat(redirect.asMap().get("username")).isEqualTo("2026-0003");
        verify(jaypeeService, org.mockito.Mockito.never()).addSubjectCrossSystem("2026-0003", 301);
    }

    @Test
    void adminProcessEnrollmentShowsSuccessFlashWhenSubjectIsAdded() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main", "role", "Registrar"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(academicService.findStudentByIdOrName("2026-0004")).thenReturn(Map.of(
            "username", "2026-0004",
            "admission_status", "ENROLLED"
        ));
        when(financeService.calculateAssessment("2026-0004")).thenReturn(assessmentForWithdrawnStudent());
        when(jaypeeService.getGroupedCourseOfferings("2026-0004")).thenReturn(List.of(Map.of(
            "course_id", 11,
            "course_code", "IT 101",
            "description", "Intro to IT",
            "reason_msg", "",
            "is_disabled", false,
            "sections", List.of(Map.of(
                "section_id", 301,
                "section_code", "IT101-A",
                "pretty_schedule", "MWF 8:00-9:00",
                "slots_left", 12,
                "is_disabled", false
            ))
        )));
        when(jaypeeService.addSubjectCrossSystem("2026-0004", 301)).thenReturn("SUCCESS");

        String view = controller.adminProcessEnrollment("2026-0004", 301, session, redirect);

        assertThat(view).isEqualTo("redirect:/admin/enrollment");
        assertThat(redirect.getFlashAttributes().get("successMessage"))
            .isEqualTo("Subject added successfully.");
        assertThat(redirect.asMap().get("username")).isEqualTo("2026-0004");
        verify(jaypeeService).addSubjectCrossSystem("2026-0004", 301);
    }

    @Test
    void adminProcessEnrollmentRejectsDisabledSectionBeforeMutation() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main", "role", "Registrar"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(academicService.findStudentByIdOrName("2026-0005")).thenReturn(Map.of(
            "username", "2026-0005",
            "admission_status", "ENROLLED"
        ));
        when(financeService.calculateAssessment("2026-0005")).thenReturn(assessmentForWithdrawnStudent());
        when(jaypeeService.getGroupedCourseOfferings("2026-0005")).thenReturn(List.of(Map.of(
            "course_id", 12,
            "course_code", "IT 102",
            "description", "Programming 2",
            "reason_msg", "",
            "is_disabled", false,
            "sections", List.of(Map.of(
                "section_id", 302,
                "section_code", "IT102-B",
                "pretty_schedule", "TTH 10:00-11:00",
                "slots_left", 0,
                "is_disabled", true,
                "reason_msg", "Class Full"
            ))
        )));

        String view = controller.adminProcessEnrollment("2026-0005", 302, session, redirect);

        assertThat(view).isEqualTo("redirect:/admin/enrollment");
        assertThat(redirect.getFlashAttributes().get("errorMessage"))
            .isEqualTo("Class Full");
        verify(jaypeeService, org.mockito.Mockito.never()).addSubjectCrossSystem("2026-0005", 302);
    }

    @Test
    void adminProcessEnrollmentUsesIrregularPolicyReasonWhenSectionIsHiddenByOverlay() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main", "role", "Registrar"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(academicService.findStudentByIdOrName("2026-0008")).thenReturn(Map.of(
            "username", "2026-0008",
            "admission_status", "ENROLLED"
        ));
        when(financeService.calculateAssessment("2026-0008")).thenReturn(assessmentForWithdrawnStudent());
        when(jaypeeService.getGroupedCourseOfferings("2026-0008")).thenReturn(List.of());
        when(jaypeeService.irregularAccessBlockReasonForStudent("2026-0008", 308))
            .thenReturn("Closed to irregular enlistment. Ask Registrar to open irregular access for the class, block, or program/year/semester scope.");

        String view = controller.adminProcessEnrollment("2026-0008", 308, session, redirect);

        assertThat(view).isEqualTo("redirect:/admin/enrollment");
        assertThat(redirect.getFlashAttributes().get("errorMessage"))
            .isEqualTo("Closed to irregular enlistment. Ask Registrar to open irregular access for the class, block, or program/year/semester scope.");
        verify(jaypeeService, org.mockito.Mockito.never()).addSubjectCrossSystem("2026-0008", 308);
    }

    @Test
    void adminBlockEnrollShowsSuccessFlashWhenSubjectsAreAdded() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", Map.of("username", "registrar.main", "role", "Registrar"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(academicService.findStudentByIdOrName("2026-0006")).thenReturn(Map.of(
            "username", "2026-0006",
            "admission_status", "ENROLLED"
        ));
        when(financeService.calculateAssessment("2026-0006")).thenReturn(assessmentForWithdrawnStudent());
        when(jaypeeService.getCrossSystemAnalyzedOfferings("2026-0006", true)).thenReturn(List.of(Map.of(
            "course_id", 11,
            "schedule_id", 301,
            "is_disabled", false
        )));
        when(jaypeeService.addSubjectCrossSystem("2026-0006", 301, true)).thenReturn("SUCCESS: Added");

        String view = controller.adminBlockEnroll("2026-0006", session, redirect);

        assertThat(view).isEqualTo("redirect:/admin/enrollment");
        assertThat(redirect.getFlashAttributes().get("successMessage"))
            .isEqualTo("Block enrolled 1 subjects.");
        assertThat(redirect.asMap().get("username")).isEqualTo("2026-0006");
        verify(jaypeeService).addSubjectCrossSystem("2026-0006", 301, true);
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
