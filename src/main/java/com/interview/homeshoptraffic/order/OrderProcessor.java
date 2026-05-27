package com.interview.homeshoptraffic.order;

import com.interview.homeshoptraffic.traffic.TrafficMetrics;
import com.interview.homeshoptraffic.traffic.TrafficProperties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class OrderProcessor implements SmartLifecycle {

    private final OrderQueue queue;
    private final OrderRepository orderRepository;
    private final TrafficProperties properties;
    private final TrafficMetrics metrics;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    private volatile boolean running;

    public OrderProcessor(
        OrderQueue queue,
        OrderRepository orderRepository,
        TrafficProperties properties,
        TrafficMetrics metrics
    ) {
        this.queue = queue;
        this.orderRepository = orderRepository;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public void start() {
        running = true;

        for (int i = 0; i < properties.orderQueue().workers(); i++) {
            executorService.submit(this::runWorker);
        }
    }

    private void runWorker() {
        while (running) {
            try {
                String orderId = queue.take();

                // 실제 서비스라면 여기서 결제 승인, 주문 확정, 알림 발송 등을 처리한다.
                Thread.sleep(properties.orderQueue().processorDelayMillis());

                boolean updated = orderRepository.updateStatus(orderId, OrderStatus.CONFIRMED);
                if (updated) {
                    metrics.orderConfirmed();
                } else {
                    // 주문 트랜잭션 커밋보다 워커가 먼저 실행된 경우를 대비한 단순 재시도.
                    queue.offer(orderId);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                metrics.orderRejected();
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        executorService.shutdownNow();
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
