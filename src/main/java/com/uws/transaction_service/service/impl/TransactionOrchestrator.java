package com.uws.transaction_service.service.impl;

import com.uws.transaction_service.events.TransactionCompletedEvent;
import com.uws.transaction_service.events.TransactionFailedEvent;
import com.uws.transaction_service.events.TransactionInitiatedEvent;
import com.uws.transaction_service.events.TransactionReversedEvent;
import com.uws.transaction_service.events.producer.TransactionEventProducer;
import com.uws.transaction_service.grpc.WalletServiceGrpcClient;
import com.uws.transaction_service.model.Transaction;
import com.uws.transaction_service.repository.TransactionRepository;
import com.uws.transaction_service.service.TransactionOrchestratorI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalEventPublisher;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
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
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .receiverId(transaction.getReceiverId())
                .senderId(transaction.getSenderId())
                .remarks(transaction.getRemarks())
                .timestamp(LocalDateTime.now())
                .senderUpiId(transaction.getSenderUpiId())
                .receiverUpiId(transaction.getReceiverUpiId())
                .build();

        eventProducer.publishTransactionInitiated(transactionInitiatedEvent);
        stateManager.transitionTo(transaction, "VALIDATING", new HashMap<>());

    }

    /**
     * Step 2: After validation, trigger debit
     */
    @Transactional
    public  void senderDebit(Transaction transaction){
        log.info("Debiting sender: transactionId={}, senderId={}, amount={}",
                transaction.getTransactionId(), transaction.getSenderId(), transaction.getAmount());

            stateManager.transitionTo(transaction,"DEBITING",new HashMap<>());

            try{
                walletServiceGrpcClient.debit(
                        transaction.getSenderId(),
                        transaction.getAmount(),
                        transaction.getTransactionId(),
                        transaction.getIdempotencyKey() + ":debit"
                );
            }catch (Exception e){
                log.error("Debit failed: transactionId={}", transaction.getTransactionId(), e);
                failTransaction(transaction, "Debit failed: " + e.getMessage());
                throw e;
            }

    }

    /**
     * Step 3: Trigger fraud check (after debit confirmed)
     */

    @Override
    public void triggerFraudCheck(Transaction transaction) {
        log.info("Triggering fraud check: transactionId={}", transaction.getTransactionId());
        // Transaction is already in FRAUD_CHECK state (set by WalletEventConsumer)

        // Publish event for Fraud Service to consume
        // Fraud Service will publish FraudCheckPassed or FraudCheckFailed
        TransactionInitiatedEvent fraudCheckReq=TransactionInitiatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("FraudCheckRequested")
                .transactionId(transaction.getTransactionId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .receiverId(transaction.getReceiverId())
                .senderId(transaction.getSenderId())
                .remarks(transaction.getRemarks())
                .timestamp(LocalDateTime.now())
                .senderUpiId(transaction.getSenderUpiId())
                .receiverUpiId(transaction.getReceiverUpiId())
                .build();
            eventProducer.publishToFraudService(fraudCheckReq);
    }


    /**
     * Step 4: Credit receiver (after fraud check passed)
     */
    @Override
    public void creditReceiver(Transaction transaction) {
        log.info("Crediting receiver: transactionId={}, receiverId={}, amount={}",
                transaction.getTransactionId(), transaction.getReceiverId(), transaction.getAmount());

        // Transaction is already in CREDITING state (set by FraudEventConsumer)

        // Call Wallet Service via gRPC to credit
        // Wallet Service will publish CreditConfirmed event
        try {
            walletServiceGrpcClient.credit(
                    transaction.getReceiverId(),
                    transaction.getAmount(),
                    transaction.getTransactionId(),
                    transaction.getIdempotencyKey() + ":credit"
            );


        } catch (Exception e) {
            log.error("Credit failed: transactionId={}", transaction.getTransactionId(), e);
            compensateTransaction(transaction, "Credit failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Step 5: Complete transaction (after credit confirmed)
     */
    @Override
    @Transactional
    public void completeTransaction(Transaction transaction) {
        log.info("Completing transaction: transactionId={}", transaction.getTransactionId());

        // Transaction is already in SUCCESS state (set by WalletEventConsumer)
        // Mark completed
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent event=TransactionCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("TransactionCompleted")
                .transactionId(transaction.getTransactionId())
                .senderId(transaction.getSenderId())
                .receiverId(transaction.getReceiverId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .timestamp(LocalDateTime.now())
                .build();

        eventProducer.publishTransactionCompleted(event);
        log.info("Transaction completed successfully: transactionId={}", transaction.getTransactionId());
    }


    @Transactional
    public  void failTransaction(Transaction transaction, String reason) {
        log.error("Failing transaction: transactionId={}, reason={}",
                transaction.getTransactionId(), reason);

        // Transition to FAILED
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("reason", reason);
        stateManager.transitionTo(transaction, "FAILED", eventData);

        // Update transaction
        transaction.setFailureReason(reason);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        // Publish TransactionFailed event
        TransactionFailedEvent event = TransactionFailedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("TransactionFailed")
                .transactionId(transaction.getTransactionId())
                .senderId(transaction.getSenderId())
                .receiverId(transaction.getReceiverId())
                .reason(reason)
                .failedAtState(transaction.getStatus())
                .timestamp(LocalDateTime.now())
                .build();

        eventProducer.publishTransactionFailed(event);

    }

    /**
     * Compensation: Refund sender on failure
     */
    @Override
    public void compensateTransaction(Transaction transaction, String reason) {
        log.warn("Compensating transaction: transactionId={}, reason={}",
                transaction.getTransactionId(), reason);

        try{
            walletServiceGrpcClient.credit(
                transaction.getSenderId(),
                transaction.getAmount(),
                    transaction.getTransactionId(),
                    transaction.getIdempotencyKey() + ":compensation"
            );

            transaction.setFailureReason(reason);
            transaction.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(transaction);


            TransactionReversedEvent event=TransactionReversedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("TransactionReversed")
                    .transactionId(transaction.getTransactionId())
                    .senderId(transaction.getSenderId())
                    .refundedAmount(transaction.getAmount())
                    .reason(reason)
                    .timestamp(LocalDateTime.now())
                    .build();

            eventProducer.publishTransactionReversed(event);
            log.info("Transaction compensated successfully: transactionId={}",
                    transaction.getTransactionId());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to compensate transaction: transactionId={}",
                    transaction.getTransactionId(), e);
            throw new RuntimeException(e);
        }

    }
}
