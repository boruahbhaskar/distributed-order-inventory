package com.example.orderservice.adapter.inbound.rest.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(

        @NotBlank(message = "customerId is required") String customerId,

        /*
         * 
         * Why @NotEmpty vs @NotNull
         * 
         * @NotNull : List must not be null -> "items": null -> fails
         * 
         * @NotEmpty : lists must not be null or empty - > "items": [] -> fails
         * 
         * Both null and empty list are invalid here - use @NotEmpty
         * 
         */

        /*
         * 
         * Why @Valid on the list:
         * 
         * Without @Valid , spring validates CreateOrderRequest but STOPS there.
         * 
         * It does NOT cascade into each OrderItemRequest inside the list
         * 
         * @Valid tells spring: " Also validate every object inside this list."
         * 
         * Without it , a list item with quantity = -1 would pass validation silently
         * 
         */

        @NotEmpty(message = "Order must contain at least one item") @Size(max = 50, message = "Order cannot exceed 50 items") @Valid List<OrderItemRequest> items

) {
}
