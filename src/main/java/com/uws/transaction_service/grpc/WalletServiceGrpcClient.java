package com.uws.transaction_service.grpc;

import com.uws.wallet.grpc.proto.WalletServiceGrpc;
import com.uws.wallet.grpc.proto.DebitRequest;
import com.uws.wallet.grpc.proto.WalletResponse;
import com.uws.wallet.grpc.proto.BalanceResponse;
import com.uws.wallet.grpc.proto.GetBalanceRequest;
import com.uws.wallet.grpc.proto.CreditRequest;
import io.grpc.Status; // Required for Status.Code
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
public class WalletServiceGrpcClient {

    @GrpcClient("wallet-service")
    private WalletServiceGrpc.WalletServiceBlockingStub walletServiceStub;

    /**
     * Debit amount from user's wallet
     */
    public WalletResponse debit(UUID userId, UUID walletId, BigDecimal amount, UUID transactionId, String idempotencyKey) {
        log.info("gRPC: Calling debit - userId={}, walletId={}, amount={}, txnId={}", userId, walletId, amount, transactionId);

        try {
            DebitRequest request = DebitRequest.newBuilder()
                    .setUserId(userId.toString())
                    .setWalletId(walletId.toString()) // Matches proto field 2
                    .setAmount(amount.doubleValue())
                    .setTransactionId(transactionId.toString())
                    .setIdempotencyKey(idempotencyKey)
                    .build();

            WalletResponse response = walletServiceStub.debit(request);

            if (!response.getSuccess()) {
                log.error("gRPC: Debit failed - userId={}, reason={}", userId, response.getMessage());
                throw new RuntimeException("Debit failed: " + response.getMessage());
            }

            return response;

        } catch (StatusRuntimeException e) {
            log.error("gRPC: Debit call failed - error={}", e.getStatus(), e);
            // Using Status.Code.VALUE to avoid "cannot find symbol" on enums
            switch (e.getStatus().getCode()) {
                case FAILED_PRECONDITION: throw new RuntimeException("Insufficient balance", e);
                case ALREADY_EXISTS: throw new RuntimeException("Duplicate debit request", e);
                case NOT_FOUND: throw new RuntimeException("Wallet not found", e);
                case UNAVAILABLE: throw new RuntimeException("Wallet service unavailable", e);
                default: throw new RuntimeException("Failed to debit wallet: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Credit amount to user's wallet
     */
    public WalletResponse credit(UUID userId, UUID walletId, BigDecimal amount, UUID transactionId, String idempotencyKey) {
        log.info("gRPC: Calling credit - userId={}, walletId={}, amount={}", userId, walletId, amount);

        try {
            CreditRequest request = CreditRequest.newBuilder()
                    .setUserId(userId.toString())
                    .setWalletId(walletId.toString()) // Matches proto field 2
                    .setAmount(amount.doubleValue())
                    .setTransactionId(transactionId.toString())
                    .setIdempotencyKey(idempotencyKey)
                    .build();

            WalletResponse response = walletServiceStub.credit(request);

            if (!response.getSuccess()) {
                throw new RuntimeException("Credit failed: " + response.getMessage());
            }

            return response;

        } catch (StatusRuntimeException e) {
            log.error("gRPC: Credit call failed - error={}", e.getStatus(), e);
            switch (e.getStatus().getCode()) {
                case ALREADY_EXISTS: throw new RuntimeException("Duplicate credit request", e);
                case NOT_FOUND: throw new RuntimeException("Wallet not found", e);
                default: throw new RuntimeException("Failed to credit wallet: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Get wallet balance
     */
    public BalanceResponse getBalance(UUID walletId) {
        try {
            GetBalanceRequest request = GetBalanceRequest.newBuilder()
                    .setWalletId(walletId.toString())
                    .build();
            return walletServiceStub.getBalance(request);
        } catch (StatusRuntimeException e) {
            throw new RuntimeException("Failed to fetch balance: " + e.getMessage(), e);
        }
    }
}