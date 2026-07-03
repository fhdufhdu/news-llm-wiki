# SQLite Codex Wiki Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the article-summary pipeline with a SQLite-backed LLM Wiki pipeline where the server collects raw article HTML and Codex directly edits wiki tables through a Python helper.

**Architecture:** Spring Boot remains a layered application: controllers call services, services coordinate repositories and infrastructure, repositories use JPA `EntityManager` with SQLite-native SQL where needed. The server owns RSS discovery, raw HTML storage, scheduling, job logs, and read-only wiki rendering. Codex owns wiki section/page/source/revision mutation via a generated Python helper that updates SQLite one article at a time.

**Tech Stack:** Spring Boot 4.0.x, Java 25, SQLite, Flyway, JPA `EntityManager`, Lombok, Thymeleaf, Python 3, BeautifulSoup, Codex CLI, Docker Compose.

---

## File Structure

- Modify `src/main/resources/db/migration/V005__sqlite_codex_wiki_redesign.sql`: add the new collection/wiki schema and indexes.
- Modify `src/main/java/com/newswiki/config/AppProperties.java`: replace AI note settings with wiki job settings.
- Modify `src/main/resources/application.yml`: expose RSS, raw fetch, wiki batch, stale lock, and Codex settings.
- Modify `src/main/java/com/newswiki/repository/ArticleRepository.java`: make articles/raw source persistence and wiki claim state the primary repository contract.
- Create `src/main/java/com/newswiki/repository/WikiPageRepository.java`: read-only server queries for wiki sections, pages, page details, and sources.
- Modify `src/main/java/com/newswiki/repository/JobRunRepository.java`: keep server/Codex progress logs append-only and ordered oldest-to-newest.
- Modify `src/main/java/com/newswiki/service/RssIngestService.java`: save raw HTML into SQLite text and mark wiki pending without AI note work.
- Replace `src/main/java/com/newswiki/service/AiOrchestrationService.java` with `src/main/java/com/newswiki/service/CodexWikiService.java`: run Codex wiki batches.
- Modify `src/main/java/com/newswiki/infrastructure/ai/AiBatchBuilder.java`: either delete after replacement or shrink into `CodexWikiPromptBuilder`.
- Create `src/main/java/com/newswiki/infrastructure/ai/CodexWikiJobBuilder.java`: create job directory, prompt, and Python helper.
- Modify `src/main/java/com/newswiki/service/ScheduledJobs.java`: run ingest every 5 minutes and trigger wiki jobs against pending articles.
- Modify `src/main/java/com/newswiki/service/StartupRecoveryService.java`: recover stale wiki article locks and interrupted job runs.
- Modify `src/main/java/com/newswiki/service/NewsViewService.java`: render from wiki repositories, not article notes.
- Modify controllers under `src/main/java/com/newswiki/application/`: keep controller-to-service-only composition.
- Modify Thymeleaf templates under `src/main/resources/templates/`: home, section, wiki page, jobs.
- Modify tests under `src/test/java/com/newswiki/**`: replace article-note assertions with wiki table behavior.

---

### Task 1: Add New SQLite Wiki Schema

**Files:**
- Create: `src/main/resources/db/migration/V005__sqlite_codex_wiki_redesign.sql`
- Test: `src/test/java/com/newswiki/repository/WikiSchemaMigrationTest.java`

- [ ] **Step 1: Write the failing schema test**

Create `src/test/java/com/newswiki/repository/WikiSchemaMigrationTest.java`:

```java
package com.newswiki.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WikiSchemaMigrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsWikiCentricTables() {
        assertThat(tableExists("article_raw_sources")).isTrue();
        assertThat(tableExists("wiki_sections")).isTrue();
        assertThat(tableExists("wiki_pages")).isTrue();
        assertThat(tableExists("wiki_page_sources")).isTrue();
        assertThat(tableExists("wiki_revisions")).isTrue();
        assertThat(tableExists("wiki_runs")).isTrue();
    }

    @Test
    void articlesHaveWikiProcessingColumns() {
        var columns = jdbc.query("pragma table_info(articles)", (rs, rowNum) -> rs.getString("name"));
        assertThat(columns).contains("raw_status", "wiki_status", "wiki_locked_at", "wiki_attempt_count", "wiki_last_error");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbc.queryForObject("""
                select count(*)
                  from sqlite_master
                 where type = 'table'
                   and name = ?
                """, Integer.class, tableName);
        return count != null && count == 1;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
rtk ./gradlew test --tests WikiSchemaMigrationTest
```

