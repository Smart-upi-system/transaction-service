package com.uws.transaction_service.model.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StateLogResponse {
    private UUID transactionId;
    private int totalTransitions;
    private List<StateTransition> transitions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StateTransition {
        private String fromState;
        private String toState;
        private Map<String, Object> eventData;
        private LocalDateTime timestamp;
    }
}
