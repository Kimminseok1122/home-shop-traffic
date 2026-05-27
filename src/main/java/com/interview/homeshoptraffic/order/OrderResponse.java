package com.interview.homeshoptraffic.order;

import java.time.Instant;

public record OrderResponse(
    String orderId,
    Long broadcastId,
    Long productId,
    Long userId,
    int quantity,
    OrderStatus status,
    Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.id(),
            order.broadcastId(),
            order.productId(),
            order.userId(),
            order.quantity(),
            order.status(),
            order.createdAt()
        );
    }
}
