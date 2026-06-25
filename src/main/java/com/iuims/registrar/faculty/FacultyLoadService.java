package com.iuims.registrar.faculty;
import com.iuims.registrar.academic.AcademicGradingService;
import com.iuims.registrar.core.GradeOutcomeSql;
import com.iuims.registrar.admission.ApplicantStatusSyncService;
import com.iuims.registrar.admission.FinanceAdmissionService;
import com.iuims.registrar.curriculum.CurriculumSeederService;
import com.iuims.registrar.curriculum.StudentCurriculumService;
import com.iuims.registrar.core.EnlistmentSchemaService;
import com.iuims.registrar.faculty.FacultyLoadService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.finance.TermFeeAdminService;
import com.iuims.registrar.core.DatabaseSetupService;
import com.iuims.registrar.jaypee.JaypeeIntegrationService;
import com.iuims.registrar.core.PolicySettings;
import com.iuims.registrar.core.SqlGenerator;
import com.iuims.registrar.academic.ScheduleConflictValidator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Faculty teaching-load calculation service.
 *
 * Load counting logic (mirrors university_scheduling3):
 *   - Normal course: counted_units = credit_units
 *   - Coordinator-based course (is_coordinator_based = 1):
 *       counted_units = coordinator_equivalent_units
 *
 * A faculty member is considered "overloaded" when their total counted_units
 * exceeds faculty.max_teaching_units for the given term.
 */
@Service
public class FacultyLoadService {

    @Autowired
    private JdbcTemplate db;

    // -------------------------------------------------------------------------
    // Assigned sections for one faculty in a term
    // -------------------------------------------------------------------------

    /**
     * Returns every class_section assigned to the given faculty in a term,
     * together with the effective load units for each section.
     */
    public List<Map<String, Object>> getFacultyLoad(int facultyId, int termId) {
        return db.queryForList(
            "SELECT " +
            "  cs.section_id, " +
            "  cs.section_code, " +
            "  c.course_code, " +
            "  c.course_title, " +
            "  c.credit_units, " +
            "  c.is_coordinator_based, " +
            "  c.coordinator_equivalent_units, " +
            "  CASE " +
            "    WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "      THEN c.coordinator_equivalent_units " +
            "    ELSE c.credit_units " +
            "  END AS effective_load_units, " +
            "  at2.term_name, " +
            "  cs.semester_number " +
            "FROM class_sections cs " +
            "JOIN courses c ON c.course_id = cs.course_id " +
            "JOIN academic_terms at2 ON at2.term_id = cs.term_id " +
            "WHERE cs.faculty_id = ? AND cs.term_id = ? " +
            "ORDER BY cs.semester_number, c.course_code",
            facultyId, termId);
    }

    // -------------------------------------------------------------------------
    // Summary: total vs max for a single faculty
    // -------------------------------------------------------------------------

