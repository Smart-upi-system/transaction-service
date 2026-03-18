package com.uws.transaction_service.model;


import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "transaction_state_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStateLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "log_id")
    private UUID logId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "from_state", nullable = false, length = 20)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 20)
    private String toState;

    @Type(JsonBinaryType.class)
    @Column(name = "event_data", columnDefinition = "jsonb")
    private Map<String, Object> eventData;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

}
