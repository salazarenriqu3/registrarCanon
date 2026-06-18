package com.iuims.registrar.academic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import java.math.BigDecimal;

import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.finance.TermFeeAdminService;
import com.iuims.registrar.forms.StudentDocumentTrailService;
import com.iuims.registrar.core.PolicySettings;
import com.iuims.registrar.core.GradeOutcomeSql;
import com.iuims.registrar.core.EnlistmentSchemaService;
import com.iuims.registrar.core.SystemSettingRepository;
import com.iuims.registrar.core.SystemSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AcademicGradingService {

    private final JdbcTemplate db;
    private final TermFeeAdminService termFeeAdminService;
    private final AcademicTermRepository academicTermRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final TermTransitionAuditRepository termTransitionAuditRepository;
    private final GradeRepository gradeRepository;

    private final com.iuims.registrar.curriculum.ProgramRepository programRepository;
    private final com.iuims.registrar.curriculum.CurriculumTemplateRepository curriculumTemplateRepository;
    private final com.iuims.registrar.curriculum.CurriculumCourseRepository curriculumCourseRepository;
    private final com.iuims.registrar.curriculum.CurriculumCatalogRepository curriculumCatalogRepository;
    private final GradingTermWindowRepository gradingTermWindowRepository;
    private final AcademicTermPolicyRepository academicTermPolicyRepository;
    private final ClassSectionRepository classSectionRepository;
    private final ClassScheduleRepository classScheduleRepository;
    private final AcademicGradingRepository academicGradingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EnlistmentSchemaService enlistmentSchemaService;
    private final ScheduleConflictValidator scheduleConflictValidator;
    
    // Phase 3.5: Entity Expansion Repositories
    private final com.iuims.registrar.core.SysUserRepository sysUserRepository;
    private final com.iuims.registrar.core.StudentRepository studentRepository;
    private final com.iuims.registrar.curriculum.CourseRepository courseRepository;
    private final com.iuims.registrar.academic.VpaaExtensionRepository vpaaExtensionRepository;
    private final com.iuims.registrar.academic.GradeChangeRequestRepository gradeChangeRequestRepository;
    private final com.iuims.registrar.core.DepartmentRepository departmentRepository;
    private final StudentDocumentTrailService documentTrailService;

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
            EnlistmentSchemaService enlistmentSchemaService,
            com.iuims.registrar.core.SysUserRepository sysUserRepository,
            com.iuims.registrar.core.StudentRepository studentRepository,
            com.iuims.registrar.curriculum.CourseRepository courseRepository,
            com.iuims.registrar.academic.VpaaExtensionRepository vpaaExtensionRepository,
            com.iuims.registrar.academic.GradeChangeRequestRepository gradeChangeRequestRepository,
            com.iuims.registrar.core.DepartmentRepository departmentRepository,
            com.iuims.registrar.curriculum.ProgramRepository programRepository,
            com.iuims.registrar.curriculum.CurriculumTemplateRepository curriculumTemplateRepository,
            com.iuims.registrar.curriculum.CurriculumCourseRepository curriculumCourseRepository,
            com.iuims.registrar.curriculum.CurriculumCatalogRepository curriculumCatalogRepository,
            GradingTermWindowRepository gradingTermWindowRepository,
            AcademicTermPolicyRepository academicTermPolicyRepository,
            StudentDocumentTrailService documentTrailService) {
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
        this.enlistmentSchemaService = enlistmentSchemaService;
        this.scheduleConflictValidator = new ScheduleConflictValidator(db);
        this.sysUserRepository = sysUserRepository;
        this.studentRepository = studentRepository;
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
    }

    // ==========================================
    // 1. GRADING COMPUTATIONS
    // ==========================================
    @Transactional 
    public Map<String, Object> saveGradeAsync(int gradeId, String prelimStr, String midStr, String finalStr) {

        double p = parseScore(prelimStr); double m = parseScore(midStr); double f = parseScore(finalStr);
        
        Grade grade = gradeRepository.findById(gradeId).orElse(null);
        if (grade == null) return new HashMap<>();

        if ("LOCKED".equalsIgnoreCase(grade.getGradeLockStatus())) {
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
        syncLegacyAcademicStatus(grade, remarks);
        gradeRepository.saveAndFlush(grade);

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
        grade.setGradeLockStatus("LOCKED");
        grade.setGradeLockReason(reason);
        grade.setRegistrarFinalizedAt(java.time.LocalDateTime.now());
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
                .map(com.iuims.registrar.core.SysUser::getUsername)
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
                    com.iuims.registrar.curriculum.Course c = courseRepository.findById(g.getCourseId()).orElse(null);
                    if (c != null) {
                        courseCode = c.getCourseCode();
                        courseTitle = c.getCourseTitle();
                        
                        List<com.iuims.registrar.curriculum.CurriculumCourse> ccs = curriculumCourseRepository.findByCourseId(c.getCourseId());
                        if (!ccs.isEmpty()) {
                            yearLevel = ccs.stream().mapToInt(com.iuims.registrar.curriculum.CurriculumCourse::getYearLevel).min().orElse(1);
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
        try {
            String studentNumber = sysUserRepository.findById(sid)
                .map(com.iuims.registrar.core.SysUser::getUsername)
                .orElse(null);
            if (studentNumber == null) return 24;

            com.iuims.registrar.core.Student student = studentRepository.findById(studentNumber).orElse(null);
            if (student == null) return 24;
            
            int yearLevel    = student.getYearLevel() != null ? student.getYearLevel() : 1;
            int semester     = student.getSemester() != null ? student.getSemester() : 1;
            String programCode = student.getProgramCode();

            int curriculumUnits = 0;
            com.iuims.registrar.curriculum.Program p = programRepository.findByProgramCode(programCode);
            if (p != null) {
                List<com.iuims.registrar.curriculum.CurriculumTemplate> templates = curriculumTemplateRepository.findByProgramId(p.getProgramId());
                for (com.iuims.registrar.curriculum.CurriculumTemplate ct : templates) {
                    List<com.iuims.registrar.curriculum.CurriculumCourse> courses = curriculumCourseRepository.findByCurriculumId(ct.getCurriculumId());
                    for (com.iuims.registrar.curriculum.CurriculumCourse cc : courses) {
                        if (cc.getYearLevel() != null && cc.getYearLevel() == yearLevel && 
                            cc.getSemesterNumber() != null && cc.getSemesterNumber() == semester) {
                            if (cc.getCourseId() != null) {
                                com.iuims.registrar.curriculum.Course course = courseRepository.findById(cc.getCourseId()).orElse(null);
                                if (course != null && course.getCreditUnits() != null) {
                                    curriculumUnits += course.getCreditUnits();
                                }
                            }
                        }
                    }
                }
            }

            int maxUnits = curriculumUnits > 0 ? Math.max(24, curriculumUnits) : 24;

            if (isGraduating(programCode, yearLevel)) {
                maxUnits += 6;
            }

            return maxUnits;
        } catch (Exception e) { return 24; }
    }

    /**
     * A student is "graduating" if their year_level equals the highest
     * year_level defined in their program's curriculum mapping.
     */
    public boolean isGraduating(String programCode, int yearLevel) {
        try {
            com.iuims.registrar.curriculum.Program p = programRepository.findByProgramCode(programCode);
            if (p == null) return false;
            
            List<com.iuims.registrar.curriculum.CurriculumTemplate> templates = curriculumTemplateRepository.findByProgramId(p.getProgramId());
            int maxYear = 0;
            for (com.iuims.registrar.curriculum.CurriculumTemplate ct : templates) {
                List<com.iuims.registrar.curriculum.CurriculumCourse> courses = curriculumCourseRepository.findByCurriculumId(ct.getCurriculumId());
                for (com.iuims.registrar.curriculum.CurriculumCourse cc : courses) {
                    if (cc.getYearLevel() != null && cc.getYearLevel() > maxYear) {
                        maxYear = cc.getYearLevel();
                    }
                }
            }
            return maxYear > 0 && yearLevel >= maxYear;
        } catch (Exception e) { return false; }
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
        String configured = null;
        try {
            AcademicTermPolicy policy = academicTermPolicyRepository.findById(termId).orElse(null);
            if (policy != null && policy.getIncExpirationDate() != null) {
                configured = policy.getIncExpirationDate().toString();
            }
        } catch (Exception ignored) {}
        if (configured != null && !configured.isBlank()) {
            settings.put("INC_EXPIRATION_DATE", configured);
            settings.put("INC_EXPIRATION_SOURCE", "TERM");
            return;
        }
        String fallback = fallbackIncExpirationDate(settings);
        settings.put("INC_EXPIRATION_DATE", fallback);
        settings.put("INC_EXPIRATION_SOURCE", fallback != null ? "FALLBACK_FINALS_PLUS_ONE_YEAR" : "UNSET");
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
            if (params.containsKey("INC_EXPIRATION_DATE")) {
                saveTermPolicySettings(termId, params.get("INC_EXPIRATION_DATE"));
            }
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

    private void saveTermPolicySettings(int termId, String incExpirationDate) {
        String cleanDate = blankToNull(incExpirationDate);
        AcademicTermPolicy policy = academicTermPolicyRepository.findById(termId).orElse(new AcademicTermPolicy());
        policy.setTermId(termId);
        if (cleanDate != null) {
            policy.setIncExpirationDate(java.time.LocalDate.parse(cleanDate));
        } else {
            policy.setIncExpirationDate(null);
        }
        policy.setUpdatedAt(java.time.LocalDateTime.now());
        academicTermPolicyRepository.save(policy);
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
        for (Map<String, Object> row : classes) {
            Object sectionId = row.get("section_id");
            try {
                List<Map<String, Object>> schedules = db.queryForList(
                    "SELECT day_of_week, start_time, end_time FROM class_schedules WHERE section_id = ? ORDER BY day_of_week, start_time",
                    sectionId);
                if (schedules.isEmpty()) {
                    row.put("pretty_schedule", "TBA");
                    continue;
                }
                List<String> parts = new ArrayList<>();
                for (Map<String, Object> schedule : schedules) {
                    parts.add(dayLabel(schedule.get("day_of_week")) + " " +
                        timeLabel(schedule.get("start_time")) + "-" + timeLabel(schedule.get("end_time")));
                }
                row.put("pretty_schedule", String.join(", ", parts));
            } catch (Exception e) {
                row.put("pretty_schedule", "TBA");
            }
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
                com.iuims.registrar.curriculum.Course c = courseRepository.findById(g.getCourseId()).orElse(null);
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
        setSqlSafeUpdates(false); 
        try { 
            finalizeSectionGradeRemarks(scheduleId); 
            gradeRepository.updateStatusBySectionId(scheduleId, "SUBMITTED");
            classSectionRepository.updateStatus(scheduleId, "PENDING_APPROVAL");
            vpaaExtensionRepository.updateStatusByScheduleId(scheduleId, "COMPLETED"); 
        } catch (Exception e) {} finally { setSqlSafeUpdates(true); } 
    }

    @Transactional 
    public void unsubmitClassGrades(int scheduleId) { 
        setSqlSafeUpdates(false); 
        try { 
            gradeRepository.updateStatusBySectionIdAndStatus(scheduleId, "DRAFT", "SUBMITTED");
            classSectionRepository.updateStatusIfIn(scheduleId, "Open", List.of("SUBMITTED", "PENDING_APPROVAL"));
        } catch (Exception e) {} finally { setSqlSafeUpdates(true); } 
    }

    @Transactional 
    public void finalizeClassGrades(int scheduleId) { 
        setSqlSafeUpdates(false); 
        try { 
            finalizeSectionGradeRemarks(scheduleId); 
            gradeRepository.updateStatusBySectionId(scheduleId, "SUBMITTED");
            classSectionRepository.updateStatus(scheduleId, "SUBMITTED");
        } catch (Exception e) {} finally { setSqlSafeUpdates(true); } 
    }

    @Transactional 
    public void revertClassToDraft(int scheduleId) { 
        setSqlSafeUpdates(false); 
        try { 
            gradeRepository.updateStatusBySectionId(scheduleId, "DRAFT");
            classSectionRepository.updateStatus(scheduleId, "Open");
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
            lockRegistrarOutcome(gradeId, 5.00, "Failed", "INC_EXPIRED");
        }
        return gradeIds.size();
    }

    private void finalizeSectionGradeRemarks(int sectionId) {
        List<Grade> grades = gradeRepository.findBySectionId(sectionId);
        for (Grade grade : grades) {
            if ("LOCKED".equalsIgnoreCase(grade.getGradeLockStatus())) {
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
            syncLegacyAcademicStatus(grade, remarks);
        }
        gradeRepository.saveAllAndFlush(grades);
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
                    com.iuims.registrar.curriculum.Course c = courseRepository.findById(cs.getCourseId()).orElse(null);
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
                            facultyName = sysUserRepository.findById(cs.getFacultyId())
                                .map(com.iuims.registrar.core.SysUser::getRealName).orElse("");
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
        gradeChangeRequestRepository.saveAndFlush(req);
        Grade grade = gradeRepository.findById(gradeId).orElse(null);
        if (grade != null) {
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
        rejectGradeChange(requestId, "registrar");
    }

    public void rejectGradeChange(int requestId, String rejectedBy) {
        gradeChangeRequestRepository.findById(requestId).ifPresent(r -> {
            r.setStatus("REJECTED");
            gradeChangeRequestRepository.saveAndFlush(r);
            Grade grade = gradeRepository.findById(r.getGradeId().intValue()).orElse(null);
            if (grade != null) {
                documentTrailService.recordStudentEvent(
                    grade.getStudentId(),
                    "STUDENT",
                    "GRADE_CHANGE",
                    "GRADE_CHANGE_REJECTED",
                    requestTypeLabel(r.getRequestType()) + " rejected",
                    "Request #" + requestId + " rejected by " + rejectedBy + ".",
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
        
        com.iuims.registrar.curriculum.Course c = null;
        if (g.getCourseId() != null) {
            c = courseRepository.findById(g.getCourseId()).orElse(null);
        }
        
        String courseCode = c != null ? c.getCourseCode() : "";
        
        String studentName = g.getStudentName();
        if (studentName == null || studentName.trim().isEmpty()) {
            studentName = g.getStudentId();
            com.iuims.registrar.core.Student s = studentRepository.findById(g.getStudentId()).orElse(null);
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
            com.iuims.registrar.core.SysUser user = sysUserRepository.findById(userId).orElse(null);
            if (user == null) {
                return "Unknown";
            }
            String username = user.getUsername();
            if (username != null && !username.isBlank()) {
                String alias = facultyLoginAlias(username);
                String lookup = alias != null ? alias : username;
                try {
                    String fromFaculty = db.queryForObject(
                        "SELECT CONCAT(first_name, ' ', last_name) FROM faculty WHERE employee_number = ? LIMIT 1",
                        String.class, lookup);
                    if (fromFaculty != null && !fromFaculty.isBlank()) {
                        return fromFaculty.trim();
                    }
                } catch (Exception ignored) {}
            }
            if (user.getRealName() != null && !user.getRealName().isBlank()) {
                return user.getRealName().trim();
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

    // ==========================================
    // 5. USER & ADMIN UTILITIES
    // ==========================================
    public Map<String, Object> findStudentByIdOrName(String q) {
        List<com.iuims.registrar.core.Student> students = studentRepository.searchStudents(q);
        if (students.isEmpty()) return null;
        com.iuims.registrar.core.Student s = students.get(0);
        
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("student_number", s.getStudentNumber());
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
        m.put("username", s.getStudentNumber());
        return m;
    }
    
    public List<Map<String, Object>> searchStudentsBySurname(String q) { 
        return studentRepository.searchStudents(q).stream().limit(10).map(s -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("username", s.getStudentNumber());
            m.put("real_name", s.getFirstName() + " " + s.getLastName());
            return m;
        }).collect(java.util.stream.Collectors.toList());
    }

    public String getUsernameFromId(int uid) { return sysUserRepository.findById(uid).map(com.iuims.registrar.core.SysUser::getUsername).orElse(null); }
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
    
    private Map<String, Object> sysUserToMap(com.iuims.registrar.core.SysUser u) {
        if (u == null) return null;
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("user_id", u.getUserId());
        m.put("username", u.getUsername());
        m.put("real_name", u.getRealName());
        m.put("role", u.getRole());
        m.put("password", u.getPassword());
        m.put("is_active", u.getIsActive() != null && u.getIsActive() ? 1 : 0);
        m.put("program_code", u.getProgramCode());
        m.put("granted_permissions", u.getGrantedPermissions());
        return m;
    }

    public Map<String, Object> login(String u, String p) {
        try {
            com.iuims.registrar.core.SysUser user = sysUserRepository.findByUsername(u).orElse(null);
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
            u.setPassword(org.mindrot.jbcrypt.BCrypt.hashpw("1234", org.mindrot.jbcrypt.BCrypt.gensalt()));
            sysUserRepository.save(u);
        });
    }
    
    public List<Map<String, Object>> getAllUsers() {
        return sysUserRepository.findAll().stream().map(this::sysUserToMap).collect(java.util.stream.Collectors.toList());
    }
    
    public void createUser(String u, String r, String role, String p, List<String> perm) {
        com.iuims.registrar.core.SysUser user = new com.iuims.registrar.core.SysUser();
        user.setUsername(u);
        user.setPassword(org.mindrot.jbcrypt.BCrypt.hashpw("1234", org.mindrot.jbcrypt.BCrypt.gensalt()));
        user.setRealName(r);
        user.setRole(role);
        user.setProgramCode(p);
        user.setGrantedPermissions(perm != null ? perm.toString() : "[]");
        user.setIsActive(true);
        sysUserRepository.save(user);
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
        
        com.iuims.registrar.curriculum.CurriculumCatalog cat = null;
        if (s.getCourseCode() != null) {
            cat = curriculumCatalogRepository.findById(s.getCourseCode()).orElse(null);
        }
        map.put("description", cat != null ? cat.getDescription() : "");
        map.put("units", cat != null ? cat.getUnits() : 3);
        
        com.iuims.registrar.core.SysUser u = null;
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

            for (Map<String, Object> course : courses) {
                int cid = ((Number) course.get("course_id")).intValue();
                List<Map<String, Object>> sections = db.queryForList(
                    "SELECT cs.section_id, cs.section_code, cs.max_capacity, cs.section_status," +
                    " cs.faculty_id," +
                    " CONCAT(COALESCE(f.first_name,''),' ',COALESCE(f.last_name,'')) AS faculty_name," +
                    " (SELECT COUNT(*) FROM student_enlistments se WHERE se.section_id = cs.section_id" +
                    enlistmentSchemaService.enlistmentStatusFilter(EnlistmentSchemaService.Scope.COMMITTED_ONLY, "se") +
                    ") AS enrolled_count " +
                    "FROM class_sections cs " +
                    "LEFT JOIN faculty f ON cs.faculty_id = f.faculty_id " +
                    "WHERE cs.course_id = ? AND cs.term_id = ? ORDER BY cs.section_code", cid, termId);

                for (Map<String, Object> sec : sections) {
                    int sid = ((Number) sec.get("section_id")).intValue();
                    List<Map<String, Object>> scheds = db.queryForList(
                        "SELECT sch.schedule_id, sch.day_of_week, " +
                        "TIME_FORMAT(sch.start_time,'%h:%i %p') AS start_fmt, " +
                        "TIME_FORMAT(sch.end_time,'%h:%i %p') AS end_fmt, " +
                        "IFNULL(r.room_code,'TBA') AS room_code " +
                        "FROM class_schedules sch LEFT JOIN rooms r ON sch.room_id = r.room_id " +
                        "WHERE sch.section_id = ? ORDER BY sch.day_of_week", sid);
                    String[] dayNames = {"","MON","TUE","WED","THU","FRI","SAT","SUN"};
                    for (Map<String, Object> s : scheds) {
                        int d = s.get("day_of_week") != null ? ((Number) s.get("day_of_week")).intValue() : 0;
                        s.put("day_name", d >= 1 && d <= 7 ? dayNames[d] : "TBA");
                    }
                    sec.put("schedules", scheds);
                }
                course.put("sections", sections);
            }
            return courses;
        } catch (Exception e) { e.printStackTrace(); return new ArrayList<>(); }
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
                    "not per-course. Use IRREG-A for irregular open sections.";
            }
            ClassSection section = new ClassSection();
            section.setCourseId(courseId);
            section.setTermId(termId);
            section.setSectionCode(sectionCode);
            section.setFacultyId((facultyId == null || facultyId == 0) ? null : facultyId);
            section.setMaxCapacity(maxCapacity);
            section.setSectionStatus("Open");
            classSectionRepository.saveAndFlush(section);
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

        // Store the global term as the 10-digit DB code (used by all SL builders)
        String normalizedGlobalTermCode = targetDbTermCode;
        SystemSetting setting = systemSettingRepository.findById("CURRENT_ACADEMIC_TERM")
            .orElse(new SystemSetting("CURRENT_ACADEMIC_TERM", normalizedGlobalTermCode));
        setting.setSettingValue(normalizedGlobalTermCode);
        systemSettingRepository.saveAndFlush(setting);

        int targetSem = Character.getNumericValue(targetDbTermCode.charAt(0));
        String targetAyStart = targetDbTermCode.substring(2, 6);
        String targetAyEnd   = targetDbTermCode.substring(6, 10);

        List<com.iuims.registrar.core.SysUser> students = sysUserRepository.findByRoleAndIsActiveAndAdmissionStatusIn("Student", true, java.util.Arrays.asList("ENROLLED", "ADMITTED", "ACTIVE"));
        
        for (com.iuims.registrar.core.SysUser s : students) {
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
            
            com.iuims.registrar.core.Student student = studentRepository.findById(studentNumber).orElse(null);
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

        recordTermTransitionAudit(newGlobalTermCode, targetDbTermCode, targetTermId, true, advanced, debtCounter.get(), null);
        return TermTransitionResult.success(advanced, debtCounter.get());
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
            int deactivated = academicTermRepository.deactivateAllExcept(targetTermId);
            int activated = academicTermRepository.activateTerm(targetTermId);
            academicTermRepository.flush();
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
