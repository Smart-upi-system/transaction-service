package com.uws.transaction_service.events;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionInitiatedEvent {

    private String eventId;
    private String eventType = "TransactionInitiated";
    private String transactionId;
    private String senderId;
    private String receiverId;
    private String senderUpiId;
    private String receiverUpiId;
    private BigDecimal amount;
    private String currency;
    private String remarks;
    private LocalDateTime timestamp;
}
