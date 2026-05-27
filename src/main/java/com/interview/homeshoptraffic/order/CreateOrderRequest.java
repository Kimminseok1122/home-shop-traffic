package com.interview.homeshoptraffic.order;

public record CreateOrderRequest(
    Long broadcastId,
    Long productId,
    Long userId,
    int quantity
) {
}
