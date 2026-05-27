package com.interview.homeshoptraffic.traffic;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "traffic")
public record TrafficProperties(
    RateLimit rateLimit,
    WaitingRoom waitingRoom,
    OrderQueue orderQueue
) {
    public record RateLimit(
        int capacity,
        double refillPerSecond
    ) {
    }

    public record WaitingRoom(
        int tokenTtlSeconds
    ) {
    }

    public record OrderQueue(
        int capacity,
        int workers,
        long processorDelayMillis
    ) {
    }
}
