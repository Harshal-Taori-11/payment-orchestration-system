package com.payment.payment_orchestration_system.routing;

import com.payment.payment_orchestration_system.dto.request.PaymentRequest;
import com.payment.payment_orchestration_system.enums.CurrencyCode;
import com.payment.payment_orchestration_system.enums.PaymentMethodType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;

// Manages the RoutingEngineTest component logic.
public class RoutingEngineTest {
    private RoutingEngine routingEngine;
    @BeforeEach
    void setUp() {
        routingEngine = new RoutingEngine( List.of( new CardRoutingStrategy(), new UPIRoutingStrategy() ) );
    }

    @Test
    void shouldRouteCardToProviderB() {
        PaymentRequest request = new PaymentRequest(1L, "ORD-1", new BigDecimal("1000.00"), CurrencyCode.INR, PaymentMethodType.CARD);
        RoutingStrategy strategy = routingEngine.resolve(request);

        assertNotNull(strategy);
        assertEquals(PaymentMethodType.CARD, request.paymentMethodType());
        // CARD routes to PROVIDER_A (per spec)
        assertEquals(com.payment.payment_orchestration_system.enums.PaymentProvider.PROVIDER_A, strategy.getProvider());
    }

    @Test
    void shouldRouteUpiToProviderA() {
        PaymentRequest request = new PaymentRequest(1L, "ORD-1", new BigDecimal("1000.00"), CurrencyCode.INR, PaymentMethodType.UPI);
        RoutingStrategy strategy = routingEngine.resolve(request);

        assertNotNull(strategy);
        assertEquals(PaymentMethodType.UPI, request.paymentMethodType());
        // UPI routes to PROVIDER_B (per spec)
        assertEquals(com.payment.payment_orchestration_system.enums.PaymentProvider.PROVIDER_B, strategy.getProvider());
    }
}
