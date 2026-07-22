package com.iuims.registrar.service.forms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.iuims.registrar.service.support.StudentProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StudentArchiveCustodyServiceTest {

    private JdbcTemplate db;
    private StudentArchiveCustodyService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:archivecustody" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        db = new JdbcTemplate(dataSource);
        StudentProfileService studentProfileService = new StudentProfileService(db);
        StudentDocumentTrailService trailService = new StudentDocumentTrailService(db, studentProfileService);
        service = new StudentArchiveCustodyService(db, studentProfileService, trailService);
    }

    @Test
    void recordsCustodyMovementAndMirrorsDocumentTrail() {
        String result = service.recordEvent(
            "2026-0001",
            "RELEASED",
            "registrar.one",
            "evaluator.one",
            "TOR generation",
            "EAC Cavite records room",
            "Released with folder checklist.");

        assertThat(result).isEqualTo("SUCCESS");

        Map<String, Object> summary = service.getSummary("2026-0001");
        assertThat(summary.get("archive_status")).isEqualTo("RELEASED");
        assertThat(summary.get("current_holder")).isEqualTo("evaluator.one");

        Integer custodyEvents = db.queryForObject(
            "SELECT COUNT(*) FROM student_archive_custody_events WHERE student_number = '2026-0001'",
            Integer.class);
        assertThat(custodyEvents).isEqualTo(1);

        Integer trailEvents = db.queryForObject(
            "SELECT COUNT(*) FROM student_document_events WHERE student_number = '2026-0001' AND document_type = 'ARCHIVE_CUSTODY'",
            Integer.class);
        assertThat(trailEvents).isEqualTo(1);
    }
}
