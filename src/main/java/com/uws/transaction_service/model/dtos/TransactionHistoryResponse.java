package com.uws.transaction_service.model.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.uws.transaction_service.model.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionHistoryResponse {
    private List<TransactionResponse> transactions;
    /**
     * Current page number (0-indexed)
     */
    private int currentPage;

    /**
     * Total number of pages available
     */
    private int totalPages;

    /**
     * Total number of transactions across all pages
     */
    private long totalElements;

    /**
     * Number of transactions in current page
     */
    private int pageSize;

    /**
     * Whether this is the first page
     */
    private boolean first;

    /**
     * Whether this is the last page
     */
    private boolean last;

    /**
     * Whether there is a next page
     */
    private boolean hasNext;

    /**
     * Whether there is a previous page
     */
    private boolean hasPrevious;

}
