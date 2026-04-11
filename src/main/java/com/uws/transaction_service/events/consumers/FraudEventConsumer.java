package com.uws.transaction_service.events.consumers;

import com.uws.transaction_service.grpc.UserServiceGrpcClient;
import com.uws.transaction_service.model.Transaction;
import com.uws.transaction_service.repository.TransactionRepository;
import com.uws.transaction_service.service.TransactionOrchestratorI;
import com.uws.transaction_service.service.impl.TransactionStateManager;
import com.uws.user.grpc.proto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudEventConsumer {

    private final TransactionRepository transactionRepository;
    private final TransactionStateManager stateManager;
    private final TransactionOrchestratorI orchestrator;
    private final UserServiceGrpcClient userServiceGrpcClient;


    /**
     * Unified Listener for all Fraud Results.
     * This prevents the "Double Processing" race condition you saw in your logs.
     */
    @KafkaListener(
            topics = "${spring.kafka.topics.fraud-results:fraud.results}",
            groupId = "${spring.kafka.consumer.fraud.group-id:transaction-service-fraud-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleFraudResult(@Payload Map<String, Object> eventData, Acknowledgment ack) {
        // 1. Extract Metadata
        String transactionId = (String) eventData.get("transactionId");
        String eventType = (String) eventData.get("eventType"); // Passed or Failed

        if (transactionId == null) {
            log.error("Received fraud event without transactionId: {}", eventData);
            return;
        }

        log.info("Processing Fraud Result: id={}, type={}", transactionId, eventType);

        try {
            // 2. Fetch Transaction
            Transaction transaction = transactionRepository.findByTransactionId(transactionId);
            if (transaction == null) {
                log.error("Transaction record not found in DB for ID: {}", transactionId);
                return;
            }

            // 3. Dispatch based on type
            if ("FraudCheckPassed".equalsIgnoreCase(eventType)) {
                processPass(transaction, eventData);
            } else if ("FraudCheckFailed".equalsIgnoreCase(eventType)) {
                processFail(transaction, eventData);
            } else {
                log.warn("Unknown fraud event type received: {}", eventType);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error handling fraud result for transaction {}: {}", transactionId, e.getMessage(), e);
        }
    }

    private void processPass(Transaction transaction, Map<String, Object> data) {
        log.info("Handling PASS for transaction: {}", transaction.getTransactionId());

        // Use a copy of the data map for the state log
        Map<String, Object> logData = new HashMap<>(data);

//        // VALIDATING -> CREDITING
//        stateManager.transitionTo(transaction, "CREDITING", logData);
        // VALIDATING -> DEBITING  (not CREDITING!)
        stateManager.transitionTo(transaction, "DEBITING", logData);
//        UserResponse userResponse=userServiceGrpcClient.getUserByUpiId(transaction.getSenderUpiId());
//        String senderWalletId=userResponse.getWalletId();
        // Trigger the next step in Saga (Synchronous gRPC or Async Kafka)
//        orchestrator.creditReceiver(transaction);
        orchestrator.senderDebit(transaction,transaction.getSenderWalletId());
    }

    private void processFail(Transaction transaction, Map<String, Object> data) {
        log.warn("Handling FAIL for transaction: {}. Reason: {}",
                transaction.getTransactionId(), data.get("reason"));

        Map<String, Object> logData = new HashMap<>(data);

        // VALIDATING -> REVERSED
        stateManager.transitionTo(transaction, "REVERSED", logData);

        // Trigger Compensation logic (Refund sender)
        String reason = data.get("reason") != null ? data.get("reason").toString() : "Fraud Detected";
        orchestrator.compensateTransaction(transaction, reason);
    }
}