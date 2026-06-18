package com.iuims.registrar.admission;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

@Service
public class ApplicantDocumentReadService {

    private static final List<String> SNAPSHOT_FIELDS = List.of(
        "reference_number", "applicant_status", "term_year", "academic_level",
        "application_track", "program1", "program2", "email_verified", "landline",
        "age", "four_ps", "indigenous", "international_student", "remarks", "extension",
        "created_at", "updated_at"
    );

    private static final LinkedHashMap<String, String> LEGACY_DOCUMENTS = new LinkedHashMap<>();

    static {
        LEGACY_DOCUMENTS.put("form138", "Form 138 / Report Card");
        LEGACY_DOCUMENTS.put("good_moral", "Good Moral Certificate");
        LEGACY_DOCUMENTS.put("psa_birth_cert", "PSA Birth Certificate");
        LEGACY_DOCUMENTS.put("id_picture", "ID Picture");
        LEGACY_DOCUMENTS.put("marriage_cert", "Marriage Certificate");
        LEGACY_DOCUMENTS.put("other_doc", "Other Supporting Document");
    }

    private final JdbcTemplate db;
    private final Path uploadRoot;

    public ApplicantDocumentReadService(
            JdbcTemplate db,
            @Value("${registrar.admission.upload-dir:${APP_UPLOAD_DIR:${user.home}/AdmissionEAC/uploads}}") String uploadDir) {
        this.db = db;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public Map<String, Object> getAdmissionSnapshot(String studentNumber) {
        Map<String, Object> applicant = findApplicant(studentNumber);
        if (applicant.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String field : SNAPSHOT_FIELDS) {
            if (applicant.containsKey(field)) {
                snapshot.put(field, applicant.get(field));
            }
        }
        return snapshot;
    }

    public List<Map<String, Object>> listDocuments(String studentNumber) {
        Map<String, Object> applicant = findApplicant(studentNumber);
        if (applicant.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> normalized = listNormalizedDocuments(applicant);
        return normalized.isEmpty() ? listLegacyDocuments(applicant) : normalized;
    }

    public Path resolveDocumentPath(String studentNumber, String documentKey) {
        if (documentKey == null || documentKey.isBlank()) {
            return null;
        }
        Map<String, Object> applicant = findApplicant(studentNumber);
        if (applicant.isEmpty()) {
            return null;
        }

        String storedPath = null;
        if (documentKey.startsWith("normalized:")) {
            storedPath = resolveNormalizedPath(applicant, documentKey.substring("normalized:".length()));
        } else if (documentKey.startsWith("legacy:")) {
            String slot = documentKey.substring("legacy:".length());
            if (LEGACY_DOCUMENTS.containsKey(slot)) {
                storedPath = stringValue(applicant.get(slot + "_path"));
            }
        }
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }

        // Admission stores generated filenames. Discard any supplied parent path before resolving.
        Path filename = Paths.get(storedPath).getFileName();
        if (filename == null) {
            return null;
        }
        Path resolved = uploadRoot.resolve(filename).normalize();
        return resolved.startsWith(uploadRoot) ? resolved : null;
    }

    private Map<String, Object> findApplicant(String studentNumber) {
        Set<String> applicantColumns = existingColumns("applicants");
        if (applicantColumns.isEmpty() || !applicantColumns.contains("reference_number")) {
            return Map.of();
        }

        StringJoiner select = new StringJoiner(", ");
        select.add("a.id");
        for (String field : SNAPSHOT_FIELDS) {
            if (applicantColumns.contains(field) && !"reference_number".equals(field)) {
                select.add("a." + field);
            }
        }
        select.add("a.reference_number");
        for (String slot : LEGACY_DOCUMENTS.keySet()) {
            if (applicantColumns.contains(slot + "_path")) {
                select.add("a." + slot + "_path");
            }
            if (applicantColumns.contains(slot + "_verified")) {
                select.add("a." + slot + "_verified");
            }
        }

        try {
            return db.queryForMap(
                "SELECT " + select + " FROM students s " +
                    "JOIN applicants a ON a.reference_number = s.reference_number " +
                    "WHERE s.student_number = ? LIMIT 1",
                studentNumber);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> listNormalizedDocuments(Map<String, Object> applicant) {
        if (!tableExists("student_requirement_files") || !tableExists("requirement_upload_definitions")) {
            return List.of();
        }
        Object applicantId = applicant.get("id");
        if (applicantId == null) {
            return List.of();
        }

        try {
            String track = stringValue(applicant.get("application_track"));
            String trackFilter = track.isBlank()
                ? " AND f.id IS NOT NULL "
                : " AND d.application_track = ? ";
            List<Object> args = new ArrayList<>();
            args.add(applicantId);
            if (!track.isBlank()) {
                args.add(track);
            }
            List<Map<String, Object>> rows = db.queryForList(
                "SELECT d.id AS definition_id, d.display_label, d.slot_key, d.required, d.sort_order, " +
                    "f.id AS file_id, f.stored_path, f.verified " +
                    "FROM requirement_upload_definitions d " +
                    "LEFT JOIN student_requirement_files f ON f.definition_id = d.id AND f.applicant_id = ? " +
                    "WHERE d.active = 1 " + trackFilter +
                    "ORDER BY d.sort_order, d.id",
                args.toArray());
            List<Map<String, Object>> documents = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> document = new LinkedHashMap<>();
                document.put("label", row.get("display_label"));
                document.put("slot", row.get("slot_key"));
                document.put("required", truthy(row.get("required")));
                document.put("submitted", row.get("stored_path") != null);
                document.put("verified", truthy(row.get("verified")));
                document.put("source", "Configured admission requirement");
                if (row.get("file_id") != null && row.get("stored_path") != null) {
                    document.put("document_key", "normalized:" + row.get("file_id"));
                }
                documents.add(document);
            }
            return documents;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Map<String, Object>> listLegacyDocuments(Map<String, Object> applicant) {
        List<Map<String, Object>> documents = new ArrayList<>();
        for (Map.Entry<String, String> definition : LEGACY_DOCUMENTS.entrySet()) {
            String slot = definition.getKey();
            if (!applicant.containsKey(slot + "_path") && !applicant.containsKey(slot + "_verified")) {
                continue;
            }
            Object storedPath = applicant.get(slot + "_path");
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("label", definition.getValue());
            document.put("slot", slot);
            document.put("required", !"marriage_cert".equals(slot) && !"other_doc".equals(slot));
            document.put("submitted", storedPath != null && !stringValue(storedPath).isBlank());
            document.put("verified", truthy(applicant.get(slot + "_verified")));
            document.put("source", "Legacy admission record");
            if (storedPath != null && !stringValue(storedPath).isBlank()) {
                document.put("document_key", "legacy:" + slot);
            }
            documents.add(document);
        }
        return documents;
    }

    private String resolveNormalizedPath(Map<String, Object> applicant, String fileId) {
        try {
            Long id = Long.valueOf(fileId);
            return db.queryForObject(
                "SELECT stored_path FROM student_requirement_files WHERE id = ? AND applicant_id = ? LIMIT 1",
                String.class, id, applicant.get("id"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean tableExists(String tableName) {
        return !existingColumns(tableName).isEmpty();
    }

    private Set<String> existingColumns(String tableName) {
        Set<String> columns = new HashSet<>();
        try {
            db.queryForList(
                "SELECT LOWER(COLUMN_NAME) AS column_name FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE LOWER(TABLE_NAME) = LOWER(?)",
                tableName).forEach(row -> columns.add(stringValue(row.get("column_name")).toLowerCase()));
        } catch (Exception ignored) {
        }
        return columns;
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return "true".equalsIgnoreCase(stringValue(value)) || "1".equals(stringValue(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
