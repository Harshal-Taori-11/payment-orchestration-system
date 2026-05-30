package com.payment.payment_orchestration_system.service.payment;

import com.payment.payment_orchestration_system.dto.request.PaymentRequest;
import com.payment.payment_orchestration_system.dto.response.PaymentResponse;
import com.payment.payment_orchestration_system.enums.CurrencyCode;
import com.payment.payment_orchestration_system.enums.PaymentMethodType;
import com.payment.payment_orchestration_system.enums.PaymentStatus;
import com.payment.payment_orchestration_system.mapper.PaymentMapper;
import com.payment.payment_orchestration_system.repository.PaymentTransactionRepository;
import com.payment.payment_orchestration_system.service.orchestration.PaymentOrchestrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
// Manages the PaymentServiceTest component logic.
public class PaymentServiceTest {

    @Mock
    private PaymentOrchestrationService orchestrationService;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void shouldCreatePaymentSuccessfully() {
        PaymentRequest request = new PaymentRequest(1L, "ORD-5000", new BigDecimal("5000.00"), CurrencyCode.INR, PaymentMethodType.CARD);
        UUID paymentId = UUID.randomUUID();
        PaymentResponse response = PaymentResponse.builder()
                .paymentId(paymentId)
                .status(PaymentStatus.PROCESSING)
                .message("Payment initiated successfully")
                .build();
        
        when( orchestrationService.processPayment( request ) ).thenReturn(response);
        
        PaymentResponse result = paymentService.createPayment( request );

        assertEquals( PaymentStatus.PROCESSING, result.status() );
        assertEquals( paymentId, result.paymentId() );
        verify(orchestrationService, times(1)).processPayment(request);
    }
}
