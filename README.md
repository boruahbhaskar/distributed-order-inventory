# Distributed Order and Inventory System

Step 1 — Replace pom.xml with the full version
Open order-service/pom.xml in VS Code, replace everything with the full pom.xml from the guide (the one with Feign, Resilience4j, Testcontainers, JaCoCo, JWT, Swagger etc.)
✅ Validate pom.xml

cd order-service

# This downloads all dependencies and verifies pom.xml is valid
mvn dependency:resolve

# What success looks like:
# [INFO] BUILD SUCCESS

# What failure looks like (and fixes):
# [ERROR] 'dependencies.dependency.version' is missing
# → You have a dependency without a version outside the BOM — add version tag

# Check for dependency conflicts
mvn dependency:tree | grep -i "conflict\|omitted"


**In VS Code** — you should see the Maven sidebar populate:

MAVEN
└── order-service
    ├── Dependencies (expandable list of all jars)
    ├── Lifecycle
    └── Plugins


If the Maven sidebar shows a ⚠️ icon, click it — it'll show exactly which line in `pom.xml` is wrong.

---

## Step 2 — Create `OrderStatus.java`

Create the file at:

order-service/src/main/java/com/example/orderservice/domain/model/OrderStatus.java


Validate OrderStatus.java
bashcd order-service

# Compile ONLY this file — catches syntax errors immediately
mvn compile

# Success:
# [INFO] BUILD SUCCESS

# Then verify the .class file was actually generated
ls target/classes/com/example/orderservice/domain/model/
# Should show: OrderStatus.class
Also validate the logic — write a quick throwaway test directly in the terminal:
bash# Quick sanity check of the state machine logic (no test framework needed yet)
cat > /tmp/TestStatus.java << 'EOF'
import com.example.orderservice.domain.model.OrderStatus;

public class TestStatus {
    public static void main(String[] args) {
        // Should be TRUE
        assert OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)  : "FAIL: PENDING→CONFIRMED";
        assert OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)  : "FAIL: PENDING→CANCELLED";
        assert OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)  : "FAIL: SHIPPED→DELIVERED";

        // Should be FALSE
        assert !OrderStatus.DELIVERED.canTransitionTo(OrderStatus.PENDING) : "FAIL: DELIVERED→PENDING should be blocked";
        assert !OrderStatus.CANCELLED.canTransitionTo(OrderStatus.CONFIRMED): "FAIL: CANCELLED→CONFIRMED should be blocked";

        System.out.println("✅ OrderStatus state machine logic is correct");
    }
}
EOF

Run these commands in your terminal:
Compile the Test File:
You must include the Maven output directory in the classpath (-cp).

Bash
javac -cp target/classes /tmp/TestStatus.java
Run the Test File:
Crucially, you must use the -ea (Enable Assertions) flag, or Java will skip the assert lines and always say it passed.

Bash
java -ea -cp target/classes:/tmp TestStatus


Phase 2 — Ports (The Contracts)
Before writing a single file, understand what you're doing and why:
┌─────────────────────────────────────────────────────┐
│  These interfaces are the CONTRACTS between layers.  │
│  They define WHAT happens, not HOW it happens.       │
│                                                      │
│  INPUT PORT  (OrderUseCase)                          │
│    ← "What can the outside world ask this service?"  │
│    ← Controller depends on this                      │
│                                                      │
│  OUTPUT PORTS (OrderRepository, InventoryClient)     │
│    ← "What does the service need from the outside?"  │
│    ← Service depends on these                        │
│    ← JPA adapter implements OrderRepository          │
│    ← Feign adapter implements InventoryClient        │
└─────────────────────────────────────────────────────┘





What is a DTO?
DTO = Data Transfer Object. It is a simple object whose only job is to carry data between layers or across a network boundary. No business logic, no database annotations, just fields and their validation rules.
WITHOUT DTO (wrong way):              WITH DTO (right way):

Client sends JSON                     Client sends JSON
      ↓                                     ↓
Controller receives it           Controller receives CreateOrderRequest
      ↓                                     ↓
Passes raw Order entity          Passes CreateOrderRequest (DTO)
to service                       to service
      ↓                                     ↓
Service saves Order              Service maps DTO → Order entity
      ↓                                     ↓
Returns Order entity             Service maps Order → OrderResponse (DTO)
to controller                    to controller
      ↓                                     ↓
Controller returns               Controller returns
Order entity as JSON             OrderResponse as JSON
(exposes @Entity, @Column,       (exposes ONLY what client needs)
 version fields, lazy proxies)

