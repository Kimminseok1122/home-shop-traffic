package com.interview.homeshoptraffic.catalog;

import java.util.Optional;

public interface ProductRepository {

    Optional<Product> findById(Long id);

    boolean decreaseStockIfEnough(Long productId, int quantity);

    void increaseStock(Long productId, int quantity);

    void resetStock(Long productId, int stock);
}
