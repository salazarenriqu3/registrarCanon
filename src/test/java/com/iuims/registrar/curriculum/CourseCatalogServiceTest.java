package com.iuims.registrar.curriculum;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

class CourseCatalogServiceTest {

    private JdbcTemplate db;
    private CourseCatalogService service;

    @BeforeEach
    void setUp() {
        db = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:course-catalog;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("DROP ALL OBJECTS");
        db.execute("CREATE TABLE departments (department_id INT PRIMARY KEY, department_code VARCHAR(20), department_name VARCHAR(150))");
        db.execute("CREATE TABLE courses (course_id INT AUTO_INCREMENT PRIMARY KEY, course_code VARCHAR(20) UNIQUE, course_title VARCHAR(150), department_id INT, credit_units INT, lec_units INT, lab_units INT, active_status INT)");
        db.execute("CREATE TABLE programs (program_id INT PRIMARY KEY, program_code VARCHAR(20), program_name VARCHAR(150))");
        db.execute("CREATE TABLE curriculum_templates (curriculum_id INT PRIMARY KEY, program_id INT, curriculum_name VARCHAR(100), academic_year VARCHAR(20), approval_status VARCHAR(20), is_active INT)");
        db.execute("CREATE TABLE curriculum_courses (curriculum_course_id INT AUTO_INCREMENT PRIMARY KEY, curriculum_id INT, course_id INT, year_level INT, semester_number INT)");
        db.execute("CREATE TABLE class_sections (section_id INT PRIMARY KEY, course_id INT, section_code VARCHAR(32), term_id INT, semester_number INT, section_status VARCHAR(30), faculty_id INT)");
        db.execute("CREATE TABLE student_enlistments (id INT AUTO_INCREMENT PRIMARY KEY, course_id INT)");
        db.execute("CREATE TABLE grades (id INT AUTO_INCREMENT PRIMARY KEY, course_id INT)");
        db.execute("CREATE TABLE waitlists (id INT AUTO_INCREMENT PRIMARY KEY, course_id INT)");
        db.execute("CREATE TABLE student_requests (id INT AUTO_INCREMENT PRIMARY KEY, course_id INT)");
        db.execute("CREATE TABLE course_prerequisites (course_id INT, prerequisite_course_id INT)");
        db.update("INSERT INTO departments VALUES (1, 'SCS', 'School of Computer Studies')");

        service = new CourseCatalogService();
        ReflectionTestUtils.setField(service, "db", db);
    }

    @Test
    void savesLectureAndLaboratoryUnitsAndDerivesTotalCreditUnits() {
        Integer courseId = service.saveCourse(null, "CS 101", "Computing Fundamentals", 1, 2, 1, true);

        Map<String, Object> saved = db.queryForMap(
            "SELECT credit_units, lec_units, lab_units FROM courses WHERE course_id = ?", courseId);
        assertThat(saved.get("CREDIT_UNITS")).isEqualTo(3);
        assertThat(saved.get("LEC_UNITS")).isEqualTo(2);
        assertThat(saved.get("LAB_UNITS")).isEqualTo(1);
    }

    @Test
    void usageDetailsNamesCurriculumAndSectionPlacements() {
        Integer courseId = service.saveCourse(null, "CS 102", "Programming", 1, 2, 1, true);
        db.update("INSERT INTO programs VALUES (10, 'BSCS', 'Bachelor of Science in Computer Science')");
        db.update("INSERT INTO curriculum_templates VALUES (20, 10, 'BSCS 2026', '2026-2027', 'Approved', 1)");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number) VALUES (20, ?, 1, 1)", courseId);
        db.update("INSERT INTO class_sections VALUES (30, ?, 'BSCS-1-A', 5, 1, 'Open', NULL)", courseId);

        Map<String, Object> details = service.usageDetails(courseId);

        assertThat((Iterable<?>) details.get("curricula")).hasSize(1);
        assertThat((Iterable<?>) details.get("sections")).hasSize(1);
        assertThat(details.toString()).contains("BSCS 2026", "BSCS-1-A");
    }
}
