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
import com.iuims.registrar.faculty.FacultyLoadService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.finance.FinancePolicyService;
import com.iuims.registrar.finance.OverpayDispositionService;
import com.iuims.registrar.finance.TermFeeAdminService;
import com.iuims.registrar.forms.RegFormEventService;
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
    private final StudentDocumentTrailService documentTrailService;
    private final StudentProfileService studentProfileService;
    private final ApplicantDocumentReadService applicantDocumentReadService;

    public EnrollmentController(AcademicGradingService academicService, JaypeeIntegrationService jaypeeService,
                                FinanceAdmissionService financeService, ScholarEnrollmentService scholarEnrollmentService,
                                StudentCurriculumService studentCurriculumService, CreditGradeService creditGradeService,
                                FinancePolicyService financePolicyService, TermFeeAdminService termFeeAdminService,
                                OverpayDispositionService overpayDispositionService,
                                WithdrawalService withdrawalService,
                                RegFormEventService regFormEventService,
                                StudentDocumentTrailService documentTrailService,
                                StudentProfileService studentProfileService,
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
        this.documentTrailService = documentTrailService;
        this.studentProfileService = studentProfileService;
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
                model.addAttribute("admissionSnapshot",
                    applicantDocumentReadService.getAdmissionSnapshot(actualStudentNumber));
                model.addAttribute("applicantDocuments",
                    applicantDocumentReadService.listDocuments(actualStudentNumber));
                model.addAttribute("enrollmentCashierUrl",
                    "/enrollment/admin/cashier?keyword=" +
                        UriUtils.encodeQueryParam(actualStudentNumber, StandardCharsets.UTF_8));
                
                List<Map<String, Object>> crossLoad = jaypeeService.getStudentLoad(actualStudentNumber);
                model.addAttribute("studentLoad", crossLoad);
                model.addAttribute("withdrawalReasons", withdrawalService.listActiveReasons());
                model.addAttribute("studentWithdrawalRequests",
                    withdrawalService.listStudentRequests(actualStudentNumber));
                model.addAttribute("regFormEvents", regFormEventService.listStudentEvents(actualStudentNumber));
                String admStatus = s.get("admission_status") != null ? s.get("admission_status").toString() : "";
                boolean hasEnrolledSubjects = !crossLoad.isEmpty();
                boolean isEnrolledStatus = "ENROLLED".equalsIgnoreCase(admStatus) && hasEnrolledSubjects;
                model.addAttribute("isEnrolledStatus", isEnrolledStatus);
                model.addAttribute("hasEnrolledSubjects", hasEnrolledSubjects);

                // Load grouped offerings (one row per course, sections as dropdown)
                if (hasEnrolledSubjects) {
                    model.addAttribute("groupedCourses", jaypeeService.getGroupedCourseOfferings(
                        actualStudentNumber, offeringSchool, offeringProgram, offeringQ));
                }
                model.addAttribute("offeringSchools", jaypeeService.listOfferingSchools());
                model.addAttribute("offeringPrograms", jaypeeService.listOfferingPrograms());
                model.addAttribute("currentCurriculum", studentCurriculumService.getCurrentAssignment(actualStudentNumber));
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
                model.addAttribute("scholarshipTypes", scholarEnrollmentService.getActiveScholarshipTypes());
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
                financeNode.put("pending_term_credit", finSummary.getOrDefault("pending_term_credit", 0.0));
                financeNode.put("pending_term_credit_fmt", finSummary.getOrDefault("pending_term_credit_fmt", "0.00"));
                financeNode.put("has_pending_overpay", finSummary.getOrDefault("has_pending_overpay", false));
                financeNode.put("has_accounting_block", finSummary.getOrDefault("has_accounting_block", false));
                model.addAttribute("finance", financeNode);
                model.addAttribute("hasAccountingBlock", Boolean.TRUE.equals(finSummary.get("has_accounting_block")));
                model.addAttribute("accountingBlockThresholdFmt", finSummary.getOrDefault("accounting_block_threshold_fmt", "0.00"));
                model.addAttribute("forwardedBalanceFmt", finSummary.getOrDefault("balance_forwarded_fmt", "0.00"));
                model.addAttribute("outstandingBalanceFmt", finSummary.getOrDefault("balance_fmt", "0.00"));
                model.addAttribute("hasPendingOverpay", Boolean.TRUE.equals(finSummary.get("has_pending_overpay")));
                model.addAttribute("pendingTermCreditFmt", finSummary.getOrDefault("pending_term_credit_fmt", "0.00"));
                
                model.addAttribute("ledger", financeService.getStudentLedger(actualStudentNumber));

                Integer activeTermId = termFeeAdminService.getActiveTermId();
                model.addAllAttributes(financePolicyService.buildStudentInstallmentView(actualStudentNumber, activeTermId));
            } else { model.addAttribute("message", "Student not found."); }
        } else {
            model.addAttribute("studentRoster", academicService.getStudentRoster(offeringProgram));
            model.addAttribute("offeringPrograms", jaypeeService.listOfferingPrograms());
            model.addAttribute("selectedOfferingProgram", offeringProgram != null ? offeringProgram : "All");
        }
        model.addAttribute("enrollmentCashierUrl", "http://localhost:8082/admin/cashier");
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
                String progCode = (String) s.get("program_code");
                model.addAttribute("isGraduating", academicService.isGraduating(progCode, yrLvl));
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
            List<String> changed = studentProfileService.updateProfile(studentNumber, form);
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
                                                          HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            Path path = applicantDocumentReadService.resolveDocumentPath(studentNumber, documentKey);
            if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
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
                    "inline; filename=\"" + path.getFileName().toString().replace("\"", "") + "\"")
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
        String result = creditGradeService.creditCourse(username, courseId, numericGrade, sourceSchool, note);
        if (result.startsWith("SUCCESS:")) {
            redir.addFlashAttribute("successMessage", result);
            recordTrail(
                username,
                "TRANSFER_CREDIT",
                "TRANSFER_CREDIT_RECORDED",
                "Transfer/TOR credit recorded",
                "Course #" + courseId + (sourceSchool != null && !sourceSchool.isBlank() ? " from " + sourceSchool.trim() : "") +
                    (numericGrade != null ? " | numeric=" + numericGrade : "") +
                    (note != null && !note.isBlank() ? " | " + note.trim() : ""),
                session,
                "grades",
                String.valueOf(courseId));
        } else {
            redir.addFlashAttribute("message", result);
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
            creditGradeService.bulkCreditFromCsv(username, bulkCreditCsv, defaultSourceSchool);
        String summary = "Bulk credit: " + result.credited() + " credited, " + result.skipped() + " skipped.";
        if (result.credited() > 0) {
            redir.addFlashAttribute("successMessage", summary);
            recordTrail(
                username,
                "TRANSFER_CREDIT",
                "BULK_TRANSFER_CREDIT_RECORDED",
                "Bulk transfer/TOR credit recorded",
                summary + (defaultSourceSchool != null && !defaultSourceSchool.isBlank() ? " Default source: " + defaultSourceSchool.trim() : ""),
                session,
                "grades",
                username);
        } else {
            redir.addFlashAttribute("message", summary);
        }
        redir.addFlashAttribute("bulkCreditLines", result.lines());
        redir.addAttribute("username", username);
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
        String result = jaypeeService.shiftStudentProgram(
            username, targetProgramCode, targetYearLevel, targetSemester, targetCurriculumId, reason);
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

        String assignedBy = "registrar";
        Object currentUser = session.getAttribute("currentUser");
        if (currentUser instanceof Map<?, ?> user && user.get("username") != null) {
            assignedBy = user.get("username").toString();
        }
        String note = reason != null && !reason.isBlank()
            ? reason.trim()
            : "Assigned from Student Profile by " + assignedBy + ".";
        try {
            studentCurriculumService.assignCurriculum(username, curriculumId, "REGISTRAR_PROFILE", note);
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
            "Direct subject removal is retired. Submit a formal withdrawal request from Student Profile.");
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
        String username = studentId;
        List<Map<String, Object>> classes = jaypeeService.getCrossSystemAnalyzedOfferings(username, true);
        int addedCount = 0;
        java.util.Set<Integer> enrolledCourseIds = new java.util.HashSet<>();
        
        for (Map<String, Object> c : classes) {
            boolean isDisabled = c.get("is_disabled") != null && (boolean) c.get("is_disabled");
            if (!isDisabled) {
                int courseId = ((Number) c.get("course_id")).intValue();
                if (!enrolledCourseIds.contains(courseId)) {
                    int scheduleId = ((Number) c.get("schedule_id")).intValue();
                    String result = jaypeeService.addSubjectCrossSystem(username, scheduleId, true);
                    if (result.startsWith("SUCCESS")) {
                        addedCount++;
                        enrolledCourseIds.add(courseId);
                    }
                }
            }
        }
        if (addedCount == 0) redir.addAttribute("errorMsg", "Could not block enroll. Classes full, conflicting, or already enrolled.");
        else {
            redir.addAttribute("errorMsg", "SUCCESS: Block enrolled " + addedCount + " subjects.");
            recordTrail(
                username,
                "ENROLLMENT",
                "BLOCK_ENROLL_COMPLETED",
                "Registrar block enrollment completed",
                "Block enrolled " + addedCount + " subject(s).",
                session,
                "student_enlistments",
                username);
        }
        redir.addAttribute("username", username);
        return "redirect:/admin/enrollment";
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
            "Direct subject removal is retired. Open Student Profile and submit a formal withdrawal request.");
        redir.addAttribute("username", studentId);
        return "redirect:/admin/enrollment";
    }

    @GetMapping("/admin/print-cor")
    public String printCor(@RequestParam String username, Model model, HttpSession session) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        Map<String, Object> student = academicService.findStudentByIdOrName(username);
        if (student != null) {
            model.addAttribute("student", student);
            List<Map<String, Object>> crossLoad = jaypeeService.getStudentLoad((String) student.get("username"));
            model.addAttribute("studentLoad", crossLoad);
            int total = 0; for(Map<String,Object> cls : crossLoad) { if(cls.get("units") != null) total += ((Number)cls.get("units")).intValue(); }
            model.addAttribute("totalUnits", total);
            model.addAttribute("finance", financeService.calculateAssessment((String) student.get("username")));
            model.addAttribute("ledger", financeService.getStudentLedger((String) student.get("username")));
            model.addAttribute("corTermLabel", academicService.getCurrentTermLabel());
            documentTrailService.recordStudentEvent(
                String.valueOf(student.get("username")),
                "STUDENT",
                "REGISTRATION_FORM",
                "PRINTED",
                "Registration Form printed",
                "Registrar generated Registration Form print output.",
                currentUsername(session),
                null,
                "print_cor",
                String.valueOf(student.get("username")));
            return "print_cor";
        }
        return "redirect:/admin/student-manager";
    }

    @GetMapping("/admin/print-cog")
    public String printCog(@RequestParam String username, Model model, HttpSession session) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        Map<String, Object> student = academicService.findStudentByIdOrName(username);
        if (student != null) {
            model.addAttribute("student", student);
            model.addAttribute("academicHistory", academicService.getStudentAcademicHistory(((Number) student.get("user_id")).intValue()));
            documentTrailService.recordStudentEvent(
                String.valueOf(student.get("username")),
                "STUDENT",
                "COG",
                "PRINTED",
                "Certificate of Grades printed",
                "Registrar generated COG print output.",
                currentUsername(session),
                null,
                "print_cog",
                String.valueOf(student.get("username")));
            return "print_cog";
        }
        return "redirect:/admin/student-manager";
    }

    @GetMapping("/admin/print-tor")
    public String printTor(@RequestParam String username, Model model, HttpSession session) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        Map<String, Object> student = academicService.findStudentByIdOrName(username);
        if (student != null) {
            model.addAttribute("student", student);
            model.addAttribute("academicHistory", academicService.getStudentAcademicHistory(((Number) student.get("user_id")).intValue()));
            documentTrailService.recordStudentEvent(
                String.valueOf(student.get("username")),
                "STUDENT",
                "TOR",
                "PRINTED",
                "Transcript of Records printed",
                "Registrar generated TOR print output.",
                currentUsername(session),
                null,
                "print_tor",
                String.valueOf(student.get("username")));
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

    private String csvCell(Object value) {
        String text = value == null ? "" : value.toString();
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
