package com.iuims.registrar.curriculum;

import com.iuims.registrar.core.GradeOutcomeSql;
import com.iuims.registrar.forms.RegFormEventService;
import com.iuims.registrar.forms.StudentDocumentTrailService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CreditGradeService {

    public record BulkCreditLineResult(String courseCode, boolean ok, String detail) {}

    public record BulkCreditResult(int credited, int skipped, List<BulkCreditLineResult> lines) {}

    public record CreditRequestActionResult(boolean ok, Long requestId, String message) {}

    private final JdbcTemplate db;
    private final StudentCurriculumService studentCurriculumService;
    private final RegFormEventService regFormEventService;
    private final StudentDocumentTrailService documentTrailService;
    private final TransferCreditGradePort transferCreditGradePort;

    public CreditGradeService(JdbcTemplate db,
                              StudentCurriculumService studentCurriculumService,
                              RegFormEventService regFormEventService,
                              StudentDocumentTrailService documentTrailService,
                              TransferCreditGradePort transferCreditGradePort) {
        this.db = db;
        this.studentCurriculumService = studentCurriculumService;
        this.regFormEventService = regFormEventService;
        this.documentTrailService = documentTrailService;
        this.transferCreditGradePort = transferCreditGradePort;
    }

    public void ensureSchema() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS transfer_credit_requests (
                request_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                student_number VARCHAR(100) NOT NULL,
                curriculum_id INT NULL,
                course_id INT NOT NULL,
                course_code VARCHAR(100) NOT NULL,
                numeric_grade DECIMAL(5,2) NULL,
                source_school VARCHAR(180) NULL,
                note VARCHAR(500) NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                requested_by VARCHAR(100) NULL,
                requested_by_role VARCHAR(50) NULL,
                source_system VARCHAR(50) NULL,
                source_table VARCHAR(100) NULL,
                source_row_id VARCHAR(100) NULL,
                requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                approved_by VARCHAR(100) NULL,
                approved_at TIMESTAMP NULL,
                rejected_by VARCHAR(100) NULL,
                rejected_at TIMESTAMP NULL,
                rejection_reason VARCHAR(500) NULL
            )
            """);
        try {
            db.execute("CREATE INDEX idx_tcr_student_status ON transfer_credit_requests (student_number, status, requested_at)");
        } catch (Exception ignored) {
        }
        try {
            db.execute("CREATE INDEX idx_tcr_course_student ON transfer_credit_requests (student_number, course_id, status)");
        } catch (Exception ignored) {
        }
        try {
            db.execute("ALTER TABLE transfer_credit_requests ADD COLUMN requested_by_role VARCHAR(50) NULL AFTER requested_by");
        } catch (Exception ignored) {
        }
        try {
            db.execute("ALTER TABLE transfer_credit_requests ADD COLUMN source_system VARCHAR(50) NULL AFTER requested_by_role");
        } catch (Exception ignored) {
        }
        try {
            db.execute("ALTER TABLE transfer_credit_requests ADD COLUMN source_table VARCHAR(100) NULL AFTER source_system");
        } catch (Exception ignored) {
        }
        try {
            db.execute("ALTER TABLE transfer_credit_requests ADD COLUMN source_row_id VARCHAR(100) NULL AFTER source_table");
        } catch (Exception ignored) {
        }
        try {
            db.execute("CREATE UNIQUE INDEX uk_tcr_source_row ON transfer_credit_requests (source_system, source_table, source_row_id)");
        } catch (Exception ignored) {
        }
        normalizeAccreditationCollations();
    }

    @Transactional
    public String creditCourse(String studentNumber, int courseId, Double numericGrade,
                               String sourceSchool, String note) {
        if (studentNumber == null || studentNumber.isBlank()) {
            return "ERROR: Student number is required.";
        }
        if (courseId <= 0) {
            return "ERROR: Invalid course.";
        }
        String sn = studentNumber.trim();
        String courseCode = lookupCourseCode(courseId);
        if (courseCode == null) {
            return "ERROR: Course not found.";
        }
        return creditCourseInternal(sn, courseId, courseCode, numericGrade, sourceSchool, note, "registrar");
    }

    @Transactional
    public String creditCourseByCode(String studentNumber, String courseCode, Double numericGrade,
                                     String sourceSchool, String note) {
        if (studentNumber == null || studentNumber.isBlank()) {
            return "ERROR: Student number is required.";
        }
        if (courseCode == null || courseCode.isBlank()) {
            return "ERROR: Course code is required.";
        }
        String sn = studentNumber.trim();
        Integer courseId = lookupCourseId(courseCode.trim());
        if (courseId == null) {
            return "ERROR: Course code not found: " + courseCode.trim();
        }
        return creditCourseInternal(sn, courseId, courseCode.trim(), numericGrade, sourceSchool, note, "registrar");
    }

    @Transactional
    public BulkCreditResult bulkCreditFromCsv(String studentNumber, String csvText, String defaultSourceSchool) {
        List<BulkCreditLineResult> lines = new ArrayList<>();
        int credited = 0;
        int skipped = 0;
        if (studentNumber == null || studentNumber.isBlank()) {
            lines.add(new BulkCreditLineResult("", false, "Student number is required."));
            return new BulkCreditResult(0, 1, lines);
        }
        if (csvText == null || csvText.isBlank()) {
            lines.add(new BulkCreditLineResult("", false, "CSV is empty."));
            return new BulkCreditResult(0, 1, lines);
        }
        try (BufferedReader reader = new BufferedReader(new StringReader(csvText))) {
            String line;
            int row = 0;
            while ((line = reader.readLine()) != null) {
                row++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (row == 1 && trimmed.toLowerCase().startsWith("course_code")) continue;

                String[] parts = trimmed.split(",", -1);
                String code = parts.length > 0 ? parts[0].trim() : "";
                if (code.isEmpty()) {
                    skipped++;
                    lines.add(new BulkCreditLineResult("", false, "Row " + row + ": missing course_code"));
                    continue;
                }
                Double numericGrade = null;
                if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                    try {
                        numericGrade = Double.parseDouble(parts[1].trim());
                    } catch (NumberFormatException e) {
                        skipped++;
                        lines.add(new BulkCreditLineResult(code, false, "Row " + row + ": invalid numeric_grade"));
                        continue;
                    }
                }
                String sourceSchool = parts.length > 2 && !parts[2].trim().isEmpty()
                    ? parts[2].trim()
                    : defaultSourceSchool;
                String note = parts.length > 3 ? parts[3].trim() : null;

                String result = creditCourseByCode(studentNumber, code, numericGrade, sourceSchool, note);
                if (result.startsWith("SUCCESS:")) {
                    credited++;
                    lines.add(new BulkCreditLineResult(code, true, result));
                } else {
                    skipped++;
                    lines.add(new BulkCreditLineResult(code, false, result));
                }
            }
        } catch (Exception e) {
            lines.add(new BulkCreditLineResult("", false, "CSV parse error: " + e.getMessage()));
            skipped++;
        }
        return new BulkCreditResult(credited, skipped, lines);
    }

    @Transactional
    public CreditRequestActionResult submitCreditRequest(String studentNumber,
                                                         int courseId,
                                                         Double numericGrade,
                                                         String sourceSchool,
                                                         String note,
                                                         String requestedBy) {
        return submitCreditRequest(
            studentNumber,
            courseId,
            numericGrade,
            sourceSchool,
            note,
            requestedBy,
            resolveUserRole(requestedBy));
    }

    @Transactional
    public CreditRequestActionResult submitCreditRequest(String studentNumber,
                                                         int courseId,
                                                         Double numericGrade,
                                                         String sourceSchool,
                                                         String note,
                                                         String requestedBy,
                                                         String requestedByRole) {
        ensureSchema();
        if (studentNumber == null || studentNumber.isBlank()) {
            return new CreditRequestActionResult(false, null, "ERROR: Student number is required.");
        }
        if (courseId <= 0) {
            return new CreditRequestActionResult(false, null, "ERROR: Invalid course.");
        }
        if (!isDeanRole(requestedByRole)) {
            return new CreditRequestActionResult(
                false,
                null,
                "ERROR: TOR accreditation requests must be submitted by the Enrollment Dean. Registrar only approves them.");
        }
        String sn = studentNumber.trim();
        String courseCode = lookupCourseCode(courseId);
        if (courseCode == null) {
            return new CreditRequestActionResult(false, null, "ERROR: Course not found.");
        }
        String validation = validateCreditable(sn, courseId, courseCode, true);
        if (validation != null) {
            return new CreditRequestActionResult(false, null, validation);
        }
        Integer curriculumId = studentCurriculumService.findCurrentCurriculumId(sn);
        db.update("""
                INSERT INTO transfer_credit_requests
                    (student_number, curriculum_id, course_id, course_code, numeric_grade,
                     source_school, note, status, requested_by, requested_by_role,
                     source_system, source_table, source_row_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, NULL, NULL, NULL)
                """,
            sn,
            curriculumId,
            courseId,
            courseCode,
            numericGrade != null ? BigDecimal.valueOf(numericGrade) : null,
            cleanNullable(sourceSchool, 180),
            cleanNullable(note, 500),
            cleanNullable(requestedBy, 100),
            cleanNullable(requestedByRole, 50));
        Long requestId = db.queryForObject(
            "SELECT request_id FROM transfer_credit_requests WHERE student_number = ? AND course_id = ? ORDER BY request_id DESC LIMIT 1",
            Long.class,
            sn,
            courseId);
        String detail = buildRequestDetail(courseCode, sourceSchool, note, numericGrade);
        regFormEventService.recordEvent(
            sn,
            "TRANSFER_CREDIT_REQUESTED",
            "Transfer/TOR credit submitted for approval",
            requestId,
            detail,
            requestedBy);
        documentTrailService.recordStudentEvent(
            sn,
            "STUDENT",
            "TRANSFER_CREDIT",
            "TRANSFER_CREDIT_REQUESTED",
            "Transfer/TOR credit submitted for approval",
            detail,
            requestedBy,
            requestId,
            "transfer_credit_requests",
            requestId != null ? String.valueOf(requestId) : null);
        return new CreditRequestActionResult(true, requestId, "SUCCESS: Transfer/TOR credit request submitted for approval.");
    }

    @Transactional
    public BulkCreditResult submitBulkCreditRequestsFromCsv(String studentNumber,
                                                            String csvText,
                                                            String defaultSourceSchool,
                                                            String requestedBy) {
        return submitBulkCreditRequestsFromCsv(
            studentNumber,
            csvText,
            defaultSourceSchool,
            requestedBy,
            resolveUserRole(requestedBy));
    }

    @Transactional
    public BulkCreditResult submitBulkCreditRequestsFromCsv(String studentNumber,
                                                            String csvText,
                                                            String defaultSourceSchool,
                                                            String requestedBy,
                                                            String requestedByRole) {
        ensureSchema();
        List<BulkCreditLineResult> lines = new ArrayList<>();
        int requested = 0;
        int skipped = 0;
        if (studentNumber == null || studentNumber.isBlank()) {
            lines.add(new BulkCreditLineResult("", false, "Student number is required."));
            return new BulkCreditResult(0, 1, lines);
        }
        if (csvText == null || csvText.isBlank()) {
            lines.add(new BulkCreditLineResult("", false, "CSV is empty."));
            return new BulkCreditResult(0, 1, lines);
        }
        try (BufferedReader reader = new BufferedReader(new StringReader(csvText))) {
            String line;
            int row = 0;
            while ((line = reader.readLine()) != null) {
                row++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (row == 1 && trimmed.toLowerCase().startsWith("course_code")) continue;

                String[] parts = trimmed.split(",", -1);
                String code = parts.length > 0 ? parts[0].trim() : "";
                if (code.isEmpty()) {
                    skipped++;
                    lines.add(new BulkCreditLineResult("", false, "Row " + row + ": missing course_code"));
                    continue;
                }
                Integer courseId = lookupCourseId(code);
                if (courseId == null) {
                    skipped++;
                    lines.add(new BulkCreditLineResult(code, false, "Row " + row + ": course code not found"));
                    continue;
                }
                Double numericGrade = null;
                if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                    try {
                        numericGrade = Double.parseDouble(parts[1].trim());
                    } catch (NumberFormatException e) {
                        skipped++;
                        lines.add(new BulkCreditLineResult(code, false, "Row " + row + ": invalid numeric_grade"));
                        continue;
                    }
                }
                String sourceSchool = parts.length > 2 && !parts[2].trim().isEmpty()
                    ? parts[2].trim()
                    : defaultSourceSchool;
                String note = parts.length > 3 ? parts[3].trim() : null;

                CreditRequestActionResult result =
                    submitCreditRequest(studentNumber, courseId, numericGrade, sourceSchool, note, requestedBy, requestedByRole);
                if (result.ok()) {
                    requested++;
                    lines.add(new BulkCreditLineResult(code, true, result.message()));
                } else {
                    skipped++;
                    lines.add(new BulkCreditLineResult(code, false, result.message()));
                }
            }
        } catch (Exception e) {
            lines.add(new BulkCreditLineResult("", false, "CSV parse error: " + e.getMessage()));
            skipped++;
        }
        if (requested > 0) {
            regFormEventService.recordEvent(
                studentNumber.trim(),
                "BULK_TRANSFER_CREDIT_REQUESTED",
                "Bulk transfer/TOR credit submitted for approval",
                null,
                "Bulk request submitted: " + requested + " course(s)." +
                    (defaultSourceSchool != null && !defaultSourceSchool.isBlank() ? " Default source: " + defaultSourceSchool.trim() : ""),
                requestedBy);
        }
        return new BulkCreditResult(requested, skipped, lines);
    }

    @Transactional
    public CreditRequestActionResult approveCreditRequest(long requestId, String approvedBy) {
        return approveCreditRequest(requestId, approvedBy, resolveUserRole(approvedBy));
    }

    @Transactional
    public CreditRequestActionResult approveCreditRequest(long requestId, String approvedBy, String approvedByRole) {
        ensureSchema();
        if (!isRegistrarApprovalRole(approvedByRole)) {
            return new CreditRequestActionResult(
                false,
                requestId,
                "ERROR: Only registrar-authorized users can approve TOR accreditation requests.");
        }
        Map<String, Object> row = findRequest(requestId);
        if (row == null) {
            return new CreditRequestActionResult(false, requestId, "ERROR: Transfer credit request not found.");
        }
        if (!"PENDING".equalsIgnoreCase(String.valueOf(row.getOrDefault("status", "")))) {
            return new CreditRequestActionResult(false, requestId, "ERROR: Only pending requests can be approved.");
        }
        if (!isDeanOriginatedRequest(row)) {
            return new CreditRequestActionResult(
                false,
                requestId,
                "ERROR: Only Enrollment Dean-submitted TOR accreditation requests can be approved.");
        }
        String studentNumber = String.valueOf(row.getOrDefault("student_number", "")).trim();
        int courseId = ((Number) row.get("course_id")).intValue();
        String courseCode = String.valueOf(row.getOrDefault("course_code", "")).trim();
        Double numericGrade = row.get("numeric_grade") instanceof Number
            ? ((Number) row.get("numeric_grade")).doubleValue()
            : null;
        String sourceSchool = row.get("source_school") != null ? row.get("source_school").toString() : null;
        String note = row.get("note") != null ? row.get("note").toString() : null;
        String validation = validateCreditable(studentNumber, courseId, courseCode, false);
        if (validation != null) {
            return new CreditRequestActionResult(false, requestId, validation);
        }
        String result = creditCourseInternal(studentNumber, courseId, courseCode, numericGrade, sourceSchool, note, approvedBy);
        if (!result.startsWith("SUCCESS:")) {
            return new CreditRequestActionResult(false, requestId, result);
        }
        db.update("""
                UPDATE transfer_credit_requests
                SET status = 'APPROVED',
                    approved_by = ?,
                    approved_at = CURRENT_TIMESTAMP,
                    rejected_by = NULL,
                    rejected_at = NULL,
                    rejection_reason = NULL
                WHERE request_id = ? AND status = 'PENDING'
                """,
            cleanNullable(approvedBy, 100),
            requestId);
        syncUpstreamApproval(row, approvedBy, findPostedGradeId(studentNumber, courseId));
        String detail = buildRequestDetail(courseCode, sourceSchool, note, numericGrade);
        regFormEventService.recordEvent(
            studentNumber,
            "TRANSFER_CREDIT_APPROVED",
            "Transfer/TOR credit approved",
            requestId,
            detail,
            approvedBy);
        documentTrailService.recordStudentEvent(
            studentNumber,
            "STUDENT",
            "TRANSFER_CREDIT",
            "TRANSFER_CREDIT_APPROVED",
            "Transfer/TOR credit approved",
            detail,
            approvedBy,
            requestId,
            "transfer_credit_requests",
            String.valueOf(requestId));
        return new CreditRequestActionResult(true, requestId, "SUCCESS: Transfer/TOR credit approved and posted.");
    }

    @Transactional
    public CreditRequestActionResult rejectCreditRequest(long requestId, String rejectedBy, String reason) {
        return rejectCreditRequest(requestId, rejectedBy, resolveUserRole(rejectedBy), reason);
    }

    @Transactional
    public CreditRequestActionResult rejectCreditRequest(long requestId,
                                                         String rejectedBy,
                                                         String rejectedByRole,
                                                         String reason) {
        ensureSchema();
        if (!isRegistrarApprovalRole(rejectedByRole)) {
            return new CreditRequestActionResult(
                false,
                requestId,
                "ERROR: Only registrar-authorized users can reject TOR accreditation requests.");
        }
        Map<String, Object> row = findRequest(requestId);
        if (row == null) {
            return new CreditRequestActionResult(false, requestId, "ERROR: Transfer credit request not found.");
        }
        if (!"PENDING".equalsIgnoreCase(String.valueOf(row.getOrDefault("status", "")))) {
            return new CreditRequestActionResult(false, requestId, "ERROR: Only pending requests can be rejected.");
        }
        if (!isDeanOriginatedRequest(row)) {
            return new CreditRequestActionResult(
                false,
                requestId,
                "ERROR: Only Enrollment Dean-submitted TOR accreditation requests can be reviewed here.");
        }
        String cleanReason = cleanNullable(reason, 500);
        if (cleanReason == null) {
            cleanReason = "Registrar review rejected the request.";
        }
        db.update("""
                UPDATE transfer_credit_requests
                SET status = 'REJECTED',
                    rejected_by = ?,
                    rejected_at = CURRENT_TIMESTAMP,
                    rejection_reason = ?
                WHERE request_id = ? AND status = 'PENDING'
                """,
            cleanNullable(rejectedBy, 100),
            cleanReason,
            requestId);
        syncUpstreamRejection(row, rejectedBy, cleanReason);
        String studentNumber = String.valueOf(row.getOrDefault("student_number", "")).trim();
        String courseCode = String.valueOf(row.getOrDefault("course_code", "")).trim();
        regFormEventService.recordEvent(
            studentNumber,
            "TRANSFER_CREDIT_REJECTED",
            "Transfer/TOR credit rejected",
            requestId,
            courseCode + " | " + cleanReason,
            rejectedBy);
        documentTrailService.recordStudentEvent(
            studentNumber,
            "STUDENT",
            "TRANSFER_CREDIT",
            "TRANSFER_CREDIT_REJECTED",
            "Transfer/TOR credit rejected",
            courseCode + " | " + cleanReason,
            rejectedBy,
            requestId,
            "transfer_credit_requests",
            String.valueOf(requestId));
        return new CreditRequestActionResult(true, requestId, "SUCCESS: Transfer/TOR credit request rejected.");
    }

    public List<Map<String, Object>> listRequestsForStudent(String studentNumber) {
        ensureSchema();
        if (studentNumber == null || studentNumber.isBlank()) {
            return List.of();
        }
        return db.queryForList("""
            SELECT request_id, student_number, curriculum_id, course_id, course_code,
                   numeric_grade, source_school, note, status,
                   requested_by, requested_by_role, source_system, source_table, source_row_id,
                   requested_at, approved_by, approved_at,
                   rejected_by, rejected_at, rejection_reason
            FROM transfer_credit_requests
            WHERE student_number = ?
            ORDER BY
                CASE status
                    WHEN 'PENDING' THEN 0
                    WHEN 'APPROVED' THEN 1
                    WHEN 'REJECTED' THEN 2
                    ELSE 9
                END,
                requested_at DESC,
                request_id DESC
            """, studentNumber.trim());
    }

    private String creditCourseInternal(String sn, int courseId, String courseCode,
                                        Double numericGrade, String sourceSchool, String note,
                                        String triggeredBy) {
        String validation = validateCreditable(sn, courseId, courseCode, false);
        if (validation != null) {
            return validation;
        }
        transferCreditGradePort.saveTransferCredit(
            sn,
            courseId,
            resolveStudentName(sn),
            buildLockReason(sourceSchool, note),
            numericGrade != null ? BigDecimal.valueOf(numericGrade) : null);

        try {
            StringBuilder remarks = new StringBuilder();
            remarks.append("Credited ").append(courseCode);
            if (sourceSchool != null && !sourceSchool.isBlank()) {
                remarks.append(" from ").append(sourceSchool.trim());
            }
            if (note != null && !note.isBlank()) {
                remarks.append(" | ").append(note.trim());
            }
            if (numericGrade != null) {
                remarks.append(" | numeric=").append(numericGrade);
            }
            regFormEventService.recordEvent(
                sn,
                "TRANSFER_CREDIT",
                "Transfer/TOR credit recorded",
                null,
                remarks.toString(),
                triggeredBy);
        } catch (Exception ignored) {
        }
        return "SUCCESS: Credited " + courseCode + " as transfer/prior-school credit.";
    }

    private String validateCreditable(String studentNumber,
                                      int courseId,
                                      String courseCode,
                                      boolean checkPending) {
        Integer curriculumId = studentCurriculumService.findCurrentCurriculumId(studentNumber);
        if (curriculumId == null) {
            return "ERROR: No curriculum assigned for this student.";
        }
        if (!courseInCurriculum(curriculumId, courseId)) {
            return "ERROR: Course is not part of the student's assigned curriculum.";
        }
        if (isCoursePassed(studentNumber, courseId)) {
            return "ERROR: Student already has a passing grade for this course.";
        }
        if (checkPending && hasPendingRequest(studentNumber, courseId)) {
            return "ERROR: There is already a pending transfer/TOR credit request for " + courseCode + ".";
        }
        return null;
    }

    private String lookupCourseCode(int courseId) {
        try {
            return db.queryForObject(
                "SELECT course_code FROM courses WHERE course_id = ? LIMIT 1",
                String.class, courseId);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer lookupCourseId(String courseCode) {
        try {
            return db.queryForObject(
                "SELECT course_id FROM courses WHERE course_code = ? LIMIT 1",
                Integer.class, courseCode);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isCoursePassed(String studentNumber, int courseId) {
        List<Object> keys = gradeLookupKeys(studentNumber);
        if (keys.isEmpty()) return false;
        String in = gradeInClause(keys.size());
        Object[] args = new Object[keys.size() + 1];
        args[0] = courseId;
        for (int i = 0; i < keys.size(); i++) {
            args[i + 1] = keys.get(i);
        }
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM grades g WHERE g.course_id = ? AND " + in + " AND " + GradeOutcomeSql.passed("g"),
                Integer.class, args);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean courseInCurriculum(int curriculumId, int courseId) {
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM curriculum_courses WHERE curriculum_id = ? AND course_id = ?",
                Integer.class, curriculumId, courseId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasPendingRequest(String studentNumber, int courseId) {
        ensureSchema();
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM transfer_credit_requests WHERE student_number = ? AND course_id = ? AND status = 'PENDING'",
                Integer.class,
                studentNumber,
                courseId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> findRequest(long requestId) {
        ensureSchema();
        List<Map<String, Object>> rows = db.queryForList(
            """
                SELECT request_id, student_number, curriculum_id, course_id, course_code,
                       numeric_grade, source_school, note, status,
                       requested_by, requested_by_role, source_system, source_table, source_row_id,
                       requested_at, approved_by, approved_at,
                       rejected_by, rejected_at, rejection_reason
                FROM transfer_credit_requests
                WHERE request_id = ?
                LIMIT 1
                """,
            requestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String resolveStudentName(String studentNumber) {
        try {
            return db.queryForObject(
                "SELECT COALESCE(NULLIF(real_name, ''), student_number) FROM students WHERE student_number = ? LIMIT 1",
                String.class, studentNumber);
        } catch (Exception ignored) {
        }
        try {
            return db.queryForObject(
                "SELECT COALESCE(NULLIF(real_name, ''), username) FROM sys_users WHERE username = ? LIMIT 1",
                String.class, studentNumber);
        } catch (Exception e) {
            return studentNumber;
        }
    }

    private String buildLockReason(String sourceSchool, String note) {
        StringBuilder sb = new StringBuilder("TRANSFER_CREDIT");
        if (sourceSchool != null && !sourceSchool.isBlank()) {
            sb.append('|').append(sourceSchool.trim());
        }
        if (note != null && !note.isBlank()) {
            sb.append('|').append(note.trim());
        }
        String reason = sb.toString();
        return reason.length() <= 255 ? reason : reason.substring(0, 255);
    }

    private boolean isDeanOriginatedRequest(Map<String, Object> row) {
        String requestedByRole = stringValue(row.get("requested_by_role"));
        if (isDeanRole(requestedByRole)) {
            return true;
        }
        return isDeanRole(resolveUserRole(stringValue(row.get("requested_by"))));
    }

    private boolean isDeanRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String normalized = role.trim().toUpperCase();
        return "DEAN".equals(normalized)
            || "ENROLLMENT DEAN".equals(normalized)
            || "ENROLLMENT_DEAN".equals(normalized);
    }

    private boolean isRegistrarApprovalRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String normalized = role.trim().toUpperCase();
        return "REGISTRAR".equals(normalized) || "ADMIN".equals(normalized);
    }

    private String resolveUserRole(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            return db.queryForObject(
                "SELECT role FROM sys_users WHERE username = ? LIMIT 1",
                String.class,
                username.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private Integer findPostedGradeId(String studentNumber, int courseId) {
        try {
            return db.queryForObject(
                "SELECT id FROM grades WHERE student_id = ? AND course_id = ? ORDER BY id DESC LIMIT 1",
                Integer.class,
                studentNumber,
                courseId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void syncUpstreamApproval(Map<String, Object> row, String actor, Integer postedGradeId) {
        String sourceTable = stringValue(row.get("source_table"));
        String sourceRowId = stringValue(row.get("source_row_id"));
        if (sourceTable == null || sourceTable.isBlank() || sourceRowId == null || sourceRowId.isBlank()) {
            return;
        }
        try {
            if ("tentative_credited_subject".equalsIgnoreCase(sourceTable)) {
                db.update(
                    "UPDATE tentative_credited_subject SET posting_status = 'POSTED', posted_by = ?, posted_at = NOW(), posted_grade_id = ? WHERE tentative_credit_id = ?",
                    cleanNullable(actor, 100),
                    postedGradeId,
                    Long.parseLong(sourceRowId));
                return;
            }
            if ("applicant_credit_accreditation_lines".equalsIgnoreCase(sourceTable)) {
                ensureApplicantAccreditationLineSyncColumns();
                db.update(
                    "UPDATE applicant_credit_accreditation_lines SET registrar_decision_status = 'APPROVED', registrar_decided_by = ?, registrar_decided_at = NOW(), registrar_decision_note = NULL, registrar_posted_grade_id = ? WHERE line_id = ?",
                    cleanNullable(actor, 100),
                    postedGradeId,
                    Long.parseLong(sourceRowId));
            }
        } catch (Exception ignored) {
        }
    }

    private void syncUpstreamRejection(Map<String, Object> row, String actor, String reason) {
        String sourceTable = stringValue(row.get("source_table"));
        String sourceRowId = stringValue(row.get("source_row_id"));
        if (sourceTable == null || sourceTable.isBlank() || sourceRowId == null || sourceRowId.isBlank()) {
            return;
        }
        if (!"applicant_credit_accreditation_lines".equalsIgnoreCase(sourceTable)) {
            return;
        }
        try {
            ensureApplicantAccreditationLineSyncColumns();
            db.update(
                "UPDATE applicant_credit_accreditation_lines SET registrar_decision_status = 'REJECTED', registrar_decided_by = ?, registrar_decided_at = NOW(), registrar_decision_note = ?, registrar_posted_grade_id = NULL WHERE line_id = ?",
                cleanNullable(actor, 100),
                cleanNullable(reason, 500),
                Long.parseLong(sourceRowId));
        } catch (Exception ignored) {
        }
    }

    private void ensureApplicantAccreditationLineSyncColumns() {
        try {
            db.execute("ALTER TABLE applicant_credit_accreditation_lines ADD COLUMN registrar_decision_status VARCHAR(32) NULL");
        } catch (Exception ignored) {
        }
        try {
            db.execute("ALTER TABLE applicant_credit_accreditation_lines ADD COLUMN registrar_decided_by VARCHAR(100) NULL");
        } catch (Exception ignored) {
        }
        try {
            db.execute("ALTER TABLE applicant_credit_accreditation_lines ADD COLUMN registrar_decided_at DATETIME NULL");
        } catch (Exception ignored) {
        }
        try {
            db.execute("ALTER TABLE applicant_credit_accreditation_lines ADD COLUMN registrar_decision_note VARCHAR(500) NULL");
        } catch (Exception ignored) {
        }
        try {
            db.execute("ALTER TABLE applicant_credit_accreditation_lines ADD COLUMN registrar_posted_grade_id INT NULL");
        } catch (Exception ignored) {
        }
    }

    private List<Object> gradeLookupKeys(String studentNumber) {
        List<Object> keys = new ArrayList<>();
        keys.add(studentNumber);
        try {
            Integer userId = db.queryForObject(
                "SELECT user_id FROM sys_users WHERE username = ? LIMIT 1",
                Integer.class, studentNumber);
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
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        sb.append(')');
        return sb.toString();
    }

    private String buildRequestDetail(String courseCode,
                                      String sourceSchool,
                                      String note,
                                      Double numericGrade) {
        StringBuilder remarks = new StringBuilder(courseCode);
        if (sourceSchool != null && !sourceSchool.isBlank()) {
            remarks.append(" from ").append(sourceSchool.trim());
        }
        if (numericGrade != null) {
            remarks.append(" | numeric=").append(numericGrade);
        }
        if (note != null && !note.isBlank()) {
            remarks.append(" | ").append(note.trim());
        }
        return remarks.toString();
    }

    private String cleanNullable(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private void normalizeAccreditationCollations() {
        tryExecute("ALTER TABLE applicant_credit_accreditations CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci");
        tryExecute("ALTER TABLE applicant_credit_accreditation_lines CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci");
        tryExecute("ALTER TABLE transfer_credit_requests CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci");
    }

    private void tryExecute(String sql) {
        try {
            db.execute(sql);
        } catch (Exception ignored) {
        }
    }
}
