package com.uws.transaction_service.service.impl;

import com.uws.transaction_service.events.*;
import com.uws.transaction_service.events.producer.TransactionEventProducer;
import com.uws.transaction_service.grpc.WalletServiceGrpcClient;
import com.uws.transaction_service.model.Transaction;
import com.uws.transaction_service.repository.TransactionRepository;
import com.uws.transaction_service.service.TransactionOrchestratorI;
import com.uws.wallet.grpc.proto.CreditRequest;
import com.uws.wallet.grpc.proto.WalletResponse;
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
public class TransactionOrchestrator implements TransactionOrchestratorI {

    private final TransactionRepository transactionRepository;
    private final TransactionStateManager stateManager;
    private final TransactionEventProducer eventProducer;
    private final WalletServiceGrpcClient walletServiceGrpcClient;

    @Override
    public void initiateTransaction(Transaction transaction) {
        log.info("Initiating transaction: {}", transaction.getTransactionId());

        TransactionInitiatedEvent event = TransactionInitiatedEvent.builder()
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

        eventProducer.publishTransactionInitiated(event);
        stateManager.transitionTo(transaction, "VALIDATING", new HashMap<>());
        triggerFraudCheck(transaction);
    }

    @Override
    @Transactional
    public void senderDebit(Transaction transaction,String walletId) {
        log.info("Debiting sender: transactionId={}, senderId={}, amount={}",
                transaction.getTransactionId(), transaction.getSenderId(), transaction.getAmount());

        stateManager.transitionTo(transaction, "DEBITING", new HashMap<>());

        try {
            // Converting String IDs to UUID for gRPC Client
            UUID senderUuid = UUID.fromString(transaction.getSenderId());
            UUID txnUuid = UUID.fromString(transaction.getTransactionId());

            walletServiceGrpcClient.debit(
                    senderUuid,                         // userId
                    UUID.fromString(walletId),            // walletId (using senderId as walletId)
                    transaction.getAmount(),
                    txnUuid,
                    transaction.getIdempotencyKey() + ":debit"
            );
        } catch (Exception e) {
            log.error("Debit failed: transactionId={}", transaction.getTransactionId(), e);
            failTransaction(transaction, "Debit failed: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void triggerFraudCheck(Transaction transaction) {
        log.info("Triggering fraud check: transactionId={}", transaction.getTransactionId());

        TransactionInitiatedEvent fraudReq = TransactionInitiatedEvent.builder()
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

        eventProducer.publishToFraudService(fraudReq);
    }

    @Override
    public void creditReceiver(Transaction transaction) {
        log.info("Crediting receiver: transactionId={}, receiverId={}, amount={}",
                transaction.getTransactionId(), transaction.getReceiverId(), transaction.getAmount());

        try {
            // Converting String IDs to UUID for gRPC Client
            UUID receiverUuid = UUID.fromString(transaction.getReceiverId());
            UUID txnUuid = UUID.fromString(transaction.getTransactionId());
            UUID receiverWalletUuid = UUID.fromString(transaction.getReceiverWalletId());
            walletServiceGrpcClient.credit(
                    receiverUuid,                       // userId
                    receiverWalletUuid,                       // walletId
                    transaction.getAmount(),
                    txnUuid,
                    transaction.getIdempotencyKey() + ":credit"
            );
        } catch (Exception e) {
            log.error("Credit failed: transactionId={}", transaction.getTransactionId(), e);
            compensateTransaction(transaction, "Credit failed: " + e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional
    public void completeTransaction(Transaction transaction) {
        log.info("Completing transaction: transactionId={}", transaction.getTransactionId());

        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
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
    }

    @Override
    @Transactional
    public void failTransaction(Transaction transaction, String reason) {
        log.error("Failing transaction: transactionId={}, reason={}",
                transaction.getTransactionId(), reason);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("reason", reason);
        stateManager.transitionTo(transaction, "FAILED", eventData);

        transaction.setFailureReason(reason);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

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

    @Override
    @Transactional
    public void compensateTransaction(Transaction transaction, String reason) {
        log.warn("Compensating transaction: transactionId={}, reason={}",
                transaction.getTransactionId(), reason);

        try {
            // Converting String IDs to UUID for gRPC Client
            UUID senderUuid = UUID.fromString(transaction.getSenderId());
            UUID txnUuid = UUID.fromString(transaction.getTransactionId());
            UUID senderWalletId = UUID.fromString(transaction.getSenderWalletId());

            walletServiceGrpcClient.credit(
                    senderUuid,
                    senderWalletId,
                    transaction.getAmount(),
                    txnUuid,
                    transaction.getIdempotencyKey() + ":compensation"
            );

            transaction.setFailureReason(reason);
            transaction.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(transaction);

            TransactionReversedEvent event = TransactionReversedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("TransactionReversed")
                    .transactionId(transaction.getTransactionId())
                    .senderId(transaction.getSenderId())
                    .refundedAmount(transaction.getAmount())
                    .reason(reason)
                    .timestamp(LocalDateTime.now())
                    .build();

            eventProducer.publishTransactionReversed(event);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to compensate transaction: transactionId={}",
                    transaction.getTransactionId(), e);
            throw new RuntimeException("Critical Saga Compensation Failure", e);
        }
    }

    @Override
    @Transactional
    public void directCredit(Transaction transaction) {
        log.info("Starting direct credit: txnId={}", transaction.getTransactionId());

        try {
            // 1. Validated transition: INITIATED -> CREDITING
            transaction = stateManager.transitionTo(transaction, "CREDITING", new HashMap<>());

            UUID senderUuid = UUID.fromString(transaction.getSenderId());
            UUID receiverUuid = UUID.fromString(transaction.getReceiverId());
            UUID txnUuid = UUID.fromString(transaction.getTransactionId());
            UUID receiverWalletUuid = UUID.fromString(transaction.getReceiverWalletId());
            // 2. Execute gRPC call
            walletServiceGrpcClient.credit(
                    receiverUuid,
                    receiverWalletUuid,
                    transaction.getAmount(),
                    txnUuid,
                    transaction.getIdempotencyKey() + ":direct"
            );

            // 3. Finalize: CREDITING -> SUCCESS
            transaction = stateManager.transitionTo(transaction, "SUCCESS", new HashMap<>());

            transaction.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(transaction);

        } catch (Exception e) {
            log.error("Direct credit failed for txn: {}", transaction.getTransactionId(), e);

            // Handle failure state
            String targetState = "CREDITING".equals(transaction.getStatus()) ? "REVERSED" : "FAILED";

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("reason", e.getMessage());
            stateManager.transitionTo(transaction, targetState, eventData);
            transactionRepository.save(transaction);
            throw  e;
        }
    }





}