package com.uws.transaction_service.exception;

public class TransactionFailedException extends RuntimeException {
    public TransactionFailedException(String message) {
        super(message);
    }
}
