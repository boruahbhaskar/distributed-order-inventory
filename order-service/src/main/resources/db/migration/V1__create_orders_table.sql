-- src/main/resources/db/migration/V1__create_orders_table.sql

-- WHY create schema explicitly:
--   PostgreSQL's default schema is "public" — shared by everything.
--   Separate schemas give each service its own namespace.
--   order-service owns order_schema, inventory-service owns inventory_schema.
--   Even if they share a PostgreSQL instance, they cannot accidentally
--   query each other's tables (no cross-schema JPA by default).
CREATE SCHEMA IF NOT EXISTS order_schema;

-- WHY gen_random_uuid():
--   UUID primary keys prevent enumeration attacks
--   (attacker cannot guess id=1, id=2, id=3 to scrape your data).
--   gen_random_uuid() is built into PostgreSQL 13+ — no extension needed.
--   We also have @GeneratedValue(strategy = UUID) in JPA as a backup,
--   but the DB default means IDs are set even for raw SQL inserts.
CREATE TABLE order_schema.orders (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id  VARCHAR(255)  NOT NULL,
    status       VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    total_amount NUMERIC(12,2) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    -- WHY TIMESTAMPTZ not TIMESTAMP:
    --   TIMESTAMPTZ stores UTC and converts on read based on session timezone.
    --   TIMESTAMP stores whatever you give it with no timezone context.
    --   Use TIMESTAMPTZ always — avoids daylight saving bugs across regions.

    CONSTRAINT chk_orders_status CHECK (
        status IN ('PENDING','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED')
    ),
    CONSTRAINT chk_orders_total CHECK (total_amount >= 0)
);

CREATE TABLE order_schema.order_items (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id   UUID          NOT NULL,
    product_id VARCHAR(255)  NOT NULL,
    quantity   INTEGER       NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL,

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id)
        REFERENCES order_schema.orders(id)
        ON DELETE CASCADE,
        -- WHY ON DELETE CASCADE:
        --   If an order is deleted, all its items are deleted automatically.
        --   Matches JPA's orphanRemoval = true.
        --   Without this, deleting an order would fail with FK constraint violation.

    CONSTRAINT chk_order_items_quantity  CHECK (quantity > 0),
    CONSTRAINT chk_order_items_price     CHECK (unit_price > 0)
);

-- Indexes — WHY each one:
-- 1. Queries filtering by customer (GET /orders?customerId=xxx)
CREATE INDEX idx_orders_customer_id ON order_schema.orders(customer_id);

-- 2. Queries filtering by status (GET /orders?status=PENDING)
CREATE INDEX idx_orders_status ON order_schema.orders(status);

-- 3. Default sort is by created_at DESC — index speeds this up
CREATE INDEX idx_orders_created_at ON order_schema.orders(created_at DESC);

-- 4. JOIN from order_items to orders (used by every order fetch with items)
CREATE INDEX idx_order_items_order_id ON order_schema.order_items(order_id);