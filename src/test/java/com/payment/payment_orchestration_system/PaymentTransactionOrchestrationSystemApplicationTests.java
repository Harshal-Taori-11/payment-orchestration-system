package com.payment.payment_orchestration_system;

import com.payment.payment_orchestration_system.service.idempotency.IdempotencyService;
import com.payment.payment_orchestration_system.service.lock.DistributedLockService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
// Manages the PaymentTransactionOrchestrationSystemApplicationTests component logic.
class PaymentTransactionOrchestrationSystemApplicationTests {

	@MockitoBean
	private IdempotencyService idempotencyService;

	@MockitoBean
	private DistributedLockService distributedLockService;

	@Test
	void contextLoads() {
	}

}
