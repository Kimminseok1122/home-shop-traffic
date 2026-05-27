package com.interview.homeshoptraffic.order;

import com.interview.homeshoptraffic.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<OrderResponse> createOrder(
        @RequestHeader(value = "X-Waiting-Token", required = false) String waitingToken,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody CreateOrderRequest request
    ) {
        return ApiResponse.accepted(orderService.acceptOrder(waitingToken, idempotencyKey, request));
    }

    @GetMapping("/api/orders/{orderId}")
    public ApiResponse<OrderResponse> findOrder(@PathVariable String orderId) {
        return ApiResponse.ok(orderService.findOrder(orderId));
    }

    @GetMapping("/api/orders/queue")
    public ApiResponse<OrderService.QueueStatus> queueStatus() {
        return ApiResponse.ok(orderService.queueStatus());
    }
}
