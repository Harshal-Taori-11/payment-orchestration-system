package com.payment.payment_orchestration_system.connector;

import com.payment.payment_orchestration_system.dto.request.ProviderPaymentRequest;
import com.payment.payment_orchestration_system.dto.response.ProviderPaymentResponse;
import com.payment.payment_orchestration_system.enums.PaymentProvider;

// Defines the contract for PaymentProviderConnector behavior.
public interface PaymentProviderConnector {
    PaymentProvider getProvider();

    ProviderPaymentResponse processPayment(ProviderPaymentRequest providerPaymentRequest);
}
