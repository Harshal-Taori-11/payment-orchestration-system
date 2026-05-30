package com.payment.payment_orchestration_system.controller;

import com.payment.payment_orchestration_system.dto.request.PaymentRequest;
import com.payment.payment_orchestration_system.dto.response.PaymentResponse;
import com.payment.payment_orchestration_system.dto.response.PaymentStatusResponse;
import com.payment.payment_orchestration_system.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Payments")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
// Manages the PaymentController component logic.
public class PaymentController {

    private final PaymentService paymentService;

    // Handles the createPayment operation.
    @PostMapping
    @Operation(summary = "Create payment")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(@Valid @RequestBody PaymentRequest request ) {
        return paymentService.createPayment(request);
    }

    // Returns the paymentStatus.
    @GetMapping("/{paymentId}")
    public PaymentStatusResponse getPaymentStatus(@PathVariable UUID paymentId ) {
        return paymentService.getPaymentStatus(paymentId);
    }
}
