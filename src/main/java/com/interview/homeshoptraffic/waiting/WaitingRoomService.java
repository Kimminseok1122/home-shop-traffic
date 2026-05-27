package com.interview.homeshoptraffic.waiting;

public interface WaitingRoomService {

    EnterWaitingRoomResponse enter(Long broadcastId, Long userId);

    void validateAndConsume(String token, Long broadcastId, Long userId);

    WaitingRoomStatus status(Long broadcastId);
}
