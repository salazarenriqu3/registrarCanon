package com.iuims.registrar.curriculum;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CurriculumSeederService {

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private CurriculumSeedManifestService manifestService;

    private final Map<String, Integer> courseIdCache = new LinkedHashMap<>();
    private static final Pattern EFFECTIVE_SY = Pattern.compile("(\\d{4})\\s*-\\s*(\\d{4})");

    /** Manifest-driven seed that only publishes into approved program codes. */
    public List<Map<String, Object>> runAutoSeeder() {
        List<Map<String, Object>> report = new ArrayList<>();
        System.out.println("=== CURRICULUM AUTO-SEEDER STARTING ===");
        courseIdCache.clear();
        try {
            List<CurriculumSeedManifestService.CurriculumSeedManifestEntry> manifest = manifestService.loadManifest();
            if (manifest.isEmpty()) {
                System.out.println("No curriculum seed manifest entries found.");
                return report;
            }

            db.execute("SET FOREIGN_KEY_CHECKS = 0");
            db.execute("SET SQL_SAFE_UPDATES = 0");
            seedUniversalGenEds();

            for (CurriculumSeedManifestService.CurriculumSeedManifestEntry entry : manifest) {
                report.add(processManifestEntry(entry));
            }
        } catch (Exception e) {
            System.err.println("Curriculum seeder failed: " + e.getMessage());
        } finally {
            try {
                db.execute("SET FOREIGN_KEY_CHECKS = 1");
            } catch (Exception ignored) {
            }
            try {
                db.execute("SET SQL_SAFE_UPDATES = 1");
            } catch (Exception ignored) {
            }
        }
        System.out.println("=== CURRICULUM AUTO-SEEDER COMPLETE ===");
        return report;
    }

    /** Dry-run preview for a manually selected target program. */
    public Map<String, Object> previewCurriculumFile(MultipartFile file, String schoolName, String programCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", file.getOriginalFilename());
        result.put("schoolName", schoolName);
        result.put("programCode", normalizeProgramCode(programCode));

        List<String> warnings = new ArrayList<>();
        if (programCode == null || programCode.isBlank()) {
            warnings.add("Select the target program before previewing a curriculum upload.");
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream(); XWPFDocument doc = new XWPFDocument(is)) {
            ParsedCurriculum parsed = parseCurriculumDocument(doc, warnings);
            rows.addAll(toPreviewRows(parsed.rows()));
        } catch (Exception e) {
            warnings.add("Parse error: " + e.getMessage());
        }

        result.put("courses", rows);
        result.put("warnings", warnings);
        result.put("courseCount", rows.size());
        return result;
    }

    /** Publishes one uploaded curriculum into an explicitly selected program code. */
    public Map<String, Object> seedUploadedFile(MultipartFile file, String schoolName, String programCode) {
        String rawName = file.getOriginalFilename() != null ? file.getOriginalFilename().replace(".docx", "") : "UNKNOWN";
        String targetProgramCode = normalizeProgramCode(programCode);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", file.getOriginalFilename());
        result.put("programCode", targetProgramCode);

        List<String> warnings = new ArrayList<>();
        int seededCount = 0;

        if (targetProgramCode == null) {
            warnings.add("No target program was selected for this curriculum upload.");
            result.put("seededCount", 0);
            result.put("warnings", warnings);
            result.put("status", "partial");
            return result;
        }

        Integer programId = programId(targetProgramCode);
        if (programId == null) {
            warnings.add("Unknown target program code: " + targetProgramCode);
            result.put("seededCount", 0);
            result.put("warnings", warnings);
            result.put("status", "partial");
            return result;
        }

        if (hasOperationalActiveCurriculum(programId)) {
            warnings.add("Program " + targetProgramCode + " already has an operational active curriculum. Upload publish was blocked to avoid overwriting live structure.");
            result.put("seededCount", 0);
            result.put("warnings", warnings);
            result.put("status", "partial");
            return result;
        }

        try {
            db.execute("SET FOREIGN_KEY_CHECKS = 0");
            db.execute("SET SQL_SAFE_UPDATES = 0");
            seedUniversalGenEds();

            try (InputStream is = file.getInputStream(); XWPFDocument doc = new XWPFDocument(is)) {
                ParsedCurriculum parsed = parseCurriculumDocument(doc, warnings);
                seededCount = publishParsedCurriculum(
                    programId,
                    targetProgramCode,
                    programName(programId, rawName) + " Curriculum",
                    parsed,
                    "Draft",
                    true,
                    warnings);
            }
        } catch (Exception e) {
            warnings.add("Seed error: " + e.getMessage());
        } finally {
            try {
                db.execute("SET FOREIGN_KEY_CHECKS = 1");
            } catch (Exception ignored) {
            }
            try {
                db.execute("SET SQL_SAFE_UPDATES = 1");
            } catch (Exception ignored) {
            }
        }

        result.put("seededCount", seededCount);
        result.put("warnings", warnings);
        result.put("status", warnings.isEmpty() ? "success" : "partial");
        return result;
    }

    public List<Map<String, Object>> listPrograms() {
        return listCurriculumDashboard("active", null);
    }

    public List<Map<String, Object>> listCurriculumDashboard(String view, String programCode) {
        String normalizedView = normalizeDashboardView(view);
        String normalizedProgramCode = normalizeProgramCode(programCode);
        StringBuilder sql = new StringBuilder(
            "SELECT p.program_id, p.program_code, p.program_name, p.school_name, " +
                "ct.curriculum_id, ct.curriculum_name, ct.academic_year, ct.version_number, " +
                "ct.approval_status, COALESCE(ct.is_active, 0) AS is_active, " +
                "COUNT(cc.curriculum_course_id) AS course_count, " +
                "CASE " +
                "WHEN ct.curriculum_id IS NULL THEN 'Missing' " +
                "WHEN COUNT(cc.curriculum_course_id) = 0 AND UPPER(COALESCE(ct.approval_status,'')) = 'PLACEHOLDER' THEN 'Placeholder' " +
                "WHEN COUNT(cc.curriculum_course_id) = 0 THEN 'Draft Shell' " +
                "WHEN COALESCE(ct.is_active, 0) = 1 THEN 'Active' " +
                "WHEN UPPER(COALESCE(ct.approval_status,'')) = 'ARCHIVED' THEN 'Archived' " +
                "ELSE 'Historical' END AS lifecycle_status " +
                "FROM programs p " +
                "LEFT JOIN curriculum_templates ct ON ct.program_id = p.program_id " +
                "LEFT JOIN curriculum_courses cc ON cc.curriculum_id = ct.curriculum_id " +
                "WHERE COALESCE(p.active_status, 1) = 1 ");
        List<Object> args = new ArrayList<>();

        if ("active".equals(normalizedView)) {
            sql.append("AND (ct.curriculum_id IS NULL OR COALESCE(ct.is_active, 0) = 1) ");
        } else if ("draft".equals(normalizedView)) {
            sql.append("AND ct.curriculum_id IS NOT NULL AND UPPER(COALESCE(ct.approval_status,'')) IN ('DRAFT','PLACEHOLDER') ");
        } else if ("history".equals(normalizedView)) {
            sql.append("AND ct.curriculum_id IS NOT NULL AND COALESCE(ct.is_active, 0) = 0 AND UPPER(COALESCE(ct.approval_status,'')) NOT IN ('DRAFT','PLACEHOLDER') ");
        } else {
            sql.append("AND (ct.curriculum_id IS NOT NULL OR 'all' = 'all') ");
        }

        if (normalizedProgramCode != null) {
            sql.append("AND UPPER(p.program_code) = ? ");
            args.add(normalizedProgramCode);
        }

        sql.append(
            "GROUP BY p.program_id, p.program_code, p.program_name, p.school_name, " +
                "ct.curriculum_id, ct.curriculum_name, ct.academic_year, ct.version_number, " +
                "ct.approval_status, ct.is_active " +
            "ORDER BY p.school_name, p.program_name, COALESCE(ct.is_active, 0) DESC, " +
                "ct.version_number DESC, ct.curriculum_id DESC");

        return db.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> listCurriculumCompletionQueue() {
        return db.queryForList(
            "SELECT p.program_id, p.program_code, p.program_name, p.school_name, " +
                "ct.curriculum_id, ct.curriculum_name, ct.academic_year, ct.version_number, " +
                "ct.approval_status, COALESCE(ct.is_active, 0) AS is_active, " +
                "COUNT(cc.curriculum_course_id) AS course_count, " +
                "CASE " +
                "WHEN ct.curriculum_id IS NULL THEN 'Needs placeholder' " +
                "WHEN COUNT(cc.curriculum_course_id) = 0 THEN 'Empty placeholder' " +
                "WHEN UPPER(COALESCE(ct.approval_status,'')) IN ('DRAFT','PLACEHOLDER') THEN 'Draft in progress' " +
                "ELSE 'Needs review' END AS completion_status " +
                "FROM programs p " +
                "LEFT JOIN curriculum_templates ct ON ct.curriculum_id = ( " +
                    "SELECT ct2.curriculum_id FROM curriculum_templates ct2 " +
                    "WHERE ct2.program_id = p.program_id " +
                    "ORDER BY CASE WHEN UPPER(COALESCE(ct2.approval_status,'')) IN ('DRAFT','PLACEHOLDER') THEN 0 ELSE 1 END, " +
                        "COALESCE(ct2.is_active, 0) ASC, ct2.version_number DESC, ct2.curriculum_id DESC LIMIT 1 " +
                ") " +
                "LEFT JOIN curriculum_courses cc ON cc.curriculum_id = ct.curriculum_id " +
                "WHERE COALESCE(p.active_status, 1) = 1 " +
                "AND NOT EXISTS ( " +
                    "SELECT 1 FROM curriculum_templates act " +
                    "JOIN curriculum_courses acc ON acc.curriculum_id = act.curriculum_id " +
                    "WHERE act.program_id = p.program_id AND COALESCE(act.is_active, 0) = 1 " +
                ") " +
                "GROUP BY p.program_id, p.program_code, p.program_name, p.school_name, " +
                    "ct.curriculum_id, ct.curriculum_name, ct.academic_year, ct.version_number, ct.approval_status, ct.is_active " +
                "ORDER BY p.school_name, p.program_code");
    }

    @Transactional
    public Map<String, Object> retireEmptyCurriculumBlockers() {
        List<Map<String, Object>> queue = listCurriculumCompletionQueue();
        List<String> retired = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Map<String, Object> row : queue) {
            String programCode = String.valueOf(row.getOrDefault("program_code", "")).trim().toUpperCase(Locale.ROOT);
            Object programIdObj = row.get("program_id");
            int courseCount = row.get("course_count") instanceof Number ? ((Number) row.get("course_count")).intValue() : 0;
            if (programCode.isBlank() || !(programIdObj instanceof Number)) {
                skipped.add(programCode + " (invalid program row)");
                continue;
            }
            if (courseCount > 0) {
                skipped.add(programCode + " (draft has course rows; review before retiring)");
                continue;
            }
            int activeStudents = countActiveStudentsForProgram(programCode);
            if (activeStudents > 0) {
                skipped.add(programCode + " (" + activeStudents + " canonical active/admitted/enrolled student record(s))");
                continue;
            }
            int changed = db.update(
                "UPDATE programs SET active_status = 0 WHERE program_id = ? AND COALESCE(active_status, 1) = 1",
                ((Number) programIdObj).intValue());
            if (changed > 0) {
                retired.add(programCode);
            } else {
                skipped.add(programCode + " (already inactive)");
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checked", queue.size());
        result.put("retired", retired);
        result.put("skipped", skipped);
        return result;
    }

    public List<Map<String, Object>> listProgramOptions() {
        return db.queryForList(
            "SELECT program_id, program_code, program_name, school_name " +
                "FROM programs WHERE COALESCE(active_status, 1) = 1 ORDER BY school_name, program_name");
    }

    public List<Map<String, Object>> listDepartments() {
        return db.queryForList(
            "SELECT department_id, department_code, department_name " +
                "FROM departments ORDER BY department_name");
    }

    public List<Map<String, Object>> searchCourseCatalog(String query, Integer departmentId) {
        String text = query != null ? query.trim() : "";
        String normalized = text.toUpperCase(Locale.ROOT);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT c.course_id, c.course_code, c.course_title, c.credit_units, " +
                "CASE WHEN COALESCE(c.lec_units, 0) + COALESCE(c.lab_units, 0) = 0 THEN c.credit_units ELSE c.lec_units END AS lec_units, " +
                "COALESCE(c.lab_units, 0) AS lab_units, " +
                "d.department_id, COALESCE(d.department_name, 'Unassigned') AS department_name " +
                "FROM courses c " +
                "LEFT JOIN departments d ON d.department_id = c.department_id " +
                "WHERE COALESCE(c.active_status, 1) = 1 ");

        if (departmentId != null && departmentId > 0) {
            sql.append("AND c.department_id = ? ");
            args.add(departmentId);
        }
        if (!normalized.isBlank()) {
            sql.append("AND (UPPER(c.course_code) LIKE ? OR UPPER(c.course_title) LIKE ?) ");
            args.add("%" + normalized + "%");
            args.add("%" + normalized + "%");
        }

        sql.append(
            "ORDER BY CASE WHEN UPPER(c.course_code) = ? THEN 0 " +
                "WHEN UPPER(c.course_code) LIKE ? THEN 1 ELSE 2 END, " +
                "d.department_name, c.course_code LIMIT 40");
        args.add(normalized);
        args.add(normalized + "%");
        return db.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> listCurriculumCourses(int curriculumId) {
        return db.queryForList(
            "SELECT cc.curriculum_course_id, cc.year_level, cc.semester_number, c.course_code, c.course_title, c.credit_units, " +
                "CASE WHEN COALESCE(c.lec_units, 0) + COALESCE(c.lab_units, 0) = 0 THEN c.credit_units ELSE c.lec_units END AS lec_units, " +
                "COALESCE(c.lab_units, 0) AS lab_units, " +
                "GROUP_CONCAT(pc.course_code ORDER BY pc.course_code SEPARATOR ', ') AS prerequisites " +
                "FROM curriculum_courses cc " +
                "JOIN courses c ON c.course_id = cc.course_id " +
                "LEFT JOIN course_prerequisites cp ON cp.course_id = c.course_id " +
                "LEFT JOIN courses pc ON pc.course_id = cp.prerequisite_course_id " +
                "WHERE cc.curriculum_id = ? " +
                "GROUP BY cc.curriculum_course_id, cc.year_level, cc.semester_number, " +
                "c.course_code, c.course_title, c.credit_units, c.lec_units, c.lab_units " +
                "ORDER BY cc.year_level, cc.semester_number, c.course_code",
            curriculumId);
    }

    public Map<String, Object> getCurriculumSummary(int curriculumId) {
        try {
            return db.queryForMap(
                "SELECT ct.curriculum_id, ct.curriculum_name, ct.academic_year, ct.version_number, " +
                    "ct.approval_status, COALESCE(ct.is_active, 0) AS is_active, " +
                    "p.program_id, p.program_code, p.program_name, p.school_name, " +
                    "COUNT(cc.curriculum_course_id) AS course_count " +
                    "FROM curriculum_templates ct " +
                    "JOIN programs p ON p.program_id = ct.program_id " +
                    "LEFT JOIN curriculum_courses cc ON cc.curriculum_id = ct.curriculum_id " +
                    "WHERE ct.curriculum_id = ? " +
                    "GROUP BY ct.curriculum_id, ct.curriculum_name, ct.academic_year, ct.version_number, " +
                    "ct.approval_status, ct.is_active, p.program_id, p.program_code, p.program_name, p.school_name",
                curriculumId);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public String exportCurriculumCsv(int curriculumId) {
        Map<String, Object> summary = getCurriculumSummary(curriculumId);
        if (summary.isEmpty()) {
            return "PROGRAM_CODE,CURRICULUM_NAME,ACADEMIC_YEAR,VERSION,STATUS,YEAR_LEVEL,SEMESTER,COURSE_CODE,COURSE_TITLE,UNITS,PREREQUISITES\n";
        }
        StringBuilder csv = new StringBuilder();
        csv.append("PROGRAM_CODE,CURRICULUM_NAME,ACADEMIC_YEAR,VERSION,STATUS,YEAR_LEVEL,SEMESTER,COURSE_CODE,COURSE_TITLE,UNITS,PREREQUISITES\n");
        for (Map<String, Object> course : listCurriculumCourses(curriculumId)) {
            csv.append(csv(summary.get("program_code"))).append(',')
                .append(csv(summary.get("curriculum_name"))).append(',')
                .append(csv(summary.get("academic_year"))).append(',')
                .append(csv(summary.get("version_number"))).append(',')
                .append(csv(summary.get("approval_status"))).append(',')
                .append(csv(course.get("year_level"))).append(',')
                .append(csv(course.get("semester_number"))).append(',')
                .append(csv(course.get("course_code"))).append(',')
                .append(csv(course.get("course_title"))).append(',')
                .append(csv(course.get("credit_units"))).append(',')
                .append(csv(course.get("prerequisites"))).append('\n');
        }
        return csv.toString();
    }

    @Transactional
    public Integer cloneCurriculumToDraft(int sourceCurriculumId, String academicYear, String curriculumName) {
        Map<String, Object> source = getCurriculumSummary(sourceCurriculumId);
        if (source.isEmpty()) {
            throw new IllegalArgumentException("Source curriculum was not found.");
        }
        int programId = ((Number) source.get("program_id")).intValue();
        String targetAcademicYear = academicYear != null && !academicYear.isBlank()
            ? academicYear.trim()
            : String.valueOf(source.get("academic_year"));
        String targetName = curriculumName != null && !curriculumName.isBlank()
            ? curriculumName.trim()
            : source.get("program_code") + " Draft Curriculum " + targetAcademicYear;
        int nextVersion = nextVersionNumber(programId);
        db.update(
            "INSERT INTO curriculum_templates (program_id, curriculum_name, academic_year, version_number, approval_status, is_active) " +
                "VALUES (?, ?, ?, ?, 'Draft', 0)",
            programId, targetName, targetAcademicYear, nextVersion);
        Integer newCurriculumId = latestCurriculumId(programId);
        if (newCurriculumId == null) {
            throw new IllegalStateException("Unable to create draft curriculum.");
        }
        db.update(
            "INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number, is_required) " +
                "SELECT ?, course_id, year_level, semester_number, is_required FROM curriculum_courses WHERE curriculum_id = ?",
            newCurriculumId, sourceCurriculumId);
        return newCurriculumId;
    }

    @Transactional
    public Map<String, Object> createProgramPlaceholder(String programCode, String academicYear) {
        String normalizedProgramCode = normalizeProgramCode(programCode);
        if (normalizedProgramCode == null) {
            throw new IllegalArgumentException("Select a program before creating a placeholder.");
        }
        Integer programId = programId(normalizedProgramCode);
        if (programId == null) {
            throw new IllegalArgumentException("Unknown program code: " + normalizedProgramCode);
        }
        String targetAcademicYear = academicYear != null && !academicYear.isBlank()
            ? academicYear.trim()
            : resolveAcademicYearFromDocOrSettings(null);
        Integer existing = findReusableActiveDraftTemplate(programId);
        if (existing != null) {
            return getCurriculumSummary(existing);
        }
        boolean activateShell = !hasAnyActiveCurriculum(programId);
        db.update(
            "INSERT INTO curriculum_templates (program_id, curriculum_name, academic_year, version_number, approval_status, is_active) " +
                "VALUES (?, ?, ?, ?, 'Placeholder', ?)",
            programId,
            normalizedProgramCode + " Placeholder Curriculum",
            targetAcademicYear,
            nextVersionNumber(programId),
            activateShell ? 1 : 0);
        Integer curriculumId = latestCurriculumId(programId);
        return curriculumId != null ? getCurriculumSummary(curriculumId) : Map.of();
    }

    @Transactional
    public Map<String, Object> deleteDraftCurriculum(int curriculumId) {
        Map<String, Object> summary = getCurriculumSummary(curriculumId);
        if (summary.isEmpty()) {
            throw new IllegalArgumentException("Curriculum was not found.");
        }
        String status = String.valueOf(summary.getOrDefault("approval_status", "")).toUpperCase(Locale.ROOT);
        int isActive = summary.get("is_active") instanceof Number ? ((Number) summary.get("is_active")).intValue() : 0;
        int courseCount = summary.get("course_count") instanceof Number ? ((Number) summary.get("course_count")).intValue() : 0;
        boolean draftLike = status.equals("DRAFT") || status.equals("PLACEHOLDER");
        boolean emptyActivePlaceholder = isActive == 1 && courseCount == 0 && status.equals("PLACEHOLDER");
        if (!draftLike || (isActive == 1 && !emptyActivePlaceholder)) {
            throw new IllegalStateException("Only inactive drafts or empty placeholders can be deleted.");
        }
        db.update("DELETE FROM curriculum_courses WHERE curriculum_id = ?", curriculumId);
        db.update("DELETE FROM curriculum_templates WHERE curriculum_id = ?", curriculumId);
        return summary;
    }

    public boolean isEditableDraft(int curriculumId) {
        return canEditCurriculum(getCurriculumSummary(curriculumId));
    }

    @Transactional
    public void addManualCourse(int curriculumId,
                                String courseCode,
                                String courseTitle,
                                Integer lectureUnits,
                                Integer laboratoryUnits,
                                Integer yearLevel,
                                Integer semesterNumber,
                                String prerequisites) {
        Map<String, Object> summary = getCurriculumSummary(curriculumId);
        requireEditableDraft(summary);
        String programCode = String.valueOf(summary.get("program_code"));
        int safeLectureUnits = lectureUnits != null ? lectureUnits : 3;
        int safeLaboratoryUnits = laboratoryUnits != null ? laboratoryUnits : 0;
        if (safeLectureUnits < 0 || safeLaboratoryUnits < 0) {
            throw new IllegalArgumentException("Lecture and laboratory units cannot be negative.");
        }
        int safeUnits = safeLectureUnits + safeLaboratoryUnits;
        if (safeUnits <= 0 || safeUnits > 12) {
            throw new IllegalArgumentException("Total credit units must be between 1 and 12.");
        }
        int safeYear = yearLevel != null && yearLevel > 0 ? yearLevel : 1;
        int safeSem = semesterNumber != null && semesterNumber > 0 ? semesterNumber : 1;
        if (courseCode == null || courseCode.isBlank()) {
            throw new IllegalArgumentException("Course code is required.");
        }
        if (courseTitle == null || courseTitle.isBlank()) {
            throw new IllegalArgumentException("Course title is required.");
        }
        String normalizedCourseCode = courseCode.trim().toUpperCase(Locale.ROOT);
        requireCourseNotInCurriculum(curriculumId, normalizedCourseCode);
        if (findCourseIdByCode(normalizedCourseCode) != null) {
            throw new IllegalStateException("This course already exists in the catalog. Select it from the picker to attach it without changing catalog details.");
        }
        deactivateEditablePlaceholder(summary);
        saveCourseAndMapping(programCode, normalizedCourseCode, courseTitle.trim(), safeUnits, safeLectureUnits, safeLaboratoryUnits,
            prerequisites, curriculumId, safeYear, safeSem);
    }

    @Transactional
    public void addExistingCourse(int curriculumId,
                                  int courseId,
                                  Integer yearLevel,
                                  Integer semesterNumber) {
        Map<String, Object> summary = getCurriculumSummary(curriculumId);
        requireEditableDraft(summary);
        int safeYear = yearLevel != null && yearLevel > 0 ? yearLevel : 1;
        int safeSem = semesterNumber != null && semesterNumber > 0 ? semesterNumber : 1;
        Map<String, Object> course = getActiveCourse(courseId);
        if (course.isEmpty()) {
            throw new IllegalArgumentException("Selected course was not found in the active catalog.");
        }
        requireCourseNotInCurriculum(curriculumId, String.valueOf(course.get("course_code")));
        deactivateEditablePlaceholder(summary);
        db.update(
            "INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number, is_required) " +
                "VALUES (?, ?, ?, ?, 1)",
            curriculumId, courseId, safeYear, safeSem);
    }

    @Transactional
    public void updateManualCoursePlacement(int curriculumId,
                                            int curriculumCourseId,
                                            Integer yearLevel,
                                            Integer semesterNumber) {
        requireEditableDraft(getCurriculumSummary(curriculumId));
        int safeYear = yearLevel != null && yearLevel > 0 ? yearLevel : 1;
        int safeSem = semesterNumber != null && semesterNumber > 0 ? semesterNumber : 1;
        int changed = db.update(
            "UPDATE curriculum_courses SET year_level = ?, semester_number = ? " +
                "WHERE curriculum_course_id = ? AND curriculum_id = ?",
            safeYear, safeSem, curriculumCourseId, curriculumId);
        if (changed == 0) {
            throw new IllegalArgumentException("Curriculum row was not found.");
        }
    }

    @Transactional
    public void removeManualCourse(int curriculumId, int curriculumCourseId) {
        requireEditableDraft(getCurriculumSummary(curriculumId));
        int changed = db.update(
            "DELETE FROM curriculum_courses WHERE curriculum_course_id = ? AND curriculum_id = ?",
            curriculumCourseId, curriculumId);
        if (changed == 0) {
            throw new IllegalArgumentException("Curriculum row was not found.");
        }
    }

    @Transactional
    public void finalizeDraftCurriculum(int curriculumId) {
        Map<String, Object> summary = getCurriculumSummary(curriculumId);
        if (summary.isEmpty()) {
            throw new IllegalArgumentException("Curriculum was not found.");
        }
        int courseCount = summary.get("course_count") instanceof Number ? ((Number) summary.get("course_count")).intValue() : 0;
        if (courseCount <= 0) {
            throw new IllegalStateException("Add at least one course before finalizing this curriculum.");
        }
        String status = String.valueOf(summary.getOrDefault("approval_status", "")).toUpperCase(Locale.ROOT);
        if (!status.equals("DRAFT") && !status.equals("PLACEHOLDER")) {
            throw new IllegalStateException("Only draft or placeholder curricula can be finalized.");
        }
        int programId = ((Number) summary.get("program_id")).intValue();
        archiveOtherActiveCurricula(programId, curriculumId);
        db.update("UPDATE curriculum_templates SET approval_status = 'Approved', is_active = 1 WHERE curriculum_id = ?", curriculumId);
    }

    @Transactional
    public Map<String, Object> enforceSingleActiveCurriculumPerProgram() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> duplicates = db.queryForList(
            "SELECT p.program_id, p.program_code, COUNT(*) AS active_count " +
                "FROM programs p JOIN curriculum_templates ct ON ct.program_id = p.program_id " +
                "WHERE COALESCE(ct.is_active, 0) = 1 " +
                "GROUP BY p.program_id, p.program_code HAVING COUNT(*) > 1 " +
                "ORDER BY p.program_code");

        List<String> normalized = new ArrayList<>();
        int archivedCount = 0;
        for (Map<String, Object> row : duplicates) {
            int programId = ((Number) row.get("program_id")).intValue();
            String programCode = String.valueOf(row.get("program_code"));
            Integer keepCurriculumId = selectCurrentCurriculumId(programId);
            if (keepCurriculumId == null) {
                continue;
            }
            int archived = archiveOtherActiveCurricula(programId, keepCurriculumId);
            approveCurrentCurriculum(keepCurriculumId);
            archivedCount += archived;
            normalized.add(programCode + " kept #" + keepCurriculumId + ", archived " + archived);
        }

        result.put("duplicatePrograms", duplicates.size());
        result.put("archivedCurricula", archivedCount);
        result.put("normalized", normalized);
        return result;
    }

    public List<Map<String, Object>> reseedAll() {
        courseIdCache.clear();
        return runAutoSeeder();
    }

    /** Creates active placeholder templates only for programs explicitly blocked in the manifest. */
    public Map<String, Object> repairReadinessCurricula() {
        Map<String, Object> result = new LinkedHashMap<>();
        int placeholdersCreated = 0;
        int blockedPrograms = 0;

        List<CurriculumSeedManifestService.CurriculumSeedManifestEntry> manifest = manifestService.loadManifest();
        for (CurriculumSeedManifestService.CurriculumSeedManifestEntry entry : manifest) {
            if (entry.seedMode() != CurriculumSeedManifestService.SeedMode.BLOCKED) {
                continue;
            }
            blockedPrograms++;
            if (ensureActiveCurriculumPlaceholder(entry.programCode(), entry.notes())) {
                placeholdersCreated++;
            }
        }

        result.put("manifestProgramsChecked", manifest.size());
        result.put("blockedPrograms", blockedPrograms);
        result.put("placeholdersCreated", placeholdersCreated);
        return result;
    }

    private Map<String, Object> processManifestEntry(CurriculumSeedManifestService.CurriculumSeedManifestEntry entry) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("programCode", entry.programCode());
        result.put("seedMode", entry.seedMode().name());
        result.put("sourceFile", entry.sourceFile());
        result.put("notes", entry.notes());

        List<String> warnings = new ArrayList<>();
        int seededCount = 0;

        Integer programId = programId(entry.programCode());
        if (programId == null) {
            warnings.add("Program code does not exist in programs: " + entry.programCode());
            result.put("seededCount", 0);
            result.put("warnings", warnings);
            result.put("status", "partial");
            return result;
        }

        if (entry.seedMode() == CurriculumSeedManifestService.SeedMode.BLOCKED) {
            boolean created = ensureActiveCurriculumPlaceholder(entry.programCode(), entry.notes());
            result.put("seededCount", 0);
            result.put("warnings", warnings);
            result.put("status", created ? "placeholder" : "blocked");
            return result;
        }

        if (hasOperationalActiveCurriculum(programId)) {
            warnings.add("Operational active curriculum already exists; manifest seed skipped.");
            result.put("seededCount", 0);
            result.put("warnings", warnings);
            result.put("status", "skipped");
            return result;
        }

        Resource source = resolveSourceResource(entry.sourceFile());
        if (source == null || !source.exists()) {
            warnings.add("Manifest source file not found: " + entry.sourceFile());
            result.put("seededCount", 0);
            result.put("warnings", warnings);
            result.put("status", "partial");
            return result;
        }

        try (InputStream is = source.getInputStream(); XWPFDocument doc = new XWPFDocument(is)) {
            ParsedCurriculum parsed = parseCurriculumDocument(doc, warnings);
            seededCount = publishParsedCurriculum(
                programId,
                entry.programCode(),
                programName(programId, entry.programCode()) + " Curriculum",
                parsed,
                "Draft",
                true,
                warnings);
        } catch (Exception e) {
            warnings.add("Seed failed: " + e.getMessage());
        }

        result.put("seededCount", seededCount);
        result.put("warnings", warnings);
        result.put("status", warnings.isEmpty() ? "success" : "partial");
        return result;
    }

    private Resource resolveSourceResource(String sourceFile) {
        if (sourceFile == null || sourceFile.isBlank()) {
            return null;
        }
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            return resolver.getResource("classpath:" + sourceFile.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private ParsedCurriculum parseCurriculumDocument(XWPFDocument doc, List<String> warnings) {
        String academicYear = resolveAcademicYearFromDocOrSettings(doc);
        List<ParsedCurriculumRow> rows = new ArrayList<>();

        int year = 1;
        int sem = 1;
        for (IBodyElement element : doc.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph) {
                String text = paragraph.getText() != null ? paragraph.getText().toUpperCase(Locale.ROOT).trim() : "";
                year = detectYear(text, year);
                sem = detectSem(text, sem);
            } else if (element instanceof XWPFTable table) {
                collectParsedRows(table, year, sem, rows);
            }
        }

        if (rows.isEmpty()) {
            warnings.add("No curriculum course rows were detected in the document.");
        }

        return new ParsedCurriculum(academicYear, rows);
    }

    private void collectParsedRows(XWPFTable table, int year, int sem, List<ParsedCurriculumRow> rows) {
        boolean headerFound = false;
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            if (cells.size() < 3) {
                continue;
            }

            String col0 = cells.get(0).getText().trim();
            if (col0.equalsIgnoreCase("Course Code") || col0.equalsIgnoreCase("Subject Code")) {
                headerFound = true;
                continue;
            }
            if (!headerFound || col0.isEmpty() || col0.equalsIgnoreCase("TOTAL")) {
                continue;
            }

            String courseCode = col0;
            String courseTitle = cells.get(1).getText().trim();
            int units = parseUnits(cells);
            String prereqRaw = cells.get(cells.size() - 1).getText().trim();
            rows.add(new ParsedCurriculumRow(courseCode, courseTitle, units, prereqRaw, year, sem));
        }
    }

    private List<Map<String, Object>> toPreviewRows(List<ParsedCurriculumRow> rows) {
        List<Map<String, Object>> preview = new ArrayList<>();
        for (ParsedCurriculumRow row : rows) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("year_level", row.yearLevel());
            view.put("semester", row.semester());
            view.put("course_code", row.courseCode());
            view.put("course_title", row.courseTitle());
            view.put("units", row.units());
            preview.add(view);
        }
        return preview;
    }

    private int publishParsedCurriculum(int programId,
                                        String programCode,
                                        String curriculumName,
                                        ParsedCurriculum parsed,
                                        String approvalStatus,
                                        boolean activate,
                                        List<String> warnings) {
        if (parsed.rows().isEmpty()) {
            return 0;
        }

        Integer curriculumId = findReusableActiveDraftTemplate(programId);
        if (curriculumId == null) {
            int nextVersion = nextVersionNumber(programId);
            db.update(
                "INSERT INTO curriculum_templates (program_id, curriculum_name, academic_year, version_number, approval_status, is_active) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                programId, curriculumName, parsed.academicYear(), nextVersion, approvalStatus, activate ? 1 : 0);
            curriculumId = latestCurriculumId(programId);
        } else {
            db.update(
                "UPDATE curriculum_templates SET curriculum_name = ?, academic_year = ?, approval_status = ?, is_active = ? WHERE curriculum_id = ?",
                curriculumName, parsed.academicYear(), approvalStatus, activate ? 1 : 0, curriculumId);
        }

        if (curriculumId == null) {
            warnings.add("Unable to create or locate the curriculum template row.");
            return 0;
        }

        if (activate) {
            archiveOtherActiveCurricula(programId, curriculumId);
            db.update("UPDATE curriculum_templates SET is_active = 1 WHERE curriculum_id = ?", curriculumId);
        }

        int seededCount = 0;
        for (ParsedCurriculumRow row : parsed.rows()) {
            try {
                saveCourseAndMapping(
                    programCode,
                    row.courseCode(),
                    row.courseTitle(),
                    row.units(),
                    row.prereqRaw(),
                    curriculumId,
                    row.yearLevel(),
                    row.semester());
                seededCount++;
            } catch (Exception e) {
                warnings.add("Row skipped [" + row.courseCode() + "]: " + e.getMessage());
            }
        }
        return seededCount;
    }

    private int parseUnits(List<XWPFTableCell> cells) {
        for (int i = 2; i < Math.min(5, cells.size()); i++) {
            try {
                return Integer.parseInt(cells.get(i).getText().trim().split(" ")[0]);
            } catch (Exception ignored) {
            }
        }
        return 3;
    }

    private int detectYear(String text, int current) {
        if (text.contains("FIRST YEAR")) return 1;
        if (text.contains("SECOND YEAR")) return 2;
        if (text.contains("THIRD YEAR")) return 3;
        if (text.contains("FOURTH YEAR")) return 4;
        if (text.contains("FIFTH YEAR")) return 5;
        return current;
    }

    private int detectSem(String text, int current) {
        if (text.contains("FIRST SEMESTER")) return 1;
        if (text.contains("SECOND SEMESTER")) return 2;
        if (text.contains("SUMMER") || text.contains("MIDYEAR")) return 3;
        return current;
    }

    private String resolveAcademicYearFromDocOrSettings(XWPFDocument doc) {
        String fromDoc = extractEffectiveAcademicYear(doc);
        if (fromDoc != null) {
            return fromDoc;
        }

        try {
            String cur = db.queryForObject(
                "SELECT setting_value FROM system_settings WHERE setting_key = 'CURRENT_ACADEMIC_TERM' LIMIT 1",
                String.class);
            String fromSetting = toAcademicYearFromTermCode(cur);
            if (fromSetting != null) {
                return fromSetting;
            }
        } catch (Exception ignored) {
        }

        return "2024-2025";
    }

    private String extractEffectiveAcademicYear(XWPFDocument doc) {
        if (doc == null) {
            return null;
        }
        for (IBodyElement element : doc.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph) {
                String text = paragraph.getText();
                if (text == null) {
                    continue;
                }
                String upper = text.toUpperCase(Locale.ROOT);
                if (!upper.contains("EFFECTIVE")) {
                    continue;
                }
                Matcher matcher = EFFECTIVE_SY.matcher(upper);
                if (matcher.find()) {
                    return matcher.group(1) + "-" + matcher.group(2);
                }
            }
        }
        return null;
    }

    private String toAcademicYearFromTermCode(String termCodeOrSl) {
        if (termCodeOrSl == null) {
            return null;
        }
        String text = termCodeOrSl.trim();
        try {
            if (text.startsWith("SL") && !text.startsWith("SL_") && text.length() >= 12) {
                int y1 = Integer.parseInt(text.substring(2, 6));
                int y2 = Integer.parseInt(text.substring(6, 10));
                return y1 + "-" + y2;
            }
            if (text.startsWith("SL_") && text.length() >= 13) {
                int y1 = Integer.parseInt(text.substring(5, 9));
                int y2 = Integer.parseInt(text.substring(9, 13));
                return y1 + "-" + y2;
            }
            if (!text.startsWith("SL_") && text.length() >= 10 && Character.isDigit(text.charAt(0))) {
                int y1 = Integer.parseInt(text.substring(2, 6));
                int y2 = Integer.parseInt(text.substring(6, 10));
                return y1 + "-" + y2;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void saveCourseAndMapping(String programCode,
                                      String courseCode,
                                      String courseTitle,
                                      int units,
                                      String preReqRaw,
                                      int curriculumId,
                                      int yearLevel,
                                      int semester) {
        saveCourseAndMapping(programCode, courseCode, courseTitle, units, units, 0,
            preReqRaw, curriculumId, yearLevel, semester);
    }

    private void saveCourseAndMapping(String programCode,
                                      String courseCode,
                                      String courseTitle,
                                      int units,
                                      int lectureUnits,
                                      int laboratoryUnits,
                                      String preReqRaw,
                                      int curriculumId,
                                      int yearLevel,
                                      int semester) {
        db.update(
            "INSERT INTO courses (course_code, course_title, credit_units, lec_units, lab_units, department_id, active_status) " +
                "VALUES (?, ?, ?, ?, ?, 1, 1) " +
                "ON DUPLICATE KEY UPDATE course_title = VALUES(course_title), credit_units = VALUES(credit_units), " +
                "lec_units = VALUES(lec_units), lab_units = VALUES(lab_units), active_status = 1",
            courseCode, courseTitle, units, lectureUnits, laboratoryUnits);

        Integer courseId = null;
        try {
            courseId = db.queryForObject(
                "SELECT course_id FROM courses WHERE course_code = ? LIMIT 1",
                Integer.class,
                courseCode);
        } catch (Exception ignored) {
        }
        if (courseId == null) {
            return;
        }
        courseIdCache.put(courseCode, courseId);

        db.update(
            "INSERT IGNORE INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number, is_required) " +
                "VALUES (?, ?, ?, ?, 1)",
            curriculumId, courseId, yearLevel, semester);

        if (preReqRaw == null || preReqRaw.isEmpty()
            || preReqRaw.equalsIgnoreCase("NONE")
            || preReqRaw.equalsIgnoreCase("N/A")) {
            return;
        }

        String[] prereqCodes = preReqRaw.split("[,;/]+");
        for (String rawCode : prereqCodes) {
            String prereqCode = rawCode.trim();
            if (prereqCode.isEmpty() || prereqCode.equalsIgnoreCase("NONE")) {
                continue;
            }

            Integer prereqId = courseIdCache.get(prereqCode);
            if (prereqId == null) {
                try {
                    prereqId = db.queryForObject(
                        "SELECT course_id FROM courses WHERE course_code = ? LIMIT 1",
                        Integer.class,
                        prereqCode);
                } catch (Exception ignored) {
                }
            }

            if (prereqId == null) {
                db.update(
                    "INSERT IGNORE INTO courses (course_code, course_title, credit_units, lec_units, lab_units, department_id, description, active_status) " +
                        "VALUES (?, ?, 3, 3, 0, 1, 'Prerequisite placeholder - update when course is seeded', 1)",
                    prereqCode, prereqCode);
                try {
                    prereqId = db.queryForObject(
                        "SELECT course_id FROM courses WHERE course_code = ? LIMIT 1",
                        Integer.class,
                        prereqCode);
                } catch (Exception ignored) {
                }
            }

            if (prereqId != null) {
                courseIdCache.put(prereqCode, prereqId);
                db.update(
                    "INSERT IGNORE INTO course_prerequisites (course_id, prerequisite_course_id) VALUES (?, ?)",
                    courseId, prereqId);
            }
        }
    }

    private void seedUniversalGenEds() {
        String[][] genEds = {
            {"AUS0 11", "Understanding the Self", "3"},
            {"ARPH 11", "Readings in Philippine History", "3"},
            {"ACW0 12", "The Contemporary World", "3"},
            {"SMMW 11", "Mathematics in the Modern World", "3"},
            {"APC0 12", "Purposive Communication", "3"},
            {"ANS1 11", "NSTP 1", "3"},
            {"ANS2 12", "NSTP 2", "3"},
            {"AET0 12", "Ethics", "3"},
            {"AHU1 11", "Arts Appreciation", "3"},
            {"ASS6 21", "The Life & Works of Rizal", "3"},
            {"AECO 11", "Emilian Culture", "1"}
        };
        for (String[] ge : genEds) {
            db.update(
                "INSERT INTO courses (course_code, course_title, credit_units, lec_units, lab_units, department_id, active_status) " +
                    "VALUES (?, ?, ?, ?, 0, 1, 1) ON DUPLICATE KEY UPDATE active_status = 1",
                ge[0], ge[1], Integer.parseInt(ge[2]), Integer.parseInt(ge[2]));
            try {
                Integer id = db.queryForObject(
                    "SELECT course_id FROM courses WHERE course_code = ? LIMIT 1",
                    Integer.class,
                    ge[0]);
                if (id != null) {
                    courseIdCache.put(ge[0], id);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private String normalizeProgramCode(String programCode) {
        if (programCode == null || programCode.isBlank()) {
            return null;
        }
        return programCode.trim().toUpperCase(Locale.ROOT);
    }

    private void requireEditableDraft(Map<String, Object> summary) {
        if (!canEditCurriculum(summary)) {
            throw new IllegalStateException("Only inactive drafts or empty placeholders can be edited.");
        }
    }

    private boolean canEditCurriculum(Map<String, Object> summary) {
        if (summary == null || summary.isEmpty()) {
            return false;
        }
        String status = String.valueOf(summary.getOrDefault("approval_status", "")).toUpperCase(Locale.ROOT);
        int isActive = summary.get("is_active") instanceof Number ? ((Number) summary.get("is_active")).intValue() : 0;
        int courseCount = summary.get("course_count") instanceof Number ? ((Number) summary.get("course_count")).intValue() : 0;
        boolean draftLike = status.equals("DRAFT") || status.equals("PLACEHOLDER");
        return draftLike && (isActive == 0 || courseCount == 0);
    }

    private void deactivateEditablePlaceholder(Map<String, Object> summary) {
        int isActive = summary.get("is_active") instanceof Number ? ((Number) summary.get("is_active")).intValue() : 0;
        int courseCount = summary.get("course_count") instanceof Number ? ((Number) summary.get("course_count")).intValue() : 0;
        if (isActive == 1 && courseCount == 0) {
            db.update("UPDATE curriculum_templates SET is_active = 0 WHERE curriculum_id = ?", summary.get("curriculum_id"));
        }
    }

    private void requireCourseNotInCurriculum(int curriculumId, String courseCode) {
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM curriculum_courses cc " +
                "JOIN courses c ON c.course_id = cc.course_id " +
                "WHERE cc.curriculum_id = ? AND UPPER(c.course_code) = UPPER(?)",
            Integer.class,
            curriculumId,
            courseCode);
        if (count != null && count > 0) {
            throw new IllegalStateException("This course is already in this curriculum.");
        }
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

    private Map<String, Object> getActiveCourse(int courseId) {
        try {
            return db.queryForMap(
                "SELECT course_id, course_code, course_title, credit_units, " +
                    "CASE WHEN COALESCE(lec_units, 0) + COALESCE(lab_units, 0) = 0 THEN credit_units ELSE lec_units END AS lec_units, " +
                    "COALESCE(lab_units, 0) AS lab_units " +
                    "FROM courses WHERE course_id = ? AND COALESCE(active_status, 1) = 1 LIMIT 1",
                courseId);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private int countActiveStudentsForProgram(String programCode) {
        if (!tableExists("students")) {
            return 0;
        }
        return countRows(
            "SELECT COUNT(*) FROM students WHERE UPPER(COALESCE(program_code,'')) = UPPER(?) " +
                "AND UPPER(COALESCE(admission_status,'')) IN ('ADMITTED','ENROLLED','ACTIVE')",
            programCode);
    }

    private int countRows(String sql, Object... args) {
        try {
            Integer count = db.queryForObject(sql, Integer.class, args);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
            Integer.class,
            tableName);
        return count != null && count > 0;
    }

    private String normalizeDashboardView(String view) {
        if (view == null || view.isBlank()) {
            return "active";
        }
        return switch (view.trim().toLowerCase(Locale.ROOT)) {
            case "draft", "history", "all" -> view.trim().toLowerCase(Locale.ROOT);
            default -> "active";
        };
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String programName(int programId, String fallback) {
        try {
            String name = db.queryForObject(
                "SELECT program_name FROM programs WHERE program_id = ? LIMIT 1",
                String.class,
                programId);
            return (name == null || name.isBlank()) ? fallback : name.trim();
        } catch (Exception e) {
            return fallback;
        }
    }

    private Integer programId(String programCode) {
        if (programCode == null || programCode.isBlank()) {
            return null;
        }
        try {
            return db.queryForObject(
                "SELECT program_id FROM programs WHERE UPPER(program_code) = UPPER(?) ORDER BY program_id ASC LIMIT 1",
                Integer.class,
                programCode);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasAnyActiveCurriculum(int programId) {
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM curriculum_templates WHERE program_id = ? AND is_active = 1",
                Integer.class,
                programId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Integer selectCurrentCurriculumId(int programId) {
        try {
            return db.queryForObject(
                "SELECT ct.curriculum_id FROM curriculum_templates ct " +
                    "LEFT JOIN curriculum_courses cc ON cc.curriculum_id = ct.curriculum_id " +
                    "WHERE ct.program_id = ? AND COALESCE(ct.is_active, 0) = 1 " +
                    "GROUP BY ct.curriculum_id, ct.version_number " +
                    "ORDER BY CASE WHEN COUNT(cc.curriculum_course_id) > 0 THEN 0 ELSE 1 END, " +
                    "ct.version_number DESC, ct.curriculum_id DESC LIMIT 1",
                Integer.class,
                programId);
        } catch (Exception e) {
            return null;
        }
    }

    private int archiveOtherActiveCurricula(int programId, int keepCurriculumId) {
        return db.update(
            "UPDATE curriculum_templates SET is_active = 0, " +
                "approval_status = CASE " +
                    "WHEN UPPER(COALESCE(approval_status,'')) IN ('DRAFT','PLACEHOLDER') " +
                        "AND NOT EXISTS (SELECT 1 FROM curriculum_courses cc WHERE cc.curriculum_id = curriculum_templates.curriculum_id) " +
                        "THEN approval_status " +
                    "ELSE 'Archived' END " +
                "WHERE program_id = ? AND curriculum_id <> ? AND COALESCE(is_active, 0) = 1",
            programId,
            keepCurriculumId);
    }

    private void approveCurrentCurriculum(int curriculumId) {
        db.update(
            "UPDATE curriculum_templates SET approval_status = 'Approved', is_active = 1 WHERE curriculum_id = ?",
            curriculumId);
    }

    private boolean hasOperationalActiveCurriculum(int programId) {
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM curriculum_templates ct " +
                    "JOIN curriculum_courses cc ON cc.curriculum_id = ct.curriculum_id " +
                    "WHERE ct.program_id = ? AND ct.is_active = 1",
                Integer.class,
                programId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Integer findReusableActiveDraftTemplate(int programId) {
        try {
            return db.queryForObject(
                "SELECT ct.curriculum_id FROM curriculum_templates ct " +
                    "LEFT JOIN curriculum_courses cc ON cc.curriculum_id = ct.curriculum_id " +
                    "WHERE ct.program_id = ? AND ct.is_active = 1 " +
                    "GROUP BY ct.curriculum_id " +
                    "HAVING COUNT(cc.curriculum_course_id) = 0 " +
                    "ORDER BY ct.curriculum_id DESC LIMIT 1",
                Integer.class,
                programId);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer latestCurriculumId(int programId) {
        try {
            return db.queryForObject(
                "SELECT curriculum_id FROM curriculum_templates WHERE program_id = ? ORDER BY curriculum_id DESC LIMIT 1",
                Integer.class,
                programId);
        } catch (Exception e) {
            return null;
        }
    }

    private int nextVersionNumber(int programId) {
        try {
            Integer next = db.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) + 1 FROM curriculum_templates WHERE program_id = ?",
                Integer.class,
                programId);
            return next != null ? next : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    private boolean ensureActiveCurriculumPlaceholder(String programCode, String reason) {
        Integer programId = programId(programCode);
        if (programId == null || hasAnyActiveCurriculum(programId)) {
            return false;
        }
        db.update(
            "INSERT INTO curriculum_templates (program_id, curriculum_name, academic_year, version_number, approval_status, is_active) " +
                "VALUES (?, ?, ?, ?, 'Placeholder', 1)",
            programId,
            programCode + " Placeholder Curriculum",
            resolveAcademicYearFromDocOrSettings(null),
            nextVersionNumber(programId));
        if (reason != null && !reason.isBlank()) {
            System.out.println("Created curriculum placeholder for " + programCode + ": " + reason);
        }
        return true;
    }

    private record ParsedCurriculum(String academicYear, List<ParsedCurriculumRow> rows) {
    }

    private record ParsedCurriculumRow(
        String courseCode,
        String courseTitle,
        int units,
        String prereqRaw,
        int yearLevel,
        int semester
    ) {
    }
}
