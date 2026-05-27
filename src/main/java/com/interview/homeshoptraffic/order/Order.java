package com.interview.homeshoptraffic.order;

import java.time.Instant;

public class Order {

    private final String id;
    private final Long broadcastId;
    private final Long productId;
    private final Long userId;
    private final int quantity;
    private final String idempotencyKey;
    private final Instant createdAt;
    private final OrderStatus status;

    public Order(
        String id,
        Long broadcastId,
        Long productId,
        Long userId,
        int quantity,
        String idempotencyKey,
        OrderStatus status,
        Instant createdAt
    ) {
        this.id = id;
        this.broadcastId = broadcastId;
        this.productId = productId;
        this.userId = userId;
        this.quantity = quantity;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public Long broadcastId() {
        return broadcastId;
    }

    public Long productId() {
        return productId;
    }

    public Long userId() {
        return userId;
    }

    public int quantity() {
        return quantity;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public OrderStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
