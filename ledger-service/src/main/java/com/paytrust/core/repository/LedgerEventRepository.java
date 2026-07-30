package com.paytrust.core.repository;

import com.paytrust.core.domain.LedgerEvent;
import org.springframework.data.repository.CrudRepository;

public interface LedgerEventRepository extends CrudRepository<LedgerEvent, Long> {}