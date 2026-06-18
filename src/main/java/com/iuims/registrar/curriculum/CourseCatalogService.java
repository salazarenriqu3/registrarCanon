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
public class CourseCatalogService {

    @Autowired
    private JdbcTemplate db;

    public List<Map<String, Object>> listDepartments() {
        return db.queryForList(
            "SELECT department_id, department_code, department_name " +
                "FROM departments ORDER BY department_name");
    }

    public List<Map<String, Object>> listCourses(String search, Integer departmentId, String status) {
        String normalizedStatus = normalizeStatus(status);
        String query = search != null ? search.trim().toUpperCase(Locale.ROOT) : "";
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT c.course_id, c.course_code, c.course_title, c.credit_units, " +
                "CASE WHEN COALESCE(c.lec_units, 0) + COALESCE(c.lab_units, 0) = 0 THEN c.credit_units ELSE c.lec_units END AS lec_units, " +
                "COALESCE(c.lab_units, 0) AS lab_units, " +
                "c.department_id, COALESCE(c.active_status, 1) AS active_status, " +
                "COALESCE(d.department_name, 'Unassigned') AS department_name, " +
                usageSubquery("curriculum_courses", "cc", "cc.course_id = c.course_id") + " AS curriculum_usage, " +
                usageSubquery("class_sections", "cs", "cs.course_id = c.course_id") + " AS section_usage, " +
                usageSubquery("student_enlistments", "se", "se.course_id = c.course_id") + " AS enlistment_usage, " +
                usageSubquery("grades", "g", "g.course_id = c.course_id") + " AS grade_usage, " +
                usageSubquery("course_prerequisites", "cp", "cp.course_id = c.course_id OR cp.prerequisite_course_id = c.course_id") + " AS prerequisite_usage " +
                "FROM courses c " +
                "LEFT JOIN departments d ON d.department_id = c.department_id " +
                "WHERE 1 = 1 ");

        if (!query.isBlank()) {
            sql.append("AND (UPPER(c.course_code) LIKE ? OR UPPER(c.course_title) LIKE ?) ");
            args.add("%" + query + "%");
            args.add("%" + query + "%");
        }
        if (departmentId != null && departmentId > 0) {
            sql.append("AND c.department_id = ? ");
            args.add(departmentId);
        }
        if ("active".equals(normalizedStatus)) {
            sql.append("AND COALESCE(c.active_status, 1) = 1 ");
        } else if ("inactive".equals(normalizedStatus)) {
            sql.append("AND COALESCE(c.active_status, 1) = 0 ");
        }

