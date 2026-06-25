package com.iuims.registrar.faculty;
import com.iuims.registrar.academic.AcademicGradingService;
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

import com.iuims.registrar.faculty.FacultyLoadService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
public class FacultyLoadController {

    @Autowired
    private FacultyLoadService loadService;

    // ------------------------------------------------------------------
    // Dashboard page
    // ------------------------------------------------------------------

    @GetMapping("/admin/faculty-load")
    public String facultyLoadDashboard(
            @RequestParam(required = false) Integer termId,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) String msg,
            HttpSession session, Model model) {

        if (session.getAttribute("currentUser") == null) return "redirect:/login";

        Map<String, Object> activeTerm = loadService.getActiveTerm();
        if (termId == null) termId = ((Number) activeTerm.get("term_id")).intValue();

        List<Map<String, Object>> loads;
        if (departmentId != null) {
            loads = loadService.getDepartmentLoadSummary(departmentId, termId);
        } else {
            loads = loadService.getAllFacultyLoadSummary(termId);
        }

        model.addAttribute("loads",       loads);
        model.addAttribute("terms",       loadService.getAllTerms());
        model.addAttribute("departments", loadService.getAllDepartments());
        model.addAttribute("selectedTermId",  termId);
        model.addAttribute("selectedDeptId",  departmentId);
        model.addAttribute("activeTerm",  activeTerm);
        model.addAttribute("assignmentAudit", loadService.getTermAssignmentAudit(termId));
        model.addAttribute("msg", msg);
        return "admin_faculty_load";
    }

    @PostMapping("/admin/faculty-load/repair-suspicious")
    public String repairSuspiciousAssignments(@RequestParam int termId, HttpSession session) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";

        Map<String, Object> result = loadService.repairSuspiciousTermAssignments(termId);
        String message = String.valueOf(result.getOrDefault("message", "Repair attempt completed."));
        return "redirect:/admin/faculty-load?termId=" + termId + "&msg=" +
            java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // JSON detail for one faculty
    // ------------------------------------------------------------------

    @GetMapping("/api/faculty/{facultyId}/load")
    @ResponseBody
    public Map<String, Object> getFacultyLoadDetail(
            @PathVariable int facultyId,
            @RequestParam(defaultValue = "1") int termId) {

        Map<String, Object> summary = loadService.getFacultyLoadSummary(facultyId, termId);
        List<Map<String, Object>> sections = loadService.getFacultyLoad(facultyId, termId);
        return Map.of("summary", summary, "sections", sections);
    }

    // ------------------------------------------------------------------
    // Assign faculty to a section (with cap check)
    // ------------------------------------------------------------------

    @PostMapping("/api/schedule/assign-faculty")
    @ResponseBody
    public Map<String, Object> assignFaculty(
            @RequestParam int sectionId,
            @RequestParam int facultyId,
            @RequestParam int termId,
            HttpSession session) {

        if (session.getAttribute("currentUser") == null)
            return Map.of("success", false, "error", "Unauthorized");

        boolean wouldOverload = loadService.wouldExceedUnitCap(facultyId, termId, sectionId);
        if (wouldOverload) {
            Map<String, Object> summary = loadService.getFacultyLoadSummary(facultyId, termId);
            return Map.of(
                "success", false,
                "overload", true,
                "message", "Assignment would exceed faculty unit cap (" +
                           summary.get("total_load_units") + "/" + summary.get("max_teaching_units") + " units)"
            );
        }

        // Perform assignment
        try {
            // Update class_sections faculty assignment
            int rows = loadService.assignFacultyToSection(sectionId, facultyId);
            if (rows > 0) {
                return Map.of("success", true, "message", "Faculty assigned successfully.");
            } else {
                return Map.of("success", false, "message", "Section not found.");
            }
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}




