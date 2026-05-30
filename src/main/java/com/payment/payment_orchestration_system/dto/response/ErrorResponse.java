package com.payment.payment_orchestration_system.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
// Holds immutable data for ErrorResponse.
public record ErrorResponse(
        int status,
        String error,
        String message,
        LocalDateTime timestamp
) {}
