package com.iuims.registrar.service.curriculum;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Program;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
        db.execute("CREATE TABLE courses (course_id INT AUTO_INCREMENT PRIMARY KEY, course_code VARCHAR(20) UNIQUE, course_title VARCHAR(150), department_id INT, credit_units INT, lec_units INT, lab_units INT, component_type VARCHAR(20), course_family_code VARCHAR(40), parent_course_id INT, active_status INT)");
        db.execute("CREATE TABLE faculty (faculty_id INT PRIMARY KEY, first_name VARCHAR(100), last_name VARCHAR(100))");
        db.execute("CREATE TABLE academic_terms (term_id INT PRIMARY KEY, term_name VARCHAR(100))");
        db.execute("CREATE TABLE programs (program_id INT PRIMARY KEY, program_code VARCHAR(20), program_name VARCHAR(150))");
        db.execute("CREATE TABLE curriculum_templates (curriculum_id INT PRIMARY KEY, program_id INT, curriculum_name VARCHAR(100), academic_year VARCHAR(20), approval_status VARCHAR(20), is_active INT)");
        db.execute("CREATE TABLE curriculum_courses (curriculum_course_id INT AUTO_INCREMENT PRIMARY KEY, curriculum_id INT, course_id INT, year_level INT, semester_number INT)");
        db.execute("CREATE TABLE class_sections (section_id INT PRIMARY KEY, course_id INT, section_code VARCHAR(32), term_id INT, semester_number INT, section_status VARCHAR(30), faculty_id INT)");
        db.execute("CREATE TABLE class_schedules (schedule_id INT AUTO_INCREMENT PRIMARY KEY, section_id INT, day_of_week INT, start_time TIME, end_time TIME)");
        db.execute("CREATE TABLE student_enlistments (id INT AUTO_INCREMENT PRIMARY KEY, course_id INT)");
        db.execute("CREATE TABLE grades (id INT AUTO_INCREMENT PRIMARY KEY, course_id INT)");
        db.execute("CREATE TABLE waitlists (id INT AUTO_INCREMENT PRIMARY KEY, course_id INT)");
        db.execute("CREATE TABLE student_requests (id INT AUTO_INCREMENT PRIMARY KEY, course_id INT)");
        db.execute("CREATE TABLE course_prerequisites (course_id INT, prerequisite_course_id INT)");
        db.execute("CREATE TABLE course_corequisites (course_id INT, corequisite_course_id INT)");
        db.execute("CREATE TABLE course_equivalencies (course_id INT, equivalent_course_id INT)");
        db.update("INSERT INTO departments VALUES (1, 'SCS', 'School of Computer Studies')");

        service = new CourseCatalogService();
        ReflectionTestUtils.setField(service, "db", db);
    }

    @Test
    void savesLectureAndLaboratoryUnitsAndDerivesTotalCreditUnits() {
        Integer courseId = service.saveCourse(null, "CS 101", "Computing Fundamentals", 1, 2, 1, true);

        Map<String, Object> saved = db.queryForMap(
            "SELECT course_code, credit_units, lec_units, lab_units, component_type, course_family_code FROM courses WHERE course_id = ?", courseId);
        assertThat(saved.get("COURSE_CODE")).isEqualTo("CS 101-LEC");
        assertThat(saved.get("CREDIT_UNITS")).isEqualTo(2);
        assertThat(saved.get("LEC_UNITS")).isEqualTo(2);
        assertThat(saved.get("LAB_UNITS")).isEqualTo(0);
        assertThat(saved.get("COMPONENT_TYPE")).isEqualTo("LEC");
        assertThat(saved.get("COURSE_FAMILY_CODE")).isEqualTo("CS 101");
        Map<String, Object> lab = db.queryForMap(
            "SELECT course_code, credit_units, lec_units, lab_units, component_type, course_family_code FROM courses WHERE course_family_code = ? AND component_type = 'LAB'",
            "CS 101");
        assertThat(lab.get("COURSE_CODE")).isEqualTo("CS 101-LAB");
        assertThat(lab.get("CREDIT_UNITS")).isEqualTo(1);
        assertThat(lab.get("LEC_UNITS")).isEqualTo(0);
        assertThat(lab.get("LAB_UNITS")).isEqualTo(1);
        assertThat(lab.get("COMPONENT_TYPE")).isEqualTo("LAB");
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

    @Test
    void pagesCoursesAndCalculatesUsageSummaryWithoutLoadingEveryCourse() {
        Integer firstId = service.saveCourse(null, "CS 101", "Foundations", 1, 3, 0, true);
        Integer secondId = service.saveCourse(null, "CS 102", "Programming", 1, 3, 0, true);
        service.saveCourse(null, "CS 103", "Networks", 1, 3, 0, false);
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number) VALUES (1, ?, 1, 1)", firstId);
        db.update("INSERT INTO course_prerequisites VALUES (?, ?)", secondId, firstId);

        CourseCatalogService.CoursePage firstPage = service.listCoursesPage(null, null, "all", 1, 2);

        assertThat(firstPage.rows()).hasSize(2);
        assertThat(firstPage.page()).isEqualTo(1);
        assertThat(firstPage.pageSize()).isEqualTo(2);
        assertThat(firstPage.totalRows()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.summary())
            .containsEntry("total", 3)
            .containsEntry("active", 2)
            .containsEntry("inactive", 1)
            .containsEntry("used", 2);
    }

    @Test
    void saveCourseRelationshipsStoreStructuredLinksAndAutoPairSplitComponents() {
        db.update("INSERT INTO courses (course_id, course_code, course_title, department_id, credit_units, lec_units, lab_units, component_type, course_family_code, parent_course_id, active_status) VALUES (90, 'MATH 100', 'College Algebra', 1, 3, 3, 0, 'LEC', 'MATH 100', NULL, 1)");
        db.update("INSERT INTO courses (course_id, course_code, course_title, department_id, credit_units, lec_units, lab_units, component_type, course_family_code, parent_course_id, active_status) VALUES (91, 'PHYS 100', 'Intro Physics Lab', 1, 1, 0, 1, 'LAB', 'PHYS 100', NULL, 1)");
        db.update("INSERT INTO courses (course_id, course_code, course_title, department_id, credit_units, lec_units, lab_units, component_type, course_family_code, parent_course_id, active_status) VALUES (92, 'NSTP 100', 'National Service Training Program', 1, 3, 3, 0, 'LEC', 'NSTP 100', NULL, 1)");

        Integer courseId = service.saveCourse(
            null,
            "CS 201",
            "Integrated Computing",
            1,
            2,
            1,
            true);

        service.saveCourseRelationships(courseId, List.of(90), List.of(91), List.of(92));

        Integer labId = db.queryForObject(
            "SELECT course_id FROM courses WHERE course_code = 'CS 201-LAB'",
            Integer.class);

        assertThat(courseId).isNotNull();
        assertThat(labId).isNotNull();
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM course_prerequisites WHERE course_id = ? AND prerequisite_course_id = 90",
            Integer.class,
            courseId)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM course_prerequisites WHERE course_id = ? AND prerequisite_course_id = 90",
            Integer.class,
            labId)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM course_corequisites WHERE course_id = ? AND corequisite_course_id = 91",
            Integer.class,
            courseId)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM course_equivalencies WHERE course_id = ? AND equivalent_course_id = 92",
            Integer.class,
            courseId)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM course_prerequisites WHERE course_id = ? AND prerequisite_course_id = 90",
            Integer.class,
            labId)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM course_equivalencies WHERE course_id = ? AND equivalent_course_id = 92",
            Integer.class,
            labId)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM course_corequisites WHERE course_id = ? AND corequisite_course_id = ?",
            Integer.class,
            courseId,
            labId)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM course_corequisites WHERE course_id = ? AND corequisite_course_id = ?",
            Integer.class,
            labId,
            courseId)).isEqualTo(1);
    }

    @Test
    void relationshipSearchIncludesInactiveCatalogCourses() {
        db.update("INSERT INTO courses (course_id, course_code, course_title, department_id, credit_units, lec_units, lab_units, component_type, course_family_code, parent_course_id, active_status) VALUES (150, 'OLD MATH 1', 'Legacy Algebra', 1, 3, 3, 0, 'LEC', 'OLD MATH 1', NULL, 0)");

        List<Map<String, Object>> results = service.searchRelationshipCourses("OLD MATH", null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("COURSE_CODE")).isEqualTo("OLD MATH 1");
        assertThat(results.get(0).get("ACTIVE_STATUS")).isEqualTo(0);
    }
}
