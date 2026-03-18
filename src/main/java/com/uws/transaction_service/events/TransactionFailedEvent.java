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
public class TransactionFailedEvent {

    private String eventId;
    private String eventType = "TransactionFailed";
    private UUID transactionId;
    private UUID senderId;
    private UUID receiverId;
    private String reason;
    private String failedAtState;
    private LocalDateTime timestamp;
}
