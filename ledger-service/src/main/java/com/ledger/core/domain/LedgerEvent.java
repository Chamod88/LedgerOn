package com.ledger.core.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "ledger_events")
public class LedgerEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "account_id")
    public String accountId;
    public BigDecimal amount;
    public String currency;

    @Column(name = "transaction_type")
    public String transactionType;

    @Column(name = "idempotency_key", unique = true)
    public String idempotencyKey;
}