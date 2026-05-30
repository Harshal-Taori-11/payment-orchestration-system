package com.payment.payment_orchestration_system.dto.response;

import com.payment.payment_orchestration_system.enums.CurrencyCode;
import com.payment.payment_orchestration_system.enums.PaymentMethodType;
import com.payment.payment_orchestration_system.enums.PaymentProvider;
import com.payment.payment_orchestration_system.enums.PaymentStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
// Holds immutable data for PaymentResponse.
public record PaymentResponse(
        UUID paymentId,
        PaymentStatus status,
        PaymentProvider provider,
        BigDecimal amount,
        CurrencyCode currency,
        PaymentMethodType paymentMethod,
        String idempotencyKey,
        LocalDateTime createdAt,
        String message
) {}

