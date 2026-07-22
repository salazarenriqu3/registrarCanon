package com.iuims.registrar.service.academic;
import com.iuims.registrar.domain.academic.TermTransitionEvent;
import com.iuims.registrar.entity.AcademicTerm;
import com.iuims.registrar.entity.AcademicTermPolicy;
import com.iuims.registrar.entity.ClassSection;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.GradingTermWindow;
import com.iuims.registrar.entity.Program;
import com.iuims.registrar.entity.Student;
import com.iuims.registrar.entity.TermTransitionAudit;
import com.iuims.registrar.entity.VpaaExtension;
import com.iuims.registrar.repository.CurriculumTemplateRepository;
import com.iuims.registrar.repository.DepartmentRepository;
import com.iuims.registrar.repository.GradeChangeRequestRepository;
import com.iuims.registrar.repository.ProgramRepository;
import com.iuims.registrar.repository.VpaaExtensionRepository;
import com.iuims.registrar.dto.ClassInfoDto;
import com.iuims.registrar.entity.ClassSchedule;
import com.iuims.registrar.entity.Grade;
import com.iuims.registrar.entity.GradeChangeRequest;
import com.iuims.registrar.repository.AcademicGradingRepository;
import com.iuims.registrar.repository.AcademicTermPolicyRepository;
import com.iuims.registrar.repository.AcademicTermRepository;
import com.iuims.registrar.repository.ClassScheduleRepository;
import com.iuims.registrar.repository.ClassSectionRepository;
import com.iuims.registrar.repository.CourseRepository;
import com.iuims.registrar.repository.CurriculumCatalogRepository;
import com.iuims.registrar.repository.CurriculumCourseRepository;
import com.iuims.registrar.repository.GradeRepository;
import com.iuims.registrar.repository.GradingTermWindowRepository;
import com.iuims.registrar.repository.StudentRepository;
import com.iuims.registrar.repository.SysUserRepository;
import com.iuims.registrar.repository.TermTransitionAuditRepository;
import com.iuims.registrar.service.support.StudentProfileService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import java.math.BigDecimal;

