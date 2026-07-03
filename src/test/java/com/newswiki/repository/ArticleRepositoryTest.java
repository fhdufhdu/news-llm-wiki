package com.newswiki.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:article_repository_test?mode=memory&cache=shared",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=none"
})
class ArticleRepositoryTest {
    @Autowired
    ArticleRepository repository;

    @Autowired
    WikiRepository wikiRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from article_raw_sources");
        jdbcTemplate.update("delete from article_raw");
        jdbcTemplate.update("delete from articles");
        jdbcTemplate.update("delete from providers where slug = 'sample-provider'");
    }

    @Test
    void insertsArticleOnceByCanonicalUrl() {
        long providerId = wikiRepository.upsertProviderByName("Sample Provider");
        long first = repository.insertArticleIfAbsent(
                "source-a",
                "https://example.com/a",
                providerId,
                "제목",
                "https://example.com/rss",
                Instant.parse("2026-07-02T00:00:00Z"),
                "hash-a"
        );

        long second = repository.insertArticleIfAbsent(
                "source-a",
                "https://example.com/a",
                providerId,
                "제목",
                "https://example.com/rss",
                Instant.parse("2026-07-02T00:00:00Z"),
                "hash-a"
        );

        Integer count = jdbcTemplate.queryForObject("select count(*) from articles", Integer.class);
        assertThat(second).isEqualTo(first);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void savesRawHtmlAsTextAndMarksArticlePendingForWiki() {
        long providerId = wikiRepository.upsertProviderByName("Sample Provider");
        long articleId = repository.insertArticleIfAbsent(
                "source-raw-text",
                "https://example.com/raw-text",
                providerId,
                "테스트 기사",
                "https://example.com/rss",
                Instant.parse("2026-07-03T00:00:00Z"),
                "hash-raw-text"
        );

        repository.saveRawHtml(articleId, "<html><article>본문</article></html>", "hash-html", 200);

        var row = jdbcTemplate.queryForMap("""
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
        assertThat(repository.hasRawHtml(articleId)).isTrue();
    }

    @Test
    void readsRawGzipBytesForAiMaterialization() {
        long providerId = wikiRepository.upsertProviderByName("Sample Provider");
        long articleId = repository.insertArticleIfAbsent(
                "source-raw",
                "https://example.com/raw",
                providerId,
                "제목",
                "https://example.com/rss",
                Instant.parse("2026-07-02T00:00:00Z"),
                "hash-raw"
        );
        byte[] rawGzip = new byte[]{31, -117, 8, 0, 1, 2, 3};
        long rawId = repository.insertRawGzip(articleId, rawGzip, "text/html", 200);

        assertThat(repository.findRawGzipByRawId(rawId)).isEqualTo(rawGzip);
    }

    @Test
    void recoversInterruptedAiRunningArticles() {
        long providerId = wikiRepository.upsertProviderByName("Sample Provider");
        long articleId = repository.insertArticleIfAbsent(
                "source-running",
                "https://example.com/running",
                providerId,
                "제목",
                "https://example.com/rss",
                Instant.parse("2026-07-02T00:00:00Z"),
                "hash-running"
        );
        jdbcTemplate.update("update articles set ai_status = 'AI_RUNNING' where id = ?", articleId);

        int recovered = repository.recoverInterruptedAiRunning();

        assertThat(recovered).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select ai_status from articles where id = ?",
                String.class,
                articleId
        )).isEqualTo("PENDING_AI");
    }
}
