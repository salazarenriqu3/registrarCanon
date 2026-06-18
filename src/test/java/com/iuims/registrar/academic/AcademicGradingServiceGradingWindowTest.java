package com.iuims.registrar.academic;
import com.iuims.registrar.academic.AcademicGradingService;
import com.iuims.registrar.core.GradeOutcomeSql;
import com.iuims.registrar.admission.ApplicantStatusSyncService;
import com.iuims.registrar.admission.FinanceAdmissionService;
import com.iuims.registrar.curriculum.CurriculumSeederService;
import com.iuims.registrar.curriculum.StudentCurriculumService;
import com.iuims.registrar.core.EnlistmentSchemaService;
import com.iuims.registrar.forms.StudentDocumentTrailService;
import com.iuims.registrar.faculty.FacultyLoadService;
import com.iuims.registrar.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.finance.TermFeeAdminService;
import com.iuims.registrar.core.DatabaseSetupService;
import com.iuims.registrar.jaypee.JaypeeIntegrationService;
import com.iuims.registrar.core.PolicySettings;
import com.iuims.registrar.core.SqlGenerator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.iuims.registrar.finance.TermFeeAdminService;
import org.springframework.context.ApplicationEventPublisher;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

@DataJpaTest
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@Import({AcademicGradingService.class, AcademicGradingRepository.class, TermFeeAdminService.class,
    EnlistmentSchemaService.class})
class AcademicGradingServiceGradingWindowTest {

    @MockBean
    private StudentDocumentTrailService studentDocumentTrailService;

    @Autowired
    private JdbcTemplate db;
    
    @Autowired
    private AcademicGradingService service;

