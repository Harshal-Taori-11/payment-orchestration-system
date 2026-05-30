package com.payment.payment_orchestration_system.exception;

import com.payment.payment_orchestration_system.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
// Manages the GlobalExceptionHandler component logic.
public class GlobalExceptionHandler {

    // Handles the handlePaymentNotFound operation.
    @ExceptionHandler( PaymentNotFoundException.class )
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex ) {
        return buildErrorResponse( HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Handles the handleDuplicateRequest operation.
    @ExceptionHandler( DuplicateRequestException.class )
    public ResponseEntity<ErrorResponse> handleDuplicateRequest(DuplicateRequestException ex ) {
        return buildErrorResponse( HttpStatus.CONFLICT, ex.getMessage() );
    }

    // Handles the handleLockException operation.
    @ExceptionHandler( LockAcquisitionException.class )
    public ResponseEntity<ErrorResponse> handleLockException(LockAcquisitionException ex ) {
        return buildErrorResponse( HttpStatus.CONFLICT, ex.getMessage() );
    }

    // Handles the handleProviderException operation.
    @ExceptionHandler( ProviderException.class )
    public ResponseEntity<ErrorResponse> handleProviderException(ProviderException ex ) {
        return buildErrorResponse( HttpStatus.BAD_GATEWAY, ex.getMessage() );
    }

    // Handles the handleValidationException operation.
    @ExceptionHandler( MethodArgumentNotValidException.class )
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex ) {
        String message = ex.getBindingResult().getFieldError().getDefaultMessage();
        return buildErrorResponse( HttpStatus.BAD_REQUEST, message );
    }

    // Handles the handleGenericException operation.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex ) {
        return buildErrorResponse( HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage() );
    }

    // Handles the buildErrorResponse operation.
    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message ) {
        ErrorResponse response = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .timestamp(LocalDateTime.now()).build();
        return ResponseEntity .status(status) .body(response);
    }
}
