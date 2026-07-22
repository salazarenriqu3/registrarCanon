package com.iuims.registrar.service.support;
import com.iuims.registrar.entity.Grade;
import com.iuims.registrar.entity.Program;
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

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseSetupService {

    private static final String REGISTRAR_COLLATION = "utf8mb4_unicode_ci";

    @Autowired
    private JdbcTemplate db;

    @Value("${registrar.bootstrap.reference-data.enabled:false}")
    private boolean referenceDataBootstrapEnabled;

    @Value("${registrar.bootstrap.legacy-migration.enabled:false}")
    private boolean legacyMigrationBootstrapEnabled;

    @PostConstruct
    public void initDatabase() {
        try {
            // MySQL reference scripts (schema vs seed split): db/01_schema_eacdb_unified.sql, db/02_seed_eacdb_test_data.sql
            // 1. CORE SYSTEM TABLES
            db.execute("CREATE TABLE IF NOT EXISTS system_settings (setting_key VARCHAR(50) PRIMARY KEY, setting_value VARCHAR(100))");
            if (referenceDataBootstrapEnabled) {
                seedDefaultSetting(PolicySettings.ACCOUNTING_BLOCK_THRESHOLD, "100.0");
                seedDefaultSetting(PolicySettings.ADMISSION_MIN_PAYMENT, "1000.0");
                seedDefaultSetting(PolicySettings.DOWNPAYMENT_THRESHOLD, "3000.0");
                seedDefaultSetting(PolicySettings.DOWNPAYMENT_PERCENT, "0");
                seedDefaultSetting(PolicySettings.SCHOLARSHIP_MAX_GWA, "1.75");
                seedDefaultSetting(PolicySettings.SCHOLARSHIP_MAX_PRELIM_GRADE, "2.00");
                seedDefaultSetting(PolicySettings.SCHOLARSHIP_MAX_MIDTERM_GRADE, "2.00");
                seedDefaultSetting(PolicySettings.SCHOLARSHIP_MAX_FINALS_GRADE, "2.00");
                seedDefaultSetting(PolicySettings.SCHOLARSHIP_DEFAULT_DISCOUNT_PERCENT, "100.0");
                seedDefaultSetting(PolicySettings.SCHOLARSHIP_MIN_COMPLETED_UNITS, "27");
                seedDefaultSetting(PolicySettings.SCHOLARSHIP_DISQUALIFY_INC, "true");
                seedDefaultSetting(PolicySettings.SCHOLARSHIP_DISQUALIFY_FAILED, "true");
            }
            ensureScholarshipTypeCatalog();
            ensureScholarshipReviewWorkflow();
            db.execute("CREATE TABLE IF NOT EXISTS grading_term_windows (window_id BIGINT AUTO_INCREMENT PRIMARY KEY, term_id INT NOT NULL, grading_period VARCHAR(20) NOT NULL, start_date DATE NULL, end_date DATE NULL, override_status VARCHAR(20) NOT NULL DEFAULT 'AUTO', updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uq_gtw_term_period (term_id, grading_period), KEY idx_gtw_term (term_id))");
            db.execute("CREATE TABLE IF NOT EXISTS academic_term_policies (term_id INT PRIMARY KEY, inc_expiration_date DATE NULL, midterm_exam_date DATE NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            tryExecute("ALTER TABLE academic_term_policies ADD COLUMN IF NOT EXISTS midterm_exam_date DATE NULL");
            ensureGradeOutcomeColumns();
            ensureAuditLogs();
            
            // 2. CORE USER TABLE (must exist before any ALTER or INSERT references it)
            db.execute("CREATE TABLE IF NOT EXISTS sys_users (" +
                "user_id INT AUTO_INCREMENT PRIMARY KEY, " +
                "username VARCHAR(50) UNIQUE, " +
                "password VARCHAR(255), " +
                "real_name VARCHAR(100), " +
                "role VARCHAR(30), " +
                "program_code VARCHAR(20), " +
                "year_level INT DEFAULT 1, " +
                "semester INT DEFAULT 1, " +
                "is_active TINYINT(1) DEFAULT 1, " +
                "granted_permissions TEXT, " +
                "admission_status VARCHAR(50), " +
                "admission_date DATETIME" +
                ")");

            // 3. LEGACY ACADEMIC TABLES (For Grading & VPAA)
            db.execute("CREATE TABLE IF NOT EXISTS curriculum_catalog (course_code VARCHAR(20) PRIMARY KEY, description VARCHAR(150), units INT DEFAULT 3)");
            db.execute("CREATE TABLE IF NOT EXISTS class_schedules (schedule_id INT AUTO_INCREMENT PRIMARY KEY, section_id INT NULL, course_code VARCHAR(20), section VARCHAR(20), faculty_id INT NULL, day_of_week INT NULL, start_time TIME, end_time TIME, room_id INT NULL, schedule_type VARCHAR(30) NULL, status VARCHAR(50) DEFAULT 'OPEN', is_unlocked TINYINT(1) DEFAULT 0)");
            if (!objectExists("student_grades")) {
                db.execute("CREATE TABLE student_grades (grade_id INT AUTO_INCREMENT PRIMARY KEY, schedule_id INT, student_name VARCHAR(100), student_id INT, prelim VARCHAR(10), midterm VARCHAR(10), final_grade VARCHAR(10), status VARCHAR(50) DEFAULT 'DRAFT')");
                try { db.execute("ALTER TABLE student_grades MODIFY COLUMN status VARCHAR(50) DEFAULT 'DRAFT'"); } catch (Exception e) {}
            }
            try { db.execute("ALTER TABLE class_schedules MODIFY COLUMN status VARCHAR(50) DEFAULT 'OPEN'"); } catch (Exception e) {}
            // Add semester column to sys_users if it doesn't exist yet
            try { db.execute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS semester INT DEFAULT 1"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS first_name VARCHAR(100) NULL"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS middle_name VARCHAR(100) NULL"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS last_name VARCHAR(100) NULL"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS email VARCHAR(150) NULL"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS mobile VARCHAR(50) NULL"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS term_year VARCHAR(50) NULL"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS student_type VARCHAR(50) NULL"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS enrollment_status_type VARCHAR(50) NULL"); } catch (Exception e) {}
            db.execute("CREATE TABLE IF NOT EXISTS vpaa_extensions (ext_id INT AUTO_INCREMENT PRIMARY KEY, schedule_id INT, faculty_id INT, status VARCHAR(50) DEFAULT 'PENDING', reason VARCHAR(255))");
            db.execute("CREATE TABLE IF NOT EXISTS grade_change_requests (" +
                "request_id INT AUTO_INCREMENT PRIMARY KEY, grade_id BIGINT NULL, student_name VARCHAR(100) NULL, " +
                "course_code VARCHAR(20) NULL, faculty_name VARCHAR(100) NULL, requested_grade VARCHAR(20) NULL, " +
                "reason TEXT NULL, status VARCHAR(30) DEFAULT 'PENDING', request_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            ensureGradeChangeRequestColumns();
            
            // 3. FINANCE & ADMISSIONS TABLES
            db.execute("CREATE TABLE IF NOT EXISTS student_ledger (ledger_id INT AUTO_INCREMENT PRIMARY KEY, student_id INT, transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, transaction_type VARCHAR(20), description VARCHAR(255), debit DECIMAL(10,2) DEFAULT 0.00, credit DECIMAL(10,2) DEFAULT 0.00)");
            db.execute("CREATE TABLE IF NOT EXISTS admission_applications (applicant_id VARCHAR(50) PRIMARY KEY, full_name VARCHAR(100), status VARCHAR(50) DEFAULT 'PENDING')");
            db.execute("CREATE TABLE IF NOT EXISTS applicant_payments (payment_id INT AUTO_INCREMENT PRIMARY KEY, applicant_id VARCHAR(50) NOT NULL, payment_amount DECIMAL(10,2) NOT NULL, payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, status VARCHAR(20) DEFAULT 'UNPROCESSED')");

            // 3b. EAC APPLICANTS â€” recreate only if missing
            db.execute(
                "CREATE TABLE IF NOT EXISTS applicants (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  reference_number VARCHAR(50) UNIQUE," +
                "  applicant_status VARCHAR(50) DEFAULT 'SUBMITTED'," +
                "  term_year VARCHAR(30)," +
                // -- Personal --
                "  first_name VARCHAR(100)," +
                "  last_name VARCHAR(100)," +
                "  middle_name VARCHAR(100)," +
                "  middle_initial VARCHAR(10)," +
                "  middle_name_na TINYINT(1) DEFAULT 0," +
                "  extension VARCHAR(20)," +
                "  sex VARCHAR(10)," +
                "  dob VARCHAR(20)," +
                "  place_of_birth TEXT," +
                "  civil_status VARCHAR(30)," +
                "  religion VARCHAR(60)," +
                "  nationality VARCHAR(60)," +
                "  citizenship VARCHAR(60)," +
                "  age INT," +
                "  four_ps TINYINT(1) DEFAULT 0," +
                "  indigenous TINYINT(1) DEFAULT 0," +
                "  international_student TINYINT(1) DEFAULT 0," +
                // -- Contact --
                "  email VARCHAR(150)," +
                "  email_verified TINYINT(1) DEFAULT 0," +
                "  mobile VARCHAR(30)," +
                "  landline VARCHAR(30)," +
                // -- Address --
                "  street TEXT," +
                "  city VARCHAR(100)," +
                "  province VARCHAR(100)," +
                "  zip VARCHAR(10)," +
                // -- Emergency --
                "  emergency_contact_name VARCHAR(150)," +
                "  emergency_contact_mobile VARCHAR(30)," +
                "  emergency_contact_relationship VARCHAR(60)," +
                // -- Family --
                "  father_name VARCHAR(150)," +
                "  father_occupation VARCHAR(100)," +
                "  father_contact VARCHAR(30)," +
                "  father_address TEXT," +
                "  mother_name VARCHAR(150)," +
                "  mother_occupation VARCHAR(100)," +
                "  mother_contact VARCHAR(30)," +
                "  mother_address TEXT," +
                "  guardian_name VARCHAR(150)," +
                "  guardian_contact VARCHAR(30)," +
                "  guardian_relationship VARCHAR(60)," +
                "  sibling_count INT," +
                "  sibling_order VARCHAR(30)," +
                "  monthly_income VARCHAR(30)," +
                // -- Education --
                "  academic_level VARCHAR(30)," +
                "  elementary_school TEXT," +
                "  elementary_address TEXT," +
                "  elementary_year VARCHAR(20)," +
                "  jhs_school TEXT," +
                "  jhs_address TEXT," +
                "  jhs_year VARCHAR(20)," +
                "  shs_school TEXT," +
                "  shs_address TEXT," +
                "  shs_track VARCHAR(60)," +
                "  shs_year VARCHAR(20)," +
                "  last_school TEXT," +
                "  last_school_year VARCHAR(20)," +
                "  course_taken VARCHAR(100)," +
                // -- Program Choices --
                "  program1 VARCHAR(20)," +
                "  program2 VARCHAR(20)," +
                // -- Documents --
                "  form138_path VARCHAR(255)," +
                "  form138_verified TINYINT(1) DEFAULT 0," +
                "  good_moral_path VARCHAR(255)," +
                "  good_moral_verified TINYINT(1) DEFAULT 0," +
                "  psa_birth_cert_path VARCHAR(255)," +
                "  psa_birth_cert_verified TINYINT(1) DEFAULT 0," +
                "  id_picture_path VARCHAR(255)," +
                "  id_picture_verified TINYINT(1) DEFAULT 0," +
                "  marriage_cert_path VARCHAR(255)," +
                "  marriage_cert_verified TINYINT(1) DEFAULT 0," +
                "  other_doc_path VARCHAR(255)," +
                "  other_doc_verified TINYINT(1) DEFAULT 0," +
                // -- Interview --
                "  interview_date TEXT," +
                "  interview_time TEXT," +
                "  interview_link TEXT," +
                // -- Metadata --
                "  remarks TEXT," +
                "  revised TINYINT(1) DEFAULT 0," +
                "  reopen_until DATETIME," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            db.execute(
                "CREATE TABLE IF NOT EXISTS eac_application_logs (" +
                "  log_id INT AUTO_INCREMENT PRIMARY KEY," +
                "  ref_no VARCHAR(30)," +
                "  action VARCHAR(100)," +
                "  performed_by VARCHAR(60)," +
                "  remarks TEXT," +
                "  log_timestamp DATETIME" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // 4. CANONICAL CURRICULUM & PROGRAM TABLES
            db.execute("CREATE TABLE IF NOT EXISTS programs (program_id INT AUTO_INCREMENT PRIMARY KEY, program_code VARCHAR(20) NOT NULL UNIQUE, program_name VARCHAR(150), department_id INT DEFAULT NULL, school_name VARCHAR(100), duration_years INT NOT NULL DEFAULT 4, active_status TINYINT(1) NOT NULL DEFAULT 1)");
            db.execute("CREATE TABLE IF NOT EXISTS curriculum_templates (curriculum_id INT AUTO_INCREMENT PRIMARY KEY, program_id INT NOT NULL, curriculum_name VARCHAR(100), academic_year VARCHAR(20), version_number INT NOT NULL DEFAULT 1, approval_status VARCHAR(20) NOT NULL DEFAULT 'Draft', lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', is_active TINYINT(1) NOT NULL DEFAULT 0)");
            try { db.execute("ALTER TABLE curriculum_templates ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'"); } catch (Exception ignored) {}
            try { db.update("UPDATE curriculum_templates SET lifecycle_status = CASE WHEN UPPER(COALESCE(approval_status,'')) IN ('ARCHIVED','RETIRED') THEN 'ARCHIVED' WHEN UPPER(COALESCE(approval_status,'')) IN ('DRAFT','PLACEHOLDER') AND COALESCE(is_active,0) = 0 THEN 'DRAFT' WHEN COALESCE(is_active,0) = 1 THEN 'CURRENT' ELSE 'LEGACY' END WHERE lifecycle_status IS NULL OR lifecycle_status = '' OR UPPER(lifecycle_status) NOT IN ('DRAFT','CURRENT','LEGACY','ARCHIVED')"); } catch (Exception ignored) {}
            db.execute("CREATE TABLE IF NOT EXISTS curriculum_courses (curriculum_course_id INT AUTO_INCREMENT PRIMARY KEY, curriculum_id INT NOT NULL, course_id INT NOT NULL, year_level INT NOT NULL, semester_number INT NOT NULL, is_required TINYINT(1) NOT NULL DEFAULT 1)");
            db.execute("CREATE TABLE IF NOT EXISTS course_prerequisites (prerequisite_id INT AUTO_INCREMENT PRIMARY KEY, course_id INT NOT NULL, prerequisite_course_id INT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY unique_prereq (course_id, prerequisite_course_id))");
            db.execute("CREATE TABLE IF NOT EXISTS course_corequisites (corequisite_id INT AUTO_INCREMENT PRIMARY KEY, course_id INT NOT NULL, corequisite_course_id INT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY unique_coreq (course_id, corequisite_course_id))");
            db.execute("CREATE TABLE IF NOT EXISTS course_equivalencies (equivalency_id INT AUTO_INCREMENT PRIMARY KEY, course_id INT NOT NULL, equivalent_course_id INT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY unique_equivalency (course_id, equivalent_course_id))");
            db.execute("CREATE TABLE IF NOT EXISTS student_curriculum_assignments (assignment_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_number VARCHAR(100) NOT NULL, curriculum_id INT NOT NULL, program_code VARCHAR(100) NOT NULL, assignment_type VARCHAR(40) NOT NULL DEFAULT 'DEFAULT', reason VARCHAR(255) NULL, is_current TINYINT(1) NOT NULL DEFAULT 1, assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_sca_student_current (student_number, is_current), KEY idx_sca_curriculum (curriculum_id), KEY idx_sca_program (program_code))");
            ensureAcademicBuilderSchema();
            if (legacyMigrationBootstrapEnabled) {
                normalizeNutritionDieteticsNames();
                migrateLegacyLecLabCoursesIfNeeded();
            }
            normalizeRegistrarAccreditationCollations();
        } catch (Exception e) {
            System.err.println("Database Init Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void seedDefaultSetting(String key, String value) {
        try {
            db.update(
                "INSERT INTO system_settings (setting_key, setting_value) " +
                "SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = ?)",
                key, value, key);
        } catch (Exception e) {
            System.err.println("Default setting seed failed for " + key + ": " + e.getMessage());
        }
    }

    private void ensureAuditLogs() {
        try {
            db.execute("""
                CREATE TABLE IF NOT EXISTS audit_logs (
                    log_id INT AUTO_INCREMENT PRIMARY KEY,
                    admin_id INT NULL,
                    actor_username VARCHAR(100) NULL,
                    actor_role VARCHAR(50) NULL,
                    module_name VARCHAR(80) NULL,
                    action_name VARCHAR(100) NULL,
                    target_type VARCHAR(80) NULL,
                    target_key VARCHAR(120) NULL,
                    summary VARCHAR(255) NULL,
                    details TEXT NULL,
                    source_table VARCHAR(80) NULL,
                    source_id VARCHAR(120) NULL,
                    action VARCHAR(255) NULL,
                    log_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_audit_actor_date (actor_username, log_date),
                    KEY idx_audit_module_date (module_name, log_date),
                    KEY idx_audit_target_date (target_type, target_key, log_date)
                )
                """);
            tryExecute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS actor_username VARCHAR(100) NULL");
            tryExecute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS actor_role VARCHAR(50) NULL");
            tryExecute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS module_name VARCHAR(80) NULL");
            tryExecute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS action_name VARCHAR(100) NULL");
            tryExecute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS target_type VARCHAR(80) NULL");
            tryExecute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS target_key VARCHAR(120) NULL");
            tryExecute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS summary VARCHAR(255) NULL");
            tryExecute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS details TEXT NULL");
            tryExecute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS source_table VARCHAR(80) NULL");
            tryExecute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS source_id VARCHAR(120) NULL");
            if (!indexExists("audit_logs", "idx_audit_actor_date")) {
                db.execute("CREATE INDEX idx_audit_actor_date ON audit_logs (actor_username, log_date)");
            }
            if (!indexExists("audit_logs", "idx_audit_module_date")) {
                db.execute("CREATE INDEX idx_audit_module_date ON audit_logs (module_name, log_date)");
            }
            if (!indexExists("audit_logs", "idx_audit_target_date")) {
                db.execute("CREATE INDEX idx_audit_target_date ON audit_logs (target_type, target_key, log_date)");
            }
        } catch (Exception e) {
            System.err.println("Audit log schema setup failed: " + e.getMessage());
        }
    }

    private void ensureGradeOutcomeColumns() {
        try { db.execute("ALTER TABLE grades ADD COLUMN IF NOT EXISTS registrar_final_grade DECIMAL(5,2) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grades ADD COLUMN IF NOT EXISTS registrar_final_remarks VARCHAR(30) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grades ADD COLUMN IF NOT EXISTS grade_lock_status VARCHAR(30) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grades ADD COLUMN IF NOT EXISTS grade_lock_reason VARCHAR(80) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grades ADD COLUMN IF NOT EXISTS registrar_finalized_at TIMESTAMP NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grades ADD COLUMN IF NOT EXISTS curriculum_year INT NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grades ADD COLUMN IF NOT EXISTS grade DOUBLE NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grades ADD COLUMN IF NOT EXISTS date_recorded DATETIME NULL"); } catch (Exception ignored) {}
        try {
            db.execute("""
                CREATE TABLE IF NOT EXISTS grade_record_events (
                    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    grade_id BIGINT NOT NULL,
                    request_id BIGINT NULL,
                    student_id VARCHAR(100) NULL,
                    student_name VARCHAR(100) NULL,
                    course_id INT NULL,
                    course_code VARCHAR(20) NULL,
                    section_id INT NULL,
                    section_code VARCHAR(50) NULL,
                    term_id INT NULL,
                    term_label VARCHAR(40) NULL,
                    action_type VARCHAR(60) NOT NULL,
                    lifecycle_status VARCHAR(30) NOT NULL,
                    actor VARCHAR(100) NULL,
                    actor_role VARCHAR(50) NULL,
                    reason VARCHAR(500) NULL,
                    component_before VARCHAR(120) NULL,
                    component_after VARCHAR(120) NULL,
                    official_grade_before DECIMAL(5,2) NULL,
                    official_grade_after DECIMAL(5,2) NULL,
                    official_remarks_before VARCHAR(30) NULL,
                    official_remarks_after VARCHAR(30) NULL,
                    grade_lock_status_before VARCHAR(30) NULL,
                    grade_lock_status_after VARCHAR(30) NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_gre_grade_created (grade_id, created_at),
                    KEY idx_gre_student_created (student_id, created_at),
                    KEY idx_gre_term_action (term_id, action_type, created_at),
                    KEY idx_gre_lifecycle_created (lifecycle_status, created_at)
                )
                """);
        } catch (Exception ignored) {}
    }

    private void ensureAcademicBuilderSchema() {
        try {
            db.execute("CREATE TABLE IF NOT EXISTS departments (department_id INT AUTO_INCREMENT PRIMARY KEY, department_code VARCHAR(20) NULL UNIQUE, department_name VARCHAR(150))");
            db.execute("CREATE TABLE IF NOT EXISTS courses (course_id INT AUTO_INCREMENT PRIMARY KEY, course_code VARCHAR(40) NOT NULL UNIQUE, course_title VARCHAR(150), department_id INT NULL, credit_units INT NOT NULL DEFAULT 3, lec_units INT NOT NULL DEFAULT 0, lab_units INT NOT NULL DEFAULT 0, component_type VARCHAR(10) NOT NULL DEFAULT 'SINGLE', course_family_code VARCHAR(40) NULL, parent_course_id INT NULL, description TEXT NULL, active_status TINYINT(1) NOT NULL DEFAULT 1, onlist TINYINT(1) NOT NULL DEFAULT 1, KEY idx_courses_family (course_family_code), KEY idx_courses_component (component_type))");
            db.execute("CREATE TABLE IF NOT EXISTS class_sections (section_id INT AUTO_INCREMENT PRIMARY KEY, course_id INT NOT NULL, term_id INT NOT NULL, section_code VARCHAR(32) NOT NULL, faculty_id INT NULL, max_capacity INT NOT NULL DEFAULT 40, section_status VARCHAR(30) NOT NULL DEFAULT 'Open', semester_number INT NULL, block_id INT NULL, KEY idx_cs_course_term (course_id, term_id), KEY idx_cs_term_section (term_id, section_code), KEY idx_cs_block (block_id))");
            db.execute("CREATE TABLE IF NOT EXISTS class_schedules (schedule_id INT AUTO_INCREMENT PRIMARY KEY, section_id INT NULL, course_code VARCHAR(20) NULL, section VARCHAR(20) NULL, faculty_id INT NULL, day_of_week INT NULL, start_time TIME NULL, end_time TIME NULL, room_id INT NULL, schedule_type VARCHAR(30) NULL, status VARCHAR(50) DEFAULT 'OPEN', is_unlocked TINYINT(1) DEFAULT 0, KEY idx_sched_section (section_id))");
            db.execute("CREATE TABLE IF NOT EXISTS course_corequisites (corequisite_id INT AUTO_INCREMENT PRIMARY KEY, course_id INT NOT NULL, corequisite_course_id INT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY unique_coreq (course_id, corequisite_course_id))");
            db.execute("CREATE TABLE IF NOT EXISTS course_equivalencies (equivalency_id INT AUTO_INCREMENT PRIMARY KEY, course_id INT NOT NULL, equivalent_course_id INT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY unique_equivalency (course_id, equivalent_course_id))");

            tryExecute("ALTER TABLE programs ADD COLUMN IF NOT EXISTS duration_years INT NOT NULL DEFAULT 4");
            tryExecute("UPDATE programs SET duration_years = 4 WHERE duration_years IS NULL OR duration_years = 0");
            tryExecute("ALTER TABLE courses ADD COLUMN IF NOT EXISTS active_status TINYINT(1) NOT NULL DEFAULT 1");
            tryExecute("ALTER TABLE courses ADD COLUMN IF NOT EXISTS onlist TINYINT(1) NOT NULL DEFAULT 1");
            tryExecute("ALTER TABLE courses ADD COLUMN IF NOT EXISTS description TEXT NULL");
            tryExecute("ALTER TABLE courses MODIFY COLUMN course_code VARCHAR(40) NOT NULL");
            tryExecute("ALTER TABLE courses ADD COLUMN IF NOT EXISTS lec_units INT NOT NULL DEFAULT 0");
            tryExecute("ALTER TABLE courses ADD COLUMN IF NOT EXISTS lab_units INT NOT NULL DEFAULT 0");
            tryExecute("ALTER TABLE courses ADD COLUMN IF NOT EXISTS component_type VARCHAR(10) NOT NULL DEFAULT 'SINGLE'");
            tryExecute("ALTER TABLE courses ADD COLUMN IF NOT EXISTS course_family_code VARCHAR(40) NULL");
            tryExecute("ALTER TABLE courses ADD COLUMN IF NOT EXISTS parent_course_id INT NULL");
            tryExecute("UPDATE courses SET lec_units = credit_units WHERE lec_units = 0 AND lab_units = 0 AND credit_units > 0");
            tryExecute("UPDATE courses SET component_type = CASE WHEN COALESCE(lec_units, 0) > 0 AND COALESCE(lab_units, 0) = 0 THEN 'LEC' WHEN COALESCE(lab_units, 0) > 0 AND COALESCE(lec_units, 0) = 0 THEN 'LAB' ELSE COALESCE(NULLIF(component_type, ''), 'SINGLE') END");
            tryExecute("UPDATE courses SET course_family_code = TRIM(REPLACE(REPLACE(REPLACE(REPLACE(course_code, '-LEC', ''), '-LAB', ''), ' LEC', ''), ' LAB', '')) WHERE course_family_code IS NULL OR course_family_code = ''");
            tryExecute("ALTER TABLE courses ADD KEY IF NOT EXISTS idx_courses_family (course_family_code)");
            tryExecute("ALTER TABLE courses ADD KEY IF NOT EXISTS idx_courses_component (component_type)");
            tryExecute("ALTER TABLE course_corequisites ADD KEY IF NOT EXISTS idx_cc_coreq (corequisite_course_id)");
            tryExecute("ALTER TABLE course_equivalencies ADD KEY IF NOT EXISTS idx_ce_equivalent (equivalent_course_id)");
            tryExecute("UPDATE courses SET active_status = 1 WHERE active_status IS NULL");
            tryExecute("UPDATE courses SET onlist = COALESCE(active_status, 1) WHERE onlist IS NULL");
            tryExecute("ALTER TABLE courses MODIFY COLUMN active_status TINYINT(1) NOT NULL DEFAULT 1");
            tryExecute("ALTER TABLE courses MODIFY COLUMN onlist TINYINT(1) NOT NULL DEFAULT 1");
            tryExecute("ALTER TABLE departments ADD COLUMN IF NOT EXISTS department_code VARCHAR(20) NULL");
            tryExecute("ALTER TABLE class_sections ADD COLUMN IF NOT EXISTS faculty_id INT NULL");
            tryExecute("ALTER TABLE class_sections ADD COLUMN IF NOT EXISTS max_capacity INT NOT NULL DEFAULT 40");
            tryExecute("ALTER TABLE class_sections ADD COLUMN IF NOT EXISTS section_status VARCHAR(30) NOT NULL DEFAULT 'Open'");
            tryExecute("ALTER TABLE class_sections ADD COLUMN IF NOT EXISTS semester_number INT NULL");
            tryExecute("ALTER TABLE class_sections ADD COLUMN IF NOT EXISTS block_id INT NULL");
            tryExecute("ALTER TABLE class_sections ADD KEY IF NOT EXISTS idx_cs_term_faculty (term_id, faculty_id)");
            tryExecute("ALTER TABLE class_sections ADD KEY IF NOT EXISTS idx_cs_term_status (term_id, section_status)");
            tryExecute("ALTER TABLE class_sections ADD UNIQUE KEY IF NOT EXISTS uk_cs_term_section_course (term_id, section_code, course_id)");
            tryExecute("ALTER TABLE class_schedules ADD COLUMN IF NOT EXISTS section_id INT NULL");
            tryExecute("ALTER TABLE class_schedules ADD COLUMN IF NOT EXISTS room_id INT NULL");
            tryExecute("ALTER TABLE class_schedules ADD COLUMN IF NOT EXISTS schedule_type VARCHAR(30) NULL");
            tryExecute("ALTER TABLE class_schedules ADD COLUMN IF NOT EXISTS is_unlocked TINYINT(1) DEFAULT 0");
            tryExecute("ALTER TABLE class_schedules ADD COLUMN IF NOT EXISTS faculty_id INT NULL");
            tryExecute("ALTER TABLE class_schedules MODIFY COLUMN day_of_week INT NULL");
            tryExecute("ALTER TABLE class_schedules ADD KEY IF NOT EXISTS idx_sched_section_day (section_id, day_of_week)");
            tryExecute("ALTER TABLE class_schedules ADD KEY IF NOT EXISTS idx_sched_room_day (room_id, day_of_week)");
            tryExecute("ALTER TABLE class_schedules ADD KEY IF NOT EXISTS idx_sched_faculty_day (faculty_id, day_of_week)");
            tryExecute("ALTER TABLE class_schedules ADD KEY IF NOT EXISTS idx_sched_day_time (day_of_week, start_time, end_time)");
            tryExecute("ALTER TABLE student_enlistments ADD KEY IF NOT EXISTS idx_se_course_status (course_id, enlistment_status)");
            tryExecute("ALTER TABLE student_enlistments ADD KEY IF NOT EXISTS idx_se_section_status (section_id, enlistment_status)");
        } catch (Exception e) {
            System.err.println("Academic builder schema setup failed: " + e.getMessage());
        }
    }

    private void normalizeRegistrarAccreditationCollations() {
        tryExecute("ALTER TABLE applicant_credit_accreditations CONVERT TO CHARACTER SET utf8mb4 COLLATE " + REGISTRAR_COLLATION);
        tryExecute("ALTER TABLE applicant_credit_accreditation_lines CONVERT TO CHARACTER SET utf8mb4 COLLATE " + REGISTRAR_COLLATION);
        tryExecute("ALTER TABLE transfer_credit_requests CONVERT TO CHARACTER SET utf8mb4 COLLATE " + REGISTRAR_COLLATION);
    }

    private void normalizeNutritionDieteticsNames() {
        tryExecute("UPDATE departments SET department_name = 'School of Nutrition and Dietetics' WHERE department_code = 'NUTR' OR department_name LIKE '%Nutrition%Diabetics%'");
        tryExecute("UPDATE programs SET program_name = 'Nutrition and Dietetics' WHERE program_code = 'NNAD' OR program_name IN ('NA Nutrition and Diabetics', 'Nutrition and Diabetics')");
        tryExecute("UPDATE programs SET program_name = REPLACE(program_name, 'Diabetics', 'Dietetics') WHERE program_name LIKE '%Diabetics%'");
        tryExecute("UPDATE programs SET school_name = REPLACE(REPLACE(school_name, 'School of  Nutrition and Diabetics', 'School of Nutrition and Dietetics'), 'School of Nutrition and Diabetics', 'School of Nutrition and Dietetics') WHERE school_name LIKE '%Nutrition%Diabetics%'");
        tryExecute("UPDATE curriculum_templates SET curriculum_name = REPLACE(REPLACE(curriculum_name, 'NA Nutrition and Diabetics', 'Nutrition and Dietetics'), 'Diabetics', 'Dietetics') WHERE curriculum_name LIKE '%Diabetics%'");
    }

    private void migrateLegacyLecLabCoursesIfNeeded() {
        try {
            Integer mixedCount = db.queryForObject(
                "SELECT COUNT(*) FROM courses " +
                    "WHERE COALESCE(lec_units, 0) > 0 AND COALESCE(lab_units, 0) > 0 " +
                    "AND COALESCE(component_type, 'SINGLE') NOT IN ('LEC', 'LAB', 'LEGACY')",
                Integer.class);
            if (mixedCount != null && mixedCount > 0) {
                runSqlResource("sql/16_migrate_legacy_lec_lab_courses_20260701.sql");
            }
        } catch (Exception e) {
            System.err.println("Legacy lecture/lab migration check failed: " + e.getMessage());
        }
    }

    private void runSqlResource(String resourcePath) {
        try {
            Resource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                System.err.println("SQL resource not found: " + resourcePath);
                return;
            }
            DataSource dataSource = db.getDataSource();
            if (dataSource == null) {
                System.err.println("No DataSource available for SQL resource: " + resourcePath);
                return;
            }
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(false);
            populator.setIgnoreFailedDrops(true);
            populator.addScript(resource);
            populator.execute(dataSource);
        } catch (Exception e) {
            System.err.println("Failed to run SQL resource " + resourcePath + ": " + e.getMessage());
        }
    }

    private void tryExecute(String sql) {
        try {
            db.execute(sql);
        } catch (Exception ignored) {
        }
    }

    private boolean tableExists(String tableName) {
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                    "WHERE UPPER(TABLE_SCHEMA) = UPPER(DATABASE()) " +
                    "AND UPPER(TABLE_NAME) = UPPER(?) AND UPPER(TABLE_TYPE) = 'BASE TABLE'",
                Integer.class, tableName);
            return count != null && count > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean objectExists(String objectName) {
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                    "WHERE UPPER(TABLE_SCHEMA) = UPPER(DATABASE()) " +
                    "AND UPPER(TABLE_NAME) = UPPER(?)",
                Integer.class, objectName);
            return count != null && count > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean indexExists(String tableName, String indexName) {
        try {
            Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS " +
                    "WHERE UPPER(TABLE_SCHEMA) = UPPER(DATABASE()) " +
                    "AND UPPER(TABLE_NAME) = UPPER(?) AND UPPER(INDEX_NAME) = UPPER(?)",
                Integer.class, tableName, indexName);
            return count != null && count > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void ensureScholarshipTypeCatalog() {
        try {
            db.execute(
                "CREATE TABLE IF NOT EXISTS scholarship_types (" +
                    "type_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "classification VARCHAR(50) NOT NULL UNIQUE, " +
                    "display_name VARCHAR(100) NULL, " +
                    "discount_mode VARCHAR(20) NOT NULL DEFAULT 'PERCENT', " +
                    "default_discount_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00, " +
                    "default_scholarship_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00, " +
                    "is_internal TINYINT(1) DEFAULT 0, " +
                    "requires_id TINYINT(1) DEFAULT 1, " +
                    "is_active TINYINT(1) NOT NULL DEFAULT 1)");
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN IF NOT EXISTS display_name VARCHAR(100) NULL"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN IF NOT EXISTS discount_mode VARCHAR(20) NOT NULL DEFAULT 'PERCENT'"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN IF NOT EXISTS default_discount_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN IF NOT EXISTS default_scholarship_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN IF NOT EXISTS is_internal TINYINT(1) DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN IF NOT EXISTS requires_id TINYINT(1) DEFAULT 1"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN IF NOT EXISTS is_active TINYINT(1) NOT NULL DEFAULT 1"); } catch (Exception ignored) {}
        } catch (Exception e) {
            System.err.println("Scholarship type catalog setup failed: " + e.getMessage());
        }
    }

    private void ensureScholarshipReviewWorkflow() {
        try {
            db.execute(
                "CREATE TABLE IF NOT EXISTS scholarship_review_workflow (" +
                    "review_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "student_number VARCHAR(100) NOT NULL, " +
                    "term_id INT NOT NULL, " +
                    "classification VARCHAR(50) NOT NULL DEFAULT 'ACADEMIC', " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'PENDING', " +
                    "discount_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00, " +
                    "scholarship_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00, " +
                    "decision_note VARCHAR(500) NULL, " +
                    "requested_by VARCHAR(100) NULL, " +
                    "requested_at TIMESTAMP NULL, " +
                    "reviewed_by VARCHAR(100) NULL, " +
                    "reviewed_at TIMESTAMP NULL, " +
                    "posted_by VARCHAR(100) NULL, " +
                    "posted_at TIMESTAMP NULL, " +
                    "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE KEY uq_scholar_review_student_term_type (student_number, term_id, classification), " +
                    "KEY idx_scholar_review_term_status (term_id, status))");
        } catch (Exception e) {
            System.err.println("Scholarship review workflow setup failed: " + e.getMessage());
        }
    }

    private void seedScholarshipType(String classification, String displayName, String mode,
                                     double discountPct, double amount, boolean internal) {
        try {
            db.update(
                "INSERT INTO scholarship_types " +
                    "(classification, display_name, discount_mode, default_discount_percentage, default_scholarship_amount, is_internal, requires_id, is_active) " +
                    "SELECT ?, ?, ?, ?, ?, ?, 1, 1 WHERE NOT EXISTS (SELECT 1 FROM scholarship_types WHERE classification = ?)",
                classification, displayName, mode, discountPct, amount, internal ? 1 : 0, classification);
        } catch (Exception ignored) {
        }
    }

    private void ensureGradeChangeRequestColumns() {
        try { db.execute("ALTER TABLE grade_change_requests ADD COLUMN IF NOT EXISTS request_type VARCHAR(40) NOT NULL DEFAULT 'FINAL_GRADE_CORRECTION'"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grade_change_requests ADD COLUMN IF NOT EXISTS requested_prelim DECIMAL(5,2) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grade_change_requests ADD COLUMN IF NOT EXISTS requested_midterm DECIMAL(5,2) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grade_change_requests ADD COLUMN IF NOT EXISTS requested_finals DECIMAL(5,2) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grade_change_requests ADD COLUMN IF NOT EXISTS applied_action VARCHAR(80) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grade_change_requests ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grade_change_requests ADD COLUMN IF NOT EXISTS reviewed_by VARCHAR(100) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grade_change_requests ADD COLUMN IF NOT EXISTS review_note VARCHAR(500) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grade_change_requests ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP NULL"); } catch (Exception ignored) {}
    }

}



