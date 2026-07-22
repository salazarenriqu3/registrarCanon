package com.iuims.registrar.service.forms;

import com.iuims.registrar.domain.forms.RegistrationFormSnapshotException;

import com.iuims.registrar.service.support.StudentProfileService;
import com.iuims.registrar.service.support.RegistrarAuditTrailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

class RegFormEventServiceTest {

    private JdbcTemplate db;
    private RegFormEventService service;
    private RegFormVersionService versionService;

    @BeforeEach
    void setUp() {
        db = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:reg-form-events;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("DROP ALL OBJECTS");
        db.execute("""
            CREATE TABLE student_reg_form_events (
                event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                student_number VARCHAR(100) NOT NULL,
                archive_key VARCHAR(80),
                event_type VARCHAR(60) NOT NULL,
                purpose VARCHAR(160) NOT NULL,
                related_request_id BIGINT,
                remarks VARCHAR(500),
                triggered_by VARCHAR(100),
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        db.execute("""
            CREATE TABLE student_reg_form_versions (
                version_id BIGINT PRIMARY KEY,
                source_event_id BIGINT,
                created_at TIMESTAMP NOT NULL
            )
            """);
        @SuppressWarnings("unchecked")
        ObjectProvider<RegistrarAuditTrailService> auditTrailProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RegFormVersionService> versionServiceProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RegistrationFormSnapshotFailureAuditService> failureAuditProvider = mock(ObjectProvider.class);
        versionService = mock(RegFormVersionService.class);
        when(versionServiceProvider.getIfAvailable()).thenReturn(versionService);
        service = new RegFormEventService(
            db,
            mock(StudentProfileService.class),
            auditTrailProvider,
            versionServiceProvider,
            failureAuditProvider);
    }

    @Test
    void recentEventsExposeOnlyTheirLinkedSavedVersion() {
        db.update("""
            INSERT INTO student_reg_form_events
                (event_id, student_number, event_type, purpose, created_at)
            VALUES (11, '2026-0001', 'SUBJECT_ADD', 'Subject added', CURRENT_TIMESTAMP)
            """);
        db.update("""
            INSERT INTO student_reg_form_events
                (event_id, student_number, event_type, purpose, created_at)
            VALUES (12, '2026-0001', 'REG_FORM_EVENT', 'Legacy event', CURRENT_TIMESTAMP)
            """);
        db.update("""
            INSERT INTO student_reg_form_versions (version_id, source_event_id, created_at)
            VALUES (91, 11, CURRENT_TIMESTAMP)
            """);

        List<Map<String, Object>> events = service.listRecentEvents(null, null, null, null, 25);
        Map<String, Object> savedEvent = eventById(events, 11L);
        Map<String, Object> legacyEvent = eventById(events, 12L);

        assertThat(savedEvent.get("version_id")).isEqualTo(91L);
        assertThat(savedEvent.get("has_saved_version")).isEqualTo(true);
        assertThat(savedEvent.get("version_created_at")).isNotNull();
        assertThat(legacyEvent.get("version_id")).isNull();
        assertThat(legacyEvent.get("has_saved_version")).isEqualTo(false);
        verify(versionService).ensureSchema();
    }

    @Test
    void requiredSnapshotFailurePropagatesInsteadOfLeavingAnEventRow() {
        StudentProfileService profileService = mock(StudentProfileService.class);
        when(profileService.ensureArchiveKey("2026-0002")).thenReturn("ARCH-2026-0002");
        @SuppressWarnings("unchecked")
        ObjectProvider<RegistrarAuditTrailService> auditTrailProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RegFormVersionService> versionServiceProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RegistrationFormSnapshotFailureAuditService> failureAuditProvider = mock(ObjectProvider.class);
        RegistrationFormSnapshotFailureAuditService failureAuditService = mock(RegistrationFormSnapshotFailureAuditService.class);
        when(versionServiceProvider.getIfAvailable()).thenReturn(versionService);
        when(failureAuditProvider.getIfAvailable()).thenReturn(failureAuditService);
        doThrow(new IllegalStateException("snapshot store unavailable"))
            .when(versionService).captureVersion(anyString(), anyString(), anyString(), anyLong(), isNull(), anyString());
        RegFormEventService failingService = new RegFormEventService(
            db, profileService, auditTrailProvider, versionServiceProvider, failureAuditProvider);

        TransactionTemplate transactionTemplate = new TransactionTemplate(
            new DataSourceTransactionManager(db.getDataSource()));
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> failingService.recordEvent(
            "2026-0002", "SUBJECT_ADD", "Subject added", null, null, "registrar")))
            .isInstanceOf(RegistrationFormSnapshotException.class);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM student_reg_form_events WHERE student_number = '2026-0002'", Integer.class)).isZero();
        verify(failureAuditService).recordFailure(
            eq("2026-0002"), eq("SUBJECT_ADD"), eq("Subject added"), eq("registrar"), anyString());
    }

    @Test
    void requiredEventPassesItsGeneratedIdToSnapshotCapture() {
        StudentProfileService profileService = mock(StudentProfileService.class);
        when(profileService.ensureArchiveKey("2026-0003")).thenReturn("ARCH-2026-0003");
        @SuppressWarnings("unchecked")
        ObjectProvider<RegistrarAuditTrailService> auditTrailProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RegFormVersionService> versionServiceProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RegistrationFormSnapshotFailureAuditService> failureAuditProvider = mock(ObjectProvider.class);
        when(versionServiceProvider.getIfAvailable()).thenReturn(versionService);
        when(versionService.captureVersion(anyString(), anyString(), anyString(), anyLong(), isNull(), anyString()))
            .thenReturn(900L);
        RegFormEventService recordingService = new RegFormEventService(
            db, profileService, auditTrailProvider, versionServiceProvider, failureAuditProvider);

        Long eventId = recordingService.recordEvent(
            "2026-0003", "SUBJECT_ADD", "Subject added", null, null, "registrar");

        verify(versionService).captureVersion(
            "2026-0003", "SUBJECT_ADD", "Subject added", eventId, null, "registrar");
    }

    private Map<String, Object> eventById(List<Map<String, Object>> events, long eventId) {
        return events.stream()
            .filter(event -> ((Number) event.get("event_id")).longValue() == eventId)
            .findFirst()
            .orElseThrow();
    }
}
