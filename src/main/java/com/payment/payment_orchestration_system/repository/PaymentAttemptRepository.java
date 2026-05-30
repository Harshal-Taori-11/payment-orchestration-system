package com.payment.payment_orchestration_system.repository;

import com.payment.payment_orchestration_system.entity.PaymentAttempt;
import com.payment.payment_orchestration_system.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Defines the contract for PaymentAttemptRepository behavior.
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    List<PaymentAttempt> findByPaymentTransaction(PaymentTransaction paymentTransaction);
}
