package com.iuims.registrar.config;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "registrar.demo.reset-passwords-on-boot", havingValue = "true")
public class DemoPasswordResetRunner implements CommandLineRunner {

    private final JdbcTemplate db;

    @Value("${registrar.accounts.temporary-password:}")
    private String configuredTemporaryPassword;

    public DemoPasswordResetRunner(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public void run(String... args) {
        if (configuredTemporaryPassword == null || configuredTemporaryPassword.isBlank()) {
            throw new IllegalStateException(
                "Demo password reset requested but registrar.accounts.temporary-password/REGISTRAR_TEMP_PASSWORD is not configured.");
        }
        String validHash = BCrypt.hashpw(configuredTemporaryPassword, BCrypt.gensalt());
        db.update(
            "UPDATE sys_users SET password = ? WHERE username IN (" +
                "'admin', 'prof', 'prof.cruz', 'prof.mendoza', 'prof.garcia', 'prof.santos', 'prof.reyes', 'prof.licuanan', 'faculty'" +
            ")",
            validHash);
        System.out.println(">>> DEMO MODE: Passwords for admin + demo faculty accounts synced to configured temporary password.");
    }
}
