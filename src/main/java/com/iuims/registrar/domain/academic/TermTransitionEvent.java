package com.iuims.registrar.domain.academic;

import java.util.concurrent.atomic.AtomicInteger;

public class TermTransitionEvent {
    private final String studentNumber;
    private final String targetDbTermCode;
    private final AtomicInteger withForwardedDebtCounter;

    public TermTransitionEvent(String studentNumber, String targetDbTermCode, AtomicInteger withForwardedDebtCounter) {
        this.studentNumber = studentNumber;
        this.targetDbTermCode = targetDbTermCode;
        this.withForwardedDebtCounter = withForwardedDebtCounter;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getTargetDbTermCode() {
        return targetDbTermCode;
    }

    public AtomicInteger getWithForwardedDebtCounter() {
        return withForwardedDebtCounter;
    }
}
