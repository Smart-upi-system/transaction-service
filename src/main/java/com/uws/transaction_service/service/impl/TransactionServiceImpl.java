package com.uws.transaction_service.service.impl;

import com.uws.transaction_service.grpc.UserServiceGrpcClient;
import com.uws.transaction_service.model.dtos.StateLogResponse;
import com.uws.transaction_service.model.dtos.TransactionResponse;
import com.uws.transaction_service.model.dtos.TransferRequest;
import com.uws.transaction_service.repository.TransactionLogRepository;
import com.uws.transaction_service.repository.TransactionRepository;
import com.uws.transaction_service.service.TransactionOrchestratorI;
import com.uws.transaction_service.service.TransactionService;
import com.uws.transaction_service.utils.IdempotencyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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


    @Override
    @Transactional
    public TransactionResponse tranfer(UUID senderId, TransferRequest request, String correlationId) {
        log.info("Processing transfer: senderId={}, receiverUpiId={}, amount={}",
                senderId, request.getReceiverUpiId(), request.getAmount());




        return null;
    }

    @Override
    public TransactionResponse getTransaction(String senderId, UUID transactionId) {
        return null;
    }

    @Override
    public TransactionResponse getTransactionHistory(UUID uuid, PageRequest of) {
        return null;
    }

    @Override
    public StateLogResponse getStateLog(UUID transactionId) {
        return null;
    }
}
