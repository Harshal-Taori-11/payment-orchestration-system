package com.payment.payment_orchestration_system.factory;

import com.payment.payment_orchestration_system.connector.PaymentProviderConnector;
import com.payment.payment_orchestration_system.enums.PaymentProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
// Manages the PaymentProviderFactory component logic.
public class PaymentProviderFactory {

    private final List<PaymentProviderConnector> connectors;
    private final Map<PaymentProvider,PaymentProviderConnector> connectorMap = new EnumMap<>(PaymentProvider.class);

    // Handles the init operation.
    @PostConstruct
    public void init(){
        connectors.forEach(connector -> connectorMap.put(connector.getProvider(), connector));
    }

    // Returns the connector.
    public PaymentProviderConnector getConnector(PaymentProvider provider){
        PaymentProviderConnector connector = connectorMap.get(provider);

        if(connector == null){
            throw new IllegalArgumentException("Unsupported provider: " + provider);
        }

        return connector;
    }

}
