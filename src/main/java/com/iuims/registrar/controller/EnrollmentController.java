package com.iuims.registrar.controller;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Grade;
import com.iuims.registrar.entity.Program;
import com.iuims.registrar.entity.Student;
import com.iuims.registrar.service.academic.AcademicGradingService;
import com.iuims.registrar.support.GradeOutcomeSql;
import com.iuims.registrar.service.admission.ApplicantStatusSyncService;
import com.iuims.registrar.service.admission.FinanceAdmissionService;
import com.iuims.registrar.service.admission.ApplicantDocumentReadService;
import com.iuims.registrar.service.curriculum.CurriculumSeederService;
import com.iuims.registrar.service.curriculum.CreditGradeService;
import com.iuims.registrar.service.curriculum.StudentCurriculumService;
import com.iuims.registrar.service.support.EnlistmentSchemaService;
import com.iuims.registrar.service.support.StudentProfileService;
import com.iuims.registrar.service.support.StudentIdentityReleaseService;
import com.iuims.registrar.service.faculty.FacultyLoadService;
import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.service.finance.FinancePolicyService;
import com.iuims.registrar.service.finance.OverpayDispositionService;
import com.iuims.registrar.service.finance.TermFeeAdminService;
import com.iuims.registrar.service.forms.RegFormEventService;
import com.iuims.registrar.domain.forms.RegistrationFormSnapshotException;
import com.iuims.registrar.service.forms.RegFormVersionService;
import com.iuims.registrar.service.forms.RegistrationFormPdfService;
import com.iuims.registrar.service.forms.StudentArchiveCustodyService;
import com.iuims.registrar.service.forms.StudentDocumentTrailService;
import com.iuims.registrar.service.support.DatabaseSetupService;
import com.iuims.registrar.service.integration.JaypeeIntegrationService;
import com.iuims.registrar.support.PolicySettings;
import com.iuims.registrar.support.SqlGenerator;
import com.iuims.registrar.service.withdrawal.WithdrawalService;

