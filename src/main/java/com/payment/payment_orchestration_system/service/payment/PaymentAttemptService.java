package com.payment.payment_orchestration_system.service.payment;

import com.payment.payment_orchestration_system.entity.PaymentAttempt;
import com.payment.payment_orchestration_system.entity.PaymentTransaction;
import com.payment.payment_orchestration_system.enums.PaymentProvider;
import com.payment.payment_orchestration_system.enums.PaymentStatus;
import com.payment.payment_orchestration_system.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// Manages the PaymentAttemptService component logic.
public class PaymentAttemptService {

    private final PaymentAttemptRepository paymentAttemptRepository;

    public void createAttempt(PaymentTransaction transaction, PaymentProvider provider,
                              int attemptNumber, PaymentStatus status, String errorCode, String errorMessage, long latency){

        PaymentAttempt attempt = PaymentAttempt.builder()
                .paymentTransaction(transaction)
                .provider(provider)
                .attemptNumber(attemptNumber)
                .status(status)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .latencyMs(latency).build();

        paymentAttemptRepository.save(attempt);
    }
}
