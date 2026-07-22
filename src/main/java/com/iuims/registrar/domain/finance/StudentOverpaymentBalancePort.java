package com.iuims.registrar.domain.finance;

public interface StudentOverpaymentBalancePort {

    double getPendingTermCredit(String studentNumber);

    double getForwardedBalanceNet(String studentNumber);
}