import com.iuims.registrar.service.academic.AcademicGradingService;
import com.iuims.registrar.service.admission.FinanceAdmissionService;
import com.iuims.registrar.service.integration.JaypeeIntegrationService;
import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.service.curriculum.CreditGradeService;
import com.iuims.registrar.service.curriculum.StudentCurriculumService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class EnrollmentController {
    private static final String STUDENT_INSTALLMENT_MIRROR_NOTICE =
        "Student payment-plan editing moved to Enrollment3 Accounting/Cashier. Registrar now shows this installment data as a read-only mirror.";
    private static final int ENROLLMENT_LEDGER_PREVIEW_LIMIT = 5;
    private static final Pattern SUBJECT_LEDGER_PATTERN = Pattern.compile(
        "(?i)^(Added Subject|Withdrawn Subject|Formal Withdrawal Charge|Withdrawal Charge):\\s*(.+?)(\\s*\\(.*)?$");

    private final AcademicGradingService academicService;
    private final JaypeeIntegrationService jaypeeService;
    private final FinanceAdmissionService financeService;
    private final ScholarEnrollmentService scholarEnrollmentService;
    private final StudentCurriculumService studentCurriculumService;
    private final CreditGradeService creditGradeService;
    private final FinancePolicyService financePolicyService;
    private final TermFeeAdminService termFeeAdminService;
    private final OverpayDispositionService overpayDispositionService;
    private final WithdrawalService withdrawalService;
    private final RegFormEventService regFormEventService;
    private final RegFormVersionService regFormVersionService;
    private final RegistrationFormPdfService registrationFormPdfService;
    private final StudentArchiveCustodyService archiveCustodyService;
    private final StudentDocumentTrailService documentTrailService;
    private final StudentProfileService studentProfileService;
    private final StudentIdentityReleaseService studentIdentityReleaseService;
    private final ApplicantDocumentReadService applicantDocumentReadService;

    @Value("${registrar.enrollment.base-url}")
    private String enrollmentBaseUrl;

    public EnrollmentController(AcademicGradingService academicService, JaypeeIntegrationService jaypeeService,
                                FinanceAdmissionService financeService, ScholarEnrollmentService scholarEnrollmentService,
                                StudentCurriculumService studentCurriculumService, CreditGradeService creditGradeService,
                                FinancePolicyService financePolicyService, TermFeeAdminService termFeeAdminService,
                                OverpayDispositionService overpayDispositionService,
                                WithdrawalService withdrawalService,
                                RegFormEventService regFormEventService,
                                RegFormVersionService regFormVersionService,
                                RegistrationFormPdfService registrationFormPdfService,
                                StudentArchiveCustodyService archiveCustodyService,
                                StudentDocumentTrailService documentTrailService,
                                StudentProfileService studentProfileService,
                                StudentIdentityReleaseService studentIdentityReleaseService,
                                ApplicantDocumentReadService applicantDocumentReadService) {
        this.academicService = academicService;
        this.jaypeeService = jaypeeService;
        this.financeService = financeService;
        this.scholarEnrollmentService = scholarEnrollmentService;
        this.studentCurriculumService = studentCurriculumService;
        this.creditGradeService = creditGradeService;
        this.financePolicyService = financePolicyService;
        this.termFeeAdminService = termFeeAdminService;
        this.overpayDispositionService = overpayDispositionService;
        this.withdrawalService = withdrawalService;
        this.regFormEventService = regFormEventService;
        this.regFormVersionService = regFormVersionService;
        this.registrationFormPdfService = registrationFormPdfService;
        this.archiveCustodyService = archiveCustodyService;
        this.documentTrailService = documentTrailService;
        this.studentProfileService = studentProfileService;
        this.studentIdentityReleaseService = studentIdentityReleaseService;
        this.applicantDocumentReadService = applicantDocumentReadService;
    }

    private String enrollmentCashierUrl(String studentNumber) {
        String base = enrollmentBaseUrl == null ? "" : enrollmentBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String cashierUrl = base + "/admin/cashier";
        if (studentNumber == null || studentNumber.isBlank()) {
            return cashierUrl;
        }
        return cashierUrl + "?keyword=" +
            UriUtils.encodeQueryParam(studentNumber.trim(), StandardCharsets.UTF_8);
    }


    @GetMapping("/admin/student-manager")
    public String manageStudentSearch(@RequestParam(required=false) String username,
                                      @RequestParam(required=false) String errorMsg,
                                      @RequestParam(required=false) String offeringSchool,
                                      @RequestParam(required=false) String offeringProgram,
                                      @RequestParam(required=false) String offeringQ,
                                      Model model, HttpSession session) {
        financeService.syncVerifiedPayments(); 
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        if (errorMsg != null) model.addAttribute("message", errorMsg);
        
        if (username != null && !username.trim().isEmpty()) {
            Map<String, Object> s = academicService.findStudentByIdOrName(username);
            if (s != null) {
                String actualStudentNumber = (String) s.get("username");
                // Get sid safely if it exists (legacy), but default to 0 to prevent NPE
                int sid = s.get("user_id") != null ? ((Number) s.get("user_id")).intValue() : 0;
                
                model.addAttribute("student", s);
                model.addAttribute("studentProfile", studentProfileService.getEditableProfile(actualStudentNumber));
                model.addAttribute("studentArchiveKey", studentProfileService.getArchiveKey(actualStudentNumber));
                model.addAttribute("studentNumberRelease",
                    studentIdentityReleaseService.findReleaseRecord(actualStudentNumber, studentProfileService.getArchiveKey(actualStudentNumber)));
                model.addAttribute("admissionSnapshot",
                    applicantDocumentReadService.getAdmissionSnapshot(actualStudentNumber));
                model.addAttribute("applicantDocuments",
                    applicantDocumentReadService.listDocuments(actualStudentNumber));
                model.addAttribute("archiveSummary",
                    archiveCustodyService.getSummary(actualStudentNumber));
                model.addAttribute("archiveEvents",
                    archiveCustodyService.listRecentEvents(actualStudentNumber));
                model.addAttribute("enrollmentCashierUrl",
                    enrollmentCashierUrl(actualStudentNumber));
                
                List<Map<String, Object>> crossLoad = jaypeeService.getStudentLoad(actualStudentNumber);
                model.addAttribute("studentLoad", crossLoad);
                model.addAttribute("withdrawalReasons", withdrawalService.listStandardReasons());
                model.addAttribute("shiftWithdrawalReasons", withdrawalService.listShiftCleanupReasons());
                model.addAttribute("studentWithdrawalRequests",
                    withdrawalService.listStudentRequests(actualStudentNumber));
                model.addAttribute("regFormEvents", regFormEventService.listStudentEvents(actualStudentNumber));
                String admStatus = s.get("admission_status") != null ? s.get("admission_status").toString() : "";
                boolean isWithdrawnStudent = "WITHDRAWN".equalsIgnoreCase(admStatus);
                boolean hasEnrolledSubjects = !isWithdrawnStudent && !crossLoad.isEmpty();
                boolean isEnrolledStatus = "ENROLLED".equalsIgnoreCase(admStatus) && hasEnrolledSubjects;
                model.addAttribute("isEnrolledStatus", isEnrolledStatus);
                model.addAttribute("hasEnrolledSubjects", hasEnrolledSubjects);
                model.addAttribute("isWithdrawnStudent", isWithdrawnStudent);
                Map<String, Object> currentCurriculum = studentCurriculumService.getCurrentAssignment(actualStudentNumber);
                model.addAttribute("currentCurriculum", currentCurriculum);
                String assignmentType = currentCurriculum != null && currentCurriculum.get("assignment_type") != null
                    ? currentCurriculum.get("assignment_type").toString()
                    : "";
                boolean isProgramShifted = "PROGRAM_SHIFT".equalsIgnoreCase(assignmentType);
                boolean readyForBulkAdd = currentCurriculum != null;
                model.addAttribute("isProgramShifted", isProgramShifted);
                model.addAttribute("readyForBulkAdd", readyForBulkAdd);
                model.addAttribute("bulkEnrollLabel",
                    isProgramShifted ? "Bulk Add Shifted Curriculum" : "Bulk Add Assigned Curriculum");
                boolean canAddSubjects = !isWithdrawnStudent && (hasEnrolledSubjects || currentCurriculum != null);
                model.addAttribute("canAddSubjects", canAddSubjects);

                // Load grouped offerings (one row per course, sections as dropdown)
                if (canAddSubjects) {
                    model.addAttribute("groupedCourses", jaypeeService.getGroupedCourseOfferings(
                        actualStudentNumber, offeringSchool, offeringProgram, offeringQ));
                }
                model.addAttribute("offeringSchools", jaypeeService.listOfferingSchools());
                model.addAttribute("offeringPrograms", jaypeeService.listOfferingPrograms());
                List<Map<String, Object>> assignableCurricula = studentCurriculumService.listAssignableCurricula();
                String studentProgramCode = s.get("program_code") != null ? s.get("program_code").toString().trim() : "";
                model.addAttribute("assignableCurricula", assignableCurricula);
                model.addAttribute("profileAssignableCurricula", assignableCurricula.stream()
                    .filter(curr -> studentProgramCode.equalsIgnoreCase(
                        String.valueOf(curr.getOrDefault("program_code", "")).trim()))
                    .toList());
                List<Map<String, Object>> curriculumDeficiencies =
                    studentCurriculumService.listCurriculumDeficiencies(actualStudentNumber);
                model.addAttribute("curriculumDeficiencies", curriculumDeficiencies);
                model.addAttribute("curriculumDeficiencyCount", curriculumDeficiencies.size());
                model.addAttribute("shiftCarryOver",
                    studentCurriculumService.getShiftCarryOverSummary(actualStudentNumber));
                model.addAttribute("transferCreditRequests",
                    creditGradeService.listRequestsForStudent(actualStudentNumber));
                model.addAttribute("canSubmitTransferCreditRequests",
                    "Dean".equalsIgnoreCase(currentUserRole(session)));
                model.addAttribute("selectedOfferingSchool", offeringSchool != null ? offeringSchool : "__DEFAULT__");
                model.addAttribute("selectedOfferingProgram", offeringProgram != null ? offeringProgram : "__ALL__");
                model.addAttribute("offeringQ", offeringQ != null ? offeringQ : "");
                
                int total = 0; 
                for(Map<String,Object> cls : crossLoad) { if(cls.get("units") != null) total += ((Number)cls.get("units")).intValue(); }
                model.addAttribute("totalUnits", total);

                model.addAttribute("academicHistory", academicService.getStudentAcademicHistory(sid));
                
                // UNIFIED FINANCIAL LOGIC — same current-term formula as cashier/ledger:
                // core fees + signed FORWARDED_BALANCE - term-scoped payments.
                Map<String, Object> finSummary = financeService.calculateAssessment(actualStudentNumber);
                Map<String, Object> financeNode = new java.util.HashMap<>();
                financeNode.put("balance_fmt",          finSummary.getOrDefault("balance_fmt", "0.00"));
                financeNode.put("tuition_fee_fmt",      finSummary.getOrDefault("tuition_fee_fmt", "0.00"));
                financeNode.put("misc_fee_fmt",         finSummary.getOrDefault("misc_fee_fmt", "0.00"));
                financeNode.put("balance_forwarded",    finSummary.getOrDefault("balance_forwarded", 0.0));
                financeNode.put("balance_forwarded_fmt", finSummary.getOrDefault("balance_forwarded_fmt", "0.00"));
                financeNode.put("total_assessment_fmt", finSummary.getOrDefault("total_assessment_fmt", "0.00"));
                financeNode.put("total_paid_fmt",       finSummary.getOrDefault("total_paid_fmt", "0.00"));
                financeNode.put("scholarship_discount_fmt", finSummary.getOrDefault("scholarship_discount_fmt", "0.00"));
                financeNode.put("pending_term_credit", finSummary.getOrDefault("pending_term_credit", 0.0));
                financeNode.put("pending_term_credit_fmt", finSummary.getOrDefault("pending_term_credit_fmt", "0.00"));
                financeNode.put("has_pending_overpay", finSummary.getOrDefault("has_pending_overpay", false));
                financeNode.put("has_accounting_block", finSummary.getOrDefault("has_accounting_block", false));
                financeNode.put("withdrawal_charges", finSummary.getOrDefault("withdrawal_charges", 0.0));
                financeNode.put("withdrawal_charges_fmt", String.format("%,.2f", numberValue(finSummary.get("withdrawal_charges"))));
                financeNode.put("misc_core_fee_fmt", String.format("%,.2f", numberValue(finSummary.get("misc_fee"))));
                financeNode.put("other_fee_fmt", String.format("%,.2f", numberValue(finSummary.get("other_fee"))));
                model.addAttribute("finance", financeNode);
                model.addAttribute("hasAccountingBlock", Boolean.TRUE.equals(finSummary.get("has_accounting_block")));
                model.addAttribute("accountingBlockThresholdFmt", finSummary.getOrDefault("accounting_block_threshold_fmt", "0.00"));
                model.addAttribute("forwardedBalanceFmt", finSummary.getOrDefault("balance_forwarded_fmt", "0.00"));
                model.addAttribute("outstandingBalanceFmt", finSummary.getOrDefault("balance_fmt", "0.00"));
                model.addAttribute("outstandingBalanceAmount", finSummary.getOrDefault("outstandingBalance", 0.0));
                model.addAttribute("hasPendingOverpay", Boolean.TRUE.equals(finSummary.get("has_pending_overpay")));
                model.addAttribute("pendingTermCreditFmt", finSummary.getOrDefault("pending_term_credit_fmt", "0.00"));
                
                List<Map<String, Object>> ledger = financeService.getStudentLedger(actualStudentNumber);
                model.addAttribute("ledger", ledger);
                int previewCount = Math.min(5, ledger.size());
                model.addAttribute("ledgerPreview", ledger.subList(Math.max(0, ledger.size() - previewCount), ledger.size()));

                Integer activeTermId = termFeeAdminService.getActiveTermId();
                model.addAllAttributes(financePolicyService.buildStudentInstallmentView(actualStudentNumber, activeTermId));
            } else { model.addAttribute("message", "Student not found."); }
        } else {
            model.addAttribute("studentRoster", academicService.getStudentRoster(offeringProgram));
            model.addAttribute("offeringPrograms", jaypeeService.listOfferingPrograms());
            model.addAttribute("selectedOfferingProgram", offeringProgram != null ? offeringProgram : "All");
        }
        if (!model.containsAttribute("enrollmentCashierUrl")) {
            model.addAttribute("enrollmentCashierUrl", enrollmentCashierUrl(null));
        }
        return "admin_student_manager";
    }

    @GetMapping("/admin/enrollment")
    public String adminEnrollmentHub(@RequestParam(required=false) String username,
                                     @RequestParam(required=false) String errorMsg,
                                     @RequestParam(required=false) String offeringSchool,
                                     @RequestParam(required=false) String offeringProgram,
                                     @RequestParam(required=false) String offeringQ,
                                     Model model, HttpSession session) {
        financeService.syncVerifiedPayments(); 
        Map<String, Object> currentUser = (Map<String, Object>) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/login";

        model.addAttribute("isAdmin", "admin".equalsIgnoreCase((String) currentUser.get("username")) || "Registrar".equalsIgnoreCase((String) currentUser.get("role")));
        if (errorMsg != null) model.addAttribute("errorMsg", errorMsg);

        String searchedUsername = username != null ? username.trim() : "";
        boolean searchAttempted = !searchedUsername.isEmpty();
        List<Map<String, Object>> withdrawalReasons = defaultList(withdrawalService.listStandardReasons());
        List<Map<String, Object>> shiftWithdrawalReasons = defaultList(withdrawalService.listShiftCleanupReasons());
        List<Map<String, Object>> assignableCurricula = defaultList(studentCurriculumService.listAssignableCurricula());
        List<String> offeringSchools = defaultList(jaypeeService.listOfferingSchools());
        List<Map<String, Object>> offeringPrograms = defaultList(jaypeeService.listOfferingPrograms());
        String selectedOfferingSchool = offeringSchool != null && !offeringSchool.trim().isEmpty()
            ? offeringSchool.trim()
            : "__DEFAULT__";
        String selectedOfferingProgram = offeringProgram != null && !offeringProgram.trim().isEmpty()
            ? offeringProgram.trim()
            : "__ALL__";
        String safeOfferingQ = offeringQ != null ? offeringQ.trim() : "";

        model.addAttribute("searchedUsername", searchedUsername);
        model.addAttribute("searchAttempted", searchAttempted);
        model.addAttribute("searchError", null);
        model.addAttribute("student", null);
        model.addAttribute("isWithdrawnStudent", false);
        model.addAttribute("canModifySubjectLoad", false);
        model.addAttribute("canManageEnrollmentActions", false);
        model.addAttribute("canEnroll", false);
        model.addAttribute("hasOutstandingBalance", false);
        model.addAttribute("hasPendingOverpay", false);
        model.addAttribute("hasEnrolledSubjects", false);
        model.addAttribute("isTransferee", false);
        model.addAttribute("irregularAccessPolicyStudent", false);
        model.addAttribute("isGraduating", false);
        model.addAttribute("studentLoad", List.of());
        model.addAttribute("groupedCourses", List.of());
        model.addAttribute("currentCurriculum", null);
        model.addAttribute("assignableCurricula", assignableCurricula);
        model.addAttribute("profileAssignableCurricula", List.of());
        model.addAttribute("withdrawalReasons", withdrawalReasons);
        model.addAttribute("shiftWithdrawalReasons", shiftWithdrawalReasons);
        model.addAttribute("offeringSchools", offeringSchools);
        model.addAttribute("offeringPrograms", offeringPrograms);
        model.addAttribute("selectedOfferingSchool", selectedOfferingSchool);
        model.addAttribute("selectedOfferingProgram", selectedOfferingProgram);
        model.addAttribute("offeringQ", safeOfferingQ);
        model.addAttribute("enrollmentLedgerPreview", List.of());

        if (searchAttempted) {
            Map<String, Object> s = academicService.findStudentByIdOrName(searchedUsername);
            if (s != null) {
                String actualStudentNumber = (String) s.get("username"); 
                int sid = s.get("user_id") != null ? ((Number) s.get("user_id")).intValue() : 0;
                int yrLvl = s.get("year_level") != null ? ((Number) s.get("year_level")).intValue() : 1;
                String admStatus = s.get("admission_status") != null ? s.get("admission_status").toString() : "";
                String studentType = s.get("student_type") != null ? s.get("student_type").toString() : "";
                boolean isWithdrawnStudent = "WITHDRAWN".equalsIgnoreCase(admStatus);

                Map<String, Object> finSummary = financeService.calculateAssessment(actualStudentNumber);
                double forwardDebt = finSummary.get("balance_forwarded") instanceof Number
                    ? ((Number) finSummary.get("balance_forwarded")).doubleValue()
                    : 0.0;
                boolean hasAccountingBlock = Boolean.TRUE.equals(finSummary.get("has_accounting_block"));
                boolean hasPendingOverpay = Boolean.TRUE.equals(finSummary.get("has_pending_overpay"));
                double pendingCredit = finSummary.get("pending_term_credit") instanceof Number
                    ? ((Number) finSummary.get("pending_term_credit")).doubleValue()
                    : 0.0;

                // Enrollment is unlocked when:
                //   1) The student has been formally admitted & paid (ENROLLED status), OR
                //   2) They are year 2+ (transferee/irregular — bypass for demo/advising), OR
                //   3) They are a Continuing/Old student (bypasses new enrollee lock)
                boolean isOfficiallyEnrolled = "ENROLLED".equalsIgnoreCase(admStatus);
                boolean isContinuing = "Continuing".equalsIgnoreCase(studentType) || "Old Student".equalsIgnoreCase(studentType);
                boolean canModifySubjectLoad = isOfficiallyEnrolled
                    && !isWithdrawnStudent
                    && !hasAccountingBlock
                    && !hasPendingOverpay;
                
                model.addAttribute("student", s);
                model.addAttribute("searchedUsername", actualStudentNumber);
                model.addAttribute("isWithdrawnStudent", isWithdrawnStudent);
                model.addAttribute("canModifySubjectLoad", canModifySubjectLoad);
                model.addAttribute("canManageEnrollmentActions", canModifySubjectLoad);
                model.addAttribute("canEnroll", canModifySubjectLoad);
                model.addAttribute("hasOutstandingBalance", hasAccountingBlock);
                if (hasAccountingBlock) {
                    model.addAttribute("outstandingBalanceFmt", String.format("%,.2f", forwardDebt));
                }
                model.addAttribute("hasPendingOverpay", hasPendingOverpay);
                if (hasPendingOverpay) {
                    model.addAttribute("pendingTermCreditFmt", String.format("%,.2f", pendingCredit));
                }

                boolean isTransferee = yrLvl >= 2 || isContinuing;
                model.addAttribute("isTransferee", isTransferee);
                model.addAttribute("irregularAccessPolicyStudent",
                    jaypeeService.isIrregularStudentForAccess(actualStudentNumber));

                List<Map<String, Object>> crossLoad = jaypeeService.getStudentLoad(actualStudentNumber);
                boolean hasEnrolledSubjects = !isWithdrawnStudent && !crossLoad.isEmpty();
                model.addAttribute("studentLoad", crossLoad);
                model.addAttribute("hasEnrolledSubjects", hasEnrolledSubjects);

                Map<String, Object> currentCurriculum = studentCurriculumService.getCurrentAssignment(actualStudentNumber);
                model.addAttribute("currentCurriculum", currentCurriculum);
                String studentProgramCode = s.get("program_code") != null ? s.get("program_code").toString().trim() : "";
                model.addAttribute("profileAssignableCurricula", assignableCurricula.stream()
                    .filter(curr -> studentProgramCode.equalsIgnoreCase(
                        String.valueOf(curr.getOrDefault("program_code", "")).trim()))
                    .toList());
                model.addAttribute("enrollmentCashierUrl",
                    enrollmentCashierUrl(actualStudentNumber));

                model.addAttribute("groupedCourses", canModifySubjectLoad
                    ? jaypeeService.getGroupedCourseOfferings(
                        actualStudentNumber, selectedOfferingSchool, selectedOfferingProgram, safeOfferingQ)
                    : List.of());

                int total = 0; for(Map<String,Object> cls : crossLoad) { if(cls.get("units") != null) total += ((Number)cls.get("units")).intValue(); }
                model.addAttribute("totalUnits", total);
                model.addAttribute("maxUnits", academicService.getDynamicMaxUnits(sid));
                model.addAttribute("isGraduating", academicService.isGraduatingStudent(actualStudentNumber));
                Map<String, Object> financeNode = new java.util.HashMap<>();
                financeNode.put("balance_fmt", finSummary.getOrDefault("balance_fmt", "0.00"));
                financeNode.put("tuition_fee_fmt", finSummary.getOrDefault("tuition_fee_fmt", "0.00"));
                financeNode.put("misc_fee_fmt", finSummary.getOrDefault("misc_fee_fmt", "0.00"));
                financeNode.put("balance_forwarded", finSummary.getOrDefault("balance_forwarded", 0.0));
                financeNode.put("balance_forwarded_fmt", finSummary.getOrDefault("balance_forwarded_fmt",
                    finSummary.getOrDefault("balance_forwarded_remaining_fmt", "0.00")));
                financeNode.put("balance_forwarded_remaining_fmt", finSummary.getOrDefault("balance_forwarded_remaining_fmt",
                    finSummary.getOrDefault("balance_forwarded_fmt", "0.00")));
                financeNode.put("total_assessment_fmt", finSummary.getOrDefault("total_assessment_fmt", "0.00"));
                financeNode.put("total_paid_fmt", finSummary.getOrDefault("total_paid_fmt", "0.00"));
                financeNode.put("scholarship_discount_fmt", finSummary.getOrDefault("scholarship_discount_fmt", "0.00"));
                financeNode.put("pending_term_credit", finSummary.getOrDefault("pending_term_credit", 0.0));
                financeNode.put("pending_term_credit_fmt", finSummary.getOrDefault("pending_term_credit_fmt", "0.00"));
                financeNode.put("has_pending_overpay", finSummary.getOrDefault("has_pending_overpay", false));
                financeNode.put("has_accounting_block", finSummary.getOrDefault("has_accounting_block", false));
                financeNode.put("withdrawal_charges", finSummary.getOrDefault("withdrawal_charges", 0.0));
                financeNode.put("withdrawal_charges_fmt", String.format("%,.2f", numberValue(finSummary.get("withdrawal_charges"))));
                financeNode.put("misc_core_fee_fmt", String.format("%,.2f", numberValue(finSummary.get("misc_fee"))));
                financeNode.put("other_fee_fmt", String.format("%,.2f", numberValue(finSummary.get("other_fee"))));
                model.addAttribute("finance", financeNode);
                List<Map<String, Object>> ledger = financeService.getStudentLedger(actualStudentNumber);
                model.addAttribute("ledger", ledger);
                int previewCount = Math.min(5, ledger.size());
                model.addAttribute("ledgerPreview", ledger.subList(Math.max(0, ledger.size() - previewCount), ledger.size()));
                model.addAttribute("enrollmentLedgerPreview", buildEnrollmentLedgerPreview(ledger));
            } else {
                model.addAttribute("searchError", "Student not found.");
            }
        }
        return "admin_enrollment";
    }

    @GetMapping("/admin/reg-form-history")
    public String regFormHistory(@RequestParam(required = false) String studentNumber,
                                 @RequestParam(required = false) String eventType,
                                 @RequestParam(required = false) LocalDate fromDate,
                                 @RequestParam(required = false) LocalDate toDate,
                                 @RequestParam(required = false, defaultValue = "250") int limit,
                                 Model model,
                                 HttpSession session) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        int safeLimit = Math.max(1, Math.min(limit, 500));
        String safeStudentNumber = studentNumber != null ? studentNumber.trim() : "";
        String safeEventType = eventType != null ? eventType.trim().toUpperCase() : "";
        model.addAttribute("pageTitle", "Reg Form History");
        model.addAttribute("pageSubtitle", "Saved registrar registration form versions plus the supporting audit trail.");
        model.addAttribute("studentNumber", safeStudentNumber);
        model.addAttribute("eventType", safeEventType);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("limit", safeLimit);
        model.addAttribute("versionSummary", regFormVersionService.versionSummary(safeStudentNumber, safeEventType, fromDate, toDate));
        model.addAttribute("versions", regFormVersionService.listRecentVersions(safeStudentNumber, safeEventType, fromDate, toDate, safeLimit));
        model.addAttribute("eventTypeSummary", regFormEventService.eventTypeSummary());
        model.addAttribute("historySummary", regFormEventService.historySummary(fromDate, toDate));
        model.addAttribute("events", regFormEventService.listRecentEvents(safeStudentNumber, safeEventType, fromDate, toDate, safeLimit));
        return "admin_reg_form_history";
    }

    @GetMapping("/admin/reg-form-history/version/{versionId}")
    public String regFormVersionView(@PathVariable long versionId,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        Map<String, Object> version = regFormVersionService.findVersion(versionId);
        if (version == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Saved registration form version not found.");
            return "redirect:/admin/reg-form-history";
        }
        return "redirect:/admin/reg-form-history/version/" + versionId + "/print";
    }

    @GetMapping("/admin/document-trail")
    public String documentTrail(@RequestParam(required = false) String query,
                                @RequestParam(required = false) String eventType,
                                @RequestParam(required = false) String documentType,
                                @RequestParam(required = false) LocalDate fromDate,
                                @RequestParam(required = false) LocalDate toDate,
                                @RequestParam(required = false, defaultValue = "250") int limit,
                                Model model,
                                HttpSession session) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        int safeLimit = Math.max(1, Math.min(limit, 500));
        String safeQuery = query != null ? query.trim() : "";
        String safeEventType = eventType != null ? eventType.trim().toUpperCase() : "";
        String safeDocumentType = documentType != null ? documentType.trim().toUpperCase() : "";
        model.addAttribute("pageTitle", "Document Trail");
        model.addAttribute("pageSubtitle", "Unified registrar trail of student-document transactions and movement.");
        model.addAttribute("query", safeQuery);
        model.addAttribute("eventType", safeEventType);
        model.addAttribute("documentType", safeDocumentType);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("limit", safeLimit);
        model.addAttribute("summary", documentTrailService.summary(safeQuery, safeEventType, safeDocumentType, fromDate, toDate));
        model.addAttribute("eventTypeOptions", documentTrailService.eventTypeSummary(safeQuery, safeDocumentType, fromDate, toDate));
        model.addAttribute("documentTypeOptions", documentTrailService.documentTypeSummary(safeQuery, safeEventType, fromDate, toDate));
        model.addAttribute("events", documentTrailService.listRecentEvents(safeQuery, safeEventType, safeDocumentType, fromDate, toDate, safeLimit));
        return "admin_document_trail";
    }

    @GetMapping("/admin/reg-form-history/export")
    public ResponseEntity<byte[]> exportRegFormHistory(@RequestParam(required = false) String studentNumber,
                                                       @RequestParam(required = false) String eventType,
                                                       @RequestParam(required = false) LocalDate fromDate,
                                                       @RequestParam(required = false) LocalDate toDate,
                                                       @RequestParam(required = false, defaultValue = "500") int limit,
                                                       HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, "/login")
                .build();
        }
        List<Map<String, Object>> events = regFormEventService.listRecentEvents(studentNumber, eventType, fromDate, toDate, limit);
        StringBuilder csv = new StringBuilder();
        csv.append("event_id,student_number,event_type,purpose,related_request_id,remarks,triggered_by,created_at\r\n");
        for (Map<String, Object> row : events) {
            csv.append(csvCell(row.get("event_id"))).append(',')
                .append(csvCell(row.get("student_number"))).append(',')
                .append(csvCell(row.get("event_type"))).append(',')
                .append(csvCell(row.get("purpose"))).append(',')
                .append(csvCell(row.get("related_request_id"))).append(',')
                .append(csvCell(row.get("remarks"))).append(',')
                .append(csvCell(row.get("triggered_by"))).append(',')
                .append(csvCell(row.get("created_at")))
                .append("\r\n");
        }
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reg-form-history.csv")
            .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/admin/reg-form-history/version/{versionId}/print")
    public ResponseEntity<byte[]> printSavedRegFormVersion(@PathVariable long versionId, HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/login").build();
        }
        Map<String, Object> version = regFormVersionService.findVersion(versionId);
        if (version == null) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/admin/reg-form-history").build();
        }
        String studentNumber = String.valueOf(version.get("student_number"));
        Map<String, Object> student = academicService.findStudentByIdOrName(studentNumber);
        if (student != null) {
            Map<String, Object> finance = financeService.calculateAssessment(studentNumber);
            String releaseBlock = withdrawnDocumentReleaseBlock(student, finance, "Registration Form", session);
            if (releaseBlock != null) {
                return ResponseEntity.status(409)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(releaseBlock.getBytes(StandardCharsets.UTF_8));
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) version.get("snapshot");
        byte[] pdf = registrationFormPdfService.renderSnapshot(snapshot);
        documentTrailService.recordStudentEvent(
            studentNumber,
            "STUDENT",
            "REGISTRATION_FORM",
            "VERSION_PRINTED",
            "Historical Registration Form version printed",
            "Registrar printed saved registration form version #" + versionId + " from immutable registrar snapshot storage.",
            currentUsername(session),
            null,
            "student_reg_form_versions",
            String.valueOf(versionId));
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"registration-form-version-" + versionId + "-" + studentNumber + ".pdf\"")
            .body(pdf);
    }

    @GetMapping("/api/search-students")
    @ResponseBody
    public List<Map<String, Object>> searchApi(@RequestParam String query) { 
        return academicService.searchStudentsBySurname(query); 
    }

    @GetMapping("/admin/enroll")
    public String adminEnrollGet(@RequestParam(required = false) String username) {
        if (username != null && !username.isBlank()) {
            return "redirect:/admin/student-manager?username=" + username.trim();
        }
        return "redirect:/admin/student-manager";
    }

    @GetMapping("/admin/scholar-ledger")
    public String scholarLedger(@RequestParam(required = false) String keyword,
                                Model model,
                                HttpSession session) {
        financeService.syncVerifiedPayments();
        if (session.getAttribute("currentUser") == null) {
            return "redirect:/login";
        }

        String safeKeyword = keyword != null ? keyword.trim() : "";
        model.addAttribute("keyword", safeKeyword);

        if (safeKeyword.isEmpty()) {
            return "admin_scholar_ledger";
        }

        Map<String, Object> student = academicService.findStudentByIdOrName(safeKeyword);
        if (student == null) {
            model.addAttribute("errorMessage", "Student not found.");
            return "admin_scholar_ledger";
        }

        String studentNumber = String.valueOf(student.get("username"));
        financeService.refreshStudentFinanceSnapshot(studentNumber);
        Map<String, Object> finSummary = financeService.calculateAssessment(studentNumber);

        model.addAttribute("student", student);
        model.addAttribute("finance", finSummary);
        model.addAttribute("outstandingBalance", finSummary.getOrDefault("outstandingBalance", finSummary.getOrDefault("balance", 0.0)));
        model.addAttribute("rawLedger", financeService.getStudentLedger(studentNumber));
        model.addAttribute("paymentHistory", financeService.getStudentPayments(studentNumber));
        model.addAttribute("academicLoad", normalizeAcademicLoad(jaypeeService.getStudentLoad(studentNumber)));
        return "admin_scholar_ledger";
    }

    private List<Map<String, Object>> normalizeAcademicLoad(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return java.util.List.of();
        }
        return rows.stream().map(row -> {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("COURSE_CODE", firstNonBlank(row, "COURSE_CODE", "course_code", "code"));
            out.put("COURSE_TITLE", firstNonBlank(row, "COURSE_TITLE", "course_title", "DESCRIPTION", "description", "title"));
            out.put("CREDIT_UNITS", firstNonNull(row, "CREDIT_UNITS", "credit_units", "units"));
            out.put("GRADE", firstNonNull(row, "GRADE", "grade"));
            return out;
        }).toList();
    }

    private Object firstNonNull(Map<String, Object> row, String... keys) {
        if (row == null) return null;
        for (String key : keys) {
            if (key == null) continue;
            Object value = row.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(Map<String, Object> row, String... keys) {
        Object value = firstNonNull(row, keys);
        return value != null ? value.toString() : "";
    }

    @PostMapping("/admin/student-manager/profile")
    public String saveStudentProfile(@RequestParam Map<String, String> form,
                                     HttpSession session,
                                     RedirectAttributes redir) {
        String studentNumber = form.get("studentNumber") != null ? form.get("studentNumber").trim() : "";
        if (studentNumber.isEmpty()) {
            redir.addFlashAttribute("errorMessage", "Student number is required.");
            return "redirect:/admin/student-manager";
        }
        try {
            List<String> changed = studentProfileService.updateProfile(studentNumber, form, currentUsername(session));
            if (changed.isEmpty()) {
                redir.addFlashAttribute("successMessage", "No profile changes detected.");
            } else {
                String details = "Updated profile fields: " + String.join(", ", changed) + ".";
                recordTrail(
                    studentNumber,
                    "PROFILE",
                    "STUDENT_PROFILE_UPDATED",
                    "Registrar updated student profile",
                    details,
                    session,
                    "students",
                    studentNumber);
                redir.addFlashAttribute("successMessage", "Student profile updated.");
            }
        } catch (Exception e) {
            redir.addFlashAttribute("errorMessage", "Profile update failed: " + e.getMessage());
        }
        return "redirect:/admin/student-manager?username=" + studentNumber;
    }

    @GetMapping("/admin/student-manager/admission-document")
    public ResponseEntity<Resource> viewAdmissionDocument(@RequestParam String studentNumber,
                                                           @RequestParam String documentKey,
                                                           @RequestParam(required = false, defaultValue = "view") String mode,
                                                           HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            Path path = applicantDocumentReadService.resolveDocumentPath(studentNumber, documentKey);
            String normalizedMode = "download".equalsIgnoreCase(mode) ? "download" : "view";
            boolean download = "download".equals(normalizedMode);
            String fileName = path != null && path.getFileName() != null
                ? path.getFileName().toString().replace("\"", "")
                : documentKey.replace("\"", "");
            String sourceTable = documentKey.startsWith("normalized:")
                ? "student_requirement_files"
                : "applicants";
            try {
                recordTrail(
                    studentNumber,
                    "ADMISSION_DOCUMENT",
                    download ? "ADMISSION_DOCUMENT_DOWNLOADED" : "ADMISSION_DOCUMENT_VIEWED",
                    download ? "Registrar downloaded admission document" : "Registrar viewed admission document",
                    "Document key: " + documentKey + "; file: " + fileName,
                    session,
                    sourceTable,
                    documentKey);
            } catch (Exception ignored) {
                // Document safekeeping access should remain available even if trail persistence fails.
            }
            if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
                String fallbackUrl = applicantDocumentReadService.resolveDocumentFallbackUrl(studentNumber, documentKey, download);
                if (fallbackUrl != null && !fallbackUrl.isBlank()) {
                    return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, fallbackUrl)
                        .build();
                }
                return ResponseEntity.notFound().build();
            }
            String contentType = Files.probeContentType(path);
            MediaType mediaType = contentType != null
                ? MediaType.parseMediaType(contentType)
                : MediaType.APPLICATION_OCTET_STREAM;
            Resource resource = new UrlResource(path.toUri());
            return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    (download ? "attachment" : "inline") + "; filename=\"" + fileName + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(resource);
        } catch (Exception ignored) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/admin/student-manager/overpay-disposition")
    public String overpayDisposition(@RequestParam String studentNumber,
                                     @RequestParam String action,
                                     @RequestParam(required = false) Double creditAmount,
                                     @RequestParam(required = false) Double refundAmount,
                                     @RequestParam(required = false) String remarks,
                                     HttpSession session,
                                     RedirectAttributes redir) {
        String sn = studentNumber != null ? studentNumber.trim() : "";
        if (sn.isEmpty()) {
            redir.addFlashAttribute("errorMessage", "Student number is required.");
            return "redirect:/admin/student-manager";
        }
        String decidedBy = "registrar";
        Map<String, Object> user = (Map<String, Object>) session.getAttribute("currentUser");
        if (user != null && user.get("username") != null) {
            decidedBy = user.get("username").toString();
        }

        OverpayDispositionService.DispositionResult result;
        String act = action != null ? action.trim().toUpperCase() : "";
        switch (act) {
            case "CREDIT" -> {
                double amt = creditAmount != null ? creditAmount : 0.0;
                result = overpayDispositionService.applyAsCredit(sn, amt, decidedBy, remarks);
            }
            case "REFUND" -> {
                double amt = refundAmount != null ? refundAmount : 0.0;
                result = overpayDispositionService.refundAsCash(sn, amt, decidedBy, remarks);
            }
            case "SPLIT" -> result = overpayDispositionService.splitDisposition(
                sn,
                refundAmount != null ? refundAmount : 0.0,
                creditAmount != null ? creditAmount : 0.0,
                decidedBy, remarks);
            default -> {
                redir.addFlashAttribute("errorMessage", "Unknown disposition action.");
                return "redirect:/admin/student-manager?username=" + sn;
            }
        }

        if (result.success()) {
            redir.addFlashAttribute("successMessage", result.message());
            recordTrail(
                sn,
                "FINANCE",
                "OVERPAY_" + act,
                "Overpayment disposition recorded",
                result.message() + (remarks != null && !remarks.isBlank() ? " Remarks: " + remarks.trim() : ""),
                session,
                "student_overpay_dispositions",
                sn);
        } else {
            redir.addFlashAttribute("errorMessage", result.message());
        }
        return "redirect:/admin/student-manager?username=" + sn;
    }

    @PostMapping({"/admin/enroll", "/admin/student-manager/enroll"})
    public String adminEnroll(@RequestParam String studentId,
                              @RequestParam(required = false) Integer scheduleId,
                              HttpSession session,
                              RedirectAttributes redir) {
        String username = studentId != null ? studentId.trim() : "";
        if (username.isEmpty()) {
            redir.addFlashAttribute("message", "ERROR: Missing student ID.");
            return "redirect:/admin/student-manager";
        }
        if (scheduleId == null || scheduleId <= 0) {
            redir.addFlashAttribute("message", "ERROR: Choose a section before clicking Add.");
            redir.addAttribute("username", username);
            return "redirect:/admin/student-manager";
        }
        try {
            String result = jaypeeService.addSubjectCrossSystem(username, scheduleId);
            if (result.startsWith("CONFLICT:") || result.startsWith("ERROR:")) {
                redir.addFlashAttribute("message", result);
            } else if (result.startsWith("SUCCESS")) {
                recordTrail(
                    username,
                    "ENROLLMENT",
                    "SUBJECT_ADD_COMPLETED",
                    "Registrar subject add completed",
                    "Schedule #" + scheduleId + " added from Student Profile.",
                    session,
                    "student_enlistments",
                    String.valueOf(scheduleId));
            }
        } catch (Exception e) {
            redir.addFlashAttribute("message", "ERROR: Add subject failed — " + e.getMessage());
        }
        redir.addAttribute("username", username);
        return "redirect:/admin/student-manager";
    }

    @PostMapping("/admin/student-manager/credit-grade")
    public String creditTransferGrade(@RequestParam String studentNumber,
                                      @RequestParam int courseId,
                                      @RequestParam(required = false) Double numericGrade,
                                      @RequestParam(required = false) String sourceSchool,
                                      @RequestParam(required = false) String note,
                                      RedirectAttributes redir,
                                      HttpSession session) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        String username = studentNumber != null ? studentNumber.trim() : "";
        CreditGradeService.CreditRequestActionResult result =
            creditGradeService.submitCreditRequest(
                username,
                courseId,
                numericGrade,
                sourceSchool,
                note,
                currentUsername(session),
                currentUserRole(session));
        if (result.ok()) {
            redir.addFlashAttribute("successMessage", result.message());
        } else {
            redir.addFlashAttribute("message", result.message());
        }
        redir.addAttribute("username", username);
        return "redirect:/admin/student-manager";
    }

    @PostMapping("/admin/student-manager/bulk-credit")
    public String bulkCreditTransferGrades(@RequestParam String studentNumber,
                                           @RequestParam String bulkCreditCsv,
                                           @RequestParam(required = false) String defaultSourceSchool,
                                           RedirectAttributes redir,
                                           HttpSession session) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        String username = studentNumber != null ? studentNumber.trim() : "";
        CreditGradeService.BulkCreditResult result =
            creditGradeService.submitBulkCreditRequestsFromCsv(
                username,
                bulkCreditCsv,
                defaultSourceSchool,
                currentUsername(session),
                currentUserRole(session));
        String summary = "Bulk TOR request: " + result.credited() + " submitted, " + result.skipped() + " skipped.";
        if (result.credited() > 0) {
            redir.addFlashAttribute("successMessage", summary);
        } else {
            redir.addFlashAttribute("message", summary);
        }
        redir.addFlashAttribute("bulkCreditLines", result.lines());
        redir.addAttribute("username", username);
        return "redirect:/admin/student-manager";
    }

    @PostMapping("/admin/student-manager/approve-credit-request")
    public String approveTransferCreditRequest(@RequestParam long requestId,
                                               @RequestParam String studentNumber,
                                               RedirectAttributes redir,
                                               HttpSession session) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        CreditGradeService.CreditRequestActionResult result =
            creditGradeService.approveCreditRequest(requestId, currentUsername(session), currentUserRole(session));
        if (result.ok()) {
            redir.addFlashAttribute("successMessage", result.message());
        } else {
            redir.addFlashAttribute("message", result.message());
        }
        redir.addAttribute("username", studentNumber != null ? studentNumber.trim() : "");
        return "redirect:/admin/student-manager";
    }

    @PostMapping("/admin/student-manager/reject-credit-request")
    public String rejectTransferCreditRequest(@RequestParam long requestId,
                                              @RequestParam String studentNumber,
                                              @RequestParam(required = false) String rejectionReason,
                                              RedirectAttributes redir,
                                              HttpSession session) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        CreditGradeService.CreditRequestActionResult result =
            creditGradeService.rejectCreditRequest(
                requestId,
                currentUsername(session),
                currentUserRole(session),
                rejectionReason);
        if (result.ok()) {
            redir.addFlashAttribute("successMessage", result.message());
        } else {
            redir.addFlashAttribute("message", result.message());
        }
        redir.addAttribute("username", studentNumber != null ? studentNumber.trim() : "");
        return "redirect:/admin/student-manager";
    }

    @PostMapping("/admin/student-manager/shift-program")
    public String shiftStudentProgram(@RequestParam String studentNumber,
                                      @RequestParam String targetProgramCode,
                                      @RequestParam(required = false) Integer targetYearLevel,
                                      @RequestParam(required = false) Integer targetSemester,
                                      @RequestParam(required = false) Integer targetCurriculumId,
                                      @RequestParam(required = false) String reason,
                                      RedirectAttributes redir,
                                      HttpSession session) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        String username = studentNumber != null ? studentNumber.trim() : "";
        String result;
        try {
            result = jaypeeService.shiftStudentProgram(
                username, targetProgramCode, targetYearLevel, targetSemester, targetCurriculumId, reason);
        } catch (Exception e) {
            redir.addFlashAttribute("errorMessage",
                "Program shift failed: " + (e.getMessage() != null ? e.getMessage() : "Unexpected server error."));
            redir.addAttribute("username", username);
            return "redirect:/admin/student-manager";
        }
        if (result.startsWith("SUCCESS:")) {
            redir.addFlashAttribute("successMessage", result);
            recordTrail(
                username,
                "CURRICULUM",
                "PROGRAM_SHIFT_COMPLETED",
                "Registrar program shift completed",
                "Target program " + targetProgramCode +
                    (targetYearLevel != null ? " year " + targetYearLevel : "") +
                    (targetSemester != null ? " semester " + targetSemester : "") +
                    (targetCurriculumId != null ? " curriculum " + targetCurriculumId : "") +
                    (reason != null && !reason.isBlank() ? " | " + reason.trim() : ""),
                session,
                "students",
                username);
        } else {
            redir.addFlashAttribute("message", result);
        }
        redir.addAttribute("username", username);
        return "redirect:/admin/student-manager";
    }

    @PostMapping("/admin/student-manager/assign-curriculum")
    public String assignStudentCurriculum(@RequestParam String studentNumber,
                                          @RequestParam Integer curriculumId,
                                          @RequestParam(required = false) String reason,
                                          RedirectAttributes redir,
                                          HttpSession session) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        String username = studentNumber != null ? studentNumber.trim() : "";
        if (username.isEmpty()) {
            redir.addFlashAttribute("errorMessage", "Student number is required.");
            return "redirect:/admin/student-manager";
        }
        if (curriculumId == null || curriculumId <= 0) {
            redir.addFlashAttribute("errorMessage", "Choose a curriculum before assigning.");
            redir.addAttribute("username", username);
            return "redirect:/admin/student-manager";
        }
        if (isWithdrawnStudent(username)) {
            redir.addFlashAttribute("errorMessage", "Withdrawn students cannot be reassigned to a curriculum.");
            redir.addAttribute("username", username);
            return "redirect:/admin/student-manager";
        }

        String assignedBy = "registrar";
        Object currentUser = session.getAttribute("currentUser");
        if (currentUser instanceof Map<?, ?> user && user.get("username") != null) {
            assignedBy = user.get("username").toString();
        }
        String note = reason != null && !reason.isBlank()
            ? reason.trim()
            : "Assigned from Student Profile by " + assignedBy + ".";
        try {
            studentCurriculumService.assignCurriculum(username, curriculumId, "REGISTRAR_PROFILE", note, assignedBy);
            redir.addFlashAttribute("successMessage", "Current curriculum assigned for " + username + ".");
            recordTrail(
                username,
                "CURRICULUM",
                "CURRICULUM_ASSIGNED",
                "Registrar curriculum assignment updated",
                "Curriculum " + curriculumId + " assigned. " + note,
                session,
                "student_curriculum_assignments",
                String.valueOf(curriculumId));
        } catch (Exception e) {
            redir.addFlashAttribute("errorMessage", "Curriculum assignment failed: " + e.getMessage());
        }
        redir.addAttribute("username", username);
        return "redirect:/admin/student-manager";
    }
    
    @PostMapping("/admin/drop")
    public String adminDrop(@RequestParam String studentId,
                            @RequestParam int scheduleId,
                            RedirectAttributes redir) {
        redir.addFlashAttribute("errorMessage",
            "Direct subject drop is retired. Use the Student Profile drop controls.");
        return "redirect:/admin/student-manager?username=" + studentId;
    }

    @PostMapping("/admin/process-enrollment")
    public String adminProcessEnrollment(@RequestParam String studentId, @RequestParam(required = false) Integer scheduleId,
                                         HttpSession session, RedirectAttributes redir) {
        String username = studentId;
        String blockReason = subjectLoadActionBlockReason(username, scheduleId);
        if (blockReason != null) {
            redir.addFlashAttribute("errorMessage", blockReason);
            redir.addAttribute("username", username);
            return "redirect:/admin/enrollment";
        }
        String result;
        try {
            result = jaypeeService.addSubjectCrossSystem(username, scheduleId);
        } catch (RegistrationFormSnapshotException e) {
            redir.addFlashAttribute("errorMessage", "Subject add was not completed because " + e.getMessage());
            redir.addAttribute("username", username);
            return "redirect:/admin/enrollment";
        }
        if (result.startsWith("CONFLICT:") || result.startsWith("ERROR:")) {
            redir.addFlashAttribute("errorMessage", result);
        } else if (result.startsWith("SUCCESS")) {
            redir.addFlashAttribute("successMessage", normalizeSubjectAddSuccessMessage(result));
            recordTrail(
                username,
                "ENROLLMENT",
                "SUBJECT_ADD_COMPLETED",
                "Registrar subject add completed",
                "Schedule #" + scheduleId + " added from Enrollment Hub.",
                session,
                "student_enlistments",
                String.valueOf(scheduleId));
        } else {
            redir.addFlashAttribute("errorMessage", result);
        }
        redir.addAttribute("username", username);
        return "redirect:/admin/enrollment";
    }
    
    @PostMapping("/admin/block-enroll")
    public String adminBlockEnroll(@RequestParam String studentId, HttpSession session, RedirectAttributes redir) {
        String username = studentId != null ? studentId.trim() : "";
        String blockReason = subjectLoadActionBlockReason(username);
        if (blockReason != null) {
            redir.addFlashAttribute("errorMessage", blockReason);
            redir.addAttribute("username", username);
            return "redirect:/admin/enrollment";
        }
        BulkEnrollResult result = bulkEnrollEligibleSubjects(username);
        if (!result.success()) {
            redir.addFlashAttribute("errorMessage", result.message());
        } else {
            redir.addFlashAttribute("successMessage", "Block enrolled " + result.addedCount() + " subjects.");
            recordTrail(
                username,
                "ENROLLMENT",
                "BLOCK_ENROLL_COMPLETED",
                "Registrar block enrollment completed",
                "Block enrolled " + result.addedCount() + " subject(s).",
                session,
                "student_enlistments",
                username);
        }
        redir.addAttribute("username", username);
        return "redirect:/admin/enrollment";
    }

    @PostMapping("/admin/student-manager/block-enroll")
    public String adminStudentManagerBlockEnroll(@RequestParam String studentId,
                                                 HttpSession session,
                                                 RedirectAttributes redir) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        String username = studentId != null ? studentId.trim() : "";
        BulkEnrollResult result = bulkEnrollEligibleSubjects(username);
        if (!result.success()) {
            redir.addFlashAttribute("errorMessage", result.message());
        } else {
            redir.addFlashAttribute("successMessage",
                "Bulk added " + result.addedCount() + " eligible subject(s) from the assigned curriculum.");
            recordTrail(
                username,
                "ENROLLMENT",
                "STUDENT_PROFILE_BULK_ENROLL_COMPLETED",
                "Registrar profile bulk enrollment completed",
                "Bulk added " + result.addedCount() + " eligible subject(s) from Student Profile.",
                session,
                "student_enlistments",
                username);
        }
        redir.addAttribute("username", username);
        return "redirect:/admin/student-manager";
    }
    
    @PostMapping("/admin/force-enroll")
    public String adminForceEnroll(@RequestParam String studentId, @RequestParam int scheduleId,
                                   HttpSession session, RedirectAttributes redir) {
        String username = studentId;
        String blockReason = subjectLoadActionBlockReason(username);
        if (blockReason != null) {
            redir.addFlashAttribute("errorMessage", blockReason);
            redir.addAttribute("username", username);
            return "redirect:/admin/enrollment";
        }
        String result;
        try {
            result = jaypeeService.addSubjectCrossSystem(username, scheduleId);
        } catch (RegistrationFormSnapshotException e) {
            redir.addFlashAttribute("errorMessage", "Subject add was not completed because " + e.getMessage());
            redir.addAttribute("username", username);
            return "redirect:/admin/enrollment";
        }
        if (result.startsWith("ERROR:") || result.startsWith("CONFLICT:")) {
            redir.addFlashAttribute("errorMessage", result);
        } else if (result.startsWith("SUCCESS")) {
            redir.addFlashAttribute("successMessage", normalizeSubjectAddSuccessMessage(result));
            recordTrail(
                username,
                "ENROLLMENT",
                "FORCE_ENROLL_COMPLETED",
                "Registrar force enroll completed",
                "Schedule #" + scheduleId + " force enrolled.",
                session,
                "student_enlistments",
                String.valueOf(scheduleId));
        } else {
            redir.addFlashAttribute("errorMessage", result);
        }
        redir.addAttribute("username", username);
        return "redirect:/admin/enrollment";
    }

    private String normalizeSubjectAddSuccessMessage(String result) {
        if (result == null) {
            return "Subject added successfully.";
        }
        String trimmed = result.trim();
        if (trimmed.equalsIgnoreCase("SUCCESS")) {
            return "Subject added successfully.";
        }
        return trimmed.replaceFirst("^SUCCESS:\\s*", "");
    }

    @PostMapping("/admin/enrollment-drop")
    public String adminEnrollmentDrop(@RequestParam String studentId, @RequestParam int scheduleId, RedirectAttributes redir) {
        redir.addFlashAttribute("errorMsg",
            "Direct subject drop is retired. Open Student Profile and use the drop controls.");
        redir.addAttribute("username", studentId);
        return "redirect:/admin/enrollment";
    }

    @GetMapping("/admin/print-cor")
    public ResponseEntity<byte[]> printCor(@RequestParam String username, HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/login").build();
        }
        Map<String, Object> student = academicService.findStudentByIdOrName(username);
        if (student == null) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/admin/student-manager").build();
        }
        String studentNumber = String.valueOf(student.get("username"));
        Map<String, Object> finance = financeService.calculateAssessment(studentNumber);
        String releaseBlock = withdrawnDocumentReleaseBlock(student, finance, "Registration Form", session);
        if (releaseBlock != null) {
            return ResponseEntity.status(409)
                .contentType(MediaType.TEXT_PLAIN)
                .body(releaseBlock.getBytes(StandardCharsets.UTF_8));
        }
        List<Map<String, Object>> crossLoad = jaypeeService.getStudentLoad(studentNumber);
        String corTermLabel = academicService.getCurrentTermLabel();
        Map<String, Object> snapshot = registrationFormPdfService.buildSnapshot(
            student,
            crossLoad,
            finance,
            corTermLabel,
            currentUsername(session));
        regFormVersionService.saveCurrentPrintVersion(
            studentNumber,
            "Current registration form printed",
            "Registrar generated a current registration form snapshot from the live registrar view.",
            currentUsername(session),
            snapshot);
        byte[] pdf = registrationFormPdfService.renderSnapshot(snapshot);
        documentTrailService.recordStudentEvent(
            studentNumber,
            "STUDENT",
            "REGISTRATION_FORM",
            "PRINTED",
            "Registration Form printed",
            "Registrar generated registration form PDF aligned to the admission pre-registration format.",
            currentUsername(session),
            null,
            "print_cor",
            studentNumber);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"registration-form-" + studentNumber + ".pdf\"")
            .body(pdf);
    }

    @GetMapping("/admin/print-cog")
    public String printCog(@RequestParam String username, Model model, HttpSession session,
                           RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        Map<String, Object> student = academicService.findStudentByIdOrName(username);
        if (student != null) {
            String studentNumber = String.valueOf(student.get("username"));
            String releaseBlock = withdrawnDocumentReleaseBlock(
                student, financeService.calculateAssessment(studentNumber), "Certificate of Grades", session);
            if (releaseBlock != null) {
                ra.addFlashAttribute("errorMessage", releaseBlock);
                return "redirect:/admin/student-manager?username=" + studentNumber;
            }
            model.addAttribute("student", student);
            model.addAttribute("academicHistory", academicService.getStudentAcademicHistory(((Number) student.get("user_id")).intValue()));
            documentTrailService.recordStudentEvent(
                studentNumber,
                "STUDENT",
                "COG",
                "PRINTED",
                "Certificate of Grades printed",
                "Registrar generated COG print output.",
                currentUsername(session),
                null,
                "print_cog",
                studentNumber);
            return "print_cog";
        }
        return "redirect:/admin/student-manager";
    }

    @GetMapping("/admin/print-tor")
    public String printTor(@RequestParam String username, Model model, HttpSession session,
                           RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        Map<String, Object> student = academicService.findStudentByIdOrName(username);
        if (student != null) {
            String studentNumber = String.valueOf(student.get("username"));
            String releaseBlock = withdrawnDocumentReleaseBlock(
                student, financeService.calculateAssessment(studentNumber), "Transcript of Records", session);
            if (releaseBlock != null) {
                ra.addFlashAttribute("errorMessage", releaseBlock);
                return "redirect:/admin/student-manager?username=" + studentNumber;
            }
            model.addAttribute("student", student);
            model.addAttribute("academicHistory", academicService.getStudentAcademicHistory(((Number) student.get("user_id")).intValue()));
            documentTrailService.recordStudentEvent(
                studentNumber,
                "STUDENT",
                "TOR",
                "PRINTED",
                "Transcript of Records printed",
                "Registrar generated TOR print output.",
                currentUsername(session),
                null,
                "print_tor",
                studentNumber);
            return "print_tor";
        }
        return "redirect:/admin/student-manager";
    }

    @PostMapping("/admin/student-manager/save-installments")
    public String saveStudentInstallments(@RequestParam String studentNumber,
                                          @RequestParam Integer installmentTermId,
                                          @RequestParam(required = false) List<Integer> instNumber,
                                          @RequestParam(required = false) List<Integer> instDueMonths,
                                          @RequestParam(required = false) List<String> instLabel,
                                          HttpSession session,
                                          RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        ra.addFlashAttribute("errorMessage", STUDENT_INSTALLMENT_MIRROR_NOTICE);
        return "redirect:/admin/student-manager?username=" + studentNumber.trim();
    }

    @PostMapping("/admin/student-manager/archive-custody")
    public String recordArchiveCustody(@RequestParam String studentNumber,
                                       @RequestParam String eventType,
                                       @RequestParam(required = false) String counterpart,
                                       @RequestParam(required = false) String purpose,
                                       @RequestParam(required = false) String storageLocation,
                                       @RequestParam(required = false) String remarks,
                                       HttpSession session,
                                       RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        String result = archiveCustodyService.recordEvent(
            studentNumber,
            eventType,
            currentUsername(session),
            counterpart,
            purpose,
            storageLocation,
            remarks);
        if ("SUCCESS".equals(result)) {
            ra.addFlashAttribute("successMessage", "Archive custody movement recorded.");
        } else {
            ra.addFlashAttribute("errorMessage", result);
        }
        return "redirect:/admin/student-manager?username=" + studentNumber.trim();
    }

    @PostMapping("/admin/student-manager/copy-installments-from-term")
    public String copyStudentInstallmentsFromTerm(@RequestParam String studentNumber,
                                                  @RequestParam Integer installmentTermId,
                                                  HttpSession session,
                                                  RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        ra.addFlashAttribute("errorMessage", STUDENT_INSTALLMENT_MIRROR_NOTICE);
        return "redirect:/admin/student-manager?username=" + studentNumber.trim();
    }

    @PostMapping("/admin/student-manager/clear-installments")
    public String clearStudentInstallments(@RequestParam String studentNumber,
                                           @RequestParam Integer installmentTermId,
                                           HttpSession session,
                                           RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        ra.addFlashAttribute("errorMessage", STUDENT_INSTALLMENT_MIRROR_NOTICE);
        return "redirect:/admin/student-manager?username=" + studentNumber.trim();
    }

    private BulkEnrollResult bulkEnrollEligibleSubjects(String username) {
        if (username == null || username.isBlank()) {
            return new BulkEnrollResult(0, "Student number is required.");
        }
        if (isWithdrawnStudent(username)) {
            return new BulkEnrollResult(0,
                "Withdrawn students cannot be enrolled in subjects. Their history stays under the archive record, and any future student-number reuse must happen through the registrar release workflow.");
        }
        List<Map<String, Object>> classes = jaypeeService.getCrossSystemAnalyzedOfferings(username, true);
        int addedCount = 0;
        java.util.Set<Integer> enrolledCourseIds = new java.util.HashSet<>();

        for (Map<String, Object> c : classes) {
            if (Boolean.TRUE.equals(c.get("is_disabled"))) {
                continue;
            }
            if (!(c.get("course_id") instanceof Number courseIdNumber)
                || !(c.get("schedule_id") instanceof Number scheduleIdNumber)) {
                continue;
            }
            int courseId = courseIdNumber.intValue();
            if (enrolledCourseIds.contains(courseId)) {
                continue;
            }
            int scheduleId = scheduleIdNumber.intValue();
            int beforeCount = jaypeeService.getStudentLoad(username).size();
            String result;
            try {
                result = jaypeeService.addSubjectCrossSystem(username, scheduleId, true);
            } catch (RegistrationFormSnapshotException e) {
                return new BulkEnrollResult(0, "Bulk enrollment stopped because " + e.getMessage());
            }
            if (result.startsWith("SUCCESS")) {
                List<Map<String, Object>> refreshedLoad = jaypeeService.getStudentLoad(username);
                addedCount += Math.max(1, refreshedLoad.size() - beforeCount);
                for (Map<String, Object> enrolled : refreshedLoad) {
                    if (enrolled.get("course_id") instanceof Number enrolledCourseId) {
                        enrolledCourseIds.add(enrolledCourseId.intValue());
                    }
                }
            }
        }

        if (addedCount == 0) {
            return new BulkEnrollResult(0,
                "No eligible classes were bulk-added. Classes may be full, conflicting, already enrolled, missing prerequisites, or above the curriculum load limit.");
        }
        return new BulkEnrollResult(addedCount,
            "Bulk added " + addedCount + " eligible subject(s) from the assigned curriculum.");
    }

    private <T> List<T> defaultList(List<T> source) {
        return source != null ? source : List.of();
    }

    private boolean isWithdrawnStudent(String username) {
        Map<String, Object> student = academicService.findStudentByIdOrName(username);
        if (student == null) {
            return false;
        }
        Object status = student.get("admission_status");
        return status != null && "WITHDRAWN".equalsIgnoreCase(String.valueOf(status));
    }

    private String withdrawnDocumentReleaseBlock(Map<String, Object> student,
                                                 Map<String, Object> finance,
                                                 String documentName,
                                                 HttpSession session) {
        if (student == null) {
            return null;
        }
        String status = String.valueOf(student.getOrDefault("admission_status", ""));
        if (!"WITHDRAWN".equalsIgnoreCase(status)) {
            return null;
        }
        double balance = numberValue(finance != null ? finance.get("balance") : null);
        if (balance <= 0.01) {
            return null;
        }
        String studentNumber = String.valueOf(student.get("username"));
        String message = documentName + " release blocked: withdrawn student has outstanding balance of PHP "
            + String.format("%,.2f", balance) + ". Settle the ledger before official document release.";
        documentTrailService.recordStudentEvent(
            studentNumber,
            "STUDENT",
            "DOCUMENT_RELEASE",
            "DOCUMENT_RELEASE_BLOCKED",
            documentName + " release blocked",
            message,
            currentUsername(session),
            null,
            "document_release_guard",
            studentNumber);
        return message;
    }

    private double numberValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private List<Map<String, Object>> buildEnrollmentLedgerPreview(List<Map<String, Object>> ledger) {
        if (ledger == null || ledger.isEmpty()) {
            return List.of();
        }
        int start = Math.max(0, ledger.size() - ENROLLMENT_LEDGER_PREVIEW_LIMIT);
        List<Map<String, Object>> recent = new ArrayList<>(ledger.subList(start, ledger.size()));
        Collections.reverse(recent);
        List<Map<String, Object>> preview = new ArrayList<>(recent.size());
        for (Map<String, Object> row : recent) {
            preview.add(buildEnrollmentLedgerPreviewRow(row));
        }
        return preview;
    }

    private Map<String, Object> buildEnrollmentLedgerPreviewRow(Map<String, Object> ledgerRow) {
        String type = textValue(ledgerRow.get("transaction_type")).trim().toUpperCase();
        String description = textValue(ledgerRow.get("description")).trim();
        double debit = numberValue(ledgerRow.get("debit"));
        double credit = numberValue(ledgerRow.get("credit"));
        double runningBalance = numberValue(ledgerRow.get("running_balance"));
        boolean debitMovement = debit > 0.009d;
        boolean creditMovement = credit > 0.009d;
        double amount = debitMovement ? debit : credit;
        String category = ledgerPreviewCategory(type);

        HashMap<String, Object> previewRow = new HashMap<>();
        previewRow.put("label", ledgerPreviewLabel(type));
        previewRow.put("detail", ledgerPreviewDetail(type, description));
        previewRow.put("subjectCode", extractLedgerSubjectCode(description));
        previewRow.put("category", category);
        previewRow.put("badgeStyle", ledgerBadgeStyle(category));
        previewRow.put("movementLabel", debitMovement ? "Debit" : (creditMovement ? "Credit" : "Entry"));
        previewRow.put("amountFmt", String.format("%,.2f", amount));
        previewRow.put("amountStyle", ledgerAmountStyle(debitMovement, creditMovement));
        previewRow.put("runningBalanceFmt", String.format("%,.2f", runningBalance));
        previewRow.put("timestampFmt", formatLedgerTimestamp(ledgerRow.get("transaction_date")));
        previewRow.put("description", description);
        previewRow.put("transactionType", type);
        return previewRow;
    }

    private String ledgerPreviewLabel(String type) {
        return switch (type) {
            case "SUBJECT_ADD" -> "Subject Added";
            case "DROP_PENALTY" -> "Subject Drop Charge";
            case "REFUND" -> "Subject Drop Refund";
            case "PAYMENT" -> "Payment";
            case "INITIAL_PAYMENT" -> "Initial Payment";
            case "FORWARDED_BALANCE" -> "Forwarded Balance";
            case "PENDING_TERM_CREDIT" -> "Pending Term Credit";
            case "REFUND_PAYOUT" -> "Refund Payout";
            case "TUITION_ASSESSMENT" -> "Tuition Assessment";
            case "MISC_ASSESSMENT" -> "Misc Assessment";
            case "OTHER_ASSESSMENT" -> "Other Assessment";
            case "RLE_ASSESSMENT" -> "RLE Assessment";
            default -> humanizeLedgerType(type);
        };
    }

    private String ledgerPreviewCategory(String type) {
        return switch (type) {
            case "SUBJECT_ADD" -> "add";
            case "DROP_PENALTY" -> "drop-charge";
            case "REFUND" -> "drop-refund";
            case "PAYMENT", "INITIAL_PAYMENT" -> "payment";
            case "FORWARDED_BALANCE", "PENDING_TERM_CREDIT", "REFUND_PAYOUT" -> "balance";
            case "TUITION_ASSESSMENT", "MISC_ASSESSMENT", "OTHER_ASSESSMENT", "RLE_ASSESSMENT" -> "assessment";
            default -> "general";
        };
    }

    private String ledgerPreviewDetail(String type, String description) {
        Matcher subjectMatch = SUBJECT_LEDGER_PATTERN.matcher(description);
        if (subjectMatch.matches()) {
            String tail = subjectMatch.group(3) != null ? subjectMatch.group(3).trim() : "";
            if (!tail.isBlank()) {
                return tail;
            }
        }
        return switch (type) {
            case "SUBJECT_ADD" -> "Added to the current-term load.";
            case "DROP_PENALTY" -> "Drop charge posted to the ledger.";
            case "REFUND" -> "Drop refund posted to the ledger.";
            case "PAYMENT" -> description.isBlank() ? "Payment posted to the ledger." : description;
            case "INITIAL_PAYMENT" -> description.isBlank() ? "Initial payment posted to the ledger." : description;
            case "FORWARDED_BALANCE" -> description.isBlank() ? "Prior-term balance carried into the current term." : description;
            case "PENDING_TERM_CREDIT" -> description.isBlank() ? "Pending overpayment credit remains on hold." : description;
            case "REFUND_PAYOUT" -> description.isBlank() ? "Refund payout recorded." : description;
            case "TUITION_ASSESSMENT", "MISC_ASSESSMENT", "OTHER_ASSESSMENT", "RLE_ASSESSMENT" ->
                description.isBlank() ? "Assessment entry posted." : description;
            default -> description.isBlank() ? "Ledger entry posted." : description;
        };
    }

    private String extractLedgerSubjectCode(String description) {
        Matcher subjectMatch = SUBJECT_LEDGER_PATTERN.matcher(description);
        if (!subjectMatch.matches()) {
            return "";
        }
        return subjectMatch.group(2) != null ? subjectMatch.group(2).trim() : "";
    }

    private String ledgerBadgeStyle(String category) {
        return switch (category) {
            case "add" -> "display:inline-flex;align-items:center;padding:0.2rem 0.5rem;border-radius:999px;background:#ECFDF5;color:#047857;font-size:0.72rem;font-weight:700;";
            case "drop-charge" -> "display:inline-flex;align-items:center;padding:0.2rem 0.5rem;border-radius:999px;background:#FEF2F2;color:#B91C1C;font-size:0.72rem;font-weight:700;";
            case "drop-refund" -> "display:inline-flex;align-items:center;padding:0.2rem 0.5rem;border-radius:999px;background:#EFF6FF;color:#1D4ED8;font-size:0.72rem;font-weight:700;";
            case "payment" -> "display:inline-flex;align-items:center;padding:0.2rem 0.5rem;border-radius:999px;background:#EFF6FF;color:#075985;font-size:0.72rem;font-weight:700;";
            case "balance" -> "display:inline-flex;align-items:center;padding:0.2rem 0.5rem;border-radius:999px;background:#FFF7ED;color:#B45309;font-size:0.72rem;font-weight:700;";
            case "assessment" -> "display:inline-flex;align-items:center;padding:0.2rem 0.5rem;border-radius:999px;background:#F8FAFC;color:#475569;font-size:0.72rem;font-weight:700;";
            default -> "display:inline-flex;align-items:center;padding:0.2rem 0.5rem;border-radius:999px;background:#F1F5F9;color:#334155;font-size:0.72rem;font-weight:700;";
        };
    }

    private String ledgerAmountStyle(boolean debitMovement, boolean creditMovement) {
        if (debitMovement) {
            return "font-size:1rem;font-weight:800;color:#B91C1C;";
        }
        if (creditMovement) {
            return "font-size:1rem;font-weight:800;color:#047857;";
        }
        return "font-size:1rem;font-weight:800;color:#334155;";
    }

    private String formatLedgerTimestamp(Object transactionDate) {
        if (transactionDate == null) {
            return "Date unavailable";
        }
        String raw = transactionDate.toString().trim();
        int fractionalStart = raw.indexOf('.');
        if (fractionalStart > 0) {
            raw = raw.substring(0, fractionalStart);
        }
        return raw.replace('T', ' ');
    }

    private String humanizeLedgerType(String type) {
        if (type == null || type.isBlank()) {
            return "Ledger Entry";
        }
        StringBuilder label = new StringBuilder();
        for (String part : type.split("_")) {
            if (part.isBlank()) {
                continue;
            }
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(part.substring(0, 1).toUpperCase())
                .append(part.substring(1).toLowerCase());
        }
        return label.isEmpty() ? "Ledger Entry" : label.toString();
    }

    private String textValue(Object value) {
        return value != null ? value.toString() : "";
    }

    private String subjectLoadActionBlockReason(String studentNumber) {
        String sn = studentNumber != null ? studentNumber.trim() : "";
        if (sn.isEmpty()) {
            return "Student number is required.";
        }
        Map<String, Object> student = academicService.findStudentByIdOrName(sn);
        if (student == null) {
            return "Student not found.";
        }
        String admStatus = student.get("admission_status") != null ? student.get("admission_status").toString() : "";
        if ("WITHDRAWN".equalsIgnoreCase(admStatus)) {
            return "Withdrawn students cannot modify current-term load.";
        }
        if (!"ENROLLED".equalsIgnoreCase(admStatus)) {
            return "Current-term subject add/drop is available only for ENROLLED students.";
        }
        Map<String, Object> finSummary = financeService.calculateAssessment(sn);
        if (Boolean.TRUE.equals(finSummary.get("has_accounting_block"))) {
            double forwardDebt = finSummary.get("balance_forwarded") instanceof Number
                ? ((Number) finSummary.get("balance_forwarded")).doubleValue()
                : 0.0;
            return "Enrollment is blocked until the prior-term balance of PHP "
                + String.format("%,.2f", forwardDebt)
                + " is settled at Cashier first.";
        }
        if (Boolean.TRUE.equals(finSummary.get("has_pending_overpay"))) {
            double pendingCredit = finSummary.get("pending_term_credit") instanceof Number
                ? ((Number) finSummary.get("pending_term_credit")).doubleValue()
                : 0.0;
            return "Enrollment is blocked until the prior-term overpayment of PHP "
                + String.format("%,.2f", pendingCredit)
                + " is resolved.";
        }
        return null;
    }

    private String subjectLoadActionBlockReason(String studentNumber, Integer scheduleId) {
        String baseReason = subjectLoadActionBlockReason(studentNumber);
        if (baseReason != null) {
            return baseReason;
        }
        if (scheduleId == null || scheduleId <= 0) {
            return "Choose a section before clicking Add.";
        }
        List<Map<String, Object>> groupedCourses = defaultList(jaypeeService.getGroupedCourseOfferings(studentNumber));
        for (Map<String, Object> course : groupedCourses) {
            List<Map<String, Object>> sections = defaultList((List<Map<String, Object>>) course.get("sections"));
            for (Map<String, Object> section : sections) {
                int sectionId = section.get("section_id") instanceof Number number ? number.intValue() : 0;
                if (sectionId != scheduleId) {
                    continue;
                }
                String sectionReason = section.get("reason_msg") != null ? section.get("reason_msg").toString().trim() : "";
                String courseReason = course.get("reason_msg") != null ? course.get("reason_msg").toString().trim() : "";
                if (Boolean.TRUE.equals(course.get("is_disabled")) || Boolean.TRUE.equals(section.get("is_disabled"))) {
                    if (!courseReason.isBlank()) {
                        return courseReason;
                    }
                    if (!sectionReason.isBlank()) {
                        return sectionReason;
                    }
                    return "Selected section is not available.";
                }
                return null;
            }
        }
        String irregularReason = jaypeeService.irregularAccessBlockReasonForStudent(studentNumber, scheduleId);
        if (irregularReason != null) {
            return irregularReason;
        }
        return "Selected section is not available.";
    }

    private record BulkEnrollResult(int addedCount, String message) {
        private boolean success() {
            return addedCount > 0;
        }
    }

    private void recordTrail(String studentNumber,
                             String documentType,
                             String eventType,
                             String summary,
                             String details,
                             HttpSession session,
                             String sourceTable,
                             String sourceId) {
        documentTrailService.recordStudentEvent(
            studentNumber,
            "STUDENT",
            documentType,
            eventType,
            summary,
            details,
            currentUsername(session),
            null,
            sourceTable,
            sourceId);
    }

    private String currentUsername(HttpSession session) {
        Object raw = session.getAttribute("currentUser");
        if (raw instanceof Map<?, ?> user && user.get("username") != null) {
            return user.get("username").toString();
        }
        return "registrar";
    }

    private String currentUserRole(HttpSession session) {
        Object raw = session.getAttribute("currentUser");
        if (raw instanceof Map<?, ?> user && user.get("role") != null) {
            return user.get("role").toString();
        }
        return null;
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : value.toString();
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
