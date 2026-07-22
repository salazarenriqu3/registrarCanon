package com.iuims.registrar.service.forms;
import com.iuims.registrar.entity.Student;

import com.iuims.registrar.service.admission.FinanceAdmissionService;
import com.iuims.registrar.service.support.EnlistmentSchemaService;
import com.iuims.registrar.service.support.StudentProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.SQLException;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RegFormVersionServiceTest {

    private JdbcTemplate db;
    private StudentProfileService studentProfileService;
    private FinanceAdmissionService financeService;
    private RegistrationFormPdfService registrationFormPdfService;
    private RegFormVersionService service;

    @BeforeEach
    void setUp() {
        db = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:reg-form-versions;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("DROP ALL OBJECTS");
        db.execute("CREATE ALIAS IF NOT EXISTS TIME_FORMAT FOR \"com.iuims.registrar.service.forms.RegFormVersionServiceTest.timeFormat\"");
        db.execute("CREATE TABLE students (student_number VARCHAR(100) PRIMARY KEY, reference_number VARCHAR(100), real_name VARCHAR(150), student_type VARCHAR(40), enrollment_status_type VARCHAR(40), admission_status VARCHAR(40), program_code VARCHAR(20), year_level INT, semester INT, term_year VARCHAR(30))");
        db.execute("CREATE TABLE academic_terms (term_id INT PRIMARY KEY, term_code VARCHAR(30), is_active INT, status VARCHAR(20))");
        db.execute("CREATE TABLE courses (course_id INT PRIMARY KEY, course_code VARCHAR(20), course_title VARCHAR(150), credit_units DECIMAL(8,2))");
        db.execute("CREATE TABLE class_sections (section_id INT PRIMARY KEY, course_id INT, section_code VARCHAR(40), term_id INT)");
        db.execute("CREATE TABLE rooms (room_id INT PRIMARY KEY, room_code VARCHAR(40))");
        db.execute("CREATE TABLE class_schedules (schedule_id INT PRIMARY KEY, section_id INT, day_of_week INT, start_time TIME, end_time TIME, room_id INT)");
        db.execute("CREATE TABLE student_enlistments (enlistment_id INT AUTO_INCREMENT PRIMARY KEY, student_id VARCHAR(100), course_id INT, section_id INT, enlistment_status VARCHAR(20))");

        studentProfileService = mock(StudentProfileService.class);
        financeService = mock(FinanceAdmissionService.class);
        registrationFormPdfService = mock(RegistrationFormPdfService.class);

        service = new RegFormVersionService(
            db,
            studentProfileService,
            financeService,
            registrationFormPdfService,
            new EnlistmentSchemaService(db)
        );
        service.ensureSchema();
    }

    @Test
    void saveSnapshotPersistsImmutableJsonPayload() {
        when(studentProfileService.ensureArchiveKey("2026-0001")).thenReturn("ARCH-2026-0001");

        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("courseCode", "BSIT 101");
        subject.put("units", 3.0);
        List<Map<String, Object>> subjects = new ArrayList<>();
        subjects.add(subject);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("semesterSchoolYearLine", "First Semester, A.Y. 2026-2027");
        snapshot.put("totalUnits", 3.0);
        snapshot.put("subjects", subjects);

        Long versionId = service.saveSnapshot(
            "2026-0001",
            "CURRENT_PRINT",
            "Current registration form printed",
            null,
            "Saved from print route",
            "registrar.main",
            snapshot);

        subject.put("courseCode", "MUTATED 999");

        Map<String, Object> stored = service.findVersion(versionId);

        assertThat(stored.get("student_number")).isEqualTo("2026-0001");
        assertThat(stored.get("source_event_type")).isEqualTo("CURRENT_PRINT");
        @SuppressWarnings("unchecked")
        Map<String, Object> storedSnapshot = (Map<String, Object>) stored.get("snapshot");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> storedSubjects = (List<Map<String, Object>>) storedSnapshot.get("subjects");
        assertThat(storedSubjects.get(0).get("courseCode")).isEqualTo("BSIT 101");
    }

    @Test
    void captureVersionBuildsSnapshotFromCurrentRegistrarState() {
        db.update(
            "INSERT INTO students (student_number, reference_number, real_name, student_type, enrollment_status_type, admission_status, program_code, year_level, semester, term_year) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            "2026-0001", "REF-2026-0001", "Maria Santos", "New Student", "REGULAR", "ENROLLED", "BSIT", 1, 1, "SL2024202511");
        db.update("INSERT INTO academic_terms (term_id, term_code, is_active, status) VALUES (1, '1120242025', 1, 'ACTIVE')");
        db.update("INSERT INTO courses (course_id, course_code, course_title, credit_units) VALUES (10, 'BSIT 101', 'Intro to IT', 3)");
        db.update("INSERT INTO class_sections (section_id, course_id, section_code, term_id) VALUES (20, 10, 'BSIT-1-A', 1)");
        db.update("INSERT INTO rooms (room_id, room_code) VALUES (30, 'R101')");
        db.update("INSERT INTO class_schedules (schedule_id, section_id, day_of_week, start_time, end_time, room_id) VALUES (?, ?, ?, ?, ?, ?)",
            40, 20, 1, Time.valueOf("08:00:00"), Time.valueOf("09:00:00"), 30);
        db.update("INSERT INTO student_enlistments (student_id, course_id, section_id, enlistment_status) VALUES (?, ?, ?, ?)",
            "2026-0001", 10, 20, "COMMITTED");

        when(studentProfileService.ensureArchiveKey("2026-0001")).thenReturn("ARCH-2026-0001");
        Map<String, Object> finance = Map.of("total_assessment", 18000.0, "balance", 15000.0);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("semesterSchoolYearLine", "First Semester, A.Y. 2024-2025");
        snapshot.put("totalUnits", 3.0);
        snapshot.put("subjects", List.of(Map.of("courseCode", "BSIT 101")));
        when(financeService.calculateAssessment("2026-0001")).thenReturn(finance);
        when(registrationFormPdfService.buildSnapshot(anyMap(), anyList(), eq(finance), eq(""), eq("registrar.main")))
            .thenReturn(snapshot);

        Long versionId = service.captureVersion(
            "2026-0001",
            "SUBJECT_ADD",
            "Registrar subject add completed",
            55L,
            "Added BSIT 101",
            "registrar.main");

        Map<String, Object> stored = service.findVersion(versionId);

        assertThat(stored.get("source_event_id")).isEqualTo(55L);
        assertThat(stored.get("source_event_type")).isEqualTo("SUBJECT_ADD");
        assertThat(stored.get("subject_count")).isEqualTo(1);
        verify(registrationFormPdfService).buildSnapshot(anyMap(), anyList(), eq(finance), eq(""), eq("registrar.main"));
    }

    @Test
    void captureVersionReturnsExistingVersionForTheSameSourceEvent() {
        when(studentProfileService.ensureArchiveKey("2026-0001")).thenReturn("ARCH-2026-0001");
        Map<String, Object> snapshot = Map.of(
            "semesterSchoolYearLine", "First Semester, A.Y. 2026-2027",
            "totalUnits", 3.0,
            "subjects", List.of(Map.of("courseCode", "BSIT 101")));

        Long firstVersionId = service.saveSnapshot(
            "2026-0001", "SUBJECT_ADD", "Subject added", 77L, null, "registrar", snapshot);
        Long retriedVersionId = service.saveSnapshot(
            "2026-0001", "SUBJECT_ADD", "Subject added", 77L, null, "registrar", snapshot);

        assertThat(retriedVersionId).isEqualTo(firstVersionId);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_reg_form_versions WHERE source_event_id = 77", Integer.class)).isEqualTo(1);
    }

    public static String timeFormat(Time value, String pattern) throws SQLException {
        if (value == null) {
            return null;
        }
        String normalized = pattern != null && pattern.equalsIgnoreCase("%h:%i %p")
            ? "hh:mm a"
            : "HH:mm:ss";
        return new SimpleDateFormat(normalized, Locale.US).format(value);
    }
}
