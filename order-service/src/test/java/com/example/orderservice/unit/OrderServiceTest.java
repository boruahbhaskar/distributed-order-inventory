package com.example.orderservice.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.orderservice.adapter.inbound.rest.dto.CreateOrderRequest;
import com.example.orderservice.adapter.inbound.rest.dto.OrderItemRequest;
import com.example.orderservice.adapter.inbound.rest.dto.OrderResponse;
import com.example.orderservice.adapter.inbound.rest.dto.PatchOrderStatusRequest;
import com.example.orderservice.config.exception.InsufficientStockException;
import com.example.orderservice.config.exception.OrderNotFoundException;
import com.example.orderservice.domain.model.Order;
import com.example.orderservice.domain.model.OrderItem;
import com.example.orderservice.domain.model.OrderStatus;
import com.example.orderservice.domain.port.out.InventoryClient;
import com.example.orderservice.domain.port.out.OrderRepository;
import com.example.orderservice.domain.service.OrderMapper;
import com.example.orderservice.domain.service.OrderServiceImpl;

/*
WHY @ExtendWith(MockitoExtension.class) and NOT @SpringBootTest:

@SpringBootTest loads the entire Spring context -database, security , Feign clients, everything. It takes 10-30 seconds to start.

MockitoExtension loads nothing - just creates mocks and injects them.

These tests run in under 1 second total.

This is what "unit test" mean - test ONE unit in isolation

*/
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl Unit Tests")
class OrderServiceTest {

    /*
     * WHY @MOCK
     * - Creates a fake implementation of the interface.
     * Every method returns null/0/false by default
     * you define specific behavior with when().thenReturn()
     */

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private OrderMapper orderMapper;

    /*
     * WHY @InjectMocks: Create a real OrderServiceImpl and injects the mocks above
     * into its constructor ( because of @RequiredArgsConstructor)
     * This is the class under test - everything else is mocked
     */

    @InjectMocks
    private OrderServiceImpl orderService;

    // ----------- Test Data Builders ---------------

    private CreateOrderRequest validCreateRequest() {
        return new CreateOrderRequest(
                "cust-123",
                List.of(new OrderItemRequest("PROD-1", 2, new BigDecimal("29.99"))));
    }

