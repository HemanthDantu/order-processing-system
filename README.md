# Order Processing System

A Spring Boot backend service for an e-commerce order processing workflow. The service supports customer and admin authentication, order creation with idempotency protection, order lookup/listing, status transitions, cancellation, automatic scheduled processing, and a manual admin scheduler trigger.

The project is intentionally implemented as a backend API with PostgreSQL, Flyway-managed schema migrations, JWT bearer authentication, and Swagger/OpenAPI documentation.

## Tech Stack

- Java 17
- Spring Boot 3.5
- Spring Web
- Spring Security with JWT bearer authentication
- Spring Data JPA and Hibernate
- PostgreSQL 16
- Flyway database migrations
- springdoc-openapi / Swagger UI
- Maven Wrapper
- Docker Compose for local PostgreSQL

## Prerequisites

- Java 17
- Docker and Docker Compose
- A shell with `curl`

No Spring Boot app Dockerfile is included. The app is run locally with Maven, while PostgreSQL runs in Docker Compose.

## Run PostgreSQL

Start PostgreSQL:

```bash
docker compose up -d
```

The database runs with:

- Host port: `5433`
- Database: `orders_db`
- Username: `orders_user`
- Password: `orders_password`
- Container name: `order-postgres`
- Persistent Docker volume: `orders-postgres-data`

Check container status:

```bash
docker compose ps
```

Stop PostgreSQL:

```bash
docker compose down
```

Reset all local database data:

```bash
docker compose down -v
docker compose up -d
```

## Run the Application

Start the Spring Boot app:

```bash
./mvnw spring-boot:run
```

The app starts on:

```text
http://localhost:8080
```

The app connects to PostgreSQL at:

```text
jdbc:postgresql://localhost:5433/orders_db
```

Flyway applies the schema and seed migrations automatically on startup. Hibernate is configured with:

```yaml
spring.jpa.hibernate.ddl-auto=validate
```

This means Hibernate validates the entity mappings against the Flyway schema instead of creating or updating tables.

## Swagger / OpenAPI

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

Swagger is configured with a JWT bearer security scheme. After logging in, copy the `accessToken`, click `Authorize` in Swagger UI, and enter the raw token value:

```text
<accessToken>
```

Swagger UI adds the `Bearer` prefix for you.

## Seeded Users

Flyway seeds two demo users. Both use the password:

```text
password
```

| Username | Role |
| --- | --- |
| `customer1` | `CUSTOMER` |
| `admin1` | `ADMIN` |

Flyway also seeds one demo `PENDING` order for `customer1`.

## Login Examples

Customer login:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"customer1","password":"password"}'
```

Admin login:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin1","password":"password"}'
```

For convenience, store tokens in shell variables:

```bash
CUSTOMER_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"customer1","password":"password"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin1","password":"password"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
```

## API Examples

### Create Order

`POST /api/v1/orders`

Order creation requires authentication and an `Idempotency-Key` header.

```bash
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: order-create-001" \
  -H 'Content-Type: application/json' \
  -d '{
    "items": [
      {
        "productId": "keyboard-001",
        "productName": "Mechanical Keyboard",
        "quantity": 2,
        "unitPrice": 49.99
      },
      {
        "productId": "mouse-001",
        "productName": "Wireless Mouse",
        "quantity": 1,
        "unitPrice": 25.00
      }
    ]
  }'
```

The authenticated user becomes the order customer. The request body does not accept `customerId`.

Save the returned order id. Use this order for retrieval, listing, and status-update examples:

```bash
ORDER_ID=<paste-order-id-here>
```

### Get Order

`GET /api/v1/orders/{orderId}`

Customers can retrieve only their own orders. Admins can retrieve any order.

```bash
curl -s http://localhost:8080/api/v1/orders/$ORDER_ID \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```

### List Orders

`GET /api/v1/orders?page=0&size=20&status=PENDING`

Admin-only endpoint. `status` is optional.

