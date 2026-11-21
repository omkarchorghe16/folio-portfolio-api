---
applyTo: "**/*.java"
---

# Naming and readability standards

These rules decide how new and changed code is named and laid out. They complement
`coding-standards.instructions.md` and `architecture.instructions.md`.

## One feature per controller and service

- Name a controller or service after the single feature or resource it owns:
  `PortfolioController` and `PortfolioService`, `WatchlistController` and `WatchlistService`.
- Never use generic names such as `ApiController`, `MainService`, `CommonService`, `Helper`,
  `Manager`, `Utils` or `FolioService`. They grow into classes that do everything.
- When a service starts serving a second resource, split it before adding more code.
- A focused sub-use-case may get its own service named after it: `WatchlistImportService`,
  `WatchlistColumnPreferencesService`.
- Controller and service for the same feature share the feature name. The controller's class-level
  `@RequestMapping` holds the resource path (`/api/v1/portfolios`), so method mappings stay short.

## Class and type suffixes

| Kind | Suffix | Example |
|---|---|---|
| REST controller | `Controller` | `WatchlistController` |
| Use-case service | `Service` | `WatchlistService` |
| Spring Data repository | `Repository` | `PortfolioRepository` |
| JPA entity | `Entity` | `UsaWatchlistItemEntity` |
| Request / response DTO | `Request` / `Response` | `WatchlistRequest`, `WatchlistResponse` |
| Configuration | `Config` / `Configuration` | `WebConfig` |
| Exception handler | `ExceptionHandler` | `ApiExceptionHandler` |

Market-specific types start with the market: `Usa...` or `India...`.

## Method names

Use verb + noun, and keep the controller method name the same as the service method it calls.

| Operation | HTTP | Name |
|---|---|---|
| Page or list | `GET /things` | `listThings` |
| One by id | `GET /things/{id}` | `getThing` |
| Create | `POST /things` | `createThing` |
| Full replace | `PUT /things/{id}` | `replaceThing` |
| Partial change | `PATCH /things/{id}` | a name for the change: `renameWatchlist`, `setWatchlistFavorite` |
| Delete | `DELETE /things/{id}` | `deleteThing` |
| Child items | `/things/{id}/items` | `addItems`, `updateItem`, `removeItem` |

- Do not use bare `get()`, `save()`, `replace()` or noun-only names such as `portfolios()`.
- Do not use a `null` argument to switch behavior (for example `save(null, request)` meaning
  "create"). Write two methods.
- Private helpers also say what they do: `findWatchlist`, `toResponse`, `toItemResponse`,
  `normalizeSymbol`, `applyRequest`.
- A lookup that throws when nothing is found is `findX` and returns the entity. A lookup that may
  return nothing returns `Optional<X>`.

## Fields, parameters and variables

- Inject dependencies into `private final` fields named after their type:
  `portfolioRepository`, `watchlistService`, `preferencesService`. Never `service`, `repo`, `p`
  or `s`.
- Single-letter names are allowed only for short lambda parameters (`w -> w.getId()`). Method
  parameters and local variables use full words: `request`, `watchlist`, `item`, `symbol`.
- Collections are plural (`items`, `usaIds`); maps say what they map (`itemsByWatchlistId`).
- Booleans read as a question: `isFavorite`, `hasName`, `symbolChanged`.
- Repeated literals, especially error messages, become `private static final` constants:
  `WATCHLIST_NOT_FOUND`.

## Layout

- One statement per line, four-space indentation, braces on every `if`/`for`, and lines of 120
  characters or fewer. Never put a whole method body on one line.
- Order members as: constants, fields, constructor, public methods (in the order of the HTTP
  operations), private helpers, mapping methods (`toResponse` and similar) last.
- Group long classes with short section comments, such as `// ---- Watchlist items ----`.
- Prefer explicit imports. Wildcard imports are acceptable only for the DTO holder classes
  (`ApiDtos.*`) and model packages that already use them.
- Add a short Javadoc sentence to a public method only when its name does not already explain the
  behavior: ordering rules, all-or-nothing semantics, ownership checks.

## Renaming existing code

- A rename must not change public behavior: URL paths, JSON field names, status codes and database
  names stay the same unless the change is requested.
- Class and method names appear in `LoggingAspect` log lines. When you rename them, update
  `docs/`, tests that use them as labels, and mention the change for anyone searching logs.
- Run `mvn test` after renaming.
