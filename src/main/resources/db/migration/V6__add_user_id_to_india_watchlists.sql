ALTER TABLE watchlists_india
  ADD COLUMN user_id BIGINT NOT NULL DEFAULT 1;

CREATE INDEX idx_watchlists_india_user_id ON watchlists_india(user_id);