Expected: FAIL because `V005__sqlite_codex_wiki_redesign.sql` does not exist and the new tables are missing.

- [ ] **Step 3: Add migration**

Create `src/main/resources/db/migration/V005__sqlite_codex_wiki_redesign.sql`:

```sql
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
```

- [ ] **Step 4: Run schema test**

Run:

```bash
rtk ./gradlew test --tests WikiSchemaMigrationTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V005__sqlite_codex_wiki_redesign.sql src/test/java/com/newswiki/repository/WikiSchemaMigrationTest.java
git commit -m "feat: add sqlite wiki schema"
```

---

### Task 2: Replace Article Raw Storage With SQLite Text

**Files:**
- Modify: `src/main/java/com/newswiki/repository/ArticleRepository.java`
- Modify: `src/main/java/com/newswiki/service/RssIngestService.java`
- Test: `src/test/java/com/newswiki/repository/ArticleRepositoryTest.java`
- Test: `src/test/java/com/newswiki/service/RssIngestServiceTest.java`

- [ ] **Step 1: Write repository tests**

Add tests to `ArticleRepositoryTest`:

```java
@Test
void savesRawHtmlAsTextAndMarksArticlePendingForWiki() {
    long providerId = wikiRepository.upsertProviderByName("테스트언론");
    long articleId = articleRepository.insertArticleIfAbsent(
            "source-raw-1",
            "https://example.com/news/1",
            providerId,
            "테스트 기사",
            "https://example.com/rss",
            Instant.parse("2026-07-03T00:00:00Z"),
            "hash-1"
    );

    articleRepository.saveRawHtml(articleId, "<html><article>본문</article></html>", "hash-html", 200);

    var row = jdbc.queryForMap("""
            select a.raw_status, a.wiki_status, r.raw_html, r.content_hash, r.http_status
              from articles a
              join article_raw_sources r on r.article_id = a.id
             where a.id = ?
            """, articleId);

    assertThat(row.get("raw_status")).isEqualTo("FETCHED");
    assertThat(row.get("wiki_status")).isEqualTo("PENDING");
    assertThat(row.get("raw_html")).isEqualTo("<html><article>본문</article></html>");
    assertThat(row.get("content_hash")).isEqualTo("hash-html");
    assertThat(row.get("http_status")).isEqualTo(200);
}
```

- [ ] **Step 2: Run repository test to verify it fails**

```bash
rtk ./gradlew test --tests ArticleRepositoryTest
```

Expected: FAIL because `saveRawHtml` is missing.

- [ ] **Step 3: Implement repository methods**

Add to `ArticleRepository`:

```java
@Transactional
public void saveRawHtml(long articleId, String rawHtml, String contentHash, int httpStatus) {
    entityManager.createNativeQuery("""
            insert into article_raw_sources(article_id, raw_html, content_hash, http_status, fetched_at, created_at)
            values (:articleId, :rawHtml, :contentHash, :httpStatus, :now, :now)
            on conflict(article_id) do update set
                raw_html = excluded.raw_html,
                content_hash = excluded.content_hash,
                http_status = excluded.http_status,
                fetched_at = excluded.fetched_at
            """)
            .setParameter("articleId", articleId)
            .setParameter("rawHtml", rawHtml)
            .setParameter("contentHash", contentHash)
            .setParameter("httpStatus", httpStatus)
            .setParameter("now", Instant.now().toString())
            .executeUpdate();

    entityManager.createNativeQuery("""
            update articles
               set raw_status = 'FETCHED',
                   wiki_status = case when wiki_status is null then 'PENDING' else wiki_status end,
                   raw_id = null
             where id = :articleId
            """)
            .setParameter("articleId", articleId)
            .executeUpdate();
}

@Transactional(readOnly = true)
public boolean hasRawHtml(long articleId) {
    Number count = (Number) entityManager.createNativeQuery("""
            select count(*)
              from article_raw_sources
             where article_id = :articleId
            """)
            .setParameter("articleId", articleId)
            .getSingleResult();
    return count != null && count.longValue() > 0;
}
```

- [ ] **Step 4: Update ingest service**

In `RssIngestService`, replace gzip/file raw persistence with:

```java
var fetched = articleFetcher.fetch(entry.link());
String html = fetched.body();
String htmlHash = sha256(html);
articleRepository.saveRawHtml(articleId, html, htmlHash, fetched.statusCode());
logger.log("INFO", "기사 원문 저장: " + source.name() + " - " + entry.title());
```

Keep retry queue handling for fetch failures.

- [ ] **Step 5: Run ingest tests**

```bash
rtk ./gradlew test --tests ArticleRepositoryTest --tests RssIngestServiceTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/newswiki/repository/ArticleRepository.java src/main/java/com/newswiki/service/RssIngestService.java src/test/java/com/newswiki/repository/ArticleRepositoryTest.java src/test/java/com/newswiki/service/RssIngestServiceTest.java
git commit -m "feat: store raw article html in sqlite"
```

---

### Task 3: Add Codex Wiki Python Helper Job

**Files:**
- Create: `src/main/java/com/newswiki/infrastructure/ai/CodexWikiJobBuilder.java`
- Modify: `src/main/java/com/newswiki/infrastructure/ai/CodexRunner.java`
- Test: `src/test/java/com/newswiki/infrastructure/ai/CodexWikiJobBuilderTest.java`

- [ ] **Step 1: Write helper generation test**

Create `CodexWikiJobBuilderTest`:

```java
package com.newswiki.infrastructure.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CodexWikiJobBuilderTest {
    @TempDir Path tempDir;

    @Test
    void writesPromptAndPythonHelper() throws Exception {
        var builder = new CodexWikiJobBuilder();

        Path jobDir = builder.build(tempDir, "/app/data/newswiki.sqlite", 123L, 80);

        String prompt = Files.readString(jobDir.resolve("prompt.md"));
        String helper = Files.readString(jobDir.resolve("wiki_helper.py"));

        assertThat(prompt).contains("claim_articles(80)");
        assertThat(prompt).contains("기사 1건마다 SQLite에 즉시 반영");
        assertThat(helper).contains("def claim_articles");
        assertThat(helper).contains("def create_section");
        assertThat(helper).contains("def delete_section");
        assertThat(helper).contains("def upsert_page");
        assertThat(helper).contains("def progress");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
rtk ./gradlew test --tests CodexWikiJobBuilderTest
```

Expected: FAIL because `CodexWikiJobBuilder` does not exist.

- [ ] **Step 3: Implement job builder skeleton**

Create `CodexWikiJobBuilder`:

```java
package com.newswiki.infrastructure.ai;

import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class CodexWikiJobBuilder {
    @SneakyThrows
    public Path build(Path baseDir, String sqlitePath, long jobRunId, int batchSize) {
        Path jobDir = Files.createDirectories(baseDir.resolve("wiki-" + jobRunId));
        Files.writeString(jobDir.resolve("prompt.md"), prompt(sqlitePath, jobRunId, batchSize));
        Files.writeString(jobDir.resolve("wiki_helper.py"), helper(sqlitePath, jobRunId));
        return jobDir;
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
                5. For each article: get_article(), extract_article_text(), inspect existing sections/pages,
                   update or create wiki sections/pages, link the source, add a revision,
                   then mark_article_done().
                6. On each meaningful step call progress().
                7. On article failure call mark_article_failed(article_id, error).
                8. Call finish_wiki_run() before exiting.

                Database: %s
                Job run id: %d
                """.formatted(batchSize, sqlitePath, jobRunId);
    }

    private String helper(String sqlitePath, long jobRunId) {
        return """
                import sqlite3, re, datetime
                from bs4 import BeautifulSoup

                DB = "%s"
                JOB_RUN_ID = %d

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
                            select a.id, a.title, a.canonical_url, a.published_at, p.name provider_name, r.raw_html
                              from articles a
                              join providers p on p.id=a.provider_id
                              join article_raw_sources r on r.article_id=a.id
                             where a.id=?
                        \"\"\", (article_id,)).fetchone()
                        return dict(row)

                def extract_article_text(article_id):
                    article = get_article(article_id)
                    soup = BeautifulSoup(article['raw_html'], 'html.parser')
                    for tag in soup(['script', 'style', 'noscript', 'iframe']):
                        tag.decompose()
                    text = re.sub(r'\\n{3,}', '\\n\\n', soup.get_text('\\n', strip=True))
                    return text[:20000]

                def list_sections():
                    with connect() as con:
                        return [dict(r) for r in con.execute("select * from wiki_sections order by display_order, title")]

                def create_section(title, summary='', order_hint=None):
                    slug = re.sub(r'(^-|-$)', '', re.sub(r'[^a-z0-9가-힣]+', '-', title.lower()))
                    with connect() as con:
                        con.execute(\"\"\"
                            insert into wiki_sections(slug,title,summary,display_order,status,created_at,updated_at)
                            values (?,?,?,?, 'ACTIVE', ?, ?)
                        \"\"\", (slug, title, summary, order_hint or 1000, now(), now()))
                        return con.execute("select last_insert_rowid()").fetchone()[0]

                def update_section(section_id, title=None, summary=None, display_order=None):
                    with connect() as con:
                        row = con.execute("select * from wiki_sections where id=?", (section_id,)).fetchone()
                        con.execute(\"\"\"
                            update wiki_sections set title=?, summary=?, display_order=?, updated_at=? where id=?
                        \"\"\", (title or row['title'], summary if summary is not None else row['summary'],
                              display_order if display_order is not None else row['display_order'], now(), section_id))

                def delete_section(section_id, move_pages_to=None):
                    with connect() as con:
                        con.execute("update wiki_pages set section_id=? where section_id=?", (move_pages_to, section_id))
                        con.execute("delete from wiki_sections where id=?", (section_id,))

                def search_pages(query):
                    like = f"%{query}%"
                    with connect() as con:
                        return [dict(r) for r in con.execute(
                            "select * from wiki_pages where title like ? or summary like ? or body like ? order by updated_at desc limit 20",
                            (like, like, like)
                        )]

                def upsert_page(section_id, title, summary, body, importance=50):
                    slug = re.sub(r'(^-|-$)', '', re.sub(r'[^a-z0-9가-힣]+', '-', title.lower()))
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
                """.formatted(sqlitePath, jobRunId);
    }
}
```

