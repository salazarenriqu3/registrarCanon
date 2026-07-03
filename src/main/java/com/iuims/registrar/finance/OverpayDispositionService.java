package com.iuims.registrar.finance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OverpayDispositionService {

    private final JdbcTemplate db;
    private final StudentOverpaymentBalancePort balancePort;

    @Autowired
    public OverpayDispositionService(JdbcTemplate db,
                                     StudentOverpaymentBalancePort balancePort) {
        this.db = db;
        this.balancePort = balancePort;
    }

    public record DispositionResult(
        boolean success,
        String message,
        double pendingRemaining,
        double forwardNet
    ) {
        public static DispositionResult ok(String message, double pendingRemaining, double forwardNet) {
            return new DispositionResult(true, message, pendingRemaining, forwardNet);
        }

        public static DispositionResult fail(String message) {
            return new DispositionResult(false, message, 0, 0);
        }
    }

    @Transactional
    public DispositionResult applyAsCredit(String studentNumber, double amount, String decidedBy, String remarks) {
        double pending = balancePort.getPendingTermCredit(studentNumber);
        double credit = amount > 0.01 ? amount : pending;
        return postDisposition(studentNumber, 0, credit, decidedBy, remarks);
    }

    @Transactional
    public DispositionResult refundAsCash(String studentNumber, double amount, String decidedBy, String remarks) {
        double pending = balancePort.getPendingTermCredit(studentNumber);
        double refund = amount > 0.01 ? amount : pending;
        return postDisposition(studentNumber, refund, 0, decidedBy, remarks);
    }

    @Transactional
    public DispositionResult splitDisposition(String studentNumber, double refundAmount, double creditAmount,
                                                String decidedBy, String remarks) {
        if (refundAmount < 0 || creditAmount < 0) {
            return DispositionResult.fail("Amounts cannot be negative.");
        }
        if (refundAmount <= 0.01 && creditAmount <= 0.01) {
            return DispositionResult.fail("Enter a refund and/or credit amount.");
        }
        return postDisposition(studentNumber, refundAmount, creditAmount, decidedBy, remarks);
    }

    private DispositionResult postDisposition(String studentNumber, double refundAmount, double creditAmount,
                                              String decidedBy, String remarks) {
        if (studentNumber == null || studentNumber.isBlank()) {
            return DispositionResult.fail("Student number is required.");
        }
        String sn = studentNumber.trim();
        double pending = balancePort.getPendingTermCredit(sn);
        double total = refundAmount + creditAmount;
        if (total <= 0.01) {
            return DispositionResult.fail("Amount must be greater than zero.");
        }
        if (total > pending + 0.01) {
            return DispositionResult.fail(String.format(
                "Total disposition (₱%,.2f) exceeds pending overpayment (₱%,.2f).", total, pending));
        }

        String note = remarks != null && !remarks.isBlank() ? remarks.trim() : "Overpayment disposition";

        if (creditAmount > 0.01) {
            db.update(
                "INSERT INTO student_ledger (student_id, transaction_type, description, debit, credit) " +
                    "VALUES (?, ?, ?, ?, 0)",
                sn, LedgerTransactionTypes.PENDING_TERM_CREDIT,
                "Applied as account credit — " + note, creditAmount);
            db.update(
                "INSERT INTO student_ledger (student_id, transaction_type, description, debit, credit) " +
                    "VALUES (?, ?, ?, 0, ?)",
                sn, LedgerTransactionTypes.FORWARDED_BALANCE,
                "Credit applied from prior-term overpayment", creditAmount);
        }

        if (refundAmount > 0.01) {
            db.update(
                "INSERT INTO student_ledger (student_id, transaction_type, description, debit, credit) " +
                    "VALUES (?, ?, ?, ?, 0)",
                sn, LedgerTransactionTypes.PENDING_TERM_CREDIT,
                "Refunded as cash — " + note, refundAmount);
            db.update(
                "INSERT INTO student_ledger (student_id, transaction_type, description, debit, credit) " +
                    "VALUES (?, ?, ?, ?, 0)",
                sn, LedgerTransactionTypes.REFUND_PAYOUT,
                "Cash refund — " + note, refundAmount);
            insertRefundPayment(sn, refundAmount, note);
        }

        insertDispositionAudit(sn, pending, refundAmount, creditAmount, decidedBy, note);
        double pendingRemaining = balancePort.getPendingTermCredit(sn);
        double forwardNet = balancePort.getForwardedBalanceNet(sn);
        return DispositionResult.ok(
            String.format("Disposition recorded: refund ₱%,.2f, credit ₱%,.2f.", refundAmount, creditAmount),
            pendingRemaining, forwardNet);
    }

    private void insertDispositionAudit(String studentNumber, double pendingBefore,
                                        double refunded, double credited, String decidedBy, String remarks) {
        try {
            String closingSl = null;
            try {
                closingSl = db.queryForObject(
                    "SELECT term_year FROM students WHERE student_number = ? LIMIT 1",
                    String.class, studentNumber);
            } catch (Exception ignored) {
            }
            db.update(
                "INSERT INTO student_overpay_dispositions " +
                    "(student_id, source_closing_sl, pending_amount, refunded_amount, credited_amount, decided_by, remarks) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                studentNumber, closingSl, pendingBefore, refunded, credited, decidedBy, remarks);
        } catch (Exception e) {
            // Table may not exist on older DBs until migration runs — ledger rows are source of truth.
        }
    }

    private void insertRefundPayment(String studentNumber, double amount, String remarks) {
        try {
            String txId = "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            int semester = 1;
            int yearLevel = 1;
            String termYear = null;
            try {
                MapRow r = db.queryForObject(
                    "SELECT COALESCE(semester,1) AS sem, COALESCE(year_level,1) AS yl, term_year AS ty " +
                        "FROM students WHERE student_number = ? LIMIT 1",
                    (rs, rowNum) -> new MapRow(rs.getInt("sem"), rs.getInt("yl"), rs.getString("ty")),
                    studentNumber);
                semester = r.semester;
                yearLevel = r.yearLevel;
                termYear = r.termYear;
            } catch (Exception ignored) {
            }
            try {
                db.update(
                    "INSERT INTO payments (transaction_id, or_number, reference_number, amount, payment_method, " +
                        "semester, year_level, term_year, remarks, payment_date, status, direction) " +
                        "VALUES (?, ?, ?, ?, 'Cash (Refund)', ?, ?, ?, ?, NOW(), 'COMPLETED', 'OUT')",
                    txId, txId, studentNumber, amount, semester, yearLevel, termYear, remarks);
            } catch (Exception e) {
                db.update(
                    "INSERT INTO payments (transaction_id, or_number, reference_number, amount, payment_method, " +
                        "semester, year_level, term_year, remarks, payment_date, status) " +
                        "VALUES (?, ?, ?, ?, 'Cash (Refund)', ?, ?, ?, ?, NOW(), 'COMPLETED')",
                    txId, txId, studentNumber, amount, semester, yearLevel, termYear, remarks);
            }
        } catch (Exception ignored) {
        }
    }

    private record MapRow(int semester, int yearLevel, String termYear) {}
}
