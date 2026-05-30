package com.payment.payment_orchestration_system.service.orchestration;

import com.payment.payment_orchestration_system.connector.PaymentProviderConnector;
import com.payment.payment_orchestration_system.dto.request.PaymentRequest;
import com.payment.payment_orchestration_system.dto.request.ProviderPaymentRequest;
import com.payment.payment_orchestration_system.dto.response.PaymentResponse;
import com.payment.payment_orchestration_system.dto.response.ProviderPaymentResponse;
import com.payment.payment_orchestration_system.entity.PaymentTransaction;
import com.payment.payment_orchestration_system.enums.PaymentProvider;
import com.payment.payment_orchestration_system.enums.PaymentStatus;
import com.payment.payment_orchestration_system.exception.LockAcquisitionException;
import com.payment.payment_orchestration_system.exception.ProviderException;
import com.payment.payment_orchestration_system.factory.PaymentProviderFactory;
import com.payment.payment_orchestration_system.mapper.PaymentMapper;
import com.payment.payment_orchestration_system.repository.PaymentTransactionRepository;
import com.payment.payment_orchestration_system.routing.RoutingEngine;
import com.payment.payment_orchestration_system.routing.RoutingStrategy;
import com.payment.payment_orchestration_system.service.idempotency.IdempotencyService;
import com.payment.payment_orchestration_system.service.lock.DistributedLockService;
import com.payment.payment_orchestration_system.service.payment.PaymentAttemptService;
import com.payment.payment_orchestration_system.service.providers.ProviderExecutorService;
import com.payment.payment_orchestration_system.service.retry.RetryService;
import com.payment.payment_orchestration_system.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
// Manages the PaymentOrchestrationService component logic.
public class PaymentOrchestrationService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final RoutingEngine routingEngine;
    private final RetryService retryService;
    private final PaymentProviderFactory paymentProviderFactory;
    private final ProviderExecutorService providerExecutorService;
    private final PaymentMapper paymentMapper;
    private final PaymentAttemptService paymentAttemptService;
    private final IdempotencyService idempotencyService;
    private final DistributedLockService distributedLockService;

    /**
     * Full payment orchestration flow:
     * <ol>
     *   <li>Fast-path idempotency check (Redis L1 → PostgreSQL L2) — before lock.</li>
     *   <li>Acquire distributed lock to serialise concurrent requests for the same key.</li>
     *   <li>Double-check idempotency after lock (race-condition guard).</li>
     *   <li>Persist {@code PaymentTransaction} and cache in Redis.</li>
     *   <li>Execute provider flow: primary → retry → failover to secondary.</li>
     * </ol>
     * Throws {@link com.payment.payment_orchestration_system.exception.DuplicateRequestException}
     * (HTTP 409) if the idempotency key was already used.
     */
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {

        // Pre-lock duplicate check: Redis → PostgreSQL (throws DuplicateRequestException if found)
        idempotencyService.checkDuplicate(request.merchantId(), request.idempotencyKey());

        String lockKey = "lock:" + request.merchantId() + ":" + request.idempotencyKey();
        boolean acquired = distributedLockService.acquireLock(lockKey);

        if (!acquired) {
            log.warn("Failed to acquire lock for merchantId={}, idempotencyKey={}",
                    request.merchantId(), request.idempotencyKey());
            throw new LockAcquisitionException("Request already in progress. Please retry after some time.");
        }

        try {
            // Post-lock double-check — eliminates TOCTOU race condition
            idempotencyService.checkDuplicate(request.merchantId(), request.idempotencyKey());

            // Persist transaction — the DB unique constraint is the final safety net
            PaymentTransaction transaction = createTransaction(request);

            // Cache in Redis so subsequent duplicates are caught at L1 without a DB hit
            idempotencyService.cacheTransaction(transaction);

            log.info("Payment initiated: paymentId={}, merchantId={}, idempotencyKey={}",
                    transaction.getPaymentId(), request.merchantId(), request.idempotencyKey());

            executeProviderFlow(request, transaction);

            return paymentMapper.toPaymentResponse(transaction);

        } finally {
            distributedLockService.releaseLock(lockKey);
        }
    }

    /**
     * Routes to primary provider with retry, falls back to secondary provider on exhaustion.
     */
    private void executeProviderFlow(PaymentRequest request, PaymentTransaction transaction) {
        RoutingStrategy strategy = routingEngine.resolve(request);
        PaymentProvider primaryProvider = strategy.getProvider();

        log.info("Routing payment: paymentId={}, primaryProvider={}", transaction.getPaymentId(), primaryProvider);

        try {
            processWithProvider(transaction, primaryProvider, 1);
        } catch (ProviderException primaryEx) {
            if (!primaryEx.isRetryable()) {
                log.error("Non-retryable provider failure: paymentId={}, provider={}, reason={}",
                        transaction.getPaymentId(), primaryProvider, primaryEx.getMessage());
                markFailed(transaction, primaryEx.getMessage());
                return;
            }

            // Retries exhausted on primary — attempt failover to secondary provider
            PaymentProvider fallbackProvider = getFallbackProvider(primaryProvider);
            log.warn("Primary provider exhausted retries, failing over: paymentId={}, primary={}, fallback={}",
                    transaction.getPaymentId(), primaryProvider, fallbackProvider);

            try {
                processWithProvider(transaction, fallbackProvider, 2);
            } catch (ProviderException fallbackEx) {
                log.error("Failover provider also failed: paymentId={}, fallback={}, reason={}",
                        transaction.getPaymentId(), fallbackProvider, fallbackEx.getMessage());
                markFailed(transaction, "Primary and fallback providers failed. Last error: " + fallbackEx.getMessage());
            }
        }
    }

    /**
     * Calls a specific provider (with retry logic), updates the transaction and records the attempt.
     */
    private void processWithProvider(PaymentTransaction transaction, PaymentProvider provider, int attemptNumber) {
        PaymentProviderConnector connector = paymentProviderFactory.getConnector(provider);
        ProviderPaymentRequest providerRequest = buildProviderRequest(transaction);

        // Record which provider was attempted before the call so it's always set
        transaction.setProvider(provider);

        long start = System.currentTimeMillis();
        ProviderPaymentResponse response = retryService.execute(
                () -> providerExecutorService.execute(connector, providerRequest)
        );
        long latency = System.currentTimeMillis() - start;

        transaction.setStatus(response.status());
        transaction.setProviderTransactionId(response.providerTransactionId());
        paymentTransactionRepository.save(transaction);

        paymentAttemptService.createAttempt(
                transaction, provider, attemptNumber,
                response.status(), response.errorCode(), response.errorMessage(), latency
        );

        log.info("Provider call succeeded: paymentId={}, provider={}, status={}, latency={}ms",
                transaction.getPaymentId(), provider, response.status(), latency);
    }

    // Handles the markFailed operation.
    private void markFailed(PaymentTransaction transaction, String reason) {
        transaction.setStatus(PaymentStatus.FAILED);
        transaction.setFailureReason(reason);
        paymentTransactionRepository.save(transaction);
        log.warn("Payment marked FAILED: paymentId={}, reason={}", transaction.getPaymentId(), reason);
    }

    // Handles the createTransaction operation.
    private PaymentTransaction createTransaction(PaymentRequest request) {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .paymentId(UUID.randomUUID())
                .merchantId(request.merchantId())
                .idempotencyKey(request.idempotencyKey())
                .amount(MoneyUtil.toMinorUnits(request.amount()))
                .currency(request.currencyCode())
                .paymentMethod(request.paymentMethodType())
                .status(PaymentStatus.PROCESSING)
                .correlationId(UUID.randomUUID().toString())
                .build();
        return paymentTransactionRepository.save(transaction);
    }

    // Handles the buildProviderRequest operation.
    private ProviderPaymentRequest buildProviderRequest(PaymentTransaction transaction) {
        return ProviderPaymentRequest.builder()
                .paymentId(transaction.getPaymentId())
                .amount(transaction.getAmount())
                .currencyCode(transaction.getCurrency())
                .build();
    }

    // Returns the fallbackProvider.
    private PaymentProvider getFallbackProvider(PaymentProvider primary) {
        return primary == PaymentProvider.PROVIDER_A ? PaymentProvider.PROVIDER_B : PaymentProvider.PROVIDER_A;
    }
}

