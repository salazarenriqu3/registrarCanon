package com.iuims.registrar.service.integration;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Program;
import com.iuims.registrar.entity.Student;
import com.iuims.registrar.service.academic.BlockOfferingService;
import com.iuims.registrar.service.admission.ApplicantStatusSyncService;
import com.iuims.registrar.service.support.EnlistmentSchemaService;
import com.iuims.registrar.support.GradeOutcomeSql;
import com.iuims.registrar.support.PolicySettings;
import com.iuims.registrar.service.curriculum.StudentCurriculumService;
import com.iuims.registrar.service.forms.RegFormEventService;
import com.iuims.registrar.domain.forms.RegistrationFormSnapshotException;
import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.service.withdrawal.WithdrawalService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cross-app enrollment integration against the <strong>canonical</strong> schema
 * shared with the enrollment subsystem: {@code sys_users}, {@code student_enlistments},
 * {@code courses}, {@code class_sections}, {@code class_schedules}, {@code grades}.
 * (Legacy {@code jp_*} mirror tables are no longer the read/write source of truth here.)
 */
@Service
public class JaypeeIntegrationService {

    private record BundleAddPlan(LinkedHashMap<Integer, Integer> courseSections,
                                 List<String> addedCourseCodes,
                                 List<String> linkedCourseCodes,
                                 double totalUnits) {
    }

    private final JdbcTemplate db;

    private final ScholarEnrollmentService scholarEnrollmentService;

    private final EnlistmentSchemaService enlistmentSchemaService;

    private final ApplicantStatusSyncService applicantStatusSyncService;

    private final StudentCurriculumService studentCurriculumService;

    private final RegFormEventService regFormEventService;

    private final WithdrawalService withdrawalService;

    private final BlockOfferingService blockOfferingService;

    @Autowired
    public JaypeeIntegrationService(JdbcTemplate db, ScholarEnrollmentService scholarEnrollmentService, EnlistmentSchemaService enlistmentSchemaService, ApplicantStatusSyncService applicantStatusSyncService, StudentCurriculumService studentCurriculumService, RegFormEventService regFormEventService, WithdrawalService withdrawalService, BlockOfferingService blockOfferingService) {
        this.db = db;
        this.scholarEnrollmentService = scholarEnrollmentService;
        this.enlistmentSchemaService = enlistmentSchemaService;
        this.applicantStatusSyncService = applicantStatusSyncService;
        this.studentCurriculumService = studentCurriculumService;
        this.regFormEventService = regFormEventService;
        this.withdrawalService = withdrawalService;
        this.blockOfferingService = blockOfferingService != null ? blockOfferingService : new BlockOfferingService(db);
    }

    public JaypeeIntegrationService(JdbcTemplate db, ScholarEnrollmentService scholarEnrollmentService, EnlistmentSchemaService enlistmentSchemaService, ApplicantStatusSyncService applicantStatusSyncService, StudentCurriculumService studentCurriculumService, RegFormEventService regFormEventService, WithdrawalService withdrawalService) {
        this(db, scholarEnrollmentService, enlistmentSchemaService, applicantStatusSyncService, studentCurriculumService, regFormEventService, withdrawalService, new BlockOfferingService(db));
    }

    public JaypeeIntegrationService(JdbcTemplate db, ScholarEnrollmentService scholarEnrollmentService, EnlistmentSchemaService enlistmentSchemaService, ApplicantStatusSyncService applicantStatusSyncService, StudentCurriculumService studentCurriculumService, RegFormEventService regFormEventService) {
        this(db, scholarEnrollmentService, enlistmentSchemaService, applicantStatusSyncService, studentCurriculumService, regFormEventService, null, new BlockOfferingService(db));
    }

    public JaypeeIntegrationService(JdbcTemplate db, ScholarEnrollmentService scholarEnrollmentService, EnlistmentSchemaService enlistmentSchemaService, ApplicantStatusSyncService applicantStatusSyncService, StudentCurriculumService studentCurriculumService) {
        this(db, scholarEnrollmentService, enlistmentSchemaService, applicantStatusSyncService, studentCurriculumService, null, null, new BlockOfferingService(db));
    }


    private String committedFilter(String alias) {
        return enlistmentSchemaService.enlistmentStatusFilter(
            EnlistmentSchemaService.Scope.COMMITTED_ONLY, alias);
    }

    private String tableCommittedFilter() {
        return enlistmentSchemaService.enlistmentStatusFilter(
            EnlistmentSchemaService.Scope.COMMITTED_ONLY);
    }

    /** {@code student_enlistments.student_id} is now {@code students.student_number}. */
    private boolean checkStudentExists(String studentNumber) {
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM students WHERE student_number = ?",
                Integer.class, studentNumber);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isWithdrawnOrBlockedStudent(String studentNumber) {
        try {
            Map<String, Object> row = db.queryForMap(
                "SELECT admission_status, COALESCE(enrollment_blocked, 0) AS enrollment_blocked, " +
                    "COALESCE(is_active, 1) AS is_active " +
                    "FROM students WHERE student_number = ? LIMIT 1",
                studentNumber);
            String status = row.get("admission_status") != null
                ? String.valueOf(row.get("admission_status")) : "";
            boolean blocked = row.get("enrollment_blocked") instanceof Number n && n.intValue() != 0;
            boolean inactive = row.get("is_active") instanceof Number n && n.intValue() == 0;
            return "WITHDRAWN".equalsIgnoreCase(status) || blocked || inactive;
        } catch (Exception e) {
            return false;
        }
    }

    /** Canonical transaction key is {@code students.student_number}. */
    private List<Object> readKeys(String studentNumber) {
        if (studentNumber != null && !studentNumber.isBlank()) {
            return List.of(studentNumber.trim());
        }
        return List.of();
    }

    private String inClause(String column, int count) {
        if (count <= 0) return column + " IS NULL";
        return column + " IN (" + "?,".repeat(count - 1) + "?)";
    }

    private Object[] bind(List<Object> keys, Object... trailing) {
        Object[] args = new Object[keys.size() + trailing.length];
        for (int i = 0; i < keys.size(); i++) args[i] = keys.get(i);
        System.arraycopy(trailing, 0, args, keys.size(), trailing.length);
        return args;
    }

    private Object[] bindLeading(Object leading, List<Object> keys, Object... trailing) {
        Object[] args = new Object[1 + keys.size() + trailing.length];
        args[0] = leading;
        for (int i = 0; i < keys.size(); i++) args[i + 1] = keys.get(i);
        System.arraycopy(trailing, 0, args, 1 + keys.size(), trailing.length);
        return args;
    }

    private Integer resolveCurrentTermId(String studentNumber) {
        try {
            String sl = db.queryForObject(
                "SELECT term_year FROM students WHERE student_number = ? LIMIT 1",
                String.class, studentNumber);
            if (sl != null && sl.length() >= 12 && sl.startsWith("SL") && !sl.startsWith("SL_")) {
                // New SL format: SL[AYstart4][AYend4][YL][Sem] — sem at char[11]
                char sem = sl.charAt(11);
                String dbCode = sem + "1" + sl.substring(2, 10);
                return db.queryForObject(
                    "SELECT term_id FROM academic_terms WHERE term_code = ? LIMIT 1",
                    Integer.class, dbCode);
            }
            if (sl != null && sl.startsWith("SL_") && sl.length() >= 13) {
                // Legacy SL_ format: sem at char[3], ay at chars[5..12]
                String dbCode = sl.charAt(3) + "1" + sl.substring(5);
                return db.queryForObject(
                    "SELECT term_id FROM academic_terms WHERE term_code = ? LIMIT 1",
                    Integer.class, dbCode);
            }
        } catch (Exception ignored) {
        }
        try {
            return db.queryForObject(
                "SELECT term_id FROM academic_terms WHERE is_active = 1 OR UPPER(COALESCE(status,'')) = 'ACTIVE' " +
                    "ORDER BY term_id DESC LIMIT 1",
                Integer.class);
        } catch (Exception e) {
            return null;
        }
    }

