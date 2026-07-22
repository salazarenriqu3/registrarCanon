package com.iuims.registrar.service.support;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Student;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.Locale;
import java.util.UUID;

@Service
public class StudentProfileService {

    private final JdbcTemplate db;
    private static final String ARCHIVE_KEY_PREFIX = "ARCH-";

    private final RegistrarAuditTrailService auditTrailService;

    private static final LinkedHashMap<String, String> EDITABLE_FIELDS = new LinkedHashMap<>();

    static {
        EDITABLE_FIELDS.put("first_name", "First name");
        EDITABLE_FIELDS.put("middle_name", "Middle name");
        EDITABLE_FIELDS.put("last_name", "Last name");
        EDITABLE_FIELDS.put("real_name", "Display name");
        EDITABLE_FIELDS.put("email", "Email");
        EDITABLE_FIELDS.put("mobile", "Mobile");
        EDITABLE_FIELDS.put("sex", "Sex");
        EDITABLE_FIELDS.put("dob", "Date of birth");
        EDITABLE_FIELDS.put("place_of_birth", "Place of birth");
        EDITABLE_FIELDS.put("civil_status", "Civil status");
        EDITABLE_FIELDS.put("religion", "Religion");
        EDITABLE_FIELDS.put("nationality", "Nationality");
        EDITABLE_FIELDS.put("citizenship", "Citizenship");
        EDITABLE_FIELDS.put("street", "Street address");
        EDITABLE_FIELDS.put("city", "City");
        EDITABLE_FIELDS.put("province", "Province");
        EDITABLE_FIELDS.put("zip", "ZIP");
        EDITABLE_FIELDS.put("emergency_contact_name", "Emergency contact");
        EDITABLE_FIELDS.put("emergency_contact_mobile", "Emergency mobile");
        EDITABLE_FIELDS.put("emergency_contact_relationship", "Emergency relationship");
        EDITABLE_FIELDS.put("father_name", "Father name");
        EDITABLE_FIELDS.put("father_occupation", "Father occupation");
        EDITABLE_FIELDS.put("father_contact", "Father contact");
        EDITABLE_FIELDS.put("father_address", "Father address");
        EDITABLE_FIELDS.put("mother_name", "Mother name");
        EDITABLE_FIELDS.put("mother_occupation", "Mother occupation");
        EDITABLE_FIELDS.put("mother_contact", "Mother contact");
        EDITABLE_FIELDS.put("mother_address", "Mother address");
        EDITABLE_FIELDS.put("guardian_name", "Guardian name");
        EDITABLE_FIELDS.put("guardian_contact", "Guardian contact");
        EDITABLE_FIELDS.put("guardian_relationship", "Guardian relationship");
        EDITABLE_FIELDS.put("elementary_school", "Elementary school");
        EDITABLE_FIELDS.put("elementary_address", "Elementary address");
        EDITABLE_FIELDS.put("elementary_year", "Elementary year");
        EDITABLE_FIELDS.put("jhs_school", "Junior high school");
        EDITABLE_FIELDS.put("jhs_address", "Junior high address");
        EDITABLE_FIELDS.put("jhs_year", "Junior high year");
        EDITABLE_FIELDS.put("shs_school", "Senior high school");
        EDITABLE_FIELDS.put("shs_address", "Senior high address");
        EDITABLE_FIELDS.put("shs_track", "Senior high track");
        EDITABLE_FIELDS.put("shs_year", "Senior high year");
        EDITABLE_FIELDS.put("last_school", "Last school");
        EDITABLE_FIELDS.put("last_school_year", "Last school year");
        EDITABLE_FIELDS.put("course_taken", "Course taken");
    }

    public StudentProfileService(JdbcTemplate db) {
        this(db, null);
    }

    @Autowired
    public StudentProfileService(JdbcTemplate db, RegistrarAuditTrailService auditTrailService) {
        this.db = db;
        this.auditTrailService = auditTrailService;
    }

    @PostConstruct
    public void init() {
        ensureSchema();
        backfillArchivedIdentitySnapshots();
    }

