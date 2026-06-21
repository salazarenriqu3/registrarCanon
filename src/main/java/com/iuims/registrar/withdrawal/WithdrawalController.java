package com.iuims.registrar.withdrawal;

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

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @GetMapping("/admin/withdrawals")
    public String registrarQueue(HttpSession session, Model model) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        model.addAttribute("queueMode", "REGISTRAR");
        model.addAttribute("pageTitle", "Registrar Withdrawal Queue");
        model.addAttribute("pageSubtitle", "Class and full-student withdrawal requests waiting for Registrar approval.");
        model.addAttribute("requests", withdrawalService.listRequests(WithdrawalService.STATUS_PENDING_REGISTRAR));
        model.addAttribute("statusCounts", withdrawalService.statusCounts());
        return "withdrawal_queue";
    }

    @GetMapping("/admin/withdrawals/report")
    public String registrarReport(HttpSession session, Model model) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        model.addAttribute("queueMode", "REPORT");
        model.addAttribute("pageTitle", "Withdrawal History");
        model.addAttribute("pageSubtitle", "Archived withdrawal requests, decisions, and completed registrar actions.");
        model.addAttribute("requests", withdrawalService.listRequests(null));
        model.addAttribute("statusCounts", withdrawalService.statusCounts());
        model.addAttribute("reasonSummary", withdrawalService.reasonSummary());
        model.addAttribute("timingSummary", withdrawalService.timingSummary());
        return "withdrawal_queue";
    }

    @PostMapping("/admin/withdrawals/request")
    public String requestWithdrawal(@RequestParam String studentNumber,
                                    @RequestParam Integer scheduleId,
                                    @RequestParam String reasonCode,
                                    @RequestParam(required = false) String remarks,
                                    HttpSession session,
                                    RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        String username = studentNumber != null ? studentNumber.trim() : "";
        try {
            long requestId = withdrawalService.createRequest(
                username, scheduleId, reasonCode, remarks, currentUsername(session));
            ra.addFlashAttribute("successMessage",
                "Class withdrawal request #" + requestId + " submitted for Registrar approval.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Withdrawal request failed: " + e.getMessage());
        }
        ra.addAttribute("username", username);
        return "redirect:/admin/student-manager";
    }

    @PostMapping("/admin/withdrawals/request-student")
    public String requestFullStudentWithdrawal(@RequestParam String studentNumber,
                                               @RequestParam String reasonCode,
                                               @RequestParam(required = false) String remarks,
                                               HttpSession session,
                                               RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        String username = studentNumber != null ? studentNumber.trim() : "";
        try {
            long requestId = withdrawalService.createFullCurrentTermRequest(
                username, reasonCode, remarks, currentUsername(session));
            ra.addFlashAttribute("successMessage",
                "Full-student withdrawal request #" + requestId + " submitted for Registrar approval.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Student withdrawal request failed: " + e.getMessage());
        }
        ra.addAttribute("username", username);
        return "redirect:/admin/student-manager";
    }

    @PostMapping("/admin/student-manager/drop-subject")
    public String legacyDropSubjectRequest(@RequestParam String studentNumber,
                                           @RequestParam Integer scheduleId,
                                           @RequestParam String reasonCode,
                                           @RequestParam(required = false) String remarks,
                                           HttpSession session,
                                           RedirectAttributes ra) {
        return requestWithdrawal(studentNumber, scheduleId, reasonCode, remarks, session, ra);
    }

    @PostMapping("/admin/student-manager/drop-student")
    public String legacyDropStudentRequest(@RequestParam String studentNumber,
                                           @RequestParam String reasonCode,
                                           @RequestParam(required = false) String remarks,
                                           HttpSession session,
                                           RedirectAttributes ra) {
        return requestFullStudentWithdrawal(studentNumber, reasonCode, remarks, session, ra);
    }

    @PostMapping("/admin/withdrawals/approve")
    public String registrarApprove(@RequestParam long requestId,
                                   HttpSession session,
                                   RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            WithdrawalService.DirectDropResult result =
                withdrawalService.approveAndExecuteRequest(requestId, currentUsername(session));
            ra.addFlashAttribute("successMessage",
                String.format("Withdrawal request #%d completed. %d subject(s) processed. Applied charge: PHP %,.2f.",
                    result.requestId(), result.subjectsDropped(), result.totalCharge()));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Withdrawal approval failed: " + e.getMessage());
        }
        return "redirect:/admin/withdrawals";
    }

    @PostMapping("/admin/withdrawals/reject")
    public String reject(@RequestParam long requestId,
                         @RequestParam(required = false) String rejectionReason,
                         HttpSession session,
                         RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        ra.addFlashAttribute("successMessage",
            withdrawalService.reject(requestId, currentUsername(session), rejectionReason));
        return "redirect:/admin/withdrawals";
    }

    private String currentUsername(HttpSession session) {
        Object raw = session.getAttribute("currentUser");
        if (raw instanceof Map<?, ?> user && user.get("username") != null) {
            return user.get("username").toString();
        }
        return "registrar";
    }
}
