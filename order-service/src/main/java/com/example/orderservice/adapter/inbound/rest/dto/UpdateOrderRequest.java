package com.example.orderservice.adapter.inbound.rest.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/*

Why UpdateOrderRequest is separate from the CreateOrderRequest:
They look similar now but diverge over time.
Update might allow changing items but not customerId.
Create might add fields like promoCode that dont apply to update
Keeping them separate protects future flexibility
Merging them with optional fields create ambuigity about what's required for each operation

*/

public record UpdateOrderRequest(
        @NotBlank(message = "customerId is required") String customerId,

        @NotEmpty(message = "Order must contain at least one item") @Size(max = 50, message = "Order can not exceed 50 items") @Valid List<OrderItemRequest> items) {
}