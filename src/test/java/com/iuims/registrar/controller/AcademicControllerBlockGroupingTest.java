package com.iuims.registrar.controller;
import com.iuims.registrar.entity.Program;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AcademicControllerBlockGroupingTest {

    @Test
    void groupsBlocksBySchoolThenProgramInStableOrder() {
        List<AcademicController.BlockSchoolGroup> groups = AcademicController.groupBlocksBySchoolAndProgram(List.of(
            block("BSIT", "Information Technology", "School of Computing", "BSIT-1-1-A"),
            block("BSBA", "Business Administration", "School of Business", "BSBA-1-1-A"),
            block("BSIT", "Information Technology", "School of Computing", "BSIT-1-1-B"),
            block("BSCS", "Computer Science", "School of Computing", "BSCS-1-1-A")
        ));

        assertThat(groups).extracting(AcademicController.BlockSchoolGroup::schoolName)
            .containsExactly("School of Business", "School of Computing");
        assertThat(groups.get(1).programs()).extracting(AcademicController.BlockProgramGroup::programCode)
            .containsExactly("BSCS", "BSIT");
        assertThat(groups.get(1).programs().get(1).blocks()).extracting(block -> block.get("block_code"))
            .containsExactly("BSIT-1-1-A", "BSIT-1-1-B");
    }

    @Test
    void assignsLegacyProgramWithoutSchoolToUnassignedSchool() {
        Map<String, Object> legacyBlock = block("BSLEG", "Legacy Program", "", "BSLEG-1-1-A");

        List<AcademicController.BlockSchoolGroup> groups =
            AcademicController.groupBlocksBySchoolAndProgram(new ArrayList<>(List.of(legacyBlock)));

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.schoolName()).isEqualTo("Unassigned School");
            assertThat(group.programs()).singleElement().satisfies(program ->
                assertThat(program.programCode()).isEqualTo("BSLEG"));
        });
    }

    @Test
    void keepsSummaryCountsWithoutPretendingToLoadBlockHeaders() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("program_code", "BSIT");
        summary.put("program_name", "Information Technology");
        summary.put("school_name", "School of Computing");
        summary.put("block_count", 64);

        List<AcademicController.BlockSchoolGroup> groups =
            AcademicController.groupBlockProgramSummaries(List.of(summary));

        assertThat(groups).singleElement().satisfies(school ->
            assertThat(school.programs()).singleElement().satisfies(program -> {
                assertThat(program.blockCount()).isEqualTo(64);
                assertThat(program.blocks()).isEmpty();
            }));
    }

    private static Map<String, Object> block(String programCode,
                                             String programName,
                                             String schoolName,
                                             String blockCode) {
        Map<String, Object> block = new HashMap<>();
        block.put("program_code", programCode);
        block.put("program_name", programName);
        block.put("school_name", schoolName);
        block.put("block_code", blockCode);
        return block;
    }
}
