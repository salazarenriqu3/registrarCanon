package com.iuims.registrar.controller;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Grade;
import com.iuims.registrar.entity.Program;
import com.iuims.registrar.entity.Student;
import com.iuims.registrar.service.academic.AcademicGradingService;
import com.iuims.registrar.service.academic.BlockOfferingService;
import com.iuims.registrar.dto.ClassInfoDto;
import com.iuims.registrar.service.academic.DemoGradeWorkspaceService;
import com.iuims.registrar.service.academic.RoomMonitoringService;
import com.iuims.registrar.service.academic.SlotMonitoringService;
import com.iuims.registrar.support.GradeOutcomeSql;
import com.iuims.registrar.service.admission.ApplicantStatusSyncService;
import com.iuims.registrar.service.admission.FinanceAdmissionService;
import com.iuims.registrar.service.curriculum.CurriculumSeederService;
import com.iuims.registrar.service.curriculum.StudentCurriculumService;
import com.iuims.registrar.service.support.EnlistmentSchemaService;
import com.iuims.registrar.service.faculty.FacultyLoadService;
import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.service.finance.TermFeeAdminService;
import com.iuims.registrar.service.support.DatabaseSetupService;
import com.iuims.registrar.service.support.RegistrarAuditTrailService;
import com.iuims.registrar.service.integration.JaypeeIntegrationService;
import com.iuims.registrar.support.PolicySettings;
import com.iuims.registrar.support.SqlGenerator;

import com.iuims.registrar.service.academic.AcademicGradingService;
import com.iuims.registrar.dto.ClassInfoDto;
import com.iuims.registrar.service.finance.TermFeeAdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
public class AcademicController {

    private final AcademicGradingService academicService;
    private final TermFeeAdminService termFeeAdminService;
    private final BlockOfferingService blockOfferingService;
    private final StudentCurriculumService studentCurriculumService;
    private final SlotMonitoringService slotMonitoringService;
    private final RoomMonitoringService roomMonitoringService;
    private final FacultyLoadService facultyLoadService;
    private final DemoGradeWorkspaceService demoGradeWorkspaceService;

    private final RegistrarAuditTrailService auditTrailService;

    public AcademicController(AcademicGradingService academicService, TermFeeAdminService termFeeAdminService,
                              BlockOfferingService blockOfferingService,
                              StudentCurriculumService studentCurriculumService,
                              SlotMonitoringService slotMonitoringService,
                              RoomMonitoringService roomMonitoringService,
                              FacultyLoadService facultyLoadService,
                              DemoGradeWorkspaceService demoGradeWorkspaceService,
                              RegistrarAuditTrailService auditTrailService) {
        this.academicService = academicService;
        this.termFeeAdminService = termFeeAdminService;
        this.blockOfferingService = blockOfferingService;
        this.studentCurriculumService = studentCurriculumService;
        this.slotMonitoringService = slotMonitoringService;
        this.roomMonitoringService = roomMonitoringService;
        this.facultyLoadService = facultyLoadService;
        this.demoGradeWorkspaceService = demoGradeWorkspaceService;
        this.auditTrailService = auditTrailService;
    }


    @GetMapping("/grades")
    public String facultyMenu(HttpSession s, Model m) {
        Map<String,Object> u = (Map<String,Object>) s.getAttribute("currentUser");
        if(u == null) return "redirect:/login";
        m.addAttribute("classes", academicService.getFacultyClassesForUser(u));
        m.addAttribute("user", u);
        m.addAttribute("canOpenDeanAdvising", false);
        m.addAttribute("deanAdvisingDormant", true);
        m.addAttribute("deanAdvisingNotice", "Dean irregular advising is currently dormant and outside the registrar scope.");
        return "grades_menu";
    }
    
    @GetMapping("/grades/view/{id}")
    public String viewGradeSheet(@PathVariable int id, HttpSession s, Model m) {
        Map<String,Object> u = (Map<String,Object>) s.getAttribute("currentUser");
        if(u == null) return "redirect:/login";
        
        Map<String, Object> classInfo = academicService.getClassInfo(id);
        List<Map<String, Object>> grades = academicService.getClassGrades(id);
        
        m.addAttribute("isLocked", false); 
        m.addAttribute("hasPendingExtension", academicService.isExtensionPending(id));
        m.addAttribute("hasApprovedChange", "SUBMITTED".equals(classInfo.get("status")) && grades.stream().anyMatch(g -> "DRAFT".equals(g.get("status")))); 
        m.addAttribute("hasExtension", academicService.isExtensionApproved(id)); 
        
        m.addAttribute("classInfo", classInfo);
        m.addAttribute("grades", grades);
        m.addAttribute("windows", academicService.getGradingWindows(extractClassTermId(classInfo)));
        m.addAttribute("user", u);
        boolean isFaculty = isFacultyUser(u);
        m.addAttribute("isFaculty", isFaculty);
        m.addAttribute("readonly", !isFaculty);
        return "grades_sheet";
    }
    @PostMapping("/api/faculty/auto-save")
    @ResponseBody
    public Map<String, Object> autoSaveGrade(@RequestParam int gradeId,
                                             @RequestParam String prelim,
                                             @RequestParam String midterm,
                                             @RequestParam String finals,
                                             @RequestParam(defaultValue="LEC") String gradeType,
                                             HttpSession session) {
        return academicService.saveGradeAsync(gradeId, prelim, midterm, finals, currentUsername(session), currentRole(session));
    }

    @GetMapping("/api/mcp/classes/{id}")
    @ResponseBody
    public ClassInfoDto getMcpClassInfo(@PathVariable int id) {
        return academicService.getClassInfoDto(id);
    }

    @PostMapping("/faculty/submit-class")
    public String submitClass(@RequestParam int scheduleId, HttpSession session) {
        academicService.submitClassGrades(scheduleId, currentUsername(session), currentRole(session));
        return "redirect:/grades/view/" + scheduleId;
    }

    @PostMapping("/faculty/unsubmit-class")
    public String unsubmitClass(@RequestParam int scheduleId, HttpSession session) {
        academicService.unsubmitClassGrades(scheduleId, currentUsername(session), currentRole(session));
        return "redirect:/grades/view/" + scheduleId;
    }

    private int sessionUserId(HttpSession session) {
        Object raw = session.getAttribute("currentUser");
        if (!(raw instanceof Map<?, ?> user)) {
            return 0;
        }
        Object id = user.get("user_id");
        return id instanceof Number ? ((Number) id).intValue() : 0;
    }

    @PostMapping("/faculty/request-change")
    public String requestChange(@RequestParam int gradeId,
                                @RequestParam(defaultValue = "FINAL_GRADE_CORRECTION") String requestType,
                                @RequestParam(required = false) String newGrade,
                                @RequestParam(required = false) String requestedPrelim,
                                @RequestParam(required = false) String requestedMidterm,
                                @RequestParam(required = false) String requestedFinals,
                                @RequestParam String reason,
                                @RequestParam int scheduleId,
                                HttpSession session) {
        academicService.requestGradeChange(
            gradeId,
            requestType,
            newGrade,
            requestedPrelim,
            requestedMidterm,
            requestedFinals,
            reason,
            sessionUserId(session));
        return "redirect:/grades/view/" + scheduleId;
    }

    @PostMapping("/faculty/request-extension")
    public String requestExtension(@RequestParam int scheduleId, @RequestParam String reason, HttpSession session) {
        academicService.requestVpaaExtension(scheduleId, sessionUserId(session), reason);
        return "redirect:/grades/view/" + scheduleId;
    }

