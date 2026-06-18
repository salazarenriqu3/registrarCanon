package com.iuims.registrar.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YearLevelLoadPolicyServiceTest {

    private YearLevelLoadPolicyService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:load-policy-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate db = new JdbcTemplate(dataSource);
        db.execute("CREATE TABLE enrollment_settings (setting_key VARCHAR(80) PRIMARY KEY, setting_value VARCHAR(500))");
        db.update("INSERT INTO enrollment_settings (setting_key, setting_value) VALUES ('max_units_regular', '27')");
        service = new YearLevelLoadPolicyService(db);
    }

    @Test
    void seedsFourYearLevelsFromLegacyMaximum() {
        assertThat(service.listPolicies()).hasSize(4);
        assertThat(service.resolve(1).maximumUnits()).isEqualByComparingTo("27");
        assertThat(service.resolve(4).minimumUnits()).isEqualByComparingTo("0");
    }

    @Test
    void savesDifferentMinimumAndMaximumPerYearLevel() {
        Map<String, String> params = validPolicyParams();
        params.put("minimumUnits_2", "16");
        params.put("maximumUnits_2", "25.5");

        service.savePolicies(params);

        assertThat(service.resolve(2).minimumUnits()).isEqualByComparingTo("16");
        assertThat(service.resolve(2).maximumUnits()).isEqualByComparingTo("25.5");
        assertThat(service.resolve(1).maximumUnits()).isEqualByComparingTo("27");
    }

    @Test
    void rejectsMinimumAboveMaximumWithoutPartialUpdate() {
        Map<String, String> params = validPolicyParams();
        params.put("minimumUnits_2", "28");
        params.put("maximumUnits_2", "27");
        params.put("minimumUnits_1", "15");

        assertThatThrownBy(() -> service.savePolicies(params))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Year 2 minimum units");

        assertThat(service.resolve(1).minimumUnits()).isEqualByComparingTo("0");
    }

    @Test
    void classifiesLoadsAgainstTheSelectedYearLevel() {
        Map<String, String> params = validPolicyParams();
        params.put("minimumUnits_3", "15");
        params.put("maximumUnits_3", "24");
        service.savePolicies(params);

        assertThat(service.classify(3, new BigDecimal("12")))
            .isEqualTo(YearLevelLoadPolicyService.LoadStanding.UNDERLOAD);
        assertThat(service.classify(3, new BigDecimal("18")))
            .isEqualTo(YearLevelLoadPolicyService.LoadStanding.REGULAR);
        assertThat(service.classify(3, new BigDecimal("27")))
            .isEqualTo(YearLevelLoadPolicyService.LoadStanding.OVERLOAD);
    }

    private Map<String, String> validPolicyParams() {
        Map<String, String> params = new HashMap<>();
        for (int yearLevel = 1; yearLevel <= 4; yearLevel++) {
            params.put("minimumUnits_" + yearLevel, "0");
            params.put("maximumUnits_" + yearLevel, "27");
        }
        return params;
    }
}
