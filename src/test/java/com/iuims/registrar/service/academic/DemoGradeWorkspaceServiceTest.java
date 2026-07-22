package com.iuims.registrar.service.academic;
import com.iuims.registrar.entity.Grade;
import com.iuims.registrar.entity.Student;

import com.iuims.registrar.service.support.EnlistmentSchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@Import({DemoGradeWorkspaceService.class, EnlistmentSchemaService.class})
class DemoGradeWorkspaceServiceTest {

    @MockBean
    private GradeRecordEventService gradeRecordEventService;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private EnlistmentSchemaService enlistmentSchemaService;

    @Autowired
    private DemoGradeWorkspaceService service;

    @BeforeEach
    void setUp() {
        db.execute("DROP ALL OBJECTS");
        db.execute("""
            CREATE TABLE academic_terms (
                term_id INT PRIMARY KEY,
                term_name VARCHAR(100) NULL,
                academic_year VARCHAR(20) NULL,
                semester_number INT NULL,
                is_active TINYINT NOT NULL DEFAULT 0
            )
            """);
        db.execute("""
            CREATE TABLE students (
                student_number VARCHAR(50) PRIMARY KEY,
                real_name VARCHAR(150) NULL,
                first_name VARCHAR(100) NULL,
                last_name VARCHAR(100) NULL,
                program_code VARCHAR(20) NULL,
                year_level INT NULL,
                semester INT NULL,
                admission_status VARCHAR(30) NULL,
                status VARCHAR(30) NULL,
                is_active TINYINT DEFAULT 1
            )
            """);
        db.execute("""
            CREATE TABLE courses (
                course_id INT PRIMARY KEY,
                course_code VARCHAR(20) NULL,
                course_title VARCHAR(150) NULL
            )
            """);
        db.execute("""
            CREATE TABLE class_sections (
                section_id INT PRIMARY KEY,
                course_id INT NOT NULL,
                term_id INT NOT NULL,
                section_code VARCHAR(50) NULL
            )
            """);
        db.execute("""
            CREATE TABLE student_enlistments (
                enlistment_id INT AUTO_INCREMENT PRIMARY KEY,
                student_id VARCHAR(50) NOT NULL,
                course_id INT NOT NULL,
                section_id INT NOT NULL,
                enlistment_status VARCHAR(20) NULL
            )
            """);
        db.execute("""
            CREATE TABLE grades (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                student_id VARCHAR(100) NOT NULL,
                section_id INT NULL,
                course_id INT NULL,
                student_name VARCHAR(100) NULL,
                prelim DECIMAL(5,2) NULL,
                midterm DECIMAL(5,2) NULL,
                final_grade DECIMAL(5,2) NULL,
                semestral_grade DECIMAL(5,2) NULL,
                remarks VARCHAR(30) NULL,
                previous_grade VARCHAR(20) NULL,
                grade_lock_status VARCHAR(30) NULL,
                grade_lock_reason VARCHAR(120) NULL,
                registrar_final_grade DECIMAL(5,2) NULL,
                registrar_final_remarks VARCHAR(30) NULL,
                registrar_finalized_at TIMESTAMP NULL,
                curriculum_year INT NULL,
                grade DOUBLE NULL,
                date_recorded TIMESTAMP NULL,
                status VARCHAR(20) NULL
            )
            """);
        db.update("INSERT INTO academic_terms (term_id, term_name, academic_year, semester_number, is_active) VALUES (1, 'A.Y. 2026-2027 - 1st Semester', '2026-2027', 1, 1)");
        db.update("INSERT INTO students (student_number, real_name, first_name, last_name, program_code, year_level, semester, admission_status, status, is_active) VALUES ('2026-0001', 'Demo Student', 'Demo', 'Student', 'BSIT', 2, 1, 'ENROLLED', 'ACTIVE', 1)");
        db.update("INSERT INTO courses (course_id, course_code, course_title) VALUES (101, 'IT 101', 'Intro to IT'), (102, 'IT 102', 'Programming 2')");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code) VALUES (201, 101, 1, 'IT101-A'), (202, 102, 1, 'IT102-B')");
        db.update("INSERT INTO student_enlistments (student_id, course_id, section_id, enlistment_status) VALUES ('2026-0001', 101, 201, 'COMMITTED'), ('2026-0001', 102, 202, 'STAGED')");
        ReflectionTestUtils.setField(enlistmentSchemaService, "enlistmentStatusColumn", true);
    }

    @Test
    void getRowsReturnsCommittedEnrollmentsOnly() {
        db.update("""
            INSERT INTO grades (student_id, section_id, course_id, student_name, prelim, midterm, final_grade, semestral_grade, remarks, status)
            VALUES ('2026-0001', 201, 101, 'Demo Student', 95, 91, 89, 2.00, 'Ongoing', 'DRAFT')
            """);

        List<Map<String, Object>> rows = service.getRows("2026-0001", 1);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("section_id")).isEqualTo(201);
        assertThat(rows.get(0).get("workflow_state")).isEqualTo("DRAFT");
        assertThat(rows.get(0).get("display_grade")).isEqualTo("2.00");
    }

    @Test
    void demoWorkflowSupportsSaveSubmitApproveAndReject() {
        String saveResult = service.saveDraft(
            "2026-0001",
            201,
            "96",
            "92",
            "88",
            null,
            "dean.user",
            "Dean");
        assertThat(saveResult).startsWith("SUCCESS");

        Map<String, Object> draft = db.queryForMap(
            "SELECT status, semestral_grade, remarks, grade_lock_status, grade_lock_reason FROM grades WHERE student_id = '2026-0001' AND section_id = 201");
        assertThat(draft.get("status")).isEqualTo("DRAFT");
        assertThat(((Number) draft.get("semestral_grade")).doubleValue()).isEqualTo(1.5);
        assertThat(draft.get("remarks")).isEqualTo("Passed");
        assertThat(draft.get("grade_lock_status")).isNull();

        String submitResult = service.submit("2026-0001", 201, "dean.user", "Dean");
        assertThat(submitResult).startsWith("SUCCESS");

        Map<String, Object> submitted = db.queryForMap(
            "SELECT status, semestral_grade, remarks FROM grades WHERE student_id = '2026-0001' AND section_id = 201");
        assertThat(submitted.get("status")).isEqualTo("SUBMITTED");

        String approveResult = service.approve("2026-0001", 201, "registrar.user", "Registrar");
        assertThat(approveResult).startsWith("SUCCESS");

        Map<String, Object> approved = db.queryForMap(
            "SELECT status, registrar_final_grade, registrar_final_remarks, grade_lock_status, grade_lock_reason FROM grades WHERE student_id = '2026-0001' AND section_id = 201");
        assertThat(approved.get("status")).isEqualTo("SUBMITTED");
        assertThat(((Number) approved.get("registrar_final_grade")).doubleValue()).isEqualTo(1.5);
        assertThat(approved.get("registrar_final_remarks")).isEqualTo("Passed");
        assertThat(approved.get("grade_lock_status")).isEqualTo("FINALIZED");
        assertThat(approved.get("grade_lock_reason")).isEqualTo("DEMO_GRADE_APPROVED");

        db.update("UPDATE student_enlistments SET enlistment_status = 'COMMITTED' WHERE student_id = '2026-0001' AND section_id = 202");
        String quickSave = service.saveDraft(
            "2026-0001",
            202,
            null,
            null,
            null,
            "1.75",
            "dean.user",
            "Dean");
        assertThat(quickSave).startsWith("SUCCESS");
        assertThat(service.submit("2026-0001", 202, "dean.user", "Dean")).startsWith("SUCCESS");

        String rejectResult = service.reject("2026-0001", 202, "registrar.user", "Registrar", "Needs another pass");
        assertThat(rejectResult).startsWith("SUCCESS");

        Map<String, Object> rejected = db.queryForMap(
            "SELECT status, grade_lock_status, grade_lock_reason, registrar_final_grade FROM grades WHERE student_id = '2026-0001' AND section_id = 202");
        assertThat(rejected.get("status")).isEqualTo("DRAFT");
        assertThat(rejected.get("grade_lock_status")).isNull();
        assertThat(String.valueOf(rejected.get("grade_lock_reason"))).startsWith("DEMO_GRADE_REJECTED");
        assertThat(rejected.get("registrar_final_grade")).isNull();
    }
}
