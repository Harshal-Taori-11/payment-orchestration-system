package com.payment.payment_orchestration_system.routing;

import com.payment.payment_orchestration_system.dto.request.PaymentRequest;
import com.payment.payment_orchestration_system.enums.PaymentMethodType;
import com.payment.payment_orchestration_system.enums.PaymentProvider;
import org.springframework.stereotype.Component;

@Component
// Manages the UPIRoutingStrategy component logic.
public class UPIRoutingStrategy implements RoutingStrategy {

    // Handles the supports operation.
    @Override
    public boolean supports(PaymentRequest request) {
        return request.paymentMethodType() == PaymentMethodType.UPI;
    }

    // Returns the provider.
    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.PROVIDER_B;
    }
}
