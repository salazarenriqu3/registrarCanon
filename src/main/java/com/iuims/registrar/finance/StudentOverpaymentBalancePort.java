package com.iuims.registrar.finance;

public interface StudentOverpaymentBalancePort {

    double getPendingTermCredit(String studentNumber);

    double getForwardedBalanceNet(String studentNumber);
}
