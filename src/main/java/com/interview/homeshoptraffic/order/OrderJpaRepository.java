package com.interview.homeshoptraffic.order;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, String> {

    Optional<OrderEntity> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update OrderEntity o
           set o.status = :status
         where o.id = :orderId
        """)
    int updateStatus(String orderId, OrderStatus status);
}
