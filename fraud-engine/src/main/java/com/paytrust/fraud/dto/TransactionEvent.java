package com.paytrust.fraud.dto;

import java.math.BigDecimal;

public class TransactionEvent {
    public String idempotencyKey;
    public String accountId;
    public BigDecimal amount;
    public String currency;
    public String transactionType;
}
