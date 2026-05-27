package com.interview.homeshoptraffic.waiting;

import java.time.Instant;

public record EnterWaitingRoomResponse(
    String waitingToken,
    long position,
    Instant expiresAt
) {
}
