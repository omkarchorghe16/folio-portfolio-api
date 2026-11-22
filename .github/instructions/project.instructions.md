---
applyTo: "**"
---

# Project rules: invesment-management-svc

Repository-specific rules. The shared rules in the other `.github/instructions/` files (managed by
dev-standards) also apply; where this file is more specific, it wins. Edit this file here.

## Layout and reference classes

- Base package `com.foliopro.api`: `controller` (HTTP), `service` (use cases, transactions),
  `repository` and `model` (JPA), `dto` (transport types), `exception/ApiExceptionHandler` (error
  mapping), `config/LoggingAspect` (controller/service logging).
- Match `PortfolioController` / `PortfolioService` and `WatchlistController` / `WatchlistService`.
  Tests: `ApiIntegrationTest`, `WatchlistColumnPreferencesIntegrationTest`, `LoggingAspectTest`.
- The project does not use Lombok; write constructors and loggers explicitly.

## Naming exceptions

- JPA entities end in `Entity`: `UsaWatchlistItemEntity`.
- Use verb + noun method names everywhere (`listWatchlists`, `createWatchlist`), including
  single-resource services.
- Wildcard imports are allowed only for the DTO holder classes (`ApiDtos.*`) and model packages that
  already use them.
- Class and method names appear in `LoggingAspect` log lines; a rename must be mentioned for anyone
  searching logs.

## Persistence

- Flyway owns the schema (`src/main/resources/db/migration`); Hibernate only validates it. Add a new
  `V<n>__<description>.sql` for every schema change and never edit an applied one.
- Every migration that changes a table must be mirrored in `src/test/resources/test-schema.sql`, or
  the H2 tests will not match production. Flyway is off in tests.
- USA and India watchlists and items are stored in separate tables and entities. USA items reference
  USA watchlists and India items reference India watchlists; never reintroduce a shared item table
  or a cross-market foreign key.
- The `folio_pro` database is shared with `../news-scheduler-svc`, which creates its own tables in
  the same `public` schema through Hibernate. This service owns `portfolios`, `holdings`,
  `watchlists_*`, `watchlist_items_*` and `user_preferences`.

## Logging

- `LoggingAspect` logs start/completion and duration of public controller and service methods with
  HTTP method and sanitized path; do not add per-method logs.
- `ApiExceptionHandler` logs handled 4xx without payloads and unexpected exceptions with stack traces.
- `APP_LOG_LEVEL` sets the `com.foliopro.api` log level; container logs:
  `docker compose logs -f folio-pro-api`.

## Integrations

- No external market-data provider is integrated here; market data lives in
  `../news-scheduler-svc`. Use `spring-boot-provider-integration` only if one is explicitly added.

## Docs, API contract and deployment

- Public API docs: OpenAPI annotations, `docs/API_ENDPOINTS.md`, `README.md` and
  `postman/Folio-Pro-API.postman_collection.json` (environment
  `postman/Folio-Pro-Local-and-Dev.postman_environment.json`).
- Docker Compose (`docker compose up --build -d`, reads `.env`) runs the app and PostgreSQL. `k8s/`
  and `scripts/deploy-local-k8s.sh` are optional, Docker Desktop only.
- Secrets come from environment variables or the git-ignored `.env` (copy `.env.example`).

## Before finishing

- [ ] `mvn test` passes.
- [ ] Flyway migration mirrored in `test-schema.sql`.
- [ ] If an endpoint contract changed, the Angular app (`../personal-wealth-management-angular`,
      `src/app/core/services/`) is checked and updated.