    private Order savedOrder(UUID id) {
        return Order.builder()
                .id(id)
                .customerId("cust-123")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("59.98"))
                .items(new ArrayList<>())
                .build();

    }

    private OrderResponse orderResponse(UUID id) {
        return new OrderResponse(
                id, "cust-123", OrderStatus.PENDING,
                new BigDecimal("59.98"), List.of(), null, null);

    }

    // ──--------------------CREATE Tests----------------------

    @Test
    @DisplayName("createOrder: happy path — reserves stock, saves order, returns response")
    void createOrder_success() {
        // ARRANGE
        UUID orderId = UUID.randomUUID();
        CreateOrderRequest request = validCreateRequest();
        Order saved = savedOrder(orderId);
        OrderResponse expected = orderResponse(orderId);

        when(inventoryClient.reserveStock("PROD-1", 2)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenReturn(saved);
        when(orderMapper.toResponse(saved)).thenReturn(expected);

        // ACT
        OrderResponse result = orderService.createOrder(request);

        // ASSERT

        assertThat(result.id()).isEqualTo(orderId);
        assertThat(result.customerId()).isEqualTo("cust-123");
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);

        // Verify interactions — did service call what it should?
        verify(inventoryClient, times(1)).reserveStock("PROD-1", 2);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(orderMapper, times(1)).toResponse(saved);
    }

    @Test
    @DisplayName("createOrder: calculates total correctly from line items")
    void createOrder_calculatesCorrectTotal() {
        // 2 items: 2×29.99 = 59.98, 3×10.00 = 30.00, total = 89.98
        CreateOrderRequest request = new CreateOrderRequest(
                "cust-123",
                List.of(
                        new OrderItemRequest("PROD-1", 2, new BigDecimal("29.99")),
                        new OrderItemRequest("PROD-2", 3, new BigDecimal("10.00"))));

        when(inventoryClient.reserveStock(anyString(), anyInt())).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            // Return the actual order that was passed to save()
            // so we can assert on its totalAmount
            return invocation.getArgument(0);
        });
        when(orderMapper.toResponse(any())).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            return new OrderResponse(UUID.randomUUID(), o.getCustomerId(),
                    o.getStatus(), o.getTotalAmount(), List.of(), null, null);
        });

        OrderResponse result = orderService.createOrder(request);

        // WHY isEqualByComparingTo and not isEqualTo for BigDecimal:
        // new BigDecimal("89.98").equals(new BigDecimal("89.980")) = FALSE
        // isEqualByComparingTo ignores scale — compares numeric value only
        assertThat(result.totalAmount()).isEqualByComparingTo("89.98");
    }

    @Test
    @DisplayName("createOrder: throws InsufficientStockException when inventory says no")
    void createOrder_insufficientStock_throwsException() {
        CreateOrderRequest request = validCreateRequest();

        when(inventoryClient.reserveStock("PROD-1", 2)).thenReturn(false);

        // WHY assertThatThrownBy instead of @Test(expected=...):
        // assertThatThrownBy lets you assert on the exception MESSAGE
        // and TYPE, not just the type. Much more precise.
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("PROD-1");

        // CRITICAL: verify order was NEVER saved when stock is insufficient
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder: does not save order if ANY item has insufficient stock")
    void createOrder_secondItemInsufficientStock_nothingSaved() {
        CreateOrderRequest request = new CreateOrderRequest(
                "cust-123",
                List.of(
                        new OrderItemRequest("PROD-1", 2, new BigDecimal("10.00")),
                        new OrderItemRequest("PROD-2", 5, new BigDecimal("10.00"))));

        // First item OK, second item fails
        when(inventoryClient.reserveStock("PROD-1", 2)).thenReturn(true);
        when(inventoryClient.reserveStock("PROD-2", 5)).thenReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("PROD-2");

        verify(orderRepository, never()).save(any());
    }

    // ── READ Tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrderById: returns response when order exists")
    void getOrderById_found_returnsResponse() {
        UUID id = UUID.randomUUID();
        Order order = savedOrder(id);
        OrderResponse expected = orderResponse(id);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(expected);

        OrderResponse result = orderService.getOrderById(id);

        assertThat(result.id()).isEqualTo(id);
        verify(orderRepository).findById(id);
    }

    @Test
    @DisplayName("getOrderById: throws OrderNotFoundException when not found")
    void getOrderById_notFound_throwsException() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(id))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    // ── PATCH Tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("patchOrderStatus: PENDING → CONFIRMED succeeds")
    void patchStatus_pendingToConfirmed_succeeds() {
        UUID id = UUID.randomUUID();
        Order order = savedOrder(id); // starts as PENDING
        PatchOrderStatusRequest request = new PatchOrderStatusRequest(OrderStatus.CONFIRMED);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.toResponse(order)).thenReturn(orderResponse(id));

        orderService.patchOrderStatus(id, request);

        // Assert the status was actually changed on the entity
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // Inventory release should NOT be called on CONFIRMED
        verify(inventoryClient, never()).releaseStock(anyString(), anyInt());
    }

    @Test
    @DisplayName("patchOrderStatus: PENDING → CANCELLED releases inventory")
    void patchStatus_cancelled_releasesInventory() {
        UUID id = UUID.randomUUID();

        // Build order with one item
        Order order = savedOrder(id);
        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .productId("PROD-1")
                .quantity(2)
                .unitPrice(new BigDecimal("29.99"))
                .build();
        order.getItems().add(item);

        PatchOrderStatusRequest request = new PatchOrderStatusRequest(OrderStatus.CANCELLED);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.toResponse(order)).thenReturn(orderResponse(id));

        orderService.patchOrderStatus(id, request);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // CRITICAL: inventory must be released when order is cancelled
        verify(inventoryClient, times(1)).releaseStock("PROD-1", 2);
    }

    @Test
    @DisplayName("patchOrderStatus: DELIVERED → PENDING is invalid — throws")
    void patchStatus_invalidTransition_throwsIllegalState() {
        UUID id = UUID.randomUUID();
        Order order = Order.builder()
                .id(id)
                .customerId("cust-123")
                .status(OrderStatus.DELIVERED) // terminal state
                .totalAmount(BigDecimal.TEN)
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        PatchOrderStatusRequest request = new PatchOrderStatusRequest(OrderStatus.PENDING);

        assertThatThrownBy(() -> orderService.patchOrderStatus(id, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DELIVERED")
                .hasMessageContaining("PENDING");

        verify(orderRepository, never()).save(any());
    }

    // ── DELETE Tests ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteOrder: deletes when order exists")
    void deleteOrder_exists_deletes() {
        UUID id = UUID.randomUUID();
        when(orderRepository.existsById(id)).thenReturn(true);

        orderService.deleteOrder(id);

        verify(orderRepository).deleteById(id);
    }

    @Test
    @DisplayName("deleteOrder: throws OrderNotFoundException for unknown id")
    void deleteOrder_notFound_throwsException() {
        UUID id = UUID.randomUUID();
        when(orderRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> orderService.deleteOrder(id))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository, never()).deleteById(any());
    }

}
