# Folio Pro API — Endpoint Reference

Base URL (local): `http://localhost:8081` · All paths are prefixed with `/api/v1`.
OpenAPI JSON: `/v3/api-docs` · Swagger UI: `/swagger-ui.html` · Health: `/actuator/health`.

This document describes, for every endpoint, its request/response contract, validation rules,
error cases, and **which database tables it reads or writes** (with the SQL Hibernate actually
issues, captured with `spring.jpa.show-sql=true`).

---

## 1. Database overview

Schema is managed by Flyway (`src/main/resources/db/migration`, V1–V8). Hibernate runs with
`ddl-auto: validate`, so entities must match these tables exactly.

| Table | Purpose | Key columns | Entity |
|---|---|---|---|
| `watchlists_usa` | US watchlists | `id` (from sequence `watchlists_id_seq`), `name` ≤40, `market` = `'US'` (CHECK), `currency` default `USD`, `user_id` NOT NULL default 1 (indexed), `is_favorite` NOT NULL default false, `created_at` | `UsaWatchlistEntity` |
| `watchlists_india` | India watchlists | `id` (same sequence), `name`, `market` = `'India'` (CHECK), `currency` default `INR`, `user_id` NOT NULL default 1 (indexed), `is_favorite` NOT NULL default false, `created_at` | `IndiaWatchlistEntity` |
| `watchlist_items_usa` | Tickers in US watchlists | `id` identity, `watchlist_id` → `watchlists_usa.id` ON DELETE CASCADE, `symbol` ≤15, `name` ≤160, `is_favorite`; UNIQUE(`watchlist_id`,`symbol`) | `UsaWatchlistItemEntity` |
| `watchlist_items_india` | Tickers in India watchlists | same shape, FK → `watchlists_india.id` | `IndiaWatchlistItemEntity` |
| `portfolios` | Portfolios | `id`, `name`, `country`, `currency`, `asset_type`, `created_at`, `updated_at` | `PortfolioEntity` |
| `holdings` | Positions in a portfolio | `portfolio_id` → `portfolios.id` ON DELETE CASCADE, `symbol`, `instrument_name`, `quantity`, `average_cost`, `market_value`, `currency`; UNIQUE(`portfolio_id`,`symbol`) | `HoldingEntity` |
| `watchlist_column_preferences` | Each user's watchlist table columns and their order | PK (`user_id`,`column_key`), `display_order` ≥ 1, UNIQUE(`user_id`,`display_order`), `created_at`, `updated_at` | `WatchlistColumnPreferenceEntity` |

The legacy `user_preferences` table and its `/api/v1/preferences` endpoint were removed (V8).

`created_at` on the two watchlist tables is filled by the DB default and is not mapped by the entity.

### How the watchlist tables are mapped (important)

```
                 WatchlistEntity (abstract, @Inheritance TABLE_PER_CLASS)
                 id from sequence watchlists_id_seq  →  IDs are unique across BOTH tables
                    ┌───────────────┴───────────────┐
         UsaWatchlistEntity                 IndiaWatchlistEntity
           watchlists_usa                     watchlists_india
                 │ 1..*                             │ 1..*
       watchlist_items_usa                 watchlist_items_india
```

* `WatchlistRepository` is typed on the **abstract** `WatchlistEntity`. Any query through it
  (`findAll`, `findById`, `findByMarket`, `count`) reads **both** watchlist tables through a
  `UNION ALL` subquery:

  ```sql
  select ... from (
      select id, currency, market, name, null as user_id, 1 as clazz_ from watchlists_india
      union all
      select id, currency, market, name, user_id,         2 as clazz_ from watchlists_usa
  ) we1_0 where ...
  ```
  `clazz_` tells Hibernate which subclass (US or India) each row is. `user_id` is `NULL` for
  India rows because only `watchlists_usa` has the column.
* Items have **no** common repository. The service checks the watchlist's subclass and
  calls `UsaWatchlistItemRepository` or `IndiaWatchlistItemRepository`.
* Because both watchlist tables draw IDs from the shared `watchlists_id_seq`, a numeric `id`
  identifies a watchlist in a single market. That's why `/watchlists/{id}` doesn't need a
  market parameter.

### Error response format (all endpoints)

```json
{ "timestamp": "2026-11-14T10:00:00Z", "status": 400, "error": "Bad Request",
  "message": "Request validation failed", "details": { "name": "must not be blank" } }
```

