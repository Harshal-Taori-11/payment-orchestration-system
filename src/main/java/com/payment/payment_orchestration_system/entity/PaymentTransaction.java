package com.payment.payment_orchestration_system.entity;

import com.payment.payment_orchestration_system.enums.CurrencyCode;
import com.payment.payment_orchestration_system.enums.PaymentMethodType;
import com.payment.payment_orchestration_system.enums.PaymentProvider;
import com.payment.payment_orchestration_system.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "payment_transactions",
        indexes = {
                @Index(name = "idx_payment_transactions_payment_id", columnList = "payment_id"),
                @Index(name = "idx_payment_transactions_merchant_idempotency", columnList = "merchant_id,idempotency_key")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_transactions_merchant_idempotency",
                        columnNames = {"merchant_id", "idempotency_key"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Manages the PaymentTransaction component logic.
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "payment_id",nullable = false, unique = true,  updatable = false)
    private UUID paymentId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private Long merchantId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 64)
    private String idempotencyKey;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethodType paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentProvider provider;

    @Column(name = "provider_transaction_id")
    private String providerTransactionId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version private
    Long version;

    @Builder.Default
    @OneToMany(
            mappedBy = "paymentTransaction",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PaymentAttempt> attempts = new ArrayList<>();

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private String correlationId;

    @Column(name = "created_at",nullable = false,updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at",nullable = false)
    private LocalDateTime updatedAt;

    // Handles the onCreate operation.
    @PrePersist
    public void onCreate(){
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if(this.paymentId == null){
            this.paymentId = UUID.randomUUID();
        }
    }

    // Handles the onUpdate operation.
    @PreUpdate
    public void onUpdate(){
        this.updatedAt = LocalDateTime.now();
    }
}
