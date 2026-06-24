package com.iuims.registrar.curriculum;

import com.iuims.registrar.core.GradeOutcomeSql;
import com.iuims.registrar.forms.RegFormEventService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentCurriculumService {

    private final JdbcTemplate db;
    private final RegFormEventService regFormEventService;

    public StudentCurriculumService(JdbcTemplate db, RegFormEventService regFormEventService) {
        this.db = db;
        this.regFormEventService = regFormEventService;
    }

    public void ensureSchema() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS student_curriculum_assignments (
                assignment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                student_number VARCHAR(100) NOT NULL,
                curriculum_id INT NOT NULL,
                program_code VARCHAR(100) NOT NULL,
                assignment_type VARCHAR(40) NOT NULL DEFAULT 'DEFAULT',
                reason VARCHAR(255) NULL,
                is_current TINYINT(1) NOT NULL DEFAULT 1,
                assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        try {
            db.execute("CREATE INDEX idx_sca_student_current ON student_curriculum_assignments (student_number, is_current)");
        } catch (Exception ignored) {
        }
        try {
            db.execute("CREATE INDEX idx_sca_curriculum ON student_curriculum_assignments (curriculum_id)");
        } catch (Exception ignored) {
        }
        try {
            db.execute("CREATE INDEX idx_sca_program ON student_curriculum_assignments (program_code)");
        } catch (Exception ignored) {
        }
    }

    public Integer findCurrentCurriculumId(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) return null;
        try {
            ensureSchema();
            return db.queryForObject(
                "SELECT curriculum_id FROM student_curriculum_assignments " +
                    "WHERE student_number = ? AND is_current = 1 ORDER BY assignment_id DESC LIMIT 1",
                Integer.class, studentNumber.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hasCurrentAssignment(String studentNumber) {
        return findCurrentCurriculumId(studentNumber) != null;
    }

    public Integer resolveCurrentCurriculum(String studentNumber) {
        return findCurrentCurriculumId(studentNumber);
    }

    public Integer requireCurrentCurriculumId(String studentNumber) {
        Integer curriculumId = findCurrentCurriculumId(studentNumber);
        if (curriculumId == null) {
            throw new IllegalStateException("No curriculum assigned for this student.");
        }
        return curriculumId;
    }

    /**
     * Backward-compatible alias for read-only resolution.
     *
     * <p>The old behavior silently created a default curriculum row; that
     * masked missing assignment data in runtime builder/read paths.
     */
    @Transactional
    public Integer resolveOrAssignCurrentCurriculum(String studentNumber) {
        return resolveCurrentCurriculum(studentNumber);
    }

    private String findStudentProgramCode(String studentNumber) {
        try {
            return db.queryForObject(
                "SELECT program_code FROM students WHERE student_number = ? LIMIT 1",
                String.class,
                studentNumber);
        } catch (Exception ignored) {
            try {
                return db.queryForObject(
                    "SELECT program_code FROM sys_users WHERE username = ? LIMIT 1",
                    String.class,
                    studentNumber);
            } catch (Exception e) {
                return null;
            }
        }
    }

    public Integer findDefaultCurriculumId(String programCode) {
        if (programCode == null || programCode.isBlank()) return null;
        try {
            return db.queryForObject(
                "SELECT ct.curriculum_id FROM curriculum_templates ct " +
                    "JOIN programs p ON p.program_id = ct.program_id " +
                    "JOIN curriculum_courses cc ON cc.curriculum_id = ct.curriculum_id " +
                    "WHERE p.program_code = ? AND " + lifecycleSql("ct") + " = 'CURRENT' " +
                    "GROUP BY ct.curriculum_id, ct.version_number " +
                    "ORDER BY ct.version_number DESC, ct.curriculum_id DESC LIMIT 1",
                Integer.class, programCode.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    public boolean curriculumBelongsToProgram(Integer curriculumId, String programCode) {
        if (curriculumId == null || programCode == null || programCode.isBlank()) return false;
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM curriculum_templates ct " +
                    "JOIN programs p ON p.program_id = ct.program_id " +
                    "WHERE ct.curriculum_id = ? AND p.program_code = ?",
                Integer.class, curriculumId, programCode.trim().toUpperCase());
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public void assignCurriculum(String studentNumber, Integer curriculumId, String assignmentType, String reason) {
        if (studentNumber == null || studentNumber.isBlank() || curriculumId == null) return;
        ensureSchema();
        String sn = studentNumber.trim();
        String programCode = db.queryForObject(
            "SELECT p.program_code FROM curriculum_templates ct " +
                "JOIN programs p ON p.program_id = ct.program_id WHERE ct.curriculum_id = ? LIMIT 1",
            String.class, curriculumId);
        validateStudentProgramMatchesCurriculum(sn, programCode);
        db.update(
            "UPDATE student_curriculum_assignments SET is_current = 0 " +
                "WHERE student_number = ? AND is_current = 1",
            sn);
        db.update(
            "INSERT INTO student_curriculum_assignments " +
                "(student_number, curriculum_id, program_code, assignment_type, reason, is_current) " +
                "VALUES (?, ?, ?, ?, ?, 1)",
            sn, curriculumId, programCode, normalizeAssignmentType(assignmentType), truncateReason(reason));
        try {
            regFormEventService.recordEvent(
                sn,
                "CURRICULUM_ASSIGNED",
                "Registrar curriculum assignment updated",
                null,
                "Curriculum " + curriculumId + " assigned for program " + programCode
                    + (reason != null && !reason.isBlank() ? " | " + reason.trim() : ""),
                "registrar");
        } catch (Exception ignored) {
        }
    }

    private void validateStudentProgramMatchesCurriculum(String studentNumber, String curriculumProgramCode) {
        if (curriculumProgramCode == null || curriculumProgramCode.isBlank()) return;
        try {
            String studentProgramCode = db.queryForObject(
                "SELECT program_code FROM students WHERE student_number = ? LIMIT 1",
                String.class, studentNumber);
            if (studentProgramCode != null && !studentProgramCode.isBlank()
                    && !studentProgramCode.trim().equalsIgnoreCase(curriculumProgramCode.trim())) {
                throw new IllegalArgumentException(
                    "Selected curriculum belongs to " + curriculumProgramCode
                        + " but student is currently in " + studentProgramCode + ".");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception ignored) {
            // Some controlled bootstrap flows assign before the canonical student row exists.
        }
    }

    public List<Map<String, Object>> listAssignableCurricula() {
        try {
            return db.queryForList(
                "SELECT ct.curriculum_id, ct.curriculum_name, ct.academic_year, ct.version_number, ct.is_active, " +
                    lifecycleSql("ct") + " AS lifecycle_status, " +
                    "p.program_code, p.program_name " +
                    "FROM curriculum_templates ct JOIN programs p ON p.program_id = ct.program_id " +
                    "WHERE COALESCE(p.active_status, 1) = 1 AND " + lifecycleSql("ct") + " IN ('CURRENT','LEGACY') " +
                    "ORDER BY p.program_code, CASE " + lifecycleSql("ct") + " WHEN 'CURRENT' THEN 0 WHEN 'LEGACY' THEN 1 ELSE 2 END, " +
                    "ct.version_number DESC, ct.curriculum_id DESC");
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public Map<String, Object> getCurrentAssignment(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) return null;
        try {
            ensureSchema();
            return db.queryForMap(
                "SELECT sca.assignment_id, sca.student_number, sca.curriculum_id, sca.program_code, " +
                    "sca.assignment_type, sca.reason, sca.assigned_at, ct.curriculum_name, ct.academic_year, " +
                    "ct.version_number, ct.is_active, " + lifecycleSql("ct") + " AS lifecycle_status " +
                    "FROM student_curriculum_assignments sca " +
                    "JOIN curriculum_templates ct ON ct.curriculum_id = sca.curriculum_id " +
                    "WHERE sca.student_number = ? AND sca.is_current = 1 " +
                    "ORDER BY sca.assignment_id DESC LIMIT 1",
                studentNumber.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeAssignmentType(String assignmentType) {
        if (assignmentType == null || assignmentType.isBlank()) return "DEFAULT";
        return assignmentType.trim().toUpperCase();
    }

    private String lifecycleSql(String alias) {
        String prefix = alias == null || alias.isBlank() ? "" : alias + ".";
        return "UPPER(COALESCE(NULLIF(" + prefix + "lifecycle_status, ''), " +
            "CASE " +
            "WHEN UPPER(COALESCE(" + prefix + "approval_status,'')) IN ('ARCHIVED','RETIRED') THEN 'ARCHIVED' " +
            "WHEN UPPER(COALESCE(" + prefix + "approval_status,'')) IN ('DRAFT','PLACEHOLDER') AND COALESCE(" + prefix + "is_active, 0) = 0 THEN 'DRAFT' " +
            "WHEN COALESCE(" + prefix + "is_active, 0) = 1 THEN 'CURRENT' " +
            "ELSE 'LEGACY' END))";
    }

    private String truncateReason(String reason) {
        if (reason == null || reason.length() <= 255) return reason;
        return reason.substring(0, 255);
    }

    /**
     * Passed courses that satisfy rows in the student's assigned curriculum (carry-over).
     */
    public List<Map<String, Object>> listCarriedOverCredits(String studentNumber) {
        return listCurriculumGradeRows(studentNumber, true);
    }

    /**
     * Passed courses not mapped to any row in the assigned curriculum (prior program / electives).
     */
    public List<Map<String, Object>> listOrphanPassedCredits(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) return List.of();
        String sn = studentNumber.trim();
        Integer curriculumId = resolveCurrentCurriculum(sn);
        if (curriculumId == null) return List.of();
        try {
            List<Object> keys = gradeLookupKeys(sn);
            if (keys.isEmpty()) return List.of();
            String in = gradeInClause(keys.size());
            Object[] args = new Object[keys.size() + 1];
            for (int i = 0; i < keys.size(); i++) {
                args[i] = keys.get(i);
            }
            args[keys.size()] = curriculumId;
            return db.queryForList(
                "SELECT c.course_code, c.course_title, c.credit_units, "
                    + GradeOutcomeSql.outcome("g") + " AS outcome "
                    + "FROM grades g "
                    + "JOIN courses c ON c.course_id = g.course_id "
                    + "WHERE " + in + " AND " + GradeOutcomeSql.passed("g") + " "
                    + "AND NOT EXISTS (SELECT 1 FROM curriculum_courses cc "
                    + "WHERE cc.curriculum_id = ? AND cc.course_id = c.course_id) "
                    + "ORDER BY c.course_code",
                args);
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, Object> getShiftCarryOverSummary(String studentNumber) {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<Map<String, Object>> carried = listCarriedOverCredits(studentNumber);
        List<Map<String, Object>> orphans = listOrphanPassedCredits(studentNumber);
        List<Map<String, Object>> deficiencies = listCurriculumDeficiencies(studentNumber);
        summary.put("carriedOver", carried);
        summary.put("orphanCredits", orphans);
        summary.put("deficiencies", deficiencies);
        summary.put("carriedOverCount", carried.size());
        summary.put("orphanCount", orphans.size());
        summary.put("deficiencyCount", deficiencies.size());
        return summary;
    }

    /**
     * Courses in the student's assigned curriculum without a passing grade.
     */
    public List<Map<String, Object>> listCurriculumDeficiencies(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) return List.of();
        String sn = studentNumber.trim();
        Integer curriculumId = resolveCurrentCurriculum(sn);
        if (curriculumId == null) return List.of();
        try {
            List<Object> keys = gradeLookupKeys(sn);
            if (keys.isEmpty()) return List.of();
            String in = gradeInClause(keys.size());
            Object[] args = new Object[keys.size() + 1];
            args[0] = curriculumId;
            for (int i = 0; i < keys.size(); i++) {
                args[i + 1] = keys.get(i);
            }
            return db.queryForList(
                "SELECT cc.year_level, cc.semester_number, c.course_id, c.course_code, c.course_title, c.credit_units "
                    + "FROM curriculum_courses cc "
                    + "JOIN courses c ON c.course_id = cc.course_id "
                    + "WHERE cc.curriculum_id = ? "
                    + "AND NOT EXISTS (SELECT 1 FROM grades g WHERE g.course_id = c.course_id AND "
                    + in + " AND " + GradeOutcomeSql.passed("g") + ") "
                    + "ORDER BY cc.year_level, cc.semester_number, c.course_code",
                args);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Object> gradeLookupKeys(String studentNumber) {
        List<Object> keys = new ArrayList<>();
        keys.add(studentNumber);
        try {
            Integer userId = db.queryForObject(
                "SELECT user_id FROM sys_users WHERE username = ? LIMIT 1",
                Integer.class, studentNumber);
            if (userId != null) keys.add(String.valueOf(userId));
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

    private List<Map<String, Object>> listCurriculumGradeRows(String studentNumber, boolean passedOnly) {
        if (studentNumber == null || studentNumber.isBlank()) return List.of();
        String sn = studentNumber.trim();
        Integer curriculumId = resolveCurrentCurriculum(sn);
        if (curriculumId == null) return List.of();
        try {
            List<Object> keys = gradeLookupKeys(sn);
            if (keys.isEmpty()) return List.of();
            String in = gradeInClause(keys.size());
            Object[] args = new Object[keys.size() + 1];
            for (int i = 0; i < keys.size(); i++) {
                args[i] = keys.get(i);
            }
            args[keys.size()] = curriculumId;
            String gradeFilter = passedOnly ? " AND " + GradeOutcomeSql.passed("g") + " " : "";
            return db.queryForList(
                "SELECT cc.year_level, cc.semester_number, c.course_id, c.course_code, c.course_title, c.credit_units, "
                    + "g.registrar_final_grade, g.semestral_grade, g.grade_lock_reason "
                    + "FROM curriculum_courses cc "
                    + "JOIN courses c ON c.course_id = cc.course_id "
                    + "JOIN grades g ON g.course_id = c.course_id AND " + in + gradeFilter
                    + "WHERE cc.curriculum_id = ? "
                    + "ORDER BY cc.year_level, cc.semester_number, c.course_code",
                args);
        } catch (Exception e) {
            return List.of();
        }
    }
}



