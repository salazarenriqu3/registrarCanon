package com.iuims.registrar.service.academic;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Student;
import com.iuims.registrar.entity.Grade;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.iuims.registrar.service.support.RegistrarAuditTrailService;
import com.iuims.registrar.service.forms.StudentDocumentTrailService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GradeRecordEventService {

    private final JdbcTemplate db;
    private final StudentDocumentTrailService documentTrailService;
    private final RegistrarAuditTrailService auditTrailService;

    public GradeRecordEventService(JdbcTemplate db, StudentDocumentTrailService documentTrailService) {
        this(db, documentTrailService, null);
    }

    @Autowired
    public GradeRecordEventService(JdbcTemplate db,
                                   StudentDocumentTrailService documentTrailService,
                                   RegistrarAuditTrailService auditTrailService) {
        this.db = db;
        this.documentTrailService = documentTrailService;
        this.auditTrailService = auditTrailService;
    }

    public void ensureSchema() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS grade_record_events (
                event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                grade_id BIGINT NOT NULL,
                request_id BIGINT NULL,
                student_id VARCHAR(100) NULL,
                student_name VARCHAR(100) NULL,
                course_id INT NULL,
                course_code VARCHAR(20) NULL,
                section_id INT NULL,
                section_code VARCHAR(50) NULL,
                term_id INT NULL,
                term_label VARCHAR(40) NULL,
                action_type VARCHAR(60) NOT NULL,
                lifecycle_status VARCHAR(30) NOT NULL,
                actor VARCHAR(100) NULL,
                actor_role VARCHAR(50) NULL,
                reason VARCHAR(500) NULL,
                component_before VARCHAR(120) NULL,
                component_after VARCHAR(120) NULL,
                official_grade_before DECIMAL(5,2) NULL,
                official_grade_after DECIMAL(5,2) NULL,
                official_remarks_before VARCHAR(30) NULL,
                official_remarks_after VARCHAR(30) NULL,
                grade_lock_status_before VARCHAR(30) NULL,
                grade_lock_status_after VARCHAR(30) NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                KEY idx_gre_grade_created (grade_id, created_at),
                KEY idx_gre_student_created (student_id, created_at),
                KEY idx_gre_term_action (term_id, action_type, created_at),
                KEY idx_gre_lifecycle_created (lifecycle_status, created_at)
            )
            """);
        addColumn("request_id", "BIGINT NULL");
        addColumn("student_id", "VARCHAR(100) NULL");
        addColumn("student_name", "VARCHAR(100) NULL");
        addColumn("course_id", "INT NULL");
        addColumn("course_code", "VARCHAR(20) NULL");
        addColumn("section_id", "INT NULL");
        addColumn("section_code", "VARCHAR(50) NULL");
        addColumn("term_id", "INT NULL");
        addColumn("term_label", "VARCHAR(40) NULL");
        addColumn("action_type", "VARCHAR(60) NOT NULL DEFAULT 'GRADE_EVENT'");
        addColumn("lifecycle_status", "VARCHAR(30) NOT NULL DEFAULT 'DRAFT'");
        addColumn("actor", "VARCHAR(100) NULL");
        addColumn("actor_role", "VARCHAR(50) NULL");
        addColumn("reason", "VARCHAR(500) NULL");
        addColumn("component_before", "VARCHAR(120) NULL");
        addColumn("component_after", "VARCHAR(120) NULL");
        addColumn("official_grade_before", "DECIMAL(5,2) NULL");
        addColumn("official_grade_after", "DECIMAL(5,2) NULL");
        addColumn("official_remarks_before", "VARCHAR(30) NULL");
        addColumn("official_remarks_after", "VARCHAR(30) NULL");
        addColumn("grade_lock_status_before", "VARCHAR(30) NULL");
        addColumn("grade_lock_status_after", "VARCHAR(30) NULL");
        createIndex("idx_gre_grade_created", "grade_id, created_at");
        createIndex("idx_gre_student_created", "student_id, created_at");
        createIndex("idx_gre_term_action", "term_id, action_type, created_at");
        createIndex("idx_gre_lifecycle_created", "lifecycle_status, created_at");
    }

    public void recordEvent(Grade grade,
                            Long requestId,
                            String actionType,
                            String lifecycleStatus,
                            String actor,
                            String actorRole,
                            String reason,
                            GradeSnapshot before,
                            GradeSnapshot after) {
        if (grade == null) {
            return;
        }
        ensureSchema();
        Map<String, Object> refs = resolveReferences(grade);
        db.update("""
            INSERT INTO grade_record_events
                (grade_id, request_id, student_id, student_name, course_id, course_code, section_id, section_code,
                 term_id, term_label, action_type, lifecycle_status, actor, actor_role, reason,
                 component_before, component_after, official_grade_before, official_grade_after,
                 official_remarks_before, official_remarks_after, grade_lock_status_before, grade_lock_status_after)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            grade.getId(),
            requestId,
            clean(grade.getStudentId(), 100),
            clean(resolveStudentName(grade), 100),
            grade.getCourseId(),
            clean((String) refs.get("course_code"), 20),
            grade.getSectionId(),
            clean((String) refs.get("section_code"), 50),
            refs.get("term_id"),
            clean((String) refs.get("term_label"), 40),
            clean(defaultString(actionType, "GRADE_EVENT"), 60),
            clean(defaultString(lifecycleStatus, "DRAFT"), 30),
            clean(actor, 100),
            clean(actorRole, 50),
            clean(reason, 500),
            before != null ? before.componentSummary() : null,
            after != null ? after.componentSummary() : null,
            before != null ? before.officialGrade() : null,
            after != null ? after.officialGrade() : null,
            before != null ? clean(before.officialRemarks(), 30) : null,
            after != null ? clean(after.officialRemarks(), 30) : null,
            before != null ? clean(before.gradeLockStatus(), 30) : null,
            after != null ? clean(after.gradeLockStatus(), 30) : null);

        if (grade.getStudentId() != null && !grade.getStudentId().isBlank()) {
            documentTrailService.recordStudentEvent(
                grade.getStudentId(),
                "STUDENT",
                "GRADE_RECORD",
                clean(defaultString(actionType, "GRADE_EVENT"), 80),
                summarize(actionType, refs),
                clean(reason, 500),
                actor,
                requestId,
                "grade_record_events",
                String.valueOf(grade.getId()));
        }
        recordAudit(actor, actorRole, grade, actionType, reason);
    }

    public Map<String, Object> buildSummary(Integer termId) {
        ensureSchema();
        Integer resolvedTermId = normalizePositive(termId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("official_rows", countOfficialRows(resolvedTermId));
        out.put("finalized_rows", countBySql("""
            SELECT COUNT(*) FROM grades g
            LEFT JOIN class_sections cs ON cs.section_id = g.section_id
            WHERE (? IS NULL OR cs.term_id = ?)
              AND UPPER(COALESCE(g.grade_lock_status, '')) IN ('LOCKED', 'FINALIZED')
            """, resolvedTermId, resolvedTermId));
        out.put("pending_requests", countBySql("""
            SELECT COUNT(*) FROM grade_change_requests r
            LEFT JOIN grades g ON g.id = r.grade_id
            LEFT JOIN class_sections cs ON cs.section_id = g.section_id
            WHERE r.status = 'PENDING' AND (? IS NULL OR cs.term_id = ?)
            """, resolvedTermId, resolvedTermId));
        out.put("approved_requests", countBySql("""
            SELECT COUNT(*) FROM grade_change_requests r
            LEFT JOIN grades g ON g.id = r.grade_id
            LEFT JOIN class_sections cs ON cs.section_id = g.section_id
            WHERE r.status = 'APPROVED' AND (? IS NULL OR cs.term_id = ?)
            """, resolvedTermId, resolvedTermId));
        out.put("rejected_requests", countBySql("""
            SELECT COUNT(*) FROM grade_change_requests r
            LEFT JOIN grades g ON g.id = r.grade_id
            LEFT JOIN class_sections cs ON cs.section_id = g.section_id
            WHERE r.status = 'REJECTED' AND (? IS NULL OR cs.term_id = ?)
            """, resolvedTermId, resolvedTermId));
        out.put("inc_rows", countBySql("""
            SELECT COUNT(*) FROM grades g
            LEFT JOIN class_sections cs ON cs.section_id = g.section_id
            WHERE (? IS NULL OR cs.term_id = ?)
              AND UPPER(COALESCE(g.registrar_final_remarks, g.remarks, '')) = 'INC'
            """, resolvedTermId, resolvedTermId));
        out.put("change_events", countBySql("""
            SELECT COUNT(*) FROM grade_record_events
            WHERE (? IS NULL OR term_id = ?)
              AND action_type IN ('GRADE_CHANGE_REQUESTED', 'GRADE_CHANGE_APPROVED', 'GRADE_CHANGE_REJECTED', 'GRADE_ROW_REOPENED')
            """, resolvedTermId, resolvedTermId));
        return out;
    }

    public List<Map<String, Object>> listGradeRegistryRows(Integer termId, String query, String lifecycleStatus, int limit) {
        ensureSchema();
        StringBuilder sql = new StringBuilder("""
            SELECT
                g.id AS grade_id,
                g.student_id,
                COALESCE(NULLIF(g.student_name, ''), g.student_id) AS student_name,
                c.course_code,
                c.course_title,
                cs.section_code,
                cs.term_id,
                CASE
                    WHEN at.academic_year IS NOT NULL THEN CONCAT('A.Y. ', at.academic_year, ' - ', at.semester_number)
                    ELSE 'Historical / No section'
                END AS term_label,
                COALESCE(g.registrar_final_grade, g.semestral_grade) AS official_grade,
                COALESCE(g.registrar_final_remarks, g.remarks, 'Ongoing') AS official_remarks,
                CASE
                    WHEN UPPER(COALESCE(g.grade_lock_status, '')) IN ('LOCKED', 'FINALIZED') THEN 'FINALIZED'
                    WHEN COALESCE(g.status, 'DRAFT') = 'SUBMITTED' THEN 'SUBMITTED'
                    ELSE 'DRAFT'
                END AS lifecycle_status,
                g.grade_lock_status,
                g.status AS row_status,
                g.registrar_finalized_at,
                g.date_recorded,
                CONCAT(
                    'P:', COALESCE(CAST(g.prelim AS CHAR), '-'),
                    ' / M:', COALESCE(CAST(g.midterm AS CHAR), '-'),
                    ' / F:', COALESCE(CAST(g.final_grade AS CHAR), '-')
                ) AS component_scores,
                (
                    SELECT COUNT(*)
                    FROM grade_change_requests r
                    WHERE r.grade_id = g.id AND r.status = 'PENDING'
                ) AS pending_requests
            FROM grades g
            LEFT JOIN courses c ON c.course_id = g.course_id
            LEFT JOIN class_sections cs ON cs.section_id = g.section_id
            LEFT JOIN academic_terms at ON at.term_id = cs.term_id
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        Integer resolvedTermId = normalizePositive(termId);
        if (resolvedTermId != null) {
            sql.append(" AND cs.term_id = ? ");
            args.add(resolvedTermId);
        }
        if (query != null && !query.isBlank()) {
            sql.append("""
                 AND (
                    COALESCE(g.student_id, '') LIKE ?
                    OR COALESCE(g.student_name, '') LIKE ?
                    OR COALESCE(c.course_code, '') LIKE ?
                    OR COALESCE(c.course_title, '') LIKE ?
                    OR COALESCE(cs.section_code, '') LIKE ?
                 )
                """);
            String like = "%" + query.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (lifecycleStatus != null && !lifecycleStatus.isBlank()) {
            String normalized = lifecycleStatus.trim().toUpperCase();
            if (!"ALL".equals(normalized)) {
                sql.append("""
                    AND (
                        CASE
                            WHEN UPPER(COALESCE(g.grade_lock_status, '')) IN ('LOCKED', 'FINALIZED') THEN 'FINALIZED'
                            WHEN COALESCE(g.status, 'DRAFT') = 'SUBMITTED' THEN 'SUBMITTED'
                            ELSE 'DRAFT'
                        END
                    ) = ?
                    """);
                args.add(normalized);
            }
        }
        sql.append(" ORDER BY COALESCE(g.registrar_finalized_at, g.date_recorded, CURRENT_TIMESTAMP) DESC, g.id DESC LIMIT ? ");
        args.add(safeLimit(limit));
        return db.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> listGradeRecordEvents(Integer termId,
                                                           String query,
                                                           String actionType,
                                                           String lifecycleStatus,
                                                           int limit) {
        ensureSchema();
        StringBuilder sql = new StringBuilder("""
            SELECT
                event_id,
                grade_id,
                request_id,
                student_id,
                student_name,
                course_code,
                section_code,
                term_id,
                term_label,
                action_type,
                lifecycle_status,
                actor,
                actor_role,
                reason,
                component_before,
                component_after,
                official_grade_before,
                official_grade_after,
                official_remarks_before,
                official_remarks_after,
                grade_lock_status_before,
                grade_lock_status_after,
                created_at
            FROM grade_record_events
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        Integer resolvedTermId = normalizePositive(termId);
        if (resolvedTermId != null) {
            sql.append(" AND term_id = ? ");
            args.add(resolvedTermId);
        }
        if (query != null && !query.isBlank()) {
            sql.append("""
                AND (
                    COALESCE(student_id, '') LIKE ?
                    OR COALESCE(student_name, '') LIKE ?
                    OR COALESCE(course_code, '') LIKE ?
                    OR COALESCE(section_code, '') LIKE ?
                    OR COALESCE(actor, '') LIKE ?
                    OR COALESCE(reason, '') LIKE ?
                )
                """);
            String like = "%" + query.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (actionType != null && !actionType.isBlank() && !"ALL".equalsIgnoreCase(actionType.trim())) {
            sql.append(" AND action_type = ? ");
            args.add(actionType.trim().toUpperCase());
        }
        if (lifecycleStatus != null && !lifecycleStatus.isBlank() && !"ALL".equalsIgnoreCase(lifecycleStatus.trim())) {
            sql.append(" AND lifecycle_status = ? ");
            args.add(lifecycleStatus.trim().toUpperCase());
        }
        sql.append(" ORDER BY created_at DESC, event_id DESC LIMIT ? ");
        args.add(safeLimit(limit));
        return db.queryForList(sql.toString(), args.toArray());
    }

    private long countOfficialRows(Integer termId) {
        return countBySql("""
            SELECT COUNT(*) FROM grades g
            LEFT JOIN class_sections cs ON cs.section_id = g.section_id
            WHERE (? IS NULL OR cs.term_id = ?)
            """, termId, termId);
    }

    private long countBySql(String sql, Object... args) {
        try {
            Long count = db.queryForObject(sql, Long.class, args);
            return count != null ? count : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private Map<String, Object> resolveReferences(Grade grade) {
        Map<String, Object> refs = new LinkedHashMap<>();
        refs.put("course_code", null);
        refs.put("section_code", null);
        refs.put("term_id", null);
        refs.put("term_label", "Historical / No section");
        if (grade.getCourseId() != null) {
            try {
                refs.put("course_code", db.queryForObject(
                    "SELECT course_code FROM courses WHERE course_id = ? LIMIT 1",
                    String.class,
                    grade.getCourseId()));
            } catch (Exception ignored) {
            }
        }
        if (grade.getSectionId() != null) {
            try {
                Map<String, Object> row = db.queryForMap("""
                    SELECT
                        cs.section_code,
                        cs.term_id,
                        CASE
                            WHEN at.academic_year IS NOT NULL THEN CONCAT('A.Y. ', at.academic_year, ' - ', at.semester_number)
                            ELSE 'Historical / No section'
                        END AS term_label
                    FROM class_sections cs
                    LEFT JOIN academic_terms at ON at.term_id = cs.term_id
                    WHERE cs.section_id = ?
                    LIMIT 1
                    """, grade.getSectionId());
                refs.put("section_code", row.get("section_code"));
                refs.put("term_id", row.get("term_id"));
                refs.put("term_label", row.get("term_label"));
            } catch (Exception ignored) {
            }
        }
        return refs;
    }

    private String summarize(String actionType, Map<String, Object> refs) {
        String courseCode = refs.get("course_code") != null ? refs.get("course_code").toString() : "course";
        return switch (defaultString(actionType, "GRADE_EVENT")) {
            case "GRADE_DRAFT_SAVED" -> "Grade draft saved for " + courseCode;
            case "GRADE_CLASS_SUBMITTED" -> "Class grade row submitted for " + courseCode;
            case "GRADE_CLASS_POSTED" -> "Official grade posted for " + courseCode;
            case "GRADE_CHANGE_REQUESTED" -> "Grade change requested for " + courseCode;
            case "GRADE_CHANGE_APPROVED" -> "Grade change approved for " + courseCode;
            case "GRADE_CHANGE_REJECTED" -> "Grade change rejected for " + courseCode;
            case "GRADE_ROW_REOPENED" -> "Grade row reopened for " + courseCode;
            case "INC_EXPIRED" -> "INC expired for " + courseCode;
            case "DEMO_GRADE_DRAFT_SAVED" -> "Demo grade draft saved for " + courseCode;
            case "DEMO_GRADE_SUBMITTED" -> "Demo grade submitted for " + courseCode;
            case "DEMO_GRADE_APPROVED" -> "Demo grade approved for " + courseCode;
            case "DEMO_GRADE_REJECTED" -> "Demo grade rejected for " + courseCode;
            default -> "Grade record updated for " + courseCode;
        };
    }

    private String resolveStudentName(Grade grade) {
        if (grade.getStudentName() != null && !grade.getStudentName().isBlank()) {
            return grade.getStudentName();
        }
        return grade.getStudentId();
    }

    private void recordAudit(String actor, String actorRole, Grade grade, String actionType, String reason) {
        if (auditTrailService == null || grade == null) {
            return;
        }
        auditTrailService.record(
            actor,
            actorRole,
            "GRADE_RECORDS",
            defaultString(actionType, "GRADE_EVENT"),
            "GRADE",
            String.valueOf(grade.getId()),
            summarize(actionType, resolveReferences(grade)),
            clean(reason, 500),
            "grade_record_events",
            String.valueOf(grade.getId()));
    }

    private Integer normalizePositive(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    private void addColumn(String columnName, String definition) {
        try {
            db.execute("ALTER TABLE grade_record_events ADD COLUMN " + columnName + " " + definition);
        } catch (Exception ignored) {
        }
    }

    private void createIndex(String indexName, String columns) {
        try {
            db.execute("CREATE INDEX " + indexName + " ON grade_record_events (" + columns + ")");
        } catch (Exception ignored) {
        }
    }

    private String clean(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private String defaultString(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    public static GradeSnapshot snapshotOf(Grade grade) {
        if (grade == null) {
            return null;
        }
        return new GradeSnapshot(
            grade.getPrelim(),
            grade.getMidterm(),
            grade.getFinalGrade(),
            grade.getSemestralGrade(),
            grade.getRemarks(),
            grade.getRegistrarFinalGrade(),
            grade.getRegistrarFinalRemarks(),
            grade.getGradeLockStatus(),
            grade.getStatus());
    }

    public record GradeSnapshot(BigDecimal prelim,
                                BigDecimal midterm,
                                BigDecimal finalGrade,
                                BigDecimal semestralGrade,
                                String remarks,
                                BigDecimal registrarFinalGrade,
                                String registrarFinalRemarks,
                                String gradeLockStatus,
                                String status) {

        public BigDecimal officialGrade() {
            return registrarFinalGrade != null ? registrarFinalGrade : semestralGrade;
        }

        public String officialRemarks() {
            return registrarFinalRemarks != null && !registrarFinalRemarks.isBlank() ? registrarFinalRemarks : remarks;
        }

        public String componentSummary() {
            return "P:" + score(prelim) + " / M:" + score(midterm) + " / F:" + score(finalGrade);
        }

        private String score(BigDecimal value) {
            if (value == null) {
                return "-";
            }
            return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
