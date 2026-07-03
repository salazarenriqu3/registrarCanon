package com.iuims.registrar.portal;
import com.iuims.registrar.academic.AcademicGradingService;
import com.iuims.registrar.core.GlobalTermService;
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

import com.iuims.registrar.academic.AcademicGradingService;
import com.iuims.registrar.core.GlobalTermService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.admission.FinanceAdmissionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ScholarController {

    private final ScholarEnrollmentService scholarService;
    private final FinanceAdmissionService financeService;
    private final AcademicGradingService academicService;
    private final GlobalTermService globalTermService;
    private final EnlistmentSchemaService enlistmentSchemaService;
    private final JdbcTemplate db;

    public ScholarController(ScholarEnrollmentService scholarService, FinanceAdmissionService financeService, AcademicGradingService academicService, GlobalTermService globalTermService, EnlistmentSchemaService enlistmentSchemaService, JdbcTemplate db) {
        this.scholarService = scholarService;
        this.financeService = financeService;
        this.academicService = academicService;
        this.globalTermService = globalTermService;
        this.enlistmentSchemaService = enlistmentSchemaService;
        this.db = db;
    }


    private boolean isNotLoggedIn(HttpSession session) {
        return session.getAttribute("currentUser") == null;
    }

    @GetMapping("/admin/scholar-walkin")
    public String walkinPage(@RequestParam(required = false) String keyword, Model model, HttpSession session) {
        if (isNotLoggedIn(session)) return "redirect:/login";
        if (keyword != null && !keyword.isBlank()) {
            Map<String, Object> student = scholarService.findStudent(keyword.trim());
            if (student != null) {
                int y = student.get("year_level") != null ? ((Number) student.get("year_level")).intValue() : 1;
                String globalTerm = globalTermService.getCurrentStudentTermYear(y);
                String termYear = student.get("term_year") != null && !student.get("term_year").toString().isBlank()
                    ? student.get("term_year").toString()
                    : globalTerm;
                if (termYear == null || termYear.isBlank()) {
                    model.addAttribute("termConfigWarning",
                        "No student term or global academic term is configured. Payment assessment may be incomplete until a term is activated.");
                }
                String studentId = resolveStudentUsername(student);
                if (studentId == null || studentId.isBlank()) {
                    model.addAttribute("errorMessage", "Student record is missing a canonical student number.");
                    model.addAttribute("student", student);
                    model.addAttribute("keyword", keyword);
                    model.addAttribute("withdrawnStudent", isWithdrawnStudent(student));
                    return "admin_scholar_walkin";
                }
                Map<String, Object> fin = financeService.calculateAssessment(studentId);
                model.addAttribute("student", student);
                model.addAttribute("keyword", keyword);
                model.addAttribute("withdrawnStudent", isWithdrawnStudent(student));
                model.addAllAttributes(fin);
            }
        }
        return "admin_scholar_walkin";
    }

    @GetMapping("/admin/scholar-cashier")
    public String cashierPage(@RequestParam(required = false) String keyword, Model model, HttpSession session) {
        if (isNotLoggedIn(session)) return "redirect:/login";
        if (keyword != null && !keyword.isBlank()) {
            Map<String, Object> student = scholarService.findStudent(keyword.trim());
            if (student != null) {
                int y = student.get("year_level") != null ? ((Number) student.get("year_level")).intValue() : 1;
                int s = student.get("semester") != null ? ((Number) student.get("semester")).intValue() : 1;
                String program = student.get("program_code") != null ? student.get("program_code").toString() : "";
                String sid = resolveStudentUsername(student);
                if (sid == null || sid.isBlank()) {
                    model.addAttribute("errorMessage", "Student record is missing a canonical student number.");
                    model.addAttribute("student", student);
                    model.addAttribute("keyword", keyword);
                    model.addAttribute("withdrawnStudent", isWithdrawnStudent(student));
                    return "admin_scholar_cashier";
                }

                model.addAttribute("student", student);
                model.addAttribute("keyword", keyword);
                boolean withdrawnStudent = isWithdrawnStudent(student);
                model.addAttribute("withdrawnStudent", withdrawnStudent);
                model.addAllAttributes(financeService.calculateAssessment(sid));
                if (!withdrawnStudent) {
                    model.addAttribute("enlistedSubjects", scholarService.getAcademicLoad(sid));
                    model.addAttribute("designatedCourses", scholarService.getAvailableSubjects(program, y, s, ""));
                    model.addAttribute("otherCourses", scholarService.getOtherSubjects(program, y, s));
                } else {
                    model.addAttribute("enlistedSubjects", java.util.List.of());
                    model.addAttribute("designatedCourses", java.util.List.of());
                    model.addAttribute("otherCourses", java.util.List.of());
                }
            }
        }
        return "admin_scholar_cashier";
    }

    @PostMapping("/admin/enlist-subject")
    public String enlistSubject(@RequestParam String sid, @RequestParam int sectionId, @RequestParam String keyword, RedirectAttributes ra) {
        String studentNumber = resolveStudentNumber(sid, keyword);
        if (studentNumber == null || studentNumber.isBlank()) {
            ra.addFlashAttribute("errorMessage", "Student not found.");
            return "redirect:/admin/scholar-cashier?keyword=" + keyword;
        }
        Map<String, Object> student = scholarService.findStudent(studentNumber);
        if (isWithdrawnStudent(student)) {
            ra.addFlashAttribute("errorMessage", "Withdrawn students cannot be enrolled in subjects.");
            return "redirect:/admin/scholar-cashier?keyword=" + keyword;
        }
        if (scholarService.hasAccountingBlock(studentNumber)) {
            ra.addFlashAttribute("errorMessage",
                "ENLISTMENT BLOCKED: Prior-term forwarded balance of PHP "
                    + String.format("%,.2f", scholarService.getForwardedBalanceNet(studentNumber))
                    + " must be settled at Cashier first.");
            return "redirect:/admin/scholar-cashier?keyword=" + keyword;
        }
        // Find course linked to section
        Map<String, Object> section = db.queryForMap("SELECT course_id FROM class_sections WHERE section_id = ?", sectionId);
        int courseId = ((Number)section.get("course_id")).intValue();
        
        if (enlistmentSchemaService.hasEnlistmentStatusColumn()) {
            db.update(
                "INSERT INTO student_enlistments (student_id, course_id, section_id, enlistment_status) VALUES (?, ?, ?, ?)",
                studentNumber, courseId, sectionId, enlistmentSchemaService.committedStatusValue());
        } else {
            db.update("INSERT INTO student_enlistments (student_id, course_id, section_id) VALUES (?, ?, ?)",
                studentNumber, courseId, sectionId);
        }
        
        // Trigger Financial Sync (Logic extracted from enrollment3)
        updateLedger(studentNumber);
        
        ra.addFlashAttribute("successMessage", "Subject added successfully.");
        return "redirect:/admin/scholar-cashier?keyword=" + keyword;
    }

    private String resolveStudentNumber(String sid, String keyword) {
        for (String candidate : List.of(sid, keyword)) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String trimmed = candidate.trim();
            try {
                return db.queryForObject(
                    "SELECT student_number FROM students WHERE student_number = ? LIMIT 1",
                    String.class, trimmed);
            } catch (Exception ignored) {
            }
            try {
                return db.queryForObject(
                    "SELECT student_number FROM students WHERE user_id = ? LIMIT 1",
                    String.class, Integer.parseInt(trimmed));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @PostMapping("/admin/drop-subject")
    public String dropSubject(@RequestParam int eid, @RequestParam String keyword, RedirectAttributes ra) {
        ra.addFlashAttribute("errorMessage",
            "Direct subject drop is retired. Open Student Profile and use the drop controls.");
        return "redirect:/admin/scholar-cashier?keyword=" + keyword;
    }

    private void updateLedger(String sid) {
        scholarService.syncCoreLedgerAssessment(sid);
    }

    @PostMapping({"/admin/scholar-payment", "/admin/scholar-process-walkin"})
    public String processPayment(@RequestParam String studentIdentifier,
                                 @RequestParam double amount,
                                 @RequestParam(required = false) String paymentType,
                                 @RequestParam(required = false) String remarks,
                                 RedirectAttributes ra) {
        Map<String, Object> student = scholarService.findStudent(studentIdentifier);
        if (student == null) {
            ra.addFlashAttribute("errorMessage", "Student not found.");
            return "redirect:/admin/scholar-cashier?keyword=" + studentIdentifier;
        }
        if (isWithdrawnStudent(student)) {
            ra.addFlashAttribute("errorMessage", "Withdrawn students cannot post payments through the cashier.");
            return "redirect:/admin/scholar-cashier?keyword=" + studentIdentifier;
        }

        int yearLevel = resolveAcademicInt(student.get("year_level"), 1);
        int semester = resolveAcademicInt(student.get("semester"), 1);
        String termYear = resolveTermYear(student, yearLevel);
        String effectivePaymentType = paymentType != null && !paymentType.isBlank() ? paymentType.trim() : "CASH";
        String effectiveRemarks = remarks != null && !remarks.isBlank() ? remarks.trim() : "Tuition Fee";

        scholarService.processWalkInPayment(
            studentIdentifier,
            amount,
            effectivePaymentType,
            effectiveRemarks,
            semester,
            yearLevel,
            termYear != null ? termYear : ""
        );
        ra.addFlashAttribute("successMessage", "Payment posted.");
        return "redirect:/admin/scholar-cashier?keyword=" + studentIdentifier;
    }

    private boolean isWithdrawnStudent(Map<String, Object> student) {
        if (student == null) {
            return false;
        }
        Object status = student.get("admission_status");
        Object appStatus = student.get("status");
        Object isActive = student.get("is_active");
        return "WITHDRAWN".equalsIgnoreCase(String.valueOf(status))
            || "WITHDRAWN".equalsIgnoreCase(String.valueOf(appStatus))
            || (isActive instanceof Number n && n.intValue() == 0);
    }

    private int resolveAcademicInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.parseInt(value.toString().trim()) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private String resolveTermYear(Map<String, Object> student, int yearLevel) {
        String termYear = student.get("term_year") != null ? student.get("term_year").toString().trim() : "";
        if (!termYear.isBlank()) {
            return termYear;
        }
        return globalTermService.getCurrentStudentTermYear(yearLevel);
    }

    private String resolveStudentUsername(Map<String, Object> student) {
        if (student == null) {
            return null;
        }
        Object username = student.get("username");
        if (username != null && !username.toString().isBlank()) {
            return username.toString().trim();
        }
        Object studentNumber = student.get("student_number");
        if (studentNumber != null && !studentNumber.toString().isBlank()) {
            return studentNumber.toString().trim();
        }
        Object referenceNumber = student.get("reference_number");
        if (referenceNumber != null && !referenceNumber.toString().isBlank()) {
            return referenceNumber.toString().trim();
        }
        return null;
    }
}




