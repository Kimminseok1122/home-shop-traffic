package com.interview.homeshoptraffic.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ProductEntity p
           set p.stock = p.stock - :quantity
         where p.id = :productId
           and p.stock >= :quantity
        """)
    int decreaseStockIfEnough(Long productId, int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ProductEntity p
           set p.stock = p.stock + :quantity
         where p.id = :productId
        """)
    int increaseStock(Long productId, int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ProductEntity p
           set p.stock = :stock
         where p.id = :productId
        """)
    int resetStock(Long productId, int stock);
}
