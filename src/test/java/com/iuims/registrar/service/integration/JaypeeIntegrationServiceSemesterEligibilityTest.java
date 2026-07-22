package com.iuims.registrar.service.integration;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Program;
import com.iuims.registrar.entity.Student;

import com.iuims.registrar.service.admission.ApplicantStatusSyncService;
import com.iuims.registrar.service.academic.BlockOfferingService;
import com.iuims.registrar.service.support.EnlistmentSchemaService;
import com.iuims.registrar.service.curriculum.StudentCurriculumService;
import com.iuims.registrar.service.forms.RegFormEventService;
import com.iuims.registrar.service.scholarship.ScholarEnrollmentService;
import com.iuims.registrar.service.withdrawal.WithdrawalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Time;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JaypeeIntegrationServiceSemesterEligibilityTest {

    private JdbcTemplate db;
    private JaypeeIntegrationService service;

    @BeforeEach
    void setUp() {
        db = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:jaypee-semester-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("CREATE ALIAS IF NOT EXISTS TIME_FORMAT FOR '" + JaypeeIntegrationServiceSemesterEligibilityTest.class.getName() + ".timeFormat'");

        db.execute("CREATE TABLE programs (program_id INT PRIMARY KEY, program_code VARCHAR(40), program_name VARCHAR(120), school_name VARCHAR(120), active_status INT)");
        db.execute("CREATE TABLE students (student_number VARCHAR(100) PRIMARY KEY, program_code VARCHAR(40), year_level INT, semester INT, term_year VARCHAR(40), admission_status VARCHAR(40), enrollment_blocked INT DEFAULT 0, is_active INT DEFAULT 1)");
        db.execute("CREATE TABLE academic_terms (term_id INT PRIMARY KEY, term_code VARCHAR(40), is_active INT, status VARCHAR(40))");
        db.execute("CREATE TABLE curriculum_templates (curriculum_id INT PRIMARY KEY, program_id INT, curriculum_name VARCHAR(160), academic_year VARCHAR(40), version_number INT, is_active INT)");
        db.execute("CREATE TABLE curriculum_courses (curriculum_id INT, course_id INT, year_level INT, semester_number INT)");
        db.execute("CREATE TABLE courses (course_id INT PRIMARY KEY, course_code VARCHAR(40), course_title VARCHAR(160), credit_units DOUBLE, onlist INT, active_status INT)");
        db.execute("CREATE TABLE class_sections (section_id INT PRIMARY KEY, course_id INT, term_id INT, section_code VARCHAR(60), max_capacity INT)");
        db.execute("CREATE TABLE class_schedules (schedule_id INT AUTO_INCREMENT PRIMARY KEY, section_id INT, day_of_week INT, start_time TIME, end_time TIME, room_id INT)");
        db.execute("CREATE TABLE student_enlistments (enlistment_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id VARCHAR(100), course_id INT, section_id INT)");
        db.execute("CREATE TABLE grades (grade_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id VARCHAR(100), course_id INT, remarks VARCHAR(40), registrar_final_remarks VARCHAR(40))");
        db.execute("CREATE TABLE course_prerequisites (prerequisite_id INT AUTO_INCREMENT PRIMARY KEY, course_id INT, prerequisite_course_id INT)");
        db.execute("CREATE TABLE course_corequisites (corequisite_id INT AUTO_INCREMENT PRIMARY KEY, course_id INT, corequisite_course_id INT)");
        db.execute("CREATE TABLE student_ledger (ledger_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id VARCHAR(100), transaction_type VARCHAR(40), description VARCHAR(255), debit DOUBLE, credit DOUBLE)");

        db.update("INSERT INTO programs (program_id, program_code, program_name, school_name, active_status) VALUES (1, 'BSIT', 'BSIT', 'School of Computer Studies', 1)");
        db.update("INSERT INTO students (student_number, program_code, year_level, semester, term_year, admission_status) VALUES ('2026-0001', 'BSIT', 1, 1, 'SL2026202711', 'ENROLLED')");
        db.update("INSERT INTO academic_terms (term_id, term_code, is_active, status) VALUES (1, '1120262027', 1, 'ACTIVE')");
        db.update("INSERT INTO curriculum_templates (curriculum_id, program_id, curriculum_name, academic_year, version_number, is_active) VALUES (501, 1, 'BSIT 2026', '2026-2027', 1, 1)");

        db.update("INSERT INTO courses (course_id, course_code, course_title, credit_units, onlist, active_status) VALUES (101, 'IT101', 'Intro to IT', 3, 1, 1)");
        db.update("INSERT INTO courses (course_id, course_code, course_title, credit_units, onlist, active_status) VALUES (201, 'IT201', 'Systems Analysis', 3, 1, 1)");
        db.update("INSERT INTO courses (course_id, course_code, course_title, credit_units, onlist, active_status) VALUES (202, 'IT202', 'Operating Systems', 3, 1, 1)");

        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number) VALUES (501, 101, 1, 1)");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number) VALUES (501, 201, 2, 1)");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number) VALUES (501, 202, 2, 2)");

        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, max_capacity) VALUES (1001, 101, 1, 'IT101-A', 40)");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, max_capacity) VALUES (2001, 201, 1, 'IT201-A', 40)");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, max_capacity) VALUES (2002, 202, 1, 'IT202-A', 40)");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, max_capacity) VALUES (3001, 101, 1, 'IRREG-A', 40)");

        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id) VALUES (1001, 1, '07:30:00', '09:00:00', NULL)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id) VALUES (2001, 2, '09:00:00', '10:30:00', NULL)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id) VALUES (2002, 3, '10:30:00', '12:00:00', NULL)");

        RegFormEventService regFormEventService = mock(RegFormEventService.class);
        StudentCurriculumService studentCurriculumService = new StudentCurriculumService(db, regFormEventService);
        studentCurriculumService.ensureSchema();
        studentCurriculumService.assignCurriculum("2026-0001", 501, "DEFAULT", "test seed", "test");

        ScholarEnrollmentService scholarEnrollmentService = mock(ScholarEnrollmentService.class);
        when(scholarEnrollmentService.getMaxAllowedUnitsForStudent(anyString(), anyString(), anyInt())).thenReturn(30.0);
        when(scholarEnrollmentService.hasAccountingBlock(anyString())).thenReturn(false);
        when(scholarEnrollmentService.tuitionRatePerUnit(anyString())).thenReturn(1000.0);

        service = new JaypeeIntegrationService(
            db,
            scholarEnrollmentService,
            new EnlistmentSchemaService(db),
            mock(ApplicantStatusSyncService.class),
            studentCurriculumService,
            regFormEventService,
            mock(WithdrawalService.class));
    }

    @Test
    void offeringsStayWithinAssignedCurriculumSemesterAcrossYearLevels() {
        List<Map<String, Object>> grouped = service.getGroupedCourseOfferings("2026-0001", null, null, null);
        List<String> groupedCodes = grouped.stream()
            .map(row -> String.valueOf(row.get("course_code")))
            .toList();

        List<Map<String, Object>> analyzed = service.getCrossSystemAnalyzedOfferings("2026-0001");
        List<String> analyzedCodes = analyzed.stream()
            .map(row -> String.valueOf(row.get("course_code")))
            .distinct()
            .toList();

        assertThat(groupedCodes).containsExactly("IT101", "IT201");
        assertThat(groupedCodes).doesNotContain("IT202");
        assertThat(analyzedCodes).containsExactly("IT101", "IT201");
        assertThat(analyzedCodes).doesNotContain("IT202");
    }

    @Test
    void addSubjectAllowsSameSemesterCourseButRejectsDifferentSemesterCourse() {
        String sameSemester = service.addSubjectCrossSystem("2026-0001", 2001);
        String differentSemester = service.addSubjectCrossSystem("2026-0001", 2002);

        assertThat(sameSemester).startsWith("SUCCESS");
        assertThat(differentSemester)
            .isEqualTo("ERROR: Course is not available in the student's current curriculum semester.");
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_enlistments WHERE student_id = '2026-0001' AND course_id = 201",
            Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_enlistments WHERE student_id = '2026-0001' AND course_id = 202",
            Integer.class)).isZero();
    }

    @Test
    void legacyIrregularSectionsAreRejected() {
        String result = service.addSubjectCrossSystem("2026-0001", 3001);

        assertThat(result)
            .isEqualTo("ERROR: Legacy irregular open sections are retired. Use a block section, or a summer/tutorial section if applicable.");
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_enlistments WHERE student_id = '2026-0001' AND section_id = 3001",
            Integer.class)).isZero();
    }

    @Test
    void manualAddBundlesConcurrentCorequisitePair() {
        db.update("INSERT INTO courses (course_id, course_code, course_title, credit_units, onlist, active_status) VALUES (301, 'IT301-LEC', 'Networking', 2, 1, 1)");
        db.update("INSERT INTO courses (course_id, course_code, course_title, credit_units, onlist, active_status) VALUES (302, 'IT301-LAB', 'Networking', 1, 1, 1)");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number) VALUES (501, 301, 1, 1)");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number) VALUES (501, 302, 1, 1)");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, max_capacity) VALUES (3011, 301, 1, 'IT301-LEC-A', 40)");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, max_capacity) VALUES (3021, 302, 1, 'IT301-LAB-A', 40)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id) VALUES (3011, 4, '13:00:00', '14:30:00', NULL)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id) VALUES (3021, 5, '14:30:00', '16:00:00', NULL)");
        db.update("INSERT INTO course_corequisites (course_id, corequisite_course_id) VALUES (301, 302)");
        db.update("INSERT INTO course_corequisites (course_id, corequisite_course_id) VALUES (302, 301)");

        String loneLecture = service.addSubjectCrossSystem("2026-0001", 3011);

        assertThat(loneLecture).startsWith("SUCCESS: Added bundled corequisites");
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_enlistments WHERE student_id = '2026-0001' AND course_id = 301",
            Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_enlistments WHERE student_id = '2026-0001' AND course_id = 302",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void manualAddUsesAlreadyEnrolledCorequisiteAsSatisfied() {
        db.update("INSERT INTO courses (course_id, course_code, course_title, credit_units, onlist, active_status) VALUES (311, 'IT311-LEC', 'Advanced Networking', 2, 1, 1)");
        db.update("INSERT INTO courses (course_id, course_code, course_title, credit_units, onlist, active_status) VALUES (312, 'IT311-LAB', 'Advanced Networking', 1, 1, 1)");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number) VALUES (501, 311, 1, 1)");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number) VALUES (501, 312, 1, 1)");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, max_capacity) VALUES (3111, 311, 1, 'IT311-LEC-A', 40)");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, max_capacity) VALUES (3121, 312, 1, 'IT311-LAB-A', 40)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id) VALUES (3111, 4, '08:00:00', '09:30:00', NULL)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id) VALUES (3121, 5, '09:30:00', '11:00:00', NULL)");
        db.update("INSERT INTO course_corequisites (course_id, corequisite_course_id) VALUES (311, 312)");
        db.update("INSERT INTO course_corequisites (course_id, corequisite_course_id) VALUES (312, 311)");

        db.update("INSERT INTO student_enlistments (student_id, course_id, section_id) VALUES ('2026-0001', 312, 3121)");

        String withLabAlreadyEnrolled = service.addSubjectCrossSystem("2026-0001", 3111);

        assertThat(withLabAlreadyEnrolled).isEqualTo("SUCCESS: Added IT311-LEC.");
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_enlistments WHERE student_id = '2026-0001' AND course_id = 311",
            Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_enlistments WHERE student_id = '2026-0001' AND course_id = 312",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void manualAddBlocksWholeBundleWhenLinkedCorequisiteHasNoSection() {
        db.update("INSERT INTO courses (course_id, course_code, course_title, credit_units, onlist, active_status) VALUES (321, 'IT321-LEC', 'Security', 2, 1, 1)");
        db.update("INSERT INTO courses (course_id, course_code, course_title, credit_units, onlist, active_status) VALUES (322, 'IT321-LAB', 'Security', 1, 1, 1)");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number) VALUES (501, 321, 1, 1)");
        db.update("INSERT INTO curriculum_courses (curriculum_id, course_id, year_level, semester_number) VALUES (501, 322, 1, 1)");
        db.update("INSERT INTO class_sections (section_id, course_id, term_id, section_code, max_capacity) VALUES (3211, 321, 1, 'IT321-LEC-A', 40)");
        db.update("INSERT INTO class_schedules (section_id, day_of_week, start_time, end_time, room_id) VALUES (3211, 4, '15:00:00', '16:30:00', NULL)");
        db.update("INSERT INTO course_corequisites (course_id, corequisite_course_id) VALUES (321, 322)");
        db.update("INSERT INTO course_corequisites (course_id, corequisite_course_id) VALUES (322, 321)");

        String result = service.addSubjectCrossSystem("2026-0001", 3211);

        assertThat(result).isEqualTo("ERROR: Failed to process Add Subject. No available section can be paired for linked corequisite IT321-LAB.");
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_enlistments WHERE student_id = '2026-0001' AND course_id IN (321, 322)",
            Integer.class)).isZero();
    }

    @Test
    void withdrawnStudentsCannotBeReenlistedOrShownOfferings() {
        db.update("UPDATE students SET admission_status = 'WITHDRAWN', enrollment_blocked = 1, is_active = 0 WHERE student_number = '2026-0001'");

        String addResult = service.addSubjectCrossSystem("2026-0001", 2001);

        assertThat(addResult).isEqualTo("ERROR: Withdrawn or inactive students cannot be enrolled in subjects.");
        assertThat(service.getCrossSystemAnalyzedOfferings("2026-0001")).isEmpty();
        assertThat(service.getGroupedCourseOfferings("2026-0001", null, null, null)).isEmpty();
        assertThat(service.getStudentLoad("2026-0001")).isEmpty();
    }

    @Test
    void irregularStudentsOnlySeeAndAddExplicitlyOpenedBlockSections() {
        db.execute("ALTER TABLE students ADD COLUMN student_type VARCHAR(40)");
        db.update("UPDATE students SET student_type = 'Irregular' WHERE student_number = '2026-0001'");
        db.update("UPDATE class_sections SET section_code = 'BSIT-1-1-A' WHERE section_id = 2001");

        Map<String, Object> blockedCourse = service.getGroupedCourseOfferings("2026-0001", null, null, null).stream()
            .filter(row -> "IT201".equals(String.valueOf(row.get("course_code"))))
            .findFirst()
            .orElseThrow();
        String blockedAdd = service.addSubjectCrossSystem("2026-0001", 2001);

        assertThat(blockedCourse.get("reason_msg")).isEqualTo("Closed to irregular enlistment");
        assertThat((List<?>) blockedCourse.get("sections")).isEmpty();
        assertThat(blockedAdd)
            .isEqualTo("ERROR: Closed to irregular enlistment. Ask Registrar to open irregular access for the class, block, or program/year/semester scope.");

        BlockOfferingService blockOfferingService = new BlockOfferingService(db);
        blockOfferingService.setProgramYearSemesterIrregularAccess(1, "BSIT", 1, 1, true);

        Map<String, Object> reopenedCourse = service.getGroupedCourseOfferings("2026-0001", null, null, null).stream()
            .filter(row -> "IT201".equals(String.valueOf(row.get("course_code"))))
            .findFirst()
            .orElseThrow();
        String reopenedAdd = service.addSubjectCrossSystem("2026-0001", 2001);

        assertThat((List<?>) reopenedCourse.get("sections")).isNotEmpty();
        assertThat(reopenedAdd).startsWith("SUCCESS");
    }

    public static String timeFormat(Time value, String pattern) {
        if (value == null) {
            return null;
        }
        return value.toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a"));
    }
}
