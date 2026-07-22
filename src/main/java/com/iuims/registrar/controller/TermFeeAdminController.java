package com.iuims.registrar.controller;
import com.iuims.registrar.service.finance.TermFeeAdminService;



import jakarta.servlet.http.HttpSession;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Controller;

import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.multipart.MultipartFile;



import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;

import java.util.List;

import java.util.Map;



@Controller

@RequestMapping("/admin/term-fees")

public class TermFeeAdminController {

    private static final boolean FEE_OWNERSHIP_READ_ONLY = true;

    private static final String FEE_OWNERSHIP_NOTICE =
        "Registrar reads canonical term fees from Enrollment3-owned shared data. Fee authoring stays outside Registrar.";



    @Autowired

    private TermFeeAdminService termFeeAdminService;



    @GetMapping

    public String show(@RequestParam(required = false) Integer termId,

                       @RequestParam(required = false) String programCode,

                       @RequestParam(defaultValue = "1") int yearLevel,

                       @RequestParam(defaultValue = "1") int semester,

                       HttpSession s,

                       Model m) {

        if (s.getAttribute("currentUser") == null) return "redirect:/login";



        List<String> missingTables = termFeeAdminService.validateTablesExist();

        m.addAttribute("missingTables", missingTables);

        m.addAttribute("terms", termFeeAdminService.listTermsAscending());

        m.addAttribute("programs", termFeeAdminService.listPrograms());



        Integer activeTermId = termFeeAdminService.getActiveTermId();

        Integer effectiveTermId = termId != null ? termId : activeTermId;

        m.addAttribute("activeTermId", activeTermId);

        m.addAttribute("termId", effectiveTermId);

        m.addAttribute("yearLevel", yearLevel);

        m.addAttribute("semester", semester);

        m.addAttribute("feeOwnershipReadOnly", FEE_OWNERSHIP_READ_ONLY);

        m.addAttribute("feeOwnershipNotice", FEE_OWNERSHIP_NOTICE);

        m.addAttribute("termReadiness", termFeeAdminService.buildTermReadinessSummary(effectiveTermId));

        m.addAttribute("unresolvedScopes", termFeeAdminService.listFeeScopeReadiness(effectiveTermId, true, 40));



        Integer previousTermId = effectiveTermId != null

            ? termFeeAdminService.resolvePreviousTermId(effectiveTermId) : null;

        m.addAttribute("previousTermId", previousTermId);

        m.addAttribute("sourceTermReadiness",

            previousTermId != null ? termFeeAdminService.buildTermReadinessSummary(previousTermId) : Map.of());



        String effectiveProgram = (programCode != null && !programCode.isBlank())

            ? programCode.trim().toUpperCase()

            : (!termFeeAdminService.listPrograms().isEmpty()

                ? (String) termFeeAdminService.listPrograms().get(0).get("program_code") : null);

        m.addAttribute("programCode", effectiveProgram);



        Integer programId = effectiveProgram != null

            ? termFeeAdminService.resolveProgramId(effectiveProgram) : null;



        if (effectiveTermId != null && programId != null && missingTables.isEmpty()) {

            List<Map<String, Object>> feeTypes =

                termFeeAdminService.listFeeTypesForAdmin(programId, effectiveTermId, yearLevel, semester);

            Map<String, Double> currentRates =

                termFeeAdminService.getFeeRatesForScope(programId, effectiveTermId, yearLevel, semester);

            Map<String, String> rateSources =

                termFeeAdminService.getFeeRateSourcesForScope(programId, effectiveTermId, yearLevel, semester);

            m.addAttribute("feeTypes", feeTypes);

            m.addAttribute("currentRates", currentRates);

            m.addAttribute("rateSources", rateSources);

            m.addAttribute("missingFeeCodes", missingFeeCodes(feeTypes, currentRates));

            m.addAttribute("fallbackFeeCodes", fallbackFeeCodes(feeTypes, rateSources));

        } else {

            m.addAttribute("feeTypes", List.of());

            m.addAttribute("currentRates", Map.of());

            m.addAttribute("rateSources", Map.of());

            m.addAttribute("missingFeeCodes", List.of());

            m.addAttribute("fallbackFeeCodes", List.of());

        }



        return "admin_term_fees";

    }



