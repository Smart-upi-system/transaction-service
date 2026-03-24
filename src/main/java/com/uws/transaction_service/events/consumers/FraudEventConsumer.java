package com.uws.transaction_service.events.consumers;

import com.uws.transaction_service.events.FraudCheckFailedEvent;
import com.uws.transaction_service.events.FraudCheckPassedEvent;
import com.uws.transaction_service.model.Transaction;
import com.uws.transaction_service.repository.TransactionRepository;
import com.uws.transaction_service.service.TransactionOrchestratorI;
import com.uws.transaction_service.service.impl.TransactionOrchestrator;
import com.uws.transaction_service.service.impl.TransactionStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
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


    /**
     * Handle FraudCheckPassed event from Fraud Service
     */
    @KafkaListener(
            topics = "${spring.kafka.topics.fraud-events:fraud.events}",
            groupId = "${spring.kafka.consumer.fraud-pass.group-id:transaction-service-fraud-pass-group}",
            containerFactory = "fraudPassedKafkaListenerFactory"
    )
    public void handleFraudCheckPass(FraudCheckPassedEvent event){
        log.info("Received FraudCheckPassed event: transactionId={}", event.getTransactionId());

        try {
            Transaction transaction=transactionRepository.findByTransactionId(event.getTransactionId());
            Map<String,Object> eventData=new HashMap<>();
            eventData.put("riskScore",event.getRiskScore());
            eventData.put("checkedBy",event.getCheckedBy());

            stateManager.transitionTo(transaction,"CREDITING",eventData);
            // Trigger credit operation
            orchestrator.creditReceiver(transaction);

        }catch (Exception e){
            log.error("Failed to handle FraudCheckPassed event: transactionId={}",
                    event.getTransactionId(), e);
        }

    }


    @KafkaListener(
            topics = "${spring.kafka.topics.fraud-events:fraud.events}",
            groupId = "${spring.kafka.consumer.fraud-fail.group-id:transaction-service-fraud-fail-group}",
            containerFactory = "fraudFailedKafkaListenerFactory"
    )
    public void handleFraudCheckFailed(FraudCheckFailedEvent event){
        log.info("Received FraudCheckFailed event: transactionId={}", event.getTransactionId());

        try {
            // Transition to REVERSED state (compensation required)
        Transaction transaction=transactionRepository.findByTransactionId(event.getTransactionId());

        Map<String,Object> eventData=new HashMap<>();
        eventData.put("riskScore",event.getRiskScore());
        eventData.put("reason",event.getReason());

            stateManager.transitionTo(transaction,"REVERSED",eventData);

                // Trigger compensation (refund sender)
            orchestrator.compensateTransaction(transaction, event.getReason());

        } catch (Exception e) {
            log.error("Failed to handle FraudCheckFailed event: transactionId={}",
                    event.getTransactionId(), e);
        }


    }



}
