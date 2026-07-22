package com.iuims.registrar.service.curriculum;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Student;

import com.iuims.registrar.service.support.YearLevelLoadPolicyService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class CurriculumLoadPolicyService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal GRADUATING_OVERLOAD_UNITS = new BigDecimal("6");

    private final JdbcTemplate db;
    private final StudentCurriculumService studentCurriculumService;

    public CurriculumLoadPolicyService(JdbcTemplate db, StudentCurriculumService studentCurriculumService) {
        this.db = db;
        this.studentCurriculumService = studentCurriculumService;
    }

    public StudentLoadPolicy resolveForStudent(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) {
            throw new IllegalArgumentException("Student number is required for curriculum load policy.");
        }
        String normalizedStudentNumber = studentNumber.trim();
        Map<String, Object> student = db.queryForMap(
            "SELECT student_number, program_code, COALESCE(year_level, 1) AS year_level, " +
                "COALESCE(semester, 1) AS semester " +
                "FROM students WHERE student_number = ? LIMIT 1",
            normalizedStudentNumber);

        String programCode = text(student.get("program_code"));
        int yearLevel = number(student.get("year_level"), 1);
        int semester = number(student.get("semester"), 1);
        Integer curriculumId = studentCurriculumService.requireCurrentCurriculumId(normalizedStudentNumber);
        validateCurriculumProgram(curriculumId, programCode);

        BigDecimal baseUnits = curriculumUnits(curriculumId, yearLevel, semester);
        int maxYearLevel = maxCurriculumYearLevel(curriculumId);
        boolean graduating = maxYearLevel > 0 && yearLevel >= maxYearLevel;
        BigDecimal graduatingExtraUnits = graduating ? GRADUATING_OVERLOAD_UNITS : ZERO;
        return new StudentLoadPolicy(
            normalizedStudentNumber,
            curriculumId,
            programCode,
            yearLevel,
            semester,
            baseUnits,
            graduating,
            graduatingExtraUnits,
            baseUnits.add(graduatingExtraUnits));
    }

    public BigDecimal effectiveMaximumUnits(String studentNumber) {
        return resolveForStudent(studentNumber).effectiveMaxUnits();
    }

    public boolean isGraduatingStudent(String studentNumber) {
        return resolveForStudent(studentNumber).graduating();
    }

    public YearLevelLoadPolicyService.LoadStanding classifyForStudent(String studentNumber, BigDecimal enrolledUnits) {
        StudentLoadPolicy policy = resolveForStudent(studentNumber);
        BigDecimal units = enrolledUnits != null ? enrolledUnits : ZERO;
        if (units.compareTo(policy.baseUnits()) < 0) {
            return YearLevelLoadPolicyService.LoadStanding.UNDERLOAD;
        }
        if (units.compareTo(policy.effectiveMaxUnits()) > 0) {
            return YearLevelLoadPolicyService.LoadStanding.OVERLOAD;
        }
        return YearLevelLoadPolicyService.LoadStanding.REGULAR;
    }

    private void validateCurriculumProgram(Integer curriculumId, String studentProgramCode) {
        String curriculumProgramCode = db.queryForObject(
            "SELECT p.program_code FROM curriculum_templates ct " +
                "JOIN programs p ON p.program_id = ct.program_id " +
                "WHERE ct.curriculum_id = ? LIMIT 1",
            String.class,
            curriculumId);
        if (studentProgramCode != null && !studentProgramCode.isBlank()
                && curriculumProgramCode != null && !curriculumProgramCode.isBlank()
                && !studentProgramCode.trim().equalsIgnoreCase(curriculumProgramCode.trim())) {
            throw new IllegalStateException(
                "Assigned curriculum belongs to " + curriculumProgramCode
                    + " but student is currently in " + studentProgramCode + ".");
        }
    }

    private BigDecimal curriculumUnits(Integer curriculumId, int yearLevel, int semester) {
        Map<String, Object> row = db.queryForMap(
            "SELECT COUNT(*) AS course_count, COALESCE(SUM(COALESCE(c.credit_units, 0)), 0) AS units " +
                "FROM curriculum_courses cc " +
                "JOIN courses c ON c.course_id = cc.course_id " +
                "WHERE cc.curriculum_id = ? AND cc.year_level = ? AND cc.semester_number = ?",
            curriculumId, yearLevel, semester);
        int courseCount = number(row.get("course_count"), 0);
        BigDecimal units = decimal(row.get("units"));
        if (courseCount <= 0) {
            throw new IllegalStateException(
                "Assigned curriculum has no course rows for Year " + yearLevel
                    + ", Semester " + semester + ".");
        }
        if (units.compareTo(ZERO) <= 0) {
            throw new IllegalStateException(
                "Assigned curriculum has no credited units for Year " + yearLevel
                    + ", Semester " + semester + ".");
        }
        return units;
    }

    private int maxCurriculumYearLevel(Integer curriculumId) {
        Integer maxYear = db.queryForObject(
            "SELECT MAX(year_level) FROM curriculum_courses WHERE curriculum_id = ?",
            Integer.class,
            curriculumId);
        return maxYear != null ? maxYear : 0;
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (value != null) {
            return new BigDecimal(value.toString());
        }
        return ZERO;
    }

    private static String text(Object value) {
        return value != null ? value.toString().trim() : null;
    }

    public record StudentLoadPolicy(
        String studentNumber,
        Integer curriculumId,
        String programCode,
        int yearLevel,
        int semester,
        BigDecimal baseUnits,
        boolean graduating,
        BigDecimal graduatingExtraUnits,
        BigDecimal effectiveMaxUnits) {
    }
}
