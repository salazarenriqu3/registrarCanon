package com.iuims.registrar.support;
import com.iuims.registrar.service.academic.AcademicGradingService;
import com.iuims.registrar.support.GradeOutcomeSql;
import com.iuims.registrar.service.admission.ApplicantStatusSyncService;
import com.iuims.registrar.service.admission.FinanceAdmissionService;
import com.iuims.registrar.service.curriculum.CurriculumSeederService;
import com.iuims.registrar.service.curriculum.StudentCurriculumService;
import com.iuims.registrar.service.support.EnlistmentSchemaService;
import com.iuims.registrar.service.faculty.FacultyLoadService;
import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.service.finance.TermFeeAdminService;
import com.iuims.registrar.service.support.DatabaseSetupService;
import com.iuims.registrar.service.integration.JaypeeIntegrationService;
import com.iuims.registrar.support.PolicySettings;
import com.iuims.registrar.support.SqlGenerator;

import org.springframework.jdbc.core.JdbcTemplate;

public final class PolicySettings {

    public static final String ACCOUNTING_BLOCK_THRESHOLD = "ACCOUNTING_BLOCK_THRESHOLD";
    public static final String ADMISSION_MIN_PAYMENT = "ADMISSION_MIN_PAYMENT";
    public static final String DOWNPAYMENT_THRESHOLD = "DOWNPAYMENT_THRESHOLD";
    public static final String DOWNPAYMENT_PERCENT = "DOWNPAYMENT_PERCENT";
    public static final String SCHOLARSHIP_MAX_GWA = "SCHOLARSHIP_MAX_GWA";
    public static final String SCHOLARSHIP_MAX_PRELIM_GRADE = "SCHOLARSHIP_MAX_PRELIM_GRADE";
    public static final String SCHOLARSHIP_MAX_MIDTERM_GRADE = "SCHOLARSHIP_MAX_MIDTERM_GRADE";
    public static final String SCHOLARSHIP_MAX_FINALS_GRADE = "SCHOLARSHIP_MAX_FINALS_GRADE";
    public static final String SCHOLARSHIP_DEFAULT_DISCOUNT_PERCENT = "SCHOLARSHIP_DEFAULT_DISCOUNT_PERCENT";
    public static final String SCHOLARSHIP_MIN_COMPLETED_UNITS = "SCHOLARSHIP_MIN_COMPLETED_UNITS";
    public static final String SCHOLARSHIP_DISQUALIFY_INC = "SCHOLARSHIP_DISQUALIFY_INC";
    public static final String SCHOLARSHIP_DISQUALIFY_FAILED = "SCHOLARSHIP_DISQUALIFY_FAILED";

    private PolicySettings() {}

    public static double accountingBlockThreshold(JdbcTemplate db) {
        return decimal(db, ACCOUNTING_BLOCK_THRESHOLD, 100.0);
    }

    public static double admissionMinPayment(JdbcTemplate db) {
        return decimal(db, ADMISSION_MIN_PAYMENT, 1_000.0);
    }

    public static double downpaymentThreshold(JdbcTemplate db) {
        return decimal(db, DOWNPAYMENT_THRESHOLD, 3_000.0);
    }

    public static double downpaymentPercent(JdbcTemplate db) {
        return decimal(db, DOWNPAYMENT_PERCENT, 0.0);
    }

    public static double scholarshipMaxGwa(JdbcTemplate db) {
        return decimal(db, SCHOLARSHIP_MAX_GWA, 1.75);
    }

    public static double scholarshipMaxPrelimGrade(JdbcTemplate db) {
        return decimal(db, SCHOLARSHIP_MAX_PRELIM_GRADE, 2.00);
    }

    public static double scholarshipMaxMidtermGrade(JdbcTemplate db) {
        return decimal(db, SCHOLARSHIP_MAX_MIDTERM_GRADE, 2.00);
    }

    public static double scholarshipMaxFinalsGrade(JdbcTemplate db) {
        return decimal(db, SCHOLARSHIP_MAX_FINALS_GRADE, 2.00);
    }

    public static double scholarshipDefaultDiscountPercent(JdbcTemplate db) {
        return decimal(db, SCHOLARSHIP_DEFAULT_DISCOUNT_PERCENT, 100.0);
    }

    public static int scholarshipMinCompletedUnits(JdbcTemplate db) {
        return (int) Math.round(decimal(db, SCHOLARSHIP_MIN_COMPLETED_UNITS, 27.0));
    }

    public static boolean scholarshipDisqualifyInc(JdbcTemplate db) {
        return bool(db, SCHOLARSHIP_DISQUALIFY_INC, true);
    }

    public static boolean scholarshipDisqualifyFailed(JdbcTemplate db) {
        return bool(db, SCHOLARSHIP_DISQUALIFY_FAILED, true);
    }

    public static void saveDecimal(JdbcTemplate db, String key, String rawValue) {
        String value = normalizeDecimal(rawValue);
        if (value == null) return;
        upsert(db, key, value);
    }

    public static void saveBoolean(JdbcTemplate db, String key, String rawValue) {
        if (rawValue == null) return;
        upsert(db, key, truthy(rawValue) ? "true" : "false");
    }

    public static void upsert(JdbcTemplate db, String key, String value) {
        try {
            int updated = db.update("UPDATE system_settings SET setting_value = ? WHERE setting_key = ?", value, key);
            if (updated == 0) {
                db.update("INSERT INTO system_settings (setting_key, setting_value) VALUES (?, ?)", key, value);
            }
        } catch (Exception ignored) {
        }
    }

    public static double decimal(JdbcTemplate db, String key, double fallback) {
        try {
            String value = db.queryForObject(
                "SELECT setting_value FROM system_settings WHERE setting_key = ? LIMIT 1",
                String.class, key);
            if (value == null || value.trim().isEmpty()) return fallback;
            double parsed = Double.parseDouble(value.trim());
            return parsed >= 0 ? parsed : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public static boolean bool(JdbcTemplate db, String key, boolean fallback) {
        try {
            String value = db.queryForObject(
                "SELECT setting_value FROM system_settings WHERE setting_key = ? LIMIT 1",
                String.class, key);
            if (value == null || value.trim().isEmpty()) return fallback;
            return truthy(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String normalizeDecimal(String rawValue) {
        try {
            if (rawValue == null || rawValue.trim().isEmpty()) return null;
            double value = Double.parseDouble(rawValue.trim());
            if (value < 0) return null;
            return String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean truthy(String value) {
        String normalized = value.trim().toLowerCase();
        return normalized.equals("true")
            || normalized.equals("1")
            || normalized.equals("yes")
            || normalized.equals("on")
            || normalized.equals("active");
    }
}





