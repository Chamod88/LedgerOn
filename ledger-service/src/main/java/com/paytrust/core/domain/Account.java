package com.paytrust.core.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    public String id;
    public BigDecimal balance;
    public String currency;

    @Version
    public int version;
}