package com.interview.homeshoptraffic.catalog;

import com.interview.homeshoptraffic.common.ApiResponse;
import com.interview.homeshoptraffic.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping("/api/products/{productId}")
    public ApiResponse<ProductResponse> findProduct(@PathVariable Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Product not found"));

        return ApiResponse.ok(ProductResponse.from(product));
    }

    @PostMapping("/api/admin/products/{productId}/stock")
    public ApiResponse<ProductResponse> resetStock(
        @PathVariable Long productId,
        @RequestParam int stock
    ) {
        productRepository.resetStock(productId, stock);

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Product not found"));

        return ApiResponse.ok(ProductResponse.from(product));
    }
}
