package com.iuims.registrar.service.forms;
import com.iuims.registrar.entity.Student;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iuims.registrar.service.admission.FinanceAdmissionService;
import com.iuims.registrar.service.support.EnlistmentSchemaService;
import com.iuims.registrar.service.support.StudentProfileService;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RegFormVersionService {

    private static final Set<String> AUTO_VERSION_EVENT_TYPES = Set.of(
        "SUBJECT_ADD",
        "CURRICULUM_ASSIGNED",
        "PROGRAM_SHIFT",
        "SHIFT_LOAD_CLEARED",
        "WITHDRAWAL_COMPLETED"
    );

    private static final TypeReference<Map<String, Object>> SNAPSHOT_TYPE = new TypeReference<>() {};

    private final JdbcTemplate db;
    private final StudentProfileService studentProfileService;
    private final FinanceAdmissionService financeService;
    private final RegistrationFormPdfService registrationFormPdfService;
    private final EnlistmentSchemaService enlistmentSchemaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RegFormVersionService(JdbcTemplate db,
                                 StudentProfileService studentProfileService,
                                 FinanceAdmissionService financeService,
                                 RegistrationFormPdfService registrationFormPdfService,
                                 EnlistmentSchemaService enlistmentSchemaService) {
        this.db = db;
        this.studentProfileService = studentProfileService;
        this.financeService = financeService;
        this.registrationFormPdfService = registrationFormPdfService;
        this.enlistmentSchemaService = enlistmentSchemaService;
    }

    public boolean shouldCaptureForEvent(String eventType) {
        return isSnapshotRequiredEvent(eventType);
    }

    public static boolean isSnapshotRequiredEvent(String eventType) {
        return AUTO_VERSION_EVENT_TYPES.contains(eventType != null ? eventType.trim().toUpperCase() : "");
    }

    @PostConstruct
    void initializeSchema() {
        ensureSchema();
    }

    public void ensureSchema() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS student_reg_form_versions (
                version_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                student_number VARCHAR(100) NOT NULL,
                archive_key VARCHAR(80) NULL,
                source_event_id BIGINT NULL,
                source_event_type VARCHAR(60) NOT NULL,
                purpose VARCHAR(160) NOT NULL,
                remarks VARCHAR(500) NULL,
                term_label VARCHAR(160) NULL,
                total_units DECIMAL(8,2) NOT NULL DEFAULT 0.00,
                subject_count INT NOT NULL DEFAULT 0,
                captured_by VARCHAR(100) NULL,
                snapshot_json LONGTEXT NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        try {
            db.execute("CREATE INDEX idx_srfv_student ON student_reg_form_versions (student_number, created_at)");
        } catch (Exception ignored) {
        }
        try {
            db.execute("CREATE INDEX idx_srfv_archive ON student_reg_form_versions (archive_key, created_at)");
        } catch (Exception ignored) {
        }
        try {
            db.execute("CREATE INDEX idx_srfv_type ON student_reg_form_versions (source_event_type, created_at)");
        } catch (Exception ignored) {
        }
        try {
            db.execute("CREATE INDEX idx_srfv_source_event ON student_reg_form_versions (source_event_id)");
        } catch (Exception ignored) {
        }
        ensureUniqueSourceEventIndex();
    }

    public Long captureVersion(String studentNumber,
                               String sourceEventType,
                               String purpose,
                               Long sourceEventId,
                               String remarks,
                               String capturedBy) {
        if (studentNumber == null || studentNumber.isBlank() || !shouldCaptureForEvent(sourceEventType)) {
            return null;
        }
        Long existingVersionId = findVersionIdBySourceEvent(sourceEventId);
        if (existingVersionId != null) {
            return existingVersionId;
        }
        Map<String, Object> student = loadStudent(studentNumber.trim());
        if (student.isEmpty()) {
            return null;
        }
        Map<String, Object> finance = financeService.calculateAssessment(studentNumber.trim());
        List<Map<String, Object>> load = loadStudentLoad(student);
        Map<String, Object> snapshot = registrationFormPdfService.buildSnapshot(
            student,
            load,
            finance,
            "",
            cleanNullable(capturedBy, 100) != null ? capturedBy.trim() : "registrar");
        return saveSnapshot(studentNumber.trim(), sourceEventType, purpose, sourceEventId, remarks, capturedBy, snapshot);
    }

    public Long saveCurrentPrintVersion(String studentNumber,
                                        String purpose,
                                        String remarks,
                                        String capturedBy,
                                        Map<String, Object> snapshot) {
        return saveSnapshot(studentNumber, "CURRENT_PRINT", purpose, null, remarks, capturedBy, snapshot);
    }

    public Long saveSnapshot(String studentNumber,
                             String sourceEventType,
                             String purpose,
                             Long sourceEventId,
                             String remarks,
                             String capturedBy,
                             Map<String, Object> snapshot) {
        if (studentNumber == null || studentNumber.isBlank() || snapshot == null || snapshot.isEmpty()) {
            return null;
        }
        Long existingVersionId = findVersionIdBySourceEvent(sourceEventId);
        if (existingVersionId != null) {
            return existingVersionId;
        }
        String archiveKey = studentProfileService.ensureArchiveKey(studentNumber.trim());
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize registration form snapshot.", e);
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            db.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO student_reg_form_versions
                        (student_number, archive_key, source_event_id, source_event_type, purpose, remarks,
                         term_label, total_units, subject_count, captured_by, snapshot_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, studentNumber.trim());
                ps.setString(2, cleanNullable(archiveKey, 80));
                if (sourceEventId != null) {
                    ps.setLong(3, sourceEventId);
                } else {
                    ps.setObject(3, null);
                }
                ps.setString(4, normalizeEventType(sourceEventType));
                ps.setString(5, clean(purpose, "Registration form version saved", 160));
                ps.setString(6, cleanNullable(remarks, 500));
                ps.setString(7, cleanNullable(text(snapshot.get("semesterSchoolYearLine")), 160));
                ps.setDouble(8, number(snapshot.get("totalUnits")));
                ps.setInt(9, listSize(snapshot.get("subjects")));
                ps.setString(10, cleanNullable(capturedBy, 100));
                ps.setString(11, snapshotJson);
                return ps;
            }, keyHolder);
            return generatedId(keyHolder, "version_id");
        } catch (DuplicateKeyException e) {
            Long concurrentVersionId = findVersionIdBySourceEvent(sourceEventId);
            if (concurrentVersionId != null) {
                return concurrentVersionId;
            }
            throw e;
        }
    }

    public List<Map<String, Object>> listRecentVersions(String studentNumber,
                                                        String eventType,
                                                        LocalDate fromDate,
                                                        LocalDate toDate,
                                                        int limit) {
        ensureSchema();
        int safeLimit = Math.max(1, Math.min(limit, 500));
        StringBuilder sql = new StringBuilder("""
            SELECT version_id, student_number, archive_key, source_event_id, source_event_type, purpose,
                   remarks, term_label, total_units, subject_count, captured_by, created_at
            FROM student_reg_form_versions
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        if (studentNumber != null && !studentNumber.isBlank()) {
            sql.append(" AND BINARY student_number = BINARY ? ");
            args.add(studentNumber.trim());
        }
        if (eventType != null && !eventType.isBlank()) {
            sql.append(" AND source_event_type = ? ");
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
        sql.append(" ORDER BY created_at DESC, version_id DESC LIMIT ").append(safeLimit);
        return db.queryForList(sql.toString(), args.toArray()).stream()
            .map(this::normalizeKeys)
            .toList();
    }

    public Map<String, Object> versionSummary(String studentNumber,
                                              String eventType,
                                              LocalDate fromDate,
                                              LocalDate toDate) {
        ensureSchema();
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*) AS total_versions,
                   COUNT(DISTINCT student_number) AS touched_students,
                   MAX(created_at) AS latest_version_at
            FROM student_reg_form_versions
            WHERE 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        if (studentNumber != null && !studentNumber.isBlank()) {
            sql.append(" AND BINARY student_number = BINARY ? ");
            args.add(studentNumber.trim());
        }
        if (eventType != null && !eventType.isBlank()) {
            sql.append(" AND source_event_type = ? ");
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
        return normalizeKeys(db.queryForMap(sql.toString(), args.toArray()));
    }

    public Map<String, Object> findVersion(long versionId) {
        ensureSchema();
        List<Map<String, Object>> rows = db.queryForList("""
            SELECT version_id, student_number, archive_key, source_event_id, source_event_type, purpose,
                   remarks, term_label, total_units, subject_count, captured_by, snapshot_json, created_at
            FROM student_reg_form_versions
            WHERE version_id = ?
            LIMIT 1
            """, versionId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> version = normalizeKeys(rows.get(0));
        version.put("snapshot", parseSnapshot(text(version.remove("snapshot_json"))));
        return version;
    }

    private Map<String, Object> loadStudent(String studentNumber) {
        try {
            return normalizeKeys(db.queryForMap("""
                SELECT student_number AS username,
                       reference_number,
                       real_name,
                       student_type,
                       enrollment_status_type,
                       admission_status,
                       program_code,
                       year_level,
                       semester
                FROM students
                WHERE student_number = ?
                LIMIT 1
                """, studentNumber));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Long findVersionIdBySourceEvent(Long sourceEventId) {
        if (sourceEventId == null) {
            return null;
        }
        List<Long> versionIds = db.queryForList(
            "SELECT version_id FROM student_reg_form_versions WHERE source_event_id = ? ORDER BY version_id LIMIT 1",
            Long.class,
            sourceEventId);
        return versionIds.isEmpty() ? null : versionIds.get(0);
    }

    private void ensureUniqueSourceEventIndex() {
        if (hasUniqueSourceEventIndex()) {
            return;
        }
        Integer duplicates = db.queryForObject("""
            SELECT COUNT(*)
            FROM (
                SELECT source_event_id
                FROM student_reg_form_versions
                WHERE source_event_id IS NOT NULL
                GROUP BY source_event_id
                HAVING COUNT(*) > 1
            ) duplicate_events
            """, Integer.class);
        if (duplicates != null && duplicates > 0) {
            throw new IllegalStateException(
                "Cannot enforce one immutable Registration Form version per event while duplicate source_event_id rows exist.");
        }
        db.execute("CREATE UNIQUE INDEX uq_srfv_source_event ON student_reg_form_versions (source_event_id)");
    }

    private boolean hasUniqueSourceEventIndex() {
        try (var connection = db.getDataSource().getConnection()) {
            for (String tableName : List.of("student_reg_form_versions", "STUDENT_REG_FORM_VERSIONS")) {
                try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                    connection.getCatalog(), null, tableName, true, false)) {
                    while (indexes.next()) {
                        String indexName = indexes.getString("INDEX_NAME");
                        String columnName = indexes.getString("COLUMN_NAME");
                        if ("uq_srfv_source_event".equalsIgnoreCase(indexName)
                            && "source_event_id".equalsIgnoreCase(columnName)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private List<Map<String, Object>> loadStudentLoad(Map<String, Object> student) {
        String studentNumber = text(student.get("username"));
        Set<String> ids = new LinkedHashSet<>();
        if (!studentNumber.isBlank()) {
            ids.add(studentNumber);
        }
        String referenceNumber = text(student.get("reference_number"));
        if (!referenceNumber.isBlank()) {
            ids.add(referenceNumber);
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
            SELECT se.enlistment_id,
                   se.course_id,
                   se.section_id AS schedule_id,
                   c.course_code,
                   c.course_title AS description,
                   c.credit_units AS units,
                   cs.section_code AS section,
                   IFNULL((
                       SELECT GROUP_CONCAT(
                           CONCAT(
                               CASE sch2.day_of_week
                                   WHEN 1 THEN 'MON'
                                   WHEN 2 THEN 'TUE'
                                   WHEN 3 THEN 'WED'
                                   WHEN 4 THEN 'THU'
                                   WHEN 5 THEN 'FRI'
                                   WHEN 6 THEN 'SAT'
                                   ELSE 'SUN'
                               END,
                               ' ',
                               TIME_FORMAT(sch2.start_time,'%h:%i %p'),
                               '-',
                               TIME_FORMAT(sch2.end_time,'%h:%i %p'),
                               ' ',
                               IFNULL(r2.room_code,'TBA')
                           )
                           ORDER BY sch2.day_of_week SEPARATOR ' | '
                       )
                       FROM class_schedules sch2
                       LEFT JOIN rooms r2 ON r2.room_id = sch2.room_id
                       WHERE sch2.section_id = se.section_id
                   ), 'TBA') AS pretty_schedule
            FROM student_enlistments se
            JOIN courses c ON c.course_id = se.course_id
            LEFT JOIN class_sections cs ON cs.section_id = se.section_id
            WHERE (
            """);
        List<Object> args = new ArrayList<>();
        int index = 0;
        for (String id : ids) {
            if (index++ > 0) {
                sql.append(" OR ");
            }
            sql.append("se.student_id = ? ");
            args.add(id);
        }
        sql.append(") ");
        Integer termId = resolveCurrentTermId(studentNumber);
        if (termId != null) {
            sql.append(" AND cs.term_id = ? ");
            args.add(termId);
        }
        sql.append(enlistmentSchemaService.enlistmentStatusFilter(
            EnlistmentSchemaService.Scope.COMMITTED_ONLY, "se"));
        sql.append(" ORDER BY c.course_code");
        return db.queryForList(sql.toString(), args.toArray()).stream()
            .map(this::normalizeKeys)
            .toList();
    }

    private Integer resolveCurrentTermId(String studentNumber) {
        try {
            String termYear = db.queryForObject(
                "SELECT term_year FROM students WHERE student_number = ? LIMIT 1",
                String.class,
                studentNumber);
            if (termYear != null && termYear.length() >= 12 && termYear.startsWith("SL") && !termYear.startsWith("SL_")) {
                char sem = termYear.charAt(11);
                String termCode = sem + "1" + termYear.substring(2, 10);
                return db.queryForObject(
                    "SELECT term_id FROM academic_terms WHERE term_code = ? LIMIT 1",
                    Integer.class,
                    termCode);
            }
            if (termYear != null && termYear.startsWith("SL_") && termYear.length() >= 13) {
                String termCode = termYear.charAt(3) + "1" + termYear.substring(5);
                return db.queryForObject(
                    "SELECT term_id FROM academic_terms WHERE term_code = ? LIMIT 1",
                    Integer.class,
                    termCode);
            }
        } catch (Exception ignored) {
        }
        try {
            return db.queryForObject(
                "SELECT term_id FROM academic_terms WHERE is_active = 1 OR UPPER(COALESCE(status,'')) = 'ACTIVE' " +
                    "ORDER BY term_id DESC LIMIT 1",
                Integer.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> parseSnapshot(String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(snapshotJson, SNAPSHOT_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse saved registration form snapshot.", e);
        }
    }

    private int listSize(Object value) {
        return value instanceof List<?> rows ? rows.size() : 0;
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

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private Map<String, Object> normalizeKeys(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(key != null ? key.toLowerCase() : null, value));
        return normalized;
    }

    private String normalizeEventType(String value) {
        return clean(value != null ? value.toUpperCase() : null, "REG_FORM_VERSION", 60);
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

    private String text(Object preferred) {
        return preferred != null ? preferred.toString().trim() : "";
    }

    private String text(Object preferred, String fallback) {
        String value = text(preferred);
        return value.isBlank() ? fallback : value;
    }
}
