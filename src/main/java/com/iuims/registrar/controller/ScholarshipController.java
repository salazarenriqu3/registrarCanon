package com.iuims.registrar.controller;
import com.iuims.registrar.entity.Student;
import com.iuims.registrar.service.academic.AcademicGradingService;
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
import com.iuims.registrar.service.integration.JaypeeIntegrationService;
import com.iuims.registrar.support.PolicySettings;
import com.iuims.registrar.support.SqlGenerator;

import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
public class ScholarshipController {

    @Autowired
    private ScholarEnrollmentService scholarEnrollmentService;

    @GetMapping("/admin/scholarships")
    public String scholarshipDashboard(@RequestParam(required = false) Integer termId,
                                       @RequestParam(required = false) String success,
                                       @RequestParam(required = false) String error,
                                       HttpSession session,
                                       Model model) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        Integer selectedTermId = termId != null && termId > 0 ? termId : scholarEnrollmentService.getDefaultScholarshipTermId();
        model.addAttribute("terms", scholarEnrollmentService.getScholarshipTermOptions());
        model.addAttribute("selectedTermId", selectedTermId);
        model.addAttribute("policy", scholarEnrollmentService.getScholarshipPolicySettings());
        model.addAttribute("candidates", scholarEnrollmentService.evaluateAcademicScholarshipCandidates(selectedTermId));
        if (success != null && !success.isBlank()) model.addAttribute("successMessage", success);
        if (error != null && !error.isBlank()) model.addAttribute("errorMessage", error);
        return "admin_scholarships";
    }

    @PostMapping("/admin/scholarships/policies")
    public String saveScholarshipPolicies(@RequestParam Map<String, String> params,
                                          HttpSession session,
                                          RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        scholarEnrollmentService.updateScholarshipPolicySettings(params);
        ra.addAttribute("success", "Scholarship policy updated.");
        appendTermId(ra, params.get("termId"));
        return "redirect:/admin/scholarships";
    }

    @PostMapping("/admin/scholarships/types")
    public String saveScholarshipType(@RequestParam Map<String, String> params,
                                      HttpSession session,
                                      RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        ra.addAttribute("error", "Manual scholarship type maintenance is retired. Registrar manages academic scholarship only.");
        appendTermId(ra, params.get("termId"));
        return "redirect:/admin/scholarships";
    }

    @PostMapping("/admin/scholarships/grant")
    public String grantExternalScholarship(
            @RequestParam(value = "studentNumber", required = false) String studentNumber,
            @RequestParam(value = "sysUserId", required = false) String sysUserId,
            @RequestParam(value = "classification", required = false) String classification,
            @RequestParam(value = "discountPct", required = false, defaultValue = "0") double discountPct,
            @RequestParam(value = "scholarshipAmount", required = false, defaultValue = "0") double scholarshipAmount,
            @RequestParam(value = "status", required = false, defaultValue = "RETIRED") String status,
            @RequestParam(value = "returnTo", required = false) String returnTo,
            RedirectAttributes ra) {
        ra.addFlashAttribute("message", "ERROR: Manual scholarship grants are retired. Use Academic Scholarship Review.");
        if ("student-manager".equals(returnTo) && studentNumber != null && !studentNumber.isBlank()) {
            return "redirect:/admin/student-manager?username=" + URLEncoder.encode(studentNumber, StandardCharsets.UTF_8);
        }
        return "redirect:/admin/scholarships";
    }

    @PostMapping("/admin/scholarships/grant-academic")
    public String requestAcademicScholarship(@RequestParam String studentNumber,
                                           @RequestParam Integer termId,
                                           HttpSession session,
                                           RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        String result = scholarEnrollmentService.requestAcademicScholarship(studentNumber, termId, currentUsername(session));
        addWorkflowResult(ra, result, "Scholarship submitted for review for " + studentNumber + ".");
        appendTermId(ra, termId != null ? String.valueOf(termId) : null);
        return "redirect:/admin/scholarships";
    }

    @PostMapping("/admin/scholarships/approve-academic")
    public String approveAcademicScholarship(@RequestParam String studentNumber, @RequestParam Integer termId,
                                             @RequestParam(required = false) String note, HttpSession session,
                                             RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        String result = scholarEnrollmentService.approveAcademicScholarship(studentNumber, termId, currentUsername(session), note);
        addWorkflowResult(ra, result, "Scholarship approved and ready for posting.");
        appendTermId(ra, String.valueOf(termId));
        return "redirect:/admin/scholarships";
    }

    @PostMapping("/admin/scholarships/reject-academic")
    public String rejectAcademicScholarship(@RequestParam String studentNumber, @RequestParam Integer termId,
                                            @RequestParam(required = false) String note, HttpSession session,
                                            RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        String result = scholarEnrollmentService.rejectAcademicScholarship(studentNumber, termId, currentUsername(session), note);
        addWorkflowResult(ra, result, "Scholarship review rejected.");
        appendTermId(ra, String.valueOf(termId));
        return "redirect:/admin/scholarships";
    }

    @PostMapping("/admin/scholarships/post-academic")
    public String postAcademicScholarship(@RequestParam String studentNumber, @RequestParam Integer termId,
                                          HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        String result = scholarEnrollmentService.postAcademicScholarship(studentNumber, termId, currentUsername(session));
        addWorkflowResult(ra, result, "Scholarship posted. The approved discount is now active.");
        appendTermId(ra, String.valueOf(termId));
        return "redirect:/admin/scholarships";
    }

    @PostMapping("/admin/scholarships/revoke")
    public String revokeScholarship(@RequestParam String studentNumber,
                                    @RequestParam(required = false) Integer termId,
                                    HttpSession session,
                                    RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        String result = scholarEnrollmentService.grantExternalScholarship(studentNumber, "NONE", 0.0, "REVOKED");
        if (result != null && result.startsWith("SUCCESS")) {
            scholarEnrollmentService.markAcademicScholarshipRevoked(studentNumber, termId, currentUsername(session));
            ra.addAttribute("success", "Scholarship revoked for " + studentNumber + ".");
        } else {
            ra.addAttribute("error", result != null ? result : "Unable to revoke scholarship.");
        }
        appendTermId(ra, termId != null ? String.valueOf(termId) : null);
        return "redirect:/admin/scholarships";
    }

    @PostMapping("/admin/scholarships/evaluate")
    @ResponseBody
    public String evaluateInternalScholarships(
            @RequestParam("currentSemester") int currentSemester,
            @RequestParam("currentYear") int currentYear) {
        scholarEnrollmentService.runGradeBasedRenewal(currentSemester, currentYear);
        return "SUCCESS: Internal scholarship evaluation triggered.";
    }

    private void appendTermId(RedirectAttributes ra, String rawTermId) {
        if (rawTermId != null && !rawTermId.isBlank()) {
            ra.addAttribute("termId", rawTermId);
        }
    }

    private void addWorkflowResult(RedirectAttributes ra, String result, String successMessage) {
        if (result != null && result.startsWith("SUCCESS")) {
            ra.addAttribute("success", successMessage);
        } else {
            ra.addAttribute("error", result != null ? result : "Unable to update scholarship review.");
        }
    }

    private String currentUsername(HttpSession session) {
        Object raw = session.getAttribute("currentUser");
        if (raw instanceof Map<?, ?> user && user.get("username") != null) {
            return user.get("username").toString();
        }
        return "SYSTEM";
    }

}




