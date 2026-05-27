package com.interview.homeshoptraffic.catalog;

public record ProductResponse(
    Long id,
    String name,
    long price,
    int stock
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.id(),
            product.name(),
            product.price(),
            product.stock()
        );
    }
}
