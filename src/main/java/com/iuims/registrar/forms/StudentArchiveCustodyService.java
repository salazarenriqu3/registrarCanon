package com.iuims.registrar.forms;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class StudentArchiveCustodyService {

    private final JdbcTemplate db;
    private final StudentDocumentTrailService documentTrailService;

    public StudentArchiveCustodyService(JdbcTemplate db, StudentDocumentTrailService documentTrailService) {
        this.db = db;
        this.documentTrailService = documentTrailService;
    }

    public void ensureSchema() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS student_archive_files (
                student_number VARCHAR(100) PRIMARY KEY,
                archive_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE_FILE',
                storage_location VARCHAR(160) NULL,
                retention_policy_code VARCHAR(80) NOT NULL DEFAULT 'PERMANENT',
                retention_until DATE NULL,
                current_holder VARCHAR(100) NULL,
                last_action_at TIMESTAMP NULL,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS student_archive_custody_events (
                event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                student_number VARCHAR(100) NOT NULL,
                event_type VARCHAR(50) NOT NULL,
                actor VARCHAR(100) NULL,
                counterpart VARCHAR(100) NULL,
                purpose VARCHAR(255) NULL,
                storage_location VARCHAR(160) NULL,
                remarks VARCHAR(500) NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                KEY idx_sace_student_created (student_number, created_at),
                KEY idx_sace_event_created (event_type, created_at)
            )
            """);
        try {
            db.execute("ALTER TABLE student_archive_files ADD COLUMN retention_policy_code VARCHAR(80) NOT NULL DEFAULT 'PERMANENT'");
        } catch (Exception ignored) {
        }
        try {
            db.execute("ALTER TABLE student_archive_files ADD COLUMN retention_until DATE NULL");
        } catch (Exception ignored) {
        }
    }

    public Map<String, Object> getSummary(String studentNumber) {
        ensureSchema();
        String sn = clean(studentNumber, 100, "");
        if (sn.isBlank()) return Map.of();
        ensureFileRow(sn);
        return db.queryForMap(
            "SELECT student_number, archive_status, storage_location, retention_policy_code, retention_until, " +
                "current_holder, last_action_at, updated_at FROM student_archive_files WHERE student_number = ?",
            sn);
    }

    public List<Map<String, Object>> listRecentEvents(String studentNumber) {
        ensureSchema();
        String sn = clean(studentNumber, 100, "");
        if (sn.isBlank()) return List.of();
        return db.queryForList(
            "SELECT event_id, student_number, event_type, actor, counterpart, purpose, storage_location, remarks, created_at " +
                "FROM student_archive_custody_events WHERE student_number = ? ORDER BY created_at DESC, event_id DESC LIMIT 25",
            sn);
    }

    public String recordEvent(String studentNumber,
                              String eventType,
                              String actor,
                              String counterpart,
                              String purpose,
                              String storageLocation,
                              String remarks) {
        ensureSchema();
        String sn = clean(studentNumber, 100, "");
        if (sn.isBlank()) return "ERROR: Student number is required.";
        ArchiveEvent event = normalizeEvent(eventType);
        ensureFileRow(sn);

        db.update(
            "INSERT INTO student_archive_custody_events " +
                "(student_number, event_type, actor, counterpart, purpose, storage_location, remarks) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
            sn,
            event.code(),
            clean(actor, 100, "registrar"),
            cleanNullable(counterpart, 100),
            cleanNullable(purpose, 255),
            cleanNullable(storageLocation, 160),
            cleanNullable(remarks, 500));

        Long eventId = db.queryForObject("SELECT MAX(event_id) FROM student_archive_custody_events WHERE student_number = ?",
            Long.class, sn);
        db.update(
            "UPDATE student_archive_files SET archive_status = ?, storage_location = COALESCE(NULLIF(?, ''), storage_location), " +
                "current_holder = ?, last_action_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE student_number = ?",
            event.status(),
            clean(storageLocation, 160, ""),
            event.currentHolder(counterpart),
            sn);

        documentTrailService.recordStudentEvent(
            sn,
            "STUDENT",
            "ARCHIVE_CUSTODY",
            event.code(),
            event.summary(),
            event.details(counterpart, purpose, storageLocation, remarks),
            clean(actor, 100, "registrar"),
            eventId,
            "student_archive_custody_events",
            eventId != null ? String.valueOf(eventId) : null);
        return "SUCCESS";
    }

    private void ensureFileRow(String studentNumber) {
        try {
            int updated = db.update(
                "UPDATE student_archive_files SET updated_at = updated_at WHERE student_number = ?",
                studentNumber);
            if (updated == 0) {
                db.update(
                    "INSERT INTO student_archive_files (student_number, archive_status, retention_policy_code) " +
                        "VALUES (?, 'ACTIVE_FILE', 'PERMANENT')",
                    studentNumber);
            }
        } catch (Exception ignored) {
        }
    }

    private ArchiveEvent normalizeEvent(String eventType) {
        String code = eventType != null ? eventType.trim().toUpperCase() : "";
        return switch (code) {
            case "REQUESTED" -> new ArchiveEvent("REQUESTED", "REQUESTED", "Physical file requested");
            case "RELEASED" -> new ArchiveEvent("RELEASED", "RELEASED", "Physical file released");
            case "EVALUATION_COMPLETED" -> new ArchiveEvent("EVALUATION_COMPLETED", "EVALUATION_COMPLETED", "Evaluator completed file review");
            case "SCAN_SUBMITTED" -> new ArchiveEvent("SCAN_SUBMITTED", "SCAN_SUBMITTED", "Physical file submitted for digitization");
            case "RETURNED" -> new ArchiveEvent("RETURNED", "RETURNED", "Physical file returned to records custody");
            case "REFILED" -> new ArchiveEvent("REFILED", "ARCHIVED", "Physical file refiled in archive");
            default -> new ArchiveEvent("REQUESTED", "REQUESTED", "Physical file requested");
        };
    }

    private static String clean(String value, int max, String fallback) {
        String text = value != null ? value.trim() : "";
        if (text.isEmpty()) text = fallback != null ? fallback : "";
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static String cleanNullable(String value, int max) {
        String text = value != null ? value.trim() : "";
        if (text.isEmpty()) return null;
        return text.length() > max ? text.substring(0, max) : text;
    }

    private record ArchiveEvent(String code, String status, String summary) {
        String currentHolder(String counterpart) {
            return switch (code) {
                case "RELEASED", "EVALUATION_COMPLETED", "SCAN_SUBMITTED" -> cleanNullable(counterpart, 100);
                case "REFILED" -> null;
                default -> null;
            };
        }

        String details(String counterpart, String purpose, String location, String remarks) {
            StringBuilder details = new StringBuilder();
            append(details, "Counterpart", counterpart);
            append(details, "Purpose", purpose);
            append(details, "Location", location);
            append(details, "Remarks", remarks);
            return details.length() == 0 ? null : details.toString();
        }

        private static void append(StringBuilder details, String label, String value) {
            if (value == null || value.trim().isEmpty()) return;
            if (details.length() > 0) details.append("; ");
            details.append(label).append(": ").append(value.trim());
        }
    }
}
