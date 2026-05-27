package com.interview.homeshoptraffic.catalog;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MySQLProductRepository implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    public MySQLProductRepository(ProductJpaRepository productJpaRepository) {
        this.productJpaRepository = productJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findById(id)
            .map(ProductEntity::toDomain);
    }

    @Override
    @Transactional
    public boolean decreaseStockIfEnough(Long productId, int quantity) {
        return productJpaRepository.decreaseStockIfEnough(productId, quantity) == 1;
    }

    @Override
    @Transactional
    public void increaseStock(Long productId, int quantity) {
        productJpaRepository.increaseStock(productId, quantity);
    }

    @Override
    @Transactional
    public void resetStock(Long productId, int stock) {
        productJpaRepository.resetStock(productId, stock);
    }
}
