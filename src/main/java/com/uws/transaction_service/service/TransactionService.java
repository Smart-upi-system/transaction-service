package com.uws.transaction_service.service;

import com.uws.transaction_service.model.dtos.StateLogResponse;
import com.uws.transaction_service.model.dtos.TransactionResponse;
import com.uws.transaction_service.model.dtos.TransferRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

public interface TransactionService {
    TransactionResponse tranfer(UUID uuid, @Valid TransferRequest request, String correlationId);

    TransactionResponse getTransaction(String senderId, UUID transactionId);

    TransactionResponse getTransactionHistory(UUID uuid, PageRequest of);

    StateLogResponse getStateLog(UUID transactionId);
}
