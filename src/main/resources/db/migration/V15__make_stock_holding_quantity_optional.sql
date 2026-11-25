-- A holding can be saved before its quantity is known. The CHECK (quantity > 0 ...) still applies to a given quantity.
ALTER TABLE stock_holdings_usa ALTER COLUMN quantity DROP NOT NULL;
ALTER TABLE stock_holdings_india ALTER COLUMN quantity DROP NOT NULL;
