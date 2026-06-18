package com.iuims.registrar.finance;

import com.iuims.registrar.core.PolicySettings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FinancePolicyService {

    private static final List<String> ENROLLMENT_RULE_KEYS = List.of(
        "enrollment_session_minutes",
        "drop_penalty_days_half",
        "drop_penalty_days_full",
        "drop_penalty_half_percent",
        "rle_hours_per_unit"
    );

    private final JdbcTemplate db;
    private final TermFeeAdminService termFeeAdminService;

    public FinancePolicyService(JdbcTemplate db, TermFeeAdminService termFeeAdminService) {
        this.db = db;
        this.termFeeAdminService = termFeeAdminService;
    }

    public void ensureSchema() {
        try {
            db.execute(
                "CREATE TABLE IF NOT EXISTS enrollment_settings (" +
                "setting_key VARCHAR(80) NOT NULL PRIMARY KEY, setting_value VARCHAR(500) NOT NULL, " +
                "description VARCHAR(255) DEFAULT NULL, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
            db.execute(
                "CREATE TABLE IF NOT EXISTS term_installment_plan (" +
                "plan_id INT AUTO_INCREMENT PRIMARY KEY, term_id INT NULL, installment_number TINYINT NOT NULL, " +
                "due_months_offset INT NOT NULL DEFAULT 1, installment_label VARCHAR(80) NOT NULL, " +
                "UNIQUE KEY uk_term_inst (term_id, installment_number))");
            db.execute(
                "CREATE TABLE IF NOT EXISTS student_installment_plan (" +
                "plan_id INT AUTO_INCREMENT PRIMARY KEY, student_number VARCHAR(100) NOT NULL, term_id INT NOT NULL, " +
                "installment_number TINYINT NOT NULL, due_months_offset INT NOT NULL DEFAULT 1, " +
                "installment_label VARCHAR(80) NOT NULL, " +
                "UNIQUE KEY uk_student_term_inst (student_number, term_id, installment_number), " +
                "KEY idx_sip_student_term (student_number, term_id))");
            seedEnrollmentDefaultsIfEmpty();
            seedDefaultInstallmentPlanIfEmpty();
        } catch (Exception ignored) {
        }
    }

    private void seedEnrollmentDefaultsIfEmpty() {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM enrollment_settings", Integer.class);
        if (count != null && count > 0) return;
        db.update("INSERT IGNORE INTO enrollment_settings (setting_key, setting_value, description) VALUES " +
            "('downpayment_amount', '3000', 'Fixed downpayment (legacy mirror)'), " +
            "('downpayment_percent', '0', 'Percent of assessment (legacy mirror)'), " +
            "('max_units_regular', '27', 'Legacy max units; year-level policy is authoritative in Registrar'), " +
            "('max_units_graduating_bonus', '6', 'Graduating bonus'), " +
            "('enrollment_session_minutes', '15', 'Session timeout'), " +
            "('drop_penalty_days_half', '7', 'Half withdrawal charge after days'), " +
            "('drop_penalty_days_full', '14', 'Full withdrawal charge after days'), " +
            "('drop_penalty_half_percent', '50', 'Half withdrawal charge percent'), " +
            "('rle_hours_per_unit', '51', 'RLE hours per unit')");
    }

    private void seedDefaultInstallmentPlanIfEmpty() {
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM term_installment_plan WHERE term_id IS NULL", Integer.class);
        if (count != null && count > 0) return;
        db.update("INSERT IGNORE INTO term_installment_plan (term_id, installment_number, due_months_offset, installment_label) VALUES " +
            "(NULL, 1, 1, '1st Installment'), (NULL, 2, 2, '2nd Installment'), (NULL, 3, 3, '3rd Installment')");
    }

    public Map<String, Object> buildPolicyView(Integer installmentTermId) {
        ensureSchema();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("accountingBlockThreshold", PolicySettings.accountingBlockThreshold(db));
        view.put("admissionMinPayment", PolicySettings.admissionMinPayment(db));
        view.put("downpaymentAmount", PolicySettings.downpaymentThreshold(db));
        view.put("downpaymentPercent", PolicySettings.downpaymentPercent(db));
        view.put("enrollmentRules", loadEnrollmentRules());
        view.put("terms", termFeeAdminService.listTermsAscending());
        view.put("activeTermId", termFeeAdminService.getActiveTermId());

        Integer effectiveTermId = installmentTermId;
        boolean usingDefaultPlan = effectiveTermId == null;
        List<Map<String, Object>> plan = listInstallmentPlan(effectiveTermId);
        if (effectiveTermId != null && plan.isEmpty()) {
            usingDefaultPlan = true;
            plan = listInstallmentPlan(null);
        }
        view.put("installmentTermId", installmentTermId);
        view.put("usingDefaultInstallmentPlan", usingDefaultPlan);
        view.put("installmentPlan", plan);
        view.put("defaultInstallmentPlan", listInstallmentPlan(null));
        view.put("previousTermId", effectiveTermId != null
            ? termFeeAdminService.resolvePreviousTermId(effectiveTermId) : null);
        return view;
    }

    public Map<String, String> loadEnrollmentRules() {
        ensureSchema();
        Map<String, String> rules = new LinkedHashMap<>();
        for (String key : ENROLLMENT_RULE_KEYS) {
            rules.put(key, readEnrollmentSetting(key, defaultFor(key)));
        }
        return rules;
    }

    private String defaultFor(String key) {
        return switch (key) {
            case "max_units_regular" -> "27";
            case "max_units_graduating_bonus" -> "6";
            case "enrollment_session_minutes" -> "15";
            case "drop_penalty_days_half" -> "7";
            case "drop_penalty_days_full" -> "14";
            case "drop_penalty_half_percent" -> "50";
            case "rle_hours_per_unit" -> "51";
            default -> "0";
        };
    }

    private String readEnrollmentSetting(String key, String fallback) {
        try {
            return db.queryForObject(
                "SELECT setting_value FROM enrollment_settings WHERE setting_key = ? LIMIT 1",
                String.class, key);
        } catch (Exception e) {
            return fallback;
        }
    }

    @Transactional
    public void savePaymentGates(Map<String, String> params) {
        ensureSchema();
        PolicySettings.saveDecimal(db, PolicySettings.ACCOUNTING_BLOCK_THRESHOLD,
            params.get(PolicySettings.ACCOUNTING_BLOCK_THRESHOLD));
        PolicySettings.saveDecimal(db, PolicySettings.ADMISSION_MIN_PAYMENT,
            params.get(PolicySettings.ADMISSION_MIN_PAYMENT));
        PolicySettings.saveDecimal(db, PolicySettings.DOWNPAYMENT_THRESHOLD,
            params.get(PolicySettings.DOWNPAYMENT_THRESHOLD));
        PolicySettings.saveDecimal(db, PolicySettings.DOWNPAYMENT_PERCENT,
            params.get(PolicySettings.DOWNPAYMENT_PERCENT));

        String fixedDp = params.get(PolicySettings.DOWNPAYMENT_THRESHOLD);
        String pctDp = params.get(PolicySettings.DOWNPAYMENT_PERCENT);
        upsertEnrollmentSetting("downpayment_amount", fixedDp != null ? fixedDp : "3000");
        upsertEnrollmentSetting("downpayment_percent", pctDp != null ? pctDp : "0");
    }

    @Transactional
    public void saveEnrollmentRules(Map<String, String> params) {
        ensureSchema();
        for (String key : ENROLLMENT_RULE_KEYS) {
            String value = params.get("rule_" + key);
            if (value != null && !value.trim().isEmpty()) {
                upsertEnrollmentSetting(key, value.trim());
            }
        }
    }

    private void upsertEnrollmentSetting(String key, String value) {
        db.update(
            "INSERT INTO enrollment_settings (setting_key, setting_value) VALUES (?, ?) " +
            "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)",
            key, value);
    }

    public List<Map<String, Object>> listInstallmentPlan(Integer termId) {
        ensureSchema();
        if (termId != null) {
            return db.queryForList(
                "SELECT installment_number, due_months_offset, installment_label " +
                "FROM term_installment_plan WHERE term_id = ? ORDER BY installment_number",
                termId);
        }
        return db.queryForList(
            "SELECT installment_number, due_months_offset, installment_label " +
            "FROM term_installment_plan WHERE term_id IS NULL ORDER BY installment_number");
    }

    @Transactional
    public int saveInstallmentPlan(Integer termId, List<InstallmentRow> rows) {
        ensureSchema();
        if (termId != null) {
            db.update("DELETE FROM term_installment_plan WHERE term_id = ?", termId);
        } else {
            db.update("DELETE FROM term_installment_plan WHERE term_id IS NULL");
        }
        int saved = 0;
        for (InstallmentRow row : rows) {
            if (row.label == null || row.label.isBlank()) continue;
            db.update(
                "INSERT INTO term_installment_plan (term_id, installment_number, due_months_offset, installment_label) " +
                "VALUES (?, ?, ?, ?)",
                termId, row.number, row.dueMonthsOffset, row.label.trim());
            saved++;
        }
        return saved;
    }

    @Transactional
    public int copyInstallmentPlan(Integer sourceTermId, Integer targetTermId) {
        ensureSchema();
        List<Map<String, Object>> source = listInstallmentPlan(sourceTermId);
        if (source.isEmpty()) return 0;
        List<InstallmentRow> rows = new ArrayList<>();
        for (Map<String, Object> row : source) {
            rows.add(new InstallmentRow(
                ((Number) row.get("installment_number")).intValue(),
                ((Number) row.get("due_months_offset")).intValue(),
                String.valueOf(row.get("installment_label"))));
        }
        return saveInstallmentPlan(targetTermId, rows);
    }

    public Integer resolvePreviousTermId(Integer termId) {
        return termFeeAdminService.resolvePreviousTermId(termId);
    }

    /** Effective term/default plan (ignores per-student overrides). */
    public List<Map<String, Object>> resolveTermInstallmentPlan(Integer termId) {
        List<Map<String, Object>> plan = listInstallmentPlan(termId);
        if (termId != null && plan.isEmpty()) {
            plan = listInstallmentPlan(null);
        }
        return plan;
    }

    public boolean hasCustomStudentInstallmentPlan(String studentNumber, Integer termId) {
        if (studentNumber == null || studentNumber.isBlank() || termId == null) return false;
        ensureSchema();
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM student_installment_plan WHERE student_number = ? AND term_id = ?",
            Integer.class, studentNumber.trim(), termId);
        return count != null && count > 0;
    }

    public List<Map<String, Object>> listStudentInstallmentPlan(String studentNumber, Integer termId) {
        ensureSchema();
        if (studentNumber == null || studentNumber.isBlank() || termId == null) return List.of();
        return db.queryForList(
            "SELECT installment_number, due_months_offset, installment_label " +
            "FROM student_installment_plan WHERE student_number = ? AND term_id = ? ORDER BY installment_number",
            studentNumber.trim(), termId);
    }

    /**
     * Cashier/enrollment: student override first, else term plan, else default.
     */
    public List<Map<String, Object>> resolveInstallmentPlanForStudent(String studentNumber, Integer termId) {
        List<Map<String, Object>> custom = listStudentInstallmentPlan(studentNumber, termId);
        if (!custom.isEmpty()) return custom;
        return resolveTermInstallmentPlan(termId);
    }

    public Map<String, Object> buildStudentInstallmentView(String studentNumber, Integer termId) {
        ensureSchema();
        Map<String, Object> view = new LinkedHashMap<>();
        boolean custom = hasCustomStudentInstallmentPlan(studentNumber, termId);
        view.put("usingCustomStudentInstallmentPlan", custom);
        view.put("studentInstallmentPlan", custom
            ? listStudentInstallmentPlan(studentNumber, termId)
            : resolveTermInstallmentPlan(termId));
        view.put("termInstallmentPlan", resolveTermInstallmentPlan(termId));
        view.put("installmentTermId", termId);
        if (termId != null) {
            try {
                view.put("installmentTermCode", db.queryForObject(
                    "SELECT term_code FROM academic_terms WHERE term_id = ? LIMIT 1",
                    String.class, termId));
            } catch (Exception ignored) {
                view.put("installmentTermCode", "");
            }
        }
        return view;
    }

    @Transactional
    public int saveStudentInstallmentPlan(String studentNumber, Integer termId, List<InstallmentRow> rows) {
        ensureSchema();
        if (studentNumber == null || studentNumber.isBlank() || termId == null) return 0;
        String sn = studentNumber.trim();
        db.update("DELETE FROM student_installment_plan WHERE student_number = ? AND term_id = ?", sn, termId);
        int saved = 0;
        for (InstallmentRow row : rows) {
            if (row.label == null || row.label.isBlank()) continue;
            db.update(
                "INSERT INTO student_installment_plan (student_number, term_id, installment_number, due_months_offset, installment_label) " +
                "VALUES (?, ?, ?, ?, ?)",
                sn, termId, row.number, row.dueMonthsOffset, row.label.trim());
            saved++;
        }
        return saved;
    }

    @Transactional
    public int copyTermPlanToStudent(String studentNumber, Integer termId) {
        List<Map<String, Object>> source = resolveTermInstallmentPlan(termId);
        if (source.isEmpty()) return 0;
        List<InstallmentRow> rows = new ArrayList<>();
        for (Map<String, Object> row : source) {
            rows.add(new InstallmentRow(
                ((Number) row.get("installment_number")).intValue(),
                ((Number) row.get("due_months_offset")).intValue(),
                String.valueOf(row.get("installment_label"))));
        }
        return saveStudentInstallmentPlan(studentNumber, termId, rows);
    }

    @Transactional
    public void clearStudentInstallmentPlan(String studentNumber, Integer termId) {
        ensureSchema();
        if (studentNumber == null || studentNumber.isBlank() || termId == null) return;
        db.update("DELETE FROM student_installment_plan WHERE student_number = ? AND term_id = ?",
            studentNumber.trim(), termId);
    }

    public static List<InstallmentRow> parseInstallmentRows(
            List<Integer> numbers, List<Integer> dueMonths, List<String> labels) {
        List<InstallmentRow> rows = new ArrayList<>();
        if (numbers == null || labels == null) return rows;
        for (int i = 0; i < numbers.size(); i++) {
            Integer num = numbers.get(i);
            String label = i < labels.size() ? labels.get(i) : null;
            if (num == null || label == null || label.isBlank()) continue;
            int due = dueMonths != null && i < dueMonths.size() && dueMonths.get(i) != null
                ? dueMonths.get(i) : 1;
            rows.add(new InstallmentRow(num, due, label));
        }
        return rows;
    }

    public record InstallmentRow(int number, int dueMonthsOffset, String label) {}
}
