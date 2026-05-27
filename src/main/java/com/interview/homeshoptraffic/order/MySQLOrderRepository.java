package com.interview.homeshoptraffic.order;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MySQLOrderRepository implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    public MySQLOrderRepository(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    @Transactional
    public Order save(Order order) {
        OrderEntity saved = orderJpaRepository.saveAndFlush(OrderEntity.from(order));
        return saved.toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(String orderId) {
        return orderJpaRepository.findById(orderId)
            .map(OrderEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey) {
        return orderJpaRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
            .map(OrderEntity::toDomain);
    }

    @Override
    @Transactional
    public boolean updateStatus(String orderId, OrderStatus status) {
        return orderJpaRepository.updateStatus(orderId, status) == 1;
    }
}