- [ ] **Step 4: Run helper test**

```bash
rtk ./gradlew test --tests CodexWikiJobBuilderTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/newswiki/infrastructure/ai/CodexWikiJobBuilder.java src/test/java/com/newswiki/infrastructure/ai/CodexWikiJobBuilderTest.java
git commit -m "feat: generate codex wiki helper"
```

---

### Task 4: Replace AI Article Notes With Codex Wiki Service

**Files:**
- Create: `src/main/java/com/newswiki/service/CodexWikiService.java`
- Modify: `src/main/java/com/newswiki/service/ScheduledJobs.java`
- Modify: `src/main/java/com/newswiki/config/AppProperties.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/newswiki/service/CodexWikiServiceTest.java`
- Test: `src/test/java/com/newswiki/service/ScheduledJobsTest.java`

- [ ] **Step 1: Write service test**

Create `CodexWikiServiceTest` with a fake runner:

```java
@Test
void runsCodexWikiJobWhenPendingArticlesExist() {
    var result = codexWikiService.runPendingWikiBatch(10L, (level, message) -> logs.add(level + ":" + message));

    assertThat(result.inputCount()).isEqualTo(80);
    assertThat(logs).anyMatch(line -> line.contains("Codex wiki job 시작"));
}
```

Use a test double for `CodexRunner` that records the prompt path and returns exit code 0.

- [ ] **Step 2: Run service test to verify it fails**

```bash
rtk ./gradlew test --tests CodexWikiServiceTest
```

Expected: FAIL because `CodexWikiService` is missing.

- [ ] **Step 3: Implement result record**

Create `src/main/java/com/newswiki/dto/WikiJobResult.java`:

```java
package com.newswiki.dto;

public record WikiJobResult(
        int inputCount,
        int succeeded,
        int failed,
        String detail
) {
}
```

- [ ] **Step 4: Implement `CodexWikiService`**

