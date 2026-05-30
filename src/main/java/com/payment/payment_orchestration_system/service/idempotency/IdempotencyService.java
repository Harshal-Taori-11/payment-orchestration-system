package com.payment.payment_orchestration_system.service.idempotency;

import com.payment.payment_orchestration_system.entity.PaymentTransaction;
import com.payment.payment_orchestration_system.exception.DuplicateRequestException;
import com.payment.payment_orchestration_system.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Idempotency guard: Redis (L1 cache) → PostgreSQL (L2 source of truth).
 * <p>
 * The {@code PaymentTransaction} table is the canonical idempotency record — it has
 * a unique constraint on {@code (merchant_id, idempotency_key)}.  This service adds
 * a Redis fast-path on top to avoid a DB round-trip on every duplicate check.
 */
@Slf4j
@Service
@RequiredArgsConstructor
// Manages the IdempotencyService component logic.
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;
    private final PaymentTransactionRepository paymentTransactionRepository;

    private static final Duration TTL = Duration.ofHours(24);

    /**
     * Checks whether this (merchantId, idempotencyKey) pair has already been processed.
     * <ol>
     *   <li>L1 – Redis: O(1) lookup, avoids DB hit on repeated duplicates.</li>
     *   <li>L2 – PostgreSQL: authoritative check; back-fills Redis on hit.</li>
     * </ol>
     *
     * @throws DuplicateRequestException if the idempotency key has already been used
     */
    public void checkDuplicate(Long merchantId, String idempotencyKey) {
        String redisKey = buildKey(merchantId, idempotencyKey);

        // L1: Redis
        try {
            String cachedPaymentId = redisTemplate.opsForValue().get(redisKey);
            if (cachedPaymentId != null) {
                log.info("Duplicate detected in Redis cache: merchantId={}, idempotencyKey={}", merchantId, idempotencyKey);
                throw new DuplicateRequestException(
                        String.format("Idempotency key '%s' already used. PaymentId: %s", idempotencyKey, cachedPaymentId)
                );
            }
        } catch (DuplicateRequestException e) {
            throw e;
        } catch (Exception e) {
            // Redis failure should not block the payment flow — fall through to DB check
            log.error("Redis read failed for key={}, falling through to DB", redisKey, e);
        }

        // L2: PostgreSQL
        Optional<PaymentTransaction> existing =
                paymentTransactionRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);

        if (existing.isPresent()) {
            PaymentTransaction tx = existing.get();
            log.info("Duplicate detected in DB: merchantId={}, idempotencyKey={}, paymentId={}, status={}",
                    merchantId, idempotencyKey, tx.getPaymentId(), tx.getStatus());

            // Back-fill Redis so future duplicates are caught at L1
            try {
                redisTemplate.opsForValue().set(redisKey, tx.getPaymentId().toString(), TTL);
            } catch (Exception e) {
                log.warn("Redis back-fill failed for key={}", redisKey, e);
            }

            throw new DuplicateRequestException(
                    String.format("Idempotency key '%s' already used. PaymentId: %s, Status: %s",
                            idempotencyKey, tx.getPaymentId(), tx.getStatus())
            );
        }
    }

    /**
     * Caches the newly created transaction in Redis so subsequent duplicates are caught at L1.
     * Call this right after the {@code PaymentTransaction} is persisted.
     */
    public void cacheTransaction(PaymentTransaction transaction) {
        try {
            redisTemplate.opsForValue().set(
                    buildKey(transaction.getMerchantId(), transaction.getIdempotencyKey()),
                    transaction.getPaymentId().toString(),
                    TTL
            );
            log.debug("Cached transaction in Redis: paymentId={}", transaction.getPaymentId());
        } catch (Exception e) {
            log.error("Redis cache write failed for paymentId={}", transaction.getPaymentId(), e);
        }
    }

    // Handles the buildKey operation.
    private String buildKey(Long merchantId, String idempotencyKey) {
        return "idempotency:" + merchantId + ":" + idempotencyKey;
    }
}

