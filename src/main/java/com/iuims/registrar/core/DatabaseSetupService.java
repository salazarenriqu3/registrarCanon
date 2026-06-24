package com.iuims.registrar.core;
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

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseSetupService {

    @Autowired
    private JdbcTemplate db;

    @PostConstruct
    public void initDatabase() {
        try {
            // MySQL reference scripts (schema vs seed split): db/01_schema_eacdb_unified.sql, db/02_seed_eacdb_test_data.sql
            // 1. CORE SYSTEM TABLES
            db.execute("CREATE TABLE IF NOT EXISTS system_settings (setting_key VARCHAR(50) PRIMARY KEY, setting_value VARCHAR(100))");
            if (db.queryForObject("SELECT COUNT(*) FROM system_settings", Integer.class) == 0) {
                db.update("INSERT INTO system_settings VALUES ('PRELIM_START', '2025-01-01'), ('PRELIM_END', '2026-12-31'), ('MIDTERM_START', '2025-01-01'), ('MIDTERM_END', '2026-12-31'), ('FINAL_START', '2025-01-01'), ('FINAL_END', '2026-12-31'), ('PRELIM_OVERRIDE', 'AUTO'), ('MIDTERM_OVERRIDE', 'AUTO'), ('FINAL_OVERRIDE', 'AUTO')");
            }
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
            ensureScholarshipTypeCatalog();
            ensureScholarshipReviewWorkflow();
            db.execute("CREATE TABLE IF NOT EXISTS grading_term_windows (window_id BIGINT AUTO_INCREMENT PRIMARY KEY, term_id INT NOT NULL, grading_period VARCHAR(20) NOT NULL, start_date DATE NULL, end_date DATE NULL, override_status VARCHAR(20) NOT NULL DEFAULT 'AUTO', updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uq_gtw_term_period (term_id, grading_period), KEY idx_gtw_term (term_id))");
            db.execute("CREATE TABLE IF NOT EXISTS academic_term_policies (term_id INT PRIMARY KEY, inc_expiration_date DATE NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            ensureGradeOutcomeColumns();
            db.execute("CREATE TABLE IF NOT EXISTS audit_logs (log_id INT AUTO_INCREMENT PRIMARY KEY, admin_id INT, action VARCHAR(255), log_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            
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
            db.execute("CREATE TABLE IF NOT EXISTS student_grades (grade_id INT AUTO_INCREMENT PRIMARY KEY, schedule_id INT, student_name VARCHAR(100), student_id INT, prelim VARCHAR(10), midterm VARCHAR(10), final_grade VARCHAR(10), status VARCHAR(50) DEFAULT 'DRAFT')");
            try { db.execute("ALTER TABLE student_grades MODIFY COLUMN status VARCHAR(50) DEFAULT 'DRAFT'"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE class_schedules MODIFY COLUMN status VARCHAR(50) DEFAULT 'OPEN'"); } catch (Exception e) {}
            // Add semester column to sys_users if it doesn't exist yet
            try { db.execute("ALTER TABLE sys_users ADD COLUMN semester INT DEFAULT 1"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN first_name VARCHAR(100) NULL"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN middle_name VARCHAR(100) NULL"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN last_name VARCHAR(100) NULL"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN email VARCHAR(150) NULL"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN mobile VARCHAR(50) NULL"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN term_year VARCHAR(50) NULL"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN student_type VARCHAR(50) NULL"); } catch (Exception e) {}
            try { db.execute("ALTER TABLE sys_users ADD COLUMN enrollment_status_type VARCHAR(50) NULL"); } catch (Exception e) {}
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

            // 3b. EAC APPLICANTS — recreate only if missing
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
            try { db.execute("ALTER TABLE curriculum_templates ADD COLUMN lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'"); } catch (Exception ignored) {}
            try { db.update("UPDATE curriculum_templates SET lifecycle_status = CASE WHEN UPPER(COALESCE(approval_status,'')) IN ('ARCHIVED','RETIRED') THEN 'ARCHIVED' WHEN UPPER(COALESCE(approval_status,'')) IN ('DRAFT','PLACEHOLDER') AND COALESCE(is_active,0) = 0 THEN 'DRAFT' WHEN COALESCE(is_active,0) = 1 THEN 'CURRENT' ELSE 'LEGACY' END WHERE lifecycle_status IS NULL OR lifecycle_status = '' OR UPPER(lifecycle_status) NOT IN ('DRAFT','CURRENT','LEGACY','ARCHIVED')"); } catch (Exception ignored) {}
            db.execute("CREATE TABLE IF NOT EXISTS curriculum_courses (curriculum_course_id INT AUTO_INCREMENT PRIMARY KEY, curriculum_id INT NOT NULL, course_id INT NOT NULL, year_level INT NOT NULL, semester_number INT NOT NULL, is_required TINYINT(1) NOT NULL DEFAULT 1)");
            db.execute("CREATE TABLE IF NOT EXISTS course_prerequisites (prerequisite_id INT AUTO_INCREMENT PRIMARY KEY, course_id INT NOT NULL, prerequisite_course_id INT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY unique_prereq (course_id, prerequisite_course_id))");
            db.execute("CREATE TABLE IF NOT EXISTS student_curriculum_assignments (assignment_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_number VARCHAR(100) NOT NULL, curriculum_id INT NOT NULL, program_code VARCHAR(100) NOT NULL, assignment_type VARCHAR(40) NOT NULL DEFAULT 'DEFAULT', reason VARCHAR(255) NULL, is_current TINYINT(1) NOT NULL DEFAULT 1, assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_sca_student_current (student_number, is_current), KEY idx_sca_curriculum (curriculum_id), KEY idx_sca_program (program_code))");
            ensureAcademicBuilderSchema();

            // Seed EAC applicants immediately after table creation
            seedEacApplicants();

            // Create default users
            ensureUserPassword("admin", "1234", "Admin");
            ensureUserPassword("prof", "1234", "Faculty");
            ensureUserPassword("dean", "1234", "Dean");
        } catch (Exception e) {
            System.err.println("Database Init Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void seedEacApplicants() {
        System.out.println("  📋 Seeding EAC applicants...");

        String sql =
            "INSERT IGNORE INTO applicants (" +
            "  reference_number, applicant_status, term_year," +
            "  first_name, last_name, middle_name, middle_initial, middle_name_na, extension," +
            "  sex, dob, place_of_birth, civil_status, religion, nationality, citizenship," +
            "  age, four_ps, indigenous, international_student," +
            "  email, email_verified, mobile, landline," +
            "  street, city, province, zip," +
            "  emergency_contact_name, emergency_contact_mobile, emergency_contact_relationship," +
            "  father_name, father_occupation, father_contact, father_address," +
            "  mother_name, mother_occupation, mother_contact, mother_address," +
            "  guardian_name, guardian_contact, guardian_relationship," +
            "  sibling_count, sibling_order, monthly_income," +
            "  academic_level," +
            "  elementary_school, elementary_address, elementary_year," +
            "  jhs_school, jhs_address, jhs_year," +
            "  shs_school, shs_address, shs_track, shs_year," +
            "  last_school, last_school_year, course_taken," +
            "  program1, program2," +
            "  form138_path, form138_verified," +
            "  good_moral_path, good_moral_verified," +
            "  psa_birth_cert_path, psa_birth_cert_verified," +
            "  id_picture_path, id_picture_verified," +
            "  marriage_cert_path, marriage_cert_verified," +
            "  other_doc_path, other_doc_verified," +
            "  remarks, created_at" +
            ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        seedRow(sql, "EAC-12E89F39","ENROLLED","2024-2025_1st","Maron","Javier","Soriano","S.",0,"","M","2004-02-13","Manila","single","catholic","Filipino","Filipino",22,0,0,0,"maronthegreatt@gmail.com",1,"09664170568","","1665 Int 13 J Zamora St. Paco Manila","Manila","Manila","1007","Maron Mikhail Javier","09123456789","Me","Marlon Javier","Chef","09987654321","1665 Int 13 J Zamora, Manila","Maricel Javier","House Wife","09123456789","1665 Int 13 J Zamora, Manila","","","",2,"eldest","below_10k","college","Justo Lukban Elem School","","2015-2016","Manuel A. Roxas HS","","2019-2020","Universidad De Manila","","GAS","2021-2022","","","","BSCS","BSIT","simulated/form138_EAC-12E89F39.pdf",1,"simulated/goodmoral_EAC-12E89F39.pdf",1,"simulated/psa_EAC-12E89F39.pdf",1,"simulated/idpic_EAC-12E89F39.jpg",1,null,0,null,0,null,"2026-02-13 12:23:40");
        seedRow(sql, "EAC-4A09644A","QUALIFIED FOR ENROLLMENT","2025-2026_2nd","Clarissa","Reyes","Marie","M.",0,"","F","2004-03-19","Quezon City","single","Catholic","Filipino","By Birth",21,0,0,0,"clarissa.reyes@gmail.com",1,"09175553241","","45 Sampaguita St. Brgy. Holy Spirit","Quezon City","Metro Manila","1127","Elena Reyes","09175551234","Mother","Roberto Reyes","Engineer","09175559876","45 Sampaguita St., Quezon City","Elena Reyes","Teacher","09175551234","45 Sampaguita St., Quezon City","","","",2,"eldest","10k_to_20k","College","Commonwealth Elem School","","2016","Batasan Hills NHS","","2020","San Bartolome HS","","HUMSS","2022","","","","BSN","BSCS","simulated/form138_EAC-4A09644A.pdf",1,"simulated/goodmoral_EAC-4A09644A.pdf",1,"simulated/psa_EAC-4A09644A.pdf",1,"simulated/idpic_EAC-4A09644A.jpg",1,"simulated/marrcert_EAC-4A09644A.pdf",1,"simulated/otherdoc_EAC-4A09644A.pdf",1,null,"2026-03-05 10:59:56");
        seedRow(sql, "EAC-F4F9B68D","QUALIFIED FOR ENROLLMENT","2025-2026_1st","Franklin","Moris","","",1,"","M","2003-02-18","Manila","single","Christian","Filipino","Filipino",23,0,0,0,"maronthegreatt@gmail.com",1,"09664170568","","1665 Int. 13 J. Zamora St.","Manila","Manila","1007","Maron Mikhail Javier","09123456789","Mother","Bush Moris","Cloud Engineer","09987654321","1665 Int. 13 J. Zamora, Manila","Analiza Moris","House Wife","09123456789","1665 Int. 13 J. Zamora, Manila","","","",1,"youngest","below_10k","College","Justo Lukban Elem School","1234 INT 14 I Test St.","2016","Manuel A. Roxas HS","1234 INT 14 I Test St.","2020","Universidad De Manila","1234 INT 14 I Test St.","GAS","2022","","","","BSCS","BSIT","simulated/form138_EAC-F4F9B68D.pdf",1,"simulated/goodmoral_EAC-F4F9B68D.pdf",1,"simulated/psa_EAC-F4F9B68D.pdf",1,"simulated/idpic_EAC-F4F9B68D.jpg",1,"simulated/marrcert_EAC-F4F9B68D.pdf",1,null,0,null,"2026-03-24 17:12:05");
        seedRow(sql, "EAC-1950DF37","QUALIFIED FOR ENROLLMENT","2025-2026_1st","Jessa Mae","Santos","Cruz","C.",0,"","F","2004-10-11","Pasig City","single","Christian","Filipino","Filipino",21,0,0,0,"jessamae.santos@gmail.com",0,"09281234567","","78 Rosario St. Brgy. Kapitolyo","Pasig City","Metro Manila","1603","Maria Santos","09281239876","Mother","Carlos Santos","OFW Seaman","09281235678","78 Rosario St., Pasig City","Maria Santos","Dressmaker","09281239876","78 Rosario St., Pasig City","Lita Cruz","09281237654","Aunt",1,"only","below_10k","College","Rizal Elem School","Rosario Ave, Pasig","2016","Pasig City Science HS","Rosario Ave, Pasig","2020","Sta. Lucia HS","Rosario Ave, Pasig","ABM","2022","","","","BSN","BSIT","simulated/form138_EAC-1950DF37.pdf",1,"simulated/goodmoral_EAC-1950DF37.pdf",1,"simulated/psa_EAC-1950DF37.pdf",1,"simulated/idpic_EAC-1950DF37.jpg",1,"simulated/marrcert_EAC-1950DF37.pdf",1,"simulated/otherdoc_EAC-1950DF37.pdf",1,null,"2026-03-24 17:29:14");
        seedRow(sql, "EAC-32987A96","REJECTED","2025-2026_1st","Denise","Torres","","",1,"","F","2004-03-18","Caloocan City","single","Christian","Filipino","Filipino",22,0,0,0,"denise.torres@gmail.com",1,"09359876543","","12 Libis St. Brgy. 171","Caloocan City","Metro Manila","1400","Rosa Torres","09359876000","Mother","Andres Torres","Driver","09359871111","12 Libis St., Caloocan City","Rosa Torres","Vendor","09359876000","12 Libis St., Caloocan City","","","",1,"only","below_10k","College","Caloocan North Elem School","Libis St, Caloocan","2016","Caloocan NHS","Libis St, Caloocan","2020","Caloocan City Senior HS","Libis St, Caloocan","ABM","2022","","","","BSN","BSCS","simulated/form138_EAC-32987A96.pdf",1,"simulated/goodmoral_EAC-32987A96.pdf",1,"simulated/psa_EAC-32987A96.pdf",1,"simulated/idpic_EAC-32987A96.jpg",0,null,0,null,0,"Did not meet admission policy criteria","2026-03-24 17:36:34");

        // Application logs
        String logSql = "INSERT IGNORE INTO eac_application_logs (log_id,ref_no,action,performed_by,remarks,log_timestamp) VALUES (?,?,?,?,?,?)";
        Object[][] logs = {
            {1,"EAC-12E89F39","SUBMITTED","anonymousUser","Initial application submission.","2026-02-13 12:20:03"},
            {2,"EAC-12E89F39","EMAIL VERIFIED","anonymousUser","Student verified their email address.","2026-02-13 12:20:27"},
            {3,"EAC-12E89F39","DEFICIENCY NOTIFIED","admin","","2026-02-13 12:23:15"},
            {4,"EAC-12E89F39","DOC VERIFIED","admin","Verified form138","2026-02-13 12:23:27"},
            {5,"EAC-12E89F39","DOC VERIFIED","admin","Verified goodMoral","2026-02-13 12:23:28"},
            {6,"EAC-12E89F39","DOC VERIFIED","admin","Verified psaBirthCert","2026-02-13 12:23:30"},
            {7,"EAC-12E89F39","DOC VERIFIED","admin","Verified idPicture","2026-02-13 12:23:31"},
            {8,"EAC-12E89F39","ENROLLED","admin",null,"2026-02-13 12:23:40"},
            {9,"EAC-4A09644A","SUBMITTED","encoder","Initial application submission.","2026-03-05 10:59:56"},
            {10,"EAC-4A09644A","DOC VERIFIED","admin","Verified form138","2026-03-05 11:20:55"},
            {11,"EAC-4A09644A","DOC VERIFIED","admin","Verified goodMoral","2026-03-05 11:20:59"},
            {12,"EAC-4A09644A","EMAIL VERIFIED","anonymousUser","Student verified their email address.","2026-03-24 16:37:50"},
            {13,"EAC-4A09644A","DOC VERIFIED","admin","Verified psaBirthCert","2026-03-24 16:38:39"},
            {14,"EAC-4A09644A","DOC VERIFIED","admin","Verified idPicture","2026-03-24 16:38:40"},
            {15,"EAC-4A09644A","DEFICIENCY NOTIFIED","admin","","2026-03-24 16:39:48"},
            {16,"EAC-4A09644A","DEFICIENCY NOTIFIED","admin","sad","2026-03-24 16:39:53"},
            {17,"EAC-F4F9B68D","SUBMITTED","anonymousUser","Initial application submission.","2026-03-24 17:12:05"},
            {18,"EAC-F4F9B68D","EMAIL VERIFIED","anonymousUser","Student verified their email address.","2026-03-24 17:12:27"},
            {19,"EAC-4A09644A","DOC VERIFIED","encoder","Verified marriageCert","2026-03-24 17:13:08"},
            {20,"EAC-4A09644A","DOC VERIFIED","encoder","Verified otherDoc","2026-03-24 17:13:10"},
            {21,"EAC-F4F9B68D","DOC VERIFIED","encoder","Verified form138","2026-03-24 17:13:12"},
            {22,"EAC-F4F9B68D","DOC VERIFIED","encoder","Unverified form138","2026-03-24 17:13:13"},
            {23,"EAC-F4F9B68D","DOC VERIFIED","encoder","Verified form138","2026-03-24 17:13:15"},
            {24,"EAC-F4F9B68D","DOC VERIFIED","encoder","Verified goodMoral","2026-03-24 17:13:18"},
            {25,"EAC-F4F9B68D","DOC VERIFIED","encoder","Verified psaBirthCert","2026-03-24 17:13:19"},
            {26,"EAC-F4F9B68D","DOC VERIFIED","encoder","Verified idPicture","2026-03-24 17:13:21"},
            {27,"EAC-F4F9B68D","DOC VERIFIED","encoder","Verified marriageCert","2026-03-24 17:13:24"},
            {28,"EAC-F4F9B68D","QUALIFIED FOR ENROLLMENT","encoder",null,"2026-03-24 17:13:27"},
            {29,"EAC-1950DF37","SUBMITTED","anonymousUser","Initial application submission.","2026-03-24 17:29:14"},
            {30,"EAC-32987A96","SUBMITTED","anonymousUser","Initial application submission.","2026-03-24 17:36:35"},
            {31,"EAC-32987A96","EMAIL VERIFIED","anonymousUser","Student verified their email address.","2026-03-24 17:36:54"},
            {32,"EAC-1950DF37","DOC VERIFIED","admin","Verified otherDoc","2026-03-24 17:37:51"},
            {33,"EAC-1950DF37","DOC VERIFIED","admin","Verified marriageCert","2026-03-24 17:37:54"},
            {34,"EAC-1950DF37","DOC VERIFIED","admin","Verified idPicture","2026-03-24 17:37:56"},
            {35,"EAC-1950DF37","DOC VERIFIED","admin","Verified psaBirthCert","2026-03-24 17:37:59"},
            {36,"EAC-1950DF37","DOC VERIFIED","admin","Verified goodMoral","2026-03-24 17:38:02"},
            {37,"EAC-1950DF37","DOC VERIFIED","admin","Verified form138","2026-03-24 17:38:05"},
            {38,"EAC-4A09644A","QUALIFIED FOR ENROLLMENT","admin",null,"2026-03-24 17:38:09"},
            {39,"EAC-32987A96","DOC VERIFIED","admin","Verified form138","2026-03-24 17:38:21"},
            {40,"EAC-32987A96","DOC VERIFIED","admin","Verified goodMoral","2026-03-24 17:38:23"},
            {41,"EAC-32987A96","DOC VERIFIED","admin","Verified psaBirthCert","2026-03-24 17:38:25"},
            {42,"EAC-32987A96","REJECTED","admin","Did not meet admission policy criteria","2026-03-24 17:38:38"},
            {43,"EAC-1950DF37","QUALIFIED FOR ENROLLMENT","admin",null,"2026-03-24 17:38:43"},
        };
        for (Object[] row : logs) {
            try { db.update(logSql, row); } catch (Exception e) { /* skip dupes */ }
        }
        System.out.println("  ✓ EAC applicant seed complete.");
    }

    private void seedRow(String sql, Object... args) {
        try {
            db.update(sql, args);
        } catch (Exception e) {
            System.err.println("  ⚠ Seed row failed: " + e.getMessage());
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

    private void ensureGradeOutcomeColumns() {
        try { db.execute("ALTER TABLE grades ADD COLUMN registrar_final_grade DECIMAL(5,2) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grades ADD COLUMN registrar_final_remarks VARCHAR(30) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grades ADD COLUMN grade_lock_status VARCHAR(30) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grades ADD COLUMN grade_lock_reason VARCHAR(80) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grades ADD COLUMN registrar_finalized_at TIMESTAMP NULL"); } catch (Exception ignored) {}
    }

    private void ensureAcademicBuilderSchema() {
        try {
            db.execute("CREATE TABLE IF NOT EXISTS departments (department_id INT AUTO_INCREMENT PRIMARY KEY, department_code VARCHAR(20) NULL UNIQUE, department_name VARCHAR(150))");
            db.execute("CREATE TABLE IF NOT EXISTS courses (course_id INT AUTO_INCREMENT PRIMARY KEY, course_code VARCHAR(20) NOT NULL UNIQUE, course_title VARCHAR(150), department_id INT NULL, credit_units INT NOT NULL DEFAULT 3, description TEXT NULL, active_status TINYINT(1) NOT NULL DEFAULT 1, onlist TINYINT(1) NOT NULL DEFAULT 1)");
            db.execute("CREATE TABLE IF NOT EXISTS class_sections (section_id INT AUTO_INCREMENT PRIMARY KEY, course_id INT NOT NULL, term_id INT NOT NULL, section_code VARCHAR(32) NOT NULL, faculty_id INT NULL, max_capacity INT NOT NULL DEFAULT 40, section_status VARCHAR(30) NOT NULL DEFAULT 'Open', semester_number INT NULL, block_id INT NULL, KEY idx_cs_course_term (course_id, term_id), KEY idx_cs_term_section (term_id, section_code), KEY idx_cs_block (block_id))");
            db.execute("CREATE TABLE IF NOT EXISTS class_schedules (schedule_id INT AUTO_INCREMENT PRIMARY KEY, section_id INT NULL, course_code VARCHAR(20) NULL, section VARCHAR(20) NULL, faculty_id INT NULL, day_of_week INT NULL, start_time TIME NULL, end_time TIME NULL, room_id INT NULL, schedule_type VARCHAR(30) NULL, status VARCHAR(50) DEFAULT 'OPEN', is_unlocked TINYINT(1) DEFAULT 0, KEY idx_sched_section (section_id))");

            tryExecute("ALTER TABLE programs ADD COLUMN duration_years INT NOT NULL DEFAULT 4");
            tryExecute("UPDATE programs SET duration_years = 4 WHERE duration_years IS NULL OR duration_years = 0");
            tryExecute("ALTER TABLE courses ADD COLUMN active_status TINYINT(1) NOT NULL DEFAULT 1");
            tryExecute("ALTER TABLE courses ADD COLUMN onlist TINYINT(1) NOT NULL DEFAULT 1");
            tryExecute("ALTER TABLE courses ADD COLUMN description TEXT NULL");
            tryExecute("ALTER TABLE courses ADD COLUMN lec_units INT NOT NULL DEFAULT 0");
            tryExecute("ALTER TABLE courses ADD COLUMN lab_units INT NOT NULL DEFAULT 0");
            tryExecute("UPDATE courses SET lec_units = credit_units WHERE lec_units = 0 AND lab_units = 0 AND credit_units > 0");
            tryExecute("UPDATE courses SET active_status = 1 WHERE active_status IS NULL");
            tryExecute("UPDATE courses SET onlist = COALESCE(active_status, 1) WHERE onlist IS NULL");
            tryExecute("ALTER TABLE courses MODIFY COLUMN active_status TINYINT(1) NOT NULL DEFAULT 1");
            tryExecute("ALTER TABLE courses MODIFY COLUMN onlist TINYINT(1) NOT NULL DEFAULT 1");
            tryExecute("ALTER TABLE departments ADD COLUMN department_code VARCHAR(20) NULL");
            tryExecute("ALTER TABLE class_sections ADD COLUMN faculty_id INT NULL");
            tryExecute("ALTER TABLE class_sections ADD COLUMN max_capacity INT NOT NULL DEFAULT 40");
            tryExecute("ALTER TABLE class_sections ADD COLUMN section_status VARCHAR(30) NOT NULL DEFAULT 'Open'");
            tryExecute("ALTER TABLE class_sections ADD COLUMN semester_number INT NULL");
            tryExecute("ALTER TABLE class_sections ADD COLUMN block_id INT NULL");
            tryExecute("ALTER TABLE class_schedules ADD COLUMN section_id INT NULL");
            tryExecute("ALTER TABLE class_schedules ADD COLUMN room_id INT NULL");
            tryExecute("ALTER TABLE class_schedules ADD COLUMN schedule_type VARCHAR(30) NULL");
            tryExecute("ALTER TABLE class_schedules ADD COLUMN is_unlocked TINYINT(1) DEFAULT 0");
            tryExecute("ALTER TABLE class_schedules ADD COLUMN faculty_id INT NULL");
            tryExecute("ALTER TABLE class_schedules MODIFY COLUMN day_of_week INT NULL");
        } catch (Exception e) {
            System.err.println("Academic builder schema setup failed: " + e.getMessage());
        }
    }

    private void tryExecute(String sql) {
        try {
            db.execute(sql);
        } catch (Exception ignored) {
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
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN display_name VARCHAR(100) NULL"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN discount_mode VARCHAR(20) NOT NULL DEFAULT 'PERCENT'"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN default_discount_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN default_scholarship_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN is_internal TINYINT(1) DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN requires_id TINYINT(1) DEFAULT 1"); } catch (Exception ignored) {}
            try { db.execute("ALTER TABLE scholarship_types ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1"); } catch (Exception ignored) {}
            seedScholarshipType("ACADEMIC", "Academic Scholarship", "FULL", 100.0, 0.0, true);
            seedScholarshipType("BARANGAY", "Barangay Scholarship", "PERCENT", 50.0, 0.0, false);
            seedScholarshipType("LGU", "LGU Scholarship", "PERCENT", 50.0, 0.0, false);
            seedScholarshipType("ATHLETE", "Athlete Scholarship", "FULL", 100.0, 0.0, true);
            seedScholarshipType("EMPLOYEE_DEPENDENT", "Employee Dependent", "PERCENT", 50.0, 0.0, false);
            seedScholarshipType("OTHER", "Other / Miscellaneous", "FLAT", 0.0, 0.0, false);
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
        try { db.execute("ALTER TABLE grade_change_requests ADD COLUMN request_type VARCHAR(40) NOT NULL DEFAULT 'FINAL_GRADE_CORRECTION'"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grade_change_requests ADD COLUMN requested_prelim DECIMAL(5,2) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grade_change_requests ADD COLUMN requested_midterm DECIMAL(5,2) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grade_change_requests ADD COLUMN requested_finals DECIMAL(5,2) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grade_change_requests ADD COLUMN applied_action VARCHAR(80) NULL"); } catch (Exception ignored) {}
        try { db.execute("ALTER TABLE grade_change_requests ADD COLUMN approved_at TIMESTAMP NULL"); } catch (Exception ignored) {}
    }

    private void ensureUserPassword(String username, String rawPassword, String role) {
        try {
            String newHash = org.mindrot.jbcrypt.BCrypt.hashpw(rawPassword, org.mindrot.jbcrypt.BCrypt.gensalt());
            List<Map<String, Object>> users = db.queryForList("SELECT * FROM sys_users WHERE username = ?", username);
            if (!users.isEmpty()) {
                db.update("UPDATE sys_users SET password = ? WHERE username = ?", newHash, username);
            } else {
                // User doesn't exist yet — create it
                db.update("INSERT INTO sys_users (username, password, real_name, role, is_active) VALUES (?, ?, ?, ?, 1)",
                          username, newHash, username, role);
            }
        } catch (Exception e) {
            System.err.println("ensureUserPassword failed for " + username + ": " + e.getMessage());
        }
    }
}



