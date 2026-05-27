package com.interview.homeshoptraffic.traffic;

public record RateLimitDecision(
    boolean allowed,
    int remainingTokens
) {
}
