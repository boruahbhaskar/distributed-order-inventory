package com.example.orderservice.domain.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.orderservice.adapter.inbound.rest.dto.OrderResponse;
import com.example.orderservice.domain.model.Order;
import com.example.orderservice.domain.model.OrderItem;

/*

This is the translator between the DTO world and the Entity world . It belongs to the service layer because it is the only 
layer that knows about both.


Why a dedicated mapper class instead of putting mapping in the service :

OrderServiceImpl is already responsible for business logic . Adding DTO-to-Entity conversion makes it harder to read and test.

Separating mapping means you can test it independantly , and the service method stay focused on what they actually decide

Why not use MapStruct here :

MapStruct generates mapping code at compile time - excellent for production . For learning purposes,

a manual mapper makes every field mapping explicit and visible so you understand exactly what is happening . You can 
swap to MapStruct later - the interface does not change


*/

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {

        List<OrderResponse.OrderItemResponse> itemResponses = order.getItems().stream().map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                itemResponses,
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public OrderResponse.OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderResponse.OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getQuantity(),
                item.getUnitPrice());

    }

}