| Cause | Status |
|---|---|
| Body bean-validation failure (`@Valid`) | 400, `details` holds field errors |
| `IllegalArgumentException` (business rule, e.g. duplicate symbol) | 400 |
| Malformed JSON / wrong path-param type | 400 `"Invalid request parameter or JSON body"` |
| `NoSuchElementException` (entity not found) | 404 |
| `ResponseStatusException` (CSV empty / too large) | status as thrown (400 / 413) |
| Constraint violation on query/path params (`@Min`, `@Pattern`, …) | handled by the generic handler → **500** (see Known issues) |
| Anything else | 500 `"Unexpected server error"` |

---

## 2. Watchlist endpoints — `WatchlistController`

Shared DTOs (`ApiDtos`):

```jsonc
// WatchlistResponse
{ "id": 1, "name": "Growth picks", "market": "US", "currency": "USD",
  "userId": 1,                     // owner; defaults to 1 for US and India (no auth yet)
  "isFavorite": false,             // starred in the UI; favorites are listed first
  "items": [ { "id": 10, "symbol": "NVDA", "name": "NVIDIA Corporation", "isFavorite": true } ] }

// PageResponse<T>
{ "content": [ ... ], "page": 0, "size": 15, "totalElements": 42, "totalPages": 3 }
```

### 2.1 `GET /api/v1/watchlists` — list watchlists with their tickers

| Query param | Type | Default | Rules |
|---|---|---|---|
| `market` | string | — (both markets) | `US` or `India` |
| `page` | int | 0 | ≥ 0 |
| `size` | int | 15 | 1–100 |

Pagination applies to **watchlists**. Each watchlist in the page includes **all** of its items.
Sorted with favorites first (`is_favorite` true before false), then by `id` ascending within each group.

**Tables read:** `watchlists_usa`, `watchlists_india`, `watchlist_items_usa`, `watchlist_items_india`.

**Query sequence** (`FolioService.listWatchlists`, one read-only transaction):

1. **Page of watchlists:** UNION ALL over both watchlist tables, `order by is_favorite desc, id`, `offset/fetch first`.
   If `market` is given, a `where market = ?` filter is applied to the union.
2. **Count** for `totalElements`/`totalPages`: `select count(*)` over the same UNION ALL.
   Spring Data skips this on the last partial page.
3. **US items** (only if the page contains US watchlists):
   ```sql
   select ... from watchlist_items_usa i join watchlists_usa w on w.id = i.watchlist_id
   where w.id in (?, ?, ...) order by i.id
   ```
4. **India items** (only if the page contains India watchlists): same query against
   `watchlist_items_india` / `watchlists_india`.

So a request runs **2–4 queries regardless of page size**. There is no N+1. Items are
grouped in memory by `watchlist_id`. Watchlists with no items return `"items": []`.

```bash
curl 'http://localhost:8081/api/v1/watchlists?market=US&page=0&size=15'
```

### 2.2 `POST /api/v1/watchlists` — create an empty watchlist

Body (`WatchlistRequest`):

| Field | Rules |
|---|---|
| `name` | required, ≤ 40 chars, trimmed |
| `market` | required, `US` or `India` |
| `userId` | optional, positive. Stored for US and India (defaults to 1) |

**Tables:** `select next value for watchlists_id_seq`, then INSERT into `watchlists_usa` **or**
`watchlists_india`, then a SELECT on the matching items table to build the (empty) `items` array.

Response: `200 OK` with `WatchlistResponse`.

```bash
curl -X POST localhost:8081/api/v1/watchlists -H 'Content-Type: application/json' \
  -d '{"name":"Growth picks","market":"US","userId":1}'
```

### 2.3 `GET /api/v1/watchlists/{id}` — one watchlist with a page of items

| Param | Rules |
|---|---|
| `id` (path) | positive |
| `page` | ≥ 0, default 0 (page of **items**) |
| `size` | 1–100, default 15 |

**Tables:** UNION ALL over `watchlists_usa` + `watchlists_india` `where id = ?` to find the watchlist and its
market. Then a paged SELECT (plus a count) on `watchlist_items_usa` **or** `watchlist_items_india`.

Errors: 404 `Watchlist not found`.

The response is a plain `WatchlistResponse`. It does not include the total item count or
total pages (see Known issues).

### 2.4 `PATCH /api/v1/watchlists/{id}` — rename

Body: the full `WatchlistRequest` (`name` and `market` are required). `market` must equal the
existing market, otherwise 400 `Market cannot be changed when renaming a watchlist`. `userId` is
ignored.

**Tables:** UNION ALL lookup by id → UPDATE `watchlists_usa`/`watchlists_india` `set name` →
SELECT all items from the matching items table for the response.

