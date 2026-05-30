package com.payment.payment_orchestration_system.service.orchestration;

import com.payment.payment_orchestration_system.connector.PaymentProviderConnector;
import com.payment.payment_orchestration_system.dto.request.PaymentRequest;
import com.payment.payment_orchestration_system.dto.response.PaymentResponse;
import com.payment.payment_orchestration_system.dto.response.ProviderPaymentResponse;
import com.payment.payment_orchestration_system.entity.PaymentTransaction;
import com.payment.payment_orchestration_system.enums.CurrencyCode;
import com.payment.payment_orchestration_system.enums.PaymentMethodType;
import com.payment.payment_orchestration_system.enums.PaymentProvider;
import com.payment.payment_orchestration_system.enums.PaymentStatus;
import com.payment.payment_orchestration_system.exception.DuplicateRequestException;
import com.payment.payment_orchestration_system.exception.LockAcquisitionException;
import com.payment.payment_orchestration_system.exception.NonRetryableProviderException;
import com.payment.payment_orchestration_system.exception.RetryableProviderException;
import com.payment.payment_orchestration_system.factory.PaymentProviderFactory;
import com.payment.payment_orchestration_system.mapper.PaymentMapper;
import com.payment.payment_orchestration_system.repository.PaymentTransactionRepository;
import com.payment.payment_orchestration_system.routing.CardRoutingStrategy;
import com.payment.payment_orchestration_system.routing.RoutingEngine;
import com.payment.payment_orchestration_system.service.idempotency.IdempotencyService;
import com.payment.payment_orchestration_system.service.lock.DistributedLockService;
import com.payment.payment_orchestration_system.service.payment.PaymentAttemptService;
import com.payment.payment_orchestration_system.service.providers.ProviderExecutorService;
import com.payment.payment_orchestration_system.service.retry.RetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Manages the PaymentOrchestrationServiceTest component logic.
public class PaymentOrchestrationServiceTest {

    private PaymentTransactionRepository repository;
    private PaymentProviderFactory factory;
    private RetryService retryService;
    private ProviderExecutorService executorService;
    private PaymentMapper mapper;
    private PaymentAttemptService attemptService;
    private DistributedLockService lockService;
    private IdempotencyService idempotencyService;
    private PaymentOrchestrationService orchestrationService;

