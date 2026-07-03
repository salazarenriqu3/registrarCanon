package com.iuims.registrar.forms;

import com.iuims.registrar.core.StudentProfileService;
import com.iuims.registrar.core.RegistrarAuditTrailService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class RegFormEventService {

    private final JdbcTemplate db;
    private final StudentProfileService studentProfileService;

    private final RegistrarAuditTrailService auditTrailService;

    public RegFormEventService(JdbcTemplate db, StudentProfileService studentProfileService) {
        this(db, studentProfileService, null);
    }

    @Autowired
    public RegFormEventService(JdbcTemplate db,
                               StudentProfileService studentProfileService,
                               RegistrarAuditTrailService auditTrailService) {
        this.db = db;
        this.studentProfileService = studentProfileService;
        this.auditTrailService = auditTrailService;
    }

    public void ensureSchema() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS student_reg_form_events (
                event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                student_number VARCHAR(100) NOT NULL,
                archive_key VARCHAR(80) NULL,
                event_type VARCHAR(60) NOT NULL,
                purpose VARCHAR(160) NOT NULL,
                related_request_id BIGINT NULL,
                remarks VARCHAR(500) NULL,
                triggered_by VARCHAR(100) NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        try {
            db.execute("CREATE INDEX idx_srfe_student ON student_reg_form_events (student_number, created_at)");
        } catch (Exception ignored) {
        }
        try {
            db.execute("CREATE INDEX idx_srfe_archive ON student_reg_form_events (archive_key, created_at)");
        } catch (Exception ignored) {
        }
        try {
            db.execute("CREATE INDEX idx_srfe_type ON student_reg_form_events (event_type, created_at)");
        } catch (Exception ignored) {
        }
        try {
            db.execute("ALTER TABLE student_reg_form_events ADD COLUMN archive_key VARCHAR(80) NULL");
        } catch (Exception ignored) {
        }
    }

    public void recordEvent(String studentNumber, String eventType, String purpose,
                            Long relatedRequestId, String remarks, String triggeredBy) {
        if (studentNumber == null || studentNumber.isBlank()) {
            return;
        }
        ensureSchema();
        String archiveKey = studentProfileService.ensureArchiveKey(studentNumber);
        String auditKey = cleanNullable(archiveKey, 80);
        db.update("""
                INSERT INTO student_reg_form_events
                    (student_number, archive_key, event_type, purpose, related_request_id, remarks, triggered_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
            studentNumber.trim(),
            auditKey,
            clean(eventType, "REG_FORM_EVENT", 60),
            clean(purpose, "Registration form event", 160),
            relatedRequestId,
            cleanNullable(remarks, 500),
            cleanNullable(triggeredBy, 100));
        recordAudit(auditKey != null ? auditKey : studentNumber, eventType, purpose, remarks, triggeredBy, relatedRequestId);
    }

    public List<Map<String, Object>> listStudentEvents(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) {
            return List.of();
        }
        ensureSchema();
        return db.queryForList("""
            SELECT event_id, student_number, archive_key, event_type, purpose, related_request_id,
                   remarks, triggered_by, created_at
            FROM student_reg_form_events
            WHERE BINARY student_number = BINARY ?
            ORDER BY created_at DESC, event_id DESC
            LIMIT 25
            """, studentNumber.trim());
    }

    public List<Map<String, Object>> listRecentEvents(String studentNumber, String eventType,
                                                      LocalDate fromDate, LocalDate toDate, int limit) {
        ensureSchema();
        int safeLimit = Math.max(1, Math.min(limit, 500));
        StringBuilder sql = new StringBuilder("""
            SELECT event_id, student_number, archive_key, event_type, purpose, related_request_id,
                   remarks, triggered_by, created_at
            FROM student_reg_form_events
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        if (studentNumber != null && !studentNumber.isBlank()) {
            sql.append(" AND BINARY student_number = BINARY ? ");
            args.add(studentNumber.trim());
        }
        if (eventType != null && !eventType.isBlank()) {
            sql.append(" AND event_type = ? ");
            args.add(eventType.trim().toUpperCase());
        }
        if (fromDate != null) {
            sql.append(" AND created_at >= ? ");
            args.add(fromDate.atStartOfDay());
        }
        if (toDate != null) {
            sql.append(" AND created_at < ? ");
            args.add(toDate.plusDays(1).atStartOfDay());
        }
        sql.append(" ORDER BY created_at DESC, event_id DESC LIMIT ").append(safeLimit);
        return db.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> historySummary(LocalDate fromDate, LocalDate toDate) {
        ensureSchema();
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*) AS total_events,
                   COUNT(DISTINCT student_number) AS touched_students,
                   MAX(created_at) AS latest_event_at
            FROM student_reg_form_events
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        if (fromDate != null) {
            sql.append(" AND created_at >= ? ");
            args.add(fromDate.atStartOfDay());
        }
        if (toDate != null) {
            sql.append(" AND created_at < ? ");
            args.add(toDate.plusDays(1).atStartOfDay());
        }
        return db.queryForMap(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> eventTypeSummary() {
        ensureSchema();
        return db.queryForList("""
            SELECT event_type, COUNT(*) AS event_count
            FROM student_reg_form_events
            GROUP BY event_type
            ORDER BY event_count DESC, event_type ASC
            """);
    }

    private String clean(String value, String defaultValue, int maxLength) {
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

    private void recordAudit(String studentNumber,
                             String eventType,
                             String purpose,
                             String remarks,
                             String triggeredBy,
                             Long relatedRequestId) {
        if (auditTrailService == null) {
            return;
        }
        auditTrailService.recordStudentAction(
            triggeredBy,
            "REG_FORM",
            clean(eventType, "REG_FORM_EVENT", 60),
            studentNumber,
            clean(purpose, "Registration form event", 160),
            remarks,
            "student_reg_form_events",
            relatedRequestId != null ? String.valueOf(relatedRequestId) : studentNumber);
    }
}
