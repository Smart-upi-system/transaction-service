package com.uws.transaction_service.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_sender_id", columnList = "sender_id"),
        @Index(name = "idx_receiver_id", columnList = "receiver_id"),
        @Index(name = "idx_idempotency_key", columnList = "idempotency_key"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_initiated_at", columnList = "initiated_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "sender_id", nullable = false)
    private String senderId;

    @Column(name = "receiver_id", nullable = false)
    private String receiverId;

    @Column(name = "sender_upi_id", nullable = false, length = 100)
    private String senderUpiId;

    @Column(name = "receiver_upi_id", nullable = false, length = 100)
    private String receiverUpiId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 3, nullable = false)
    @Builder.Default
    private String currency = "INR";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "INITIATED";

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String type = "P2P_TRANSFER";

    @Column(length = 500)
    private String remarks;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "initiated_at", nullable = false)
    @Builder.Default
    private LocalDateTime initiatedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Pre-persist callback to generate ID if not set
     */
    @PrePersist
    public void prePersist() {
        if (this.transactionId == null) {
            this.transactionId = java.util.UUID.randomUUID().toString();
        }
    }
}
