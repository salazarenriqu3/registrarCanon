package com.iuims.registrar.finance;

import com.iuims.registrar.core.YearLevelLoadPolicyService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/finance-policy")
public class FinancePolicyController {

    private final FinancePolicyService financePolicyService;
    private final YearLevelLoadPolicyService yearLevelLoadPolicyService;

    public FinancePolicyController(FinancePolicyService financePolicyService,
                                   YearLevelLoadPolicyService yearLevelLoadPolicyService) {
        this.financePolicyService = financePolicyService;
        this.yearLevelLoadPolicyService = yearLevelLoadPolicyService;
    }

    @GetMapping
    public String show(@RequestParam(required = false) Integer installmentTermId,
                       HttpSession session,
                       Model model) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        Map<String, Object> view = financePolicyService.buildPolicyView(installmentTermId);
        model.addAllAttributes(view);
        model.addAttribute("yearLevelLoadPolicies", yearLevelLoadPolicyService.listPolicies());
        return "admin_finance_policy";
    }

    @PostMapping("/save-year-level-loads")
    public String saveYearLevelLoads(@RequestParam Map<String, String> params,
                                     @RequestParam(required = false) Integer installmentTermId,
                                     HttpSession session,
                                     RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            yearLevelLoadPolicyService.savePolicies(params);
            ra.addFlashAttribute("successMessage", "Year-level unit load policy saved.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirect(installmentTermId);
    }

    @PostMapping("/save-gates")
    public String saveGates(@RequestParam Map<String, String> params,
                            @RequestParam(required = false) Integer installmentTermId,
                            HttpSession session,
                            RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        financePolicyService.savePaymentGates(params);
        ra.addFlashAttribute("successMessage", "Payment gates saved.");
        return redirect(installmentTermId);
    }

    @PostMapping("/save-rules")
    public String saveRules(@RequestParam Map<String, String> params,
                           @RequestParam(required = false) Integer installmentTermId,
                           HttpSession session,
                           RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        financePolicyService.saveEnrollmentRules(params);
        ra.addFlashAttribute("successMessage", "Enrollment rules saved.");
        return redirect(installmentTermId);
    }

    @PostMapping("/save-installments")
    public String saveInstallments(@RequestParam(required = false) Integer installmentTermId,
                                   @RequestParam(required = false) List<Integer> instNumber,
                                   @RequestParam(required = false) List<Integer> instDueMonths,
                                   @RequestParam(required = false) List<String> instLabel,
                                   HttpSession session,
                                   RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        List<FinancePolicyService.InstallmentRow> rows =
            FinancePolicyService.parseInstallmentRows(instNumber, instDueMonths, instLabel);
        int saved = financePolicyService.saveInstallmentPlan(installmentTermId, rows);
        ra.addFlashAttribute("successMessage",
            (installmentTermId == null ? "Default" : "Term") + " installment plan saved (" + saved + " row(s)).");
        return redirect(installmentTermId);
    }

    @PostMapping("/copy-installments")
    public String copyInstallments(@RequestParam String copyMode,
                                   @RequestParam Integer targetTermId,
                                   @RequestParam(required = false) Integer sourceTermId,
                                   HttpSession session,
                                   RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        Integer source = "default".equals(copyMode) ? null : sourceTermId;
        if ("previous".equals(copyMode)) {
            source = financePolicyService.resolvePreviousTermId(targetTermId);
        }
        int copied = financePolicyService.copyInstallmentPlan(source, targetTermId);
        if (copied == 0) {
            ra.addFlashAttribute("errorMessage", "No installment rows to copy from the selected source.");
        } else {
            ra.addFlashAttribute("successMessage", "Copied " + copied + " installment row(s) into the selected term.");
        }
        return redirect(targetTermId);
    }

    private String redirect(Integer installmentTermId) {
        return installmentTermId != null
            ? "redirect:/admin/finance-policy?installmentTermId=" + installmentTermId
            : "redirect:/admin/finance-policy";
    }
}
