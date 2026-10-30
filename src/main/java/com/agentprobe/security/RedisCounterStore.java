package com.agentprobe.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisCounterStore implements CounterStore {

    private final StringRedisTemplate redis;

    public RedisCounterStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration ttl) {
        Long value = redis.opsForValue().increment(key);
        if (value == null) {
            return 0;
        }
        if (value == 1L) {
            redis.expire(key, ttl);
        }
        return value;
    }
}