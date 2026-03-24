package com.uws.transaction_service.repository;

import com.uws.transaction_service.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    Transaction findByTransactionId(String transactionId);

    Page<Transaction> findBySenderIdOrReceiverIdOrderByInitiatedAtDesc(String userId, String userId1, Pageable pageable);

    boolean existsByIdempotencyKey(String idempotencyKey);


    // Get sent transactions
    Page<Transaction> findBySenderIdOrderByInitiatedAtDesc(
            String senderId,
            Pageable pageable
    );

    // Get received transactions
    Page<Transaction> findByReceiverIdOrderByInitiatedAtDesc(
            String receiverId,
            Pageable pageable
    );

    // Get transactions by status
    List<Transaction> findByStatusAndInitiatedAtBefore(
            String status,
            LocalDateTime cutoffTime
    );

    // Get stuck transactions (for monitoring)
    @Query("SELECT t FROM Transaction t WHERE t.status IN :stuckStates " +
            "AND t.initiatedAt < :cutoffTime")
    List<Transaction> findStuckTransactions(
            List<String> stuckStates,
            LocalDateTime cutoffTime
    );
}