```java
package com.newswiki.service;

import com.newswiki.config.AppProperties;
import com.newswiki.dto.WikiJobResult;
import com.newswiki.infrastructure.ai.CodexRunner;
import com.newswiki.infrastructure.ai.CodexWikiJobBuilder;
import com.newswiki.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.function.BiConsumer;

@Service
@RequiredArgsConstructor
public class CodexWikiService {
    private final ArticleRepository articles;
    private final CodexWikiJobBuilder jobBuilder;
    private final CodexRunner codexRunner;
    private final AppProperties properties;

    public WikiJobResult runPendingWikiBatch(long jobRunId, BiConsumer<String, String> log) {
        int pending = articles.countPendingWikiArticles();
        if (pending == 0) {
            return new WikiJobResult(0, 0, 0, "no pending wiki articles");
        }

        int input = Math.min(pending, properties.aiBatchSize());
        Path baseDir = Path.of(properties.dataDir(), "wiki-jobs");
        Path jobDir = jobBuilder.build(baseDir, Path.of(properties.dataDir(), "newswiki.sqlite").toString(), jobRunId, input);
        log.accept("INFO", "Codex wiki job 시작: input=" + input + ", jobDir=" + jobDir);
        var exit = codexRunner.run(jobDir.resolve("prompt.md"), jobDir, properties.aiTimeoutSeconds());
        if (exit.exitCode() == 0) {
            return new WikiJobResult(input, 0, 0, "codex finished; article-level counts are stored in DB");
        }
        return new WikiJobResult(input, 0, input, exit.stderrExcerpt());
    }
}
```

- [ ] **Step 5: Update scheduled jobs**

In `ScheduledJobs`, replace `AiOrchestrationService aiService` with `CodexWikiService wikiService`. In ingest:

```java
if (articleRepository.countPendingWikiArticles() > 0) {
    runs.appendLog(runId, "INFO", "위키 작업 시작: pending " + articleRepository.countPendingWikiArticles() + "개");
    var wikiResult = wikiService.runPendingWikiBatch(runId, (level, message) -> runs.appendLog(runId, level, message));
    runs.appendLog(runId, "INFO", "위키 작업 완료: 입력 " + wikiResult.inputCount() + "개, detail=" + wikiResult.detail());
} else {
    runs.appendLog(runId, "INFO", "위키 처리 대기 기사가 없어 위키 작업을 건너뜀");
}
```

Rename daily rebuild wording to wiki backlog processing.

- [ ] **Step 6: Run service and scheduler tests**

```bash
rtk ./gradlew test --tests CodexWikiServiceTest --tests ScheduledJobsTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/newswiki/service/CodexWikiService.java src/main/java/com/newswiki/dto/WikiJobResult.java src/main/java/com/newswiki/service/ScheduledJobs.java src/main/java/com/newswiki/config/AppProperties.java src/main/resources/application.yml src/test/java/com/newswiki/service/CodexWikiServiceTest.java src/test/java/com/newswiki/service/ScheduledJobsTest.java
git commit -m "feat: run codex wiki batches"
```

---

### Task 5: Add Read-Only Wiki Rendering Repository And Services

**Files:**
- Create: `src/main/java/com/newswiki/repository/WikiPageRepository.java`
- Create: `src/main/java/com/newswiki/dto/WikiSection.java`
- Create: `src/main/java/com/newswiki/dto/WikiPageListItem.java`
- Create: `src/main/java/com/newswiki/dto/WikiPageDetail.java`
- Create: `src/main/java/com/newswiki/dto/WikiSourceRef.java`
- Modify: `src/main/java/com/newswiki/service/NewsViewService.java`
- Test: `src/test/java/com/newswiki/repository/WikiPageRepositoryTest.java`
- Test: `src/test/java/com/newswiki/service/NewsViewServiceTest.java`

- [ ] **Step 1: Write repository tests**

Create `WikiPageRepositoryTest`:

```java
@Test
void readsActiveSectionsAndPagesFromWikiTables() {
    jdbc.update("insert into wiki_sections(slug,title,summary,display_order,status,created_at,updated_at) values('ai','AI','AI 흐름',10,'ACTIVE',datetime('now'),datetime('now'))");
    jdbc.update("insert into wiki_pages(section_id,slug,title,summary,body,importance,status,created_at,updated_at) values(1,'gpu-power','GPU 전력','전력 병목','본문',90,'ACTIVE',datetime('now'),datetime('now'))");

    assertThat(repository.findSections()).extracting(WikiSection::slug).containsExactly("ai");
    assertThat(repository.findPagesBySection("ai")).extracting(WikiPageListItem::title).containsExactly("GPU 전력");
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
rtk ./gradlew test --tests WikiPageRepositoryTest
```

Expected: FAIL because repository and DTOs are missing.

- [ ] **Step 3: Add DTOs**

Create records:

```java
public record WikiSection(long id, String slug, String title, String summary, int displayOrder) {}
public record WikiPageListItem(long id, String slug, String title, String summary, int importance, String updatedAt) {}
public record WikiSourceRef(long articleId, String providerName, String title, String canonicalUrl, String contributionSummary) {}
public record WikiPageDetail(long id, String slug, String title, String summary, String body, int importance, String updatedAt, List<WikiSourceRef> sources) {}
```

- [ ] **Step 4: Implement repository**

Use native JPA queries in `WikiPageRepository`:

```java
@Repository
@RequiredArgsConstructor
public class WikiPageRepository {
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<WikiSection> findSections() {
        return entityManager.createNativeQuery("""
                select id, slug, title, summary, display_order
                  from wiki_sections
                 where status='ACTIVE'
                 order by display_order asc, title asc
                """).getResultStream().map(row -> {
            Object[] v = (Object[]) row;
            return new WikiSection(((Number) v[0]).longValue(), v[1].toString(), v[2].toString(), v[3].toString(), ((Number) v[4]).intValue());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<WikiPageListItem> findPagesBySection(String sectionSlug) {
        return entityManager.createNativeQuery("""
                select p.id, p.slug, p.title, p.summary, p.importance, p.updated_at
                  from wiki_pages p
                  join wiki_sections s on s.id=p.section_id
                 where s.slug=:sectionSlug and p.status='ACTIVE'
                 order by p.importance desc, p.updated_at desc
                """).setParameter("sectionSlug", sectionSlug)
                .getResultStream().map(this::pageListItem).toList();
    }
}
```

Add private mappers in the same file.

- [ ] **Step 5: Run repository tests**

```bash
rtk ./gradlew test --tests WikiPageRepositoryTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/newswiki/repository/WikiPageRepository.java src/main/java/com/newswiki/dto/WikiSection.java src/main/java/com/newswiki/dto/WikiPageListItem.java src/main/java/com/newswiki/dto/WikiPageDetail.java src/main/java/com/newswiki/dto/WikiSourceRef.java src/main/java/com/newswiki/service/NewsViewService.java src/test/java/com/newswiki/repository/WikiPageRepositoryTest.java src/test/java/com/newswiki/service/NewsViewServiceTest.java
git commit -m "feat: render wiki pages from sqlite"
```

---

### Task 6: Rebuild Web UI Around Wiki Pages

**Files:**
- Modify: `src/main/java/com/newswiki/application/HomeController.java`
- Modify: `src/main/java/com/newswiki/application/SectionController.java`
- Modify: `src/main/java/com/newswiki/application/WikiController.java`
- Modify: `src/main/resources/templates/pages/home.html`
- Modify: `src/main/resources/templates/pages/section.html`
- Modify: `src/main/resources/templates/pages/wiki-detail.html`
- Modify: `src/main/resources/templates/pages/jobs.html`
- Modify: `src/main/resources/static/css/components.css`
- Test: `src/test/java/com/newswiki/application/HomeControllerTest.java`
- Test: `src/test/java/com/newswiki/application/SectionControllerTest.java`
- Test: `src/test/java/com/newswiki/application/WikiControllerTest.java`

- [ ] **Step 1: Write controller tests**

Add assertions that controllers call services and render wiki model attributes:

```java
@Test
void homeRendersWikiSectionsAndPages() throws Exception {
    when(newsViewService.home()).thenReturn(new HomeView(
            List.of(new SectionNavItem("ai", "AI", false)),
            List.of(new WikiPageListItem(1L, "gpu-power", "GPU 전력", "요약", 90, "2026-07-03T00:00:00Z"))
    ));

    mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("sections"))
            .andExpect(model().attributeExists("pages"));
}
```

- [ ] **Step 2: Run controller tests to verify failures**

```bash
rtk ./gradlew test --tests HomeControllerTest --tests SectionControllerTest --tests WikiControllerTest
```

Expected: FAIL where old article-note model attributes are still expected.

- [ ] **Step 3: Update templates**

Home page must use:

```html
<nav class="section-nav">
  <a th:each="section : ${sections}"
     th:href="@{/sections/{slug}(slug=${section.slug})}"
     th:text="${section.title}"></a>
</nav>

<main class="wiki-page-list">
  <article th:each="page : ${pages}" class="wiki-page-card">
    <a th:href="@{/wiki/{slug}(slug=${page.slug})}" th:text="${page.title}"></a>
    <p th:text="${page.summary}"></p>
  </article>
</main>
```

Wiki detail must use:

