package com.payment.payment_orchestration_system.service;

import com.payment.payment_orchestration_system.dto.request.PaymentRequest;
import com.payment.payment_orchestration_system.dto.response.PaymentResponse;
import com.payment.payment_orchestration_system.dto.response.PaymentStatusResponse;

import java.util.UUID;


// Defines the contract for PaymentService behavior.
public interface PaymentService {
    // Handles the createPayment operation.
    public PaymentResponse createPayment(PaymentRequest request );

    // Returns the paymentStatus.
    public PaymentStatusResponse getPaymentStatus(UUID paymentId );
}
