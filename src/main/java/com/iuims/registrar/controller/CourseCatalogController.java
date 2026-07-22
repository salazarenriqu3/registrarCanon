package com.iuims.registrar.controller;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.service.curriculum.CourseCatalogService;

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

import java.util.List;

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
                                @RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "50") int size,
                                @RequestParam(required = false) String msg,
                                @RequestParam(required = false) String error,
                                @RequestParam(required = false) Integer focusCourseId) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";

        CourseCatalogService.CoursePage coursePage = courseCatalogService.listCoursesPage(search, departmentId, status, page, size);
        model.addAttribute("courses", coursePage.rows());
        model.addAttribute("departments", courseCatalogService.listDepartments());
        model.addAttribute("summary", coursePage.summary());
        model.addAttribute("search", search);
        model.addAttribute("selectedDepartmentId", departmentId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("focusCourseId", focusCourseId);
        model.addAttribute("page", coursePage.page());
        model.addAttribute("pageSize", coursePage.pageSize());
        model.addAttribute("totalRows", coursePage.totalRows());
        model.addAttribute("totalPages", coursePage.totalPages());
        model.addAttribute("pageStart", coursePage.totalRows() == 0 ? 0 : ((coursePage.page() - 1) * coursePage.pageSize()) + 1);
        model.addAttribute("pageEnd", Math.min(coursePage.page() * coursePage.pageSize(), coursePage.totalRows()));
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
                             @RequestParam(required = false, defaultValue = "1") Integer returnPage,
                             @RequestParam(required = false, defaultValue = "50") Integer returnSize,
                             HttpSession session,
                             RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            Integer savedId = courseCatalogService.saveCourse(
                courseId,
                courseCode,
                courseTitle,
                departmentId,
                lectureUnits,
                laboratoryUnits,
                active);
            ra.addAttribute("msg", "Course saved.");
            ra.addAttribute("focusCourseId", savedId);
        } catch (Exception e) {
            ra.addAttribute("error", "Save failed: " + e.getMessage());
        }
        addReturnFilters(ra, returnSearch, returnDepartmentId, returnStatus, returnPage, returnSize);
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

    @GetMapping("/admin/courses/relationships")
    @ResponseBody
    public ResponseEntity<?> courseRelationships(@RequestParam int courseId, HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return ResponseEntity.status(401).body("Login required.");
        }
        try {
            return ResponseEntity.ok(courseCatalogService.relationshipEditorDetails(courseId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/admin/courses/relationship-search")
    @ResponseBody
    public ResponseEntity<?> relationshipSearch(@RequestParam(required = false, defaultValue = "") String q,
                                                @RequestParam(required = false) Integer departmentId,
                                                HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return ResponseEntity.status(401).body("Login required.");
        }
        return ResponseEntity.ok(courseCatalogService.searchRelationshipCourses(q, departmentId));
    }

    @PostMapping("/admin/courses/relationships/save")
    public String saveCourseRelationships(@RequestParam int courseId,
                                          @RequestParam(required = false) List<Integer> prerequisiteCourseIds,
                                          @RequestParam(required = false) List<Integer> corequisiteCourseIds,
                                          @RequestParam(required = false) List<Integer> equivalencyCourseIds,
                                          @RequestParam(required = false) String returnSearch,
                                          @RequestParam(required = false) Integer returnDepartmentId,
                                          @RequestParam(required = false, defaultValue = "active") String returnStatus,
                                          @RequestParam(required = false, defaultValue = "1") Integer returnPage,
                                          @RequestParam(required = false, defaultValue = "50") Integer returnSize,
                                          HttpSession session,
                                          RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            courseCatalogService.saveCourseRelationships(courseId, prerequisiteCourseIds, corequisiteCourseIds, equivalencyCourseIds);
            ra.addAttribute("msg", "Course relationships saved.");
            ra.addAttribute("focusCourseId", courseId);
        } catch (Exception e) {
            ra.addAttribute("error", "Relationship save failed: " + e.getMessage());
        }
        addReturnFilters(ra, returnSearch, returnDepartmentId, returnStatus, returnPage, returnSize);
        return "redirect:/admin/courses";
    }

    @PostMapping("/admin/courses/status")
    public String setCourseStatus(@RequestParam int courseId,
                                  @RequestParam boolean active,
                                  @RequestParam(required = false) String returnSearch,
                                  @RequestParam(required = false) Integer returnDepartmentId,
                                  @RequestParam(required = false, defaultValue = "active") String returnStatus,
                                  @RequestParam(required = false, defaultValue = "1") Integer returnPage,
                                  @RequestParam(required = false, defaultValue = "50") Integer returnSize,
                                  HttpSession session,
                                  RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            courseCatalogService.setActiveStatus(courseId, active);
            ra.addAttribute("msg", active ? "Course reactivated." : "Course deactivated.");
        } catch (Exception e) {
            ra.addAttribute("error", "Status update failed: " + e.getMessage());
        }
        addReturnFilters(ra, returnSearch, returnDepartmentId, returnStatus, returnPage, returnSize);
        return "redirect:/admin/courses";
    }

    @PostMapping("/admin/courses/delete-unused")
    public String deleteUnusedCourse(@RequestParam int courseId,
                                     @RequestParam(required = false) String returnSearch,
                                     @RequestParam(required = false) Integer returnDepartmentId,
                                     @RequestParam(required = false, defaultValue = "active") String returnStatus,
                                     @RequestParam(required = false, defaultValue = "1") Integer returnPage,
                                     @RequestParam(required = false, defaultValue = "50") Integer returnSize,
                                     HttpSession session,
                                     RedirectAttributes ra) {
        if (session.getAttribute("currentUser") == null) return "redirect:/login";
        try {
            courseCatalogService.deleteUnusedCourse(courseId);
            ra.addAttribute("msg", "Unused course deleted.");
        } catch (Exception e) {
            ra.addAttribute("error", "Delete failed: " + e.getMessage());
        }
        addReturnFilters(ra, returnSearch, returnDepartmentId, returnStatus, returnPage, returnSize);
        return "redirect:/admin/courses";
    }

    private void addReturnFilters(RedirectAttributes ra, String search, Integer departmentId, String status, Integer page, Integer size) {
        if (search != null && !search.isBlank()) {
            ra.addAttribute("search", search);
        }
        if (departmentId != null && departmentId > 0) {
            ra.addAttribute("departmentId", departmentId);
        }
        if (status != null && !status.isBlank()) {
            ra.addAttribute("status", status);
        }
        if (page != null && page > 1) ra.addAttribute("page", page);
        if (size != null && size != 50) ra.addAttribute("size", size);
    }
}
