package com.iuims.registrar.service.academic;

import com.iuims.registrar.service.support.GlobalTermService;
import com.iuims.registrar.service.forms.RegFormEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermRolloverDispositionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private GlobalTermService globalTermService;
    @Mock
    private ObjectProvider<TermRolloverDispositionService.WithdrawalDispositionBridge> withdrawalDispositionBridgeProvider;
    @Mock
    private RegFormEventService regFormEventService;

    @Test
    void resolveNextEnrollmentMode_marksStudentsWithDeficienciesAsManual() {
        when(jdbcTemplate.queryForObject(
            contains("student_curriculum_assignments"),
            eq(Integer.class),
            any(Object[].class)))
            .thenReturn(11);
        when(jdbcTemplate.queryForObject(
            contains("curriculum_courses"),
            eq(Integer.class),
            any(Object[].class)))
            .thenReturn(1);

        TermRolloverDispositionService service = new TermRolloverDispositionService(
            jdbcTemplate,
            globalTermService,
            withdrawalDispositionBridgeProvider,
            regFormEventService);

        assertEquals(
            TermRolloverDispositionService.ENROLLMENT_MODE_IRREGULAR_MANUAL,
            service.resolveNextEnrollmentMode("2026-0001"));
    }

    @Test
    void resolveNextEnrollmentMode_keepsCleanStudentsOnRegularBlock() {
        when(jdbcTemplate.queryForObject(
            contains("student_curriculum_assignments"),
            eq(Integer.class),
            any(Object[].class)))
            .thenReturn(11);
        when(jdbcTemplate.queryForObject(
            contains("curriculum_courses"),
            eq(Integer.class),
            any(Object[].class)))
            .thenReturn(0);

        TermRolloverDispositionService service = new TermRolloverDispositionService(
            jdbcTemplate,
            globalTermService,
            withdrawalDispositionBridgeProvider,
            regFormEventService);

        assertEquals(
            TermRolloverDispositionService.ENROLLMENT_MODE_REGULAR_BLOCK,
            service.resolveNextEnrollmentMode("2026-0001"));
    }
}
