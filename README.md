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
