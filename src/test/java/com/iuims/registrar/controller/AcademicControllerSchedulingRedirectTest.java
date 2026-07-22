package com.iuims.registrar.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AcademicControllerSchedulingRedirectTest {

    @Test
    void redirectToClassSchedulingKeepsBlocksViewAndContext() {
        String redirect = AcademicController.redirectToClassScheduling(
            108,
            "blocks",
            44,
            "NNAD",
            "BSIT",
            3,
            "AECO",
            "block-card-44",
            "SUCCESS: Added slot.");

        assertThat(redirect)
            .startsWith("redirect:/admin/class-scheduling?termId=108")
            .contains("view=blocks")
            .contains("openBlockId=44")
            .contains("blockSearch=NNAD")
            .contains("blockProgram=BSIT")
            .doesNotContain("blockPage=")
            .contains("courseSearch=AECO")
            .contains("anchor=block-card-44")
            .contains("msg=SUCCESS%3A+Added+slot.");
    }
}
