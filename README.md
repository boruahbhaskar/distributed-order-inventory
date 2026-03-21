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
