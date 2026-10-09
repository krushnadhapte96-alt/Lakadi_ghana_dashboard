# Ghana Dashboard

Full-stack operations dashboard for a mustard-oil trade shop (lakdi ghana): seed purchases,
pressing batches, bottling, sales counter, udhaar khata, expenses, returns, licences, expiry
alerts and a live owner dashboard.

- **API** â€” `ghana-dashboard-api`: Spring Boot 4 (Java 24), MySQL 8, Flyway, JWT, springdoc-openapi
- **Web** â€” `ghana-dashboard-web`: React 19 + TypeScript, Vite 8, Tailwind CSS v4, React Router 7, React Query 5

## Repository layout

```
ghana-dashboard/
â”œâ”€â”€ docker-compose.prod.yml   # VPS deployment: mysql + api + web
â”œâ”€â”€ .env.example              # compose secrets template
â”œâ”€â”€ ghana-dashboard-api/      # backend
â”‚   â”œâ”€â”€ Dockerfile
â”‚   â”œâ”€â”€ mvnw / mvnw.cmd
â”‚   â”œâ”€â”€ README.md
â”‚   â””â”€â”€ src/main/resources/db/migration  # V1..V17 schema
â””â”€â”€ ghana-dashboard-web/      # frontend
    â”œâ”€â”€ Dockerfile
    â”œâ”€â”€ nginx.conf
    â””â”€â”€ README.md
```

## Data flow

```
Seed purchase (Beej) --> seed_stock --> Batch (pressing) --> BatchCost â”€â”
                                          â”‚                              â”œ--> Sale (POS) --> Payment
                                          v                              â”‚        â”‚
                        product_batch_stock (bottling) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜        v
                                          â”‚                                    Khata ledger
                                          v                                        |
                        Sale returns ------------------------------------> Restock / Write-off
                                          â”‚
                                          v
                                   Expiry alerts / Licence reminders / Low stock
                                          â”‚
                                          v
                              Notification bell + Ctrl+K global search
```

A new sale moves stock FIFO, fires low-stock / new-order notifications, and if payment mode is
UDHAAR it posts a khata entry. Payments close the khata balance. P&L reads sales minus
`batch_costs` minus expenses.

## Local development on Windows

### Prereqs

- JDK 24 (`C:\Program Files\Java\jdk-24`)
- Node 22+ and npm
- MySQL 8.x with a Workbench connection
- IntelliJ IDEA (backend) + VS Code (frontend), though both can be run from the CLI

### MySQL Workbench setup

Run this once in a Workbench query tab:

```sql
CREATE DATABASE IF NOT EXISTS ghana_dashboard CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'ghana_app'@'localhost' IDENTIFIED BY 'Ghana@2026#Dev';
GRANT ALL PRIVILEGES ON ghana_dashboard.* TO 'ghana_app'@'localhost';
FLUSH PRIVILEGES;
```

### Run the backend

Set `JAVA_HOME` in the window, then:

```powershell
cd ghana-dashboard-api
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
.\mvnw.cmd spring-boot:run
```

IntelliJ run config: main class `com.ghana.dashboard.GhanaDashboardApiApplication`, JDK 24,
working directory `ghana-dashboard-api`, default profile is `dev` (application-dev.yml). The API
listens on `http://localhost:8080`; Swagger UI is at `/swagger-ui.html` and health at
`/api/v1/health`.

### Run the frontend

```powershell
cd ghana-dashboard-web
copy .env.example .env.development
npm install
npm run dev
```

Open `http://localhost:5173`. Base URL of the API comes from `VITE_API_BASE_URL` in
`.env.development`.

### Demo logins (dev profile only)

On first boot the dev profile seeds demo data for shop **Shri Krishna Lakdi Ghana**:

| Role  | Phone        | Password   |
| ----- | ------------ | ---------- |
| OWNER | 9876501234   | Owner@2026 |
| STAFF | 9812345670   | Staff@2026 |

To reseed, stop the app, delete those rows (`DELETE FROM shops WHERE name = ...`) or drop and
recreate the database, then start the app again. Flyway re-applies all migrations.

## Tests

```powershell
# Backend (79 integration tests, real MySQL)
cd ghana-dashboard-api
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
.\mvnw.cmd test

# Frontend (Vitest + React Testing Library)
cd ghana-dashboard-web
npm run test
```

## Environment variables (prod)

| Variable              | Meaning                                   |
| --------------------- | ----------------------------------------- |
| `DB_URL`              | JDBC URL incl. timezone/charset           |
| `DB_USER` / `DB_PASS` | DB credentials                            |
| `JWT_SECRET`          | at least 32 random chars                  |
| `JWT_ACCESS_TTL`      | access token TTL, e.g. `15m`              |
| `JWT_REFRESH_TTL`     | refresh token TTL, e.g. `7d`              |
| `CORS_ORIGINS`        | comma-separated allowed origins           |
| `UPLOAD_DIR`          | document/bill storage path                |
| `MAX_FILE_SIZE_MB`    | upload cap                                |
| `AES_KEY`             | key for encrypting bank details at rest   |

## Production (later VPS)

```bash
cp .env.example .env   # fill in secrets
docker compose -f docker-compose.prod.yml up -d --build
```

The web container (nginx) proxies `/api/` to the API. Dev stays local via Workbench MySQL; do not
run `docker compose` locally.

## Backups

- MySQL: `mysqldump -u ghana_app -p ghana_dashboard > backup.sql` (nightly cron on the VPS +
  off-box copy).
- Uploads: the `uploads` Docker volume holds bills/licence scans - include it in the VPS volume
  backup (`tar czf uploads.tgz /data/uploads`).
