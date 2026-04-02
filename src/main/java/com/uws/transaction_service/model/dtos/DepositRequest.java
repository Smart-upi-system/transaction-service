package com.uws.transaction_service.model.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DepositRequest {
    @NotBlank
    private String upiId;
    @Positive
    private BigDecimal amount;
    private String idempotencyKey; // Optional, can be generated if null
    private String description;
}
