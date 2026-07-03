ALTER TABLE articles ADD COLUMN raw_status TEXT NOT NULL DEFAULT 'PENDING';
ALTER TABLE articles ADD COLUMN wiki_status TEXT NOT NULL DEFAULT 'PENDING';
ALTER TABLE articles ADD COLUMN wiki_locked_at TEXT;
ALTER TABLE articles ADD COLUMN wiki_attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE articles ADD COLUMN wiki_last_error TEXT;
ALTER TABLE articles ADD COLUMN rss_feed_id INTEGER;
ALTER TABLE articles ADD COLUMN collected_at TEXT;

CREATE TABLE IF NOT EXISTS rss_feeds (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    provider_id INTEGER NOT NULL,
    title TEXT NOT NULL,
    feed_url TEXT NOT NULL UNIQUE,
    section_hint TEXT,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY(provider_id) REFERENCES providers(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS article_raw_sources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    article_id INTEGER NOT NULL UNIQUE,
    raw_html TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    http_status INTEGER,
    fetched_at TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY(article_id) REFERENCES articles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS wiki_sections (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    summary TEXT NOT NULL DEFAULT '',
    display_order INTEGER NOT NULL DEFAULT 1000,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS wiki_pages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    section_id INTEGER,
    slug TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    summary TEXT NOT NULL DEFAULT '',
    body TEXT NOT NULL DEFAULT '',
    importance INTEGER NOT NULL DEFAULT 50,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY(section_id) REFERENCES wiki_sections(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS wiki_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_run_id INTEGER,
    status TEXT NOT NULL,
    claimed_count INTEGER NOT NULL DEFAULT 0,
    done_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    last_message TEXT NOT NULL DEFAULT '',
    FOREIGN KEY(job_run_id) REFERENCES job_runs(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS wiki_page_sources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    wiki_page_id INTEGER NOT NULL,
    article_id INTEGER NOT NULL,
    contribution_summary TEXT NOT NULL DEFAULT '',
    evidence_type TEXT,
    created_at TEXT NOT NULL,
    UNIQUE(wiki_page_id, article_id),
    FOREIGN KEY(wiki_page_id) REFERENCES wiki_pages(id) ON DELETE CASCADE,
    FOREIGN KEY(article_id) REFERENCES articles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS wiki_revisions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    wiki_page_id INTEGER NOT NULL,
    article_id INTEGER,
    wiki_run_id INTEGER,
    change_summary TEXT NOT NULL,
    body_snapshot TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY(wiki_page_id) REFERENCES wiki_pages(id) ON DELETE CASCADE,
    FOREIGN KEY(article_id) REFERENCES articles(id) ON DELETE SET NULL,
    FOREIGN KEY(wiki_run_id) REFERENCES wiki_runs(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_articles_wiki_status ON articles(wiki_status, wiki_locked_at);
CREATE INDEX IF NOT EXISTS idx_articles_raw_status ON articles(raw_status);
CREATE INDEX IF NOT EXISTS idx_rss_feeds_enabled ON rss_feeds(enabled, provider_id);
CREATE INDEX IF NOT EXISTS idx_wiki_sections_nav ON wiki_sections(status, display_order, title);
CREATE INDEX IF NOT EXISTS idx_wiki_pages_section ON wiki_pages(section_id, status, importance, updated_at);
CREATE INDEX IF NOT EXISTS idx_wiki_sources_article ON wiki_page_sources(article_id);
CREATE INDEX IF NOT EXISTS idx_wiki_revisions_page ON wiki_revisions(wiki_page_id, created_at);
