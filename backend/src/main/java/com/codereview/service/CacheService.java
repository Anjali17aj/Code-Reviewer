package com.codereview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service for Redis-based caching operations.
 * Provides generic get/set/delete operations with TTL support.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "codereview:";
    private static final long DEFAULT_TTL_HOURS = 24;

    /**
     * Get a cached value by key.
     *
     * @param key the cache key (will be prefixed)
     * @return Optional containing the cached value, or empty if not found
     */
    public Optional<String> get(String key) {
        try {
            String fullKey = KEY_PREFIX + key;
            String value = redisTemplate.opsForValue().get(fullKey);
            log.debug("Cache GET key={}, hit={}", fullKey, value != null);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.warn("Cache GET failed for key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Store a value in cache with default TTL (24 hours).
     *
     * @param key   the cache key (will be prefixed)
     * @param value the value to cache
     */
    public void set(String key, String value) {
        set(key, value, DEFAULT_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * Store a value in cache with specified TTL.
     *
     * @param key      the cache key (will be prefixed)
     * @param value    the value to cache
     * @param ttl      time-to-live value
     * @param timeUnit time unit for TTL
     */
    public void set(String key, String value, long ttl, TimeUnit timeUnit) {
        try {
            String fullKey = KEY_PREFIX + key;
            redisTemplate.opsForValue().set(fullKey, value, ttl, timeUnit);
            log.debug("Cache SET key={}, ttl={} {}", fullKey, ttl, timeUnit);
        } catch (Exception e) {
            log.warn("Cache SET failed for key={}: {}", key, e.getMessage());
        }
    }

    /**
     * Delete a cached value by key.
     *
     * @param key the cache key (will be prefixed)
     */
    public void delete(String key) {
        try {
            String fullKey = KEY_PREFIX + key;
            Boolean deleted = redisTemplate.delete(fullKey);
            log.debug("Cache DELETE key={}, deleted={}", fullKey, deleted);
        } catch (Exception e) {
            log.warn("Cache DELETE failed for key={}: {}", key, e.getMessage());
        }
    }

    /**
     * Check if a key exists in the cache.
     *
     * @param key the cache key (will be prefixed)
     * @return true if key exists
     */
    public boolean exists(String key) {
        try {
            String fullKey = KEY_PREFIX + key;
            Boolean exists = redisTemplate.hasKey(fullKey);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Cache EXISTS check failed for key={}: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * Increment a counter value in cache.
     * Useful for rate limiting and analytics.
     *
     * @param key the cache key (will be prefixed)
     * @return the incremented value, or 0 if operation failed
     */
    public long increment(String key) {
        return increment(key, 1);
    }

    /**
     * Increment a counter value in cache by specified amount.
     *
     * @param key   the cache key (will be prefixed)
     * @param delta the amount to increment by
     * @return the incremented value, or 0 if operation failed
     */
    public long increment(String key, long delta) {
        try {
            String fullKey = KEY_PREFIX + key;
            Long value = redisTemplate.opsForValue().increment(fullKey, delta);
            return value != null ? value : 0;
        } catch (Exception e) {
            log.warn("Cache INCREMENT failed for key={}: {}", key, e.getMessage());
            return 0;
        }
    }

    /**
     * Set expiry on an existing key.
     *
     * @param key      the cache key (will be prefixed)
     * @param ttl      time-to-live value
     * @param timeUnit time unit for TTL
     * @return true if expiry was set
     */
    public boolean expire(String key, long ttl, TimeUnit timeUnit) {
        try {
            String fullKey = KEY_PREFIX + key;
            return Boolean.TRUE.equals(redisTemplate.expire(fullKey, ttl, timeUnit));
        } catch (Exception e) {
            log.warn("Cache EXPIRE failed for key={}: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * Get remaining TTL for a key.
     *
     * @param key the cache key (will be prefixed)
     * @return remaining TTL in seconds, or -1 if key doesn't exist, or -2 on error
     */
    public long getTTL(String key) {
        try {
            String fullKey = KEY_PREFIX + key;
            Long ttl = redisTemplate.getExpire(fullKey);
            return ttl != null ? ttl : -1;
        } catch (Exception e) {
            log.warn("Cache TTL check failed for key={}: {}", key, e.getMessage());
            return -2;
        }
    }
}
