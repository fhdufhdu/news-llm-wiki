package com.newswiki.infrastructure.ai;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class CodexWikiJobBuilder {
    public Path build(Path baseDir, String sqlitePath, long jobRunId, int batchSize) {
        try {
            Path jobDir = Files.createDirectories(baseDir.resolve("wiki-" + jobRunId));
            Files.writeString(jobDir.resolve("prompt.md"), prompt(sqlitePath, jobRunId, batchSize));
            Files.writeString(jobDir.resolve("wiki_helper.py"), helper(sqlitePath, jobRunId));
            return jobDir;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build Codex wiki job", e);
        }
    }

    private String prompt(String sqlitePath, long jobRunId, int batchSize) {
        return """
                You are maintaining a personal LLM Wiki.

                Use ./wiki_helper.py. Do not create article summaries as the final output.
                Update SQLite directly after every article.

                Required flow:
                1. Import wiki_helper.
                2. Call recover_stale_articles().
                3. Call claim_articles(%d).
                4. Call start_wiki_run(claimed_count=len(article_ids)).
                5. For each article: get_article(), extract_article_text(), inspect existing major categories,
                   subcategories, and pages. Update or create wiki subcategories/pages, link the source, add a revision,
                   then mark_article_done().
                6. On each meaningful step call progress().
                7. On article failure call mark_article_failed(article_id, error).
                8. Call finish_wiki_run() before exiting.

                Important:
                - The server mechanically collects user-provided article URLs and raw HTML.
                - Your job is to maintain durable wiki data in SQLite.
                - 기사 1건마다 SQLite에 즉시 반영한다.
                - Process one article at a time and commit each article to SQLite immediately.
                - Major categories are wiki_sections.fixed=1. They are broad categories such as politics, economy,
                  society, world, technology, culture, sports, and other. Treat them as fixed navigation data.
                - Subcategories are wiki_sections.fixed=0. They are yours to create, update, and delete naturally
                  while maintaining wiki pages.
                - Do not create article-summary-only buckets. Subcategories should describe durable knowledge flows.
                - Reuse existing wiki pages when a new article updates an existing knowledge flow.
                - Create a new page only when the knowledge does not fit an existing page.
                - Before updating a wiki page, call list_major_categories() and list_subcategories().
                - For each article, call search_pages() with title keywords, entity keywords, and topic keywords.
                - If search_pages() returns candidates, call get_page() before upsert_page().
                - Create a new page only when no existing page can absorb the article.

                Database: %s
                Job run id: %d
                """.formatted(batchSize, sqlitePath, jobRunId);
    }

    private String helper(String sqlitePath, long jobRunId) {
        return """
                import datetime
                import re
                import sqlite3

                from bs4 import BeautifulSoup

                DB = "__SQLITE_PATH__"
                JOB_RUN_ID = __JOB_RUN_ID__

                def now():
                    return datetime.datetime.utcnow().replace(microsecond=0).isoformat() + "Z"

                def connect():
                    con = sqlite3.connect(DB)
                    con.row_factory = sqlite3.Row
                    return con

                def progress(message, **counts):
                    detail = " ".join([f"{k}={v}" for k, v in counts.items()])
                    with connect() as con:
                        con.execute(
                            "insert into job_logs(job_run_id, level, message, created_at) values (?, 'INFO', ?, ?)",
                            (JOB_RUN_ID, (message + (" " + detail if detail else "")), now())
                        )

                def start_wiki_run(claimed_count=0):
                    with connect() as con:
                        con.execute(
                            "insert into wiki_runs(job_run_id,status,claimed_count,started_at,last_message) values (?, 'RUNNING', ?, ?, '')",
                            (JOB_RUN_ID, claimed_count, now())
                        )
                        return con.execute("select last_insert_rowid()").fetchone()[0]

                def finish_wiki_run(wiki_run_id, status, done_count=0, failed_count=0, message=''):
                    with connect() as con:
                        con.execute(\"\"\"
                            update wiki_runs
                               set status=?, done_count=?, failed_count=?, finished_at=?, last_message=?
                             where id=?
                        \"\"\", (status, done_count, failed_count, now(), message, wiki_run_id))

                def recover_stale_articles(timeout_minutes=60):
                    with connect() as con:
                        con.execute(\"\"\"
                            update articles
                               set wiki_status='PENDING', wiki_locked_at=null, wiki_last_error='Recovered stale wiki lock'
                             where wiki_status='RUNNING'
                               and coalesce(wiki_locked_at, '') < datetime('now', '-' || ? || ' minutes')
                        \"\"\", (timeout_minutes,))

                def claim_articles(limit=80):
                    with connect() as con:
                        rows = con.execute(\"\"\"
                            select a.id
                              from articles a
                              join article_raw_sources r on r.article_id = a.id
                             where a.wiki_status='PENDING'
                             order by coalesce(a.published_at, a.ingested_at) asc, a.id asc
                             limit ?
                        \"\"\", (limit,)).fetchall()
                        ids = [r['id'] for r in rows]
                        for article_id in ids:
                            con.execute(\"\"\"
                                update articles
                                   set wiki_status='RUNNING', wiki_locked_at=?, wiki_attempt_count=wiki_attempt_count+1
                                 where id=? and wiki_status='PENDING'
                            \"\"\", (now(), article_id))
                    return ids

                def get_article(article_id):
                    with connect() as con:
                        row = con.execute(\"\"\"
                            select a.id, a.title, a.canonical_url, a.published_at, r.raw_html
                              from articles a
                              join article_raw_sources r on r.article_id=a.id
                             where a.id=?
                        \"\"\", (article_id,)).fetchone()
                        return dict(row) if row else None

                def extract_article_text(article_id):
                    article = get_article(article_id)
                    if article is None:
                        return ''
                    soup = BeautifulSoup(article['raw_html'], 'html.parser')
                    for tag in soup(['script', 'style', 'noscript', 'iframe']):
                        tag.decompose()
                    text = re.sub(r'\\n{3,}', '\\n\\n', soup.get_text('\\n', strip=True))
                    return text[:20000]

                def list_sections():
                    with connect() as con:
                        return [dict(r) for r in con.execute("select * from wiki_sections order by display_order, title")]

                def list_major_categories():
                    with connect() as con:
                        return [dict(r) for r in con.execute(
                            "select * from wiki_sections where fixed=1 and status='ACTIVE' order by display_order, title"
                        )]

                def list_subcategories():
                    with connect() as con:
                        return [dict(r) for r in con.execute(
                            "select * from wiki_sections where fixed=0 and status='ACTIVE' order by display_order, title"
                        )]

                def create_subcategory(title, summary='', order_hint=None):
                    slug = slugify(title)
                    with connect() as con:
                        con.execute(\"\"\"
                            insert into wiki_sections(slug,title,summary,display_order,status,fixed,created_at,updated_at)
                            values (?,?,?,?, 'ACTIVE', 0, ?, ?)
                        \"\"\", (slug, title, summary, order_hint or 1000, now(), now()))
                        return con.execute("select last_insert_rowid()").fetchone()[0]

                def create_section(title, summary='', order_hint=None):
                    return create_subcategory(title, summary, order_hint)

                def update_subcategory(section_id, title=None, summary=None, display_order=None):
                    with connect() as con:
                        row = con.execute("select * from wiki_sections where id=?", (section_id,)).fetchone()
                        if row is None:
                            raise ValueError(f"subcategory not found: {section_id}")
                        if row['fixed']:
                            raise ValueError(f"major category cannot be updated by Codex: {section_id}")
                        con.execute(\"\"\"
                            update wiki_sections set title=?, summary=?, display_order=?, updated_at=? where id=?
                        \"\"\", (title or row['title'], summary if summary is not None else row['summary'],
                              display_order if display_order is not None else row['display_order'], now(), section_id))

                def update_section(section_id, title=None, summary=None, display_order=None):
                    return update_subcategory(section_id, title, summary, display_order)

                def delete_subcategory(section_id, move_pages_to=None):
                    with connect() as con:
                        row = con.execute("select * from wiki_sections where id=?", (section_id,)).fetchone()
                        if row is None:
                            raise ValueError(f"subcategory not found: {section_id}")
                        if row['fixed']:
                            raise ValueError(f"major category cannot be deleted by Codex: {section_id}")
                        con.execute("update wiki_pages set section_id=? where section_id=?", (move_pages_to, section_id))
                        con.execute("delete from wiki_sections where id=?", (section_id,))

                def delete_section(section_id, move_pages_to=None):
                    return delete_subcategory(section_id, move_pages_to)

                def search_pages(query):
                    like = f"%{query}%"
                    with connect() as con:
                        return [dict(r) for r in con.execute(
                            "select * from wiki_pages where title like ? or summary like ? or body like ? order by updated_at desc limit 20",
                            (like, like, like)
                        )]

                def get_page(page_id):
                    with connect() as con:
                        row = con.execute("select * from wiki_pages where id=?", (page_id,)).fetchone()
                        return dict(row) if row else None

                def upsert_page(section_id, title, summary, body, importance=50):
                    slug = slugify(title)
                    with connect() as con:
                        con.execute(\"\"\"
                            insert into wiki_pages(section_id,slug,title,summary,body,importance,status,created_at,updated_at)
                            values (?,?,?,?,?,?, 'ACTIVE', ?, ?)
                            on conflict(slug) do update set
                                section_id=excluded.section_id, title=excluded.title, summary=excluded.summary,
                                body=excluded.body, importance=excluded.importance, updated_at=excluded.updated_at
                        \"\"\", (section_id, slug, title, summary, body, importance, now(), now()))
                        return con.execute("select id from wiki_pages where slug=?", (slug,)).fetchone()[0]

                def link_source(page_id, article_id, contribution_summary, evidence_type=None):
                    with connect() as con:
                        con.execute(\"\"\"
                            insert or ignore into wiki_page_sources(wiki_page_id,article_id,contribution_summary,evidence_type,created_at)
                            values (?,?,?,?,?)
                        \"\"\", (page_id, article_id, contribution_summary, evidence_type, now()))

                def add_revision(page_id, article_id, change_summary, body_snapshot, wiki_run_id=None):
                    with connect() as con:
                        con.execute(\"\"\"
                            insert into wiki_revisions(wiki_page_id,article_id,wiki_run_id,change_summary,body_snapshot,created_at)
                            values (?,?,?,?,?,?)
                        \"\"\", (page_id, article_id, wiki_run_id, change_summary, body_snapshot, now()))

                def mark_article_done(article_id):
                    with connect() as con:
                        con.execute("update articles set wiki_status='DONE', wiki_locked_at=null, wiki_last_error=null where id=?",
                                    (article_id,))

                def mark_article_failed(article_id, error):
                    with connect() as con:
                        con.execute(\"\"\"
                            update articles
                               set wiki_status='FAILED', wiki_locked_at=null, wiki_last_error=?
                             where id=?
                        \"\"\", (str(error)[:2000], article_id))

                def slugify(value):
                    slug = re.sub(r'(^-|-$)', '', re.sub(r'[^a-z0-9가-힣]+', '-', value.lower()))
                    return slug or 'wiki-page'
                """
                .replace("__SQLITE_PATH__", sqlitePath.replace("\\", "\\\\").replace("\"", "\\\""))
                .replace("__JOB_RUN_ID__", Long.toString(jobRunId));
    }
}
