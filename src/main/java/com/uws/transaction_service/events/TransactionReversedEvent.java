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
public class TransactionReversedEvent {

    private String eventId;
    private String eventType = "TransactionReversed";
    private UUID transactionId;
    private UUID senderId;
    private BigDecimal refundedAmount;
    private String reason;
    private LocalDateTime timestamp;

}
