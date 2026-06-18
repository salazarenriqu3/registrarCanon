package com.iuims.registrar.curriculum;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProgramController {

    @Autowired
    private ProgramService programService;

    @GetMapping("/admin/programs")
    public String programCatalog(HttpSession session,
                                 Model model,
                                 Authentication authentication,
                                 @RequestParam(required = false) String search,
                                 @RequestParam(required = false) Integer departmentId,
                                 @RequestParam(defaultValue = "active") String status,
                                 @RequestParam(required = false) String msg,
                                 @RequestParam(required = false) String error) {
        if (session.getAttribute("currentUser") == null) {
            return "redirect:/login";
        }

        model.addAttribute("programs", programService.listPrograms(search, departmentId, status));
        model.addAttribute("departments", programService.listDepartments());
        model.addAttribute("summary", programService.summary(search, departmentId, status));
        model.addAttribute("search", search);
        model.addAttribute("selectedDepartmentId", departmentId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("canManagePrograms", canManagePrograms(authentication));
        if (msg != null) {
            model.addAttribute("successMsg", msg);
        }
        if (error != null) {
            model.addAttribute("errorMsg", error);
        }
        return "admin_programs";
    }

    @PostMapping("/admin/programs/save")
    public String saveProgram(@RequestParam(required = false) Integer programId,
                              @RequestParam String programCode,
                              @RequestParam String programName,
                              @RequestParam Integer departmentId,
                              @RequestParam(required = false) String schoolName,
                              @RequestParam(required = false) Integer durationYears,
                              @RequestParam(required = false) Boolean active,
                              @RequestParam(required = false) String returnSearch,
                              @RequestParam(required = false) Integer returnDepartmentId,
                              @RequestParam(required = false, defaultValue = "active") String returnStatus,
                              HttpSession session,
                              Authentication authentication,
                              RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) {
            return "redirect:/login";
        }
        if (!canManagePrograms(authentication)) {
            ra.addAttribute("error", "Only administrators may create or edit program records.");
            addReturnFilters(ra, returnSearch, returnDepartmentId, returnStatus);
            return "redirect:/admin/programs";
        }
        try {
            Integer savedId = programService.saveProgram(
                programId, programCode, programName, departmentId, schoolName, durationYears, active);
            ra.addAttribute("msg", "Program saved.");
            ra.addAttribute("focusProgramId", savedId);
        } catch (Exception e) {
            ra.addAttribute("error", "Save failed: " + e.getMessage());
        }
        addReturnFilters(ra, returnSearch, returnDepartmentId, returnStatus);
        return "redirect:/admin/programs";
    }

    @GetMapping("/admin/programs/usage")
    @ResponseBody
    public ResponseEntity<?> programUsage(@RequestParam int programId, HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return ResponseEntity.status(401).body("Login required.");
        }
        try {
            return ResponseEntity.ok(programService.usageDetails(programId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/admin/programs/status")
    public String setProgramStatus(@RequestParam int programId,
                                   @RequestParam boolean active,
                                   @RequestParam(required = false) String returnSearch,
                                   @RequestParam(required = false) Integer returnDepartmentId,
                                   @RequestParam(required = false, defaultValue = "active") String returnStatus,
                                   HttpSession session,
                                   Authentication authentication,
                                   RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) {
            return "redirect:/login";
        }
        if (!canManagePrograms(authentication)) {
            ra.addAttribute("error", "Only administrators may change program status.");
            addReturnFilters(ra, returnSearch, returnDepartmentId, returnStatus);
            return "redirect:/admin/programs";
        }
        try {
            programService.setActiveStatus(programId, active);
            ra.addAttribute("msg", active ? "Program reactivated." : "Program deactivated.");
        } catch (Exception e) {
            ra.addAttribute("error", "Status update failed: " + e.getMessage());
        }
        addReturnFilters(ra, returnSearch, returnDepartmentId, returnStatus);
        return "redirect:/admin/programs";
    }

    @PostMapping("/admin/programs/delete-unused")
    public String deleteUnusedProgram(@RequestParam int programId,
                                      @RequestParam(required = false) String returnSearch,
                                      @RequestParam(required = false) Integer returnDepartmentId,
                                      @RequestParam(required = false, defaultValue = "active") String returnStatus,
                                      HttpSession session,
                                      Authentication authentication,
                                      RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) {
            return "redirect:/login";
        }
        if (!canManagePrograms(authentication)) {
            ra.addAttribute("error", "Only administrators may delete program records.");
            addReturnFilters(ra, returnSearch, returnDepartmentId, returnStatus);
            return "redirect:/admin/programs";
        }
        try {
            programService.deleteUnusedProgram(programId);
            ra.addAttribute("msg", "Unused program deleted.");
        } catch (Exception e) {
            ra.addAttribute("error", "Delete failed: " + e.getMessage());
        }
        addReturnFilters(ra, returnSearch, returnDepartmentId, returnStatus);
        return "redirect:/admin/programs";
    }

    private boolean canManagePrograms(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
    }

    private void addReturnFilters(RedirectAttributes ra, String search, Integer departmentId, String status) {
        if (search != null && !search.isBlank()) {
            ra.addAttribute("search", search);
        }
        if (departmentId != null && departmentId > 0) {
            ra.addAttribute("departmentId", departmentId);
        }
        if (status != null && !status.isBlank()) {
            ra.addAttribute("status", status);
        }
    }
}
