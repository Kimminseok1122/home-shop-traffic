package com.interview.homeshoptraffic.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "orders",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_orders_user_id_idempotency_key", columnNames = {"user_id", "idempotency_key"})
    },
    indexes = {
        @Index(name = "ix_orders_user_id_created_at", columnList = "user_id, created_at"),
        @Index(name = "ix_orders_product_id_created_at", columnList = "product_id, created_at"),
        @Index(name = "ix_orders_status_created_at", columnList = "status, created_at")
    }
)
public class OrderEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private Long broadcastId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    protected OrderEntity() {
    }

    public OrderEntity(
        String id,
        Long broadcastId,
        Long productId,
        Long userId,
        Integer quantity,
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

    public static OrderEntity from(Order order) {
        return new OrderEntity(
            order.id(),
            order.broadcastId(),
            order.productId(),
            order.userId(),
            order.quantity(),
            order.idempotencyKey(),
            order.status(),
            order.createdAt()
        );
    }

    public Order toDomain() {
        return new Order(
            id,
            broadcastId,
            productId,
            userId,
            quantity,
            idempotencyKey,
            status,
            createdAt
        );
    }
}
