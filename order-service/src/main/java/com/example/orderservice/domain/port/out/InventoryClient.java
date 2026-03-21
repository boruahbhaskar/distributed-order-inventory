package com.example.orderservice.domain.port.out;

/**
 * 
 * Why this is an OUTPUT PORT and not just the Feign Client directly ?
 * 
 * Ans- If OrderServiceImpl imported InventoryFeignClient directly, the domain
 * layer would have a compile-time
 * dependency on Spring Cloud OpenFeign - a framework concern. That violates
 * hexagonal architecture.
 * 
 * Instead -
 * - Domain declares this simple interface ( zero framework imports)
 * - InventoryClientAdapter ( Outbound adapter ) implements this interface and
 * internally uses the Feign Client
 * 
 * This also makes unit testing trivial - mock(InventoryClient.class) - no Feign
 * , no HTTP, No network in tests
 * 
 * 
 */

public interface InventoryClient {

    // Return True if reservation succeeded, false if insufficient stock
    boolean reserveStock(String productId, int quantity);

    // Called when an order is cancelled - release previously held stock
    void releaseStock(String productId, int quantity);

}
