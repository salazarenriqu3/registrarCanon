package com.iuims.registrar.admission;

import com.iuims.registrar.core.GlobalTermService;
import com.iuims.registrar.finance.TermFeeAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApplicantPreRegSnapshotServiceCompatibilityTest {

    private JdbcTemplate db;
    private ApplicantPreRegSnapshotService service;

    @BeforeEach
    void setUp() {
        db = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:pre_reg_compat_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        service = new ApplicantPreRegSnapshotService(
            db,
            mock(TermFeeAdminService.class),
            mock(GlobalTermService.class));

        db.execute("""
            CREATE TABLE applicants (
                id BIGINT PRIMARY KEY,
                reference_number VARCHAR(64) NOT NULL,
                program1 VARCHAR(32),
                final_program_code VARCHAR(32),
                last_school VARCHAR(255),
                course_taken VARCHAR(255),
                admission_classification VARCHAR(64),
                year_level INT
            )
            """);
        db.execute("""
            CREATE TABLE applicant_pre_reg_snapshots (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                applicant_id BIGINT,
                reference_number VARCHAR(64) NOT NULL,
                term_id INT NOT NULL,
                semester_number INT NOT NULL,
                school_year VARCHAR(32),
                semester_label VARCHAR(64),
                program_code VARCHAR(32) NOT NULL,
                program_name VARCHAR(255),
                year_level INT NOT NULL DEFAULT 1,
                total_units DECIMAL(8,2) NOT NULL DEFAULT 0,
                tuition_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
                misc_total DECIMAL(12,2) NOT NULL DEFAULT 0,
                misc_amount DECIMAL(12,2),
                other_total DECIMAL(12,2) NOT NULL DEFAULT 0,
                rle_fee DECIMAL(12,2) NOT NULL DEFAULT 0,
                total_assessment DECIMAL(12,2) NOT NULL DEFAULT 0,
                assessment_amount DECIMAL(12,2),
                downpayment_required DECIMAL(12,2) NOT NULL DEFAULT 0,
                snapshot_at TIMESTAMP NOT NULL,
                snapshot_source VARCHAR(16),
                snapshot_status VARCHAR(20),
                evaluation_finalized_at TIMESTAMP,
                updated_at TIMESTAMP
            )
            """);
        db.execute("""
            CREATE TABLE applicant_pre_reg_subject_lines (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                snapshot_id BIGINT NOT NULL,
                line_order INT NOT NULL,
                course_id BIGINT,
                course_code VARCHAR(32) NOT NULL,
                course_title VARCHAR(255),
                section_id BIGINT,
                section_code VARCHAR(64),
                schedule_text VARCHAR(255),
                units DECIMAL(5,2) NOT NULL DEFAULT 0,
                year_level INT,
                semester_number INT
            )
            """);

        db.update("""
            INSERT INTO applicants (
                id, reference_number, program1, final_program_code, last_school, course_taken, admission_classification, year_level
            ) VALUES (1, 'IRREG-001', 'BSIT', 'BSIT', 'Prior College', 'Old Program', 'TRANSFEREE', 2)
            """);
        db.update("""
            INSERT INTO applicant_pre_reg_snapshots (
                applicant_id, reference_number, term_id, semester_number, school_year, semester_label,
                program_code, program_name, year_level, total_units, tuition_amount, misc_total, misc_amount,
                other_total, rle_fee, total_assessment, assessment_amount, downpayment_required,
                snapshot_at, snapshot_source, snapshot_status, evaluation_finalized_at, updated_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, CURRENT_TIMESTAMP, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            1L, "IRREG-001", 1, 1, "2024-2025", "1st Semester",
            "BSIT", "BSIT", 2, 3, 1500, 100, 100,
            50, 0, 1650, 1650, 1000,
            "ENR_PRE_ADVISE", "FINAL");
        db.update("""
            INSERT INTO applicant_pre_reg_subject_lines (
                snapshot_id, line_order, course_id, course_code, course_title, section_id, section_code, schedule_text, units, year_level, semester_number
            ) VALUES (1, 1, 101, 'IT101', 'Intro to IT', 5001, 'BSIT-1-1', 'M 07:30-09:00', 3, 2, 1)
            """);
    }

    @Test
    void sharedSnapshotPreviewReadsEnrollmentOwnedRows() {
        Map<String, Object> snapshot = service.findSnapshotByReference("IRREG-001");

        assertThat(snapshot)
            .containsEntry("exists", true)
            .containsEntry("ready", true)
            .containsEntry("finalized", true);
        assertThat((Map<String, Object>) snapshot.get("snapshot"))
            .containsEntry("SNAPSHOT_SOURCE", "ENR_PRE_ADVISE");
        assertThat((List<Map<String, Object>>) snapshot.get("subject_lines")).hasSize(1);
    }

    @Test
    void validateRegistrarSnapshotReadyAcceptsEnrollmentOwnedSnapshot() {
        String result = service.validateRegistrarSnapshotReady("IRREG-001", "BSIT");

        assertThat(result).isNull();
    }
}
