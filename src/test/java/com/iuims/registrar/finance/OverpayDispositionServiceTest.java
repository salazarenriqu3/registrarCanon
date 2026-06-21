package com.iuims.registrar.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverpayDispositionServiceTest {

    @Mock
    private JdbcTemplate db;

    @Mock
    private StudentOverpaymentBalancePort balancePort;

    private OverpayDispositionService service;

    @BeforeEach
    void setUp() {
        service = new OverpayDispositionService(db, balancePort);
    }

    @Test
    void applyAsCredit_usesFullPendingWhenAmountZero() {
        when(balancePort.getPendingTermCredit("2026-0001")).thenReturn(2500.0, 2500.0, 0.0);
        when(balancePort.getForwardedBalanceNet("2026-0001")).thenReturn(-2500.0);

        OverpayDispositionService.DispositionResult result =
            service.applyAsCredit("2026-0001", 0, "admin", "test");

        assertThat(result.success()).isTrue();
        assertThat(result.pendingRemaining()).isEqualTo(0.0);
        assertThat(result.forwardNet()).isEqualTo(-2500.0);
    }

    @Test
    void refundAsCash_rejectsWhenExceedsPending() {
        when(balancePort.getPendingTermCredit("2026-0001")).thenReturn(1000.0);

        OverpayDispositionService.DispositionResult result =
            service.refundAsCash("2026-0001", 1500, "admin", "test");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("exceeds pending");
    }
}
