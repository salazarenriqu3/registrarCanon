package com.iuims.registrar.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StudentProfileServiceTest {

    private JdbcTemplate db;
    private StudentProfileService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:studentprofile" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        db = new JdbcTemplate(dataSource);
        db.execute("""
            CREATE TABLE sys_users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) UNIQUE,
                real_name VARCHAR(100)
            )
            """);
        db.execute("""
            CREATE TABLE student_number_release_registry (
                released_student_number VARCHAR(100) PRIMARY KEY,
                archive_key VARCHAR(80) NOT NULL,
                release_status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
                released_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                released_by VARCHAR(100) NULL,
                release_note VARCHAR(500) NULL,
                reissued_reference_number VARCHAR(100) NULL,
                reissued_student_number VARCHAR(100) NULL,
                reissued_at TIMESTAMP NULL,
                reissued_by VARCHAR(100) NULL,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        service = new StudentProfileService(db);
        service.ensureSchema();
    }

    @Test
    void editableProfileSurvivesLeanApplicantTable() {
        db.execute("""
            CREATE TABLE applicants (
                reference_number VARCHAR(50) PRIMARY KEY,
                first_name VARCHAR(100),
                last_name VARCHAR(100),
                email VARCHAR(150),
                mobile VARCHAR(30),
                last_school TEXT,
                course_taken VARCHAR(100)
            )
            """);
        db.update("""
            INSERT INTO students (student_number, reference_number, first_name, last_name, real_name, email, mobile)
            VALUES ('26-1-00001', 'IRG-0001', '', '', '', '', '')
            """);
        db.update("""
            INSERT INTO applicants (reference_number, first_name, last_name, email, mobile, last_school, course_taken)
            VALUES ('IRG-0001', 'Mara', 'Delacruz', 'mara@example.edu', '09171234567', 'St. Claire College', 'BSIT subjects')
            """);

        Map<String, Object> profile = service.getEditableProfile("26-1-00001");

        assertThat(profile).containsEntry("first_name", "Mara");
        assertThat(profile).containsEntry("last_name", "Delacruz");
        assertThat(profile).containsEntry("email", "mara@example.edu");
        assertThat(profile).containsEntry("mobile", "09171234567");
        assertThat(profile).containsEntry("last_school", "St. Claire College");
        assertThat(profile).containsEntry("course_taken", "BSIT subjects");
        assertThat(profile).containsKey("guardian_name");
    }

    @Test
    void updateProfileSyncsRegistrarIdentityToSysUsers() {
        db.execute("CREATE TABLE applicants (reference_number VARCHAR(50) PRIMARY KEY)");
        db.update("INSERT INTO sys_users (username, real_name) VALUES ('26-1-00002', 'Old Name')");
        db.update("INSERT INTO students (student_number, real_name) VALUES ('26-1-00002', 'Old Name')");

        Map<String, String> form = new HashMap<>();
        form.put("first_name", "Lia");
        form.put("middle_name", "Santos");
        form.put("last_name", "Ramos");
        form.put("email", "lia.ramos@example.edu");
        form.put("mobile", "09170001111");

        service.updateProfile("26-1-00002", form);

        Map<String, Object> user = db.queryForMap("SELECT * FROM sys_users WHERE username = '26-1-00002'");
        assertThat(user).containsEntry("real_name", "Lia Santos Ramos");
        assertThat(user).containsEntry("first_name", "Lia");
        assertThat(user).containsEntry("middle_name", "Santos");
        assertThat(user).containsEntry("last_name", "Ramos");
        assertThat(user).containsEntry("email", "lia.ramos@example.edu");
        assertThat(user).containsEntry("mobile", "09170001111");
    }

    @Test
    void archivedIdentityResolvesWithoutMintingNewArchiveKey() {
        db.update("""
            INSERT INTO students (student_number, archive_key, reference_number, first_name, last_name, real_name,
                admission_status, status, is_active)
            VALUES ('ARCH-OLD-001', 'ARCH-OLD-001', 'REF-OLD-001', 'Nina', 'Reyes', 'Nina Reyes',
                'WITHDRAWN', 'WITHDRAWN', 0)
            """);
        db.update("""
            INSERT INTO student_number_release_registry (
                released_student_number, archive_key, release_status, released_by, release_note
            ) VALUES ('26-1-00009', 'ARCH-OLD-001', 'AVAILABLE', 'registrar', 'archived release')
            """);

        assertThat(service.resolveCurrentStudentNumber("26-1-00009")).isEqualTo("ARCH-OLD-001");
        assertThat(service.ensureArchiveKey("26-1-00009")).isEqualTo("ARCH-OLD-001");

        Map<String, Object> profile = service.getEditableProfile("26-1-00009");
        assertThat(profile).containsEntry("student_number", "ARCH-OLD-001");
        assertThat(profile).containsEntry("archive_key", "ARCH-OLD-001");
    }
}
