package com.interview.homeshoptraffic.order;

import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(String orderId);

    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    boolean updateStatus(String orderId, OrderStatus status);
}