    @BeforeEach
    void setup() {
        repository = mock(PaymentTransactionRepository.class);
        factory = mock(PaymentProviderFactory.class);
        retryService = new RetryService();
        executorService = mock(ProviderExecutorService.class);
        mapper = new PaymentMapper();
        attemptService = mock(PaymentAttemptService.class);
        lockService = mock(DistributedLockService.class);
        idempotencyService = mock(IdempotencyService.class);

        // CardRoutingStrategy routes CARD → PROVIDER_A (per spec)
        RoutingEngine routingEngine = new RoutingEngine(List.of(new CardRoutingStrategy()));
        orchestrationService = new PaymentOrchestrationService(
                repository, routingEngine, retryService, factory, executorService,
                mapper, attemptService, idempotencyService, lockService
        );

        // Common stub: save returns the transaction passed in (null-safe for Mockito stub registration)
        when(repository.save(any())).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            if (tx != null && tx.getId() == 0) tx.setId(1);
            return tx;
        });
    }

    @Test
    void shouldProcessPaymentSuccessfully() {
        PaymentRequest request = new PaymentRequest(1L, "ORD-001", new BigDecimal("1000.00"), CurrencyCode.INR, PaymentMethodType.CARD);

        // checkDuplicate does nothing (no duplicate)
        doNothing().when(idempotencyService).checkDuplicate(1L, "ORD-001");
        when(lockService.acquireLock(any())).thenReturn(true);

        PaymentProviderConnector connector = mock(PaymentProviderConnector.class);
        when(factory.getConnector(PaymentProvider.PROVIDER_A)).thenReturn(connector);

        ProviderPaymentResponse providerResponse = ProviderPaymentResponse.builder()
                .status(PaymentStatus.SUCCESS)
                .providerTransactionId("TXN-123")
                .build();
        when(executorService.execute(any(), any())).thenReturn(providerResponse);

        PaymentResponse response = orchestrationService.processPayment(request);

        assertEquals(PaymentStatus.SUCCESS, response.status());
        assertEquals(PaymentProvider.PROVIDER_A, response.provider());
        verify(repository, atLeastOnce()).save(any(PaymentTransaction.class));
        verify(idempotencyService).cacheTransaction(any(PaymentTransaction.class));
    }

    @Test
    void shouldThrowDuplicateExceptionForExistingKey() {
        PaymentRequest request = new PaymentRequest(1L, "ORD-001", new BigDecimal("1000.00"), CurrencyCode.INR, PaymentMethodType.CARD);

        // Simulate duplicate detected in Redis or PostgreSQL
        doThrow(new DuplicateRequestException("Idempotency key 'ORD-001' already used."))
                .when(idempotencyService).checkDuplicate(1L, "ORD-001");

        assertThrows(DuplicateRequestException.class, () -> orchestrationService.processPayment(request));

        // Lock must never be acquired — duplicate check is pre-lock fast path
        verify(lockService, never()).acquireLock(any());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowLockAcquisitionExceptionWhenLockFails() {
        PaymentRequest request = new PaymentRequest(1L, "ORD-001", new BigDecimal("1000.00"), CurrencyCode.INR, PaymentMethodType.CARD);

        doNothing().when(idempotencyService).checkDuplicate(anyLong(), anyString());
        when(lockService.acquireLock(any())).thenReturn(false);

        assertThrows(LockAcquisitionException.class, () -> orchestrationService.processPayment(request));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldFailoverToSecondaryProviderOnPrimaryRetryExhaustion() {
        PaymentRequest request = new PaymentRequest(1L, "ORD-001", new BigDecimal("1000.00"), CurrencyCode.INR, PaymentMethodType.CARD);

        doNothing().when(idempotencyService).checkDuplicate(anyLong(), anyString());
        when(lockService.acquireLock(any())).thenReturn(true);

        PaymentProviderConnector connectorA = mock(PaymentProviderConnector.class);
        PaymentProviderConnector connectorB = mock(PaymentProviderConnector.class);
        when(factory.getConnector(PaymentProvider.PROVIDER_A)).thenReturn(connectorA);
        when(factory.getConnector(PaymentProvider.PROVIDER_B)).thenReturn(connectorB);

        // Provider A always throws retryable exception (exhausts retries)
        when(executorService.execute(eq(connectorA), any()))
                .thenThrow(new RetryableProviderException("Timeout"));

        // Provider B (fallback) succeeds
        ProviderPaymentResponse fallbackResponse = ProviderPaymentResponse.builder()
                .status(PaymentStatus.SUCCESS)
                .providerTransactionId("PB-FALLBACK-001")
                .build();
        when(executorService.execute(eq(connectorB), any())).thenReturn(fallbackResponse);

        PaymentResponse response = orchestrationService.processPayment(request);

        assertEquals(PaymentStatus.SUCCESS, response.status());
        assertEquals(PaymentProvider.PROVIDER_B, response.provider());
    }

    @Test
    void shouldMarkPaymentFailedOnNonRetryableException() {
        PaymentRequest request = new PaymentRequest(1L, "ORD-001", new BigDecimal("1000.00"), CurrencyCode.INR, PaymentMethodType.CARD);

        doNothing().when(idempotencyService).checkDuplicate(anyLong(), anyString());
        when(lockService.acquireLock(any())).thenReturn(true);

        PaymentProviderConnector connector = mock(PaymentProviderConnector.class);
        when(factory.getConnector(PaymentProvider.PROVIDER_A)).thenReturn(connector);
        when(executorService.execute(any(), any()))
                .thenThrow(new NonRetryableProviderException("Card declined"));

        PaymentResponse response = orchestrationService.processPayment(request);

        assertEquals(PaymentStatus.FAILED, response.status());
    }

    @Test
    void shouldReleaseLockEvenOnException() {
        PaymentRequest request = new PaymentRequest(1L, "ORD-001", new BigDecimal("1000.00"), CurrencyCode.INR, PaymentMethodType.CARD);

        doNothing().when(idempotencyService).checkDuplicate(anyLong(), anyString());
        when(lockService.acquireLock(any())).thenReturn(true);

        // Simulate unexpected DB error after lock acquisition
        when(repository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> orchestrationService.processPayment(request));

        // Lock must always be released regardless of exception
        verify(lockService).releaseLock(any());
    }
}

