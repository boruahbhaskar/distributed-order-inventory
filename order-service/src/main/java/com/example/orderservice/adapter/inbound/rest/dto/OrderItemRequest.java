package com.example.orderservice.adapter.inbound.rest.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/*

Why Java record instead of class

Records are immutable by default - no setters , final fields

DTOs should never be modified after construction

Records also auto generated equals(), hashcode(), toString() and a canonical constructor

Less code, same result

*/

public record OrderItemRequest(

        @NotBlank(message = "productId is required") String productId,

        @NotNull(message = "quantity is required") @Min(value = 1, message = "Quantity must be at least 1") @Max(value = 1000, message = "Quantity can not exceed 1000 per item") Integer quantity,

        @NotNull(message = "unitPrice is required") @DecimalMin(value = "0.01", message = "Unit price must be greater than zero") BigDecimal unitPrice) {
}
