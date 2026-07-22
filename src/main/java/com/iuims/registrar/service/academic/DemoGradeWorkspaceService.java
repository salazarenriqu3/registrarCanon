package com.iuims.registrar.service.academic;
import com.iuims.registrar.entity.Student;
import com.iuims.registrar.entity.Grade;
import com.iuims.registrar.repository.GradeRepository;

import com.iuims.registrar.service.support.EnlistmentSchemaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DemoGradeWorkspaceService {

    private final JdbcTemplate db;
    private final GradeRepository gradeRepository;
    private final GradeRecordEventService gradeRecordEventService;
    private final EnlistmentSchemaService enlistmentSchemaService;

    public DemoGradeWorkspaceService(JdbcTemplate db,
                                     GradeRepository gradeRepository,
                                     GradeRecordEventService gradeRecordEventService,
                                     EnlistmentSchemaService enlistmentSchemaService) {
        this.db = db;
        this.gradeRepository = gradeRepository;
        this.gradeRecordEventService = gradeRecordEventService;
        this.enlistmentSchemaService = enlistmentSchemaService;
    }

    public List<Map<String, Object>> getRows(String studentNumber, Integer termId) {
        if (studentNumber == null || studentNumber.isBlank()) {
            return List.of();
        }
        Integer resolvedTermId = termId != null && termId > 0 ? termId : getActiveTermId();
        if (resolvedTermId == null) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = db.queryForList("""
                SELECT
                    se.section_id,
                    se.course_id,
                    c.course_code,
                    c.course_title,
                    cs.section_code,
                    cs.term_id,
                    at.term_name,
                    at.academic_year,
                    at.semester_number,
                    g.id AS grade_id,
                    g.prelim,
                    g.midterm,
                    g.final_grade,
                    g.semestral_grade,
                    g.remarks,
                    g.previous_grade,
                    g.status,
                    g.grade_lock_status,
                    g.grade_lock_reason,
                    g.registrar_final_grade,
                    g.registrar_final_remarks,
                    g.registrar_finalized_at,
                    g.date_recorded
                FROM student_enlistments se
                JOIN class_sections cs ON cs.section_id = se.section_id
                JOIN courses c ON c.course_id = cs.course_id
                LEFT JOIN academic_terms at ON at.term_id = cs.term_id
                LEFT JOIN grades g ON g.student_id = se.student_id AND g.section_id = se.section_id
                WHERE se.student_id = ? AND cs.term_id = ?
                """ + enlistmentSchemaService.enlistmentStatusFilter(EnlistmentSchemaService.Scope.COMMITTED_ONLY, "se") + """
                ORDER BY c.course_code, cs.section_code
                """, studentNumber.trim(), resolvedTermId);

            for (Map<String, Object> row : rows) {
                row.put("term_label", buildTermLabel(row));
                row.put("workflow_state", workflowState(row));
                row.put("display_grade", displayGrade(row));
                row.put("display_remarks", displayRemarks(row));
                row.put("has_full_entry", hasFullEntry(row));
                row.put("has_quick_entry", row.get("semestral_grade") instanceof Number);
                row.put("is_locked", isLocked(row));
                row.put("can_save", !isLocked(row) && !"SUBMITTED".equals(workflowState(row)));
                row.put("can_submit", !isLocked(row) && hasEditableOutcome(row) && !"SUBMITTED".equals(workflowState(row)));
                row.put("can_review", "SUBMITTED".equals(workflowState(row)));
            }
            return rows;
        } catch (Exception e) {
            return List.of();
        }
    }

    @Transactional
    public String saveDraft(String studentNumber,
                            int sectionId,
                            String prelimStr,
                            String midtermStr,
                            String finalStr,
                            String quickFinalStr,
                            String actor,
                            String actorRole) {
        Grade grade = loadOrCreateGrade(studentNumber, sectionId);
        if (grade == null) {
            return "ERROR: Enrolled subject row was not found.";
        }
        if (isLocked(grade)) {
            return "ERROR: Approved demo grades are locked.";
        }
        if ("SUBMITTED".equalsIgnoreCase(statusOrDefault(grade.getStatus(), "DRAFT"))) {
            return "ERROR: Submitted demo grades must be reviewed before editing.";
        }
        if (!hasCommittedEnrollment(studentNumber, sectionId)) {
            return "ERROR: Demo grade entry is available only for committed enrolled subjects.";
        }

        GradeRecordEventService.GradeSnapshot before = GradeRecordEventService.snapshotOf(grade);
        String quickFinal = clean(quickFinalStr);
        if (quickFinal != null) {
            Double quickPoint = parsePointGrade(quickFinal);
            if (quickPoint == null) {
                return "ERROR: Quick final grade must be numeric.";
            }
            grade.setPrelim(null);
            grade.setMidterm(null);
            grade.setFinalGrade(null);
            grade.setSemestralGrade(BigDecimal.valueOf(quickPoint));
            grade.setRemarks(remarkForPointGrade(quickPoint));
        } else {
            Double prelim = parseScore(prelimStr);
            Double midterm = parseScore(midtermStr);
            Double finals = parseScore(finalStr);
            grade.setPrelim(prelim != null ? BigDecimal.valueOf(prelim) : null);
            grade.setMidterm(midterm != null ? BigDecimal.valueOf(midterm) : null);
            grade.setFinalGrade(finals != null ? BigDecimal.valueOf(finals) : null);
            Double pointGrade = derivePointGrade(prelim, midterm, finals);
            grade.setSemestralGrade(pointGrade != null ? BigDecimal.valueOf(pointGrade) : null);
            grade.setRemarks(deriveDraftRemarks(prelim, midterm, finals, pointGrade));
        }

        grade.setStatus("DRAFT");
        grade.setGradeLockStatus(null);
        grade.setGradeLockReason(null);
        grade.setRegistrarFinalGrade(null);
        grade.setRegistrarFinalRemarks(null);
        grade.setRegistrarFinalizedAt(null);
        if (grade.getDateRecorded() == null) {
            grade.setDateRecorded(LocalDateTime.now());
        }
        gradeRepository.saveAndFlush(grade);
        gradeRecordEventService.recordEvent(
            grade,
            null,
            "DEMO_GRADE_DRAFT_SAVED",
            "DRAFT",
            actor,
            actorRole,
            quickFinal != null ? "Quick final demo grade saved." : "Full component demo grade draft saved.",
            before,
            GradeRecordEventService.snapshotOf(grade));
        return "SUCCESS: Demo grade draft saved.";
    }

    @Transactional
    public String submit(String studentNumber, int sectionId, String actor, String actorRole) {
        Grade grade = loadGrade(studentNumber, sectionId);
        if (grade == null) {
            return "ERROR: Demo grade row was not found.";
        }
        if (isLocked(grade)) {
            return "ERROR: Approved demo grades are already locked.";
        }
        if (!"DRAFT".equalsIgnoreCase(statusOrDefault(grade.getStatus(), "DRAFT"))
            && !"REJECTED".equalsIgnoreCase(statusOrDefault(grade.getStatus(), "DRAFT"))) {
            return "ERROR: Only draft demo grades can be submitted.";
        }
        if (!hasCommittedEnrollment(studentNumber, sectionId)) {
            return "ERROR: Demo grade entry is available only for committed enrolled subjects.";
        }
        if (!hasEditableOutcome(grade)) {
            return "ERROR: Enter full components or use the quick final grade before submitting.";
        }

        GradeRecordEventService.GradeSnapshot before = GradeRecordEventService.snapshotOf(grade);
        grade.setStatus("SUBMITTED");
        gradeRepository.saveAndFlush(grade);
        gradeRecordEventService.recordEvent(
            grade,
            null,
            "DEMO_GRADE_SUBMITTED",
            "SUBMITTED",
            actor,
            actorRole,
            "Dean submitted demo grades for registrar review.",
            before,
            GradeRecordEventService.snapshotOf(grade));
        return "SUCCESS: Demo grades submitted.";
    }

    @Transactional
    public String approve(String studentNumber, int sectionId, String actor, String actorRole) {
        Grade grade = loadGrade(studentNumber, sectionId);
        if (grade == null) {
            return "ERROR: Demo grade row was not found.";
        }
        if (!"SUBMITTED".equalsIgnoreCase(statusOrDefault(grade.getStatus(), "DRAFT"))) {
            return "ERROR: Only submitted demo grades can be approved.";
        }
        if (!hasCommittedEnrollment(studentNumber, sectionId)) {
            return "ERROR: Demo grade entry is available only for committed enrolled subjects.";
        }

        GradeRecordEventService.GradeSnapshot before = GradeRecordEventService.snapshotOf(grade);
        Double finalGrade = resolveDemoFinalGrade(grade);
        if (finalGrade == null) {
            return "ERROR: Demo grade row has no entered score to approve.";
        }
        String finalRemarks = resolveDemoFinalRemarks(grade, finalGrade);
        lockOfficialOutcome(grade, finalGrade, finalRemarks, "DEMO_GRADE_APPROVED");
        Grade afterLock = loadGrade(studentNumber, sectionId);
        if (afterLock != null) {
            afterLock.setStatus("SUBMITTED");
            gradeRepository.saveAndFlush(afterLock);
            gradeRecordEventService.recordEvent(
                afterLock,
                null,
                "DEMO_GRADE_APPROVED",
                "FINALIZED",
                actor,
                actorRole,
                "Registrar approved demo grade entry.",
                before,
                GradeRecordEventService.snapshotOf(afterLock));
        }
        return "SUCCESS: Demo grade approved.";
    }

    @Transactional
    public String reject(String studentNumber, int sectionId, String actor, String actorRole, String note) {
        Grade grade = loadGrade(studentNumber, sectionId);
        if (grade == null) {
            return "ERROR: Demo grade row was not found.";
        }
        if (!"SUBMITTED".equalsIgnoreCase(statusOrDefault(grade.getStatus(), "DRAFT"))) {
            return "ERROR: Only submitted demo grades can be rejected.";
        }
        if (!hasCommittedEnrollment(studentNumber, sectionId)) {
            return "ERROR: Demo grade entry is available only for committed enrolled subjects.";
        }

        GradeRecordEventService.GradeSnapshot before = GradeRecordEventService.snapshotOf(grade);
        grade.setStatus("DRAFT");
        grade.setGradeLockStatus(null);
        grade.setGradeLockReason(cleanRejectReason(note));
        grade.setRegistrarFinalGrade(null);
        grade.setRegistrarFinalRemarks(null);
        grade.setRegistrarFinalizedAt(null);
        gradeRepository.saveAndFlush(grade);
        gradeRecordEventService.recordEvent(
            grade,
            null,
            "DEMO_GRADE_REJECTED",
            "DRAFT",
            actor,
            actorRole,
            cleanRejectReason(note),
            before,
            GradeRecordEventService.snapshotOf(grade));
        return "SUCCESS: Demo grade rejected.";
    }

    private Grade loadGrade(String studentNumber, int sectionId) {
        if (studentNumber == null || studentNumber.isBlank()) {
            return null;
        }
        Optional<Grade> existing = gradeRepository.findTopByStudentIdAndSectionIdOrderByIdDesc(studentNumber.trim(), sectionId);
        return existing.orElse(null);
    }

    private Grade loadOrCreateGrade(String studentNumber, int sectionId) {
        Grade grade = loadGrade(studentNumber, sectionId);
        return grade != null ? grade : createGrade(studentNumber, sectionId);
    }

    private Grade createGrade(String studentNumber, int sectionId) {
        if (!hasCommittedEnrollment(studentNumber, sectionId)) {
            return null;
        }
        Map<String, Object> student = queryStudent(studentNumber);
        Map<String, Object> section = querySection(sectionId);
        if (student == null || section == null) {
            return null;
        }
        Grade grade = new Grade();
        grade.setStudentId(studentNumber.trim());
        grade.setStudentName(resolveStudentName(student));
        grade.setSectionId(sectionId);
        grade.setCourseId(section.get("course_id") instanceof Number n ? n.intValue() : null);
        grade.setStatus("DRAFT");
        grade.setDateRecorded(LocalDateTime.now());
        gradeRepository.saveAndFlush(grade);
        return grade;
    }

    private Map<String, Object> queryStudent(String studentNumber) {
        try {
            return db.queryForMap("""
                SELECT student_number, real_name, first_name, last_name
                FROM students
                WHERE student_number = ?
                LIMIT 1
                """, studentNumber.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> querySection(int sectionId) {
        try {
            return db.queryForMap("""
                SELECT cs.section_id, cs.course_id, c.course_code, c.course_title, cs.term_id
                FROM class_sections cs
                JOIN courses c ON c.course_id = cs.course_id
                WHERE cs.section_id = ?
                LIMIT 1
                """, sectionId);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasCommittedEnrollment(String studentNumber, int sectionId) {
        try {
            Integer count = db.queryForObject("""
                SELECT COUNT(*)
                FROM student_enlistments se
                JOIN class_sections cs ON cs.section_id = se.section_id
                WHERE se.student_id = ? AND se.section_id = ?
                """ + enlistmentSchemaService.enlistmentStatusFilter(EnlistmentSchemaService.Scope.COMMITTED_ONLY, "se"),
                Integer.class,
                studentNumber.trim(),
                sectionId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildTermLabel(Map<String, Object> row) {
        Object year = row.get("academic_year");
        Object sem = row.get("semester_number");
        Object termName = row.get("term_name");
        if (year != null && sem != null) {
            return "A.Y. " + year + " - " + sem;
        }
        return termName != null ? termName.toString() : "Current Term";
    }

    private String workflowState(Map<String, Object> row) {
        if (isLocked(row)) {
            return "APPROVED";
        }
        String status = statusOrDefault(row.get("status"), "DRAFT");
        if ("SUBMITTED".equalsIgnoreCase(status)) {
            return "SUBMITTED";
        }
        String reason = defaultString(row.get("grade_lock_reason"));
        if (reason.startsWith("DEMO_GRADE_REJECTED")) {
            return "REJECTED";
        }
        return "DRAFT";
    }

    private String displayGrade(Map<String, Object> row) {
        Object finalGrade = row.get("registrar_final_grade");
        if (finalGrade instanceof Number number) {
            return formatGrade(number.doubleValue());
        }
        Object semestral = row.get("semestral_grade");
        if (semestral instanceof Number number) {
            return formatGrade(number.doubleValue());
        }
        return "-";
    }

    private String displayRemarks(Map<String, Object> row) {
        Object finalRemarks = row.get("registrar_final_remarks");
        if (finalRemarks != null && !finalRemarks.toString().isBlank()) {
            return finalRemarks.toString();
        }
        Object remarks = row.get("remarks");
        return remarks != null && !remarks.toString().isBlank() ? remarks.toString() : "Ongoing";
    }

    private boolean hasFullEntry(Map<String, Object> row) {
        return row.get("prelim") instanceof Number
            && row.get("midterm") instanceof Number
            && row.get("final_grade") instanceof Number;
    }

    private boolean hasEditableOutcome(Map<String, Object> row) {
        return row.get("semestral_grade") instanceof Number || hasFullEntry(row);
    }

    private boolean hasEditableOutcome(Grade grade) {
        return grade != null && (grade.getSemestralGrade() != null
            || (grade.getPrelim() != null && grade.getMidterm() != null && grade.getFinalGrade() != null));
    }

    private boolean isLocked(Map<String, Object> row) {
        Object lock = row.get("grade_lock_status");
        if (lock == null) {
            return false;
        }
        String normalized = lock.toString().trim().toUpperCase();
        return "LOCKED".equals(normalized) || "FINALIZED".equals(normalized);
    }

    private boolean isLocked(Grade grade) {
        if (grade == null || grade.getGradeLockStatus() == null) {
            return false;
        }
        String normalized = grade.getGradeLockStatus().trim().toUpperCase();
        return "LOCKED".equals(normalized) || "FINALIZED".equals(normalized);
    }

    private Double resolveDemoFinalGrade(Grade grade) {
        if (grade.getSemestralGrade() != null) {
            return grade.getSemestralGrade().doubleValue();
        }
        Double prelim = grade.getPrelim() != null ? grade.getPrelim().doubleValue() : null;
        Double midterm = grade.getMidterm() != null ? grade.getMidterm().doubleValue() : null;
        Double finals = grade.getFinalGrade() != null ? grade.getFinalGrade().doubleValue() : null;
        return derivePointGrade(prelim, midterm, finals);
    }

    private String resolveDemoFinalRemarks(Grade grade, Double finalGrade) {
        if (grade.getSemestralGrade() != null && (grade.getPrelim() == null || grade.getMidterm() == null || grade.getFinalGrade() == null)) {
            return remarkForPointGrade(finalGrade);
        }
        Double prelim = grade.getPrelim() != null ? grade.getPrelim().doubleValue() : null;
        Double midterm = grade.getMidterm() != null ? grade.getMidterm().doubleValue() : null;
        Double finals = grade.getFinalGrade() != null ? grade.getFinalGrade().doubleValue() : null;
        return deriveDraftRemarks(prelim, midterm, finals, finalGrade);
    }

    private Double derivePointGrade(Double prelim, Double midterm, Double finals) {
        int count = 0;
        double sum = 0;
        if (prelim != null && prelim > 0) { sum += prelim; count++; }
        if (midterm != null && midterm > 0) { sum += midterm; count++; }
        if (finals != null && finals > 0) { sum += finals; count++; }
        return count > 0 ? convertToPointGrade(sum / count) : null;
    }

    private String deriveDraftRemarks(Double prelim, Double midterm, Double finals, Double pointGrade) {
        boolean allPresent = prelim != null && prelim > 0
            && midterm != null && midterm > 0
            && finals != null && finals > 0;
        if (!allPresent) {
            return "Ongoing";
        }
        return remarkForPointGrade(pointGrade);
    }

    private void lockOfficialOutcome(Grade grade, Double finalGrade, String finalRemarks, String reason) {
        if (grade.getPreviousGrade() == null || grade.getPreviousGrade().isEmpty()) {
            grade.setPreviousGrade(defaultString(grade.getRemarks(), ""));
        }
        grade.setSemestralGrade(finalGrade != null ? BigDecimal.valueOf(finalGrade) : null);
        grade.setRemarks(finalRemarks);
        grade.setRegistrarFinalGrade(finalGrade != null ? BigDecimal.valueOf(finalGrade) : null);
        grade.setRegistrarFinalRemarks(finalRemarks);
        grade.setGradeLockStatus("FINALIZED");
        grade.setGradeLockReason(reason);
        grade.setRegistrarFinalizedAt(LocalDateTime.now());
        if (grade.getDateRecorded() == null) {
            grade.setDateRecorded(LocalDateTime.now());
        }
        syncLegacyAcademicStatus(grade, finalRemarks);
        gradeRepository.saveAndFlush(grade);
    }

    private void syncLegacyAcademicStatus(Grade grade, String finalRemarks) {
        if (finalRemarks == null || finalRemarks.isBlank()) {
            return;
        }
        String normalized = finalRemarks.trim().toUpperCase();
        if ("PASSED".equals(normalized) || "FAILED".equals(normalized) || "INC".equals(normalized)) {
            grade.setStatus(normalized);
        }
    }

    private Double parseScore(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return Double.parseDouble(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double parsePointGrade(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return Double.parseDouble(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double convertToPointGrade(double avg) {
        if (avg >= 98) return 1.00;
        if (avg >= 95) return 1.25;
        if (avg >= 92) return 1.50;
        if (avg >= 89) return 1.75;
        if (avg >= 86) return 2.00;
        if (avg >= 83) return 2.25;
        if (avg >= 80) return 2.50;
        if (avg >= 77) return 2.75;
        if (avg >= 75) return 3.00;
        return 5.00;
    }

    private String remarkForPointGrade(Double pointGrade) {
        if (pointGrade == null) {
            return "INC";
        }
        return pointGrade > 3.0 ? "Failed" : "Passed";
    }

    private String resolveStudentName(Map<String, Object> student) {
        Object realName = student.get("real_name");
        if (realName != null && !realName.toString().isBlank()) {
            return realName.toString().trim();
        }
        String firstName = defaultString(student.get("first_name"));
        String lastName = defaultString(student.get("last_name"));
        String combined = (firstName + " " + lastName).trim();
        if (!combined.isBlank()) {
            return combined;
        }
        Object studentNumber = student.get("student_number");
        return studentNumber != null ? studentNumber.toString() : null;
    }

    private String formatGrade(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private String cleanRejectReason(String note) {
        String clean = clean(note);
        return clean == null ? "DEMO_GRADE_REJECTED" : "DEMO_GRADE_REJECTED: " + clean;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }

    private String defaultString(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String statusOrDefault(Object value, String fallback) {
        String normalized = defaultString(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Integer getActiveTermId() {
        try {
            return db.queryForObject(
                "SELECT term_id FROM academic_terms WHERE is_active = 1 ORDER BY term_id DESC LIMIT 1",
                Integer.class);
        } catch (Exception e) {
            return null;
        }
    }
}
