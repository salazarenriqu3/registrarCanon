package com.iuims.registrar.core;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class YearLevelLoadPolicyService {

    public static final int FIRST_YEAR_LEVEL = 1;
    public static final int LAST_YEAR_LEVEL = 4;
    private static final BigDecimal DEFAULT_MIN_UNITS = BigDecimal.ZERO;
    private static final BigDecimal DEFAULT_MAX_UNITS = new BigDecimal("27");
    private static final BigDecimal ABSOLUTE_MAX_UNITS = new BigDecimal("60");

    private final JdbcTemplate db;
    private volatile boolean schemaReady;

    public YearLevelLoadPolicyService(JdbcTemplate db) {
        this.db = db;
    }

    public synchronized void ensureSchema() {
        if (schemaReady) return;
        db.execute(
            "CREATE TABLE IF NOT EXISTS year_level_load_policies (" +
                "year_level TINYINT NOT NULL PRIMARY KEY, " +
                "minimum_units DECIMAL(5,2) NOT NULL DEFAULT 0, " +
                "maximum_units DECIMAL(5,2) NOT NULL DEFAULT 27, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

        BigDecimal legacyMaximum = readLegacyMaximum();
        for (int yearLevel = FIRST_YEAR_LEVEL; yearLevel <= LAST_YEAR_LEVEL; yearLevel++) {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM year_level_load_policies WHERE year_level = ?",
                Integer.class, yearLevel);
            if (count == null || count == 0) {
                db.update(
                    "INSERT INTO year_level_load_policies " +
                        "(year_level, minimum_units, maximum_units) VALUES (?, ?, ?)",
                    yearLevel, DEFAULT_MIN_UNITS, legacyMaximum);
            }
        }
        schemaReady = true;
    }

    public List<LoadPolicy> listPolicies() {
        ensureSchema();
        return db.query(
            "SELECT year_level, minimum_units, maximum_units " +
                "FROM year_level_load_policies ORDER BY year_level",
            (rs, rowNum) -> new LoadPolicy(
                rs.getInt("year_level"),
                rs.getBigDecimal("minimum_units"),
                rs.getBigDecimal("maximum_units")));
    }

    public LoadPolicy resolve(int yearLevel) {
        ensureSchema();
        int normalizedYearLevel = Math.max(FIRST_YEAR_LEVEL, Math.min(LAST_YEAR_LEVEL, yearLevel));
        return db.queryForObject(
            "SELECT year_level, minimum_units, maximum_units " +
                "FROM year_level_load_policies WHERE year_level = ?",
            (rs, rowNum) -> new LoadPolicy(
                rs.getInt("year_level"),
                rs.getBigDecimal("minimum_units"),
                rs.getBigDecimal("maximum_units")),
            normalizedYearLevel);
    }

    public LoadStanding classify(int yearLevel, BigDecimal enrolledUnits) {
        LoadPolicy policy = resolve(yearLevel);
        BigDecimal units = enrolledUnits != null ? enrolledUnits : BigDecimal.ZERO;
        if (units.compareTo(policy.minimumUnits()) < 0) return LoadStanding.UNDERLOAD;
        if (units.compareTo(policy.maximumUnits()) > 0) return LoadStanding.OVERLOAD;
        return LoadStanding.REGULAR;
    }

    @Transactional
    public void savePolicies(Map<String, String> params) {
        ensureSchema();
        List<LoadPolicy> validatedPolicies = new ArrayList<>();
        for (int yearLevel = FIRST_YEAR_LEVEL; yearLevel <= LAST_YEAR_LEVEL; yearLevel++) {
            BigDecimal minimum = parseUnits(params.get("minimumUnits_" + yearLevel), "minimum", yearLevel);
            BigDecimal maximum = parseUnits(params.get("maximumUnits_" + yearLevel), "maximum", yearLevel);
            if (minimum.compareTo(maximum) > 0) {
                throw new IllegalArgumentException(
                    "Year " + yearLevel + " minimum units cannot exceed maximum units.");
            }
            validatedPolicies.add(new LoadPolicy(yearLevel, minimum, maximum));
        }
        for (LoadPolicy policy : validatedPolicies) {
            db.update(
                "UPDATE year_level_load_policies " +
                    "SET minimum_units = ?, maximum_units = ?, updated_at = CURRENT_TIMESTAMP " +
                    "WHERE year_level = ?",
                policy.minimumUnits(), policy.maximumUnits(), policy.yearLevel());
        }
    }

    private BigDecimal parseUnits(String rawValue, String field, int yearLevel) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Year " + yearLevel + " " + field + " units is required.");
        }
        try {
            BigDecimal value = new BigDecimal(rawValue.trim());
            if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(ABSOLUTE_MAX_UNITS) > 0) {
                throw new IllegalArgumentException(
                    "Year " + yearLevel + " " + field + " units must be between 0 and 60.");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Year " + yearLevel + " " + field + " units must be a number.");
        }
    }

    private BigDecimal readLegacyMaximum() {
        try {
            String value = db.queryForObject(
                "SELECT setting_value FROM enrollment_settings " +
                    "WHERE setting_key = 'max_units_regular' LIMIT 1",
                String.class);
            BigDecimal parsed = new BigDecimal(value);
            return parsed.compareTo(BigDecimal.ZERO) >= 0 && parsed.compareTo(ABSOLUTE_MAX_UNITS) <= 0
                ? parsed : DEFAULT_MAX_UNITS;
        } catch (Exception ignored) {
            return DEFAULT_MAX_UNITS;
        }
    }

    public record LoadPolicy(int yearLevel, BigDecimal minimumUnits, BigDecimal maximumUnits) {}

    public enum LoadStanding {
        UNDERLOAD,
        REGULAR,
        OVERLOAD
    }
}
