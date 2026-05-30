package com.payment.payment_orchestration_system.repository;

import com.payment.payment_orchestration_system.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// Defines the contract for PaymentTransactionRepository behavior.
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByPaymentId(UUID paymentId);

    Optional<PaymentTransaction> findByMerchantIdAndIdempotencyKey(Long merchantId, String idempotencyKey);
}
