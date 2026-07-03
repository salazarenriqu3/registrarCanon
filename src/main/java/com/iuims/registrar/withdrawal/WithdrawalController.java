package com.iuims.registrar.withdrawal;

import com.iuims.registrar.core.StudentIdentityReleaseService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
public class WithdrawalController {

    private final WithdrawalService withdrawalService;
    private final StudentIdentityReleaseService studentIdentityReleaseService;

    public WithdrawalController(WithdrawalService withdrawalService,
                                StudentIdentityReleaseService studentIdentityReleaseService) {
        this.withdrawalService = withdrawalService;
        this.studentIdentityReleaseService = studentIdentityReleaseService;
    }

    @GetMapping("/admin/withdrawals")
    public String registrarQueue(HttpSession session, Model model) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        model.addAttribute("queueMode", "REPORT");
        model.addAttribute("pageTitle", "Withdrawal History");
        model.addAttribute("pageSubtitle", "Completed registrar withdrawals, rejected legacy cases, and archived audit lines.");
        model.addAttribute("requests", withdrawalService.listRequests(null));
        model.addAttribute("statusCounts", withdrawalService.statusCounts());
        model.addAttribute("reasonSummary", withdrawalService.reasonSummary());
        model.addAttribute("timingSummary", withdrawalService.timingSummary());
        return "withdrawal_queue";
    }

    @GetMapping("/admin/withdrawals/report")
    public String registrarReport(HttpSession session, Model model) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        model.addAttribute("queueMode", "REPORT");
        model.addAttribute("pageTitle", "Withdrawal History");
        model.addAttribute("pageSubtitle", "Completed registrar withdrawals, rejected legacy cases, and archived audit lines.");
        model.addAttribute("requests", withdrawalService.listRequests(null));
        model.addAttribute("statusCounts", withdrawalService.statusCounts());
        model.addAttribute("reasonSummary", withdrawalService.reasonSummary());
        model.addAttribute("timingSummary", withdrawalService.timingSummary());
        return "withdrawal_queue";
    }

    @PostMapping("/admin/withdrawals/drop-subject")
    public String dropSubject(@RequestParam String studentNumber,
                              @RequestParam Integer scheduleId,
                              @RequestParam(required = false) String remarks,
                              HttpSession session,
                              RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            WithdrawalService.DirectDropResult result = withdrawalService.dropSubjectByRegistrar(
                studentNumber, scheduleId, remarks, currentUsername(session));
            ra.addFlashAttribute("successMessage",
                String.format("Subject drop completed. Request #%d archived. %d subject(s) processed. Applied charge: PHP %,.2f.",
                    result.requestId(), result.subjectsDropped(), result.totalCharge()));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Subject drop failed: " + e.getMessage());
        }
        ra.addAttribute("username", studentNumber != null ? studentNumber.trim() : "");
        return "redirect:/admin/student-manager";
    }

    @PostMapping("/admin/withdrawals/drop-student")
    public String dropStudent(@RequestParam String studentNumber,
                              @RequestParam String reasonCode,
                              @RequestParam(required = false) String remarks,
                              HttpSession session,
                              RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            WithdrawalService.DirectDropResult result = withdrawalService.dropStudentByRegistrar(
                studentNumber, reasonCode, remarks, currentUsername(session));
            ra.addFlashAttribute("successMessage",
                String.format("Student withdrawal completed. Request #%d archived. %d subject(s) processed. Applied charge: PHP %,.2f.",
                    result.requestId(), result.subjectsDropped(), result.totalCharge()));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Student withdrawal failed: " + e.getMessage());
        }
        ra.addAttribute("username", studentNumber != null ? studentNumber.trim() : "");
        return "redirect:/admin/student-manager";
    }

    @PostMapping("/admin/withdrawals/clear-load-for-shift")
    public String clearLoadForShift(@RequestParam String studentNumber,
                                    @RequestParam String reasonCode,
                                    @RequestParam(required = false) String remarks,
                                    HttpSession session,
                                    RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            WithdrawalService.DirectDropResult result = withdrawalService.clearCurrentTermLoadForProgramShift(
                studentNumber, reasonCode, remarks, currentUsername(session));
            ra.addFlashAttribute("successMessage",
                String.format("Subject load cleared for shifting. Request #%d archived. %d subject(s) processed. Student profile remains active/enrolled. Applied charge: PHP %,.2f.",
                    result.requestId(), result.subjectsDropped(), result.totalCharge()));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Shift load cleanup failed: " + e.getMessage());
        }
        ra.addAttribute("username", studentNumber != null ? studentNumber.trim() : "");
        return "redirect:/admin/student-manager";
    }

    @PostMapping("/admin/withdrawals/release-student-number")
    public String releaseStudentNumber(@RequestParam String studentNumber,
                                       @RequestParam(required = false) String remarks,
                                       HttpSession session,
                                       RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        StudentIdentityReleaseService.ReleaseResult result =
            studentIdentityReleaseService.releaseWithdrawnStudentNumber(
                studentNumber, currentUsername(session), remarks);
        if (result.ok()) {
            ra.addFlashAttribute("successMessage", result.message());
            ra.addAttribute("username", result.archiveKey() != null ? result.archiveKey() : studentNumber.trim());
        } else {
            ra.addFlashAttribute("errorMessage", result.message());
            ra.addAttribute("username", studentNumber != null ? studentNumber.trim() : "");
        }
        return "redirect:/admin/student-manager";
    }

    private String currentUsername(HttpSession session) {
        Object raw = session.getAttribute("currentUser");
        if (raw instanceof Map<?, ?> user && user.get("username") != null) {
            return user.get("username").toString();
        }
        return "registrar";
    }
}
