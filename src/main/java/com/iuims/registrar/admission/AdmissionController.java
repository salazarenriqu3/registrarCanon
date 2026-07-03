package com.iuims.registrar.admission;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
public class AdmissionController {

    @Autowired private FinanceAdmissionService financeService;
    @Autowired private JdbcTemplate db;
    @Autowired private ApplicantPreRegSnapshotService preRegSnapshotService;

    @GetMapping("/admin/admission-acceptance")
    public String admissionPage(@RequestParam(required = false) String refNo, Model model, HttpSession session) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";

        List<Map<String, Object>> programs = db.queryForList(
            "SELECT program_code, program_name FROM programs WHERE active_status = 1 ORDER BY program_name"
        );
        model.addAttribute("programs", programs);

        if (refNo != null && !refNo.isEmpty()) {
            Map<String, Object> app = financeService.getApplicantDetails(refNo);
            if (app != null) {
                model.addAttribute("applicant", app);
                boolean irregularApplicant = preRegSnapshotService.isIrregularApplicant(app);
                model.addAttribute("isIrregularApplicant", irregularApplicant);
                if (irregularApplicant) {
                    Map<String, Object> snapshot = preRegSnapshotService.findSnapshotByReference(refNo);
                    model.addAttribute("preRegSnapshot", snapshot.get("snapshot"));
                    model.addAttribute("preRegLines", snapshot.get("subject_lines"));
                    model.addAttribute("preRegLineCount", snapshot.get("line_count"));
                    model.addAttribute("preRegReady", snapshot.get("ready"));
                    model.addAttribute("preRegFinalized", snapshot.get("finalized"));
                }
            } else {
                model.addAttribute("message", "Applicant not found or already processed.");
            }
        }

        return "admin_admission_acceptance";
    }

    /** AJAX search endpoint used by the admission page autocomplete. */
    @GetMapping("/api/search-applicants")
    @ResponseBody
    public List<Map<String, Object>> searchApplicants(@RequestParam String query) {
        return financeService.searchApplicantsByName(query);
    }

    @PostMapping("/admin/approve-admission")
    public String approveAdmission(@RequestParam String refNo,
                                   @RequestParam String programCode,
                                   @RequestParam int yearLevel,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";

        String existingStudentNumber = financeService.findStudentNumberByReference(refNo);
        if (hasText(existingStudentNumber)) {
            redirectAttributes.addFlashAttribute("successMessage",
                "Student number " + existingStudentNumber + " already exists for this applicant. Continue from Student Profile or Cashier.");
            return "redirect:/admin/student-manager?username=" + existingStudentNumber;
        }

        Map<String, Object> applicant = financeService.getApplicantDetails(refNo);
        if (applicant == null) {
            redirectAttributes.addFlashAttribute("message", "Applicant not found.");
            return "redirect:/admin/admission-acceptance?refNo=" + refNo;
        }

        if (!preRegSnapshotService.isIrregularApplicant(applicant)) {
            redirectAttributes.addFlashAttribute("message",
                "Registrar only previews the shared irregular handoff here. Regular applicants continue through Admission and Enrollment/Cashier.");
            return "redirect:/admin/admission-acceptance?refNo=" + refNo;
        }

        String irregularBlock = preRegSnapshotService.validateRegistrarSnapshotReady(refNo, programCode);
        if (irregularBlock != null) {
            redirectAttributes.addFlashAttribute("message", irregularBlock);
            return "redirect:/admin/admission-acceptance?refNo=" + refNo;
        }

        redirectAttributes.addFlashAttribute("successMessage",
            "Enrollment3 faculty/dean pre-advising is ready. Continue applicant qualification in Admission and payment/student-number issuance in Enrollment/Cashier.");
        return "redirect:/admin/admission-acceptance?refNo=" + refNo;
    }

    @GetMapping("/admin/pre-reg/{refNo}/snapshot")
    @ResponseBody
    public Map<String, Object> viewPreRegSnapshot(@PathVariable String refNo, HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return Map.of("error", "LOGIN_REQUIRED");
        }
        return preRegSnapshotService.findSnapshotByReference(refNo);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