Errors: 404 not found, 400 market mismatch / validation.

### 2.4a `PATCH /api/v1/watchlists/{id}/favorite?userId=1` — star / un-star a watchlist

Body: `{"isFavorite": true}` (required). `userId` defaults to 1. Works for USA and India watchlists.

**Tables:** UNION ALL lookup by id → UPDATE `watchlists_usa`/`watchlists_india` `set is_favorite` →
SELECT all items for the response. The response's `isFavorite` reflects the saved value.

Errors: 404 if the watchlist does not exist or belongs to another user, 400 if `isFavorite` is missing.

### 2.5 `DELETE /api/v1/watchlists/{id}` — delete watchlist and its items

**Tables:** UNION ALL lookup by id → `deleteByWatchlistId` on the matching items table (Spring Data
derived delete: SELECTs the items, then issues one DELETE per item) → DELETE from the watchlist table.
The DB-level `ON DELETE CASCADE` would also delete the items.

Response: `200 OK`, empty body. Errors: 404.

### 2.6 `POST /api/v1/watchlists/{id}/items` — bulk add items

Body (`BulkItemsRequest`): `{ "items": [ { "symbol": "AAPL", "name": "Apple", "isFavorite": false } ] }`

| Field | Rules |
|---|---|
| `items` | 1–100 entries |
| `symbol` | required, `[A-Za-z0-9.-]{1,15}`, stored upper-case |
| `name` | optional, ≤160. Defaults to the upper-cased symbol if blank |
| `isFavorite` | optional, default `false` |

Atomic (single transaction). Any duplicate fails the whole request with
400 `Duplicate symbol in watchlist: X`. A duplicate is a symbol that appears twice in the request
or already exists in the watchlist.

**Tables:** UNION ALL lookup by id. Then **for each item**: a SELECT on the items table
`where watchlist_id = ? and symbol = ?` (duplicate check), followed by an INSERT into
`watchlist_items_usa` or `watchlist_items_india`.

Response: `200 OK`, array of `WatchlistItemResponse`.

### 2.7 `PUT /api/v1/watchlists/{id}/items/{symbol}` — update an item

Path `symbol` identifies the existing item (case-insensitive). Body is `WatchlistItemRequest`.
The body `symbol` can rename the ticker as long as the new symbol doesn't already exist in the list.
`name` and `isFavorite` are overwritten. An omitted `isFavorite` becomes `false`.

**Tables:** UNION ALL lookup → SELECT item by (`watchlist_id`, `symbol`) → (if renamed) SELECT
for the new symbol → UPDATE on the matching items table.

Errors: 404 watchlist/item not found, 400 duplicate.

### 2.8 `DELETE /api/v1/watchlists/{id}/items/{symbol}` — remove an item

**Tables:** UNION ALL lookup → SELECT item by (`watchlist_id`, `symbol`) → DELETE from the
matching items table. Errors: 404.

### 2.9 `POST /api/v1/watchlists/import` — import watchlists from CSV

`multipart/form-data` with part `file` (≤ 5 MB, non-empty). Returns **201 Created**.

| Query param | Default | Rules |
|---|---|---|
| `market` | `USA` | `USA`, `US`, or `India` (case-insensitive) |
| `userId` | 1 | positive. Stored on `watchlists_usa.user_id` or `watchlists_india.user_id` |

CSV format is column-oriented. Row 1 holds watchlist names, and each column below it lists that watchlist's symbols:

```csv
Tech,Banks
AAPL,JPM
MSFT,BAC
NVDA,
```

Validation runs on the whole file before anything is saved:
* names are 1–40 chars and unique within the file (case-insensitive)
* symbols match `[A-Za-z0-9.&-]{1,15}` (so Indian tickers like `M&M` work) and are unique within their column
* rows can't have more columns than the header row
* quoting follows RFC 4180, and a UTF-8 BOM is accepted

Names aren't checked against watchlists that already exist, so an import can create a second
list with the same name.

**Tables (`WatchlistImportService`, one transaction):** `watchlists_id_seq` (one `next value` per
watchlist) → INSERT into `watchlists_usa` or `watchlists_india` (`saveAll`) → one INSERT per symbol into
`watchlist_items_usa` or `watchlist_items_india`.

Response: `{ "watchlistsCreated": 2, "itemsCreated": 5, "watchlistNames": ["Tech","Banks"] }`

Errors: 400 empty file / parse or validation error / bad market or userId, 413 file > 5 MB.

