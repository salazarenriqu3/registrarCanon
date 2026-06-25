package com.iuims.registrar.withdrawal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WithdrawalControllerTest {

    private static final String DEMO_USER = "demo.registrar";

    @Mock
    private WithdrawalService withdrawalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WithdrawalController(withdrawalService)).build();
    }

    @Test
    void studentSingleCourseWithdrawalRequestRedirectsToStudentManager() throws Exception {
        when(withdrawalService.createRequest("2026-0001", 101, "ACADEMIC_LOAD", "Need to drop", DEMO_USER))
            .thenReturn(42L);

        mockMvc.perform(post("/admin/withdrawals/request")
                .param("studentNumber", "2026-0001")
                .param("scheduleId", "101")
                .param("reasonCode", "ACADEMIC_LOAD")
                .param("remarks", "Need to drop")
                .sessionAttr("currentUser", Map.of("username", DEMO_USER)))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/student-manager?username=2026-0001"))
            .andExpect(flash().attribute("successMessage",
                containsString("Class withdrawal request #42 submitted for Registrar approval.")));
    }

    @Test
    void studentFullWithdrawalRequestRedirectsToStudentManager() throws Exception {
        when(withdrawalService.createFullCurrentTermRequest(
            "2026-0001", "TRANSFER", "Leaving school", DEMO_USER)).thenReturn(99L);

        mockMvc.perform(post("/admin/withdrawals/request-student")
                .param("studentNumber", "2026-0001")
                .param("reasonCode", "TRANSFER")
                .param("remarks", "Leaving school")
                .sessionAttr("currentUser", Map.of("username", DEMO_USER)))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/student-manager?username=2026-0001"))
            .andExpect(flash().attribute("successMessage",
                containsString("Full-student withdrawal request #99 submitted for Registrar approval.")));
    }

    @Test
    void registrarApprovalRedirectsBackToQueue() throws Exception {
        when(withdrawalService.approveAndExecuteRequest(7L, DEMO_USER))
            .thenReturn(new WithdrawalService.DirectDropResult(7L, 2, 750.0, "FULL_CURRENT_TERM"));

        mockMvc.perform(post("/admin/withdrawals/approve")
                .param("requestId", "7")
                .sessionAttr("currentUser", Map.of("username", DEMO_USER)))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/withdrawals"))
            .andExpect(flash().attribute("successMessage",
                containsString("Withdrawal request #7 completed. 2 subject(s) processed. Applied charge: PHP 750.00.")));
    }
}
