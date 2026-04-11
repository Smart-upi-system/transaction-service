package com.uws.transaction_service.service.impl;

import com.uws.transaction_service.model.Transaction;
import com.uws.transaction_service.model.TransactionStateLog;
import com.uws.transaction_service.repository.TransactionLogRepository;
import com.uws.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionStateManager {

    private final TransactionRepository transactionRepository;
    private final TransactionLogRepository transactionLogRepository;

    @Transactional
    public Transaction transitionTo(Transaction transaction, String newState, Map<String, Object> eventData) {
        String oldState = transaction.getStatus();

        // 1. IDEMPOTENCY CHECK: If already in the target state, ignore and return
        if (oldState.equals(newState)) {
            log.info("Transaction {} already in state {}, skipping transition", transaction.getTransactionId(), newState);
            return transaction;
        }

        // 2. VALIDATION
        validateStateTransition(oldState, newState);

        log.info("Transitioning transaction {} from {} to {}", transaction.getTransactionId(), oldState, newState);

        transaction.setStatus(newState);
        Transaction updatedTransaction = transactionRepository.save(transaction);

        TransactionStateLog logEntry = TransactionStateLog.builder()
                .transactionId(transaction.getTransactionId())
                .fromState(oldState)
                .toState(newState)
                .eventData(eventData)
                .createdAt(LocalDateTime.now()) // Ensure timestamp is set
                .build();
        transactionLogRepository.save(logEntry);

        return updatedTransaction;
    }

    private void validateStateTransition(String oldState, String newState) {
        Map<String, String[]> validTransitions = new HashMap<>();

        // Add skip/jump states based on your logs
        validTransitions.put("INITIATED", new String[]{"VALIDATING", "CREDITING", "FAILED"});

        // ALLOW VALIDATING -> CREDITING (Fixes your log error)
        validTransitions.put("VALIDATING", new String[]{"DEBITING", "CREDITING", "FAILED", "REVERSED"});

        validTransitions.put("DEBITING", new String[]{"FRAUD_CHECK", "CREDITING", "FAILED"});
        validTransitions.put("FRAUD_CHECK", new String[]{"CREDITING", "REVERSED", "FAILED"});
        validTransitions.put("CREDITING", new String[]{"SUCCESS", "REVERSED", "FAILED"});

        // Terminal states
        validTransitions.put("SUCCESS", new String[]{});
        validTransitions.put("FAILED", new String[]{});
        validTransitions.put("REVERSED", new String[]{});

        String[] allowed = validTransitions.get(oldState);
        if (allowed == null) throw new IllegalStateException("Invalid current state: " + oldState);

        boolean isValid = false;
        for (String allowedState : allowed) {
            if (allowedState.equals(newState)) {
                isValid = true;
                break;
            }
        }

        if (!isValid) {
            // Log it instead of crashing the thread, or handle compensation
            log.error("ILLEGAL TRANSITION ATTEMPT: {} -> {}", oldState, newState);
            throw new IllegalStateException(String.format("Invalid state transition: %s -> %s", oldState, newState));
        }
    }


//    private void validateStateTransition(String oldState, String newState) {
//        Map<String, String[]> validTransitions = new HashMap<>();
//        validTransitions.put("INITIATED", new String[]{"VALIDATING", "CREDITING", "FAILED"});
//        validTransitions.put("VALIDATING", new String[]{"DEBITING", "FAILED"});
//        validTransitions.put("DEBITING", new String[]{"FRAUD_CHECK", "FAILED"});
//        validTransitions.put("FRAUD_CHECK", new String[]{"CREDITING", "REVERSED"});
//        validTransitions.put("CREDITING", new String[]{"SUCCESS", "REVERSED","FAILED"});
//        validTransitions.put("SUCCESS", new String[]{});
//        validTransitions.put("FAILED", new String[]{});
//        validTransitions.put("REVERSED", new String[]{});
//
//        String[] allowed = validTransitions.get(oldState);
//        if (allowed == null) {
//            throw new IllegalStateException("Invalid current state: " + oldState);
//        }
//
//        for (String allowedState : allowed) {
//            if (allowedState.equals(newState)) {
//                return; // Valid transition
//            }
//        }
//
//        throw new IllegalStateException(
//                String.format("Invalid state transition: %s -> %s", oldState, newState));
//    }
//


}
