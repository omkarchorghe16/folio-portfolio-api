# folio-portfolio-api — guide for Claude Code

Spring Boot (Java 21, Maven) API on port **8081** for the Folio Pro app. It owns portfolios,
holdings, USA and India watchlists, watchlist CSV import and per-user watchlist column preferences,
stored in the `folio_pro` PostgreSQL database. The Angular app (`../folio-pro-ui`)
calls its `/api/v1/*` endpoints. `../folio-market-feed-svc` (port 8082) shares the same database.

## Rules

Before any code change, use the `java-coding-standards` skill (`.claude/skills/java-coding-standards/SKILL.md`).
It loads the project rules, which live in `.github/` and are shared with GitHub Copilot:

- `.github/copilot-instructions.md`: repository overview and the list of rule files.
- `.github/instructions/project.instructions.md`: rules for this repository only (packages, Flyway,
  USA/India separation, docs paths, final checks).
- `.github/instructions/*.instructions.md` (the rest): shared change-hygiene, coding standards,
  naming and readability, architecture, Spring Boot and logging rules.
- `.github/skills/*/SKILL.md`: workflows for REST APIs, JPA, architecture, logging, tests and providers.

### Where to change a rule

- Rules for this repository only: edit `project.instructions.md` (or `copilot-instructions.md`) here.
- Shared rules and skills: every file that starts with a `Managed by dev-standards` comment, plus
  `.claude/skills/java-coding-standards`, is copied from
  https://github.com/omkarchorghe16/dev-standards (cloned at `../dev-standards`) and listed in
  `.github/dev-standards.lock`. Do not edit those copies here. Change them in `../dev-standards`, then
  run `./sync.sh --all` there and commit the result in each repository.
- `../dev-standards/sync.sh --check .` (run from this repository) reports drift.

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