    @PostMapping("/admin/approve-extension")
    public String approveExtension(@RequestParam int extId) { academicService.approveExtension(extId); return "redirect:/admin/approvals"; }

    @GetMapping("/admin/settings")
    public String adminSettings(@RequestParam(required = false) Integer gradingTermId, HttpSession s, Model m) {
        if(s.getAttribute("currentUser") == null) return "redirect:/login";
        Integer activeTermId = termFeeAdminService.getActiveTermId();
        Integer selectedGradingTermId = gradingTermId != null && gradingTermId > 0 ? gradingTermId : activeTermId;
        m.addAttribute("settings", academicService.getGradingWindows(selectedGradingTermId));
        m.addAttribute("termOptions", academicService.getAcademicTermOptionsForSettings());
        m.addAttribute("selectedGradingTermId", selectedGradingTermId);
        m.addAttribute("termReadiness", termFeeAdminService.buildTermReadinessSummary(activeTermId));
        return "admin_settings";
    }

    @GetMapping("/admin/settings/readiness")
    @ResponseBody
    public Map<String, Object> termReadiness(@RequestParam(required = false) String termCode, HttpSession s) {
        if (s.getAttribute("currentUser") == null) {
            return Map.of("error", "LOGIN_REQUIRED");
        }
        Integer termId = termFeeAdminService.resolveTermIdFromTermCode(termCode);
        Map<String, Object> summary = termFeeAdminService.buildTermReadinessSummary(termId);
        summary.put("requestedTermCode", termCode);
        return summary;
    }

    @PostMapping("/admin/save-settings")
    public String saveSettings(@RequestParam Map<String, String> params) {
        academicService.updateSettings(params);
        String termId = params.get("gradingTermId");
        String suffix = termId != null && !termId.isBlank() ? "&gradingTermId=" + termId : "";
        return "redirect:/admin/settings?success=true" + suffix;
    }

    @PostMapping("/admin/expire-inc")
    public String expireIncGrades(@RequestParam Map<String, String> params, HttpSession s, RedirectAttributes ra) {
        if(s.getAttribute("currentUser") == null) return "redirect:/login";
        Integer termId = parsePositiveInt(params.get("gradingTermId"));
        int expired = academicService.expireOverdueIncGrades(termId);
        ra.addFlashAttribute("incExpireMsg", "Expired " + expired + " due INC grade(s) for the selected term.");
        String suffix = termId != null ? "&gradingTermId=" + termId : "";
        return "redirect:/admin/settings?success=true" + suffix;
    }

    @PostMapping("/admin/update-global-term")
    public String updateGlobalTerm(@RequestParam String newTermCode, HttpSession s, RedirectAttributes ra) {
        if(s.getAttribute("currentUser") == null) return "redirect:/login";
        AcademicGradingService.TermTransitionResult result = academicService.triggerTermTransition(newTermCode);
        if (!result.success()) {
            ra.addFlashAttribute("termErrorMsg", result.errorMessage());
            return "redirect:/admin/settings";
        }
        if (result.advanced() > 0) {
            ra.addFlashAttribute("termAdvancedMsg",
                result.advanced() + " student(s) advanced. Unpaid balances were forwarded to the new term.");
        }
        if (result.withForwardedDebt() > 0) {
            ra.addFlashAttribute("termForwardedMsg",
                result.withForwardedDebt()
                    + " student(s) have forwarded balance at or above the configured accounting threshold. Enlistment is blocked until prior-term debt is reduced at Cashier.");
        }
        return "redirect:/admin/settings?termSuccess=true";
    }

    @PostMapping("/admin/terms/add")
    public String addAcademicTerm(@RequestParam String academicYear,
                                 @RequestParam int semesterNumber,
                                 @RequestParam(required=false) String startDate,
                                 @RequestParam(required=false) String endDate,
                                 HttpSession s) {
        if(s.getAttribute("currentUser") == null) return "redirect:/login";
        String r = academicService.addAcademicTerm(academicYear, semesterNumber, startDate, endDate);
        return "redirect:/admin/settings?termAddMsg=" + java.net.URLEncoder.encode(r, java.nio.charset.StandardCharsets.UTF_8);
    }

    private Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("/admin/toggle-unlock")
    public String toggleUnlock(@RequestParam int scheduleId,
                               @RequestParam int unlockStatus,
                               @RequestParam(required = false) String returnSearch,
                               @RequestParam(required = false, defaultValue = "ALL") String returnStatus,
                               @RequestParam(required = false, defaultValue = "1") Integer returnPage,
                               @RequestParam(required = false, defaultValue = "50") Integer returnSize) {
        academicService.toggleClassUnlock(scheduleId, unlockStatus);
        return redirectToClasses(returnSearch, returnStatus, returnPage, returnSize);
    }

    @GetMapping("/admin/approvals")
    public String adminApprovals(HttpSession s, Model m) {
        if(s.getAttribute("currentUser") == null) return "redirect:/login";
        m.addAttribute("pendingClasses", academicService.getPendingClassSubmissions());
        m.addAttribute("requests", academicService.getGradeChangeRequests());
        m.addAttribute("extensions", academicService.getPendingExtensions());
        m.addAttribute("user", s.getAttribute("currentUser"));
        return "admin_approvals";
    }

    @PostMapping("/admin/approve-class")
    public String approveClass(@RequestParam int scheduleId, HttpSession session) {
        academicService.finalizeClassGrades(scheduleId, currentUsername(session), currentRole(session));
        return "redirect:/admin/approvals";
    }

    @PostMapping("/admin/approve-change")
    public String approveChange(@RequestParam int requestId, HttpSession session) {
        academicService.approveGradeChange(requestId, currentUsername(session));
        return "redirect:/admin/approvals";
    }

    @PostMapping("/admin/reject-change")
    public String rejectChange(@RequestParam int requestId,
                               @RequestParam String reviewNote,
                               HttpSession session) {
        academicService.rejectGradeChange(requestId, currentUsername(session), reviewNote);
        return "redirect:/admin/approvals";
    }

    @GetMapping("/admin/grade-records")
    public String adminGradeRecords(@RequestParam(required = false) Integer termId,
                                    @RequestParam(required = false) String query,
                                    @RequestParam(required = false) String lifecycleStatus,
                                    @RequestParam(required = false) String actionType,
                                    HttpSession s,
                                    Model m) {
        if (s.getAttribute("currentUser") == null) return "redirect:/login";
        Integer selectedTermId = termId != null && termId > 0 ? termId : null;
        m.addAttribute("selectedGradeTermId", selectedTermId);
        m.addAttribute("gradeQuery", query != null ? query : "");
        m.addAttribute("selectedLifecycleStatus", lifecycleStatus != null ? lifecycleStatus : "ALL");
        m.addAttribute("selectedActionType", actionType != null ? actionType : "ALL");
        m.addAttribute("terms", academicService.getAllTerms());
        m.addAttribute("summary", academicService.getGradeGovernanceSummary(selectedTermId));
        m.addAttribute("gradeRows", academicService.getGradeRegistryRows(selectedTermId, query, lifecycleStatus, 200));
        m.addAttribute("gradeEvents", academicService.getGradeRecordEvents(selectedTermId, query, actionType, lifecycleStatus, 200));
        m.addAttribute("user", s.getAttribute("currentUser"));
        return "admin_grade_records";
    }

