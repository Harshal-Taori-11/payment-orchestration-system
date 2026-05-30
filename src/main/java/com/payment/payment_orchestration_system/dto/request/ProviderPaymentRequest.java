package com.payment.payment_orchestration_system.dto.request;

import com.payment.payment_orchestration_system.enums.CurrencyCode;
import lombok.Builder;

import java.util.UUID;

@Builder
// Holds immutable data for ProviderPaymentRequest.
public record ProviderPaymentRequest (
        UUID paymentId,
        Long amount,
        CurrencyCode currencyCode
){}
