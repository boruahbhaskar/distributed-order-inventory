package com.example.orderservice.config.exception;

import java.util.UUID;

/*

Why extend RuntimeException not Exception :

Checked exception(Exception) force every caller to catch of declare throws - pollute every method signature
up the call stack.

RuntimeException is unchecked - Spring's @Transactional rolls back on RunTimeException by default , and GlobalExceptionHandler catches it


*/

public class OrderNotFoundException extends RuntimeException {

    private final UUID orderId;

    public OrderNotFoundException(UUID id) {
        super("Order not found with id: " + id);
        this.orderId = id;
    }

    public UUID getOrderId() {
        return orderId;
    }

}