        sql.append("ORDER BY d.department_name, c.course_code");
        List<Map<String, Object>> rows = db.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> row : rows) {
            int curriculum = intValue(row.get("curriculum_usage"));
            int sections = intValue(row.get("section_usage"));
            int enlistments = intValue(row.get("enlistment_usage"));
            int grades = intValue(row.get("grade_usage"));
            int prerequisites = intValue(row.get("prerequisite_usage"));
            row.put("usage_count", curriculum + sections + enlistments + grades + prerequisites);
        }
        return rows;
    }

    public Map<String, Object> summary(String search, Integer departmentId, String status) {
        List<Map<String, Object>> courses = listCourses(search, departmentId, status);
        int active = 0;
        int inactive = 0;
        int used = 0;
        for (Map<String, Object> course : courses) {
            if (intValue(course.get("active_status")) == 1) {
                active++;
            } else {
                inactive++;
            }
            if (intValue(course.get("usage_count")) > 0) {
                used++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", courses.size());
        result.put("active", active);
        result.put("inactive", inactive);
        result.put("used", used);
        return result;
    }

    @Transactional
    public Integer saveCourse(Integer courseId,
                              String courseCode,
                              String courseTitle,
                              Integer departmentId,
                              Integer lectureUnits,
                              Integer laboratoryUnits,
                              Boolean active) {
        String normalizedCode = normalizeCourseCode(courseCode);
        if (normalizedCode == null) {
            throw new IllegalArgumentException("Course code is required.");
        }
        if (courseTitle == null || courseTitle.isBlank()) {
            throw new IllegalArgumentException("Course title is required.");
        }
        int safeDepartmentId = requireDepartment(departmentId);
        int safeLectureUnits = lectureUnits != null ? lectureUnits : 3;
        int safeLaboratoryUnits = laboratoryUnits != null ? laboratoryUnits : 0;
        if (safeLectureUnits < 0 || safeLaboratoryUnits < 0) {
            throw new IllegalArgumentException("Lecture and laboratory units cannot be negative.");
        }
        int safeUnits = safeLectureUnits + safeLaboratoryUnits;
        if (safeUnits <= 0 || safeUnits > 12) {
            throw new IllegalArgumentException("Total credit units must be between 1 and 12.");
        }
        int activeStatus = Boolean.FALSE.equals(active) ? 0 : 1;

        Integer existingId = findCourseIdByCode(normalizedCode);
        if (courseId == null || courseId <= 0) {
            if (existingId != null) {
                throw new IllegalStateException("A course with this code already exists.");
            }
            db.update(
                "INSERT INTO courses (course_code, course_title, department_id, credit_units, lec_units, lab_units, active_status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                normalizedCode, courseTitle.trim(), safeDepartmentId, safeUnits, safeLectureUnits, safeLaboratoryUnits, activeStatus);
            Integer created = findCourseIdByCode(normalizedCode);
            if (created == null) {
                throw new IllegalStateException("Course was saved but could not be reopened.");
            }
            return created;
        }

        if (existingId != null && existingId.intValue() != courseId.intValue()) {
            throw new IllegalStateException("Another course already uses this code.");
        }
        int changed = db.update(
            "UPDATE courses SET course_code = ?, course_title = ?, department_id = ?, credit_units = ?, lec_units = ?, lab_units = ?, active_status = ? " +
                "WHERE course_id = ?",
            normalizedCode, courseTitle.trim(), safeDepartmentId, safeUnits, safeLectureUnits, safeLaboratoryUnits, activeStatus, courseId);
        if (changed == 0) {
            throw new IllegalArgumentException("Course was not found.");
        }
        return courseId;
    }

    public Map<String, Object> usageDetails(int courseId) {
        List<Map<String, Object>> courses = db.queryForList(
            "SELECT c.course_id, c.course_code, c.course_title, c.credit_units, " +
                "CASE WHEN COALESCE(c.lec_units, 0) + COALESCE(c.lab_units, 0) = 0 THEN c.credit_units ELSE c.lec_units END AS lec_units, " +
                "COALESCE(c.lab_units, 0) AS lab_units, " +
                "COALESCE(d.department_name, 'Unassigned') AS department_name " +
                "FROM courses c LEFT JOIN departments d ON d.department_id = c.department_id WHERE c.course_id = ?",
            courseId);
        if (courses.isEmpty()) {
            throw new IllegalArgumentException("Course was not found.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("course", courses.get(0));
        result.put("curricula", tableExists("curriculum_courses")
            ? db.queryForList(
                "SELECT ct.curriculum_id, p.program_code, p.program_name, ct.curriculum_name, ct.academic_year, " +
                    "ct.approval_status, COALESCE(ct.is_active, 0) AS is_active, cc.year_level, cc.semester_number " +
                    "FROM curriculum_courses cc JOIN curriculum_templates ct ON ct.curriculum_id = cc.curriculum_id " +
                    "JOIN programs p ON p.program_id = ct.program_id WHERE cc.course_id = ? " +
                    "ORDER BY COALESCE(ct.is_active, 0) DESC, p.program_code, ct.academic_year DESC",
                courseId)
            : List.of());
        result.put("sections", tableExists("class_sections")
            ? db.queryForList(
                "SELECT section_id, section_code, term_id, semester_number, section_status, faculty_id " +
                    "FROM class_sections WHERE course_id = ? ORDER BY term_id DESC, section_code LIMIT 100",
                courseId)
            : List.of());

        Map<String, Object> records = new LinkedHashMap<>();
        records.put("enlistments", tableUsageCount("student_enlistments", "course_id = ?", courseId));
        records.put("grades", tableUsageCount("grades", "course_id = ?", courseId));
        records.put("waitlists", tableUsageCount("waitlists", "course_id = ?", courseId));
        records.put("requests", tableUsageCount("student_requests", "course_id = ?", courseId));
        result.put("records", records);
        result.put("prerequisites", tableExists("course_prerequisites")
            ? db.queryForList(
                "SELECT c.course_code, c.course_title, pc.course_code AS prerequisite_code, pc.course_title AS prerequisite_title " +
                    "FROM course_prerequisites cp JOIN courses c ON c.course_id = cp.course_id " +
                    "JOIN courses pc ON pc.course_id = cp.prerequisite_course_id " +
                    "WHERE cp.course_id = ? OR cp.prerequisite_course_id = ? ORDER BY c.course_code, pc.course_code",
                courseId, courseId)
            : List.of());
        return result;
    }

    @Transactional
    public void setActiveStatus(int courseId, boolean active) {
        int changed = db.update(
            "UPDATE courses SET active_status = ? WHERE course_id = ?",
            active ? 1 : 0,
            courseId);
        if (changed == 0) {
            throw new IllegalArgumentException("Course was not found.");
        }
    }

    @Transactional
    public void deleteUnusedCourse(int courseId) {
        int usage = usageCount(courseId);
        if (usage > 0) {
            throw new IllegalStateException("This course is already used. Deactivate it instead of deleting it.");
        }
        int changed = db.update("DELETE FROM courses WHERE course_id = ?", courseId);
        if (changed == 0) {
            throw new IllegalArgumentException("Course was not found.");
        }
    }

    private int usageCount(int courseId) {
        int count = 0;
        count += tableUsageCount("curriculum_courses", "course_id = ?", courseId);
        count += tableUsageCount("class_sections", "course_id = ?", courseId);
        count += tableUsageCount("student_enlistments", "course_id = ?", courseId);
        count += tableUsageCount("grades", "course_id = ?", courseId);
        count += tableUsageCount("waitlists", "course_id = ?", courseId);
        count += tableUsageCount("student_requests", "course_id = ?", courseId);
        count += tableUsageCount("course_prerequisites", "course_id = ? OR prerequisite_course_id = ?", courseId, courseId);
        return count;
    }

    private String usageSubquery(String table, String alias, String condition) {
        return "(SELECT COUNT(*) FROM " + table + " " + alias + " WHERE " + condition + ")";
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

    private Integer findCourseIdByCode(String courseCode) {
        try {
            return db.queryForObject(
                "SELECT course_id FROM courses WHERE UPPER(course_code) = UPPER(?) LIMIT 1",
                Integer.class,
                courseCode);
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

    private String normalizeCourseCode(String courseCode) {
        if (courseCode == null || courseCode.isBlank()) {
            return null;
        }
        return courseCode.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
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
