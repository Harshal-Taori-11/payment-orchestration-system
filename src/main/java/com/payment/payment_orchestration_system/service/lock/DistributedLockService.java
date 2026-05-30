package com.payment.payment_orchestration_system.service.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
// Manages the DistributedLockService component logic.
public class DistributedLockService {
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    // Handles the acquireLock operation.
    public boolean acquireLock(String key){

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "LOCKED",LOCK_TTL);
        boolean success = Boolean.TRUE.equals(acquired);

        log.info("Lock acquire key={} success={}",key ,success);
        return success;
    }

    // Handles the releaseLock operation.
    public void releaseLock(String key) {
        redisTemplate.delete(key);
        log.info( "Lock released key={}", key );
    }
}
