package com.interview.homeshoptraffic.waiting;

public record WaitingRoomStatus(
    long issuedTickets,
    long activeTickets
) {
}
