package com.example.orderservice.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.orderservice.domain.model.Order;

/**
 * 
 * Why this interface exists?
 * 
 * This is the OUTPUT PORT - the service declares what it needs from persistence
 * , in domain terms.
 * It has no knowledge of JPA , Hiberante , SQL or PostgreSQL . Those are
 * infrastructure concerns.
 * 
 * The concrete implementation (OrderPersistenceAdapter) lives in the outbound
 * adapter layer and implemented this interface
 * 
 * Benefit: You can swap PostGreSQL for MongoDB , DynamoDB or evenn an in-memory
 * map for tests - OrderServiceImpl never changes.
 * 
 * Only the adapter changes
 * 
 */

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    Page<Order> findAll(Pageable pageable);

    boolean existsById(UUID id);

    void deleteById(UUID id);

}