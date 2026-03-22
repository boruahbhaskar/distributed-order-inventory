package com.example.orderservice.adapter.inbound.rest.dto;

import com.example.orderservice.domain.model.OrderStatus;

import jakarta.validation.constraints.NotNull;

/*

Why this DTO has only one field ?

- PATCH = partial update. This endpoint only change status.
 Accepting more fields here would silently ignore them or accidently allow clients to change things they should not.

 One field = explicit , minimal , impossible to misuse

 Why @NotNull and not @NotBlank

 @NotBlank is for Strings only

 OrderStatus is an enum - use @NotNull to ensure its provided

 Spring automatically deserializes "CONFIMRED" -> OrderStatus.CONFIRMED

 If the client sends an invalid string like "FLYING",

 JACKSON throws HttpMessageNotReadableException before validation even runs -> GlobalExceptionHandler catches it -> 400 response

*/

public record PatchOrderStatusRequest(
        @NotNull(message = "status is required") OrderStatus status) {

}