Why You Need DTOs — 4 Concrete Reasons
Reason 1: Protect your domain model from the outside world
Reason 2: API contract stability

Your database schema will change. Column renamed, table split, new field added.
Without DTOs, every schema change breaks your API contract — clients have to update too.
With DTOs, you absorb the change in the mapper layer and the API response stays identical.

DB change: rename "customerId" to "customer_reference_id"

Without DTO: API response field changes → client breaks
With DTO:    Mapper reads new field name → maps to same "customerId" in response → client unaffected

Reason 3: Validation belongs on input, not on entities
@NotBlank and @Min on a JPA entity is asking the wrong layer to do the job. The entity should only care about database constraints. 
The DTO is the right place to validate what the client sends.

Reason 4: Different operations need different shapes

One entity, many views of it:

Same Order entity → 4 different DTOs:

CreateOrderRequest  → what client sends to CREATE  (no id, no status, no timestamps)
UpdateOrderRequest  → what client sends to UPDATE   (no id, no timestamps)
PatchOrderStatusRequest → what client sends to PATCH (just the status field)
OrderResponse       → what client receives back     (everything including id, timestamps)


## How DTOs Fit in the Overall Flow

HTTP Request (JSON)
      │
      ▼
  Controller
  @Valid @RequestBody CreateOrderRequest  ← DTO arrives here, validation fires
      │
      │  passes DTO to →
      ▼
  OrderServiceImpl.createOrder(CreateOrderRequest)
      │
      │  maps DTO → domain entity (Order) internally
      │  does business logic
      │  saves entity
      │  maps entity → response DTO
      │
      │  returns →
      ▼
  Controller
  returns ResponseEntity<OrderResponse>   ← response DTO leaves here
      │
      ▼
HTTP Response (JSON)

mvn compile
# BUILD SUCCESS

# Also manually verify the validation cascade logic makes sense:
# Ask yourself: what happens if client sends this payload?
#
# Case 1: {"customerId": "", "items": [...]}
#   → @NotBlank fires on customerId → 400 Bad Request ✅
#
# Case 2: {"customerId": "cust-1", "items": []}
#   → @NotEmpty fires on items → 400 Bad Request ✅
#
# Case 3: {"customerId": "cust-1", "items": [{"productId":"P1","quantity":-1,"unitPrice":10}]}
#   → @Valid cascades → @Min fires on quantity → 400 Bad Request ✅
#
# Case 4: {"customerId": "cust-1", "items": null}
#   → @NotEmpty fires (covers null too) → 400 Bad Request ✅


## What You Can Now Explain in an Interview

**"Why not just pass the Order entity directly from controller to service?"**

Three problems:
 first, it exposes JPA internals like Hibernate proxies and `@Version` to your API contract.
 Second, Jackson will try to serialize lazy-loaded collections and throw `LazyInitializationException`.
 Third, your API becomes coupled to your schema — rename a database column and your API contract breaks for every client.

**"Why use Java records for DTOs?"**

Records are immutable by default — no setters means nobody can accidentally modify a request after it's been validated.
They auto-generate `equals()`, `hashCode()`, and `toString()` which matters for logging and testing.
And they're significantly less code than a class with a constructor, getters, and Lombok annotations.

**"What does `@Valid` on the list actually do?"**

Without `@Valid`, Spring validates `CreateOrderRequest` and stops. The `List<OrderItemRequest>` is checked to be not-empty, but the objects inside it are never inspected. `@Valid` triggers cascading validation — Spring iterates every `OrderItemRequest` in the list and runs its constraints too. Remove it and a client can send `quantity: -999` without any error.


Phase 4

What is the Service Layer?
It is the brain of your application. It is the only place where business logic lives. Every other layer either delivers data to it or stores data from it.
CONTROLLER        "I received a POST /orders request with this JSON"
     ↓             passes CreateOrderRequest DTO
SERVICE LAYER     "Let me think about what to do with this"
     │             - Is the customer valid?
     │             - Can inventory fulfill this?
     │             - What should the total be?
     │             - What status should it start at?
     │             - What happens if inventory is down?
     ↓             passes Order entity
REPOSITORY        "I'll store whatever you give me"
The controller does not think. The repository does not think. Only the service thinks.

What Does "Business Logic" Actually Mean?
It means rules that come from the business, not from technology. Examples in this project:
TECHNOLOGY concern (NOT service layer):
  - "Save this to PostgreSQL"           → Repository's job
  - "Deserialize this JSON"             → Controller/Jackson's job
  - "Return HTTP 201"                   → Controller's job

