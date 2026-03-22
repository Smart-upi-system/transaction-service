package com.uws.transaction_service.service.impl;

import com.uws.transaction_service.grpc.UserServiceGrpcClient;
import com.uws.transaction_service.model.Transaction;
import com.uws.transaction_service.model.TransactionStateLog;
import com.uws.transaction_service.model.dtos.StateLogResponse;
import com.uws.transaction_service.model.dtos.TransactionHistoryResponse;
import com.uws.transaction_service.model.dtos.TransactionResponse;
import com.uws.transaction_service.model.dtos.TransferRequest;
import com.uws.transaction_service.repository.TransactionLogRepository;
import com.uws.transaction_service.repository.TransactionRepository;
import com.uws.transaction_service.service.TransactionOrchestratorI;
import com.uws.transaction_service.service.TransactionService;
import com.uws.transaction_service.utils.IdempotencyManager;
import jakarta.transaction.InvalidTransactionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.TransactionalIdNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionLogRepository logRepository;
    private final TransactionOrchestrator transactionOrchestrator;
    private final TransactionStateManager stateManager;
    private final UserServiceGrpcClient userServiceGrpcClient;
    private final IdempotencyManager idempotencyManager;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public TransactionResponse tranfer(UUID senderId, TransferRequest request, String correlationId) throws InvalidTransactionException {
        log.info("Processing transfer: senderId={}, receiverUpiId={}, amount={}",
                senderId, request.getReceiverUpiId(), request.getAmount());

        String idempotencyKey=idempotencyManager.generateOrGetKey(request.getIdempotencyKey());
        if (idempotencyManager.isDuplicate(idempotencyKey)) {
            String existingTxnId = idempotencyManager.getTransactionId(idempotencyKey);
            log.info("Duplicate request: idempotencyKey={}, existingTxnId={}",
                    idempotencyKey, existingTxnId);
            return getTransaction(senderId.toString(),UUID.fromString(existingTxnId));
        }

        // Step 2: Validate sender
        ValidationResponse senderValidation=userServiceGrpcClient.validateUser(senderId.toString());
        if(!senderValidation.getValid() || !senderValidation.getActive()){
            throw new InvalidTransactionException("Sender account is invalid or inactive");
        }

//        step 3 : get receiver by upi id
        UserResponse receiver = userServiceGrpcClient.getUserByUpiId(request.getReceiverUpiId());
        if(!receiver.getSuccess() || !receiver.getActive()){
            throw new UserNotFoundException("Receiver not found: " + request.getReceiverUpiId());
        }

//        step 4 validate that sender is no receiver
        if(senderId.toString().equals(receiver.getUserId())){
            throw new InvalidTransactionException("Cannot transfer to yourself");
        }

        Map<String,Object> metdaData = new HashMap<>();
        metdaData.put("senderKycVerified",senderValidation.getKycVerified());
        metdaData.put("receiverKycVerified",receiver.getKycVerified());
        metdaData.put("initiatedBy","API");

        Transaction transaction=Transaction.builder()
                .senderId(senderId)
                .receiverId(UUID.fromString(receiver.getUserId()))
                .senderUpiId(getSenderUpiId(senderId))
                .receiverUpiId(request.getReceiverUpiId())
                .amount(request.getAmount())
                .currency("INR")
                .status("INITIATED")
                .type("P2P_TRANSFER")
                .remarks(request.getRemarks())
                .idempotencyKey(idempotencyKey)
                .correlationId(correlationId)
                .metadata(metdaData)
                .initiatedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);
        log.info("Transaction created: transactionId={}", transaction.getTransactionId());

        // Step 6: Mark as processed (idempotency)
        idempotencyManager.markAsProcessed(idempotencyKey, transaction.getTransactionId().toString());

        // Step 7: Initiate Saga workflow
        transactionOrchestrator.initiateTransaction(transaction);

        // Step 8: Trigger debit (state will be VALIDATING at this point)
        transactionOrchestrator.senderDebit(transaction);

        TransactionResponse transactionResponse= modelMapper.map(transaction,TransactionResponse.class);
        return  transactionResponse;

    }

    private String getSenderUpiId(UUID senderId) {
        // In production: Call User Service gRPC to get UPI ID
        // For now, return placeholder
        return "sender@wallet";
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(String senderId, UUID transactionId) {
        log.info("Getting transaction: {}", transactionId);

        Transaction transaction=transactionRepository.findByTransactionId(transactionId);
        return modelMapper.map(transaction,TransactionResponse.class);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionHistoryResponse getTransactionHistory(UUID userId, PageRequest pageable) {
        log.info("Getting transaction history: userId={}, page={}", userId, pageable.getPageNumber());
        Page<Transaction> transactionsPage=transactionRepository.
                findBySenderIdOrReceiverIdOrderByInitiatedAtDesc(userId,userId,pageable);

        List<TransactionResponse> transactions = transactionsPage.getContent()
                .stream()
                .map(t -> modelMapper.map(t, TransactionResponse.class))
                .collect(Collectors.toList());

        return TransactionHistoryResponse.builder()
                .transactions(transactions)
                .currentPage(transactionsPage.getNumber())
                .totalPages(transactionsPage.getTotalPages())
                .totalElements(transactionsPage.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StateLogResponse getStateLog(UUID transactionId) {
        log.info("Getting state log: transactionId={}", transactionId);

        // Verify transaction exists
        transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionalIdNotFoundException("Transaction not found: " + transactionId));

        List<TransactionStateLog> logs = logRepository
                .findByTransactionIdOrderByCreatedAtAsc(transactionId);

        List<StateLogResponse.StateTransition> transitions = logs.stream()
                .map(log -> StateLogResponse.StateTransition.builder()
                        .fromState(log.getFromState())
                        .toState(log.getToState())
                        .eventData(log.getEventData())
                        .timestamp(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return StateLogResponse.builder()
                .transactionId(transactionId)
                .totalTransitions(transitions.size())
                .transitions(transitions)
                .build();

    }
}
