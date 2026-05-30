package com.payment.payment_orchestration_system.service.retry;

import com.payment.payment_orchestration_system.exception.NonRetryableProviderException;
import com.payment.payment_orchestration_system.exception.RetryableProviderException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Manages the RetryServiceTest component logic.
public class RetryServiceTest {

    private final RetryService retryService = new RetryService();

    @Test
    void shouldSucceedOnFirstAttempt(){
        String result = retryService.execute(() -> "SUCCESS");

        assertEquals("SUCCESS",result);
    }

    @Test
    void shouldRetryAndEventuallySucceed(){
        AtomicInteger counter = new AtomicInteger();

        String result = retryService.execute(() -> {
            if(counter.incrementAndGet() < 3){
                throw new RetryableProviderException(
                        "Temporary Failure"
                );
            }
            return "SUCCESS";
        });

        assertEquals("SUCCESS",result);
        assertEquals(3, counter.get());
    }
    @Test
    void shouldThrowAfterRetryExhaustion() {
        assertThrows( RetryableProviderException.class, () -> retryService.execute(() -> {
            throw new RetryableProviderException( "Always failing" );
        }) );
    }

    @Test
    void shouldNotRetryNonRetryableException() {
        AtomicInteger counter = new AtomicInteger();

        assertThrows( NonRetryableProviderException.class, () -> retryService.execute(() -> {
            counter.incrementAndGet(); throw new NonRetryableProviderException( "Hard failure" );
        }) );
        assertEquals(1, counter.get());
    }
}