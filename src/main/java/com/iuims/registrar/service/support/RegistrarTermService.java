package com.iuims.registrar.service.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RegistrarTermService {

    private final JdbcTemplate db;

    public RegistrarTermService(JdbcTemplate db) {
        this.db = db;
    }

    public Optional<Map<String, Object>> getCurrentAcademicTerm() {
        Optional<String> rawOpt = getCurrentSettingValue();
        if (rawOpt.isPresent()) {
            String raw = rawOpt.get().trim();

            List<Map<String, Object>> direct = queryByCodeOrName(raw);
            if (!direct.isEmpty()) {
                return Optional.of(direct.get(0));
            }

            String normalizedCode = normalizeToDbTermCode(raw);
            if (normalizedCode != null) {
                List<Map<String, Object>> normalized = queryByCodeOrName(normalizedCode);
                if (!normalized.isEmpty()) {
                    return Optional.of(normalized.get(0));
                }
            }
        }

        List<Map<String, Object>> active = db.queryForList(
            "SELECT term_id, term_code, term_name, academic_year, semester_number, status, is_active " +
                "FROM academic_terms " +
                "WHERE is_active = 1 OR UPPER(COALESCE(status,'')) = 'ACTIVE' " +
                "ORDER BY term_id DESC LIMIT 1");
        if (!active.isEmpty()) {
            return Optional.of(active.get(0));
        }

        List<Map<String, Object>> latest = db.queryForList(
            "SELECT term_id, term_code, term_name, academic_year, semester_number, status, is_active " +
                "FROM academic_terms ORDER BY term_id DESC LIMIT 1");
        return latest.isEmpty() ? Optional.empty() : Optional.of(latest.get(0));
    }

    public Optional<String> getCurrentSettingValue() {
        try {
            String raw = db.queryForObject(
                "SELECT setting_value FROM system_settings WHERE setting_key = 'CURRENT_ACADEMIC_TERM' LIMIT 1",
                String.class);
            return (raw == null || raw.isBlank()) ? Optional.empty() : Optional.of(raw.trim());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<String> getCurrentDbTermCode() {
        return getCurrentAcademicTerm()
            .map(row -> row.get("term_code"))
            .map(Object::toString)
            .filter(code -> !code.isBlank());
    }

    public Optional<Integer> getCurrentTermId() {
        return getCurrentAcademicTerm()
            .map(row -> row.get("term_id"))
            .filter(Number.class::isInstance)
            .map(Number.class::cast)
            .map(Number::intValue);
    }

    public Optional<Integer> getCurrentSemesterNumber() {
        return getCurrentAcademicTerm()
            .map(row -> row.get("semester_number"))
            .filter(Number.class::isInstance)
            .map(Number.class::cast)
            .map(Number::intValue);
    }

    public Optional<String> getCurrentStudentTermYear(int yearLevel) {
        int yl = Math.max(1, Math.min(4, yearLevel));
        return getCurrentDbTermCode().map(code -> toStudentTermYear(code, yl));
    }

    private List<Map<String, Object>> queryByCodeOrName(String value) {
        return db.queryForList(
            "SELECT term_id, term_code, term_name, academic_year, semester_number, status, is_active " +
                "FROM academic_terms WHERE term_code = ? OR term_name = ? ORDER BY term_id DESC LIMIT 1",
            value, value);
    }

    private String normalizeToDbTermCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() >= 10 && Character.isDigit(trimmed.charAt(0))) {
            return trimmed.substring(0, 10);
        }
        if (trimmed.startsWith("SL") && !trimmed.startsWith("SL_") && trimmed.length() >= 12) {
            char sem = trimmed.charAt(11);
            return sem + "1" + trimmed.substring(2, 10);
        }
        if (trimmed.startsWith("SL_") && trimmed.length() >= 13) {
            char sem = trimmed.charAt(3);
            return sem + "1" + trimmed.substring(5, 13);
        }
        return null;
    }

    private String toStudentTermYear(String dbCode, int yearLevel) {
        if (dbCode == null || dbCode.length() < 10 || !Character.isDigit(dbCode.charAt(0))) {
            return null;
        }
        char semester = dbCode.charAt(0);
        String ayStart = dbCode.substring(2, 6);
        String ayEnd = dbCode.substring(6, 10);
        return "SL" + ayStart + ayEnd + yearLevel + semester;
    }
}
