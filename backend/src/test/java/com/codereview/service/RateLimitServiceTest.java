package com.codereview.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    @SuppressWarnings("rawtypes")
    private ZSetOperations zSetOperations;

    @InjectMocks
    private RateLimitService rateLimitService;

    private static final Long USER_ID = 1L;
    private static final String RATE_LIMIT_KEY = "ratelimit:1";

    @BeforeEach
    void setUp() {
        setField(rateLimitService, "maxReviewsPerDay", 50);
        setField(rateLimitService, "windowHours", 24);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    // The RateLimitService passes 6 String varargs to redisTemplate.execute()
    // Script, List.of(key), windowStart, maxRequests, now, expirySeconds, windowMillis

    @SuppressWarnings("unchecked")
    private void mockLuaScriptReturn(Object returnValue) {
        lenient().when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(returnValue);
    }

    // --- isAllowed tests (sliding window) ---

    @Test
    void isAllowed_UnderLimit_ReturnsTrue() {
        mockLuaScriptReturn(List.of(5L, 1L));

        boolean result = rateLimitService.isAllowed(USER_ID);

        assertTrue(result);
    }

    @Test
    void isAllowed_AtLimit_ReturnsFalse() {
        mockLuaScriptReturn(List.of(50L, 0L, 3600L));

        boolean result = rateLimitService.isAllowed(USER_ID);

        assertFalse(result);
    }

    @Test
    void isAllowed_OverLimit_ReturnsFalse() {
        mockLuaScriptReturn(List.of(55L, 0L, 1800L));

        boolean result = rateLimitService.isAllowed(USER_ID);

        assertFalse(result);
    }

    @Test
    void isAllowed_CustomLimits_RespectsCustomMax() {
        mockLuaScriptReturn(List.of(5L, 1L));

        boolean result = rateLimitService.isAllowed(USER_ID, 5, 1);

        assertTrue(result);
    }

    @Test
    void isAllowed_CustomLimits_DeniesWhenOverMax() {
        mockLuaScriptReturn(List.of(5L, 0L, 600L));

        boolean result = rateLimitService.isAllowed(USER_ID, 5, 1);

        assertFalse(result);
    }

    @Test
    void isAllowed_RedisException_AllowsRequest() {
        lenient().when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis down"));

        boolean result = rateLimitService.isAllowed(USER_ID);

        assertTrue(result); // Fail open
    }

    @Test
    void isAllowed_NullResult_AllowsRequest() {
        mockLuaScriptReturn(null);

        boolean result = rateLimitService.isAllowed(USER_ID);

        assertTrue(result); // Fail open
    }

    // --- getRemainingRequests tests ---

    @Test
    void getRemainingRequests_NoEntries_ReturnsMax() {
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.zCard(anyString())).thenReturn(0L);

        long remaining = rateLimitService.getRemainingRequests(USER_ID);

        assertEquals(50, remaining);
    }

    @Test
    void getRemainingRequests_PartialUsage_ReturnsRemaining() {
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.zCard(anyString())).thenReturn(20L);

        long remaining = rateLimitService.getRemainingRequests(USER_ID);

        assertEquals(30, remaining);
    }

    @Test
    void getRemainingRequests_FullUsage_ReturnsZero() {
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.zCard(anyString())).thenReturn(50L);

        long remaining = rateLimitService.getRemainingRequests(USER_ID);

        assertEquals(0, remaining);
    }

    @Test
    void getRemainingRequests_RedisException_ReturnsMax() {
        when(redisTemplate.opsForZSet()).thenThrow(new RuntimeException("Redis down"));

        long remaining = rateLimitService.getRemainingRequests(USER_ID);

        assertEquals(50, remaining); // Fail open
    }

    // --- resetLimit tests ---

    @Test
    void resetLimit_DeletesKey() {
        when(redisTemplate.delete(RATE_LIMIT_KEY)).thenReturn(true);

        rateLimitService.resetLimit(USER_ID);

        verify(redisTemplate).delete(RATE_LIMIT_KEY);
    }

    @Test
    void resetLimit_RedisException_DoesNotThrow() {
        when(redisTemplate.delete(RATE_LIMIT_KEY)).thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> rateLimitService.resetLimit(USER_ID));
    }

    // --- getTimeUntilReset tests ---

    @Test
    void getTimeUntilReset_NoEntries_ReturnsZero() {
        when(zSetOperations.rangeWithScores(eq(RATE_LIMIT_KEY), eq(0L), eq(0L))).thenReturn(Set.of());

        long ttl = rateLimitService.getTimeUntilReset(USER_ID);

        assertEquals(0L, ttl);
    }

    @Test
    void getTimeUntilReset_RedisException_ReturnsZero() {
        when(redisTemplate.opsForZSet()).thenThrow(new RuntimeException("Redis down"));

        long ttl = rateLimitService.getTimeUntilReset(USER_ID);

        assertEquals(0L, ttl);
    }

    // --- getMaxReviewsPerDay test ---

    @Test
    void getMaxReviewsPerDay_ReturnsConfiguredValue() {
        assertEquals(50, rateLimitService.getMaxReviewsPerDay());
    }

    // --- Helper ---

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
