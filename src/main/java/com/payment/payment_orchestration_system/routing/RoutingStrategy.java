package com.payment.payment_orchestration_system.routing;

import com.payment.payment_orchestration_system.dto.request.PaymentRequest;
import com.payment.payment_orchestration_system.enums.PaymentProvider;

// Defines the contract for RoutingStrategy behavior.
public interface RoutingStrategy {

    boolean supports(PaymentRequest request);

    PaymentProvider getProvider();
}
