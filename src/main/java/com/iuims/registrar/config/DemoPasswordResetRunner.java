package com.iuims.registrar.config;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "registrar.demo.reset-passwords-on-boot", havingValue = "true")
public class DemoPasswordResetRunner implements CommandLineRunner {

    private final JdbcTemplate db;

    public DemoPasswordResetRunner(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public void run(String... args) {
        String validHash = BCrypt.hashpw("1234", BCrypt.gensalt());
        db.update(
            "UPDATE sys_users SET password = ? WHERE username IN (" +
                "'admin', 'prof', 'prof.cruz', 'prof.mendoza', 'prof.garcia', 'prof.santos', 'prof.reyes', 'prof.licuanan', 'faculty'" +
            ")",
            validHash);
        System.out.println(">>> DEMO MODE: Passwords for admin + demo faculty accounts synced to 1234.");
    }
}