```bash
curl -s 'http://localhost:8080/api/v1/orders?page=0&size=20&status=PENDING' \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Page size is capped at `100`.

### Update Order Status

`PATCH /api/v1/orders/{orderId}/status`

Admin-only endpoint.

```bash
curl -s -X PATCH http://localhost:8080/api/v1/orders/$ORDER_ID/status \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "status": "PROCESSING",
    "reason": "Begin fulfillment"
  }'
```

### Cancel Order

`POST /api/v1/orders/{orderId}/cancel`

Customers can cancel their own `PENDING` orders. Admins can cancel any `PENDING` order. Already cancelled orders return the current cancelled state idempotently.

If you already used `ORDER_ID` in the status-update example above, that order is now `PROCESSING` and cannot be cancelled. Create a fresh pending order for this cancellation example:

```bash
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: cancel-demo-001" \
  -H 'Content-Type: application/json' \
  -d '{
    "items": [
      {
        "productId": "cancel-product",
        "productName": "Cancellation Demo Product",
        "quantity": 1,
        "unitPrice": 12.50
      }
    ]
  }'
```

Save the returned id:

```bash
CANCEL_ORDER_ID=<paste-cancel-demo-order-id-here>
```

```bash
curl -s -X POST http://localhost:8080/api/v1/orders/$CANCEL_ORDER_ID/cancel \
  -H "Authorization: Bearer $CUSTOMER_TOKEN"
```

### Manual Scheduler Trigger

`POST /api/v1/admin/scheduler/order-processing/trigger`

Admin-only endpoint. It runs the same service method used by the scheduled job.

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/scheduler/order-processing/trigger \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Example response:

```json
{
  "processedCount": 3,
  "durationMs": 15,
  "startedAt": "2026-06-07T15:00:00Z",
  "completedAt": "2026-06-07T15:00:00.015Z"
}
```

## Order Status Transition Rules

Allowed transitions are centralized in `OrderStatusTransitionValidator`.

| From | To |
| --- | --- |
| `PENDING` | `PROCESSING` |
| `PENDING` | `CANCELLED` |
| `PROCESSING` | `SHIPPED` |
| `SHIPPED` | `DELIVERED` |

Invalid transitions return `400 Bad Request`.

Cancellation rules:

- `PENDING` orders can be cancelled.
- `CANCELLED` orders return the current cancelled state idempotently.
- `PROCESSING`, `SHIPPED`, and `DELIVERED` orders cannot be cancelled.

## Idempotency Behavior

Order creation uses idempotency to make retries safe.

Only this endpoint uses idempotency:

```text
POST /api/v1/orders
```

Rules:

- `Idempotency-Key` header is required.
- The service hashes a normalized representation of the request body.
- The service inserts an `IN_PROGRESS` idempotency record first.
- The database unique constraint on `(user_id, idempotency_key)` prevents duplicate processing.
- Same user + same key + same body returns the stored response.
- Same user + same key + different body returns `409 Conflict`.
- If another request with the same key is still `IN_PROGRESS`, the service returns `409 Conflict` with `Retry-After`.

Test a safe retry:

```bash
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: retry-demo-001" \
  -H 'Content-Type: application/json' \
  -d '{
    "items": [
      {
        "productId": "retry-product",
        "productName": "Retry Product",
        "quantity": 1,
        "unitPrice": 10.00
      }
    ]
  }'
```

Run the exact same command again. The response should contain the same order id and no duplicate order should be created.

## Scheduler Behavior

The scheduled order processor moves `PENDING` orders to `PROCESSING`.

Configuration:

```yaml
order:
  processing:
    scheduler:
      fixed-rate-ms: 300000
      batch-size: 500
