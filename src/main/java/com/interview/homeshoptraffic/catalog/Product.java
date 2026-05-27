package com.interview.homeshoptraffic.catalog;

public record Product(
    Long id,
    String name,
    long price,
    int stock
) {
}
