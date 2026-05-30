package com.payment.payment_orchestration_system.service.payment;

import com.payment.payment_orchestration_system.dto.request.PaymentRequest;
import com.payment.payment_orchestration_system.dto.response.PaymentResponse;
import com.payment.payment_orchestration_system.dto.response.PaymentStatusResponse;
import com.payment.payment_orchestration_system.entity.PaymentTransaction;
import com.payment.payment_orchestration_system.exception.PaymentNotFoundException;
import com.payment.payment_orchestration_system.mapper.PaymentMapper;
import com.payment.payment_orchestration_system.repository.PaymentTransactionRepository;
import com.payment.payment_orchestration_system.service.PaymentService;
import com.payment.payment_orchestration_system.service.orchestration.PaymentOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
// Manages the PaymentServiceImpl component logic.
public class PaymentServiceImpl implements PaymentService {

    private final PaymentOrchestrationService orchestrationService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentMapper paymentMapper;

    // Handles the createPayment operation.
    @Override
    public PaymentResponse createPayment(PaymentRequest request ) {
        return orchestrationService .processPayment(request);
    }

    // Returns the paymentStatus.
    @Override
    public PaymentStatusResponse getPaymentStatus(UUID paymentId ) {
        PaymentTransaction transaction = paymentTransactionRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException( "Payment not found" ));
        return paymentMapper.toStatusResponse(transaction);
    }
}
