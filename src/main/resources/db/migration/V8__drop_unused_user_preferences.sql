-- The legacy /api/v1/preferences endpoint was removed. Column choices now live in
-- watchlist_column_preferences and favorites in watchlists_usa/watchlists_india.is_favorite.
DROP TABLE IF EXISTS user_preferences;
