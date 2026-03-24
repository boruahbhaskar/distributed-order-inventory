package com.example.orderservice.domain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.orderservice.adapter.inbound.rest.dto.CreateOrderRequest;
import com.example.orderservice.adapter.inbound.rest.dto.OrderResponse;
import com.example.orderservice.adapter.inbound.rest.dto.PatchOrderStatusRequest;
import com.example.orderservice.adapter.inbound.rest.dto.UpdateOrderRequest;
import com.example.orderservice.config.exception.InsufficientStockException;
import com.example.orderservice.config.exception.InvalidOrderStateException;
import com.example.orderservice.config.exception.OrderNotFoundException;
import com.example.orderservice.domain.model.Order;
import com.example.orderservice.domain.model.OrderItem;
import com.example.orderservice.domain.model.OrderStatus;
import com.example.orderservice.domain.port.in.OrderUseCase;
import com.example.orderservice.domain.port.out.InventoryClient;
import com.example.orderservice.domain.port.out.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*

Why @Transactional at class level :

Every public method gets a transaction by default
Read methods override with @Transactional(readOnly = true)
This is safer than forgetting to annotate individual methods

*/

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderServiceImpl implements OrderUseCase {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final OrderMapper orderMapper;

    // ---------------- CREATE -----------------

    // TRANSACTIONAL BOUNDARY EXPLANATION (for interviews):
    //
    // This method is @Transactional. The sequence is:
    // 1. Build Order entity (in-memory, no DB call yet)
    // 2. Call inventoryClient.reserveStock() — this is an OUTBOUND HTTP
    // call to Inventory-Service. It is NOT in our DB transaction.
    // 3. Save order to DB — DB transaction commits here.
    //
    // PROBLEM: If step 3 fails after step 2 succeeds, we've reserved
    // inventory but have no order. This is a distributed consistency gap.
    //
    // SOLUTION OPTIONS:
    // a) Saga Pattern (Choreography): Order-Service publishes an
    // ORDER_CREATED event; Inventory-Service listens and reserves.
    // If Order save fails, publish ORDER_FAILED event to release stock.
    // b) Two-Phase Commit (2PC): Distributed transaction. Works but
    // introduces coupling and performance bottlenecks.
    // c) Outbox Pattern: Write order + inventory reservation command to
    // same DB transaction. A separate process publishes the command.
    //
    // FOR THIS PROJECT: We use approach (a) partially — the circuit breaker
    // ensures we don't save the order if inventory reservation fails.

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {

        log.info("Creating order for customerId: {}", request.customerId());

        // Business Rule 1 : Reserve Inventory before creating the order.
        // Why : if we saved the order first, then inventory failed, we had have an
        // order in the DB with no reserved stock - a phantom order
        // Reserving first means a failure leaves no DB record to clean up

        // Step 1: Reserve inventory (circuit breaker protects this call)
        request.items().forEach(item -> {
            log.debug("Reserving stock for product: {}, qty: {}", item.productId(), item.quantity());

            boolean reserved = inventoryClient.reserveStock(
                    item.productId(),
                    item.quantity());

            if (!reserved) {
                // Reservation failed - throw BEFORE any DB write happens
                throw new InsufficientStockException(item.productId(), item.quantity());
            }
        });

        /*
         * Business Rule 2 : Calculate total from items
         * WHY - Calculate here and not trust client-sent total
         * Never trust the client for money calculations. A client could send
         * totalAmount = 0.01 for a $500 order.
         * Always recalculate on the server from the line items
         */

        // Step 2: Build domain entity

        BigDecimal total = request.items().stream()
                .map(item -> item.unitPrice()
                        .multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Build the domain entity

        Order order = Order.builder()
                .customerId(request.customerId())
                .totalAmount(total)
                .status(OrderStatus.PENDING)
                .items(new ArrayList<>())
                .build();

        // Build order items and link them to the order
        List<OrderItem> items = request.items().stream()
                .map(itemRequest -> OrderItem.builder()
                        .order(order) // WHY: bidirectional link required for JPA
                        .productId(itemRequest.productId())
                        .quantity(itemRequest.quantity())
                        .unitPrice(itemRequest.unitPrice())
                        .build())
                .toList();

        order.getItems().addAll(items);

        // Persist - CascadeType.ALL saves order + all items in one transaction
        // Step 3: Persist — transaction commits here

        Order saved = orderRepository.save(order);
        log.info("Order created successfully with id: {}", saved.getId());

        return orderMapper.toResponse(saved);

    }

    // ── READ SINGLE ─────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    // WHY readOnly = true:
    // 1. Hibernate skips dirty checking (no need to track changes)
    // 2. Database can route to a read replica if configured
    // 3. Communicates intent clearly — this method never writes
    public OrderResponse getOrderById(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        return orderMapper.toResponse(order);
    }

    // ── READ ALL (PAGINATED) ─────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        // WHY return Page not List:
        // A List would load ALL orders from the DB into memory.
        // With 1 million orders that is an OOM error.
        // Page returns only the requested slice — e.g., 20 orders at a time.
        return orderRepository.findAll(pageable)
                .map(orderMapper::toResponse);
    }

    // ──--------------- FULL UPDATE (PUT) ─────────────────────────────────────

    public OrderResponse updateOrder(UUID id, UpdateOrderRequest request) {

        Order order = orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));

        // Business Rule : Only PENDING orders can be updated
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException(order.getStatus(), order.getStatus());
        }

        // Full replacement - PUT semantics

        order.setCustomerId(request.customerId());

        /*
         * WHY clear() + addAll() for items:
         * JPA orphanRemoval = true means clearing the collection marks existing items
         * for deletion.
         * Adding new ones inserts them .This handles add/removal/update of items in one
         * operation
         */

        order.getItems().clear();

        BigDecimal newTotal = request.items().stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setTotalAmount(newTotal);

        request.items().forEach(itemRequest -> order.getItems().add(OrderItem.builder()
                .order(order)
                .productId(itemRequest.productId())
                .quantity(itemRequest.quantity())
                .unitPrice(itemRequest.unitPrice())
                .build()));

        return orderMapper.toResponse(orderRepository.save(order));

    }

    // ──--------------- PARTIAL UPDATE (PATCH)─────────────────────────────────────

    @Override
    public OrderResponse patchOrderStatus(UUID id, PatchOrderStatusRequest request) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));

        /*
         * WHY call domain method instead of order.setStatus(request.status()):
         * order.transitionStatus() enforces the state machine.
         * setStatus() bypasses it - any code could set any status directly.
         * Business rules must be enforced at the domain level, not hoped for at the
         * caller level
         * 
         * // Domain method enforces the state machine — no raw setStatus() here.
         * // WHY: Business rule enforcement lives in the domain, not the service.
         * // This prevents the service layer from bypassing invariants.
         * 
         */

        order.transitionStatus(request.status());

        // Business rule : cancellation releases inventory

        if (request.status() == OrderStatus.CANCELLED) {
            order.getItems().forEach(item -> {
                log.info("Releasing stock for product: {} qty: {}", item.getProductId(),
                        item.getQuantity());

                inventoryClient.releaseStock(item.getProductId(),
                        item.getQuantity());

            });
        }

        Order updated = orderRepository.save(order);
        log.info("Order {} status updated to {}", id, request.status());

        return orderMapper.toResponse(updated);

    }

    // ──--------------- DELETE ─────────────────────────────────────
    @Override
    public void deleteOrder(UUID id) {

        /*
         * WHY check existence before deleting
         * deleteById() on a non-existent ID silently doe s nothing Spring Data.
         * without this check, DELETE /orders/nonexistent return 204 No Content which is
         * misleading - the clients think they deleted something
         */

        if (!orderRepository.existsById(id)) {
            throw new OrderNotFoundException(id);
        }

        orderRepository.deleteById(id);
        log.info("Order {} deleted", id);

    }

}
