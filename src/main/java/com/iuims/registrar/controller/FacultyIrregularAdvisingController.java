package com.iuims.registrar.controller;

import com.iuims.registrar.service.admission.ApplicantPreRegSnapshotService;
import com.iuims.registrar.service.admission.FinanceAdmissionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/faculty")
public class FacultyIrregularAdvisingController {

    private static final boolean DEAN_ADVISING_DORMANT = true;
    private static final String DORMANT_NOTICE =
        "Dean irregular advising is currently dormant and outside the registrar scope.";

    private final FinanceAdmissionService financeService;
    private final JdbcTemplate db;
    private final ApplicantPreRegSnapshotService preRegSnapshotService;

    public FacultyIrregularAdvisingController(FinanceAdmissionService financeService,
                                              JdbcTemplate db,
                                              ApplicantPreRegSnapshotService preRegSnapshotService) {
        this.financeService = financeService;
        this.db = db;
        this.preRegSnapshotService = preRegSnapshotService;
    }

    @GetMapping("/irregular-advising")
    public String advisingPage(@RequestParam(required = false) String refNo,
                               Model model,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        if (!isAdviserUser(session)) return "redirect:/login";
        if (DEAN_ADVISING_DORMANT) {
            redirectAttributes.addFlashAttribute("message", DORMANT_NOTICE);
            return "redirect:/grades";
        }

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
                    Map<String, Object> workspace = preRegSnapshotService.buildWorkspace(app);
                    model.addAttribute("preRegSnapshot", workspace.get("snapshot"));
                    model.addAttribute("preRegLines", workspace.get("lines"));
                    model.addAttribute("preRegCreditLines", workspace.get("credits"));
                    model.addAttribute("preRegLineCount", workspace.get("lineCount"));
                    model.addAttribute("preRegCreditCount", workspace.get("creditCount"));
                    model.addAttribute("preRegReady", workspace.get("ready"));
                    model.addAttribute("preRegFinalized", workspace.get("finalized"));
                    model.addAttribute("preRegCourseOptions", workspace.get("courseOptions"));
                    model.addAttribute("preRegSectionOptions", workspace.get("sectionOptions"));
                }
            } else {
                model.addAttribute("message", "Applicant not found or already processed.");
            }
        }

        return "faculty_irregular_advising";
    }

    @PostMapping("/pre-reg/save")
    public String savePreRegSnapshot(@RequestParam String refNo,
                                     @RequestParam String programCode,
                                     @RequestParam(required = false) Integer yearLevel,
                                     @RequestParam(required = false) Integer semesterNumber,
                                     @RequestParam(required = false) Double tuitionAmount,
                                     @RequestParam(required = false) Double miscAmount,
                                     @RequestParam(required = false) Double assessmentAmount,
                                     @RequestParam(required = false) String notes,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        if (!isAdviserUser(session)) return "redirect:/login";
        if (DEAN_ADVISING_DORMANT) {
            redirectAttributes.addFlashAttribute("message", DORMANT_NOTICE);
            return "redirect:/grades";
        }
        String message = preRegSnapshotService.upsertSnapshotHeader(
            refNo, programCode, yearLevel, semesterNumber, tuitionAmount, miscAmount, assessmentAmount, notes, currentUsername(session));
        if (message.startsWith("Irregular pre-registration header saved")) {
            redirectAttributes.addFlashAttribute("successMessage", message);
        } else {
            redirectAttributes.addFlashAttribute("message", message);
        }
        return "redirect:/faculty/irregular-advising?refNo=" + refNo;
    }

    @PostMapping("/pre-reg/add-credit")
    public String addTorCredit(@RequestParam String refNo,
                               @RequestParam(required = false) Integer courseId,
                               @RequestParam(required = false) String sourceSchool,
                               @RequestParam(required = false) String sourceCourseCode,
                               @RequestParam(required = false) String sourceCourseTitle,
                               @RequestParam(required = false) Double creditedUnits,
                               @RequestParam(required = false) String remarks,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        if (!isAdviserUser(session)) return "redirect:/login";
        if (DEAN_ADVISING_DORMANT) {
            redirectAttributes.addFlashAttribute("message", DORMANT_NOTICE);
            return "redirect:/grades";
        }
        String message = preRegSnapshotService.addCreditLine(
            refNo, courseId, sourceSchool, sourceCourseCode, sourceCourseTitle, creditedUnits, remarks, currentUsername(session));
        if (message.startsWith("TOR credit line added")) {
            redirectAttributes.addFlashAttribute("successMessage", message);
        } else {
            redirectAttributes.addFlashAttribute("message", message);
        }
        return "redirect:/faculty/irregular-advising?refNo=" + refNo;
    }

    @PostMapping("/pre-reg/remove-credit")
    public String removeTorCredit(@RequestParam String refNo,
                                  @RequestParam Long creditId,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        if (!isAdviserUser(session)) return "redirect:/login";
        if (DEAN_ADVISING_DORMANT) {
            redirectAttributes.addFlashAttribute("message", DORMANT_NOTICE);
            return "redirect:/grades";
        }
        String message = preRegSnapshotService.removeCreditLine(refNo, creditId);
        if (message.startsWith("TOR credit line removed")) {
            redirectAttributes.addFlashAttribute("successMessage", message);
        } else {
            redirectAttributes.addFlashAttribute("message", message);
        }
        return "redirect:/faculty/irregular-advising?refNo=" + refNo;
    }

    @PostMapping("/pre-reg/add-subject")
    public String addPreRegSubject(@RequestParam String refNo,
                                   @RequestParam(required = false) Integer courseId,
                                   @RequestParam(required = false) Integer sectionId,
                                   @RequestParam(required = false) String sectionCode,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        if (!isAdviserUser(session)) return "redirect:/login";
        if (DEAN_ADVISING_DORMANT) {
            redirectAttributes.addFlashAttribute("message", DORMANT_NOTICE);
            return "redirect:/grades";
        }
        String message = preRegSnapshotService.addSubjectLine(refNo, courseId, sectionId, sectionCode);
        if (message.startsWith("Subject added")) {
            redirectAttributes.addFlashAttribute("successMessage", message);
        } else {
            redirectAttributes.addFlashAttribute("message", message);
        }
        return "redirect:/faculty/irregular-advising?refNo=" + refNo;
    }

    @PostMapping("/pre-reg/remove-subject")
    public String removePreRegSubject(@RequestParam String refNo,
                                      @RequestParam Long lineId,
                                      HttpSession session,
                                      RedirectAttributes redirectAttributes) {
        if (!isAdviserUser(session)) return "redirect:/login";
        if (DEAN_ADVISING_DORMANT) {
            redirectAttributes.addFlashAttribute("message", DORMANT_NOTICE);
            return "redirect:/grades";
        }
        String message = preRegSnapshotService.removeSubjectLine(refNo, lineId);
        if (message.startsWith("Subject removed")) {
            redirectAttributes.addFlashAttribute("successMessage", message);
        } else {
            redirectAttributes.addFlashAttribute("message", message);
        }
        return "redirect:/faculty/irregular-advising?refNo=" + refNo;
    }

    @PostMapping("/pre-reg/finalize")
    public String finalizePreRegSnapshot(@RequestParam String refNo,
                                         HttpSession session,
                                         RedirectAttributes redirectAttributes) {
        if (!isAdviserUser(session)) return "redirect:/login";
        if (DEAN_ADVISING_DORMANT) {
            redirectAttributes.addFlashAttribute("message", DORMANT_NOTICE);
            return "redirect:/grades";
        }
        String message = preRegSnapshotService.finalizeSnapshot(refNo, currentUsername(session));
        if (message.startsWith("Irregular pre-registration finalized")) {
            redirectAttributes.addFlashAttribute("successMessage", message);
        } else {
            redirectAttributes.addFlashAttribute("message", message);
        }
        return "redirect:/faculty/irregular-advising?refNo=" + refNo;
    }

    @PostMapping("/pre-reg/reopen")
    public String reopenPreRegSnapshot(@RequestParam String refNo,
                                       HttpSession session,
                                       RedirectAttributes redirectAttributes) {
        if (!isAdviserUser(session)) return "redirect:/login";
        if (DEAN_ADVISING_DORMANT) {
            redirectAttributes.addFlashAttribute("message", DORMANT_NOTICE);
            return "redirect:/grades";
        }
        String message = preRegSnapshotService.reopenSnapshot(refNo, currentUsername(session));
        if (message.startsWith("Irregular pre-registration reopened")) {
            redirectAttributes.addFlashAttribute("successMessage", message);
        } else {
            redirectAttributes.addFlashAttribute("message", message);
        }
        return "redirect:/faculty/irregular-advising?refNo=" + refNo;
    }

    @GetMapping("/pre-reg/{refNo}/snapshot")
    @ResponseBody
    public Map<String, Object> viewPreRegSnapshot(@PathVariable String refNo, HttpSession session) {
        if (!isAdviserUser(session)) {
            return Map.of("error", "LOGIN_REQUIRED");
        }
        if (DEAN_ADVISING_DORMANT) {
            return Map.of("error", "DEAN_ADVISING_DORMANT", "message", DORMANT_NOTICE);
        }
        return preRegSnapshotService.findSnapshotByReference(refNo);
    }

    private boolean isAdviserUser(HttpSession session) {
        Object raw = session.getAttribute("currentUser");
        if (!(raw instanceof Map<?, ?> user)) {
            return false;
        }
        Object role = user.get("role");
        if (role == null) {
            return false;
        }
        String normalized = role.toString().trim();
        return "Dean".equalsIgnoreCase(normalized);
    }

    private String currentUsername(HttpSession session) {
        Object raw = session.getAttribute("currentUser");
        if (raw instanceof Map<?, ?> user && user.get("username") != null) {
            return user.get("username").toString();
        }
        return "dean";
    }
}
