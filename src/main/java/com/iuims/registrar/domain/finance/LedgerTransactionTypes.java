package com.iuims.registrar.domain.finance;

/** Shared student_ledger transaction_type values (Registrar overpayment phase 1). */
public final class LedgerTransactionTypes {

    public static final String FORWARDED_BALANCE = "FORWARDED_BALANCE";
    public static final String PENDING_TERM_CREDIT = "PENDING_TERM_CREDIT";
    public static final String REFUND_PAYOUT = "REFUND_PAYOUT";

    private LedgerTransactionTypes() {}
}
