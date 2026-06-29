package com.ledger.core.repository;

import com.ledger.core.domain.LedgerEvent;
import org.springframework.data.repository.CrudRepository;

public interface LedgerEventRepository extends CrudRepository<LedgerEvent, Long> {}