```html
<article class="wiki-document">
  <h1 th:text="${page.title}"></h1>
  <p class="wiki-summary" th:text="${page.summary}"></p>
  <section class="wiki-body" th:utext="${page.body}"></section>
  <aside class="wiki-sources">
    <h2>출처</h2>
    <a th:each="source : ${page.sources}"
       th:href="${source.canonicalUrl}"
       th:text="${source.providerName + ' - ' + source.title}"></a>
  </aside>
</article>
```

- [ ] **Step 4: Run controller tests**

```bash
rtk ./gradlew test --tests HomeControllerTest --tests SectionControllerTest --tests WikiControllerTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/newswiki/application/HomeController.java src/main/java/com/newswiki/application/SectionController.java src/main/java/com/newswiki/application/WikiController.java src/main/resources/templates/pages/home.html src/main/resources/templates/pages/section.html src/main/resources/templates/pages/wiki-detail.html src/main/resources/templates/pages/jobs.html src/main/resources/static/css/components.css src/test/java/com/newswiki/application/HomeControllerTest.java src/test/java/com/newswiki/application/SectionControllerTest.java src/test/java/com/newswiki/application/WikiControllerTest.java
git commit -m "feat: render wiki-first web ui"
```

---

### Task 7: Recovery, Logs, And Job Page Behavior

**Files:**
- Modify: `src/main/java/com/newswiki/service/StartupRecoveryService.java`
- Modify: `src/main/java/com/newswiki/repository/ArticleRepository.java`
- Modify: `src/main/java/com/newswiki/repository/JobRunRepository.java`
- Modify: `src/main/resources/templates/pages/jobs.html`
- Test: `src/test/java/com/newswiki/service/StartupRecoveryServiceTest.java`
- Test: `src/test/java/com/newswiki/repository/JobRunRepositoryTest.java`

- [ ] **Step 1: Write recovery test**

```java
@Test
void recoversStaleWikiRunningArticlesOnStartup() {
    jdbc.update("""
            insert into articles(source_id, canonical_url, provider_id, title, feed_url, ingested_at, content_hash, raw_status, wiki_status, wiki_locked_at)
            values('s1','https://example.com/1',1,'기사','rss',datetime('now'),'h','FETCHED','RUNNING','2026-07-03T00:00:00Z')
            """);

    int recovered = articleRepository.recoverInterruptedWikiRunning();

    assertThat(recovered).isEqualTo(1);
    assertThat(jdbc.queryForObject("select wiki_status from articles where source_id='s1'", String.class)).isEqualTo("PENDING");
}
```

- [ ] **Step 2: Run recovery test to verify it fails**

```bash
rtk ./gradlew test --tests StartupRecoveryServiceTest --tests JobRunRepositoryTest
```

Expected: FAIL until wiki recovery replaces AI recovery.

- [ ] **Step 3: Implement repository recovery**

Add:

```java
@Transactional
public int recoverInterruptedWikiRunning() {
    return entityManager.createNativeQuery("""
            update articles
               set wiki_status='PENDING',
                   wiki_locked_at=null,
                   wiki_last_error='Server restarted while wiki processing was running'
             where wiki_status='RUNNING'
            """).executeUpdate();
}

@Transactional(readOnly = true)
public int countPendingWikiArticles() {
    Number count = (Number) entityManager.createNativeQuery("""
            select count(*)
              from articles a
              join article_raw_sources r on r.article_id=a.id
             where a.wiki_status='PENDING'
            """).getSingleResult();
    return count == null ? 0 : count.intValue();
}
```

- [ ] **Step 4: Keep job logs copyable**

In `jobs.html`, avoid replacing the whole log container every second. Render logs in a stable container and only refresh when the user presses refresh or when no text is selected:

```javascript
setInterval(() => {
  const selection = window.getSelection();
  if (selection && selection.toString().length > 0) {
    return;
  }
  refreshLogs();
}, 3000);
```

Render logs oldest-to-newest:

```sql
select *
  from job_logs
 where job_run_id = :runId
 order by created_at asc, id asc
```

- [ ] **Step 5: Run recovery/log tests**

```bash
rtk ./gradlew test --tests StartupRecoveryServiceTest --tests JobRunRepositoryTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/newswiki/service/StartupRecoveryService.java src/main/java/com/newswiki/repository/ArticleRepository.java src/main/java/com/newswiki/repository/JobRunRepository.java src/main/resources/templates/pages/jobs.html src/test/java/com/newswiki/service/StartupRecoveryServiceTest.java src/test/java/com/newswiki/repository/JobRunRepositoryTest.java
git commit -m "feat: recover interrupted wiki jobs"
```

