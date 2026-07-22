package com.iuims.registrar.service.forms;

import com.iuims.registrar.service.support.RegistrarAuditTrailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationFormSnapshotFailureAuditService {

    private final RegistrarAuditTrailService auditTrailService;

    public RegistrationFormSnapshotFailureAuditService(RegistrarAuditTrailService auditTrailService) {
        this.auditTrailService = auditTrailService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String studentNumber,
                              String eventType,
                              String purpose,
                              String actor,
                              String safeReason) {
        auditTrailService.recordStudentAction(
            actor,
            "REG_FORM",
            "REG_FORM_SNAPSHOT_CAPTURE_FAILED",
            studentNumber,
            "Registration Form snapshot capture blocked registrar action",
            "eventType=" + eventType + " | purpose=" + purpose + " | reason=" + safeReason,
            "student_reg_form_versions",
            eventType);
    }
}
