package com.iuims.registrar.finance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TermFeeAdminService {

    private static final String CODE_TUITION = "TUITION_PER_UNIT";
    private static final String CODE_RLE = "RLE_RATE_PER_HOUR";

    // Known static fee types in the new unified model
    private static final List<String> KNOWN_FEES = Arrays.asList(
        "TUITION_PER_UNIT", "LEC_FEE_PER_UNIT", "LAB_FEE_PER_UNIT", "COMP_FEE_PER_UNIT", "RLE_FEE_PER_UNIT",
        "MISC_REGISTRATION", "MISC_LIBRARY", "MISC_MEDICAL", "MISC_ID", "MISC_ATHLETIC", "MISC_GUIDANCE", "MISC_LMS", "MISC_INS", "MISC_CULT", "MISC_AV", "MISC_ENERGY",
        "OTHER_LATE_ENROLLMENT", "OTHER_ADD_DROP", "OTHER_INSTALLMENT", "OTHER_ID", "OTHER_INS", "OTHER_COMP", "OTHER_DEV"
    );

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private ProgramFeeSettingRepository feeSettingRepository;

    public List<Map<String, Object>> listTermsAscending() {
        try {
            return db.queryForList(
                "SELECT term_id, term_code, term_name, academic_year, semester_number, status, is_active " +
                    "FROM academic_terms ORDER BY term_id ASC");
        } catch (Exception e) {
            return List.of();
        }
    }

    public Integer getActiveTermId() {
        try {
            return db.queryForObject(
                "SELECT term_id FROM academic_terms WHERE is_active = 1 OR UPPER(COALESCE(status,'')) = 'ACTIVE' " +
                    "ORDER BY term_id DESC LIMIT 1",
                Integer.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Prior calendar term in the same database ordering (e.g. term 2 → term 1). */
    public Integer resolvePreviousTermId(Integer targetTermId) {
        if (targetTermId == null) return null;
        try {
            return db.queryForObject(
                "SELECT term_id FROM academic_terms WHERE term_id < ? ORDER BY term_id DESC LIMIT 1",
                Integer.class, targetTermId);
        } catch (Exception e) {
            return null;
        }
    }

    /** Global import: all active programs × Y1–Y4 × curriculum slots S1/S2. */
    @Transactional
    public FeeTemplateCopyResult importFeesGlobal(Integer sourceTermId, Integer targetTermId) {
        if (sourceTermId == null || targetTermId == null) {
            return new FeeTemplateCopyResult(0, 0, 0, 0, 0, 0);
        }
        return importFeesFromSpecificTerm(sourceTermId, targetTermId, null, null, null);
    }

    /** Scoped import: one program + year level + curriculum slot. */
    @Transactional
    public FeeTemplateCopyResult importFeesScoped(Integer sourceTermId, Integer targetTermId,
                                                  String programCode, int yearLevel, int semester) {
        Integer programId = resolveProgramId(programCode);
        if (sourceTermId == null || targetTermId == null || programId == null) {
            return new FeeTemplateCopyResult(0, 0, 0, 0, 0, 0);
        }
        return importFeesFromSpecificTerm(sourceTermId, targetTermId, programId, yearLevel, semester);
    }

    public List<Map<String, Object>> listPrograms() {
        try {
            return db.queryForList(
                "SELECT program_code, program_name FROM programs WHERE COALESCE(active_status, 1) = 1 ORDER BY program_code");
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<String> validateTablesExist() {
        List<String> missing = new ArrayList<>();
        if (!tableExists("program_fee_settings")) missing.add("program_fee_settings");
        if (!tableExists("programs")) missing.add("programs");
        if (!tableExists("curriculum_templates")) missing.add("curriculum_templates");
        return missing;
    }

    public Integer resolveProgramId(String programCode) {
        if (programCode == null || programCode.isBlank()) return null;
        try {
            return db.queryForObject(
                "SELECT program_id FROM programs WHERE program_code = ? LIMIT 1",
                Integer.class, programCode.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    public Integer resolveTermIdFromTermCode(String termCode) {
        if (termCode == null || termCode.isBlank()) return null;
        String dbTermCode = termCode.trim();
        // New SL format: SL[AYstart4][AYend4][YL][Sem] → convert to DB code
        if (dbTermCode.startsWith("SL") && !dbTermCode.startsWith("SL_") && dbTermCode.length() >= 12) {
            char sem = dbTermCode.charAt(11);
            dbTermCode = sem + "1" + dbTermCode.substring(2, 10);
        } else if (dbTermCode.startsWith("SL_")) {
            // Legacy SL_ format
            dbTermCode = dbTermCode.substring(3);
        }
        try {
            return db.queryForObject(
                "SELECT term_id FROM academic_terms WHERE term_code = ? ORDER BY term_id DESC LIMIT 1",
                Integer.class, dbTermCode);
        } catch (Exception ignored) {
        }
        try {
            if (dbTermCode.length() >= 10) {
                int sem = Character.getNumericValue(dbTermCode.charAt(0));
                String ay = dbTermCode.substring(2, 6) + "-" + dbTermCode.substring(6, 10);
                return db.queryForObject(
                    "SELECT term_id FROM academic_terms WHERE academic_year = ? AND semester_number = ? ORDER BY term_id DESC LIMIT 1",
                    Integer.class, ay, sem);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public List<Map<String, Object>> listFeeTypesForAdmin(int programId, Integer termId, int yearLevel, int semester) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(feeRow(CODE_TUITION, "Tuition per unit", "RATE", "PER_UNIT"));
        rows.add(feeRow("LEC_FEE_PER_UNIT", "Lecture fee per unit", "RATE", "PER_UNIT"));
        rows.add(feeRow("LAB_FEE_PER_UNIT", "Lab fee per unit", "RATE", "PER_UNIT"));
        rows.add(feeRow("COMP_FEE_PER_UNIT", "Computer fee per unit", "RATE", "PER_UNIT"));
        rows.add(feeRow(CODE_RLE, "RLE rate per hour (BSN)", "RATE", "PER_HOUR"));
        
        rows.add(feeRow("MISC_REGISTRATION", "Registration Fee", "MISC", "FLAT"));
        rows.add(feeRow("MISC_LIBRARY", "Library Fee", "MISC", "FLAT"));
        rows.add(feeRow("MISC_MEDICAL", "Medical/Dental Fee", "MISC", "FLAT"));
        rows.add(feeRow("MISC_ID", "ID Fee", "MISC", "FLAT"));
        rows.add(feeRow("MISC_ATHLETIC", "Athletic Fee", "MISC", "FLAT"));
        rows.add(feeRow("MISC_GUIDANCE", "Guidance & Counselling", "MISC", "FLAT"));
        rows.add(feeRow("MISC_LMS", "Learning Management System", "MISC", "FLAT"));
        rows.add(feeRow("MISC_INS", "Student Accident Insurance", "MISC", "FLAT"));
        rows.add(feeRow("MISC_CULT", "Cultural Fee", "MISC", "FLAT"));
        rows.add(feeRow("MISC_AV", "Multi-media / Audio Visual", "MISC", "FLAT"));
        rows.add(feeRow("MISC_ENERGY", "Energy Fee", "MISC", "FLAT"));
        
        rows.add(feeRow("OTHER_LATE_ENROLLMENT", "Late Enrollment Fee", "OTHER", "FLAT"));
        rows.add(feeRow("OTHER_ADD_DROP", "Enrollment Change Fee", "OTHER", "FLAT"));
        rows.add(feeRow("OTHER_INSTALLMENT", "Installment Charge", "OTHER", "FLAT"));
        rows.add(feeRow("OTHER_ID", "Identification Card", "OTHER", "FLAT"));
        rows.add(feeRow("OTHER_INS", "Insurance", "OTHER", "FLAT"));
        rows.add(feeRow("OTHER_COMP", "Computer Hands-On", "OTHER", "FLAT"));
        rows.add(feeRow("OTHER_DEV", "Developmental Fees", "OTHER", "FLAT"));
        return rows;
    }

    public Map<String, Double> getFeeRatesForScope(int programId, Integer termId, int yearLevel, int semester) {
        Map<String, Double> map = new LinkedHashMap<>();
        Optional<ProgramFeeSetting> settingOpt = feeSettingRepository.findBestMatch(programId, yearLevel, semester, termId);
        
        if (settingOpt.isPresent()) {
            ProgramFeeSetting setting = settingOpt.get();
            for (String code : KNOWN_FEES) {
                double val = setting.getFee(code);
                if (val > 0) {
                    map.put(code, val);
                }
            }
        }
        return map;
    }

    public Map<String, String> getFeeRateSourcesForScope(int programId, Integer termId, int yearLevel, int semester) {
        Map<String, String> map = new LinkedHashMap<>();
        Optional<ProgramFeeSetting> settingOpt = feeSettingRepository.findBestMatch(programId, yearLevel, semester, termId);
        
        if (settingOpt.isPresent()) {
            ProgramFeeSetting setting = settingOpt.get();
            String source = (setting.getTermId() != null && setting.getTermId().equals(termId)) ? "EXACT_TERM" : "FALLBACK";
            for (String code : KNOWN_FEES) {
                double val = setting.getFee(code);
                if (val > 0) {
                    map.put(code, source);
                }
            }
        }
        return map;
    }

    @Transactional
    public boolean saveFeeRate(int programId, Integer termId, int yearLevel, int semester,
                               String feeCode, double amount) {
        if (feeCode == null || feeCode.isBlank()) return false;
        String code = feeCode.trim().toUpperCase();

        ProgramFeeSetting setting;
        Optional<ProgramFeeSetting> existingOpt = feeSettingRepository.findActiveForScopeOrFallback(programId, yearLevel, semester, termId);
        
        if (existingOpt.isPresent() && Objects.equals(existingOpt.get().getTermId(), termId)) {
            // Found exact match
            setting = existingOpt.get();
        } else {
            // Need to create a new row for this specific term
            setting = new ProgramFeeSetting();
            setting.setProgramId(programId);
            setting.setTermId(termId);
            setting.setYearLevel(yearLevel);
            setting.setSemesterNumber(semester);
            
            // If fallback exists, copy its values
            if (existingOpt.isPresent()) {
                ProgramFeeSetting fallback = existingOpt.get();
                for (String k : KNOWN_FEES) {
                    setting.setFee(k, fallback.getFee(k));
                }
            }
        }
        
        // Ensure standard tuition carries over to lec if not explicitly handled
        if (CODE_TUITION.equals(code) && setting.getFee("LEC_FEE_PER_UNIT") == 0) {
            setting.setFee("LEC_FEE_PER_UNIT", amount);
        }

        setting.setFee(code, amount);
        feeSettingRepository.saveAndFlush(setting);
        return true;
    }

    @Transactional
    public boolean addMiscOrOtherFee(int programId, Integer termId, int yearLevel, int semester,
                                     String feeCode, String feeName) {
        // In the wide-table model, new dynamic columns require a schema update.
        // We only support saving to existing known columns.
        if (!KNOWN_FEES.contains(feeCode.trim().toUpperCase())) {
            return false;
        }
        return saveFeeRate(programId, termId, yearLevel, semester, feeCode, 0.0);
    }

    public boolean isSupportedChargeCode(String feeCode) {
        if (feeCode == null) return false;
        return KNOWN_FEES.contains(feeCode.trim().toUpperCase());
    }

    @Transactional
    public TermFeePreparationResult prepareTermFees(Integer termId) {
        if (termId == null || !validateTablesExist().isEmpty()) {
            return new TermFeePreparationResult(0, 0, 0, 0, 0, 0);
        }

        int programsChecked = 0;
        int scopesChecked = 0;
        int coreRowsCreated = 0;
        int coreRowsUpdated = 0;
        int specificRowsCopied = 0;
        int unableToInferTuition = 0;

        for (Map<String, Object> program : listPrograms()) {
            Object codeObj = program.get("program_code");
            if (codeObj == null) continue;
            Integer programId = resolveProgramId(codeObj.toString());
            if (programId == null) continue;
            programsChecked++;

            for (int yearLevel = 1; yearLevel <= 4; yearLevel++) {
                for (int semester = 1; semester <= 2; semester++) {
                    scopesChecked++;
                    
                    Optional<ProgramFeeSetting> exactOpt = feeSettingRepository.findBestMatch(programId, yearLevel, semester, termId);
                    if (exactOpt.isPresent() && Objects.equals(exactOpt.get().getTermId(), termId)) {
                        // Already exists
                        if (exactOpt.get().getFeeTuitionPerUnit().doubleValue() == 0 && exactOpt.get().getFeeRlePerUnit().doubleValue() == 0) {
                            unableToInferTuition++;
                        } else {
                            coreRowsUpdated++;
                        }
                    } else if (exactOpt.isPresent()) {
                        // Needs creation from fallback/template data.
                        ProgramFeeSetting setting = new ProgramFeeSetting();
                        setting.setProgramId(programId);
                        setting.setTermId(termId);
                        setting.setYearLevel(yearLevel);
                        setting.setSemesterNumber(semester);

                        ProgramFeeSetting fallback = exactOpt.get();
                        for (String k : KNOWN_FEES) {
                            setting.setFee(k, fallback.getFee(k));
                        }
                        feeSettingRepository.saveAndFlush(setting);
                        coreRowsCreated++;
                        specificRowsCopied += KNOWN_FEES.size();
                    } else {
                        unableToInferTuition++;
                    }
                }
            }
        }

        return new TermFeePreparationResult(
            programsChecked, scopesChecked, coreRowsCreated, coreRowsUpdated, specificRowsCopied, unableToInferTuition);
    }

    @Transactional
    public FeeTemplateCopyResult copyImportedProgramFeeTemplates(Integer termId) {
        if (termId == null || !validateTablesExist().isEmpty()) {
            return new FeeTemplateCopyResult(0, 0, 0, 0, 0, 0);
        }

        int programsChecked = 0;
        int programsSeeded = 0;
        int coreRowsCreated = 0;
        int coreRowsUpdated = 0;
        int specificRowsCopied = 0;
        int skippedNoSource = 0;

        Map<String, String> sources = feeTemplateSources();
        for (Map<String, Object> program : listPrograms()) {
            Object codeObj = program.get("program_code");
            if (codeObj == null) continue;
            String targetCode = codeObj.toString().trim().toUpperCase();
            String sourceCode = sources.get(targetCode);
            if (sourceCode == null) continue;
            programsChecked++;

            Integer targetProgramId = resolveProgramId(targetCode);
            Integer sourceProgramId = resolveProgramId(sourceCode);
            if (targetProgramId == null || sourceProgramId == null) {
                skippedNoSource++;
                continue;
            }

            boolean seededProgram = false;
            boolean missingSourceForProgram = false;
            for (int yearLevel = 1; yearLevel <= 4; yearLevel++) {
                for (int semester = 1; semester <= 2; semester++) {
                    Optional<ProgramFeeSetting> sourceOpt = feeSettingRepository.findBestMatch(sourceProgramId, yearLevel, semester, termId);
                    if (sourceOpt.isEmpty() || sourceOpt.get().getFeeTuitionPerUnit().doubleValue() == 0) {
                        missingSourceForProgram = true;
                        continue;
                    }
                    
                    ProgramFeeSetting sourceTemplate = sourceOpt.get();
                    Optional<ProgramFeeSetting> targetOpt = feeSettingRepository.findActiveForScopeOrFallback(targetProgramId, yearLevel, semester, termId);
                    
                    ProgramFeeSetting targetTemplate;
                    if (targetOpt.isPresent() && Objects.equals(targetOpt.get().getTermId(), termId)) {
                        targetTemplate = targetOpt.get();
                        coreRowsUpdated++;
                    } else {
                        targetTemplate = new ProgramFeeSetting();
                        targetTemplate.setProgramId(targetProgramId);
                        targetTemplate.setTermId(termId);
                        targetTemplate.setYearLevel(yearLevel);
                        targetTemplate.setSemesterNumber(semester);
                        coreRowsCreated++;
                    }
                    
                    // Copy values
                    for (String k : KNOWN_FEES) {
                        targetTemplate.setFee(k, sourceTemplate.getFee(k));
                    }
                    feeSettingRepository.saveAndFlush(targetTemplate);
                    seededProgram = true;
                    specificRowsCopied += 5;
                }
            }

            if (seededProgram) programsSeeded++;
            if (!seededProgram && missingSourceForProgram) skippedNoSource++;
        }

        return new FeeTemplateCopyResult(
            programsChecked, programsSeeded, coreRowsCreated, coreRowsUpdated, specificRowsCopied, skippedNoSource);
    }

    @Transactional
    public FeeTemplateCopyResult importFeesFromSpecificTerm(Integer sourceTermId, Integer targetTermId, Integer reqProgramId, Integer reqYearLevel, Integer reqSemesterNumber) {
        if (Objects.equals(sourceTermId, targetTermId) || !validateTablesExist().isEmpty()) {
            return new FeeTemplateCopyResult(0, 0, 0, 0, 0, 0);
        }

        int scopesChecked = 0;
        int scopesSeeded = 0;
        int coreRowsCreated = 0;
        int coreRowsUpdated = 0;
        int specificRowsCopied = 0;
        int skippedNoSource = 0;

        List<Integer> targetPrograms = new ArrayList<>();
        if (reqProgramId != null) {
            targetPrograms.add(reqProgramId);
        } else {
            for (Map<String, Object> program : listPrograms()) {
                Object codeObj = program.get("program_code");
                if (codeObj != null) {
                    Integer pid = resolveProgramId(codeObj.toString());
                    if (pid != null) targetPrograms.add(pid);
                }
            }
        }

        for (Integer programId : targetPrograms) {
            int startYear = (reqYearLevel != null) ? reqYearLevel : 1;
            int endYear = (reqYearLevel != null) ? reqYearLevel : 4;
            
            for (int yearLevel = startYear; yearLevel <= endYear; yearLevel++) {
                int startSem = (reqSemesterNumber != null) ? reqSemesterNumber : 1;
                int endSem = (reqSemesterNumber != null) ? reqSemesterNumber : 2;
                
                for (int semester = startSem; semester <= endSem; semester++) {
                    scopesChecked++;

                    Optional<ProgramFeeSetting> sourceOpt = feeSettingRepository.findBestMatch(programId, yearLevel, semester, sourceTermId);
                    
                    if (sourceOpt.isEmpty() || !hasPrimaryRate(sourceOpt.get())) {
                        skippedNoSource++;
                        continue;
                    }

                    ProgramFeeSetting sourceTemplate = sourceOpt.get();
                    
                    Optional<ProgramFeeSetting> targetOpt = feeSettingRepository.findActiveForScopeOrFallback(programId, yearLevel, semester, targetTermId);

                    ProgramFeeSetting targetTemplate;
                    if (targetOpt.isPresent() && Objects.equals(targetOpt.get().getTermId(), targetTermId)) {
                        targetTemplate = targetOpt.get();
                        coreRowsUpdated++;
                    } else {
                        targetTemplate = new ProgramFeeSetting();
                        targetTemplate.setProgramId(programId);
                        targetTemplate.setTermId(targetTermId);
                        targetTemplate.setYearLevel(yearLevel);
                        targetTemplate.setSemesterNumber(semester);
                        coreRowsCreated++;
                    }

                    // Copy values
                    for (String k : KNOWN_FEES) {
                        targetTemplate.setFee(k, sourceTemplate.getFee(k));
                    }
                    feeSettingRepository.saveAndFlush(targetTemplate);
                    scopesSeeded++;
                    specificRowsCopied += KNOWN_FEES.size();
                }
            }
        }

        return new FeeTemplateCopyResult(
            scopesChecked, scopesSeeded, coreRowsCreated, coreRowsUpdated, specificRowsCopied, skippedNoSource);
    }

    public Map<String, Object> buildTermReadinessSummary(Integer termId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<String> missingTables = validateTablesExist();
        List<Map<String, Object>> programs = listPrograms();
        List<String> missingFeeExamples = new ArrayList<>();
        List<String> fallbackFeeExamples = new ArrayList<>();
        List<String> incompleteFeeExamples = new ArrayList<>();
        List<String> missingCurricula = new ArrayList<>();

        summary.put("termId", termId);
        summary.put("missingTables", missingTables);
        summary.put("programCount", programs.size());
        summary.put("missingFeeExamples", missingFeeExamples);
        summary.put("fallbackFeeExamples", fallbackFeeExamples);
        summary.put("incompleteFeeExamples", incompleteFeeExamples);
        summary.put("missingCurricula", missingCurricula);
        summary.put("missingFeeScopeCount", 0);
        summary.put("fallbackFeeScopeCount", 0);
        summary.put("incompleteFeeScopeCount", 0);
        summary.put("checkedScopeCount", 0);
        summary.put("ready", false);

        if (termId == null || !missingTables.isEmpty()) {
            summary.put("statusText", termId == null ? "No active term selected." : "Required tables are missing.");
            return summary;
        }

        Map<String, Object> term = termDetails(termId);
        Object termName = term.get("term_name");
        summary.put("termLabel", termName != null && !termName.toString().isBlank() ? termName : "Term #" + termId);
        summary.put("termActive", isTruthy(term.get("is_active")) || "ACTIVE".equalsIgnoreCase(String.valueOf(term.get("status"))));
        summary.put("termStatus", term.get("status"));

        int missingFeeScopeCount = 0;
        int fallbackFeeScopeCount = 0;
        int incompleteFeeScopeCount = 0;
        int checkedScopeCount = 0;

        for (Map<String, Object> program : programs) {
            String programCode = program.get("program_code") != null ? program.get("program_code").toString() : "";
            Integer programId = resolveProgramId(programCode);
            if (programId == null) {
                missingCurricula.add(programCode + " (program id missing)");
                continue;
            }
            if (!hasActiveCurriculum(programId)) {
                missingCurricula.add(programCode);
            }

            for (int yearLevel = 1; yearLevel <= 4; yearLevel++) {
                for (int semester = 1; semester <= 2; semester++) {
                    checkedScopeCount++;
                    Optional<ProgramFeeSetting> exactOpt =
                        feeSettingRepository.findActiveForExactScope(programId, yearLevel, semester, termId);
                    Optional<ProgramFeeSetting> settingOpt = feeSettingRepository.findBestMatch(programId, yearLevel, semester, termId);

                    String status = resolveScopeStatus(exactOpt, settingOpt);
                    if ("MISSING".equals(status)) {
                        missingFeeScopeCount++;
                        addExample(missingFeeExamples, programCode + " Y" + yearLevel + " S" + semester + " TUITION");
                    } else if ("INCOMPLETE".equals(status)) {
                        incompleteFeeScopeCount++;
                        addExample(incompleteFeeExamples, programCode + " Y" + yearLevel + " S" + semester + " PRIMARY RATE");
                    } else if ("FALLBACK_ONLY".equals(status)) {
                        fallbackFeeScopeCount++;
                        addExample(fallbackFeeExamples, programCode + " Y" + yearLevel + " S" + semester + " TUITION");
                    }
                }
            }
        }

        boolean ready = !programs.isEmpty()
            && missingFeeScopeCount == 0
            && fallbackFeeScopeCount == 0
            && incompleteFeeScopeCount == 0
            && missingCurricula.isEmpty();
        summary.put("missingFeeScopeCount", missingFeeScopeCount);
        summary.put("fallbackFeeScopeCount", fallbackFeeScopeCount);
        summary.put("incompleteFeeScopeCount", incompleteFeeScopeCount);
        summary.put("checkedScopeCount", checkedScopeCount);
        summary.put("ready", ready);
        summary.put("statusText", ready
            ? "Ready for operation."
            : "Review warnings before activating or operating this term: "
                + missingFeeScopeCount + " missing fee scope(s), "
                + fallbackFeeScopeCount + " fallback fee scope(s), "
                + incompleteFeeScopeCount + " incomplete primary-rate scope(s), "
                + missingCurricula.size() + " program(s) without active curriculum.");
        return summary;
    }

    public List<Map<String, Object>> listFeeScopeReadiness(Integer termId, boolean unresolvedOnly, Integer limit) {
        if (termId == null || !validateTablesExist().isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, String> templateSources = feeTemplateSources();

        for (Map<String, Object> program : listPrograms()) {
            String programCode = program.get("program_code") != null ? program.get("program_code").toString().trim().toUpperCase() : "";
            String programName = program.get("program_name") != null ? program.get("program_name").toString().trim() : "";
            Integer programId = resolveProgramId(programCode);
            if (programId == null) {
                continue;
            }

            String templateSourceCode = templateSources.get(programCode);
            Integer templateSourceProgramId = resolveProgramId(templateSourceCode);

            for (int yearLevel = 1; yearLevel <= 4; yearLevel++) {
                for (int semester = 1; semester <= 2; semester++) {
                    Optional<ProgramFeeSetting> exactOpt =
                        feeSettingRepository.findActiveForExactScope(programId, yearLevel, semester, termId);
                    Optional<ProgramFeeSetting> bestOpt =
                        feeSettingRepository.findBestMatch(programId, yearLevel, semester, termId);
                    Optional<ProgramFeeSetting> templateOpt =
                        templateSourceProgramId != null
                            ? feeSettingRepository.findBestMatch(templateSourceProgramId, yearLevel, semester, termId)
                            : Optional.empty();
                    Map<String, Object> previousExact = findLatestExactScope(programId, yearLevel, semester, termId);

                    String status = resolveScopeStatus(exactOpt, bestOpt);
                    if (unresolvedOnly && "READY".equals(status)) {
                        continue;
                    }

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("programCode", programCode);
                    row.put("programName", programName);
                    row.put("yearLevel", yearLevel);
                    row.put("semester", semester);
                    row.put("status", status);
                    row.put("sourceHint", buildSourceHint(status, bestOpt, templateSourceCode, templateOpt, previousExact));
                    row.put("actionHint", buildActionHint(status, templateSourceCode, templateOpt, previousExact));
                    row.put("hasExactRow", exactOpt.isPresent());
                    row.put("loadUrl", "/admin/term-fees?termId=" + termId
                        + "&programCode=" + programCode
                        + "&yearLevel=" + yearLevel
                        + "&semester=" + semester);
                    rows.add(row);

                    if (limit != null && rows.size() >= limit) {
                        return rows;
                    }
                }
            }
        }

        return rows;
    }

    public String exportFeeScopeReadinessCsv(Integer termId, boolean unresolvedOnly) {
        StringBuilder csv = new StringBuilder();
        csv.append("program_code,program_name,year_level,semester,status,source_hint,action_hint\n");
        for (Map<String, Object> row : listFeeScopeReadiness(termId, unresolvedOnly, null)) {
            csv.append(csvValue(row.get("programCode"))).append(',')
                .append(csvValue(row.get("programName"))).append(',')
                .append(csvValue(row.get("yearLevel"))).append(',')
                .append(csvValue(row.get("semester"))).append(',')
                .append(csvValue(row.get("status"))).append(',')
                .append(csvValue(row.get("sourceHint"))).append(',')
                .append(csvValue(row.get("actionHint"))).append('\n');
        }
        return csv.toString();
    }

    public String exportFeeImportTemplateCsv(Integer termId, boolean unresolvedOnly) {
        StringBuilder csv = new StringBuilder();
        csv.append("program_code,program_name,year_level,semester,status,source_hint,action_hint");
        for (String feeCode : KNOWN_FEES) {
            csv.append(',').append(csvValue(feeCode));
        }
        csv.append('\n');

        for (Map<String, Object> row : listFeeScopeReadiness(termId, unresolvedOnly, null)) {
            String programCode = stringValue(row.get("programCode"));
            int yearLevel = intValue(row.get("yearLevel"), 0);
            int semester = intValue(row.get("semester"), 0);
            Integer programId = resolveProgramId(programCode);
            Optional<ProgramFeeSetting> exactOpt = programId != null
                ? feeSettingRepository.findActiveForExactScope(programId, yearLevel, semester, termId)
                : Optional.empty();

            csv.append(csvValue(programCode)).append(',')
                .append(csvValue(row.get("programName"))).append(',')
                .append(csvValue(yearLevel)).append(',')
                .append(csvValue(semester)).append(',')
                .append(csvValue(row.get("status"))).append(',')
                .append(csvValue(row.get("sourceHint"))).append(',')
                .append(csvValue(row.get("actionHint")));

            for (String feeCode : KNOWN_FEES) {
                csv.append(',');
                if (exactOpt.isPresent() && exactOpt.get().getFee(feeCode) > 0) {
                    csv.append(csvValue(exactOpt.get().getFee(feeCode)));
                } else {
                    csv.append(csvValue(""));
                }
            }
            csv.append('\n');
        }
        return csv.toString();
    }

    @Transactional
    public FeeCsvImportResult importFeeTemplateCsv(Integer termId, String csvText) {
        if (termId == null || csvText == null || csvText.isBlank() || !validateTablesExist().isEmpty()) {
            return new FeeCsvImportResult(0, 0, 0, 0, 0, 1, List.of("No term, file content, or required fee tables available."));
        }

        List<List<String>> records = parseCsvRecords(csvText);
        if (records.isEmpty()) {
            return new FeeCsvImportResult(0, 0, 0, 0, 0, 1, List.of("CSV file has no header row."));
        }

        Map<String, Integer> headers = csvHeaders(records.get(0));
        if (!headers.containsKey("program_code") || !headers.containsKey("year_level") || !headers.containsKey("semester")) {
            return new FeeCsvImportResult(0, 0, 0, 0, 0, 1, List.of("CSV must include program_code, year_level, and semester columns."));
        }

        int rowsChecked = 0;
        int rowsImported = 0;
        int rowsCreated = 0;
        int rowsUpdated = 0;
        int feeValuesApplied = 0;
        int rowsSkipped = 0;
        List<String> errorExamples = new ArrayList<>();

        for (int i = 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            if (isBlankRecord(record)) {
                continue;
            }

            rowsChecked++;
            int lineNumber = i + 1;
            try {
                String programCode = csvCell(record, headers, "program_code").trim().toUpperCase();
                Integer programId = resolveProgramId(programCode);
                int yearLevel = parseRequiredInt(csvCell(record, headers, "year_level"), "year_level");
                int semester = parseRequiredInt(csvCell(record, headers, "semester"), "semester");

                if (programId == null) {
                    throw new IllegalArgumentException("unknown program " + programCode);
                }
                if (yearLevel < 1 || yearLevel > 4) {
                    throw new IllegalArgumentException("year_level must be 1-4");
                }
                if (semester < 1 || semester > 2) {
                    throw new IllegalArgumentException("semester must be 1 or 2");
                }

                Optional<ProgramFeeSetting> existingOpt =
                    feeSettingRepository.findActiveForExactScope(programId, yearLevel, semester, termId);
                ProgramFeeSetting setting = existingOpt.orElseGet(() -> {
                    ProgramFeeSetting created = new ProgramFeeSetting();
                    created.setProgramId(programId);
                    created.setTermId(termId);
                    created.setYearLevel(yearLevel);
                    created.setSemesterNumber(semester);
                    return created;
                });

                int appliedForRow = 0;
                for (String feeCode : KNOWN_FEES) {
                    Integer column = headers.get(normalizeHeader(feeCode));
                    if (column == null) {
                        continue;
                    }
                    String rawAmount = csvCell(record, column).trim();
                    if (rawAmount.isBlank()) {
                        continue;
                    }
                    double amount = parseAmount(rawAmount, feeCode);
                    setting.setFee(feeCode, amount);
                    appliedForRow++;
                }

                if (appliedForRow == 0) {
                    rowsSkipped++;
                    addExample(errorExamples, "Line " + lineNumber + ": no fee amount cells were filled.");
                    continue;
                }

                if (setting.getFeeTuitionPerUnit().doubleValue() > 0 && setting.getFeeLecPerUnit().doubleValue() == 0) {
                    setting.setFee("LEC_FEE_PER_UNIT", setting.getFeeTuitionPerUnit().doubleValue());
                }

                if (!hasPrimaryRate(setting)) {
                    rowsSkipped++;
                    addExample(errorExamples, "Line " + lineNumber + ": missing base tuition/lecture rate.");
                    continue;
                }

                feeSettingRepository.saveAndFlush(setting);
                rowsImported++;
                feeValuesApplied += appliedForRow;
                if (existingOpt.isPresent()) {
                    rowsUpdated++;
                } else {
                    rowsCreated++;
                }
            } catch (Exception e) {
                rowsSkipped++;
                addExample(errorExamples, "Line " + lineNumber + ": " + e.getMessage());
            }
        }

        return new FeeCsvImportResult(
            rowsChecked, rowsImported, rowsCreated, rowsUpdated, feeValuesApplied, rowsSkipped, errorExamples);
    }

    private Map<String, Object> termDetails(Integer termId) {
        try {
            return db.queryForMap(
                "SELECT term_id, term_code, term_name, academic_year, semester_number, status, is_active " +
                    "FROM academic_terms WHERE term_id = ? LIMIT 1",
                termId);
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("term_id", termId);
            fallback.put("term_name", "Term #" + termId);
            fallback.put("status", "UNKNOWN");
            fallback.put("is_active", 0);
            return fallback;
        }
    }

    private boolean hasActiveCurriculum(int programId) {
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM curriculum_templates ct " +
                    "JOIN curriculum_courses cc ON cc.curriculum_id = ct.curriculum_id " +
                    "WHERE ct.program_id = ? AND ct.is_active = 1",
                Integer.class, programId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isTruthy(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static void addExample(List<String> examples, String value) {
        if (examples.size() < 8) {
            examples.add(value);
        }
    }

    private Map<String, String> feeTemplateSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("BSBAFIN", "BSBA");
        sources.put("BSBAMKTG", "BSBA");
        sources.put("BSBAOPS", "BSBA");
        sources.put("BSCUSTOMAD", "BSBA");
        sources.put("BSPHARM", "BSMT");
        sources.put("BSPT", "BSMT");
        sources.put("BSOT", "BSMT");
        sources.put("BSRT", "BSMT");
        sources.put("BSRT-RESP", "BSMT");
        sources.put("BSND", "BSMT");
        sources.put("DMD", "BSMT");
        sources.put("BSCPE", "BSIT");
        sources.put("BEED", "BSED");
        return sources;
    }

    private Map<String, Object> findLatestExactScope(int programId, int yearLevel, int semester, Integer excludeTermId) {
        try {
            return db.queryForMap(
                "SELECT pfs.term_id, COALESCE(at.term_name, at.term_code, CONCAT('Term #', pfs.term_id)) AS term_label " +
                    "FROM program_fee_settings pfs " +
                    "LEFT JOIN academic_terms at ON at.term_id = pfs.term_id " +
                    "WHERE pfs.program_id = ? AND pfs.year_level = ? AND pfs.semester_number = ? " +
                    "AND pfs.is_active = 1 AND pfs.term_id IS NOT NULL AND pfs.term_id <> ? " +
                    "AND (COALESCE(pfs.fee_tuition_per_unit, 0) > 0 OR COALESCE(pfs.fee_rle_per_unit, 0) > 0) " +
                    "ORDER BY pfs.term_id DESC, pfs.fee_setting_id DESC LIMIT 1",
                programId, yearLevel, semester, excludeTermId);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String resolveScopeStatus(Optional<ProgramFeeSetting> exactOpt, Optional<ProgramFeeSetting> bestOpt) {
        if (exactOpt.isPresent()) {
            return hasPrimaryRate(exactOpt.get()) ? "READY" : "INCOMPLETE";
        }
        if (bestOpt.isPresent() && hasPrimaryRate(bestOpt.get())) {
            return "FALLBACK_ONLY";
        }
        return "MISSING";
    }

    private String buildSourceHint(String status,
                                   Optional<ProgramFeeSetting> bestOpt,
                                   String templateSourceCode,
                                   Optional<ProgramFeeSetting> templateOpt,
                                   Map<String, Object> previousExact) {
        if ("READY".equals(status)) {
            return "Exact active-term row is present.";
        }
        if ("INCOMPLETE".equals(status)) {
            return "Exact active-term row exists but the base tuition/lecture rate is still blank.";
        }
        if ("FALLBACK_ONLY".equals(status)) {
            return "Global fallback row exists for this scope.";
        }
        if (templateSourceCode != null && templateOpt.isPresent() && hasPrimaryRate(templateOpt.get())) {
            return "Template source " + templateSourceCode + " has usable current-term data.";
        }
        if (!previousExact.isEmpty()) {
            return "Previous exact term available: " + previousExact.getOrDefault("term_label", "older term");
        }
        if (bestOpt.isPresent()) {
            return "A row exists, but it has no usable primary rate.";
        }
        return "No usable source row found.";
    }

    private String buildActionHint(String status,
                                   String templateSourceCode,
                                   Optional<ProgramFeeSetting> templateOpt,
                                   Map<String, Object> previousExact) {
        if ("READY".equals(status)) {
            return "No action needed.";
        }
        if ("INCOMPLETE".equals(status)) {
            return "Open this scope and enter the base tuition/lecture rate before relying on it.";
        }
        if ("FALLBACK_ONLY".equals(status)) {
            return "Save or import official fees to create an exact term row.";
        }
        if (templateSourceCode != null && templateOpt.isPresent() && hasPrimaryRate(templateOpt.get())) {
            return "Use template copy or review " + templateSourceCode + " values before saving.";
        }
        if (!previousExact.isEmpty()) {
            return "Import this scope from " + previousExact.getOrDefault("term_label", "the previous term") + " if still valid.";
        }
        return "Manual official fee entry/import is required.";
    }

    private boolean hasPrimaryRate(ProgramFeeSetting setting) {
        if (setting == null) {
            return false;
        }
        return setting.getFee(CODE_TUITION) > 0
            || setting.getFee("LEC_FEE_PER_UNIT") > 0
            || setting.getFee("RLE_FEE_PER_UNIT") > 0;
    }

    private String csvValue(Object value) {
        String text = value == null ? "" : value.toString();
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private List<List<String>> parseCsvRecords(String csvText) {
        List<List<String>> records = new ArrayList<>();
        List<String> currentRecord = new ArrayList<>();
        StringBuilder currentCell = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < csvText.length(); i++) {
            char ch = csvText.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < csvText.length() && csvText.charAt(i + 1) == '"') {
                        currentCell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    currentCell.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                currentRecord.add(currentCell.toString());
                currentCell.setLength(0);
            } else if (ch == '\n') {
                currentRecord.add(stripTrailingCarriageReturn(currentCell.toString()));
                records.add(currentRecord);
                currentRecord = new ArrayList<>();
                currentCell.setLength(0);
            } else {
                currentCell.append(ch);
            }
        }

        if (currentCell.length() > 0 || !currentRecord.isEmpty()) {
            currentRecord.add(stripTrailingCarriageReturn(currentCell.toString()));
            records.add(currentRecord);
        }
        return records;
    }

    private Map<String, Integer> csvHeaders(List<String> headerRecord) {
        Map<String, Integer> headers = new LinkedHashMap<>();
        for (int i = 0; i < headerRecord.size(); i++) {
            headers.put(normalizeHeader(headerRecord.get(i)), i);
        }
        return headers;
    }

    private String csvCell(List<String> record, Map<String, Integer> headers, String header) {
        Integer index = headers.get(normalizeHeader(header));
        return index == null ? "" : csvCell(record, index);
    }

    private String csvCell(List<String> record, int index) {
        if (index < 0 || index >= record.size()) {
            return "";
        }
        return record.get(index) != null ? record.get(index) : "";
    }

    private boolean isBlankRecord(List<String> record) {
        for (String value : record) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
    }

    private int parseRequiredInt(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is blank");
        }
        return Integer.parseInt(value.trim());
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(value.toString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private double parseAmount(String value, String feeCode) {
        try {
            double amount = Double.parseDouble(value.replace(",", "").trim());
            if (amount < 0) {
                throw new IllegalArgumentException(feeCode + " must not be negative");
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(feeCode + " is not a valid amount");
        }
    }

    private String stripTrailingCarriageReturn(String value) {
        if (value != null && value.endsWith("\r")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static Map<String, Object> feeRow(String code, String name, String kind, String unitBasis) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fee_code", code);
        m.put("fee_name", name);
        m.put("kind", kind);
        m.put("unit_basis", unitBasis);
        return m;
    }

    private boolean tableExists(String table) {
        try {
            Integer c = db.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, table);
            if (c != null && c > 0) {
                return true;
            }
        } catch (Exception ignored) {
        }
        try {
            db.queryForList("SELECT 1 FROM " + table + " WHERE 1 = 0");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public record TermFeePreparationResult(
        int programsChecked,
        int scopesChecked,
        int coreRowsCreated,
        int coreRowsUpdated,
        int specificRowsCopied,
        int unableToInferTuition
    ) {}

    public record FeeTemplateCopyResult(
        int programsChecked,
        int programsSeeded,
        int coreRowsCreated,
        int coreRowsUpdated,
        int specificRowsCopied,
        int skippedNoSource
    ) {}

    public record FeeCsvImportResult(
        int rowsChecked,
        int rowsImported,
        int rowsCreated,
        int rowsUpdated,
        int feeValuesApplied,
        int rowsSkipped,
        List<String> errorExamples
    ) {}
}
