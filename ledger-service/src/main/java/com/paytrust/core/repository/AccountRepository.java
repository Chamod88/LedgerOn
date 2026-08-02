package com.paytrust.core.repository;

import com.paytrust.core.domain.Account;
import org.springframework.data.repository.CrudRepository;

public interface AccountRepository extends CrudRepository<Account, String> {}