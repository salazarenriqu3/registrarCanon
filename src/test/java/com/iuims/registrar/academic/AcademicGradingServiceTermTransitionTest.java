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
class AcademicGradingServiceTermTransitionTest {

    @MockBean
    private StudentDocumentTrailService studentDocumentTrailService;

    @Autowired
    private JdbcTemplate db;
    
    @Autowired
    private AcademicGradingService service;
    
    @Autowired
    private TermFeeAdminService termFeeAdminService;

    @BeforeEach
    void setUp() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS academic_terms (
                term_id INT AUTO_INCREMENT PRIMARY KEY,
                term_code VARCHAR(20) NULL,
                term_name VARCHAR(100) NULL,
                academic_year VARCHAR(20) NULL,
                semester_number INT NULL,
                start_date DATE NULL,
                end_date DATE NULL,
                is_active TINYINT NOT NULL DEFAULT 0,
                status VARCHAR(20) NULL
            )
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS system_settings (
                setting_key VARCHAR(100) PRIMARY KEY,
                setting_value VARCHAR(100) NULL
            )
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS sys_users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NULL,
                real_name VARCHAR(100) NULL,
                role VARCHAR(50) NULL,
                password VARCHAR(100) NULL,
                program_code VARCHAR(50) NULL,
                is_active TINYINT NOT NULL DEFAULT 1,
                admission_status VARCHAR(50) NULL,
                semester INT NULL,
                year_level INT NULL,
                term_year VARCHAR(50) NULL,
                student_type VARCHAR(50) NULL,
                enrollment_start_time TIMESTAMP NULL,
                granted_permissions VARCHAR(255) NULL
            )
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS grading_term_windows (
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
            CREATE TABLE IF NOT EXISTS term_transition_audit (
                audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                requested_term_code VARCHAR(32) NULL,
                target_db_term_code VARCHAR(32) NULL,
                target_term_id INT NULL,
                success TINYINT NOT NULL DEFAULT 0,
                advanced_count INT NOT NULL DEFAULT 0,
                forwarded_debt_count INT NOT NULL DEFAULT 0,
                error_message VARCHAR(500) NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS programs (
                program_id INT AUTO_INCREMENT PRIMARY KEY,
                program_code VARCHAR(20) NOT NULL,
                program_name VARCHAR(100) NULL,
                active_status TINYINT NOT NULL DEFAULT 1
            )
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS curriculum_templates (
                curriculum_id INT AUTO_INCREMENT PRIMARY KEY,
                program_id INT NOT NULL,
                curriculum_name VARCHAR(100) NULL,
                is_active TINYINT NOT NULL DEFAULT 0
            )
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS curriculum_courses (
                curriculum_course_id INT AUTO_INCREMENT PRIMARY KEY,
                curriculum_id INT NULL,
                course_id INT NULL,
                year_level INT NULL,
                semester_number INT NULL
            )
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS program_fee_settings (
                fee_setting_id INT AUTO_INCREMENT PRIMARY KEY,
                program_id INT NOT NULL,
                term_id INT NULL,
                year_level INT NULL,
                semester_number INT NULL,
                fee_tuition_per_unit DECIMAL(10,2) DEFAULT 0.00,
                fee_lec_per_unit DECIMAL(10,2) DEFAULT 0.00,
                fee_lab_per_unit DECIMAL(10,2) DEFAULT 0.00,
                fee_comp_per_unit DECIMAL(10,2) DEFAULT 0.00,
                fee_rle_per_unit DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_registration DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_library DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_medical DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_id DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_athletic DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_guidance DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_lms DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_insurance DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_cultural DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_av DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_energy DECIMAL(10,2) DEFAULT 0.00,
                fee_other_late_enrollment DECIMAL(10,2) DEFAULT 0.00,
                fee_other_add_drop DECIMAL(10,2) DEFAULT 0.00,
                fee_other_installment DECIMAL(10,2) DEFAULT 0.00,
                fee_other_id DECIMAL(10,2) DEFAULT 0.00,
                fee_other_insurance DECIMAL(10,2) DEFAULT 0.00,
                fee_other_comp DECIMAL(10,2) DEFAULT 0.00,
                fee_other_dev DECIMAL(10,2) DEFAULT 0.00,
                is_active TINYINT NOT NULL DEFAULT 1
            )
            """);

    }

    @Test
    void missingTargetTermDoesNotChangeCurrentTermOrActiveTerm() {
        db.update("INSERT INTO academic_terms (term_code, academic_year, semester_number, is_active, status) VALUES ('1120242025', '2024-2025', 1, 1, 'ACTIVE')");
        db.update("INSERT INTO system_settings (setting_key, setting_value) VALUES ('CURRENT_ACADEMIC_TERM', '1120242025')");

        AcademicGradingService.TermTransitionResult result = service.triggerTermTransition("1120252026");

        assertThat(result.success()).isFalse();
        assertThat(db.queryForObject(
            "SELECT setting_value FROM system_settings WHERE setting_key = 'CURRENT_ACADEMIC_TERM'",
            String.class)).isEqualTo("1120242025");
        assertThat(db.queryForObject(
            "SELECT is_active FROM academic_terms WHERE term_code = '1120242025'",
            Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM term_transition_audit WHERE success = 0 AND requested_term_code = '1120252026' " +
                "AND target_db_term_code = '1120252026' AND error_message LIKE 'Target academic term%'",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void validTargetTermActivatesTermBeforeUpdatingCurrentTerm() {
        db.update("INSERT INTO academic_terms (term_code, academic_year, semester_number, is_active, status) VALUES ('1120242025', '2024-2025', 1, 1, 'ACTIVE')");
        db.update("INSERT INTO academic_terms (term_code, academic_year, semester_number, is_active, status) VALUES ('2120242025', '2024-2025', 2, 0, 'INACTIVE')");
        db.update("INSERT INTO system_settings (setting_key, setting_value) VALUES ('CURRENT_ACADEMIC_TERM', '1120242025')");
        seedReadyProgramForTerm(2);

        AcademicGradingService.TermTransitionResult result = service.triggerTermTransition("2120242025");

        assertThat(result.success()).isTrue();
        assertThat(db.queryForObject(
            "SELECT setting_value FROM system_settings WHERE setting_key = 'CURRENT_ACADEMIC_TERM'",
            String.class)).isEqualTo("2120242025");
        assertThat(db.queryForObject(
            "SELECT is_active FROM academic_terms WHERE term_code = '2120242025'",
            Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT status FROM academic_terms WHERE term_code = '1120242025'",
            String.class)).isEqualTo("INACTIVE");
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM term_transition_audit WHERE success = 1 AND requested_term_code = '2120242025' " +
                "AND target_db_term_code = '2120242025' AND target_term_id = 2 AND advanced_count = 0 AND forwarded_debt_count = 0",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void unreadyTargetTermDoesNotChangeCurrentTermOrActiveTerm() {
        db.update("INSERT INTO academic_terms (term_code, academic_year, semester_number, is_active, status) VALUES ('1120242025', '2024-2025', 1, 1, 'ACTIVE')");
        db.update("INSERT INTO academic_terms (term_code, academic_year, semester_number, is_active, status) VALUES ('2120242025', '2024-2025', 2, 0, 'INACTIVE')");
        db.update("INSERT INTO system_settings (setting_key, setting_value) VALUES ('CURRENT_ACADEMIC_TERM', '1120242025')");
        db.update("INSERT INTO programs (program_id, program_code, program_name, active_status) VALUES (1, 'BSIT', 'BSIT Program', 1)");

        AcademicGradingService.TermTransitionResult result = service.triggerTermTransition("2120242025");

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Target term is not ready");
        assertThat(db.queryForObject(
            "SELECT setting_value FROM system_settings WHERE setting_key = 'CURRENT_ACADEMIC_TERM'",
            String.class)).isEqualTo("1120242025");
        assertThat(db.queryForObject(
            "SELECT is_active FROM academic_terms WHERE term_code = '1120242025'",
            Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT is_active FROM academic_terms WHERE term_code = '2120242025'",
            Integer.class)).isEqualTo(0);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM term_transition_audit WHERE success = 0 AND requested_term_code = '2120242025' " +
                "AND target_db_term_code = '2120242025' AND error_message LIKE 'Target term is not ready%'",
            Integer.class)).isEqualTo(1);
    }

    private void seedReadyProgramForTerm(int termId) {
        db.update("INSERT INTO programs (program_id, program_code, program_name, active_status) VALUES (1, 'BSIT', 'BSIT Program', 1)");
        db.update("INSERT INTO curriculum_templates (program_id, curriculum_name, is_active) VALUES (1, 'Active Curriculum', 1)");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number) " +
            "SELECT curriculum_id, 100, 1, 1 FROM curriculum_templates WHERE program_id = 1 AND is_active = 1");
        for (int yearLevel = 1; yearLevel <= 4; yearLevel++) {
            for (int semester = 1; semester <= 2; semester++) {
                db.update(
                    "INSERT INTO program_fee_settings " +
                        "(program_id, term_id, year_level, semester_number, fee_tuition_per_unit, fee_rle_per_unit, is_active) " +
                        "VALUES (1, ?, ?, ?, 800.00, 75.00, 1)",
                    termId, yearLevel, semester);
            }
        }
    }
}



