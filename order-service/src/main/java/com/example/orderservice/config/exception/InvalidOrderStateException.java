package com.example.orderservice.config.exception;

import com.example.orderservice.domain.model.OrderStatus;

public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(OrderStatus current, OrderStatus attempted) {
        super(String.format(
                "Cannot transition order from %s to %s", current, attempted));
    }
}