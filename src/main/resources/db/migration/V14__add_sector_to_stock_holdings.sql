-- Sector picked by the user, by name. Each market has its own list (US: table sectors of folio-market-feed-svc; India:
-- the user's watchlists), so there is no foreign key. NULL when none was picked.
ALTER TABLE stock_holdings_usa ADD COLUMN sector VARCHAR(100);
ALTER TABLE stock_holdings_india ADD COLUMN sector VARCHAR(100);
