package com.interview.homeshoptraffic.traffic;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class TrafficMetrics {

    private final Counter ticketIssuedCounter;
    private final Counter orderAcceptedCounter;
    private final Counter orderConfirmedCounter;
    private final Counter orderRejectedCounter;
    private final AtomicInteger queueSize = new AtomicInteger();

    public TrafficMetrics(MeterRegistry meterRegistry) {
        this.ticketIssuedCounter = Counter.builder("home_shop_waiting_ticket_issued_total")
            .description("Total issued waiting room tickets")
            .register(meterRegistry);

        this.orderAcceptedCounter = Counter.builder("home_shop_order_accepted_total")
            .description("Orders accepted into the internal queue")
            .register(meterRegistry);

        this.orderConfirmedCounter = Counter.builder("home_shop_order_confirmed_total")
            .description("Orders confirmed by async workers")
            .register(meterRegistry);

        this.orderRejectedCounter = Counter.builder("home_shop_order_rejected_total")
            .description("Rejected orders")
            .register(meterRegistry);

        Gauge.builder("home_shop_order_queue_size", queueSize, AtomicInteger::get)
            .description("Current order queue size")
            .register(meterRegistry);
    }

    public void ticketIssued() {
        ticketIssuedCounter.increment();
    }

    public void orderAccepted() {
        orderAcceptedCounter.increment();
    }

    public void orderConfirmed() {
        orderConfirmedCounter.increment();
    }

    public void orderRejected() {
        orderRejectedCounter.increment();
    }

    public void updateQueueSize(int size) {
        queueSize.set(size);
    }
}
