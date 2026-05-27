package com.interview.homeshoptraffic.order;

public interface OrderQueue {

    boolean offer(String orderId);

    String take() throws InterruptedException;

    int size();
}
