package com.iuims.registrar.curriculum;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CourseCatalogController {

    @Autowired
    private CourseCatalogService courseCatalogService;

    @GetMapping("/admin/courses")
    public String courseCatalog(HttpSession session,
                                Model model,
                                @RequestParam(required = false) String search,
                                @RequestParam(required = false) Integer departmentId,
                                @RequestParam(defaultValue = "active") String status,
                                @RequestParam(required = false) String msg,
                                @RequestParam(required = false) String error) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";

        model.addAttribute("courses", courseCatalogService.listCourses(search, departmentId, status));
        model.addAttribute("departments", courseCatalogService.listDepartments());
        model.addAttribute("summary", courseCatalogService.summary(search, departmentId, status));
        model.addAttribute("search", search);
        model.addAttribute("selectedDepartmentId", departmentId);
        model.addAttribute("selectedStatus", status);
        if (msg != null) model.addAttribute("successMsg", msg);
        if (error != null) model.addAttribute("errorMsg", error);
        return "admin_course_catalog";
    }

    @PostMapping("/admin/courses/save")
    public String saveCourse(@RequestParam(required = false) Integer courseId,
                             @RequestParam String courseCode,
                             @RequestParam String courseTitle,
                             @RequestParam Integer departmentId,
                             @RequestParam(required = false) Integer lectureUnits,
                             @RequestParam(required = false) Integer laboratoryUnits,
                             @RequestParam(required = false) Boolean active,
                             @RequestParam(required = false) String returnSearch,
                             @RequestParam(required = false) Integer returnDepartmentId,
                             @RequestParam(required = false, defaultValue = "active") String returnStatus,
                             HttpSession session,
                             RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            Integer savedId = courseCatalogService.saveCourse(courseId, courseCode, courseTitle, departmentId, lectureUnits, laboratoryUnits, active);
            ra.addAttribute("msg", "Course saved.");
            ra.addAttribute("focusCourseId", savedId);
        } catch (Exception e) {
            ra.addAttribute("error", "Save failed: " + e.getMessage());
        }
        addReturnFilters(ra, returnSearch, returnDepartmentId, returnStatus);
        return "redirect:/admin/courses";
    }

    @GetMapping("/admin/courses/usage")
    @ResponseBody
    public ResponseEntity<?> courseUsage(@RequestParam int courseId, HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return ResponseEntity.status(401).body("Login required.");
        }
        try {
            return ResponseEntity.ok(courseCatalogService.usageDetails(courseId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/admin/courses/status")
    public String setCourseStatus(@RequestParam int courseId,
                                  @RequestParam boolean active,
                                  @RequestParam(required = false) String returnSearch,
                                  @RequestParam(required = false) Integer returnDepartmentId,
                                  @RequestParam(required = false, defaultValue = "active") String returnStatus,
                                  HttpSession session,
                                  RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            courseCatalogService.setActiveStatus(courseId, active);
            ra.addAttribute("msg", active ? "Course reactivated." : "Course deactivated.");
        } catch (Exception e) {
            ra.addAttribute("error", "Status update failed: " + e.getMessage());
        }
        addReturnFilters(ra, returnSearch, returnDepartmentId, returnStatus);
        return "redirect:/admin/courses";
    }

    @PostMapping("/admin/courses/delete-unused")
    public String deleteUnusedCourse(@RequestParam int courseId,
                                     @RequestParam(required = false) String returnSearch,
                                     @RequestParam(required = false) Integer returnDepartmentId,
                                     @RequestParam(required = false, defaultValue = "active") String returnStatus,
                                     HttpSession session,
                                     RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            courseCatalogService.deleteUnusedCourse(courseId);
            ra.addAttribute("msg", "Unused course deleted.");
        } catch (Exception e) {
            ra.addAttribute("error", "Delete failed: " + e.getMessage());
        }
        addReturnFilters(ra, returnSearch, returnDepartmentId, returnStatus);
        return "redirect:/admin/courses";
    }

    private void addReturnFilters(RedirectAttributes ra, String search, Integer departmentId, String status) {
        if (search != null && !search.isBlank()) {
            ra.addAttribute("search", search);
        }
        if (departmentId != null && departmentId > 0) {
            ra.addAttribute("departmentId", departmentId);
        }
        if (status != null && !status.isBlank()) {
            ra.addAttribute("status", status);
        }
    }
}
