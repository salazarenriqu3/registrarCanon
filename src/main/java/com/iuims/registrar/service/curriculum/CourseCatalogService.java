package com.iuims.registrar.service.curriculum;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Department;

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

    public record CoursePage(List<Map<String, Object>> rows,
                             Map<String, Object> summary,
                             int page,
                             int pageSize,
                             int totalRows,
                             int totalPages) { }

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
        boolean hasCorequisites = tableExists("course_corequisites");
        boolean hasEquivalencies = tableExists("course_equivalencies");
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT c.course_id, c.course_code, c.course_title, c.credit_units, " +
                "CASE WHEN COALESCE(c.lec_units, 0) + COALESCE(c.lab_units, 0) = 0 THEN c.credit_units ELSE c.lec_units END AS lec_units, " +
                "COALESCE(c.lab_units, 0) AS lab_units, " +
                "COALESCE(c.component_type, 'SINGLE') AS component_type, COALESCE(c.course_family_code, c.course_code) AS course_family_code, " +
                "c.department_id, COALESCE(c.active_status, 1) AS active_status, " +
                "COALESCE(d.department_name, 'Unassigned') AS department_name, " +
                usageSubquery("curriculum_courses", "cc", "cc.course_id = c.course_id") + " AS curriculum_usage, " +
                usageSubquery("class_sections", "cs", "cs.course_id = c.course_id") + " AS section_usage, " +
                usageSubquery("student_enlistments", "se", "se.course_id = c.course_id") + " AS enlistment_usage, " +
                usageSubquery("grades", "g", "g.course_id = c.course_id") + " AS grade_usage, " +
                usageSubquery("course_prerequisites", "cp", "cp.course_id = c.course_id OR cp.prerequisite_course_id = c.course_id") + " AS prerequisite_usage, " +
                relationshipCodesSubquery("course_prerequisites", "prerequisite_course_id", "c.course_id") + " AS prerequisite_codes, " +
                (hasCorequisites
                    ? symmetricUsageSubquery("course_corequisites", "ccq", "corequisite_course_id", "c.course_id")
                    : "0") + " AS corequisite_usage, " +
                (hasCorequisites
                    ? relationshipCodesSubquery("course_corequisites", "corequisite_course_id", "c.course_id")
                    : "NULL") + " AS corequisite_codes, " +
                (hasEquivalencies
                    ? symmetricUsageSubquery("course_equivalencies", "ceq", "equivalent_course_id", "c.course_id")
                    : "0") + " AS equivalency_usage, " +
                (hasEquivalencies
                    ? relationshipCodesSubquery("course_equivalencies", "equivalent_course_id", "c.course_id")
                    : "NULL") + " AS equivalency_codes " +
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
            int corequisites = intValue(row.get("corequisite_usage"));
            int equivalencies = intValue(row.get("equivalency_usage"));
            row.put("usage_count", curriculum + sections + enlistments + grades + prerequisites + corequisites + equivalencies);
        }
        return rows;
    }

    public CoursePage listCoursesPage(String search, Integer departmentId, String status, int requestedPage, int requestedSize) {
        int pageSize = Math.min(100, Math.max(1, requestedSize));
        int page = Math.max(1, requestedPage);
        String normalizedStatus = normalizeStatus(status);
        String query = search != null ? search.trim().toUpperCase(Locale.ROOT) : "";
        boolean hasCorequisites = tableExists("course_corequisites");
        boolean hasEquivalencies = tableExists("course_equivalencies");
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        if (!query.isBlank()) {
            where.append("AND (UPPER(c.course_code) LIKE ? OR UPPER(c.course_title) LIKE ?) ");
            args.add("%" + query + "%");
            args.add("%" + query + "%");
        }
        if (departmentId != null && departmentId > 0) {
            where.append("AND c.department_id = ? ");
            args.add(departmentId);
        }
        if ("active".equals(normalizedStatus)) {
            where.append("AND COALESCE(c.active_status, 1) = 1 ");
        } else if ("inactive".equals(normalizedStatus)) {
            where.append("AND COALESCE(c.active_status, 1) = 0 ");
        }

        String from = " FROM courses c LEFT JOIN departments d ON d.department_id = c.department_id ";
        Integer total = db.queryForObject("SELECT COUNT(*)" + from + where, Integer.class, args.toArray());
        int totalRows = total != null ? total : 0;
        int totalPages = Math.max(1, (int) Math.ceil(totalRows / (double) pageSize));
        page = Math.min(page, totalPages);
        String select = "SELECT c.course_id, c.course_code, c.course_title, c.credit_units, " +
            "CASE WHEN COALESCE(c.lec_units, 0) + COALESCE(c.lab_units, 0) = 0 THEN c.credit_units ELSE c.lec_units END AS lec_units, " +
            "COALESCE(c.lab_units, 0) AS lab_units, COALESCE(c.component_type, 'SINGLE') AS component_type, " +
            "COALESCE(c.course_family_code, c.course_code) AS course_family_code, c.department_id, " +
            "COALESCE(c.active_status, 1) AS active_status, COALESCE(d.department_name, 'Unassigned') AS department_name, " +
            usageSubquery("curriculum_courses", "cc", "cc.course_id = c.course_id") + " AS curriculum_usage, " +
            usageSubquery("class_sections", "cs", "cs.course_id = c.course_id") + " AS section_usage, " +
            usageSubquery("student_enlistments", "se", "se.course_id = c.course_id") + " AS enlistment_usage, " +
            usageSubquery("grades", "g", "g.course_id = c.course_id") + " AS grade_usage, " +
            usageSubquery("course_prerequisites", "cp", "cp.course_id = c.course_id OR cp.prerequisite_course_id = c.course_id") + " AS prerequisite_usage, " +
            (hasCorequisites ? symmetricUsageSubquery("course_corequisites", "ccq", "corequisite_course_id", "c.course_id") : "0") + " AS corequisite_usage, " +
            (hasEquivalencies ? symmetricUsageSubquery("course_equivalencies", "ceq", "equivalent_course_id", "c.course_id") : "0") + " AS equivalency_usage ";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((page - 1) * pageSize);
        List<Map<String, Object>> rows = db.queryForList(
            select + from + where + " ORDER BY d.department_name, c.course_code LIMIT ? OFFSET ?",
            pageArgs.toArray());
        for (Map<String, Object> row : rows) {
            int usage = intValue(row.get("curriculum_usage")) + intValue(row.get("section_usage")) +
                intValue(row.get("enlistment_usage")) + intValue(row.get("grade_usage")) +
                intValue(row.get("prerequisite_usage")) + intValue(row.get("corequisite_usage")) +
                intValue(row.get("equivalency_usage"));
            row.put("usage_count", usage);
        }

        String usedCondition = " AND (EXISTS (SELECT 1 FROM curriculum_courses cc WHERE cc.course_id = c.course_id) " +
            "OR EXISTS (SELECT 1 FROM class_sections cs WHERE cs.course_id = c.course_id) " +
            "OR EXISTS (SELECT 1 FROM student_enlistments se WHERE se.course_id = c.course_id) " +
            "OR EXISTS (SELECT 1 FROM grades g WHERE g.course_id = c.course_id) " +
            "OR EXISTS (SELECT 1 FROM course_prerequisites cp WHERE cp.course_id = c.course_id OR cp.prerequisite_course_id = c.course_id)" +
            (hasCorequisites ? " OR EXISTS (SELECT 1 FROM course_corequisites ccq WHERE ccq.course_id = c.course_id OR ccq.corequisite_course_id = c.course_id)" : "") +
            (hasEquivalencies ? " OR EXISTS (SELECT 1 FROM course_equivalencies ceq WHERE ceq.course_id = c.course_id OR ceq.equivalent_course_id = c.course_id)" : "") + ")";
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", totalRows);
        summary.put("active", db.queryForObject("SELECT COUNT(*)" + from + where + " AND COALESCE(c.active_status, 1) = 1", Integer.class, args.toArray()));
        summary.put("inactive", db.queryForObject("SELECT COUNT(*)" + from + where + " AND COALESCE(c.active_status, 1) = 0", Integer.class, args.toArray()));
        summary.put("used", db.queryForObject("SELECT COUNT(*)" + from + where + usedCondition, Integer.class, args.toArray()));
        return new CoursePage(rows, summary, page, pageSize, totalRows, totalPages);
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
        String familyCode = stripComponentSuffix(normalizedCode);
        boolean splitComponents = safeLectureUnits > 0 && safeLaboratoryUnits > 0;

        if (splitComponents) {
            return saveSplitCourse(courseId, normalizedCode, courseTitle.trim(), safeDepartmentId,
                safeLectureUnits, safeLaboratoryUnits, activeStatus, familyCode);
        }

        Integer existingId = findCourseIdByCode(normalizedCode);
        if (courseId == null || courseId <= 0) {
            if (existingId != null) {
                throw new IllegalStateException("A course with this code already exists.");
            }
            db.update(
                "INSERT INTO courses (course_code, course_title, department_id, credit_units, lec_units, lab_units, component_type, course_family_code, active_status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                normalizedCode, courseTitle.trim(), safeDepartmentId, safeUnits, safeLectureUnits, safeLaboratoryUnits,
                componentType(safeLectureUnits, safeLaboratoryUnits), familyCode, activeStatus);
            Integer created = findCourseIdByCode(normalizedCode);
            if (created == null) {
                throw new IllegalStateException("Course was saved but could not be reopened.");
            }
            ensurePairedComponentCorequisite(created);
            return created;
        }

        if (existingId != null && existingId.intValue() != courseId.intValue()) {
            throw new IllegalStateException("Another course already uses this code.");
        }
        int changed = db.update(
            "UPDATE courses SET course_code = ?, course_title = ?, department_id = ?, credit_units = ?, lec_units = ?, lab_units = ?, component_type = ?, course_family_code = ?, parent_course_id = NULL, active_status = ? " +
                "WHERE course_id = ?",
            normalizedCode, courseTitle.trim(), safeDepartmentId, safeUnits, safeLectureUnits, safeLaboratoryUnits,
            componentType(safeLectureUnits, safeLaboratoryUnits), familyCode, activeStatus, courseId);
        if (changed == 0) {
            throw new IllegalArgumentException("Course was not found.");
        }
        ensurePairedComponentCorequisite(courseId);
        return courseId;
    }

    public Map<String, Object> relationshipEditorDetails(int courseId) {
        List<Map<String, Object>> courses = db.queryForList(
            "SELECT c.course_id, c.course_code, c.course_title, c.credit_units, " +
                "CASE WHEN COALESCE(c.lec_units, 0) + COALESCE(c.lab_units, 0) = 0 THEN c.credit_units ELSE c.lec_units END AS lec_units, " +
                "COALESCE(c.lab_units, 0) AS lab_units, COALESCE(c.active_status, 1) AS active_status, " +
                "COALESCE(c.component_type, 'SINGLE') AS component_type, COALESCE(c.course_family_code, c.course_code) AS course_family_code, " +
                "c.department_id, COALESCE(d.department_name, 'Unassigned') AS department_name " +
                "FROM courses c LEFT JOIN departments d ON d.department_id = c.department_id WHERE c.course_id = ?",
            courseId);
        if (courses.isEmpty()) {
            throw new IllegalArgumentException("Course was not found.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("course", courses.get(0));
        result.put("prerequisites", loadDirectedRelationships("course_prerequisites", "prerequisite_course_id", courseId));
        result.put("corequisites", loadSymmetricRelationships("course_corequisites", "corequisite_course_id", courseId));
        result.put("equivalencies", loadSymmetricRelationships("course_equivalencies", "equivalent_course_id", courseId));
        return result;
    }

    public List<Map<String, Object>> searchRelationshipCourses(String query, Integer departmentId) {
        String text = query != null ? query.trim() : "";
        String normalized = text.toUpperCase(Locale.ROOT);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT c.course_id, c.course_code, c.course_title, c.credit_units, " +
                "CASE WHEN COALESCE(c.lec_units, 0) + COALESCE(c.lab_units, 0) = 0 THEN c.credit_units ELSE c.lec_units END AS lec_units, " +
                "COALESCE(c.lab_units, 0) AS lab_units, COALESCE(c.active_status, 1) AS active_status, " +
                "COALESCE(c.component_type, 'SINGLE') AS component_type, COALESCE(c.course_family_code, c.course_code) AS course_family_code, " +
                "c.department_id, COALESCE(d.department_name, 'Unassigned') AS department_name " +
                "FROM courses c LEFT JOIN departments d ON d.department_id = c.department_id WHERE 1 = 1 ");

        if (departmentId != null && departmentId > 0) {
            sql.append("AND c.department_id = ? ");
            args.add(departmentId);
        }
        if (!normalized.isBlank()) {
            sql.append("AND (UPPER(c.course_code) LIKE ? OR UPPER(c.course_title) LIKE ?) ");
            args.add("%" + normalized + "%");
            args.add("%" + normalized + "%");
        }

        sql.append("ORDER BY COALESCE(c.active_status, 1) DESC, ");
        if (!normalized.isBlank()) {
            sql.append("CASE WHEN UPPER(c.course_code) = ? THEN 0 WHEN UPPER(c.course_code) LIKE ? THEN 1 ELSE 2 END, ");
            args.add(normalized);
            args.add(normalized + "%");
        }
        sql.append("d.department_name, c.course_code LIMIT 40");
        return db.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public void saveCourseRelationships(int courseId,
                                        List<Integer> prerequisiteCourseIds,
                                        List<Integer> corequisiteCourseIds,
                                        List<Integer> equivalencyCourseIds) {
        requireCourseExists(courseId);
        List<Integer> ownerIds = relationshipOwnerIds(courseId);
        List<Integer> prerequisites = normalizeRelationshipIds(prerequisiteCourseIds, ownerIds);
        List<Integer> corequisites = normalizeRelationshipIds(corequisiteCourseIds, ownerIds);
        List<Integer> equivalencies = normalizeRelationshipIds(equivalencyCourseIds, ownerIds);

        for (Integer ownerId : ownerIds) {
            syncDirectedRelationshipIds("course_prerequisites", "prerequisite_course_id", ownerId, prerequisites);
        }
        for (Integer ownerId : ownerIds) {
            syncSymmetricRelationshipIds("course_corequisites", "corequisite_course_id", ownerId, corequisites);
        }
        for (Integer ownerId : ownerIds) {
            syncSymmetricRelationshipIds("course_equivalencies", "equivalent_course_id", ownerId, equivalencies);
        }
        for (Integer ownerId : ownerIds) {
            ensurePairedComponentCorequisite(ownerId);
        }
    }

    public Map<String, Object> usageDetails(int courseId) {
        List<Map<String, Object>> courses = db.queryForList(
            "SELECT c.course_id, c.course_code, c.course_title, c.credit_units, " +
                "CASE WHEN COALESCE(c.lec_units, 0) + COALESCE(c.lab_units, 0) = 0 THEN c.credit_units ELSE c.lec_units END AS lec_units, " +
                "COALESCE(c.lab_units, 0) AS lab_units, " +
                "COALESCE(c.component_type, 'SINGLE') AS component_type, COALESCE(c.course_family_code, c.course_code) AS course_family_code, " +
                "COALESCE(d.department_name, 'Unassigned') AS department_name " +
                "FROM courses c LEFT JOIN departments d ON d.department_id = c.department_id WHERE c.course_id = ?",
            courseId);
        if (courses.isEmpty()) {
            throw new IllegalArgumentException("Course was not found.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("course", courses.get(0));
        String curriculumLifecycleSql = lifecycleSql("ct");
        result.put("curricula", tableExists("curriculum_courses")
            ? db.queryForList(
                "SELECT ct.curriculum_id, p.program_code, p.program_name, ct.curriculum_name, ct.academic_year, " +
                    "ct.approval_status, COALESCE(ct.is_active, 0) AS is_active, " +
                    curriculumLifecycleSql + " AS lifecycle_status, cc.year_level, cc.semester_number " +
                    "FROM curriculum_courses cc JOIN curriculum_templates ct ON ct.curriculum_id = cc.curriculum_id " +
                    "JOIN programs p ON p.program_id = ct.program_id WHERE cc.course_id = ? " +
                    "ORDER BY CASE " + curriculumLifecycleSql + " WHEN 'CURRENT' THEN 0 WHEN 'LEGACY' THEN 1 WHEN 'DRAFT' THEN 2 ELSE 3 END, " +
                    "p.program_code, ct.academic_year DESC",
                courseId)
            : List.of());
        result.put("sections", tableExists("class_sections")
            ? db.queryForList(
                "SELECT cs.section_id, cs.section_code, cs.term_id, cs.semester_number, cs.section_status, cs.faculty_id, " +
                    "COALESCE(at.term_name, CONCAT('Term #', cs.term_id)) AS term_label, " +
                    "CONCAT(COALESCE(f.first_name,''),' ',COALESCE(f.last_name,'')) AS faculty_name, " +
                    "(SELECT COUNT(*) FROM class_schedules sch WHERE sch.section_id = cs.section_id) AS schedule_slot_count " +
                    "FROM class_sections cs " +
                    "LEFT JOIN academic_terms at ON at.term_id = cs.term_id " +
                    "LEFT JOIN faculty f ON f.faculty_id = cs.faculty_id " +
                    "WHERE cs.course_id = ? ORDER BY cs.term_id DESC, cs.section_code LIMIT 100",
                courseId)
            : List.of());

        Map<String, Object> records = new LinkedHashMap<>();
        records.put("enlistments", tableUsageCount("student_enlistments", "course_id = ?", courseId));
        records.put("grades", tableUsageCount("grades", "course_id = ?", courseId));
        records.put("waitlists", tableUsageCountAny(List.of("student_waitlist", "waitlists"), "course_id = ?", courseId));
        records.put("requests", tableUsageCountAny(List.of("subject_requests", "student_requests"), "course_id = ?", courseId));
        result.put("records", records);
        result.put("prerequisites", tableExists("course_prerequisites")
            ? db.queryForList(
                "SELECT c.course_code, c.course_title, pc.course_code AS prerequisite_code, pc.course_title AS prerequisite_title " +
                    "FROM course_prerequisites cp JOIN courses c ON c.course_id = cp.course_id " +
                    "JOIN courses pc ON pc.course_id = cp.prerequisite_course_id " +
                    "WHERE cp.course_id = ? OR cp.prerequisite_course_id = ? ORDER BY c.course_code, pc.course_code",
                courseId, courseId)
            : List.of());
        result.put("corequisites", tableExists("course_corequisites")
            ? db.queryForList(
                "SELECT c.course_code, c.course_title, qc.course_code AS corequisite_code, qc.course_title AS corequisite_title " +
                    "FROM course_corequisites ccq JOIN courses c ON c.course_id = ccq.course_id " +
                    "JOIN courses qc ON qc.course_id = ccq.corequisite_course_id " +
                    "WHERE ccq.course_id = ? OR ccq.corequisite_course_id = ? ORDER BY c.course_code, qc.course_code",
                courseId, courseId)
            : List.of());
        result.put("equivalencies", tableExists("course_equivalencies")
            ? db.queryForList(
                "SELECT c.course_code, c.course_title, ec.course_code AS equivalent_code, ec.course_title AS equivalent_title " +
                    "FROM course_equivalencies ceq JOIN courses c ON c.course_id = ceq.course_id " +
                    "JOIN courses ec ON ec.course_id = ceq.equivalent_course_id " +
                    "WHERE ceq.course_id = ? OR ceq.equivalent_course_id = ? ORDER BY c.course_code, ec.course_code",
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
        count += tableUsageCountAny(List.of("student_waitlist", "waitlists"), "course_id = ?", courseId);
        count += tableUsageCountAny(List.of("subject_requests", "student_requests"), "course_id = ?", courseId);
        count += tableUsageCount("course_prerequisites", "course_id = ? OR prerequisite_course_id = ?", courseId, courseId);
        count += tableUsageCount("course_corequisites", "course_id = ? OR corequisite_course_id = ?", courseId, courseId);
        count += tableUsageCount("course_equivalencies", "course_id = ? OR equivalent_course_id = ?", courseId, courseId);
        return count;
    }

    private Integer saveSplitCourse(Integer courseId,
                                    String submittedCode,
                                    String courseTitle,
                                    int departmentId,
                                    int lectureUnits,
                                    int laboratoryUnits,
                                    int activeStatus,
                                    String familyCode) {
        String lecCode = componentCode(familyCode, "LEC");
        String labCode = componentCode(familyCode, "LAB");
        Integer submittedId = findCourseIdByCode(submittedCode);
        Integer existingLecId = findCourseIdByCode(lecCode);
        Integer existingLabId = findCourseIdByCode(labCode);

        if (courseId == null || courseId <= 0) {
            if (submittedId != null || existingLecId != null || existingLabId != null) {
                throw new IllegalStateException("A course with this code or its LEC/LAB component code already exists.");
            }
            Integer lecId = insertCourseComponent(lecCode, courseTitle, departmentId, lectureUnits, lectureUnits, 0, "LEC", familyCode, activeStatus);
            Integer labId = insertCourseComponent(labCode, courseTitle, departmentId, laboratoryUnits, 0, laboratoryUnits, "LAB", familyCode, activeStatus);
            linkComponentFamily(lecId, labId);
            ensureComponentPairCorequisite(lecId, labId);
            return lecId;
        }

        if (usageCount(courseId) > 0) {
            throw new IllegalStateException("This mixed lecture/lab course is already used. Create separate LEC/LAB catalog entries and migrate placements intentionally.");
        }
        if (existingLecId != null && existingLecId.intValue() != courseId.intValue()) {
            throw new IllegalStateException("Another course already uses the LEC component code.");
        }
        if (existingLabId != null) {
            throw new IllegalStateException("Another course already uses the LAB component code.");
        }

        int changed = db.update(
            "UPDATE courses SET course_code = ?, course_title = ?, department_id = ?, credit_units = ?, lec_units = ?, lab_units = 0, component_type = 'LEC', course_family_code = ?, parent_course_id = NULL, active_status = ? " +
                "WHERE course_id = ?",
            lecCode, courseTitle, departmentId, lectureUnits, lectureUnits, familyCode, activeStatus, courseId);
        if (changed == 0) {
            throw new IllegalArgumentException("Course was not found.");
        }
        Integer labId = insertCourseComponent(labCode, courseTitle, departmentId, laboratoryUnits, 0, laboratoryUnits, "LAB", familyCode, activeStatus);
        linkComponentFamily(courseId, labId);
        ensureComponentPairCorequisite(courseId, labId);
        return courseId;
    }

    private Integer insertCourseComponent(String courseCode,
                                          String courseTitle,
                                          int departmentId,
                                          int creditUnits,
                                          int lectureUnits,
                                          int laboratoryUnits,
                                          String componentType,
                                          String familyCode,
                                          int activeStatus) {
        db.update(
            "INSERT INTO courses (course_code, course_title, department_id, credit_units, lec_units, lab_units, component_type, course_family_code, active_status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            courseCode, courseTitle, departmentId, creditUnits, lectureUnits, laboratoryUnits, componentType, familyCode, activeStatus);
        Integer created = findCourseIdByCode(courseCode);
        if (created == null) {
            throw new IllegalStateException("Course component was saved but could not be reopened.");
        }
        return created;
    }

    private void linkComponentFamily(Integer lecId, Integer labId) {
        if (lecId == null || labId == null) {
            return;
        }
        db.update("UPDATE courses SET parent_course_id = ? WHERE course_id IN (?, ?)", lecId, lecId, labId);
    }

    private String componentCode(String baseCode, String component) {
        String base = stripComponentSuffix(baseCode);
        return base + "-" + component;
    }

    private String stripComponentSuffix(String courseCode) {
        String normalized = normalizeCourseCode(courseCode);
        if (normalized == null) {
            return null;
        }
        return normalized
            .replaceFirst("[-\\s]+LEC$", "")
            .replaceFirst("[-\\s]+LAB$", "")
            .trim();
    }

    private String componentType(int lectureUnits, int laboratoryUnits) {
        if (lectureUnits > 0 && laboratoryUnits == 0) {
            return "LEC";
        }
        if (laboratoryUnits > 0 && lectureUnits == 0) {
            return "LAB";
        }
        return "SINGLE";
    }

    private String usageSubquery(String table, String alias, String condition) {
        return "(SELECT COUNT(*) FROM " + table + " " + alias + " WHERE " + condition + ")";
    }

    private String relationshipCodesSubquery(String table, String relationshipColumn, String ownerColumnExpression) {
        return "(SELECT GROUP_CONCAT(rc.course_code ORDER BY rc.course_code SEPARATOR ', ') " +
            "FROM " + table + " rel " +
            "JOIN courses rc ON rc.course_id = rel." + relationshipColumn + " " +
            "WHERE rel.course_id = " + ownerColumnExpression + ")";
    }

    private String symmetricUsageSubquery(String table, String alias, String relationshipColumn, String ownerColumnExpression) {
        return "(SELECT COUNT(DISTINCT CASE WHEN " + alias + ".course_id = " + ownerColumnExpression
            + " THEN " + alias + "." + relationshipColumn + " ELSE " + alias + ".course_id END) "
            + "FROM " + table + " " + alias + " WHERE " + alias + ".course_id = " + ownerColumnExpression
            + " OR " + alias + "." + relationshipColumn + " = " + ownerColumnExpression + ")";
    }

    private String lifecycleSql(String alias) {
        String prefix = alias == null || alias.isBlank() ? "" : alias + ".";
        String derivedLifecycle = "CASE " +
            "WHEN UPPER(COALESCE(" + prefix + "approval_status,'')) IN ('ARCHIVED','RETIRED') THEN 'ARCHIVED' " +
            "WHEN UPPER(COALESCE(" + prefix + "approval_status,'')) IN ('DRAFT','PLACEHOLDER') AND COALESCE(" + prefix + "is_active, 0) = 0 THEN 'DRAFT' " +
            "WHEN COALESCE(" + prefix + "is_active, 0) = 1 THEN 'CURRENT' " +
            "ELSE 'LEGACY' END";
        if (!columnExists("curriculum_templates", "lifecycle_status")) {
            return "UPPER(" + derivedLifecycle + ")";
        }
        return "UPPER(COALESCE(NULLIF(" + prefix + "lifecycle_status, ''), " + derivedLifecycle + "))";
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

    private int tableUsageCountAny(List<String> tables, String condition, Object... args) {
        for (String table : tables) {
            if (tableExists(table)) {
                return tableUsageCount(table, condition, args);
            }
        }
        return 0;
    }

    private boolean tableExists(String table) {
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE LOWER(TABLE_SCHEMA) = LOWER(SCHEMA()) AND LOWER(TABLE_NAME) = LOWER(?)",
            Integer.class,
            table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE LOWER(TABLE_SCHEMA) = LOWER(SCHEMA()) AND LOWER(TABLE_NAME) = LOWER(?) " +
                "AND LOWER(COLUMN_NAME) = LOWER(?)",
            Integer.class,
            table,
            column);
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

    private void requireCourseExists(int courseId) {
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM courses WHERE course_id = ?",
            Integer.class,
            courseId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Course was not found.");
        }
    }

    private void syncRelationships(Integer courseId,
                                   String prerequisiteCodes,
                                   String corequisiteCodes,
                                   String equivalencyCodes) {
        if (courseId == null || courseId <= 0) {
            return;
        }
        syncDirectedRelationship("course_prerequisites", "prerequisite_course_id", courseId, prerequisiteCodes);
        syncSymmetricRelationship("course_corequisites", "corequisite_course_id", courseId, corequisiteCodes);
        syncSymmetricRelationship("course_equivalencies", "equivalent_course_id", courseId, equivalencyCodes);
    }

    private void syncDirectedRelationship(String table, String targetColumn, int courseId, String codes) {
        if (!tableExists(table)) {
            return;
        }
        db.update("DELETE FROM " + table + " WHERE course_id = ?", courseId);
        for (Integer relatedCourseId : resolveRelationshipCourseIds(codes, courseId)) {
            db.update(
                "INSERT INTO " + table + " (course_id, " + targetColumn + ") VALUES (?, ?)",
                courseId,
                relatedCourseId);
        }
    }

    private void syncSymmetricRelationship(String table, String targetColumn, int courseId, String codes) {
        if (!tableExists(table)) {
            return;
        }
        db.update("DELETE FROM " + table + " WHERE course_id = ? OR " + targetColumn + " = ?", courseId, courseId);
        for (Integer relatedCourseId : resolveRelationshipCourseIds(codes, courseId)) {
            db.update(
                "INSERT INTO " + table + " (course_id, " + targetColumn + ") VALUES (?, ?)",
                courseId,
                relatedCourseId);
            db.update(
                "INSERT INTO " + table + " (course_id, " + targetColumn + ") VALUES (?, ?)",
                relatedCourseId,
                courseId);
        }
    }

    private List<Integer> resolveRelationshipCourseIds(String codes, int courseId) {
        List<Integer> relatedIds = new ArrayList<>();
        if (codes == null || codes.isBlank()) {
            return relatedIds;
        }
        for (String rawCode : codes.split("[,;\\n\\r]+")) {
            String normalized = normalizeCourseCode(rawCode);
            if (normalized == null) {
                continue;
            }
            Integer relatedCourseId = findCourseIdByCode(normalized);
            if (relatedCourseId == null) {
                throw new IllegalArgumentException("Related course was not found: " + normalized);
            }
            if (relatedCourseId == courseId || relatedIds.contains(relatedCourseId)) {
                continue;
            }
            relatedIds.add(relatedCourseId);
        }
        return relatedIds;
    }

    private List<Map<String, Object>> loadDirectedRelationships(String table, String targetColumn, int courseId) {
        if (!tableExists(table)) {
            return List.of();
        }
        return db.queryForList(
            "SELECT DISTINCT rc.course_id, rc.course_code, rc.course_title, COALESCE(rc.active_status, 1) AS active_status, " +
                "COALESCE(rc.component_type, 'SINGLE') AS component_type, COALESCE(rc.course_family_code, rc.course_code) AS course_family_code, " +
                "COALESCE(d.department_name, 'Unassigned') AS department_name " +
                "FROM " + table + " rel " +
                "JOIN courses rc ON rc.course_id = rel." + targetColumn + " " +
                "LEFT JOIN departments d ON d.department_id = rc.department_id " +
                "WHERE rel.course_id = ? ORDER BY rc.course_code",
            courseId);
    }

    private List<Map<String, Object>> loadSymmetricRelationships(String table, String targetColumn, int courseId) {
        if (!tableExists(table)) {
            return List.of();
        }
        return db.queryForList(
            "SELECT DISTINCT rc.course_id, rc.course_code, rc.course_title, COALESCE(rc.active_status, 1) AS active_status, " +
                "COALESCE(rc.component_type, 'SINGLE') AS component_type, COALESCE(rc.course_family_code, rc.course_code) AS course_family_code, " +
                "COALESCE(d.department_name, 'Unassigned') AS department_name " +
                "FROM " + table + " rel " +
                "JOIN courses rc ON rc.course_id = CASE WHEN rel.course_id = ? THEN rel." + targetColumn + " ELSE rel.course_id END " +
                "LEFT JOIN departments d ON d.department_id = rc.department_id " +
                "WHERE rel.course_id = ? OR rel." + targetColumn + " = ? ORDER BY rc.course_code",
            courseId, courseId, courseId);
    }

    private List<Integer> relationshipOwnerIds(int courseId) {
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT course_id FROM courses " +
                "WHERE course_id = ? " +
                "OR (COALESCE(component_type, 'SINGLE') IN ('LEC', 'LAB') AND parent_course_id = (SELECT COALESCE(parent_course_id, course_id) FROM courses WHERE course_id = ?) " +
                "AND COALESCE(component_type, 'SINGLE') IN ('LEC', 'LAB')) " +
                "ORDER BY course_id",
            courseId, courseId);
        List<Integer> ownerIds = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Integer ownerId = intValue(row.get("course_id"));
            if (ownerId != null && !ownerIds.contains(ownerId)) {
                ownerIds.add(ownerId);
            }
        }
        if (ownerIds.isEmpty()) {
            ownerIds.add(courseId);
        }
        return ownerIds;
    }

    private List<Integer> normalizeRelationshipIds(List<Integer> selectedIds, List<Integer> ownerIds) {
        List<Integer> normalizedIds = new ArrayList<>();
        if (selectedIds == null) {
            return normalizedIds;
        }
        for (Integer selectedId : selectedIds) {
            if (selectedId == null || selectedId <= 0 || ownerIds.contains(selectedId) || normalizedIds.contains(selectedId)) {
                continue;
            }
            requireCourseExists(selectedId);
            normalizedIds.add(selectedId);
        }
        return normalizedIds;
    }

    private void syncDirectedRelationshipIds(String table, String targetColumn, int courseId, List<Integer> relatedCourseIds) {
        if (!tableExists(table)) {
            return;
        }
        db.update("DELETE FROM " + table + " WHERE course_id = ?", courseId);
        for (Integer relatedCourseId : relatedCourseIds) {
            db.update(
                "INSERT INTO " + table + " (course_id, " + targetColumn + ") VALUES (?, ?)",
                courseId,
                relatedCourseId);
        }
    }

    private void syncSymmetricRelationshipIds(String table, String targetColumn, int courseId, List<Integer> relatedCourseIds) {
        if (!tableExists(table)) {
            return;
        }
        db.update("DELETE FROM " + table + " WHERE course_id = ? OR " + targetColumn + " = ?", courseId, courseId);
        for (Integer relatedCourseId : relatedCourseIds) {
            db.update(
                "INSERT INTO " + table + " (course_id, " + targetColumn + ") VALUES (?, ?)",
                courseId,
                relatedCourseId);
            db.update(
                "INSERT INTO " + table + " (course_id, " + targetColumn + ") VALUES (?, ?)",
                relatedCourseId,
                courseId);
        }
    }

    private void ensurePairedComponentCorequisite(int courseId) {
        if (!tableExists("course_corequisites")) {
            return;
        }
        List<Map<String, Object>> siblings = db.queryForList(
            "SELECT course_id FROM courses " +
                "WHERE course_id <> ? AND parent_course_id = (SELECT COALESCE(parent_course_id, course_id) FROM courses WHERE course_id = ?) " +
                "AND COALESCE(component_type, 'SINGLE') IN ('LEC', 'LAB')",
            courseId, courseId);
        for (Map<String, Object> sibling : siblings) {
            int siblingId = intValue(sibling.get("course_id"));
            ensureComponentPairCorequisite(courseId, siblingId);
        }
    }

    private void ensureComponentPairCorequisite(Integer firstCourseId, Integer secondCourseId) {
        if (firstCourseId == null || secondCourseId == null || firstCourseId.equals(secondCourseId) || !tableExists("course_corequisites")) {
            return;
        }
        db.update(
            "INSERT INTO course_corequisites (course_id, corequisite_course_id) " +
                "SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM course_corequisites WHERE course_id = ? AND corequisite_course_id = ?)",
            firstCourseId, secondCourseId, firstCourseId, secondCourseId);
        db.update(
            "INSERT INTO course_corequisites (course_id, corequisite_course_id) " +
                "SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM course_corequisites WHERE course_id = ? AND corequisite_course_id = ?)",
            secondCourseId, firstCourseId, secondCourseId, firstCourseId);
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
