package com.iuims.registrar.controller;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Program;
import com.iuims.registrar.service.academic.AcademicGradingService;
import com.iuims.registrar.support.GradeOutcomeSql;
import com.iuims.registrar.service.admission.ApplicantStatusSyncService;
import com.iuims.registrar.service.admission.FinanceAdmissionService;
import com.iuims.registrar.service.curriculum.CurriculumSeederService;
import com.iuims.registrar.service.curriculum.StudentCurriculumService;
import com.iuims.registrar.service.support.EnlistmentSchemaService;
import com.iuims.registrar.service.faculty.FacultyLoadService;
import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.service.finance.TermFeeAdminService;
import com.iuims.registrar.service.support.DatabaseSetupService;
import com.iuims.registrar.service.integration.JaypeeIntegrationService;
import com.iuims.registrar.support.PolicySettings;
import com.iuims.registrar.support.SqlGenerator;

import com.iuims.registrar.service.curriculum.CurriculumSeederService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles all curriculum management UI routes:
 *
 *  GET  /admin/curriculum            — program list dashboard
 *  GET  /admin/curriculum/view/{id}  — per-program course table
 *  POST /admin/curriculum/seed-all   — re-seed all classpath .docx files
 *  POST /admin/curriculum/upload     — upload & seed a single .docx
 */
@Controller
public class CurriculumController {

    @Autowired
    private CurriculumSeederService seederService;

    // ----------------------------------------------------------------
    // Dashboard — list all seeded programs
    // ----------------------------------------------------------------
    @GetMapping("/admin/curriculum")
    public String curriculumDashboard(HttpSession session, Model model,
                                      @RequestParam(defaultValue = "active") String view,
                                      @RequestParam(required = false) String programCode,
                                      @RequestParam(required = false) String msg,
                                      @RequestParam(required = false) String error) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";

