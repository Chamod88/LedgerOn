package com.paytrust.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class TransactionEvent {
    @NotBlank(message = "idempotencyKey is required")
    public String idempotencyKey;

    @NotBlank(message = "accountId is required")
    public String accountId;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than zero")
    public BigDecimal amount;

    @NotBlank(message = "currency is required")
    public String currency;

    @NotBlank(message = "transactionType is required")
    public String transactionType;
}