package com.iuims.registrar.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "term_transition_audit")
public class TermTransitionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "requested_term_code", length = 32)
    private String requestedTermCode;

    @Column(name = "target_db_term_code", length = 32)
    private String targetDbTermCode;

    @Column(name = "target_term_id")
    private Integer targetTermId;

    @Column(name = "success", nullable = false)
    private Byte success = 0;

    @Column(name = "advanced_count", nullable = false)
    private Integer advancedCount = 0;

    @Column(name = "forwarded_debt_count", nullable = false)
    private Integer forwardedDebtCount = 0;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Getters and setters
    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }
    public String getRequestedTermCode() { return requestedTermCode; }
    public void setRequestedTermCode(String requestedTermCode) { this.requestedTermCode = requestedTermCode; }
    public String getTargetDbTermCode() { return targetDbTermCode; }
    public void setTargetDbTermCode(String targetDbTermCode) { this.targetDbTermCode = targetDbTermCode; }
    public Integer getTargetTermId() { return targetTermId; }
    public void setTargetTermId(Integer targetTermId) { this.targetTermId = targetTermId; }
    public Byte getSuccess() { return success; }
    public void setSuccess(Byte success) { this.success = success; }
    public Integer getAdvancedCount() { return advancedCount; }
    public void setAdvancedCount(Integer advancedCount) { this.advancedCount = advancedCount; }
    public Integer getForwardedDebtCount() { return forwardedDebtCount; }
    public void setForwardedDebtCount(Integer forwardedDebtCount) { this.forwardedDebtCount = forwardedDebtCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
