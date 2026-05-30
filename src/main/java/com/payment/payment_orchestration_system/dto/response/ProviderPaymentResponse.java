package com.payment.payment_orchestration_system.dto.response;

import com.payment.payment_orchestration_system.enums.PaymentStatus;
import lombok.Builder;

@Builder
// Holds immutable data for ProviderPaymentResponse.
public record ProviderPaymentResponse(
        PaymentStatus status,
        String providerTransactionId,
        String errorCode,
        String errorMessage,
        boolean retryable
) {}
