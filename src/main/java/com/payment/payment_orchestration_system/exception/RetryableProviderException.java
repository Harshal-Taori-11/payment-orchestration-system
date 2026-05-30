package com.payment.payment_orchestration_system.exception;

// Manages the RetryableProviderException component logic.
public class RetryableProviderException extends ProviderException{

    // Handles the RetryableProviderException operation.
    public RetryableProviderException(String message){
        super(message, true);
    }
}
