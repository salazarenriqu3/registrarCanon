package com.iuims.registrar.curriculum;

import java.math.BigDecimal;

public interface TransferCreditGradePort {

    void saveTransferCredit(String studentNumber,
                            int courseId,
                            String studentName,
                            String lockReason,
                            BigDecimal numericGrade);
}
