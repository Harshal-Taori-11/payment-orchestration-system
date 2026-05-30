package com.payment.payment_orchestration_system.service.providers;

import com.payment.payment_orchestration_system.connector.PaymentProviderConnector;
import com.payment.payment_orchestration_system.dto.request.ProviderPaymentRequest;
import com.payment.payment_orchestration_system.dto.response.ProviderPaymentResponse;
import com.payment.payment_orchestration_system.exception.RetryableProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
@Slf4j
// Manages the ProviderExecutorService component logic.
public class ProviderExecutorService {

    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    // Handles the execute operation.
    public ProviderPaymentResponse execute(PaymentProviderConnector connector, ProviderPaymentRequest request){
        long start = System.currentTimeMillis();

        try{
            Future<ProviderPaymentResponse> future = executorService.submit(() -> connector.processPayment(request));
            ProviderPaymentResponse response = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
            long latency = System.currentTimeMillis() - start;

            log.info("Provider execution successful provider={} and latency={}",connector.getProvider(), latency);
            return response;
        }
        catch (java.util.concurrent.TimeoutException ex) {
            throw new RetryableProviderException("Provider " + connector.getProvider() + " timed out after 10s");
        }
        catch (ExecutionException ex){
            Throwable cause = ex.getCause();
            if(cause instanceof RuntimeException runtimeException){
                throw runtimeException;
            }
            throw new RuntimeException(cause);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Provider execution interrupted");
        }
    }
}
