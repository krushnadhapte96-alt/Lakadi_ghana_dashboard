# ghana-dashboard-api

Backend for Ghana Dashboard. Spring Boot 4, Java 24, MySQL 8, Flyway (V1â€“V17), JWT auth,
springdoc-openapi, scheduled notification jobs.

## Run locally (Windows)

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
.\mvnw.cmd spring-boot:run
```

Default profile is `dev` (see `src/main/resources/application-dev.yml`). API on
`http://localhost:8080`. Health: `/api/v1/health`. Swagger UI: `/swagger-ui.html`.

## IntelliJ run configuration

- Main class: `com.ghana.dashboard.GhanaDashboardApiApplication`
- Working directory: `ghana-dashboard-api`
- JDK 24, no extra VM options. The active profile defaults to `dev`.

## Environment variables (prod profile)

| Variable | Purpose |
| -------- | ------- |
| `DB_URL` | JDBC URL, e.g. `jdbc:mysql://mysql:3306/ghana_dashboard?useSSL=false&serverTimezone=Asia/Kolkata` |
| `DB_USER` / `DB_PASS` | DB credentials |
| `JWT_SECRET` | Min 32 chars |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | Token TTLs |
| `CORS_ORIGINS` | Allowed web origins |
| `UPLOAD_DIR` / `MAX_FILE_SIZE_MB` | File storage |
| `AES_KEY` | Encryption key for bank details |

## Key modules

| Area | Main classes |
| ---- | ------------ |
| Dashboard summary | `DashboardController`, `DashboardService` |
| Inventory/products | `InventoryController`, `InventoryService`, `ProductRepository` |
| Sales / Orders | `SaleController`, `SaleService`, `OrderController`, `OrderService` |
| Udhaar / khata | `KhataController`, `KhataService`, `KhataLedgerService` |
| Beej / production | `BatchController`, `BatchService`, `SeedStockService` |
| P&L | `ProfitLossController`, `ProfitLossService` |
| Licences | `LicenceController`, `LicenceService` |
| Notifications | `NotificationController`, `NotificationService`, `NotificationJobs`, `NotificationScheduler` |
| Global search | `SearchController`, `GlobalSearchService` |

## Notifications

- Fan-out per shop: targeted rows (`user_id`) and broadcast rows (`user_id` NULL, per-user read in
  `notification_reads`).
- 24-hour de-dupe via `dedupe_key`.
- `LOW_STOCK` and `NEW_ORDER` fire synchronously on sale/order events; the rest run in the daily
  sweep (`NotificationScheduler`, 07:30 IST) with the day-not-closed check at 22:00 IST.
- Dev-only manual triggers: `POST /api/v1/dev/notifications/jobs/{job}` (OWNER only, dev profile).
  The full set of jobs: `LOW_STOCK`, `NEW_ORDER`, `ORDER_OVERDUE`, `EXPIRY`, `LICENCE`,
  `UDHAAR_OVERDUE`, `SEED_LOW`, `SUPPLIER_DUE`, `REFILL_DUE`, `DAY_NOT_CLOSED`.

## Demo seeding

`config/DemoSeedRunner` (dev profile) creates shop "Shri Krishna Lakdi Ghana" with an OWNER and
STAFF user, 3 suppliers, packaging/products, seed purchases, batches with costs and quality notes,
customers, 60 sales, 20 orders, 5 khata parties, expenses, returns and 4 licences. It only inserts
when the shop does not already exist, and prints the demo logins at startup. Never enabled in prod:
the class is annotated `@Profile("dev")`.

## Tests

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
.\mvnw.cmd test
```

79 integration tests share one MySQL schema per run (tests roll back). Notable suites:
`DashboardFlowIntegrationTest`, `NotificationSearchIntegrationTest`, `ServiceLogicIntegrationTest`,
`ExpensePnlIntegrationTest`, `AuthFlowIntegrationTest`.
