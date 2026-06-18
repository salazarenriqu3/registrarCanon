package com.iuims.registrar.admission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicantDocumentReadServiceTest {

    @TempDir
    Path uploadRoot;

    private JdbcTemplate db;
    private ApplicantDocumentReadService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:applicantdocs" + System.nanoTime()
            + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        db = new JdbcTemplate(dataSource);

        db.execute("""
            CREATE TABLE students (
                student_number VARCHAR(100) PRIMARY KEY,
                reference_number VARCHAR(100)
            )
            """);
        db.execute("""
            CREATE TABLE applicants (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                reference_number VARCHAR(100) UNIQUE,
                applicant_status VARCHAR(50),
                term_year VARCHAR(30),
                application_track VARCHAR(32),
                program1 VARCHAR(20),
                program2 VARCHAR(20),
                email_verified TINYINT DEFAULT 0,
                remarks TEXT,
                form138_path VARCHAR(255),
                form138_verified TINYINT DEFAULT 0,
                good_moral_path VARCHAR(255),
                good_moral_verified TINYINT DEFAULT 0
            )
            """);
        service = new ApplicantDocumentReadService(db, uploadRoot.toString());
    }

    @Test
    void readsAdmissionSnapshotAndLegacyDocumentsThroughStudentReference() {
        db.update("""
            INSERT INTO applicants (
                reference_number, applicant_status, term_year, application_track,
                program1, program2, email_verified, remarks,
                form138_path, form138_verified, good_moral_path, good_moral_verified
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            "APP-001", "QUALIFIED FOR ENROLLMENT", "2026-2027_1st", "REGULAR",
            "BSIT", "BSCS", 1, "Admission note", "form138.pdf", 1, null, 0);
        db.update("INSERT INTO students (student_number, reference_number) VALUES (?, ?)",
            "26-1-00001", "APP-001");

        Map<String, Object> snapshot = service.getAdmissionSnapshot("26-1-00001");
        List<Map<String, Object>> documents = service.listDocuments("26-1-00001");

        assertThat(snapshot)
            .containsEntry("reference_number", "APP-001")
            .containsEntry("applicant_status", "QUALIFIED FOR ENROLLMENT")
            .containsEntry("program1", "BSIT")
            .containsEntry("remarks", "Admission note");
        assertThat(documents).hasSize(2);
        assertThat(documents.get(0))
            .containsEntry("label", "Form 138 / Report Card")
            .containsEntry("submitted", true)
            .containsEntry("verified", true)
            .containsEntry("document_key", "legacy:form138");
        assertThat(service.resolveDocumentPath("26-1-00001", "legacy:form138"))
            .isEqualTo(uploadRoot.resolve("form138.pdf").toAbsolutePath().normalize());
    }

    @Test
    void prefersConfiguredRequirementRowsAndChecksApplicantOwnership() {
        db.execute("""
            CREATE TABLE requirement_upload_definitions (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                application_track VARCHAR(32),
                slot_key VARCHAR(64),
                display_label VARCHAR(255),
                active TINYINT,
                required TINYINT,
                sort_order INT
            )
            """);
        db.execute("""
            CREATE TABLE student_requirement_files (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                applicant_id BIGINT,
                definition_id BIGINT,
                stored_path TEXT,
                verified TINYINT
            )
            """);
        db.update("INSERT INTO applicants (reference_number, application_track, form138_path) VALUES (?, ?, ?)",
            "APP-002", "REGULAR", "legacy.pdf");
        db.update("INSERT INTO applicants (reference_number, application_track) VALUES (?, ?)",
            "APP-OTHER", "REGULAR");
        db.update("INSERT INTO students (student_number, reference_number) VALUES (?, ?)",
            "26-1-00002", "APP-002");
        db.update("INSERT INTO students (student_number, reference_number) VALUES (?, ?)",
            "26-1-00003", "APP-OTHER");
        db.update("""
            INSERT INTO requirement_upload_definitions
                (application_track, slot_key, display_label, active, required, sort_order)
            VALUES ('REGULAR', 'tor', 'Transcript of Records', 1, 1, 1),
                   ('REGULAR', 'photo', 'Applicant Photo', 1, 1, 2)
            """);
        Long applicantId = db.queryForObject(
            "SELECT id FROM applicants WHERE reference_number = 'APP-002'", Long.class);
        db.update("""
            INSERT INTO student_requirement_files
                (applicant_id, definition_id, stored_path, verified)
            VALUES (?, 1, 'tor.pdf', 0)
            """, applicantId);

        List<Map<String, Object>> documents = service.listDocuments("26-1-00002");

        assertThat(documents).hasSize(2);
        assertThat(documents.get(0))
            .containsEntry("label", "Transcript of Records")
            .containsEntry("submitted", true)
            .containsEntry("verified", false)
            .containsEntry("document_key", "normalized:1");
        assertThat(documents.get(1)).containsEntry("submitted", false);
        assertThat(service.resolveDocumentPath("26-1-00002", "normalized:1"))
            .isEqualTo(uploadRoot.resolve("tor.pdf").toAbsolutePath().normalize());
        assertThat(service.resolveDocumentPath("26-1-00003", "normalized:1")).isNull();
    }

    @Test
    void returnsEmptyCollectionsWhenStudentHasNoAdmissionReference() {
        db.update("INSERT INTO students (student_number, reference_number) VALUES (?, NULL)", "26-1-00004");

        assertThat(service.getAdmissionSnapshot("26-1-00004")).isEmpty();
        assertThat(service.listDocuments("26-1-00004")).isEmpty();
        assertThat(service.resolveDocumentPath("26-1-00004", "legacy:form138")).isNull();
    }
}
