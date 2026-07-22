package com.iuims.registrar.controller;
import com.iuims.registrar.entity.Student;
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

import com.iuims.registrar.service.academic.AcademicGradingService;
import com.iuims.registrar.service.admission.FinanceAdmissionService;
import com.iuims.registrar.service.support.GlobalTermService;
import com.iuims.registrar.service.integration.JaypeeIntegrationService;
import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class PortalController {

    private final AcademicGradingService academicService;
    private final FinanceAdmissionService financeService;
    private final JaypeeIntegrationService jaypeeService;
    private final ScholarEnrollmentService scholarEnrollmentService;
    private final GlobalTermService globalTermService;
    private final JdbcTemplate jdbcTemplate;

    public PortalController(AcademicGradingService academicService, FinanceAdmissionService financeService, JaypeeIntegrationService jaypeeService, ScholarEnrollmentService scholarEnrollmentService, GlobalTermService globalTermService, JdbcTemplate jdbcTemplate) {
        this.academicService = academicService;
        this.financeService = financeService;
        this.jaypeeService = jaypeeService;
        this.scholarEnrollmentService = scholarEnrollmentService;
        this.globalTermService = globalTermService;
        this.jdbcTemplate = jdbcTemplate;
    }


    @GetMapping("/")
    public String dashboard(HttpSession s, Model m) {
        financeService.syncVerifiedPayments(); 
        Map<String, Object> user = (Map<String, Object>) s.getAttribute("currentUser");
        if (user == null) return "redirect:/login";
        
        String role = (String) user.get("role");
        if ("Dean".equals(role)) return "redirect:/grades";
        if ("Faculty".equals(role)) return "redirect:/grades";
        if ("Student".equals(role)) return "redirect:/enrollment";
        
        // Active Term Details
        String termCode = globalTermService.getCurrentGlobalTermCode();
        
        Map<String, Object> activeTerm = null;
        if (termCode != null) {
            try {
                activeTerm = jdbcTemplate.queryForMap("SELECT * FROM academic_terms WHERE term_code = ?", termCode);
                m.addAttribute("activeTerm", activeTerm);
            } catch (Exception e) {}
        }
        
        // Stat Cards
        Long totalEnrolled = countCurrentTermStudents(
            "UPPER(COALESCE(s.admission_status, u.admission_status, '')) = 'ENROLLED'",
            termCode);
        Long newEnrolled = countCurrentTermStudents(
            "COALESCE(s.student_type, u.student_type, '') = 'New Student' " +
                "AND UPPER(COALESCE(s.admission_status, u.admission_status, '')) IN ('ADMITTED', 'ENROLLED')",
            termCode);
        Long scholarshipsGranted = jdbcTemplate.queryForObject("SELECT count(*) FROM student_scholarships WHERE status = 'ACTIVE'", Long.class);
        
        m.addAttribute("totalEnrolled", totalEnrolled != null ? totalEnrolled : 0);
        m.addAttribute("newEnrolled", newEnrolled != null ? newEnrolled : 0);
        m.addAttribute("scholarshipsGranted", scholarshipsGranted != null ? scholarshipsGranted : 0);
        
        // Recent Enrollments
        List<Map<String, Object>> recentEnrollments = recentCurrentTermEnrollments(termCode);
        m.addAttribute("recentEnrollments", recentEnrollments);
        
        m.addAttribute("user", user);
        return "dashboard";
    }

    private Long countCurrentTermStudents(String statusFilter, String termCode) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM students s " +
            "LEFT JOIN sys_users u ON u.username = s.student_number " +
            "WHERE " + statusFilter;
        sql += appendCurrentTermFilter("s", termCode, args);
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return count != null ? count : 0L;
    }

    private List<Map<String, Object>> recentCurrentTermEnrollments(String termCode) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT s.student_number, s.real_name, s.year_level, " +
            "COALESCE(s.admission_status, u.admission_status) AS admission_status, u.admission_date " +
            "FROM students s LEFT JOIN sys_users u ON u.username = s.student_number " +
            "WHERE UPPER(COALESCE(s.admission_status, u.admission_status, '')) IN ('ADMITTED', 'ENROLLED')";
        sql += appendCurrentTermFilter("s", termCode, args);
        sql += " ORDER BY u.admission_date DESC, s.student_number DESC LIMIT 5";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    private String appendCurrentTermFilter(String alias, String termCode, List<Object> args) {
        if (termCode == null || termCode.isBlank()) {
            return "";
        }
        args.add(termCode);
        args.add(termCode);
        args.add(termCode);
        return " AND (" + alias + ".term_year = ? OR " + alias + ".term_year = " +
            "CONCAT('SL', SUBSTRING(?, 3, 8), COALESCE(" + alias + ".year_level, 1), SUBSTRING(?, 1, 1)))";
    }

    @GetMapping("/login")
    public String loginPage() { return "login"; }

    @PostMapping("/login")
    public String doLogin(@RequestParam String username, @RequestParam String password, HttpSession s, Model m) {
        Map<String, Object> user = academicService.login(username, password);
        if (user != null) {
            s.setAttribute("currentUser", user);
            return "redirect:/";
        }
        m.addAttribute("error", "Invalid Credentials");
        return "login";
    }

    @PostMapping("/logout")
    public String logout(HttpSession s) { 
        s.invalidate(); 
        return "redirect:/login"; 
    }

    @GetMapping("/enrollment")
    public String studentEnrollment(HttpSession s, Model m) {
        Map<String, Object> u = (Map<String, Object>) s.getAttribute("currentUser");
        if (u == null || !"Student".equals(u.get("role"))) return "redirect:/login";
        
        m.addAttribute("studentLoad", jaypeeService.getStudentLoad((String) u.get("username")));
        return "enrollment"; 
    }

    @GetMapping("/my-grades")
    public String studentMyGrades(HttpSession s, Model m) {
        financeService.syncVerifiedPayments(); 
        Map<String, Object> u = (Map<String, Object>) s.getAttribute("currentUser");
        if (u == null || !"Student".equals(u.get("role"))) return "redirect:/login";
        
        int sid = ((Number) u.get("user_id")).intValue();
        String username = (String) u.get("username");
        if (Boolean.TRUE.equals(financeService.calculateAssessment(username).get("has_accounting_block"))) {
            return "redirect:/student/finance?errorMsg=ACCOUNTING HOLD: Settle account to view grades.";
        }
        m.addAttribute("academicHistory", academicService.getStudentAcademicHistory(sid));
        return "student_grades"; 
    }

    @GetMapping("/student/finance")
    public String studentFinance(@RequestParam(required=false) String errorMsg, HttpSession s, Model m) {
        financeService.syncVerifiedPayments(); 
        Map<String, Object> u = (Map<String, Object>) s.getAttribute("currentUser");
        if (u == null || !"Student".equals(u.get("role"))) return "redirect:/login";
        
        int sid = ((Number) u.get("user_id")).intValue();
        String username = (String) u.get("username");
        if (errorMsg != null) m.addAttribute("errorMsg", errorMsg);
        
        m.addAttribute("finance", financeService.calculateAssessment(username));
        m.addAttribute("ledger", financeService.getStudentLedger(username)); 
        m.addAttribute("scholarInfo", scholarEnrollmentService.buildFinancialSummary(u));
        return "student_finance"; 
    }

    @GetMapping("/my-load")
    public String studentMyLoad(HttpSession s, Model m) {
        financeService.syncVerifiedPayments(); 
        Map<String, Object> u = (Map<String, Object>) s.getAttribute("currentUser");
        if (u == null || !"Student".equals(u.get("role"))) return "redirect:/login";
        
        int sid = ((Number) u.get("user_id")).intValue();
        String username = (String) u.get("username");
        Map<String, Object> financeInfo = financeService.calculateAssessment(username);
        if (Boolean.TRUE.equals(financeInfo.get("has_accounting_block"))) {
            return "redirect:/student/finance?errorMsg=ACCOUNTING HOLD: Required downpayment is needed for the official Registration Form.";
        }
        
        m.addAttribute("student", u);

        List<Map<String, Object>> crossLoad = jaypeeService.getStudentLoad((String) u.get("username"));
        m.addAttribute("studentLoad", crossLoad);
        
        int total = 0; 
        for(Map<String,Object> cls : crossLoad) { 
            if(cls.get("units") != null) total += ((Number)cls.get("units")).intValue(); 
        }
        m.addAttribute("totalUnits", total);
        m.addAttribute("finance", financeInfo);
        m.addAttribute("corTermLabel", academicService.getCurrentTermLabel());
        return "student_cor"; 
    }
}



