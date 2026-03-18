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
public class CreditConfirmedEvent {

    private String eventId;
    private String eventType = "CreditConfirmed";
    private UUID transactionId;
    private UUID walletId;
    private UUID userId;
    private BigDecimal amount;
    private BigDecimal newBalance;
    private LocalDateTime timestamp;
}
