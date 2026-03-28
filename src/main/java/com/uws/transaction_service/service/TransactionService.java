package com.uws.transaction_service.service;

import com.uws.transaction_service.model.dtos.*;
import jakarta.transaction.InvalidTransactionException;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

public interface TransactionService {
    TransactionResponse tranfer(String uuid, @Valid TransferRequest request, String correlationId) throws InvalidTransactionException;

    TransactionResponse getTransaction(String senderId, String transactionId);

    TransactionHistoryResponse getTransactionHistory(String uuid, PageRequest of);

    StateLogResponse getStateLog(String transactionId);

    TransactionResponse deposit(String userId, DepositRequest request);
}