```

Behavior:

- Runs every `300000` milliseconds by default.
- Processes up to `500` orders per run by default.
- Selects oldest `PENDING` orders first.
- Uses PostgreSQL `FOR UPDATE SKIP LOCKED` to avoid blocking competing workers.
- Writes one `order_status_history` row per processed order.
- Uses `changed_by = NULL`, `changed_by_type = SYSTEM`, and reason `Scheduled processing`.
- Leaves `CANCELLED`, `PROCESSING`, `SHIPPED`, and `DELIVERED` orders untouched.

To test scheduler behavior without waiting five minutes, call the manual trigger:

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/scheduler/order-processing/trigger \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

## Database Design Summary

Flyway owns database schema changes.

Tables:

- `users`: application users with `CUSTOMER` or `ADMIN` role.
- `orders`: order aggregate root with customer, status, currency, total amount, version, and timestamps.
- `order_items`: line items belonging to an order.
- `order_status_history`: audit log of order status changes.
- `idempotency_keys`: idempotency records for order creation.

Important design details:

- UUID primary keys are used for all tables.
- `TIMESTAMPTZ` is used for timestamps.
- `NUMERIC(12,2)` is used for money values.
- `orders.currency` defaults to `USD`.
- `orders.version` supports optimistic locking.
- Check constraints enforce valid roles, statuses, actor types, quantities, and money values.
- `order_status_history.changed_by` is nullable for system events.
- `changed_by` must be `NULL` when `changed_by_type = SYSTEM`.
- `changed_by` must be present for `CUSTOMER` and `ADMIN` history rows.

Indexes support common access patterns:

- orders by customer
- orders by status
- orders by created timestamp
- order items by order
- status history by order and actor
- idempotency lookup by user and key

## Race Conditions Considered

### Duplicate Order Creation

The create-order flow uses an insert-first idempotency strategy. It does not perform a select-before-insert check. Instead, it relies on the database unique constraint on `(user_id, idempotency_key)`.

This prevents two concurrent requests with the same key from creating duplicate orders.

### Concurrent Status Updates

`orders.version` is mapped with JPA `@Version`. Optimistic locking conflicts are mapped to `409 Conflict`.

### Concurrent Scheduler Runs

The scheduler uses:

```sql
FOR UPDATE SKIP LOCKED
```

This allows multiple scheduler instances to claim different pending orders without waiting on each other.

### Idempotency Response Persistence Trade-off

The idempotency record is inserted as `IN_PROGRESS` before order creation. After the order transaction succeeds, the serialized response is stored and the idempotency record is marked `COMPLETED`.

This makes concurrent duplicates safe, but there is a small failure window if the process crashes after the order is committed and before the response body is persisted. A production system could close this with an outbox, recovery job, or stronger transaction boundary around response persistence.

## Design Decisions and Trade-offs

- Flyway controls schema creation; Hibernate validates mappings only.
- Controllers are thin and delegate business logic to services.
- DTOs are returned from controllers; JPA entities are not exposed directly.
- Order transition rules are centralized in one validator.
- Order creation uses idempotency only where duplicate creation is most dangerous.
- Scheduler uses JDBC/native SQL for PostgreSQL-specific locking semantics.
- The Spring Boot app is not containerized yet because the assignment phase asked for PostgreSQL Docker Compose only.
- Swagger/OpenAPI is included to make review and manual testing easier.
- Centralized exception handling returns consistent JSON without stack traces or database internals.

## Testing

Run all tests:

```bash
./mvnw test
```

Current coverage includes:

- authentication flow
- JWT-secured endpoint access
- order creation calculations and history
- order retrieval/listing access rules
- status transition validation
- admin status updates
- cancellation rules
- idempotency behavior
- scheduler batch behavior
- admin manual trigger authorization
- centralized error JSON

## Deferred Improvements

These are intentionally left out or scoped for future production hardening:

- App Dockerfile
- Correlation ID filter
- Structured logging
- ShedLock for single scheduler ownership across distributed app instances
- Product catalog service
- Payment integration
- Inventory integration
- Distributed tracing
- Automated PostgreSQL integration tests with Testcontainers
- Outbox/recovery flow for idempotency completion gaps
- More detailed OpenAPI schema examples
