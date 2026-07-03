CREATE TABLE article_fetch_failures (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    canonical_url TEXT NOT NULL UNIQUE,
    source_key TEXT NOT NULL,
    source_name TEXT NOT NULL,
    feed_url TEXT NOT NULL,
    title TEXT,
    published_at TEXT,
    failure_count INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'RETRYABLE',
    last_error TEXT,
    last_attempt_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX idx_article_fetch_failures_status ON article_fetch_failures(status, updated_at);
