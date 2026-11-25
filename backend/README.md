# FoodHub Spring Boot backend

This Java 21 Spring Boot module is the migration target. The PHP API under `../backenddd/` remains the current production API. Implemented routes here are profile synchronization (`GET|POST|PUT|PATCH /api/users/me`), catalog reads (`GET /api/categories`, `/api/stores`, `/api/stores/{id}`, `/api/dishes`, `/api/dishes/{id}`), and promotion reads (`GET /api/promotions`, `/api/vouchers`). Other business routes are not migrated yet.

## Environment

Configure values through process environment variables. Do not place credentials in source files or command history.

| Variable | Required | Purpose |
| --- | --- | --- |
| `DB_URL` | Yes | MySQL JDBC URL for TiDB Cloud; do not put user/password in the URL. |
| `DB_USERNAME` | Yes | TiDB database user. |
| `DB_PASSWORD` | Yes | TiDB database password. |
| `DB_CA_CERT` | No | CA certificate path; defaults to `certs/isrgrootx1.pem`. |
| `FIREBASE_PROJECT_ID` | Yes | Firebase ID-token audience and issuer project. |
| `FLYWAY_ENABLED` | No | Defaults to `false`; set `true` deliberately to run Flyway. |
| `DELIVERY_FEE` | No | Base delivery fee used by store distance estimates; defaults to `15000`. |

The application builds a temporary PKCS12 trust store from the configured CA PEM and forces MySQL Connector/J to use `sslMode=VERIFY_IDENTITY`. HikariCP is limited to five connections, a five-minute max lifetime, a ten-second connection timeout, and initializes the DB session in `+07:00`. The CA file is copied from `backenddd/certs/isrgrootx1.pem` into this module's `certs/` directory.

## Run locally

From this directory, set the required variables in the current shell, then run:

```powershell
.\gradlew.bat bootRun
```

On macOS/Linux, use `./gradlew bootRun`. `bootRun` uses the configured TiDB datasource; tests use H2 in MySQL mode and do not inherit the production datasource.

The API health URL is `/api/`; it executes `SELECT 1`. Actuator exposes only `/actuator/health`, without component details.

## Migrated read endpoints

Catalog and profile responses use typed DTOs and the PHP `data` envelope. Category, store, and dish reads support the PHP `limit`/`offset` defaults; stores accept `lat`/`lng`; dishes accept `restaurantId`, `category`, and owner/admin-only `includeUnavailable=1`. Catalog GET responses use a private 60-second cache policy and content ETags. `GET /api/promotions` and `/api/vouchers` are aliases; non-admins see currently effective vouchers only.

Run the H2 fixture-backed contract tests with `.\gradlew.bat test` on Windows or `./gradlew test` on macOS/Linux. Tests do not use TiDB or call Firebase; Firebase token verification is stubbed for endpoint tests and covered with locally signed fake tokens in filter tests.

## Existing database and Flyway

Hibernate schema generation is disabled. Flyway baseline-on-migrate uses version 1 and is disabled unless `FLYWAY_ENABLED=true`. Do not add an initial schema migration that recreates existing tables. Future incremental migrations belong in `src/main/resources/db/migration/` and start at `V2__...sql`.

## Authentication and response conventions

The Firebase filter verifies RS256 ID tokens and loads `users` by `firebase_uid`. The database role remains the authorization source; both DB-role and Android-role authorities are made available. A missing profile may pass only through the profile bootstrap routes; a disabled or soft-deleted existing profile is rejected. Use `ApiEnvelope` for `{"data":...}` responses and `ApiErrorResponse`/`ApiException` for `{"error":"..."}`. IDs in endpoint DTOs must be declared/serialized as strings to preserve the PHP contract.
