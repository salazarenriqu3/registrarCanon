package com.iuims.registrar.controller;
import com.iuims.registrar.entity.Student;
import com.iuims.registrar.service.withdrawal.WithdrawalService;

import com.iuims.registrar.service.support.StudentIdentityReleaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WithdrawalControllerTest {

    private static final String DEMO_USER = "demo.registrar";

    @Mock
    private WithdrawalService withdrawalService;
    @Mock
    private StudentIdentityReleaseService studentIdentityReleaseService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            new WithdrawalController(withdrawalService, studentIdentityReleaseService)).build();
    }

    @Test
    void studentSingleCourseWithdrawalRedirectsToStudentManager() throws Exception {
        when(withdrawalService.dropSubjectByRegistrar("2026-0001", 101, "ACADEMIC_LOAD", "Need to drop", DEMO_USER))
            .thenReturn(new WithdrawalService.DirectDropResult(42L, 1, 750.0, "SINGLE_SUBJECT"));

        mockMvc.perform(post("/admin/withdrawals/drop-subject")
                .param("studentNumber", "2026-0001")
                .param("scheduleId", "101")
                .param("reasonCode", "ACADEMIC_LOAD")
                .param("remarks", "Need to drop")
                .sessionAttr("currentUser", Map.of("username", DEMO_USER)))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/student-manager?username=2026-0001"));
        verify(withdrawalService).dropSubjectByRegistrar("2026-0001", 101, "Need to drop", DEMO_USER);
    }

    @Test
    void studentFullWithdrawalRedirectsToStudentManager() throws Exception {
        when(withdrawalService.dropStudentByRegistrar(
            "2026-0001", "TRANSFER", "Leaving school", DEMO_USER))
            .thenReturn(new WithdrawalService.DirectDropResult(99L, 3, 2250.0, "FULL_CURRENT_TERM"));

        mockMvc.perform(post("/admin/withdrawals/drop-student")
                .param("studentNumber", "2026-0001")
                .param("reasonCode", "TRANSFER")
                .param("remarks", "Leaving school")
                .sessionAttr("currentUser", Map.of("username", DEMO_USER)))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/student-manager?username=2026-0001"));
        verify(withdrawalService).dropStudentByRegistrar("2026-0001", "TRANSFER", "Leaving school", DEMO_USER);
    }

    @Test
    void shiftLoadCleanupRedirectsToStudentManagerWithoutSchoolWithdrawal() throws Exception {
        when(withdrawalService.clearCurrentTermLoadForProgramShift(
            "2026-0001", "SHIFTING", "Shift from BSIT to BSCS", DEMO_USER))
            .thenReturn(new WithdrawalService.DirectDropResult(77L, 3, 2250.0, "SHIFT_PROGRAM_CLEANUP"));

        mockMvc.perform(post("/admin/withdrawals/clear-load-for-shift")
                .param("studentNumber", "2026-0001")
                .param("reasonCode", "SHIFTING")
                .param("remarks", "Shift from BSIT to BSCS")
                .sessionAttr("currentUser", Map.of("username", DEMO_USER)))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/student-manager?username=2026-0001"));
        verify(withdrawalService).clearCurrentTermLoadForProgramShift("2026-0001", "SHIFTING", "Shift from BSIT to BSCS", DEMO_USER);
    }
}
