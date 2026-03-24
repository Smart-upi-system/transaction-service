package com.uws.transaction_service.controller;

import com.uws.transaction_service.model.dtos.StateLogResponse;
import com.uws.transaction_service.model.dtos.TransactionHistoryResponse;
import com.uws.transaction_service.model.dtos.TransactionResponse;
import com.uws.transaction_service.model.dtos.TransferRequest;
import com.uws.transaction_service.service.TransactionService;
import jakarta.transaction.InvalidTransactionException;
import jakarta.validation.Valid;
import jakarta.ws.rs.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/transaction")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Initiate P2P transfer
     */
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(@RequestHeader("X-User-Id") String senderId, @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId, @Valid @RequestBody TransferRequest request) throws InvalidTransactionException {
        log.info("POST /transactions/transfer - senderId: {}, receiverUpiId: {}",
                senderId, request.getReceiverUpiId());


        TransactionResponse response=transactionService.tranfer(
                senderId ,request,correlationId);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}

@GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(@RequestHeader("X-User-Id") String senderId, @PathVariable String transactionId){
    log.info("GET /transactions/{}", transactionId);
    TransactionResponse response=transactionService.getTransaction(senderId,transactionId);
//   if (!response.getSenderId().equals(UUID.fromString(userId)) &&
//            !response.getReceiverId().equals(UUID.fromString(userId))) {
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
//        }
    if(ObjectUtils.isEmpty(response)){
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return ResponseEntity.ok(response);

}

@GetMapping("/history")
public ResponseEntity<TransactionHistoryResponse> getHistory(@RequestHeader("X-User-Id") String userId,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size){
    log.info("GET /transactions/history - userId: {}", userId);

    TransactionHistoryResponse response=transactionService.getTransactionHistory(userId, PageRequest.of(page,size));

return ResponseEntity.ok(response);

}
    /**
     * Get transaction state log (audit)
     */
    @GetMapping("/{transactionId}/state-log")
    public ResponseEntity<StateLogResponse> getStateLog(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String transactionId) {

        log.info("GET /transactions/{}/state-log", transactionId);

        StateLogResponse response = transactionService.getStateLog(transactionId);

        return ResponseEntity.ok(response);
    }





}
