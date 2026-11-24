# Folio Pro API

Java 21 / Spring Boot REST backend for the Angular Folio Pro prototype. The frontend repository is `omkarchorghe16/folio-pro-ui`; this API keeps its US/India watchlist workflow and DTO fields compatible with `wealth.models.ts`. It follows the same Java 21 / Spring Web / JPA / PostgreSQL / OpenAPI approach as `folio-market-feed-svc`, and both services share the `folio_pro` database (this service owns its tables through Flyway).

## How the Angular app uses this API

The Folio Pro UI (`../folio-pro-ui`, http://localhost:4200) has two watchlist
pages, `/watchlists/us` and `/watchlists/india`. Both call this service directly at
`http://localhost:8081` (CORS origin `FRONTEND_ORIGIN`):

- `GET /watchlists?market=US|USA|India` (every watchlist with all its tickers, not paginated) to fill the watchlist dropdown; favorites come first.
- `PATCH /watchlists/{id}/favorite` when the user clicks the star in the dropdown.
- `POST /watchlists/import?market=USA|India&userId=1` for "Import CSV".
- `GET /watchlists/export?market=US|India&userId=1&watchlistId=…` for "Export CSV".
- `POST`/`DELETE /watchlists/us/{id}/items` and `/watchlists/india/{id}/items` to add and remove stocks.
- `GET`/`PUT /users/1/watchlist-column-preferences` for "Edit columns".

Prices, market cap and fundamentals are **not** stored here. The UI loads them per symbol from
`folio-market-feed-svc` (port 8082): FMP profiles for US, Tapetide profiles for India.

Contract notes: IDs are numeric; pages are zero-based with `size` 1–100 (default 15); bulk item adds
are atomic and duplicate symbols fail with 400. There is no authentication: `userId` defaults to `1`
everywhere and is not authorization-checked, so this service is **development-only** and must not be
publicly exposed. Add Spring Security and real ownership checks before production.

## Endpoints

All paths use `/api/v1`. OpenAPI is available at `/v3/api-docs`; Swagger UI at `/swagger-ui.html`.
See [docs/API_ENDPOINTS.md](docs/API_ENDPOINTS.md) for a per-endpoint reference, including the database tables and queries each endpoint uses.

| Method | Path | Purpose |
|---|---|---|
| GET, POST | `/portfolios` | Paginated list / create (page=0,size=15) |
| GET, PUT, DELETE | `/portfolios/{id}` | Read, replace portfolio and holdings, delete |
| GET | `/watchlists?market=US\|India` | Every watchlist (not paginated) with every ticker nested in its `items` array; omit market for both |
| POST | `/watchlists` | Create `{name,market,userId?}`; `userId` defaults to `1`. If that user already has a watchlist with the same name in the same market (case-insensitive), the existing one is returned instead of creating a duplicate |
| POST | `/watchlists/import?market=USA&userId=1` | Import a column-oriented CSV (`multipart/form-data`, field `file`); each header is a watchlist name and cells beneath it are symbols. `market=USA` (or `US`) writes to `watchlists_usa`, `market=India` to `watchlists_india`; both store `userId` (default `1`). Market defaults to `USA`. Imports only add: a column whose name matches one of that user's watchlists in the same market (case-insensitive) appends its new symbols there and skips ones already present; other columns create new watchlists. Nothing is deleted. |
| GET, PATCH, DELETE | `/watchlists/{id}` | Read with all items / rename (400 if the owner already has another watchlist with that name in the market) / delete |
| PATCH | `/watchlists/{id}/favorite?userId=1` | Star or un-star a USA or India watchlist: body `{"isFavorite": true}` |
| POST | `/watchlists/{id}/items` | Atomic bulk-add `{items:[...]}` |
| GET | `/watchlists/export?market=US\|USA\|India&userId=1[&watchlistId=]` | CSV download in the import format: row 1 holds the watchlist names, one per column, and rows 2+ hold each watchlist's ticker symbols in its column. One list with `watchlistId`, else all of the user's lists in the market. Cells starting with `=`, `+`, `-` or `@` get a leading `'` so spreadsheets show them as plain text (never a formula or hyperlink); the import removes it. 404 if the list is not the user's in that market |
| POST | `/watchlists/india/{id}/items?userId=1` | Atomic bulk-add of NSE stocks into `watchlist_items_india`; 201 with the saved items. 404 if the list is a USA list or belongs to another user; 400 on a duplicate symbol. Symbols may contain `. - & $` (e.g. `M&M`, `BRK.B`, `ABR$D`) |
| PUT, DELETE | `/watchlists/{id}/items/{symbol}` | Update or remove one symbol |
| DELETE | `/watchlists/india/{id}/items/{symbol}?userId=1` | Remove one stock from an India watchlist owned by the user (204). 404 for a USA list, another user's list, or a symbol not in the list |
| POST | `/watchlists/us/{id}/items?userId=1` | Same as the India bulk-add, into `watchlist_items_usa`; 404 for an India list or another user's list |
| DELETE | `/watchlists/us/{id}/items/{symbol}?userId=1` | Remove one stock from a USA watchlist owned by the user (204); 404 otherwise |
| GET, PUT | `/users/{userId}/watchlist-column-preferences` | The user's watchlist table columns and order (GET returns 404 until first saved) |

Watchlist items contain `symbol`, `name`, and `isFavorite` (false by default). These items are only for symbol tracking/favorites; market quote and technical data are not stored in these tables. USA items are stored in `watchlist_items_usa` and reference `watchlists_usa`; India items are stored in `watchlist_items_india` and reference `watchlists_india`. Portfolio holdings include `symbol`, `instrument`, `quantity`, `averageCost`, `marketValue`, and `currency`.

The watchlist-list response is a JSON array of every matching watchlist, each with all of its ticker items (there is no pagination; `page`/`size` are no longer read):

```json
[
  {
    "id": 1,
    "name": "Long-term ideas",
    "market": "US",
    "currency": "USD",
    "userId": 1,
    "isFavorite": true,
    "items": [
      {"id": 10, "symbol": "AAPL", "name": "Apple Inc.", "isFavorite": true}
    ]
  }
]
```

Errors have `{timestamp,status,error,message,details}`. Validation errors map field names to messages. Watchlists are sorted with favorites first, then by ID; other lists are sorted by ID. Duplicate item symbol (case-insensitive) returns HTTP 400; missing resource returns 404. CSV imports validate the complete file before saving and save all watchlists/items atomically; duplicate names or symbols within the file, malformed CSV, and invalid symbols fail the import. Re-importing never removes existing stocks: same-named watchlists of the same user and market receive the new symbols, and the response reports `watchlistsCreated`, `watchlistsUpdated`, `itemsCreated` and `itemsSkipped`.

Watchlists and items are stored in separate USA and India tables: `watchlists_usa`/`watchlist_items_usa` and `watchlists_india`/`watchlist_items_india`. Each items table has a foreign key to its corresponding watchlist table. Flyway migrations move existing rows into the market-specific tables, preserve IDs and item links, remove quote/technical columns, and initialize `is_favorite` to false. All watchlist endpoints accept `US`, `USA` or `India` (case-insensitive) for `market`; values are stored and returned as `US` or `India`.

USA and India watchlists both have `user_id` (default `1`) and `is_favorite` (default `false`) columns. The create-watchlist request can include `userId`, and CSV import accepts it as a query parameter. The favorite endpoint returns 404 for a watchlist owned by another user. The user ID is a development placeholder and is not authenticated.

### Postman

Import `postman/Folio-Pro-API.postman_collection.json` and `postman/Folio-Pro-Local-and-Dev.postman_environment.json` into Postman. The shared environment contains `localBaseUrl`, `devBaseUrl`, and the active `baseUrl` used by requests. `baseUrl` defaults to `http://localhost:8081`; replace it with `devBaseUrl` when testing a deployed dev API. Update the `devBaseUrl` placeholder with your actual dev server URL first. To run locally with the Spring `dev` profile and its sample data:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

In the collection, the CSV import request uses `multipart/form-data`; choose the CSV file in its `file` form-data field. `importMarket` is `USA` by default; change it to `India` to write the imported lists to `watchlists_india`. The two export requests use the same `importMarket` and `userId`; use **Send and Download** to save the CSV. Create portfolio/watchlist requests update `portfolioId` and `watchlistId` from their responses. Delete requests are explicitly marked destructive.

### Examples

```bash
curl 'http://localhost:8081/api/v1/watchlists?market=US'
curl -X POST http://localhost:8081/api/v1/watchlists -H 'Content-Type: application/json' \
  -d '{"name":"Long-term ideas","market":"US","userId":1}'
curl -X POST 'http://localhost:8081/api/v1/watchlists/import?market=USA&userId=1' \
  -F 'file=@robinhood_watchlists_by_column.csv'
curl -OJ 'http://localhost:8081/api/v1/watchlists/export?market=USA&userId=1'   # saves us-watchlists.csv
curl -X POST http://localhost:8081/api/v1/watchlists/1/items -H 'Content-Type: application/json' \
  -d '{"items":[{"symbol":"AAPL","name":"Apple Inc.","isFavorite":true},{"symbol":"MSFT","name":"Microsoft","isFavorite":false}]}'
curl -X PUT http://localhost:8081/api/v1/portfolios -H 'Content-Type: application/json' \
  -d '{"name":"Core account","country":"United States","currency":"USD","assetType":"Stocks & ETFs","holdings":[{"symbol":"AAPL","instrument":"Apple Inc.","quantity":10,"averageCost":150,"marketValue":null,"currency":"USD"}]}'
```

## Prerequisites / run

- JDK 21, Maven 3.9+, PostgreSQL 15+.
- Set `POSTGRES_URL` (JDBC URL), `POSTGRES_USER`, and `POSTGRES_PASSWORD`; defaults are local development convenience only. `FRONTEND_ORIGIN` defaults to `http://localhost:4200`; use an exact origin, not wildcard. `SERVER_PORT` defaults to 8081.
- `APP_LOG_LEVEL` controls application package logging and defaults to `INFO`; use `DEBUG` locally
  when investigating application behavior.
- Create a PostgreSQL database `folio_pro`, then run:

```bash
export POSTGRES_URL=jdbc:postgresql://localhost:5432/folio_pro
export POSTGRES_USER=folio_pro
export POSTGRES_PASSWORD=admin
export FRONTEND_ORIGIN=http://localhost:4200
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn test
```

Flyway applies `src/main/resources/db/migration`. Optional `dev` profile seeds two sample watchlists only when watchlists are empty; it inserts no holdings and never claims live prices. Keep secrets in environment/secret manager; no provider credentials are configured. No market-data provider is integrated; the API does not serve prices or quotes.

## Docker Compose

Docker Compose runs only the API, available at `http://localhost:8081`. It does not start PostgreSQL:
the container connects to the shared `folio_pro` database on the Mac, so that server must be running
(`brew services start postgresql@16`) and accept connections from Docker Desktop.

```bash
cp .env.example .env
# Set POSTGRES_PASSWORD to a local-only value in the ignored .env file.
mvn -DskipTests package
docker compose up --build -d
docker compose ps
```

The API container connects to `host.docker.internal:${POSTGRES_HOST_PORT}` (default `5432`). Spring
Boot run directly on the host uses the same database through the configured default
`localhost:5432`. Stop the container with `docker compose down`; it never touches database data,
because the database lives outside Compose. Do not commit `.env` or put secrets in the Docker image.

Application controller/service invocation logs and exceptions are written to standard output.
Follow container logs with `docker compose logs -f folio-pro-api`; set `APP_LOG_LEVEL=DEBUG` in
`.env` to increase application detail. Method arguments and request bodies are intentionally not
logged. Unexpected API exceptions include a stack trace in the server logs while the client receives
the existing sanitized 500 response.

## Optional Docker Desktop Kubernetes

The `k8s/` manifests and `scripts/deploy-local-k8s.sh` provide a local-only Docker Desktop
deployment. Build the image first, create an ignored `application-k8s.env` with database connection
values reachable from Docker Desktop Kubernetes (for a Mac-hosted PostgreSQL instance,
`POSTGRES_URL=jdbc:postgresql://host.docker.internal:5432/folio_pro`), and deploy with:

```bash
mvn -DskipTests package
docker build -t folio-pro-api:local .
# Store POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD, and FRONTEND_ORIGIN in
# application-k8s.env (KEY=value, one per line); this file is ignored by git.
bash scripts/deploy-local-k8s.sh local
```

Set `APP_SECRETS_FILE` to use a different env file. The script refuses to deploy unless the active
Kubernetes context is `docker-desktop`, the image exists locally, and the env file is present. It
creates/updates a Kubernetes Secret from that ignored env file and verifies readiness. No cloud
deployment or registry publishing is configured.

## Engineering conventions

Repository-specific Copilot guidance is in `.github/copilot-instructions.md`, with Java coding,
architecture, Spring Boot, and logging rules under `.github/instructions/`. Reusable workflows for
architecture, JPA, REST APIs, testing, logging, and future provider integrations are under
`.github/skills/`. These instructions supplement the existing implementation and do not introduce
news-scheduler jobs, notification channels, or provider credentials into this application.

## Tests and limitations

`mvn test` runs Spring integration tests against H2 (`src/test/resources/test-schema.sql`, which must be kept in step with the Flyway migrations). They cover CRUD, bulk duplicate handling, listing every watchlist and item without pagination, request validation, CSV import into both markets (including adding to existing same-named watchlists), CSV export and its round trip back through the import, one watchlist name per user and market, favorites and their ordering, and column preferences. There is intentionally no authentication or multi-user authorization, and this API serves no market data or financial advice. Known gaps are listed in [docs/API_ENDPOINTS.md](docs/API_ENDPOINTS.md#5-known-issues--improvement-notes).
