package com.payment.payment_orchestration_system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.payment_orchestration_system.dto.request.PaymentRequest;
import com.payment.payment_orchestration_system.dto.response.PaymentResponse;
import com.payment.payment_orchestration_system.dto.response.PaymentStatusResponse;
import com.payment.payment_orchestration_system.entity.PaymentTransaction;
import com.payment.payment_orchestration_system.enums.CurrencyCode;
import com.payment.payment_orchestration_system.enums.PaymentMethodType;
import com.payment.payment_orchestration_system.enums.PaymentProvider;
import com.payment.payment_orchestration_system.enums.PaymentStatus;
import com.payment.payment_orchestration_system.exception.DuplicateRequestException;
import com.payment.payment_orchestration_system.repository.PaymentTransactionRepository;
import com.payment.payment_orchestration_system.service.idempotency.IdempotencyService;
import com.payment.payment_orchestration_system.service.lock.DistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
// Manages the PaymentIntegrationTest component logic.
class PaymentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;

    @MockitoBean private IdempotencyService idempotencyService;
    @MockitoBean private DistributedLockService distributedLockService;

    @BeforeEach
    void setUp() {
        when(distributedLockService.acquireLock(any())).thenReturn(true);
        doNothing().when(idempotencyService).checkDuplicate(anyLong(), anyString());
        doNothing().when(idempotencyService).cacheTransaction(any());
    }

    // ─── POSITIVE SCENARIOS ────────────────────────────────────────────────

    @Test
    void shouldCreateCardPaymentSuccessfully() throws Exception {
        PaymentRequest request = new PaymentRequest(1L, "ORD-CARD-001", new BigDecimal("5000.00"), CurrencyCode.INR, PaymentMethodType.CARD);

        MvcResult result = mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        PaymentResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentResponse.class);
        assertNotNull(response.paymentId());
        assertNotNull(response.status());
        assertEquals(CurrencyCode.INR, response.currency());
        assertEquals(PaymentMethodType.CARD, response.paymentMethod());
        assertEquals("ORD-CARD-001", response.idempotencyKey());
        assertEquals(new BigDecimal("5000.00"), response.amount());
        assertNotNull(response.message());
        // CARD must route to PROVIDER_A
        assertEquals(PaymentProvider.PROVIDER_A, response.provider());
    }

    @Test
    void shouldCreateUpiPaymentSuccessfully() throws Exception {
        PaymentRequest request = new PaymentRequest(1L, "ORD-UPI-001", new BigDecimal("250.00"), CurrencyCode.USD, PaymentMethodType.UPI);

        MvcResult result = mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        PaymentResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), PaymentResponse.class);
        assertNotNull(response.paymentId());
        assertEquals(PaymentMethodType.UPI, response.paymentMethod());
        assertEquals(new BigDecimal("250.00"), response.amount());
        // UPI must route to PROVIDER_B
        assertEquals(PaymentProvider.PROVIDER_B, response.provider());
    }

    @Test
    void shouldFetchPaymentStatusById() throws Exception {
        // First create a payment so we have a real paymentId in H2
        PaymentRequest request = new PaymentRequest(1L, "ORD-STATUS-001", new BigDecimal("1000.00"), CurrencyCode.INR, PaymentMethodType.CARD);

        MvcResult createResult = mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        PaymentResponse created = objectMapper.readValue(createResult.getResponse().getContentAsString(), PaymentResponse.class);
        UUID paymentId = created.paymentId();

        // Now fetch it
        MvcResult statusResult = mockMvc.perform(get("/api/v1/payments/" + paymentId))
                .andExpect(status().isOk())
                .andReturn();

        PaymentStatusResponse status = objectMapper.readValue(statusResult.getResponse().getContentAsString(), PaymentStatusResponse.class);
        assertEquals(paymentId, status.paymentId());
        assertNotNull(status.status());
        assertNotNull(status.createdAt());
        assertNotNull(status.updatedAt());
        assertEquals(CurrencyCode.INR, status.currency());
        assertEquals(PaymentMethodType.CARD, status.paymentMethod());
        assertEquals("ORD-STATUS-001", status.idempotencyKey());
    }

    // ─── NEGATIVE SCENARIOS ────────────────────────────────────────────────

    @Test
    void shouldReturn409ForDuplicateIdempotencyKey() throws Exception {
        PaymentRequest request = new PaymentRequest(2L, "ORD-DUP-001", new BigDecimal("5000.00"), CurrencyCode.INR, PaymentMethodType.CARD);

        doThrow(new DuplicateRequestException("Idempotency key 'ORD-DUP-001' already used."))
                .when(idempotencyService).checkDuplicate(2L, "ORD-DUP-001");

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn400WhenAmountIsMissing() throws Exception {
        // amount is null — @Positive @NotNull should reject
        String json = "{\"merchantId\":1,\"idempotencyKey\":\"ORD-NO-AMT\",\"currencyCode\":\"INR\",\"paymentMethodType\":\"CARD\"}";

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenMerchantIdIsNull() throws Exception {
        String json = "{\"idempotencyKey\":\"ORD-NO-MERCHANT\",\"amount\":100.00,\"currencyCode\":\"INR\",\"paymentMethodType\":\"CARD\"}";

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenIdempotencyKeyExceedsMaxLength() throws Exception {
        String longKey = "K".repeat(65); // max is 64
        PaymentRequest request = new PaymentRequest(1L, longKey, new BigDecimal("100.00"), CurrencyCode.INR, PaymentMethodType.CARD);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenAmountIsNegative() throws Exception {
        PaymentRequest request = new PaymentRequest(1L, "ORD-NEG-001", new BigDecimal("-50.00"), CurrencyCode.INR, PaymentMethodType.CARD);

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404ForNonExistentPaymentId() throws Exception {
        UUID nonExistent = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/payments/" + nonExistent))
                .andExpect(status().isNotFound());
    }
}
