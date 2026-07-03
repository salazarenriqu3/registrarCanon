package com.iuims.registrar.portal;
import com.iuims.registrar.academic.AcademicGradingService;
import com.iuims.registrar.core.GradeOutcomeSql;
import com.iuims.registrar.admission.ApplicantStatusSyncService;
import com.iuims.registrar.admission.FinanceAdmissionService;
import com.iuims.registrar.admission.ApplicantDocumentReadService;
import com.iuims.registrar.curriculum.CurriculumSeederService;
import com.iuims.registrar.curriculum.CreditGradeService;
import com.iuims.registrar.curriculum.StudentCurriculumService;
import com.iuims.registrar.core.EnlistmentSchemaService;
import com.iuims.registrar.core.StudentProfileService;
import com.iuims.registrar.core.StudentIdentityReleaseService;
import com.iuims.registrar.faculty.FacultyLoadService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.finance.FinancePolicyService;
import com.iuims.registrar.finance.OverpayDispositionService;
import com.iuims.registrar.finance.TermFeeAdminService;
import com.iuims.registrar.forms.RegFormEventService;
import com.iuims.registrar.forms.RegistrationFormPdfService;
import com.iuims.registrar.forms.StudentArchiveCustodyService;
import com.iuims.registrar.forms.StudentDocumentTrailService;
import com.iuims.registrar.core.DatabaseSetupService;
import com.iuims.registrar.jaypee.JaypeeIntegrationService;
import com.iuims.registrar.core.PolicySettings;
import com.iuims.registrar.core.SqlGenerator;
import com.iuims.registrar.withdrawal.WithdrawalService;

import com.iuims.registrar.academic.AcademicGradingService;
import com.iuims.registrar.admission.FinanceAdmissionService;
import com.iuims.registrar.jaypee.JaypeeIntegrationService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.curriculum.CreditGradeService;
import com.iuims.registrar.curriculum.StudentCurriculumService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
public class EnrollmentController {

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
    private final RegistrationFormPdfService registrationFormPdfService;
    private final StudentArchiveCustodyService archiveCustodyService;
    private final StudentDocumentTrailService documentTrailService;
    private final StudentProfileService studentProfileService;
    private final StudentIdentityReleaseService studentIdentityReleaseService;
    private final ApplicantDocumentReadService applicantDocumentReadService;

