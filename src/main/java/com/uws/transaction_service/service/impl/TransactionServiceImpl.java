package com.uws.transaction_service.service.impl;

import com.uws.transaction_service.grpc.UserServiceGrpcClient;
import com.uws.transaction_service.model.Transaction;
import com.uws.transaction_service.model.TransactionStateLog;
import com.uws.transaction_service.model.dtos.*;
import com.uws.transaction_service.repository.TransactionLogRepository;
import com.uws.transaction_service.repository.TransactionRepository;
import com.uws.transaction_service.service.TransactionOrchestratorI;
import com.uws.user.grpc.proto.*;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link TransactionService}.
 *
 * <p>Handles the transaction lifecycle for P2P transfers and deposits:
 * <ul>
 *   <li>Guards against duplicate requests via the {@link IdempotencyManager}.</li>
 *   <li>Validates sender/receiver against the User Service over gRPC.</li>
 *   <li>Persists the transaction and hands off execution to the
 *       {@link TransactionOrchestrator} (Saga workflow).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionLogRepository logRepository;
    private final TransactionOrchestrator transactionOrchestrator;
    private final UserServiceGrpcClient userServiceGrpcClient;
    private final IdempotencyManager idempotencyManager;
    private final ModelMapper modelMapper;

    /**
     * Initiates a P2P money transfer from the given sender to the receiver identified by UPI ID.
     *
     * <p>The method is idempotent: if a request arrives with an idempotency key that was already
     * processed, the existing transaction is returned instead of creating a duplicate.
     *
     * <p>Runs inside a Spring-managed transaction. The idempotency key is only marked as processed
     * <em>after</em> the DB transaction commits, to avoid marking an in-flight (potentially
     * rolled-back) transaction as done.
     *
     * @param senderId      ID of the user sending money
     * @param request       transfer details (receiver UPI ID, amount, remarks, idempotency key)
     * @param correlationId correlation ID propagated for end-to-end tracing
     * @return the created transaction mapped to a response DTO
     * @throws InvalidTransactionException if the sender/receiver is invalid or it is a self-transfer
     */
    @Override
    @Transactional
    public TransactionResponse tranfer(String senderId, TransferRequest request, String correlationId) throws InvalidTransactionException {
        log.info("Processing transfer: senderId={}, receiverUpiId={}, amount={}",
                senderId, request.getReceiverUpiId(), request.getAmount());

        // Step 1: Idempotency check — if this idempotency key was already processed,
        // return the previously created transaction instead of processing it again.
        String idempotencyKey=idempotencyManager.generateOrGetKey(request.getIdempotencyKey());
        if (idempotencyManager.isDuplicate(idempotencyKey)) {
            String existingTxnId = idempotencyManager.getTransactionId(idempotencyKey);
            log.info("Duplicate request: idempotencyKey={}, existingTxnId={}",
                    idempotencyKey, existingTxnId);
            return getTransaction(senderId.toString(), existingTxnId);
        }

        // Step 2: Validate sender — verify the sender exists and is active.
        ValidationResponse senderValidation=userServiceGrpcClient.validateUser(senderId);
        if(!senderValidation.getValid() || !senderValidation.getActive()){
            throw new InvalidTransactionException("Sender account is invalid or inactive");
        }

        // Step 3: Get receiver by UPI ID — resolve the target wallet from the user service.
        UserResponse receiver = userServiceGrpcClient.getUserByUpiId(request.getReceiverUpiId());
        System.out.println("recevier user id========================"+receiver);
        if(!receiver.getSuccess() || !receiver.getActive()){
            throw new RuntimeException("Receiver not found: " + request.getReceiverUpiId());
        }

        // Step 4: Validate that sender != receiver — a self-transfer is not allowed.
        if(senderId.equals(receiver.getUserId())){
            throw new InvalidTransactionException("Cannot transfer to yourself");
        }

        // Step 5: Get sender UPI ID and wallet ID from the user service.
        String senderUpiId = String.valueOf(userServiceGrpcClient.getUserProfile(senderId).getUpiId());
        UserResponse userResponse=userServiceGrpcClient.getUserByUpiId(senderUpiId);
        String senderWalletId=userResponse.getWalletId();

        // Step 6: Build metadata — attach audit/context info (KYC state, initiation source).
        Map<String,Object> metdaData = new HashMap<>();
        metdaData.put("senderKycVerified",senderValidation.getKycVerified());
        metdaData.put("receiverKycVerified",receiver.getKycVerified());
        metdaData.put("initiatedBy","API");

        // Resolve the receiver's wallet ID for the transaction record.
        String responseWalletId=receiver.getWalletId();

        // Step 7: Build and persist the transaction entity in INITIATED state.
        Transaction transaction=Transaction.builder()
                .senderId(senderId)
                .receiverId(receiver.getUserId())
                .senderWalletId(senderWalletId)
                .receiverWalletId(responseWalletId)
                .senderUpiId(senderUpiId)
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

        // Capture the values the after-commit callback will need (effective-final locals).
        final String finalKey=idempotencyKey;
        log.info("Idempotency key={}", idempotencyKey);
        final String finalTransactionId=transaction.getTransactionId();

        // Register an after-commit hook: only mark the idempotency key as processed once the
        // enclosing DB transaction has actually committed. If the transaction rolls back, the key
        // stays unprocessed so the caller can safely retry.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                idempotencyManager.markAsProcessed(finalKey, finalTransactionId);
            }
        });

        // Step 8: Initiate the Saga workflow — the orchestrator drives debit, validation,
        // and credit steps asynchronously (via Kafka).
        transactionOrchestrator.initiateTransaction(transaction);

        // Step 9: (Commented out) Optionally trigger debit directly — state is VALIDATING here.
        // transactionOrchestrator.senderDebit(transaction,senderWalletId);

        // Return the created transaction mapped to the response DTO.
        TransactionResponse transactionResponse= modelMapper.map(transaction,TransactionResponse.class);
        return  transactionResponse;

    }

