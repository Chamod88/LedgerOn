package com.ledger.core.service;

import com.ledger.core.domain.Account;
import com.ledger.core.domain.LedgerEvent;
import com.ledger.core.dto.TransactionEvent;
import com.ledger.core.repository.AccountRepository;
import com.ledger.core.repository.LedgerEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransactionExecutor {

    private static final Logger log = LoggerFactory.getLogger(TransactionExecutor.class);
    private final AccountRepository accountRepository;
    private final LedgerEventRepository eventRepository;

    public TransactionExecutor(AccountRepository accountRepository, LedgerEventRepository eventRepository) {
        this.accountRepository = accountRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void execute(TransactionEvent event) {
        Account account = accountRepository.findById(event.accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + event.accountId));

        // Security / Correctness constraint: currency validation
        if (!account.currency.equalsIgnoreCase(event.currency)) {
            log.error("CURRENCY MISMATCH: Account {} uses {}, but transaction requested {}", 
                    event.accountId, account.currency, event.currency);
            throw new IllegalArgumentException("Currency mismatch: Account uses " + account.currency 
                    + ", but transaction requested " + event.currency);
        }

        BigDecimal transactionAmount = event.amount;

        if ("WITHDRAWAL".equalsIgnoreCase(event.transactionType)) {
            if (account.balance.compareTo(transactionAmount) < 0) {
                log.warn("INSUFFICIENT FUNDS: Account {} attempted to withdraw {}", event.accountId, transactionAmount);
                return;
            }
            account.balance = account.balance.subtract(transactionAmount);
        } else if ("DEPOSIT".equalsIgnoreCase(event.transactionType)) {
            account.balance = account.balance.add(transactionAmount);
        } else {
            log.warn("UNKNOWN TRANSACTION TYPE: {}", event.transactionType);
            return;
        }

        accountRepository.save(account);

        LedgerEvent ledgerEvent = new LedgerEvent();
        ledgerEvent.accountId = event.accountId;
        ledgerEvent.amount = transactionAmount;
        ledgerEvent.currency = event.currency;
        ledgerEvent.transactionType = event.transactionType;
        ledgerEvent.idempotencyKey = event.idempotencyKey;

        eventRepository.save(ledgerEvent);
        log.info("Successfully recorded transaction. New balance: {}", account.balance);
    }
}