        model.addAttribute("programs", seederService.listCurriculumDashboard(view, programCode));
        model.addAttribute("programOptions", seederService.listProgramOptions());
        model.addAttribute("departments", seederService.listDepartments());
        model.addAttribute("catalogCourseOptions", seederService.listActiveCourseCatalogOptions(null));
        model.addAttribute("completionQueue", seederService.listCurriculumCompletionQueue());
        model.addAttribute("selectedView", view);
        model.addAttribute("selectedProgramCode", programCode);
        if (msg   != null) model.addAttribute("successMsg", msg);
        if (error != null) model.addAttribute("errorMsg", error);
        return "admin_curriculum";
    }

    // ----------------------------------------------------------------
    // View courses for a specific curriculum
    // ----------------------------------------------------------------
    @GetMapping("/admin/curriculum/view/{curriculumId}")
    public String viewCurriculum(@PathVariable int curriculumId,
                                 HttpSession session,
                                 Model model,
                                 @RequestParam(required = false) String msg,
                                 @RequestParam(required = false) String error) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";

        List<Map<String, Object>> courses = seederService.listCurriculumCourses(curriculumId);
        List<Map<String, Object>> curriculumGroups = buildCurriculumGroups(courses);
        model.addAttribute("courses", courses);
        model.addAttribute("curriculumGroups", curriculumGroups);
        model.addAttribute("curriculumOutlineSummary", buildCurriculumOutlineSummary(courses, curriculumGroups));
        model.addAttribute("curriculum", seederService.getCurriculumSummary(curriculumId));
        model.addAttribute("curriculumId", curriculumId);
        model.addAttribute("curriculumEditable", seederService.isEditableDraft(curriculumId));
        model.addAttribute("programs", seederService.listPrograms());
        model.addAttribute("programOptions", seederService.listProgramOptions());
        model.addAttribute("departments", seederService.listDepartments());
        model.addAttribute("catalogCourseOptions", seederService.listActiveCourseCatalogOptions(null));
        model.addAttribute("completionQueue", seederService.listCurriculumCompletionQueue());
        model.addAttribute("selectedView", "active");
        if (msg   != null) model.addAttribute("successMsg", msg);
        if (error != null) model.addAttribute("errorMsg", error);
        return "admin_curriculum";
    }

    private List<Map<String, Object>> buildCurriculumGroups(List<Map<String, Object>> courses) {
        Map<Integer, Map<String, Object>> yearGroups = new LinkedHashMap<>();
        Map<Integer, Map<Integer, Map<String, Object>>> semesterGroups = new LinkedHashMap<>();

        for (Map<String, Object> course : courses) {
            int yearLevel = safeInt(course.get("year_level"), 1);
            int semesterNumber = safeInt(course.get("semester_number"), 1);
            int creditUnits = safeInt(course.get("credit_units"), 0);

            Map<String, Object> yearGroup = yearGroups.computeIfAbsent(yearLevel, year -> {
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("year_level", year);
                group.put("year_label", "Year " + year);
                group.put("course_count", 0);
                group.put("total_units", 0);
                group.put("semester_groups", new ArrayList<Map<String, Object>>());
                return group;
            });
            yearGroup.put("course_count", safeInt(yearGroup.get("course_count"), 0) + 1);
            yearGroup.put("total_units", safeInt(yearGroup.get("total_units"), 0) + creditUnits);

            Map<Integer, Map<String, Object>> perYearSemesters = semesterGroups.computeIfAbsent(yearLevel, key -> new LinkedHashMap<>());
            Map<String, Object> semesterGroup = perYearSemesters.computeIfAbsent(semesterNumber, sem -> {
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("semester_number", sem);
                group.put("semester_label", semesterLabel(sem));
                group.put("course_count", 0);
                group.put("total_units", 0);
                group.put("courses", new ArrayList<Map<String, Object>>());
                return group;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> semesterCourses = (List<Map<String, Object>>) semesterGroup.get("courses");
            semesterCourses.add(course);
            semesterGroup.put("course_count", safeInt(semesterGroup.get("course_count"), 0) + 1);
            semesterGroup.put("total_units", safeInt(semesterGroup.get("total_units"), 0) + creditUnits);
        }

        List<Map<String, Object>> groupedYears = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Object>> yearEntry : yearGroups.entrySet()) {
            Integer yearLevel = yearEntry.getKey();
            Map<String, Object> yearGroup = yearEntry.getValue();
            List<Map<String, Object>> semesters = new ArrayList<>();
            Map<Integer, Map<String, Object>> perYearSemesters = semesterGroups.get(yearLevel);
            if (perYearSemesters != null) {
                semesters.addAll(perYearSemesters.values());
            }
            yearGroup.put("semester_count", semesters.size());
            yearGroup.put("semester_groups", semesters);
            groupedYears.add(yearGroup);
        }
        return groupedYears;
    }

    private Map<String, Object> buildCurriculumOutlineSummary(List<Map<String, Object>> courses,
                                                             List<Map<String, Object>> curriculumGroups) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("year_count", curriculumGroups.size());
        summary.put("course_count", courses.size());
        int totalUnits = 0;
        for (Map<String, Object> course : courses) {
            totalUnits += safeInt(course.get("credit_units"), 0);
        }
        int semesterCount = curriculumGroups.stream().mapToInt(group -> safeInt(group.get("semester_count"), 0)).sum();
        summary.put("semester_count", semesterCount);
        summary.put("total_units", totalUnits);
        return summary;
    }

    private int safeInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private String semesterLabel(int semesterNumber) {
        return switch (semesterNumber) {
            case 1 -> "1st Sem";
            case 2 -> "2nd Sem";
            default -> "Summer";
        };
    }

    // ----------------------------------------------------------------
    // Re-seed everything from classpath .docx files
    // ----------------------------------------------------------------
    @PostMapping("/admin/curriculum/seed-all")
    public String seedAll(HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            List<Map<String, Object>> report = seederService.reseedAll();
            long ok      = report.stream().filter(r -> "success".equals(r.get("status"))).count();
            long partial = report.stream().filter(r -> "partial".equals(r.get("status"))).count();
            ra.addAttribute("msg", "✅ Seeded " + report.size() + " programs — "
                    + ok + " clean, " + partial + " with warnings.");
        } catch (Exception e) {
            ra.addAttribute("error", "❌ Seed failed: " + e.getMessage());
        }
        return "redirect:/admin/curriculum";
    }

    @PostMapping("/admin/curriculum/repair-readiness")
    public String repairReadinessCurricula(HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            Map<String, Object> result = seederService.repairReadinessCurricula();
            ra.addAttribute("msg", "Readiness repair completed: "
                + result.get("placeholdersCreated") + " placeholder curriculum template(s) created for "
                + result.get("blockedPrograms") + " blocked manifest program(s).");
        } catch (Exception e) {
            ra.addAttribute("error", "Curriculum readiness repair failed: " + e.getMessage());
        }
        return "redirect:/admin/curriculum";
    }

    @PostMapping("/admin/curriculum/normalize-active")
    public String normalizeActiveCurricula(HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            Map<String, Object> result = seederService.enforceSingleActiveCurriculumPerProgram();
            ra.addAttribute("msg", "Normalized current offerings: "
                + result.get("duplicatePrograms") + " program(s) checked, "
                + result.getOrDefault("legacyCurricula", 0) + " duplicate curriculum template(s) moved to legacy.");
        } catch (Exception e) {
            ra.addAttribute("error", "Current offering normalization failed: " + e.getMessage());
        }
        return "redirect:/admin/curriculum";
    }

    @PostMapping("/admin/curriculum/lifecycle")
    public String updateCurriculumLifecycle(@RequestParam int curriculumId,
                                            @RequestParam String targetStatus,
                                            HttpSession session,
                                            RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            seederService.setCurriculumLifecycle(curriculumId, targetStatus);
            String normalized = targetStatus == null ? "" : targetStatus.trim().toUpperCase();
            String message = switch (normalized) {
                case "CURRENT", "ACTIVE", "CURRENT_OFFERING" -> "Curriculum set as the current offering.";
                case "LEGACY", "HISTORICAL" -> "Curriculum marked as legacy.";
                case "ARCHIVED", "RETIRED" -> "Curriculum archived.";
                default -> "Curriculum lifecycle updated.";
            };
            ra.addAttribute("msg", message);
        } catch (Exception e) {
            ra.addAttribute("error", "Lifecycle update failed: " + e.getMessage());
        }
        return "redirect:/admin/curriculum/view/" + curriculumId;
    }

    @PostMapping("/admin/curriculum/retire-empty-blockers")
    public String retireEmptyCurriculumBlockers(HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            Map<String, Object> result = seederService.retireEmptyCurriculumBlockers();
            @SuppressWarnings("unchecked")
            List<String> retired = (List<String>) result.getOrDefault("retired", List.of());
            @SuppressWarnings("unchecked")
            List<String> skipped = (List<String>) result.getOrDefault("skipped", List.of());
            String msg = "Retired " + retired.size() + " empty curriculum blocker program(s)";
            if (!retired.isEmpty()) {
                msg += ": " + String.join(", ", retired);
            }
            if (!skipped.isEmpty()) {
                msg += ". Skipped " + skipped.size() + ": " + String.join("; ", skipped);
            }
            ra.addAttribute("msg", msg + ".");
        } catch (Exception e) {
            ra.addAttribute("error", "Program retirement failed: " + e.getMessage());
        }
        return "redirect:/admin/curriculum";
    }

    @PostMapping("/admin/curriculum/placeholder")
    public String createPlaceholder(@RequestParam String programCode,
                                    @RequestParam(required = false) String academicYear,
                                    HttpSession session,
                                    RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            Map<String, Object> created = seederService.createProgramPlaceholder(programCode, academicYear);
            ra.addAttribute("msg", "Placeholder ready for " + created.getOrDefault("program_code", programCode) + ".");
        } catch (Exception e) {
            ra.addAttribute("error", "Placeholder creation failed: " + e.getMessage());
        }
        return "redirect:/admin/curriculum?view=draft";
    }

    @PostMapping("/admin/curriculum/clone")
    public String cloneCurriculum(@RequestParam int sourceCurriculumId,
                                  @RequestParam(required = false) String targetProgramCode,
                                  @RequestParam(required = false) String academicYear,
                                  @RequestParam(required = false) String curriculumName,
                                  @RequestParam(required = false) Integer versionNumber,
                                  HttpSession session,
                                  RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            Integer newCurriculumId = seederService.cloneCurriculumToDraft(
                sourceCurriculumId, targetProgramCode, academicYear, curriculumName, versionNumber);
            ra.addAttribute("msg", "Draft curriculum created from snapshot #" + sourceCurriculumId + ".");
            return "redirect:/admin/curriculum/view/" + newCurriculumId;
        } catch (Exception e) {
            ra.addAttribute("error", "Clone failed: " + e.getMessage());
            return "redirect:/admin/curriculum";
        }
    }

    @PostMapping("/admin/curriculum/draft/update")
    public String updateDraftMetadata(@RequestParam int curriculumId,
                                      @RequestParam(required = false) String targetProgramCode,
                                      @RequestParam String curriculumName,
                                      @RequestParam String academicYear,
                                      @RequestParam(required = false) Integer versionNumber,
                                      HttpSession session,
                                      RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            seederService.updateDraftMetadata(curriculumId, targetProgramCode, curriculumName, academicYear, versionNumber);
            ra.addAttribute("msg", "Draft metadata updated.");
        } catch (Exception e) {
            ra.addAttribute("error", "Draft update failed: " + e.getMessage());
        }
        return "redirect:/admin/curriculum/view/" + curriculumId;
    }

    @PostMapping("/admin/curriculum/delete-draft")
    public String deleteDraft(@RequestParam int curriculumId,
                              HttpSession session,
                              RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            Map<String, Object> deleted = seederService.deleteDraftCurriculum(curriculumId);
            ra.addAttribute("msg", "Draft curriculum deleted for " + deleted.getOrDefault("program_code", "program") + ".");
        } catch (Exception e) {
            ra.addAttribute("error", "Delete failed: " + e.getMessage());
        }
        return "redirect:/admin/curriculum?view=draft";
    }

    @PostMapping("/admin/curriculum/course/add")
    public String addManualCourse(@RequestParam int curriculumId,
                                  @RequestParam String courseCode,
                                  @RequestParam String courseTitle,
                                  @RequestParam(required = false) Integer lectureUnits,
                                  @RequestParam(required = false) Integer laboratoryUnits,
                                  @RequestParam(required = false) Integer yearLevel,
                                  @RequestParam(required = false) Integer semesterNumber,
                                  HttpSession session,
                                  RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            seederService.addManualCourse(curriculumId, courseCode, courseTitle, lectureUnits, laboratoryUnits, yearLevel, semesterNumber);
            ra.addAttribute("msg", "Course row added.");
        } catch (Exception e) {
            ra.addAttribute("error", "Add course failed: " + e.getMessage());
        }
        return "redirect:/admin/curriculum/view/" + curriculumId;
    }

    @PostMapping("/admin/curriculum/course/add-existing")
    public String addExistingCourse(@RequestParam int curriculumId,
                                    @RequestParam int courseId,
                                    @RequestParam(required = false) Integer yearLevel,
                                    @RequestParam(required = false) Integer semesterNumber,
                                    HttpSession session,
                                    RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            seederService.addExistingCourse(curriculumId, courseId, yearLevel, semesterNumber);
            ra.addAttribute("msg", "Existing course attached.");
        } catch (Exception e) {
            ra.addAttribute("error", "Attach course failed: " + e.getMessage());
        }
        return "redirect:/admin/curriculum/view/" + curriculumId;
    }

    @GetMapping("/admin/curriculum/course-search")
    @ResponseBody
    public ResponseEntity<?> searchCourses(@RequestParam(required = false, defaultValue = "") String q,
                                           @RequestParam(required = false) Integer departmentId,
                                           HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return ResponseEntity.status(401).body("Login required.");
        }
        return ResponseEntity.ok(seederService.searchCourseCatalog(q, departmentId));
    }

    @GetMapping("/admin/curriculum/course-options")
    @ResponseBody
    public ResponseEntity<?> courseOptions(@RequestParam(required = false) Integer departmentId,
                                           HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return ResponseEntity.status(401).body("Login required.");
        }
        return ResponseEntity.ok(seederService.listActiveCourseCatalogOptions(departmentId));
    }

    @PostMapping("/admin/curriculum/course/update-placement")
    public String updateManualCoursePlacement(@RequestParam int curriculumId,
                                              @RequestParam int curriculumCourseId,
                                              @RequestParam(required = false) Integer yearLevel,
                                              @RequestParam(required = false) Integer semesterNumber,
                                              HttpSession session,
                                              RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            seederService.updateManualCoursePlacement(curriculumId, curriculumCourseId, yearLevel, semesterNumber);
            ra.addAttribute("msg", "Course placement updated.");
        } catch (Exception e) {
            ra.addAttribute("error", "Update failed: " + e.getMessage());
        }
        return "redirect:/admin/curriculum/view/" + curriculumId;
    }

    @PostMapping("/admin/curriculum/course/remove")
    public String removeManualCourse(@RequestParam int curriculumId,
                                     @RequestParam int curriculumCourseId,
                                     HttpSession session,
                                     RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            seederService.removeManualCourse(curriculumId, curriculumCourseId);
            ra.addAttribute("msg", "Course row removed.");
        } catch (Exception e) {
            ra.addAttribute("error", "Remove failed: " + e.getMessage());
        }
        return "redirect:/admin/curriculum/view/" + curriculumId;
    }

    @PostMapping("/admin/curriculum/finalize")
    public String finalizeCurriculum(@RequestParam int curriculumId,
                                     HttpSession session,
                                     RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            seederService.finalizeDraftCurriculum(curriculumId);
            ra.addAttribute("msg", "Curriculum set as the current offering.");
        } catch (Exception e) {
            ra.addAttribute("error", "Finalize failed: " + e.getMessage());
        }
        return "redirect:/admin/curriculum/view/" + curriculumId;
    }

    @GetMapping("/admin/curriculum/export/{curriculumId}")
    @ResponseBody
    public ResponseEntity<String> exportCurriculum(@PathVariable int curriculumId, HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return ResponseEntity.status(401).body("Login required.");
        }
        String csv = seederService.exportCurriculumCsv(curriculumId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"curriculum-" + curriculumId + ".csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv);
    }

    // ----------------------------------------------------------------
    // Upload a single .docx into a draft curriculum
    // ----------------------------------------------------------------
    @PostMapping("/admin/curriculum/upload")
    public String uploadAndSeed(@RequestParam("file") MultipartFile file,
                                @RequestParam(defaultValue = "General") String schoolName,
                                @RequestParam String programCode,
                                @RequestParam(required = false) String academicYear,
                                @RequestParam(required = false) String curriculumName,
                                @RequestParam(required = false) Integer versionNumber,
                                HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";

        if (file.isEmpty() || !Boolean.TRUE.equals(
                file.getOriginalFilename() != null
                && file.getOriginalFilename().toLowerCase().endsWith(".docx"))) {
            ra.addAttribute("error", "❌ Please upload a valid .docx file.");
            return "redirect:/admin/curriculum";
        }
        try {
            Map<String, Object> result = seederService.seedUploadedFile(
                file, schoolName, programCode, academicYear, curriculumName, versionNumber);
            int count = result.get("seededCount") != null ? (int) result.get("seededCount") : 0;
            Integer curriculumId = result.get("curriculumId") instanceof Number number ? number.intValue() : null;
            @SuppressWarnings("unchecked")
            List<String> warnings = (List<String>) result.get("warnings");
            String msg = "✅ \"" + file.getOriginalFilename() + "\" — " + count + " courses seeded ["
                + result.get("programCode") + "].";
            if (warnings != null && !warnings.isEmpty())
                msg += " ⚠ " + warnings.size() + " warning(s).";
            ra.addAttribute("msg", msg);
            if (curriculumId != null) {
                return "redirect:/admin/curriculum/view/" + curriculumId;
            }
        } catch (Exception e) {
            ra.addAttribute("error", "❌ Upload failed: " + e.getMessage());
        }
        return "redirect:/admin/curriculum";
    }

    // ----------------------------------------------------------------
    // AJAX dry-run preview (returns JSON)
    // ----------------------------------------------------------------
    @PostMapping("/admin/curriculum/preview")
    @ResponseBody
    public ResponseEntity<?> previewUpload(@RequestParam("file") MultipartFile file,
                                           @RequestParam(defaultValue = "General") String schoolName,
                                           @RequestParam(required = false) String programCode,
                                           HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return ResponseEntity.status(401).body("Login required.");
        }
        if (file.isEmpty()) return ResponseEntity.badRequest().body("No file uploaded.");
        try {
            Map<String, Object> preview = seederService.previewCurriculumFile(file, schoolName, programCode);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Preview failed: " + e.getMessage());
        }
    }
}