    @PostMapping("/save")

    public String save(@RequestParam(required = false) Integer termId,

                       @RequestParam String programCode,

                       @RequestParam(defaultValue = "1") int yearLevel,

                       @RequestParam(defaultValue = "1") int semester,

                       @RequestParam Map<String, String> params,

                       HttpSession s) {

        if (s.getAttribute("currentUser") == null) return "redirect:/login";

        if (FEE_OWNERSHIP_READ_ONLY) return readOnlyRedirect(termId, programCode, yearLevel, semester);

        String prog = programCode != null ? programCode.trim().toUpperCase() : null;

        Integer programId = prog != null ? termFeeAdminService.resolveProgramId(prog) : null;

        Integer effectiveTermId = termId;

        if (programId == null) return "redirect:/admin/term-fees";



        int saved = 0, blank = 0, invalid = 0;

        for (Map.Entry<String, String> e : params.entrySet()) {

            if (!e.getKey().startsWith("rate_")) continue;

            String feeCode = e.getKey().substring("rate_".length()).trim().toUpperCase();

            String raw = e.getValue();

            if (raw == null || raw.isBlank()) { blank++; continue; }

            try {

                double amount = Double.parseDouble(raw.trim().replace(",", ""));

                if (termFeeAdminService.saveFeeRate(programId, effectiveTermId, yearLevel, semester, feeCode, amount)) {

                    saved++;

                } else {

                    invalid++;

                }

            } catch (Exception ignored) {

                invalid++;

            }

        }

        return redirectScope(termId, prog, yearLevel, semester)

            + "&success=true&saved=" + saved + "&blank=" + blank + "&invalid=" + invalid;

    }



    @PostMapping("/import-global")

    public String importGlobal(@RequestParam Integer sourceTermId,

                               @RequestParam Integer targetTermId,

                               @RequestParam(required = false) String programCode,

                               @RequestParam(defaultValue = "1") int yearLevel,

                               @RequestParam(defaultValue = "1") int semester,

                               HttpSession s) {

        if (s.getAttribute("currentUser") == null) return "redirect:/login";

        if (FEE_OWNERSHIP_READ_ONLY) return readOnlyRedirect(targetTermId, programCode, yearLevel, semester);

        if (sourceTermId == null || targetTermId == null) return "redirect:/admin/term-fees";



        TermFeeAdminService.FeeTemplateCopyResult result =

            termFeeAdminService.importFeesGlobal(sourceTermId, targetTermId);

        String prog = programCode != null ? programCode.trim().toUpperCase() : "";

        return redirectScope(targetTermId, prog, yearLevel, semester)

            + "&imported=true&importMode=global"

            + "&importSeeded=" + result.programsSeeded()

            + "&importCoreCreated=" + result.coreRowsCreated()

            + "&importCoreUpdated=" + result.coreRowsUpdated()

            + "&importSkipped=" + result.skippedNoSource();

    }



    @PostMapping("/import-scope")

    public String importScope(@RequestParam Integer sourceTermId,

                              @RequestParam Integer targetTermId,

                              @RequestParam String programCode,

                              @RequestParam(defaultValue = "1") int yearLevel,

                              @RequestParam(defaultValue = "1") int semester,

                              HttpSession s) {

        if (s.getAttribute("currentUser") == null) return "redirect:/login";

        if (FEE_OWNERSHIP_READ_ONLY) return readOnlyRedirect(targetTermId, programCode, yearLevel, semester);

        if (sourceTermId == null || targetTermId == null) return "redirect:/admin/term-fees";



        String prog = programCode.trim().toUpperCase();

        TermFeeAdminService.FeeTemplateCopyResult result =

            termFeeAdminService.importFeesScoped(sourceTermId, targetTermId, prog, yearLevel, semester);

        return redirectScope(targetTermId, prog, yearLevel, semester)

            + "&imported=true&importMode=scope"

            + "&importSeeded=" + result.programsSeeded()

            + "&importCoreCreated=" + result.coreRowsCreated()

            + "&importCoreUpdated=" + result.coreRowsUpdated()

            + "&importSkipped=" + result.skippedNoSource();

    }



