package com.uws.transaction_service.exception;

import org.springframework.http.HttpStatus;

public class UpiPinVerificationException extends RuntimeException {
    private final HttpStatus status;

    public UpiPinVerificationException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
