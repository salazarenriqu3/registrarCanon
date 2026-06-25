package com.iuims.registrar.portal;
import com.iuims.registrar.academic.AcademicGradingService;
import com.iuims.registrar.academic.BlockOfferingService;
import com.iuims.registrar.academic.ClassInfoDto;
import com.iuims.registrar.academic.SlotMonitoringService;
import com.iuims.registrar.core.GradeOutcomeSql;
import com.iuims.registrar.admission.ApplicantStatusSyncService;
import com.iuims.registrar.admission.FinanceAdmissionService;
import com.iuims.registrar.curriculum.CurriculumSeederService;
import com.iuims.registrar.curriculum.StudentCurriculumService;
import com.iuims.registrar.core.EnlistmentSchemaService;
import com.iuims.registrar.faculty.FacultyLoadService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.finance.TermFeeAdminService;
import com.iuims.registrar.core.DatabaseSetupService;
import com.iuims.registrar.jaypee.JaypeeIntegrationService;
import com.iuims.registrar.core.PolicySettings;
import com.iuims.registrar.core.SqlGenerator;

import com.iuims.registrar.academic.AcademicGradingService;
import com.iuims.registrar.academic.ClassInfoDto;
import com.iuims.registrar.finance.TermFeeAdminService;
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
    private final FacultyLoadService facultyLoadService;

    public AcademicController(AcademicGradingService academicService, TermFeeAdminService termFeeAdminService,
                              BlockOfferingService blockOfferingService,
                              StudentCurriculumService studentCurriculumService,
                              SlotMonitoringService slotMonitoringService,
                              FacultyLoadService facultyLoadService) {
        this.academicService = academicService;
        this.termFeeAdminService = termFeeAdminService;
        this.blockOfferingService = blockOfferingService;
        this.studentCurriculumService = studentCurriculumService;
        this.slotMonitoringService = slotMonitoringService;
        this.facultyLoadService = facultyLoadService;
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
    public Map<String, Object> autoSaveGrade(@RequestParam int gradeId, @RequestParam String prelim, @RequestParam String midterm, @RequestParam String finals, @RequestParam(defaultValue="LEC") String gradeType) {
        return academicService.saveGradeAsync(gradeId, prelim, midterm, finals);
    }

    @GetMapping("/api/mcp/classes/{id}")
    @ResponseBody
    public ClassInfoDto getMcpClassInfo(@PathVariable int id) {
        return academicService.getClassInfoDto(id);
    }

    @PostMapping("/faculty/submit-class")
    public String submitClass(@RequestParam int scheduleId) { academicService.submitClassGrades(scheduleId); return "redirect:/grades/view/" + scheduleId; }

    @PostMapping("/faculty/unsubmit-class")
    public String unsubmitClass(@RequestParam int scheduleId) { academicService.unsubmitClassGrades(scheduleId); return "redirect:/grades/view/" + scheduleId; }

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
    public String toggleUnlock(@RequestParam int scheduleId, @RequestParam int unlockStatus) { academicService.toggleClassUnlock(scheduleId, unlockStatus); return "redirect:/admin/classes"; }

    @GetMapping("/admin/approvals")
    public String adminApprovals(HttpSession s, Model m) {
        if(s.getAttribute("currentUser") == null) return "redirect:/login";
        m.addAttribute("pendingClasses", academicService.getPendingClassSubmissions());
        m.addAttribute("requests", academicService.getGradeChangeRequests());
        m.addAttribute("extensions", academicService.getPendingExtensions());
        return "admin_approvals";
    }

    @PostMapping("/admin/approve-class")
    public String approveClass(@RequestParam int scheduleId) { academicService.finalizeClassGrades(scheduleId); return "redirect:/admin/approvals"; }

    @PostMapping("/admin/approve-change")
    public String approveChange(@RequestParam int requestId, HttpSession session) {
        academicService.approveGradeChange(requestId, currentUsername(session));
        return "redirect:/admin/approvals";
    }

    @GetMapping("/admin/users")
    public String adminUsers(Model m, HttpSession s) {
        if(s.getAttribute("currentUser") == null) return "redirect:/login";
        m.addAttribute("users", academicService.getAllUsers());
        return "admin_users";
    }

    @PostMapping("/create-user")
    public String createUser(@RequestParam String username, @RequestParam String realName, @RequestParam String role, @RequestParam(required=false) String program, @RequestParam(required=false) List<String> permissions) { academicService.createUser(username, realName, role, program, permissions); return "redirect:/admin/users"; }

    @PostMapping("/admin/update-user")
    public String updateUser(@RequestParam int userId, @RequestParam String role, @RequestParam(required=false) List<String> permissions) { academicService.updateUserPermissions(userId, role, permissions); return "redirect:/admin/users"; }

    @PostMapping("/admin/delete-user")
    public String deleteUser(@RequestParam int userId) { academicService.deleteUser(userId); return "redirect:/admin/users"; }

    @PostMapping("/admin/toggle-status")
    public String toggleStatus(@RequestParam int userId, @RequestParam boolean isActive) { academicService.toggleUserStatus(userId, isActive); return "redirect:/admin/users"; }

    @PostMapping("/admin/reset-password")
    public String resetPassword(@RequestParam int userId) { academicService.resetPassword(userId); return "redirect:/admin/users"; }

    @GetMapping("/admin/classes")
    public String adminClasses(HttpSession s, Model m) {
        if(s.getAttribute("currentUser") == null) return "redirect:/login";
        m.addAttribute("classes", academicService.getAllClassesAdmin());
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

    @PostMapping("/admin/revert-class")
    public String revertClass(@RequestParam int scheduleId) { academicService.revertClassToDraft(scheduleId); return "redirect:/admin/classes"; }

    // ---- Class Scheduling Management ----

    @GetMapping("/admin/class-scheduling")
    public String classScheduling(
            @RequestParam(defaultValue = "0") int termId,
            @RequestParam(required = false) String dept,
            @RequestParam(defaultValue = "overview") String view,
            @RequestParam(required = false) String msg,
            Model model, HttpSession s) {
        if (s.getAttribute("currentUser") == null) return "redirect:/login";
        if (termId == 0) termId = academicService.getActiveTermId();
        blockOfferingService.ensureSchema();
        blockOfferingService.syncLegacyBlockLinks(termId);
        var blocks = blockOfferingService.listBlocksForTerm(termId);
        var blockCourses = new java.util.LinkedHashMap<Integer, List<Map<String, Object>>>();
        boolean loadBlockDetails = "blocks".equalsIgnoreCase(view) || "all".equalsIgnoreCase(view);
        boolean loadCourseSections = "courses".equalsIgnoreCase(view) || "all".equalsIgnoreCase(view);
        if (loadBlockDetails) {
            for (Map<String, Object> block : blocks) {
                int blockId = ((Number) block.get("block_id")).intValue();
                blockCourses.put(blockId, blockOfferingService.listBlockCourses(blockId));
            }
        }
        model.addAttribute("termId",   termId);
        model.addAttribute("deptFilter", dept);
        model.addAttribute("selectedSchedulingView", view);
        model.addAttribute("loadBlockDetails", loadBlockDetails);
        model.addAttribute("loadCourseSections", loadCourseSections);
        model.addAttribute("terms",    academicService.getAllTerms());
        model.addAttribute("blocks",   blocks);
        model.addAttribute("blockCourses", blockCourses);
        model.addAttribute("programs", blockOfferingService.listPrograms());
        model.addAttribute("curricula", studentCurriculumService.listAssignableCurricula());
        model.addAttribute("courses",  loadCourseSections ? academicService.getCoursesWithSections(termId) : java.util.List.of());
        model.addAttribute("faculty",  academicService.getAllFacultyForScheduling());
        model.addAttribute("rooms",    academicService.getAllRoomsForScheduling());
        var conflictPreview = academicService.getScheduleConflictPreview(termId, 50);
        model.addAttribute("scheduleConflicts", conflictPreview.conflicts());
        model.addAttribute("scheduleConflictsTruncated", conflictPreview.truncated());
        model.addAttribute("assignmentAudit", facultyLoadService.getTermAssignmentAudit(termId));
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
                              @RequestParam(defaultValue = "0") int curriculumId) {
        String r = blockOfferingService.createAndMaterializeBlock(
            termId, programCode, yearLevel, semesterNumber, sectionGroup, maxCapacity,
            facultyId == 0 ? null : facultyId, curriculumId == 0 ? null : curriculumId);
        return "redirect:/admin/class-scheduling?termId=" + termId + "&msg="
            + java.net.URLEncoder.encode(r, java.nio.charset.StandardCharsets.UTF_8);
    }

    @PostMapping("/admin/class-scheduling/rematerialize-block")
    public String rematerializeBlock(@RequestParam int blockId, @RequestParam int termId) {
        String r = blockOfferingService.rematerializeBlock(blockId);
        return "redirect:/admin/class-scheduling?termId=" + termId + "&msg="
            + java.net.URLEncoder.encode(r, java.nio.charset.StandardCharsets.UTF_8);
    }

    @PostMapping("/admin/class-scheduling/open-section")
    public String openSection(@RequestParam int courseId, @RequestParam int termId,
                              @RequestParam String sectionCode, @RequestParam(defaultValue="0") int facultyId,
                              @RequestParam(defaultValue="40") int maxCapacity) {
        String r = academicService.openSection(courseId, termId, sectionCode,
                        facultyId == 0 ? null : facultyId, maxCapacity);
        return "redirect:/admin/class-scheduling?termId=" + termId + "&msg=" + java.net.URLEncoder.encode(r, java.nio.charset.StandardCharsets.UTF_8);
    }

    @PostMapping("/admin/class-scheduling/add-schedule")
    public String addSchedule(@RequestParam int sectionId, @RequestParam int termId,
                              @RequestParam int day1, @RequestParam(defaultValue="0") int day2,
                              @RequestParam String startTime, @RequestParam String endTime,
                              @RequestParam(defaultValue="0") int roomId) {
        String r = academicService.addScheduleSlot(sectionId, day1, startTime, endTime,
                        roomId == 0 ? null : roomId, day2 == 0 ? null : day2);
        return "redirect:/admin/class-scheduling?termId=" + termId + "&msg=" + java.net.URLEncoder.encode(r, java.nio.charset.StandardCharsets.UTF_8);
    }

    @PostMapping("/admin/class-scheduling/remove-schedule")
    public String removeSchedule(@RequestParam int scheduleId, @RequestParam int termId) {
        academicService.removeScheduleSlot(scheduleId);
        return "redirect:/admin/class-scheduling?termId=" + termId;
    }

    @PostMapping("/admin/class-scheduling/close-section")
    public String closeSection(@RequestParam int sectionId, @RequestParam int termId) {
        String r = academicService.closeSection(sectionId);
        return "redirect:/admin/class-scheduling?termId=" + termId + "&msg=" + java.net.URLEncoder.encode(r, java.nio.charset.StandardCharsets.UTF_8);
    }

    @PostMapping("/admin/class-scheduling/assign-faculty")
    public String assignFaculty(@RequestParam int sectionId, @RequestParam int termId,
                                @RequestParam(defaultValue="0") int facultyId) {
        String r = academicService.assignFaculty(sectionId, facultyId == 0 ? null : facultyId);
        return "redirect:/admin/class-scheduling?termId=" + termId + "&msg="
            + java.net.URLEncoder.encode(r, java.nio.charset.StandardCharsets.UTF_8);
    }

    @GetMapping("/admin/slot-monitoring")
    public String slotMonitoring(@RequestParam(defaultValue = "0") int termId,
                                 @RequestParam(required = false) String search,
                                 @RequestParam(required = false) String msg,
                                 Model model, HttpSession s) {
        if (s.getAttribute("currentUser") == null) return "redirect:/login";
        if (termId == 0) termId = academicService.getActiveTermId();
        model.addAttribute("termId", termId);
        model.addAttribute("terms", academicService.getAllTerms());
        model.addAttribute("sections", slotMonitoringService.listSectionsForTerm(termId, search));
        model.addAttribute("summary", slotMonitoringService.summary(termId));
        model.addAttribute("search", search);
        if (msg != null) model.addAttribute("msg", msg);
        return "admin_slot_monitoring";
    }

    @PostMapping("/admin/slot-monitoring/update-capacity")
    public String updateSlotCapacity(@RequestParam int sectionId, @RequestParam int termId,
                                     @RequestParam int maxCapacity,
                                     @RequestParam(required = false) String search) {
        String r = slotMonitoringService.updateCapacity(sectionId, maxCapacity);
        return redirectSlotMonitoring(termId, search, r);
    }

    @PostMapping("/admin/slot-monitoring/close")
    public String closeSlotSection(@RequestParam int sectionId, @RequestParam int termId,
                                   @RequestParam(required = false) String search) {
        String r = slotMonitoringService.closeSection(sectionId);
        return redirectSlotMonitoring(termId, search, r);
    }

    @PostMapping("/admin/slot-monitoring/bulk-close")
    public String bulkCloseSections(@RequestParam int termId,
                                    @RequestParam(required = false) List<Integer> sectionIds,
                                    @RequestParam(required = false) String search) {
        String r = slotMonitoringService.bulkClose(termId, sectionIds);
        return redirectSlotMonitoring(termId, search, r);
    }

    private String redirectSlotMonitoring(int termId, String search, String result) {
        StringBuilder url = new StringBuilder("/admin/slot-monitoring?termId=").append(termId);
        if (search != null && !search.isBlank()) {
            url.append("&search=").append(java.net.URLEncoder.encode(search, java.nio.charset.StandardCharsets.UTF_8));
        }
        url.append("&msg=").append(java.net.URLEncoder.encode(result, java.nio.charset.StandardCharsets.UTF_8));
        return "redirect:" + url;
    }
}
