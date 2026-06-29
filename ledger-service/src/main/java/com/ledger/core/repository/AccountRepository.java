package com.ledger.core.repository;

import com.ledger.core.domain.Account;
import org.springframework.data.repository.CrudRepository;

public interface AccountRepository extends CrudRepository<Account, String> {}