BUSINESS concern (SERVICE LAYER's job):
  - "An order needs at least one item"
  - "You can't confirm an already-cancelled order"
  - "Total = sum of (quantity × unitPrice) for each item"
  - "Before creating an order, check inventory has enough stock"
  - "If the order is cancelled, release the reserved inventory"
  - "A DELIVERED order can never go back to PENDING"

When is the Service Layer Called?
HTTP Request arrives
        │
        ▼
   [ Controller ]
   validates input (Bean Validation)
   calls service method
        │
        ▼
   [ Service ]      ← YOU ARE HERE
   runs business rules
   calls repository (to read/write DB)
   calls external clients (Inventory-Service)
   maps entities to DTOs
   returns response DTO
        │
        ▼
   [ Controller ]
   wraps DTO in ResponseEntity
   returns HTTP response
The service is called once per use case. One HTTP request = one service method call. The service may internally make multiple repository calls and external API calls, but the controller only ever calls one service method.

Why a Separate Service Layer — Can't the Controller Just Do It?
Look at what happens if you put business logic in the controller:
java// THE WRONG WAY — fat controller anti-pattern
@PostMapping
public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {

    // Business logic 1 — total calculation
    BigDecimal total = request.items().stream()
        .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Business logic 2 — inventory check
    for (var item : request.items()) {
        boolean ok = inventoryFeignClient.reserveStock(item.productId(), item.quantity());
        if (!ok) throw new RuntimeException("No stock");
    }

    // Persistence
    Order order = new Order();
    order.setCustomerId(request.customerId());
    order.setTotalAmount(total);
    orderJpaRepository.save(order);

    return ResponseEntity.ok(order);
}


**Problems:**
- You cannot unit test this without starting an HTTP server, a database, and a Feign client simultaneously
- If you add a CLI interface or a message queue consumer that also creates orders, you copy-paste all this logic again
- One class is doing routing + validation + business logic + persistence — impossible to reason about

**The service layer solves all three:**

Test the business logic   → mock OrderRepository + mock InventoryClient
                            zero Spring, zero HTTP, zero DB
                            runs in milliseconds

Reuse the logic           → HTTP controller calls service
                            Kafka consumer calls same service
                            Scheduled job calls same service
                            Logic lives in ONE place

Single responsibility      → Controller routes, Service thinks, Repository stores


---

## The Transaction Boundary — Critical Interview Topic

The service layer owns the **database transaction**. This is not optional — it is the fundamental reason the service exists as a separate layer.

@Transactional
createOrder() {
    ┌─────────────────────────────────────┐
    │  TRANSACTION STARTS                 │
    │                                     │
    │  1. reserveStock() ← HTTP call      │  ← NOT in transaction
    │     (Inventory-Service)             │     (external system)
    │                                     │
    │  2. orderRepository.save(order)     │  ← IN transaction
    │                                     │
    │  3. orderRepository.save(items)     │  ← IN transaction
    │                                     │
    │  TRANSACTION COMMITS ✅             │
    └─────────────────────────────────────┘

If step 3 fails → entire transaction rolls back
                → step 2 is undone automatically
                → database is consistent
Without @Transactional on the service, steps 2 and 3 are separate database operations. If step 3 fails, step 2 is already committed — you have a partial order in the database with no items.


cd order-service
mvn test

# What success looks like:
# [INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
# [INFO] BUILD SUCCESS

# Run with detailed output to see each test name
mvn test -Dsurefire.useFile=false

# Check coverage report was generated
open target/site/jacoco/index.html
# Service layer should show ~90%+ coverage


Phase 4 Complete — Final Validation
bashmvn compile && mvn test

find target/classes -name "*.class" | grep -E "service|exception" | sort
# Should show:
# config/exception/InsufficientStockException.class
# config/exception/InvalidOrderStateException.class
# config/exception/OrderNotFoundException.class
# domain/service/OrderMapper.class
# domain/service/OrderServiceImpl.class

git add .
git commit -m "feat(service): complete Phase 4 — service layer with passing unit tests"
git push origin main


---

## What You Can Now Explain in an Interview

**"Why are your unit tests not using @SpringBootTest?"**

Because `@SpringBootTest` boots the entire application context — security, database, Feign, all of it. That is an integration test. A unit test proves one class works in isolation. With Mockito, my 10 tests run in under 500ms total. If I used `@SpringBootTest` for these, I'd be waiting 30 seconds to test pure Java logic that has nothing to do with Spring.

**"Where does your transaction boundary sit?"**

On the service layer. `@Transactional` on `OrderServiceImpl` means the entire method runs in one database transaction. If saving order items fails after saving the order header, the whole transaction rolls back automatically. The controller and repository have no transaction awareness — that responsibility belongs entirely to the service.

**"How do you prevent a client from sending totalAmount: 0.01 for a $500 order?"**

The `CreateOrderRequest` DTO does not even have a `totalAmount` field. The client sends line items with quantities and unit prices. The service recalculates the total server-side every single time. The client never influences money calculations.

---

## What's Next
Phase 5 → Persistence Adapter
  └── OrderJpaRepository.java   (Spring Data interface)
  └── OrderPersistenceAdapter.java  (implements domain's OrderRepository port)
  └── V1__create_orders_table.sql
  └── application.yml

What is a Persistence Adapter?
It is the bridge between your domain world and the database world. It translates domain language into database operations and back.
DOMAIN WORLD                    DATABASE WORLD
(pure Java, no framework)       (JPA, Hibernate, PostgreSQL)

OrderRepository interface  →→→  OrderPersistenceAdapter  →→→  OrderJpaRepository
(what the service needs)        (the bridge/translator)        (Spring Data JPA)
                                                               (actual SQL)
Think of it like a power adapter when you travel:
Your laptop (domain)  →  Power Adapter  →  Wall socket (database)

Your laptop doesn't know or care what country's socket it is.
The adapter handles the conversion.
Same principle here.

Why Does This Layer Exist Separately?
Without it, your service would directly use Spring Data JPA:
java// WITHOUT persistence adapter — wrong way
@Service
public class OrderServiceImpl {

    // Service directly depends on JPA repository
    private final OrderJpaRepository jpaRepository;  // ← Spring Data JPA

    public OrderResponse createOrder(...) {
        jpaRepository.save(order);  // ← JPA leaks into domain
    }
}


**Three problems this creates:**

### Problem 1 — Domain depends on framework

`OrderJpaRepository` extends `JpaRepository` which is a Spring Data interface. Your domain layer now has a compile-time dependency on Spring Data JPA. You cannot use your domain without Spring. You cannot test your service without a database.

### Problem 2 — Cannot swap database technology

If you want to move from PostgreSQL to MongoDB or DynamoDB, you have to change `OrderServiceImpl` — the brain of your application — to swap database libraries. A domain change should never be caused by an infrastructure decision.

### Problem 3 — Spring Data methods leak into domain

`JpaRepository` gives you methods like `flush()`, `saveAndFlush()`, `findAllById()`, `getOne()` — database-specific concepts that have no business meaning. Your domain port only exposes what the domain needs: `save`, `findById`, `findAll`, `existsById`, `deleteById`. Clean, meaningful, no database noise.

---

## How These Three Files Relate to Each Other

┌─────────────────────────────────────────────────────────────────┐
│                    WHAT EACH FILE DOES                          │
│                                                                 │
│  OrderJpaRepository.java                                        │
│  ─────────────────────                                          │
│  A Spring Data interface. You write ZERO implementation.        │
│  Spring generates the SQL at startup automatically.             │
│  Extends JpaRepository → gets save/find/delete for free.        │
│                                                                 │
│  OrderPersistenceAdapter.java                                   │
│  ────────────────────────────                                   │
│  Implements domain's OrderRepository port.                      │
│  Wraps OrderJpaRepository internally.                           │
│  This is the only class that knows about both worlds.           │
│  Service never sees JpaRepository — only this adapter.          │
│                                                                 │
│  V1__create_orders_table.sql                                    │
│  ───────────────────────────                                    │
│  Flyway migration. Runs once at startup to create the schema.   │
│  Version controlled. Never changes once applied.                │
│  The ground truth for your database structure.                  │
└─────────────────────────────────────────────────────────────────┘


---

## The Full Call Chain — With Persistence Adapter

POST /api/v1/orders
        │
        ▼
OrderController.createOrder(CreateOrderRequest)
        │  calls
        ▼
OrderServiceImpl.createOrder(CreateOrderRequest)      ← domain layer
        │  calls orderRepository.save(order)
        │  (orderRepository is the PORT INTERFACE)
        ▼
OrderPersistenceAdapter.save(Order)                   ← adapter layer
        │  calls jpaRepository.save(order)
        ▼
OrderJpaRepository.save(Order)                        ← infrastructure layer
        │  Hibernate generates SQL
        ▼
PostgreSQL                                            ← database
        │  executes: INSERT INTO order_schema.orders ...
        │  returns saved row
        ▼
(result travels back up the chain)
The service never crosses below the port interface line. It calls orderRepository.save() and does not know or care whether the implementation is JPA, MongoDB, an HTTP call, or an in-memory HashMap.

Pre-Step — Start PostgreSQL Before Writing Code
You need a running database to validate this phase end-to-end:
bash# Start only the databases from docker-compose
docker-compose up -d order-postgres

# Verify it is running
docker ps | grep order-postgres

# Connect to verify (optional but good habit)
docker exec -it order-postgres psql -U orderuser -d order_db
# Inside psql:
# \conninfo    → shows connection info
# \q           → quit

Step 1 — V1__create_orders_table.sql
Create this file before the Java code because Flyway runs it at startup and JPA validates against it:

Validate SQL
bash# Apply the migration manually to check SQL syntax
docker exec -i order-postgres psql -U orderuser -d order_db < \
  order-service/src/main/resources/db/migration/V1__create_orders_table.sql

# Verify tables were created
docker exec -it order-postgres psql -U orderuser -d order_db -c \
  "\dt order_schema.*"

# Expected output:
#             List of relations
#   Schema      |    Name     | Type  |   Owner
# order_schema  | order_items | table | orderuser
# order_schema  | orders      | table | orderuser


In a Spring Boot microservice, `application.yml` is the **Environment Blueprint**. It defines how the Java code interacts with external infrastructure (Databases, other Services, and Monitoring tools) without hardcoding values into the logic.

Here is an explanation of its contribution to the application flow, categorized by the "Professional Why" provided in your code:

---

### **1. The Startup Flow (The "Safety Check" Phase)**
Before the service even starts accepting HTTP requests, the `application.yml` orchestrates two critical checks:

* **Flyway (`flyway.enabled: true`)**: The app first looks at `db/migration`. It executes the SQL scripts to ensure the database table structure exists. 
* **JPA Validation (`ddl-auto: validate`)**: Once Flyway is done, Hibernate compares your Java `@Entity` classes against the actual database. If you added a field in Java but forgot the SQL migration, **the app fails to start.**
    > **Contribution:** This prevents "Runtime Surprises" where a user tries to save an order and the app crashes because a column is missing.

---

### **2. The Database Flow (Performance & Efficiency)**
The `hikari` and `jpa.properties` sections control the "pipes" connecting your app to PostgreSQL:

* **Connection Pooling (`maximum-pool-size: 10`)**: Instead of opening a new slow connection for every order, the app keeps 10 "warm" connections ready.
* **Batching (`batch_size: 25`)**: If an order has 10 items, the `application.yml` tells Hibernate to send **one** big database command instead of 10 small ones.
    > **Contribution:** This significantly reduces latency (the time a user waits for a "Success" message).

---

### **3. The Request Flow (The "Clean Logic" Phase)**
* **Open-in-View (`false`)**: This is a critical senior-level setting. It forces the database connection to close as soon as the `@Service` method finishes. 
    > **Contribution:** It prevents the **N+1 Problem**, where the app accidentally makes dozens of tiny database calls while trying to turn an Order into JSON for the user.
* **Logging Levels**: It keeps the console clean. By setting `SQL: OFF`, you ensure that in production, your logs aren't flooded with thousands of lines of database queries, making it easier to find actual errors.

---

### **4. The Integration Flow (External Connectivity)**
* **Environment Variables (`${DB_USERNAME:orderuser}`)**: This allows the exact same code to run on your Mac (using `orderuser`) and in a Kubernetes cluster (using secret production credentials) without you touching the file.
* **Feign Client (`inventory-service.url`)**: This tells the `order-service` exactly where to find the Inventory service.

---

### **5. The Observability Flow (Health & Monitoring)**
* **Actuator & Prometheus**: This "exposes" internal statistics. 
    > **Contribution:** A monitoring tool like Prometheus can "scrape" these settings to create a graph showing how many orders were placed in the last hour or if the database connection pool is currently full.

---

### **Interview Summary Tip**
If an interviewer asks about your configuration strategy, say:
> *"I treat my `application.yml` as a **Zero-Change Configuration**. I use environment variable placeholders with local fallbacks so the app is portable. I also strictly disable `open-in-view` and use `ddl-auto: validate` alongside **Flyway** to ensure the database schema is version-controlled and safe for production."*



**Since your `application.yml` is now fully explained and configured, would you like to create the `V1__create_orders_table.sql` file so Flyway can actually build your tables?**
