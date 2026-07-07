CREATE TABLE IF NOT EXISTS daily_wiki_summaries (
    summary_date TEXT PRIMARY KEY,
    summary TEXT NOT NULL,
    highlights TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
