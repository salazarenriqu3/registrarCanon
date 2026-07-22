package com.iuims.registrar.service.scholarship;

import com.iuims.registrar.service.academic.AcademicGradingService;
import com.iuims.registrar.service.support.EnlistmentSchemaService;
import com.iuims.registrar.service.support.GlobalTermService;
import com.iuims.registrar.service.support.YearLevelLoadPolicyService;
import com.iuims.registrar.service.curriculum.CurriculumLoadPolicyService;
import com.iuims.registrar.service.curriculum.StudentCurriculumService;
import com.iuims.registrar.service.finance.TermFeeAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ScholarEnrollmentServiceWithdrawalGuardTest {

    private JdbcTemplate db;
    private ScholarEnrollmentService service;

    @BeforeEach
    void setUp() {
        db = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:scholar-withdrawal-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""));
        db.execute("""
            CREATE TABLE students (
                student_number VARCHAR(100) PRIMARY KEY,
                first_name VARCHAR(100),
                last_name VARCHAR(100),
                program_code VARCHAR(40),
                year_level INT,
                semester INT,
                term_year VARCHAR(40),
                admission_status VARCHAR(40),
                status VARCHAR(40),
                is_active INT DEFAULT 1
            )
            """);
        db.execute("""
            CREATE TABLE student_ledger (
                ledger_id INT AUTO_INCREMENT PRIMARY KEY,
                student_id VARCHAR(100),
                transaction_type VARCHAR(80),
                description VARCHAR(255),
                debit DECIMAL(12,2) DEFAULT 0,
                credit DECIMAL(12,2) DEFAULT 0
            )
            """);
        db.update("""
            INSERT INTO students (student_number, first_name, last_name, program_code, year_level, semester, term_year, admission_status, status, is_active)
            VALUES ('2026-0001', 'Lyncer', 'Contang', 'BSPSYCH', 1, 1, 'SL2024202511', 'WITHDRAWN', 'WITHDRAWN', 0)
            """);

        service = new ScholarEnrollmentService(
            db,
            mock(AcademicGradingService.class),
            mock(GlobalTermService.class),
            mock(EnlistmentSchemaService.class),
            mock(StudentCurriculumService.class),
            mock(TermFeeAdminService.class),
            mock(YearLevelLoadPolicyService.class),
            mock(CurriculumLoadPolicyService.class));
    }

    @Test
    void withdrawnStudentCannotPostWalkInPayments() {
        String result = service.processWalkInPayment(
            "2026-0001",
            1500.0,
            "CASH",
            "Tuition Fee",
            1,
            1,
            "SL2024202511");

        assertThat(result).isEqualTo("ERROR: Withdrawn students cannot post payments through the cashier.");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM student_ledger", Integer.class)).isZero();
    }
}
