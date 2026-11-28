---
applyTo: "**"
---

# Project rules: folio-portfolio-api

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
- The `folio_pro` database is shared with `../folio-market-feed-svc`, which creates its own tables in
  the same `public` schema through Hibernate. This service owns `portfolios`, `holdings`,
  `portfolio_entries_*`, `portfolio_asset_types_*`, `portfolio_sub_asset_types_*`, `stock_holdings_*`, `watchlists_*`, `watchlist_items_*` and
  `watchlist_column_preferences`.

## Logging

- `LoggingAspect` logs start/completion and duration of public controller and service methods with
  HTTP method and sanitized path; do not add per-method logs.
- `ApiExceptionHandler` logs handled 4xx without payloads and unexpected exceptions with stack traces.
- `APP_LOG_LEVEL` sets the `com.foliopro.api` log level; container logs:
  `docker compose logs -f folio-pro-api`.

## Integrations

- No external market-data provider is integrated here; market data lives in
  `../folio-market-feed-svc`. Use `spring-boot-provider-integration` only if one is explicitly added.

## Docs, API contract and deployment

- Public API docs: OpenAPI annotations, `docs/API_ENDPOINTS.md`, `README.md` and
  `postman/Folio-Pro-API.postman_collection.json` (environment
  `postman/Folio-Pro-Local-and-Dev.postman_environment.json`).
- Docker Compose (`docker compose up --build -d`, reads `.env`) runs only the app, which connects
  to PostgreSQL on the Mac host through `host.docker.internal:5432`. The `Dockerfile` is multi-stage and
  builds the jar itself. `k8s/` and `scripts/deploy-local-k8s.sh` are optional, Docker Desktop only.
- `.github/workflows/ci-cd.yml` tests and builds the image on every push and pull request, publishes it to GHCR
  from `main`, and deploys only when the repository variable `KUBE_DEPLOY_ENABLED` is `true` (secrets
  `KUBE_CONFIG`, `APP_SECRETS_ENV`). `../folio-market-feed-svc` has the same workflow; keep them alike.
- `../folio-pro-ui` runs this API with the UI and folio-market-feed-svc: its `compose.yaml` includes this
  `compose.yaml` (service name `folio-pro-api`) and its `scripts/deploy-local-k8s.sh` calls this repository's
  script (Service `folio-pro-api` in namespace `dev`). Keep those names, or update the UI repository's nginx
  routing (`docker/nginx.conf.template`, `k8s/deployment.yaml`) with them.
- Secrets come from environment variables or the git-ignored `.env` (copy `.env.example`).

## Before finishing

- [ ] `mvn test` passes.
- [ ] Flyway migration mirrored in `test-schema.sql`.
- [ ] If an endpoint contract changed, the Angular app (`../folio-pro-ui`,
      `src/app/core/services/`) is checked and updated.