```bash
curl -X POST 'localhost:8081/api/v1/watchlists/import?market=USA&userId=1' -F 'file=@watchlists.csv'
```

---

## 3. Portfolio endpoints — `ApiController`

`PortfolioRequest`: `name` (≤100, required), `country` (≤40, required), `currency` (`USD`|`INR`),
`assetType` (≤160, optional), `holdings[]` (optional). Each holding has `symbol` (≤20), `instrument` (≤160),
`quantity` (> 0), `averageCost` (≥ 0), `marketValue` (≥ 0, optional), and `currency` (`USD`|`INR`).

Response `PortfolioResponse`: `id, name, country, currency, assetType, holdings[], sampleData=false`.

| Endpoint | What it does | Tables |
|---|---|---|
| `GET /api/v1/portfolios?page=0&size=15` | Page of portfolios (by `id`) with holdings | SELECT page + count on `portfolios`. Then **one SELECT on `holdings` per portfolio** (N+1) |
| `POST /api/v1/portfolios` | Create a portfolio and optional holdings | INSERT `portfolios`, INSERT `holdings` per holding, SELECT `holdings` |
| `GET /api/v1/portfolios/{id}` | One portfolio | SELECT `portfolios` by id, SELECT `holdings` by `portfolio_id`. 404 if missing |
| `PUT /api/v1/portfolios/{id}` | Replace fields **and** all holdings | SELECT + UPDATE `portfolios` (`updated_at` refreshed). DELETE all `holdings` for the portfolio, INSERT new ones. 404 if missing |
| `DELETE /api/v1/portfolios/{id}` | Delete | exists check + DELETE `portfolios`. `holdings` removed by FK `ON DELETE CASCADE`. 404 if missing |

A holding symbol that appears twice in one request violates UNIQUE(`portfolio_id`,`symbol`) and currently returns 500.

---

## 4. Watchlist column preferences — `WatchlistColumnPreferencesController`

Stores which watchlist table columns a user shows and in what order. The Angular watchlist pages use
this for both the US and India pages (one selection per user, not per page).

| Endpoint | What it does | Tables |
|---|---|---|
| `GET /api/v1/users/{userId}/watchlist-column-preferences` | Returns the columns sorted by `displayOrder`. **404** if the user has never saved columns (the UI then uses its defaults) | SELECT `watchlist_column_preferences` by `user_id` order by `display_order` |
| `PUT /api/v1/users/{userId}/watchlist-column-preferences` | Replaces the whole selection. Orders are renumbered 1..N in request order | DELETE by `user_id`, then INSERT one row per column |

Body / response:
```json
// request
{ "columns": [ { "columnKey": "symbol", "displayOrder": 1 }, { "columnKey": "marketCap", "displayOrder": 2 } ] }
// response
{ "userId": 1, "columns": [ ... ], "updatedAt": "2026-11-15T07:00:00" }
```
Validation: 1–100 columns; `columnKey` matches `[A-Za-z0-9_]+` (≤ 50 chars) and must be unique;
`displayOrder` ≥ 1 and unique. Keys are not checked against a fixed list, so the UI can add columns
(for example the India-only columns) without a backend change. Violations return 400.

---

## 5. Known issues / improvement notes

1. **Unknown routes return 500, not 404.** A request to a path with no endpoint (for example
   `PATCH /api/v1/watchlists/1/favorite` on a build that predates it) reaches the generic 500 handler
   because `NoResourceFoundException` is not mapped. Query/path parameter violations such as
   `?size=500` or `?market=XX` correctly return 400.
2. **`GET /watchlists/{id}` paginates items but returns no item totals**, so clients can't tell
   how many pages of items exist.
3. **Market vocabulary is inconsistent.** Import accepts `USA`/`US`/`India`, while list/create accept only `US`/`India`.
4. **List endpoint ignores `userId`.** `user_id` is stored on US and India watchlists, but there is no filter by owner.
7. **Item symbol rules differ from import.** CSV import accepts `&` (e.g. `M&M`), but `POST /watchlists/{id}/items` and `PUT /watchlists/{id}/items/{symbol}` validate with `[A-Za-z0-9.-]{1,15}`, so `M&M` is rejected there.
5. **N+1 patterns:** `GET /portfolios` (holdings per portfolio), bulk add items (one duplicate-check
   SELECT per item), and delete watchlist (derived delete loads the items, then deletes them one at a time; the DB cascade
   would do this in a single statement).
6. The item-by-watchlist queries add an unnecessary `join watchlists_*` because the derived query
   navigates `watchlist.id`. A `@Query` on `watchlist_id` alone would avoid the join.
