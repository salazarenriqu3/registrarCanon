package com.iuims.registrar.service.forms;
import com.iuims.registrar.domain.forms.RegistrationFormSnapshotException;

import com.iuims.registrar.service.support.StudentProfileService;
import com.iuims.registrar.service.support.RegistrarAuditTrailService;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
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
    private final RegFormVersionService regFormVersionService;
    private final ObjectProvider<RegFormVersionService> regFormVersionServiceProvider;
    private final RegistrationFormSnapshotFailureAuditService snapshotFailureAuditService;

    public RegFormEventService(JdbcTemplate db, StudentProfileService studentProfileService) {
        this(db, studentProfileService, (RegistrarAuditTrailService) null, null, null);
    }

    public RegFormEventService(JdbcTemplate db,
                               StudentProfileService studentProfileService,
                               RegistrarAuditTrailService auditTrailService) {
        this(db, studentProfileService, auditTrailService, null, null);
    }

    @Autowired
    public RegFormEventService(JdbcTemplate db,
                               StudentProfileService studentProfileService,
                               ObjectProvider<RegistrarAuditTrailService> auditTrailServiceProvider,
                               ObjectProvider<RegFormVersionService> regFormVersionServiceProvider,
                               ObjectProvider<RegistrationFormSnapshotFailureAuditService> snapshotFailureAuditServiceProvider) {
        this.db = db;
        this.studentProfileService = studentProfileService;
        this.auditTrailService = auditTrailServiceProvider.getIfAvailable();
        this.regFormVersionService = null;
        this.regFormVersionServiceProvider = regFormVersionServiceProvider;
        this.snapshotFailureAuditService = snapshotFailureAuditServiceProvider.getIfAvailable();
    }

    private RegFormEventService(JdbcTemplate db,
                                StudentProfileService studentProfileService,
                                RegistrarAuditTrailService auditTrailService,
                                RegFormVersionService regFormVersionService,
                                RegistrationFormSnapshotFailureAuditService snapshotFailureAuditService) {
        this.db = db;
        this.studentProfileService = studentProfileService;
        this.auditTrailService = auditTrailService;
        this.regFormVersionService = regFormVersionService;
        this.regFormVersionServiceProvider = null;
        this.snapshotFailureAuditService = snapshotFailureAuditService;
    }

    @PostConstruct
    void initializeSchema() {
        ensureSchema();
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

    @Transactional
    public Long recordEvent(String studentNumber, String eventType, String purpose,
                            Long relatedRequestId, String remarks, String triggeredBy) {
        if (studentNumber == null || studentNumber.isBlank()) {
            return null;
        }
        String archiveKey = studentProfileService.ensureArchiveKey(studentNumber);
        String auditKey = cleanNullable(archiveKey, 80);
        String normalizedEventType = clean(eventType, "REG_FORM_EVENT", 60);
        String normalizedPurpose = clean(purpose, "Registration form event", 160);
        String normalizedRemarks = cleanNullable(remarks, 500);
        String normalizedTriggeredBy = cleanNullable(triggeredBy, 100);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        db.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO student_reg_form_events
                    (student_number, archive_key, event_type, purpose, related_request_id, remarks, triggered_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, studentNumber.trim());
            ps.setString(2, auditKey);
            ps.setString(3, normalizedEventType);
            ps.setString(4, normalizedPurpose);
            if (relatedRequestId != null) {
                ps.setLong(5, relatedRequestId);
            } else {
                ps.setObject(5, null);
            }
            ps.setString(6, normalizedRemarks);
            ps.setString(7, normalizedTriggeredBy);
            return ps;
        }, keyHolder);
        Long eventId = generatedId(keyHolder, "event_id");
        captureRequiredVersionSnapshot(studentNumber.trim(), normalizedEventType, normalizedPurpose, eventId,
            normalizedRemarks, normalizedTriggeredBy);
        recordAudit(auditKey != null ? auditKey : studentNumber, normalizedEventType, normalizedPurpose,
            normalizedRemarks, normalizedTriggeredBy, relatedRequestId);
        return eventId;
    }

    public List<Map<String, Object>> listStudentEvents(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) {
            return List.of();
        }
        ensureSchema();
        ensureVersionSchema();
        return db.queryForList("""
            SELECT event.event_id, event.student_number, event.archive_key, event.event_type, event.purpose,
                   event.related_request_id, event.remarks, event.triggered_by, event.created_at,
                   saved_version.version_id,
                   CASE WHEN saved_version.version_id IS NOT NULL THEN TRUE ELSE FALSE END AS has_saved_version,
                   saved_version.created_at AS version_created_at
            FROM student_reg_form_events event
            LEFT JOIN student_reg_form_versions saved_version ON saved_version.source_event_id = event.event_id
            WHERE BINARY event.student_number = BINARY ?
            ORDER BY event.created_at DESC, event.event_id DESC
            LIMIT 25
            """, studentNumber.trim());
    }

    public List<Map<String, Object>> listRecentEvents(String studentNumber, String eventType,
                                                      LocalDate fromDate, LocalDate toDate, int limit) {
        ensureSchema();
        ensureVersionSchema();
        int safeLimit = Math.max(1, Math.min(limit, 500));
        StringBuilder sql = new StringBuilder("""
            SELECT event.event_id, event.student_number, event.archive_key, event.event_type, event.purpose,
                   event.related_request_id, event.remarks, event.triggered_by, event.created_at,
                   saved_version.version_id,
                   CASE WHEN saved_version.version_id IS NOT NULL THEN TRUE ELSE FALSE END AS has_saved_version,
                   saved_version.created_at AS version_created_at
            FROM student_reg_form_events event
            LEFT JOIN student_reg_form_versions saved_version ON saved_version.source_event_id = event.event_id
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        if (studentNumber != null && !studentNumber.isBlank()) {
            sql.append(" AND BINARY event.student_number = BINARY ? ");
            args.add(studentNumber.trim());
        }
        if (eventType != null && !eventType.isBlank()) {
            sql.append(" AND event.event_type = ? ");
            args.add(eventType.trim().toUpperCase());
        }
        if (fromDate != null) {
            sql.append(" AND event.created_at >= ? ");
            args.add(fromDate.atStartOfDay());
        }
        if (toDate != null) {
            sql.append(" AND event.created_at < ? ");
            args.add(toDate.plusDays(1).atStartOfDay());
        }
        sql.append(" ORDER BY event.created_at DESC, event.event_id DESC LIMIT ").append(safeLimit);
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

    private void captureRequiredVersionSnapshot(String studentNumber,
                                                String eventType,
                                                String purpose,
                                                Long relatedEventId,
                                                String remarks,
                                                String triggeredBy) {
        if (!RegFormVersionService.isSnapshotRequiredEvent(eventType)) {
            return;
        }
        RegFormVersionService versionService = resolveRegFormVersionService();
        try {
            if (versionService == null) {
                throw new IllegalStateException("Registration Form version service is unavailable.");
            }
            if (relatedEventId == null) {
                throw new IllegalStateException("Registration Form event ID was not generated.");
            }
            Long versionId = versionService.captureVersion(
                studentNumber,
                eventType,
                purpose,
                relatedEventId,
                remarks,
                triggeredBy);
            if (versionId == null) {
                throw new IllegalStateException("Registration Form version was not created.");
            }
        } catch (Exception e) {
            recordSnapshotFailureAudit(studentNumber, eventType, purpose, triggeredBy, e);
            throw new RegistrationFormSnapshotException(
                "Registration Form version could not be saved. No enrollment change was committed.", e);
        }
    }

    private void recordSnapshotFailureAudit(String studentNumber,
                                            String eventType,
                                            String purpose,
                                            String triggeredBy,
                                            Exception failure) {
        if (snapshotFailureAuditService == null) {
            return;
        }
        try {
            String reason = failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
            snapshotFailureAuditService.recordFailure(studentNumber, eventType, purpose, triggeredBy, clean(reason, "Snapshot capture failed", 500));
        } catch (Exception ignored) {
            // The primary transaction must still roll back when the separate operational audit is unavailable.
        }
    }

    private RegFormVersionService resolveRegFormVersionService() {
        if (regFormVersionService != null) {
            return regFormVersionService;
        }
        return regFormVersionServiceProvider != null ? regFormVersionServiceProvider.getIfAvailable() : null;
    }

    private void ensureVersionSchema() {
        RegFormVersionService versionService = resolveRegFormVersionService();
        if (versionService != null) {
            versionService.ensureSchema();
        }
    }

    private Long generatedId(KeyHolder keyHolder, String keyName) {
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null) {
            Object value = keys.get(keyName);
            if (!(value instanceof Number) && keyName != null) {
                value = keys.get(keyName.toUpperCase());
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
        }
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }
}
