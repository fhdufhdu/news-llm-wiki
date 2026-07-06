CREATE TABLE IF NOT EXISTS article_import_jobs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    total_count INTEGER NOT NULL DEFAULT 0,
    fetched_count INTEGER NOT NULL DEFAULT 0,
    wiki_done_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    started_at TEXT,
    finished_at TEXT,
    last_message TEXT
);

CREATE TABLE IF NOT EXISTS article_import_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL,
    input_url TEXT NOT NULL,
    canonical_url TEXT,
    article_id INTEGER,
    status TEXT NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY(job_id) REFERENCES article_import_jobs(id),
    FOREIGN KEY(article_id) REFERENCES articles(id)
);

CREATE INDEX IF NOT EXISTS idx_article_import_items_job_status ON article_import_items(job_id, status);
CREATE INDEX IF NOT EXISTS idx_article_import_items_article ON article_import_items(article_id);
