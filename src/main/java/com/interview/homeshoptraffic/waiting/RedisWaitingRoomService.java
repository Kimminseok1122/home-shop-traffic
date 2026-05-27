package com.interview.homeshoptraffic.waiting;

import com.interview.homeshoptraffic.common.BusinessException;
import com.interview.homeshoptraffic.traffic.TrafficMetrics;
import com.interview.homeshoptraffic.traffic.TrafficProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RedisWaitingRoomService implements WaitingRoomService {

    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
        local tokenKey = KEYS[1]
        local tokenSetKey = KEYS[2]
        local token = ARGV[1]
        local broadcastId = ARGV[2]
        local userId = ARGV[3]

        if redis.call('EXISTS', tokenKey) == 0 then
            return 0
        end

        if redis.call('HGET', tokenKey, 'broadcastId') ~= broadcastId then
            return -1
        end

        if redis.call('HGET', tokenKey, 'userId') ~= userId then
            return -2
        end

        redis.call('DEL', tokenKey)
        redis.call('ZREM', tokenSetKey, token)

        return 1
        """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final TrafficProperties properties;
    private final TrafficMetrics metrics;

    public RedisWaitingRoomService(
        StringRedisTemplate redisTemplate,
        TrafficProperties properties,
        TrafficMetrics metrics
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public EnterWaitingRoomResponse enter(Long broadcastId, Long userId) {
        if (broadcastId == null || userId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "broadcastId and userId are required");
        }

        String seqKey = seqKey(broadcastId);
        String tokenSetKey = tokenSetKey(broadcastId);

        Long position = redisTemplate.opsForValue().increment(seqKey);
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(properties.waitingRoom().tokenTtlSeconds());
        String tokenKey = tokenKey(token);

        redisTemplate.opsForHash().putAll(tokenKey, Map.of(
            "broadcastId", String.valueOf(broadcastId),
            "userId", String.valueOf(userId),
            "position", String.valueOf(position),
            "expiresAt", String.valueOf(expiresAt.toEpochMilli())
        ));
        redisTemplate.expire(tokenKey, Duration.ofSeconds(properties.waitingRoom().tokenTtlSeconds()));
        redisTemplate.opsForZSet().add(tokenSetKey, token, expiresAt.toEpochMilli());

        metrics.ticketIssued();

        return new EnterWaitingRoomResponse(token, position == null ? 0 : position, expiresAt);
    }

    @Override
    public void validateAndConsume(String token, Long broadcastId, Long userId) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "X-Waiting-Token header is required");
        }

        Long result = redisTemplate.execute(
            CONSUME_SCRIPT,
            List.of(tokenKey(token), tokenSetKey(broadcastId)),
            token,
            String.valueOf(broadcastId),
            String.valueOf(userId)
        );

        if (result == null || result == 0) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid or expired waiting token");
        }
        if (result == -1 || result == -2) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Waiting token owner does not match request");
        }
    }

    @Override
    public WaitingRoomStatus status(Long broadcastId) {
        String seqKey = seqKey(broadcastId);
        String tokenSetKey = tokenSetKey(broadcastId);

        redisTemplate.opsForZSet().removeRangeByScore(tokenSetKey, 0, Instant.now().toEpochMilli());

        String issued = redisTemplate.opsForValue().get(seqKey);
        Long active = redisTemplate.opsForZSet().size(tokenSetKey);

        return new WaitingRoomStatus(
            issued == null ? 0 : Long.parseLong(issued),
            active == null ? 0 : active
        );
    }

    private String seqKey(Long broadcastId) {
        return "waiting:live:" + broadcastId + ":seq";
    }

    private String tokenSetKey(Long broadcastId) {
        return "waiting:live:" + broadcastId + ":tokens";
    }

    private String tokenKey(String token) {
        return "waiting:token:" + token;
    }
}
