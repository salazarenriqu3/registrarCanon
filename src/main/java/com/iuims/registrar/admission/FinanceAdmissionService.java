package com.iuims.registrar.admission;
import com.iuims.registrar.academic.AcademicGradingService;
import com.iuims.registrar.core.GradeOutcomeSql;
import com.iuims.registrar.admission.ApplicantStatusSyncService;
import com.iuims.registrar.admission.FinanceAdmissionService;
import com.iuims.registrar.curriculum.CurriculumSeederService;
import com.iuims.registrar.curriculum.StudentCurriculumService;
import com.iuims.registrar.core.EnlistmentSchemaService;
import com.iuims.registrar.core.GlobalTermService;
import com.iuims.registrar.core.StudentProfileService;
import com.iuims.registrar.faculty.FacultyLoadService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.finance.TermFeeAdminService;
import com.iuims.registrar.core.DatabaseSetupService;
import com.iuims.registrar.jaypee.JaypeeIntegrationService;
import com.iuims.registrar.core.PolicySettings;
import com.iuims.registrar.core.SqlGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FinanceAdmissionService {

    private final JdbcTemplate db;

    private final ScholarEnrollmentService scholarEnrollmentService;

    private final ApplicantStatusSyncService applicantStatusSyncService;

    private final StudentCurriculumService studentCurriculumService;

    private final GlobalTermService globalTermService;

    private final StudentProfileService studentProfileService;

    public FinanceAdmissionService(JdbcTemplate db, ScholarEnrollmentService scholarEnrollmentService, ApplicantStatusSyncService applicantStatusSyncService, StudentCurriculumService studentCurriculumService, GlobalTermService globalTermService, StudentProfileService studentProfileService) {
        this.db = db;
        this.scholarEnrollmentService = scholarEnrollmentService;
        this.applicantStatusSyncService = applicantStatusSyncService;
        this.studentCurriculumService = studentCurriculumService;
        this.globalTermService = globalTermService;
        this.studentProfileService = studentProfileService;
    }


    // ==========================================
    // 1. ADMISSION WORKFLOWS
    // ==========================================

    public List<Map<String, Object>> getPendingApplications() {
        return db.queryForList(
            "SELECT reference_number, CONCAT(first_name, ' ', last_name) AS full_name, " +
            "program1, applicant_status, term_year, email " +
            "FROM applicants WHERE applicant_status = 'QUALIFIED FOR ENROLLMENT' ORDER BY updated_at DESC"
        );
    }

    public List<Map<String, Object>> searchApplicantsByName(String q) {
        return db.queryForList(
            "SELECT reference_number AS applicant_id, CONCAT(first_name, ' ', last_name) AS full_name " +
            "FROM applicants " +
            "WHERE applicant_status = 'QUALIFIED FOR ENROLLMENT' " +
            "AND (LOWER(first_name) LIKE LOWER(?) OR LOWER(last_name) LIKE LOWER(?) OR LOWER(reference_number) LIKE LOWER(?)) " +
            "LIMIT 10",
            "%" + q + "%", "%" + q + "%", "%" + q + "%"
        );
    }

    public Map<String, Object> findPendingApplicant(String q) {
        try {
            return db.queryForMap(
                "SELECT * FROM applicants " +
                "WHERE applicant_status = 'QUALIFIED FOR ENROLLMENT' " +
                "AND (LOWER(reference_number) LIKE LOWER(?) OR LOWER(first_name) LIKE LOWER(?) OR LOWER(last_name) LIKE LOWER(?)) " +
                "LIMIT 1",
                "%" + q + "%", "%" + q + "%", "%" + q + "%"
            );
        } catch (Exception e) { return null; }
    }

    public Map<String, Object> getApplicantDetails(String refNo) {
        try {
            Map<String, Object> app = db.queryForMap(
                "SELECT *, CONCAT(first_name, ' ', last_name) AS full_name " +
                "FROM applicants WHERE reference_number = ?", refNo
            );
            // Fetch audit trail logs and inject into the map
            List<Map<String, Object>> logs;
            try {
                logs = db.queryForList(
                    "SELECT action, performed_by, remarks, log_timestamp " +
                    "FROM eac_application_logs WHERE ref_no = ? ORDER BY log_timestamp ASC", refNo
                );
            } catch (Exception ignored) {
                logs = new java.util.ArrayList<>();
            }
            app.put("logs", logs);

            // Fetch payment total — check both the bridge table AND the payments table for resilience
            Double paid;
            try {
                // Primary: applicant_payments bridge table (written by Enrollment module)
                Double paidFromBridge = db.queryForObject(
                    "SELECT COALESCE(SUM(payment_amount), 0) FROM applicant_payments WHERE applicant_id = ? AND status = 'UNPROCESSED'",
                    Double.class, refNo);

                // Fallback: payments table (also written by Enrollment for applicants)
                Double paidFromPayments = db.queryForObject(
                    "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE reference_number = ? AND status IN ('COMPLETED', 'VERIFIED')",
                    Double.class, refNo);

                // Use whichever source shows the higher total
                double bridge = (paidFromBridge != null) ? paidFromBridge : 0.0;
                double direct = (paidFromPayments != null) ? paidFromPayments : 0.0;
                paid = Math.max(bridge, direct);
            } catch (Exception ignored) {
                paid = 0.0;
            }
            if (paid == null) paid = 0.0;
            app.put("has_paid", paid >= PolicySettings.admissionMinPayment(db));
            app.put("amount_paid", paid);
            String existingStudentNumber = findStudentNumberByReference(refNo);
            app.put("existing_student_number", existingStudentNumber);
            app.put("has_existing_student_number", hasText(existingStudentNumber));
            return app;
        } catch (Exception e) { return null; }
    }

    public String findStudentNumberByReference(String refNo) {
        if (!hasText(refNo)) {
            return null;
        }
        String referenceNumber = refNo.trim();
        try {
            String studentNumber = db.queryForObject(
                "SELECT student_number FROM students WHERE reference_number = ? LIMIT 1",
                String.class, referenceNumber);
            if (hasText(studentNumber)) {
                return studentNumber.trim();
            }
        } catch (Exception ignored) {
        }
        try {
            String username = db.queryForObject(
                "SELECT username FROM sys_users WHERE reference_number = ? AND role = 'Student' LIMIT 1",
                String.class, referenceNumber);
            if (hasText(username)) {
                return username.trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Transactional
    public String approveApplicantAndGenerateStudentId(String refNo, String programCode, int yearLevel) {
        try {
            String existingStudentNumber = findStudentNumberByReference(refNo);
            if (hasText(existingStudentNumber)) {
                return "ERROR: Student number " + existingStudentNumber + " already exists for applicant reference " + refNo + ".";
            }

            Map<String, Object> app = db.queryForMap("SELECT * FROM applicants WHERE reference_number = ?", refNo);

            // FIX: Use dual-source payment check — mirrors getApplicantDetails().
            // The bridge table (applicant_payments) may be out of sync; also check
            // the main payments table that Enrollment writes to directly.
            Double paidFromBridge = db.queryForObject(
                "SELECT COALESCE(SUM(payment_amount), 0) FROM applicant_payments WHERE applicant_id = ? AND status = 'UNPROCESSED'",
                Double.class, refNo);
            Double paidFromPayments = db.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE reference_number = ? AND status IN ('COMPLETED', 'VERIFIED')",
                Double.class, refNo);
            double bridge  = (paidFromBridge   != null) ? paidFromBridge   : 0.0;
            double direct  = (paidFromPayments != null) ? paidFromPayments : 0.0;
            double totalPaid = Math.max(bridge, direct);

            double admissionMinPayment = PolicySettings.admissionMinPayment(db);
            if (totalPaid < admissionMinPayment) return "ERROR: Minimum admission fee of ₱" + String.format("%,.2f", admissionMinPayment) + " not met. " +
                "Bridge table total: ₱" + bridge + ", Payments table total: ₱" + direct + ".";

            String admissionTermYear = resolveAdmissionTermYearSl(yearLevel);
            int currentSem = globalTermService.getCurrentSemesterNumber() != null
                ? globalTermService.getCurrentSemesterNumber()
                : 1;
            if (currentSem < 1 || currentSem > 2) currentSem = 1;
            // Student ID: [2-digit calendar year]-[semester]-[5-digit sequence]
            String yearPrefix2 = String.format("%02d", java.time.Year.now().getValue() % 100);
            String idPattern = yearPrefix2 + "-" + currentSem + "-%";
            Integer maxId = db.queryForObject(
                "SELECT MAX(CAST(SUBSTRING_INDEX(username, '-', -1) AS UNSIGNED)) FROM sys_users WHERE role = 'Student' AND username LIKE ?",
                Integer.class, idPattern);
            int nextId = (maxId != null ? maxId : 0) + 1;
            String studentNumber = String.format("%s-%d-%05d", yearPrefix2, currentSem, nextId);
            String hashedPass = org.mindrot.jbcrypt.BCrypt.hashpw("1234", org.mindrot.jbcrypt.BCrypt.gensalt());

            // Extract name parts from the applicant record — guard against null values
            String firstName = app.get("first_name") != null ? app.get("first_name").toString().trim() : "";
            String lastName  = app.get("last_name")  != null ? app.get("last_name").toString().trim()  : "";
            String middleName = app.get("middle_name") != null ? app.get("middle_name").toString().trim() : null;
            String realName  = (firstName + " " + lastName).trim();
            if (realName.isEmpty()) realName = studentNumber; // last-resort fallback

            String studentType = yearLevel >= 2 ? "Transferee" : "New Student";
            String enrollmentStatusType = yearLevel >= 2 ? "Irregular" : null;

            db.update(
                "INSERT INTO sys_users " +
                "(username, password, real_name, first_name, last_name, middle_name, role, program_code, year_level, semester, term_year, email, is_active, admission_status, admission_date, student_type, enrollment_status_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'Student', ?, ?, ?, ?, ?, 1, 'ADMITTED', NOW(), ?, ?)",
                studentNumber, hashedPass, realName, firstName, lastName, middleName,
                programCode, yearLevel, currentSem, admissionTermYear, app.get("email"),
                studentType, enrollmentStatusType);


            Integer sysUserId = db.queryForObject("SELECT user_id FROM sys_users WHERE username = ?", Integer.class, studentNumber);

            // Sync into the new students table
            db.update(
                "INSERT INTO students " +
                "(student_number, user_id, reference_number, first_name, last_name, middle_name, real_name, email, mobile, program_code, year_level, semester, term_year, student_type, admission_status, password) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ADMITTED', ?)",
                studentNumber, sysUserId, refNo, firstName, lastName, middleName, realName, 
                app.get("email"), app.get("mobile"), programCode, yearLevel, currentSem, admissionTermYear,
                studentType, hashedPass);
            studentProfileService.copyApplicantProfile(studentNumber, refNo);
            Integer defaultCurriculumId = studentCurriculumService.findDefaultCurriculumId(programCode);
            if (defaultCurriculumId != null) {
                studentCurriculumService.assignCurriculum(
                    studentNumber,
                    defaultCurriculumId,
                    yearLevel >= 2 ? "TRANSFEREE" : "NEW_ENTRANT",
                    yearLevel >= 2
                        ? "Assigned at transferee admission (Y" + yearLevel + ")."
                        : "Assigned from program default curriculum at admission.");
            }

            // student_ledger was migrated to use VARCHAR student_number
            db.update("INSERT INTO student_ledger (student_id, transaction_type, description, credit) VALUES (?, 'PAYMENT', 'Tuition Fee', ?)", studentNumber, totalPaid);
            db.update("INSERT INTO payments (transaction_id, reference_number, amount, payment_method, remarks, payment_date, status, semester, year_level, term_year) VALUES (?, ?, ?, 'Walk-in', 'Tuition Fee', NOW(), 'COMPLETED', ?, ?, ?)",
                "ADM-" + UUID.randomUUID(), studentNumber, totalPaid, currentSem, yearLevel, admissionTermYear);
            db.update("UPDATE applicant_payments SET status = 'PROCESSED' WHERE applicant_id = ?", refNo);
            applicantStatusSyncService.markAdmitted(refNo);
            // Student is now canonical in sys_users; legacy mirror writes are retired.


            return studentNumber;
        } catch (Exception e) {
            // Log the FULL stack trace so it appears in Tomcat logs —
            // this is the single most important debugging line for silent failures.
            System.err.println("[AdmissionService] approveApplicantAndGenerateStudentId FAILED for refNo=" + refNo);
            e.printStackTrace();
            // Return the real exception message so the controller can surface it in the UI.
            String msg = (e.getMessage() != null) ? e.getMessage() : e.getClass().getSimpleName();
            return "ERROR: " + msg;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    // ==========================================
    // 2. FINANCIAL WORKFLOWS
    // ==========================================

    @Transactional
    public void syncVerifiedPayments() {
        try {
            // Join via username (= student_number) so the ledger insert uses VARCHAR, not INT user_id.
            // student_ledger.student_id was migrated to VARCHAR(100) by DatabaseMigrationRunner.
            List<Map<String, Object>> ready = db.queryForList(
                "SELECT u.username AS student_number, MAX(p.amount) AS amount" +
                " FROM payments p" +
                " JOIN sys_users u ON u.username = p.reference_number" +
                " WHERE p.status = 'VERIFIED'" +
                "   AND NOT EXISTS (" +
                "     SELECT 1 FROM student_ledger sl" +
                "     WHERE sl.student_id = u.username" +
                "       AND sl.transaction_type = 'INITIAL_PAYMENT'" +
                "   )" +
                " GROUP BY u.username");
            for (Map<String, Object> row : ready) {
                db.update(
                    "INSERT INTO student_ledger (student_id, transaction_type, description, credit) VALUES (?, 'INITIAL_PAYMENT', 'Downpayment Sync', ?)",
                    row.get("student_number"), row.get("amount"));
            }
        } catch (Exception ignore) {}
    }

    public List<Map<String, Object>> getStudentLedger(String studentNumber) {
        List<Map<String, Object>> records = db.queryForList("SELECT * FROM student_ledger WHERE student_id = ? ORDER BY transaction_date ASC, ledger_id ASC", studentNumber);
        double runningBalance = 0.0;
        for (Map<String, Object> r : records) {
            double debit = ((Number) (r.get("debit") != null ? r.get("debit") : 0)).doubleValue();
            double credit = ((Number) (r.get("credit") != null ? r.get("credit") : 0)).doubleValue();
            runningBalance += (debit - credit);
            r.put("running_balance", runningBalance);
        }
        return records;
    }

    /**
     * Ensures ledger PAYMENT credits exist for completed rows in enrollment's payments table.
     * Enrollment cashier writes both; registrar must not show ₱0 paid when payments exist.
     */
    @Transactional
    public void reconcileLedgerWithPayments(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) return;
        try {
            List<Object> keys = scholarEnrollmentService.ledgerKeysForStudent(studentNumber);
            String in = keys.stream().map(k -> "?").collect(java.util.stream.Collectors.joining(","));
            Object[] keyArr = keys.toArray();
            Double ledgerPaid = db.queryForObject(
                "SELECT COALESCE(SUM(credit),0) FROM student_ledger WHERE student_id IN (" + in + ") " +
                "AND transaction_type IN ('PAYMENT', 'INITIAL_PAYMENT')",
                Double.class, keyArr);
            Double forwardedPaid = db.queryForObject(
                "SELECT COALESCE(SUM(credit),0) FROM student_ledger WHERE student_id IN (" + in + ") " +
                "AND transaction_type = 'FORWARDED_BALANCE'",
                Double.class, keyArr);
            Double tablePaid = db.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM payments WHERE reference_number = ? AND status = 'COMPLETED'",
                Double.class, studentNumber);
            double mirrored = (ledgerPaid != null ? ledgerPaid : 0) + (forwardedPaid != null ? forwardedPaid : 0);
            double gap = (tablePaid != null ? tablePaid : 0) - mirrored;
            if (gap > 0.01) {
                db.update(
                    "INSERT INTO student_ledger (student_id, transaction_type, description, debit, credit) " +
                    "VALUES (?, 'PAYMENT', 'Synced from enrollment payments', 0, ?)",
                    studentNumber, gap);
            }
        } catch (Exception ignored) {
            // payments table may be absent on isolated registrar DBs
        }
    }

    public Map<String, Object> calculateAssessment(String studentNumber) {
        reconcileLedgerWithPayments(studentNumber);
        Map<String, Object> m = new HashMap<>();

        // Align with enrollment cashier: current-term fees + signed forward − term-scoped payments.
        ScholarEnrollmentService.TermFeeBreakdown fees = scholarEnrollmentService.computeCurrentTermFees(studentNumber);
        double tuition = fees.tuition;
        double misc = fees.misc;
        double other = fees.other;
        double termFees = fees.totalFees();
        double balanceForwarded = scholarEnrollmentService.getForwardedBalanceNet(studentNumber);
        double pendingTermCredit = scholarEnrollmentService.getPendingTermCredit(studentNumber);
        double totalAssessment = termFees + balanceForwarded;
        double totalPaid = scholarEnrollmentService.sumCompletedPaymentsForCurrentTerm(studentNumber);

        double scholarDiscount = 0.0;
        try {
            Map<String, Object> sData = db.queryForMap(
                "SELECT scholarship_approved, scholarship_type, scholarship_amount, discount_percentage FROM students WHERE student_number = ?",
                studentNumber);
            if (truthy(sData.get("scholarship_approved"))) {
                Integer fails = db.queryForObject(
                    "SELECT COUNT(*) FROM grades g WHERE g.student_id = ? AND " + GradeOutcomeSql.failedOrInc("g"),
                    Integer.class, studentNumber);
                if (fails == null || fails == 0) {
                    String type = (String) sData.get("scholarship_type");
                    if (type != null) {
                        type = type.toUpperCase();
                        Double amount = numericDouble(sData.get("scholarship_amount"));
                        Double pct = numericDouble(sData.get("discount_percentage"));
                        if ("ACADEMIC".equals(type) || "ATHLETE".equals(type)) scholarDiscount = totalAssessment;
                        else if (amount != null && amount > 0) scholarDiscount = Math.min(amount, totalAssessment);
                        else if ("DISCOUNT".equals(type)) scholarDiscount = (amount != null) ? amount : 0.0;
                        else if (pct != null) scholarDiscount = totalAssessment * (pct / 100.0);
                    }
                }
            }
        } catch (Exception ignored) {}

        double balance = Math.max(0, totalAssessment - (totalPaid + scholarDiscount));
        double accountingBlockThreshold = PolicySettings.accountingBlockThreshold(db);
        boolean enlistBlocked = balanceForwarded >= accountingBlockThreshold;

        m.put("enrollment_status", (totalPaid >= PolicySettings.admissionMinPayment(db)) ? "Officially Enrolled" : "Pending (Needs DP)");
        m.put("has_accounting_block", enlistBlocked);
        m.put("accountingBlocked", enlistBlocked);
        m.put("accounting_block_threshold", accountingBlockThreshold);
        m.put("outstandingBalance", balance);
        m.put("tuition_fee", tuition);
        m.put("misc_fee", misc);
        m.put("other_fee", other);
        m.put("balance_forwarded", balanceForwarded);
        m.put("pending_term_credit", pendingTermCredit);
        m.put("has_pending_overpay", pendingTermCredit > 0.01);
        m.put("term_fees", termFees);
        m.put("total_assessment", totalAssessment);
        m.put("total_paid", totalPaid);
        m.put("balance", balance);

        m.put("tuition_fee_fmt", String.format("%,.2f", Math.max(0, tuition)));
        m.put("misc_fee_fmt", String.format("%,.2f", misc + other));
        m.put("balance_forwarded_fmt", String.format("%,.2f", balanceForwarded));
        m.put("accounting_block_threshold_fmt", String.format("%,.2f", accountingBlockThreshold));
        m.put("pending_term_credit_fmt", String.format("%,.2f", pendingTermCredit));
        m.put("total_assessment_fmt", String.format("%,.2f", totalAssessment));
        m.put("total_paid_fmt", String.format("%,.2f", totalPaid));
        m.put("balance_fmt", String.format("%,.2f", balance));
        return m;
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private Double numericDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    /**
     * Builds canonical SL code for the active term at the given year level.
     * Format: SL[AYstart4][AYend4][YearLevel][Semester]
     * e.g. SL2025202611 = AY 2025-2026, YL 1, Sem 1
     */
    private String resolveAdmissionTermYearSl(int yearLevel) {
        String resolved = globalTermService.getCurrentStudentTermYear(yearLevel);
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }
        int yl = Math.max(1, Math.min(4, yearLevel));
        try {
            String current = db.queryForObject(
                "SELECT setting_value FROM system_settings WHERE setting_key = 'CURRENT_ACADEMIC_TERM' LIMIT 1",
                String.class);
            if (current != null && !current.isBlank()) {
                String t = current.trim();
                // New SL format: SL[AYstart4][AYend4][YL][Sem]
                if (t.startsWith("SL") && !t.startsWith("SL_") && t.length() >= 12) {
                    char sem     = t.charAt(11);
                    String ayStart = t.substring(2, 6);
                    String ayEnd   = t.substring(6, 10);
                    return "SL" + ayStart + ayEnd + yl + sem;
                }
                // Legacy SL_ format — read sem from char[3], ay from chars[5..12]
                if (t.startsWith("SL_") && t.length() >= 13) {
                    char sem     = t.charAt(3);
                    String ayStart = t.substring(5, 9);
                    String ayEnd   = t.substring(9, 13);
                    return "SL" + ayStart + ayEnd + yl + sem;
                }
                // Raw 10-digit DB code
                if (t.length() >= 10 && Character.isDigit(t.charAt(0))) {
                    char sem     = t.charAt(0);
                    String ayStart = t.substring(2, 6);
                    String ayEnd   = t.substring(6, 10);
                    return "SL" + ayStart + ayEnd + yl + sem;
                }
            }
            List<Map<String, Object>> rows = db.queryForList(
                "SELECT term_code FROM academic_terms WHERE is_active = 1 ORDER BY term_id DESC LIMIT 1");
            if (!rows.isEmpty()) {
                String code = (String) rows.get(0).get("term_code");
                if (code != null && code.length() >= 10) {
                    char sem     = code.charAt(0);
                    String ayStart = code.substring(2, 6);
                    String ayEnd   = code.substring(6, 10);
                    return "SL" + ayStart + ayEnd + yl + sem;
                }
            }
        } catch (Exception ignored) {
        }
        int startYear = java.time.Year.now().getValue();
        return "SL" + startYear + (startYear + 1) + yl + "1";
    }
}



