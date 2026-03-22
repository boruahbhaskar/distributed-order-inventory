package com.example.orderservice.config.exception;

public class InsufficientStockException extends RuntimeException {

    private final String productId;
    private final int requested;

    public InsufficientStockException(String productId, int requested) {
        super(String.format(
                "Insufficient stock for product '%s'. Requested: %d", productId, requested));
        this.productId = productId;
        this.requested = requested;
    }

    public String getProductId() {
        return productId;
    }

    public int getRequested() {
        return requested;
    }
}