-- flyway:executeInTransaction=false

PRAGMA foreign_keys=off;

DROP TRIGGER IF EXISTS articles_ai_fts;
DROP TRIGGER IF EXISTS articles_ad_fts;
DROP TRIGGER IF EXISTS articles_au_fts;

CREATE TABLE articles_without_provider (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id TEXT NOT NULL UNIQUE,
    canonical_url TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    feed_url TEXT NOT NULL,
    published_at TEXT,
    ingested_at TEXT NOT NULL,
    content_hash TEXT,
    raw_id INTEGER,
    ai_status TEXT NOT NULL DEFAULT 'PENDING_AI',
    ai_retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    raw_status TEXT NOT NULL DEFAULT 'PENDING',
    wiki_status TEXT NOT NULL DEFAULT 'PENDING',
    wiki_locked_at TEXT,
    wiki_attempt_count INTEGER NOT NULL DEFAULT 0,
    wiki_last_error TEXT,
    rss_feed_id INTEGER,
    collected_at TEXT
);

INSERT INTO articles_without_provider (
    id,
    source_id,
    canonical_url,
    title,
    feed_url,
    published_at,
    ingested_at,
    content_hash,
    raw_id,
    ai_status,
    ai_retry_count,
    last_error,
    raw_status,
    wiki_status,
    wiki_locked_at,
    wiki_attempt_count,
    wiki_last_error,
    rss_feed_id,
    collected_at
)
SELECT
    id,
    source_id,
    canonical_url,
    title,
    feed_url,
    published_at,
    ingested_at,
    content_hash,
    raw_id,
    ai_status,
    ai_retry_count,
    last_error,
    raw_status,
    wiki_status,
    wiki_locked_at,
    wiki_attempt_count,
    wiki_last_error,
    rss_feed_id,
    collected_at
FROM articles;

DROP TABLE articles;
ALTER TABLE articles_without_provider RENAME TO articles;

CREATE INDEX IF NOT EXISTS idx_articles_published_at ON articles(published_at);
CREATE INDEX IF NOT EXISTS idx_articles_ai_status ON articles(ai_status);
CREATE INDEX IF NOT EXISTS idx_articles_wiki_status ON articles(wiki_status, wiki_locked_at);
CREATE INDEX IF NOT EXISTS idx_articles_raw_status ON articles(raw_status);

CREATE TRIGGER articles_ai_fts AFTER INSERT ON articles BEGIN
    INSERT INTO article_fts(rowid, title, canonical_url)
    VALUES (new.id, new.title, new.canonical_url);
END;

CREATE TRIGGER articles_ad_fts AFTER DELETE ON articles BEGIN
    INSERT INTO article_fts(article_fts, rowid, title, canonical_url)
    VALUES ('delete', old.id, old.title, old.canonical_url);
END;

CREATE TRIGGER articles_au_fts AFTER UPDATE OF title, canonical_url ON articles BEGIN
    INSERT INTO article_fts(article_fts, rowid, title, canonical_url)
    VALUES ('delete', old.id, old.title, old.canonical_url);
    INSERT INTO article_fts(rowid, title, canonical_url)
    VALUES (new.id, new.title, new.canonical_url);
END;

INSERT INTO article_fts(article_fts) VALUES ('rebuild');

PRAGMA foreign_keys=on;
