package com.interview.homeshoptraffic.traffic;

public interface RequestRateLimiter {

    RateLimitDecision tryConsume(String key);
}
