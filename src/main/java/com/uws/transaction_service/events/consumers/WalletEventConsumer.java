package com.uws.transaction_service.events.consumers;

import com.uws.transaction_service.events.CreditConfirmedEvent;
import com.uws.transaction_service.events.DebitConfirmedEvent;
import com.uws.transaction_service.model.Transaction;
import com.uws.transaction_service.repository.TransactionLogRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletEventConsumer {

    private final TransactionRepository transactionRepository;
    private final TransactionStateManager stateManager;
    private final TransactionOrchestratorI orchestrator;

    @KafkaListener(
            topics = "${kafka.topics.wallet-events:wallet.events}",
            groupId = "${spring.kafka.consumer.wallet.group-id:transaction-service-wallet-group}",
            containerFactory = "debitConfirmedKafkaListenerFactory"
    )
    public void handleDebitConfirmed(DebitConfirmedEvent event){
        log.info("Received DebitConfirmed event: transactionId={}", event.getTransactionId());

        try {
            Transaction transaction=transactionRepository.findByTransactionId(event.getTransactionId());
            Map<String,Object> eventData=new HashMap<>();
            eventData.put("debitedAmount",event.getAmount());
            eventData.put("newBalance",event.getNewBalance());
            eventData.put("walletId",event.getWalletId());

            stateManager.transitionTo(transaction,"FRAUD_CHECK",eventData);

//            // Trigger fraud check (publishes event to fraud service)
            orchestrator.triggerFraudCheck(transaction);

            log.info("DebitConfirmed processed: transactionId={}", event.getTransactionId());
        } catch (Exception e){
            log.error("Failed to handle DebitConfirmed event: transactionId={}",
                    event.getTransactionId(), e);
        }

    }


    /**
     * Handle CreditConfirmed event from Wallet Service
     */
    @KafkaListener(
            topics = "${kafka.topics.wallet-events:wallet.events}",
            groupId = "${spring.kafka.consumer.group-id:transaction-service-wallet-group}",
            containerFactory = "debitConfirmedKafkaListenerFactory"
    )
    public void handleCreditConfirmed(CreditConfirmedEvent event){
        log.info("Received CreditConfirmed event: transactionId={}", event.getTransactionId());
        try {

            Transaction transaction=transactionRepository.findByTransactionId(event.getTransactionId());
            Map<String,Object> eventData=new HashMap<>();
                eventData.put("creditedAmount",event.getAmount());
                eventData.put("newBalance",event.getNewBalance());
                eventData.put("walletId",event.getWalletId());

                stateManager.transitionTo(transaction,"SUCCESS",eventData);

                orchestrator.completeTransaction(transaction);

        } catch (RuntimeException e) {
            log.error("Failed to handle CreditConfirmed event: transactionId={}",
                    event.getTransactionId(), e);
        }

    }




}
