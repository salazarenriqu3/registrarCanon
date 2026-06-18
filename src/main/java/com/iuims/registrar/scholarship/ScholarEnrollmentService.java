package com.iuims.registrar.scholarship;
import com.iuims.registrar.academic.AcademicGradingService;
import com.iuims.registrar.core.GlobalTermService;
import com.iuims.registrar.core.GradeOutcomeSql;
import com.iuims.registrar.admission.ApplicantStatusSyncService;
import com.iuims.registrar.admission.FinanceAdmissionService;
import com.iuims.registrar.curriculum.CurriculumSeederService;
import com.iuims.registrar.curriculum.StudentCurriculumService;
import com.iuims.registrar.core.EnlistmentSchemaService;
import com.iuims.registrar.faculty.FacultyLoadService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.finance.LedgerTransactionTypes;
import com.iuims.registrar.finance.TermFeeAdminService;
import com.iuims.registrar.core.YearLevelLoadPolicyService;
import com.iuims.registrar.core.DatabaseSetupService;
import com.iuims.registrar.jaypee.JaypeeIntegrationService;
import com.iuims.registrar.core.PolicySettings;
import com.iuims.registrar.core.SqlGenerator;
import com.iuims.registrar.core.GradeOutcomeSql;
import com.iuims.registrar.core.PolicySettings;
import com.iuims.registrar.core.EnlistmentSchemaService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import com.iuims.registrar.academic.TermTransitionEvent;
import com.iuims.registrar.core.PolicySettings;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ScholarEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(ScholarEnrollmentService.class);

    /** Exclude prior-term / forward settlement rows from current-term payment totals (matches enrollment cashier). */
    private static final String PAYMENT_EXCLUDE_FORWARDED =
        " AND (remarks IS NULL OR (UPPER(remarks) NOT LIKE '%FORWARDED%' AND UPPER(remarks) NOT LIKE '%PRIOR TERM%'))";

    private final JdbcTemplate db;

    private final AcademicGradingService academicService;

    private final GlobalTermService globalTermService;

    private final EnlistmentSchemaService enlistmentSchemaService;

    private final StudentCurriculumService studentCurriculumService;
    private final TermFeeAdminService termFeeAdminService;
    private final YearLevelLoadPolicyService yearLevelLoadPolicyService;

    @Autowired
    public ScholarEnrollmentService(JdbcTemplate db, AcademicGradingService academicService, GlobalTermService globalTermService, EnlistmentSchemaService enlistmentSchemaService, StudentCurriculumService studentCurriculumService, TermFeeAdminService termFeeAdminService, YearLevelLoadPolicyService yearLevelLoadPolicyService) {
        this.db = db;
        this.academicService = academicService;
        this.globalTermService = globalTermService;
        this.enlistmentSchemaService = enlistmentSchemaService;
        this.studentCurriculumService = studentCurriculumService;
        this.termFeeAdminService = termFeeAdminService;
        this.yearLevelLoadPolicyService = yearLevelLoadPolicyService;
    }

    public ScholarEnrollmentService(JdbcTemplate db, AcademicGradingService academicService, GlobalTermService globalTermService, EnlistmentSchemaService enlistmentSchemaService, StudentCurriculumService studentCurriculumService, TermFeeAdminService termFeeAdminService) {
        this(db, academicService, globalTermService, enlistmentSchemaService, studentCurriculumService,
            termFeeAdminService, null);
    }


    /** Current-term fee breakdown from enlistments + program_general_fees / program_specific_fees. */
    public static final class TermFeeBreakdown {
        public final double tuition;
        public final double misc;
        public final double other;
        public final double units;
        public final double rate;

        public TermFeeBreakdown(double tuition, double misc, double other, double units, double rate) {
            this.tuition = tuition;
            this.misc = misc;
            this.other = other;
            this.units = units;
            this.rate = rate;
        }

        public double totalFees() {
            return tuition + misc + other;
        }
    }

    public TermFeeBreakdown computeCurrentTermFees(String studentNumber) {
        try {
            Map<String, Object> student = db.queryForMap(
                "SELECT s.program_code, COALESCE(s.year_level,1) AS year_level, COALESCE(s.semester,1) AS semester, " +
                    "p.program_id FROM students s LEFT JOIN programs p ON p.program_code = s.program_code " +
                    "WHERE s.student_number = ? LIMIT 1",
                studentNumber);
            Integer termId = resolveCurrentTermId(studentNumber);
            Integer programId = student.get("program_id") != null ? ((Number) student.get("program_id")).intValue() : null;
            int yearLevel = ((Number) student.get("year_level")).intValue();
            int semester = ((Number) student.get("semester")).intValue();
            double units = currentTermUnits(studentNumber, termId);
            if (units <= 0) {
                return new TermFeeBreakdown(0, 0, 0, 0, 0);
            }
            double rate = coreTuitionRate(programId, termId, yearLevel, semester);
            double tuition = units * rate;
            double misc = coreFeeSum(programId, termId, yearLevel, semester, "MISC");
            double other = coreFeeSum(programId, termId, yearLevel, semester, "OTHER");
            return new TermFeeBreakdown(tuition, misc, other, units, rate);
        } catch (IllegalStateException e) {
            log.warn("Fee configuration missing while computing current-term fees for {}", studentNumber, e);
            throw e;
        } catch (Exception e) {
            return new TermFeeBreakdown(0, 0, 0, 0, 0);
        }
    }

    /** Net FORWARDED_BALANCE (debit − credit). Negative = credit carried from prior term. */
    public double getForwardedBalanceNet(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) return 0.0;
        try {
            List<Object> keys = ledgerKeysForStudent(studentNumber);
            String in = "student_id IN (" + ledgerInClause(keys.size()) + ")";
            Double fwd = db.queryForObject(
                "SELECT COALESCE(SUM(debit), 0) - COALESCE(SUM(credit), 0) FROM student_ledger " +
                    "WHERE " + in + " AND transaction_type = 'FORWARDED_BALANCE'",
                Double.class, keys.toArray());
            return fwd != null ? fwd : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** Net PENDING_TERM_CREDIT (credit − debit). Held overpayment awaiting refund/credit choice. */
    public double getPendingTermCredit(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) return 0.0;
        try {
            List<Object> keys = ledgerKeysForStudent(studentNumber);
            String in = "student_id IN (" + ledgerInClause(keys.size()) + ")";
            Object[] args = bindLedgerType(keys, LedgerTransactionTypes.PENDING_TERM_CREDIT);
            Double pending = db.queryForObject(
                "SELECT COALESCE(SUM(credit), 0) - COALESCE(SUM(debit), 0) FROM student_ledger " +
                    "WHERE " + in + " AND transaction_type = ?",
                Double.class, args);
            return pending != null ? pending : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public boolean hasUnresolvedPendingCredit(String studentNumber) {
        return getPendingTermCredit(studentNumber) > 0.01;
    }

    private static Object[] bindLedgerType(List<Object> keys, String type) {
        Object[] keyArr = keys.toArray();
        Object[] out = new Object[keyArr.length + 1];
        System.arraycopy(keyArr, 0, out, 0, keyArr.length);
        out[keyArr.length] = type;
        return out;
    }

    /** Gross FORWARDED_BALANCE debits only — audit/debug; do not use in balance totals. */
    public double getForwardedBalanceGrossDebit(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) return 0.0;
        try {
            List<Object> keys = ledgerKeysForStudent(studentNumber);
            String in = "student_id IN (" + ledgerInClause(keys.size()) + ")";
            Double fwd = db.queryForObject(
                "SELECT COALESCE(SUM(debit), 0) FROM student_ledger " +
                    "WHERE " + in + " AND transaction_type = 'FORWARDED_BALANCE'",
                Double.class, keys.toArray());
            return fwd != null ? fwd : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public List<Object> ledgerKeysForStudent(String studentNumber) {
        List<Object> keys = new ArrayList<>();
        keys.add(studentNumber.trim());
        try {
            Integer uid = db.queryForObject(
                "SELECT user_id FROM sys_users WHERE username = ? LIMIT 1",
                Integer.class, studentNumber.trim());
            if (uid != null) {
                keys.add(uid);
                keys.add(String.valueOf(uid));
            }
        } catch (Exception ignored) {
        }
        return keys.stream().distinct().toList();
    }

    /** Net outstanding balance (current term + forwarded − payments − scholar discount). */
    public double getOutstandingBalanceNet(String studentNumber) {
        TermFeeBreakdown fees = computeCurrentTermFees(studentNumber);
        double forwardNet = getForwardedBalanceNet(studentNumber);
        double totalAssessment = fees.totalFees() + forwardNet;
        double totalPaid = sumCompletedPaymentsForCurrentTerm(studentNumber);
        double scholarDiscount = computeScholarDiscount(studentNumber, totalAssessment);
        return Math.max(0, totalAssessment - (totalPaid + scholarDiscount));
    }

    /** Enlist block: prior-term forwarded debt only (matches enrollment cashier). */
    public boolean hasAccountingBlock(String studentNumber) {
        return getForwardedBalanceNet(studentNumber) >= PolicySettings.accountingBlockThreshold(db);
    }

    private static final String CLOSABLE_LEDGER_TYPES =
        "'TUITION_ASSESSMENT', 'MISC_ASSESSMENT', 'OTHER_ASSESSMENT', 'RLE_ASSESSMENT', " +
        "'SUBJECT_ADD', 'FORWARDED_BALANCE', 'REFUND'";

    private static final String ASSESSMENT_DEBIT_TYPES =
        "'TUITION_ASSESSMENT', 'MISC_ASSESSMENT', 'OTHER_ASSESSMENT', 'RLE_ASSESSMENT', 'SUBJECT_ADD'";

    /**
     * Closes the current term on the ledger: rolls net debt into FORWARDED_BALANCE debit,
     * overpay into PENDING_TERM_CREDIT (staff disposition via Student Profile), and removes
     * term assessment rows (preserves PAYMENT / INITIAL_PAYMENT). Matches registrar
     * {@code triggerTermTransition} per-student logic.
     *
     * <p>Balance forwarded = prior FORWARDED + current assessment charges − scholarship discount
     * − payments tagged to the closing term only (not lifetime ledger credits).
     *
     * @return forwarded amount (positive = debt, negative = credit), or 0 if already closed
     */
    @Transactional
    @EventListener
    public void onTermTransition(TermTransitionEvent event) {
        double forwarded = closeTermAndForwardBalance(event.getStudentNumber());
        if (forwarded >= PolicySettings.accountingBlockThreshold(db)) {
            event.getWithForwardedDebtCounter().incrementAndGet();
        }
    }

    public double closeTermAndForwardBalance(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) return 0.0;
        List<Object> keys = ledgerKeys(studentNumber);
        if (keys.isEmpty()) return 0.0;

        String closingSl;
        try {
            closingSl = db.queryForObject(
                "SELECT term_year FROM students WHERE student_number = ? LIMIT 1",
                String.class, studentNumber.trim());
        } catch (Exception e) {
            closingSl = null;
        }

        String in = ledgerInClause(keys.size());
        Object[] keyArr = keys.toArray();

        double priorForwarded = getForwardedBalanceNet(studentNumber);

        Double assessDebits = db.queryForObject(
            "SELECT COALESCE(SUM(debit), 0) FROM student_ledger WHERE student_id IN (" + in + ") " +
                "AND transaction_type IN (" + ASSESSMENT_DEBIT_TYPES + ")",
            Double.class, keyArr);
        double ledgerAssessDebits = assessDebits != null ? assessDebits : 0.0;

        if (ledgerAssessDebits <= 0.01) {
            if (Math.abs(priorForwarded) > 0.01) {
                return 0.0;
            }
        }

        TermFeeBreakdown computed = computeCurrentTermFees(studentNumber);
        double assessCharges = computed.totalFees();
        if (assessCharges <= 0.01 && ledgerAssessDebits > 0.01) {
            assessCharges = ledgerAssessDebits;
        }
        if (assessCharges <= 0.01 && ledgerAssessDebits <= 0.01) {
            return 0.0;
        }

        double orphanGross = Math.max(0.0, ledgerAssessDebits - assessCharges);
        double orphanNet = computeOrphanAssessNet(studentNumber, orphanGross, closingSl);
        if (priorForwarded > 0.01 && orphanNet > 0.01) {
            orphanNet = Math.max(0.0, orphanNet - priorForwarded);
        }

        // Term-scoped close: prior forward + orphan + closing assessment − this term's payments only.
        double termPayments = closingSl != null && closingSl.startsWith("SL")
            ? sumCompletedPaymentsForSlStrict(studentNumber, closingSl)
            : sumCompletedPaymentsForCurrentTerm(studentNumber);
        double discount = computeTermCloseScholarDiscount(studentNumber, assessCharges + orphanNet);
        double oldBalance = priorForwarded + orphanNet + assessCharges - discount - termPayments;
        Integer termId = resolveCurrentTermId(studentNumber);

        upsertTermCloseSnapshot(studentNumber, closingSl, termId, assessCharges, termPayments, discount,
            priorForwarded, oldBalance, oldBalance);
        log.info("Term close snapshot student={} closingSl={} termId={} assess={} payments={} discount={} priorForward={} orphanNet={} forwardNet={}",
            studentNumber, closingSl, termId, assessCharges, termPayments, discount, priorForwarded, orphanNet,
            oldBalance);

        db.update(
            "DELETE FROM student_ledger WHERE student_id IN (" + in + ") AND transaction_type IN (" + CLOSABLE_LEDGER_TYPES + ")",
            keyArr);

        String writeKey = studentNumber.trim();
        if (oldBalance > 0.01) {
            db.update(
                "INSERT INTO student_ledger (student_id, transaction_type, description, debit, credit) " +
                    "VALUES (?, 'FORWARDED_BALANCE', 'Balance forwarded from previous term', ?, 0)",
                writeKey, oldBalance);
            return oldBalance;
        }
        if (oldBalance < -0.01) {
            db.update(
                "INSERT INTO student_ledger (student_id, transaction_type, description, debit, credit) " +
                    "VALUES (?, ?, 'Prior-term overpayment (pending disposition)', 0, ?)",
                writeKey, LedgerTransactionTypes.PENDING_TERM_CREDIT, Math.abs(oldBalance));
            return 0.0;
        }
        return 0.0;
    }

    /** Prior-term assess still on ledger at term close — net of that term's SL-scoped payments. */
    private void upsertTermCloseSnapshot(String studentNumber, String closingSl, Integer termId,
                                         double assessTotal, double termPayments, double scholarDiscount,
                                         double priorForward, double forwardNet, double termBalance) {
        if (studentNumber == null || closingSl == null || !closingSl.startsWith("SL")) {
            return;
        }
        try {
            db.update(
                "INSERT INTO student_term_closes " +
                    "(student_id, closing_sl, term_id, assess_total, term_payments, scholar_discount, " +
                    "prior_forward, forward_net, term_balance) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE term_id = VALUES(term_id), assess_total = VALUES(assess_total), " +
                    "term_payments = VALUES(term_payments), scholar_discount = VALUES(scholar_discount), " +
                    "prior_forward = VALUES(prior_forward), forward_net = VALUES(forward_net), " +
                    "term_balance = VALUES(term_balance), closed_at = CURRENT_TIMESTAMP",
                studentNumber.trim(), closingSl.trim(), termId, assessTotal, termPayments, scholarDiscount,
                priorForward, forwardNet, termBalance);
        } catch (Exception e) {
            log.warn("Unable to write term close snapshot for student={} closingSl={}: {}",
                studentNumber, closingSl, e.getMessage());
        }
    }

    private double computeOrphanAssessNet(String studentNumber, double orphanGross, String closingSl) {
        if (orphanGross <= 0.01 || closingSl == null) {
            return Math.max(0.0, orphanGross);
        }
        String priorSl = priorStudentTermYear(closingSl);
        if (priorSl == null) {
            return 0.0;
        }
        double priorTermPayments = sumCompletedPaymentsForSlStrict(studentNumber, priorSl);
        return Math.max(0.0, orphanGross - priorTermPayments);
    }

    private double computeTermCloseScholarDiscount(String studentNumber, double assessCharges) {
        try {
            Map<String, Object> sData = db.queryForMap(
                "SELECT scholarship_approved, scholarship_type, scholarship_amount, discount_percentage FROM students WHERE student_number = ?",
                studentNumber);
            if (!truthy(sData.get("scholarship_approved"))) return 0.0;
            Integer fails = db.queryForObject(
                "SELECT COUNT(*) FROM grades g WHERE g.student_id = ? AND " + GradeOutcomeSql.failedOrInc("g"),
                Integer.class, studentNumber);
            if (fails != null && fails > 0) return 0.0;
            String type = (String) sData.get("scholarship_type");
            if (type == null) return 0.0;
            type = type.toUpperCase();
            Double amount = numericDouble(sData.get("scholarship_amount"));
            Double pct = numericDouble(sData.get("discount_percentage"));
            if ("ACADEMIC".equals(type) || "ATHLETE".equals(type)) return assessCharges;
            if (amount != null && amount > 0) return Math.min(amount, assessCharges);
            if ("DISCOUNT".equals(type)) return amount != null ? amount : 0.0;
            if (pct != null && pct > 0) return assessCharges * (pct / 100.0);
        } catch (Exception ignored) {
        }
        return 0.0;
    }

    private List<Object> ledgerKeys(String studentNumber) {
        return ledgerKeysForStudent(studentNumber);
    }

    private static String ledgerInClause(int n) {
        return "?,".repeat(Math.max(0, n - 1)) + "?";
    }

    private double computeScholarDiscount(String studentNumber, double totalAssessment) {
        try {
            Map<String, Object> sData = db.queryForMap(
                "SELECT scholarship_approved, scholarship_type, scholarship_amount, discount_percentage FROM students WHERE student_number = ?",
                studentNumber);
            if (!truthy(sData.get("scholarship_approved"))) return 0.0;
            Integer fails = db.queryForObject(
                "SELECT COUNT(*) FROM grades g WHERE g.student_id = ? AND " + GradeOutcomeSql.failedOrInc("g"),
                Integer.class, studentNumber);
            if (fails != null && fails > 0) return 0.0;
            String type = (String) sData.get("scholarship_type");
            if (type == null) return 0.0;
            type = type.toUpperCase();
            Double amount = numericDouble(sData.get("scholarship_amount"));
            Double pct = numericDouble(sData.get("discount_percentage"));
            if ("ACADEMIC".equals(type) || "ATHLETE".equals(type)) return totalAssessment;
            if (amount != null && amount > 0) return Math.min(amount, totalAssessment);
            if ("DISCOUNT".equals(type)) return amount != null ? amount : 0.0;
            if (pct != null) return totalAssessment * (pct / 100.0);
        } catch (Exception ignored) {
        }
        return 0.0;
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private Double numericDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    /** Completed payments for the closing SL term only (exact term_year match — used at term close). */
    public double sumCompletedPaymentsForSlStrict(String studentNumber, String slTermYear) {
        if (studentNumber == null || slTermYear == null || !slTermYear.startsWith("SL")) {
            return 0.0;
        }
        try {
            Double bySl = db.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM payments " +
                    "WHERE reference_number = ? AND status = 'COMPLETED' AND term_year = ?" + PAYMENT_EXCLUDE_FORWARDED,
                Double.class, studentNumber.trim(), slTermYear.trim());
            return bySl != null ? bySl : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** Completed payments for the student's current SL_ term (same filter as enrollment cashier). */
    public double sumCompletedPaymentsForCurrentTerm(String studentNumber) {
        try {
            Map<String, Object> row = db.queryForMap(
                "SELECT term_year, COALESCE(year_level,1) AS year_level, COALESCE(semester,1) AS semester " +
                    "FROM students WHERE student_number = ? LIMIT 1",
                studentNumber);
            String sl = row.get("term_year") != null ? row.get("term_year").toString().trim() : "";
            int yl = ((Number) row.get("year_level")).intValue();
            int sem = ((Number) row.get("semester")).intValue();
            return sumCompletedPaymentsForTerm(studentNumber, sl, yl, sem);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double sumCompletedPaymentsForTerm(String studentNumber, String slTermYear, int yearLevel, int semester) {
        if (studentNumber == null || slTermYear == null || !slTermYear.startsWith("SL")) {
            return 0.0;
        }
        String academicYear = academicYearFromSl(slTermYear);
        try {
            Double bySl = db.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM payments " +
                    "WHERE reference_number = ? AND status = 'COMPLETED' AND term_year = ?" + PAYMENT_EXCLUDE_FORWARDED,
                Double.class, studentNumber, slTermYear);
            if (bySl != null && bySl > 0) {
                return bySl;
            }
            if (academicYear == null) {
                return bySl != null ? bySl : 0.0;
            }
            Double byStanding = db.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM payments p " +
                    "WHERE p.reference_number = ? AND p.status = 'COMPLETED' " +
                    "AND (p.term_year IS NULL OR p.term_year = '') " +
                    "AND p.year_level = ? AND p.semester = ? " +
                    "AND EXISTS (SELECT 1 FROM academic_terms at " +
                    "WHERE at.academic_year = ? AND at.semester_number = ? " +
                    "AND (at.start_date IS NULL OR DATE(p.payment_date) >= at.start_date) " +
                    "AND (at.end_date IS NULL OR DATE(p.payment_date) <= at.end_date))" + PAYMENT_EXCLUDE_FORWARDED,
                Double.class, studentNumber, yearLevel, semester, academicYear, semester);
            return byStanding != null ? byStanding : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String academicYearFromSl(String slTermYear) {
        if (slTermYear == null || !slTermYear.startsWith("SL") || slTermYear.length() < 12) {
            return null;
        }
        try {
            // New format: SL[AYstart4][AYend4][YL][Sem] — AY at chars 2..10
            if (!slTermYear.startsWith("SL_") && slTermYear.length() >= 12) {
                int y0 = Integer.parseInt(slTermYear.substring(2, 6));
                int y1 = Integer.parseInt(slTermYear.substring(6, 10));
                return y0 + "-" + y1;
            }
            // Legacy SL_ format: SL_[sem][yl][AYstart4][AYend4]
            if (slTermYear.startsWith("SL_") && slTermYear.length() >= 13) {
                int y0 = Integer.parseInt(slTermYear.substring(5, 9));
                int y1 = Integer.parseInt(slTermYear.substring(9, 13));
                return y0 + "-" + y1;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /** Immediate prior student term. Handles new SL format: SL[AYstart4][AYend4][YL][Sem]. */
    private static String priorStudentTermYear(String slTermYear) {
        if (slTermYear == null || !slTermYear.startsWith("SL") || slTermYear.length() < 12) {
            return null;
        }
        try {
            // New format: SL[AYstart4][AYend4][YL1][Sem1]
            if (!slTermYear.startsWith("SL_") && slTermYear.length() >= 12) {
                int y0  = Integer.parseInt(slTermYear.substring(2, 6));
                int y1  = Integer.parseInt(slTermYear.substring(6, 10));
                int yl  = Character.getNumericValue(slTermYear.charAt(10));
                int sem = Character.getNumericValue(slTermYear.charAt(11));
                if (sem < 1 || sem > 2 || yl < 1 || yl > 4) return null;
                if (sem == 2) return "SL" + y0 + y1 + yl + "1";
                if (yl > 1)  return "SL" + (y0 - 1) + (y1 - 1) + (yl - 1) + "2";
                return null;
            }
            // Legacy SL_ format: SL_[sem][yl][AYstart4][AYend4]
            if (slTermYear.startsWith("SL_") && slTermYear.length() >= 13) {
                int sem = Character.getNumericValue(slTermYear.charAt(3));
                int yl  = Character.getNumericValue(slTermYear.charAt(4));
                if (sem < 1 || sem > 2 || yl < 1 || yl > 4) return null;
                int y0 = Integer.parseInt(slTermYear.substring(5, 9));
                int y1 = Integer.parseInt(slTermYear.substring(9, 13));
                if (sem == 2) return "SL" + y0 + y1 + yl + "1";          // convert to new format
                if (yl > 1)  return "SL" + (y0-1) + (y1-1) + (yl-1) + "2";
            }
        } catch (Exception ignored) {}
        return null;
    }

    public Map<String, Object> findStudent(String keyword) {
        try {
            Map<String, Object> result = db.queryForMap(
                "SELECT * FROM students WHERE (student_number = ? OR LOWER(CONCAT(first_name, ' ', last_name)) LIKE LOWER(?)) LIMIT 1",
                keyword, "%" + keyword + "%"
            );
            Map<String, Object> mutableResult = new java.util.HashMap<>(result);
            mutableResult.put("username", mutableResult.get("student_number"));
            return mutableResult;
        } catch (Exception e) {
            try {
                Map<String, Object> applicant = db.queryForMap(
                    "SELECT * FROM applicants WHERE reference_number = ? OR LOWER(last_name) LIKE LOWER(?) LIMIT 1",
                    keyword, "%" + keyword + "%"
                );
                Map<String, Object> mapped = new HashMap<>();
                mapped.put("user_id", -1);
                mapped.put("username", applicant.get("ref_no"));
                mapped.put("real_name", (applicant.get("first_name") + " " + applicant.get("last_name")) + " (APPLICANT)");
                mapped.put("year_level", 1);
                mapped.put("semester", 1);
                mapped.put("program_code", applicant.get("program1"));
                String globalTerm = globalTermService.getCurrentStudentTermYear(1);
                String applicantTermYear = applicant.get("term_year") != null && !applicant.get("term_year").toString().isBlank()
                    ? applicant.get("term_year").toString()
                    : globalTerm;
                mapped.put("term_year", applicantTermYear);
                mapped.put("admission_status", applicant.getOrDefault("applicant_status", "APPLICANT"));
                mapped.put("scholarship_type", "NONE");
                return mapped;
            } catch (Exception e2) { return null; }
        }
    }

    public List<Map<String, Object>> getAvailableSubjects(String programCode, int year, int sem, String termCode) {
        Integer termId = globalTermService.getCurrentTermId();
        String sql = scholarSubjectSql("AND cc.year_level = ? AND cc.semester_number = ? ", termId);
        return termId != null
            ? db.queryForList(sql, programCode, year, sem, termId)
            : db.queryForList(sql, programCode, year, sem);
    }

    public List<Map<String, Object>> getOtherSubjects(String programCode, int year, int sem) {
        Integer termId = globalTermService.getCurrentTermId();
        String sql = scholarSubjectSql("AND (cc.year_level <> ? OR cc.semester_number <> ?) ", termId);
        return termId != null
            ? db.queryForList(sql, programCode, year, sem, termId)
            : db.queryForList(sql, programCode, year, sem);
    }

    private String scholarSubjectSql(String standingFilter, Integer termId) {
        return "SELECT c.course_id, c.course_code, c.course_title, cs.section_id, cs.section_code, " +
            "c.credit_units, cc.year_level, cc.semester_number AS semester, " +
            "IFNULL((SELECT GROUP_CONCAT(CONCAT(CASE sch.day_of_week " +
            "WHEN 1 THEN 'M' WHEN 2 THEN 'T' WHEN 3 THEN 'W' WHEN 4 THEN 'TH' WHEN 5 THEN 'F' WHEN 6 THEN 'S' ELSE 'SUN' END, " +
            "' ', TIME_FORMAT(sch.start_time, '%H:%i'), '-', TIME_FORMAT(sch.end_time, '%H:%i')) SEPARATOR ', ') " +
            "FROM class_schedules sch WHERE sch.section_id = cs.section_id), 'TBA') AS schedule " +
            "FROM class_sections cs " +
            "JOIN courses c ON c.course_id = cs.course_id " +
            "JOIN curriculum_courses cc ON cc.course_id = c.course_id " +
            "JOIN curriculum_templates ct ON ct.curriculum_id = cc.curriculum_id AND COALESCE(ct.is_active, 0) = 1 " +
            "JOIN programs p ON p.program_id = ct.program_id " +
            "WHERE p.program_code = ? " +
            "AND COALESCE(c.active_status, 1) = 1 AND COALESCE(c.onlist, 1) = 1 " +
            standingFilter +
            (termId != null ? "AND cs.term_id = ? " : "") +
            "ORDER BY cc.year_level, cc.semester_number, c.course_code, cs.section_code";
    }

    public List<Map<String, Object>> getAcademicLoad(String studentNumber) {
        try {
            return db.queryForList(
                "SELECT se.enlistment_id, c.course_code AS course_code, c.course_title AS description, " +
                "c.credit_units AS units, cc.semester_number AS semester, cc.year_level AS year_level, " +
                "IFNULL((SELECT GROUP_CONCAT(" +
                "  CONCAT(CASE sch2.day_of_week " +
                "    WHEN 1 THEN 'MON' WHEN 2 THEN 'TUE' WHEN 3 THEN 'WED' " +
                "    WHEN 4 THEN 'THU' WHEN 5 THEN 'FRI' WHEN 6 THEN 'SAT' ELSE 'SUN' END, " +
                "    ' ', TIME_FORMAT(sch2.start_time,'%h:%i %p'), " +
                "    '-', TIME_FORMAT(sch2.end_time,'%h:%i %p')) " +
                "  ORDER BY sch2.day_of_week SEPARATOR ' | ') " +
                "  FROM class_schedules sch2 WHERE sch2.section_id = se.section_id), 'TBA') AS schedule " +
                "FROM student_enlistments se " +
                "JOIN courses c ON se.course_id = c.course_id " +
                "JOIN class_sections cs ON cs.section_id = se.section_id " +
                "LEFT JOIN students s ON s.student_number = se.student_id " +
                "LEFT JOIN programs p ON p.program_code = s.program_code " +
                "LEFT JOIN curriculum_templates ct ON ct.program_id = p.program_id AND COALESCE(ct.is_active, 0) = 1 " +
                "LEFT JOIN curriculum_courses cc ON cc.curriculum_id = ct.curriculum_id AND cc.course_id = c.course_id " +
                "WHERE se.student_id = ?"
                + enlistmentSchemaService.enlistmentStatusFilter(
                    EnlistmentSchemaService.Scope.COMMITTED_ONLY, "se"),
                studentNumber);
        } catch (Exception e) { return new ArrayList<>(); }
    }

    /** Per-unit tuition from program_general_fees for the student's current term context. */
    public double tuitionRatePerUnit(String studentNumber) {
        return computeCurrentTermFees(studentNumber).rate;
    }

    /** Registrar-owned year-level cap, with the legacy global rule retained as a fallback. */
    public double getMaxAllowedUnits(String programCode, int studentYearLevel) {
        if (yearLevelLoadPolicyService != null) {
            return yearLevelLoadPolicyService.resolve(studentYearLevel).maximumUnits().doubleValue();
        }
        double max = readEnrollmentSettingInt("max_units_regular", 24);
        try {
            Integer maxYear = db.queryForObject(
                "SELECT MAX(cc.year_level) FROM curriculum_courses cc " +
                    "JOIN curriculum_templates ct ON cc.curriculum_id = ct.curriculum_id " +
                    "JOIN programs p ON ct.program_id = p.program_id " +
                    "WHERE p.program_code = ?",
                Integer.class, programCode);
            if (maxYear != null && studentYearLevel >= maxYear) {
                max += readEnrollmentSettingInt("max_units_graduating_bonus", 6);
            }
        } catch (Exception ignored) {
        }
        return max;
    }

    public double getMaxAllowedUnitsForStudent(String studentNumber, String programCode, int studentYearLevel) {
        if (yearLevelLoadPolicyService != null) {
            return yearLevelLoadPolicyService.resolve(studentYearLevel).maximumUnits().doubleValue();
        }
        double max = readEnrollmentSettingInt("max_units_regular", 24);
        try {
            Integer curriculumId = studentCurriculumService.findCurrentCurriculumId(studentNumber);
            Integer maxYear = curriculumId != null
                ? db.queryForObject(
                    "SELECT MAX(year_level) FROM curriculum_courses WHERE curriculum_id = ?",
                    Integer.class, curriculumId)
                : null;
            if (maxYear != null && studentYearLevel >= maxYear) {
                max += readEnrollmentSettingInt("max_units_graduating_bonus", 6);
            }
        } catch (Exception ignored) {
        }
        return max;
    }

    public boolean isOfficialEnrollment(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) {
            return false;
        }
        try {
            String status = db.queryForObject(
                "SELECT COALESCE(s.admission_status, u.admission_status) FROM students s " +
                    "LEFT JOIN sys_users u ON BINARY u.username = BINARY s.student_number " +
                    "WHERE BINARY s.student_number = BINARY ? LIMIT 1",
                String.class, studentNumber.trim());
            return status != null
                && ("ENROLLED".equalsIgnoreCase(status) || "ADMITTED".equalsIgnoreCase(status));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Same-term withdrawal after enlistment row is removed: resync assess, charge, conditional REFUND.
     * Never posts FORWARDED_BALANCE.
     */
    @Transactional
    public void processSubjectDrop(String studentNumber, String courseCode, double units,
                                   LocalDateTime enlistedAt) {
        processSubjectDrop(studentNumber, courseCode, units, enlistedAt, null, null);
    }

    @Transactional
    public void processSubjectDrop(String studentNumber, String courseCode, double units,
                                   LocalDateTime enlistedAt, Double chargeOverride, String policyNote) {
        processSubjectDrop(studentNumber, courseCode, units, enlistedAt, chargeOverride, policyNote, null);
    }

    @Transactional
    private void processSubjectDrop(String studentNumber, String courseCode, double units,
                                   LocalDateTime enlistedAt, Double chargeOverride, String policyNote,
                                   Double originalCostOverride) {
        if (studentNumber == null || studentNumber.isBlank() || courseCode == null) {
            return;
        }
        double originalCost = originalCostOverride != null && originalCostOverride > 0.01
            ? originalCostOverride
            : units * tuitionRatePerUnit(studentNumber);
        long daysEnrolled = enlistedAt != null
            ? java.time.temporal.ChronoUnit.DAYS.between(enlistedAt, LocalDateTime.now()) : 0L;
        syncCoreLedgerAssessment(studentNumber);
        applyDropLedgerAdjustments(studentNumber, courseCode, units, originalCost, daysEnrolled,
            chargeOverride, policyNote);
    }

    /** Withdrawal by enlistment_id. Staging removals skip ledger adjustments. */
    @Transactional
    public void dropSubjectByEnlistmentId(long enlistmentId) {
        dropSubjectByEnlistmentId(enlistmentId, null, null);
    }

    @Transactional
    public void dropSubjectByEnlistmentId(long enlistmentId, Double chargeOverride, String policyNote) {
        Map<String, Object> row = db.queryForMap(
            "SELECT se.student_id, se.enlisted_date, c.course_code, c.credit_units AS units " +
                "FROM student_enlistments se " +
                "JOIN courses c ON se.course_id = c.course_id " +
                "WHERE se.enlistment_id = ? LIMIT 1",
            enlistmentId);
        String studentNumber = (String) row.get("student_id");
        String courseCode = (String) row.get("course_code");
        double units = ((Number) row.get("units")).doubleValue();
        java.sql.Timestamp enlistedDate = (java.sql.Timestamp) row.get("enlisted_date");
        LocalDateTime enlistedAt = enlistedDate != null ? enlistedDate.toLocalDateTime() : null;
        double originalCost = units * tuitionRatePerUnit(studentNumber);

        db.update("DELETE FROM student_enlistments WHERE enlistment_id = ?", enlistmentId);

        if (chargeOverride != null || isOfficialEnrollment(studentNumber)) {
            processSubjectDrop(studentNumber, courseCode, units, enlistedAt, chargeOverride, policyNote, originalCost);
        }
    }

    /**
     * After enlistment removal and assess resync: withdrawal charge + conditional ledger REFUND only
     * when term payments exceed post-drop amount still owed. Never posts FORWARDED_BALANCE.
     */
    @Transactional
    private void applyDropLedgerAdjustments(String studentNumber, String courseCode, double units,
                                           double originalCost, long daysEnrolled) {
        applyDropLedgerAdjustments(studentNumber, courseCode, units, originalCost, daysEnrolled, null, null);
    }

    @Transactional
    private void applyDropLedgerAdjustments(String studentNumber, String courseCode, double units,
                                           double originalCost, long daysEnrolled,
                                           Double chargeOverride, String policyNote) {
        if (studentNumber == null || studentNumber.isBlank()) {
            return;
        }
        double penaltyAmount = chargeOverride != null
            ? Math.max(0.0, Math.min(originalCost, chargeOverride))
            : computeDropPenalty(originalCost, daysEnrolled);
        String penaltyMsg = chargeOverride != null
            ? formatFormalWithdrawalPolicyMessage(policyNote)
            : formatDropPenaltyMessage(daysEnrolled, penaltyAmount, originalCost);

        if (penaltyAmount > 0.01) {
            db.update(
                "INSERT INTO student_ledger (student_id, transaction_type, description, debit, credit) " +
                    "VALUES (?, 'DROP_PENALTY', ?, ?, 0)",
                studentNumber.trim(),
                (chargeOverride != null ? "Formal Withdrawal Charge: " : "Withdrawal Charge: ")
                    + courseCode + penaltyMsg,
                penaltyAmount);
        }

        double refundCredit = computeDropRefundCredit(studentNumber, originalCost, penaltyAmount);
        if (refundCredit > 0.01) {
            String dropDesc = String.format("Withdrawn Subject: %s (%.1f units) [-PHP %,.2f]",
                courseCode, units, refundCredit);
            db.update(
                "INSERT INTO student_ledger (student_id, transaction_type, description, debit, credit) " +
                    "VALUES (?, 'REFUND', ?, 0, ?)",
                studentNumber.trim(), dropDesc, refundCredit);
        }
    }

    private String formatFormalWithdrawalPolicyMessage(String policyNote) {
        if (policyNote == null || policyNote.isBlank()) {
            return " (formal withdrawal policy)";
        }
        return " (" + policyNote.trim() + ")";
    }

    private double computeDropRefundCredit(String studentNumber, double originalCost, double penaltyAmount) {
        TermFeeBreakdown fees = computeCurrentTermFees(studentNumber);
        double forwardNet = getForwardedBalanceNet(studentNumber);
        double totalDue = fees.totalFees() + forwardNet + penaltyAmount;
        double scholarDiscount = computeScholarDiscount(studentNumber, totalDue);
        double termPayments = sumCompletedPaymentsForCurrentTerm(studentNumber);
        double stillOwed = totalDue - scholarDiscount - termPayments;
        double overpay = Math.max(0.0, -stillOwed);
        double maxUnitRefund = Math.max(0.0, originalCost - penaltyAmount);
        return Math.min(overpay, maxUnitRefund);
    }

    private double computeDropPenalty(double originalCost, long daysEnrolled) {
        int halfDays = readEnrollmentSettingInt("drop_penalty_days_half", 7);
        int fullDays = readEnrollmentSettingInt("drop_penalty_days_full", 14);
        double halfPct = readEnrollmentSettingDouble("drop_penalty_half_percent", 50.0);
        if (daysEnrolled >= fullDays) {
            return originalCost;
        }
        if (daysEnrolled >= halfDays) {
            return originalCost * (halfPct / 100.0);
        }
        return 0.0;
    }

    private String formatDropPenaltyMessage(long daysEnrolled, double penaltyAmount, double originalCost) {
        if (penaltyAmount <= 0.01) {
            return "";
        }
        int fullDays = readEnrollmentSettingInt("drop_penalty_days_full", 14);
        int halfDays = readEnrollmentSettingInt("drop_penalty_days_half", 7);
        double halfPct = readEnrollmentSettingDouble("drop_penalty_half_percent", 50.0);
        if (daysEnrolled >= fullDays) {
            return " (100% Penalty - Over " + fullDays + " Days)";
        }
        if (daysEnrolled >= halfDays) {
            return " (" + (int) halfPct + "% Penalty Applied)";
        }
        return "";
    }

    private int readEnrollmentSettingInt(String key, int defaultValue) {
        try {
            Integer v = db.queryForObject(
                "SELECT CAST(setting_value AS SIGNED) FROM enrollment_settings WHERE setting_key = ? LIMIT 1",
                Integer.class, key);
            return v != null ? v : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double readEnrollmentSettingDouble(String key, double defaultValue) {
        try {
            Double v = db.queryForObject(
                "SELECT CAST(setting_value AS DECIMAL(10,2)) FROM enrollment_settings WHERE setting_key = ? LIMIT 1",
                Double.class, key);
            return v != null ? v : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Transactional
    public String processWalkInPayment(String studentNumber, double amount, String paymentType, String remarks, int semester, int yearLevel, String termYear) {
        try {
            Map<String, Object> student = findStudent(studentNumber);
            if (student == null) return "ERROR: Student not found.";
            String txId = "WLK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            int sysUserId = ((Number) student.get("user_id")).intValue();
            if (sysUserId > 0) {
                db.update("INSERT INTO payments (transaction_id, or_number, reference_number, amount, payment_method, semester, year_level, term_year, remarks, payment_date, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), 'COMPLETED')",
                    txId, String.format("%06d", (int)(Math.random()*1000000)), studentNumber, amount, paymentType+" (OTC)", semester, yearLevel, termYear, remarks);
                db.update("INSERT INTO student_ledger (student_id, transaction_type, description, credit) VALUES (?, 'PAYMENT', ?, ?)", studentNumber, "Walk-in: "+remarks, amount);
                syncCoreLedgerAssessment(studentNumber);
            } else {
                db.update("INSERT INTO applicant_payments (applicant_id, payment_amount, status) VALUES (?, ?, 'UNPROCESSED')", studentNumber, amount);
            }
            return "SUCCESS:" + txId;
        } catch (Exception e) { return "ERROR: " + e.getMessage(); }
    }

    public List<Map<String, Object>> buildLedgerHistory(Map<String, Object> student) {
        return new ArrayList<>(); // Placeholder for brevity
    }

    @Transactional
    public String grantExternalScholarship(String studentNumber, String classification, double discountPct, String status) {
        return grantExternalScholarship(studentNumber, classification, discountPct, 0.0, status);
    }

    @Transactional
    public String grantExternalScholarship(String studentNumber, String classification, double discountPct,
                                           double scholarshipAmount, String status) {
        try {
            ensureScholarshipTypeCatalog();
            String resolvedStudentNumber = resolveScholarshipStudentNumber(studentNumber);
            if (resolvedStudentNumber == null) {
                return "ERROR: Student not found.";
            }

            String cleanStatus = status != null ? status.trim().toUpperCase() : "ACTIVE";
            String cleanClassification = classification != null ? classification.trim().toUpperCase() : "NONE";
            boolean revoke = "REVOKED".equals(cleanStatus) || "NONE".equals(cleanClassification);
            double cleanDiscountPct = Math.max(0.0, Math.min(100.0, discountPct));
            double cleanScholarshipAmount = Math.max(0.0, scholarshipAmount);

            if (revoke) {
                db.update(
                    "UPDATE students SET scholarship_approved = 0, scholarship_type = 'NONE', " +
                        "scholarship_amount = 0, discount_percentage = 0 WHERE student_number = ?",
                    resolvedStudentNumber);
            } else {
                db.update(
                    "UPDATE students SET scholarship_approved = 1, scholarship_type = ?, " +
                        "scholarship_amount = ?, discount_percentage = ? WHERE student_number = ?",
                    cleanClassification, cleanScholarshipAmount, cleanDiscountPct, resolvedStudentNumber);
            }

            syncCoreLedgerAssessment(resolvedStudentNumber);
            return "SUCCESS";
        } catch (Exception e) { return "ERROR: " + e.getMessage(); }
    }

    public List<Map<String, Object>> getActiveScholarshipTypes() {
        ensureScholarshipTypeCatalog();
        return db.queryForList(
            "SELECT classification, COALESCE(display_name, classification) AS display_name, discount_mode, " +
                "default_discount_percentage, default_scholarship_amount, COALESCE(is_internal, 0) AS is_internal, " +
                "COALESCE(is_active, 1) AS is_active " +
                "FROM scholarship_types WHERE COALESCE(is_active, 1) = 1 AND classification <> 'ACADEMIC' " +
                "ORDER BY display_name, classification");
    }

    public boolean isManualExternalScholarshipType(String classification) {
        if (classification == null || classification.isBlank()) return false;
        if ("NONE".equalsIgnoreCase(classification.trim())) return true;
        ensureScholarshipTypeCatalog();
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM scholarship_types WHERE classification = ? AND COALESCE(is_active, 1) = 1 " +
                "AND classification <> 'ACADEMIC'",
            Integer.class, classification.trim().toUpperCase(java.util.Locale.US));
        return count != null && count > 0;
    }

    public List<Map<String, Object>> getAllScholarshipTypes() {
        ensureScholarshipTypeCatalog();
        return db.queryForList(
            "SELECT classification, COALESCE(display_name, classification) AS display_name, discount_mode, " +
                "default_discount_percentage, default_scholarship_amount, COALESCE(is_internal, 0) AS is_internal, " +
                "COALESCE(is_active, 1) AS is_active " +
                "FROM scholarship_types ORDER BY display_name, classification");
    }

    public void saveScholarshipType(String classification, String displayName, String discountMode,
                                    double defaultDiscountPct, double defaultAmount, boolean internal,
                                    boolean active) {
        ensureScholarshipTypeCatalog();
        String code = normalizeScholarshipClassification(classification);
        if (code == null) return;
        String name = displayName != null && !displayName.isBlank()
            ? displayName.trim()
            : code.replace('_', ' ');
        String mode = normalizeDiscountMode(discountMode);
        double pct = "FULL".equals(mode) ? 100.0 : Math.max(0.0, Math.min(100.0, defaultDiscountPct));
        double amount = Math.max(0.0, defaultAmount);
        int updated = db.update(
            "UPDATE scholarship_types SET display_name = ?, discount_mode = ?, default_discount_percentage = ?, " +
                "default_scholarship_amount = ?, is_internal = ?, requires_id = 1, is_active = ? WHERE classification = ?",
            name, mode, pct, amount, internal ? 1 : 0, active ? 1 : 0, code);
        if (updated == 0) {
            db.update(
                "INSERT INTO scholarship_types " +
                    "(classification, display_name, discount_mode, default_discount_percentage, default_scholarship_amount, is_internal, requires_id, is_active) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 1, ?)",
                code, name, mode, pct, amount, internal ? 1 : 0, active ? 1 : 0);
        }
    }

    private void ensureScholarshipTypeCatalog() {
        try {
            db.execute(
                "CREATE TABLE IF NOT EXISTS scholarship_types (" +
                    "type_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "classification VARCHAR(50) NOT NULL UNIQUE, " +
                    "display_name VARCHAR(100) NULL, " +
                    "discount_mode VARCHAR(20) NOT NULL DEFAULT 'PERCENT', " +
                    "default_discount_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00, " +
                    "default_scholarship_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00, " +
                    "is_internal TINYINT(1) DEFAULT 0, " +
                    "requires_id TINYINT(1) DEFAULT 1, " +
                    "is_active TINYINT(1) NOT NULL DEFAULT 1)");
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN display_name VARCHAR(100) NULL"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN discount_mode VARCHAR(20) NOT NULL DEFAULT 'PERCENT'"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN default_discount_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN default_scholarship_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN is_internal TINYINT(1) DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN requires_id TINYINT(1) DEFAULT 1"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1"); } catch (Exception ignored) {}
            seedScholarshipType("ACADEMIC", "Academic Scholarship", "FULL", 100.0, 0.0, true);
            seedScholarshipType("BARANGAY", "Barangay Scholarship", "PERCENT", 50.0, 0.0, false);
            seedScholarshipType("LGU", "LGU Scholarship", "PERCENT", 50.0, 0.0, false);
            seedScholarshipType("ATHLETE", "Athlete Scholarship", "FULL", 100.0, 0.0, true);
            seedScholarshipType("EMPLOYEE_DEPENDENT", "Employee Dependent", "PERCENT", 50.0, 0.0, false);
            seedScholarshipType("OTHER", "Other / Miscellaneous", "FLAT", 0.0, 0.0, false);
        } catch (Exception ignored) {
        }
    }

    private void seedScholarshipType(String classification, String displayName, String mode,
                                     double discountPct, double amount, boolean internal) {
        try {
            db.update(
                "INSERT INTO scholarship_types " +
                    "(classification, display_name, discount_mode, default_discount_percentage, default_scholarship_amount, is_internal, requires_id, is_active) " +
                    "SELECT ?, ?, ?, ?, ?, ?, 1, 1 WHERE NOT EXISTS (SELECT 1 FROM scholarship_types WHERE classification = ?)",
                classification, displayName, mode, discountPct, amount, internal ? 1 : 0, classification);
        } catch (Exception ignored) {
        }
    }

    private String normalizeScholarshipClassification(String classification) {
        if (classification == null || classification.isBlank()) return null;
        String code = classification.trim().toUpperCase(java.util.Locale.US)
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        return code.isBlank() ? null : code;
    }

    private String normalizeDiscountMode(String discountMode) {
        String mode = discountMode != null ? discountMode.trim().toUpperCase(java.util.Locale.US) : "PERCENT";
        return switch (mode) {
            case "FLAT", "FULL" -> mode;
            default -> "PERCENT";
        };
    }

    public Map<String, Object> getScholarshipPolicySettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put(PolicySettings.SCHOLARSHIP_MAX_GWA, PolicySettings.scholarshipMaxGwa(db));
        settings.put(PolicySettings.SCHOLARSHIP_MAX_INDIVIDUAL_GRADE, PolicySettings.scholarshipMaxIndividualGrade(db));
        settings.put(PolicySettings.SCHOLARSHIP_DEFAULT_DISCOUNT_PERCENT, PolicySettings.scholarshipDefaultDiscountPercent(db));
        settings.put(PolicySettings.SCHOLARSHIP_MIN_COMPLETED_SUBJECTS, PolicySettings.scholarshipMinCompletedSubjects(db));
        settings.put(PolicySettings.SCHOLARSHIP_MIN_COMPLETED_UNITS, PolicySettings.scholarshipMinCompletedUnits(db));
        settings.put(PolicySettings.SCHOLARSHIP_DISQUALIFY_INC, PolicySettings.scholarshipDisqualifyInc(db));
        settings.put(PolicySettings.SCHOLARSHIP_DISQUALIFY_FAILED, PolicySettings.scholarshipDisqualifyFailed(db));
        return settings;
    }

    public void updateScholarshipPolicySettings(Map<String, String> params) {
        PolicySettings.saveDecimal(db, PolicySettings.SCHOLARSHIP_MAX_GWA, params.get(PolicySettings.SCHOLARSHIP_MAX_GWA));
        PolicySettings.saveDecimal(db, PolicySettings.SCHOLARSHIP_MAX_INDIVIDUAL_GRADE, params.get(PolicySettings.SCHOLARSHIP_MAX_INDIVIDUAL_GRADE));
        PolicySettings.saveDecimal(db, PolicySettings.SCHOLARSHIP_DEFAULT_DISCOUNT_PERCENT, params.get(PolicySettings.SCHOLARSHIP_DEFAULT_DISCOUNT_PERCENT));
        PolicySettings.saveDecimal(db, PolicySettings.SCHOLARSHIP_MIN_COMPLETED_SUBJECTS, params.get(PolicySettings.SCHOLARSHIP_MIN_COMPLETED_SUBJECTS));
        PolicySettings.saveDecimal(db, PolicySettings.SCHOLARSHIP_MIN_COMPLETED_UNITS, params.get(PolicySettings.SCHOLARSHIP_MIN_COMPLETED_UNITS));
        PolicySettings.saveBoolean(db, PolicySettings.SCHOLARSHIP_DISQUALIFY_INC, params.get(PolicySettings.SCHOLARSHIP_DISQUALIFY_INC));
        PolicySettings.saveBoolean(db, PolicySettings.SCHOLARSHIP_DISQUALIFY_FAILED, params.get(PolicySettings.SCHOLARSHIP_DISQUALIFY_FAILED));
    }

    public List<Map<String, Object>> getScholarshipTermOptions() {
        try {
            return db.queryForList(
                "SELECT term_id, term_name, status, is_active, " +
                    "CASE WHEN is_active = 1 OR UPPER(COALESCE(status,'')) = 'ACTIVE' THEN 1 ELSE 0 END AS current_term " +
                    "FROM academic_terms ORDER BY term_id DESC");
        } catch (Exception e) {
            return List.of();
        }
    }

    public Integer getDefaultScholarshipTermId() {
        try {
            return db.queryForObject(
                "SELECT term_id FROM academic_terms WHERE is_active = 1 OR UPPER(COALESCE(status,'')) = 'ACTIVE' " +
                    "ORDER BY term_id DESC LIMIT 1",
                Integer.class);
        } catch (Exception ignored) {
        }
        try {
            return db.queryForObject("SELECT term_id FROM academic_terms ORDER BY term_id DESC LIMIT 1", Integer.class);
        } catch (Exception e) {
            return null;
        }
    }

    public List<Map<String, Object>> evaluateAcademicScholarshipCandidates(Integer termId) {
        ensureScholarshipReviewWorkflow();
        Integer resolvedTermId = termId != null && termId > 0 ? termId : getDefaultScholarshipTermId();
        if (resolvedTermId == null) {
            return List.of();
        }

        Map<String, Object> policy = getScholarshipPolicySettings();
        double maxGwa = ((Number) policy.get(PolicySettings.SCHOLARSHIP_MAX_GWA)).doubleValue();
        double maxIndividual = ((Number) policy.get(PolicySettings.SCHOLARSHIP_MAX_INDIVIDUAL_GRADE)).doubleValue();
        int minUnits = ((Number) policy.get(PolicySettings.SCHOLARSHIP_MIN_COMPLETED_UNITS)).intValue();
        boolean blockInc = Boolean.TRUE.equals(policy.get(PolicySettings.SCHOLARSHIP_DISQUALIFY_INC));
        boolean blockFailed = Boolean.TRUE.equals(policy.get(PolicySettings.SCHOLARSHIP_DISQUALIFY_FAILED));

        List<Map<String, Object>> rows = db.queryForList(
            "SELECT s.student_number, COALESCE(NULLIF(s.real_name, ''), NULLIF(u.real_name, ''), s.student_number) AS student_name, " +
                "s.program_code, COALESCE(s.scholarship_approved, 0) AS scholarship_approved, " +
                "COALESCE(s.scholarship_type, 'NONE') AS scholarship_type, COALESCE(s.discount_percentage, 0) AS discount_percentage, " +
                "COUNT(g.id) AS subject_count, " +
                "COALESCE(SUM(COALESCE(c.credit_units, 0)), 0) AS completed_units, " +
                "AVG(COALESCE(g.registrar_final_grade, g.semestral_grade)) AS gwa, " +
                "MAX(COALESCE(g.registrar_final_grade, g.semestral_grade)) AS highest_grade, " +
                "SUM(CASE WHEN " + GradeOutcomeSql.failed("g") + " THEN 1 ELSE 0 END) AS failed_count, " +
                "SUM(CASE WHEN " + GradeOutcomeSql.outcome("g") + " = 'INC' THEN 1 ELSE 0 END) AS inc_count " +
                "FROM grades g " +
                "JOIN class_sections cs ON cs.section_id = g.section_id " +
                "LEFT JOIN courses c ON c.course_id = COALESCE(g.course_id, cs.course_id) " +
                "JOIN students s ON s.student_number = g.student_id " +
                "LEFT JOIN sys_users u ON u.username = s.student_number " +
                "WHERE cs.term_id = ? " +
                "AND (COALESCE(g.registrar_final_grade, g.semestral_grade) IS NOT NULL " +
                "OR " + GradeOutcomeSql.outcome("g") + " IN ('FAILED', 'INC', 'PASSED')) " +
                "GROUP BY s.student_number, s.real_name, u.real_name, s.program_code, s.scholarship_approved, s.scholarship_type, s.discount_percentage " +
                "ORDER BY student_name, s.student_number",
            resolvedTermId);

        Map<String, Map<String, Object>> reviewsByStudent = new HashMap<>();
        for (Map<String, Object> review : db.queryForList(
                "SELECT * FROM scholarship_review_workflow WHERE term_id = ? AND classification = 'ACADEMIC'",
                resolvedTermId)) {
            reviewsByStudent.put(String.valueOf(review.get("student_number")), review);
        }

        for (Map<String, Object> row : rows) {
            double gwa = numericOrZero(row.get("gwa"));
            double highest = numericOrZero(row.get("highest_grade"));
            int subjects = intOrZero(row.get("subject_count"));
            double units = numericOrZero(row.get("completed_units"));
            int failed = intOrZero(row.get("failed_count"));
            int inc = intOrZero(row.get("inc_count"));

            List<String> reasons = new ArrayList<>();
            if (units < minUnits) reasons.add("Needs at least " + minUnits + " completed unit(s)");
            if (blockFailed && failed > 0) reasons.add(failed + " failed grade(s)");
            if (blockInc && inc > 0) reasons.add(inc + " INC grade(s)");
            if (gwa <= 0 || gwa > maxGwa) reasons.add("GWA " + formatGrade(gwa) + " exceeds " + formatGrade(maxGwa));
            if (highest > maxIndividual) reasons.add("Highest individual grade " + formatGrade(highest) + " exceeds " + formatGrade(maxIndividual));

            row.put("gwa_fmt", formatGrade(gwa));
            row.put("highest_grade_fmt", formatGrade(highest));
            row.put("completed_units_fmt", formatUnits(units));
            row.put("eligible", reasons.isEmpty());
            row.put("scholarship_granted", truthy(row.get("scholarship_approved")));
            Map<String, Object> review = reviewsByStudent.get(String.valueOf(row.get("student_number")));
            row.put("review_status", review != null ? String.valueOf(review.get("status")).toUpperCase() : "NONE");
            row.put("decision_note", review != null ? review.get("decision_note") : null);
            row.put("reason", reasons.isEmpty() ? "Meets configured scholarship policy." : String.join("; ", reasons));
        }
        return rows;
    }

    @Transactional
    public String requestAcademicScholarship(String studentNumber, Integer termId, String requestedBy) {
        ensureScholarshipReviewWorkflow();
        Integer resolvedTermId = termId != null && termId > 0 ? termId : getDefaultScholarshipTermId();
        if (resolvedTermId == null) return "ERROR: Academic term not found.";

        Map<String, Object> candidate = evaluateAcademicScholarshipCandidates(resolvedTermId).stream()
            .filter(row -> studentNumber.equals(String.valueOf(row.get("student_number"))))
            .findFirst().orElse(null);
        if (candidate == null) return "ERROR: Student has no official grade record for this term.";
        if (!Boolean.TRUE.equals(candidate.get("eligible"))) return "ERROR: Student does not meet the configured policy.";
        if (truthy(candidate.get("scholarship_granted"))) return "ERROR: Scholarship is already posted.";

        String currentStatus = String.valueOf(candidate.get("review_status"));
        if ("PENDING".equals(currentStatus) || "APPROVED".equals(currentStatus)) {
            return "ERROR: Scholarship review is already " + currentStatus.toLowerCase() + ".";
        }

        double discount = PolicySettings.scholarshipDefaultDiscountPercent(db);
        int updated = db.update(
            "UPDATE scholarship_review_workflow SET status = 'PENDING', discount_percentage = ?, scholarship_amount = 0, " +
                "decision_note = NULL, requested_by = ?, requested_at = CURRENT_TIMESTAMP, reviewed_by = NULL, reviewed_at = NULL, " +
                "posted_by = NULL, posted_at = NULL, updated_at = CURRENT_TIMESTAMP " +
                "WHERE student_number = ? AND term_id = ? AND classification = 'ACADEMIC'",
            discount, cleanActor(requestedBy), studentNumber, resolvedTermId);
        if (updated == 0) {
            db.update(
                "INSERT INTO scholarship_review_workflow " +
                    "(student_number, term_id, classification, status, discount_percentage, scholarship_amount, requested_by, requested_at) " +
                    "VALUES (?, ?, 'ACADEMIC', 'PENDING', ?, 0, ?, CURRENT_TIMESTAMP)",
                studentNumber, resolvedTermId, discount, cleanActor(requestedBy));
        }
        return "SUCCESS";
    }

    @Transactional
    public String approveAcademicScholarship(String studentNumber, Integer termId, String reviewedBy, String note) {
        ensureScholarshipReviewWorkflow();
        int updated = db.update(
            "UPDATE scholarship_review_workflow SET status = 'APPROVED', decision_note = ?, reviewed_by = ?, " +
                "reviewed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
                "WHERE student_number = ? AND term_id = ? AND classification = 'ACADEMIC' AND status = 'PENDING'",
            cleanNote(note), cleanActor(reviewedBy), studentNumber, termId);
        return updated == 1 ? "SUCCESS" : "ERROR: Only a pending review can be approved.";
    }

    @Transactional
    public String rejectAcademicScholarship(String studentNumber, Integer termId, String reviewedBy, String note) {
        ensureScholarshipReviewWorkflow();
        int updated = db.update(
            "UPDATE scholarship_review_workflow SET status = 'REJECTED', decision_note = ?, reviewed_by = ?, " +
                "reviewed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
                "WHERE student_number = ? AND term_id = ? AND classification = 'ACADEMIC' AND status IN ('PENDING', 'APPROVED')",
            cleanNote(note), cleanActor(reviewedBy), studentNumber, termId);
        return updated == 1 ? "SUCCESS" : "ERROR: Only a pending or approved review can be rejected.";
    }

    @Transactional
    public String postAcademicScholarship(String studentNumber, Integer termId, String postedBy) {
        ensureScholarshipReviewWorkflow();
        List<Map<String, Object>> reviews = db.queryForList(
            "SELECT discount_percentage, scholarship_amount FROM scholarship_review_workflow " +
                "WHERE student_number = ? AND term_id = ? AND classification = 'ACADEMIC' AND status = 'APPROVED'",
            studentNumber, termId);
        if (reviews.isEmpty()) return "ERROR: Scholarship must be approved before posting.";

        Map<String, Object> review = reviews.get(0);
        String result = grantExternalScholarship(
            studentNumber, "ACADEMIC", numericOrZero(review.get("discount_percentage")),
            numericOrZero(review.get("scholarship_amount")), "ACTIVE");
        if (!result.startsWith("SUCCESS")) return result;

        db.update(
            "UPDATE scholarship_review_workflow SET status = 'POSTED', posted_by = ?, posted_at = CURRENT_TIMESTAMP, " +
                "updated_at = CURRENT_TIMESTAMP WHERE student_number = ? AND term_id = ? AND classification = 'ACADEMIC'",
            cleanActor(postedBy), studentNumber, termId);
        return "SUCCESS";
    }

    public void markAcademicScholarshipRevoked(String studentNumber, Integer termId, String actor) {
        if (termId == null) return;
        ensureScholarshipReviewWorkflow();
        db.update(
            "UPDATE scholarship_review_workflow SET status = 'REVOKED', decision_note = 'Revoked by registrar', " +
                "reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
                "WHERE student_number = ? AND term_id = ? AND classification = 'ACADEMIC'",
            cleanActor(actor), studentNumber, termId);
    }

    private void ensureScholarshipReviewWorkflow() {
        db.execute(
            "CREATE TABLE IF NOT EXISTS scholarship_review_workflow (" +
                "review_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_number VARCHAR(100) NOT NULL, term_id INT NOT NULL, " +
                "classification VARCHAR(50) NOT NULL DEFAULT 'ACADEMIC', status VARCHAR(20) NOT NULL DEFAULT 'PENDING', " +
                "discount_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00, scholarship_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00, " +
                "decision_note VARCHAR(500) NULL, requested_by VARCHAR(100) NULL, requested_at TIMESTAMP NULL, " +
                "reviewed_by VARCHAR(100) NULL, reviewed_at TIMESTAMP NULL, posted_by VARCHAR(100) NULL, posted_at TIMESTAMP NULL, " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE KEY uq_scholar_review_student_term_type (student_number, term_id, classification))");
    }

    private String cleanActor(String actor) {
        return actor == null || actor.isBlank() ? "SYSTEM" : actor.trim();
    }

    private String cleanNote(String note) {
        if (note == null || note.isBlank()) return null;
        String clean = note.trim();
        return clean.length() <= 500 ? clean : clean.substring(0, 500);
    }

    private String resolveScholarshipStudentNumber(String rawStudentRef) {
        if (rawStudentRef == null || rawStudentRef.isBlank()) {
            return null;
        }
        String clean = rawStudentRef.trim();
        try {
            return db.queryForObject(
                "SELECT student_number FROM students WHERE student_number = ? LIMIT 1",
                String.class, clean);
        } catch (Exception ignored) {
        }
        try {
            return db.queryForObject(
                "SELECT s.student_number FROM students s JOIN sys_users u ON u.username = s.student_number " +
                    "WHERE CAST(u.user_id AS CHAR) = ? OR u.username = ? LIMIT 1",
                String.class, clean, clean);
        } catch (Exception e) {
            return null;
        }
    }

    private double numericOrZero(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private int intOrZero(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String formatGrade(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private String formatUnits(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) {
            return String.format(java.util.Locale.US, "%.0f", value);
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    @Transactional
    public void syncCoreLedgerAssessment(String studentNumber) {
        try {
            Map<String, Object> student = db.queryForMap(
                "SELECT s.program_code, COALESCE(s.year_level,1) AS year_level, COALESCE(s.semester,1) AS semester, " +
                    "p.program_id FROM students s LEFT JOIN programs p ON p.program_code = s.program_code " +
                    "WHERE s.student_number = ? LIMIT 1",
                studentNumber);
            Integer termId = resolveCurrentTermId(studentNumber);
            Integer programId = student.get("program_id") != null ? ((Number) student.get("program_id")).intValue() : null;
            int yearLevel = ((Number) student.get("year_level")).intValue();
            int semester = ((Number) student.get("semester")).intValue();
            double units = currentTermUnits(studentNumber, termId);
            if (units <= 0) {
                db.update(
                    "DELETE FROM student_ledger WHERE student_id = ? AND transaction_type IN (" + ASSESSMENT_DEBIT_TYPES + ")",
                    studentNumber);
                return;
            }
            double rate = coreTuitionRate(programId, termId, yearLevel, semester);
            double tuition = units * rate;
            double misc = coreFeeSum(programId, termId, yearLevel, semester, "MISC");
            double other = coreFeeSum(programId, termId, yearLevel, semester, "OTHER");

            db.update(
                "DELETE FROM student_ledger WHERE student_id = ? AND transaction_type IN (" + ASSESSMENT_DEBIT_TYPES + ")",
                studentNumber);
            if (units > 0) {
                db.update("INSERT INTO student_ledger (student_id, transaction_type, description, debit) VALUES (?, 'TUITION_ASSESSMENT', ?, ?)",
                    studentNumber, "Tuition Assessment (" + units + " units @ " + rate + ")", tuition);
                db.update("INSERT INTO student_ledger (student_id, transaction_type, description, debit) VALUES (?, 'MISC_ASSESSMENT', 'Miscellaneous Fees', ?)",
                    studentNumber, misc);
                db.update("INSERT INTO student_ledger (student_id, transaction_type, description, debit) VALUES (?, 'OTHER_ASSESSMENT', 'Other Fees', ?)",
                    studentNumber, other);
            }
        } catch (Exception e) {
            log.warn("Unable to sync core ledger assessment for {}", studentNumber, e);
        }
    }

    private Integer resolveCurrentTermId(String studentNumber) {
        try {
            String sl = db.queryForObject("SELECT term_year FROM students WHERE student_number = ? LIMIT 1", String.class, studentNumber);
            if (sl != null && sl.length() >= 12 && sl.startsWith("SL") && !sl.startsWith("SL_")) {
                char sem = sl.charAt(11);
                String dbCode = sem + "1" + sl.substring(2, 10);
                return db.queryForObject("SELECT term_id FROM academic_terms WHERE term_code = ? LIMIT 1", Integer.class, dbCode);
            }
            if (sl != null && sl.startsWith("SL_") && sl.length() >= 13) {
                String dbCode = sl.charAt(3) + "1" + sl.substring(5);
                return db.queryForObject("SELECT term_id FROM academic_terms WHERE term_code = ? LIMIT 1", Integer.class, dbCode);
            }
        } catch (Exception ignored) {}
        return globalTermService.getCurrentTermId();
    }

    private double currentTermUnits(String studentNumber, Integer termId) {
        try {
            Double units;
            if (termId != null) {
                units = db.queryForObject(
                    "SELECT COALESCE(SUM(c.credit_units),0) FROM student_enlistments se " +
                        "JOIN courses c ON se.course_id = c.course_id " +
                        "JOIN class_sections cs ON se.section_id = cs.section_id " +
                        "WHERE se.student_id = ? AND cs.term_id = ?"
                        + enlistmentSchemaService.enlistmentStatusFilter(
                            EnlistmentSchemaService.Scope.COMMITTED_ONLY, "se"),
                    Double.class, studentNumber, termId);
            } else {
                units = db.queryForObject(
                    "SELECT COALESCE(SUM(c.credit_units),0) FROM student_enlistments se " +
                        "JOIN courses c ON se.course_id = c.course_id " +
                        "WHERE se.student_id = ?"
                        + enlistmentSchemaService.enlistmentStatusFilter(
                            EnlistmentSchemaService.Scope.COMMITTED_ONLY, "se"),
                    Double.class, studentNumber);
            }
            return units != null ? units : 0.0;
        } catch (Exception e) { return 0.0; }
    }

    private double coreTuitionRate(Integer programId, Integer termId, int yearLevel, int semester) {
        if (programId == null) {
            throw missingFee(programId, termId, yearLevel, semester, "PROGRAM");
        }
        Map<String, Double> rates = termFeeAdminService.getFeeRatesForScope(programId, termId, yearLevel, semester);
        Double rate = rates.get("TUITION_PER_UNIT");
        if (rate != null && rate > 0) {
            return rate;
        }
        throw missingFee(programId, termId, yearLevel, semester, "TUITION_PER_UNIT");
    }

    private double coreFeeSum(Integer programId, Integer termId, int yearLevel, int semester, String group) {
        if (programId == null) {
            throw missingFee(programId, termId, yearLevel, semester, "PROGRAM");
        }
        Map<String, Double> rates = termFeeAdminService.getFeeRatesForScope(programId, termId, yearLevel, semester);
        double sum = 0.0;
        String prefix = group != null ? group.trim().toUpperCase() + "_" : "";
        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                sum += entry.getValue();
            }
        }
        return sum;
    }

    private IllegalStateException missingFee(Integer programId, Integer termId, int yearLevel, int semester, String feeCode) {
        return new IllegalStateException(String.format(
            "Missing official fee configuration (%s) in program_fee_settings for programId=%s termId=%s year=%s semester=%s",
            feeCode, programId, termId, yearLevel, semester));
    }

    @Transactional
    public void runGradeBasedRenewal(int currentSemester, int currentYear) {
        try {
            int currentRank = (currentYear * 10) + currentSemester;
            List<String> scholars = db.queryForList("SELECT student_number FROM students WHERE scholarship_type IS NOT NULL AND scholarship_type != 'NONE'", String.class);
            for (String sid : scholars) {
                Integer failCount = db.queryForObject(
                    "SELECT COUNT(*) FROM grades g " +
                        "JOIN curriculum_courses cc ON cc.course_id = g.course_id " +
                        "WHERE g.student_id = ? AND " + GradeOutcomeSql.failed("g") +
                        " AND ((cc.year_level * 10) + cc.semester_number < ?)",
                    Integer.class, sid, currentRank);
                if (failCount != null && failCount > 0) {
                    grantExternalScholarship(sid, "NONE", 0.0, "REVOKED");
                }
            }
        } catch (Exception ignore) {}
    }

    public Map<String, Object> buildFinancialSummary(Map<String, Object> user) {
        Map<String, Object> m = new HashMap<>();
        try {
            String sid = (String) user.get("username");
            TermFeeBreakdown fees = computeCurrentTermFees(sid);
            double forwarded = getForwardedBalanceNet(sid);
            double totalAssessment = fees.totalFees() + forwarded;
            double totalPaid = sumCompletedPaymentsForCurrentTerm(sid);
            double scholarDiscount = computeScholarDiscount(sid, totalAssessment);
            double outstanding = Math.max(0, totalAssessment - (totalPaid + scholarDiscount));
            m.put("totalAssessment", totalAssessment);
            m.put("totalPaid", totalPaid);
            m.put("balance", outstanding);
            m.put("outstandingBalance", outstanding);
            m.put("balanceForwarded", forwarded);
            double accountingBlockThreshold = PolicySettings.accountingBlockThreshold(db);
            m.put("accountingBlocked", outstanding >= accountingBlockThreshold);
            m.put("has_accounting_block", outstanding >= accountingBlockThreshold);
            String schType = db.queryForObject("SELECT scholarship_type FROM students WHERE student_number = ?", String.class, sid);
            m.put("scholarshipType", schType != null ? schType : "NONE");
        } catch (Exception e) {
            m.put("totalAssessment", 0.0);
            m.put("totalPaid", 0.0);
            m.put("balance", 0.0);
            m.put("scholarshipType", "NONE");
        }
        return m;
    }
}




