package com.example.orderservice.adapter.outbound.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.example.orderservice.domain.model.Order;
import com.example.orderservice.domain.port.out.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// WHY @Component and not @Repository:
//   @Repository is semantically for Spring Data interfaces.
//   This class is an ADAPTER — it wraps a repository.
//   @Component is accurate and avoids confusion.
//   Functionally identical for Spring's component scan.
//
// This class is the ONLY class in the entire project that:
//   1. Implements the domain's OrderRepository port
//   2. Has access to the JPA repository
// Everything above it (service) sees only the port interface.
// Everything below it (JPA) knows nothing about the domain port.
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPersistenceAdapter implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    @Override
    public Order save(Order order) {
        // Delegates directly — no transformation needed because
        // Order is both the domain entity and the JPA entity here.
        // In a stricter hexagonal architecture, you'd have separate
        // domain model and JPA model classes with a mapper between them.
        // That adds safety but also significant boilerplate.
        // This is a pragmatic trade-off for a microservice this size.
        return jpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        // WHY findByIdWithItems instead of findById:
        // Our service needs order.getItems() in most operations.
        // Loading them eagerly here avoids LazyInitializationException
        // when the service accesses items after the JPA session closes.
        return jpaRepository.findByIdWithItems(id);
    }

    @Override
    public Page<Order> findAll(Pageable pageable) {
        // WHY standard findAll for the list:
        // Loading items for every order in a paginated list is expensive.
        // For a list view, the summary (id, customerId, status, total)
        // is enough. Items are loaded only for single-order fetches.
        return jpaRepository.findAll(pageable);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsByIdEfficient(id);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}