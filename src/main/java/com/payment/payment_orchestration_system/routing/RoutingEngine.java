package com.payment.payment_orchestration_system.routing;

import com.payment.payment_orchestration_system.dto.request.PaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
// Manages the RoutingEngine component logic.
public class RoutingEngine {

    private final List<RoutingStrategy> routingStrategies;

    // Handles the resolve operation.
    public RoutingStrategy resolve(PaymentRequest request){
        return routingStrategies.stream().filter(strategy -> strategy.supports(request))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No Routing Strategy Found"));
    }

}
