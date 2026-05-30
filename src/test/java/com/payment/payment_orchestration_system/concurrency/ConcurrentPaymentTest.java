package com.payment.payment_orchestration_system.concurrency;

import com.payment.payment_orchestration_system.dto.request.PaymentRequest;
import com.payment.payment_orchestration_system.dto.response.PaymentResponse;
import com.payment.payment_orchestration_system.enums.CurrencyCode;
import com.payment.payment_orchestration_system.enums.PaymentMethodType;
import com.payment.payment_orchestration_system.exception.LockAcquisitionException;
import com.payment.payment_orchestration_system.repository.PaymentTransactionRepository;
import com.payment.payment_orchestration_system.service.idempotency.IdempotencyService;
import com.payment.payment_orchestration_system.service.lock.DistributedLockService;
import com.payment.payment_orchestration_system.service.orchestration.PaymentOrchestrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
// Manages the ConcurrentPaymentTest component logic.
class ConcurrentPaymentTest {

    @Autowired
    private PaymentOrchestrationService orchestrationService;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @MockitoBean
    private IdempotencyService idempotencyService;

    @MockitoBean
    private DistributedLockService distributedLockService;

    @AfterEach
    void cleanup() {
        paymentTransactionRepository.deleteAll();
    }

    @Test
    void shouldHandleConcurrentDuplicateRequests() throws Exception {
        int threadCount = 20;

        // Simulate a real distributed lock using an AtomicBoolean (one winner, rest fail)
        AtomicBoolean lockHolder = new AtomicBoolean(false);
        when(distributedLockService.acquireLock(any())).thenAnswer(inv ->
                lockHolder.compareAndSet(false, true)
        );
        doNothing().when(distributedLockService).releaseLock(any());

        // checkDuplicate does nothing (no duplicate) — IdempotencyService is mocked
        doNothing().when(idempotencyService).checkDuplicate(anyLong(), anyString());
        doNothing().when(idempotencyService).cacheTransaction(any());

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<Object>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    return orchestrationService.processPayment(PaymentRequest.builder()
                            .merchantId(1L)
                            .idempotencyKey("ORD-999")
                            .amount(BigDecimal.valueOf(100))
                            .currencyCode(CurrencyCode.INR)
                            .paymentMethodType(PaymentMethodType.CARD)
                            .build());
                } catch (LockAcquisitionException e) {
                    return e; // expected for concurrent duplicates
                } finally {
                    latch.countDown();
                }
            }));
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long successCount = futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { return e; } })
                .filter(r -> r instanceof PaymentResponse)
                .count();

        long lockRejected = futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { return e; } })
                .filter(r -> r instanceof LockAcquisitionException)
                .count();

        // Exactly 1 should succeed (only one thread wins the lock)
        assertEquals(1, successCount, "Exactly one request should succeed");
        assertEquals(threadCount - 1, lockRejected, "All others should be rejected by lock");
    }
}