    @BeforeEach
    void setUp() {
        db.execute("DROP ALL OBJECTS");
        db.execute("""
            CREATE TABLE system_settings (
                setting_key VARCHAR(100) PRIMARY KEY,
                setting_value VARCHAR(100) NULL
            )
            """);
        db.execute("""
            CREATE TABLE academic_terms (
                term_id INT AUTO_INCREMENT PRIMARY KEY,
                term_code VARCHAR(20) NULL,
                term_name VARCHAR(100) NULL,
                academic_year VARCHAR(20) NULL,
                semester_number INT NULL,
                is_active TINYINT NOT NULL DEFAULT 0,
                status VARCHAR(20) NULL
            )
            """);
        db.execute("""
            CREATE TABLE faculty (
                faculty_id INT AUTO_INCREMENT PRIMARY KEY,
                employee_number VARCHAR(20) NULL,
                first_name VARCHAR(100) NULL,
                last_name VARCHAR(100) NULL,
                email VARCHAR(150) NULL
            )
            """);
        db.execute("""
            CREATE TABLE courses (
                course_id INT PRIMARY KEY,
                course_code VARCHAR(20),
                course_title VARCHAR(150),
                department_id INT,
                credit_units INT
            );
            """);
        db.execute("""
            CREATE TABLE class_sections (
                section_id INT AUTO_INCREMENT PRIMARY KEY,
                course_id INT NOT NULL,
                term_id INT NOT NULL,
                section_code VARCHAR(50) NULL,
                faculty_id INT NULL,
                max_capacity INT DEFAULT 50,
                semester_number INT NULL,
                block_id INT NULL,
                section_status VARCHAR(30) NULL
            )
            """);
        db.execute("""
            CREATE TABLE class_schedules (
                schedule_id INT AUTO_INCREMENT PRIMARY KEY,
                section_id INT NOT NULL,
                day_of_week INT NULL,
                start_time TIME NULL,
                end_time TIME NULL
            )
            """);
        db.execute("""
            CREATE TABLE grades (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                student_id VARCHAR(100) NOT NULL,
                course_id INT NOT NULL,
                section_id INT NULL,
                prelim DECIMAL(5,2) NULL,
                midterm DECIMAL(5,2) NULL,
                final_grade DECIMAL(5,2) NULL,
                semestral_grade DECIMAL(5,2) NULL,
                remarks VARCHAR(30) NULL,
                previous_grade VARCHAR(20) NULL,
                registrar_final_grade DECIMAL(5,2) NULL,
                registrar_final_remarks VARCHAR(30) NULL,
                grade_lock_status VARCHAR(30) NULL,
                grade_lock_reason VARCHAR(80) NULL,
                registrar_finalized_at TIMESTAMP NULL,
                student_name VARCHAR(100) NULL,
                status VARCHAR(20) DEFAULT 'DRAFT'
            )
            """);
        db.execute("""
            CREATE TABLE grade_change_requests (
                request_id INT AUTO_INCREMENT PRIMARY KEY,
                grade_id BIGINT NULL,
                student_name VARCHAR(100) NULL,
                course_code VARCHAR(20) NULL,
                faculty_name VARCHAR(100) NULL,
                request_type VARCHAR(40) NOT NULL DEFAULT 'FINAL_GRADE_CORRECTION',
                requested_grade VARCHAR(20) NULL,
                requested_prelim DECIMAL(5,2) NULL,
                requested_midterm DECIMAL(5,2) NULL,
                requested_finals DECIMAL(5,2) NULL,
                reason VARCHAR(500) NULL,
                status VARCHAR(30) DEFAULT 'PENDING',
                request_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                applied_action VARCHAR(80) NULL,
                approved_at TIMESTAMP NULL
            )
            """);
        db.execute("""
            CREATE TABLE sys_users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NULL,
                real_name VARCHAR(100) NULL,
                role VARCHAR(30) NULL,
                password VARCHAR(100) NULL,
                program_code VARCHAR(50) NULL,
                semester INT NULL,
                year_level INT NULL,
                student_type VARCHAR(50) NULL,
                admission_status VARCHAR(50) NULL,
                term_year VARCHAR(20) NULL,
                enrollment_start_time TIMESTAMP NULL,
                granted_permissions VARCHAR(255) NULL,
                is_active BOOLEAN DEFAULT TRUE
            )
            """);

        db.execute("""
            CREATE TABLE grading_term_windows (
                window_id INT AUTO_INCREMENT PRIMARY KEY,
                term_id INT NULL,
                grading_period VARCHAR(20) NULL,
                start_date DATE NULL,
                end_date DATE NULL,
                override_status VARCHAR(20) NULL,
                updated_at TIMESTAMP NULL
            )
            """);

        db.execute("""
            CREATE TABLE academic_term_policies (
                term_id INT PRIMARY KEY,
                inc_expiration_date DATE NULL,
                updated_at TIMESTAMP NULL
            )
            """);

        db.execute("""
            CREATE TABLE students (
                student_number VARCHAR(50) PRIMARY KEY,
                first_name VARCHAR(100) NULL,
                last_name VARCHAR(100) NULL,
                middle_name VARCHAR(100) NULL,
                email VARCHAR(100) NULL,
                mobile VARCHAR(50) NULL,
                program_code VARCHAR(50) NULL,
                year_level INT NULL,
                semester INT NULL,
                term_year VARCHAR(20) NULL,
                student_type VARCHAR(50) NULL,
                admission_status VARCHAR(50) NULL,
                enrollment_status_type VARCHAR(50) NULL,
                real_name VARCHAR(150) NULL,
                user_id INT NULL,
                reference_number VARCHAR(50) NULL,
                scholarship_type VARCHAR(100) NULL,
                discount_percentage DECIMAL(5,2) NULL,
                scholarship_amount DECIMAL(10,2) NULL,
                scholarship_approved BOOLEAN NULL
            )
            """);

        db.update("INSERT INTO academic_terms (term_id, term_code, term_name, is_active, status) VALUES (1, '1120262027', 'A.Y. 2026-2027 - 1st Semester', 0, 'INACTIVE')");
        db.update("INSERT INTO academic_terms (term_id, term_code, term_name, is_active, status) VALUES (2, '2120262027', 'A.Y. 2026-2027 - 2nd Semester', 1, 'ACTIVE')");
        seedGlobalSettings();
    }

    @Test
    void getGradingWindowsUsesGlobalSettingsWhenTermHasNoSpecificRows() {
        Map<String, Object> windows = service.getGradingWindows(1);

        assertThat(windows.get("PRELIM_START")).isEqualTo("2026-01-01");
        assertThat(windows.get("PRELIM_END")).isEqualTo("2026-01-31");
        assertThat(windows.get("PRELIM_OVERRIDE")).isEqualTo("FORCE_CLOSED");
        assertThat(windows.get("prelim_open")).isEqualTo(false);
        assertThat(windows.get("term_scoped")).isEqualTo(false);
    }

