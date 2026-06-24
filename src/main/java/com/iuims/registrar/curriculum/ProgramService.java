package com.iuims.registrar.curriculum;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProgramService {

    @Autowired
    private JdbcTemplate db;

    public List<Map<String, Object>> listDepartments() {
        return db.queryForList(
            "SELECT department_id, department_code, department_name " +
                "FROM departments ORDER BY department_name");
    }

    public List<Map<String, Object>> listPrograms(String search, Integer departmentId, String status) {
        String normalizedStatus = normalizeStatus(status);
        String query = search != null ? search.trim().toUpperCase(Locale.ROOT) : "";
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT p.program_id, p.program_code, p.program_name, p.school_name, " +
                "COALESCE(p.duration_years, 0) AS duration_years, " +
                "p.department_id, COALESCE(p.active_status, 1) AS active_status, " +
                "COALESCE(d.department_name, 'Unassigned') AS department_name, " +
                usageSubquery("curriculum_templates", "ct", "ct.program_id = p.program_id") + " AS curriculum_usage, " +
                "(SELECT COUNT(*) FROM student_curriculum_assignments sca " +
                " WHERE sca.program_code = p.program_code) AS assignment_usage " +
                "FROM programs p " +
                "LEFT JOIN departments d ON d.department_id = p.department_id " +
                "WHERE 1 = 1 ");

        if (!query.isBlank()) {
            sql.append("AND (UPPER(p.program_code) LIKE ? OR UPPER(p.program_name) LIKE ?) ");
            args.add("%" + query + "%");
            args.add("%" + query + "%");
        }
        if (departmentId != null && departmentId > 0) {
            sql.append("AND p.department_id = ? ");
            args.add(departmentId);
        }
        if ("active".equals(normalizedStatus)) {
            sql.append("AND COALESCE(p.active_status, 1) = 1 ");
        } else if ("inactive".equals(normalizedStatus)) {
            sql.append("AND COALESCE(p.active_status, 1) = 0 ");
        }

        sql.append("ORDER BY d.department_name, p.program_code");
        List<Map<String, Object>> rows = db.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> row : rows) {
            int curriculum = intValue(row.get("curriculum_usage"));
            int assignments = intValue(row.get("assignment_usage"));
            row.put("usage_count", curriculum + assignments);
        }
        return rows;
    }

    public List<Map<String, Object>> listActivePrograms() {
        return db.queryForList(
            "SELECT p.program_id, p.program_code, p.program_name, p.school_name, " +
                "COALESCE(p.duration_years, 0) AS duration_years, p.department_id, " +
                "COALESCE(d.department_name, 'Unassigned') AS department_name " +
                "FROM programs p " +
                "LEFT JOIN departments d ON d.department_id = p.department_id " +
                "WHERE COALESCE(p.active_status, 1) = 1 " +
                "ORDER BY p.program_code");
    }

    public Map<String, Object> summary(String search, Integer departmentId, String status) {
        List<Map<String, Object>> programs = listPrograms(search, departmentId, status);
        int active = 0;
        int inactive = 0;
        int used = 0;
        for (Map<String, Object> program : programs) {
            if (intValue(program.get("active_status")) == 1) {
                active++;
            } else {
                inactive++;
            }
            if (intValue(program.get("usage_count")) > 0) {
                used++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", programs.size());
        result.put("active", active);
        result.put("inactive", inactive);
        result.put("used", used);
        return result;
    }

    @Transactional
    public Integer saveProgram(Integer programId,
                               String programCode,
                               String programName,
                               Integer departmentId,
                               String schoolName,
                               Integer durationYears,
                               Boolean active) {
        String normalizedCode = normalizeProgramCode(programCode);
        if (normalizedCode == null) {
            throw new IllegalArgumentException("Program code is required.");
        }
        if (programName == null || programName.isBlank()) {
            throw new IllegalArgumentException("Program name is required.");
        }
        int safeDepartmentId = requireDepartment(departmentId);
        int safeDuration = durationYears != null ? durationYears : 4;
        if (safeDuration < 1 || safeDuration > 12) {
            throw new IllegalArgumentException("Program duration must be between 1 and 12 years.");
        }
        String safeSchool = schoolName != null && !schoolName.isBlank()
            ? schoolName.trim()
            : "Emilio Aguinaldo College";
        int activeStatus = Boolean.FALSE.equals(active) ? 0 : 1;

        Integer existingId = findProgramIdByCode(normalizedCode);
        if (programId == null || programId <= 0) {
            if (existingId != null) {
                throw new IllegalStateException("A program with this code already exists.");
            }
            db.update(
                "INSERT INTO programs (program_code, program_name, department_id, school_name, duration_years, active_status) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                normalizedCode, programName.trim(), safeDepartmentId, safeSchool, safeDuration, activeStatus);
            Integer created = findProgramIdByCode(normalizedCode);
            if (created == null) {
                throw new IllegalStateException("Program was saved but could not be reopened.");
            }
            return created;
        }

        Map<String, Object> existing = requireProgram(programId);
        String storedCode = String.valueOf(existing.get("program_code"));
        if (!storedCode.equalsIgnoreCase(normalizedCode)) {
            throw new IllegalStateException("Program code cannot be changed after the program is created.");
        }
        if (existingId != null && existingId.intValue() != programId.intValue()) {
            throw new IllegalStateException("Another program already uses this code.");
        }

        int changed = db.update(
            "UPDATE programs SET program_name = ?, department_id = ?, school_name = ?, duration_years = ?, active_status = ? " +
                "WHERE program_id = ?",
            programName.trim(), safeDepartmentId, safeSchool, safeDuration, activeStatus, programId);
        if (changed == 0) {
            throw new IllegalArgumentException("Program was not found.");
        }
        return programId;
    }

    public Map<String, Object> usageDetails(int programId) {
        Map<String, Object> program = requireProgram(programId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("program", program);
        result.put("curricula", tableExists("curriculum_templates")
            ? db.queryForList(
                "SELECT curriculum_id, curriculum_name, academic_year, version_number, approval_status, " +
                    "COALESCE(is_active, 0) AS is_active, " + lifecycleSql(null) + " AS lifecycle_status " +
                    "FROM curriculum_templates WHERE program_id = ? " +
                    "ORDER BY CASE " + lifecycleSql(null) + " WHEN 'CURRENT' THEN 0 WHEN 'LEGACY' THEN 1 WHEN 'DRAFT' THEN 2 ELSE 3 END, " +
                    "academic_year DESC, curriculum_id DESC",
                programId)
            : List.of());
        result.put("assignments", tableExists("student_curriculum_assignments")
            ? db.queryForList(
                "SELECT student_number, assignment_type, reason, is_current, assigned_at " +
                    "FROM student_curriculum_assignments WHERE program_code = ? " +
                    "ORDER BY is_current DESC, assigned_at DESC LIMIT 100",
                program.get("program_code"))
            : List.of());
        return result;
    }

    @Transactional
    public void setActiveStatus(int programId, boolean active) {
        requireProgram(programId);
        int changed = db.update(
            "UPDATE programs SET active_status = ? WHERE program_id = ?",
            active ? 1 : 0,
            programId);
        if (changed == 0) {
            throw new IllegalArgumentException("Program was not found.");
        }
    }

    @Transactional
    public void deleteUnusedProgram(int programId) {
        int usage = usageCount(programId);
        if (usage > 0) {
            throw new IllegalStateException(
                "This program is already used by curricula or student assignments. Deactivate it instead of deleting it.");
        }
        int changed = db.update("DELETE FROM programs WHERE program_id = ?", programId);
        if (changed == 0) {
            throw new IllegalArgumentException("Program was not found.");
        }
    }

    private Map<String, Object> requireProgram(int programId) {
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT p.program_id, p.program_code, p.program_name, p.school_name, " +
                "COALESCE(p.duration_years, 0) AS duration_years, p.department_id, " +
                "COALESCE(p.active_status, 1) AS active_status, " +
                "COALESCE(d.department_name, 'Unassigned') AS department_name " +
                "FROM programs p LEFT JOIN departments d ON d.department_id = p.department_id " +
                "WHERE p.program_id = ?",
            programId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Program was not found.");
        }
        return rows.get(0);
    }

    private int usageCount(int programId) {
        Map<String, Object> program = requireProgram(programId);
        int count = 0;
        count += tableUsageCount("curriculum_templates", "program_id = ?", programId);
        if (tableExists("student_curriculum_assignments")) {
            count += tableUsageCount(
                "student_curriculum_assignments",
                "program_code = ?",
                program.get("program_code"));
        }
        return count;
    }

    private String usageSubquery(String table, String alias, String condition) {
        return "(SELECT COUNT(*) FROM " + table + " " + alias + " WHERE " + condition + ")";
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

    private int tableUsageCount(String table, String condition, Object... args) {
        if (!tableExists(table)) {
            return 0;
        }
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + condition,
            Integer.class,
            args);
        return count != null ? count : 0;
    }

    private boolean tableExists(String table) {
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE LOWER(table_schema) = LOWER(SCHEMA()) AND LOWER(table_name) = LOWER(?)",
            Integer.class,
            table);
        return count != null && count > 0;
    }

    private Integer findProgramIdByCode(String programCode) {
        try {
            return db.queryForObject(
                "SELECT program_id FROM programs WHERE UPPER(program_code) = UPPER(?) LIMIT 1",
                Integer.class,
                programCode);
        } catch (Exception e) {
            return null;
        }
    }

    private int requireDepartment(Integer departmentId) {
        if (departmentId == null || departmentId <= 0) {
            throw new IllegalArgumentException("Department is required.");
        }
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM departments WHERE department_id = ?",
            Integer.class,
            departmentId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Selected department was not found.");
        }
        return departmentId;
    }

    private String normalizeProgramCode(String programCode) {
        if (programCode == null || programCode.isBlank()) {
            return null;
        }
        return programCode.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "active";
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "all", "inactive" -> normalized;
            default -> "active";
        };
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }
}
