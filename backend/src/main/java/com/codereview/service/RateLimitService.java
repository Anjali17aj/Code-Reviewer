package com.codereview.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis-based rate limiting service using a sliding window approach.
 * Uses Redis sorted sets (ZSET) where scores are timestamps.
 * This provides accurate rate limiting without the burst issues of fixed windows.
 */
@Slf4j
@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:";

    @Value("${ratelimit.max-reviews-per-day:50}")
    private int maxReviewsPerDay;

    @Value("${ratelimit.window-hours:24}")
    private int windowHours;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check if a request is allowed for the given user.
     * Uses a sliding window algorithm:
     * 1. Remove all entries older than the window
     * 2. Count remaining entries
     * 3. If under limit, add new entry
     *
     * @param userId the user's ID
     * @return true if the request is allowed, false if rate limited
     */
    public boolean isAllowed(Long userId) {
        return isAllowed(userId, maxReviewsPerDay, windowHours);
    }

    /**
     * Check if a request is allowed for a string-based key (e.g., IP address).
     * Used for IP-based rate limiting on auth endpoints.
     *
     * @param key           the rate limit key (e.g., "login:192.168.1.1")
     * @param maxRequests   maximum requests allowed in the window
     * @param windowMinutes the time window in minutes
     * @return true if the request is allowed, false if rate limited
     */
    public boolean isIpAllowed(String key, int maxRequests, int windowMinutes) {
        String redisKey = "ratelimit:ip:" + key;

        try {
            long now = System.currentTimeMillis();
            long windowStart = now - (windowMinutes * 60L * 1000L);

            String luaScript = """
                -- Remove entries outside the window
                redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1])
                
                -- Count current entries in window
                local count = redis.call('ZCARD', KEYS[1])
                
                -- Check if under limit
                if count < tonumber(ARGV[2]) then
                    -- Add new entry with current timestamp as score
                    redis.call('ZADD', KEYS[1], ARGV[3], ARGV[3])
                    -- Set expiry on the key (window + 1 minute buffer)
                    redis.call('EXPIRE', KEYS[1], ARGV[4])
                    return {count + 1, 1}
                else
                    return {count, 0}
                end
                """.trim();

            Long windowMillis = windowMinutes * 60L * 1000L;
            Long expirySeconds = windowMinutes * 60L + 60L; // window + 1min buffer

            var result = redisTemplate.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(luaScript, java.util.List.class),
                    java.util.List.of(redisKey),
                    String.valueOf(windowStart),
                    String.valueOf(maxRequests),
                    String.valueOf(now),
                    String.valueOf(expirySeconds)
            );

            if (result == null || result.isEmpty()) {
                log.warn("Redis Lua script returned null for key={}, allowing request as fail-open", key);
                return true;
            }

            boolean allowed = ((Number) result.get(1)).longValue() == 1;

            if (!allowed) {
                log.warn("Rate limit exceeded for key={}: {}/{} requests",
                        key, result.get(0), maxRequests);
            }

            return allowed;

        } catch (Exception e) {
            log.error("Rate limit check failed for key={}, allowing request: {}",
                    key, e.getMessage());
            return true; // Fail open
        }
    }

    /**
     * Check if a request is allowed with custom limits.
     *
     * @param userId       the user's ID
     * @param maxRequests  maximum requests allowed in the window
     * @param windowHours  the time window in hours
     * @return true if the request is allowed, false if rate limited
     */
    public boolean isAllowed(Long userId, int maxRequests, int windowHours) {
        String key = buildKey(userId);

        try {
            long now = System.currentTimeMillis();
            long windowStart = now - (windowHours * 3600L * 1000L);

            // Use a Lua script for atomic sliding window check + insert
            String luaScript = """
                -- Remove entries outside the window
                redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1])
                
                -- Count current entries in window
                local count = redis.call('ZCARD', KEYS[1])
                
                -- Check if under limit
                if count < tonumber(ARGV[2]) then
                    -- Add new entry with current timestamp as score
                    redis.call('ZADD', KEYS[1], ARGV[3], ARGV[3])
                    -- Set expiry on the key (window + 1 hour buffer)
                    redis.call('EXPIRE', KEYS[1], ARGV[4])
                    return {count + 1, 1}
                else
                    -- Get the oldest entry to calculate retry-after
                    local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
                    local retryAfter = 0
                    if #oldest > 0 then
                        local oldestTime = tonumber(oldest[2])
                        retryAfter = math.ceil((oldestTime + tonumber(ARGV[5]) - now) / 1000)
                    end
                    return {count, 0, retryAfter}
                end
                """.trim();

            Long windowMillis = windowHours * 3600L * 1000L;
            Long expirySeconds = windowHours * 3600L + 3600L; // window + 1h buffer

            var result = redisTemplate.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(luaScript, java.util.List.class),
                    java.util.List.of(key),
                    String.valueOf(windowStart),
                    String.valueOf(maxRequests),
                    String.valueOf(now),
                    String.valueOf(expirySeconds),
                    String.valueOf(windowMillis)
            );

            if (result == null || result.isEmpty()) {
                log.warn("Redis Lua script returned null for user={}, allowing request as fail-open", userId);
                return true;
            }

            long currentCount = ((Number) result.get(0)).longValue();
            boolean allowed = ((Number) result.get(1)).longValue() == 1;

            if (!allowed) {
                long retryAfter = result.size() > 2 ? ((Number) result.get(2)).longValue() : 0;
                log.warn("Rate limit exceeded for user={}: {}/{} requests. Retry after {} seconds",
                        userId, currentCount, maxRequests, retryAfter);
            } else {
                log.debug("Rate limit check for user={}: {}/{} requests (allowed)",
                        userId, currentCount, maxRequests);
            }

            return allowed;

        } catch (Exception e) {
            // If Redis is down, allow the request (fail open)
            log.error("Rate limit check failed for user={}, allowing request: {}",
                    userId, e.getMessage());
            return true;
        }
    }

    /**
     * Get the remaining requests for a user within the current window.
     *
     * @param userId the user's ID
     * @return number of remaining requests (0 if rate limited)
     */
    public long getRemainingRequests(Long userId) {
        return getRemainingRequests(userId, maxReviewsPerDay);
    }

    /**
     * Get the remaining requests for a user with custom limit.
     *
     * @param userId      the user's ID
     * @param maxRequests maximum requests allowed in the window
     * @return number of remaining requests (0 if rate limited)
     */
    public long getRemainingRequests(Long userId, int maxRequests) {
        String key = buildKey(userId);

        try {
            long now = System.currentTimeMillis();
            long windowStart = now - (windowHours * 3600L * 1000L);

            // Remove expired entries and count
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
            Long count = redisTemplate.opsForZSet().zCard(key);

            long currentCount = count != null ? count : 0;
            long remaining = Math.max(0, maxRequests - currentCount);

            log.debug("Remaining requests for user={}: {}", userId, remaining);
            return remaining;

        } catch (Exception e) {
            log.error("Failed to get remaining requests for user={}: {}", userId, e.getMessage());
            return maxRequests; // Fail open
        }
    }

    /**
     * Reset the rate limit for a user (e.g., for admin override).
     *
     * @param userId the user's ID
     */
    public void resetLimit(Long userId) {
        String key = buildKey(userId);
        try {
            redisTemplate.delete(key);
            log.info("Rate limit reset for user={}", userId);
        } catch (Exception e) {
            log.error("Failed to reset rate limit for user={}: {}", userId, e.getMessage());
        }
    }

    /**
     * Get the time remaining until the oldest entry in the window expires.
     * This is the retry-after time in seconds.
     *
     * @param userId the user's ID
     * @return remaining TTL in seconds, or 0 if no limit active
     */
    public long getTimeUntilReset(Long userId) {
        String key = buildKey(userId);
        try {
            // Get the oldest entry in the sorted set
            var oldest = redisTemplate.opsForZSet().rangeWithScores(key, 0, 0);
            if (oldest == null || oldest.isEmpty()) {
                return 0;
            }

            double oldestScore = oldest.iterator().next().getScore();
            long windowMillis = windowHours * 3600L * 1000L;
            long expiresAt = (long) oldestScore + windowMillis;
            long now = System.currentTimeMillis();
            long remaining = Math.max(0, (expiresAt - now) / 1000);

            return remaining;
        } catch (Exception e) {
            log.error("Failed to get TTL for user={}: {}", userId, e.getMessage());
            return 0;
        }
    }

    /**
     * Get the configured max reviews per day.
     */
    public int getMaxReviewsPerDay() {
        return maxReviewsPerDay;
    }

    private String buildKey(Long userId) {
        return RATE_LIMIT_KEY_PREFIX + userId;
    }
}
