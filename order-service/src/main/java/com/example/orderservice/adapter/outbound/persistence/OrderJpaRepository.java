package com.example.orderservice.adapter.outbound.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.orderservice.domain.model.Order;
import com.example.orderservice.domain.model.OrderStatus;

// WHY this interface has almost no code:
//   Spring Data JPA generates the implementation at startup.
//   Method names follow a naming convention that Spring parses into SQL.
//   findByCustomerId → SELECT * FROM orders WHERE customer_id = ?
//   You write zero SQL for standard operations.
//
// WHY it extends JpaRepository and not CrudRepository:
//   JpaRepository adds:
//     - Pagination (findAll(Pageable))
//     - Batch operations (saveAll, deleteAllInBatch)
//     - flush() for explicit persistence
//   CrudRepository only has basic CRUD.
//   Use JpaRepository as your default starting point.
public interface OrderJpaRepository extends JpaRepository<Order, UUID> {

    // Spring Data derives SQL from method name:
    // SELECT * FROM orders WHERE customer_id = ? ORDER BY created_at DESC
    Page<Order> findByCustomerId(String customerId, Pageable pageable);

    // Spring Data derives: SELECT * FROM orders WHERE status = ?
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    // WHY @Query with JOIN FETCH:
    // findById() from JpaRepository loads Order but NOT its items
    // (because items is LAZY). Accessing order.getItems() outside a
    // transaction throws LazyInitializationException.
    // JOIN FETCH loads Order + items in a SINGLE SQL query.
    // Use this when you know you always need the items.
    @Query("""
            SELECT o FROM Order o
            LEFT JOIN FETCH o.items
            WHERE o.id = :id
            """)
    Optional<Order> findByIdWithItems(@Param("id") UUID id);

    // WHY a custom exists query vs existsById:
    // existsById from JpaRepository fetches the entire entity first,
    // then checks if it's non-null. Wasteful for a boolean check.
    // This query fetches only the id — much lighter on the DB.
    @Query("SELECT COUNT(o) > 0 FROM Order o WHERE o.id = :id")
    boolean existsByIdEfficient(@Param("id") UUID id);
}