    /** @deprecated Use import-global. Kept for bookmarked links. */

    @PostMapping("/import-from-term")

    public String importFromTermLegacy(@RequestParam Integer sourceTermId,

                                       @RequestParam(required = false) Integer targetTermId,

                                       @RequestParam(required = false) String programCode,

                                       @RequestParam(defaultValue = "1") int yearLevel,

                                       @RequestParam(defaultValue = "1") int semester,

                                       @RequestParam(required = false) String importScope,

                                       HttpSession s) {

        if (FEE_OWNERSHIP_READ_ONLY) return readOnlyRedirect(targetTermId, programCode, yearLevel, semester);

        if ("all".equals(importScope)) {

            return importGlobal(sourceTermId, targetTermId, programCode, yearLevel, semester, s);

        }

        return importScope(sourceTermId, targetTermId,

            programCode != null ? programCode : "", yearLevel, semester, s);

    }



    /** @deprecated Use import-global. */

    @PostMapping("/prepare")

    public String prepareLegacy(@RequestParam Integer termId,

                                @RequestParam(required = false) String programCode,

                                @RequestParam(defaultValue = "1") int yearLevel,

                                @RequestParam(defaultValue = "1") int semester,

                                HttpSession s) {

        if (FEE_OWNERSHIP_READ_ONLY) return readOnlyRedirect(termId, programCode, yearLevel, semester);

        Integer prev = termFeeAdminService.resolvePreviousTermId(termId);

        if (prev == null) return "redirect:/admin/term-fees?termId=" + termId + "&importCsvError=no-source-term";

        return importGlobal(prev, termId, programCode, yearLevel, semester, s);

    }



    /** @deprecated Use import-global. */

    @PostMapping("/copy-templates")

    public String copyTemplatesLegacy(@RequestParam Integer termId,

                                       @RequestParam(required = false) String programCode,

                                       @RequestParam(defaultValue = "1") int yearLevel,

                                       @RequestParam(defaultValue = "1") int semester,

                                       HttpSession s) {

        if (FEE_OWNERSHIP_READ_ONLY) return readOnlyRedirect(termId, programCode, yearLevel, semester);

        Integer prev = termFeeAdminService.resolvePreviousTermId(termId);

        if (prev == null) return "redirect:/admin/term-fees?termId=" + termId + "&importCsvError=no-source-term";

        return importGlobal(prev, termId, programCode, yearLevel, semester, s);

    }



    @GetMapping("/readiness-export")

    public void exportReadiness(@RequestParam Integer termId,

                                @RequestParam(defaultValue = "unresolved") String scope,

                                HttpSession s,

                                HttpServletResponse response) throws java.io.IOException {

        if (s.getAttribute("currentUser") == null) {

            response.sendRedirect("/login");

            return;

        }

        boolean unresolvedOnly = !"all".equalsIgnoreCase(scope);

        String csv = termFeeAdminService.exportFeeScopeReadinessCsv(termId, unresolvedOnly);

        writeCsvAttachment(response,

            "term-fee-gaps-term-" + termId + (unresolvedOnly ? "-unresolved" : "-all") + ".csv", csv);

    }



    @GetMapping("/import-template")

    public void exportImportTemplate(@RequestParam Integer termId,

                                     @RequestParam(defaultValue = "unresolved") String scope,

                                     HttpSession s,

                                     HttpServletResponse response) throws java.io.IOException {

        if (s.getAttribute("currentUser") == null) {

            response.sendRedirect("/login");

            return;

        }

        boolean unresolvedOnly = !"all".equalsIgnoreCase(scope);

        String csv = termFeeAdminService.exportFeeImportTemplateCsv(termId, unresolvedOnly);

        writeCsvAttachment(response,

            "term-fee-import-template-term-" + termId + (unresolvedOnly ? "-unresolved" : "-all") + ".csv", csv);

    }



    @PostMapping("/import-template")

