package com.uws.transaction_service.service.impl;

import com.uws.transaction_service.model.Transaction;
import com.uws.transaction_service.model.TransactionStateLog;
import com.uws.transaction_service.repository.TransactionLogRepository;
import com.uws.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionStateManager {

    private final TransactionRepository transactionRepository;
    private final TransactionLogRepository transactionLogRepository;

    @Transactional
    public void transitionTo(Transaction transaction, String newState, Map<String,Object> eventData){
        String oldState=transaction.getStatus();
        validateStateTransition(oldState,newState);

        transaction.setStatus(newState);
        transactionRepository.save(transaction);

        TransactionStateLog transactionStateLog= TransactionStateLog.builder()
                .transactionId(transaction.getTransactionId())
                .fromState(oldState)
                .toState(newState)
                .eventData(eventData)
                .build();

        transactionLogRepository.save(transactionStateLog);
        log.info("Transaction state transition: {} -> {} for txnId: {}",
                oldState, newState, transaction.getTransactionId());


    }

    private void validateStateTransition(String oldState, String newState) {
        Map<String, String[]> validTransitions = new HashMap<>();
        validTransitions.put("INITIATED", new String[]{"VALIDATING", "FAILED"});
        validTransitions.put("VALIDATING", new String[]{"DEBITING", "FAILED"});
        validTransitions.put("DEBITING", new String[]{"FRAUD_CHECK", "FAILED"});
        validTransitions.put("FRAUD_CHECK", new String[]{"CREDITING", "REVERSED"});
        validTransitions.put("CREDITING", new String[]{"SUCCESS", "REVERSED"});
        validTransitions.put("SUCCESS", new String[]{});
        validTransitions.put("FAILED", new String[]{});
        validTransitions.put("REVERSED", new String[]{});

        String[] allowed = validTransitions.get(oldState);
        if (allowed == null) {
            throw new IllegalStateException("Invalid current state: " + oldState);
        }

        for (String allowedState : allowed) {
            if (allowedState.equals(newState)) {
                return; // Valid transition
            }
        }

        throw new IllegalStateException(
                String.format("Invalid state transition: %s -> %s", oldState, newState));
    }



}
