package com.uws.transaction_service;

import com.uws.transaction_service.exception.UpiPinVerificationException;
import com.uws.transaction_service.grpc.UserServiceGrpcClient;
import com.uws.transaction_service.grpc.WalletServiceGrpcClient;
import com.uws.transaction_service.model.Transaction;
import com.uws.transaction_service.model.dtos.DepositRequest;
import com.uws.transaction_service.repository.TransactionLogRepository;
import com.uws.transaction_service.repository.TransactionRepository;
import com.uws.transaction_service.security.AuthPinClient;
import com.uws.transaction_service.service.impl.TransactionOrchestrator;
import com.uws.transaction_service.service.impl.TransactionServiceImpl;
import com.uws.transaction_service.utils.IdempotencyManager;
import com.uws.user.grpc.proto.UserResponse;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositPinTests {
    @Mock TransactionRepository transactionRepository;
    @Mock TransactionLogRepository logRepository;
    @Mock TransactionOrchestrator transactionOrchestrator;
    @Mock UserServiceGrpcClient userServiceGrpcClient;
    @Mock WalletServiceGrpcClient walletServiceGrpcClient;
    @Mock IdempotencyManager idempotencyManager;
    @Mock ModelMapper modelMapper;
    @Mock AuthPinClient authPinClient;
    @InjectMocks TransactionServiceImpl service;

    private DepositRequest request(String pin) {
        return DepositRequest.builder().upiId("alice@wallet").amount(BigDecimal.TEN)
                .idempotencyKey("deposit-test").upiPin(pin).build();
    }

    @Test void missingOrMalformedPinIsRejectedAndNotPrinted() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String pin : new String[] {null, "", "12345", "1234567", "abcdef"}) {
                assertTrue(validator.validate(request(pin)).stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("upiPin")));
            }
            assertTrue(validator.validate(request("654321")).isEmpty());
            assertFalse(request("654321").toString().contains("654321"));
        }
    }

    @Test void incorrectPinCannotCreateOrCreditDeposit() {
        doThrow(new UpiPinVerificationException("Invalid PIN", HttpStatus.UNAUTHORIZED))
                .when(authPinClient).verify("alice", "123456");
        assertThrows(UpiPinVerificationException.class, () -> service.deposit("alice", request("123456")));
        verifyNoInteractions(userServiceGrpcClient, transactionRepository, transactionOrchestrator);
        verify(idempotencyManager, never()).markAsProcessed(any(), any());
    }

    @Test void unavailableVerificationCannotCreditDeposit() {
        doThrow(new UpiPinVerificationException("Unavailable", HttpStatus.SERVICE_UNAVAILABLE))
                .when(authPinClient).verify("alice", "123456");
        assertThrows(UpiPinVerificationException.class, () -> service.deposit("alice", request("123456")));
        verifyNoInteractions(userServiceGrpcClient, transactionRepository, transactionOrchestrator);
    }

    @Test void validPinIsCheckedBeforeCreatingAndCreditingDeposit() {
        when(userServiceGrpcClient.getUserByUpiId("alice@wallet"))
                .thenReturn(UserResponse.newBuilder().setWalletId("wallet-alice").build());
        service.deposit("alice", request("123456"));
        var order = inOrder(authPinClient, userServiceGrpcClient, transactionRepository, transactionOrchestrator);
        order.verify(authPinClient).verify("alice", "123456");
        order.verify(userServiceGrpcClient).getUserByUpiId("alice@wallet");
        order.verify(transactionRepository).save(any(Transaction.class));
        order.verify(transactionOrchestrator).directCredit(any(Transaction.class));
    }
}
