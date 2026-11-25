# Folio Pro API — Endpoint Reference

Base URL (local): `http://localhost:8081` · All paths are prefixed with `/api/v1`.
OpenAPI JSON: `/v3/api-docs` · Swagger UI: `/swagger-ui.html` · Health: `/actuator/health`.

This document describes, for every endpoint, its request/response contract, validation rules,
error cases, and **which database tables it reads or writes** (with the SQL Hibernate actually
issues, captured with `spring.jpa.show-sql=true`).

---

## 1. Database overview

Schema is managed by Flyway (`src/main/resources/db/migration`, V1–V15). Hibernate runs with
`ddl-auto: validate`, so entities must match these tables exactly.

| Table | Purpose | Key columns | Entity |
|---|---|---|---|
| `watchlists_usa` | US watchlists | `id` (from sequence `watchlists_id_seq`), `name` ≤40, `market` = `'US'` (CHECK), `currency` default `USD`, `user_id` NOT NULL default 1 (indexed), `is_favorite` NOT NULL default false, `created_at` | `UsaWatchlistEntity` |
| `watchlists_india` | India watchlists | `id` (same sequence), `name`, `market` = `'India'` (CHECK), `currency` default `INR`, `user_id` NOT NULL default 1 (indexed), `is_favorite` NOT NULL default false, `created_at` | `IndiaWatchlistEntity` |
| `watchlist_items_usa` | Tickers in US watchlists | `id` identity, `watchlist_id` → `watchlists_usa.id` ON DELETE CASCADE, `symbol` ≤15, `name` ≤160, `is_favorite`; UNIQUE(`watchlist_id`,`symbol`) | `UsaWatchlistItemEntity` |
| `watchlist_items_india` | Tickers in India watchlists | same shape, FK → `watchlists_india.id` | `IndiaWatchlistItemEntity` |
| `portfolios` | Portfolios | `id`, `name`, `country`, `currency`, `asset_type`, `created_at`, `updated_at` | `PortfolioEntity` |
| `holdings` | Positions in a portfolio | `portfolio_id` → `portfolios.id` ON DELETE CASCADE, `symbol`, `instrument_name`, `quantity`, `average_cost`, `market_value`, `currency`; UNIQUE(`portfolio_id`,`symbol`) | `HoldingEntity` |
| `portfolio_entries_usa`, `portfolio_entries_india` | Monthly amounts per asset type, entered by hand | `id` identity, `user_id`, `country` (`US` / `India`, CHECK), `currency` (`USD` / `INR`, CHECK), `entry_month` (1st of month, CHECK), `asset_type` ≤40 (a default code or one of the user's `CUSTOM_…` codes, checked by the service), `sub_asset_type_id` (nullable, FK with `user_id` → `portfolio_sub_asset_types_*`), `invested_amount` ≥ 0, `current_value` ≥ 0, `created_at`, `updated_at`; UNIQUE NULLS NOT DISTINCT(`user_id`,`entry_month`,`asset_type`,`sub_asset_type_id`) | `UsaPortfolioEntryEntity`, `IndiaPortfolioEntryEntity` |
| `portfolio_asset_types_usa`, `portfolio_asset_types_india` | A user's own asset types and renamed defaults | `id` identity, `user_id`, `country` (CHECK), `code` ≤40, `name` ≤60, `is_hidden` default false, `created_at`, `updated_at`; UNIQUE(`user_id`,`code`). Unchanged defaults have no row | `UsaPortfolioAssetTypeEntity`, `IndiaPortfolioAssetTypeEntity` |
| `portfolio_sub_asset_types_usa`, `portfolio_sub_asset_types_india` | Many sub asset types per asset type | `id` identity, `user_id`, `country` (CHECK), `asset_type_id` NOT NULL, `code` ≤40, `name` ≤60, `is_hidden` default false, `created_at`, `updated_at`; FK(`user_id`,`asset_type_id`) → `portfolio_asset_types_*`(`user_id`,`id`); UNIQUE(`user_id`,`code`). A default asset type gets its row when its first sub asset type is added | `UsaPortfolioSubAssetTypeEntity`, `IndiaPortfolioSubAssetTypeEntity` |
| `stock_holdings_usa`, `stock_holdings_india` | Stocks and ETFs held | `id` identity, `user_id`, `country`, `currency` (both CHECK), `symbol` ≤20, `exchange` (India only, `NSE`/`BSE`, default `NSE`), `name` ≤160, `asset_type` (`STOCK`/`ETF`), `quantity` > 0 or null (not known), `average_cost` ≥ 0, `sector` ≤100 (name, nullable), `created_at`, `updated_at`; UNIQUE(`user_id`,`symbol`), plus `exchange` for India | `UsaStockHoldingEntity`, `IndiaStockHoldingEntity` |
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

## 2. Watchlist endpoints — `WatchlistController` and `WatchlistExportController`

Shared DTOs (`ApiDtos`):

```jsonc
// WatchlistResponse
{ "id": 1, "name": "Growth picks", "market": "US", "currency": "USD",
  "userId": 1,                     // owner; defaults to 1 for US and India (no auth yet)
  "isFavorite": false,             // starred in the UI; favorites are listed first
  "items": [ { "id": 10, "symbol": "NVDA", "name": "NVIDIA Corporation", "isFavorite": true } ] }

// PageResponse<T> (portfolios only; watchlist endpoints are not paginated)
{ "content": [ ... ], "page": 0, "size": 15, "totalElements": 42, "totalPages": 3 }
```

### 2.1 `GET /api/v1/watchlists` — list watchlists with their tickers

| Query param | Type | Default | Rules |
|---|---|---|---|
| `market` | string | — (both markets) | `US`, `USA` (alias of `US`) or `India`, case-insensitive |

Not paginated: the response is a JSON **array** (`WatchlistResponse[]`) of every matching watchlist, each
with **all** of its items. `page`/`size` are no longer read (extra query parameters are ignored).
Sorted with favorites first (`is_favorite` true before false), then by `id` ascending within each group.

**Tables read:** `watchlists_usa`, `watchlists_india`, `watchlist_items_usa`, `watchlist_items_india`.

**Query sequence** (`WatchlistService.listWatchlists`, one read-only transaction):

1. **All watchlists:** UNION ALL over both watchlist tables, `order by is_favorite desc, id`.
   If `market` is given, a `where market = ?` filter is applied to the union.
2. **US items** (only if there are US watchlists):
   ```sql
   select ... from watchlist_items_usa i join watchlists_usa w on w.id = i.watchlist_id
   where w.id in (?, ?, ...) order by i.id
   ```
3. **India items** (only if there are India watchlists): same query against
   `watchlist_items_india` / `watchlists_india`.

So a request runs **1–3 queries** however many watchlists and items there are. There is no N+1. Items are
grouped in memory by `watchlist_id`. Watchlists with no items return `"items": []`.

```bash
curl 'http://localhost:8081/api/v1/watchlists?market=US'
```

### 2.2 `POST /api/v1/watchlists` — create an empty watchlist

Body (`WatchlistRequest`):

| Field | Rules |
|---|---|
| `name` | required, ≤ 40 chars, trimmed |
| `market` | required, `US`, `USA` (stored as `US`) or `India`, case-insensitive |
| `userId` | optional, positive. Stored for US and India (defaults to 1) |

**One watchlist per name per user and market.** If the user (`userId`, default 1) already has a
watchlist with this name in this market (case-insensitive, trimmed), nothing is created and that
existing watchlist is returned with its items. A different user, or the other market, gets its own
watchlist. If several same-named watchlists already exist from before this rule, the oldest is returned.

**Tables:** a SELECT on `watchlists_usa` **or** `watchlists_india` by `user_id` and `lower(name)`; if none
matches, `select next value for watchlists_id_seq` and an INSERT. Then a SELECT on the matching items
table to build the `items` array.

Response: `200 OK` with `WatchlistResponse` (the new watchlist, or the existing one).

```bash
curl -X POST localhost:8081/api/v1/watchlists -H 'Content-Type: application/json' \
  -d '{"name":"Growth picks","market":"US","userId":1}'
```

### 2.3 `GET /api/v1/watchlists/{id}` — one watchlist with all of its items

| Param | Rules |
|---|---|
| `id` (path) | positive |

**Tables:** UNION ALL over `watchlists_usa` + `watchlists_india` `where id = ?` to find the watchlist and its
market. Then one SELECT of all its rows in `watchlist_items_usa` **or** `watchlist_items_india`.

Errors: 404 `Watchlist not found`.

The response is a plain `WatchlistResponse` with every item (not paginated).

### 2.4 `PATCH /api/v1/watchlists/{id}` — rename

Body: the full `WatchlistRequest` (`name` and `market` are required). `market` must equal the
existing market, otherwise 400 `Market cannot be changed when renaming a watchlist`. `userId` is
ignored. The new name must not be used by another of the owner's watchlists in that market
(case-insensitive), otherwise 400 `A watchlist named "…" already exists`. Changing only the case of
the watchlist's own name is allowed.

**Tables:** UNION ALL lookup by id → SELECT for a same-named watchlist of the owner → UPDATE
`watchlists_usa`/`watchlists_india` `set name` →
SELECT all items from the matching items table for the response.

Errors: 404 not found, 400 market mismatch / name already used / validation.

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
| `symbol` | required, `[A-Za-z0-9.&$-]{1,15}` (e.g. `M&M`, `BRK.B`, `ABR$D`), stored upper-case |
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
* symbols match `[A-Za-z0-9.&$-]{1,15}`, the same rule as the item endpoints (so `M&M` and `ABR$D` work), and are unique within their column
* a leading apostrophe added by the export's formula protection (`'=...`, `'+...`, `'-...`, `'@...`) is removed from names and symbols
* rows can't have more columns than the header row
* quoting follows RFC 4180, and a UTF-8 BOM is accepted

**Imports only add, never delete or overwrite.** Each column is matched against the user's existing
watchlists in the same market by name (case-insensitive, surrounding spaces ignored):

| Column name | Result |
|---|---|
| Matches an existing watchlist of the same `userId` and market | New symbols are appended to that watchlist; symbols it already has are skipped (counted in `itemsSkipped`) |
| No match | A new watchlist is created with the column's symbols |

Existing watchlists and stocks are never removed or renamed. A watchlist with the same name owned by
another user, or in the other market, is not touched; a new one is created instead. If the user
already has several same-named watchlists (from imports made before this rule), the oldest one is used.

**Tables (`WatchlistImportService`, one transaction):** one SELECT of the user's watchlists in that
market (`watchlists_usa` or `watchlists_india`) → for each matched watchlist, one SELECT of its items →
INSERT of each new watchlist (`watchlists_id_seq`) → one INSERT per new symbol into
`watchlist_items_usa` or `watchlist_items_india`.

Response (`watchlistNames` lists every watchlist the file touched, using its stored name):

```json
{ "watchlistsCreated": 1, "watchlistsUpdated": 1, "itemsCreated": 3, "itemsSkipped": 2,
  "watchlistNames": ["Tech", "Banks"] }
```

Errors: 400 empty file / parse or validation error / bad market or userId, 413 file > 5 MB.

```bash
curl -X POST 'localhost:8081/api/v1/watchlists/import?market=USA&userId=1' -F 'file=@watchlists.csv'
```

### 2.10 Market-specific item endpoints — `/watchlists/us/...` and `/watchlists/india/...`

These are the item endpoints the Angular app uses. Unlike `/watchlists/{id}/items`, they check the
market **and** the owner: a watchlist that is in the other market or belongs to another `userId` is
reported as 404.

| Method & path | Behavior |
|---|---|
| `POST /api/v1/watchlists/india/{id}/items?userId=1` | Body `BulkItemsRequest` (1–100 items). Saves into `watchlist_items_india`. All or none: a symbol repeated in the request or already in the list returns 400. **201 Created** with the saved items |
| `DELETE /api/v1/watchlists/india/{id}/items/{symbol}?userId=1` | Deletes one stock from `watchlist_items_india`; symbol matched case-insensitively. **204**, or 404 if the list or symbol is not found |
| `POST /api/v1/watchlists/us/{id}/items?userId=1` | Same as the India add, into `watchlist_items_usa` |
| `DELETE /api/v1/watchlists/us/{id}/items/{symbol}?userId=1` | Same as the India delete, from `watchlist_items_usa` |

`userId` defaults to 1. Symbols match `[A-Za-z0-9.&$-]{1,15}`, so `M&M`, `BRK.B` and `ABR$D` are
valid; URL-encode `&` and `$` in the path (`M%26M`).

**Tables:** a lookup of the watchlist by id in the market's own table, filtered by `user_id` → per
item, a duplicate-check SELECT and an INSERT (add) or one DELETE (remove) on the market's items table.

```bash
curl -X POST 'localhost:8081/api/v1/watchlists/india/42/items?userId=1' -H 'Content-Type: application/json' \
  -d '{"items":[{"symbol":"RELIANCE"},{"symbol":"M&M"}]}'
curl -X DELETE 'localhost:8081/api/v1/watchlists/india/42/items/M%26M?userId=1'
```

### 2.11 `GET /api/v1/watchlists/export` — download watchlists as CSV (`WatchlistExportController`)

| Query param | Default | Rules |
|---|---|---|
| `market` | — (required) | `US`, `USA` or `India`, case-insensitive |
| `userId` | 1 | positive; only this user's watchlists are exported |
| `watchlistId` | — (all) | positive. Export just this watchlist; omit it to export every watchlist the user has in the market |

**File format** — the same column-oriented layout the import reads, so a file can be exported and
imported again unchanged:

* **Row 1** holds the watchlist names, one per column, oldest watchlist first.
* **Rows 2 onwards** hold each watchlist's ticker symbols in its own column, in the order they were
  added. Shorter columns are padded with empty cells.

Example for a user with three US watchlists:

```csv
Tech,Banks,Energy
AAPL,JPM,XOM
MSFT,BAC,
NVDA,,
```

| Column | Row 1 (name) | Rows 2+ (tickers) |
|---|---|---|
| A | `Tech` | `AAPL`, `MSFT`, `NVDA` |
| B | `Banks` | `JPM`, `BAC` |
| C | `Energy` | `XOM` |

Other details:

* Only ticker symbols are exported. Item display names and favorite stars are not.
* UTF-8 with a byte-order mark (so Excel opens it correctly) and `\r\n` line endings. The import
  ignores the byte-order mark.
* RFC 4180 quoting: a cell containing a comma, quote or line break is wrapped in quotes and inner
  quotes are doubled, for example `"Core, India"`.
* **Every cell is plain text, never a formula.** A cell that starts with `=`, `+`, `-`, `@`, tab or
  carriage return (for example a watchlist named `=HYPERLINK("http://x")`) gets a leading apostrophe
  (`'=HYPERLINK(...)`), which Excel and Google Sheets show as ordinary text instead of a link or
  formula. The import removes that apostrophe, so the name is restored exactly.
* File name, sent in `Content-Disposition: attachment`: `<market>-<watchlist-name>.csv` for one
  watchlist (`india-core-india.csv`) or `<market>-watchlists.csv` for all. The header is listed in CORS
  `exposedHeaders` so the browser app can read it.

Response: **200** `text/csv;charset=UTF-8`. Errors: **400** invalid `market`/`userId`/`watchlistId`;
**404** `watchlistId` is not one of this user's watchlists in that market.

**Tables (`WatchlistExportService`, read-only transaction):** one SELECT of the user's watchlists in
`watchlists_usa` or `watchlists_india` → one SELECT per exported watchlist on its items table, ordered
by `id`.

```bash
curl -OJ 'localhost:8081/api/v1/watchlists/export?market=USA&userId=1'                 # all US lists
curl -OJ 'localhost:8081/api/v1/watchlists/export?market=India&userId=1&watchlistId=42' # one list
```

---

## 3. Portfolio endpoints — `PortfolioController`

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

## 5. Portfolio entries — `PortfolioEntryController`

Base: `/api/v1/users/{userId}/portfolio-entries/{market}`. `market` is `US`, `USA` or `India` (any case) and selects
the table. All amounts are in that table's currency.

| Endpoint | What it does | Tables |
|---|---|---|
| `GET /summary` | Latest month's totals and asset lines, and `history` (oldest first). Nothing recorded: empty lists, no `latestMonth`/`totals` | SELECT all of the user's rows |
| `GET /months/{yyyy-MM}` | The month's entries with `saved: true`. Otherwise `saved: false` with the entries of `sourceMonth`, the latest earlier month | 1–3 SELECTs |
| `PUT /months/{yyyy-MM}` | Replaces the month in one transaction; asset types left out are deleted, an empty list clears it | SELECT, then UPDATE / INSERT / DELETE per row |

Body: `{"entries":[{"assetType":"STOCKS","investedAmount":1400,"currentValue":1600}],
"assetTypes":[{"assetType":"CUSTOM_0123456789ABCDEF0123456789ABCDEF","name":"401(k)"}]}`. `assetTypes` is optional and
lists only new and renamed types; a new code is `CUSTOM_` and 32 hex digits chosen by the client, names are unique per
user and country (any case), and codes never change. Sub asset types work the same way: `subAssetTypes` lists new and renamed ones as
`{"subAssetType":"SUB_<32 hex>","assetType":"LAND","name":"Farm"}` (name unique within the asset type; it cannot move to
another one), and an entry for one adds `"subAssetType":"SUB_…"`. Own and sub asset type entries for the same asset type
in one month return 400. In the summary an asset's amounts are the total of its `subAssets`.
Both take an optional `"hidden"` (left as it is when absent). There is no delete: hidden types keep the amounts of saved
months and are left out of the suggestion for an unsaved month; a hidden asset type also leaves out its sub asset types.
`GET /months` returns every type in `assetTypes` and `subAssetTypes`, and the summary
returns each asset's `name`. Default asset types: `STOCKS`,
`MUTUAL_FUNDS`, `ETFS`, `RESIDENTIAL_PROPERTY`, `COMMERCIAL_PROPERTY`, `LAND`, `REITS`, `BONDS_FIXED_INCOME`, `CASH`,
`CRYPTO`, `GOLD_COMMODITIES`. Amounts are ≥ 0 with at most 2 decimals. A future month, duplicate or unknown asset type
returns 400. `investedAmount` is the total cost still held. `profit` = value − invested; `monthlyReturnPercent` =
change in profit ÷ previous calendar month's value. A percentage with a zero denominator or no previous month is absent.

## 6. Stock holdings — `StockHoldingController`

Base: `/api/v1/users/{userId}/stock-holdings/{market}`. Prices are not stored here; clients read them from
folio-market-feed-svc by `symbol` (US, `fmp_stock_profiles`) or `symbol` + `exchange` (India, `upstox_stock_quotes`).

| Endpoint | What it does |
|---|---|
| `GET` | All holdings by symbol |
| `POST` | Add `{symbol,name,assetType,quantity?,averageCost,sector?}` (sector: name, at most 100 characters); 201, or 409 if already held. India: `.NS`/`.BO` is removed. US symbols must match what FMP accepts (`[A-Z0-9][A-Z0-9.-]{0,19}`), else 400 |
| `PUT /{id}` | Replace `{quantity?,averageCost,sector?}` (left out: cleared); 404 if not the user's holding in that market |
| `DELETE /{id}` | Remove; 204, or 404 |

---

## 7. Known issues / improvement notes

1. **Unknown routes return 500, not 404.** A request to a path with no endpoint (for example
   `PATCH /api/v1/watchlists/1/favorite` on a build that predates it) reaches the generic 500 handler
   because `NoResourceFoundException` is not mapped. Query/path parameter violations such as
   `?market=XX` correctly return 400.
2. **Watchlist lists are unbounded.** `GET /watchlists` and `GET /watchlists/{id}` return every watchlist
   and item in one response (by design, so the UI can show one scrolling page). Fine at personal scale;
   revisit if a user ever has thousands of lists.
3. **Responses always use `US`.** Every endpoint accepts `US`, `USA` or `India` (case-insensitive), but stored and returned values are `US` and `India`.
4. **List endpoint ignores `userId`.** `user_id` is stored on US and India watchlists, but there is no filter by owner.
7. **Export after older duplicates.** A user who still has two same-named watchlists from before
   the one-name-per-user rule gets that name twice in the export's first row, and importing that
   file fails with *Duplicate watchlist name*. Rename or delete one of them first.
8. **Export with no watchlists** returns 200 with only an empty first row; importing such a file fails.
9. **Export loads items with one query per watchlist.** `findByWatchlistIdInOrderByIdAsc` could load
   all of them in one query.
5. **N+1 patterns:** `GET /portfolios` (holdings per portfolio), bulk add items (one duplicate-check
   SELECT per item), and delete watchlist (derived delete loads the items, then deletes them one at a time; the DB cascade
   would do this in a single statement).
6. The item-by-watchlist queries add an unnecessary `join watchlists_*` because the derived query
   navigates `watchlist.id`. A `@Query` on `watchlist_id` alone would avoid the join.
