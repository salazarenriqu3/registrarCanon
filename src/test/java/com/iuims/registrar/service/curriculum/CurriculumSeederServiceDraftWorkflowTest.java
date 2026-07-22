package com.iuims.registrar.service.curriculum;
import com.iuims.registrar.entity.Course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

class CurriculumSeederServiceDraftWorkflowTest {

    private JdbcTemplate db;
    private CurriculumSeederService service;

    @BeforeEach
    void setUp() {
        db = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:curriculum-drafts;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("DROP ALL OBJECTS");
        db.execute("CREATE TABLE programs (program_id INT PRIMARY KEY, program_code VARCHAR(20), program_name VARCHAR(150), school_name VARCHAR(150), active_status INT)");
        db.execute("CREATE TABLE curriculum_templates (curriculum_id INT AUTO_INCREMENT PRIMARY KEY, program_id INT, curriculum_name VARCHAR(150), academic_year VARCHAR(20), version_number INT, approval_status VARCHAR(20), lifecycle_status VARCHAR(20), is_active INT)");
        db.execute("CREATE TABLE curriculum_courses (curriculum_course_id INT AUTO_INCREMENT PRIMARY KEY, curriculum_id INT, course_id INT, year_level INT, semester_number INT, is_required INT)");
        db.execute("CREATE TABLE courses (course_id INT PRIMARY KEY, course_code VARCHAR(20), course_title VARCHAR(150), credit_units INT, lec_units INT, lab_units INT, department_id INT, component_type VARCHAR(20), course_family_code VARCHAR(40), parent_course_id INT, active_status INT)");
        db.execute("CREATE TABLE departments (department_id INT PRIMARY KEY, department_code VARCHAR(20), department_name VARCHAR(150))");
        db.execute("CREATE TABLE student_curriculum_assignments (id INT AUTO_INCREMENT PRIMARY KEY, curriculum_id INT, is_current INT)");
        db.execute("CREATE TABLE course_prerequisites (course_id INT, prerequisite_course_id INT)");
        db.execute("CREATE TABLE course_corequisites (course_id INT, corequisite_course_id INT)");
        db.execute("CREATE TABLE course_equivalencies (course_id INT, equivalent_course_id INT)");

        db.update("INSERT INTO programs VALUES (1, 'BSIT', 'Bachelor of Science in Information Technology', 'SET', 1)");
        db.update("INSERT INTO programs VALUES (2, 'BSBIO', 'Bachelor of Science in Biology', 'SAS', 1)");
        db.update("INSERT INTO departments VALUES (1, 'IT', 'Information Technology')");
        db.update("INSERT INTO departments VALUES (2, 'BIO', 'Biology')");
        db.update("INSERT INTO courses VALUES (10, 'IT 101', 'Intro to IT', 3, 3, 0, 1, 'LEC', 'IT 101', NULL, 1)");

        service = new CurriculumSeederService();
        ReflectionTestUtils.setField(service, "db", db);
    }

