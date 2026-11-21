# invesment-management-svc — guide for Claude Code

Spring Boot (Java 21, Maven) API on port **8081** for the Folio Pro app. It owns portfolios,
holdings, USA and India watchlists, watchlist CSV import and per-user watchlist column preferences,
stored in the `folio_pro` PostgreSQL database. The Angular app (`../personal-wealth-management-angular`)
calls its `/api/v1/*` endpoints. `../news-scheduler-svc` (port 8082) shares the same database.

## Rules

Before any code change, use the `java-coding-standards` skill (`.claude/skills/java-coding-standards/SKILL.md`).
It loads the project rules, which live in `.github/` and are shared with GitHub Copilot:

- `.github/copilot-instructions.md`: repository rules, including the documentation and skill sync policy.
- `.github/instructions/*.instructions.md`: coding standards, naming and readability, architecture,
  Spring Boot, logging.
- `.github/skills/*/SKILL.md`: workflows for REST APIs, JPA, architecture, logging, tests and providers.

When a rule changes, edit the `.github/` file so both tools stay in sync.

## Layout

`controller` (HTTP) → `service` (use cases, transactions) → `repository` / `model` (JPA).
DTOs are in `dto`, error mapping in `exception/ApiExceptionHandler`, and controller/service logging
in `config/LoggingAspect`.

## Commands

```bash
mvn test                                         # H2 in-memory; Flyway is off in tests
mvn spring-boot:run -Dspring-boot.run.profiles=dev
docker compose up --build -d                     # app + PostgreSQL, reads .env
```

## Things to know

- Flyway owns the schema (`src/main/resources/db/migration`) and Hibernate only validates it. Add a
  new `V<n>__*.sql` for every schema change, never edit an applied one, and mirror it in
  `src/test/resources/test-schema.sql`.
- Secrets come from environment variables or the git-ignored `.env` (copy `.env.example`). Never
  commit real values or log them.
