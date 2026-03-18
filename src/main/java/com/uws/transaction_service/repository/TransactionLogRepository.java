package com.uws.transaction_service.repository;

import com.uws.transaction_service.model.TransactionStateLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionLogRepository extends JpaRepository<TransactionStateLog, UUID> {

}
