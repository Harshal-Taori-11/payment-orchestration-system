package com.payment.payment_orchestration_system.exception;

// Manages the ProviderException component logic.
public class ProviderException extends RuntimeException{

    private final boolean retryable;

    // Handles the ProviderException operation.
    public ProviderException(String message,boolean retryable){
        super(message);
        this.retryable = retryable;
    }

    // Checks whether retryable is true.
    public boolean isRetryable(){
        return retryable;
    }

}