    @Test
    void cloneCurriculumToDraftRetargetsProgramAndVersion() {
        db.update(
            "INSERT INTO curriculum_templates (curriculum_id, program_id, curriculum_name, academic_year, version_number, approval_status, lifecycle_status, is_active) " +
                "VALUES (100, 1, 'BSIT 2026', '2026-2027', 3, 'Approved', 'CURRENT', 1)");
        db.update(
            "INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number, is_required) VALUES (100, 10, 1, 1, 1)");

        Integer cloneId = service.cloneCurriculumToDraft(100, "BSBIO", "2027-2028", "BSBIO Draft", 9);

        Map<String, Object> clone = service.getCurriculumSummary(cloneId);
        assertThat(clone.get("PROGRAM_CODE")).isEqualTo("BSBIO");
        assertThat(clone.get("CURRICULUM_NAME")).isEqualTo("BSBIO Draft");
        assertThat(clone.get("ACADEMIC_YEAR")).isEqualTo("2027-2028");
        assertThat(clone.get("VERSION_NUMBER")).isEqualTo(9);
        assertThat(clone.get("LIFECYCLE_STATUS")).isEqualTo("DRAFT");
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM curriculum_courses WHERE curriculum_id = ?",
            Integer.class,
            cloneId)).isEqualTo(1);
    }

    @Test
    void updateDraftMetadataCanRetargetEditableDraft() {
        db.update(
            "INSERT INTO curriculum_templates (curriculum_id, program_id, curriculum_name, academic_year, version_number, approval_status, lifecycle_status, is_active) " +
                "VALUES (101, 1, 'BSIT Draft', '2026-2027', 1, 'Draft', 'DRAFT', 0)");

        service.updateDraftMetadata(101, "BSBIO", "BSBIO Transfer Draft", "2028-2029", 4);

        Map<String, Object> updated = service.getCurriculumSummary(101);
        assertThat(updated.get("PROGRAM_CODE")).isEqualTo("BSBIO");
        assertThat(updated.get("CURRICULUM_NAME")).isEqualTo("BSBIO Transfer Draft");
        assertThat(updated.get("ACADEMIC_YEAR")).isEqualTo("2028-2029");
        assertThat(updated.get("VERSION_NUMBER")).isEqualTo(4);
    }

    @Test
    void setCurrentDemotesPriorCurrentToLegacy() {
        db.update(
            "INSERT INTO curriculum_templates (curriculum_id, program_id, curriculum_name, academic_year, version_number, approval_status, lifecycle_status, is_active) " +
                "VALUES (201, 1, 'BSIT Current', '2025-2026', 1, 'Approved', 'CURRENT', 1)");
        db.update(
            "INSERT INTO curriculum_templates (curriculum_id, program_id, curriculum_name, academic_year, version_number, approval_status, lifecycle_status, is_active) " +
                "VALUES (202, 1, 'BSIT Draft Next', '2026-2027', 2, 'Draft', 'DRAFT', 0)");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number, is_required) VALUES (201, 10, 1, 1, 1)");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number, is_required) VALUES (202, 10, 1, 1, 1)");

        service.setCurriculumLifecycle(202, "CURRENT");

        assertThat(db.queryForObject(
            "SELECT lifecycle_status FROM curriculum_templates WHERE curriculum_id = 201",
            String.class)).isEqualTo("LEGACY");
        assertThat(db.queryForObject(
            "SELECT lifecycle_status FROM curriculum_templates WHERE curriculum_id = 202",
            String.class)).isEqualTo("CURRENT");
        assertThat(db.queryForObject(
            "SELECT is_active FROM curriculum_templates WHERE curriculum_id = 202",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void manualCourseCreationIsRetired() {
        assertThatThrownBy(() -> service.addManualCourse(999, "IT 999", "Ghost Course", 3, 0, 1, 1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Manual course creation");
    }

    @Test
    void activeCatalogOptionsAreOrderedAndExcludeInactiveCourses() {
        db.update("INSERT INTO courses VALUES (11, 'IT 201', 'Systems Analysis', 3, 3, 0, 1, 'LEC', 'IT 201', NULL, 1)");
        db.update("INSERT INTO courses VALUES (12, 'BIO 101', 'Biology Basics', 3, 3, 0, 2, 'LEC', 'BIO 101', NULL, 1)");
        db.update("INSERT INTO courses VALUES (13, 'IT 099', 'Retired IT', 3, 3, 0, 1, 'LEC', 'IT 099', NULL, 0)");

        List<Map<String, Object>> options = service.listActiveCourseCatalogOptions(null);

        assertThat(options).extracting(row -> row.get("COURSE_CODE"))
            .containsExactly("BIO 101", "IT 101", "IT 201");
        assertThat(options).extracting(row -> row.get("COURSE_CODE")).doesNotContain("IT 099");
    }

    @Test
    void activeCatalogOptionsRespectDepartmentFilter() {
        db.update("INSERT INTO courses VALUES (11, 'IT 201', 'Systems Analysis', 3, 3, 0, 1, 'LEC', 'IT 201', NULL, 1)");
        db.update("INSERT INTO courses VALUES (12, 'BIO 101', 'Biology Basics', 3, 3, 0, 2, 'LEC', 'BIO 101', NULL, 1)");

        List<Map<String, Object>> options = service.listActiveCourseCatalogOptions(1);

        assertThat(options).extracting(row -> row.get("COURSE_CODE"))
            .containsExactly("IT 101", "IT 201");
    }

    @Test
    void activeCatalogCourseIdAttachesThroughExistingDraftGuard() {
        db.update(
            "INSERT INTO curriculum_templates (curriculum_id, program_id, curriculum_name, academic_year, version_number, approval_status, lifecycle_status, is_active) " +
                "VALUES (301, 1, 'BSIT Draft', '2026-2027', 1, 'Draft', 'DRAFT', 0)");

        service.addExistingCourse(301, 10, 2, 1);

        assertThat(db.queryForMap(
            "SELECT course_id, year_level, semester_number FROM curriculum_courses WHERE curriculum_id = 301"))
            .containsEntry("COURSE_ID", 10)
            .containsEntry("YEAR_LEVEL", 2)
            .containsEntry("SEMESTER_NUMBER", 1);
    }
}
