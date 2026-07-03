package com.iuims.registrar.forms;

import com.iuims.registrar.core.StudentProfileService;
import com.iuims.registrar.core.RegistrarAuditTrailService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentDocumentTrailService {

    private final JdbcTemplate db;
    private final StudentProfileService studentProfileService;

    private final RegistrarAuditTrailService auditTrailService;

    public StudentDocumentTrailService(JdbcTemplate db, StudentProfileService studentProfileService) {
        this(db, studentProfileService, null);
    }

    @Autowired
    public StudentDocumentTrailService(JdbcTemplate db,
                                       StudentProfileService studentProfileService,
                                       RegistrarAuditTrailService auditTrailService) {
        this.db = db;
        this.studentProfileService = studentProfileService;
        this.auditTrailService = auditTrailService;
    }

    public void ensureSchema() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS student_document_events (
                event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                student_number VARCHAR(100) NULL,
                archive_key VARCHAR(80) NULL,
                reference_number VARCHAR(100) NULL,
                document_scope VARCHAR(40) NOT NULL,
                document_type VARCHAR(60) NOT NULL,
                event_type VARCHAR(80) NOT NULL,
                event_summary VARCHAR(180) NOT NULL,
                event_details VARCHAR(500) NULL,
                actor VARCHAR(100) NULL,
                related_request_id BIGINT NULL,
                source_table VARCHAR(80) NULL,
                source_id VARCHAR(80) NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                KEY idx_sdet_student_created (student_number, created_at),
                KEY idx_sdet_archive_created (archive_key, created_at),
                KEY idx_sdet_ref_created (reference_number, created_at),
                KEY idx_sdet_scope_created (document_scope, created_at),
                KEY idx_sdet_type_created (document_type, created_at)
            )
            """);
        try {
            db.execute("ALTER TABLE student_document_events ADD COLUMN archive_key VARCHAR(80) NULL");
        } catch (Exception ignored) {
        }
    }

    public void recordStudentEvent(String studentNumber,
                                   String documentScope,
                                   String documentType,
                                   String eventType,
                                   String eventSummary,
                                   String eventDetails,
                                   String actor,
                                   Long relatedRequestId,
                                   String sourceTable,
                                   String sourceId) {
        ensureSchema();
        String archiveKey = resolveArchiveKey(studentNumber);
        String archiveKeyValue = cleanNullable(archiveKey, 80);
        db.update("""
            INSERT INTO student_document_events
                (student_number, archive_key, reference_number, document_scope, document_type, event_type,
                 event_summary, event_details, actor, related_request_id, source_table, source_id)
            VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            clean(studentNumber, 100, null),
            archiveKeyValue,
            clean(documentScope, 40, "STUDENT"),
            clean(documentType, 60, "REG_FORM"),
            clean(eventType, 80, "DOCUMENT_EVENT"),
            clean(eventSummary, 180, "Document event"),
            cleanNullable(eventDetails, 500),
            cleanNullable(actor, 100),
            relatedRequestId,
            cleanNullable(sourceTable, 80),
            cleanNullable(sourceId, 80));
        recordAudit(actor, eventType, "STUDENT", archiveKeyValue != null ? archiveKeyValue : studentNumber, eventSummary, eventDetails, sourceTable, sourceId);
    }

    public void recordReferenceEvent(String referenceNumber,
                                     String documentScope,
                                     String documentType,
                                     String eventType,
                                     String eventSummary,
                                     String eventDetails,
                                     String actor,
                                     Long relatedRequestId,
                                     String sourceTable,
                                     String sourceId) {
        ensureSchema();
        db.update("""
            INSERT INTO student_document_events
                (student_number, archive_key, reference_number, document_scope, document_type, event_type,
                 event_summary, event_details, actor, related_request_id, source_table, source_id)
            VALUES (NULL, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            clean(referenceNumber, 100, null),
            clean(documentScope, 40, "APPLICATION"),
            clean(documentType, 60, "APPLICATION"),
            clean(eventType, 80, "DOCUMENT_EVENT"),
            clean(eventSummary, 180, "Document event"),
            cleanNullable(eventDetails, 500),
            cleanNullable(actor, 100),
            relatedRequestId,
            cleanNullable(sourceTable, 80),
            cleanNullable(sourceId, 80));
        recordAudit(actor, eventType, "APPLICATION", referenceNumber, eventSummary, eventDetails, sourceTable, sourceId);
    }

    public List<Map<String, Object>> listRecentEvents(String query,
                                                     String eventType,
                                                     String documentType,
                                                     LocalDate fromDate,
                                                     LocalDate toDate,
                                                     int limit) {
        ensureSchema();
        List<Map<String, Object>> rows = new ArrayList<>();
        String q = query != null ? query.trim() : "";
        String et = eventType != null ? eventType.trim().toUpperCase() : "";
        String dt = documentType != null ? documentType.trim().toUpperCase() : "";
        int safeLimit = Math.max(1, Math.min(limit, 500));

        rows.addAll(fetchUnifiedEvents(q, et, dt, fromDate, toDate));
        rows.sort(Comparator.comparing(
                (Map<String, Object> row) -> toLocalDateTimeValue(row.get("created_at")),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed()
            .thenComparing(row -> String.valueOf(row.getOrDefault("event_id", "")), Comparator.reverseOrder()));
        if (rows.size() > safeLimit) {
            return new ArrayList<>(rows.subList(0, safeLimit));
        }
        return rows;
    }

    public Map<String, Object> summary(String query, String eventType, String documentType, LocalDate fromDate, LocalDate toDate) {
        List<Map<String, Object>> rows = fetchUnifiedEvents(
            query != null ? query.trim() : "",
            eventType != null ? eventType.trim().toUpperCase() : "",
            documentType != null ? documentType.trim().toUpperCase() : "",
            fromDate,
            toDate
        );
        Map<String, Object> out = new HashMap<>();
        out.put("total_events", rows.size());
        out.put("touched_entities", rows.stream()
            .map(this::resolveSubjectKey)
            .filter(v -> v != null && !v.isBlank())
            .distinct()
            .count());
        out.put("latest_event_at", rows.stream()
            .map(row -> toLocalDateTimeValue(row.get("created_at")))
            .filter(v -> v != null)
            .max(LocalDateTime::compareTo)
            .orElse(null));
        return out;
    }

    public List<Map<String, Object>> eventTypeSummary(String query,
                                                     String documentType,
                                                     LocalDate fromDate,
                                                     LocalDate toDate) {
        return summarize(rowsForSummary(query, "", documentType, fromDate, toDate), "event_type");
    }

    public List<Map<String, Object>> documentTypeSummary(String query,
                                                        String eventType,
                                                        LocalDate fromDate,
                                                        LocalDate toDate) {
        return summarize(rowsForSummary(query, eventType, "", fromDate, toDate), "document_type");
    }

    private List<Map<String, Object>> fetchUnifiedEvents(String query,
                                                        String eventType,
                                                        String documentType,
                                                        LocalDate fromDate,
                                                        LocalDate toDate) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(fetchStoredEvents(query, eventType, documentType, fromDate, toDate));
        rows.addAll(fetchRegFormEvents(query, eventType, documentType, fromDate, toDate));
        rows.addAll(fetchAdmissionEvents(query, eventType, documentType, fromDate, toDate));
        rows.addAll(fetchWithdrawalEvents(query, eventType, documentType, fromDate, toDate));
        rows.addAll(fetchGradeChangeEvents(query, eventType, documentType, fromDate, toDate));
        rows.addAll(fetchGradeRecordEvents(query, eventType, documentType, fromDate, toDate));
        return rows;
    }

    private List<Map<String, Object>> rowsForSummary(String query,
                                                     String eventType,
                                                     String documentType,
                                                     LocalDate fromDate,
                                                     LocalDate toDate) {
        return fetchUnifiedEvents(query, eventType, documentType, fromDate, toDate);
    }

    private List<Map<String, Object>> summarize(List<Map<String, Object>> rows, String key) {
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String value = String.valueOf(row.getOrDefault(key, "")).trim();
            if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
                continue;
            }
            counts.put(value, counts.getOrDefault(value, 0L) + 1L);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()))
            .forEach(entry -> {
                Map<String, Object> row = new HashMap<>();
                row.put(key, entry.getKey());
                row.put("event_count", entry.getValue());
                out.add(row);
            });
        return out;
    }

    private List<Map<String, Object>> fetchStoredEvents(String query,
                                                       String eventType,
                                                       String documentType,
                                                       LocalDate fromDate,
                                                       LocalDate toDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT event_id, student_number, archive_key, reference_number, document_scope, document_type,
                   event_type, event_summary, event_details, actor, related_request_id,
                   source_table, source_id, created_at
            FROM student_document_events
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, query, eventType, documentType, fromDate, toDate,
            "event_type", "document_type", "created_at",
            "student_number", "archive_key", "reference_number", "event_type", "document_type", "event_summary", "event_details", "actor", "source_id");
        sql.append(" ORDER BY created_at DESC, event_id DESC");
        return db.queryForList(sql.toString(), args.toArray());
    }

    private List<Map<String, Object>> fetchRegFormEvents(String query,
                                                        String eventType,
                                                        String documentType,
                                                        LocalDate fromDate,
                                                        LocalDate toDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT event_id, student_number, archive_key, NULL AS reference_number, 'STUDENT' AS document_scope,
                   CASE
                       WHEN event_type IN ('SUBJECT_ADD', 'ENROLLMENT_ACTIVATED', 'BLOCK_ENROLL_COMPLETED', 'FORCE_ENROLL_COMPLETED') THEN 'ENROLLMENT'
                       WHEN event_type IN ('TRANSFER_CREDIT', 'BULK_TRANSFER_CREDIT') THEN 'TRANSFER_CREDIT'
                       WHEN event_type IN ('CURRICULUM_ASSIGNED', 'PROGRAM_SHIFT') THEN 'CURRICULUM'
                       WHEN event_type LIKE 'OVERPAY_%' THEN 'FINANCE'
                       ELSE 'REG_FORM'
                   END AS document_type,
                   event_type, purpose AS event_summary,
                   remarks AS event_details, triggered_by AS actor, related_request_id,
                   'student_reg_form_events' AS source_table,
                   CAST(event_id AS CHAR) AS source_id,
                   created_at
            FROM student_reg_form_events
            WHERE 1 = 1
        """);
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, query, eventType, documentType, fromDate, toDate,
            "event_type", regFormDocumentTypeSql(), "created_at",
            "student_number", "archive_key", "event_type", "purpose", "remarks", "triggered_by");
        sql.append(" ORDER BY created_at DESC, event_id DESC");
        return db.queryForList(sql.toString(), args.toArray());
    }

    private List<Map<String, Object>> fetchAdmissionEvents(String query,
                                                          String eventType,
                                                          String documentType,
                                                          LocalDate fromDate,
                                                          LocalDate toDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT log_id AS event_id, NULL AS student_number, NULL AS archive_key, ref_no AS reference_number,
                   'APPLICATION' AS document_scope, 'ADMISSION' AS document_type,
                   action AS event_type, action AS event_summary, remarks AS event_details,
                   performed_by AS actor, NULL AS related_request_id,
                   'eac_application_logs' AS source_table,
                   CAST(log_id AS CHAR) AS source_id,
                   log_timestamp AS created_at
            FROM eac_application_logs
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, query, eventType, documentType, fromDate, toDate,
            "action", "'ADMISSION'", "log_timestamp",
            "ref_no", "action", "remarks", "performed_by");
        sql.append(" ORDER BY log_timestamp DESC, log_id DESC");
        return db.queryForList(sql.toString(), args.toArray());
    }

    private List<Map<String, Object>> fetchWithdrawalEvents(String query,
                                                           String eventType,
                                                           String documentType,
                                                           LocalDate fromDate,
                                                           LocalDate toDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT request_id AS event_id, student_number, archive_key, NULL AS reference_number,
                   'STUDENT' AS document_scope, 'WITHDRAWAL' AS document_type,
                   CONCAT('WITHDRAWAL_', status) AS event_type,
                   CONCAT('Withdrawal ', status) AS event_summary,
                   CONCAT(COALESCE(reason_code, ''), COALESCE(CONCAT(' - ', remarks), '')) AS event_details,
                   COALESCE(registrar_approved_by, dean_approved_by, requested_by, rejected_by) AS actor,
                   request_id AS related_request_id,
                   'student_withdrawal_requests' AS source_table,
                   CAST(request_id AS CHAR) AS source_id,
                   COALESCE(completed_at, registrar_approved_at, dean_approved_at, rejected_at, requested_at) AS created_at
            FROM student_withdrawal_requests
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, query, eventType, documentType, fromDate, toDate,
            "CONCAT('WITHDRAWAL_', status)", "'WITHDRAWAL'",
            "COALESCE(completed_at, registrar_approved_at, dean_approved_at, rejected_at, requested_at)",
            "student_number", "archive_key", "reason_code", "remarks", "status", "rejection_reason", "policy_note");
        sql.append(" ORDER BY created_at DESC, request_id DESC");
        return db.queryForList(sql.toString(), args.toArray());
    }

    private List<Map<String, Object>> fetchGradeChangeEvents(String query,
                                                            String eventType,
                                                            String documentType,
                                                            LocalDate fromDate,
                                                            LocalDate toDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT request_id AS event_id, NULL AS student_number, NULL AS archive_key, NULL AS reference_number,
                   'STUDENT' AS document_scope, 'GRADE_CHANGE' AS document_type,
                   CONCAT('GRADE_CHANGE_', status) AS event_type,
                   CONCAT('Grade change ', status) AS event_summary,
                   CONCAT(
                       COALESCE(request_type, 'FINAL_GRADE_CORRECTION'),
                       ' - ',
                       COALESCE(reason, ''),
                       CASE
                           WHEN COALESCE(review_note, '') <> '' THEN CONCAT(' | Review: ', review_note)
                           ELSE ''
                       END
                   ) AS event_details,
                   COALESCE(reviewed_by, faculty_name) AS actor, request_id AS related_request_id,
                   'grade_change_requests' AS source_table,
                   CAST(request_id AS CHAR) AS source_id,
                   COALESCE(rejected_at, approved_at, request_date) AS created_at
            FROM grade_change_requests
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, query, eventType, documentType, fromDate, toDate,
            "CONCAT('GRADE_CHANGE_', status)", "'GRADE_CHANGE'", "COALESCE(rejected_at, approved_at, request_date)",
            "student_name", "course_code", "faculty_name", "reviewed_by", "reason", "review_note", "status", "request_type");
        sql.append(" ORDER BY created_at DESC, request_id DESC");
        return db.queryForList(sql.toString(), args.toArray());
    }

    private List<Map<String, Object>> fetchGradeRecordEvents(String query,
                                                             String eventType,
                                                             String documentType,
                                                             LocalDate fromDate,
                                                             LocalDate toDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT event_id, student_id AS student_number, NULL AS archive_key, NULL AS reference_number,
                   'STUDENT' AS document_scope, 'GRADE_RECORD' AS document_type,
                   action_type AS event_type,
                   CONCAT('Grade record ', REPLACE(LOWER(action_type), '_', ' ')) AS event_summary,
                   CONCAT(
                       COALESCE(course_code, 'COURSE'),
                       CASE
                           WHEN COALESCE(reason, '') <> '' THEN CONCAT(' - ', reason)
                           ELSE ''
                       END
                   ) AS event_details,
                   actor, request_id AS related_request_id,
                   'grade_record_events' AS source_table,
                   CAST(event_id AS CHAR) AS source_id,
                   created_at
            FROM grade_record_events
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, query, eventType, documentType, fromDate, toDate,
            "action_type", "'GRADE_RECORD'", "created_at",
            "student_id", "student_name", "course_code", "section_code", "actor", "reason", "lifecycle_status");
        sql.append(" ORDER BY created_at DESC, event_id DESC");
        return db.queryForList(sql.toString(), args.toArray());
    }

    private void appendFilters(StringBuilder sql, List<Object> args, String query, String eventType, String documentType,
                               LocalDate fromDate, LocalDate toDate, String eventTypeSql, String documentTypeSql,
                               String createdAtSql, String... columns) {
        if (query != null && !query.isBlank()) {
            sql.append(" AND (");
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("COALESCE(").append(columns[i]).append(", '') LIKE ?");
                args.add("%" + query + "%");
            }
            sql.append(") ");
        }
        if (eventType != null && !eventType.isBlank()) {
            sql.append(" AND ").append(eventTypeSql).append(" = ? ");
            args.add(eventType);
        }
        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND ").append(documentTypeSql).append(" = ? ");
            args.add(documentType);
        }
        if (fromDate != null) {
            sql.append(" AND ").append(createdAtSql).append(" >= ? ");
            args.add(fromDate.atStartOfDay());
        }
        if (toDate != null) {
            sql.append(" AND ").append(createdAtSql).append(" < ? ");
            args.add(toDate.plusDays(1).atStartOfDay());
        }
    }

    private String regFormDocumentTypeSql() {
        return """
            CASE
                WHEN event_type IN ('SUBJECT_ADD', 'ENROLLMENT_ACTIVATED', 'BLOCK_ENROLL_COMPLETED', 'FORCE_ENROLL_COMPLETED') THEN 'ENROLLMENT'
                WHEN event_type LIKE 'TRANSFER_CREDIT%' OR event_type LIKE 'BULK_TRANSFER_CREDIT%' THEN 'TRANSFER_CREDIT'
                WHEN event_type IN ('CURRICULUM_ASSIGNED', 'PROGRAM_SHIFT') THEN 'CURRICULUM'
                WHEN event_type LIKE 'OVERPAY_%' THEN 'FINANCE'
                ELSE 'REG_FORM'
            END
            """;
    }

    private String resolveSubjectKey(Map<String, Object> row) {
        String archiveKey = normalizeValue(row.get("archive_key"));
        if (!archiveKey.isBlank()) {
            return archiveKey;
        }
        String studentNumber = normalizeValue(row.get("student_number"));
        if (!studentNumber.isBlank()) {
            return studentNumber;
        }
        String referenceNumber = normalizeValue(row.get("reference_number"));
        if (!referenceNumber.isBlank()) {
            return referenceNumber;
        }
        return normalizeValue(row.get("source_id"));
    }

    private String resolveArchiveKey(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) {
            return null;
        }
        return studentProfileService.ensureArchiveKey(studentNumber);
    }

    private String normalizeValue(Object raw) {
        return raw != null ? raw.toString().trim() : "";
    }

    private LocalDateTime toLocalDateTimeValue(Object raw) {
        if (raw instanceof LocalDateTime dt) {
            return dt;
        }
        if (raw instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }

    private String clean(String value, int maxLength, String defaultValue) {
        String cleaned = value != null && !value.isBlank() ? value.trim() : defaultValue;
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private String cleanNullable(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private void recordAudit(String actor,
                             String eventType,
                             String targetType,
                             String targetKey,
                             String eventSummary,
                             String eventDetails,
                             String sourceTable,
                             String sourceId) {
        if (auditTrailService == null) {
            return;
        }
        auditTrailService.record(
            actor,
            "Registrar",
            "DOCUMENTS",
            clean(eventType, 80, "DOCUMENT_EVENT"),
            targetType,
            targetKey,
            clean(eventSummary, 180, "Document event"),
            eventDetails,
            sourceTable,
            sourceId);
    }
}
