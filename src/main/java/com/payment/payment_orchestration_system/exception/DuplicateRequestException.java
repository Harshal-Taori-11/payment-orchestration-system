package com.payment.payment_orchestration_system.exception;

// Manages the DuplicateRequestException component logic.
public class DuplicateRequestException extends RuntimeException {

    // Handles the DuplicateRequestException operation.
    public DuplicateRequestException( String message) {
        super(message);
    }
}