//    private String getSenderUpiId(String senderId) {
//        // In production: Call User Service gRPC to get UPI ID
//        // For now, return placeholder
//        return "sender@wallet";
//    }

    /**
     * Fetches a single transaction by its ID.
     *
     * @param senderId      ID of the requesting user (used for authorization/audit)
     * @param transactionId the transaction to fetch
     * @return the transaction mapped to a response DTO
     */
    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(String senderId, String transactionId) {
        log.info("Getting transaction: {}", transactionId);

        Transaction transaction=transactionRepository.findByTransactionId(transactionId);
        return modelMapper.map(transaction,TransactionResponse.class);
    }

    /**
     * Fetches the paginated transaction history for a user, most recent first.
     * A transaction is included if the user is either the sender or the receiver.
     *
     * @param userId   the user whose history to fetch
     * @param pageable pagination request (page number, page size, sort)
     * @return a page of transactions plus pagination metadata
     */
    @Override
    @Transactional(readOnly = true)
    public TransactionHistoryResponse getTransactionHistory(String userId, PageRequest pageable) {
        log.info("Getting transaction history: userId={}, page={}", userId, pageable.getPageNumber());
        Page<Transaction> transactionsPage=transactionRepository.
                findBySenderIdOrReceiverIdOrderByInitiatedAtDesc(userId,userId,pageable);

        // Map each entity to its response DTO.
        List<TransactionResponse> transactions = transactionsPage.getContent()
                .stream()
                .map(t -> modelMapper.map(t, TransactionResponse.class))
                .collect(Collectors.toList());

        // Build the response with pagination metadata.
        return TransactionHistoryResponse.builder()
                .transactions(transactions)
                .currentPage(transactionsPage.getNumber())
                .totalPages(transactionsPage.getTotalPages())
                .totalElements(transactionsPage.getTotalElements())
                .pageSize(transactionsPage.getSize())
                .first(transactionsPage.isFirst())
                .last(transactionsPage.isLast())
                .hasNext(transactionsPage.hasNext())
                .hasPrevious(transactionsPage.hasPrevious())
                .build();
    }

    /**
     * Fetches the ordered state-transition log for a transaction (i.e. the Saga audit trail).
     *
     * @param transactionId the transaction whose state log to fetch
     * @return the list of state transitions in chronological order
     */
    @Override
    @Transactional(readOnly = true)
    public StateLogResponse getStateLog(String transactionId) {
        log.info("Getting state log: transactionId={}", transactionId);

        // Verify the transaction exists before reading its log.
        transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionalIdNotFoundException("Transaction not found: " + transactionId));

        // Fetch all state logs for this transaction, oldest first.
        List<TransactionStateLog> logs = logRepository
                .findByTransactionIdOrderByCreatedAtAsc(transactionId);

        // Convert each log row into a state-transition DTO.
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

    /**
     * Credits the user's wallet directly — used for self-funded deposits.
     *
     * <p>Idempotent: a duplicate idempotency key returns the existing transaction. Unlike a P2P
     * transfer, there is no debit step — the orchestrator jumps straight to crediting the wallet.
     *
     * @param userId  ID of the user depositing money
     * @param request deposit details (UPI ID, amount, description, idempotency key)
     * @return the created transaction mapped to a response DTO
     */
    @Override
    @Transactional
    public TransactionResponse deposit(String userId, DepositRequest request) {
        // Step 1: Handle idempotency — if this key was already processed, return the
        // existing transaction instead of creating a duplicate deposit.
        String key = idempotencyManager.generateOrGetKey(request.getIdempotencyKey());
        if (idempotencyManager.isDuplicate(key)) {
            return getTransaction(userId, idempotencyManager.getTransactionId(key));
        }

        // Resolve the wallet for the given UPI ID.
        UserResponse receiver = userServiceGrpcClient.getUserByUpiId(request.getUpiId());
        String receiverWalletId=receiver.getWalletId();

        System.out.println("recevier user id========================"+receiver);

        // Step 2: Create the deposit transaction record (sender == receiver == self).
        Transaction transaction = Transaction.builder()
                .senderId(userId) // Or "BANK_GATEWAY"
                .senderUpiId(request.getUpiId())
                .receiverId(userId)
                .receiverUpiId(request.getUpiId())
                .receiverWalletId(receiverWalletId)
                .amount(request.getAmount())
                .currency("INR")
                .status("INITIATED")
                .type("DEPOSIT - SelfFund")
                .remarks(request.getDescription())
                .idempotencyKey(key)
                .initiatedAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);

        // Step 3: Mark the key as processed so retries short-circuit to the saved transaction.
        idempotencyManager.markAsProcessed(key, transaction.getTransactionId());

        // Step 4: Trigger the orchestrator.
        // Since this is a deposit, we jump straight to crediting the wallet.
        transactionOrchestrator.directCredit(transaction);

        return modelMapper.map(transaction, TransactionResponse.class);
    }
}
