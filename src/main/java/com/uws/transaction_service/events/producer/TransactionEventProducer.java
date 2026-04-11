package com.uws.transaction_service.events.producer;

import com.uws.transaction_service.events.TransactionCompletedEvent;
import com.uws.transaction_service.events.TransactionFailedEvent;
import com.uws.transaction_service.events.TransactionInitiatedEvent;
import com.uws.transaction_service.events.TransactionReversedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventProducer {

    private final KafkaTemplate<String,Object> kafkaTemplate;

    @Value("${spring.kafka.topics.transaction-events:transaction.events}")
    private String transactionEventsTopic;

    @Value("${spring.kafka.topics.fraud-requests:fraud.requests}")
    private String fraudEventsTopic;


    public void publishTransactionInitiated(TransactionInitiatedEvent event) {
        log.info("Publishing TransactionInitiated: {}", event.getTransactionId());

        // Fix: Ensure the event object is passed as the third argument
        kafkaTemplate.send(transactionEventsTopic, event.getTransactionId().toString(), event);
    }

    public void publishToFraudService(TransactionInitiatedEvent fraudCheckReq) {
        log.info("Publishing FraudService: {}", fraudCheckReq.getTransactionId());
        kafkaTemplate.send(fraudEventsTopic,fraudCheckReq.getTransactionId().toString(),fraudCheckReq);

    }

    public void publishTransactionCompleted(TransactionCompletedEvent event) {
        log.info("Publishing Transaction Completed: {}", event.getTransactionId());
        kafkaTemplate.send(transactionEventsTopic,event.getTransactionId().toString(),event);
    }

    public void publishTransactionFailed(TransactionFailedEvent event) {
        log.info("Publishing Transaction Failed: {}", event.getTransactionId());
        kafkaTemplate.send(transactionEventsTopic,event.getTransactionId().toString(),event);
    }

    public void publishTransactionReversed(TransactionReversedEvent event) {
        log.info("Publishing Transaction reversed: {}", event.getTransactionId());
        kafkaTemplate.send(transactionEventsTopic,event.getTransactionId().toString(),event);
    }
}
