package com.iuims.registrar.service.finance;
import com.iuims.registrar.repository.ProgramFeeSettingRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@Import({TermFeeAdminService.class})
class TermFeeAdminServiceTest {

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private TermFeeAdminService service;

    @Autowired
    private ProgramFeeSettingRepository feeSettingRepository;

    @BeforeEach
    void setUp() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS programs (
                program_id INT AUTO_INCREMENT PRIMARY KEY,
                program_code VARCHAR(20) NOT NULL,
                program_name VARCHAR(100) NULL,
                active_status TINYINT NOT NULL DEFAULT 1
            )
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS academic_terms (
                term_id INT AUTO_INCREMENT PRIMARY KEY,
                term_code VARCHAR(20) NULL,
                term_name VARCHAR(100) NULL,
                academic_year VARCHAR(20) NULL,
                semester_number INT NULL,
                status VARCHAR(20) NULL,
                is_active TINYINT NOT NULL DEFAULT 0
            )
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS curriculum_templates (
                curriculum_id INT AUTO_INCREMENT PRIMARY KEY,
                program_id INT NOT NULL,
                curriculum_name VARCHAR(100) NULL,
                is_active TINYINT NOT NULL DEFAULT 0
            )
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS program_fee_settings (
                fee_setting_id INT AUTO_INCREMENT PRIMARY KEY,
                program_id INT NOT NULL,
                term_id INT NULL,
                year_level INT NULL,
                semester_number INT NULL,
                fee_tuition_per_unit DECIMAL(10,2) DEFAULT 0.00,
                fee_lec_per_unit DECIMAL(10,2) DEFAULT 0.00,
                fee_lab_per_unit DECIMAL(10,2) DEFAULT 0.00,
                fee_comp_per_unit DECIMAL(10,2) DEFAULT 0.00,
                fee_rle_per_unit DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_registration DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_library DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_medical DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_id DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_athletic DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_guidance DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_lms DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_insurance DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_cultural DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_av DECIMAL(10,2) DEFAULT 0.00,
                fee_misc_energy DECIMAL(10,2) DEFAULT 0.00,
                fee_other_late_enrollment DECIMAL(10,2) DEFAULT 0.00,
                fee_other_add_drop DECIMAL(10,2) DEFAULT 0.00,
                fee_other_installment DECIMAL(10,2) DEFAULT 0.00,
                fee_other_id DECIMAL(10,2) DEFAULT 0.00,
                fee_other_insurance DECIMAL(10,2) DEFAULT 0.00,
                fee_other_comp DECIMAL(10,2) DEFAULT 0.00,
                fee_other_dev DECIMAL(10,2) DEFAULT 0.00,
                is_active TINYINT NOT NULL DEFAULT 1
            )
            """);

        // Seed some base programs
        db.update("INSERT INTO programs (program_code, program_name) VALUES ('BSIT', 'BS Information Technology')");
        db.update("INSERT INTO programs (program_code, program_name) VALUES ('BSCS', 'BS Computer Science')");
        db.update("INSERT INTO academic_terms (term_code, term_name, is_active) VALUES ('120232024', '1st Sem 23-24', 1)");
    }

    @Test
    void saveFeeRate_createsNewFallbackSetting() {
        boolean saved = service.saveFeeRate(1, null, 1, 1, "TUITION_PER_UNIT", 1500.0);
        assertThat(saved).isTrue();

        Map<String, Double> rates = service.getFeeRatesForScope(1, null, 1, 1);
        assertThat(rates).containsEntry("TUITION_PER_UNIT", 1500.0);
    }

    @Test
    void exactTermFeeWinsOverFallback() {
        // Fallback setting
        service.saveFeeRate(1, null, 1, 1, "MISC_ID", 100.00);
        service.saveFeeRate(1, null, 1, 1, "TUITION_PER_UNIT", 1200.00);

        // Exact term setting
        service.saveFeeRate(1, 10, 1, 1, "MISC_ID", 250.00);

        // Exact term request should return the exact term's MISC_ID, but wait: 
        // since saveFeeRate copies fallback values if it creates a new row,
        // it should have also copied TUITION_PER_UNIT.
        Map<String, Double> rates = service.getFeeRatesForScope(1, 10, 1, 1);
        assertThat(rates).containsEntry("MISC_ID", 250.00);
        assertThat(rates).containsEntry("TUITION_PER_UNIT", 1200.00);

        Map<String, String> sources = service.getFeeRateSourcesForScope(1, 10, 1, 1);
        assertThat(sources).containsEntry("MISC_ID", "EXACT_TERM");
    }

    @Test
    void fallbackFeeSourcesCorrectly() {
        service.saveFeeRate(1, null, 1, 1, "OTHER_INSTALLMENT", 250.00);

        Map<String, Double> rates = service.getFeeRatesForScope(1, 10, 1, 1);
        assertThat(rates).containsEntry("OTHER_INSTALLMENT", 250.00);

        Map<String, String> sources = service.getFeeRateSourcesForScope(1, 10, 1, 1);
        assertThat(sources).containsEntry("OTHER_INSTALLMENT", "FALLBACK");
    }


}
