# shop-cart-service

Shopping cart microservice of the online shop. It stores the active cart of a user and keeps
a **snapshot** of every product (name, unit price with currency and VAT rate, chosen options)
taken at the moment the product was added. Prices are never sent by the client: they are
always fetched from `product-service`.

The service is not exposed to the outside. In the full system it is called only by
`user-interaction-service`, which takes the user id from the Keycloak token (`sub`) and puts
it into the path.

## Tech stack

Java 21, Spring Boot 4, Spring Data JPA, PostgreSQL 17, Liquibase, MapStruct, Lombok,
OpenFeign + Resilience4j (retry, fallback, circuit breaker), springdoc-openapi (Swagger UI).

## API

Base path: `/carts/{userId}` (`userId` is a UUID, the Keycloak `sub`).

| Method | Path | Description | Success |
|---|---|---|---|
| GET | `/carts/{userId}` | Active cart with items and totals (empty cart if none exists) | 200 |
| POST | `/carts/{userId}/items` | Add a product (`productId`, `quantity` 1-100, `selectedOptions`) | 201 |
| PATCH | `/carts/{userId}/items/{itemId}` | Change the quantity of an item | 200 |
| DELETE | `/carts/{userId}/items/{itemId}` | Remove an item | 204 |
| DELETE | `/carts/{userId}` | Remove all items | 204 |

Swagger UI: `http://localhost:8082/swagger-ui.html`, OpenAPI JSON: `/v3/api-docs`.

Errors are returned as RFC 7807 `ProblemDetail`:

| Status | When |
|---|---|
| 400 | invalid request body or path, invalid product options, quantity above the limit |
| 404 | product not found in product-service, item not found in the active cart |
| 409 | product currency differs from the cart currency, concurrent modification |
| 503 | product-service is unavailable (after retries) |

## Behaviour worth knowing

- **Price snapshot.** `unitPrice` already includes the surcharges of the selected options.
  Later changes of the catalog price do not change items already in the cart.
- **Merging.** The same product with the same options increases the quantity of the existing
  item (and refreshes the price from the catalog). Different options create a new item.
- **One currency per cart.** A product in another currency is rejected with 409.
- **One ACTIVE cart per user** is guaranteed by a partial unique index in the database.
  A `CHECKED_OUT` cart (after an order is placed) does not block a new one.
- **Optimistic locking** (`@Version`) protects the cart against concurrent changes.

## Communication with product-service

`ProductClient` (OpenFeign) calls `POST /products/{id}/price-quote`.

- **Retry:** 3 attempts (500 ms, growing to 2 s) for 503 and network errors.
- **Error decoding:** 404 and 400 are turned into domain exceptions with the detail from
  product-service and are not retried.
- **Fallback:** `ProductClientFallbackFactory` returns 503 `ServiceUnavailable` when the
  service cannot be reached. A price is never invented.
- **Circuit breaker:** Resilience4j, configured in `application.yml`.

## Database

Own PostgreSQL database `cart_db`, schema managed by Liquibase
(`src/main/resources/db/changelog`): tables `carts` and `cart_items`
(price columns `unit_price`, `currency`, `vat_rate`; options as JSONB).
There are no foreign keys to other services: `product_id` is a plain UUID.

## Running

The whole shop is started from the `shop-infra` repository:

```
docker compose up --build
```

Locally from the IDE: start PostgreSQL and `product-service` (for example
`docker compose up -d postgres product-service` in `shop-infra`), then run
`ShopCartServiceApplication`. Defaults (`application.yml`): port `8082`, database
`jdbc:postgresql://localhost:5432/cart_db` (user/password `shop`), product-service
`http://localhost:8081`.

Environment variables used in Docker: `SPRING_DATASOURCE_URL`, `CLIENTS_PRODUCT_SERVICE_URL`.

Example requests: Bruno collection in `shop-infra/bruno/cart-service`.

## Tests

```
./mvnw test
```

Docker is required (Testcontainers).

| Kind | Class | What it checks |
|---|---|---|
| Unit | `CartServiceTest` | service logic with mocked repository and client |
| Client | `ProductClientTest` | WireMock: 200, 404, 400, 503 with retries, connection reset, fallback |
| Controller | `CartControllerTest` | `@WebMvcTest`: statuses, validation, error mapping |
| Integration | `CartApiIntegrationTest` | real PostgreSQL (Testcontainers) + WireMock instead of product-service |