-- Asset types and sub asset types are hidden, never deleted, so that the amounts of past months keep their type.
ALTER TABLE portfolio_asset_types_usa ADD COLUMN is_hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE portfolio_asset_types_india ADD COLUMN is_hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE portfolio_sub_asset_types_usa ADD COLUMN is_hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE portfolio_sub_asset_types_india ADD COLUMN is_hidden BOOLEAN NOT NULL DEFAULT FALSE;