    @Test
    void updateSettingsWithGradingTermIdSavesTermSpecificRowsWithoutChangingGlobalFallback() {
        service.updateSettings(settingsWith(
            "gradingTermId", "2",
            "PRELIM_START", "2026-06-01",
            "PRELIM_END", "2026-06-30",
            "PRELIM_OVERRIDE", "FORCE_OPEN",
            "MIDTERM_START", "2026-07-01",
            "MIDTERM_END", "2026-07-31",
            "MIDTERM_OVERRIDE", "AUTO",
            "FINAL_START", "2026-08-01",
            "FINAL_END", "2026-08-31",
            "FINAL_OVERRIDE", "FORCE_CLOSED"
        ));

        Map<String, Object> selectedTerm = service.getGradingWindows(2);
        Map<String, Object> fallbackTerm = service.getGradingWindows(1);

        assertThat(selectedTerm.get("PRELIM_START")).isEqualTo("2026-06-01");
        assertThat(selectedTerm.get("PRELIM_END")).isEqualTo("2026-06-30");
        assertThat(selectedTerm.get("PRELIM_OVERRIDE")).isEqualTo("FORCE_OPEN");
        assertThat(selectedTerm.get("prelim_open")).isEqualTo(true);
        assertThat(selectedTerm.get("FINAL_OVERRIDE")).isEqualTo("FORCE_CLOSED");
        assertThat(selectedTerm.get("term_scoped")).isEqualTo(true);
        assertThat(fallbackTerm.get("PRELIM_START")).isEqualTo("2026-01-01");
        assertThat(fallbackTerm.get("PRELIM_OVERRIDE")).isEqualTo("FORCE_CLOSED");
    }

    @Test
    void updateSettingsSavesPolicyThresholdsAndTermIncExpirationDate() {
        service.updateSettings(settingsWith(
            "gradingTermId", "2",
            "ACCOUNTING_BLOCK_THRESHOLD", "250.50",
            "ADMISSION_MIN_PAYMENT", "1500.00",
            "DOWNPAYMENT_THRESHOLD", "3500.00",
            "INC_EXPIRATION_DATE", "2027-06-30",
            "PRELIM_START", "2026-06-01",
            "PRELIM_END", "2026-06-30",
            "PRELIM_OVERRIDE", "AUTO",
            "MIDTERM_START", "2026-07-01",
            "MIDTERM_END", "2026-07-31",
            "MIDTERM_OVERRIDE", "AUTO",
            "FINAL_START", "2026-08-01",
            "FINAL_END", "2026-08-31",
            "FINAL_OVERRIDE", "AUTO"
        ));

        Map<String, Object> selectedTerm = service.getGradingWindows(2);

        assertThat(PolicySettings.accountingBlockThreshold(db)).isEqualTo(250.50);
        assertThat(PolicySettings.admissionMinPayment(db)).isEqualTo(1500.00);
        assertThat(PolicySettings.downpaymentThreshold(db)).isEqualTo(3500.00);
        assertThat(selectedTerm.get("INC_EXPIRATION_DATE")).isEqualTo("2027-06-30");
        assertThat(selectedTerm.get("INC_EXPIRATION_SOURCE")).isEqualTo("TERM");
    }

