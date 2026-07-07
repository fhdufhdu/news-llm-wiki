CREATE VIRTUAL TABLE IF NOT EXISTS article_fts USING fts5(
    title,
    canonical_url,
    content='articles',
    content_rowid='id',
    tokenize='unicode61'
);

CREATE VIRTUAL TABLE IF NOT EXISTS wiki_page_fts USING fts5(
    title,
    summary,
    body,
    content='wiki_pages',
    content_rowid='id',
    tokenize='unicode61'
);

INSERT INTO article_fts(rowid, title, canonical_url)
SELECT id, title, canonical_url FROM articles
WHERE id NOT IN (SELECT rowid FROM article_fts);

INSERT INTO wiki_page_fts(rowid, title, summary, body)
SELECT id, title, summary, body FROM wiki_pages
WHERE id NOT IN (SELECT rowid FROM wiki_page_fts);

CREATE TRIGGER IF NOT EXISTS articles_ai_fts AFTER INSERT ON articles BEGIN
    INSERT INTO article_fts(rowid, title, canonical_url)
    VALUES (new.id, new.title, new.canonical_url);
END;

CREATE TRIGGER IF NOT EXISTS articles_ad_fts AFTER DELETE ON articles BEGIN
    INSERT INTO article_fts(article_fts, rowid, title, canonical_url)
    VALUES ('delete', old.id, old.title, old.canonical_url);
END;

CREATE TRIGGER IF NOT EXISTS articles_au_fts AFTER UPDATE OF title, canonical_url ON articles BEGIN
    INSERT INTO article_fts(article_fts, rowid, title, canonical_url)
    VALUES ('delete', old.id, old.title, old.canonical_url);
    INSERT INTO article_fts(rowid, title, canonical_url)
    VALUES (new.id, new.title, new.canonical_url);
END;

CREATE TRIGGER IF NOT EXISTS wiki_pages_ai_fts AFTER INSERT ON wiki_pages BEGIN
    INSERT INTO wiki_page_fts(rowid, title, summary, body)
    VALUES (new.id, new.title, new.summary, new.body);
END;

CREATE TRIGGER IF NOT EXISTS wiki_pages_ad_fts AFTER DELETE ON wiki_pages BEGIN
    INSERT INTO wiki_page_fts(wiki_page_fts, rowid, title, summary, body)
    VALUES ('delete', old.id, old.title, old.summary, old.body);
END;

CREATE TRIGGER IF NOT EXISTS wiki_pages_au_fts AFTER UPDATE OF title, summary, body ON wiki_pages BEGIN
    INSERT INTO wiki_page_fts(wiki_page_fts, rowid, title, summary, body)
    VALUES ('delete', old.id, old.title, old.summary, old.body);
    INSERT INTO wiki_page_fts(rowid, title, summary, body)
    VALUES (new.id, new.title, new.summary, new.body);
END;
