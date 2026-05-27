package com.interview.homeshoptraffic.order;

import com.interview.homeshoptraffic.catalog.ProductRepository;
import com.interview.homeshoptraffic.common.BusinessException;
import com.interview.homeshoptraffic.traffic.TrafficMetrics;
import com.interview.homeshoptraffic.waiting.WaitingRoomService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final WaitingRoomService waitingRoomService;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderQueue orderQueue;
    private final TrafficMetrics metrics;

    public OrderService(
        WaitingRoomService waitingRoomService,
        ProductRepository productRepository,
        OrderRepository orderRepository,
        OrderQueue orderQueue,
        TrafficMetrics metrics
    ) {
        this.waitingRoomService = waitingRoomService;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderQueue = orderQueue;
        this.metrics = metrics;
    }

    @Transactional
    public OrderResponse acceptOrder(
        String waitingToken,
        String idempotencyKey,
        CreateOrderRequest request
    ) {
        validateRequest(idempotencyKey, request);

        return orderRepository.findByUserIdAndIdempotencyKey(request.userId(), idempotencyKey)
            .map(OrderResponse::from)
            .orElseGet(() -> createNewOrder(waitingToken, idempotencyKey, request));
    }

    @Transactional(readOnly = true)
    public OrderResponse findOrder(String orderId) {
        return orderRepository.findById(orderId)
            .map(OrderResponse::from)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    public QueueStatus queueStatus() {
        return new QueueStatus(orderQueue.size());
    }

    private OrderResponse createNewOrder(
        String waitingToken,
        String idempotencyKey,
        CreateOrderRequest request
    ) {
        waitingRoomService.validateAndConsume(
            waitingToken,
            request.broadcastId(),
            request.userId()
        );

        boolean stockReserved = productRepository.decreaseStockIfEnough(
            request.productId(),
            request.quantity()
        );

        if (!stockReserved) {
            metrics.orderRejected();
            throw new BusinessException(HttpStatus.CONFLICT, "Sold out");
        }

        Order order = new Order(
            UUID.randomUUID().toString(),
            request.broadcastId(),
            request.productId(),
            request.userId(),
            request.quantity(),
            idempotencyKey,
            OrderStatus.ACCEPTED,
            Instant.now()
        );

        try {
            orderRepository.save(order);
        } catch (DataIntegrityViolationException exception) {
            metrics.orderRejected();
            throw new BusinessException(HttpStatus.CONFLICT, "Duplicated order request");
        }

        boolean enqueued = orderQueue.offer(order.id());
        if (!enqueued) {
            metrics.orderRejected();
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Order queue is full");
        }

        metrics.orderAccepted();

        return OrderResponse.from(order);
    }

    private void validateRequest(String idempotencyKey, CreateOrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }

        if (request == null
            || request.broadcastId() == null
            || request.productId() == null
            || request.userId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "broadcastId, productId and userId are required");
        }

        if (request.quantity() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "quantity must be greater than zero");
        }
    }

    public record QueueStatus(
        int size
    ) {
    }
}
