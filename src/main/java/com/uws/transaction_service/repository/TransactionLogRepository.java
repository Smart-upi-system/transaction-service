package com.uws.transaction_service.repository;

import com.uws.transaction_service.model.TransactionStateLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionLogRepository extends JpaRepository<TransactionStateLog, String> {

    List<TransactionStateLog> findByTransactionIdOrderByCreatedAtAsc(String transactionId);

    long countByTransactionId(String transactionId);

    @Modifying
    @Transactional
    @Query(
            value = "DELETE FROM transaction_state_log WHERE created_at < :cutoff",
            nativeQuery = true
    )
    int deleteLogsOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
