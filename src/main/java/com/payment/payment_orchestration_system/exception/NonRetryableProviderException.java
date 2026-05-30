package com.payment.payment_orchestration_system.exception;

// Manages the NonRetryableProviderException component logic.
public class NonRetryableProviderException extends ProviderException{

    // Handles the NonRetryableProviderException operation.
    public NonRetryableProviderException(String message){
        super(message, false);
    }
}