    private double sumCurrentTermUnits(String studentNumber) {
        List<Object> keys = readKeys(studentNumber);
        if (keys.isEmpty()) return 0.0;
        Integer termId = resolveCurrentTermId(studentNumber);
        try {
            Double units;
            if (termId != null) {
                units = db.queryForObject(
                    "SELECT COALESCE(SUM(c.credit_units),0) FROM student_enlistments se " +
                        "JOIN courses c ON se.course_id = c.course_id " +
                        "JOIN class_sections cs ON se.section_id = cs.section_id " +
                        "WHERE " + inClause("se.student_id", keys.size()) + " AND cs.term_id = ?"
                        + committedFilter("se"),
                    Double.class, bind(keys, termId));
            } else {
                units = db.queryForObject(
                    "SELECT COALESCE(SUM(c.credit_units),0) FROM student_enlistments se " +
                        "JOIN courses c ON se.course_id = c.course_id " +
                        "WHERE " + inClause("se.student_id", keys.size()) + committedFilter("se"),
                    Double.class, keys.toArray());
            }
            return units != null ? units : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Gets all currently enlisted subjects for a student.
     */
    public List<Map<String, Object>> getStudentLoad(String studentNumber) {
        try {
            if (!checkStudentExists(studentNumber)) return new ArrayList<>();
            if (isWithdrawnOrBlockedStudent(studentNumber)) return new ArrayList<>();

            List<Object> keys = readKeys(studentNumber);
            String in = inClause("se.student_id", keys.size());
            Integer termId = resolveCurrentTermId(studentNumber);

            // Use a correlated subquery for schedule to avoid duplicate rows
            // caused by multiple class_schedules rows per section (e.g. TUE + WED).
            String sql = "SELECT se.enlistment_id, se.course_id, se.section_id as schedule_id, " +
                         "c.course_code, c.course_title as description, " +
                         "c.credit_units as units, cs.section_code as section, " +
                         "IFNULL((SELECT GROUP_CONCAT(" +
                         "  CONCAT(CASE sch2.day_of_week " +
                         "    WHEN 1 THEN 'MON' WHEN 2 THEN 'TUE' WHEN 3 THEN 'WED' " +
                         "    WHEN 4 THEN 'THU' WHEN 5 THEN 'FRI' WHEN 6 THEN 'SAT' ELSE 'SUN' END, " +
                         "    ' ', TIME_FORMAT(sch2.start_time,'%h:%i %p'), " +
                         "    '-', TIME_FORMAT(sch2.end_time,'%h:%i %p'), " +
                         "    ' ', IFNULL(r2.room_code,'TBA')) " +
                         "  ORDER BY sch2.day_of_week SEPARATOR ' | ') " +
                         "  FROM class_schedules sch2 LEFT JOIN rooms r2 ON r2.room_id = sch2.room_id " +
                         "  WHERE sch2.section_id = se.section_id), 'TBA') " +
                         "  AS pretty_schedule " +
                         "FROM student_enlistments se " +
                         "JOIN courses c ON se.course_id = c.course_id " +
                         "LEFT JOIN class_sections cs ON se.section_id = cs.section_id " +
                         "WHERE " + in + " " +
                         (termId != null ? "AND cs.term_id = ? " : "") +
                         committedFilter("se") +
                         "ORDER BY c.course_code";

            // Each enlistment row is exactly one row — no duplicate inflation.
            List<Map<String, Object>> rows = db.queryForList(sql, termId != null ? bind(keys, termId) : keys.toArray());
            double tuitionRate = 0.0;
            try {
                tuitionRate = scholarEnrollmentService.tuitionRatePerUnit(studentNumber);
            } catch (Exception ignored) {
                tuitionRate = 0.0;
            }
            DecimalFormat money = new DecimalFormat("#,##0.00");
            for (Map<String, Object> row : rows) {
                double units = row.get("units") instanceof Number ? ((Number) row.get("units")).doubleValue() : 0.0;
                double tuitionAmount = Math.max(0.0, units * tuitionRate);
                row.put("tuition_amount", tuitionAmount);
                row.put("tuition_amount_fmt", money.format(tuitionAmount));
            }
            Set<Integer> enrolledCourseIds = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                if (row.get("course_id") instanceof Number courseIdNumber) {
                    enrolledCourseIds.add(courseIdNumber.intValue());
                }
            }
            for (Map<String, Object> row : rows) {
                if (!(row.get("course_id") instanceof Number courseIdNumber)) {
                    continue;
                }
                List<String> linkedDropCodes = listSatisfiedCorequisiteCodes(courseIdNumber.intValue(), enrolledCourseIds, Set.of());
                if (!linkedDropCodes.isEmpty()) {
                    row.put("bundle_drop_note", "Dropping this subject will also drop linked corequisites: " + String.join(", ", linkedDropCodes) + ".");
                    row.put("drop_action_label", "Drop Bundle");
                } else {
                    row.put("bundle_drop_note", "");
                    row.put("drop_action_label", "Drop");
                }
            }
            return rows;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getCrossSystemAnalyzedOfferings(String studentNumber) {
        return getCrossSystemAnalyzedOfferings(studentNumber, false);
    }

    /**
     * Analyzes available sections filtered by curriculum ({@code courses} program / year / sem).
     */
    public List<Map<String, Object>> getCrossSystemAnalyzedOfferings(String studentNumber, boolean isBlockEnroll) {
        try {
            if (!checkStudentExists(studentNumber)) return new ArrayList<>();
            if (isWithdrawnOrBlockedStudent(studentNumber)) return new ArrayList<>();

            Map<String, Object> userInfo = db.queryForMap(
                "SELECT program_code, year_level, semester FROM students WHERE student_number = ?", studentNumber);
            String programCode = (String) userInfo.get("program_code");
            Integer assignedCurriculumId = studentCurriculumService.findCurrentCurriculumId(studentNumber);
            if (assignedCurriculumId == null) {
                return new ArrayList<>();
            }
            int stuYear = userInfo.get("year_level") != null ? ((Number) userInfo.get("year_level")).intValue() : 1;
            int stuSem = userInfo.get("semester") != null ? ((Number) userInfo.get("semester")).intValue() : 1;
            boolean irregularStudent = isIrregularStudentForAccess(studentNumber);

            double maxAllowedUnits = scholarEnrollmentService.getMaxAllowedUnitsForStudent(studentNumber, programCode, stuYear);

            List<Object> keys = readKeys(studentNumber);
            String eIn = inClause("e.student_id", keys.size());
            Integer currentTermId = resolveCurrentTermId(studentNumber);

            Double currentUnits = sumCurrentTermUnits(studentNumber);

            String sql;
            List<Map<String, Object>> classes;
            String curriculumJoin = "JOIN curriculum_templates ct ON cc.curriculum_id = ct.curriculum_id AND ct.curriculum_id = ? ";

            if (isBlockEnroll) {
                // Pick ONE section per course (lowest section_id) — mirrors EnrollmentIntegrationService block-enroll logic.
                // c.onlist = 1 ensures only admin-opened courses appear.
                sql = "SELECT cs.section_id as schedule_id, c.course_id, c.course_code, " +
                      "c.course_title as description, c.credit_units as units, cs.section_code as section, cs.term_id, " +
                      "NULL as prereq_id, cs.max_capacity as max_slots, " +
                      "MIN(sch.day_of_week) as day_of_week, MIN(sch.start_time) as start_time, MIN(sch.end_time) as end_time, " +
                      "cc.year_level as curr_year, cc.semester_number as curr_sem " +
                      "FROM class_sections cs " +
                      "JOIN courses c ON cs.course_id = c.course_id " +
                      "JOIN curriculum_courses cc ON c.course_id = cc.course_id " +
                      curriculumJoin +
                      "JOIN programs p ON ct.program_id = p.program_id " +
                      "LEFT JOIN class_schedules sch ON cs.section_id = sch.section_id " +
                      "WHERE p.program_code = ? AND cc.year_level = ? AND cc.semester_number = ? " +
                      "AND COALESCE(c.onlist, c.active_status, 1) = 1 " +
                      (currentTermId != null ? "AND cs.term_id = ? " : "") +
                      "AND cs.section_id = (" +
                      "  SELECT cs2.section_id FROM class_sections cs2 " +
                      "  WHERE cs2.course_id = c.course_id " +
                      (currentTermId != null ? "AND cs2.term_id = ? " : "") +
                      "ORDER BY CASE WHEN cs2.section_code REGEXP '^[A-Z0-9]+-[0-9]+-[0-9]+-[A-Z]$' THEN 0 ELSE 1 END, cs2.section_id LIMIT 1" +
                      ") " +
                      "GROUP BY cs.section_id, c.course_id, c.course_code, c.course_title, c.credit_units, cs.term_id, " +
                      "  cs.section_code, cs.max_capacity, cc.year_level, cc.semester_number " +
                      "ORDER BY cc.year_level, cc.semester_number, c.course_code";
                if (currentTermId != null) {
                    classes = assignedCurriculumId != null
                        ? db.queryForList(sql, assignedCurriculumId, programCode, stuYear, stuSem, currentTermId, currentTermId)
                        : db.queryForList(sql, programCode, stuYear, stuSem, currentTermId, currentTermId);
                } else {
                    classes = assignedCurriculumId != null
                        ? db.queryForList(sql, assignedCurriculumId, programCode, stuYear, stuSem)
                        : db.queryForList(sql, programCode, stuYear, stuSem);
                }
            } else {
                // Irregular/manual add can reach across year levels, but only within the
                // student's current semester in the assigned curriculum.
                sql = "SELECT cs.section_id as schedule_id, c.course_id, c.course_code, " +
                      "c.course_title as description, c.credit_units as units, cs.section_code as section, cs.term_id, " +
                      "(SELECT cp.prerequisite_course_id FROM course_prerequisites cp " +
                      " WHERE cp.course_id = c.course_id ORDER BY cp.prerequisite_id LIMIT 1) as prereq_id, " +
                      "cs.max_capacity as max_slots, " +
                      "MIN(sch.day_of_week) as day_of_week, MIN(sch.start_time) as start_time, MIN(sch.end_time) as end_time, " +
                      "cc.year_level as curr_year, cc.semester_number as curr_sem " +
                      "FROM class_sections cs " +
                      "JOIN courses c ON cs.course_id = c.course_id " +
                      "JOIN curriculum_courses cc ON c.course_id = cc.course_id " +
                      curriculumJoin +
                      "JOIN programs p ON ct.program_id = p.program_id " +
                      "LEFT JOIN class_schedules sch ON cs.section_id = sch.section_id " +
                      "WHERE p.program_code = ? AND cc.semester_number = ? " +
                      "AND COALESCE(c.onlist, c.active_status, 1) = 1 " +
                      (currentTermId != null ? "AND cs.term_id = ? " : "") +
                      "GROUP BY cs.section_id, c.course_id, c.course_code, c.course_title, c.credit_units, cs.term_id, " +
                      "  cs.section_code, cs.max_capacity, cc.year_level, cc.semester_number " +
                      "ORDER BY cc.year_level, cc.semester_number, c.course_code";
                if (currentTermId != null) {
                    classes = assignedCurriculumId != null
                        ? db.queryForList(sql, assignedCurriculumId, programCode, stuSem, currentTermId)
                        : db.queryForList(sql, programCode, stuSem, currentTermId);
                } else {
                    classes = assignedCurriculumId != null
                        ? db.queryForList(sql, assignedCurriculumId, programCode, stuSem)
                        : db.queryForList(sql, programCode, stuSem);
                }
            }

            String seIn = inClause("student_id", keys.size());
            List<Integer> enrolledSections = currentTermId != null
                ? db.queryForList(
                    "SELECT se.section_id FROM student_enlistments se " +
                        "JOIN class_sections cs ON se.section_id = cs.section_id " +
                        "WHERE " + inClause("se.student_id", keys.size()) + " AND cs.term_id = ?"
                        + committedFilter("se"),
                    Integer.class, bind(keys, currentTermId))
                : db.queryForList(
                    "SELECT section_id FROM student_enlistments WHERE " + seIn + tableCommittedFilter(),
                    Integer.class, keys.toArray());
            Set<Integer> enrolledCourses = new LinkedHashSet<>(currentTermId != null
                ? db.queryForList(
                    "SELECT se.course_id FROM student_enlistments se " +
                        "JOIN class_sections cs ON se.section_id = cs.section_id " +
                        "WHERE " + inClause("se.student_id", keys.size()) + " AND cs.term_id = ?"
                        + committedFilter("se"),
                    Integer.class, bind(keys, currentTermId))
                : db.queryForList(
                    "SELECT course_id FROM student_enlistments WHERE " + seIn + tableCommittedFilter(),
                    Integer.class, keys.toArray()));

            List<Integer> passedCourses = listPassedCourseIds(keys);

            for (Map<String, Object> c : classes) {
                formatScheduleString(c);

                int sectId = ((Number) c.get("schedule_id")).intValue();
                int courseId = ((Number) c.get("course_id")).intValue();
                Integer prereqId = c.get("prereq_id") != null ? ((Number) c.get("prereq_id")).intValue() : null;
                List<String> reasons = new ArrayList<>();

                if (passedCourses.contains(courseId)) {
                    reasons.add("Completed");
                }

                if (enrolledSections.contains(sectId) || enrolledCourses.contains(courseId)) {
                    reasons.add("Currently Enrolled");
                }

                String prereqBlock = findMissingPrerequisite(keys, courseId, passedCourses);
                if (prereqBlock != null) {
                    reasons.add(prereqBlock);
                }
                String irregularAccessBlock = irregularStudent ? irregularAccessBlockReason(sectId) : null;
                if (irregularAccessBlock != null) {
                    reasons.add(irregularAccessBlock);
                }
                if (!isBlockEnroll) {
                    List<String> linkedAddCodes = listMissingCorequisiteCodes(courseId, new LinkedHashSet<>(enrolledCourses), Set.of());
                    if (!linkedAddCodes.isEmpty()) {
                        c.put("bundle_add_note",
                            "Adds together with linked corequisites: " + String.join(", ", linkedAddCodes) + ".");
                    }
                }

                String conflict = findScheduleConflictCourse(keys, sectId);
                if (conflict != null) {
                    reasons.add("Schedule conflict with " + conflict);
                }

                int max = c.get("max_slots") != null ? ((Number) c.get("max_slots")).intValue() : 40;
                int enrolled = db.queryForObject(
                    "SELECT COUNT(*) FROM student_enlistments se WHERE se.section_id = ?" + committedFilter("se"),
                    Integer.class, sectId);
                if (enrolled >= max) {
                    reasons.add("Class Full");
                }

                double courseUnits = c.get("units") != null ? ((Number) c.get("units")).doubleValue() : 0.0;
                if (courseUnits > 0 && (currentUnits + courseUnits) > maxAllowedUnits) {
                    reasons.add("Exceeds Unit Limit");
                }

                boolean isDisabled = !reasons.isEmpty();
                c.put("is_disabled", isDisabled);
                c.put("constraint_msgs", reasons);
                c.put("reason_msg", isDisabled ? String.join("; ", reasons) : "");
            }

            return classes;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Returns one entry per curriculum course for the student's program/year/sem.
     * Each entry carries a {@code sections} list (nested Map) so the UI can render
     * a single row per course with a section-picker dropdown.
     */
    public List<Map<String, Object>> getGroupedCourseOfferings(String studentNumber) {
        return getGroupedCourseOfferings(studentNumber, null, null, null);
    }

    public List<Map<String, Object>> listOfferingPrograms() {
        try {
            return db.queryForList(
                "SELECT program_code, program_name, school_name FROM programs " +
                    "WHERE COALESCE(active_status, 1) = 1 " +
                    "AND (school_name IS NULL OR (school_name NOT LIKE '%Basic%' AND school_name NOT LIKE '%Senior%' AND school_name NOT LIKE '%Junior%')) " +
                    "ORDER BY school_name, program_code");
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<String> listOfferingSchools() {
        try {
            return db.queryForList(
                "SELECT DISTINCT school_name FROM programs " +
                    "WHERE school_name IS NOT NULL AND TRIM(school_name) <> '' " +
                    "AND COALESCE(active_status, 1) = 1 " +
                    "AND (school_name NOT LIKE '%Basic%' AND school_name NOT LIKE '%Senior%' AND school_name NOT LIKE '%Junior%') " +
                    "ORDER BY school_name",
                String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public String shiftStudentProgram(String studentNumber,
                                      String targetProgramCode,
                                      Integer targetYearLevel,
                                      Integer targetSemester,
                                      Integer targetCurriculumId,
                                      String reason) {
        String sn = studentNumber != null ? studentNumber.trim() : "";
        String program = targetProgramCode != null ? targetProgramCode.trim().toUpperCase() : "";
        if (sn.isEmpty()) return "ERROR: Missing student number.";
        if (program.isEmpty()) return "ERROR: Choose a target program.";

        try {
            if (!checkStudentExists(sn)) return "ERROR: Student not found.";
            if (isWithdrawnOrBlockedStudent(sn)) {
                return "ERROR: Withdrawn or inactive students cannot be shifted.";
            }
            Integer programCount = db.queryForObject(
                "SELECT COUNT(*) FROM programs WHERE program_code = ? AND COALESCE(active_status, 1) = 1",
                Integer.class, program);
            if (programCount == null || programCount == 0) {
                return "ERROR: Target program is not active or does not exist.";
            }
            Integer destinationCurriculumId = targetCurriculumId != null && targetCurriculumId > 0
                ? targetCurriculumId
                : studentCurriculumService.findDefaultCurriculumId(program);
            if (destinationCurriculumId == null) {
                return "ERROR: Target program has no active/default curriculum to assign.";
            }
            if (!studentCurriculumService.curriculumBelongsToProgram(destinationCurriculumId, program)) {
                return "ERROR: Selected curriculum does not belong to the target program.";
            }

            Map<String, Object> student = db.queryForMap(
                "SELECT program_code, year_level, semester, term_year FROM students WHERE student_number = ? LIMIT 1",
                sn);
            String fromProgram = student.get("program_code") != null ? student.get("program_code").toString() : "N/A";
            int yearLevel = sanitizeAcademicNumber(
                targetYearLevel,
                student.get("year_level") != null ? ((Number) student.get("year_level")).intValue() : 1,
                1,
                6);
            int semester = sanitizeAcademicNumber(
                targetSemester,
                student.get("semester") != null ? ((Number) student.get("semester")).intValue() : 1,
                1,
                3);
            String currentTermYear = student.get("term_year") != null ? student.get("term_year").toString() : null;
            String shiftedTermYear = rewriteSlTermYear(currentTermYear, yearLevel, semester);

            db.update(
                "UPDATE students SET program_code = ?, year_level = ?, semester = ?, term_year = ?, student_type = ? WHERE student_number = ?",
                program, yearLevel, semester, shiftedTermYear, "Irregular", sn);
            syncShiftedSysUser(sn, program, yearLevel, semester, shiftedTermYear);

            studentCurriculumService.assignCurriculumForProgramShift(
                sn,
                destinationCurriculumId,
                reason != null && !reason.trim().isEmpty()
                    ? reason.trim()
                    : "Assigned during registrar program shift.",
                "registrar");

            int clearedCurrentLoad = clearCurrentTermLoadForShift(sn, reason);
            Map<String, Object> carryOver = studentCurriculumService.getShiftCarryOverSummary(sn);
            int carried = carryOver.get("carriedOverCount") instanceof Number n ? n.intValue() : 0;
            int orphans = carryOver.get("orphanCount") instanceof Number n ? n.intValue() : 0;
            int deficiencies = carryOver.get("deficiencyCount") instanceof Number n ? n.intValue() : 0;

            int clearedStaged = clearCurrentTermStagedEnlistments(sn);
            String note = reason != null && !reason.trim().isEmpty()
                ? " Reason: " + reason.trim()
                : "";
            if (regFormEventService != null) {
                regFormEventService.recordEvent(
                    sn,
                    "PROGRAM_SHIFT",
                    "Registrar program shift completed",
                    null,
                    "From " + fromProgram + " to " + program + " | curriculum=" + destinationCurriculumId
                        + " | carried=" + carried + " | orphan=" + orphans + " | deficient=" + deficiencies
                        + (reason != null && !reason.trim().isEmpty() ? " | " + reason.trim() : ""),
                    "registrar");
            }
            return "SUCCESS: Shifted " + sn + " from " + fromProgram + " to " + program +
                " as Irregular. Assigned curriculum " + destinationCurriculumId +
                ". Carry-over: " + carried + " matched, " + orphans + " orphan passed, " +
                deficiencies + " still required." +
                " Cleared " + clearedCurrentLoad + " enrolled and " + clearedStaged +
                " staged current-term enlistment(s)." + note;
        } catch (RegistrationFormSnapshotException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Program shift was not completed. No changes were committed.", e);
        }
    }

    private int clearCurrentTermLoadForShift(String studentNumber, String reason) {
        if (withdrawalService == null) return 0;
        String note = reason != null && !reason.trim().isEmpty()
            ? "Program shift load cleanup. " + reason.trim()
            : "Program shift load cleanup.";
        WithdrawalService.DirectDropResult result =
            withdrawalService.clearCurrentTermLoadForCompositeProgramShift(studentNumber, note, "registrar");
        return result.subjectsDropped();
    }

    private void syncShiftedSysUser(String studentNumber,
                                    String programCode,
                                    int yearLevel,
                                    int semester,
                                    String termYear) {
        try {
            db.update(
                "UPDATE sys_users SET program_code = ?, year_level = ?, semester = ?, term_year = ?, " +
                    "student_type = ?, enrollment_status_type = ? WHERE username = ?",
                programCode, yearLevel, semester, termYear, "Irregular", "Irregular", studentNumber
            );
        } catch (Exception ex) {
            db.update(
                "UPDATE sys_users SET program_code = ?, year_level = ?, semester = ?, term_year = ?, student_type = ? " +
                    "WHERE username = ?",
                programCode, yearLevel, semester, termYear, "Irregular", studentNumber
            );
        }
    }

    private int sanitizeAcademicNumber(Integer requested, int fallback, int min, int max) {
        int value = requested != null ? requested : fallback;
        if (value < min || value > max) return fallback;
        return value;
    }

    private String rewriteSlTermYear(String currentTermYear, int yearLevel, int semester) {
        if (currentTermYear == null) return null;
        // New SL format: SL[AYstart4][AYend4][YL][Sem]
        if (currentTermYear.startsWith("SL") && !currentTermYear.startsWith("SL_") && currentTermYear.length() >= 12) {
            return "SL" + currentTermYear.substring(2, 10) + yearLevel + semester;
        }
        // Legacy SL_ format — convert to new format
        if (currentTermYear.startsWith("SL_") && currentTermYear.length() >= 13) {
            String ayStart = currentTermYear.substring(5, 9);
            String ayEnd   = currentTermYear.substring(9, 13);
            return "SL" + ayStart + ayEnd + yearLevel + semester;
        }
        return currentTermYear;
    }

    private int clearCurrentTermStagedEnlistments(String studentNumber) {
        if (!enlistmentSchemaService.hasEnlistmentStatusColumn()) return 0;
        List<Object> keys = readKeys(studentNumber);
        if (keys.isEmpty()) return 0;
        Integer currentTermId = resolveCurrentTermId(studentNumber);
        if (currentTermId == null) return 0;
        return db.update(
            "DELETE se FROM student_enlistments se " +
                "JOIN class_sections cs ON se.section_id = cs.section_id " +
                "WHERE " + inClause("se.student_id", keys.size()) +
                " AND cs.term_id = ? AND se.enlistment_status = ?",
            bind(keys, currentTermId, enlistmentSchemaService.stagedStatusValue()));
    }

    public List<Map<String, Object>> getGroupedCourseOfferings(String studentNumber,
                                                               String schoolName,
                                                               String selectedProgram,
                                                               String searchQuery) {
        try {
            if (!checkStudentExists(studentNumber)) return new ArrayList<>();
            if (isWithdrawnOrBlockedStudent(studentNumber)) return new ArrayList<>();

            Map<String, Object> userInfo = db.queryForMap(
                "SELECT program_code, year_level, semester FROM students WHERE student_number = ?", studentNumber);
            String programCode = (String) userInfo.get("program_code");
            Integer assignedCurriculumId = studentCurriculumService.findCurrentCurriculumId(studentNumber);
            if (assignedCurriculumId == null) {
                return new ArrayList<>();
            }
            int stuYear = userInfo.get("year_level") != null ? ((Number) userInfo.get("year_level")).intValue() : 1;
            int stuSem  = userInfo.get("semester")   != null ? ((Number) userInfo.get("semester")).intValue()   : 1;
            boolean irregularStudent = isIrregularStudentForAccess(studentNumber);
            String school = normalizeOfferingFilter(schoolName);
            String program = normalizeOfferingFilter(selectedProgram);
            String search = normalizeOfferingFilter(searchQuery);
            Integer curriculumFilterId = ((program == null && school == null) ||
                (program != null && program.equalsIgnoreCase(programCode)))
                ? assignedCurriculumId
                : null;

            double maxUnits = scholarEnrollmentService.getMaxAllowedUnitsForStudent(studentNumber, programCode, stuYear);

            Double currentUnits = sumCurrentTermUnits(studentNumber);

            List<Object> keys = readKeys(studentNumber);
            String seIn = inClause("student_id", keys.size());
            Integer currentTermId = resolveCurrentTermId(studentNumber);
            Set<Integer> enrolledCourses = new LinkedHashSet<>(currentTermId != null
                ? db.queryForList(
                    "SELECT se.course_id FROM student_enlistments se " +
                        "JOIN class_sections cs ON se.section_id = cs.section_id " +
                        "WHERE " + inClause("se.student_id", keys.size()) + " AND cs.term_id = ?"
                        + committedFilter("se"),
                    Integer.class, bind(keys, currentTermId))
                : db.queryForList(
                    "SELECT course_id FROM student_enlistments WHERE " + seIn + tableCommittedFilter(),
                    Integer.class, keys.toArray()));
            List<Integer> passedCourses = listPassedCourseIds(keys);

            // All courses in the student's program+semester, across ALL year levels.
            // Year-level is shown for reference only — access is gated by prerequisites, not year.
            StringBuilder where = new StringBuilder(" WHERE COALESCE(c.onlist, c.active_status, 1) = 1 ");
            List<Object> courseParams = new ArrayList<>();
            if (program != null) {
                where.append(" AND p.program_code = ? ");
                courseParams.add(program);
            } else if (school != null) {
                where.append(" AND p.school_name = ? ");
                courseParams.add(school);
            } else {
                where.append(" AND p.program_code = ? ");
                courseParams.add(programCode);
            }
            if (search != null) {
                String like = "%" + search + "%";
                where.append(" AND (c.course_code LIKE ? OR c.course_title LIKE ?) ");
                courseParams.add(like);
                courseParams.add(like);
            }
            if (curriculumFilterId != null) {
                where.append(" AND ct.curriculum_id = ? AND cc.semester_number = ? ");
                courseParams.add(curriculumFilterId);
                courseParams.add(stuSem);
            } else {
                where.append(" AND 1 = 0 ");
            }

            List<Map<String, Object>> courses = db.queryForList(
                "SELECT DISTINCT c.course_id, c.course_code, c.course_title AS description," +
                " c.credit_units AS units, cc.year_level, p.program_code AS offering_program_code, p.school_name AS offering_school_name," +
                " (SELECT cp.prerequisite_course_id FROM course_prerequisites cp WHERE cp.course_id = c.course_id LIMIT 1) AS prereq_id" +
                " FROM courses c" +
                " JOIN curriculum_courses cc ON c.course_id = cc.course_id" +
                " JOIN curriculum_templates ct ON cc.curriculum_id = ct.curriculum_id" +
                " JOIN programs p ON ct.program_id = p.program_id" +
                where +
                " ORDER BY p.program_code, cc.year_level, cc.semester_number, c.course_code",
                courseParams.toArray());

            for (Map<String, Object> course : courses) {
                int courseId = ((Number) course.get("course_id")).intValue();
                double courseUnits = course.get("units") != null ? ((Number) course.get("units")).doubleValue() : 0.0;
                Integer prereqId = course.get("prereq_id") != null ? ((Number) course.get("prereq_id")).intValue() : null;
                List<String> courseReasons = new ArrayList<>();

                if (passedCourses.contains(courseId)) {
                    courseReasons.add("Completed");
                }
                if (enrolledCourses.contains(courseId)) {
                    courseReasons.add("Currently Enrolled");
                }
                String prereqBlock = findMissingPrerequisite(keys, courseId, passedCourses);
                if (prereqBlock != null) {
                    courseReasons.add(prereqBlock);
                }
                List<String> linkedAddCodes = listMissingCorequisiteCodes(courseId, enrolledCourses, Set.of());
                if (!linkedAddCodes.isEmpty()) {
                    course.put("bundle_add_note",
                        "Adds together with linked corequisites: " + String.join(", ", linkedAddCodes) + ".");
                    course.put("add_action_label", "Add Bundle");
                } else {
                    course.put("bundle_add_note", "");
                    course.put("add_action_label", "Add");
                }
                double projectedUnits = totalUnitsForCourses(resolveRequiredCorequisiteSet(courseId), enrolledCourses);
                if (projectedUnits > 0 && (currentUnits + projectedUnits) > maxUnits) {
                    courseReasons.add("Exceeds Unit Limit");
                }

                // Fetch all sections for this course with schedule + capacity
                List<Map<String, Object>> rawSections = db.queryForList(
                    "SELECT cs.section_id, cs.section_code, cs.max_capacity," +
                    " IFNULL((SELECT GROUP_CONCAT(" +
                    "   CONCAT(CASE s2.day_of_week WHEN 1 THEN 'MON' WHEN 2 THEN 'TUE'" +
                    "     WHEN 3 THEN 'WED' WHEN 4 THEN 'THU' WHEN 5 THEN 'FRI' WHEN 6 THEN 'SAT' ELSE 'SUN' END," +
                    "   ' ',TIME_FORMAT(s2.start_time,'%h:%i %p'),'-',TIME_FORMAT(s2.end_time,'%h:%i %p'))" +
                    "   ORDER BY s2.day_of_week SEPARATOR ' | ')" +
                    "   FROM class_schedules s2 WHERE s2.section_id = cs.section_id),'TBA') AS pretty_schedule," +
                    " (SELECT COUNT(*) FROM student_enlistments se2 WHERE se2.section_id = cs.section_id" +
                    committedFilter("se2") +
                    ") AS enrolled_count" +
                    " FROM class_sections cs WHERE cs.course_id = ? " +
                    (currentTermId != null ? "AND cs.term_id = ? " : "") +
                    "ORDER BY cs.section_code",
                    currentTermId != null ? new Object[]{courseId, currentTermId} : new Object[]{courseId});

                List<Map<String, Object>> sections = new ArrayList<>();
                boolean anyAvailable = false;
                boolean filteredByIrregularAccess = false;
                for (Map<String, Object> sec : rawSections) {
                    int sectionId = ((Number) sec.get("section_id")).intValue();
                    String irregularAccessBlock = irregularStudent ? irregularAccessBlockReason(sectionId) : null;
                    if (irregularAccessBlock != null) {
                        filteredByIrregularAccess = true;
                        continue;
                    }
                    int cap   = sec.get("max_capacity")  != null ? ((Number) sec.get("max_capacity")).intValue()  : 40;
                    int taken = sec.get("enrolled_count") != null ? ((Number) sec.get("enrolled_count")).intValue(): 0;
                    int slots = Math.max(0, cap - taken);
                    sec.put("slots_left", slots);
                    sec.put("is_full", slots == 0);

                    List<String> sectionReasons = new ArrayList<>();
                    if (slots == 0) {
                        sectionReasons.add("Class Full");
                    }
                    String conflict = findScheduleConflictCourse(keys, sectionId);
                    if (conflict != null) {
                        sectionReasons.add("Schedule conflict with " + conflict);
                    }
                    boolean sectionDisabled = !sectionReasons.isEmpty() || !courseReasons.isEmpty();
                    sec.put("has_conflict", conflict != null);
                    sec.put("constraint_msgs", sectionReasons);
                    sec.put("reason_msg", String.join("; ", sectionReasons));
                    sec.put("is_disabled", sectionDisabled);
                    if (!sectionReasons.isEmpty()) {
                        sec.put("section_code", String.join("; ", sectionReasons) + "  " + sec.get("section_code"));
                    }
                    if (!sectionDisabled) anyAvailable = true;
                    sections.add(sec);
                }

                if (sections.isEmpty()) {
                    courseReasons.add(filteredByIrregularAccess
                        ? "Closed to irregular enlistment"
                        : "No Sections Set Up");
                } else if (courseReasons.isEmpty() && !anyAvailable) {
                    courseReasons.add("No Available Slots");
                }
                boolean courseDisabled = !courseReasons.isEmpty();
                course.put("is_disabled", courseDisabled);
                course.put("constraint_msgs", courseReasons);
                course.put("reason_msg", courseDisabled ? String.join("; ", courseReasons) : "");
                course.put("sections", sections);
            }
            return courses;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private static boolean isLegacyIrregularSectionCode(String sectionCode) {
        return sectionCode != null && sectionCode.trim().toUpperCase().startsWith("IRREG");
    }

    private static String normalizeOfferingFilter(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "__ALL__".equals(trimmed) || "__DEFAULT__".equals(trimmed) ? null : trimmed;
    }

    public boolean isIrregularStudentForAccess(String studentNumber) {
        if (studentNumber == null || studentNumber.isBlank()) {
            return false;
        }
        String enrollmentStatusType = readOptionalStudentField(
            "SELECT enrollment_status_type FROM students WHERE student_number = ? LIMIT 1",
            studentNumber.trim());
        if ("Irregular".equalsIgnoreCase(enrollmentStatusType)) {
            return true;
        }
        if ("Regular".equalsIgnoreCase(enrollmentStatusType)) {
            return false;
        }
        String studentType = readOptionalStudentField(
            "SELECT student_type FROM students WHERE student_number = ? LIMIT 1",
            studentNumber.trim());
        if ("Transferee".equalsIgnoreCase(studentType) || "Irregular".equalsIgnoreCase(studentType)) {
            return true;
        }
        String referenceNumber = readOptionalStudentField(
            "SELECT reference_number FROM students WHERE student_number = ? LIMIT 1",
            studentNumber.trim());
        if (referenceNumber != null && !referenceNumber.isBlank()) {
            try {
                Map<String, Object> applicant = db.queryForMap(
                    "SELECT enrollment_type, last_school, course_taken FROM applicants WHERE reference_number = ? LIMIT 1",
                    referenceNumber.trim());
                String applicantType = applicant.get("enrollment_type") != null
                    ? applicant.get("enrollment_type").toString().trim()
                    : "";
                if (applicantType.toUpperCase(java.util.Locale.ROOT).contains("IRREG")) {
                    return true;
                }
                if (applicantType.toUpperCase(java.util.Locale.ROOT).contains("REGULAR")) {
                    return false;
                }
                return hasText(applicant.get("last_school")) || hasText(applicant.get("course_taken"));
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public String irregularAccessBlockReasonForStudent(String studentNumber, int sectionId) {
        if (!isIrregularStudentForAccess(studentNumber)) {
            return null;
        }
        return irregularAccessBlockReason(sectionId);
    }

    private String irregularAccessBlockReason(int sectionId) {
        BlockOfferingService.IrregularAccessDecision decision = blockOfferingService.resolveSectionIrregularAccess(sectionId);
        if (!decision.blockManaged() || decision.open()) {
            return null;
        }
        return "Closed to irregular enlistment. Ask Registrar to open irregular access for the class, block, or program/year/semester scope.";
    }

    private String readOptionalStudentField(String sql, String studentNumber) {
        try {
            String value = db.queryForObject(sql, String.class, studentNumber);
            return value != null ? value.trim() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().trim().isEmpty();
    }

    @Transactional
    public String addSubjectCrossSystem(String studentNumber, int passedId) {
        return addSubjectCrossSystem(studentNumber, passedId, false);
    }

    @Transactional
    public String addSubjectCrossSystem(String studentNumber, int passedId, boolean allowBlockSection) {
        try {
            if (!checkStudentExists(studentNumber)) return "ERROR: Student not found.";
            if (isWithdrawnOrBlockedStudent(studentNumber)) {
                return "ERROR: Withdrawn or inactive students cannot be enrolled in subjects.";
            }

            Map<String, Object> userInfo = db.queryForMap(
                "SELECT program_code, year_level, semester FROM students WHERE student_number = ?", studentNumber);
            String programCode = (String) userInfo.get("program_code");
            int stuYear = userInfo.get("year_level") != null ? ((Number) userInfo.get("year_level")).intValue() : 1;
            int stuSem = userInfo.get("semester") != null ? ((Number) userInfo.get("semester")).intValue() : 1;

            // Prior-term forwarded debt >= PHP 100 blocks new enlistment (not current-term balance alone).
            if (scholarEnrollmentService.hasAccountingBlock(studentNumber)) {
                double forwardDebt = scholarEnrollmentService.getForwardedBalanceNet(studentNumber);
                double threshold = PolicySettings.accountingBlockThreshold(db);
                return "ERROR: Enlistment blocked — prior-term forwarded balance of PHP "
                    + String.format("%,.2f", forwardDebt)
                    + " must be settled at Cashier first (threshold PHP "
                    + String.format("%,.2f", threshold) + ").";
            }

            String sql = "SELECT course_id, section_id FROM class_sections WHERE section_id = ?";
            Map<String, Object> classData;
            try {
                classData = db.queryForMap(sql, passedId);
            } catch (Exception e) {
                try {
                    classData = db.queryForMap(
                        "SELECT cs.course_id, cs.section_id FROM class_schedules sch " +
                        "JOIN class_sections cs ON sch.section_id = cs.section_id WHERE sch.schedule_id = ?",
                        passedId);
                } catch (Exception ex) {
                    return "ERROR: Class section or schedule not found.";
                }
            }

            Integer courseId = ((Number) classData.get("course_id")).intValue();
            Integer sectionId = ((Number) classData.get("section_id")).intValue();

            if (!isCourseInAssignedCurriculumSemester(studentNumber, courseId, stuSem)) {
                return "ERROR: Course is not available in the student's current curriculum semester.";
            }

            String sectionCode = db.queryForObject(
                "SELECT section_code FROM class_sections WHERE section_id = ? LIMIT 1",
                String.class, sectionId);
            if (isLegacyIrregularSectionCode(sectionCode)) {
                return "ERROR: Legacy irregular open sections are retired. Use a block section, or a summer/tutorial section if applicable.";
            }
            String irregularAccessBlock = irregularAccessBlockReasonForStudent(studentNumber, sectionId);
            if (irregularAccessBlock != null) {
                return "ERROR: " + irregularAccessBlock;
            }

            List<Object> keys = readKeys(studentNumber);
            Integer currentTermId = resolveCurrentTermId(studentNumber);

            // Guard against same section AND same course (different section of same course).
            int countSection = db.queryForObject(
                "SELECT COUNT(*) FROM student_enlistments se " +
                    "JOIN class_sections existing_cs ON se.section_id = existing_cs.section_id " +
                    "JOIN class_sections new_cs ON new_cs.section_id = ? " +
                    "WHERE " + inClause("se.student_id", keys.size()) +
                    " AND se.section_id = ?" + committedFilter("se") +
                    " AND existing_cs.term_id = new_cs.term_id",
                Integer.class, bindLeading(sectionId, keys, sectionId));
            if (countSection > 0) return "CONFLICT: Student is already enrolled in this section.";

            int countCourse = db.queryForObject(
                "SELECT COUNT(*) FROM student_enlistments se " +
                    "JOIN class_sections existing_cs ON se.section_id = existing_cs.section_id " +
                    "JOIN class_sections new_cs ON new_cs.section_id = ? " +
                    "WHERE " + inClause("se.student_id", keys.size()) +
                    " AND se.course_id = ?" + committedFilter("se") +
                    " AND existing_cs.term_id = new_cs.term_id",
                Integer.class, bindLeading(sectionId, keys, courseId));
            if (countCourse > 0) return "CONFLICT: Student is already enrolled in this course.";

            Map<String, Object> courseInfo = db.queryForMap(
                "SELECT course_code, credit_units as units FROM courses WHERE course_id = ?", courseId);
            String courseCode = (String) courseInfo.get("course_code");
            boolean bundledCorequisiteFlow = true;
            if (bundledCorequisiteFlow) {
                double bundleMaxAllowedUnits = scholarEnrollmentService.getMaxAllowedUnitsForStudent(studentNumber, programCode, stuYear);
                Double bundleCurrentUnits = sumCurrentTermUnits(studentNumber);
                List<Integer> passedCourses = listPassedCourseIds(keys);
                String prereqBlock = findMissingPrerequisite(keys, courseId, passedCourses);
                if (prereqBlock != null) {
                    return "ERROR: " + prereqBlock + ".";
                }
                Set<Integer> enrolledCourses = new LinkedHashSet<>(currentTermId != null
                    ? db.queryForList(
                        "SELECT se.course_id FROM student_enlistments se " +
                            "JOIN class_sections cs ON se.section_id = cs.section_id " +
                            "WHERE " + inClause("se.student_id", keys.size()) + " AND cs.term_id = ?"
                            + committedFilter("se"),
                        Integer.class, bind(keys, currentTermId))
                    : db.queryForList(
                        "SELECT course_id FROM student_enlistments WHERE " + inClause("student_id", keys.size()) + tableCommittedFilter(),
                        Integer.class, keys.toArray()));

                BundleAddPlan bundlePlan = resolveAddBundlePlan(
                    studentNumber, keys, courseId, sectionId, passedCourses, enrolledCourses,
                    currentTermId, bundleCurrentUnits, bundleMaxAllowedUnits, stuSem);

                double tuitionRate = scholarEnrollmentService.tuitionRatePerUnit(studentNumber);
                boolean firstAddedSubject = bundleCurrentUnits <= 0.0001d;
                String activationDetail = null;

                for (Map.Entry<Integer, Integer> bundleEntry : bundlePlan.courseSections().entrySet()) {
                    int bundleCourseId = bundleEntry.getKey();
                    int bundleSectionId = bundleEntry.getValue();
                    Map<String, Object> bundleCourseInfo = db.queryForMap(
                        "SELECT course_code, credit_units as units FROM courses WHERE course_id = ?",
                        bundleCourseId);
                    String bundleCourseCode = String.valueOf(bundleCourseInfo.get("course_code"));
                    double bundleUnits = bundleCourseInfo.get("units") instanceof Number n ? n.doubleValue() : 0.0;
                    String bundleSectionCode = db.queryForObject(
                        "SELECT section_code FROM class_sections WHERE section_id = ? LIMIT 1",
                        String.class, bundleSectionId);

                    if (enlistmentSchemaService.hasEnlistmentStatusColumn()) {
                        db.update(
                            "INSERT INTO student_enlistments (student_id, course_id, section_id, enlistment_status) VALUES (?, ?, ?, ?)",
                            studentNumber, bundleCourseId, bundleSectionId, enlistmentSchemaService.committedStatusValue());
                    } else {
                        db.update(
                            "INSERT INTO student_enlistments (student_id, course_id, section_id) VALUES (?, ?, ?)",
                            studentNumber, bundleCourseId, bundleSectionId);
                    }

                    double cost = bundleUnits * tuitionRate;
                    String desc = String.format("Added Subject: %s (%.1f units) [+PHP %,.2f]", bundleCourseCode, bundleUnits, cost);
                    db.update(
                        "INSERT INTO student_ledger (student_id, transaction_type, description, debit, credit) VALUES (?, 'SUBJECT_ADD', ?, ?, 0.0)",
                        studentNumber, desc, cost);

                    if (activationDetail == null) {
                        activationDetail = "First active subject added: " + bundleCourseCode + " via " + bundleSectionCode + " | " + desc;
                    }

                    if (regFormEventService != null) {
                        regFormEventService.recordEvent(
                            studentNumber,
                            "SUBJECT_ADD",
                            "Registrar subject add completed",
                            null,
                            "Added " + bundleCourseCode + " via " + bundleSectionCode
                                + (bundlePlan.linkedCourseCodes().isEmpty() ? "" : " as part of bundled corequisite action with " + String.join(", ", bundlePlan.addedCourseCodes()))
                                + " | " + desc,
                            "registrar");
                    }
                }

                if (firstAddedSubject) {
                    int promoted = db.update(
                        "UPDATE students SET admission_status = 'ENROLLED' " +
                            "WHERE student_number = ? AND (admission_status = 'ADMITTED' OR admission_status IS NULL)",
                        studentNumber);
                    if (promoted > 0) {
                        applicantStatusSyncService.markEnrolled(studentNumber);
                        if (regFormEventService != null && activationDetail != null) {
                            try {
                                regFormEventService.recordEvent(
                                    studentNumber,
                                    "ENROLLMENT_ACTIVATED",
                                    "First subject added and student activated",
                                    null,
                                    activationDetail,
                                    "registrar");
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }

                syncLedgerAssessment(studentNumber);

                if (!bundlePlan.linkedCourseCodes().isEmpty()) {
                    return "SUCCESS: Added bundled corequisites " + String.join(", ", bundlePlan.addedCourseCodes()) + ".";
                }
                return "SUCCESS: Added " + courseCode + ".";
            }
            Double units = ((Number) courseInfo.get("units")).doubleValue();

            double maxAllowedUnits = scholarEnrollmentService.getMaxAllowedUnitsForStudent(studentNumber, programCode, stuYear);

            Double currentUnits = sumCurrentTermUnits(studentNumber);

            if (units > 0 && (currentUnits + units) > maxAllowedUnits) {
                return "ERROR: Adding this subject exceeds the maximum allowable units for this semester.";
            }

            List<Integer> passedCourses = listPassedCourseIds(keys);
            String prereqBlock = findMissingPrerequisite(keys, courseId, passedCourses);
            if (prereqBlock != null) {
                return "ERROR: " + prereqBlock + ".";
            }
            if (!allowBlockSection) {
                List<Integer> enrolledCourses = currentTermId != null
                    ? db.queryForList(
                        "SELECT se.course_id FROM student_enlistments se " +
                            "JOIN class_sections cs ON se.section_id = cs.section_id " +
                            "WHERE " + inClause("se.student_id", keys.size()) + " AND cs.term_id = ?"
                            + committedFilter("se"),
                        Integer.class, bind(keys, currentTermId))
                    : db.queryForList(
                        "SELECT course_id FROM student_enlistments WHERE " + inClause("student_id", keys.size()) + tableCommittedFilter(),
                        Integer.class, keys.toArray());
                String coreqBlock = findMissingCorequisite(keys, courseId, passedCourses, enrolledCourses);
                if (coreqBlock != null) {
                    return "ERROR: " + coreqBlock + ".";
                }
            }

            String conflictCourse = findScheduleConflictCourse(keys, sectionId);
            if (conflictCourse != null) {
                return "ERROR: Schedule conflict with '" + conflictCourse + "'.";
            }

            if (isSectionFull(sectionId)) {
                return "ERROR: Section is full.";
            }

            if (enlistmentSchemaService.hasEnlistmentStatusColumn()) {
                db.update(
                    "INSERT INTO student_enlistments (student_id, course_id, section_id, enlistment_status) VALUES (?, ?, ?, ?)",
                    studentNumber, courseId, sectionId, enlistmentSchemaService.committedStatusValue());
            } else {
                db.update("INSERT INTO student_enlistments (student_id, course_id, section_id) VALUES (?, ?, ?)",
                    studentNumber, courseId, sectionId);
            }

            double cost = units * scholarEnrollmentService.tuitionRatePerUnit(studentNumber);
            String desc = String.format("Added Subject: %s (%.1f units) [+₱%,.2f]", courseCode, units, cost);
            db.update(
                    "INSERT INTO student_ledger (student_id, transaction_type, description, debit, credit) VALUES (?, 'SUBJECT_ADD', ?, ?, 0.0)",
                    studentNumber, desc, cost);

            // If this is the student's first enlistment, promote status ADMITTED -> ENROLLED.
            // ADMITTED  = student ID generated, no subjects yet.
            // ENROLLED  = student has at least one active subject.
            int promoted = db.update(
                "UPDATE students SET admission_status = 'ENROLLED' " +
                "WHERE student_number = ? AND (admission_status = 'ADMITTED' OR admission_status IS NULL)",
                studentNumber);
            if (promoted > 0) {
                applicantStatusSyncService.markEnrolled(studentNumber);
                if (regFormEventService != null) {
                    try {
                        regFormEventService.recordEvent(
                            studentNumber,
                            "ENROLLMENT_ACTIVATED",
                            "First subject added and student activated",
                            null,
                            "First active subject added: " + courseCode + " via " + sectionCode + " | " + desc,
                            "registrar");
                    } catch (Exception ignored) {
                    }
                }
            }

            if (regFormEventService != null) {
                regFormEventService.recordEvent(
                    studentNumber,
                    "SUBJECT_ADD",
                    "Registrar subject add completed",
                    null,
                    "Added " + courseCode + " via " + sectionCode + " | " + desc,
                    "registrar");
            }

            syncLedgerAssessment(studentNumber);

            return "SUCCESS";


        } catch (RegistrationFormSnapshotException e) {
            throw e;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return "CONFLICT: Student is already enrolled in this subject.";
        } catch (Exception e) {
            System.out.println("CROSS ADD ERROR: " + e.getMessage());
            return "ERROR: Failed to process Add Subject. " + e.getMessage();
        }
    }

    @Transactional
    public void dropSubjectCrossSystem(String studentNumber, int scheduleId) {
        dropSubjectCrossSystem(studentNumber, scheduleId, null, null);
    }

    @Transactional
    public void dropSubjectCrossSystem(String studentNumber, int scheduleId,
                                       Double chargeOverride, String policyNote) {
        try {
            if (!checkStudentExists(studentNumber)) return;

            List<Object> keys = readKeys(studentNumber);
            String seIn = inClause("e.student_id", keys.size());

            Long enlistmentId = db.queryForObject(
                "SELECT e.enlistment_id FROM student_enlistments e " +
                    "WHERE " + seIn + " AND e.section_id = ? LIMIT 1",
                Long.class,
                java.util.stream.Stream.concat(keys.stream(), java.util.stream.Stream.of(scheduleId)).toArray());

            if (enlistmentId != null) {
                scholarEnrollmentService.dropSubjectByEnlistmentId(enlistmentId, chargeOverride, policyNote);
            }
        } catch (Exception e) {
            System.out.println("DROP ERROR: " + e.getMessage());
        }
    }

    private BundleAddPlan resolveAddBundlePlan(String studentNumber,
                                               List<Object> keys,
                                               int primaryCourseId,
                                               int selectedSectionId,
                                               List<Integer> passedCourses,
                                               Set<Integer> enrolledCourses,
                                               Integer currentTermId,
                                               Double currentUnits,
                                               double maxAllowedUnits,
                                               int semesterNumber) {
        LinkedHashSet<Integer> corequisiteSet = resolveRequiredCorequisiteSet(primaryCourseId);
        Map<Integer, String> courseCodes = loadCourseCodes(corequisiteSet);
        List<Integer> orderedCourseIds = new ArrayList<>();
        orderedCourseIds.add(primaryCourseId);
        corequisiteSet.stream()
            .filter(courseId -> courseId != primaryCourseId)
            .sorted((left, right) -> courseCodes.getOrDefault(left, String.valueOf(left))
                .compareToIgnoreCase(courseCodes.getOrDefault(right, String.valueOf(right))))
            .forEach(orderedCourseIds::add);

        LinkedHashMap<Integer, Integer> courseSections = new LinkedHashMap<>();
        LinkedHashMap<Integer, String> chosenSectionCourseCodes = new LinkedHashMap<>();
        List<String> addedCourseCodes = new ArrayList<>();
        List<String> linkedCourseCodes = new ArrayList<>();
        double bundleUnits = 0.0;

        for (Integer courseId : orderedCourseIds) {
            if (enrolledCourses.contains(courseId)) {
                continue;
            }
            String courseCode = courseCodes.getOrDefault(courseId, "Course #" + courseId);
            if (!isCourseInAssignedCurriculumSemester(studentNumber, courseId, semesterNumber)) {
                throw new IllegalArgumentException(courseCode + " is not available in the student's current curriculum semester.");
            }
            String prereqBlock = findMissingPrerequisite(keys, courseId, passedCourses);
            if (prereqBlock != null) {
                throw new IllegalArgumentException(courseCode + " " + prereqBlock + ".");
            }
            bundleUnits += courseUnits(courseId);
            addedCourseCodes.add(courseCode);
            if (courseId != primaryCourseId) {
                linkedCourseCodes.add(courseCode);
            }
        }

        double safeCurrentUnits = currentUnits != null ? currentUnits : 0.0;
        if (bundleUnits > 0.0 && (safeCurrentUnits + bundleUnits) > maxAllowedUnits) {
            throw new IllegalArgumentException("Adding this bundled corequisite set exceeds the maximum allowable units for this semester.");
        }

        validateSelectedBundleSection(studentNumber, keys, selectedSectionId);
        courseSections.put(primaryCourseId, selectedSectionId);
        chosenSectionCourseCodes.put(selectedSectionId, courseCodes.getOrDefault(primaryCourseId, "Course #" + primaryCourseId));

        for (Integer courseId : orderedCourseIds) {
            if (courseId == primaryCourseId || enrolledCourses.contains(courseId)) {
                continue;
            }
            Map<String, Object> section = findFirstBundleSection(studentNumber, keys, courseId, currentTermId, chosenSectionCourseCodes);
            if (section == null) {
                throw new IllegalArgumentException(
                    "No available section can be paired for linked corequisite " + courseCodes.getOrDefault(courseId, "Course #" + courseId) + ".");
            }
            int bundleSectionId = ((Number) section.get("section_id")).intValue();
            courseSections.put(courseId, bundleSectionId);
            chosenSectionCourseCodes.put(bundleSectionId, courseCodes.getOrDefault(courseId, "Course #" + courseId));
        }

        return new BundleAddPlan(courseSections, addedCourseCodes, linkedCourseCodes, bundleUnits);
    }

    private void validateSelectedBundleSection(String studentNumber, List<Object> keys, int sectionId) {
        String conflictCourse = findScheduleConflictCourse(keys, sectionId);
        if (conflictCourse != null) {
            throw new IllegalArgumentException("Schedule conflict with '" + conflictCourse + "'.");
        }
        if (isSectionFull(sectionId)) {
            throw new IllegalArgumentException("Section is full.");
        }
        String sectionCode = db.queryForObject(
            "SELECT section_code FROM class_sections WHERE section_id = ? LIMIT 1",
            String.class, sectionId);
        if (isLegacyIrregularSectionCode(sectionCode)) {
            throw new IllegalArgumentException(
                "Legacy irregular open sections are retired. Use a block section, or a summer/tutorial section if applicable.");
        }
        String irregularAccessBlock = irregularAccessBlockReasonForStudent(studentNumber, sectionId);
        if (irregularAccessBlock != null) {
            throw new IllegalArgumentException(irregularAccessBlock);
        }
    }

    private Map<String, Object> findFirstBundleSection(String studentNumber,
                                                       List<Object> keys,
                                                       int courseId,
                                                       Integer currentTermId,
                                                       Map<Integer, String> chosenSectionCourseCodes) {
        List<Map<String, Object>> sections = db.queryForList(
            "SELECT section_id, section_code FROM class_sections WHERE course_id = ? "
                + (currentTermId != null ? "AND term_id = ? " : "")
                + "ORDER BY section_code",
            currentTermId != null ? new Object[]{courseId, currentTermId} : new Object[]{courseId});
        for (Map<String, Object> section : sections) {
            int sectionId = ((Number) section.get("section_id")).intValue();
            String sectionCode = String.valueOf(section.get("section_code"));
            if (isLegacyIrregularSectionCode(sectionCode) || isSectionFull(sectionId)) {
                continue;
            }
            if (irregularAccessBlockReasonForStudent(studentNumber, sectionId) != null) {
                continue;
            }
            String conflictCourse = findScheduleConflictCourse(keys, sectionId);
            if (conflictCourse != null) {
                continue;
            }
            if (findPendingBundleConflict(sectionId, chosenSectionCourseCodes) != null) {
                continue;
            }
            return section;
        }
        return null;
    }

    private String findPendingBundleConflict(int candidateSectionId, Map<Integer, String> chosenSectionCourseCodes) {
        for (Map.Entry<Integer, String> chosen : chosenSectionCourseCodes.entrySet()) {
            if (sectionsOverlap(candidateSectionId, chosen.getKey())) {
                return chosen.getValue();
            }
        }
        return null;
    }

    private boolean sectionsOverlap(int firstSectionId, int secondSectionId) {
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM class_schedules a " +
                "JOIN class_schedules b ON a.day_of_week = b.day_of_week " +
                "WHERE a.section_id = ? AND b.section_id = ? " +
                "AND a.start_time < b.end_time AND a.end_time > b.start_time",
            Integer.class,
            firstSectionId,
            secondSectionId);
        return count != null && count > 0;
    }

    private LinkedHashSet<Integer> resolveRequiredCorequisiteSet(int courseId) {
        LinkedHashSet<Integer> resolved = new LinkedHashSet<>();
        List<Integer> queue = new ArrayList<>();
        resolved.add(courseId);
        queue.add(courseId);
        while (!queue.isEmpty()) {
            int currentCourseId = queue.remove(0);
            if (!tableExists("course_corequisites")) {
                continue;
            }
            List<Integer> linked = db.queryForList(
                "SELECT corequisite_course_id FROM course_corequisites WHERE course_id = ?",
                Integer.class,
                currentCourseId);
            for (Integer linkedCourseId : linked) {
                if (linkedCourseId != null && resolved.add(linkedCourseId)) {
                    queue.add(linkedCourseId);
                }
            }
        }
        return resolved;
    }

    private Map<Integer, String> loadCourseCodes(Set<Integer> courseIds) {
        Map<Integer, String> codes = new LinkedHashMap<>();
        for (Integer courseId : courseIds) {
            String code = db.queryForObject(
                "SELECT course_code FROM courses WHERE course_id = ?",
                String.class,
                courseId);
            codes.put(courseId, code != null ? code : "Course #" + courseId);
        }
        return codes;
    }

    private double totalUnitsForCourses(Set<Integer> courseIds, Set<Integer> alreadyEnrolledCourseIds) {
        double total = 0.0;
        for (Integer courseId : courseIds) {
            if (alreadyEnrolledCourseIds.contains(courseId)) {
                continue;
            }
            total += courseUnits(courseId);
        }
        return total;
    }

    private double courseUnits(int courseId) {
        Double units = db.queryForObject(
            "SELECT credit_units FROM courses WHERE course_id = ?",
            Double.class,
            courseId);
        return units != null ? units : 0.0;
    }

    private List<String> listMissingCorequisiteCodes(int courseId,
                                                     Set<Integer> enrolledCourses,
                                                     Set<Integer> pendingCourses) {
        List<String> missing = new ArrayList<>();
        Map<Integer, String> courseCodes = loadCourseCodes(resolveRequiredCorequisiteSet(courseId));
        for (Map.Entry<Integer, String> entry : courseCodes.entrySet()) {
            if (entry.getKey() == courseId) {
                continue;
            }
            if (enrolledCourses.contains(entry.getKey()) || pendingCourses.contains(entry.getKey())) {
                continue;
            }
            missing.add(entry.getValue());
        }
        return missing;
    }

    private List<String> listSatisfiedCorequisiteCodes(int courseId,
                                                       Set<Integer> enrolledCourses,
                                                       Set<Integer> pendingCourses) {
        List<String> satisfied = new ArrayList<>();
        Map<Integer, String> courseCodes = loadCourseCodes(resolveRequiredCorequisiteSet(courseId));
        for (Map.Entry<Integer, String> entry : courseCodes.entrySet()) {
            if (entry.getKey() == courseId) {
                continue;
            }
            if (enrolledCourses.contains(entry.getKey()) || pendingCourses.contains(entry.getKey())) {
                satisfied.add(entry.getValue());
            }
        }
        return satisfied;
    }

    private void syncLedgerAssessment(String studentNumber) {
        scholarEnrollmentService.syncCoreLedgerAssessment(studentNumber);
    }

    private List<Integer> listPassedCourseIds(List<Object> keys) {
        if (keys.isEmpty()) return List.of();
        return db.queryForList(
            "SELECT DISTINCT course_id FROM grades WHERE "
                + inClause("student_id", keys.size()) + " AND " + GradeOutcomeSql.passed("grades"),
            Integer.class, keys.toArray());
    }

    /** Returns e.g. "Needs UPR1 11" when a prerequisite is unmet, or null if all are satisfied. */
    private String findMissingPrerequisite(List<Object> keys, int courseId, List<Integer> passedCourses) {
        List<Map<String, Object>> prereqs = db.queryForList(
            "SELECT c.course_id, c.course_code FROM course_prerequisites cp "
                + "JOIN courses c ON c.course_id = cp.prerequisite_course_id "
                + "WHERE cp.course_id = ?",
            courseId);
        for (Map<String, Object> prereq : prereqs) {
            int prereqId = ((Number) prereq.get("course_id")).intValue();
            if (!passedCourses.contains(prereqId)) {
                return "Needs " + prereq.get("course_code");
            }
        }
        return null;
    }

    private String findMissingCorequisite(List<Object> keys,
                                          int courseId,
                                          List<Integer> passedCourses,
                                          List<Integer> enrolledCourses) {
        if (!tableExists("course_corequisites")) {
            return null;
        }
        List<Map<String, Object>> coreqs = db.queryForList(
            "SELECT c.course_id, c.course_code FROM course_corequisites ccq "
                + "JOIN courses c ON c.course_id = ccq.corequisite_course_id "
                + "WHERE ccq.course_id = ? ORDER BY c.course_code",
            courseId);
        for (Map<String, Object> coreq : coreqs) {
            int coreqId = ((Number) coreq.get("course_id")).intValue();
            if (passedCourses.contains(coreqId) || enrolledCourses.contains(coreqId)) {
                continue;
            }
            return "Needs concurrent " + coreq.get("course_code");
        }
        return null;
    }

    /** Returns the title of the first conflicting enlisted course, or null if none. */
    private String findScheduleConflictCourse(List<Object> keys, int sectionIdToCheck) {
        if (keys.isEmpty()) return null;
        String sql =
            "SELECT c.course_title "
                + "FROM class_schedules new_sch "
                + "JOIN class_sections new_cs ON new_sch.section_id = new_cs.section_id "
                + "JOIN class_schedules existing_sch ON new_sch.day_of_week = existing_sch.day_of_week "
                + "JOIN student_enlistments se ON existing_sch.section_id = se.section_id "
                + "JOIN class_sections existing_cs ON se.section_id = existing_cs.section_id "
                + "JOIN courses c ON se.course_id = c.course_id "
                + "WHERE new_sch.section_id = ? AND " + inClause("se.student_id", keys.size()) + " "
                + committedFilter("se") + " "
                + "AND existing_cs.term_id = new_cs.term_id "
                + "AND new_sch.start_time < existing_sch.end_time "
                + "AND new_sch.end_time > existing_sch.start_time "
                + "LIMIT 1";
        try {
            Object[] args = bindLeading(sectionIdToCheck, keys);
            List<String> conflicts = db.queryForList(sql, String.class, args);
            return conflicts.isEmpty() ? null : conflicts.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isSectionFull(int sectionId) {
        try {
            Map<String, Object> row = db.queryForMap(
                "SELECT cs.max_capacity, "
                    + "(SELECT COUNT(*) FROM student_enlistments se WHERE se.section_id = cs.section_id"
                    + committedFilter("se") + ") AS enrolled "
                    + "FROM class_sections cs WHERE cs.section_id = ?",
                sectionId);
            int max = row.get("max_capacity") != null ? ((Number) row.get("max_capacity")).intValue() : 40;
            int enrolled = row.get("enrolled") != null ? ((Number) row.get("enrolled")).intValue() : 0;
            return enrolled >= max;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tableExists(String tableName) {
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                    "WHERE LOWER(TABLE_NAME) = LOWER(?)",
                Integer.class,
                tableName);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isCourseInAssignedCurriculumSemester(String studentNumber, int courseId, int semesterNumber) {
        Integer curriculumId = studentCurriculumService.findCurrentCurriculumId(studentNumber);
        if (curriculumId == null) {
            return false;
        }
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM curriculum_courses " +
                    "WHERE curriculum_id = ? AND course_id = ? AND semester_number = ?",
                Integer.class,
                curriculumId,
                courseId,
                semesterNumber);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void formatScheduleString(Map<String, Object> c) {
        if (c.get("day_of_week") == null || c.get("start_time") == null) {
            c.put("pretty_schedule", "TBA");
        } else {
            String dayStr = "";
            Object rawDay = c.get("day_of_week");
            try {
                int d = Integer.parseInt(rawDay.toString());
                dayStr = (d == 1) ? "MON" : (d == 2) ? "TUE" : (d == 3) ? "WED" : (d == 4) ? "THU" : (d == 5) ? "FRI" : (d == 6) ? "SAT" : "SUN";
            } catch (NumberFormatException e) {
                dayStr = rawDay.toString();
                if (dayStr.length() > 3) dayStr = dayStr.substring(0, 3).toUpperCase();
            }

            String stStr = c.get("start_time").toString();
            String etStr = c.get("end_time").toString();
            try {
                java.text.SimpleDateFormat inFmt = new java.text.SimpleDateFormat("HH:mm:ss");
                java.text.SimpleDateFormat outFmt = new java.text.SimpleDateFormat("h:mm a");
                c.put("pretty_schedule", dayStr + " " + outFmt.format(inFmt.parse(stStr)) + "-" + outFmt.format(inFmt.parse(etStr)));
            } catch (Exception parseEx) {
                c.put("pretty_schedule", dayStr + " " + stStr + "-" + etStr);
            }
        }
    }

}




