package com.interview.homeshoptraffic.traffic;

import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisRateLimiter implements RequestRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final TrafficProperties properties;

    public RedisRateLimiter(StringRedisTemplate redisTemplate, TrafficProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public RateLimitDecision tryConsume(String key) {
        int limit = properties.rateLimit().capacity();
        long currentSecond = Instant.now().getEpochSecond();
        String redisKey = "rate:order:" + key + ":" + currentSecond;

        Long used = redisTemplate.opsForValue().increment(redisKey);
        if (used != null && used == 1L) {
            redisTemplate.expire(redisKey, Duration.ofSeconds(2));
        }

        int usedCount = used == null ? limit : used.intValue();
        int remaining = Math.max(0, limit - usedCount);

        return new RateLimitDecision(usedCount <= limit, remaining);
    }
}
