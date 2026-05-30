package com.payment.payment_orchestration_system.connector.impl;

import com.payment.payment_orchestration_system.connector.PaymentProviderConnector;
import com.payment.payment_orchestration_system.dto.request.ProviderPaymentRequest;
import com.payment.payment_orchestration_system.dto.response.ProviderPaymentResponse;
import com.payment.payment_orchestration_system.enums.PaymentProvider;
import com.payment.payment_orchestration_system.enums.PaymentStatus;
import com.payment.payment_orchestration_system.exception.NonRetryableProviderException;
import com.payment.payment_orchestration_system.exception.RetryableProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

@Slf4j
@Component
// Manages the ProviderBConnector component logic.
public class ProviderBConnector implements PaymentProviderConnector {
    private static final Random random = new Random();

    // Returns the provider.
    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.PROVIDER_B;
    }

    // Handles the processPayment operation.
    @Override
    public ProviderPaymentResponse processPayment(ProviderPaymentRequest providerPaymentRequest) {

        int value = random.nextInt(100);

        log.info("Provider B processing paymentd={} randomValue={}", providerPaymentRequest.paymentId(),value);

        if(value < 90){
            return ProviderPaymentResponse.builder()
                    .status(PaymentStatus.SUCCESS)
                    .providerTransactionId("PB-"+ System.nanoTime())
                    .retryable(false).build();
        }

        if(value < 95){
            throw new RetryableProviderException("Temporary Timeout from Provider B");
        }

        throw new NonRetryableProviderException("Provider B rejected Request");
    }
}