    @Test
    void expireOverdueIncGradesConvertsOnlyDueIncGradesForSelectedTerm() {
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (100, 'UCP2 42', 'Capstone Project 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (200, 100, 2, 'BSIT-4-2-A', 10, 'SUBMITTED')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (201, 100, 1, 'BSIT-4-1-A', 10, 'SUBMITTED')");
        db.update("""
            INSERT INTO grades
                (id, student_id, course_id, section_id, student_name, semestral_grade, remarks, status)
            VALUES
                (300, '2026-0011', 100, 200, 'Smith, Jane', 2.75, 'INC', 'SUBMITTED'),
                (301, '2026-0012', 100, 200, 'Doe, John', 1.75, 'Passed', 'SUBMITTED'),
                (302, '2026-0013', 100, 201, 'Reyes, Clarissa', 2.75, 'INC', 'SUBMITTED')
            """);
        service.updateSettings(settingsWith(
            "gradingTermId", "2",
            "INC_EXPIRATION_DATE", "2026-06-03",
            "PRELIM_START", "2026-01-01",
            "PRELIM_END", "2026-01-31",
            "PRELIM_OVERRIDE", "AUTO",
            "MIDTERM_START", "2026-02-01",
            "MIDTERM_END", "2026-02-28",
            "MIDTERM_OVERRIDE", "AUTO",
            "FINAL_START", "2026-03-01",
            "FINAL_END", "2026-03-31",
            "FINAL_OVERRIDE", "AUTO"
        ));

        int expired = service.expireOverdueIncGrades(2, LocalDate.parse("2026-06-03"));

        assertThat(expired).isEqualTo(1);
        assertThat(db.queryForObject("SELECT remarks FROM grades WHERE id = 300", String.class)).isEqualTo("Failed");
        assertThat(db.queryForObject("SELECT previous_grade FROM grades WHERE id = 300", String.class)).isEqualTo("INC");
        assertThat(db.queryForObject("SELECT semestral_grade FROM grades WHERE id = 300", Double.class)).isEqualTo(5.00);
        assertThat(db.queryForObject("SELECT registrar_final_remarks FROM grades WHERE id = 300", String.class)).isEqualTo("Failed");
        assertThat(db.queryForObject("SELECT grade_lock_status FROM grades WHERE id = 300", String.class)).isEqualTo("LOCKED");
        assertThat(db.queryForObject("SELECT grade_lock_reason FROM grades WHERE id = 300", String.class)).isEqualTo("INC_EXPIRED");
        assertThat(db.queryForObject("SELECT remarks FROM grades WHERE id = 301", String.class)).isEqualTo("Passed");
        assertThat(db.queryForObject("SELECT remarks FROM grades WHERE id = 302", String.class)).isEqualTo("INC");
    }

    @Test
    void expiredIncStaysFailedWhenClassIsRecomputedLater() {
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (100, 'UCP2 42', 'Capstone Project 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (200, 100, 2, 'BSIT-4-2-A', 10, 'SUBMITTED')");
        db.update("""
            INSERT INTO grades
                (id, student_id, course_id, section_id, student_name, prelim, midterm, final_grade, semestral_grade, remarks, status)
            VALUES
                (300, '2026-0011', 100, 200, 'Smith, Jane', 90.00, 90.00, NULL, 1.75, 'INC', 'SUBMITTED')
            """);
        service.updateSettings(settingsWith(
            "gradingTermId", "2",
            "INC_EXPIRATION_DATE", "2026-06-03",
            "PRELIM_START", "2026-01-01",
            "PRELIM_END", "2026-01-31",
            "PRELIM_OVERRIDE", "AUTO",
            "MIDTERM_START", "2026-02-01",
            "MIDTERM_END", "2026-02-28",
            "MIDTERM_OVERRIDE", "AUTO",
            "FINAL_START", "2026-03-01",
            "FINAL_END", "2026-03-31",
            "FINAL_OVERRIDE", "AUTO"
        ));

        service.expireOverdueIncGrades(2, LocalDate.parse("2026-06-03"));
        service.submitClassGrades(200);
        var display = service.getClassGrades(200).get(0);

        assertThat(db.queryForObject("SELECT remarks FROM grades WHERE id = 300", String.class)).isEqualTo("Failed");
        assertThat(db.queryForObject("SELECT semestral_grade FROM grades WHERE id = 300", Double.class)).isEqualTo(5.00);
        assertThat(display.get("remarks")).isEqualTo("Failed");
        assertThat(display.get("semestral_grade")).isEqualTo(5.00);
        assertThat(display.get("grade_lock_status")).isEqualTo("LOCKED");
    }

    @Test
    void expireOverdueIncGradesUsesRegistrarFinalOutcomeBeforeRawRemark() {
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (100, 'UCP2 42', 'Capstone Project 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (200, 100, 2, 'BSIT-4-2-A', 10, 'SUBMITTED')");
        db.update("""
            INSERT INTO grades
                (id, student_id, course_id, section_id, student_name, semestral_grade, remarks,
                 registrar_final_grade, registrar_final_remarks, grade_lock_status, grade_lock_reason, status)
            VALUES
                (300, '2026-0011', 100, 200, 'Smith, Jane', 1.75, 'INC',
                 5.00, 'Failed', 'LOCKED', 'INC_EXPIRED', 'SUBMITTED')
            """);
        service.updateSettings(settingsWith(
            "gradingTermId", "2",
            "INC_EXPIRATION_DATE", "2026-06-03",
            "PRELIM_START", "2026-01-01",
            "PRELIM_END", "2026-01-31",
            "PRELIM_OVERRIDE", "AUTO",
            "MIDTERM_START", "2026-02-01",
            "MIDTERM_END", "2026-02-28",
            "MIDTERM_OVERRIDE", "AUTO",
            "FINAL_START", "2026-03-01",
            "FINAL_END", "2026-03-31",
            "FINAL_OVERRIDE", "AUTO"
        ));

        int expired = service.expireOverdueIncGrades(2, LocalDate.parse("2026-06-03"));

        assertThat(expired).isZero();
        assertThat(db.queryForObject("SELECT registrar_final_remarks FROM grades WHERE id = 300", String.class)).isEqualTo("Failed");
        assertThat(db.queryForObject("SELECT grade_lock_reason FROM grades WHERE id = 300", String.class)).isEqualTo("INC_EXPIRED");
    }

    @Test
    void expireOverdueIncGradesDoesNothingBeforeExpirationDate() {
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (100, 'UCP2 42', 'Capstone Project 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (200, 100, 2, 'BSIT-4-2-A', 10, 'SUBMITTED')");
        db.update("""
            INSERT INTO grades
                (id, student_id, course_id, section_id, student_name, semestral_grade, remarks, status)
            VALUES
                (300, '2026-0011', 100, 200, 'Smith, Jane', 2.75, 'INC', 'SUBMITTED')
            """);
        service.updateSettings(settingsWith(
            "gradingTermId", "2",
            "INC_EXPIRATION_DATE", "2026-06-04",
            "PRELIM_START", "2026-01-01",
            "PRELIM_END", "2026-01-31",
            "PRELIM_OVERRIDE", "AUTO",
            "MIDTERM_START", "2026-02-01",
            "MIDTERM_END", "2026-02-28",
            "MIDTERM_OVERRIDE", "AUTO",
            "FINAL_START", "2026-03-01",
            "FINAL_END", "2026-03-31",
            "FINAL_OVERRIDE", "AUTO"
        ));

        int expired = service.expireOverdueIncGrades(2, LocalDate.parse("2026-06-03"));

        assertThat(expired).isZero();
        assertThat(db.queryForObject("SELECT remarks FROM grades WHERE id = 300", String.class)).isEqualTo("INC");
    }

    @Test
    void updateSettingsWithoutGradingTermIdKeepsLegacyGlobalBehavior() {
        service.updateSettings(Map.of(
            "PRELIM_START", "2026-03-01",
            "PRELIM_END", "2026-03-31",
            "PRELIM_OVERRIDE", "FORCE_OPEN"
        ));

        Map<String, Object> windows = service.getGradingWindows(1);

        assertThat(windows.get("PRELIM_START")).isEqualTo("2026-03-01");
        assertThat(windows.get("PRELIM_END")).isEqualTo("2026-03-31");
        assertThat(windows.get("PRELIM_OVERRIDE")).isEqualTo("FORCE_OPEN");
    }

    @Test
    void getFacultyClassesForUserResolvesSysUserToCanonicalFacultyByEmployeeNumber() {
        db.update("INSERT INTO faculty (faculty_id, employee_number, first_name, last_name, email) VALUES (10, 'prof', 'Professor', 'Demo', 'prof@school.edu.ph')");
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (100, 'UCP2 42', 'Capstone Project 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (200, 100, 2, 'BSIT-4-2-A', 10, 'Open')");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time) VALUES (200, 1, '07:30:00', '09:00:00')");

        var classes = service.getFacultyClassesForUser(Map.of(
            "user_id", 99,
            "username", "prof",
            "real_name", "Professor Demo",
            "role", "Faculty"
        ));

        assertThat(classes).hasSize(1);
        assertThat(classes.get(0).get("schedule_id")).isEqualTo(200);
        assertThat(classes.get(0).get("section")).isEqualTo("BSIT-4-2-A");
        assertThat(classes.get(0).get("course_code")).isEqualTo("UCP2 42");
        assertThat(classes.get(0).get("pretty_schedule")).isEqualTo("MON 07:30-09:00");
    }

    @Test
    void getClassGradesReadsCanonicalGradesTableColumns() {
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (100, 'UCP2 42', 'Capstone Project 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (200, 100, 2, 'BSIT-4-2-A', 10, 'Open')");
        db.update("""
            INSERT INTO grades
                (id, student_id, course_id, section_id, student_name, prelim, midterm, final_grade, semestral_grade, remarks, status)
            VALUES
                (300, '2026-0011', 100, 200, 'Smith, Jane', 88.00, NULL, NULL, NULL, NULL, 'DRAFT')
            """);

        var grades = service.getClassGrades(200);

        assertThat(grades).hasSize(1);
        assertThat(grades.get(0).get("grade_id")).isEqualTo(300);
        assertThat(grades.get(0).get("student_name")).isEqualTo("Smith, Jane");
        assertThat(grades.get(0).get("course_code")).isEqualTo("UCP2 42");
        assertThat(grades.get(0).get("prelim")).isEqualTo(88.0);
        assertThat(grades.get(0).get("remarks")).isEqualTo("Ongoing");
        assertThat(grades.get(0).get("status")).isEqualTo("DRAFT");
    }

    @Test
    void getPendingClassSubmissionsReadsCanonicalSectionsWithScheduleText() {
        db.update("INSERT INTO faculty (faculty_id, employee_number, first_name, last_name, email) VALUES (10, 'prof', 'Professor', 'Demo', 'prof@school.edu.ph')");
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (100, 'UCP2 42', 'Capstone Project 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (200, 100, 2, 'BSIT-4-2-A', 10, 'PENDING_APPROVAL')");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time) VALUES (200, 1, '07:30:00', '09:00:00')");

        var pending = service.getPendingClassSubmissions();

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).get("schedule_id")).isEqualTo(200);
        assertThat(pending.get(0).get("faculty_name")).isEqualTo("Professor Demo");
        assertThat(pending.get(0).get("course_code")).isEqualTo("UCP2 42");
        assertThat(pending.get(0).get("pretty_schedule")).isEqualTo("MON 07:30-09:00");
    }

    @Test
    void finalizeClassGradesPostsCanonicalSectionAndKeepsGradesSubmitted() {
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (100, 'UCP2 42', 'Capstone Project 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (200, 100, 2, 'BSIT-4-2-A', 10, 'PENDING_APPROVAL')");
        db.update("""
            INSERT INTO grades
                (id, student_id, course_id, section_id, student_name, prelim, midterm, status)
            VALUES
                (300, '2026-0011', 100, 200, 'Smith, Jane', 90.00, 88.00, 'SUBMITTED')
            """);

        service.finalizeClassGrades(200);

        assertThat(db.queryForObject(
            "SELECT section_status FROM class_sections WHERE section_id = 200",
            String.class)).isEqualTo("SUBMITTED");
        assertThat(db.queryForObject(
            "SELECT status FROM grades WHERE id = 300",
            String.class)).isEqualTo("SUBMITTED");
        assertThat(service.getPendingClassSubmissions()).isEmpty();
    }

    @Test
    void submitClassGradesTagsMissingPeriodAsIncAndMovesClassToPendingApproval() {
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (100, 'UCP2 42', 'Capstone Project 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (200, 100, 2, 'BSIT-4-2-A', 10, 'Open')");
        db.update("""
            INSERT INTO grades
                (id, student_id, course_id, section_id, student_name, prelim, midterm, final_grade, status)
            VALUES
                (300, '2026-0011', 100, 200, 'Smith, Jane', 90.00, 68.00, NULL, 'DRAFT')
            """);

        service.submitClassGrades(200);

        assertThat(db.queryForObject(
            "SELECT section_status FROM class_sections WHERE section_id = 200",
            String.class)).isEqualTo("PENDING_APPROVAL");
        assertThat(db.queryForObject(
            "SELECT status FROM grades WHERE id = 300",
            String.class)).isEqualTo("SUBMITTED");
        assertThat(db.queryForObject(
            "SELECT remarks FROM grades WHERE id = 300",
            String.class)).isEqualTo("INC");
        assertThat(db.queryForObject(
            "SELECT semestral_grade FROM grades WHERE id = 300",
            Double.class)).isEqualTo(2.75);
    }

    @Test
    void requestGradeChangeUsesCanonicalGradesTable() {
        db.update("INSERT INTO sys_users (user_id, username, real_name, role) VALUES (99, 'prof', 'Professor Demo', 'Faculty')");
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (100, 'UCP2 42', 'Capstone Project 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (200, 100, 2, 'BSIT-4-2-A', 10, 'SUBMITTED')");
        db.update("""
            INSERT INTO grades
                (id, student_id, course_id, section_id, student_name, prelim, midterm, final_grade, semestral_grade, remarks, status)
            VALUES
                (300, '2026-0011', 100, 200, 'Smith, Jane', 90.00, 68.00, NULL, 2.75, 'INC', 'SUBMITTED')
            """);

        service.requestGradeChange(300, "2.00", "Completion submitted.", 99);

        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM grade_change_requests WHERE grade_id = 300 AND student_name = 'Smith, Jane' " +
                "AND course_code = 'UCP2 42' AND faculty_name = 'Professor Demo' AND requested_grade = '2.00' " +
                "AND reason = 'Completion submitted.' AND status = 'PENDING'",
            Integer.class)).isEqualTo(1);
        assertThat(service.getGradeChangeRequests()).hasSize(1);
        assertThat(service.getClassGrades(200).get(0).get("pending_change")).isEqualTo(1);
    }

    @Test
    void requestComponentGradeChangeStoresRequestedComponents() {
        db.update("INSERT INTO sys_users (user_id, username, real_name, role) VALUES (99, 'prof', 'Professor Demo', 'Faculty')");
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (100, 'UCP2 42', 'Capstone Project 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (200, 100, 2, 'BSIT-4-2-A', 10, 'SUBMITTED')");
        db.update("""
            INSERT INTO grades
                (id, student_id, course_id, section_id, student_name, prelim, midterm, final_grade, semestral_grade, remarks, status)
            VALUES
                (300, '2026-0011', 100, 200, 'Smith, Jane', 90.00, 68.00, NULL, 2.75, 'INC', 'SUBMITTED')
            """);

        service.requestGradeChange(300, "COMPONENT_GRADE_CORRECTION", null, "90", "90", "90", "Corrected finals submitted.", 99);
        Map<String, Object> request = service.getGradeChangeRequests().get(0);

        assertThat(request.get("request_type")).isEqualTo("COMPONENT_GRADE_CORRECTION");
        assertThat(request.get("request_label")).isEqualTo("Component Correction");
        assertThat(request.get("requested_summary")).isEqualTo("P: 90.0 / M: 90.0 / F: 90.0");
        assertThat(db.queryForObject(
            "SELECT requested_finals FROM grade_change_requests WHERE grade_id = 300",
            Double.class)).isEqualTo(90.00);
    }

    @Test
    void approveGradeChangeLocksRegistrarFinalOutcome() {
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (100, 'UCP2 42', 'Capstone Project 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (200, 100, 2, 'BSIT-4-2-A', 10, 'SUBMITTED')");
        db.update("""
            INSERT INTO grades
                (id, student_id, course_id, section_id, student_name, prelim, midterm, final_grade, semestral_grade, remarks, status)
            VALUES
                (300, '2026-0011', 100, 200, 'Smith, Jane', 90.00, 68.00, NULL, 2.75, 'INC', 'SUBMITTED')
            """);
        db.update("""
            INSERT INTO grade_change_requests
                (request_id, grade_id, student_name, course_code, faculty_name, requested_grade, reason, status)
            VALUES
                (400, 300, 'Smith, Jane', 'UCP2 42', 'Professor Demo', '2.00', 'Completion submitted.', 'PENDING')
            """);

        service.approveGradeChange(400);

        assertThat(db.queryForObject(
            "SELECT status FROM grades WHERE id = 300",
            String.class)).isEqualTo("SUBMITTED");
        assertThat(db.queryForObject(
            "SELECT previous_grade FROM grades WHERE id = 300",
            String.class)).isEqualTo("INC");
        assertThat(db.queryForObject(
            "SELECT registrar_final_grade FROM grades WHERE id = 300",
            Double.class)).isEqualTo(2.00);
        assertThat(db.queryForObject(
            "SELECT registrar_final_remarks FROM grades WHERE id = 300",
            String.class)).isEqualTo("Passed");
        assertThat(db.queryForObject(
            "SELECT grade_lock_status FROM grades WHERE id = 300",
            String.class)).isEqualTo("LOCKED");
        assertThat(db.queryForObject(
            "SELECT status FROM grade_change_requests WHERE request_id = 400",
            String.class)).isEqualTo("APPROVED");
    }

    @Test
    void approvedGradeChangeStaysFinalWhenRawFinalsAreCleared() {
        db.update("INSERT INTO sys_users (user_id, username, real_name, role) VALUES (99, 'prof', 'Professor Demo', 'Faculty')");
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (100, 'UCP2 42', 'Capstone Project 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (200, 100, 2, 'BSIT-4-2-A', 10, 'SUBMITTED')");
        db.update("""
            INSERT INTO grades
                (id, student_id, course_id, section_id, student_name, prelim, midterm, final_grade, semestral_grade, remarks, status)
            VALUES
                (300, '2026-0011', 100, 200, 'Smith, Jane', 90.00, 68.00, 90.00, 2.50, 'Passed', 'SUBMITTED')
            """);
        db.update("""
            INSERT INTO grade_change_requests
                (request_id, grade_id, student_name, course_code, faculty_name, requested_grade, reason, status)
            VALUES
                (400, 300, 'Smith, Jane', 'UCP2 42', 'Professor Demo', '2.00', 'Registrar correction.', 'PENDING')
            """);

        service.approveGradeChange(400);
        service.saveGradeAsync(300, "90", "68", "");
        service.submitClassGrades(200);
        var display = service.getClassGrades(200).get(0);

        assertThat(db.queryForObject("SELECT remarks FROM grades WHERE id = 300", String.class)).isEqualTo("Passed");
        assertThat(db.queryForObject("SELECT semestral_grade FROM grades WHERE id = 300", Double.class)).isEqualTo(2.00);
        assertThat(display.get("remarks")).isEqualTo("Passed");
        assertThat(display.get("semestral_grade")).isEqualTo(2.00);
        assertThat(display.get("grade_lock_status")).isEqualTo("LOCKED");
    }

    @Test
    void approveComponentGradeChangeAppliesComponentsAndLocksOutcome() {
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (100, 'UCP2 42', 'Capstone Project 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (200, 100, 2, 'BSIT-4-2-A', 10, 'SUBMITTED')");
        db.update("""
            INSERT INTO grades
                (id, student_id, course_id, section_id, student_name, prelim, midterm, final_grade, semestral_grade, remarks, status)
            VALUES
                (300, '2026-0011', 100, 200, 'Smith, Jane', 90.00, 68.00, NULL, 2.75, 'INC', 'SUBMITTED')
            """);
        db.update("""
            INSERT INTO grade_change_requests
                (request_id, grade_id, student_name, course_code, faculty_name, request_type, requested_prelim, requested_midterm, requested_finals, reason, status)
            VALUES
                (400, 300, 'Smith, Jane', 'UCP2 42', 'Professor Demo', 'COMPONENT_GRADE_CORRECTION', 90.00, 90.00, 90.00, 'Corrected component scores.', 'PENDING')
            """);

        service.approveGradeChange(400);
        Map<String, Object> display = service.getClassGrades(200).get(0);

        assertThat(db.queryForObject("SELECT final_grade FROM grades WHERE id = 300", Double.class)).isEqualTo(90.00);
        assertThat(db.queryForObject("SELECT registrar_final_grade FROM grades WHERE id = 300", Double.class)).isEqualTo(1.75);
        assertThat(db.queryForObject("SELECT registrar_final_remarks FROM grades WHERE id = 300", String.class)).isEqualTo("Passed");
        assertThat(db.queryForObject("SELECT grade_lock_reason FROM grades WHERE id = 300", String.class)).isEqualTo("COMPONENT_GRADE_CHANGE_APPROVED");
        assertThat(display.get("remarks")).isEqualTo("Passed");
        assertThat(display.get("semestral_grade")).isEqualTo(1.75);
    }

    @Test
    void approveReopenForEditUnlocksRegistrarFinalOutcomeForFacultyCorrection() {
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (100, 'UCP2 42', 'Capstone Project 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, faculty_id, section_status) VALUES (200, 100, 2, 'BSIT-4-2-A', 10, 'SUBMITTED')");
        db.update("""
            INSERT INTO grades
                (id, student_id, course_id, section_id, student_name, prelim, midterm, final_grade, semestral_grade, remarks, status,
                 registrar_final_grade, registrar_final_remarks, grade_lock_status, grade_lock_reason)
            VALUES
                (300, '2026-0011', 100, 200, 'Smith, Jane', 90.00, 68.00, NULL, 5.00, 'Failed', 'SUBMITTED',
                 5.00, 'Failed', 'LOCKED', 'INC_EXPIRED')
            """);
        db.update("""
            INSERT INTO grade_change_requests
                (request_id, grade_id, student_name, course_code, faculty_name, request_type, reason, status)
            VALUES
                (400, 300, 'Smith, Jane', 'UCP2 42', 'Professor Demo', 'REOPEN_FOR_EDIT', 'Needs component review.', 'PENDING')
            """);

        service.approveGradeChange(400);

        assertThat(db.queryForObject("SELECT registrar_final_grade FROM grades WHERE id = 300", Double.class)).isNull();
        assertThat(db.queryForObject("SELECT registrar_final_remarks FROM grades WHERE id = 300", String.class)).isNull();
        assertThat(db.queryForObject("SELECT grade_lock_status FROM grades WHERE id = 300", String.class)).isNull();
        assertThat(db.queryForObject("SELECT grade_lock_reason FROM grades WHERE id = 300", String.class)).isEqualTo("REOPENED_FOR_EDIT");
        assertThat(db.queryForObject("SELECT status FROM grades WHERE id = 300", String.class)).isEqualTo("DRAFT");
    }

    private void seedGlobalSettings() {
        db.update("INSERT INTO system_settings (setting_key, setting_value) VALUES ('PRELIM_START', '2026-01-01')");
        db.update("INSERT INTO system_settings (setting_key, setting_value) VALUES ('PRELIM_END', '2026-01-31')");
        db.update("INSERT INTO system_settings (setting_key, setting_value) VALUES ('MIDTERM_START', '2026-02-01')");
        db.update("INSERT INTO system_settings (setting_key, setting_value) VALUES ('MIDTERM_END', '2026-02-28')");
        db.update("INSERT INTO system_settings (setting_key, setting_value) VALUES ('FINAL_START', '2026-03-01')");
        db.update("INSERT INTO system_settings (setting_key, setting_value) VALUES ('FINAL_END', '2026-03-31')");
        db.update("INSERT INTO system_settings (setting_key, setting_value) VALUES ('PRELIM_OVERRIDE', 'FORCE_CLOSED')");
        db.update("INSERT INTO system_settings (setting_key, setting_value) VALUES ('MIDTERM_OVERRIDE', 'AUTO')");
        db.update("INSERT INTO system_settings (setting_key, setting_value) VALUES ('FINAL_OVERRIDE', 'AUTO')");
    }

    private Map<String, String> settingsWith(String... pairs) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return values;
    }
}



