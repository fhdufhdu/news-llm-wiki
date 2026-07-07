ALTER TABLE wiki_sections ADD COLUMN fixed INTEGER NOT NULL DEFAULT 0;

INSERT INTO wiki_sections(slug, title, summary, display_order, status, fixed, created_at, updated_at)
VALUES
    ('politics', '정치', '정치와 정책 흐름', 10, 'ACTIVE', 1, datetime('now'), datetime('now')),
    ('economy', '경제', '경제, 산업, 금융 흐름', 20, 'ACTIVE', 1, datetime('now'), datetime('now')),
    ('society', '사회', '사회 이슈와 제도 변화', 30, 'ACTIVE', 1, datetime('now'), datetime('now')),
    ('world', '국제', '국제 정세와 해외 뉴스', 40, 'ACTIVE', 1, datetime('now'), datetime('now')),
    ('technology', '기술', '기술, 과학, AI 흐름', 50, 'ACTIVE', 1, datetime('now'), datetime('now')),
    ('culture', '문화', '문화, 생활, 엔터테인먼트', 60, 'ACTIVE', 1, datetime('now'), datetime('now')),
    ('sports', '스포츠', '스포츠 주요 흐름', 70, 'ACTIVE', 1, datetime('now'), datetime('now')),
    ('other', '기타', '분류되지 않은 주요 흐름', 999, 'ACTIVE', 1, datetime('now'), datetime('now'))
ON CONFLICT(slug) DO UPDATE SET
    fixed = 1,
    title = excluded.title,
    summary = excluded.summary,
    display_order = excluded.display_order,
    status = 'ACTIVE',
    updated_at = datetime('now');

CREATE INDEX IF NOT EXISTS idx_wiki_sections_fixed_nav ON wiki_sections(fixed, status, display_order, title);
