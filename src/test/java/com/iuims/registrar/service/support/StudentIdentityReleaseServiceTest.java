package com.iuims.registrar.service.support;
import com.iuims.registrar.entity.Student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class StudentIdentityReleaseServiceTest {

    private JdbcTemplate db;
    private StudentProfileService studentProfileService;
    private StudentIdentityReleaseService service;

    @BeforeEach
    void setUp() {
        db = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:release-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        createSchema();
        studentProfileService = new StudentProfileService(db);
        service = new StudentIdentityReleaseService(db, studentProfileService);
    }

    @Test
    void releaseWithdrawnStudentMovesHistoricalRowsToArchiveKeyAndMarksNumberAvailable() {
        db.update("""
            INSERT INTO students (student_number, reference_number, first_name, last_name, real_name,
                program_code, year_level, semester, term_year, student_type, admission_status, status, is_active, enrollment_blocked)
            VALUES ('26-1-00001', 'REF-1001', 'Liam', 'Lopez', 'Liam Lopez',
                'BSIT', 2, 1, 'SL2024202521', 'Regular', 'WITHDRAWN', 'WITHDRAWN', 0, 1)
            """);
        db.update("""
            INSERT INTO sys_users (username, role, admission_status, status, is_active, reference_number)
            VALUES ('26-1-00001', 'Student', 'WITHDRAWN', 'INACTIVE', 0, 'REF-1001')
            """);
        db.update("INSERT INTO student_ledger (student_id, transaction_type, description, credit) VALUES ('26-1-00001', 'PAYMENT', 'Legacy', 500)");
        db.update("INSERT INTO payments (reference_number, amount, status) VALUES ('26-1-00001', 500, 'COMPLETED')");
        db.update("INSERT INTO student_document_events (student_number, document_scope, document_type, event_type, event_summary) VALUES ('26-1-00001', 'STUDENT', 'WITHDRAWAL', 'WITHDRAWAL_COMPLETED', 'done')");
        db.update("INSERT INTO student_archive_files (student_number, archive_status, retention_policy_code) VALUES ('26-1-00001', 'WITHDRAWN_FILE', 'PERMANENT')");
        db.update("INSERT INTO student_withdrawal_requests (student_number, section_id, course_id, reason_code, status) VALUES ('26-1-00001', 1, 1, 'TRANSFER', 'APPROVED')");

        StudentIdentityReleaseService.ReleaseResult result =
            service.releaseWithdrawnStudentNumber("26-1-00001", "registrar.main", "Ready for archival release");

        assertThat(result.ok()).isTrue();
        assertThat(result.archiveKey()).startsWith("ARCH-");
        assertThat(db.queryForObject("SELECT student_number FROM students", String.class)).isEqualTo(result.archiveKey());
        assertThat(db.queryForObject("SELECT username FROM sys_users", String.class)).isEqualTo(result.archiveKey());
        assertThat(db.queryForObject("SELECT student_id FROM student_ledger", String.class)).isEqualTo(result.archiveKey());
        assertThat(db.queryForObject("SELECT reference_number FROM payments", String.class)).isEqualTo(result.archiveKey());
        assertThat(db.queryForObject("SELECT student_number FROM student_document_events", String.class)).isEqualTo(result.archiveKey());
        assertThat(db.queryForObject("SELECT student_number FROM student_archive_files", String.class)).isEqualTo(result.archiveKey());
        assertThat(db.queryForObject("SELECT student_number FROM student_withdrawal_requests", String.class)).isEqualTo(result.archiveKey());
        assertThat(db.queryForMap("SELECT released_student_number, archive_key, release_status FROM student_number_release_registry"))
            .containsEntry("RELEASED_STUDENT_NUMBER", "26-1-00001")
            .containsEntry("ARCHIVE_KEY", result.archiveKey())
            .containsEntry("RELEASE_STATUS", "AVAILABLE");
    }

    @Test
    void activeStudentCannotBeReleased() {
        db.update("""
            INSERT INTO students (student_number, reference_number, admission_status, status, is_active)
            VALUES ('26-1-00002', 'REF-1002', 'ENROLLED', 'ACTIVE', 1)
            """);

        StudentIdentityReleaseService.ReleaseResult result =
            service.releaseWithdrawnStudentNumber("26-1-00002", "registrar.main", "Should fail");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).contains("Only withdrawn students");
    }

    private void createSchema() {
        db.execute("CREATE TABLE students (student_number VARCHAR(100) PRIMARY KEY, reference_number VARCHAR(100), archive_key VARCHAR(80), first_name VARCHAR(100), last_name VARCHAR(100), real_name VARCHAR(200), program_code VARCHAR(100), year_level INT, semester INT, term_year VARCHAR(50), student_type VARCHAR(50), admission_status VARCHAR(50), status VARCHAR(50), is_active INT, enrollment_blocked INT, email VARCHAR(150), mobile VARCHAR(50))");
        db.execute("CREATE TABLE sys_users (username VARCHAR(100) PRIMARY KEY, role VARCHAR(30), admission_status VARCHAR(50), status VARCHAR(50), is_active INT, reference_number VARCHAR(100))");
        db.execute("CREATE TABLE student_ledger (ledger_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id VARCHAR(100), transaction_type VARCHAR(40), description VARCHAR(255), credit DECIMAL(12,2))");
        db.execute("CREATE TABLE payments (payment_id BIGINT AUTO_INCREMENT PRIMARY KEY, reference_number VARCHAR(100), amount DECIMAL(12,2), status VARCHAR(40))");
        db.execute("CREATE TABLE student_document_events (event_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_number VARCHAR(100), archive_key VARCHAR(80), reference_number VARCHAR(100), document_scope VARCHAR(40), document_type VARCHAR(60), event_type VARCHAR(80), event_summary VARCHAR(180))");
        db.execute("CREATE TABLE student_archive_files (student_number VARCHAR(100) PRIMARY KEY, archive_key VARCHAR(80), archive_status VARCHAR(40), retention_policy_code VARCHAR(80))");
        db.execute("CREATE TABLE withdrawal_reasons (reason_code VARCHAR(40) PRIMARY KEY, reason_label VARCHAR(160))");
        db.update("INSERT INTO withdrawal_reasons (reason_code, reason_label) VALUES ('TRANSFER', 'Transfer')");
        db.execute("CREATE TABLE student_withdrawal_requests (request_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_number VARCHAR(100), archive_key VARCHAR(80), section_id INT, course_id INT, reason_code VARCHAR(40), status VARCHAR(40))");
    }
}