    public String importTemplate(@RequestParam Integer termId,

                                 @RequestParam(required = false) String programCode,

                                 @RequestParam(defaultValue = "1") int yearLevel,

                                 @RequestParam(defaultValue = "1") int semester,

                                 @RequestParam("file") MultipartFile file,

                                 HttpSession s) {

        if (s.getAttribute("currentUser") == null) return "redirect:/login";

        if (FEE_OWNERSHIP_READ_ONLY) return readOnlyRedirect(termId, programCode, yearLevel, semester);

        if (termId == null || file == null || file.isEmpty()) {

            return "redirect:/admin/term-fees?termId=" + termId + "&importCsvError=empty";

        }

        try {

            String csv = new String(file.getBytes(), StandardCharsets.UTF_8);

            TermFeeAdminService.FeeCsvImportResult result =

                termFeeAdminService.importFeeTemplateCsv(termId, csv);

            String prog = programCode != null ? programCode.trim().toUpperCase() : "";

            String firstError = result.errorExamples().isEmpty()

                ? "" : URLEncoder.encode(result.errorExamples().get(0), StandardCharsets.UTF_8);

            return redirectScope(termId, prog, yearLevel, semester)

                + "&importCsv=true"

                + "&csvRowsChecked=" + result.rowsChecked()

                + "&csvRowsImported=" + result.rowsImported()

                + "&csvRowsCreated=" + result.rowsCreated()

                + "&csvRowsUpdated=" + result.rowsUpdated()

                + "&csvFeeValuesApplied=" + result.feeValuesApplied()

                + "&csvRowsSkipped=" + result.rowsSkipped()

                + (firstError.isBlank() ? "" : "&csvFirstError=" + firstError);

        } catch (Exception e) {

            String msg = URLEncoder.encode(e.getMessage() != null ? e.getMessage() : "CSV import failed", StandardCharsets.UTF_8);

            return "redirect:/admin/term-fees?termId=" + termId + "&importCsvError=" + msg;

        }

    }



    private static String redirectScope(Integer termId, String programCode, int yearLevel, int semester) {

        String url = "redirect:/admin/term-fees?";

        if (termId != null) url += "termId=" + termId;

        if (programCode != null && !programCode.isBlank()) {

            url += (termId != null ? "&" : "") + "programCode=" + programCode;

        }

        url += "&yearLevel=" + yearLevel + "&semester=" + semester;

        return url;

    }



    private static String readOnlyRedirect(Integer termId, String programCode, int yearLevel, int semester) {

        StringBuilder url = new StringBuilder("redirect:/admin/term-fees?ownershipReadOnly=1");

        if (termId != null) {
            url.append("&termId=").append(termId);
        }

        if (programCode != null && !programCode.isBlank()) {
            url.append("&programCode=").append(URLEncoder.encode(programCode.trim(), StandardCharsets.UTF_8));
        }

        url.append("&yearLevel=").append(yearLevel);
        url.append("&semester=").append(semester);
        url.append("&importCsvError=").append(URLEncoder.encode(FEE_OWNERSHIP_NOTICE, StandardCharsets.UTF_8));

        return url.toString();

    }



    private static void writeCsvAttachment(HttpServletResponse response, String filename, String csv)

            throws java.io.IOException {

        response.setContentType("text/csv");

        response.setCharacterEncoding("UTF-8");

        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        response.getWriter().write(csv);

        response.getWriter().flush();

    }



    private List<String> missingFeeCodes(List<Map<String, Object>> feeTypes, Map<String, Double> currentRates) {

        java.util.ArrayList<String> missing = new java.util.ArrayList<>();

        for (Map<String, Object> feeType : feeTypes) {

            Object codeObj = feeType.get("fee_code");

            if (codeObj == null) continue;

            if (!currentRates.containsKey(codeObj.toString())) missing.add(codeObj.toString());

        }

        return missing;

    }



    private List<String> fallbackFeeCodes(List<Map<String, Object>> feeTypes, Map<String, String> rateSources) {

        java.util.ArrayList<String> fallback = new java.util.ArrayList<>();

        for (Map<String, Object> feeType : feeTypes) {

            Object codeObj = feeType.get("fee_code");

            if (codeObj == null) continue;

            if ("FALLBACK".equals(rateSources.get(codeObj.toString()))) fallback.add(codeObj.toString());

        }

        return fallback;

    }

}


