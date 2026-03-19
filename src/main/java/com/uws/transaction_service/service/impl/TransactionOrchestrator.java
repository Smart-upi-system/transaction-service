package com.uws.transaction_service.service.impl;

import com.uws.transaction_service.events.TransactionInitiatedEvent;
import com.uws.transaction_service.model.Transaction;
import com.uws.transaction_service.repository.TransactionRepository;
import com.uws.transaction_service.service.TransactionOrchestratorI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalEventPublisher;

import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionOrchestrator implements TransactionOrchestratorI {

    private final TransactionRepository transactionRepository;
    private final TransactionStateManager stateManager;
    private final TransactionEventProducer eventProducer;
    private final WalletServiceGrpcClient walletServiceGrpcClient;


    public void initiateTransaction(Transaction transaction){
        log.info("Initiating transaction: {}", transaction.getTransactionId());

        TransactionInitiatedEvent transactionInitiatedEvent=TransactionInitiatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("TransactionInitiated")
                .transactionId(transaction.getTransactionId())

                .build();

    }



    @Override
    @Transactional
    public void completeTransaction(Transaction transaction) {

    }

    @Override
    public void triggerFraudCheck(Transaction transaction) {

    }

    @Override
    public void creditReceiver(Transaction transaction) {

    }

    @Override
    public void compensateTransaction(Transaction transaction, String reason) {

    }
}
