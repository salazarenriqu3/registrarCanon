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

    // -------------------------------------------------------------------------
    // Guard: check before assigning a section to a faculty member
    // -------------------------------------------------------------------------

    /**
     * Returns true if adding the given section to this faculty would push their
     * total load over their max_teaching_units cap.
     */
    public boolean wouldExceedUnitCap(int facultyId, int termId, int sectionId) {
        // Get current load
        Integer currentLoad = db.queryForObject(
            "SELECT COALESCE(SUM( " +
            "  CASE WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "    THEN c.coordinator_equivalent_units ELSE c.credit_units END" +
            "), 0) " +
            "FROM class_sections cs " +
            "JOIN courses c ON c.course_id = cs.course_id " +
            "WHERE cs.faculty_id = ? AND cs.term_id = ?",
            Integer.class, facultyId, termId);

        // Get units of the section being assigned
        Integer sectionUnits = db.queryForObject(
            "SELECT CASE WHEN c.is_coordinator_based = 1 AND c.coordinator_equivalent_units IS NOT NULL " +
            "  THEN c.coordinator_equivalent_units ELSE c.credit_units END " +
            "FROM class_sections cs JOIN courses c ON c.course_id = cs.course_id " +
            "WHERE cs.section_id = ?",
            Integer.class, sectionId);

        Integer maxUnits = db.queryForObject(
            "SELECT max_teaching_units FROM faculty WHERE faculty_id = ?",
            Integer.class, facultyId);

        int load = (currentLoad == null ? 0 : currentLoad);
        int add  = (sectionUnits == null ? 0 : sectionUnits);
        int max  = (maxUnits == null ? 18 : maxUnits);

        return (load + add) > max;
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



