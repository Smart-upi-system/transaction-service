package com.uws.transaction_service.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyManager {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:transaction:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24); // 24 hours

    /**
     * Generate or validate idempotency key
     * If client provides key, use it. Otherwise generate new UUID.
     *
     * @param providedKey Key provided by client (can be null)
     * @return Valid idempotency key
     */
    public String generateOrGetKey(String providedKey) {
        if (providedKey != null && !providedKey.isBlank()) {
            log.debug("Using client-provided idempotency key: {}", providedKey);
            return providedKey.trim();
        }

        String generated = UUID.randomUUID().toString();
        log.debug("Generated new idempotency key: {}", generated);
        return generated;
    }

    /**
     * Check if this idempotency key has already been processed
     *
     * @param idempotencyKey Unique key for this request
     * @return true if request is duplicate (already processed)
     */
    public boolean isDuplicate(String idempotencyKey) {
        String key = buildRedisKey(idempotencyKey);
        Boolean exists = redisTemplate.hasKey(key);

        boolean isDuplicate = Boolean.TRUE.equals(exists);

        if (isDuplicate) {
            log.warn("Duplicate request detected: idempotencyKey={}", idempotencyKey);
        }

        return isDuplicate;
    }

    /**
     * Mark this idempotency key as processed and store transaction ID
     *
     * @param idempotencyKey Unique key for this request
     * @param transactionId Transaction ID to associate with this key
     */
    public void markAsProcessed(String idempotencyKey, String transactionId) {
        String key = buildRedisKey(idempotencyKey);

        redisTemplate.opsForValue().set(key, transactionId, IDEMPOTENCY_TTL);

        log.info("Marked idempotency key as processed: key={}, transactionId={}, ttl={}",
                idempotencyKey, transactionId, IDEMPOTENCY_TTL);
    }

    /**
     * Get transaction ID associated with this idempotency key
     * Used for returning cached response on duplicate requests
     *
     * @param idempotencyKey Unique key for this request
     * @return Transaction ID if key exists, null otherwise
     */
    public String getTransactionId(String idempotencyKey) {
        String key = buildRedisKey(idempotencyKey);
        String transactionId = redisTemplate.opsForValue().get(key);

        if (transactionId != null) {
            log.info("Retrieved cached transaction ID: idempotencyKey={}, transactionId={}",
                    idempotencyKey, transactionId);
        }

        return transactionId;
    }

    /**
     * Remove idempotency key from cache (for testing or manual intervention)
     *
     * @param idempotencyKey Key to remove
     * @return true if key was removed, false if it didn't exist
     */
    public boolean remove(String idempotencyKey) {
        String key = buildRedisKey(idempotencyKey);
        Boolean deleted = redisTemplate.delete(key);

        boolean removed = Boolean.TRUE.equals(deleted);

        if (removed) {
            log.info("Removed idempotency key: {}", idempotencyKey);
        }

        return removed;
    }

    /**
     * Check remaining TTL for an idempotency key
     *
     * @param idempotencyKey Key to check
     * @return Remaining TTL in seconds, -1 if key doesn't exist, -2 if no expiration
     */
    public long getRemainingTTL(String idempotencyKey) {
        String key = buildRedisKey(idempotencyKey);
        Long ttl = redisTemplate.getExpire(key);

        return ttl != null ? ttl : -1;
    }

    /**
     * Build Redis key with prefix
     */
    private String buildRedisKey(String idempotencyKey) {
        return IDEMPOTENCY_PREFIX + idempotencyKey;
    }
}
