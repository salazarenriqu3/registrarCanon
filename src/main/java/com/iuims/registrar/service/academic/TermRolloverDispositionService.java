package com.iuims.registrar.service.academic;
import com.iuims.registrar.entity.Student;

import com.iuims.registrar.service.support.GlobalTermService;
import com.iuims.registrar.support.GradeOutcomeSql;
import com.iuims.registrar.service.forms.RegFormEventService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TermRolloverDispositionService {

    public static final String DECISION_PENDING = "PENDING";
    public static final String DECISION_PROMOTED = "PROMOTED";
    public static final String DECISION_SHIFTED = "SHIFTED";
    public static final String DECISION_WITHDRAWN = "WITHDRAWN";
    public static final String ENROLLMENT_MODE_REGULAR_BLOCK = "REGULAR_BLOCK";
    public static final String ENROLLMENT_MODE_IRREGULAR_MANUAL = "IRREGULAR_MANUAL";

    public record QueueResult(boolean success, String message, int affectedRows) {
        public static QueueResult ok(String message, int affectedRows) {
            return new QueueResult(true, message, affectedRows);
        }

        public static QueueResult fail(String message) {
            return new QueueResult(false, message, 0);
        }
    }

    private final JdbcTemplate db;
    private final GlobalTermService globalTermService;
    private final ObjectProvider<WithdrawalDispositionBridge> withdrawalDispositionBridgeProvider;
    private final RegFormEventService regFormEventService;

    public TermRolloverDispositionService(JdbcTemplate db,
                                          GlobalTermService globalTermService,
                                          ObjectProvider<WithdrawalDispositionBridge> withdrawalDispositionBridgeProvider,
                                          RegFormEventService regFormEventService) {
        this.db = db;
        this.globalTermService = globalTermService;
        this.withdrawalDispositionBridgeProvider = withdrawalDispositionBridgeProvider;
        this.regFormEventService = regFormEventService;
    }

    @PostConstruct
    public void init() {
        ensureSchema();
        normalizeCurrentTermQueueToAutomaticContinuation();
        backfillCurrentTermQueueIfNeeded();
        backfillCurrentTermQueueEnrollmentModes();
    }

    public void ensureSchema() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS student_term_rollover_queue (
                queue_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                student_number VARCHAR(100) NOT NULL,
                archive_key VARCHAR(80) NULL,
                source_term_code VARCHAR(50) NULL,
                source_term_id INT NULL,
                target_term_code VARCHAR(50) NOT NULL,
                target_term_id INT NOT NULL,
                program_code VARCHAR(20) NULL,
                year_level INT NULL,
                semester INT NULL,
                student_type VARCHAR(50) NULL,
                enrollment_status_type VARCHAR(50) NULL,
                next_enrollment_mode VARCHAR(30) NULL,
                decision_status VARCHAR(30) NOT NULL DEFAULT 'PROMOTED',
                decision_code VARCHAR(30) NULL,
                decision_note VARCHAR(500) NULL,
                seeded_by VARCHAR(100) NULL,
                seeded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                decided_by VARCHAR(100) NULL,
                decided_at TIMESTAMP NULL,
                action_payload TEXT NULL,
                KEY idx_strq_target_status (target_term_id, decision_status),
                KEY idx_strq_student_target (student_number, target_term_id),
                KEY idx_strq_archive (archive_key),
                UNIQUE KEY uq_stuq_student_target (student_number, target_term_id)
            )
            """);
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS archive_key VARCHAR(80) NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS source_term_code VARCHAR(50) NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS source_term_id INT NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS target_term_code VARCHAR(50) NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS target_term_id INT NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS program_code VARCHAR(20) NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS year_level INT NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS semester INT NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS student_type VARCHAR(50) NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS enrollment_status_type VARCHAR(50) NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS next_enrollment_mode VARCHAR(30) NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS decision_status VARCHAR(30) NULL DEFAULT 'PROMOTED'");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS decision_code VARCHAR(30) NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS decision_note VARCHAR(500) NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS seeded_by VARCHAR(100) NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS seeded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS decided_by VARCHAR(100) NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS decided_at TIMESTAMP NULL");
        tryExecute("ALTER TABLE student_term_rollover_queue ADD COLUMN IF NOT EXISTS action_payload TEXT NULL");
        tryExecute("CREATE INDEX IF NOT EXISTS idx_strq_target_status ON student_term_rollover_queue (target_term_id, decision_status)");
        tryExecute("CREATE INDEX IF NOT EXISTS idx_strq_student_target ON student_term_rollover_queue (student_number, target_term_id)");
        tryExecute("CREATE INDEX IF NOT EXISTS idx_strq_archive ON student_term_rollover_queue (archive_key)");
    }

    @Transactional
    public int seedQueueForTermTransition(List<Map<String, Object>> students,
                                          String sourceTermCode,
                                          Integer sourceTermId,
                                          String targetTermCode,
                                          Integer targetTermId,
                                          String seededBy) {
        ensureSchema();
        if (students == null || students.isEmpty() || targetTermCode == null || targetTermCode.isBlank() || targetTermId == null) {
            return 0;
        }
        int inserted = 0;
        String actor = clean(seededBy) != null ? clean(seededBy) : "registrar";
        for (Map<String, Object> student : students) {
            String studentNumber = clean(text(student.get("username")));
            if (studentNumber == null) {
                continue;
            }
            String archiveKey = clean(text(student.get("archive_key")));
            String programCode = clean(text(student.get("program_code")));
            Integer yearLevel = toInteger(student.get("year_level"));
            Integer semester = toInteger(student.get("semester"));
            String studentType = clean(text(student.get("student_type")));
            String enrollmentStatusType = clean(text(student.get("enrollment_status_type")));
            String nextEnrollmentMode = normalizeEnrollmentMode(text(student.get("next_enrollment_mode")));
            if (nextEnrollmentMode == null) {
                nextEnrollmentMode = resolveNextEnrollmentMode(studentNumber);
            }
            int affected = db.update("""
                    INSERT IGNORE INTO student_term_rollover_queue
                        (student_number, archive_key, source_term_code, source_term_id, target_term_code, target_term_id,
                         program_code, year_level, semester, student_type, enrollment_status_type, next_enrollment_mode,
                         decision_status, seeded_by, seeded_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PROMOTED', ?, CURRENT_TIMESTAMP)
                """,
                studentNumber,
                archiveKey,
                clean(sourceTermCode),
                sourceTermId,
                targetTermCode.trim(),
                targetTermId,
                programCode,
                yearLevel,
                semester,
                studentType,
                enrollmentStatusType,
                nextEnrollmentMode,
                actor);
            inserted += affected;
        }
        return inserted;
    }

    public List<Map<String, Object>> listQueueForTerm(Integer targetTermId, int limit) {
        ensureSchema();
        Integer tid = targetTermId != null ? targetTermId : globalTermService.getCurrentTermId();
        if (tid == null) {
            return new ArrayList<>();
        }
        int safeLimit = Math.max(1, Math.min(limit, 250));
        try {
            return db.queryForList("""
                SELECT queue_id, student_number, archive_key, source_term_code, source_term_id, target_term_code,
                       target_term_id, program_code, year_level, semester, student_type, enrollment_status_type,
                       next_enrollment_mode, decision_status, decision_code, decision_note, seeded_by, seeded_at,
                       decided_by, decided_at
                FROM student_term_rollover_queue
                WHERE target_term_id = ?
                ORDER BY CASE decision_status
                    WHEN 'PENDING' THEN 0
                    WHEN 'PROMOTED' THEN 1
                    WHEN 'SHIFTED' THEN 2
                    WHEN 'WITHDRAWN' THEN 3
                    ELSE 9 END,
                    seeded_at ASC, queue_id ASC
                LIMIT ?
                """, tid, safeLimit);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public Map<String, Object> summarizeQueueForTerm(Integer targetTermId) {
        ensureSchema();
        Integer tid = targetTermId != null ? targetTermId : globalTermService.getCurrentTermId();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("target_term_id", tid);
        summary.put("target_term_code", globalTermService.getCurrentGlobalTermCode());
        summary.put("total_count", 0);
        summary.put("pending_count", 0);
        summary.put("promoted_count", 0);
        summary.put("shifted_count", 0);
        summary.put("withdrawn_count", 0);
        summary.put("next_enrollment_mode", null);
        if (tid == null) {
            return summary;
        }
        List<Map<String, Object>> rows;
        try {
            rows = db.queryForList("""
                SELECT decision_status, COUNT(*) AS cnt
                FROM student_term_rollover_queue
                WHERE target_term_id = ?
                GROUP BY decision_status
                """, tid);
        } catch (Exception e) {
            return summary;
        }
        int total = 0;
        for (Map<String, Object> row : rows) {
            String status = clean(text(row.get("decision_status")));
            int cnt = toInteger(row.get("cnt")) != null ? toInteger(row.get("cnt")) : 0;
            total += cnt;
            switch (normalizeDecision(status)) {
                case DECISION_PROMOTED -> summary.put("promoted_count", cnt);
                case DECISION_SHIFTED -> summary.put("shifted_count", cnt);
                case DECISION_WITHDRAWN -> summary.put("withdrawn_count", cnt);
                default -> summary.put("pending_count", cnt);
            }
        }
        summary.put("total_count", total);
        return summary;
    }

    public Map<String, Object> findQueueEntry(String studentNumber, Integer targetTermId) {
        ensureSchema();
        String sn = clean(studentNumber);
        Integer tid = targetTermId != null ? targetTermId : globalTermService.getCurrentTermId();
        if (sn == null || tid == null) {
            return null;
        }
        try {
            return db.queryForMap("""
                SELECT queue_id, student_number, archive_key, source_term_code, source_term_id, target_term_code,
                       target_term_id, program_code, year_level, semester, student_type, enrollment_status_type,
                       next_enrollment_mode, decision_status, decision_code, decision_note, seeded_by, seeded_at,
                       decided_by, decided_at
                FROM student_term_rollover_queue
                WHERE student_number = ? AND target_term_id = ?
                LIMIT 1
                """, sn, tid);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public QueueResult decide(String studentNumber, Integer targetTermId, String decision, String note, String decidedBy) {
        ensureSchema();
        String sn = clean(studentNumber);
        if (sn == null) {
            return QueueResult.fail("Student number is required.");
        }
        Integer tid = targetTermId != null ? targetTermId : globalTermService.getCurrentTermId();
        if (tid == null) {
            return QueueResult.fail("Active term could not be determined.");
        }
        String decisionCode = normalizeDecision(decision);
        if (DECISION_PENDING.equals(decisionCode)) {
            return QueueResult.fail("Choose PROMOTE, SHIFT, or WITHDRAW.");
        }
        Map<String, Object> entry = findQueueEntry(sn, tid);
        if (entry == null) {
            return QueueResult.fail("No rollover queue row was found for this student in the active term.");
        }
        String actor = clean(decidedBy) != null ? clean(decidedBy) : "registrar";
        String decisionNote = truncate(note, 500);
        db.update("""
            UPDATE student_term_rollover_queue
            SET decision_status = ?, decision_code = ?, decision_note = ?, decided_by = ?, decided_at = CURRENT_TIMESTAMP
            WHERE student_number = ? AND target_term_id = ?
            """, decisionCode, decisionCode, decisionNote, actor, sn, tid);

        if (DECISION_WITHDRAWN.equals(decisionCode)) {
            WithdrawalDispositionBridge bridge = withdrawalDispositionBridgeProvider.getIfAvailable();
            if (bridge != null) {
                bridge.withdrawStudentForQueue(sn, decisionNote, actor);
            }
        } else if (DECISION_SHIFTED.equals(decisionCode)) {
            WithdrawalDispositionBridge bridge = withdrawalDispositionBridgeProvider.getIfAvailable();
            if (bridge != null) {
                bridge.clearForShift(sn, decisionNote, actor);
            }
        }

        try {
            regFormEventService.recordEvent(
                sn,
                "TERM_ROLLOVER_DECISION",
                "Registrar term rollover disposition set",
                null,
                "Decision=" + decisionCode + (decisionNote != null && !decisionNote.isBlank() ? " | " + decisionNote : ""),
                actor);
        } catch (Exception ignored) {
        }

        return QueueResult.ok("Recorded " + decisionCode + " for " + sn + ".", 1);
    }

    @Transactional
    public int normalizeCurrentTermQueueToAutomaticContinuation() {
        Integer currentTermId = globalTermService.getCurrentTermId();
        if (currentTermId == null) {
            return 0;
        }
        try {
            return db.update("""
                UPDATE student_term_rollover_queue
                SET decision_status = 'PROMOTED',
                    decision_code = COALESCE(NULLIF(decision_code, ''), 'PROMOTED'),
                    decision_note = COALESCE(NULLIF(decision_note, ''), 'Auto-carried forward after term transition'),
                    decided_by = COALESCE(NULLIF(decided_by, ''), 'system'),
                    decided_at = COALESCE(decided_at, CURRENT_TIMESTAMP)
                WHERE target_term_id = ?
                  AND UPPER(COALESCE(decision_status, 'PENDING')) = 'PENDING'
                """, currentTermId);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void backfillCurrentTermQueueIfNeeded() {
        Integer currentTermId = globalTermService.getCurrentTermId();
        if (currentTermId == null) {
            return;
        }

        try {
            Integer existing = db.queryForObject(
                "SELECT COUNT(*) FROM student_term_rollover_queue WHERE target_term_id = ?",
                Integer.class,
                currentTermId);
            if (existing != null && existing > 0) {
                return;
            }
        } catch (Exception ignored) {
            return;
        }

        String currentTermCode = clean(globalTermService.getCurrentGlobalTermCode());
        if (currentTermCode == null) {
            return;
        }

        try {
            int inserted = db.update("""
                INSERT IGNORE INTO student_term_rollover_queue
                    (student_number, archive_key, source_term_code, source_term_id, target_term_code, target_term_id,
                     program_code, year_level, semester, student_type, enrollment_status_type, decision_status,
                     seeded_by, seeded_at)
                SELECT
                    s.student_number,
                    s.archive_key,
                    prev.term_code AS source_term_code,
                    s.entry_term_id AS source_term_id,
                    ? AS target_term_code,
                    ? AS target_term_id,
                    s.program_code,
                    s.year_level,
                    s.semester,
                    s.student_type,
                    s.enrollment_status_type,
                    'PROMOTED' AS decision_status,
                    'registrar' AS seeded_by,
                    CURRENT_TIMESTAMP
                FROM students s
                LEFT JOIN academic_terms prev ON prev.term_id = s.entry_term_id
                WHERE s.entry_term_id IS NOT NULL
                  AND s.entry_term_id < ?
                  AND UPPER(COALESCE(s.admission_status, '')) <> 'WITHDRAWN'
                  AND UPPER(COALESCE(s.status, '')) <> 'WITHDRAWN'
                """,
                currentTermCode,
                currentTermId,
                currentTermId);
            if (inserted > 0) {
                try {
                    regFormEventService.recordEvent(
                        "SYSTEM",
                        "TERM_ROLLOVER_BACKFILL",
                        "Registrar backfilled rollover queue for current term",
                        null,
                        "Inserted " + inserted + " carried student(s) into the rollover queue.",
                        "registrar");
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private int backfillCurrentTermQueueEnrollmentModes() {
        Integer currentTermId = globalTermService.getCurrentTermId();
        if (currentTermId == null) {
            return 0;
        }
        List<Map<String, Object>> rows;
        try {
            rows = db.queryForList("""
                SELECT queue_id, student_number
                FROM student_term_rollover_queue
                WHERE target_term_id = ?
                  AND (next_enrollment_mode IS NULL OR TRIM(next_enrollment_mode) = '')
                """, currentTermId);
        } catch (Exception ignored) {
            return 0;
        }
        int updated = 0;
        for (Map<String, Object> row : rows) {
            Long queueId = row.get("queue_id") instanceof Number n ? n.longValue() : null;
            String studentNumber = clean(text(row.get("student_number")));
            if (queueId == null || studentNumber == null) {
                continue;
            }
            updated += db.update(
                "UPDATE student_term_rollover_queue SET next_enrollment_mode = ? WHERE queue_id = ?",
                resolveNextEnrollmentMode(studentNumber), queueId);
        }
        return updated;
    }

    public String resolveNextEnrollmentMode(String studentNumber) {
        String sn = clean(studentNumber);
        if (sn == null) {
            return ENROLLMENT_MODE_IRREGULAR_MANUAL;
        }
        try {
            Integer curriculumId = db.queryForObject("""
                SELECT sca.curriculum_id
                FROM student_curriculum_assignments sca
                WHERE sca.student_number = ?
                  AND COALESCE(sca.is_current, 0) = 1
                ORDER BY sca.assignment_id DESC
                LIMIT 1
                """, Integer.class, sn);
            if (curriculumId == null) {
                return ENROLLMENT_MODE_IRREGULAR_MANUAL;
            }
            int deficiencyCount = curriculumDeficiencyCount(sn, curriculumId);
            return deficiencyCount > 0 ? ENROLLMENT_MODE_IRREGULAR_MANUAL : ENROLLMENT_MODE_REGULAR_BLOCK;
        } catch (Exception ignored) {
            return ENROLLMENT_MODE_IRREGULAR_MANUAL;
        }
    }

    private int curriculumDeficiencyCount(String studentNumber, Integer curriculumId) {
        if (studentNumber == null || studentNumber.isBlank() || curriculumId == null) {
            return 0;
        }
        List<Object> keys = gradeLookupKeys(studentNumber);
        if (keys.isEmpty()) {
            return 0;
        }
        String in = gradeInClause(keys.size());
        Object[] args = new Object[keys.size() + 1];
        args[0] = curriculumId;
        for (int i = 0; i < keys.size(); i++) {
            args[i + 1] = keys.get(i);
        }
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM curriculum_courses cc WHERE cc.curriculum_id = ? "
                    + "AND NOT EXISTS (SELECT 1 FROM grades g WHERE g.course_id = cc.course_id AND "
                    + in + " AND " + GradeOutcomeSql.passed("g") + ")",
                Integer.class,
                args);
            return count != null ? count : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private List<Object> gradeLookupKeys(String studentNumber) {
        List<Object> keys = new ArrayList<>();
        keys.add(studentNumber);
        try {
            Integer userId = db.queryForObject(
                "SELECT user_id FROM sys_users WHERE username = ? LIMIT 1",
                Integer.class,
                studentNumber);
            if (userId != null) {
                keys.add(String.valueOf(userId));
            }
        } catch (Exception ignored) {
        }
        return keys;
    }

    private String gradeInClause(int count) {
        StringBuilder sb = new StringBuilder("g.student_id IN (");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        sb.append(')');
        return sb.toString();
    }

    private String normalizeEnrollmentMode(String mode) {
        String value = clean(mode);
        if (value == null) {
            return null;
        }
        value = value.toUpperCase(Locale.ROOT);
        return switch (value) {
            case "REGULAR_BLOCK", "REGULAR" -> ENROLLMENT_MODE_REGULAR_BLOCK;
            case "IRREGULAR_MANUAL", "IRREGULAR" -> ENROLLMENT_MODE_IRREGULAR_MANUAL;
            default -> null;
        };
    }

    private String normalizeDecision(String decision) {
        String value = clean(decision);
        if (value == null) {
            return DECISION_PENDING;
        }
        value = value.toUpperCase(Locale.ROOT);
        if ("PROMOTE".equals(value) || "PROMOTED".equals(value)) {
            return DECISION_PROMOTED;
        }
        if ("SHIFT".equals(value) || "SHIFTED".equals(value)) {
            return DECISION_SHIFTED;
        }
        if ("WITHDRAW".equals(value) || "WITHDRAWN".equals(value)) {
            return DECISION_WITHDRAWN;
        }
        return DECISION_PENDING;
    }

    private void tryExecute(String sql) {
        try {
            db.execute(sql);
        } catch (Exception ignored) {
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (Exception ignored) {
            return null;
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

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    public interface WithdrawalDispositionBridge {
        void withdrawStudentForQueue(String studentNumber, String remarks, String actor);

        void clearForShift(String studentNumber, String remarks, String actor);
    }
}