import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.service.finance.TermFeeAdminService;
import com.iuims.registrar.service.forms.StudentDocumentTrailService;
import com.iuims.registrar.support.PolicySettings;
import com.iuims.registrar.support.GradeOutcomeSql;
import com.iuims.registrar.service.support.EnlistmentSchemaService;
import com.iuims.registrar.service.curriculum.CurriculumLoadPolicyService;
import com.iuims.registrar.repository.SystemSettingRepository;
import com.iuims.registrar.entity.SystemSetting;
import com.iuims.registrar.service.academic.TermRolloverDispositionService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AcademicGradingService {

    public record ClassPage(List<Map<String, Object>> rows,
                            int page,
                            int pageSize,
                            int totalRows,
                            int totalPages) { }

    private final JdbcTemplate db;
    private final TermFeeAdminService termFeeAdminService;
    private final AcademicTermRepository academicTermRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final TermTransitionAuditRepository termTransitionAuditRepository;
    private final GradeRepository gradeRepository;

    private final com.iuims.registrar.repository.ProgramRepository programRepository;
    private final com.iuims.registrar.repository.CurriculumTemplateRepository curriculumTemplateRepository;
    private final com.iuims.registrar.repository.CurriculumCourseRepository curriculumCourseRepository;
    private final com.iuims.registrar.repository.CurriculumCatalogRepository curriculumCatalogRepository;
    private final GradingTermWindowRepository gradingTermWindowRepository;
    private final AcademicTermPolicyRepository academicTermPolicyRepository;
    private final ClassSectionRepository classSectionRepository;
    private final ClassScheduleRepository classScheduleRepository;
    private final AcademicGradingRepository academicGradingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final GradeRecordEventService gradeRecordEventService;
    private final EnlistmentSchemaService enlistmentSchemaService;
    private final ScheduleConflictValidator scheduleConflictValidator;
    private final ObjectProvider<CurriculumLoadPolicyService> curriculumLoadPolicyService;
    
    // Phase 3.5: Entity Expansion Repositories
    private final com.iuims.registrar.repository.SysUserRepository sysUserRepository;
    private final com.iuims.registrar.repository.StudentRepository studentRepository;
    private final com.iuims.registrar.service.support.StudentProfileService studentProfileService;
    private final com.iuims.registrar.repository.CourseRepository courseRepository;
    private final com.iuims.registrar.repository.VpaaExtensionRepository vpaaExtensionRepository;
    private final com.iuims.registrar.repository.GradeChangeRequestRepository gradeChangeRequestRepository;
    private final com.iuims.registrar.repository.DepartmentRepository departmentRepository;
    private final StudentDocumentTrailService documentTrailService;
    private final TermRolloverDispositionService termRolloverDispositionService;

    @Value("${registrar.accounts.temporary-password:}")
    private String configuredTemporaryPassword;

    public AcademicGradingService(
            JdbcTemplate db,
            TermFeeAdminService termFeeAdminService,
            AcademicTermRepository academicTermRepository,
            SystemSettingRepository systemSettingRepository,
            TermTransitionAuditRepository termTransitionAuditRepository,
            GradeRepository gradeRepository,
            ClassSectionRepository classSectionRepository,
            ClassScheduleRepository classScheduleRepository,
            AcademicGradingRepository academicGradingRepository,
            ApplicationEventPublisher eventPublisher,
            GradeRecordEventService gradeRecordEventService,
            EnlistmentSchemaService enlistmentSchemaService,
            com.iuims.registrar.repository.SysUserRepository sysUserRepository,
            com.iuims.registrar.repository.StudentRepository studentRepository,
            com.iuims.registrar.service.support.StudentProfileService studentProfileService,
            com.iuims.registrar.repository.CourseRepository courseRepository,
            com.iuims.registrar.repository.VpaaExtensionRepository vpaaExtensionRepository,
            com.iuims.registrar.repository.GradeChangeRequestRepository gradeChangeRequestRepository,
            com.iuims.registrar.repository.DepartmentRepository departmentRepository,
            com.iuims.registrar.repository.ProgramRepository programRepository,
            com.iuims.registrar.repository.CurriculumTemplateRepository curriculumTemplateRepository,
            com.iuims.registrar.repository.CurriculumCourseRepository curriculumCourseRepository,
            com.iuims.registrar.repository.CurriculumCatalogRepository curriculumCatalogRepository,
            GradingTermWindowRepository gradingTermWindowRepository,
            AcademicTermPolicyRepository academicTermPolicyRepository,
            StudentDocumentTrailService documentTrailService,
            TermRolloverDispositionService termRolloverDispositionService,
            ObjectProvider<CurriculumLoadPolicyService> curriculumLoadPolicyService) {
        this.db = db;
        this.termFeeAdminService = termFeeAdminService;
        this.academicTermRepository = academicTermRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.termTransitionAuditRepository = termTransitionAuditRepository;
        this.gradeRepository = gradeRepository;
        this.classSectionRepository = classSectionRepository;
        this.classScheduleRepository = classScheduleRepository;
        this.academicGradingRepository = academicGradingRepository;
        this.eventPublisher = eventPublisher;
        this.gradeRecordEventService = gradeRecordEventService;
        this.enlistmentSchemaService = enlistmentSchemaService;
        this.scheduleConflictValidator = new ScheduleConflictValidator(db);
        this.sysUserRepository = sysUserRepository;
        this.studentRepository = studentRepository;
        this.studentProfileService = studentProfileService;
        this.courseRepository = courseRepository;
        this.vpaaExtensionRepository = vpaaExtensionRepository;
        this.gradeChangeRequestRepository = gradeChangeRequestRepository;
        this.departmentRepository = departmentRepository;
        this.programRepository = programRepository;
        this.curriculumTemplateRepository = curriculumTemplateRepository;
        this.curriculumCourseRepository = curriculumCourseRepository;
        this.curriculumCatalogRepository = curriculumCatalogRepository;
        this.gradingTermWindowRepository = gradingTermWindowRepository;
        this.academicTermPolicyRepository = academicTermPolicyRepository;
        this.documentTrailService = documentTrailService;
        this.termRolloverDispositionService = termRolloverDispositionService;
        this.curriculumLoadPolicyService = curriculumLoadPolicyService;
    }

    // ==========================================
    // 1. GRADING COMPUTATIONS
    // ==========================================
    @Transactional
    public Map<String, Object> saveGradeAsync(int gradeId, String prelimStr, String midStr, String finalStr) {
        return saveGradeAsync(gradeId, prelimStr, midStr, finalStr, "faculty", "Faculty");
    }

    @Transactional
    public Map<String, Object> saveGradeAsync(int gradeId, String prelimStr, String midStr, String finalStr,
                                              String actor, String actorRole) {

        double p = parseScore(prelimStr); double m = parseScore(midStr); double f = parseScore(finalStr);
        
        Grade grade = gradeRepository.findById(gradeId).orElse(null);
        if (grade == null) return new HashMap<>();
        GradeRecordEventService.GradeSnapshot before = GradeRecordEventService.snapshotOf(grade);

        if (isRegistrarLocked(grade.getGradeLockStatus())) {
            grade.setPrelim(BigDecimal.valueOf(p));
            grade.setMidterm(BigDecimal.valueOf(m));
            grade.setFinalGrade(BigDecimal.valueOf(f));
            gradeRepository.saveAndFlush(grade);
            return effectiveGradeResult(grade, p, m, f);
        }

        Map<String, Object> windows = getGradingWindows(resolveTermIdForGrade(gradeId));
        Integer sectionId = grade.getSectionId();
        boolean bypassWindow = sectionId != null && isExtensionApproved(sectionId);
        if (!bypassWindow) {
            Map<String, Object> blocked = blockedPeriodSave(grade, windows, p, m, f);
            if (blocked != null) return blocked;
        }

        boolean allPeriodsPassed = !(boolean)windows.get("prelim_open") && !(boolean)windows.get("midterm_open") && !(boolean)windows.get("final_open");

        int count = 0; double sum = 0;
        if (p > 0) { sum += p; count++; }
        if (m > 0) { sum += m; count++; }
        if (f > 0) { sum += f; count++; }

        double pointGrade = (count > 0) ? convertToPointGrade(sum / count) : 0.0;
        String remarks = "Ongoing";
        if (allPeriodsPassed) { remarks = (p == 0 || m == 0 || f == 0) ? "INC" : ((pointGrade > 3.0) ? "Failed" : "Passed"); } 
        else { remarks = (count == 3) ? ((pointGrade > 3.0) ? "Failed" : "Passed") : "Ongoing"; }
        
        grade.setPrelim(BigDecimal.valueOf(p));
        grade.setMidterm(BigDecimal.valueOf(m));
        grade.setFinalGrade(BigDecimal.valueOf(f));
        grade.setSemestralGrade(BigDecimal.valueOf(pointGrade));
        grade.setRemarks(remarks);
        if (grade.getDateRecorded() == null) {
            grade.setDateRecorded(java.time.LocalDateTime.now());
        }
        syncLegacyAcademicStatus(grade, remarks);
        gradeRepository.saveAndFlush(grade);
        gradeRecordEventService.recordEvent(
            grade,
            null,
            "GRADE_DRAFT_SAVED",
            defaultLifecycleStatus(grade),
            actor,
            actorRole,
            "Faculty draft grade save.",
            before,
            GradeRecordEventService.snapshotOf(grade));

        Map<String, Object> result = new HashMap<>();
        result.put("semestral_grade", remarks.equals("INC") ? "INC" : (pointGrade > 0 ? String.format("%.2f", pointGrade) : "-"));
        result.put("remarks", remarks);
        result.put("prelim_point", p > 0 ? String.format("%.2f", convertToPointGrade(p)) : "");
        result.put("midterm_point", m > 0 ? String.format("%.2f", convertToPointGrade(m)) : "");
        result.put("final_point", f > 0 ? String.format("%.2f", convertToPointGrade(f)) : "");
        result.put("success", true);
        return result;
    }
    
    private double parseScore(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; } }

    private boolean scoreChanged(double incoming, BigDecimal existing) {
        double current = existing != null ? existing.doubleValue() : 0.0;
        return Double.compare(incoming, current) != 0;
    }

    private boolean isRegistrarLocked(String gradeLockStatus) {
        if (gradeLockStatus == null || gradeLockStatus.isBlank()) {
            return false;
        }
        String normalized = gradeLockStatus.trim().toUpperCase();
        return "LOCKED".equals(normalized) || "FINALIZED".equals(normalized);
    }

    private String defaultLifecycleStatus(Grade grade) {
        if (grade == null) {
            return "DRAFT";
        }
        if (isRegistrarLocked(grade.getGradeLockStatus())) {
            return "FINALIZED";
        }
        return grade.getStatus() != null && !grade.getStatus().isBlank()
            ? grade.getStatus().trim().toUpperCase()
            : "DRAFT";
    }

    private Map<String, Object> blockedPeriodSave(Grade grade, Map<String, Object> windows,
                                                  double prelim, double midterm, double finals) {
        if ("SUBMITTED".equalsIgnoreCase(grade.getStatus())) {
            Map<String, Object> result = effectiveGradeResult(grade,
                grade.getPrelim() != null ? grade.getPrelim().doubleValue() : 0.0,
                grade.getMidterm() != null ? grade.getMidterm().doubleValue() : 0.0,
                grade.getFinalGrade() != null ? grade.getFinalGrade().doubleValue() : 0.0);
            result.put("error", "Class grades are submitted. Request a grade change instead.");
            return result;
        }
        if (scoreChanged(prelim, grade.getPrelim()) && !(boolean) windows.get("prelim_open")) {
            return periodClosedResult(grade, "Prelim");
        }
        if (scoreChanged(midterm, grade.getMidterm()) && !(boolean) windows.get("midterm_open")) {
            return periodClosedResult(grade, "Midterm");
        }
        if (scoreChanged(finals, grade.getFinalGrade()) && !(boolean) windows.get("final_open")) {
            return periodClosedResult(grade, "Finals");
        }
        return null;
    }

    private Map<String, Object> periodClosedResult(Grade grade, String periodLabel) {
        Map<String, Object> result = effectiveGradeResult(grade,
            grade.getPrelim() != null ? grade.getPrelim().doubleValue() : 0.0,
            grade.getMidterm() != null ? grade.getMidterm().doubleValue() : 0.0,
            grade.getFinalGrade() != null ? grade.getFinalGrade().doubleValue() : 0.0);
        result.put("error", periodLabel + " grading period is closed.");
        return result;
    }



    private Map<String, Object> effectiveGradeResult(Grade grade, double prelim, double midterm, double finals) {
        BigDecimal sg = grade.getRegistrarFinalGrade() != null ? grade.getRegistrarFinalGrade() : grade.getSemestralGrade();
        double pointGrade = sg != null ? sg.doubleValue() : 0.0;
        String remarks = grade.getRegistrarFinalRemarks() != null ? grade.getRegistrarFinalRemarks() : (grade.getRemarks() != null ? grade.getRemarks() : "Ongoing");
        
        Map<String, Object> result = new HashMap<>();
        result.put("semestral_grade", "INC".equals(remarks) ? "INC" : (pointGrade > 0 ? String.format("%.2f", pointGrade) : "-"));
        result.put("remarks", remarks);
        result.put("prelim_point", prelim > 0 ? String.format("%.2f", convertToPointGrade(prelim)) : "");
        result.put("midterm_point", midterm > 0 ? String.format("%.2f", convertToPointGrade(midterm)) : "");
        result.put("final_point", finals > 0 ? String.format("%.2f", convertToPointGrade(finals)) : "");
        return result;
    }

    private void lockRegistrarOutcome(Number gradeId, Double finalGrade, String finalRemarks, String reason) {
        Grade grade = gradeRepository.findById(gradeId.intValue()).orElse(null);
        if (grade == null) return;
        
        if (grade.getPreviousGrade() == null || grade.getPreviousGrade().isEmpty()) {
            grade.setPreviousGrade(grade.getRemarks() != null ? grade.getRemarks() : "");
        }
        
        grade.setSemestralGrade(finalGrade != null ? BigDecimal.valueOf(finalGrade) : null);
        grade.setRemarks(finalRemarks);
        grade.setRegistrarFinalGrade(finalGrade != null ? BigDecimal.valueOf(finalGrade) : null);
        grade.setRegistrarFinalRemarks(finalRemarks);
        grade.setGradeLockStatus("FINALIZED");
        grade.setGradeLockReason(reason);
        grade.setRegistrarFinalizedAt(java.time.LocalDateTime.now());
        if (grade.getDateRecorded() == null) {
            grade.setDateRecorded(java.time.LocalDateTime.now());
        }
        syncLegacyAcademicStatus(grade, finalRemarks);
        
        gradeRepository.saveAndFlush(grade);
    }

    private void syncLegacyAcademicStatus(Grade grade, String finalRemarks) {
        if (finalRemarks == null || finalRemarks.isBlank()) return;
        String normalized = finalRemarks.trim().toUpperCase();
        if ("PASSED".equals(normalized) || "FAILED".equals(normalized) || "INC".equals(normalized)) {
            grade.setStatus(normalized);
        }
    }

    private Double parsePointGrade(Object raw) {
        try {
            if (raw == null || raw.toString().trim().isEmpty()) return null;
            return Double.parseDouble(raw.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String remarkForPointGrade(Double pointGrade) {
        if (pointGrade == null) return "INC";
        return pointGrade > 3.0 ? "Failed" : "Passed";
    }

    private String normalizeRequestType(String requestType) {
        if (requestType == null || requestType.isBlank()) return "FINAL_GRADE_CORRECTION";
        String normalized = requestType.trim().toUpperCase();
        return switch (normalized) {
            case "COMPONENT_GRADE_CORRECTION", "REOPEN_FOR_EDIT" -> normalized;
            default -> "FINAL_GRADE_CORRECTION";
        };
    }

    private Double parseScoreObject(Object raw) {
        try {
            if (raw == null || raw.toString().trim().isEmpty()) return null;
            return Double.parseDouble(raw.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }
    
    private double convertToPointGrade(double avg) {
        if(avg >= 98) return 1.00; if(avg >= 95) return 1.25; if(avg >= 92) return 1.50; if(avg >= 89) return 1.75; if(avg >= 86) return 2.00;
        if(avg >= 83) return 2.25; if(avg >= 80) return 2.50; if(avg >= 77) return 2.75; if(avg >= 75) return 3.00; return 5.00;
    }

    // ==========================================
    // 2. ACADEMIC RECORDS & HISTORY
    // ==========================================
    public Map<String, List<Map<String, Object>>> getStudentAcademicHistory(int sid) {
        try {
            String studentNumber = sysUserRepository.findById(sid)
                .map(com.iuims.registrar.entity.SysUser::getUsername)
                .orElse(null);
            
            if (studentNumber == null) return new LinkedHashMap<>();

            List<Grade> grades = gradeRepository.findByStudentId(studentNumber);
            if (grades.isEmpty()) {
                grades = gradeRepository.findByStudentName(studentNumber); // Fallback
            }

            List<Map<String, Object>> raw = new ArrayList<>();
            for (Grade g : grades) {
                Map<String, Object> r = new HashMap<>();
                
                String courseCode = "Unknown";
                String courseTitle = "Unknown";
                int yearLevel = 1;

                if (g.getCourseId() != null) {
                    com.iuims.registrar.entity.Course c = courseRepository.findById(g.getCourseId()).orElse(null);
                    if (c != null) {
                        courseCode = c.getCourseCode();
                        courseTitle = c.getCourseTitle();
                        
                        List<com.iuims.registrar.entity.CurriculumCourse> ccs = curriculumCourseRepository.findByCourseId(c.getCourseId());
                        if (!ccs.isEmpty()) {
                            yearLevel = ccs.stream().mapToInt(com.iuims.registrar.entity.CurriculumCourse::getYearLevel).min().orElse(1);
                        }
                    }
                }
                
                r.put("course_code", courseCode);
                r.put("description", courseTitle);
                
                double p = g.getPrelim() != null ? g.getPrelim().doubleValue() : 0.0;
                double m = g.getMidterm() != null ? g.getMidterm().doubleValue() : 0.0;
                double f = g.getFinalGrade() != null ? g.getFinalGrade().doubleValue() : 0.0;
                double sg = (g.getRegistrarFinalGrade() != null) ? g.getRegistrarFinalGrade().doubleValue() : 
                            (g.getSemestralGrade() != null ? g.getSemestralGrade().doubleValue() : 0.0);

                r.put("prelim", g.getPrelim());
                r.put("midterm", g.getMidterm());
                r.put("final", g.getFinalGrade());
                r.put("semestral_grade", sg > 0 ? java.math.BigDecimal.valueOf(sg) : null);
                
                String remarks = g.getRegistrarFinalRemarks() != null ? g.getRegistrarFinalRemarks() : g.getRemarks();
                r.put("remarks", remarks);
                r.put("previous_grade", g.getPreviousGrade());
                r.put("registrar_final_grade", g.getRegistrarFinalGrade());
                r.put("registrar_final_remarks", g.getRegistrarFinalRemarks());
                r.put("grade_lock_status", g.getGradeLockStatus() != null ? g.getGradeLockStatus() : "");
                r.put("row_locked", isRegistrarLocked(g.getGradeLockStatus()));
                r.put("curriculum_year", String.valueOf(yearLevel));

                r.put("prelim_score", p > 0 ? String.valueOf(p) : "-");
                r.put("midterm_score", m > 0 ? String.valueOf(m) : "-");
                r.put("finals_score", f > 0 ? String.valueOf(f) : "-");

                String prev = g.getPreviousGrade();
                r.put("semestral_score", (prev != null && !prev.isEmpty())
                    ? ((sg > 0 ? String.format("%.2f", sg) : "-") + " (Prev: " + prev + ")")
                    : (sg > 0 ? String.format("%.2f", sg) : "-"));
                    
                r.put("prelim_point", p > 0 ? String.format("%.2f", convertToPointGrade(p)) : "");
                r.put("midterm_point", m > 0 ? String.format("%.2f", convertToPointGrade(m)) : "");
                r.put("final_point", f > 0 ? String.format("%.2f", convertToPointGrade(f)) : "");
                
                r.put("_sort_year", yearLevel);
                raw.add(r);
            }

            raw.sort((a, b) -> Integer.compare((int) b.get("_sort_year"), (int) a.get("_sort_year")));

            Map<String, List<Map<String, Object>>> h = new LinkedHashMap<>();
            for (Map<String, Object> r : raw) {
                String cy = r.get("curriculum_year").toString();
                h.computeIfAbsent(cy, k -> new ArrayList<>()).add(r);
            }
            return h;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public int getDynamicMaxUnits(int sid) {
        String studentNumber = sysUserRepository.findById(sid)
            .map(com.iuims.registrar.entity.SysUser::getUsername)
            .orElseThrow(() -> new IllegalStateException("Student account was not found."));
        BigDecimal maxUnits = requireCurriculumLoadPolicyService().effectiveMaximumUnits(studentNumber);
        return maxUnits.setScale(0, java.math.RoundingMode.CEILING).intValue();
    }

    public boolean isGraduatingStudent(String studentNumber) {
        return requireCurriculumLoadPolicyService().isGraduatingStudent(studentNumber);
    }

    /** Use {@link #isGraduatingStudent(String)} for live registrar decisions. */
    @Deprecated
    public boolean isGraduating(String programCode, int yearLevel) {
        throw new IllegalStateException("Graduating status now requires a student curriculum assignment.");
    }

    private CurriculumLoadPolicyService requireCurriculumLoadPolicyService() {
        CurriculumLoadPolicyService service = curriculumLoadPolicyService.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Curriculum load policy service is required for unit-load decisions.");
        }
        return service;
    }

    // ==========================================
    // 3. FACULTY & SYSTEM SETTINGS
    // ==========================================
    @Cacheable("gradingWindows")
    public Map<String, Object> getGradingWindows() {
        return getGradingWindows(getActiveTermId());
    }

    @Cacheable(value = "gradingWindows", key = "'term:' + #termId")
    public Map<String, Object> getGradingWindows(Integer termId) {


        List<SystemSetting> list = systemSettingRepository.findAll();
        Map<String, Object> settings = new HashMap<>();
        for (SystemSetting row : list) { settings.put(row.getSettingKey(), row.getSettingValue()); }
        settings.put(PolicySettings.ACCOUNTING_BLOCK_THRESHOLD, String.valueOf(PolicySettings.accountingBlockThreshold(db)));
        settings.put(PolicySettings.ADMISSION_MIN_PAYMENT, String.valueOf(PolicySettings.admissionMinPayment(db)));
        settings.put(PolicySettings.DOWNPAYMENT_THRESHOLD, String.valueOf(PolicySettings.downpaymentThreshold(db)));
        settings.put(PolicySettings.DOWNPAYMENT_PERCENT, String.valueOf(PolicySettings.downpaymentPercent(db)));

        int resolvedTermId = termId != null && termId > 0 ? termId : getActiveTermId();
        settings.put("term_id", resolvedTermId);
        settings.put("term_scoped", false);
        overlayTermGradingWindows(settings, resolvedTermId);
        overlayTermPolicySettings(settings, resolvedTermId);

        java.time.LocalDate today = java.time.LocalDate.now();
        boolean pDate = isDateInRange(today, (String)settings.get("PRELIM_START"), (String)settings.get("PRELIM_END"));
        settings.put("prelim_open", "FORCE_OPEN".equals(normalizeOverride(settings.get("PRELIM_OVERRIDE"))) ? true : ("FORCE_CLOSED".equals(normalizeOverride(settings.get("PRELIM_OVERRIDE"))) ? false : pDate));

        boolean mDate = isDateInRange(today, (String)settings.get("MIDTERM_START"), (String)settings.get("MIDTERM_END"));
        settings.put("midterm_open", "FORCE_OPEN".equals(normalizeOverride(settings.get("MIDTERM_OVERRIDE"))) ? true : ("FORCE_CLOSED".equals(normalizeOverride(settings.get("MIDTERM_OVERRIDE"))) ? false : mDate));

        boolean fDate = isDateInRange(today, (String)settings.get("FINAL_START"), (String)settings.get("FINAL_END"));
        settings.put("final_open", "FORCE_OPEN".equals(normalizeOverride(settings.get("FINAL_OVERRIDE"))) ? true : ("FORCE_CLOSED".equals(normalizeOverride(settings.get("FINAL_OVERRIDE"))) ? false : fDate));
        return settings;
    }

    private void overlayTermGradingWindows(Map<String, Object> settings, int termId) {
        try {
            List<GradingTermWindow> windows = gradingTermWindowRepository.findByTermId(termId);
            for (GradingTermWindow w : windows) {
                String period = w.getGradingPeriod() != null ? w.getGradingPeriod().toUpperCase() : "";
                if (!period.equals("PRELIM") && !period.equals("MIDTERM") && !period.equals("FINAL")) {
                    continue;
                }
                settings.put(period + "_START", dateToString(w.getStartDate()));
                settings.put(period + "_END", dateToString(w.getEndDate()));
                settings.put(period + "_OVERRIDE", normalizeOverride(w.getOverrideStatus()));
                settings.put("term_scoped", true);
            }
        } catch (Exception ignored) {}
        settings.put("PRELIM_OVERRIDE", normalizeOverride(settings.get("PRELIM_OVERRIDE")));
        settings.put("MIDTERM_OVERRIDE", normalizeOverride(settings.get("MIDTERM_OVERRIDE")));
        settings.put("FINAL_OVERRIDE", normalizeOverride(settings.get("FINAL_OVERRIDE")));
    }

    private void overlayTermPolicySettings(Map<String, Object> settings, int termId) {
        String incConfigured = null;
        String midtermConfigured = null;
        try {
            AcademicTermPolicy policy = academicTermPolicyRepository.findById(termId).orElse(null);
            if (policy != null) {
                if (policy.getIncExpirationDate() != null) {
                    incConfigured = policy.getIncExpirationDate().toString();
                }
                if (policy.getMidtermExamDate() != null) {
                    midtermConfigured = policy.getMidtermExamDate().toString();
                }
            }
        } catch (Exception ignored) {}
        if (incConfigured != null && !incConfigured.isBlank()) {
            settings.put("INC_EXPIRATION_DATE", incConfigured);
            settings.put("INC_EXPIRATION_SOURCE", "TERM");
        }
        if (midtermConfigured != null && !midtermConfigured.isBlank()) {
            settings.put("MIDTERM_EXAM_DATE", midtermConfigured);
            settings.put("MIDTERM_EXAM_DATE_SOURCE", "TERM");
        } else {
            settings.put("MIDTERM_EXAM_DATE", null);
            settings.put("MIDTERM_EXAM_DATE_SOURCE", "UNSET");
        }
        if (incConfigured == null || incConfigured.isBlank()) {
            String fallback = fallbackIncExpirationDate(settings);
            settings.put("INC_EXPIRATION_DATE", fallback);
            settings.put("INC_EXPIRATION_SOURCE", fallback != null ? "FALLBACK_FINALS_PLUS_ONE_YEAR" : "UNSET");
        }
    }

    private String fallbackIncExpirationDate(Map<String, Object> settings) {
        try {
            Object finalEnd = settings.get("FINAL_END");
            if (finalEnd == null || finalEnd.toString().isBlank()) return null;
            return java.time.LocalDate.parse(finalEnd.toString()).plusYears(1).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String dateToString(Object value) {
        return value != null ? value.toString() : null;
    }

    private String normalizeOverride(Object value) {
        String raw = value != null ? value.toString().trim() : "";
        return raw.isEmpty() ? "AUTO" : raw;
    }

    private boolean isDateInRange(java.time.LocalDate date, String start, String end) {
        try { return !date.isBefore(java.time.LocalDate.parse(start)) && !date.isAfter(java.time.LocalDate.parse(end)); } catch (Exception ex) { return false; }
    }

    @CacheEvict(value = "gradingWindows", allEntries = true)
    public void updateSettings(Map<String, String> params) {
        savePolicySettings(params);
        Integer termId = parsePositiveInt(params.get("gradingTermId"));
        if (termId != null) {
            saveTermGradingWindow(termId, "PRELIM", params.get("PRELIM_START"), params.get("PRELIM_END"), params.get("PRELIM_OVERRIDE"));
            saveTermGradingWindow(termId, "MIDTERM", params.get("MIDTERM_START"), params.get("MIDTERM_END"), params.get("MIDTERM_OVERRIDE"));
            saveTermGradingWindow(termId, "FINAL", params.get("FINAL_START"), params.get("FINAL_END"), params.get("FINAL_OVERRIDE"));
            saveTermPolicySettings(termId, params.get("INC_EXPIRATION_DATE"), params.get("MIDTERM_EXAM_DATE"));
            return;
        }

        String[] keys = {"PRELIM_START", "PRELIM_END", "MIDTERM_START", "MIDTERM_END", "FINAL_START", "FINAL_END", "PRELIM_OVERRIDE", "MIDTERM_OVERRIDE", "FINAL_OVERRIDE"};
        for (String k : keys) {
            String val = params.get(k);
            if (val != null && !val.trim().isEmpty()) {
                SystemSetting setting = systemSettingRepository.findById(k).orElse(new SystemSetting(k, val));
                setting.setSettingValue(val);
                systemSettingRepository.save(setting);
            }
        }
        systemSettingRepository.flush();
    }

    private void savePolicySettings(Map<String, String> params) {
        PolicySettings.saveDecimal(db, PolicySettings.ACCOUNTING_BLOCK_THRESHOLD, params.get(PolicySettings.ACCOUNTING_BLOCK_THRESHOLD));
        PolicySettings.saveDecimal(db, PolicySettings.ADMISSION_MIN_PAYMENT, params.get(PolicySettings.ADMISSION_MIN_PAYMENT));
        PolicySettings.saveDecimal(db, PolicySettings.DOWNPAYMENT_THRESHOLD, params.get(PolicySettings.DOWNPAYMENT_THRESHOLD));
        PolicySettings.saveDecimal(db, PolicySettings.DOWNPAYMENT_PERCENT, params.get(PolicySettings.DOWNPAYMENT_PERCENT));
    }

    private void saveTermPolicySettings(int termId, String incExpirationDate, String midtermExamDate) {
        String cleanIncDate = blankToNull(incExpirationDate);
        String cleanMidtermDate = blankToNull(midtermExamDate);
        AcademicTermPolicy policy = academicTermPolicyRepository.findById(termId).orElse(new AcademicTermPolicy());
        policy.setTermId(termId);
        policy.setIncExpirationDate(cleanIncDate != null ? java.time.LocalDate.parse(cleanIncDate) : null);
        policy.setMidtermExamDate(cleanMidtermDate != null ? java.time.LocalDate.parse(cleanMidtermDate) : null);
        policy.setUpdatedAt(java.time.LocalDateTime.now());
        academicTermPolicyRepository.save(policy);
    }

    private void saveTermPolicySettings(int termId, String incExpirationDate) {
        saveTermPolicySettings(termId, incExpirationDate, null);
    }

    private void saveTermGradingWindow(int termId, String period, String startDate, String endDate, String overrideStatus) {
        String cleanStart = blankToNull(startDate);
        String cleanEnd = blankToNull(endDate);
        String cleanOverride = normalizeOverride(overrideStatus);
        
        GradingTermWindow window = gradingTermWindowRepository.findByTermIdAndGradingPeriod(termId, period);
        if (window == null) {
            window = new GradingTermWindow();
            window.setTermId(termId);
            window.setGradingPeriod(period);
        }
        
        if (cleanStart != null) window.setStartDate(java.time.LocalDate.parse(cleanStart));
        else window.setStartDate(null);
        
        if (cleanEnd != null) window.setEndDate(java.time.LocalDate.parse(cleanEnd));
        else window.setEndDate(null);
        
        window.setOverrideStatus(cleanOverride);
        window.setUpdatedAt(java.time.LocalDateTime.now());
        gradingTermWindowRepository.save(window);
    }

    private String blankToNull(String value) {
        return value != null && !value.trim().isEmpty() ? value.trim() : null;
    }

    private Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (Exception e) {
            return null;
        }
    }



    private Integer resolveTermIdForGrade(int gradeId) {
        try {
            return db.queryForObject(
                "SELECT cs.term_id FROM grades g JOIN class_sections cs ON cs.section_id = g.section_id WHERE g.id = ?",
                Integer.class, gradeId);
        } catch (Exception e) {
            return getActiveTermId();
        }
    }

    @Transactional
    public void toggleClassUnlock(int scheduleId, int unlockStatus) {
        try { classScheduleRepository.updateIsUnlockedByScheduleId(scheduleId, unlockStatus); }
        catch (Exception ignored) {}
    }

    public List<Map<String, Object>> getFacultyClassesForUser(Map<String, Object> user) {
        List<Integer> facultyIds = resolveFacultyIdsForUser(user);
        if (facultyIds.isEmpty()) {
            return new ArrayList<>();
        }
        return getFacultyClassesByFacultyIds(facultyIds, getActiveTermId());
    }

    public List<Map<String, Object>> getFacultyClasses(int facultyId) {
        return getFacultyClassesByFacultyIds(List.of(facultyId), getActiveTermId());
    }

    private List<Map<String, Object>> getFacultyClassesByFacultyIds(List<Integer> facultyIds, int activeTermId) {
        try {
            String placeholders = String.join(",", java.util.Collections.nCopies(facultyIds.size(), "?"));
            List<Object> args = new ArrayList<>(facultyIds);
            args.add(activeTermId);
            List<Map<String, Object>> classes = db.queryForList(
                "SELECT cs.section_id AS schedule_id, cs.section_id, cs.section_code AS section, cs.section_code," +
                " COALESCE(cs.section_status, 'Open') AS status, c.course_code, c.course_title AS description" +
                " FROM class_sections cs" +
                " JOIN courses c ON cs.course_id = c.course_id" +
                " WHERE cs.faculty_id IN (" + placeholders + ") AND cs.term_id = ?" +
                " ORDER BY c.course_code, cs.section_code",
                args.toArray());
            attachPrettySchedules(classes);
            return classes;
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private List<Integer> resolveFacultyIdsForUser(Map<String, Object> user) {
        java.util.LinkedHashSet<Integer> ids = new java.util.LinkedHashSet<>();
        if (user == null) return new ArrayList<>();

        Integer userId = numberToInteger(user.get("user_id"));
        String username = stringValue(user.get("username"));
        String realName = stringValue(user.get("real_name"));
        String email = stringValue(user.get("email"));

        if (userId != null) {
            addFacultyIdIfExists(ids, "SELECT faculty_id FROM faculty WHERE faculty_id = ? LIMIT 1", userId);
        }
        if (username != null) {
            addFacultyIdIfExists(ids, "SELECT faculty_id FROM faculty WHERE employee_number = ? LIMIT 1", username);
            String alias = facultyLoginAlias(username);
            if (alias != null && !alias.equals(username)) {
                addFacultyIdIfExists(ids, "SELECT faculty_id FROM faculty WHERE employee_number = ? LIMIT 1", alias);
            }
        }
        if (email != null) {
            addFacultyIdIfExists(ids, "SELECT faculty_id FROM faculty WHERE email = ? LIMIT 1", email);
        }
        if (realName != null) {
            addFacultyIdIfExists(ids,
                "SELECT faculty_id FROM faculty WHERE LOWER(TRIM(CONCAT(COALESCE(first_name,''),' ',COALESCE(last_name,'')))) = LOWER(?) LIMIT 1",
                realName);
        }
        return new ArrayList<>(ids);
    }

    private String facultyLoginAlias(String username) {
        if (username == null) return null;
        return switch (username.trim().toLowerCase()) {
            case "prof" -> "prof.cruz";
            case "faculty" -> "prof.garcia";
            default -> null;
        };
    }

    private void addFacultyIdIfExists(java.util.Set<Integer> ids, String sql, Object value) {
        try {
            Integer facultyId = db.queryForObject(sql, Integer.class, value);
            if (facultyId != null) ids.add(facultyId);
        } catch (Exception ignored) {}
    }

    private Integer numberToInteger(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    private String stringValue(Object value) {
        String text = value != null ? value.toString().trim() : "";
        return text.isEmpty() ? null : text;
    }

    private void attachPrettySchedules(List<Map<String, Object>> classes) {
        if (classes == null || classes.isEmpty()) {
            return;
        }
        List<Object> sectionIds = new ArrayList<>();
        for (Map<String, Object> row : classes) {
            Object sectionId = row.get("section_id");
            if (sectionId != null && !sectionIds.contains(sectionId)) {
                sectionIds.add(sectionId);
            }
        }
        if (sectionIds.isEmpty()) {
            classes.forEach(row -> row.put("pretty_schedule", "TBA"));
            return;
        }
        Map<String, List<String>> schedulesBySection = new HashMap<>();
        try {
            String placeholders = String.join(",", java.util.Collections.nCopies(sectionIds.size(), "?"));
            List<Map<String, Object>> schedules = db.queryForList(
                "SELECT section_id, day_of_week, start_time, end_time FROM class_schedules " +
                    "WHERE section_id IN (" + placeholders + ") ORDER BY section_id, day_of_week, start_time",
                sectionIds.toArray());
            for (Map<String, Object> schedule : schedules) {
                String key = String.valueOf(schedule.get("section_id"));
                schedulesBySection.computeIfAbsent(key, ignored -> new ArrayList<>()).add(
                    dayLabel(schedule.get("day_of_week")) + " " +
                        timeLabel(schedule.get("start_time")) + "-" + timeLabel(schedule.get("end_time")));
            }
        } catch (Exception ignored) {
            // Keep the same user-facing fallback when schedule data is unavailable.
        }
        for (Map<String, Object> row : classes) {
            List<String> parts = schedulesBySection.get(String.valueOf(row.get("section_id")));
            row.put("pretty_schedule", parts == null || parts.isEmpty() ? "TBA" : String.join(", ", parts));
        }
    }

    private String dayLabel(Object value) {
        int day = value instanceof Number ? ((Number) value).intValue() : 0;
        String[] dayNames = {"TBA", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
        return day >= 1 && day <= 7 ? dayNames[day] : "TBA";
    }

    private String timeLabel(Object value) {
        if (value == null) return "TBA";
        String text = value.toString();
        return text.length() >= 5 ? text.substring(0, 5) : text;
    }
    
    @Tool(description = "Retrieve all grades for a specific class section")
    public List<Map<String, Object>> getClassGrades(int scheduleId) {
        List<Grade> grades = gradeRepository.findBySectionId(scheduleId);
        List<Map<String, Object>> gradeList = new ArrayList<>();
        
        for (Grade g : grades) {
            Map<String, Object> map = new HashMap<>();
            map.put("grade_id", g.getId());
            map.put("student_id", g.getStudentId());
            map.put("student_name", g.getStudentName() != null ? g.getStudentName() : g.getStudentId());
            
            String courseCode = "";
            if (g.getCourseId() != null) {
                com.iuims.registrar.entity.Course c = courseRepository.findById(g.getCourseId()).orElse(null);
                if (c != null) courseCode = c.getCourseCode();
            }
            map.put("course_code", courseCode);
            
            double p = g.getPrelim() != null ? g.getPrelim().doubleValue() : 0.0;
            double m = g.getMidterm() != null ? g.getMidterm().doubleValue() : 0.0;
            double f = g.getFinalGrade() != null ? g.getFinalGrade().doubleValue() : 0.0;
            double sg = g.getRegistrarFinalGrade() != null ? g.getRegistrarFinalGrade().doubleValue() : 
                        (g.getSemestralGrade() != null ? g.getSemestralGrade().doubleValue() : 0.0);
            
            map.put("prelim", p);
            map.put("midterm", m);
            map.put("final", f);
            if (sg > 0) map.put("semestral_grade", sg);
            
            String remarks = g.getRegistrarFinalRemarks() != null ? g.getRegistrarFinalRemarks() : 
                             (g.getRemarks() != null ? g.getRemarks() : "Ongoing");
            map.put("remarks", remarks);
            
            map.put("previous_grade", g.getPreviousGrade());
            map.put("registrar_final_grade", g.getRegistrarFinalGrade());
            map.put("registrar_final_remarks", g.getRegistrarFinalRemarks());
            map.put("grade_lock_status", g.getGradeLockStatus() != null ? g.getGradeLockStatus() : "");
            map.put("grade_lock_reason", g.getGradeLockReason());
            map.put("row_locked", isRegistrarLocked(g.getGradeLockStatus()));
            map.put("lab_remarks", "Ongoing");
            map.put("status", g.getStatus() != null ? g.getStatus() : "DRAFT");
            
            boolean pendingChange = gradeChangeRequestRepository.findByGradeId(Long.valueOf(g.getId()))
                .stream().anyMatch(r -> "PENDING".equals(r.getStatus()));
            map.put("pending_change", pendingChange ? 1 : 0);
            
            map.put("prelim_point", p > 0 ? String.format("%.2f", convertToPointGrade(p)) : "");
            map.put("midterm_point", m > 0 ? String.format("%.2f", convertToPointGrade(m)) : "");
            map.put("final_point", f > 0 ? String.format("%.2f", convertToPointGrade(f)) : "");
            
            gradeList.add(map);
        }
        return gradeList;
    }

    public Map<String, Object> getClassInfo(int scheduleId) {
        try {
            Map<String, Object> map = db.queryForMap(
                "SELECT cs.section_id AS schedule_id, cs.section_id, cs.section_code AS section, cs.section_code," +
                " cs.term_id, COALESCE(cs.section_status, 'Open') AS status, c.course_code, c.course_title AS description," +
                " IFNULL(f.first_name,'') AS faculty_first, IFNULL(f.last_name,'') AS faculty_last" +
                " FROM class_sections cs" +
                " JOIN courses c ON cs.course_id = c.course_id" +
                " LEFT JOIN faculty f ON cs.faculty_id = f.faculty_id" +
                " WHERE cs.section_id = ?", scheduleId);
            map.put("pretty_schedule", "TBA"); // Schedule shown separately via getClassGrades
            return map;
        } catch (Exception e) { return new java.util.HashMap<>(); }
    }

    // ==========================================
    // 4. VPAA & APPROVALS
    // ==========================================
    @Transactional
    public void submitClassGrades(int scheduleId) {
        submitClassGrades(scheduleId, "faculty", "Faculty");
    }

    @Transactional
    public void submitClassGrades(int scheduleId, String actor, String actorRole) {
        setSqlSafeUpdates(false); 
        try { 
            List<Grade> beforeGrades = gradeRepository.findBySectionId(scheduleId).stream()
                .map(this::copyGrade)
                .toList();
            finalizeSectionGradeRemarks(scheduleId); 
            gradeRepository.updateStatusBySectionId(scheduleId, "SUBMITTED");
            classSectionRepository.updateStatus(scheduleId, "PENDING_APPROVAL");
            vpaaExtensionRepository.updateStatusByScheduleId(scheduleId, "COMPLETED"); 
            logSectionLifecycleEvents(scheduleId, beforeGrades, "GRADE_CLASS_SUBMITTED", "SUBMITTED",
                "Faculty submitted class grades for registrar review.", actor, actorRole, null);
        } catch (Exception e) {} finally { setSqlSafeUpdates(true); } 
    }

    @Transactional
    public void unsubmitClassGrades(int scheduleId) {
        unsubmitClassGrades(scheduleId, "registrar", "Registrar");
    }

    @Transactional
    public void unsubmitClassGrades(int scheduleId, String actor, String actorRole) {
        setSqlSafeUpdates(false); 
        try { 
            List<Grade> beforeGrades = gradeRepository.findBySectionId(scheduleId).stream()
                .map(this::copyGrade)
                .toList();
            gradeRepository.updateStatusBySectionIdAndStatus(scheduleId, "DRAFT", "SUBMITTED");
            classSectionRepository.updateStatusIfIn(scheduleId, "Open", List.of("SUBMITTED", "PENDING_APPROVAL"));
            logSectionLifecycleEvents(scheduleId, beforeGrades, "GRADE_CLASS_REOPENED_DRAFT", "DRAFT",
                "Class grade submission reopened to draft.", actor, actorRole, null);
        } catch (Exception e) {} finally { setSqlSafeUpdates(true); } 
    }

    @Transactional
    public void finalizeClassGrades(int scheduleId) {
        finalizeClassGrades(scheduleId, "registrar", "Registrar");
    }

    @Transactional
    public void finalizeClassGrades(int scheduleId, String actor, String actorRole) {
        setSqlSafeUpdates(false); 
        try { 
            List<Grade> beforeGrades = gradeRepository.findBySectionId(scheduleId).stream()
                .map(this::copyGrade)
                .toList();
            finalizeSectionGradeRemarks(scheduleId); 
            gradeRepository.updateStatusBySectionId(scheduleId, "SUBMITTED");
            classSectionRepository.updateStatus(scheduleId, "SUBMITTED");
            for (Grade grade : gradeRepository.findBySectionId(scheduleId)) {
                GradeRecordEventService.GradeSnapshot before = snapshotById(beforeGrades, grade.getId());
                if (!isRegistrarLocked(grade.getGradeLockStatus())) {
                    lockRegistrarOutcome(grade.getId(),
                        grade.getSemestralGrade() != null ? grade.getSemestralGrade().doubleValue() : null,
                        grade.getRemarks(),
                        "CLASS_POSTED_TO_TRANSCRIPT");
                    grade = gradeRepository.findById(grade.getId()).orElse(grade);
                    if (grade != null) {
                        grade.setStatus("SUBMITTED");
                        gradeRepository.saveAndFlush(grade);
                    }
                }
                gradeRecordEventService.recordEvent(
                    grade,
                    null,
                    "GRADE_CLASS_POSTED",
                    "FINALIZED",
                    actor,
                    actorRole,
                    "Registrar posted approved class grades to the official record.",
                    before,
                    GradeRecordEventService.snapshotOf(grade));
            }
        } catch (Exception e) {} finally { setSqlSafeUpdates(true); } 
    }

    @Transactional
    public void revertClassToDraft(int scheduleId) {
        revertClassToDraft(scheduleId, "registrar", "Registrar");
    }

    @Transactional
    public void revertClassToDraft(int scheduleId, String actor, String actorRole) {
        setSqlSafeUpdates(false); 
        try { 
            List<Grade> beforeGrades = gradeRepository.findBySectionId(scheduleId).stream()
                .map(this::copyGrade)
                .toList();
            gradeRepository.updateStatusBySectionId(scheduleId, "DRAFT");
            classSectionRepository.updateStatus(scheduleId, "Open");
            logSectionLifecycleEvents(scheduleId, beforeGrades, "GRADE_CLASS_REVERTED_TO_DRAFT", "DRAFT",
                "Registrar reverted class grade rows to draft.", actor, actorRole, null);
        } catch (Exception e) {} finally { setSqlSafeUpdates(true); } 
    }

    @Transactional
    public int expireOverdueIncGrades(Integer termId) {
        return expireOverdueIncGrades(termId, java.time.LocalDate.now());
    }

    int expireOverdueIncGrades(Integer termId, java.time.LocalDate today) {
        int resolvedTermId = termId != null && termId > 0 ? termId : getActiveTermId();
        java.time.LocalDate expirationDate;
        try {
            Object raw = getGradingWindows(resolvedTermId).get("INC_EXPIRATION_DATE");
            if (raw == null || raw.toString().isBlank()) return 0;
            expirationDate = java.time.LocalDate.parse(raw.toString());
        } catch (Exception e) {
            return 0;
        }
        if (today.isBefore(expirationDate)) return 0;
        
        List<Long> gradeIds = db.queryForList(
            "SELECT g.id FROM grades g JOIN class_sections cs ON cs.section_id = g.section_id " +
                "WHERE cs.term_id = ? AND " + GradeOutcomeSql.outcome("g") + " = 'INC'",
            Long.class, resolvedTermId);
        for (Long gradeId : gradeIds) {
            Grade before = gradeRepository.findById(gradeId.intValue()).orElse(null);
            lockRegistrarOutcome(gradeId, 5.00, "Failed", "INC_EXPIRED");
            Grade after = gradeRepository.findById(gradeId.intValue()).orElse(null);
            gradeRecordEventService.recordEvent(
                after,
                null,
                "INC_EXPIRED",
                "FINALIZED",
                "registrar",
                "Registrar",
                "INC deadline expired; official outcome set to Failed.",
                GradeRecordEventService.snapshotOf(before),
                GradeRecordEventService.snapshotOf(after));
        }
        return gradeIds.size();
    }

    private void finalizeSectionGradeRemarks(int sectionId) {
        List<Grade> grades = gradeRepository.findBySectionId(sectionId);
        for (Grade grade : grades) {
            if (isRegistrarLocked(grade.getGradeLockStatus())) {
                continue;
            }

            Double p = grade.getPrelim() != null ? grade.getPrelim().doubleValue() : 0.0;
            Double m = grade.getMidterm() != null ? grade.getMidterm().doubleValue() : 0.0;
            Double f = grade.getFinalGrade() != null ? grade.getFinalGrade().doubleValue() : 0.0;

            int count = 0; double sum = 0;
            if (p > 0) { sum += p; count++; }
            if (m > 0) { sum += m; count++; }
            if (f > 0) { sum += f; count++; }

            double pointGrade = (count > 0) ? convertToPointGrade(sum / count) : 0.0;
            String remarks = (p == 0 || m == 0 || f == 0) ? "INC" : ((pointGrade > 3.0) ? "Failed" : "Passed");

            grade.setSemestralGrade(BigDecimal.valueOf(pointGrade));
            grade.setRemarks(remarks);
        }
        gradeRepository.saveAllAndFlush(grades);
    }

    private void logSectionLifecycleEvents(int sectionId,
                                           List<Grade> beforeGrades,
                                           String actionType,
                                           String lifecycleStatus,
                                           String reason,
                                           String actor,
                                           String actorRole,
                                           Long requestId) {
        Map<Integer, GradeRecordEventService.GradeSnapshot> snapshots = new LinkedHashMap<>();
        for (Grade grade : beforeGrades) {
            snapshots.put(grade.getId(), GradeRecordEventService.snapshotOf(grade));
        }
        for (Grade grade : gradeRepository.findBySectionId(sectionId)) {
            gradeRecordEventService.recordEvent(
                grade,
                requestId,
                actionType,
                lifecycleStatus,
                actor,
                actorRole,
                reason,
                snapshots.get(grade.getId()),
                GradeRecordEventService.snapshotOf(grade));
        }
    }

    private GradeRecordEventService.GradeSnapshot snapshotById(List<Grade> grades, Integer gradeId) {
        if (gradeId == null || grades == null) {
            return null;
        }
        for (Grade grade : grades) {
            if (gradeId.equals(grade.getId())) {
                return GradeRecordEventService.snapshotOf(grade);
            }
        }
        return null;
    }

    private Grade copyGrade(Grade source) {
        Grade copy = new Grade();
        copy.setId(source.getId());
        copy.setStudentId(source.getStudentId());
        copy.setSectionId(source.getSectionId());
        copy.setCourseId(source.getCourseId());
        copy.setStudentName(source.getStudentName());
        copy.setPrelim(source.getPrelim());
        copy.setMidterm(source.getMidterm());
        copy.setFinalGrade(source.getFinalGrade());
        copy.setSemestralGrade(source.getSemestralGrade());
        copy.setRemarks(source.getRemarks());
        copy.setPreviousGrade(source.getPreviousGrade());
        copy.setGradeLockStatus(source.getGradeLockStatus());
        copy.setGradeLockReason(source.getGradeLockReason());
        copy.setRegistrarFinalGrade(source.getRegistrarFinalGrade());
        copy.setRegistrarFinalRemarks(source.getRegistrarFinalRemarks());
        copy.setRegistrarFinalizedAt(source.getRegistrarFinalizedAt());
        copy.setCurriculumYear(source.getCurriculumYear());
        copy.setGrade(source.getGrade());
        copy.setDateRecorded(source.getDateRecorded());
        copy.setStatus(source.getStatus());
        return copy;
    }

    private void setSqlSafeUpdates(boolean enabled) {
        try {
            db.execute("SET SQL_SAFE_UPDATES = " + (enabled ? "1" : "0"));
        } catch (Exception ignored) {}
    }

    @Tool(description = "Get a list of class sections pending grade submission")
    public List<Map<String, Object>> getPendingClassSubmissions() { 
        try {
            List<ClassSection> sections = classSectionRepository.findBySectionStatus("PENDING_APPROVAL");
            List<Map<String, Object>> rows = new ArrayList<>();
            for (ClassSection cs : sections) {
                Map<String, Object> map = new HashMap<>();
                map.put("schedule_id", cs.getSectionId());
                map.put("section_id", cs.getSectionId());
                map.put("section_code", cs.getSectionCode());
                map.put("status", cs.getSectionStatus());
                
                String courseCode = "Unknown";
                String courseTitle = "Unknown";
                if (cs.getCourseId() != null) {
                    com.iuims.registrar.entity.Course c = courseRepository.findById(cs.getCourseId()).orElse(null);
                    if (c != null) {
                        courseCode = c.getCourseCode();
                        courseTitle = c.getCourseTitle();
                    }
                }
                map.put("course_code", courseCode);
                map.put("description", courseTitle);
                
                String facultyName = "";
                if (cs.getFacultyId() != null) {
                    // Faculty can live in either sys_users or the faculty table; try both
                    try {
                        facultyName = db.queryForObject(
                            "SELECT CONCAT(COALESCE(first_name,''), ' ', COALESCE(last_name,'')) FROM faculty WHERE faculty_id = ?",
                            String.class, cs.getFacultyId());
                        if (facultyName == null) facultyName = "";
                    } catch (Exception ignored) {
                        try {
                            facultyName = db.queryForObject(
                                "SELECT COALESCE(NULLIF(real_name, ''), username) FROM sys_users WHERE user_id = ?",
                                String.class, cs.getFacultyId());
                            if (facultyName == null) facultyName = "";
                        } catch (Exception ignored2) {}
                    }
                }
                map.put("faculty_name", facultyName);
                
                rows.add(map);
            }
            attachPrettySchedules(rows);
            return rows;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Tool(description = "View pending grade change requests")
    public List<Map<String, Object>> getGradeChangeRequests() { 
        try {


            
            return gradeChangeRequestRepository.findByStatus("PENDING").stream().map(r -> {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("request_id", r.getRequestId());
                map.put("grade_id", r.getGradeId());
                String type = normalizeRequestType(r.getRequestType() != null ? r.getRequestType() : "FINAL_GRADE_CORRECTION");
                map.put("request_type", type);
                map.put("student_name", r.getStudentName());
                map.put("faculty", r.getFacultyName());
                map.put("new_grade", r.getRequestedGrade());
                map.put("requested_prelim", r.getRequestedPrelim());
                map.put("requested_midterm", r.getRequestedMidterm());
                map.put("requested_finals", r.getRequestedFinals());
                map.put("reason", r.getReason());
                map.put("course_code", r.getCourseCode());
                
                Grade g = gradeRepository.findById(r.getGradeId().intValue()).orElse(null);
                if (g != null) {
                    map.put("old_grade", g.getRegistrarFinalGrade() != null ? g.getRegistrarFinalGrade().toString() : (g.getSemestralGrade() != null ? g.getSemestralGrade().toString() : "N/A"));
                    map.put("old_remarks", g.getRegistrarFinalRemarks() != null ? g.getRegistrarFinalRemarks() : (g.getRemarks() != null ? g.getRemarks() : "Ongoing"));
                } else {
                    map.put("old_grade", "N/A");
                    map.put("old_remarks", "N/A");
                }
                map.put("request_label", requestTypeLabel(type));
                map.put("requested_summary", requestedSummary(map, type));
                return map;
            }).sorted((m1, m2) -> ((Integer)m2.get("request_id")).compareTo((Integer)m1.get("request_id"))).collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String requestTypeLabel(String requestType) {
        return switch (normalizeRequestType(requestType)) {
            case "COMPONENT_GRADE_CORRECTION" -> "Component Correction";
            case "REOPEN_FOR_EDIT" -> "Reopen for Edit";
            default -> "Final Grade Correction";
        };
    }

    private String requestedSummary(Map<String, Object> row, String requestType) {
        return switch (normalizeRequestType(requestType)) {
            case "COMPONENT_GRADE_CORRECTION" ->
                "P: " + displayRequestedScore(row.get("requested_prelim")) +
                " / M: " + displayRequestedScore(row.get("requested_midterm")) +
                " / F: " + displayRequestedScore(row.get("requested_finals"));
            case "REOPEN_FOR_EDIT" -> "Unlock row for faculty edit";
            default -> row.get("new_grade") != null ? row.get("new_grade").toString() : "N/A";
        };
    }

    private String displayRequestedScore(Object raw) {
        Double score = parseScoreObject(raw);
        return score == null ? "-" : String.format("%.1f", score);
    }

    @Transactional
    public void approveGradeChange(int requestId) {
        approveGradeChange(requestId, "registrar");
    }

    @Transactional
    public void approveGradeChange(int requestId, String approvedBy) {
        GradeChangeRequest req = gradeChangeRequestRepository.findById(requestId).orElse(null);
        if (req == null) return;
        
        int gradeId = req.getGradeId().intValue();
        String requestType = normalizeRequestType(req.getRequestType());
        Grade beforeGrade = gradeRepository.findById(gradeId).orElse(null);
        switch (requestType) {
            case "COMPONENT_GRADE_CORRECTION" -> approveComponentGradeCorrection(gradeId, req);
            case "REOPEN_FOR_EDIT" -> approveReopenForEdit(gradeId);
            default -> {
                Double requestedGrade = parsePointGrade(req.getRequestedGrade());
                String finalRemarks = remarkForPointGrade(requestedGrade);
                lockRegistrarOutcome(gradeId, requestedGrade, finalRemarks, "GRADE_CHANGE_APPROVED");
                Grade g = gradeRepository.findById(gradeId).orElse(null);
                if (g != null) {
                    g.setStatus("SUBMITTED");
                    gradeRepository.saveAndFlush(g);
                }
            }
        }
        
        req.setStatus("APPROVED");
        req.setAppliedAction(requestType);
        req.setApprovedAt(java.time.LocalDateTime.now());
        req.setReviewedBy(approvedBy);
        req.setReviewNote("Approved by registrar.");
        gradeChangeRequestRepository.saveAndFlush(req);
        Grade grade = gradeRepository.findById(gradeId).orElse(null);
        if (grade != null) {
            String eventType = "REOPEN_FOR_EDIT".equals(requestType) ? "GRADE_ROW_REOPENED" : "GRADE_CHANGE_APPROVED";
            gradeRecordEventService.recordEvent(
                grade,
                req.getRequestId() != null ? req.getRequestId().longValue() : null,
                eventType,
                defaultLifecycleStatus(grade),
                approvedBy,
                "Registrar",
                req.getReason(),
                GradeRecordEventService.snapshotOf(beforeGrade),
                GradeRecordEventService.snapshotOf(grade));
            documentTrailService.recordStudentEvent(
                grade.getStudentId(),
                "STUDENT",
                "GRADE_CHANGE",
                "GRADE_CHANGE_APPROVED",
                requestTypeLabel(requestType) + " approved",
                "Request #" + requestId + " approved by " + approvedBy + ".",
                approvedBy,
                req.getRequestId() != null ? req.getRequestId().longValue() : null,
                "grade_change_requests",
                String.valueOf(requestId));
        }
    }

    private void approveComponentGradeCorrection(int gradeId, GradeChangeRequest req) {
        Double prelim = req.getRequestedPrelim() != null ? req.getRequestedPrelim().doubleValue() : null;
        Double midterm = req.getRequestedMidterm() != null ? req.getRequestedMidterm().doubleValue() : null;
        Double finals = req.getRequestedFinals() != null ? req.getRequestedFinals().doubleValue() : null;
        double p = prelim != null ? prelim : 0.0;
        double m = midterm != null ? midterm : 0.0;
        double f = finals != null ? finals : 0.0;
        boolean incomplete = p <= 0 || m <= 0 || f <= 0;
        Double pointGrade = null;
        String finalRemarks = "INC";
        if (!incomplete) {
            pointGrade = convertToPointGrade((p + m + f) / 3.0);
            finalRemarks = remarkForPointGrade(pointGrade);
        }
        Grade g = gradeRepository.findById(gradeId).orElse(null);
        if (g != null) {
            g.setPrelim(java.math.BigDecimal.valueOf(p));
            g.setMidterm(java.math.BigDecimal.valueOf(m));
            g.setFinalGrade(java.math.BigDecimal.valueOf(f));
            g.setStatus("SUBMITTED");
            gradeRepository.saveAndFlush(g);
        }
        lockRegistrarOutcome(gradeId, pointGrade, finalRemarks, "COMPONENT_GRADE_CHANGE_APPROVED");
    }

    private void approveReopenForEdit(int gradeId) {
        Grade grade = gradeRepository.findById(gradeId).orElse(null);
        if (grade == null) return;
        
        if (grade.getPreviousGrade() == null || grade.getPreviousGrade().isEmpty()) {
            grade.setPreviousGrade(grade.getRegistrarFinalRemarks() != null ? grade.getRegistrarFinalRemarks() : (grade.getRemarks() != null ? grade.getRemarks() : ""));
        }
        
        grade.setRegistrarFinalGrade(null);
        grade.setRegistrarFinalRemarks(null);
        grade.setGradeLockStatus(null);
        grade.setGradeLockReason("REOPENED_FOR_EDIT");
        grade.setStatus("DRAFT");
        gradeRepository.saveAndFlush(grade);
    }

    public void rejectGradeChange(int requestId) {
        rejectGradeChange(requestId, "registrar", "Rejected by registrar.");
    }

    public void rejectGradeChange(int requestId, String rejectedBy, String reviewNote) {
        gradeChangeRequestRepository.findById(requestId).ifPresent(r -> {
            r.setStatus("REJECTED");
            r.setReviewedBy(rejectedBy);
            r.setReviewNote(reviewNote);
            r.setRejectedAt(java.time.LocalDateTime.now());
            gradeChangeRequestRepository.saveAndFlush(r);
            Grade grade = gradeRepository.findById(r.getGradeId().intValue()).orElse(null);
            if (grade != null) {
                gradeRecordEventService.recordEvent(
                    grade,
                    r.getRequestId() != null ? r.getRequestId().longValue() : null,
                    "GRADE_CHANGE_REJECTED",
                    defaultLifecycleStatus(grade),
                    rejectedBy,
                    "Registrar",
                    reviewNote,
                    GradeRecordEventService.snapshotOf(grade),
                    GradeRecordEventService.snapshotOf(grade));
                documentTrailService.recordStudentEvent(
                    grade.getStudentId(),
                    "STUDENT",
                    "GRADE_CHANGE",
                    "GRADE_CHANGE_REJECTED",
                    requestTypeLabel(r.getRequestType()) + " rejected",
                    "Request #" + requestId + " rejected by " + rejectedBy + ". " + reviewNote,
                    rejectedBy,
                    r.getRequestId() != null ? r.getRequestId().longValue() : null,
                    "grade_change_requests",
                    String.valueOf(requestId));
            }
        });
    }

    @Transactional
    public void requestGradeChange(int gradeId, String requestType, String newGrade, String requestedPrelim, String requestedMidterm, String requestedFinals, String reason, int facultyId) {

        String normalizedType = normalizeRequestType(requestType);
        
        Grade g = gradeRepository.findById(gradeId).orElse(null);
        if (g == null) return;
        
        com.iuims.registrar.entity.Course c = null;
        if (g.getCourseId() != null) {
            c = courseRepository.findById(g.getCourseId()).orElse(null);
        }
        
        String courseCode = c != null ? c.getCourseCode() : "";
        
        String studentName = g.getStudentName();
        if (studentName == null || studentName.trim().isEmpty()) {
            studentName = g.getStudentId();
            com.iuims.registrar.entity.Student s = studentRepository.findById(g.getStudentId()).orElse(null);
            if (s != null) {
                studentName = s.getFirstName() + " " + s.getLastName();
            }
        }
        
        String facultyName = resolveFacultyDisplayName(facultyId);
        
        GradeChangeRequest req = new GradeChangeRequest();
        req.setGradeId((long) gradeId);
        req.setStudentName(studentName);
        req.setCourseCode(courseCode);
        req.setFacultyName(facultyName);
        req.setRequestType(normalizedType);
        req.setRequestedGrade("FINAL_GRADE_CORRECTION".equals(normalizedType) ? newGrade : null);
        
        try {
            if (requestedPrelim != null && !requestedPrelim.isEmpty()) req.setRequestedPrelim(new java.math.BigDecimal(requestedPrelim));
            if (requestedMidterm != null && !requestedMidterm.isEmpty()) req.setRequestedMidterm(new java.math.BigDecimal(requestedMidterm));
            if (requestedFinals != null && !requestedFinals.isEmpty()) req.setRequestedFinals(new java.math.BigDecimal(requestedFinals));
        } catch (NumberFormatException ignored) {}
        
        req.setReason(reason);
        req.setStatus("PENDING");
        req.setRequestDate(java.time.LocalDateTime.now());
        gradeChangeRequestRepository.saveAndFlush(req);
        gradeRecordEventService.recordEvent(
            g,
            req.getRequestId() != null ? req.getRequestId().longValue() : null,
            "GRADE_CHANGE_REQUESTED",
            defaultLifecycleStatus(g),
            facultyName,
            "Faculty",
            reason,
            GradeRecordEventService.snapshotOf(g),
            GradeRecordEventService.snapshotOf(g));
        documentTrailService.recordStudentEvent(
            g.getStudentId(),
            "STUDENT",
            "GRADE_CHANGE",
            "GRADE_CHANGE_REQUESTED",
            requestTypeLabel(normalizedType) + " requested",
            "Course " + courseCode + ". Reason: " + reason,
            facultyName,
            req.getRequestId() != null ? req.getRequestId().longValue() : null,
            "grade_change_requests",
            String.valueOf(req.getRequestId()));
    }

    private String resolveFacultyDisplayName(int userId) {
        try {
            try {
                String fromFaculty = db.queryForObject(
                    "SELECT CONCAT(COALESCE(first_name,''), ' ', COALESCE(last_name,'')) FROM faculty WHERE faculty_id = ? LIMIT 1",
                    String.class, userId);
                if (fromFaculty != null && !fromFaculty.isBlank()) {
                    return fromFaculty.trim();
                }
            } catch (Exception ignored) {}

            String userName = db.queryForObject(
                "SELECT COALESCE(NULLIF(real_name, ''), username) FROM sys_users WHERE user_id = ?",
                String.class, userId);
            if (userName != null && !userName.isBlank()) {
                return userName.trim();
            }
        } catch (Exception ignored) {}
        return "Unknown";
    }

    @Transactional
    public void requestGradeChange(int gradeId, String newGrade, String reason, int facultyId) {
        requestGradeChange(gradeId, "FINAL_GRADE_CORRECTION", newGrade, null, null, null, reason, facultyId);
    }
    public void requestVpaaExtension(int scheduleId, int facultyId, String reason) {
        VpaaExtension ext = new VpaaExtension();
        ext.setScheduleId(scheduleId);
        ext.setFacultyId(facultyId);
        ext.setReason(reason);
        ext.setStatus("PENDING");
        vpaaExtensionRepository.save(ext);
    }
    
    public List<Map<String, Object>> getPendingExtensions() {
        return vpaaExtensionRepository.findPendingExtensions().stream().map(e -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("ext_id", e.getExtId());
            map.put("schedule_id", e.getScheduleId());
            map.put("faculty_id", e.getFacultyId());
            map.put("reason", e.getReason());
            map.put("status", e.getStatus());
            
            // Just basic data for now since we are eliminating JdbcTemplate
            // We can fetch course_code and faculty_name if needed using other repos
            map.put("course_code", "Course");
            map.put("faculty_name", "Faculty");
            return map;
        }).collect(java.util.stream.Collectors.toList());
    }
    
    public void approveExtension(int extId) {
        vpaaExtensionRepository.findById(extId).ifPresent(e -> {
            e.setStatus("APPROVED");
            vpaaExtensionRepository.save(e);
        });
    }
    
    public boolean isExtensionApproved(int scheduleId) {
        return !vpaaExtensionRepository.findByScheduleIdAndStatus(scheduleId, "APPROVED").isEmpty();
    }
    
    public boolean isExtensionPending(int scheduleId) {
        return !vpaaExtensionRepository.findByScheduleIdAndStatus(scheduleId, "PENDING").isEmpty();
    }

    public Map<String, Object> getGradeGovernanceSummary(Integer termId) {
        return gradeRecordEventService.buildSummary(termId);
    }

    public List<Map<String, Object>> getGradeRegistryRows(Integer termId, String query, String lifecycleStatus, int limit) {
        return gradeRecordEventService.listGradeRegistryRows(termId, query, lifecycleStatus, limit);
    }

    public List<Map<String, Object>> getGradeRecordEvents(Integer termId,
                                                          String query,
                                                          String actionType,
                                                          String lifecycleStatus,
                                                          int limit) {
        return gradeRecordEventService.listGradeRecordEvents(termId, query, actionType, lifecycleStatus, limit);
    }

    // ==========================================
    // 5. USER & ADMIN UTILITIES
    // ==========================================
    public Map<String, Object> findStudentByIdOrName(String q) {
        List<com.iuims.registrar.entity.Student> students = studentRepository.searchStudents(q);
        if (students.isEmpty()) {
            String resolved = studentProfileService.resolveCurrentStudentNumber(q);
            if (resolved != null && !resolved.isBlank() && !resolved.equalsIgnoreCase(q != null ? q.trim() : "")) {
                students = studentRepository.searchStudents(resolved);
            }
        }
        if (students.isEmpty()) return null;
        com.iuims.registrar.entity.Student s = students.get(0);
        
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("student_number", s.getStudentNumber());
        m.put("archive_key", s.getArchiveKey());
        m.put("user_id", s.getUserId());
        m.put("first_name", s.getFirstName());
        m.put("last_name", s.getLastName());
        m.put("real_name", s.getRealName());
        m.put("email", s.getEmail());
        m.put("mobile", s.getMobile());
        m.put("contact_number", s.getMobile());
        m.put("program_code", s.getProgramCode());
        m.put("year_level", s.getYearLevel());
        m.put("semester", s.getSemester());
        m.put("term_year", s.getTermYear());
        m.put("student_type", s.getStudentType());
        m.put("enrollment_status_type", s.getEnrollmentStatusType());
        m.put("admission_status", s.getAdmissionStatus());
        m.put("status", s.getStatus());
        m.put("is_active", s.getIsActive() != null && s.getIsActive() ? 1 : 0);
        m.put("enrollment_blocked", s.getEnrollmentBlocked() != null && s.getEnrollmentBlocked() ? 1 : 0);
        m.put("archive_key", s.getArchiveKey());
        m.put("username", s.getStudentNumber());
        return m;
    }
    
    public List<Map<String, Object>> searchStudentsBySurname(String q) { 
        return studentRepository.searchStudents(q).stream().limit(10).map(s -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("username", s.getStudentNumber());
            m.put("archive_key", s.getArchiveKey());
            m.put("real_name", s.getFirstName() + " " + s.getLastName());
            return m;
        }).collect(java.util.stream.Collectors.toList());
    }

    public String getUsernameFromId(int uid) { return sysUserRepository.findById(uid).map(com.iuims.registrar.entity.SysUser::getUsername).orElse(null); }
    public int getTotalStudentCount() { return (int) studentRepository.count(); }
    
    public List<Map<String, Object>> getStudentRoster(String programFilter) {
        try {
            Integer activeTermId = getActiveTermId();
            String sql = "SELECT s.student_number as username, s.real_name, s.program_code, s.year_level, s.admission_status, " +
                         "(SELECT COALESCE(SUM(c.credit_units), 0) " +
                         "FROM student_enlistments se " +
                         "JOIN class_sections cs ON cs.section_id = se.section_id " +
                         "JOIN courses c ON c.course_id = cs.course_id " +
                         "WHERE se.student_id = s.student_number AND cs.term_id = ? " +
                         enlistmentSchemaService.enlistmentStatusFilter(EnlistmentSchemaService.Scope.COMMITTED_ONLY, "se") +
                         ") AS enrolled_units " +
                         "FROM students s " +
                         "LEFT JOIN programs p ON s.program_code = p.program_code " +
                         "WHERE (p.school_name IS NULL OR (p.school_name NOT LIKE '%Basic%' AND p.school_name NOT LIKE '%Senior%' AND p.school_name NOT LIKE '%Junior%')) ";
            if (programFilter != null && !programFilter.trim().isEmpty() && !programFilter.equalsIgnoreCase("All")) {
                sql += " AND s.program_code = ? ORDER BY s.real_name ASC";
                return db.queryForList(sql, activeTermId, programFilter.trim());
            }
            sql += " ORDER BY s.real_name ASC LIMIT 500";
            return db.queryForList(sql, activeTermId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    private Map<String, Object> sysUserToMap(com.iuims.registrar.entity.SysUser u) {
        if (u == null) return null;
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("user_id", u.getUserId());
        m.put("username", u.getUsername());
        m.put("real_name", u.getRealName());
        m.put("email", u.getEmail());
        m.put("role", u.getRole());
        m.put("is_active", u.getIsActive() != null && u.getIsActive() ? 1 : 0);
        m.put("program_code", u.getProgramCode());
        m.put("granted_permissions", u.getGrantedPermissions());
        return m;
    }

    public Map<String, Object> login(String u, String p) {
        try {
            com.iuims.registrar.entity.SysUser user = sysUserRepository.findByUsername(u).orElse(null);
            if (user != null && org.mindrot.jbcrypt.BCrypt.checkpw(p, user.getPassword())) {
                return sysUserToMap(user);
            }
        } catch (Exception e) {}
        return null;
    }
    
    public void toggleUserStatus(int uid, boolean a) {
        sysUserRepository.findById(uid).ifPresent(u -> {
            u.setIsActive(a);
            sysUserRepository.save(u);
        });
    }
    
    public void resetPassword(int uid) {
        sysUserRepository.findById(uid).ifPresent(u -> {
            u.setPassword(org.mindrot.jbcrypt.BCrypt.hashpw(requiredTemporaryPassword(), org.mindrot.jbcrypt.BCrypt.gensalt()));
            sysUserRepository.save(u);
        });
    }
    
    public List<Map<String, Object>> getAllUsers() {
        return sysUserRepository.findAll().stream().map(this::sysUserToMap).collect(java.util.stream.Collectors.toList());
    }
    
    public void createUser(String u, String r, String role, String p, List<String> perm) {
        com.iuims.registrar.entity.SysUser user = new com.iuims.registrar.entity.SysUser();
        user.setUsername(u);
        user.setPassword(org.mindrot.jbcrypt.BCrypt.hashpw(requiredTemporaryPassword(), org.mindrot.jbcrypt.BCrypt.gensalt()));
        user.setRealName(r);
        user.setRole(role);
        user.setProgramCode(p);
        user.setGrantedPermissions(perm != null ? perm.toString() : "[]");
        user.setIsActive(true);
        sysUserRepository.save(user);
    }

    private String requiredTemporaryPassword() {
        if (configuredTemporaryPassword == null || configuredTemporaryPassword.isBlank()) {
            throw new IllegalStateException(
                "Registrar temporary password is not configured. Set REGISTRAR_TEMP_PASSWORD or registrar.accounts.temporary-password.");
        }
        return configuredTemporaryPassword;
    }
    
    public void updateUserPermissions(int uid, String role, List<String> perm) {
        sysUserRepository.findById(uid).ifPresent(u -> {
            u.setRole(role);
            u.setGrantedPermissions(perm != null ? perm.toString() : "[]");
            sysUserRepository.save(u);
        });
    }
    
    public void deleteUser(int uid) {
        sysUserRepository.deleteById(uid);
    }
    
    public List<Map<String, Object>> getAllClassesAdmin() {
        try {
            int termId = getActiveTermId();
            List<Map<String, Object>> classes = db.queryForList(
                "SELECT cs.section_id AS schedule_id, cs.section_id, cs.section_code AS section, " +
                "COALESCE(cs.section_status, 'Open') AS status, c.course_code, c.course_title AS description, " +
                "IFNULL(NULLIF(TRIM(CONCAT(COALESCE(f.first_name,''), ' ', COALESCE(f.last_name,''))), ''), 'TBA') AS faculty_name, " +
                "0 AS is_unlocked " +
                "FROM class_sections cs " +
                "JOIN courses c ON cs.course_id = c.course_id " +
                "LEFT JOIN faculty f ON cs.faculty_id = f.faculty_id " +
                "WHERE cs.term_id = ? " +
                "ORDER BY cs.section_code, c.course_code",
                termId);
            attachPrettySchedules(classes);
            for (Map<String, Object> row : classes) {
                String st = row.get("status") != null ? row.get("status").toString().trim() : "Open";
                if ("Open".equalsIgnoreCase(st) || "Planning".equalsIgnoreCase(st)) {
                    row.put("status", "OPEN");
                }
            }
            return classes;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    private Map<String, Object> mapClassSchedule(ClassSchedule s) {
        Map<String, Object> map = new HashMap<>();
        map.put("schedule_id", s.getScheduleId());
        map.put("course_code", s.getCourseCode());
        map.put("section_id", s.getSectionId());
        map.put("room_id", s.getRoomId());
        map.put("faculty_id", s.getFacultyId());
        
        String dayStr = "";
        if (s.getDayOfWeek() != null) {
            switch(s.getDayOfWeek()) {
                case 1: dayStr = "Mon"; break;
                case 2: dayStr = "Tue"; break;
                case 3: dayStr = "Wed"; break;
                case 4: dayStr = "Thu"; break;
                case 5: dayStr = "Fri"; break;
                case 6: dayStr = "Sat"; break;
                case 7: dayStr = "Sun"; break;
                default: dayStr = String.valueOf(s.getDayOfWeek()); break;
            }
        }
        map.put("day", dayStr);
        map.put("day_of_week", dayStr);
        
        String stStr = "TBA";
        if (s.getStartTime() != null) {
            int h = s.getStartTime().getHour();
            int m = s.getStartTime().getMinute();
            stStr = String.format("%d:%02d %s", (h > 12 ? h - 12 : (h == 0 ? 12 : h)), m, (h >= 12 ? "PM" : "AM"));
            map.put("start_time", s.getStartTime().getHour() * 100 + s.getStartTime().getMinute());
        } else {
            map.put("start_time", 0);
        }
        
        String etStr = "TBA";
        if (s.getEndTime() != null) {
            int h = s.getEndTime().getHour();
            int m = s.getEndTime().getMinute();
            etStr = String.format("%d:%02d %s", (h > 12 ? h - 12 : (h == 0 ? 12 : h)), m, (h >= 12 ? "PM" : "AM"));
            map.put("end_time", s.getEndTime().getHour() * 100 + s.getEndTime().getMinute());
        } else {
            map.put("end_time", 0);
        }
        
        map.put("pretty_schedule", (dayStr.isEmpty() || stStr.equals("TBA")) ? "TBA (Asynchronous)" : dayStr + " " + stStr + "-" + etStr);
        
        com.iuims.registrar.entity.CurriculumCatalog cat = null;
        if (s.getCourseCode() != null) {
            cat = curriculumCatalogRepository.findById(s.getCourseCode()).orElse(null);
        }
        map.put("description", cat != null ? cat.getDescription() : "");
        map.put("units", cat != null ? cat.getUnits() : 3);
        
        com.iuims.registrar.entity.SysUser u = null;
        if (s.getFacultyId() != null) {
            u = sysUserRepository.findById(s.getFacultyId()).orElse(null);
        }
        map.put("faculty_name", u != null ? u.getRealName() : "Unknown");
        map.put("status", (s.getIsUnlocked() != null && s.getIsUnlocked()) ? "OPEN" : "LOCKED");
        return map;
    }

    // ==========================================
    // 6. CLASS SCHEDULING MANAGEMENT
    // ==========================================

    public int getActiveTermId() {
        try {
            // Support both 'ACTIVE' status string and is_active flag
            return db.queryForObject(
                "SELECT term_id FROM academic_terms WHERE status = 'ACTIVE' OR is_active = 1 ORDER BY term_id DESC LIMIT 1",
                Integer.class);
        } catch (Exception e) { return 1; }
    }

    public List<Map<String, Object>> getAllTerms() {
        try { return db.queryForList("SELECT term_id, term_name, status, start_date, end_date FROM academic_terms ORDER BY term_id DESC"); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    /**
     * Returns the current global academic term as a 10-digit DB code (e.g. "1120252026").
     * Falls back to empty string if not set.
     */
    public String getCurrentGlobalTermCode() {
        try {
            String val = db.queryForObject(
                "SELECT setting_value FROM system_settings WHERE setting_key = 'CURRENT_ACADEMIC_TERM'",
                String.class);
            return (val != null && !val.isBlank()) ? val.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Returns a human-readable label for the currently active global academic term.
     * e.g. "A.Y. 2025-2026 - 1st Semester"
     */
    public String getCurrentTermLabel() {
        try {
            String raw = db.queryForObject(
                "SELECT setting_value FROM system_settings WHERE setting_key = 'CURRENT_ACADEMIC_TERM'",
                String.class);
            if (raw == null || raw.isBlank()) return "";
            // Normalize to 10-digit DB code for lookup
            String termCode;
            if (raw.startsWith("SL") && !raw.startsWith("SL_") && raw.length() >= 12) {
                // New SL format: sem at char[11], ay at chars[2..10]
                char sem = raw.charAt(11);
                termCode = sem + "1" + raw.substring(2, 10);
            } else if (raw.startsWith("SL_") && raw.length() >= 13) {
                // Legacy SL_ format
                termCode = raw.substring(3);
            } else {
                termCode = raw.trim();
            }
            // Try to get the friendly name from academic_terms first
            try {
                String name = db.queryForObject(
                    "SELECT COALESCE(NULLIF(term_name,''), CONCAT('A.Y. ', academic_year, ' - ', IF(semester_number=2,'2nd','1st'), ' Semester')) " +
                    "FROM academic_terms WHERE term_code = ? LIMIT 1",
                    String.class, termCode);
                if (name != null && !name.isBlank()) return name;
            } catch (Exception ignored) {}
            // Fallback: parse from term_code
            String ay = inferAcademicYear(termCode);
            Integer sem = inferSemester(termCode);
            if (ay != null && sem != null) {
                return "A.Y. " + ay + " - " + (sem == 2 ? "2nd" : "1st") + " Semester";
            }
            return raw;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Terms for the System Settings "Global Academic Term Transition" dropdown.
     * Value is stored in system_settings.CURRENT_ACADEMIC_TERM in SL_* format.
     */
    public List<Map<String, Object>> getAcademicTermOptionsForSettings() {
        try {
            List<Map<String, Object>> raw = db.queryForList(
                "SELECT term_id, term_code, term_name, academic_year, semester_number, status, is_active " +
                    "FROM academic_terms ORDER BY term_id DESC");

            List<Map<String, Object>> out = new ArrayList<>();
            java.util.Set<String> seenValues = new java.util.HashSet<>();
            for (Map<String, Object> r : raw) {
                String termCode = r.get("term_code") != null ? r.get("term_code").toString() : null;
                String ay = r.get("academic_year") != null ? r.get("academic_year").toString() : inferAcademicYear(termCode);
                Integer sem = r.get("semester_number") != null ? ((Number) r.get("semester_number")).intValue() : inferSemester(termCode);
                if (ay == null || sem == null || termCode == null || termCode.length() < 10) {
                    continue;
                }
                // Store CURRENT_ACADEMIC_TERM as the 10-digit DB code (format-neutral)
                String value = termCode;
                if (!seenValues.add(value)) {
                    // Defensive: avoid duplicate dropdown options if academic_terms contains duplicate rows.
                    continue;
                }
                String termName = r.get("term_name") != null ? r.get("term_name").toString() : null;
                String label = (termName != null && !termName.isBlank())
                    ? termName
                    : ("A.Y. " + ay + " - " + (sem == 2 ? "2nd Semester" : "1st Semester"));
                Map<String, Object> row = new HashMap<>();
                row.put("term_id", r.get("term_id"));
                row.put("term_code", termCode);
                row.put("value", value);
                row.put("label", label);
                out.add(row);
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Transactional
    @CacheEvict(value = "gradingWindows", allEntries = true)
    @Tool(description = "Create a new academic term")
    public String addAcademicTerm(String academicYear, int semesterNumber, String startDate, String endDate) {
        try {
            if (academicYear == null || academicYear.trim().isEmpty()) return "ERROR: Missing academic year.";
            if (semesterNumber != 1 && semesterNumber != 2) return "ERROR: Semester must be 1 or 2.";
            String ay = academicYear.trim();
            Matcher m = Pattern.compile("^(\\d{4})\\s*-\\s*(\\d{4})$").matcher(ay);
            if (!m.find()) return "ERROR: Academic year must be in YYYY-YYYY format.";
            String startY = m.group(1);
            String endY = m.group(2);
            String termCode = semesterNumber + "1" + startY + endY; // e.g. 1120242025, 2120242025

            if (academicTermRepository.findByTermCode(termCode).isPresent()) {
                return "ERROR: Term already exists (" + termCode + ").";
            }

            String termName = "A.Y. " + ay + " - " + (semesterNumber == 2 ? "2nd Semester" : "1st Semester");

            AcademicTerm term = new AcademicTerm();
            term.setTermCode(termCode);
            term.setTermName(termName);
            term.setAcademicYear(ay);
            term.setSemesterNumber(semesterNumber);
            if (startDate != null && !startDate.isBlank()) {
                term.setStartDate(java.time.LocalDate.parse(startDate));
            }
            if (endDate != null && !endDate.isBlank()) {
                term.setEndDate(java.time.LocalDate.parse(endDate));
            }
            term.setStatus("INACTIVE");
            term.setIsActive(0);
            academicTermRepository.saveAndFlush(term);

            return "SUCCESS";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private static Integer inferSemester(String termCode) {
        try {
            if (termCode != null && termCode.length() >= 1) {
                int s = Integer.parseInt(termCode.substring(0, 1));
                return (s == 1 || s == 2) ? s : null;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String inferAcademicYear(String termCode) {
        try {
            if (termCode != null && termCode.length() >= 10) {
                String start = termCode.substring(2, 6);
                String end = termCode.substring(6, 10);
                return start + "-" + end;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public List<Map<String, Object>> getAllFacultyForScheduling() {
        try {
            return db.queryForList(
                "SELECT f.faculty_id," +
                " CONCAT(COALESCE(f.first_name,''), ' ', COALESCE(f.last_name,'')) AS real_name," +
                " d.department_name" +
                " FROM faculty f" +
                " JOIN departments d ON f.department_id = d.department_id" +
                " WHERE f.active_status = 1 ORDER BY f.last_name, f.first_name");
        } catch (Exception e) { return new ArrayList<>(); }
    }

    public List<Map<String, Object>> getAllRoomsForScheduling() {
        try {
            return db.queryForList(
                "SELECT room_id, room_code, building_name, capacity, room_type " +
                "FROM rooms WHERE active_status = 1 ORDER BY room_code");
        } catch (Exception e) { return new ArrayList<>(); }
    }

    /** Returns all active courses grouped by dept with current sections + schedule rows. */
    public List<Map<String, Object>> getCoursesWithSections(int termId) {
        try {
            List<Map<String, Object>> courses = db.queryForList(
                "SELECT c.course_id, c.course_code, c.course_title, c.credit_units, " +
                "d.department_id, d.department_name FROM courses c " +
                "JOIN departments d ON c.department_id = d.department_id " +
                "WHERE c.active_status = 1 ORDER BY d.department_name, c.course_code");

            Map<Integer, List<Map<String, Object>>> sectionsByCourse = new LinkedHashMap<>();
            List<Map<String, Object>> sections = db.queryForList(
                "SELECT cs.course_id, cs.section_id, cs.section_code, cs.max_capacity, cs.section_status, " +
                    "cs.faculty_id, " +
                    "CONCAT(COALESCE(f.first_name,''),' ',COALESCE(f.last_name,'')) AS faculty_name, " +
                    "COALESCE(se_counts.enrolled_count, 0) AS enrolled_count " +
                    "FROM class_sections cs " +
                    "LEFT JOIN faculty f ON cs.faculty_id = f.faculty_id " +
                    "LEFT JOIN (" +
                    "  SELECT se.section_id, COUNT(*) AS enrolled_count " +
                    "  FROM student_enlistments se " +
                    "  WHERE 1 = 1 " +
                    enlistmentSchemaService.enlistmentStatusFilter(EnlistmentSchemaService.Scope.COMMITTED_ONLY, "se") +
                    "  GROUP BY se.section_id" +
                    ") se_counts ON se_counts.section_id = cs.section_id " +
                    "WHERE cs.term_id = ? " +
                    "ORDER BY cs.course_id, cs.section_code",
                termId);

            Map<Integer, Map<String, Object>> sectionIndex = new LinkedHashMap<>();
            for (Map<String, Object> sec : sections) {
                int courseId = ((Number) sec.get("course_id")).intValue();
                int sectionId = ((Number) sec.get("section_id")).intValue();
                sec.put("schedules", new ArrayList<Map<String, Object>>());
                sectionsByCourse.computeIfAbsent(courseId, ignored -> new ArrayList<>()).add(sec);
                sectionIndex.put(sectionId, sec);
            }

            if (!sectionIndex.isEmpty()) {
                String[] dayNames = {"", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
                List<Map<String, Object>> scheduleRows = db.queryForList(
                    "SELECT sch.section_id, sch.schedule_id, sch.day_of_week, " +
                        "TIME_FORMAT(sch.start_time,'%h:%i %p') AS start_fmt, " +
                        "TIME_FORMAT(sch.end_time,'%h:%i %p') AS end_fmt, " +
                        "IFNULL(r.room_code,'TBA') AS room_code " +
                        "FROM class_schedules sch " +
                        "JOIN class_sections cs ON cs.section_id = sch.section_id " +
                        "LEFT JOIN rooms r ON sch.room_id = r.room_id " +
                        "WHERE cs.term_id = ? " +
                        "ORDER BY sch.section_id, sch.day_of_week, sch.start_time, sch.schedule_id",
                    termId);
                for (Map<String, Object> sched : scheduleRows) {
                    int sectionId = ((Number) sched.get("section_id")).intValue();
                    int day = sched.get("day_of_week") != null ? ((Number) sched.get("day_of_week")).intValue() : 0;
                    sched.put("day_name", day >= 1 && day <= 7 ? dayNames[day] : "TBA");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> scheduleList = (List<Map<String, Object>>) sectionIndex.get(sectionId).get("schedules");
                    scheduleList.add(sched);
                }
            }

            for (Map<String, Object> course : courses) {
                int cid = ((Number) course.get("course_id")).intValue();
                course.put("sections", sectionsByCourse.getOrDefault(cid, List.of()));
            }
            return courses;
        } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); }
    }

    public ClassPage getAllClassesAdminPage(String search, String status, int requestedPage, int requestedSize) {
        int pageSize = Math.min(100, Math.max(1, requestedSize));
        int page = Math.max(1, requestedPage);
        try {
            int termId = getActiveTermId();
            String query = search != null ? search.trim().toUpperCase() : "";
            String normalizedStatus = status == null ? "ALL" : status.trim().toUpperCase();
            List<Object> args = new ArrayList<>();
            args.add(termId);
            StringBuilder where = new StringBuilder(" WHERE cs.term_id = ? ");
            if (!query.isBlank()) {
                where.append("AND (UPPER(c.course_code) LIKE ? OR UPPER(c.course_title) LIKE ? OR ")
                    .append("UPPER(cs.section_code) LIKE ? OR UPPER(CONCAT(COALESCE(f.first_name,''), ' ', COALESCE(f.last_name,''))) LIKE ?) ");
                for (int i = 0; i < 4; i++) args.add("%" + query + "%");
            }
            if ("OPEN".equals(normalizedStatus)) {
                where.append("AND UPPER(COALESCE(cs.section_status, 'OPEN')) IN ('OPEN', 'PLANNING') ");
            } else if ("SUBMITTED".equals(normalizedStatus) || "APPROVED".equals(normalizedStatus)) {
                where.append("AND UPPER(COALESCE(cs.section_status, 'OPEN')) = ? ");
                args.add(normalizedStatus);
            } else {
                normalizedStatus = "ALL";
            }
            String from = " FROM class_sections cs JOIN courses c ON cs.course_id = c.course_id " +
                "LEFT JOIN faculty f ON cs.faculty_id = f.faculty_id ";
            Integer total = db.queryForObject("SELECT COUNT(*)" + from + where, Integer.class, args.toArray());
            int totalRows = total != null ? total : 0;
            int totalPages = Math.max(1, (int) Math.ceil(totalRows / (double) pageSize));
            page = Math.min(page, totalPages);
            List<Object> pageArgs = new ArrayList<>(args);
            pageArgs.add(pageSize);
            pageArgs.add((page - 1) * pageSize);
            List<Map<String, Object>> classes = db.queryForList(
                "SELECT cs.section_id AS schedule_id, cs.section_id, cs.section_code AS section, " +
                    "COALESCE(cs.section_status, 'Open') AS status, c.course_code, c.course_title AS description, " +
                    "IFNULL(NULLIF(TRIM(CONCAT(COALESCE(f.first_name,''), ' ', COALESCE(f.last_name,''))), ''), 'TBA') AS faculty_name, " +
                    "0 AS is_unlocked " + from + where + " ORDER BY cs.section_code, c.course_code LIMIT ? OFFSET ?",
                pageArgs.toArray());
            attachPrettySchedules(classes);
            for (Map<String, Object> row : classes) {
                String value = row.get("status") != null ? row.get("status").toString().trim() : "Open";
                if ("Open".equalsIgnoreCase(value) || "Planning".equalsIgnoreCase(value)) {
                    row.put("status", "OPEN");
                }
            }
            return new ClassPage(classes, page, pageSize, totalRows, totalPages);
        } catch (Exception e) {
            return new ClassPage(List.of(), 1, pageSize, 0, 1);
        }
    }

    @Transactional
    @Tool(description = "Open a new class section for enrollment")
    public String openSection(int courseId, int termId, String sectionCode, Integer facultyId, int maxCapacity) {
        try {
            if (classSectionRepository.findByCourseIdAndTermIdAndSectionCode(courseId, termId, sectionCode).isPresent()) {
                return "ERROR: Section '" + sectionCode + "' already exists for this course.";
            }
            if (BlockOfferingService.parseBlockCode(sectionCode) != null) {
                return "ERROR: Block section codes (e.g. BSIT-1-2-A) must be created from Block Sections above, " +
                    "not per-course. Use this form only for summer/tutorial special sections.";
            }
            if (sectionCode != null && sectionCode.trim().toUpperCase().startsWith("IRREG")) {
                return "ERROR: Legacy irregular open sections are retired. Use a block section, or a summer/tutorial section if applicable.";
            }
            ClassSection section = new ClassSection();
            section.setCourseId(courseId);
            section.setTermId(termId);
            section.setSectionCode(sectionCode);
            section.setFacultyId(null);
            section.setMaxCapacity(maxCapacity);
            section.setSectionStatus("Open");
            classSectionRepository.saveAndFlush(section);
            if (facultyId != null && facultyId != 0) {
                String facultyAssignment = assignFaculty(section.getSectionId(), facultyId);
                if (!"SUCCESS".equals(facultyAssignment)) {
                    throw new IllegalArgumentException(
                        facultyAssignment.startsWith("ERROR: ")
                            ? facultyAssignment.substring("ERROR: ".length())
                            : facultyAssignment);
                }
            }
            return "SUCCESS";
        } catch (Exception e) { return "ERROR: " + e.getMessage(); }
    }

    @Transactional
    public String addScheduleSlot(int sectionId, int day1, String startTime, String endTime, Integer roomId, Integer day2) {
        try {
            if (day1 < 1 || day1 > 7 || (day2 != null && day2 > 0 && (day2 < 1 || day2 > 7))) {
                return "ERROR: Day must be between 1 and 7.";
            }
            java.time.LocalTime parsedStart = java.time.LocalTime.parse(startTime);
            java.time.LocalTime parsedEnd = java.time.LocalTime.parse(endTime);
            if (!parsedStart.isBefore(parsedEnd)) {
                return "ERROR: Start time must be before end time.";
            }
            if (roomId == null || roomId == 0) {
                return "ERROR: Room is required before a schedule slot can be saved.";
            }
            Integer rid = (roomId == null || roomId == 0) ? null : roomId;
            Integer sectionFacultyId = sectionFacultyId(sectionId);

            String day1Conflict = scheduleConflictValidator.validateNewSlot(
                sectionId, sectionFacultyId, rid, day1, parsedStart, parsedEnd);
            if (day1Conflict != null) return "ERROR: " + day1Conflict;
            if (day2 != null && day2 > 0 && day2 != day1) {
                String day2Conflict = scheduleConflictValidator.validateNewSlot(
                    sectionId, sectionFacultyId, rid, day2, parsedStart, parsedEnd);
                if (day2Conflict != null) return "ERROR: " + day2Conflict;
            }
            
            ClassSchedule s1 = new ClassSchedule();
            s1.setSectionId(sectionId);
            s1.setFacultyId(sectionFacultyId);
            s1.setDayOfWeek(day1);
            s1.setStartTime(parsedStart);
            s1.setEndTime(parsedEnd);
            s1.setRoomId(rid);
            classScheduleRepository.save(s1);
            
            if (day2 != null && day2 > 0 && day2 != day1) {
                ClassSchedule s2 = new ClassSchedule();
                s2.setSectionId(sectionId);
                s2.setFacultyId(sectionFacultyId);
                s2.setDayOfWeek(day2);
                s2.setStartTime(parsedStart);
                s2.setEndTime(parsedEnd);
                s2.setRoomId(rid);
                classScheduleRepository.save(s2);
            }
            classScheduleRepository.flush();
            return "SUCCESS";
        } catch (Exception e) { return "ERROR: " + e.getMessage(); }
    }

    public ScheduleConflictValidator.ConflictPreview getScheduleConflictPreview(int termId, int maxResults) {
        try {
            return scheduleConflictValidator.findExistingConflictPreview(termId, maxResults);
        } catch (Exception e) {
            return new ScheduleConflictValidator.ConflictPreview(List.of(), false);
        }
    }

    @Transactional
    public String repairScheduleConflicts(int termId) {
        try {
            ScheduleConflictValidator.RepairResult result =
                scheduleConflictValidator.repairExistingConflicts(termId);
            if (!result.changed()) {
                return "No schedulable conflicts needed repair for this term.";
            }
            return "Repaired scheduling conflicts for term " + termId
                + ": cleared " + result.roomAssignmentsCleared() + " room assignment(s), deleted "
                + result.duplicateRowsDeleted() + " duplicate slot(s), deleted "
                + result.sectionOverlapRowsDeleted() + " section-overlap slot(s), cleared faculty from "
                + result.facultySectionsCleared() + " section(s), and resynced "
                + result.facultyScheduleRowsCleared() + " schedule row(s).";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Transactional
    public String removeScheduleSlot(int scheduleId) {
        try { classScheduleRepository.deleteById(scheduleId); return "SUCCESS"; }
        catch (Exception e) { return "ERROR: " + e.getMessage(); }
    }

    @Transactional
    public String closeSection(int sectionId) {
        try {
            int enrolled = db.queryForObject(
                "SELECT COUNT(*) FROM student_enlistments se WHERE se.section_id = ?" +
                    enlistmentSchemaService.enlistmentStatusFilter(EnlistmentSchemaService.Scope.COMMITTED_ONLY, "se"),
                Integer.class, sectionId);
            if (enrolled > 0) return "ERROR: Cannot close — " + enrolled + " student(s) still enrolled.";
            classScheduleRepository.deleteAll(classScheduleRepository.findBySectionId(sectionId));
            classSectionRepository.deleteById(sectionId);
            return "SUCCESS";
        } catch (Exception e) { return "ERROR: " + e.getMessage(); }
    }

    @Transactional
    public String assignFaculty(int sectionId, Integer facultyId) {
        try {
            ClassSection section = classSectionRepository.findById(sectionId).orElse(null);
            if (section == null) return "ERROR: Section not found.";
            Integer normalizedFacultyId = (facultyId == null || facultyId == 0) ? null : facultyId;
            if (normalizedFacultyId != null) {
                String conflict = scheduleConflictValidator.validateFacultyAssignment(sectionId, normalizedFacultyId);
                if (conflict != null) return "ERROR: " + conflict;
            }
            section.setFacultyId(normalizedFacultyId);
            classSectionRepository.saveAndFlush(section);
            db.update(
                "UPDATE class_schedules SET faculty_id = ? WHERE section_id = ?",
                section.getFacultyId(), sectionId);
            return "SUCCESS";
        } catch (Exception e) { return "ERROR: " + e.getMessage(); }
    }

    private Integer sectionFacultyId(int sectionId) {
        try {
            return db.queryForObject(
                "SELECT faculty_id FROM class_sections WHERE section_id = ?",
                Integer.class,
                sectionId);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    @CacheEvict(value = "gradingWindows", allEntries = true)
    public TermTransitionResult triggerTermTransition(String newGlobalTermCode) {
        String targetDbTermCode = normalizeDbTermCode(newGlobalTermCode);
        if (targetDbTermCode == null) {
            String errorMessage = "Invalid target academic term code.";
            recordTermTransitionAudit(newGlobalTermCode, null, null, false, 0, 0, errorMessage);
            return TermTransitionResult.error(errorMessage);
        }
        Integer targetTermId = findAcademicTermId(targetDbTermCode);
        if (targetTermId == null) {
            String errorMessage = "Target academic term does not exist in academic_terms: " + targetDbTermCode;
            recordTermTransitionAudit(newGlobalTermCode, targetDbTermCode, null, false, 0, 0, errorMessage);
            return TermTransitionResult.error(errorMessage);
        }
        Map<String, Object> readiness = termFeeAdminService.buildTermReadinessSummary(targetTermId);
        if (!Boolean.TRUE.equals(readiness.get("ready"))) {
            String errorMessage = readinessErrorMessage(readiness);
            recordTermTransitionAudit(newGlobalTermCode, targetDbTermCode, targetTermId, false, 0, 0, errorMessage);
            return TermTransitionResult.error(errorMessage);
        }
        int advanced = 0;
        java.util.concurrent.atomic.AtomicInteger debtCounter = new java.util.concurrent.atomic.AtomicInteger(0);

        if (!syncAcademicTermsActiveFlag(targetTermId)) {
            String errorMessage = "Unable to activate academic term row: " + targetDbTermCode;
            recordTermTransitionAudit(newGlobalTermCode, targetDbTermCode, targetTermId, false, 0, 0, errorMessage);
            return TermTransitionResult.error(errorMessage);
        }

        String sourceGlobalTermCode = getCurrentGlobalTermCode();
        Integer sourceTermId = findAcademicTermId(sourceGlobalTermCode);

        // Store the global term as the 10-digit DB code (used by all SL builders)
        String normalizedGlobalTermCode = targetDbTermCode;
        SystemSetting setting = systemSettingRepository.findById("CURRENT_ACADEMIC_TERM")
            .orElse(new SystemSetting("CURRENT_ACADEMIC_TERM", normalizedGlobalTermCode));
        setting.setSettingValue(normalizedGlobalTermCode);
        systemSettingRepository.saveAndFlush(setting);
        ensureTermPolicyRow(targetTermId);

        int targetSem = Character.getNumericValue(targetDbTermCode.charAt(0));
        String targetAyStart = targetDbTermCode.substring(2, 6);
        String targetAyEnd   = targetDbTermCode.substring(6, 10);

        List<com.iuims.registrar.entity.SysUser> students = sysUserRepository.findByRoleAndIsActiveAndAdmissionStatusIn("Student", true, java.util.Arrays.asList("ENROLLED", "ADMITTED", "ACTIVE"));
        List<Map<String, Object>> rolloverSnapshots = new ArrayList<>();
        
        for (com.iuims.registrar.entity.SysUser s : students) {
            String studentNumber = s.getUsername();

            int currSem = s.getSemester() != null ? s.getSemester() : 1;
            int currYr = s.getYearLevel() != null ? s.getYearLevel() : 1;
            String currTerm = s.getTermYear();
            
            // Determine the student's current AY from their stored SL code
            String currAyStart = "";
            String currAyEnd = "";
            if (currTerm != null && currTerm.startsWith("SL") && !currTerm.startsWith("SL_") && currTerm.length() >= 12) {
                currAyStart = currTerm.substring(2, 6);
                currAyEnd   = currTerm.substring(6, 10);
            } else if (currTerm != null && currTerm.startsWith("SL_") && currTerm.length() >= 13) {
                currAyStart = currTerm.substring(5, 9);
                currAyEnd   = currTerm.substring(9, 13);
            }

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("username", studentNumber);
            snapshot.put("archive_key", studentProfileService.getArchiveKey(studentNumber));
            snapshot.put("source_term_code", currTerm);
            snapshot.put("source_term_id", sourceTermId);
            snapshot.put("target_term_code", targetDbTermCode);
            snapshot.put("target_term_id", targetTermId);
            snapshot.put("program_code", s.getProgramCode());
            snapshot.put("year_level", s.getYearLevel());
            snapshot.put("semester", s.getSemester());
            snapshot.put("student_type", s.getStudentType());
            snapshot.put("enrollment_status_type", s.getStudentType());
            rolloverSnapshots.add(snapshot);

            // Skip students already on the target term
            String expectedCurrentSl = "SL" + targetAyStart + targetAyEnd + currYr + targetSem;
            if (currTerm != null && currTerm.equals(expectedCurrentSl)) {
                continue;
            }

            if (currSem == 2 && targetSem == 1) {
                currYr++;
            } else if (!currAyStart.equals(targetAyStart)) {
                if (currSem == 2) {
                    currYr++;
                } else if (currSem == 1 && targetSem == 1) {
                    currYr++;
                }
            }
            currSem = targetSem;
            // Build new student SL code in canonical format: SL[AYstart][AYend][YL][Sem]
            String studentTermYear = "SL" + targetAyStart + targetAyEnd + currYr + currSem;

            eventPublisher.publishEvent(new TermTransitionEvent(studentNumber, targetDbTermCode, debtCounter));

            s.setSemester(currSem);
            s.setYearLevel(currYr);
            s.setTermYear(studentTermYear);
            s.setAdmissionStatus("ADMITTED");
            if (s.getStudentType() == null || s.getStudentType().isEmpty()) {
                s.setStudentType("Continuing");
            }
            s.setEnrollmentStartTime(null);
            
            com.iuims.registrar.entity.Student student = studentRepository.findById(studentNumber).orElse(null);
            if (student != null) {
                student.setSemester(currSem);
                student.setYearLevel(currYr);
                student.setTermYear(studentTermYear);
                student.setAdmissionStatus("ADMITTED");
                if (student.getStudentType() == null || student.getStudentType().isEmpty()) {
                    student.setStudentType("Continuing");
                }
                studentRepository.save(student);
            }
            
            advanced++;
        }
        sysUserRepository.saveAllAndFlush(students);
        termRolloverDispositionService.seedQueueForTermTransition(
            rolloverSnapshots,
            sourceGlobalTermCode,
            sourceTermId,
            targetDbTermCode,
            targetTermId,
            "registrar");

        recordTermTransitionAudit(newGlobalTermCode, targetDbTermCode, targetTermId, true, advanced, debtCounter.get(), null);
        return TermTransitionResult.success(advanced, debtCounter.get());
    }

    private void ensureTermPolicyRow(Integer termId) {
        if (termId == null || termId <= 0) {
            return;
        }
        try {
            AcademicTermPolicy policy = academicTermPolicyRepository.findById(termId).orElse(null);
            if (policy != null) {
                return;
            }
            AcademicTermPolicy newPolicy = new AcademicTermPolicy();
            newPolicy.setTermId(termId);
            newPolicy.setUpdatedAt(java.time.LocalDateTime.now());
            academicTermPolicyRepository.saveAndFlush(newPolicy);
        } catch (Exception ignored) {
        }
    }

    private void recordTermTransitionAudit(
        String requestedTermCode,
        String targetDbTermCode,
        Integer targetTermId,
        boolean success,
        int advanced,
        int withForwardedDebt,
        String errorMessage
    ) {
        try {
            TermTransitionAudit audit = new TermTransitionAudit();
            audit.setRequestedTermCode(requestedTermCode);
            audit.setTargetDbTermCode(targetDbTermCode);
            audit.setTargetTermId(targetTermId);
            audit.setSuccess(success ? (byte) 1 : (byte) 0);
            audit.setAdvancedCount(advanced);
            audit.setForwardedDebtCount(withForwardedDebt);
            audit.setErrorMessage(truncateAuditMessage(errorMessage));
            termTransitionAuditRepository.saveAndFlush(audit);
        } catch (Exception e) {
            // Auditing should never block the operational term transition path.
        }
    }

    private void ensureTermTransitionAuditTable() {
        // Handled by JPA schema generation in tests, or Flyway in prod
    }

    private String truncateAuditMessage(String msg) {
        if (msg == null) return null;
        return msg.length() > 500 ? msg.substring(0, 497) + "..." : msg;
    }

    /** Counts from {@link #triggerTermTransition(String)}. */
    public record TermTransitionResult(int advanced, int withForwardedDebt, String errorMessage) {
        public static TermTransitionResult success(int advanced, int withForwardedDebt) {
            return new TermTransitionResult(advanced, withForwardedDebt, "");
        }

        public static TermTransitionResult error(String errorMessage) {
            return new TermTransitionResult(0, 0, errorMessage);
        }

        public boolean success() {
            return errorMessage == null || errorMessage.isBlank();
        }
    }

    private String normalizeDbTermCode(String globalTermCode) {
        if (globalTermCode == null || globalTermCode.isBlank()) {
            return null;
        }
        String t = globalTermCode.trim();
        // New SL format: SL[AYstart4][AYend4][YL][Sem] -> convert to DB code
        if (t.startsWith("SL") && !t.startsWith("SL_") && t.length() >= 12) {
            char sem = t.charAt(11);
            return sem + "1" + t.substring(2, 10);
        } else if (t.startsWith("SL_")) {
            // Legacy SL_ format
            t = t.substring(3);
        }
        if (!Pattern.matches("[12]\\d\\d{8}", t)) {
            return null;
        }
        return t;
    }

    private String readinessErrorMessage(Map<String, Object> readiness) {
        int missingFees = intValue(readiness.get("missingFeeScopeCount"));
        int fallbackFees = intValue(readiness.get("fallbackFeeScopeCount"));
        int incompleteFees = intValue(readiness.get("incompleteFeeScopeCount"));
        int missingCurricula = readiness.get("missingCurricula") instanceof List
            ? ((List<?>) readiness.get("missingCurricula")).size()
            : 0;
        Object missingTables = readiness.get("missingTables");
        if (missingTables instanceof List && !((List<?>) missingTables).isEmpty()) {
            return "Target term is not ready: missing required tables " + missingTables + ".";
        }
        return "Target term is not ready: " + missingFees + " missing fee scope(s), "
            + fallbackFees + " fallback fee scope(s), "
            + incompleteFees + " incomplete primary-rate fee scope(s), "
            + missingCurricula + " program(s) without active curriculum.";
    }

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private Integer findAcademicTermId(String dbTermCode) {
        if (dbTermCode == null) return null;
        AcademicTerm term = academicTermRepository.findFirstByTermCodeOrderByTermIdDesc(dbTermCode.trim()).orElse(null);
        return term != null ? term.getTermId() : null;
    }

    private boolean syncAcademicTermsActiveFlag(int targetTermId) {
        if (targetTermId <= 0) return false;
        try {
            db.update(
                "UPDATE academic_terms SET is_active = 0, status = 'INACTIVE' WHERE term_id <> ?",
                targetTermId);
            int activated = db.update(
                "UPDATE academic_terms SET is_active = 1, status = 'ACTIVE' WHERE term_id = ?",
                targetTermId);
            return activated > 0;
        } catch (Exception ignored) {}
        return false;
    }

    public ClassInfoDto getClassInfoDto(int classId) {
        Map<String, Object> info = getClassInfo(classId);
        if (info == null || info.isEmpty()) return null;
        
        return new ClassInfoDto(
            classId,
            info.get("section_id") != null ? ((Number) info.get("section_id")).intValue() : 0,
            (String) info.get("section_code"),
            info.get("term_id") != null ? ((Number) info.get("term_id")).intValue() : 0,
            (String) info.get("status"),
            (String) info.get("course_code"),
            (String) info.get("description"),
            (String) info.get("faculty_first"),
            (String) info.get("faculty_last"),
            (String) info.get("pretty_schedule")
        );
    }
}
