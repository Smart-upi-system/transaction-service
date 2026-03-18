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
public class FraudCheckFailedEvent {

    private String eventId;
    private String eventType = "FraudCheckFailed";
    private UUID transactionId;
    private String reason;
    private int riskScore;
    private LocalDateTime timestamp;

}
