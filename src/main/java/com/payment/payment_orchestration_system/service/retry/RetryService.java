package com.payment.payment_orchestration_system.service.retry;

import com.payment.payment_orchestration_system.exception.ProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Slf4j
@Service
// Manages the RetryService component logic.
public class RetryService {

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 500;

    // Handles the execute operation.
    public <T> T execute(Supplier<T> operation){
        int attempt = 0;

        while(true){
            try {
                return operation.get();
            }
            catch (ProviderException e){
                attempt++;

                if(!e.isRetryable()){
                    log.error("Non-Retryable Exception encountered");
                    throw e;
                }

                if(attempt >= MAX_RETRIES){
                    log.error("Retry exhausted after {} attempts", attempt);
                    throw e;
                }

                long backOff = INITIAL_BACKOFF_MS * (1L << attempt); // exponential: 500ms, 1000ms, 2000ms
                log.warn("Retrying operation attempt={} backOffMs={}ms ", attempt, backOff);
                sleep(backOff);
            }
        }
    }

    // Handles the sleep operation.
    private void sleep(long millis){
        try{
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new RuntimeException("Retry Interrupted");
        }
    }
}
