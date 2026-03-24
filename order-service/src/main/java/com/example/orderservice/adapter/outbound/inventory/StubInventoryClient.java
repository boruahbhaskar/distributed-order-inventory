package com.example.orderservice.adapter.outbound.inventory;

import org.springframework.stereotype.Component;

import com.example.orderservice.domain.port.out.InventoryClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Temporary Stub to allow OrderService to run without the real Inventory
 * Service.
 * This will be replaced by a FeignClient in Phase 7.
 */
@Slf4j
@Component
public class StubInventoryClient implements InventoryClient {

    @Override
    public boolean reserveStock(String productId, int quantity) {
        log.info("[STUB] Automatically approving stock reservation for: {} x {}", productId, quantity);
        return true; // Always succeed for now
    }

    @Override
    public void releaseStock(String productId, int quantity) {
        log.info("[STUB] Automatically releasing stock for: {} x {}", productId, quantity);
    }
}