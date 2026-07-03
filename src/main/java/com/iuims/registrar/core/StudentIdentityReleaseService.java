package com.iuims.registrar.core;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class StudentIdentityReleaseService {

    public record ReleaseResult(boolean ok,
                                String message,
                                String releasedStudentNumber,
                                String archiveKey) {}

    private static final List<TableColumnRef> STRING_STUDENT_REFS = List.of(
        new TableColumnRef("student_ledger", "student_id"),
        new TableColumnRef("student_term_closes", "student_id"),
        new TableColumnRef("student_enlistments", "student_id"),
        new TableColumnRef("grades", "student_id"),
        new TableColumnRef("student_curriculum_assignments", "student_number"),
        new TableColumnRef("student_installment_plan", "student_number"),
        new TableColumnRef("student_reg_form_events", "student_number"),
        new TableColumnRef("student_document_events", "student_number"),
        new TableColumnRef("student_archive_files", "student_number"),
        new TableColumnRef("student_archive_custody_events", "student_number"),
        new TableColumnRef("student_withdrawal_requests", "student_number"),
        new TableColumnRef("student_withdrawal_request_lines", "student_number"),
        new TableColumnRef("transfer_credit_requests", "student_number"),
        new TableColumnRef("scholarship_review_workflow", "student_number"),
        new TableColumnRef("student_overpay_dispositions", "student_id"),
        new TableColumnRef("draft_pre_applicant", "student_number"),
        new TableColumnRef("tentative_credited_subject", "student_number")
    );

    private final JdbcTemplate db;
    private final StudentProfileService studentProfileService;

    public StudentIdentityReleaseService(JdbcTemplate db, StudentProfileService studentProfileService) {
        this.db = db;
        this.studentProfileService = studentProfileService;
    }

    @PostConstruct
    public void init() {
        ensureSchema();
    }

    public void ensureSchema() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS student_number_release_registry (
                released_student_number VARCHAR(100) NOT NULL PRIMARY KEY,
                archive_key VARCHAR(80) NOT NULL,
                release_status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
                released_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                released_by VARCHAR(100) NULL,
                release_note VARCHAR(500) NULL,
                reissued_reference_number VARCHAR(100) NULL,
                reissued_student_number VARCHAR(100) NULL,
                reissued_at TIMESTAMP NULL,
                reissued_by VARCHAR(100) NULL,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                KEY idx_snrr_archive (archive_key),
                KEY idx_snrr_status_released (release_status, released_at)
            )
            """);
        tryExecute("ALTER TABLE student_number_release_registry ADD COLUMN IF NOT EXISTS release_note VARCHAR(500) NULL");
        tryExecute("ALTER TABLE student_number_release_registry ADD COLUMN IF NOT EXISTS reissued_reference_number VARCHAR(100) NULL");
        tryExecute("ALTER TABLE student_number_release_registry ADD COLUMN IF NOT EXISTS reissued_student_number VARCHAR(100) NULL");
        tryExecute("ALTER TABLE student_number_release_registry ADD COLUMN IF NOT EXISTS reissued_at TIMESTAMP NULL");
        tryExecute("ALTER TABLE student_number_release_registry ADD COLUMN IF NOT EXISTS reissued_by VARCHAR(100) NULL");
        tryExecute("ALTER TABLE student_number_release_registry ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
        tryExecute("CREATE INDEX IF NOT EXISTS idx_snrr_archive ON student_number_release_registry (archive_key)");
        tryExecute("CREATE INDEX IF NOT EXISTS idx_snrr_status_released ON student_number_release_registry (release_status, released_at)");
    }

    public Map<String, Object> findReleaseRecord(String studentNumber, String archiveKey) {
        ensureSchema();
        String studentKey = clean(studentNumber);
        String archive = clean(archiveKey);
        if (studentKey == null && archive == null) {
            return null;
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT released_student_number, archive_key, release_status, released_at, released_by, release_note, " +
                "reissued_reference_number, reissued_student_number, reissued_at, reissued_by " +
                "FROM student_number_release_registry WHERE ");
        if (studentKey != null) {
            sql.append("released_student_number = ?");
            args.add(studentKey);
        }
        if (archive != null) {
            if (!args.isEmpty()) {
                sql.append(" OR ");
            }
            sql.append("archive_key = ?");
            args.add(archive);
        }
        sql.append(" ORDER BY released_at DESC LIMIT 1");
        try {
            return db.queryForMap(sql.toString(), args.toArray());
        } catch (Exception ignored) {
            return null;
        }
    }

    @Transactional
    public ReleaseResult releaseWithdrawnStudentNumber(String studentNumber, String actor, String note) {
        ensureSchema();
        String liveStudentNumber = clean(studentNumber);
        if (liveStudentNumber == null) {
            return new ReleaseResult(false, "Student number is required.", null, null);
        }

        Map<String, Object> existingRelease = findReleaseRecord(liveStudentNumber, null);
        if (existingRelease != null) {
            String archiveKey = clean(text(existingRelease.get("archive_key")));
            String status = clean(text(existingRelease.get("release_status")));
            return new ReleaseResult(
                false,
                "Student number " + liveStudentNumber + " is already in release status " + status + ".",
                liveStudentNumber,
                archiveKey);
        }

        Map<String, Object> student = loadLiveStudent(liveStudentNumber);
        if (student == null || student.isEmpty()) {
            return new ReleaseResult(false, "Student record was not found.", null, null);
        }
        if (!isWithdrawnStudent(student)) {
            return new ReleaseResult(false,
                "Only withdrawn students can be archived and released for future reissue.",
                liveStudentNumber,
                clean(text(student.get("archive_key"))));
        }

        String archiveKey = studentProfileService.snapshotArchivedIdentity(
            liveStudentNumber,
            clean(actor) != null ? clean(actor) : "registrar-release",
            "WITHDRAWN_RECORD");
        if (archiveKey == null) {
            return new ReleaseResult(false, "Archive key could not be prepared.", liveStudentNumber, null);
        }

        migrateStudentIdentityReferences(liveStudentNumber, archiveKey);
        upsertReleaseRegistry(liveStudentNumber, archiveKey, actor, note);

        return new ReleaseResult(
            true,
            "Student number " + liveStudentNumber + " was archived under " + archiveKey + " and is now available for reissue.",
            liveStudentNumber,
            archiveKey);
    }

    private void migrateStudentIdentityReferences(String fromStudentNumber, String archiveKey) {
        for (TableColumnRef ref : STRING_STUDENT_REFS) {
            updateStringIdentity(ref.tableName(), ref.columnName(), fromStudentNumber, archiveKey);
        }
        updatePaymentsReference(fromStudentNumber, archiveKey);
        updateSysUserUsername(fromStudentNumber, archiveKey);
        updateStudentsPrimaryIdentity(fromStudentNumber, archiveKey);
    }

    private void updatePaymentsReference(String fromStudentNumber, String archiveKey) {
        if (!tableExists("payments") || !columnExists("payments", "reference_number")) {
            return;
        }
        db.update(
            "UPDATE payments SET reference_number = ? WHERE reference_number = ?",
            archiveKey, fromStudentNumber);
    }

    private void updateSysUserUsername(String fromStudentNumber, String archiveKey) {
        if (!tableExists("sys_users") || !columnExists("sys_users", "username")) {
            return;
        }
        db.update(
            "UPDATE sys_users SET username = ? WHERE username = ?",
            archiveKey, fromStudentNumber);
    }

    private void updateStudentsPrimaryIdentity(String fromStudentNumber, String archiveKey) {
        if (!tableExists("students") || !columnExists("students", "student_number")) {
            return;
        }
        db.update(
            "UPDATE students SET student_number = ?, archive_key = ?, " +
                "admission_status = 'WITHDRAWN', status = 'WITHDRAWN', is_active = 0, enrollment_blocked = 1 " +
                "WHERE student_number = ?",
            archiveKey, archiveKey, fromStudentNumber);
    }

    private void upsertReleaseRegistry(String releasedStudentNumber, String archiveKey, String actor, String note) {
        db.update("""
            INSERT INTO student_number_release_registry (
                released_student_number, archive_key, release_status, released_by, release_note
            ) VALUES (?, ?, 'AVAILABLE', ?, ?)
            ON DUPLICATE KEY UPDATE
                archive_key = VALUES(archive_key),
                release_status = 'AVAILABLE',
                released_by = VALUES(released_by),
                release_note = VALUES(release_note),
                updated_at = CURRENT_TIMESTAMP
            """,
            releasedStudentNumber,
            archiveKey,
            clean(actor),
            clean(note));
    }

    private void updateStringIdentity(String tableName, String columnName, String fromStudentNumber, String archiveKey) {
        if (!tableExists(tableName) || !columnExists(tableName, columnName)) {
            return;
        }
        db.update(
            "UPDATE " + tableName + " SET " + columnName + " = ? WHERE " + columnName + " = ?",
            archiveKey, fromStudentNumber);
    }

    private Map<String, Object> loadLiveStudent(String studentNumber) {
        try {
            return db.queryForMap(
                "SELECT student_number, archive_key, admission_status, status, is_active " +
                    "FROM students WHERE student_number = ? LIMIT 1",
                studentNumber);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isWithdrawnStudent(Map<String, Object> student) {
        String admissionStatus = text(student.get("admission_status"));
        String status = text(student.get("status"));
        boolean inactive = student.get("is_active") instanceof Number number && number.intValue() == 0;
        return "WITHDRAWN".equalsIgnoreCase(blank(admissionStatus))
            || "WITHDRAWN".equalsIgnoreCase(blank(status))
            || inactive;
    }

    private boolean tableExists(String tableName) {
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                    "WHERE UPPER(TABLE_SCHEMA) = UPPER(DATABASE()) AND UPPER(TABLE_NAME) = UPPER(?)",
                Integer.class, tableName);
            if (count != null && count > 0) {
                return true;
            }
            Integer fallback = db.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = UPPER(?)",
                Integer.class, tableName);
            return fallback != null && fallback > 0;
        } catch (Exception ignored) {
            try {
                Integer fallback = db.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = UPPER(?)",
                    Integer.class, tableName);
                return fallback != null && fallback > 0;
            } catch (Exception ignoredAgain) {
                return false;
            }
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE UPPER(TABLE_SCHEMA) = UPPER(DATABASE()) " +
                    "AND UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = UPPER(?)",
                Integer.class, tableName, columnName);
            if (count != null && count > 0) {
                return true;
            }
            Integer fallback = db.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = UPPER(?)",
                Integer.class, tableName, columnName);
            return fallback != null && fallback > 0;
        } catch (Exception ignored) {
            try {
                Integer fallback = db.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = UPPER(?)",
                    Integer.class, tableName, columnName);
                return fallback != null && fallback > 0;
            } catch (Exception ignoredAgain) {
                return false;
            }
        }
    }

    private boolean isStringColumn(String tableName, String columnName) {
        try {
            String dataType = db.queryForObject(
                "SELECT LOWER(DATA_TYPE) FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE UPPER(TABLE_SCHEMA) = UPPER(DATABASE()) " +
                    "AND UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = UPPER(?) LIMIT 1",
                String.class, tableName, columnName);
            if (dataType != null) {
                Set<String> textTypes = Set.of("varchar", "char", "text", "tinytext", "mediumtext", "longtext");
                return textTypes.contains(dataType.toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) {
        }
        try {
            String fallbackType = db.queryForObject(
                "SELECT LOWER(DATA_TYPE) FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = UPPER(?) LIMIT 1",
                String.class, tableName, columnName);
            if (fallbackType == null) {
                return false;
            }
            Set<String> textTypes = Set.of("varchar", "char", "text", "tinytext", "mediumtext", "longtext");
            return textTypes.contains(fallbackType.toLowerCase(Locale.ROOT));
        } catch (Exception ignoredAgain) {
            return false;
        }
   }

    private void tryExecute(String sql) {
        try {
            db.execute(sql);
        } catch (Exception ignored) {
        }
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private record TableColumnRef(String tableName, String columnName) {}
}
