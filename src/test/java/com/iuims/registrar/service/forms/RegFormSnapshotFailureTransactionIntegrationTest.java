package com.iuims.registrar.service.forms;

import com.iuims.registrar.domain.forms.RegistrationFormSnapshotException;
import com.iuims.registrar.entity.Program;

import com.iuims.registrar.service.support.RegistrarAuditTrailService;
import com.iuims.registrar.service.support.StudentProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegFormSnapshotFailureTransactionIntegrationTest {

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate db;
    private CompositeShiftMutationService mutationService;
    private RegFormEventService eventService;
    private RegistrationFormSnapshotFailureAuditService failureAuditService;
    private RegFormVersionService versionService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        db = context.getBean(JdbcTemplate.class);
        mutationService = context.getBean(CompositeShiftMutationService.class);
        eventService = context.getBean(RegFormEventService.class);
        failureAuditService = context.getBean(RegistrationFormSnapshotFailureAuditService.class);
        versionService = context.getBean(RegFormVersionService.class);

        db.execute("CREATE TABLE students (student_number VARCHAR(100) PRIMARY KEY, program_code VARCHAR(40))");
        db.execute("CREATE TABLE student_curriculum_assignments (assignment_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_number VARCHAR(100), curriculum_id INT)");
        db.execute("CREATE TABLE student_enlistments (enlistment_id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id VARCHAR(100), course_id INT)");
        db.update("INSERT INTO students (student_number, program_code) VALUES ('SHIFT-IT-001', 'BSIT')");
        db.update("INSERT INTO student_curriculum_assignments (student_number, curriculum_id) VALUES ('SHIFT-IT-001', 101)");
        db.update("INSERT INTO student_enlistments (student_id, course_id) VALUES ('SHIFT-IT-001', 9001)");

        doThrow(new IllegalStateException("controlled snapshot storage failure"))
            .when(versionService).captureVersion(anyString(), anyString(), anyString(), anyLong(), any(), anyString());
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void requiredSnapshotFailureRollsBackParentMutationWhileFailureAuditCommits() {
        assertThat(AopUtils.isAopProxy(eventService)).isTrue();
        assertThat(AopUtils.isAopProxy(failureAuditService)).isTrue();
        assertThat(AopUtils.isAopProxy(mutationService)).isTrue();

        assertThatThrownBy(() -> mutationService.shiftAndCapture("SHIFT-IT-001", "BSCS", 202))
            .isInstanceOf(RegistrationFormSnapshotException.class)
            .hasMessage("Registration Form version could not be saved. No enrollment change was committed.");

        assertThat(db.queryForObject("SELECT program_code FROM students WHERE student_number = 'SHIFT-IT-001'", String.class))
            .isEqualTo("BSIT");
        assertThat(db.queryForObject("SELECT curriculum_id FROM student_curriculum_assignments WHERE student_number = 'SHIFT-IT-001'", Integer.class))
            .isEqualTo(101);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_enlistments WHERE student_id = 'SHIFT-IT-001'", Integer.class))
            .isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_reg_form_events WHERE student_number = 'SHIFT-IT-001'", Integer.class))
            .isZero();

        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action_name = 'REG_FORM_SNAPSHOT_CAPTURE_FAILED'", Integer.class))
            .isEqualTo(1);
        assertThat(db.queryForMap("SELECT target_key, source_id, details FROM audit_logs WHERE action_name = 'REG_FORM_SNAPSHOT_CAPTURE_FAILED'"))
            .containsEntry("TARGET_KEY", "SHIFT-IT-001")
            .containsEntry("SOURCE_ID", "PROGRAM_SHIFT");
        assertThat(String.valueOf(db.queryForMap(
            "SELECT details FROM audit_logs WHERE action_name = 'REG_FORM_SNAPSHOT_CAPTURE_FAILED'").get("DETAILS")))
            .contains("eventType=PROGRAM_SHIFT")
            .contains("controlled snapshot storage failure");
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                "jdbc:h2:mem:reg-form-snapshot-failure-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        StudentProfileService studentProfileService() {
            StudentProfileService profileService = mock(StudentProfileService.class);
            when(profileService.ensureArchiveKey("SHIFT-IT-001")).thenReturn("ARCH-SHIFT-IT-001");
            return profileService;
        }

        @Bean
        RegistrarAuditTrailService registrarAuditTrailService(JdbcTemplate db) {
            return new RegistrarAuditTrailService(db);
        }

        @Bean
        RegistrationFormSnapshotFailureAuditService registrationFormSnapshotFailureAuditService(
            RegistrarAuditTrailService auditTrailService) {
            return new RegistrationFormSnapshotFailureAuditService(auditTrailService);
        }

        @Bean
        RegFormVersionService regFormVersionService() {
            return mock(RegFormVersionService.class);
        }

        @Bean
        RegFormEventService regFormEventService(JdbcTemplate db,
                                                StudentProfileService studentProfileService,
                                                RegistrarAuditTrailService auditTrailService,
                                                RegFormVersionService versionService,
                                                RegistrationFormSnapshotFailureAuditService failureAuditService) {
            return new RegFormEventService(
                db,
                studentProfileService,
                new FixedObjectProvider<>(auditTrailService),
                new FixedObjectProvider<>(versionService),
                new FixedObjectProvider<>(failureAuditService));
        }

        @Bean
        CompositeShiftMutationService compositeShiftMutationService(JdbcTemplate db, RegFormEventService eventService) {
            return new CompositeShiftMutationService(db, eventService);
        }
    }

    static class CompositeShiftMutationService {
        private final JdbcTemplate db;
        private final RegFormEventService eventService;

        CompositeShiftMutationService(JdbcTemplate db, RegFormEventService eventService) {
            this.db = db;
            this.eventService = eventService;
        }

        @Transactional(rollbackFor = Exception.class)
        public void shiftAndCapture(String studentNumber, String targetProgram, int targetCurriculumId) {
            db.update("UPDATE students SET program_code = ? WHERE student_number = ?", targetProgram, studentNumber);
            db.update("UPDATE student_curriculum_assignments SET curriculum_id = ? WHERE student_number = ?", targetCurriculumId, studentNumber);
            db.update("DELETE FROM student_enlistments WHERE student_id = ?", studentNumber);
            eventService.recordEvent(
                studentNumber,
                "PROGRAM_SHIFT",
                "Registrar program shift completed",
                null,
                "Controlled integration-test program shift",
                "integration.registrar");
        }
    }

    static class FixedObjectProvider<T> implements org.springframework.beans.factory.ObjectProvider<T> {
        private final T value;

        FixedObjectProvider(T value) {
            this.value = value;
        }

        @Override
        public T getObject() {
            return value;
        }

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public java.util.Iterator<T> iterator() {
            return java.util.List.of(value).iterator();
        }
    }
}
