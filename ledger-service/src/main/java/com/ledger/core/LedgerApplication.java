package com.ledger.core;

import com.ledger.core.domain.Account;
import com.ledger.core.repository.AccountRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@SpringBootApplication
public class LedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(LedgerApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/v1")
class AccountController {
    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @PostMapping("/accounts")
    public ResponseEntity<Object> createAccount(@RequestBody Map<String, Object> request) {
        String id = (String) request.get("id");
        if (accountRepository.existsById(id)) {
            return ResponseEntity.status(409).body(Map.of("error", "ACCOUNT_EXISTS"));
        }
        
        Account account = new Account();
        account.id = id;
        account.currency = (String) request.get("currency");
        account.balance = new BigDecimal(request.get("initialBalance").toString());
        
        accountRepository.save(account);
        return ResponseEntity.status(201).body(account);
    }

    @GetMapping("/ledger/accounts/{accountId}/balance")
    public ResponseEntity<Object> getBalance(@PathVariable String accountId) {
        return accountRepository.findById(accountId)
                .<ResponseEntity<Object>>map(account -> ResponseEntity.ok(Map.of("accountId", accountId, "balance", account.balance)))
                .orElse(ResponseEntity.notFound().build());
    }
}