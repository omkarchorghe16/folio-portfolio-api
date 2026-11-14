CREATE TABLE watchlist_column_preferences (
    user_id       BIGINT      NOT NULL,
    column_key    VARCHAR(50) NOT NULL,
    display_order INT         NOT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_watchlist_column_preferences PRIMARY KEY (user_id, column_key),
    CONSTRAINT uq_watchlist_column_preferences_order UNIQUE (user_id, display_order),
    CONSTRAINT ck_watchlist_column_preferences_order CHECK (display_order >= 1)
);

CREATE INDEX idx_watchlist_column_preferences_user_id ON watchlist_column_preferences(user_id);