    @GetMapping("/admin/demo-grades")
    public String adminDemoGrades(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) Integer termId,
                                  HttpSession s,
                                  Model m) {
        if (s.getAttribute("currentUser") == null) return "redirect:/login";
        Integer selectedTermId = termId != null && termId > 0 ? termId : academicService.getActiveTermId();
        String searchQuery = q != null ? q.trim() : "";
        Map<String, Object> student = null;
        boolean searchAttempted = !searchQuery.isBlank();
        String searchError = null;
        if (searchAttempted) {
            student = academicService.findStudentByIdOrName(searchQuery);
            if (student == null) {
                searchError = "Student not found.";
            }
        }
        String studentNumber = student != null && student.get("student_number") != null
            ? student.get("student_number").toString()
            : null;
        List<Map<String, Object>> demoRows = studentNumber != null
            ? demoGradeWorkspaceService.getRows(studentNumber, selectedTermId)
            : List.of();
        m.addAttribute("terms", academicService.getAllTerms());
        m.addAttribute("selectedTermId", selectedTermId);
        m.addAttribute("searchQuery", searchQuery);
        m.addAttribute("searchAttempted", searchAttempted);
        m.addAttribute("searchError", searchError);
        m.addAttribute("student", student);
        m.addAttribute("demoRows", demoRows);
        m.addAttribute("user", s.getAttribute("currentUser"));
        m.addAttribute("canSaveDemoGrades", hasRole(s, "DEAN") || hasRole(s, "REGISTRAR"));
        m.addAttribute("canSubmitDemoGrades", hasRole(s, "DEAN"));
        m.addAttribute("canReviewDemoGrades", hasRole(s, "REGISTRAR"));
        return "admin_demo_grades";
    }

    @PostMapping("/admin/demo-grades/save")
    public String saveDemoGrade(@RequestParam String studentNumber,
                                @RequestParam int sectionId,
                                @RequestParam(required = false) String prelim,
                                @RequestParam(required = false) String midterm,
                                @RequestParam(required = false) String finals,
                                @RequestParam(required = false) String quickFinal,
                                @RequestParam(required = false) Integer termId,
                                @RequestParam(required = false) String q,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        String result = demoGradeWorkspaceService.saveDraft(
            studentNumber,
            sectionId,
            prelim,
            midterm,
            finals,
            quickFinal,
            currentUsername(session),
            currentRole(session));
        flashDemoResult(redirectAttributes, result, studentNumber, q, termId);
        return redirectToDemoGrades(studentNumber, q, termId);
    }

    @PostMapping("/admin/demo-grades/submit")
    public String submitDemoGrade(@RequestParam String studentNumber,
                                  @RequestParam int sectionId,
                                  @RequestParam(required = false) Integer termId,
                                  @RequestParam(required = false) String q,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        String result = ensureDemoDean(session)
            ? demoGradeWorkspaceService.submit(studentNumber, sectionId, currentUsername(session), currentRole(session))
            : "ERROR: Only a dean can submit demo grades.";
        flashDemoResult(redirectAttributes, result, studentNumber, q, termId);
        return redirectToDemoGrades(studentNumber, q, termId);
    }

    @PostMapping("/admin/demo-grades/approve")
    public String approveDemoGrade(@RequestParam String studentNumber,
                                   @RequestParam int sectionId,
                                   @RequestParam(required = false) Integer termId,
                                   @RequestParam(required = false) String q,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        String result = ensureDemoRegistrar(session)
            ? demoGradeWorkspaceService.approve(studentNumber, sectionId, currentUsername(session), currentRole(session))
            : "ERROR: Only a registrar can approve demo grades.";
        flashDemoResult(redirectAttributes, result, studentNumber, q, termId);
        return redirectToDemoGrades(studentNumber, q, termId);
    }

    @PostMapping("/admin/demo-grades/reject")
    public String rejectDemoGrade(@RequestParam String studentNumber,
                                  @RequestParam int sectionId,
                                  @RequestParam(required = false) String reviewNote,
                                  @RequestParam(required = false) Integer termId,
                                  @RequestParam(required = false) String q,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        String result = ensureDemoRegistrar(session)
            ? demoGradeWorkspaceService.reject(studentNumber, sectionId, currentUsername(session), currentRole(session), reviewNote)
            : "ERROR: Only a registrar can reject demo grades.";
        flashDemoResult(redirectAttributes, result, studentNumber, q, termId);
        return redirectToDemoGrades(studentNumber, q, termId);
    }

    @GetMapping("/admin/users")
    public String adminUsers(Model m, HttpSession s) {
        if(s.getAttribute("currentUser") == null) return "redirect:/login";
        m.addAttribute("users", academicService.getAllUsers());
        return "admin_users";
    }

    @PostMapping("/create-user")
    public String createUser(@RequestParam String username,
                             @RequestParam String realName,
                             @RequestParam String role,
                             @RequestParam(required=false) String program,
                             @RequestParam(required=false) List<String> permissions,
                             RedirectAttributes ra) {
        try {
            academicService.createUser(username, realName, role, program, permissions);
            ra.addFlashAttribute("successMsg", "User created with the configured temporary password.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/update-user")
    public String updateUser(@RequestParam int userId, @RequestParam String role, @RequestParam(required=false) List<String> permissions) { academicService.updateUserPermissions(userId, role, permissions); return "redirect:/admin/users"; }

    @PostMapping("/admin/delete-user")
    public String deleteUser(@RequestParam int userId) { academicService.deleteUser(userId); return "redirect:/admin/users"; }

    @PostMapping("/admin/toggle-status")
    public String toggleStatus(@RequestParam int userId, @RequestParam boolean isActive) { academicService.toggleUserStatus(userId, isActive); return "redirect:/admin/users"; }

    @PostMapping("/admin/reset-password")
    public String resetPassword(@RequestParam int userId, RedirectAttributes ra) {
        try {
            academicService.resetPassword(userId);
            ra.addFlashAttribute("successMsg", "Password reset to the configured temporary password.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/admin/classes")
    public String adminClasses(HttpSession s,
                               Model m,
                               @RequestParam(required = false) String search,
                               @RequestParam(defaultValue = "ALL") String status,
                               @RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "50") int size) {
        if(s.getAttribute("currentUser") == null) return "redirect:/login";
        AcademicGradingService.ClassPage classPage = academicService.getAllClassesAdminPage(search, status, page, size);
        m.addAttribute("classes", classPage.rows());
        m.addAttribute("search", search);
        m.addAttribute("selectedClassStatus", status);
        m.addAttribute("page", classPage.page());
        m.addAttribute("pageSize", classPage.pageSize());
        m.addAttribute("totalRows", classPage.totalRows());
        m.addAttribute("totalPages", classPage.totalPages());
        m.addAttribute("pageStart", classPage.totalRows() == 0 ? 0 : ((classPage.page() - 1) * classPage.pageSize()) + 1);
        m.addAttribute("pageEnd", Math.min(classPage.page() * classPage.pageSize(), classPage.totalRows()));
        return "admin_classes";
    }

    @GetMapping("/admin/classes/view/{id}")
    public String adminViewClass(@PathVariable int id, HttpSession s, Model m) {
        if(s.getAttribute("currentUser") == null) return "redirect:/login";
        Map<String, Object> classInfo = academicService.getClassInfo(id);
        m.addAttribute("classInfo", classInfo);
        m.addAttribute("grades", academicService.getClassGrades(id));
        m.addAttribute("windows", academicService.getGradingWindows(extractClassTermId(classInfo)));
        m.addAttribute("user", s.getAttribute("currentUser")); 
        m.addAttribute("hasExtension", false);
        m.addAttribute("hasApprovedChange", false);
        m.addAttribute("isLocked", false);
        m.addAttribute("hasPendingExtension", false);
        m.addAttribute("isFaculty", false);
        m.addAttribute("readonly", true);
        return "grades_sheet";
    }

    private boolean isFacultyUser(Map<String, Object> user) {
        Object role = user != null ? user.get("role") : null;
        return role != null && "Faculty".equalsIgnoreCase(role.toString().trim());
    }

    private Integer extractClassTermId(Map<String, Object> classInfo) {
        try {
            Object value = classInfo != null ? classInfo.get("term_id") : null;
            return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String currentUsername(HttpSession session) {
        Object raw = session.getAttribute("currentUser");
        if (raw instanceof Map<?, ?> user && user.get("username") != null) {
            return user.get("username").toString();
        }
        return "registrar";
    }

    private String currentRole(HttpSession session) {
        Object raw = session.getAttribute("currentUser");
        if (raw instanceof Map<?, ?> user && user.get("role") != null) {
            return user.get("role").toString();
        }
        return "Registrar";
    }

    private boolean hasRole(HttpSession session, String role) {
        Object raw = session.getAttribute("currentUser");
        if (!(raw instanceof Map<?, ?> user) || user.get("role") == null) {
            return false;
        }
        return role.equalsIgnoreCase(user.get("role").toString().trim());
    }

    private boolean ensureDemoDean(HttpSession session) {
        return hasRole(session, "Dean");
    }

    private boolean ensureDemoRegistrar(HttpSession session) {
        return hasRole(session, "Registrar");
    }

    private void flashDemoResult(RedirectAttributes redirectAttributes,
                                 String result,
                                 String studentNumber,
                                 String q,
                                 Integer termId) {
        String message = result != null && result.startsWith("SUCCESS:")
            ? result.substring("SUCCESS:".length()).trim()
            : result;
        if (result != null && result.startsWith("SUCCESS:")) {
            redirectAttributes.addFlashAttribute("successMessage", message);
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", message != null ? message : "Unable to save demo grades.");
        }
        if (studentNumber != null && !studentNumber.isBlank()) {
            redirectAttributes.addFlashAttribute("username", studentNumber);
        }
        if (q != null && !q.isBlank()) {
            redirectAttributes.addFlashAttribute("demoQuery", q.trim());
        }
        if (termId != null) {
            redirectAttributes.addFlashAttribute("demoTermId", termId);
        }
    }

    private String redirectToDemoGrades(String studentNumber, String q, Integer termId) {
        StringBuilder target = new StringBuilder("redirect:/admin/demo-grades");
        String search = q != null && !q.isBlank() ? q.trim() : (studentNumber != null ? studentNumber.trim() : "");
        boolean hasQuery = false;
        if (!search.isBlank()) {
            target.append("?q=").append(java.net.URLEncoder.encode(search, java.nio.charset.StandardCharsets.UTF_8));
            hasQuery = true;
        }
        if (termId != null && termId > 0) {
            target.append(hasQuery ? "&" : "?").append("termId=").append(termId);
        }
        return target.toString();
    }

    @PostMapping("/admin/revert-class")
    public String revertClass(@RequestParam int scheduleId,
                              @RequestParam(required = false) String returnSearch,
                              @RequestParam(required = false, defaultValue = "ALL") String returnStatus,
                              @RequestParam(required = false, defaultValue = "1") Integer returnPage,
                              @RequestParam(required = false, defaultValue = "50") Integer returnSize) {
        academicService.revertClassToDraft(scheduleId);
        return redirectToClasses(returnSearch, returnStatus, returnPage, returnSize);
    }

    // ---- Class Scheduling Management ----

    @GetMapping("/admin/class-scheduling")
    public String classScheduling(
            @RequestParam(defaultValue = "0") int termId,
            @RequestParam(required = false) String dept,
            @RequestParam(defaultValue = "overview") String view,
            @RequestParam(required = false) Integer openBlockId,
            @RequestParam(required = false) String blockSearch,
            @RequestParam(required = false) String blockProgram,
            @RequestParam(required = false) String blockMode,
            @RequestParam(required = false) Integer blockPage,
            @RequestParam(required = false) Integer blockSize,
            @RequestParam(required = false) String courseSearch,
            @RequestParam(required = false) String anchor,
            @RequestParam(required = false) String msg,
            Model model, HttpSession s) {
        if (s.getAttribute("currentUser") == null) return "redirect:/login";
        if (termId == 0) termId = academicService.getActiveTermId();
        blockOfferingService.ensureSchema();
        boolean loadDiagnostics = "diagnostics".equalsIgnoreCase(view) || "all".equalsIgnoreCase(view);
        String normalizedBlockMode = blockMode != null ? blockMode.trim().toLowerCase(java.util.Locale.ROOT) : "";
        Integer selectedBlockId = openBlockId != null && openBlockId > 0 ? openBlockId : null;
        boolean hasBlockScope = "all".equals(normalizedBlockMode)
            || (blockSearch != null && !blockSearch.isBlank())
            || (blockProgram != null && !blockProgram.isBlank());
        boolean loadBlockHeaders = selectedBlockId != null || hasBlockScope;
        List<Map<String, Object>> blocks;
        List<BlockSchoolGroup> blockSchoolGroups;
        if (hasBlockScope) {
            blocks = blockOfferingService.listBlockHeadersForTerm(termId, blockSearch, blockProgram);
            blockSchoolGroups = groupBlocksBySchoolAndProgram(blocks);
        } else if (selectedBlockId != null) {
            blocks = blockOfferingService.listBlockHeadersForTermById(termId, selectedBlockId);
            blockSchoolGroups = groupBlocksBySchoolAndProgram(blocks);
        } else {
            blocks = java.util.List.of();
            blockSchoolGroups = groupBlockProgramSummaries(
                blockOfferingService.listBlockProgramSummariesForTerm(termId));
        }
        var blockCourses = new java.util.LinkedHashMap<Integer, List<Map<String, Object>>>();
        boolean loadBlockDetails = selectedBlockId != null
            && ("blocks".equalsIgnoreCase(view) || "all".equalsIgnoreCase(view));
        boolean loadCourseSections = "courses".equalsIgnoreCase(view) || "all".equalsIgnoreCase(view);
        if (loadBlockDetails || loadCourseSections || loadDiagnostics) {
            blockOfferingService.syncLegacyBlockLinks(termId);
        }
        if (loadBlockDetails) {
            blockCourses.putAll(blockOfferingService.listBlockCoursesForBlocks(java.util.List.of(selectedBlockId)));
        }
        model.addAttribute("termId",   termId);
        model.addAttribute("deptFilter", dept);
        model.addAttribute("selectedSchedulingView", view);
        model.addAttribute("openBlockId", selectedBlockId);
        model.addAttribute("blockSearchValue", blockSearch);
        model.addAttribute("blockProgramValue", blockProgram);
        model.addAttribute("blockBrowseMode", loadBlockHeaders ? normalizedBlockMode : "summary");
        model.addAttribute("blockHeadersLoaded", loadBlockHeaders);
        model.addAttribute("courseSearchValue", courseSearch);
        model.addAttribute("scheduleAnchor", anchor);
        model.addAttribute("loadBlockDetails", loadBlockDetails);
        model.addAttribute("loadCourseSections", loadCourseSections);
        model.addAttribute("loadDiagnostics", loadDiagnostics);
        model.addAttribute("terms",    academicService.getAllTerms());
        model.addAttribute("blocks",   blocks);
        model.addAttribute("blockSchoolGroups", blockSchoolGroups);
        model.addAttribute("blockCourses", blockCourses);
        model.addAttribute("programs", blockOfferingService.listPrograms());
        model.addAttribute("curricula", studentCurriculumService.listAssignableCurricula());
        model.addAttribute("courses",  loadCourseSections ? academicService.getCoursesWithSections(termId) : java.util.List.of());
        model.addAttribute("faculty",  (loadBlockDetails || loadCourseSections) ? academicService.getAllFacultyForScheduling() : java.util.List.of());
        model.addAttribute("rooms",    (loadBlockDetails || loadCourseSections) ? academicService.getAllRoomsForScheduling() : java.util.List.of());
        if (loadDiagnostics) {
            var conflictPreview = academicService.getScheduleConflictPreview(termId, 50);
            model.addAttribute("scheduleConflicts", conflictPreview.conflicts());
            model.addAttribute("scheduleConflictsTruncated", conflictPreview.truncated());
            model.addAttribute("assignmentAudit", facultyLoadService.getTermAssignmentAudit(termId));
        }
        if (msg != null) model.addAttribute("msg", msg);
        return "admin_class_scheduling";
    }

    @PostMapping("/admin/class-scheduling/create-block")
    public String createBlock(@RequestParam int termId,
                              @RequestParam String programCode,
                              @RequestParam int yearLevel,
                              @RequestParam int semesterNumber,
                              @RequestParam(defaultValue = "A") String sectionGroup,
                              @RequestParam(defaultValue = "40") int maxCapacity,
                              @RequestParam(defaultValue = "0") int facultyId,
                              @RequestParam(defaultValue = "0") int curriculumId,
                              @RequestParam(required = false, defaultValue = "blocks") String view,
                              @RequestParam(required = false) Integer openBlockId,
                              @RequestParam(required = false) String blockSearch,
                              @RequestParam(required = false) String blockProgram,
                              @RequestParam(defaultValue = "1") int blockPage,
                              @RequestParam(required = false) String courseSearch,
                              @RequestParam(required = false) String anchor,
                              HttpSession session) {
        String r = blockOfferingService.createAndMaterializeBlock(
            termId, programCode, yearLevel, semesterNumber, sectionGroup, maxCapacity,
            facultyId == 0 ? null : facultyId, curriculumId == 0 ? null : curriculumId);
        recordAcademicAudit(session, "SCHEDULING", "BLOCK_CREATED",
            "BLOCK", programCode + "-" + yearLevel + "-" + semesterNumber + "-" + sectionGroup,
            "Block section materialized", r, "block_sections", String.valueOf(termId));
        return redirectToClassScheduling(termId, view, openBlockId, blockSearch, blockProgram, blockPage, courseSearch, anchor, r);
    }

    @PostMapping("/admin/class-scheduling/rematerialize-block")
    public String rematerializeBlock(@RequestParam int blockId,
                                     @RequestParam int termId,
                                     @RequestParam(required = false, defaultValue = "blocks") String view,
                                     @RequestParam(required = false) Integer openBlockId,
                                     @RequestParam(required = false) String blockSearch,
                                     @RequestParam(required = false) String blockProgram,
                                     @RequestParam(defaultValue = "1") int blockPage,
                                     @RequestParam(required = false) String courseSearch,
                                     @RequestParam(required = false) String anchor,
                                     HttpSession session) {
        String r = blockOfferingService.rematerializeBlock(blockId);
        recordAcademicAudit(session, "SCHEDULING", "BLOCK_REMATERIALIZED",
            "BLOCK", String.valueOf(blockId),
            "Block section rematerialized", r, "block_sections", String.valueOf(blockId));
        return redirectToClassScheduling(termId, view, openBlockId, blockSearch, blockProgram, blockPage, courseSearch, anchor, r);
    }

    @PostMapping("/admin/class-scheduling/set-irregular-block-access")
    public String setIrregularBlockAccess(@RequestParam int blockId,
                                          @RequestParam int termId,
                                          @RequestParam String accessMode,
                                          @RequestParam(required = false, defaultValue = "blocks") String view,
                                          @RequestParam(required = false) Integer openBlockId,
                                          @RequestParam(required = false) String blockSearch,
                                          @RequestParam(required = false) String blockProgram,
                                          @RequestParam(defaultValue = "1") int blockPage,
                                          @RequestParam(required = false) String courseSearch,
                                          @RequestParam(required = false) String anchor,
                                          HttpSession session) {
        String result;
        try {
            blockOfferingService.setBlockIrregularAccessOverride(blockId, accessMode);
            String normalized = accessMode != null ? accessMode.trim().toUpperCase(java.util.Locale.ROOT) : "AUTO";
            result = switch (normalized) {
                case "OPEN" -> "SUCCESS: Block irregular access opened.";
                case "CLOSED" -> "SUCCESS: Block irregular access closed.";
                default -> "SUCCESS: Block irregular access reset to inherited policy.";
            };
            recordAcademicAudit(session, "SCHEDULING", "IRREGULAR_BLOCK_ACCESS_UPDATED",
                "BLOCK", String.valueOf(blockId),
                "Irregular block access updated", normalized, "block_offerings", String.valueOf(blockId));
        } catch (Exception e) {
            result = "ERROR: " + e.getMessage();
        }
        return redirectToClassScheduling(termId, view, openBlockId, blockSearch, blockProgram, blockPage, courseSearch, anchor, result);
    }

    @PostMapping("/admin/class-scheduling/set-irregular-class-access")
    public String setIrregularClassAccess(@RequestParam int sectionId,
                                          @RequestParam int termId,
                                          @RequestParam String accessMode,
                                          @RequestParam(required = false, defaultValue = "blocks") String view,
                                          @RequestParam(required = false) Integer openBlockId,
                                          @RequestParam(required = false) String blockSearch,
                                          @RequestParam(required = false) String blockProgram,
                                          @RequestParam(defaultValue = "1") int blockPage,
                                          @RequestParam(required = false) String courseSearch,
                                          @RequestParam(required = false) String anchor,
                                          HttpSession session) {
        String result;
        try {
            blockOfferingService.setClassIrregularAccessOverride(sectionId, accessMode);
            String normalized = accessMode != null ? accessMode.trim().toUpperCase(java.util.Locale.ROOT) : "AUTO";
            result = switch (normalized) {
                case "OPEN" -> "SUCCESS: Class irregular access opened.";
                case "CLOSED" -> "SUCCESS: Class irregular access closed.";
                default -> "SUCCESS: Class irregular access reset to inherited policy.";
            };
            recordAcademicAudit(session, "SCHEDULING", "IRREGULAR_CLASS_ACCESS_UPDATED",
                "SECTION", String.valueOf(sectionId),
                "Irregular class access updated", normalized, "class_sections", String.valueOf(sectionId));
        } catch (Exception e) {
            result = "ERROR: " + e.getMessage();
        }
        return redirectToClassScheduling(termId, view, openBlockId, blockSearch, blockProgram, blockPage, courseSearch, anchor, result);
    }

    @PostMapping("/admin/class-scheduling/set-irregular-program-scope")
    public String setIrregularProgramScopeAccess(@RequestParam int termId,
                                                 @RequestParam String programCode,
                                                 @RequestParam int yearLevel,
                                                 @RequestParam int semesterNumber,
                                                 @RequestParam boolean open,
                                                 @RequestParam(required = false, defaultValue = "blocks") String view,
                                                 @RequestParam(required = false) Integer openBlockId,
                                                 @RequestParam(required = false) String blockSearch,
                                                 @RequestParam(required = false) String blockProgram,
                                                 @RequestParam(defaultValue = "1") int blockPage,
                                                 @RequestParam(required = false) String courseSearch,
                                                 @RequestParam(required = false) String anchor,
                                                 HttpSession session) {
        String result;
        try {
            blockOfferingService.setProgramYearSemesterIrregularAccess(
                termId, programCode, yearLevel, semesterNumber, open);
            result = open
                ? "SUCCESS: Program/year/semester irregular access opened."
                : "SUCCESS: Program/year/semester irregular access closed.";
            recordAcademicAudit(session, "SCHEDULING", "IRREGULAR_PROGRAM_SCOPE_UPDATED",
                "BLOCK_SCOPE", programCode + "-" + yearLevel + "-" + semesterNumber,
                "Irregular program/year/semester access updated",
                "termId=" + termId + " open=" + open, "irregular_block_access_policies",
                termId + ":" + programCode + ":" + yearLevel + ":" + semesterNumber);
        } catch (Exception e) {
            result = "ERROR: " + e.getMessage();
        }
        return redirectToClassScheduling(termId, view, openBlockId, blockSearch, blockProgram, blockPage, courseSearch, anchor, result);
    }

    @PostMapping("/admin/class-scheduling/open-section")
    public String openSection(@RequestParam int courseId, @RequestParam int termId,
                              @RequestParam String sectionCode, @RequestParam(defaultValue="0") int facultyId,
                              @RequestParam(defaultValue="40") int maxCapacity,
                              @RequestParam(required = false, defaultValue = "courses") String view,
                              @RequestParam(required = false) Integer openBlockId,
                              @RequestParam(required = false) String blockSearch,
                              @RequestParam(required = false) String blockProgram,
                              @RequestParam(defaultValue = "1") int blockPage,
                              @RequestParam(required = false) String courseSearch,
                              @RequestParam(required = false) String anchor,
                              HttpSession session) {
        String r = academicService.openSection(courseId, termId, sectionCode,
                        facultyId == 0 ? null : facultyId, maxCapacity);
        recordAcademicAudit(session, "SCHEDULING", "COURSE_SECTION_OPENED",
            "SECTION", sectionCode,
            "Course section opened", r, "class_sections", sectionCode);
        return redirectToClassScheduling(termId, view, openBlockId, blockSearch, blockProgram, blockPage, courseSearch, anchor, r);
    }

    @PostMapping("/admin/class-scheduling/add-schedule")
    public String addSchedule(@RequestParam int sectionId, @RequestParam int termId,
                              @RequestParam int day1, @RequestParam(defaultValue="0") int day2,
                              @RequestParam String startTime, @RequestParam String endTime,
                              @RequestParam(defaultValue="0") int roomId,
                              @RequestParam(required = false, defaultValue = "blocks") String view,
                              @RequestParam(required = false) Integer openBlockId,
                              @RequestParam(required = false) String blockSearch,
                              @RequestParam(required = false) String blockProgram,
                              @RequestParam(defaultValue = "1") int blockPage,
                              @RequestParam(required = false) String courseSearch,
                              @RequestParam(required = false) String anchor,
                              HttpSession session) {
        if (roomId == 0) {
            String r = "ERROR: Room is required before a schedule slot can be saved.";
            return redirectToClassScheduling(termId, view, openBlockId, blockSearch, blockProgram, blockPage, courseSearch, anchor, r);
        }
        String r = academicService.addScheduleSlot(sectionId, day1, startTime, endTime,
                        roomId, day2 == 0 ? null : day2);
        recordAcademicAudit(session, "SCHEDULING", "SCHEDULE_SLOT_ADDED",
            "SECTION", String.valueOf(sectionId),
            "Schedule slot added",
            "Day " + day1 + (day2 > 0 ? "/" + day2 : "") + " " + startTime + "-" + endTime
                + " room #" + roomId + " | " + r,
            "class_schedules", String.valueOf(sectionId));
        return redirectToClassScheduling(termId, view, openBlockId, blockSearch, blockProgram, blockPage, courseSearch, anchor, r);
    }

    @PostMapping("/admin/class-scheduling/repair-conflicts")
    public String repairSchedulingConflicts(@RequestParam int termId,
                                            @RequestParam(required = false, defaultValue = "diagnostics") String view,
                                            @RequestParam(required = false) Integer openBlockId,
                                            @RequestParam(required = false) String blockSearch,
                                            @RequestParam(required = false) String blockProgram,
                                            @RequestParam(defaultValue = "1") int blockPage,
                                            @RequestParam(required = false) String courseSearch,
                                            @RequestParam(required = false) String anchor) {
        String r = academicService.repairScheduleConflicts(termId);
        return redirectToClassScheduling(termId, view, openBlockId, blockSearch, blockProgram, blockPage, courseSearch, anchor, r);
    }

    @PostMapping("/admin/class-scheduling/remove-schedule")
    public String removeSchedule(@RequestParam int scheduleId,
                                 @RequestParam int termId,
                                 @RequestParam(required = false, defaultValue = "blocks") String view,
                                 @RequestParam(required = false) Integer openBlockId,
                                 @RequestParam(required = false) String blockSearch,
                                 @RequestParam(required = false) String blockProgram,
                                 @RequestParam(defaultValue = "1") int blockPage,
                                 @RequestParam(required = false) String courseSearch,
                                 @RequestParam(required = false) String anchor,
                                 HttpSession session) {
        academicService.removeScheduleSlot(scheduleId);
        recordAcademicAudit(session, "SCHEDULING", "SCHEDULE_SLOT_REMOVED",
            "SCHEDULE", String.valueOf(scheduleId),
            "Schedule slot removed", "Schedule #" + scheduleId + " removed.",
            "class_schedules", String.valueOf(scheduleId));
        return redirectToClassScheduling(termId, view, openBlockId, blockSearch, blockProgram, blockPage, courseSearch, anchor,
            "SUCCESS: Schedule slot removed.");
    }

    @PostMapping("/admin/class-scheduling/close-section")
    public String closeSection(@RequestParam int sectionId,
                               @RequestParam int termId,
                               @RequestParam(required = false, defaultValue = "courses") String view,
                               @RequestParam(required = false) Integer openBlockId,
                               @RequestParam(required = false) String blockSearch,
                               @RequestParam(required = false) String blockProgram,
                               @RequestParam(defaultValue = "1") int blockPage,
                               @RequestParam(required = false) String courseSearch,
                               @RequestParam(required = false) String anchor,
                               HttpSession session) {
        String r = academicService.closeSection(sectionId);
        recordAcademicAudit(session, "SCHEDULING", "COURSE_SECTION_CLOSED",
            "SECTION", String.valueOf(sectionId),
            "Course section closed", r, "class_sections", String.valueOf(sectionId));
        return redirectToClassScheduling(termId, view, openBlockId, blockSearch, blockProgram, blockPage, courseSearch, anchor, r);
    }

    @PostMapping("/admin/class-scheduling/assign-faculty")
    public String assignFaculty(@RequestParam int sectionId, @RequestParam int termId,
                                @RequestParam(defaultValue="0") int facultyId,
                                @RequestParam(required = false, defaultValue = "blocks") String view,
                                @RequestParam(required = false) Integer openBlockId,
                                @RequestParam(required = false) String blockSearch,
                                @RequestParam(required = false) String blockProgram,
                                @RequestParam(defaultValue = "1") int blockPage,
                                @RequestParam(required = false) String courseSearch,
                                @RequestParam(required = false) String anchor,
                                HttpSession session) {
        String r = academicService.assignFaculty(sectionId, facultyId == 0 ? null : facultyId);
        recordAcademicAudit(session, "SCHEDULING", "SECTION_FACULTY_ASSIGNED",
            "SECTION", String.valueOf(sectionId),
            "Section faculty assignment updated", r, "class_sections", String.valueOf(sectionId));
        return redirectToClassScheduling(termId, view, openBlockId, blockSearch, blockProgram, blockPage, courseSearch, anchor, r);
    }

    static String redirectToClassScheduling(int termId,
                                            String view,
                                            Integer openBlockId,
                                            String blockSearch,
                                            String blockProgram,
                                            int blockPage,
                                            String courseSearch,
                                            String anchor,
                                            String msg) {
        StringBuilder redirect = new StringBuilder("redirect:/admin/class-scheduling?termId=").append(termId);
        appendSchedulingRedirectParam(redirect, "view", view);
        if (openBlockId != null && openBlockId > 0) {
            redirect.append("&openBlockId=").append(openBlockId);
        }
        appendSchedulingRedirectParam(redirect, "blockSearch", blockSearch);
        appendSchedulingRedirectParam(redirect, "blockProgram", blockProgram);
        appendSchedulingRedirectParam(redirect, "courseSearch", courseSearch);
        appendSchedulingRedirectParam(redirect, "anchor", anchor);
        appendSchedulingRedirectParam(redirect, "msg", msg);
        return redirect.toString();
    }

    static List<BlockSchoolGroup> groupBlocksBySchoolAndProgram(List<Map<String, Object>> blocks) {
        return groupBlocksBySchoolAndProgram(blocks, true);
    }

    static List<BlockSchoolGroup> groupBlockProgramSummaries(List<Map<String, Object>> summaries) {
        return groupBlocksBySchoolAndProgram(summaries, false);
    }

    private static List<BlockSchoolGroup> groupBlocksBySchoolAndProgram(List<Map<String, Object>> rows,
                                                                          boolean includeBlockHeaders) {
        Map<String, Map<String, List<Map<String, Object>>>> bySchool =
            new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map<String, Object> row : rows) {
            String schoolName = blockGroupValue(row, "school_name", "Unassigned School");
            String programCode = blockGroupValue(row, "program_code", "Unassigned Program");
            bySchool
                .computeIfAbsent(schoolName, ignored -> new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER))
                .computeIfAbsent(programCode, ignored -> new java.util.ArrayList<>())
                .add(row);
        }

        List<BlockSchoolGroup> groups = new java.util.ArrayList<>();
        for (Map.Entry<String, Map<String, List<Map<String, Object>>>> school : bySchool.entrySet()) {
            List<BlockProgramGroup> programs = new java.util.ArrayList<>();
            for (Map.Entry<String, List<Map<String, Object>>> program : school.getValue().entrySet()) {
                Map<String, Object> firstRow = program.getValue().get(0);
                int blockCount = includeBlockHeaders
                    ? program.getValue().size()
                    : blockGroupCount(firstRow);
                programs.add(new BlockProgramGroup(
                    program.getKey(),
                    blockGroupValue(firstRow, "program_name", program.getKey()),
                    includeBlockHeaders ? program.getValue() : java.util.List.of(),
                    blockCount));
            }
            groups.add(new BlockSchoolGroup(school.getKey(), programs));
        }
        return groups;
    }

    private static int blockGroupCount(Map<String, Object> row) {
        Object value = row.get("block_count");
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String blockGroupValue(Map<String, Object> block, String key, String fallback) {
        Object value = block.get(key);
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return value.toString().trim();
    }

    record BlockSchoolGroup(String schoolName, List<BlockProgramGroup> programs) { }

    record BlockProgramGroup(String programCode, String programName, List<Map<String, Object>> blocks,
                             int blockCount) { }

    private static void appendSchedulingRedirectParam(StringBuilder redirect, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        redirect.append('&')
            .append(key)
            .append('=')
            .append(java.net.URLEncoder.encode(value.trim(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @GetMapping("/admin/slot-monitoring")
    public String slotMonitoring(@RequestParam(defaultValue = "0") int termId,
                                 @RequestParam(required = false) String search,
                                 @RequestParam(required = false) String programCode,
                                 @RequestParam(required = false) String msg,
                                 Model model, HttpSession s) {
        if (s.getAttribute("currentUser") == null) return "redirect:/login";
        if (termId == 0) termId = academicService.getActiveTermId();
        String selectedProgramCode = programCode != null ? programCode.trim().toUpperCase() : "";
        boolean hasProgramFilter = !selectedProgramCode.isBlank();
        model.addAttribute("termId", termId);
        model.addAttribute("terms", academicService.getAllTerms());
        model.addAttribute("slotProgramOptions", slotMonitoringService.listProgramsForFilter(termId));
        model.addAttribute("sections", hasProgramFilter ? slotMonitoringService.listSectionsForTerm(termId, search, selectedProgramCode) : java.util.List.of());
        model.addAttribute("summary", hasProgramFilter ? slotMonitoringService.summary(termId, selectedProgramCode) : java.util.Map.of("total", 0, "open", 0, "closed", 0, "full", 0));
        model.addAttribute("hasSlotProgramFilter", hasProgramFilter);
        model.addAttribute("search", search);
        model.addAttribute("programCode", selectedProgramCode);
        if (msg != null) model.addAttribute("msg", msg);
        return "admin_slot_monitoring";
    }

    @GetMapping("/admin/room-monitoring")
    public String roomMonitoring(@RequestParam(defaultValue = "0") int termId,
                                 @RequestParam(defaultValue = "overview") String view,
                                 @RequestParam(required = false) String search,
                                 @RequestParam(required = false) String programCode,
                                 @RequestParam(required = false) String building,
                                 @RequestParam(required = false) String roomType,
                                 @RequestParam(required = false) Integer roomId,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "50") int size,
                                 @RequestParam(required = false) String msg,
                                 Model model, HttpSession s) {
        if (s.getAttribute("currentUser") == null) return "redirect:/login";
        if (termId == 0) termId = academicService.getActiveTermId();
        String selectedProgramCode = programCode != null ? programCode.trim().toUpperCase() : "";
        boolean hasProgramFilter = !selectedProgramCode.isBlank();
        boolean loadRoomDetails = hasProgramFilter;
        boolean loadRoomDiagnostics = hasProgramFilter;
        RoomMonitoringService.RoomPage roomPage = loadRoomDetails
            ? roomMonitoringService.listRoomsForTermPage(termId, search, building, roomType, selectedProgramCode, page, size)
            : new RoomMonitoringService.RoomPage(java.util.List.of(), 1, Math.min(100, Math.max(1, size)), 0, 1);
        model.addAttribute("termId", termId);
        model.addAttribute("selectedRoomMonitoringView", view);
        model.addAttribute("loadRoomDetails", loadRoomDetails);
        model.addAttribute("loadRoomDiagnostics", loadRoomDiagnostics);
        model.addAttribute("hasRoomProgramFilter", hasProgramFilter);
        model.addAttribute("terms", academicService.getAllTerms());
        model.addAttribute("roomProgramOptions", roomMonitoringService.listProgramsForFilter(termId));
        model.addAttribute("rooms", roomPage.rows());
        model.addAttribute("roomSchedules", loadRoomDetails && roomId != null && roomId > 0 ? roomMonitoringService.listRoomSchedules(termId, roomId, selectedProgramCode) : java.util.List.of());
        model.addAttribute("summary", hasProgramFilter ? roomMonitoringService.summary(termId, selectedProgramCode) : java.util.Map.of(
            "total_rooms", 0,
            "used_rooms", 0,
            "unused_rooms", 0,
            "conflict_rooms", 0,
            "scheduled_slots", 0,
            "missing_room_rows", 0,
            "sections_without_schedule", 0,
            "sections_without_faculty", 0));
        model.addAttribute("incompleteSchedules", loadRoomDiagnostics ? roomMonitoringService.incompleteSchedules(termId, selectedProgramCode) : java.util.List.of());
        model.addAttribute("buildings", roomMonitoringService.buildings());
        model.addAttribute("roomTypes", roomMonitoringService.roomTypes());
        model.addAttribute("selectedRoomId", roomId);
        model.addAttribute("page", roomPage.page());
        model.addAttribute("pageSize", roomPage.pageSize());
        model.addAttribute("totalRows", roomPage.totalRows());
        model.addAttribute("totalPages", roomPage.totalPages());
        model.addAttribute("pageStart", roomPage.totalRows() == 0 ? 0 : ((roomPage.page() - 1) * roomPage.pageSize()) + 1);
        model.addAttribute("pageEnd", Math.min(roomPage.page() * roomPage.pageSize(), roomPage.totalRows()));
        model.addAttribute("search", search);
        model.addAttribute("programCode", selectedProgramCode);
        model.addAttribute("building", building);
        model.addAttribute("roomType", roomType);
        if (msg != null) model.addAttribute("msg", msg);
        return "admin_room_monitoring";
    }

    @PostMapping("/admin/room-monitoring/add-room")
    public String addRoom(@RequestParam int termId,
                          @RequestParam String roomCode,
                          @RequestParam String buildingName,
                          @RequestParam int capacity,
                          @RequestParam(defaultValue = "Lecture") String roomType,
                          @RequestParam(defaultValue = "1") int activeStatus,
                          @RequestParam(required = false) String search,
                          @RequestParam(required = false) String programCode,
                          @RequestParam(required = false) String building,
                          @RequestParam(required = false) String roomFilterType) {
        String r = roomMonitoringService.createRoom(roomCode, buildingName, capacity, roomType, activeStatus);
        StringBuilder url = new StringBuilder("/admin/room-monitoring?termId=").append(termId);
        if (search != null && !search.isBlank()) {
            url.append("&search=").append(java.net.URLEncoder.encode(search, java.nio.charset.StandardCharsets.UTF_8));
        }
        if (programCode != null && !programCode.isBlank()) {
            url.append("&programCode=").append(java.net.URLEncoder.encode(programCode, java.nio.charset.StandardCharsets.UTF_8));
        }
        if (building != null && !building.isBlank()) {
            url.append("&building=").append(java.net.URLEncoder.encode(building, java.nio.charset.StandardCharsets.UTF_8));
        }
        if (roomFilterType != null && !roomFilterType.isBlank()) {
            url.append("&roomType=").append(java.net.URLEncoder.encode(roomFilterType, java.nio.charset.StandardCharsets.UTF_8));
        }
        url.append("&msg=").append(java.net.URLEncoder.encode(r, java.nio.charset.StandardCharsets.UTF_8));
        return "redirect:" + url;
    }

    @PostMapping("/admin/slot-monitoring/update-capacity")
    public String updateSlotCapacity(@RequestParam int sectionId, @RequestParam int termId,
                                     @RequestParam int maxCapacity,
                                     @RequestParam(required = false) String search,
                                     @RequestParam(required = false) String programCode,
                                     HttpSession session) {
        String r = slotMonitoringService.updateCapacity(sectionId, maxCapacity);
        recordAcademicAudit(session, "SLOT_MONITORING", "SECTION_CAPACITY_UPDATED",
            "SECTION", String.valueOf(sectionId),
            "Section capacity updated", "Capacity set to " + maxCapacity + ". " + r,
            "class_sections", String.valueOf(sectionId));
        return redirectSlotMonitoring(termId, search, programCode, r);
    }

    @PostMapping("/admin/slot-monitoring/close")
    public String closeSlotSection(@RequestParam int sectionId, @RequestParam int termId,
                                   @RequestParam(required = false) String search,
                                   @RequestParam(required = false) String programCode,
                                   HttpSession session) {
        String r = slotMonitoringService.closeSection(sectionId);
        recordAcademicAudit(session, "SLOT_MONITORING", "SECTION_CLOSED_FROM_SLOT_MONITORING",
            "SECTION", String.valueOf(sectionId),
            "Section closed from slot monitoring", r, "class_sections", String.valueOf(sectionId));
        return redirectSlotMonitoring(termId, search, programCode, r);
    }

    @PostMapping("/admin/slot-monitoring/bulk-close")
    public String bulkCloseSections(@RequestParam int termId,
                                    @RequestParam(required = false) List<Integer> sectionIds,
                                    @RequestParam(required = false) String search,
                                    @RequestParam(required = false) String programCode,
                                    HttpSession session) {
        String r = slotMonitoringService.bulkClose(termId, sectionIds);
        recordAcademicAudit(session, "SLOT_MONITORING", "SECTIONS_BULK_CLOSED",
            "TERM", String.valueOf(termId),
            "Sections bulk closed from slot monitoring", r, "class_sections", String.valueOf(termId));
        return redirectSlotMonitoring(termId, search, programCode, r);
    }

    private String redirectSlotMonitoring(int termId, String search, String programCode, String result) {
        StringBuilder url = new StringBuilder("/admin/slot-monitoring?termId=").append(termId);
        if (search != null && !search.isBlank()) {
            url.append("&search=").append(java.net.URLEncoder.encode(search, java.nio.charset.StandardCharsets.UTF_8));
        }
        if (programCode != null && !programCode.isBlank()) {
            url.append("&programCode=").append(java.net.URLEncoder.encode(programCode, java.nio.charset.StandardCharsets.UTF_8));
        }
        url.append("&msg=").append(java.net.URLEncoder.encode(result, java.nio.charset.StandardCharsets.UTF_8));
        return "redirect:" + url;
    }

    private String redirectToClasses(String search, String status, Integer page, Integer size) {
        StringBuilder target = new StringBuilder("redirect:/admin/classes");
        boolean hasQuery = false;
        if (search != null && !search.isBlank()) {
            target.append("?search=").append(java.net.URLEncoder.encode(search.trim(), java.nio.charset.StandardCharsets.UTF_8));
            hasQuery = true;
        }
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            target.append(hasQuery ? "&" : "?").append("status=")
                .append(java.net.URLEncoder.encode(status.trim(), java.nio.charset.StandardCharsets.UTF_8));
            hasQuery = true;
        }
        if (page != null && page > 1) {
            target.append(hasQuery ? "&" : "?").append("page=").append(page);
            hasQuery = true;
        }
        if (size != null && size != 50) {
            target.append(hasQuery ? "&" : "?").append("size=").append(size);
        }
        return target.toString();
    }

    private void recordAcademicAudit(HttpSession session,
                                     String moduleName,
                                     String actionName,
                                     String targetType,
                                     String targetKey,
                                     String summary,
                                     String details,
                                     String sourceTable,
                                     String sourceId) {
        if (auditTrailService == null) {
            return;
        }
        auditTrailService.record(
            currentUsername(session),
            "Registrar",
            moduleName,
            actionName,
            targetType,
            targetKey,
            summary,
            details,
            sourceTable,
            sourceId);
    }
}
