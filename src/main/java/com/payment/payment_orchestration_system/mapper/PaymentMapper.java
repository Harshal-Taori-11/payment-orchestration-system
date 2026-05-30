package com.payment.payment_orchestration_system.mapper;

import com.payment.payment_orchestration_system.dto.response.PaymentResponse;
import com.payment.payment_orchestration_system.dto.response.PaymentStatusResponse;
import com.payment.payment_orchestration_system.entity.PaymentTransaction;
import com.payment.payment_orchestration_system.util.MoneyUtil;
import org.springframework.stereotype.Component;

@Component
// Manages the PaymentMapper component logic.
public class PaymentMapper {

    // Handles the toPaymentResponse operation.
    public PaymentResponse toPaymentResponse(PaymentTransaction transaction) {
        String message = switch (transaction.getStatus()) {
            case SUCCESS -> "Payment processed successfully";
            case FAILED  -> "Payment failed: " + transaction.getFailureReason();
            default      -> "Payment is being processed";
        };
        return PaymentResponse.builder()
                .paymentId(transaction.getPaymentId())
                .status(transaction.getStatus())
                .provider(transaction.getProvider())
                .amount(MoneyUtil.toMajorUnits(transaction.getAmount()))
                .currency(transaction.getCurrency())
                .paymentMethod(transaction.getPaymentMethod())
                .idempotencyKey(transaction.getIdempotencyKey())
                .createdAt(transaction.getCreatedAt())
                .message(message)
                .build();
    }

    // Handles the toStatusResponse operation.
    public PaymentStatusResponse toStatusResponse(PaymentTransaction transaction) {
        return PaymentStatusResponse.builder()
                .paymentId(transaction.getPaymentId())
                .status(transaction.getStatus())
                .provider(transaction.getProvider())
                .amount(MoneyUtil.toMajorUnits(transaction.getAmount()))
                .currency(transaction.getCurrency())
                .paymentMethod(transaction.getPaymentMethod())
                .idempotencyKey(transaction.getIdempotencyKey())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .failureReason(transaction.getFailureReason())
                .build();
    }
}