    public EnrollmentController(AcademicGradingService academicService, JaypeeIntegrationService jaypeeService,
                                FinanceAdmissionService financeService, ScholarEnrollmentService scholarEnrollmentService,
                                StudentCurriculumService studentCurriculumService, CreditGradeService creditGradeService,
                                FinancePolicyService financePolicyService, TermFeeAdminService termFeeAdminService,
                                OverpayDispositionService overpayDispositionService,
                                WithdrawalService withdrawalService,
                                RegFormEventService regFormEventService,
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
        this.registrationFormPdfService = registrationFormPdfService;
        this.archiveCustodyService = archiveCustodyService;
        this.documentTrailService = documentTrailService;
        this.studentProfileService = studentProfileService;
        this.studentIdentityReleaseService = studentIdentityReleaseService;
        this.applicantDocumentReadService = applicantDocumentReadService;
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
                    "http://localhost:8082/admin/cashier?keyword=" +
                        UriUtils.encodeQueryParam(actualStudentNumber, StandardCharsets.UTF_8));
                
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
            model.addAttribute("enrollmentCashierUrl", "http://localhost:8082/admin/cashier");
        }
        return "admin_student_manager";
    }

    @GetMapping("/admin/enrollment")
    public String adminEnrollmentHub(@RequestParam(required=false) String username, @RequestParam(required=false) String errorMsg, Model model, HttpSession session) {
        financeService.syncVerifiedPayments(); 
        Map<String, Object> currentUser = (Map<String, Object>) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/login";

        model.addAttribute("isAdmin", "admin".equalsIgnoreCase((String) currentUser.get("username")) || "Registrar".equalsIgnoreCase((String) currentUser.get("role")));
        if (errorMsg != null) model.addAttribute("errorMsg", errorMsg);
        
        if (username != null && !username.trim().isEmpty()) {
            Map<String, Object> s = academicService.findStudentByIdOrName(username);
            if (s != null) {
                String actualStudentNumber = (String) s.get("username"); 
                int sid = s.get("user_id") != null ? ((Number) s.get("user_id")).intValue() : 0;
                int yrLvl = s.get("year_level") != null ? ((Number) s.get("year_level")).intValue() : 1;
                String admStatus = s.get("admission_status") != null ? s.get("admission_status").toString() : "";
                String studentType = s.get("student_type") != null ? s.get("student_type").toString() : "";

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
                boolean isContinuing = "Continuing".equalsIgnoreCase(studentType) || "Old Student".equalsIgnoreCase(studentType);
                
                boolean canEnroll = ("ENROLLED".equalsIgnoreCase(admStatus) || yrLvl >= 2 || isContinuing)
                    && !hasAccountingBlock && !hasPendingOverpay;
                
                if (hasAccountingBlock) {
                    model.addAttribute("hasOutstandingBalance", true);
                    model.addAttribute("outstandingBalanceFmt", String.format("%,.2f", forwardDebt));
                }
                if (hasPendingOverpay) {
                    model.addAttribute("hasPendingOverpay", true);
                    model.addAttribute("pendingTermCreditFmt", String.format("%,.2f", pendingCredit));
                }

                boolean isTransferee = yrLvl >= 2 || isContinuing;

                model.addAttribute("student", s);
                model.addAttribute("canEnroll", canEnroll);
                model.addAttribute("isTransferee", isTransferee);

                List<Map<String, Object>> crossLoad = jaypeeService.getStudentLoad(actualStudentNumber);
                model.addAttribute("studentLoad", crossLoad);
                model.addAttribute("hasEnrolledSubjects", !crossLoad.isEmpty());

                if (canEnroll) {
                    model.addAttribute("classes", jaypeeService.getCrossSystemAnalyzedOfferings(actualStudentNumber));
                }
                
                int total = 0; for(Map<String,Object> cls : crossLoad) { if(cls.get("units") != null) total += ((Number)cls.get("units")).intValue(); }
                model.addAttribute("totalUnits", total);
                model.addAttribute("maxUnits", academicService.getDynamicMaxUnits(sid));
                model.addAttribute("isGraduating", academicService.isGraduatingStudent(actualStudentNumber));
                model.addAttribute("finance", finSummary);
                model.addAttribute("ledger", financeService.getStudentLedger(actualStudentNumber));
            } else { model.addAttribute("message", "Student not found."); }
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
        model.addAttribute("pageTitle", "Reg Form History");
        model.addAttribute("pageSubtitle", "Audit trail of registrar-side registration form changes.");
        model.addAttribute("studentNumber", studentNumber != null ? studentNumber.trim() : "");
        model.addAttribute("eventType", eventType != null ? eventType.trim().toUpperCase() : "");
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("limit", Math.max(1, Math.min(limit, 500)));
        model.addAttribute("eventTypeSummary", regFormEventService.eventTypeSummary());
        model.addAttribute("historySummary", regFormEventService.historySummary(fromDate, toDate));
        model.addAttribute("events", regFormEventService.listRecentEvents(studentNumber, eventType, fromDate, toDate, limit));
        return "admin_reg_form_history";
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
    public String adminProcessEnrollment(@RequestParam String studentId, @RequestParam int scheduleId,
                                         HttpSession session, RedirectAttributes redir) {
        String username = studentId;
        String result = jaypeeService.addSubjectCrossSystem(username, scheduleId);
        if (result.startsWith("CONFLICT:") || result.startsWith("ERROR:")) redir.addFlashAttribute("message", result);
        else if (result.startsWith("SUCCESS")) {
            recordTrail(
                username,
                "ENROLLMENT",
                "SUBJECT_ADD_COMPLETED",
                "Registrar subject add completed",
                "Schedule #" + scheduleId + " added from Enrollment Hub.",
                session,
                "student_enlistments",
                String.valueOf(scheduleId));
        }
        redir.addAttribute("username", username);
        return "redirect:/admin/enrollment";
    }
    
    @PostMapping("/admin/block-enroll")
    public String adminBlockEnroll(@RequestParam String studentId, HttpSession session, RedirectAttributes redir) {
        String username = studentId != null ? studentId.trim() : "";
        BulkEnrollResult result = bulkEnrollEligibleSubjects(username);
        if (!result.success()) redir.addAttribute("errorMsg", result.message());
        else {
            redir.addAttribute("errorMsg", "SUCCESS: Block enrolled " + result.addedCount() + " subjects.");
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
        String result = jaypeeService.addSubjectCrossSystem(username, scheduleId);
        if (result.startsWith("ERROR:")) redir.addAttribute("errorMsg", result);
        else if (result.startsWith("SUCCESS")) {
            recordTrail(
                username,
                "ENROLLMENT",
                "FORCE_ENROLL_COMPLETED",
                "Registrar force enroll completed",
                "Schedule #" + scheduleId + " force enrolled.",
                session,
                "student_enlistments",
                String.valueOf(scheduleId));
        }
        redir.addAttribute("username", username);
        return "redirect:/admin/enrollment";
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
        byte[] pdf = registrationFormPdfService.render(
            student,
            crossLoad,
            finance,
            corTermLabel,
            currentUsername(session));
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
        List<FinancePolicyService.InstallmentRow> rows =
            FinancePolicyService.parseInstallmentRows(instNumber, instDueMonths, instLabel);
        int saved = financePolicyService.saveStudentInstallmentPlan(studentNumber, installmentTermId, rows);
        ra.addFlashAttribute("successMessage",
            "Student installment override saved (" + saved + " row(s)) for active term.");
        recordTrail(
            studentNumber,
            "FINANCE",
            "INSTALLMENT_PLAN_SAVED",
            "Student installment override saved",
            "Saved " + saved + " installment row(s) for term #" + installmentTermId + ".",
            session,
            "student_installment_plans",
            String.valueOf(installmentTermId));
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
        int copied = financePolicyService.copyTermPlanToStudent(studentNumber, installmentTermId);
        if (copied == 0) {
            ra.addFlashAttribute("errorMessage", "No term/default installment rows to copy.");
        } else {
            ra.addFlashAttribute("successMessage",
                "Copied " + copied + " row(s) from term plan into student override.");
            recordTrail(
                studentNumber,
                "FINANCE",
                "INSTALLMENT_PLAN_COPIED",
                "Student installment override copied",
                "Copied " + copied + " installment row(s) from term plan #" + installmentTermId + ".",
                session,
                "student_installment_plans",
                String.valueOf(installmentTermId));
        }
        return "redirect:/admin/student-manager?username=" + studentNumber.trim();
    }

    @PostMapping("/admin/student-manager/clear-installments")
    public String clearStudentInstallments(@RequestParam String studentNumber,
                                           @RequestParam Integer installmentTermId,
                                           HttpSession session,
                                           RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        financePolicyService.clearStudentInstallmentPlan(studentNumber, installmentTermId);
        recordTrail(
            studentNumber,
            "FINANCE",
            "INSTALLMENT_PLAN_CLEARED",
            "Student installment override cleared",
            "Cleared student installment override for term #" + installmentTermId + ".",
            session,
            "student_installment_plans",
            String.valueOf(installmentTermId));
        ra.addFlashAttribute("successMessage", "Student installment override cleared — using term/default plan.");
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
            String result = jaypeeService.addSubjectCrossSystem(username, scheduleId, true);
            if (result.startsWith("SUCCESS")) {
                addedCount++;
                enrolledCourseIds.add(courseId);
            }
        }

        if (addedCount == 0) {
            return new BulkEnrollResult(0,
                "No eligible classes were bulk-added. Classes may be full, conflicting, already enrolled, missing prerequisites, or above the curriculum load limit.");
        }
        return new BulkEnrollResult(addedCount,
            "Bulk added " + addedCount + " eligible subject(s) from the assigned curriculum.");
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
