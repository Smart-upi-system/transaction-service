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
public class TransactionCompletedEvent {

    private String eventId;
    private String eventType = "TransactionCompleted";
    private UUID transactionId;
    private UUID senderId;
    private UUID receiverId;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime timestamp;

}