---

### Task 8: Remove Main-Path Article Note Pipeline

**Files:**
- Delete or stop using: `src/main/java/com/newswiki/dto/AiArticleResult.java`
- Delete or stop using: `src/main/java/com/newswiki/dto/ArticleNote.java`
- Delete or stop using: `src/main/java/com/newswiki/repository/entity/ArticleNoteEntity.java`
- Delete or stop using: `src/main/java/com/newswiki/service/AiOrchestrationService.java`
- Delete or stop using: `src/main/java/com/newswiki/infrastructure/ai/AiBatchBuilder.java`
- Modify: tests that reference article notes.

- [ ] **Step 1: Find article-note references**

Run:

```bash
rg -n "ArticleNote|AiArticleResult|AiOrchestrationService|AiBatchBuilder|article_notes|AI_DONE|PENDING_AI|AI_FAILED|AI_RUNNING" src
```

Expected: references remain before cleanup.

- [ ] **Step 2: Remove references from main path**

Replace status names:

```text
PENDING_AI -> PENDING
AI_RUNNING -> RUNNING
AI_DONE -> DONE
AI_FAILED -> FAILED
```

Remove imports and constructor parameters for old AI note services. Keep old tables in the database for migration compatibility, but no server screen should depend on them.

- [ ] **Step 3: Run reference check**

```bash
rg -n "ArticleNote|AiArticleResult|AiOrchestrationService|AiBatchBuilder|AI_DONE|PENDING_AI|AI_FAILED|AI_RUNNING" src/main src/test
```

Expected: no output, except migration files that preserve historical schema.

- [ ] **Step 4: Run full tests**

```bash
rtk ./gradlew test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main src/test
git commit -m "refactor: remove article note main path"
```

---

### Task 9: Docker And End-To-End Verification

**Files:**
- Modify: `Dockerfile`
- Modify: `docker-compose.yml`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Ensure container has Python helper dependencies**

In `Dockerfile`, keep:

```dockerfile
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gzip gosu nodejs npm python3 python3-bs4 \
    && npm install -g @openai/codex \
    && rm -rf /var/lib/apt/lists/*
```

- [ ] **Step 2: Ensure compose config has wiki settings**

In `docker-compose.yml`, set:

```yaml
environment:
  NEWSWIKI_INGEST_CRON: "0 */5 * * * *"
  NEWSWIKI_AI_BATCH_SIZE: "80"
  NEWSWIKI_AI_TIMEOUT_SECONDS: "1800"
  RSS_FEED_ENTRY_LIMIT: "5"
```

- [ ] **Step 3: Build jar**

```bash
rtk ./gradlew bootJar
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Build and restart container**

```bash
rtk docker compose up -d --build news-wiki
```

Expected: container starts and logs show Spring Boot started.

- [ ] **Step 5: Smoke test database-backed wiki**

Run:

```bash
rtk docker compose exec news-wiki sqlite3 /app/data/newswiki.sqlite ".tables"
```

Expected output includes:

```text
article_raw_sources
wiki_sections
wiki_pages
wiki_page_sources
wiki_revisions
wiki_runs
```

- [ ] **Step 6: Trigger one ingest**

Use the job page force-run button or call the internal job endpoint already used by `JobControllerTest`.

Expected logs:

```text
INGEST 작업 시작
기사 원문 저장
위키 작업 시작
Codex wiki job 시작
```

- [ ] **Step 7: Commit Docker/runtime changes**

```bash
git add Dockerfile docker-compose.yml src/main/resources/application.yml
git commit -m "chore: package codex wiki runtime"
```

---

## Final Verification

Run:

```bash
rtk ./gradlew test
rtk ./gradlew bootJar
rtk docker compose up -d --build news-wiki
rtk docker compose logs --tail=120 news-wiki
```

Expected:

- Gradle tests pass.
- Boot jar builds.
- Docker image builds.
- Application starts.
- Job page shows logs oldest-to-newest.
- RSS ingest stores raw HTML in SQLite text.
- Codex wiki job creates or updates `wiki_sections` and `wiki_pages`.
- Home/section/wiki pages render from wiki tables.
