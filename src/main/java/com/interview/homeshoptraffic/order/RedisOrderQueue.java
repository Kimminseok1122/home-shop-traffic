package com.interview.homeshoptraffic.order;

import com.interview.homeshoptraffic.traffic.TrafficMetrics;
import com.interview.homeshoptraffic.traffic.TrafficProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisOrderQueue implements OrderQueue {

    private static final String KEY = "queue:orders";

    private static final DefaultRedisScript<Long> OFFER_SCRIPT = new DefaultRedisScript<>("""
        local queueKey = KEYS[1]
        local orderId = ARGV[1]
        local capacity = tonumber(ARGV[2])

        if redis.call('LLEN', queueKey) >= capacity then
            return 0
        end

        redis.call('LPUSH', queueKey, orderId)
        return 1
        """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final TrafficProperties properties;
    private final TrafficMetrics metrics;

    public RedisOrderQueue(
        StringRedisTemplate redisTemplate,
        TrafficProperties properties,
        TrafficMetrics metrics
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public boolean offer(String orderId) {
        Long result = redisTemplate.execute(
            OFFER_SCRIPT,
            List.of(KEY),
            orderId,
            String.valueOf(properties.orderQueue().capacity())
        );
        metrics.updateQueueSize(size());
        return result != null && result == 1L;
    }

    @Override
    public String take() throws InterruptedException {
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            String orderId = redisTemplate.opsForList().rightPop(KEY, Duration.ofSeconds(1));
            if (orderId != null) {
                metrics.updateQueueSize(size());
                return orderId;
            }
        }
    }

    @Override
    public int size() {
        Long size = redisTemplate.opsForList().size(KEY);
        return size == null ? 0 : size.intValue();
    }
}
