package com.iuims.registrar.curriculum;
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

import com.iuims.registrar.curriculum.CurriculumSeederService;
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
        model.addAttribute("courses", courses);
        model.addAttribute("curriculum", seederService.getCurriculumSummary(curriculumId));
        model.addAttribute("curriculumId", curriculumId);
        model.addAttribute("curriculumEditable", seederService.isEditableDraft(curriculumId));
        model.addAttribute("programs", seederService.listPrograms());
        model.addAttribute("programOptions", seederService.listProgramOptions());
        model.addAttribute("departments", seederService.listDepartments());
        model.addAttribute("completionQueue", seederService.listCurriculumCompletionQueue());
        model.addAttribute("selectedView", "active");
        if (msg   != null) model.addAttribute("successMsg", msg);
        if (error != null) model.addAttribute("errorMsg", error);
        return "admin_curriculum";
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
            ra.addAttribute("msg", "Normalized active curricula: "
                + result.get("duplicatePrograms") + " program(s) checked, "
                + result.get("archivedCurricula") + " legacy curriculum template(s) archived.");
        } catch (Exception e) {
            ra.addAttribute("error", "Active curriculum normalization failed: " + e.getMessage());
        }
        return "redirect:/admin/curriculum";
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
                                  @RequestParam(required = false) String academicYear,
                                  @RequestParam(required = false) String curriculumName,
                                  HttpSession session,
                                  RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            Integer newCurriculumId = seederService.cloneCurriculumToDraft(sourceCurriculumId, academicYear, curriculumName);
            ra.addAttribute("msg", "Draft curriculum created from snapshot #" + sourceCurriculumId + ".");
            return "redirect:/admin/curriculum/view/" + newCurriculumId;
        } catch (Exception e) {
            ra.addAttribute("error", "Clone failed: " + e.getMessage());
            return "redirect:/admin/curriculum";
        }
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
                                  @RequestParam(required = false) String prerequisites,
                                  HttpSession session,
                                  RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            seederService.addManualCourse(curriculumId, courseCode, courseTitle, lectureUnits, laboratoryUnits, yearLevel, semesterNumber, prerequisites);
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
            ra.addAttribute("msg", "Curriculum finalized and activated.");
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
    // Upload a single .docx and seed it immediately
    // ----------------------------------------------------------------
    @PostMapping("/admin/curriculum/upload")
    public String uploadAndSeed(@RequestParam("file") MultipartFile file,
                                @RequestParam(defaultValue = "General") String schoolName,
                                @RequestParam String programCode,
                                HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";

        if (file.isEmpty() || !Boolean.TRUE.equals(
                file.getOriginalFilename() != null
                && file.getOriginalFilename().toLowerCase().endsWith(".docx"))) {
            ra.addAttribute("error", "❌ Please upload a valid .docx file.");
            return "redirect:/admin/curriculum";
        }
        try {
            Map<String, Object> result = seederService.seedUploadedFile(file, schoolName, programCode);
            int count = result.get("seededCount") != null ? (int) result.get("seededCount") : 0;
            @SuppressWarnings("unchecked")
            List<String> warnings = (List<String>) result.get("warnings");
            String msg = "✅ \"" + file.getOriginalFilename() + "\" — " + count + " courses seeded ["
                + result.get("programCode") + "].";
            if (warnings != null && !warnings.isEmpty())
                msg += " ⚠ " + warnings.size() + " warning(s).";
            ra.addAttribute("msg", msg);
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




