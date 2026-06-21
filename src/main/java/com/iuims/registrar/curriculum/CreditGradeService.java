package com.iuims.registrar.curriculum;

import com.iuims.registrar.core.GradeOutcomeSql;
import com.iuims.registrar.forms.RegFormEventService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CreditGradeService {

    public record BulkCreditLineResult(String courseCode, boolean ok, String detail) {}

    public record BulkCreditResult(int credited, int skipped, List<BulkCreditLineResult> lines) {}

    private final JdbcTemplate db;
    private final StudentCurriculumService studentCurriculumService;
    private final RegFormEventService regFormEventService;
    private final TransferCreditGradePort transferCreditGradePort;

    public CreditGradeService(JdbcTemplate db,
                              StudentCurriculumService studentCurriculumService,
                              RegFormEventService regFormEventService,
                              TransferCreditGradePort transferCreditGradePort) {
        this.db = db;
        this.studentCurriculumService = studentCurriculumService;
        this.regFormEventService = regFormEventService;
        this.transferCreditGradePort = transferCreditGradePort;
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
        return creditCourseInternal(sn, courseId, courseCode, numericGrade, sourceSchool, note);
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
        return creditCourseInternal(sn, courseId, courseCode.trim(), numericGrade, sourceSchool, note);
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

    private String creditCourseInternal(String sn, int courseId, String courseCode,
                                        Double numericGrade, String sourceSchool, String note) {
        Integer curriculumId = studentCurriculumService.findCurrentCurriculumId(sn);
        if (curriculumId == null) {
            return "ERROR: No curriculum assigned for this student.";
        }
        if (!courseInCurriculum(curriculumId, courseId)) {
            return "ERROR: Course is not part of the student's assigned curriculum.";
        }
        if (isCoursePassed(sn, courseId)) {
            return "ERROR: Student already has a passing grade for this course.";
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
                "registrar");
        } catch (Exception ignored) {
        }
        return "SUCCESS: Credited " + courseCode + " as transfer/prior-school credit.";
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
}
