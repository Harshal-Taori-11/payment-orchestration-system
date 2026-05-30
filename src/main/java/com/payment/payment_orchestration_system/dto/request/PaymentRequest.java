package com.payment.payment_orchestration_system.dto.request;

import com.payment.payment_orchestration_system.enums.CurrencyCode;
import com.payment.payment_orchestration_system.enums.PaymentMethodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
// Holds immutable data for PaymentRequest.
public record PaymentRequest(

        @NotNull
        Long merchantId,

        @NotBlank
        @Size(max = 64, message = "idempotencyKey must not exceed 64 characters")
        String idempotencyKey,

        @NotNull
        @Positive
        BigDecimal amount,

        @NotNull
        CurrencyCode currencyCode,

        @NotNull
        PaymentMethodType paymentMethodType
) {}


