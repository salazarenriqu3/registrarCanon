package com.iuims.registrar.withdrawal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.iuims.registrar.core.EnlistmentSchemaService;
import com.iuims.registrar.core.GlobalTermService;
import com.iuims.registrar.core.StudentProfileService;
import com.iuims.registrar.finance.TermFeeAdminService;
import com.iuims.registrar.forms.RegFormEventService;
import com.iuims.registrar.forms.StudentDocumentTrailService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class WithdrawalService {

    public static final String STATUS_PENDING_REGISTRAR = "PENDING_REGISTRAR";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    private static final String REASON_CLASS_DROP = "CLASS_DROP";
    private static final String REASON_SHIFTING = "SHIFTING";

    private final JdbcTemplate db;
    private final ScholarEnrollmentService scholarEnrollmentService;
    private final StudentDocumentTrailService documentTrailService;
    private final RegFormEventService regFormEventService;
    private final GlobalTermService globalTermService;
    private final EnlistmentSchemaService enlistmentSchemaService;
    private final StudentProfileService studentProfileService;
    private final TermFeeAdminService termFeeAdminService;

    public WithdrawalService(JdbcTemplate db, ScholarEnrollmentService scholarEnrollmentService,
                             StudentDocumentTrailService documentTrailService,
                             RegFormEventService regFormEventService,
                             GlobalTermService globalTermService,
                             EnlistmentSchemaService enlistmentSchemaService,
                             StudentProfileService studentProfileService,
                             TermFeeAdminService termFeeAdminService) {
        this.db = db;
        this.scholarEnrollmentService = scholarEnrollmentService;
        this.documentTrailService = documentTrailService;
        this.regFormEventService = regFormEventService;
        this.globalTermService = globalTermService;
        this.enlistmentSchemaService = enlistmentSchemaService;
        this.studentProfileService = studentProfileService;
        this.termFeeAdminService = termFeeAdminService;
    }

    public void ensureSchema() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS withdrawal_reasons (
                reason_code VARCHAR(40) PRIMARY KEY,
                reason_label VARCHAR(160) NOT NULL,
                is_active TINYINT(1) NOT NULL DEFAULT 1,
                sort_order INT NOT NULL DEFAULT 100
            )
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS student_withdrawal_requests (
                request_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                student_number VARCHAR(100) NOT NULL,
                archive_key VARCHAR(80) NULL,
                section_id INT NOT NULL,
                course_id INT NOT NULL,
                term_id INT NULL,
                reason_code VARCHAR(40) NOT NULL,
                remarks VARCHAR(500) NULL,
                requested_on DATE NULL,
                enlisted_at TIMESTAMP NULL,
                days_enrolled_at_request INT NULL,
                timing_bucket VARCHAR(40) NULL,
                charge_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
                estimated_charge DECIMAL(12,2) NOT NULL DEFAULT 0,
                deadline_blocked TINYINT(1) NOT NULL DEFAULT 0,
                policy_note VARCHAR(255) NULL,
                status VARCHAR(40) NOT NULL DEFAULT 'PENDING_REGISTRAR',
                requested_by VARCHAR(100) NULL,
                requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                dean_approved_by VARCHAR(100) NULL,
                dean_approved_at TIMESTAMP NULL,
                registrar_approved_by VARCHAR(100) NULL,
                registrar_approved_at TIMESTAMP NULL,
                rejected_by VARCHAR(100) NULL,
                rejected_at TIMESTAMP NULL,
                rejection_reason VARCHAR(500) NULL,
                completed_at TIMESTAMP NULL,
                CONSTRAINT fk_swr_reason FOREIGN KEY (reason_code) REFERENCES withdrawal_reasons(reason_code)
            )
            """);
        addColumnIfMissing("student_withdrawal_requests", "requested_on", "DATE NULL");
        addColumnIfMissing("student_withdrawal_requests", "enlisted_at", "TIMESTAMP NULL");
        addColumnIfMissing("student_withdrawal_requests", "days_enrolled_at_request", "INT NULL");
        addColumnIfMissing("student_withdrawal_requests", "timing_bucket", "VARCHAR(40) NULL");
        addColumnIfMissing("student_withdrawal_requests", "charge_percent", "DECIMAL(5,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("student_withdrawal_requests", "estimated_charge", "DECIMAL(12,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("student_withdrawal_requests", "deadline_blocked", "TINYINT(1) NOT NULL DEFAULT 0");
        addColumnIfMissing("student_withdrawal_requests", "policy_note", "VARCHAR(255) NULL");
        addColumnIfMissing("student_withdrawal_requests", "archive_key", "VARCHAR(80) NULL");
        addColumnIfMissing("student_withdrawal_requests", "withdrawal_scope", "VARCHAR(30) NOT NULL DEFAULT 'SINGLE_SUBJECT'");
        addColumnIfMissing("student_withdrawal_requests", "subject_count", "INT NOT NULL DEFAULT 1");
        addColumnIfMissing("student_withdrawal_requests", "approval_source", "VARCHAR(40) NULL");
        addColumnIfMissing("academic_term_policies", "midterm_exam_date", "DATE NULL");
        db.execute("""
            CREATE TABLE IF NOT EXISTS student_withdrawal_request_lines (
                line_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                request_id BIGINT NOT NULL,
                student_number VARCHAR(100) NOT NULL,
                archive_key VARCHAR(80) NULL,
                section_id INT NOT NULL,
                course_id INT NOT NULL,
                requested_on DATE NULL,
                enlisted_at TIMESTAMP NULL,
                days_enrolled_at_request INT NULL,
                timing_bucket VARCHAR(40) NULL,
                charge_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
                estimated_charge DECIMAL(12,2) NOT NULL DEFAULT 0,
                policy_note VARCHAR(255) NULL,
                status VARCHAR(40) NOT NULL DEFAULT 'PENDING_REGISTRAR',
                completed_at TIMESTAMP NULL,
                CONSTRAINT fk_swrl_request FOREIGN KEY (request_id) REFERENCES student_withdrawal_requests(request_id)
            )
            """);
        addColumnIfMissing("student_withdrawal_request_lines", "requested_on", "DATE NULL");
        addColumnIfMissing("student_withdrawal_request_lines", "enlisted_at", "TIMESTAMP NULL");
        addColumnIfMissing("student_withdrawal_request_lines", "days_enrolled_at_request", "INT NULL");
        addColumnIfMissing("student_withdrawal_request_lines", "timing_bucket", "VARCHAR(40) NULL");
        addColumnIfMissing("student_withdrawal_request_lines", "charge_percent", "DECIMAL(5,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("student_withdrawal_request_lines", "estimated_charge", "DECIMAL(12,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("student_withdrawal_request_lines", "policy_note", "VARCHAR(255) NULL");
        addColumnIfMissing("student_withdrawal_request_lines", "archive_key", "VARCHAR(80) NULL");
        normalizeColumnCollation("withdrawal_reasons", "reason_code", "VARCHAR(40) NOT NULL");
        normalizeColumnCollation("withdrawal_reasons", "reason_label", "VARCHAR(160) NOT NULL");
        normalizeColumnCollation("student_withdrawal_requests", "student_number", "VARCHAR(100) NOT NULL");
        normalizeColumnCollation("student_withdrawal_requests", "archive_key", "VARCHAR(80) NULL");
        normalizeColumnCollation("student_withdrawal_requests", "reason_code", "VARCHAR(40) NOT NULL");
        normalizeColumnCollation("student_withdrawal_request_lines", "student_number", "VARCHAR(100) NOT NULL");
        normalizeColumnCollation("student_withdrawal_request_lines", "archive_key", "VARCHAR(80) NULL");
        db.update("""
            UPDATE student_withdrawal_requests
            SET status = ?, approval_source = COALESCE(approval_source, 'REGISTRAR_WORKFLOW')
            WHERE request_id >= 0 AND status = 'PENDING_DEAN'
            """, STATUS_PENDING_REGISTRAR);
        db.update("UPDATE student_withdrawal_request_lines SET status = ? WHERE line_id >= 0 AND status = 'PENDING_DEAN'",
            STATUS_PENDING_REGISTRAR);
        try {
            db.execute("CREATE INDEX idx_swr_status ON student_withdrawal_requests (status)");
        } catch (Exception ignored) {
        }
        try {
            db.execute("CREATE INDEX idx_swr_student ON student_withdrawal_requests (student_number)");
        } catch (Exception ignored) {
        }
        try {
            db.execute("CREATE INDEX idx_swr_archive ON student_withdrawal_requests (archive_key)");
        } catch (Exception ignored) {
        }
        try {
            db.execute("CREATE INDEX idx_swr_section ON student_withdrawal_requests (student_number, section_id, status)");
        } catch (Exception ignored) {
        }
        seedDefaultReasons();
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class, tableName, columnName);
            if (count == null || count == 0) {
                db.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
            }
        } catch (Exception ignored) {
        }
    }

    private void normalizeColumnCollation(String tableName, String columnName, String definition) {
        try {
            db.execute(
                "ALTER TABLE " + tableName + " MODIFY " + columnName + " " + definition +
                    " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (Exception ignored) {
        }
    }

    private void seedDefaultReasons() {
        db.update("""
            INSERT IGNORE INTO withdrawal_reasons (reason_code, reason_label, sort_order) VALUES
            ('ACADEMIC_LOAD', 'Academic load adjustment', 10),
            ('CLASS_DROP', 'Class drop', 15),
            ('SCHEDULE_CONFLICT', 'Schedule conflict', 20),
            ('MEDICAL', 'Medical / health reason', 30),
            ('FINANCIAL', 'Financial reason', 40),
            ('SHIFTING', 'Shifting', 45),
            ('TRANSFER', 'Transfer / change of school', 50),
            ('OTHER', 'Other reason', 100)
            """);
    }

    public List<Map<String, Object>> listActiveReasons() {
        return listStandardReasons();
    }

    public List<Map<String, Object>> listStandardReasons() {
        return listReasons("AND reason_code NOT IN ('SHIFTING', 'CLASS_DROP') ");
    }

    public List<Map<String, Object>> listShiftCleanupReasons() {
        return listReasons("AND reason_code = 'SHIFTING' ");
    }

    private List<Map<String, Object>> listReasons(String extraWhereClause) {
        ensureSchema();
        return db.queryForList(
            "SELECT reason_code, reason_label FROM withdrawal_reasons " +
                "WHERE COALESCE(is_active, 1) = 1 " +
                (extraWhereClause == null ? "" : extraWhereClause) +
                "ORDER BY sort_order, reason_label");
    }

    public List<Map<String, Object>> listStudentRequests(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) return List.of();
        ensureSchema();
        return db.queryForList(baseRequestSql() +
                "WHERE wr.student_number COLLATE utf8mb4_uca1400_ai_ci = ? ORDER BY wr.requested_at DESC, wr.request_id DESC",
            studentNumber.trim());
    }

    public List<Map<String, Object>> listRequests(String status) {
        ensureSchema();
        if (status == null || status.isBlank()) {
            return db.queryForList(baseRequestSql() + "ORDER BY wr.requested_at DESC, wr.request_id DESC");
        }
        return db.queryForList(baseRequestSql() +
                "WHERE wr.status COLLATE utf8mb4_uca1400_ai_ci = ? ORDER BY wr.requested_at ASC, wr.request_id ASC",
            status.trim().toUpperCase());
    }

    private String baseRequestSql() {
        return """
            SELECT wr.request_id, wr.student_number, wr.archive_key,
                   COALESCE(s.real_name, wr.student_number COLLATE utf8mb4_uca1400_ai_ci) AS student_name,
                   s.program_code, s.year_level, wr.section_id, wr.course_id, wr.term_id,
                   c.course_code, c.course_title, c.credit_units, c.lec_units, c.lab_units,
                   COALESCE(c.component_type, 'SINGLE') AS component_type, cs.section_code,
                   wr.reason_code, rr.reason_label, wr.remarks, wr.status,
                   wr.withdrawal_scope, wr.subject_count, wr.approval_source,
                   wr.requested_on, wr.enlisted_at, wr.days_enrolled_at_request,
                   wr.timing_bucket, wr.charge_percent, wr.estimated_charge,
                   wr.deadline_blocked, wr.policy_note,
                   wr.requested_by, wr.requested_at, wr.dean_approved_by, wr.dean_approved_at,
                   wr.registrar_approved_by, wr.registrar_approved_at, wr.rejected_by,
                   wr.rejected_at, wr.rejection_reason, wr.completed_at
            FROM student_withdrawal_requests wr
            LEFT JOIN students s
                   ON s.student_number = wr.student_number COLLATE utf8mb4_uca1400_ai_ci
            LEFT JOIN courses c ON c.course_id = wr.course_id
            LEFT JOIN class_sections cs ON cs.section_id = wr.section_id
            LEFT JOIN withdrawal_reasons rr
                   ON rr.reason_code COLLATE utf8mb4_unicode_ci = wr.reason_code COLLATE utf8mb4_unicode_ci
            """;
    }

    // Legacy request-builder retained for archived records; live UI now uses direct registrar execution.
    @Transactional
    public long createRequest(String studentNumber, Integer sectionId, String reasonCode, String remarks, String requestedBy) {
        ensureSchema();
        String sn = clean(studentNumber);
        String rc = clean(reasonCode).toUpperCase();
        if (sn.isEmpty()) throw new IllegalArgumentException("Student number is required.");
        if (sectionId == null || sectionId <= 0) throw new IllegalArgumentException("Subject section is required.");
        if (rc.isEmpty()) throw new IllegalArgumentException("Withdrawal reason is required.");
        ensureReasonExists(rc);

        String withdrawalBlock = withdrawalBlockMessage();
        if (withdrawalBlock != null) {
            throw new IllegalArgumentException(withdrawalBlock);
        }

        Map<String, Object> enlistment = findActiveEnlistment(sn, sectionId);
        WithdrawalPolicySnapshot policy = computePolicySnapshot(sn, enlistment);
        if (policy.deadlineBlocked()) {
            throw new IllegalArgumentException(policy.policyNote());
        }
        ensureNoActiveRequest(sn, sectionId);
        long requestId = insertPendingRequest(
            sn, rc, remarks, cleanActor(requestedBy), "SINGLE_SUBJECT", List.of(enlistment), List.of(policy));
        documentTrailService.recordStudentEvent(
            sn,
            "STUDENT",
            "WITHDRAWAL",
            "WITHDRAWAL_REQUESTED",
            "Withdrawal request submitted",
            "Reason: " + rc + (remarks != null && !remarks.isBlank() ? ". " + truncate(remarks, 500) : "") +
                ". Request #" + requestId + ".",
            requestedBy,
            requestId,
            "student_withdrawal_requests",
            String.valueOf(requestId));
        return requestId;
    }

    // Legacy request-builder retained for archived records; live UI now uses direct registrar execution.
    @Transactional
    public long createFullCurrentTermRequest(String studentNumber, String reasonCode, String remarks, String requestedBy) {
        ensureSchema();
        String sn = clean(studentNumber);
        String rc = clean(reasonCode).toUpperCase();
        if (sn.isEmpty()) throw new IllegalArgumentException("Student number is required.");
        if (rc.isEmpty()) throw new IllegalArgumentException("Withdrawal reason is required.");
        ensureReasonExists(rc);

        String withdrawalBlock = withdrawalBlockMessage();
        if (withdrawalBlock != null) {
            throw new IllegalArgumentException(withdrawalBlock);
        }

        Integer currentTermId = requireCurrentTermId();
        List<Map<String, Object>> currentLoad = findCurrentTermEnlistments(sn, currentTermId);
        if (currentLoad.isEmpty()) {
            throw new IllegalArgumentException("The student has no current-term subjects to withdraw.");
        }

        List<WithdrawalPolicySnapshot> policies = new ArrayList<>();
        List<Integer> sectionIds = new ArrayList<>();
        for (Map<String, Object> enlistment : currentLoad) {
            sectionIds.add(((Number) enlistment.get("section_id")).intValue());
            policies.add(requireAllowedPolicy(sn, enlistment));
        }
        ensureNoPendingRequests(sn, sectionIds);

        long requestId = insertPendingRequest(
            sn, rc, remarks, cleanActor(requestedBy), "FULL_CURRENT_TERM", currentLoad, policies);
        documentTrailService.recordStudentEvent(
            sn,
            "STUDENT",
            "WITHDRAWAL",
            "WITHDRAWAL_REQUESTED",
            "Full current-term withdrawal request submitted",
            "Reason: " + rc + ". Request #" + requestId + " covers " + currentLoad.size() + " subject(s)." +
                (remarks != null && !remarks.isBlank() ? " " + truncate(remarks, 400) : ""),
            cleanActor(requestedBy),
            requestId,
            "student_withdrawal_requests",
            String.valueOf(requestId));
        return requestId;
    }

    @Transactional
    public DirectDropResult dropSubjectByRegistrar(String studentNumber, Integer sectionId,
                                                   String reasonCode, String remarks, String actor) {
        return dropSubjectByRegistrar(studentNumber, sectionId, remarks, actor);
    }

    @Transactional
    public DirectDropResult dropSubjectByRegistrar(String studentNumber, Integer sectionId,
                                                   String remarks, String actor) {
        String sn = validateSubjectDrop(studentNumber);
        Map<String, Object> enlistment = findActiveEnlistment(sn, sectionId);
        Integer currentTermId = requireCurrentTermId();
        if (!currentTermId.equals(numberAsInteger(enlistment.get("term_id")))) {
            throw new IllegalArgumentException("Only a current-term class can be dropped from Student Profile.");
        }

        supersedePendingRequests(sn, List.of(sectionId), cleanActor(actor));
        WithdrawalPolicySnapshot policy = requireAllowedPolicy(sn, enlistment);
        String actedBy = cleanActor(actor);
        long requestId = insertCompletedDirectDrop(
            sn, REASON_CLASS_DROP, remarks, actedBy, "SINGLE_SUBJECT", List.of(enlistment), List.of(policy));

        scholarEnrollmentService.dropSubjectByEnlistmentId(
            ((Number) enlistment.get("enlistment_id")).longValue(),
            policy.estimatedCharge(), policy.policyNote());
        recordDirectDropEvents(sn, requestId, actedBy, "SINGLE_SUBJECT", List.of(enlistment),
            policy.estimatedCharge(), remarks);
        return new DirectDropResult(requestId, 1, policy.estimatedCharge(), "SINGLE_SUBJECT");
    }

    @Transactional
    public DirectDropResult dropStudentByRegistrar(String studentNumber, String reasonCode,
                                                   String remarks, String actor) {
        String sn = validateDirectDrop(studentNumber, reasonCode);
        Integer currentTermId = requireCurrentTermId();
        List<Map<String, Object>> currentLoad = findCurrentTermEnlistments(sn, currentTermId);
        String rc = clean(reasonCode).toUpperCase();
        String actedBy = cleanActor(actor);
        if (currentLoad.isEmpty()) {
            long requestId = insertCompletedZeroLoadStudentDrop(
                sn, rc, remarks, actedBy, currentTermId);
            persistOutstandingBalanceHold(requestId, sn);
            markStudentWithdrawn(sn);
            recordDirectDropEvents(sn, requestId, actedBy, "FULL_CURRENT_TERM", List.of(), 0.0, remarks);
            return new DirectDropResult(requestId, 0, 0.0, "FULL_CURRENT_TERM");
        }

        List<WithdrawalPolicySnapshot> policies = new ArrayList<>();
        List<Integer> sectionIds = new ArrayList<>();
        for (Map<String, Object> enlistment : currentLoad) {
            int sectionId = ((Number) enlistment.get("section_id")).intValue();
            sectionIds.add(sectionId);
            policies.add(requireAllowedPolicy(sn, enlistment));
        }

        supersedePendingRequests(sn, sectionIds, actedBy);
        long requestId = insertCompletedDirectDrop(
            sn, rc, remarks, actedBy, "FULL_CURRENT_TERM", currentLoad, policies);
        double totalCharge = 0.0;
        for (int i = 0; i < currentLoad.size(); i++) {
            Map<String, Object> enlistment = currentLoad.get(i);
            WithdrawalPolicySnapshot policy = policies.get(i);
            scholarEnrollmentService.dropSubjectByEnlistmentId(
                ((Number) enlistment.get("enlistment_id")).longValue(),
                policy.estimatedCharge(), policy.policyNote());
            totalCharge += policy.estimatedCharge();
        }
        persistOutstandingBalanceHold(requestId, sn);
        markStudentWithdrawn(sn);
        recordDirectDropEvents(sn, requestId, actedBy, "FULL_CURRENT_TERM", currentLoad, totalCharge, remarks);
        return new DirectDropResult(requestId, currentLoad.size(), totalCharge, "FULL_CURRENT_TERM");
    }

    @Transactional
    public DirectDropResult clearCurrentTermLoadForProgramShift(String studentNumber, String remarks, String actor) {
        return clearCurrentTermLoadForProgramShift(studentNumber, "SHIFTING", remarks, actor);
    }

    @Transactional
    public DirectDropResult clearCurrentTermLoadForProgramShift(String studentNumber, String reasonCode,
                                                               String remarks, String actor) {
        ensureSchema();
        String sn = clean(studentNumber);
        String rc = clean(reasonCode).toUpperCase();
        if (sn.isEmpty()) throw new IllegalArgumentException("Student number is required.");
        if (rc.isEmpty()) throw new IllegalArgumentException("Withdrawal reason is required.");
        ensureReasonExists(rc);
        if (isWithdrawnOrInactiveStudent(sn)) {
            throw new IllegalArgumentException("Student is already withdrawn from school.");
        }
        Integer currentTermId = requireCurrentTermId();
        List<Map<String, Object>> currentLoad = findCurrentTermEnlistments(sn, currentTermId);
        if (currentLoad.isEmpty()) {
            return new DirectDropResult(0L, 0, 0.0, "SHIFT_PROGRAM_CLEANUP");
        }

        List<WithdrawalPolicySnapshot> policies = new ArrayList<>();
        List<Integer> sectionIds = new ArrayList<>();
        for (Map<String, Object> enlistment : currentLoad) {
            sectionIds.add(((Number) enlistment.get("section_id")).intValue());
            policies.add(computePolicySnapshot(sn, enlistment));
        }

        String actedBy = cleanActor(actor);
        supersedePendingRequests(sn, sectionIds, actedBy);
        long requestId = insertCompletedDirectDrop(
            sn,
            rc,
            remarks,
            actedBy,
            "SHIFT_PROGRAM_CLEANUP",
            currentLoad,
            policies,
            "REGISTRAR_SHIFT");
        double totalCharge = 0.0;
        for (int i = 0; i < currentLoad.size(); i++) {
            Map<String, Object> enlistment = currentLoad.get(i);
            WithdrawalPolicySnapshot policy = policies.get(i);
            scholarEnrollmentService.dropSubjectByEnlistmentId(
                ((Number) enlistment.get("enlistment_id")).longValue(),
                policy.estimatedCharge(),
                policy.policyNote());
            totalCharge += policy.estimatedCharge();
        }
        recordShiftCleanupEvents(sn, requestId, actedBy, currentLoad, totalCharge, remarks);
        return new DirectDropResult(requestId, currentLoad.size(), totalCharge, "SHIFT_PROGRAM_CLEANUP");
    }

    private long insertCompletedZeroLoadStudentDrop(String studentNumber, String reasonCode,
                                                    String remarks, String actor, Integer termId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String note = "Registrar completed full-student withdrawal with no remaining current-term subject load.";
        String archiveKey = studentProfileService.ensureArchiveKey(studentNumber);
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO student_withdrawal_requests
                    (student_number, archive_key, section_id, course_id, term_id, reason_code, remarks, requested_on,
                     enlisted_at, days_enrolled_at_request, timing_bucket, charge_percent, estimated_charge,
                     deadline_blocked, policy_note, status, requested_by, registrar_approved_by,
                     registrar_approved_at, completed_at, withdrawal_scope, subject_count, approval_source)
                VALUES (?, ?, 0, 0, ?, ?, ?, CURRENT_DATE,
                        NULL, NULL, 'FULL_CURRENT_TERM', 0, 0, 0, ?, ?, ?, ?, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, 'FULL_CURRENT_TERM', 0, ?)
                """, new String[]{"request_id"});
            ps.setString(1, studentNumber);
            ps.setString(2, archiveKey);
            if (termId != null) {
                ps.setInt(3, termId);
            } else {
                ps.setNull(3, java.sql.Types.INTEGER);
            }
            ps.setString(4, reasonCode);
            ps.setString(5, truncate(remarks, 500));
            ps.setString(6, note);
            ps.setString(7, STATUS_APPROVED);
            ps.setString(8, actor);
            ps.setString(9, actor);
            ps.setString(10, "REGISTRAR_DIRECT");
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String validateDirectDrop(String studentNumber, String reasonCode) {
        ensureSchema();
        String sn = clean(studentNumber);
        String rc = clean(reasonCode).toUpperCase();
        if (sn.isEmpty()) throw new IllegalArgumentException("Student number is required.");
        if (rc.isEmpty()) throw new IllegalArgumentException("Drop reason is required.");
        ensureReasonExists(rc);
        if (isWithdrawnOrInactiveStudent(sn)) {
            throw new IllegalArgumentException("Student is already withdrawn from school.");
        }
        String withdrawalBlock = withdrawalBlockMessage();
        if (withdrawalBlock != null) throw new IllegalArgumentException(withdrawalBlock);
        return sn;
    }

    private String validateSubjectDrop(String studentNumber) {
        ensureSchema();
        String sn = clean(studentNumber);
        if (sn.isEmpty()) throw new IllegalArgumentException("Student number is required.");
        if (isWithdrawnOrInactiveStudent(sn)) {
            throw new IllegalArgumentException("Student is already withdrawn from school.");
        }
        String withdrawalBlock = withdrawalBlockMessage();
        if (withdrawalBlock != null) throw new IllegalArgumentException(withdrawalBlock);
        return sn;
    }

    private WithdrawalPolicySnapshot requireAllowedPolicy(String studentNumber, Map<String, Object> enlistment) {
        WithdrawalPolicySnapshot policy = computePolicySnapshot(studentNumber, enlistment);
        if (policy.deadlineBlocked()) throw new IllegalArgumentException(policy.policyNote());
        return policy;
    }

    private Integer requireCurrentTermId() {
        Integer termId = globalTermService.getCurrentTermId();
        if (termId == null) throw new IllegalStateException("No active registrar term is configured.");
        return termId;
    }

    private String withdrawalBlockMessage() {
        try {
            String raw = db.queryForObject(
                "SELECT setting_value FROM system_settings WHERE setting_key = ? LIMIT 1",
                String.class, "ADD_DROP_CLOSE_DATE");
            if (raw != null && !raw.isBlank() && LocalDate.now().isAfter(LocalDate.parse(raw.trim()))) {
                return "Add/drop period has closed. New drop requests are not accepted.";
            }
        } catch (Exception ignored) {
            // An unset or legacy policy table means no configured withdrawal deadline.
        }
        return null;
    }

    private List<Map<String, Object>> findCurrentTermEnlistments(String studentNumber, Integer termId) {
        return db.queryForList("""
            SELECT se.enlistment_id, se.student_id, se.course_id, se.section_id, se.enlisted_date,
                   cs.term_id, c.course_code, c.course_title, c.credit_units, c.lec_units, c.lab_units,
                   COALESCE(c.component_type, 'SINGLE') AS component_type
            FROM student_enlistments se
            JOIN class_sections cs ON cs.section_id = se.section_id
            JOIN courses c ON c.course_id = se.course_id
            WHERE se.student_id = ? AND cs.term_id = ?
            """ + enlistmentSchemaService.enlistmentStatusFilter(
                EnlistmentSchemaService.Scope.COMMITTED_ONLY, "se") +
            " ORDER BY c.course_code, se.enlistment_id", studentNumber, termId);
    }

    private long insertPendingRequest(String studentNumber, String reasonCode, String remarks,
                                      String requestedBy, String scope,
                                      List<Map<String, Object>> enlistments,
                                      List<WithdrawalPolicySnapshot> policies) {
        Map<String, Object> first = enlistments.get(0);
        WithdrawalPolicySnapshot firstPolicy = policies.get(0);
        double totalCharge = policies.stream().mapToDouble(WithdrawalPolicySnapshot::estimatedCharge).sum();
        double maxChargePercent = policies.stream().mapToDouble(WithdrawalPolicySnapshot::chargePercent).max().orElse(0.0);
        String headerTimingBucket = "FULL_CURRENT_TERM".equals(scope)
            ? "FULL_CURRENT_TERM"
            : firstPolicy.timingBucket();
        String headerPolicyNote = "FULL_CURRENT_TERM".equals(scope)
            ? "Full current-term withdrawal request covering " + enlistments.size()
                + " subject(s). Per-subject timing and charge snapshots were archived."
            : firstPolicy.policyNote();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO student_withdrawal_requests " +
                    "(student_number, section_id, course_id, term_id, reason_code, remarks, requested_on, " +
                    "enlisted_at, days_enrolled_at_request, timing_bucket, charge_percent, estimated_charge, " +
                    "deadline_blocked, policy_note, status, requested_by, withdrawal_scope, subject_count, approval_source) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new String[]{"request_id"});
            ps.setString(1, studentNumber);
            ps.setInt(2, ((Number) first.get("section_id")).intValue());
            ps.setInt(3, ((Number) first.get("course_id")).intValue());
            ps.setInt(4, ((Number) first.get("term_id")).intValue());
            ps.setString(5, reasonCode);
            ps.setString(6, truncate(remarks, 500));
            ps.setObject(7, firstPolicy.requestedOn());
            ps.setObject(8, firstPolicy.enlistedAt());
            ps.setInt(9, firstPolicy.daysEnrolled());
            ps.setString(10, headerTimingBucket);
            ps.setDouble(11, maxChargePercent);
            ps.setDouble(12, totalCharge);
            ps.setInt(13, 0);
            ps.setString(14, truncate(headerPolicyNote, 255));
            ps.setString(15, STATUS_PENDING_REGISTRAR);
            ps.setString(16, requestedBy);
            ps.setString(17, scope);
            ps.setInt(18, enlistments.size());
            ps.setString(19, "REGISTRAR_WORKFLOW");
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long requestId = key != null ? key.longValue() : db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        insertRequestLines(requestId, studentNumber, enlistments, policies, STATUS_PENDING_REGISTRAR, false);
        return requestId;
    }

    private void insertRequestLines(long requestId, String studentNumber,
                                    List<Map<String, Object>> enlistments,
                                    List<WithdrawalPolicySnapshot> policies,
                                    String status,
                                    boolean completed) {
        String archiveKey = studentProfileService.ensureArchiveKey(studentNumber);
        for (int i = 0; i < enlistments.size(); i++) {
            Map<String, Object> enlistment = enlistments.get(i);
            WithdrawalPolicySnapshot policy = policies.get(i);
            db.update("""
                INSERT INTO student_withdrawal_request_lines
                    (request_id, student_number, archive_key, section_id, course_id, requested_on, enlisted_at,
                     days_enrolled_at_request, timing_bucket, charge_percent, estimated_charge,
                     policy_note, status, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                requestId,
                studentNumber,
                cleanNullable(archiveKey, 80),
                ((Number) enlistment.get("section_id")).intValue(),
                ((Number) enlistment.get("course_id")).intValue(),
                policy.requestedOn(),
                policy.enlistedAt(),
                policy.daysEnrolled(),
                policy.timingBucket(),
                policy.chargePercent(),
                policy.estimatedCharge(),
                truncate(policy.policyNote(), 255),
                status,
                completed ? LocalDateTime.now() : null);
        }
    }

    private long insertCompletedDirectDrop(String studentNumber, String reasonCode, String remarks,
                                           String actor, String scope,
                                           List<Map<String, Object>> enlistments,
                                           List<WithdrawalPolicySnapshot> policies) {
        return insertCompletedDirectDrop(
            studentNumber, reasonCode, remarks, actor, scope, enlistments, policies, "REGISTRAR_DIRECT");
    }

    private long insertCompletedDirectDrop(String studentNumber, String reasonCode, String remarks,
                                           String actor, String scope,
                                           List<Map<String, Object>> enlistments,
                                           List<WithdrawalPolicySnapshot> policies,
                                           String approvalSource) {
        Map<String, Object> first = enlistments.get(0);
        WithdrawalPolicySnapshot firstPolicy = policies.get(0);
        double totalCharge = policies.stream().mapToDouble(WithdrawalPolicySnapshot::estimatedCharge).sum();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO student_withdrawal_requests
                    (student_number, archive_key, section_id, course_id, term_id, reason_code, remarks, requested_on,
                     enlisted_at, days_enrolled_at_request, timing_bucket, charge_percent, estimated_charge,
                     deadline_blocked, policy_note, status, requested_by, registrar_approved_by,
                     registrar_approved_at, completed_at, withdrawal_scope, subject_count, approval_source)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, ?, ?, ?)
                """, new String[]{"request_id"});
            ps.setString(1, studentNumber);
            String archiveKey = studentProfileService.ensureArchiveKey(studentNumber);
            ps.setString(2, archiveKey);
            ps.setInt(3, ((Number) first.get("section_id")).intValue());
            ps.setInt(4, ((Number) first.get("course_id")).intValue());
            ps.setInt(5, ((Number) first.get("term_id")).intValue());
            ps.setString(6, reasonCode);
            ps.setString(7, truncate(remarks, 500));
            ps.setObject(8, firstPolicy.requestedOn());
            ps.setObject(9, firstPolicy.enlistedAt());
            ps.setInt(10, firstPolicy.daysEnrolled());
            ps.setString(11, scope.equals("FULL_CURRENT_TERM") ? "FULL_CURRENT_TERM" : firstPolicy.timingBucket());
            ps.setDouble(12, firstPolicy.chargePercent());
            ps.setDouble(13, totalCharge);
            ps.setString(14, truncate(firstPolicy.policyNote(), 255));
            ps.setString(15, STATUS_APPROVED);
            ps.setString(16, actor);
            ps.setString(17, actor);
            ps.setString(18, scope);
            ps.setInt(19, enlistments.size());
            ps.setString(20, approvalSource);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long requestId = key != null ? key.longValue() : db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        insertRequestLines(requestId, studentNumber, enlistments, policies, STATUS_APPROVED, true);
        return requestId;
    }

    private void markStudentWithdrawn(String studentNumber) {
        String archiveKey = studentProfileService.ensureArchiveKey(studentNumber);
        db.update("""
            UPDATE students
            SET archive_key = COALESCE(archive_key, ?), admission_status = 'WITHDRAWN', status = 'WITHDRAWN',
                is_active = 0, enrollment_blocked = 1
            WHERE student_number = ?
            """, archiveKey, studentNumber);
        db.update("""
            UPDATE sys_users
            SET admission_status = 'WITHDRAWN', status = 'INACTIVE', is_active = 0,
                enrollment_blocked = 1
            WHERE username = ?
            """, studentNumber);
        try {
            db.update("""
                UPDATE applicants
                SET applicant_status = 'WITHDRAWN', updated_at = CURRENT_TIMESTAMP
                WHERE reference_number = (
                    SELECT reference_number FROM students WHERE student_number = ?
                )
                """, studentNumber);
        } catch (Exception ignored) {
            db.update("""
                UPDATE applicants
                SET applicant_status = 'WITHDRAWN'
                WHERE reference_number = (
                    SELECT reference_number FROM students WHERE student_number = ?
                )
                """, studentNumber);
        }
        markArchiveFileWithdrawn(studentNumber, archiveKey);
        studentProfileService.snapshotArchivedIdentity(studentNumber, "registrar-withdrawal", "WITHDRAWN_RECORD");
    }

    private void markArchiveFileWithdrawn(String studentNumber, String archiveKey) {
        try {
            db.execute("""
                CREATE TABLE IF NOT EXISTS student_archive_files (
                    student_number VARCHAR(100) PRIMARY KEY,
                    archive_key VARCHAR(80) NULL,
                    archive_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE_FILE',
                    storage_location VARCHAR(160) NULL,
                    retention_policy_code VARCHAR(80) NOT NULL DEFAULT 'PERMANENT',
                    retention_until DATE NULL,
                    current_holder VARCHAR(100) NULL,
                    last_action_at TIMESTAMP NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            try {
                db.execute("ALTER TABLE student_archive_files ADD COLUMN archive_key VARCHAR(80) NULL");
            } catch (Exception ignored) {
            }
            db.update("""
                INSERT INTO student_archive_files
                    (student_number, archive_key, archive_status, retention_policy_code, current_holder, last_action_at, updated_at)
                VALUES (?, ?, 'WITHDRAWN_FILE', 'PERMANENT', 'Records custody', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    archive_key = COALESCE(archive_key, VALUES(archive_key)),
                    archive_status = 'WITHDRAWN_FILE',
                    retention_policy_code = COALESCE(retention_policy_code, 'PERMANENT'),
                    current_holder = 'Records custody',
                    last_action_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                """, studentNumber, archiveKey);
        } catch (Exception ignored) {
            // Archive custody is an audit enhancement; withdrawal status remains authoritative.
        }
    }

    private void recordDirectDropEvents(String studentNumber, long requestId, String actor, String scope,
                                        List<Map<String, Object>> enlistments, double totalCharge,
                                        String remarks) {
        String courses = enlistments.stream()
            .map(row -> String.valueOf(row.get("course_code")))
            .reduce((left, right) -> left + ", " + right)
            .orElse("none");
        String summary = scope.equals("FULL_CURRENT_TERM")
            ? "Student dropped from current term"
            : "Class dropped by Registrar";
        String details = String.format("%s | Subjects: %s | Applied charge: PHP %,.2f%s",
            scope, courses, totalCharge,
            remarks != null && !remarks.isBlank() ? " | " + truncate(remarks, 300) : "")
            + ("FULL_CURRENT_TERM".equals(scope) ? outstandingBalanceTrailDetail(studentNumber) : "");
        documentTrailService.recordStudentEvent(
            studentNumber, "STUDENT", "WITHDRAWAL", "WITHDRAWAL_COMPLETED", summary,
            details, actor, requestId, "student_withdrawal_requests", String.valueOf(requestId));
        regFormEventService.recordEvent(
            studentNumber, "WITHDRAWAL_COMPLETED", summary, requestId, details, actor);
    }

    private void recordShiftCleanupEvents(String studentNumber, long requestId, String actor,
                                          List<Map<String, Object>> enlistments, double totalCharge,
                                          String remarks) {
        String courses = enlistments.stream()
            .map(row -> String.valueOf(row.get("course_code")))
            .reduce((left, right) -> left + ", " + right)
            .orElse("none");
        String details = String.format("SHIFT_PROGRAM_CLEANUP | Subjects: %s | Applied charge: PHP %,.2f%s",
            courses, totalCharge,
            remarks != null && !remarks.isBlank() ? " | " + truncate(remarks, 300) : "");
        documentTrailService.recordStudentEvent(
            studentNumber, "STUDENT", "PROGRAM_SHIFT", "SHIFT_LOAD_CLEARED",
            "Current-term load cleared for program shift",
            details, actor, requestId, "student_withdrawal_requests", String.valueOf(requestId));
        regFormEventService.recordEvent(
            studentNumber, "SHIFT_LOAD_CLEARED",
            "Current-term load cleared for program shift", requestId, details, actor);
    }

    private void persistOutstandingBalanceHold(long requestId, String studentNumber) {
        double outstanding = scholarEnrollmentService.getOutstandingBalanceNet(studentNumber);
        if (outstanding <= 0.01) {
            return;
        }
        String holdNote = String.format(
            "Outstanding balance remains: PHP %,.2f. Official document release stays blocked until settled.",
            outstanding);
        String existing = null;
        try {
            existing = db.queryForObject(
                "SELECT policy_note FROM student_withdrawal_requests WHERE request_id = ? LIMIT 1",
                String.class,
                requestId);
        } catch (Exception ignored) {
        }
        String merged = existing != null && !existing.isBlank()
            ? truncate(existing.trim() + " | " + holdNote, 255)
            : truncate(holdNote, 255);
        db.update(
            "UPDATE student_withdrawal_requests SET policy_note = ? WHERE request_id = ?",
            merged,
            requestId);
    }

    private String outstandingBalanceTrailDetail(String studentNumber) {
        double outstanding = scholarEnrollmentService.getOutstandingBalanceNet(studentNumber);
        if (outstanding <= 0.01) {
            return "";
        }
        return String.format(
            " | Outstanding balance remains: PHP %,.2f | Official document release stays blocked until settled",
            outstanding);
    }

    private Integer numberAsInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String cleanActor(String actor) {
        String cleaned = clean(actor);
        return cleaned.isEmpty() ? "registrar" : truncate(cleaned, 100);
    }

    private String cleanNullable(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private boolean isWithdrawnOrInactiveStudent(String studentNumber) {
        try {
            Map<String, Object> row = db.queryForMap(
                "SELECT admission_status, status, COALESCE(is_active, 1) AS is_active " +
                    "FROM students WHERE student_number = ? LIMIT 1",
                studentNumber);
            String admissionStatus = row.get("admission_status") != null
                ? String.valueOf(row.get("admission_status"))
                : "";
            String status = row.get("status") != null ? String.valueOf(row.get("status")) : "";
            boolean inactive = row.get("is_active") instanceof Number n && n.intValue() == 0;
            return "WITHDRAWN".equalsIgnoreCase(admissionStatus)
                || "WITHDRAWN".equalsIgnoreCase(status)
                || inactive;
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureReasonExists(String reasonCode) {
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM withdrawal_reasons WHERE reason_code = ? AND COALESCE(is_active, 1) = 1",
            Integer.class, reasonCode);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Selected withdrawal reason is not available.");
        }
    }

    private Map<String, Object> findActiveEnlistment(String studentNumber, Integer sectionId) {
        List<Map<String, Object>> rows = db.queryForList("""
            SELECT se.enlistment_id, se.student_id, se.course_id, se.section_id, se.enlisted_date,
                   cs.term_id, c.course_code, c.course_title, c.credit_units, c.lec_units, c.lab_units,
                   COALESCE(c.component_type, 'SINGLE') AS component_type
            FROM student_enlistments se
            JOIN class_sections cs ON cs.section_id = se.section_id
            JOIN courses c ON c.course_id = se.course_id
            WHERE se.student_id = ? AND se.section_id = ?
            LIMIT 1
            """, studentNumber, sectionId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The selected subject is not currently enlisted for this student.");
        }
        return rows.get(0);
    }

    private WithdrawalPolicySnapshot computePolicySnapshot(String studentNumber, Map<String, Object> enlistment) {
        LocalDate requestedOn = LocalDate.now();
        LocalDateTime enlistedAt = toLocalDateTime(enlistment.get("enlisted_date"));
        int daysEnrolled = enlistedAt != null
            ? Math.max(0, (int) ChronoUnit.DAYS.between(enlistedAt.toLocalDate(), requestedOn))
            : 0;
        Integer termId = enlistment.get("term_id") instanceof Number n ? n.intValue() : null;
        boolean afterMidterm = isAfterMidterm(termId, requestedOn);
        double originalCost = calculateDropOriginalCost(studentNumber, enlistment);
        int halfDays = readEnrollmentSettingInt("drop_penalty_days_half", 7);
        int fullDays = readEnrollmentSettingInt("drop_penalty_days_full", 21);
        double firstWeekPct = readEnrollmentSettingDouble("drop_penalty_first_week_percent", 25.0);
        double halfPct = readEnrollmentSettingDouble("drop_penalty_half_percent", 50.0);

        if (afterMidterm) {
            return new WithdrawalPolicySnapshot(
                requestedOn, enlistedAt, daysEnrolled, "AFTER_MIDTERM_BLOCKED",
                100.0, originalCost, true,
                "Withdrawal deadline has passed. Requests are allowed only until the midterm exam date.");
        }
        if (daysEnrolled >= fullDays) {
            return new WithdrawalPolicySnapshot(
                requestedOn, enlistedAt, daysEnrolled, "FULL_CHARGE",
                100.0, originalCost, false,
                "Full tuition charge applies after " + fullDays + " enrolled day(s).");
        }
        if (daysEnrolled >= halfDays) {
            return new WithdrawalPolicySnapshot(
                requestedOn, enlistedAt, daysEnrolled, "PARTIAL_HALF",
                halfPct, originalCost * (halfPct / 100.0), false,
                String.format("%.0f%% tuition charge applies after %d enrolled day(s).", halfPct, halfDays));
        }
        if (daysEnrolled > 0) {
            return new WithdrawalPolicySnapshot(
                requestedOn, enlistedAt, daysEnrolled, "PARTIAL_FIRST_WEEK",
                firstWeekPct, originalCost * (firstWeekPct / 100.0), false,
                String.format("%.0f%% tuition charge applies within the first week.", firstWeekPct));
        }
        return new WithdrawalPolicySnapshot(
            requestedOn, enlistedAt, daysEnrolled, "PARTIAL_FIRST_WEEK",
            firstWeekPct, originalCost * (firstWeekPct / 100.0), false,
            String.format("%.0f%% tuition charge applies within the first week.", firstWeekPct));
    }

    private LocalDateTime toLocalDateTime(Object raw) {
        if (raw instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (raw instanceof LocalDateTime dt) return dt;
        return null;
    }

    private boolean isAfterMidterm(Integer termId, LocalDate requestedOn) {
        if (termId == null) return false;
        try {
            LocalDate midtermDate = null;
            try {
                midtermDate = toLocalDate(db.queryForObject(
                    "SELECT midterm_exam_date FROM academic_term_policies WHERE term_id = ? LIMIT 1",
                    Object.class, termId));
            } catch (Exception ignored) {
            }
            if (midtermDate != null) {
                return requestedOn.isAfter(midtermDate);
            }
            Map<String, Object> term = db.queryForMap(
                "SELECT start_date, end_date FROM academic_terms WHERE term_id = ? LIMIT 1", termId);
            LocalDate start = toLocalDate(term.get("start_date"));
            LocalDate end = toLocalDate(term.get("end_date"));
            if (start == null || end == null || end.isBefore(start)) return false;
            LocalDate midpoint = start.plusDays(ChronoUnit.DAYS.between(start, end) / 2);
            return requestedOn.isAfter(midpoint);
        } catch (Exception e) {
            return false;
        }
    }

    private LocalDate toLocalDate(Object raw) {
        if (raw instanceof java.sql.Date d) return d.toLocalDate();
        if (raw instanceof LocalDate d) return d;
        return null;
    }

    private double safeTuitionRate(String studentNumber) {
        try {
            return scholarEnrollmentService.tuitionRatePerUnit(studentNumber);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double calculateDropOriginalCost(String studentNumber, Map<String, Object> enlistment) {
        if (studentNumber == null || studentNumber.isBlank() || enlistment == null) {
            return 0.0;
        }
        double creditUnits = enlistment.get("credit_units") instanceof Number n ? n.doubleValue() : 0.0;
        double lectureUnits = enlistment.get("lec_units") instanceof Number n ? n.doubleValue() : 0.0;
        double labUnits = enlistment.get("lab_units") instanceof Number n ? n.doubleValue() : 0.0;
        String courseCode = enlistment.get("course_code") != null ? String.valueOf(enlistment.get("course_code")) : null;
        String courseTitle = enlistment.get("course_title") != null ? String.valueOf(enlistment.get("course_title")) : null;
        String componentType = enlistment.get("component_type") != null ? String.valueOf(enlistment.get("component_type")) : null;
        Integer termId = enlistment.get("term_id") instanceof Number n ? n.intValue() : null;

        String programCode = null;
        int yearLevel = 1;
        try {
            Map<String, Object> student = db.queryForMap(
                "SELECT program_code, COALESCE(year_level, 1) AS year_level FROM students WHERE student_number = ? LIMIT 1",
                studentNumber);
            programCode = student.get("program_code") != null ? String.valueOf(student.get("program_code")) : null;
            yearLevel = student.get("year_level") instanceof Number n ? n.intValue() : 1;
        } catch (Exception ignored) {
        }

        Integer programId = termFeeAdminService.resolveProgramId(programCode);
        if (programId == null) {
            double fallbackUnits = creditUnits > 0.0 ? creditUnits : Math.max(lectureUnits, labUnits);
            return roundMoney(fallbackUnits * safeTuitionRate(studentNumber));
        }

        int semesterNumber = resolveSemesterNumber(termId);
        Map<String, Double> rates = termFeeAdminService.getFeeRatesForScope(programId, termId, yearLevel, semesterNumber);
        String normalizedType = componentType != null ? componentType.trim().toUpperCase() : "";
        String normalizedCode = courseCode != null ? courseCode.trim().toUpperCase() : "";
        String normalizedTitle = courseTitle != null ? courseTitle.trim().toUpperCase() : "";

        if (isRleDropCourse(normalizedType, normalizedCode, normalizedTitle)) {
            double rleRate = rates.getOrDefault("RLE_FEE_PER_UNIT", 0.0);
            if (rleRate > 0.0) {
                double rleUnits = creditUnits > 0.0 ? creditUnits : Math.max(lectureUnits, labUnits);
                return roundMoney(Math.max(0.0, rleUnits) * rleRate);
            }
        }

        if (isSpecialDropCourse(normalizedType, normalizedCode, normalizedTitle)) {
            double specialRate = rates.getOrDefault("COMP_FEE_PER_UNIT", 0.0);
            if (specialRate <= 0.0) {
                specialRate = rates.getOrDefault("RLE_FEE_PER_UNIT", 0.0);
            }
            if (specialRate > 0.0) {
                double specialUnits = creditUnits > 0.0 ? creditUnits : Math.max(lectureUnits, labUnits);
                return roundMoney(Math.max(0.0, specialUnits) * specialRate);
            }
        }

        double lectureRate = rates.getOrDefault("LEC_FEE_PER_UNIT", 0.0);
        if (lectureRate <= 0.0) {
            lectureRate = rates.getOrDefault("TUITION_PER_UNIT", safeTuitionRate(studentNumber));
        }
        double labRate = rates.getOrDefault("LAB_FEE_PER_UNIT", 0.0);
        double resolvedLectureUnits = lectureUnits;
        double resolvedLabUnits = labUnits;
        if (resolvedLectureUnits <= 0.0 && resolvedLabUnits <= 0.0 && creditUnits > 0.0) {
            resolvedLectureUnits = creditUnits;
        }
        return roundMoney((Math.max(0.0, resolvedLectureUnits) * Math.max(0.0, lectureRate))
            + (Math.max(0.0, resolvedLabUnits) * Math.max(0.0, labRate)));
    }

    private int resolveSemesterNumber(Integer termId) {
        if (termId == null) {
            return 1;
        }
        try {
            Integer sem = db.queryForObject(
                "SELECT semester_number FROM academic_terms WHERE term_id = ? LIMIT 1",
                Integer.class, termId);
            return sem != null ? sem : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    private boolean isRleDropCourse(String componentType, String courseCode, String courseTitle) {
        return "RLE".equals(componentType)
            || containsAny(courseCode, "RLE", "NURS")
            || containsAny(courseTitle, "RLE", "CLINICAL", "NURS", "NURSING");
    }

    private boolean isSpecialDropCourse(String componentType, String courseCode, String courseTitle) {
        return "COMP".equals(componentType)
            || "SPECIAL".equals(componentType)
            || containsAny(courseCode, "THESIS", "CAPSTONE", "PRACTICUM", "PROJECT")
            || containsAny(courseTitle, "THESIS", "CAPSTONE", "PRACTICUM", "PROJECT", "EXTERNSHIP", "INTERNSHIP");
    }

    private boolean containsAny(String value, String... tokens) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toUpperCase();
        for (String token : tokens) {
            if (token != null && !token.isBlank() && normalized.contains(token.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    private double roundMoney(double value) {
        return Math.round(Math.max(0.0, value) * 100.0) / 100.0;
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
                "SELECT CAST(setting_value AS DECIMAL(12,2)) FROM enrollment_settings WHERE setting_key = ? LIMIT 1",
                Double.class, key);
            return v != null ? v : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void ensureNoActiveRequest(String studentNumber, Integer sectionId) {
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM student_withdrawal_requests " +
                "WHERE student_number = ? AND section_id = ? " +
                "AND (status = ? OR status = ?)",
            Integer.class, studentNumber, sectionId, "PENDING_DEAN", STATUS_PENDING_REGISTRAR);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("This subject already has a pending withdrawal request.");
        }
    }

    private void ensureNoPendingRequests(String studentNumber, List<Integer> sectionIds) {
        for (Integer sectionId : sectionIds) {
            ensureNoActiveRequest(studentNumber, sectionId);
        }
    }

    private void supersedePendingRequests(String studentNumber, List<Integer> sectionIds, String actor) {
        for (Integer sectionId : sectionIds) {
            List<Long> requestIds = db.queryForList(
                "SELECT request_id FROM student_withdrawal_requests " +
                    "WHERE student_number = ? AND section_id = ? AND (status = ? OR status = ?)",
                Long.class, studentNumber, sectionId, "PENDING_DEAN", STATUS_PENDING_REGISTRAR);
            for (Long requestId : requestIds) {
                db.update(
                    "UPDATE student_withdrawal_request_lines SET status = ? WHERE request_id = ?",
                    STATUS_SUPERSEDED, requestId);
                db.update(
                    "UPDATE student_withdrawal_requests SET status = ?, rejected_by = ?, " +
                        "rejected_at = CURRENT_TIMESTAMP, rejection_reason = ? WHERE request_id = ?",
                    STATUS_SUPERSEDED, actor,
                    "Superseded by direct Registrar processing.", requestId);
            }
        }
    }

    private List<Map<String, Object>> loadRequestLines(long requestId) {
        return db.queryForList("""
            SELECT line_id, request_id, student_number, section_id, course_id,
                   requested_on, enlisted_at, days_enrolled_at_request, timing_bucket,
                   charge_percent, estimated_charge, policy_note, status, completed_at
            FROM student_withdrawal_request_lines
            WHERE request_id = ?
            ORDER BY line_id
            """, requestId);
    }

    private void validateActiveLinesForRequest(long requestId, String studentNumber) {
        for (Map<String, Object> line : loadRequestLines(requestId)) {
            findActiveEnlistment(studentNumber, ((Number) line.get("section_id")).intValue());
        }
    }

    @Transactional
    public Map<String, Object> prepareRegistrarApproval(long requestId, String approvedBy) {
        ensureSchema();
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT wr.request_id, wr.student_number, wr.section_id, wr.status, wr.estimated_charge, " +
                "wr.policy_note, wr.timing_bucket, wr.withdrawal_scope, wr.subject_count, c.course_code " +
                "FROM student_withdrawal_requests wr " +
                "LEFT JOIN courses c ON c.course_id = wr.course_id " +
                "WHERE wr.request_id = ?",
            requestId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Withdrawal request not found.");
        Map<String, Object> row = rows.get(0);
        if (!STATUS_PENDING_REGISTRAR.equals(row.get("status"))) {
            throw new IllegalArgumentException("Withdrawal request is not pending Registrar approval.");
        }
        validateActiveLinesForRequest(requestId, String.valueOf(row.get("student_number")));
        db.update(
            "UPDATE student_withdrawal_requests " +
                "SET registrar_approved_by = ?, registrar_approved_at = CURRENT_TIMESTAMP " +
                "WHERE request_id = ? AND status = ?",
            clean(approvedBy), requestId, STATUS_PENDING_REGISTRAR);
        documentTrailService.recordStudentEvent(
            String.valueOf(row.get("student_number")),
            "STUDENT",
            "WITHDRAWAL",
            "WITHDRAWAL_REGISTRAR_APPROVED",
            "Withdrawal approved by Registrar",
            "Request #" + requestId + " approved. Estimated charge: PHP " +
                String.format("%,.2f", row.get("estimated_charge") instanceof Number n ? n.doubleValue() : 0.0) + ".",
            clean(approvedBy),
            requestId,
            "student_withdrawal_requests",
            String.valueOf(requestId));
        return row;
    }

    @Transactional
    public DirectDropResult executeApprovedRequest(long requestId, String actor) {
        ensureSchema();
        Map<String, Object> header = db.queryForMap(
            "SELECT request_id, student_number, withdrawal_scope, status FROM student_withdrawal_requests WHERE request_id = ?",
            requestId);
        if (!STATUS_PENDING_REGISTRAR.equals(String.valueOf(header.get("status")))) {
            throw new IllegalArgumentException("Withdrawal request is not pending Registrar approval.");
        }

        String studentNumber = String.valueOf(header.get("student_number"));
        String scope = String.valueOf(header.getOrDefault("withdrawal_scope", "SINGLE_SUBJECT"));
        List<Map<String, Object>> lines = loadRequestLines(requestId);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Withdrawal request has no subject lines to execute.");
        }

        double totalCharge = 0.0;
        int subjectsDropped = 0;
        List<Map<String, Object>> executedEnlistments = new ArrayList<>();
        for (Map<String, Object> line : lines) {
            Map<String, Object> enlistment = findActiveEnlistment(studentNumber, ((Number) line.get("section_id")).intValue());
            double charge = line.get("estimated_charge") instanceof Number n ? n.doubleValue() : 0.0;
            String policyNote = line.get("policy_note") != null ? line.get("policy_note").toString() : null;
            scholarEnrollmentService.dropSubjectByEnlistmentId(
                ((Number) enlistment.get("enlistment_id")).longValue(),
                charge,
                policyNote);
            executedEnlistments.add(enlistment);
            totalCharge += charge;
            subjectsDropped++;
        }

        if ("FULL_CURRENT_TERM".equalsIgnoreCase(scope)) {
            markStudentWithdrawn(studentNumber);
        }
        markCompletedInternal(requestId, cleanActor(actor), scope, studentNumber, executedEnlistments, totalCharge);
        return new DirectDropResult(requestId, subjectsDropped, totalCharge, scope);
    }

    // Legacy approval path retained for archived records; live UI now executes withdrawals directly.
    @Transactional
    public DirectDropResult approveAndExecuteRequest(long requestId, String approvedBy) {
        prepareRegistrarApproval(requestId, approvedBy);
        return executeApprovedRequest(requestId, approvedBy);
    }

    @Transactional
    public void markCompleted(long requestId) {
        ensureSchema();
        markCompletedInternal(requestId, "registrar", "SINGLE_SUBJECT", null, List.of(), 0.0);
    }

    // Legacy rejection path retained for archived records; live UI now executes withdrawals directly.
    @Transactional
    public String reject(long requestId, String rejectedBy, String reason) {
        ensureSchema();
        int updated = db.update(
            "UPDATE student_withdrawal_requests " +
                "SET status = ?, rejected_by = ?, rejected_at = CURRENT_TIMESTAMP, rejection_reason = ? " +
                "WHERE request_id = ? AND status = ?",
            STATUS_REJECTED, clean(rejectedBy), truncate(reason, 500), requestId,
            STATUS_PENDING_REGISTRAR);
        if (updated == 0) return "Withdrawal request is no longer pending.";
        db.update("UPDATE student_withdrawal_request_lines SET status = ? WHERE request_id = ?",
            STATUS_REJECTED, requestId);
        Map<String, Object> trailRow = loadRequestForTrail(requestId);
        if (trailRow != null) {
            documentTrailService.recordStudentEvent(
                String.valueOf(trailRow.get("student_number")),
                "STUDENT",
                "WITHDRAWAL",
                "WITHDRAWAL_REJECTED",
                "Withdrawal request rejected",
                reason != null && !reason.isBlank()
                    ? "Request #" + requestId + " rejected: " + truncate(reason, 500)
                    : "Request #" + requestId + " rejected.",
                clean(rejectedBy),
                requestId,
                "student_withdrawal_requests",
                String.valueOf(requestId));
        }
        return "Withdrawal request rejected.";
    }

    private void markCompletedInternal(long requestId, String actor, String scope, String studentNumber,
                                       List<Map<String, Object>> executedEnlistments, double totalCharge) {
        db.update(
            "UPDATE student_withdrawal_requests SET status = ?, completed_at = CURRENT_TIMESTAMP, estimated_charge = ? " +
                "WHERE request_id = ?",
            STATUS_APPROVED, totalCharge, requestId);
        db.update(
            "UPDATE student_withdrawal_request_lines SET status = ?, completed_at = CURRENT_TIMESTAMP " +
                "WHERE request_id = ?",
            STATUS_APPROVED, requestId);

        Map<String, Object> trailRow = loadRequestForTrail(requestId);
        String resolvedStudentNumber = studentNumber != null ? studentNumber
            : (trailRow != null ? String.valueOf(trailRow.get("student_number")) : null);
        if (resolvedStudentNumber == null || resolvedStudentNumber.isBlank()) {
            return;
        }

        String effectiveActor = actor != null && !actor.isBlank()
            ? actor
            : String.valueOf(trailRow != null
                ? trailRow.getOrDefault("registrar_approved_by", trailRow.getOrDefault("dean_approved_by", "registrar"))
                : "registrar");
        String summary = "FULL_CURRENT_TERM".equalsIgnoreCase(scope)
            ? "Student withdrawn from current term"
            : "Subject drop completed";
        String details;
        if (executedEnlistments == null || executedEnlistments.isEmpty()) {
            details = "Request #" + requestId + " completed.";
        } else {
            String courses = executedEnlistments.stream()
                .map(row -> String.valueOf(row.get("course_code")))
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
            details = String.format("Request #%d completed. Scope: %s | Subjects: %s | Applied charge: PHP %,.2f.",
                requestId, scope, courses, totalCharge);
        }
        documentTrailService.recordStudentEvent(
            resolvedStudentNumber,
            "STUDENT",
            "WITHDRAWAL",
            "WITHDRAWAL_COMPLETED",
            summary,
            details,
            effectiveActor,
            requestId,
            "student_withdrawal_requests",
            String.valueOf(requestId));
        regFormEventService.recordEvent(
            resolvedStudentNumber,
            "WITHDRAWAL_COMPLETED",
            summary,
            requestId,
            details,
            effectiveActor);
    }

    public Map<String, Long> statusCounts() {
        ensureSchema();
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        counts.put(STATUS_PENDING_REGISTRAR, 0L);
        counts.put(STATUS_APPROVED, 0L);
        counts.put(STATUS_REJECTED, 0L);
        counts.put(STATUS_SUPERSEDED, 0L);
        for (Map<String, Object> row : db.queryForList(
                "SELECT status, COUNT(*) AS cnt FROM student_withdrawal_requests GROUP BY status")) {
            counts.put(String.valueOf(row.get("status")), ((Number) row.get("cnt")).longValue());
        }
        return counts;
    }

    public List<Map<String, Object>> reasonSummary() {
        ensureSchema();
        return db.queryForList("""
            SELECT COALESCE(rr.reason_label, wr.reason_code) AS reason_label,
                   COUNT(*) AS request_count,
                   COALESCE(SUM(wr.estimated_charge), 0) AS estimated_charge_total
            FROM student_withdrawal_requests wr
            LEFT JOIN withdrawal_reasons rr ON rr.reason_code = wr.reason_code
            GROUP BY COALESCE(rr.reason_label, wr.reason_code)
            ORDER BY request_count DESC, reason_label
            """);
    }

    public List<Map<String, Object>> timingSummary() {
        ensureSchema();
        return db.queryForList("""
            SELECT COALESCE(timing_bucket, 'UNCLASSIFIED') AS timing_bucket,
                   COUNT(*) AS request_count,
                   COALESCE(SUM(estimated_charge), 0) AS estimated_charge_total
            FROM student_withdrawal_requests
            GROUP BY COALESCE(timing_bucket, 'UNCLASSIFIED')
            ORDER BY request_count DESC, timing_bucket
            """);
    }

    private String clean(String value) {
        return value != null ? value.trim() : "";
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private Map<String, Object> loadRequestForTrail(long requestId) {
        try {
            List<Map<String, Object>> rows = db.queryForList(
                "SELECT request_id, student_number, requested_by, dean_approved_by, registrar_approved_by " +
                    "FROM student_withdrawal_requests WHERE request_id = ? LIMIT 1",
                requestId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private record WithdrawalPolicySnapshot(LocalDate requestedOn,
                                            LocalDateTime enlistedAt,
                                            int daysEnrolled,
                                            String timingBucket,
                                            double chargePercent,
                                            double estimatedCharge,
                                            boolean deadlineBlocked,
                                            String policyNote) {
    }

    public record DirectDropResult(long requestId, int subjectsDropped,
                                   double totalCharge, String scope) {
    }
}