    public void ensureSchema() {
        db.execute(
            "CREATE TABLE IF NOT EXISTS students (" +
                "student_number VARCHAR(100) NOT NULL PRIMARY KEY, " +
                "user_id INT NULL, reference_number VARCHAR(100) NULL, archive_key VARCHAR(80) NULL, " +
                "first_name VARCHAR(100) NULL, middle_name VARCHAR(100) NULL, last_name VARCHAR(100) NULL, " +
                "real_name VARCHAR(200) NULL, email VARCHAR(150) NULL, mobile VARCHAR(50) NULL, " +
                "program_code VARCHAR(100) NULL, year_level INT DEFAULT 1, semester INT DEFAULT 1, " +
                "term_year VARCHAR(50) NULL, student_type VARCHAR(50) NULL, enrollment_status_type VARCHAR(50) NULL, " +
                "admission_status VARCHAR(50) NULL, scholarship_type VARCHAR(50) NULL, " +
                "scholarship_approved TINYINT(1) DEFAULT 0, scholarship_amount DECIMAL(10,2) DEFAULT 0.00, " +
                "discount_percentage DECIMAL(5,2) DEFAULT 0.00, section_group VARCHAR(10) NULL, " +
                "status VARCHAR(50) DEFAULT 'ACTIVE', is_active TINYINT(1) DEFAULT 1, " +
                "enrollment_blocked TINYINT(1) DEFAULT 0, password VARCHAR(255) NULL, role VARCHAR(30) DEFAULT 'STUDENT')"
        );

        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS sex VARCHAR(10) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS archive_key VARCHAR(80) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS dob VARCHAR(20) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS place_of_birth TEXT NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS civil_status VARCHAR(30) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS religion VARCHAR(60) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS nationality VARCHAR(60) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS citizenship VARCHAR(60) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS street TEXT NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS city VARCHAR(100) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS province VARCHAR(100) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS zip VARCHAR(20) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS emergency_contact_name VARCHAR(150) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS emergency_contact_mobile VARCHAR(30) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS emergency_contact_relationship VARCHAR(60) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS father_name VARCHAR(150) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS father_occupation VARCHAR(100) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS father_contact VARCHAR(30) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS father_address TEXT NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS mother_name VARCHAR(150) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS mother_occupation VARCHAR(100) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS mother_contact VARCHAR(30) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS mother_address TEXT NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS guardian_name VARCHAR(150) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS guardian_contact VARCHAR(30) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS guardian_relationship VARCHAR(60) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS elementary_school TEXT NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS elementary_address TEXT NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS elementary_year VARCHAR(20) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS jhs_school TEXT NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS jhs_address TEXT NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS jhs_year VARCHAR(20) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS shs_school TEXT NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS shs_address TEXT NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS shs_track VARCHAR(60) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS shs_year VARCHAR(20) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS last_school TEXT NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS last_school_year VARCHAR(20) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS course_taken VARCHAR(100) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS scholarship_type VARCHAR(50) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS scholarship_approved TINYINT(1) DEFAULT 0");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS scholarship_amount DECIMAL(10,2) DEFAULT 0.00");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS discount_percentage DECIMAL(5,2) DEFAULT 0.00");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS section_group VARCHAR(10) NULL");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'ACTIVE'");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS is_active TINYINT(1) DEFAULT 1");
        tryExecute("ALTER TABLE students ADD COLUMN IF NOT EXISTS enrollment_blocked TINYINT(1) DEFAULT 0");
        tryExecute("CREATE UNIQUE INDEX IF NOT EXISTS uq_students_archive_key ON students (archive_key)");
        ensureArchiveIdentitySchema();
        ensureSysUserProfileColumns();
    }

    public Map<String, Object> getEditableProfile(String studentNumber) {
        ensureSchema();
        String resolvedStudentNumber = resolveCurrentStudentNumber(studentNumber);
        if (isBlank(resolvedStudentNumber)) {
            return new LinkedHashMap<>();
        }
        Set<String> applicantColumns = existingColumns("applicants");
        String select = buildProfileSelect(applicantColumns);
        String applicantJoin = applicantColumns.isEmpty()
            ? " "
            : " LEFT JOIN applicants a ON a.reference_number = s.reference_number ";
        try {
            return db.queryForMap(
                "SELECT s.student_number, s.reference_number, s.archive_key, " + select +
                    " FROM students s" + applicantJoin +
                    "WHERE s.student_number = ? LIMIT 1",
                resolvedStudentNumber);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public String ensureArchiveKey(String studentNumber) {
        ensureSchema();
        String sn = clean(studentNumber);
        if (isBlank(sn)) {
            return null;
        }
        String existing = resolveArchiveKeyForIdentity(sn);
        if (!isBlank(existing)) {
            return existing;
        }
        String resolvedStudentNumber = resolveCurrentStudentNumber(sn);
        if (isBlank(resolvedStudentNumber)) {
            return null;
        }
        String generated = generateArchiveKey();
        try {
            int updated = db.update(
                "UPDATE students SET archive_key = ? WHERE student_number = ? AND (archive_key IS NULL OR archive_key = '')",
                generated, resolvedStudentNumber);
            if (updated > 0) {
                return generated;
            }
        } catch (Exception ignored) {
        }
        String fallback = resolveArchiveKeyForIdentity(resolvedStudentNumber);
        return !isBlank(fallback) ? fallback : generated;
    }

    public String getArchiveKey(String studentNumber) {
        ensureSchema();
        return resolveArchiveKeyForIdentity(studentNumber);
    }

    public boolean isWithdrawnIdentity(String identity) {
        ensureSchema();
        String key = clean(identity);
        if (isBlank(key)) {
            return false;
        }
        if (isWithdrawnLiveStudent(key)) {
            return true;
        }
        if (isWithdrawnArchivedIdentity(key)) {
            return true;
        }
        return isWithdrawnArchiveFile(key);
    }

    public String resolveCurrentStudentNumber(String identity) {
        ensureSchema();
        String key = clean(identity);
        if (isBlank(key)) {
            return null;
        }
        String current = queryCurrentStudentNumber(key);
        if (!isBlank(current)) {
            return current;
        }
        String archiveKey = resolveArchiveKeyForIdentity(key);
        if (!isBlank(archiveKey)) {
            return archiveKey;
        }
        return key;
    }

    public String resolveArchiveKeyForIdentity(String identity) {
        ensureSchema();
        String key = clean(identity);
        if (isBlank(key)) {
            return null;
        }
        String currentStudentNumber = queryCurrentStudentNumber(key);
        if (isBlank(currentStudentNumber)) {
            String registryArchiveKey = queryReleaseRegistryArchiveKey(key);
            if (!isBlank(registryArchiveKey)) {
                return registryArchiveKey;
            }
            String archivedArchiveKey = queryArchivedIdentityArchiveKey(key);
            if (!isBlank(archivedArchiveKey)) {
                return archivedArchiveKey;
            }
            return null;
        }
        try {
            return db.queryForObject(
                "SELECT archive_key FROM students WHERE student_number = ? LIMIT 1",
                String.class, currentStudentNumber);
        } catch (Exception ignored) {
            String registryArchiveKey = queryReleaseRegistryArchiveKey(key);
            if (!isBlank(registryArchiveKey)) {
                return registryArchiveKey;
            }
            String archivedArchiveKey = queryArchivedIdentityArchiveKey(key);
            return !isBlank(archivedArchiveKey) ? archivedArchiveKey : null;
        }
    }

    @Transactional
    public String snapshotArchivedIdentity(String studentNumber, String archivedBy, String archiveStatus) {
        ensureSchema();
        String sn = clean(studentNumber);
        if (isBlank(sn)) {
            return null;
        }
        Map<String, Object> student = loadStudentArchiveSource(sn);
        if (student.isEmpty()) {
            return null;
        }
        String archiveKey = ensureArchiveKey(sn);
        if (isBlank(archiveKey)) {
            return null;
        }

        db.update("""
            INSERT INTO student_identity_archive (
                archive_key, archived_student_number, reference_number,
                first_name, middle_name, last_name, real_name, email, mobile,
                program_code, year_level, semester, term_year, student_type,
                admission_status, status, archive_status, archived_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                archived_student_number = VALUES(archived_student_number),
                reference_number = VALUES(reference_number),
                first_name = VALUES(first_name),
                middle_name = VALUES(middle_name),
                last_name = VALUES(last_name),
                real_name = VALUES(real_name),
                email = VALUES(email),
                mobile = VALUES(mobile),
                program_code = VALUES(program_code),
                year_level = VALUES(year_level),
                semester = VALUES(semester),
                term_year = VALUES(term_year),
                student_type = VALUES(student_type),
                admission_status = VALUES(admission_status),
                status = VALUES(status),
                archive_status = VALUES(archive_status),
                archived_by = COALESCE(VALUES(archived_by), archived_by),
                updated_at = CURRENT_TIMESTAMP
            """,
            archiveKey,
            sn,
            clean(text(student.get("reference_number"))),
            clean(text(student.get("first_name"))),
            clean(text(student.get("middle_name"))),
            clean(text(student.get("last_name"))),
            clean(resolveArchivedRealName(student)),
            clean(text(student.get("email"))),
            clean(text(student.get("mobile"))),
            clean(text(student.get("program_code"))),
            integerValue(student.get("year_level")),
            integerValue(student.get("semester")),
            clean(text(student.get("term_year"))),
            clean(text(student.get("student_type"))),
            clean(text(student.get("admission_status"))),
            clean(text(student.get("status"))),
            clean(archiveStatus) != null ? clean(archiveStatus) : "WITHDRAWN_RECORD",
            clean(archivedBy));

        return archiveKey;
    }

    public List<Map<String, Object>> searchArchivedIdentityMatches(String query, int limit) {
        ensureSchema();
        String q = clean(query);
        if (isBlank(q)) {
            return List.of();
        }
        String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
        int safeLimit = Math.max(1, Math.min(limit, 20));
        try {
            return db.queryForList(
                "SELECT archive_key, archived_student_number, reference_number, real_name, " +
                    "program_code, archive_status, archived_at " +
                    "FROM student_identity_archive " +
                    "WHERE archive_key = ? " +
                    "   OR LOWER(COALESCE(real_name, '')) LIKE ? " +
                    "   OR LOWER(COALESCE(first_name, '')) LIKE ? " +
                    "   OR LOWER(COALESCE(last_name, '')) LIKE ? " +
                    "   OR LOWER(COALESCE(reference_number, '')) LIKE ? " +
                    "ORDER BY archived_at DESC LIMIT " + safeLimit,
                q, like, like, like, like);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @Transactional
    public List<String> updateProfile(String studentNumber, Map<String, String> form) {
        return updateProfile(studentNumber, form, null);
    }

    @Transactional
    public List<String> updateProfile(String studentNumber, Map<String, String> form, String actor) {
        ensureSchema();
        String resolvedStudentNumber = resolveCurrentStudentNumber(studentNumber);
        if (isBlank(resolvedStudentNumber)) {
            return List.of();
        }
        Map<String, Object> before = getEditableProfile(resolvedStudentNumber);
        Map<String, String> values = readEditableValues(form);
        if (isBlank(values.get("real_name"))) {
            values.put("real_name", buildRealName(values));
        }

        updateStudentColumns(resolvedStudentNumber, values);
        syncSysUser(resolvedStudentNumber, values);

        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> field : EDITABLE_FIELDS.entrySet()) {
            String oldValue = normalizeValue(before.get(field.getKey()));
            String newValue = normalizeValue(values.get(field.getKey()));
            if (!oldValue.equals(newValue)) {
                changed.add(field.getValue());
            }
        }
        if (!changed.isEmpty()) {
            auditProfileUpdate(resolvedStudentNumber, actor, changed);
        }
        return changed;
    }

    @Transactional
    public void copyApplicantProfile(String studentNumber, String referenceNumber) {
        ensureSchema();
        try {
            Map<String, Object> applicant = db.queryForMap(
                "SELECT * FROM applicants WHERE reference_number = ? LIMIT 1",
                referenceNumber);
            Map<String, String> values = new LinkedHashMap<>();
            for (String field : EDITABLE_FIELDS.keySet()) {
                values.put(field, normalizeValue(applicant.get(field)));
            }
            if (isBlank(values.get("real_name"))) {
                values.put("real_name", buildRealName(values));
            }
            updateStudentColumns(studentNumber, values);
            syncSysUser(studentNumber, values);
        } catch (Exception ignored) {
        }
    }

    private String buildProfileSelect(Set<String> applicantColumns) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String field : EDITABLE_FIELDS.keySet()) {
            if ("real_name".equals(field)) {
                if (applicantColumns.contains("first_name") && applicantColumns.contains("middle_name") && applicantColumns.contains("last_name")) {
                    joiner.add("COALESCE(NULLIF(s.real_name, ''), NULLIF(TRIM(CONCAT(COALESCE(a.first_name, ''), ' ', COALESCE(a.middle_name, ''), ' ', COALESCE(a.last_name, ''))), '')) AS real_name");
                } else {
                    joiner.add("s.real_name AS real_name");
                }
            } else if (applicantColumns.contains(field)) {
                joiner.add("COALESCE(NULLIF(s." + field + ", ''), a." + field + ") AS " + field);
            } else {
                joiner.add("s." + field + " AS " + field);
            }
        }
        return joiner.toString();
    }

    private Map<String, String> readEditableValues(Map<String, String> form) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String field : EDITABLE_FIELDS.keySet()) {
            values.put(field, clean(form.get(field)));
        }
        return values;
    }

    private void updateStudentColumns(String studentNumber, Map<String, String> values) {
        StringJoiner setters = new StringJoiner(", ");
        List<Object> args = new ArrayList<>();
        for (String field : EDITABLE_FIELDS.keySet()) {
            setters.add(field + " = ?");
            args.add(values.get(field));
        }
        args.add(studentNumber);
        db.update(
            "UPDATE students SET " + setters + " WHERE student_number = ?",
            args.toArray());
    }

    private void syncSysUser(String studentNumber, Map<String, String> values) {
        Set<String> sysUserColumns = existingColumns("sys_users");
        LinkedHashMap<String, String> syncValues = new LinkedHashMap<>();
        addIfColumnExists(syncValues, sysUserColumns, "real_name", values.get("real_name"));
        addIfColumnExists(syncValues, sysUserColumns, "email", values.get("email"));
        addIfColumnExists(syncValues, sysUserColumns, "mobile", values.get("mobile"));
        addIfColumnExists(syncValues, sysUserColumns, "first_name", values.get("first_name"));
        addIfColumnExists(syncValues, sysUserColumns, "middle_name", values.get("middle_name"));
        addIfColumnExists(syncValues, sysUserColumns, "last_name", values.get("last_name"));
        if (syncValues.isEmpty()) {
            return;
        }

        StringJoiner setters = new StringJoiner(", ");
        List<Object> args = new ArrayList<>();
        syncValues.forEach((field, value) -> {
            setters.add(field + " = ?");
            args.add(value);
        });
        args.add(studentNumber);
        try {
            db.update(
                "UPDATE sys_users SET " + setters + " WHERE username = ?",
                args.toArray());
        } catch (Exception ignored) {
        }
    }

    private void addIfColumnExists(Map<String, String> target, Set<String> columns, String field, String value) {
        if (columns.contains(field)) {
            target.put(field, value);
        }
    }

    private void ensureSysUserProfileColumns() {
        tryExecute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS first_name VARCHAR(100) NULL");
        tryExecute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS middle_name VARCHAR(100) NULL");
        tryExecute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS last_name VARCHAR(100) NULL");
        tryExecute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS email VARCHAR(150) NULL");
        tryExecute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS mobile VARCHAR(50) NULL");
        tryExecute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS term_year VARCHAR(50) NULL");
        tryExecute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS student_type VARCHAR(50) NULL");
        tryExecute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS enrollment_status_type VARCHAR(50) NULL");
    }

    private void ensureArchiveIdentitySchema() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS student_identity_archive (
                archive_key VARCHAR(80) NOT NULL PRIMARY KEY,
                archived_student_number VARCHAR(100) NOT NULL,
                reference_number VARCHAR(100) NULL,
                first_name VARCHAR(100) NULL,
                middle_name VARCHAR(100) NULL,
                last_name VARCHAR(100) NULL,
                real_name VARCHAR(200) NULL,
                email VARCHAR(150) NULL,
                mobile VARCHAR(50) NULL,
                program_code VARCHAR(100) NULL,
                year_level INT NULL,
                semester INT NULL,
                term_year VARCHAR(50) NULL,
                student_type VARCHAR(50) NULL,
                admission_status VARCHAR(50) NULL,
                status VARCHAR(50) NULL,
                archive_status VARCHAR(50) NOT NULL DEFAULT 'WITHDRAWN_RECORD',
                archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                archived_by VARCHAR(100) NULL,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS reference_number VARCHAR(100) NULL");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS first_name VARCHAR(100) NULL");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS middle_name VARCHAR(100) NULL");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS last_name VARCHAR(100) NULL");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS real_name VARCHAR(200) NULL");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS email VARCHAR(150) NULL");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS mobile VARCHAR(50) NULL");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS program_code VARCHAR(100) NULL");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS year_level INT NULL");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS semester INT NULL");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS term_year VARCHAR(50) NULL");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS student_type VARCHAR(50) NULL");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS admission_status VARCHAR(50) NULL");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS status VARCHAR(50) NULL");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS archive_status VARCHAR(50) NOT NULL DEFAULT 'WITHDRAWN_RECORD'");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS archived_by VARCHAR(100) NULL");
        tryExecute("ALTER TABLE student_identity_archive ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
        tryExecute("CREATE INDEX IF NOT EXISTS idx_sia_student_number ON student_identity_archive (archived_student_number)");
        tryExecute("CREATE INDEX IF NOT EXISTS idx_sia_reference ON student_identity_archive (reference_number)");
        tryExecute("CREATE INDEX IF NOT EXISTS idx_sia_real_name ON student_identity_archive (real_name)");
    }

    private Set<String> existingColumns(String tableName) {
        Set<String> columns = new HashSet<>();
        try {
            db.queryForList(
                "SELECT LOWER(COLUMN_NAME) AS column_name FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_NAME) = LOWER(?)",
                tableName)
                .forEach(row -> columns.add(String.valueOf(row.get("column_name")).toLowerCase()));
        } catch (Exception ignored) {
        }
        return columns;
    }

    private String buildRealName(Map<String, String> values) {
        StringJoiner joiner = new StringJoiner(" ");
        addIfPresent(joiner, values.get("first_name"));
        addIfPresent(joiner, values.get("middle_name"));
        addIfPresent(joiner, values.get("last_name"));
        return joiner.length() > 0 ? joiner.toString() : null;
    }

    private String generateArchiveKey() {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        return ARCHIVE_KEY_PREFIX + token;
    }

    private void backfillArchivedIdentitySnapshots() {
        Set<String> studentColumns = existingColumns("students");
        if (!studentColumns.contains("student_number")) {
            return;
        }
        List<String> filters = new ArrayList<>();
        if (studentColumns.contains("admission_status")) {
            filters.add("UPPER(COALESCE(admission_status, '')) = 'WITHDRAWN'");
        }
        if (studentColumns.contains("status")) {
            filters.add("UPPER(COALESCE(status, '')) = 'WITHDRAWN'");
        }
        if (filters.isEmpty()) {
            return;
        }
        try {
            List<String> studentNumbers = db.queryForList(
                "SELECT student_number FROM students WHERE " + String.join(" OR ", filters),
                String.class);
            for (String studentNumber : studentNumbers) {
                snapshotArchivedIdentity(studentNumber, "system-backfill", "WITHDRAWN_RECORD");
            }
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> loadStudentArchiveSource(String studentNumber) {
        String resolvedStudentNumber = resolveCurrentStudentNumber(studentNumber);
        if (isBlank(resolvedStudentNumber)) {
            return Map.of();
        }
        Set<String> studentColumns = existingColumns("students");
        if (!studentColumns.contains("student_number")) {
            return Map.of();
        }
        List<String> select = new ArrayList<>();
        select.add("student_number");
        select.add(selectOrNull(studentColumns, "reference_number"));
        select.add(selectOrNull(studentColumns, "archive_key"));
        select.add(selectOrNull(studentColumns, "first_name"));
        select.add(selectOrNull(studentColumns, "middle_name"));
        select.add(selectOrNull(studentColumns, "last_name"));
        select.add(selectOrNull(studentColumns, "real_name"));
        select.add(selectOrNull(studentColumns, "email"));
        select.add(selectOrNull(studentColumns, "mobile"));
        select.add(selectOrNull(studentColumns, "program_code"));
        select.add(selectOrNull(studentColumns, "year_level"));
        select.add(selectOrNull(studentColumns, "semester"));
        select.add(selectOrNull(studentColumns, "term_year"));
        select.add(selectOrNull(studentColumns, "student_type"));
        select.add(selectOrNull(studentColumns, "admission_status"));
        select.add(selectOrNull(studentColumns, "status"));
        try {
            return db.queryForMap(
                "SELECT " + String.join(", ", select) + " FROM students WHERE student_number = ? LIMIT 1",
                resolvedStudentNumber);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String queryCurrentStudentNumber(String identity) {
        try {
            return db.queryForObject(
                "SELECT student_number FROM students WHERE student_number = ? OR archive_key = ? LIMIT 1",
                String.class, identity, identity);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String queryReleaseRegistryArchiveKey(String identity) {
        try {
            return db.queryForObject(
                "SELECT NULLIF(archive_key, '') " +
                    "FROM student_number_release_registry " +
                    "WHERE released_student_number = ? OR archive_key = ? " +
                    "ORDER BY released_at DESC LIMIT 1",
                String.class, identity, identity);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String queryArchivedIdentityArchiveKey(String identity) {
        try {
            return db.queryForObject(
                "SELECT archive_key FROM student_identity_archive " +
                    "WHERE archived_student_number = ? OR archive_key = ? " +
                    "ORDER BY archived_at DESC LIMIT 1",
                String.class, identity, identity);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isWithdrawnLiveStudent(String identity) {
        try {
            Map<String, Object> row = db.queryForMap(
                "SELECT admission_status, status, is_active FROM students " +
                    "WHERE student_number = ? OR archive_key = ? LIMIT 1",
                identity, identity);
            String admissionStatus = normalizeStatus(row.get("admission_status"));
            String status = normalizeStatus(row.get("status"));
            boolean inactive = row.get("is_active") instanceof Number n && n.intValue() == 0;
            return "WITHDRAWN".equalsIgnoreCase(admissionStatus)
                || "WITHDRAWN".equalsIgnoreCase(status)
                || inactive;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isWithdrawnArchivedIdentity(String identity) {
        try {
            Map<String, Object> row = db.queryForMap(
                "SELECT admission_status, status, archive_status FROM student_identity_archive " +
                    "WHERE archived_student_number = ? OR archive_key = ? LIMIT 1",
                identity, identity);
            String admissionStatus = normalizeStatus(row.get("admission_status"));
            String status = normalizeStatus(row.get("status"));
            String archiveStatus = normalizeStatus(row.get("archive_status"));
            return "WITHDRAWN".equalsIgnoreCase(admissionStatus)
                || "WITHDRAWN".equalsIgnoreCase(status)
                || archiveStatus.startsWith("WITHDRAWN");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isWithdrawnArchiveFile(String identity) {
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM student_archive_files " +
                    "WHERE (student_number = ? OR archive_key = ?) " +
                    "AND UPPER(COALESCE(archive_status, '')) LIKE 'WITHDRAWN%'",
                Integer.class, identity, identity);
            return count != null && count > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String selectOrNull(Set<String> columns, String column) {
        return columns.contains(column)
            ? column
            : "NULL AS " + column;
    }

    private String resolveArchivedRealName(Map<String, Object> student) {
        String realName = clean(text(student.get("real_name")));
        if (!isBlank(realName)) {
            return realName;
        }
        StringJoiner joiner = new StringJoiner(" ");
        addIfPresent(joiner, text(student.get("first_name")));
        addIfPresent(joiner, text(student.get("middle_name")));
        addIfPresent(joiner, text(student.get("last_name")));
        return joiner.length() > 0 ? joiner.toString() : null;
    }

    private Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private String normalizeStatus(Object value) {
        String text = text(value);
        return text == null ? "" : text.trim();
    }

    private void addIfPresent(StringJoiner joiner, String value) {
        if (!isBlank(value)) {
            joiner.add(value.trim());
        }
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void tryExecute(String sql) {
        try {
            db.execute(sql);
        } catch (Exception ignored) {
        }
    }

    private void auditProfileUpdate(String studentNumber, String actor, List<String> changed) {
        if (auditTrailService == null) {
            return;
        }
        auditTrailService.recordStudentAction(
            actor,
            "STUDENT_PROFILE",
            "STUDENT_PROFILE_UPDATED",
            studentNumber,
            "Registrar profile updated",
            "Changed fields: " + String.join(", ", changed),
            "students",
            studentNumber);
    }
}
