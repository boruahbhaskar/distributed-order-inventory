package com.example.orderservice.adapter.inbound.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.orderservice.domain.model.OrderStatus;

/*

Why OrderResponse is separate from the request DTOs

Request DTOs = whatthe CLIENT sends IN ( never has id, timestamps)
Response DTO = what the server sends OUT ( always has id, timestamps)

Mixing them leads to confusion about which fields are required / optional on which operation. Separate classes = zero ambuiguity 

Why Instant for timestamps instead of LocalDateTime:

Instance is timezone-agnostic - it represents a point in time in UTC

Local timezone has no timezone info - if your server moves to a different timezone , stored values means different things .

Always use Instant for timestamps in APIs. Let client localize


Why a nested record ?

The item response is only meaningful in the context of an order response.
Nesting it communicates ownership . It also keeps the DTO package clean instead of adding a separate OrderItemResponse.java file


*/

public record OrderResponse(
        UUID id,
        String customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt) {

    public record OrderItemResponse(
            UUID id,
            String productId,
            Integer quantity,
            BigDecimal unitPrice) {
    }

}
