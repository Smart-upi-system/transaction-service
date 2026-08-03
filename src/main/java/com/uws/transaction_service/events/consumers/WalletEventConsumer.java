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
            topics = "${spring.kafka.topics.wallet-debit:wallet.debited}",
            groupId = "${spring.kafka.consumer.wallet-debit.group-id:transaction-service-wallet-debit-group}",
            containerFactory = "debitConfirmedKafkaListenerFactory"
    )
    public void handleDebitConfirmed(DebitConfirmedEvent event){
        log.info("Received DebitConfirmed event: transactionId={}", event.getTransactionId());

        try {
            Transaction transaction = transactionRepository.findByTransactionId(event.getTransactionId());

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("debitedAmount", event.getAmount());
            eventData.put("newBalance", event.getNewBalance());
            eventData.put("walletId", event.getWalletId());

            // DEBITING -> CREDITING  (remove the FRAUD_CHECK step — fraud already passed)
            stateManager.transitionTo(transaction, "CREDITING", eventData);
            orchestrator.creditReceiver(transaction);

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
            topics = "${spring.kafka.topics.wallet-credit:wallet.credited}",
            groupId = "${spring.kafka.consumer.wallet-credit.group-id:transaction-service-wallet-credit-group}",
            containerFactory = "creditConfirmedKafkaListenerFactory"
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
