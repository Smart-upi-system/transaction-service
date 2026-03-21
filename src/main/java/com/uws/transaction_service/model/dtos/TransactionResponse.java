package com.uws.transaction_service.model.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse {
    private UUID transactionId;
    private UUID senderId;
    private UUID receiverId;
    private String senderUpiId;
    private String receiverUpiId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String type;
    private String remarks;
    private String failureReason;
    private Map<String, Object> metadata;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
