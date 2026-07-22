package com.iuims.registrar.service.academic;
import com.iuims.registrar.entity.Grade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts overdue INC grades to Failed once per day for every configured term.
 * Manual "Expire Due INCs" in Settings remains available for immediate runs.
 */
@Component
public class IncExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(IncExpirationScheduler.class);

    private final JdbcTemplate db;
    private final AcademicGradingService gradingService;

    public IncExpirationScheduler(JdbcTemplate db, AcademicGradingService gradingService) {
        this.db = db;
        this.gradingService = gradingService;
    }

    @Scheduled(cron = "${registrar.inc-expiration.cron:0 30 1 * * *}")
    public void expireDueIncGradesDaily() {
        try {
            List<Integer> termIds = db.queryForList("SELECT term_id FROM academic_terms ORDER BY term_id", Integer.class);
            int total = 0;
            for (Integer termId : termIds) {
                if (termId == null) continue;
                total += gradingService.expireOverdueIncGrades(termId);
            }
            if (total > 0) {
                log.info("INC expiration job converted {} overdue INC grade(s) across {} term(s).", total, termIds.size());
            }
        } catch (Exception e) {
            log.warn("INC expiration job failed: {}", e.getMessage());
        }
    }
}
