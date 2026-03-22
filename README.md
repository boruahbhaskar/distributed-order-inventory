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
```

**In VS Code** — you should see the Maven sidebar populate:
```
MAVEN
└── order-service
    ├── Dependencies (expandable list of all jars)
    ├── Lifecycle
    └── Plugins
```

If the Maven sidebar shows a ⚠️ icon, click it — it'll show exactly which line in `pom.xml` is wrong.

---

## Step 2 — Create `OrderStatus.java`

Create the file at:
```
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
```
Same Order entity → 4 different DTOs:

CreateOrderRequest  → what client sends to CREATE  (no id, no status, no timestamps)
UpdateOrderRequest  → what client sends to UPDATE   (no id, no timestamps)
PatchOrderStatusRequest → what client sends to PATCH (just the status field)
OrderResponse       → what client receives back     (everything including id, timestamps)
```

## How DTOs Fit in the Overall Flow
```
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

