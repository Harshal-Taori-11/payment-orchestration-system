package com.payment.payment_orchestration_system.exception;

// Manages the PaymentNotFoundException component logic.
public class PaymentNotFoundException extends RuntimeException {
    // Handles the PaymentNotFoundException operation.
    public PaymentNotFoundException(String message) {
        super(message);
    }
}
