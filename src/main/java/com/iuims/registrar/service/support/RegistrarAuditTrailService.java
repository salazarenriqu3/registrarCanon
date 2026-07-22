package com.iuims.registrar.service.support;
import com.iuims.registrar.entity.Student;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RegistrarAuditTrailService {

    private final JdbcTemplate db;

    public RegistrarAuditTrailService(JdbcTemplate db) {
        this.db = db;
    }

    public void ensureSchema() {
        db.execute("""
            CREATE TABLE IF NOT EXISTS audit_logs (
                log_id INT AUTO_INCREMENT PRIMARY KEY,
                admin_id INT NULL,
                actor_username VARCHAR(100) NULL,
                actor_role VARCHAR(50) NULL,
                module_name VARCHAR(80) NULL,
                action_name VARCHAR(100) NULL,
                target_type VARCHAR(80) NULL,
                target_key VARCHAR(120) NULL,
                summary VARCHAR(255) NULL,
                details TEXT NULL,
                source_table VARCHAR(80) NULL,
                source_id VARCHAR(120) NULL,
                action VARCHAR(255) NULL,
                log_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                KEY idx_audit_actor_date (actor_username, log_date),
                KEY idx_audit_module_date (module_name, log_date),
                KEY idx_audit_target_date (target_type, target_key, log_date)
            )
            """);
        addColumn("actor_username", "VARCHAR(100) NULL");
        addColumn("actor_role", "VARCHAR(50) NULL");
        addColumn("module_name", "VARCHAR(80) NULL");
        addColumn("action_name", "VARCHAR(100) NULL");
        addColumn("target_type", "VARCHAR(80) NULL");
        addColumn("target_key", "VARCHAR(120) NULL");
        addColumn("summary", "VARCHAR(255) NULL");
        addColumn("details", "TEXT NULL");
        addColumn("source_table", "VARCHAR(80) NULL");
        addColumn("source_id", "VARCHAR(120) NULL");
        createIndex("idx_audit_actor_date", "actor_username, log_date");
        createIndex("idx_audit_module_date", "module_name, log_date");
        createIndex("idx_audit_target_date", "target_type, target_key, log_date");
    }

    public void record(String actorUsername,
                       String actorRole,
                       String moduleName,
                       String actionName,
                       String targetType,
                       String targetKey,
                       String summary,
                       String details,
                       String sourceTable,
                       String sourceId) {
        try {
            ensureSchema();
            String actor = clean(actorUsername, 100, "SYSTEM");
            String action = clean(actionName, 100, "REGISTRAR_ACTION");
            String safeSummary = clean(summary, 255, action);
            Integer adminId = resolveAdminId(actor);
            db.update("""
                INSERT INTO audit_logs
                    (admin_id, actor_username, actor_role, module_name, action_name,
                     target_type, target_key, summary, details, source_table, source_id, action)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                adminId,
                actor,
                cleanNullable(actorRole, 50),
                clean(moduleName, 80, "REGISTRAR"),
                action,
                cleanNullable(targetType, 80),
                cleanNullable(targetKey, 120),
                safeSummary,
                cleanNullable(details, 4000),
                cleanNullable(sourceTable, 80),
                cleanNullable(sourceId, 120),
                clean(safeSummary + " [" + action + "]", 255, action));
        } catch (Exception ignored) {
        }
    }

    public void recordStudentAction(String actorUsername,
                                    String moduleName,
                                    String actionName,
                                    String studentNumber,
                                    String summary,
                                    String details,
                                    String sourceTable,
                                    String sourceId) {
        record(actorUsername, "Registrar", moduleName, actionName, "STUDENT",
            studentNumber, summary, details, sourceTable, sourceId);
    }

    private Integer resolveAdminId(String actorUsername) {
        if (actorUsername == null || actorUsername.isBlank()) {
            return null;
        }
        try {
            return db.queryForObject(
                "SELECT user_id FROM sys_users WHERE username = ? LIMIT 1",
                Integer.class,
                actorUsername.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private void addColumn(String columnName, String definition) {
        try {
            db.execute("ALTER TABLE audit_logs ADD COLUMN " + columnName + " " + definition);
        } catch (Exception ignored) {
        }
    }

    private void createIndex(String indexName, String columns) {
        try {
            db.execute("CREATE INDEX " + indexName + " ON audit_logs (" + columns + ")");
        } catch (Exception ignored) {
        }
    }

    private String clean(String value, int maxLength, String defaultValue) {
        String cleaned = value != null && !value.isBlank() ? value.trim() : defaultValue;
        if (cleaned == null) {
            return null;
        }
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private String cleanNullable(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
