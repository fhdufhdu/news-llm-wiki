CREATE TABLE sections (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    display_order INTEGER NOT NULL DEFAULT 1000,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE providers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    homepage_url TEXT,
    description TEXT NOT NULL DEFAULT '',
    display_order INTEGER NOT NULL DEFAULT 1000,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE rss_sources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_key TEXT NOT NULL UNIQUE,
    provider_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    url TEXT NOT NULL,
    type TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT '_all_',
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY(provider_id) REFERENCES providers(id) ON DELETE RESTRICT
);

CREATE TABLE articles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id TEXT NOT NULL UNIQUE,
    canonical_url TEXT NOT NULL UNIQUE,
    provider_id INTEGER NOT NULL,
    title TEXT NOT NULL,
    feed_url TEXT NOT NULL,
    published_at TEXT,
    ingested_at TEXT NOT NULL,
    content_hash TEXT,
    raw_id INTEGER,
    ai_status TEXT NOT NULL DEFAULT 'PENDING_AI',
    ai_retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    FOREIGN KEY(provider_id) REFERENCES providers(id) ON DELETE RESTRICT
);

CREATE TABLE article_raw (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    article_id INTEGER NOT NULL UNIQUE,
    storage_mode TEXT NOT NULL,
    html TEXT,
    html_gzip BLOB,
    file_path TEXT,
    content_type TEXT,
    http_status INTEGER,
    fetched_at TEXT NOT NULL,
    FOREIGN KEY(article_id) REFERENCES articles(id) ON DELETE CASCADE
);

CREATE TABLE article_notes (
    article_id INTEGER PRIMARY KEY,
    short_summary TEXT NOT NULL,
    durable_knowledge TEXT NOT NULL,
    durability TEXT NOT NULL,
    generated_by_job_id INTEGER,
    generated_at TEXT NOT NULL,
    FOREIGN KEY(article_id) REFERENCES articles(id) ON DELETE CASCADE
);

CREATE TABLE topics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE entities (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    summary TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    status TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE claims (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slug TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    claim_type TEXT NOT NULL,
    summary TEXT NOT NULL,
    verification_status TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE article_topic (
    article_id INTEGER NOT NULL,
    topic_id INTEGER NOT NULL,
    confidence REAL NOT NULL DEFAULT 1.0,
    PRIMARY KEY(article_id, topic_id),
    FOREIGN KEY(article_id) REFERENCES articles(id) ON DELETE CASCADE,
    FOREIGN KEY(topic_id) REFERENCES topics(id) ON DELETE CASCADE
);

CREATE TABLE article_entity (
    article_id INTEGER NOT NULL,
    entity_id INTEGER NOT NULL,
    role TEXT NOT NULL DEFAULT 'mentioned',
    PRIMARY KEY(article_id, entity_id),
    FOREIGN KEY(article_id) REFERENCES articles(id) ON DELETE CASCADE,
    FOREIGN KEY(entity_id) REFERENCES entities(id) ON DELETE CASCADE
);

CREATE TABLE article_event (
    article_id INTEGER NOT NULL,
    event_id INTEGER NOT NULL,
    role TEXT NOT NULL DEFAULT 'evidence',
    PRIMARY KEY(article_id, event_id),
    FOREIGN KEY(article_id) REFERENCES articles(id) ON DELETE CASCADE,
    FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE
);

CREATE TABLE article_claim (
    article_id INTEGER NOT NULL,
    claim_id INTEGER NOT NULL,
    stance TEXT NOT NULL DEFAULT 'reported',
    PRIMARY KEY(article_id, claim_id),
    FOREIGN KEY(article_id) REFERENCES articles(id) ON DELETE CASCADE,
    FOREIGN KEY(claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE TABLE section_summaries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    summary_date TEXT NOT NULL,
    scope TEXT NOT NULL,
    provider_id INTEGER,
    section_id INTEGER,
    topic_id INTEGER,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    major_article_ids TEXT NOT NULL,
    generated_at TEXT NOT NULL,
    FOREIGN KEY(provider_id) REFERENCES providers(id) ON DELETE SET NULL,
    FOREIGN KEY(section_id) REFERENCES sections(id) ON DELETE SET NULL,
    FOREIGN KEY(topic_id) REFERENCES topics(id) ON DELETE SET NULL
);

CREATE TABLE job_locks (
    name TEXT PRIMARY KEY,
    locked_until TEXT NOT NULL,
    locked_by TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE job_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_type TEXT NOT NULL,
    status TEXT NOT NULL,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    input_count INTEGER NOT NULL DEFAULT 0,
    output_count INTEGER NOT NULL DEFAULT 0,
    exit_code INTEGER,
    stdout_excerpt TEXT,
    stderr_excerpt TEXT,
    error_message TEXT
);

CREATE INDEX idx_articles_published_at ON articles(published_at);
CREATE INDEX idx_articles_provider ON articles(provider_id);
CREATE INDEX idx_articles_ai_status ON articles(ai_status);
CREATE INDEX idx_sections_enabled_order ON sections(enabled, display_order, title);
CREATE INDEX idx_providers_enabled_order ON providers(enabled, display_order, name);
CREATE INDEX idx_section_summaries_scope_date ON section_summaries(scope, summary_date);
CREATE INDEX idx_job_runs_type_started ON job_runs(job_type, started_at);

INSERT INTO sections(slug, title, description, display_order, enabled, created_at, updated_at) VALUES
('politics', '정치', '국회, 정당, 행정, 선거 관련 기사', 10, 1, datetime('now'), datetime('now')),
('economy', '경제', '거시경제, 금융, 부동산, 소비 관련 기사', 20, 1, datetime('now'), datetime('now')),
('industry-ai', '산업·AI', '기업, 산업, 기술, 인공지능 관련 기사', 30, 1, datetime('now'), datetime('now')),
('society', '사회', '노동, 교육, 복지, 지역, 생활 관련 기사', 40, 1, datetime('now'), datetime('now')),
('justice', '사건·사법', '수사, 재판, 범죄, 사법기관 관련 기사', 50, 1, datetime('now'), datetime('now')),
('diplomacy-security', '외교·안보', '외교, 국방, 국제 안보 관련 기사', 60, 1, datetime('now'), datetime('now')),
('culture-sports', '문화·스포츠', '문화, 방송, 연예, 스포츠 관련 기사', 70, 1, datetime('now'), datetime('now')),
('science-climate', '과학·기후', '과학, 의료, 환경, 기후 관련 기사', 80, 1, datetime('now'), datetime('now'));

INSERT INTO providers(slug, name, homepage_url, description, display_order, enabled, created_at, updated_at) VALUES
('geeknews', 'GeekNews', 'https://news.hada.io/', '개발자 커뮤니티와 기술 뉴스 제공사', 10, 1, datetime('now'), datetime('now')),
('naver-news', '네이버뉴스', 'https://news.naver.com/', '네이버 뉴스 포털 및 제휴 언론 기사', 20, 1, datetime('now'), datetime('now')),
('yonhap', '연합뉴스', 'https://www.yna.co.kr/', '국내 종합 뉴스 통신사', 30, 1, datetime('now'), datetime('now')),
('mk', '매일경제', 'https://www.mk.co.kr/', '경제와 산업 중심 언론사', 40, 1, datetime('now'), datetime('now')),
('hankyung', '한국경제', 'https://www.hankyung.com/', '경제와 기업 중심 언론사', 50, 1, datetime('now'), datetime('now'));
