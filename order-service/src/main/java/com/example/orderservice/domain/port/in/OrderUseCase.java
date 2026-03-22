package com.example.orderservice.domain.port.in;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.orderservice.adapter.inbound.rest.dto.CreateOrderRequest;
import com.example.orderservice.adapter.inbound.rest.dto.OrderResponse;
import com.example.orderservice.adapter.inbound.rest.dto.PatchOrderStatusRequest;
import com.example.orderservice.adapter.inbound.rest.dto.UpdateOrderRequest;

/*

Why forward-reference DTOs that dont exists yet:

The input port defines the API CONTRACT . Writing it now forces you to think about what operations this service exposes BEFORE writing any
implementation. This is design-first thinking

The compiler errors on missing DTO classes are expected at this stage. They will resolve in Phase 3 when DTOs are created.

Why DTOs in the port interface and not domain objects ?

-> The input port is the boundary between the outside world and the domain.
We dont expose the raw domain entities (Order) to the outside - that would leak persistence details ( @Entity , @Column etc ) to callers

DTOs are purpose-built for transport : no JPA annotations, no surpises

*/

public interface OrderUseCase {

    // CREATE - takes a request , returns the created order as response
    OrderResponse createOrder(CreateOrderRequest request);

    // READ - Single Order by Id
    OrderResponse getOrderById(UUID id);

    // READ - Pagination list
    Page<OrderResponse> getAllOrders(Pageable pageable);

    // Full UPDATE - PUT semantics ( replace the entire order)
    OrderResponse updateOrder(UUID id, UpdateOrderRequest request);

    // PARTIAL UPDATE - PATCH semantics ( status only)
    OrderResponse patchOrderStatus(UUID id, PatchOrderStatusRequest request);

    // DELETE
    void deleteOrder(UUID id);

}