    /**
     * Returns a summary row:
     *   { faculty_id, full_name, employment_type, total_load_units, max_teaching_units,
     *     remaining_units, is_overloaded, load_pct }
     */
    public Map<String, Object> getFacultyLoadSummary(int facultyId, int termId) {
        return db.queryForMap(
            "SELECT " +
            "  f.faculty_id, " +
            "  CONCAT(f.first_name, ' ', f.last_name)  AS full_name, " +
            "  f.employment_type, " +
            "  f.max_teaching_units, " +
            "  COALESCE(SUM( " +
            "    CASE " +
            "      WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "        THEN c.coordinator_equivalent_units " +
            "      ELSE c.credit_units " +
            "    END " +
            "  ), 0) AS total_load_units, " +
            "  f.max_teaching_units - COALESCE(SUM( " +
            "    CASE " +
            "      WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "        THEN c.coordinator_equivalent_units " +
            "      ELSE c.credit_units " +
            "    END " +
            "  ), 0) AS remaining_units, " +
            "  CASE " +
            "    WHEN COALESCE(SUM( " +
            "      CASE WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "        THEN c.coordinator_equivalent_units ELSE c.credit_units END), 0) > f.max_teaching_units " +
            "      THEN 1 ELSE 0 " +
            "  END AS is_overloaded, " +
            "  ROUND(COALESCE(SUM( " +
            "    CASE WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "      THEN c.coordinator_equivalent_units ELSE c.credit_units END), 0) " +
            "    * 100.0 / f.max_teaching_units, 1) AS load_pct " +
            "FROM faculty f " +
            "LEFT JOIN class_sections cs ON cs.faculty_id = f.faculty_id AND cs.term_id = ? " +
            "LEFT JOIN courses c ON c.course_id = cs.course_id " +
            "WHERE f.faculty_id = ? " +
            "GROUP BY f.faculty_id",
            termId, facultyId);
    }

    // -------------------------------------------------------------------------
    // Department-wide load summary
    // -------------------------------------------------------------------------

    /**
     * Returns one summary row per active faculty in a department for a given term.
     * Ordered: overloaded first, then by load_pct descending.
     */
    public List<Map<String, Object>> getDepartmentLoadSummary(int departmentId, int termId) {
        return db.queryForList(
            "SELECT " +
            "  f.faculty_id, " +
            "  CONCAT(f.first_name, ' ', f.last_name)  AS full_name, " +
            "  f.employee_number, " +
            "  f.employment_type, " +
            "  f.max_teaching_units, " +
            "  d.department_name, " +
            "  COALESCE(SUM( " +
            "    CASE " +
            "      WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "        THEN c.coordinator_equivalent_units " +
            "      ELSE c.credit_units " +
            "    END " +
            "  ), 0) AS total_load_units, " +
            "  f.max_teaching_units - COALESCE(SUM( " +
            "    CASE " +
            "      WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "        THEN c.coordinator_equivalent_units " +
            "      ELSE c.credit_units " +
            "    END " +
            "  ), 0) AS remaining_units, " +
            "  CASE " +
            "    WHEN COALESCE(SUM( " +
            "      CASE WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "        THEN c.coordinator_equivalent_units ELSE c.credit_units END), 0) > f.max_teaching_units " +
            "      THEN 1 ELSE 0 " +
            "  END AS is_overloaded, " +
            "  ROUND(COALESCE(SUM( " +
            "    CASE WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "      THEN c.coordinator_equivalent_units ELSE c.credit_units END), 0) " +
            "    * 100.0 / NULLIF(f.max_teaching_units, 0), 1) AS load_pct, " +
            "  COUNT(cs.section_id) AS section_count " +
            "FROM faculty f " +
            "JOIN departments d ON d.department_id = f.department_id " +
            "LEFT JOIN class_sections cs ON cs.faculty_id = f.faculty_id AND cs.term_id = ? " +
            "LEFT JOIN courses c ON c.course_id = cs.course_id " +
            "WHERE f.department_id = ? AND f.active_status = 1 " +
            "GROUP BY f.faculty_id " +
            "ORDER BY is_overloaded DESC, load_pct DESC",
            termId, departmentId);
    }

    // -------------------------------------------------------------------------
    // All departments summary (admin view)
    // -------------------------------------------------------------------------

    public List<Map<String, Object>> getAllFacultyLoadSummary(int termId) {
        return db.queryForList(
            "SELECT " +
            "  f.faculty_id, " +
            "  CONCAT(f.first_name, ' ', f.last_name)  AS full_name, " +
            "  f.employee_number, " +
            "  f.employment_type, " +
            "  f.max_teaching_units, " +
            "  d.department_name, " +
            "  d.department_id, " +
            "  COALESCE(SUM( " +
            "    CASE " +
            "      WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "        THEN c.coordinator_equivalent_units " +
            "      ELSE c.credit_units " +
            "    END " +
            "  ), 0) AS total_load_units, " +
            "  f.max_teaching_units - COALESCE(SUM( " +
            "    CASE " +
            "      WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "        THEN c.coordinator_equivalent_units " +
            "      ELSE c.credit_units " +
            "    END " +
            "  ), 0) AS remaining_units, " +
            "  CASE " +
            "    WHEN COALESCE(SUM( " +
            "      CASE WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "        THEN c.coordinator_equivalent_units ELSE c.credit_units END), 0) > f.max_teaching_units " +
            "      THEN 1 ELSE 0 " +
            "  END AS is_overloaded, " +
            "  ROUND(COALESCE(SUM( " +
            "    CASE WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "      THEN c.coordinator_equivalent_units ELSE c.credit_units END), 0) " +
            "    * 100.0 / NULLIF(f.max_teaching_units, 0), 1) AS load_pct, " +
            "  COUNT(cs.section_id) AS section_count " +
            "FROM faculty f " +
            "LEFT JOIN departments d ON d.department_id = f.department_id " +
            "LEFT JOIN class_sections cs ON cs.faculty_id = f.faculty_id AND cs.term_id = ? " +
            "LEFT JOIN courses c ON c.course_id = cs.course_id " +
            "WHERE f.active_status = 1 " +
            "GROUP BY f.faculty_id " +
            "ORDER BY d.department_name, is_overloaded DESC, load_pct DESC",
            termId);
    }

    public Map<String, Object> getTermAssignmentAudit(int termId) {
        int totalSections = db.queryForObject(
            "SELECT COUNT(*) FROM class_sections WHERE term_id = ?",
            Integer.class,
            termId);
        int assignedSections = db.queryForObject(
            "SELECT COUNT(*) FROM class_sections WHERE term_id = ? AND faculty_id IS NOT NULL",
            Integer.class,
            termId);
        int duplicateGroups = db.queryForObject(
            "SELECT COUNT(*) FROM (" +
            " SELECT 1 FROM class_sections WHERE term_id = ? " +
            " GROUP BY course_id, term_id, section_code HAVING COUNT(*) > 1" +
            ") dupes",
            Integer.class,
            termId);
        int duplicateRows = 0;
        if (duplicateGroups > 0) {
            duplicateRows = db.queryForObject(
                "SELECT COALESCE(SUM(dupe_count), 0) FROM (" +
                " SELECT COUNT(*) AS dupe_count FROM class_sections WHERE term_id = ? " +
                " GROUP BY course_id, term_id, section_code HAVING COUNT(*) > 1" +
                ") dupes",
                Integer.class,
                termId);
        }

        Map<String, Object> audit = new java.util.LinkedHashMap<>();
        audit.put("term_id", termId);
        audit.put("total_sections", totalSections);
        audit.put("assigned_sections", assignedSections);
        audit.put("duplicate_section_groups", duplicateGroups);
        audit.put("duplicate_section_rows", duplicateRows);
        audit.put("needs_attention", duplicateGroups > 0);
        audit.put("suspicious_assignment_concentration", false);

        if (assignedSections == 0) {
            return audit;
        }

        List<Map<String, Object>> topRows = db.queryForList(
            "SELECT cs.faculty_id, CONCAT(f.first_name, ' ', f.last_name) AS full_name, " +
            " f.max_teaching_units, COUNT(*) AS assigned_count, COALESCE(SUM(" +
            "   CASE WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "     THEN c.coordinator_equivalent_units ELSE c.credit_units END" +
            " ), 0) AS total_load_units " +
            "FROM class_sections cs " +
            "JOIN faculty f ON f.faculty_id = cs.faculty_id " +
            "LEFT JOIN courses c ON c.course_id = cs.course_id " +
            "WHERE cs.term_id = ? AND cs.faculty_id IS NOT NULL " +
            "GROUP BY cs.faculty_id, f.first_name, f.last_name, f.max_teaching_units " +
            "ORDER BY assigned_count DESC, total_load_units DESC LIMIT 1",
            termId);
        if (topRows.isEmpty()) {
            return audit;
        }

        Map<String, Object> top = topRows.get(0);
        int topFacultyId = top.get("faculty_id") instanceof Number n ? n.intValue() : 0;
        int topAssigned = top.get("assigned_count") instanceof Number n ? n.intValue() : 0;
        int maxUnits = top.get("max_teaching_units") instanceof Number n ? n.intValue() : 0;
        int totalLoadUnits = top.get("total_load_units") instanceof Number n ? n.intValue() : 0;
        double assignedSharePct = assignedSections > 0
            ? Math.round((topAssigned * 1000.0) / assignedSections) / 10.0
            : 0.0;

        int totalSchedules = db.queryForObject(
            "SELECT COUNT(*) FROM class_schedules sch " +
            "JOIN class_sections cs ON cs.section_id = sch.section_id " +
            "WHERE cs.term_id = ?",
            Integer.class,
            termId);
        int mirroredSchedules = db.queryForObject(
            "SELECT COUNT(*) FROM class_schedules sch " +
            "JOIN class_sections cs ON cs.section_id = sch.section_id " +
            "WHERE cs.term_id = ? AND sch.faculty_id = ?",
            Integer.class,
            termId, topFacultyId);

        boolean suspiciousConcentration =
            totalSections >= 25 &&
            assignedSections >= 25 &&
            topAssigned == assignedSections &&
            assignedSharePct >= 90.0 &&
            maxUnits > 0 &&
            totalLoadUnits > (maxUnits * 4);

        audit.put("top_faculty_id", topFacultyId);
        audit.put("top_faculty_name", top.get("full_name"));
        audit.put("top_faculty_assigned_sections", topAssigned);
        audit.put("top_faculty_total_load_units", totalLoadUnits);
        audit.put("top_faculty_max_units", maxUnits);
        audit.put("top_faculty_assigned_share_pct", assignedSharePct);
        audit.put("total_schedule_rows", totalSchedules);
        audit.put("mirrored_schedule_rows", mirroredSchedules);
        audit.put("suspicious_assignment_concentration", suspiciousConcentration);
        audit.put("needs_attention", duplicateGroups > 0 || suspiciousConcentration);
        return audit;
    }

    @Transactional
    public Map<String, Object> repairSuspiciousTermAssignments(int termId) {
        Map<String, Object> audit = getTermAssignmentAudit(termId);
        boolean suspicious = Boolean.TRUE.equals(audit.get("suspicious_assignment_concentration"));
        int topFacultyId = audit.get("top_faculty_id") instanceof Number n ? n.intValue() : 0;
        if (!suspicious || topFacultyId <= 0) {
            return Map.of(
                "repaired", false,
                "message", "No suspicious faculty concentration was detected for this term.");
        }

        int clearedScheduleRows = db.update(
            "UPDATE class_schedules SET faculty_id = NULL " +
            "WHERE faculty_id = ? AND section_id IN (" +
            " SELECT section_id FROM class_sections WHERE term_id = ?" +
            ")",
            topFacultyId, termId);
        int clearedSections = db.update(
            "UPDATE class_sections SET faculty_id = NULL WHERE term_id = ? AND faculty_id = ?",
            termId, topFacultyId);

        return Map.of(
            "repaired", true,
            "cleared_sections", clearedSections,
            "cleared_schedule_rows", clearedScheduleRows,
            "faculty_id", topFacultyId,
            "faculty_name", audit.getOrDefault("top_faculty_name", "Unknown faculty"),
            "message", "Cleared suspicious faculty assignments for term " + termId + ".");
    }

    // -------------------------------------------------------------------------
    // Guard: check before assigning a section to a faculty member
    // -------------------------------------------------------------------------

    /**
     * Returns true if adding the given section to this faculty would push their
     * total load over their max_teaching_units cap.
     */
    public boolean wouldExceedUnitCap(int facultyId, int termId, int sectionId) {
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT cs.term_id, " +
            "CASE WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "  THEN c.coordinator_equivalent_units ELSE c.credit_units END AS load_units " +
            "FROM class_sections cs JOIN courses c ON c.course_id = cs.course_id " +
            "WHERE cs.section_id = ?",
            sectionId);
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> section = rows.get(0);
        int sectionTermId = section.get("term_id") instanceof Number n ? n.intValue() : termId;
        int sectionUnits = section.get("load_units") instanceof Number n ? n.intValue() : 0;
        String conflict = new ScheduleConflictValidator(db)
            .validateFacultyLoadCap(facultyId, sectionTermId, sectionUnits, sectionId);
        return conflict != null;
    }

    // -------------------------------------------------------------------------
    // Assignment
    // -------------------------------------------------------------------------

    /**
     * Assigns a faculty member to a class section.
     * Also propagates the assignment to class_schedules rows for that section.
     * Returns the number of rows updated.
     */
    public int assignFacultyToSection(int sectionId, int facultyId) {
        String conflict = new ScheduleConflictValidator(db).validateFacultyAssignment(sectionId, facultyId);
        if (conflict != null) {
            throw new IllegalArgumentException(conflict);
        }
        int rows = db.update(
            "UPDATE class_sections SET faculty_id = ? WHERE section_id = ?",
            facultyId, sectionId);
        // Mirror into class_schedules for grading / schedule views
        db.update(
            "UPDATE class_schedules SET faculty_id = ? WHERE section_id = ?",
            facultyId, sectionId);
        return rows;
    }

    // -------------------------------------------------------------------------
    // Term helpers
    // -------------------------------------------------------------------------

    public List<Map<String, Object>> getAllTerms() {
        return db.queryForList("SELECT term_id, term_code, term_name, status FROM academic_terms ORDER BY term_id DESC");
    }

    public List<Map<String, Object>> getAllDepartments() {
        return db.queryForList("SELECT department_id, department_code, department_name FROM departments ORDER BY department_name");
    }

    public Map<String, Object> getActiveTerm() {
        try {
            return db.queryForMap("SELECT term_id, term_code, term_name FROM academic_terms WHERE status = 'Active' LIMIT 1");
        } catch (Exception e) {
            try {
                return db.queryForMap("SELECT term_id, term_code, term_name FROM academic_terms ORDER BY term_id DESC LIMIT 1");
            } catch (Exception ex) {
                return Map.of("term_id", 1, "term_name", "Default Term");
            }
        }
    }
}



