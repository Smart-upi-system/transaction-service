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
public class FraudCheckPassedEvent {

    private String eventId;
    private String eventType = "FraudCheckPassed";
    private UUID transactionId;
    private int riskScore;
    private String checkedBy;
    private LocalDateTime timestamp;

}


