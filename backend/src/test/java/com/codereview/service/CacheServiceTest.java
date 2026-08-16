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
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CacheService cacheService;

    private static final String TEST_KEY = "test-key";
    private static final String FULL_KEY = "codereview:test-key";
    private static final String TEST_VALUE = "test-value";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // --- get tests ---

    @Test
    void get_CacheHit_ReturnsValue() {
        when(valueOperations.get(FULL_KEY)).thenReturn(TEST_VALUE);

        Optional<String> result = cacheService.get(TEST_KEY);

        assertTrue(result.isPresent());
        assertEquals(TEST_VALUE, result.get());
        verify(valueOperations).get(FULL_KEY);
    }

    @Test
    void get_CacheHit_ReturnsEmpty() {
        when(valueOperations.get(FULL_KEY)).thenReturn(null);

        Optional<String> result = cacheService.get(TEST_KEY);

        assertFalse(result.isPresent());
    }

    @Test
    void get_RedisException_ReturnsEmpty() {
        when(valueOperations.get(FULL_KEY)).thenThrow(new RuntimeException("Redis down"));

        Optional<String> result = cacheService.get(TEST_KEY);

        assertFalse(result.isPresent());
    }

    // --- set tests ---

    @Test
    void set_WithDefaultTTL_StoresValue() {
        cacheService.set(TEST_KEY, TEST_VALUE);

        verify(valueOperations).set(FULL_KEY, TEST_VALUE, 24, TimeUnit.HOURS);
    }

    @Test
    void set_WithCustomTTL_StoresValue() {
        cacheService.set(TEST_KEY, TEST_VALUE, 30, TimeUnit.MINUTES);

        verify(valueOperations).set(FULL_KEY, TEST_VALUE, 30, TimeUnit.MINUTES);
    }

    @Test
    void set_RedisException_DoesNotThrow() {
        doThrow(new RuntimeException("Redis down")).when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        assertDoesNotThrow(() -> cacheService.set(TEST_KEY, TEST_VALUE));
    }

    // --- delete tests ---

    @Test
    void delete_ExistingKey_DeletesSuccessfully() {
        when(redisTemplate.delete(FULL_KEY)).thenReturn(true);

        cacheService.delete(TEST_KEY);

        verify(redisTemplate).delete(FULL_KEY);
    }

    @Test
    void delete_NonExistentKey_DoesNotThrow() {
        when(redisTemplate.delete(FULL_KEY)).thenReturn(false);

        assertDoesNotThrow(() -> cacheService.delete(TEST_KEY));
    }

    @Test
    void delete_RedisException_DoesNotThrow() {
        when(redisTemplate.delete(FULL_KEY)).thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> cacheService.delete(TEST_KEY));
    }

    // --- exists tests ---

    @Test
    void exists_KeyExists_ReturnsTrue() {
        when(redisTemplate.hasKey(FULL_KEY)).thenReturn(true);

        assertTrue(cacheService.exists(TEST_KEY));
    }

    @Test
    void exists_KeyNotExists_ReturnsFalse() {
        when(redisTemplate.hasKey(FULL_KEY)).thenReturn(false);

        assertFalse(cacheService.exists(TEST_KEY));
    }

    @Test
    void exists_RedisException_ReturnsFalse() {
        when(redisTemplate.hasKey(FULL_KEY)).thenThrow(new RuntimeException("Redis down"));

        assertFalse(cacheService.exists(TEST_KEY));
    }

    // --- increment tests ---

    @Test
    void increment_DefaultDelta_IncrementsByOne() {
        when(valueOperations.increment(FULL_KEY, 1)).thenReturn(1L);

        long result = cacheService.increment(TEST_KEY);

        assertEquals(1L, result);
        verify(valueOperations).increment(FULL_KEY, 1);
    }

    @Test
    void increment_CustomDelta_IncrementsByDelta() {
        when(valueOperations.increment(FULL_KEY, 5)).thenReturn(5L);

        long result = cacheService.increment(TEST_KEY, 5);

        assertEquals(5L, result);
        verify(valueOperations).increment(FULL_KEY, 5);
    }

    @Test
    void increment_RedisException_ReturnsZero() {
        when(valueOperations.increment(FULL_KEY, 1)).thenThrow(new RuntimeException("Redis down"));

        long result = cacheService.increment(TEST_KEY);

        assertEquals(0L, result);
    }

    // --- expire tests ---

    @Test
    void expire_ValidKey_ReturnsTrue() {
        when(redisTemplate.expire(FULL_KEY, 1, TimeUnit.HOURS)).thenReturn(true);

        assertTrue(cacheService.expire(TEST_KEY, 1, TimeUnit.HOURS));
    }

    @Test
    void expire_InvalidKey_ReturnsFalse() {
        when(redisTemplate.expire(FULL_KEY, 1, TimeUnit.HOURS)).thenReturn(false);

        assertFalse(cacheService.expire(TEST_KEY, 1, TimeUnit.HOURS));
    }

    @Test
    void expire_RedisException_ReturnsFalse() {
        when(redisTemplate.expire(FULL_KEY, 1, TimeUnit.HOURS)).thenThrow(new RuntimeException("Redis down"));

        assertFalse(cacheService.expire(TEST_KEY, 1, TimeUnit.HOURS));
    }

    // --- getTTL tests ---

    @Test
    void getTTL_KeyExists_ReturnsTTL() {
        when(redisTemplate.getExpire(FULL_KEY)).thenReturn(3600L);

        long result = cacheService.getTTL(TEST_KEY);

        assertEquals(3600L, result);
    }

    @Test
    void getTTL_KeyNotExists_ReturnsNegativeOne() {
        when(redisTemplate.getExpire(FULL_KEY)).thenReturn(-1L);

        long result = cacheService.getTTL(TEST_KEY);

        assertEquals(-1L, result);
    }

    @Test
    void getTTL_RedisException_ReturnsNegativeTwo() {
        when(redisTemplate.getExpire(FULL_KEY)).thenThrow(new RuntimeException("Redis down"));

        long result = cacheService.getTTL(TEST_KEY);

        assertEquals(-2L, result);
    }
}
