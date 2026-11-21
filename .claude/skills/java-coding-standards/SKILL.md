---
name: java-coding-standards
description: Coding, naming, architecture and logging standards for invesment-management-svc. Use before writing, changing, reviewing or renaming any Java code, Flyway migration, or REST endpoint in this repository.
---

# Java coding standards for invesment-management-svc

This skill applies the project's rules to Claude Code. The rules themselves live in `.github/` and
are shared with GitHub Copilot, so there is one source of truth. Do not copy them here; update the
`.github/` file instead.

## 1. Load the rules

Read every file below before writing code. All of them apply to every Java change.

- `.github/copilot-instructions.md`: repository rules and documentation sync policy
- `.github/instructions/coding-standards.instructions.md`
- `.github/instructions/naming-and-readability.instructions.md`: class, method and field names; layout
- `.github/instructions/architecture.instructions.md`
- `.github/instructions/spring-boot.instructions.md`
- `.github/instructions/logging.instructions.md`

Then read the task-specific workflow from `.github/skills/`:

| Task | Skill |
|---|---|
| New or changed endpoint, DTO, status code | `.github/skills/spring-boot-rest-api/SKILL.md` |
| Entity, repository, Flyway migration | `.github/skills/spring-boot-jpa/SKILL.md` |
| New package, layer or cross-cutting design | `.github/skills/spring-boot-architecture/SKILL.md` |
| Logging or error handling | `.github/skills/spring-boot-logging/SKILL.md` |
| Tests | `.github/skills/spring-boot-testing/SKILL.md` |
| External market-data provider | `.github/skills/spring-boot-provider-integration/SKILL.md` |

## 2. Look at nearby code

Open the closest existing controller, service and test for the same feature and match them.
Current reference classes are `PortfolioController` and `PortfolioService`, `WatchlistController`
and `WatchlistService`, and `ApiIntegrationTest`.

## 3. Write the code

- Put code in the feature's own controller and service. Create `<Feature>Controller` and
  `<Feature>Service` for a new resource instead of adding to an unrelated class.
- Controller → service → repository, never controller → repository.
- Schema changes go in a new `src/main/resources/db/migration/V<next>__<description>.sql`. Never
  edit a migration that has already run. Mirror the change in `src/test/resources/test-schema.sql`.
- Keep USA and India persistence in separate tables and entities.

## 4. Check before finishing

- [ ] Class, method and field names follow `naming-and-readability.instructions.md`.
- [ ] Controller method names match the service methods they call.
- [ ] No one-line method bodies, single-letter parameters or generic class names.
- [ ] No credentials, secrets or request bodies in code or logs.
- [ ] Tests added or updated for changed behavior, and `mvn test` passes.
- [ ] README, `docs/API_ENDPOINTS.md`, OpenAPI annotations and the Postman collection are updated
      when the API or its behavior changed.
- [ ] Every `.github/skills/*` file affected by the change is updated.
- [ ] If an endpoint contract changed, the Angular app (`../personal-wealth-management-angular`,
      `src/app/core/services/`) is checked and updated.
