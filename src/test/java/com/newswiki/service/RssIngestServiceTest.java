package com.newswiki.service;

import com.newswiki.config.AppProperties;
import com.newswiki.infrastructure.http.ArticleFetcher;
import com.newswiki.infrastructure.rss.RssIndexExpander;
import com.newswiki.infrastructure.rss.RssParser;
import com.newswiki.infrastructure.rss.RssSourceLoader;
import com.newswiki.repository.ArticleFetchFailureRepository;
import com.newswiki.repository.ArticleRepository;
import com.newswiki.repository.WikiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:rss_ingest_service_test?mode=memory&cache=shared",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=none"
})
class RssIngestServiceTest {
    @TempDir
    Path tempDir;

    @Autowired
    ArticleRepository articleRepository;

    @Autowired
    ArticleFetchFailureRepository failureRepository;

    @Autowired
    WikiRepository wikiRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from article_fetch_failures");
        jdbcTemplate.update("delete from article_raw");
        jdbcTemplate.update("delete from articles");
        jdbcTemplate.update("delete from providers where slug = '샘플뉴스'");
    }

    @Test
    void fetchesRssItemsAndStoresRawArticleHtmlOnce() throws Exception {
        Path rssSources = tempDir.resolve("rss-sources.yaml");
        Files.writeString(rssSources, """
                sources:
                  - id: sample
                    name: 샘플뉴스
                    type: rss
                    url: https://example.com/rss.xml
                    categories: tech
                """);
        var service = new RssIngestService(
                properties(rssSources),
                new RssSourceLoader(),
                articleRepository,
                new ArticleService(articleRepository),
                new RssParser(),
                new RssIndexExpander(),
                new FakeArticleFetcher(properties(rssSources)),
                failureRepository,
                wikiRepository
        );

        var first = service.ingest();
        var second = service.ingest();

        assertThat(first.feedsSeen()).isEqualTo(1);
        assertThat(first.articlesSaved()).isEqualTo(1);
        assertThat(second.articlesSaved()).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from articles", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from article_raw", Integer.class)).isEqualTo(1);
        String storageMode = jdbcTemplate.queryForObject("select storage_mode from article_raw", String.class);
        Integer rawBytes = jdbcTemplate.queryForObject("select length(html_gzip) from article_raw", Integer.class);
        Integer rawFiles = Math.toIntExact(Files.walk(tempDir.resolve("data").resolve("raw"))
                .filter(Files::isRegularFile)
                .count());
        assertThat(storageMode).isEqualTo("DB_GZIP");
        assertThat(rawBytes).isGreaterThan(0);
        assertThat(rawFiles).isZero();
    }

    @Test
    void retriesFailedArticleFetchesAndClearsFailureAfterSuccess() throws Exception {
        Path rssSources = writeSingleSource();
        var fetcher = new FailOnceArticleFetcher(properties(rssSources));
        var service = new RssIngestService(
                properties(rssSources),
                new RssSourceLoader(),
                articleRepository,
                new ArticleService(articleRepository),
                new RssParser(),
                new RssIndexExpander(),
                fetcher,
                failureRepository,
                wikiRepository
        );
        List<String> logs = new ArrayList<>();

        var first = service.ingest((level, message) -> logs.add(level + " " + message));
        var second = service.ingest((level, message) -> logs.add(level + " " + message));

        assertThat(first.articlesSaved()).isZero();
        assertThat(first.articleErrors()).isEqualTo(1);
        assertThat(second.articlesSaved()).isEqualTo(1);
        assertThat(second.articleErrors()).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from articles", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from article_fetch_failures", Integer.class)).isZero();
        assertThat(logs).anySatisfy(line -> assertThat(line).contains("기사 원문 fetch 실패"));
        assertThat(logs).anySatisfy(line -> assertThat(line).contains("실패 기사 재처리"));
    }

    @Test
    void ignoresFailedArticleAfterConfiguredRetryLimit() throws Exception {
        Path rssSources = writeSingleSource();
        var fetcher = new AlwaysFailArticleFetcher(properties(rssSources));
        var service = new RssIngestService(
                properties(rssSources),
                new RssSourceLoader(),
                articleRepository,
                new ArticleService(articleRepository),
                new RssParser(),
                new RssIndexExpander(),
                fetcher,
                failureRepository,
                wikiRepository
        );

        var first = service.ingest();
        var second = service.ingest();
        var third = service.ingest();

        Integer attempts = jdbcTemplate.queryForObject("select failure_count from article_fetch_failures", Integer.class);
        String status = jdbcTemplate.queryForObject("select status from article_fetch_failures", String.class);
        assertThat(first.articleErrors()).isEqualTo(1);
        assertThat(second.articleErrors()).isEqualTo(1);
        assertThat(third.articleErrors()).isZero();
        assertThat(attempts).isEqualTo(2);
        assertThat(status).isEqualTo("IGNORED");
        assertThat(fetcher.articleFetches.get()).isEqualTo(2);
    }

    private Path writeSingleSource() throws Exception {
        Path rssSources = tempDir.resolve("rss-sources.yaml");
        Files.writeString(rssSources, """
                sources:
                  - id: sample
                    name: 샘플뉴스
                    type: rss
                    url: https://example.com/rss.xml
                    categories: tech
                """);
        return rssSources;
    }

    private AppProperties properties(Path rssSources) {
        return new AppProperties(
                rssSources.toString(),
                tempDir.resolve("data").toString(),
                "/tmp/codex",
                "gpt-5.5",
                "workspace-write",
                "0 0 * * * *",
                "0 30 3 * * *",
                25,
                3,
                80,
                1800,
                15,
                10,
                2,
                2,
                false,
                "DB_GZIP"
        );
    }

    private static class FakeArticleFetcher extends ArticleFetcher {
        FakeArticleFetcher(AppProperties properties) {
            super(properties);
        }

        @Override
        public FetchedArticle fetch(String url) {
            if (url.endsWith("rss.xml")) {
                return new FetchedArticle(200, "application/rss+xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <rss version="2.0">
                          <channel>
                            <title>Sample</title>
                            <item>
                              <title>테스트 기사</title>
                              <link>https://example.com/articles/1?utm_source=rss</link>
                              <guid>article-1</guid>
                              <pubDate>Thu, 02 Jul 2026 12:00:00 +0900</pubDate>
                              <description>테스트 기사 설명</description>
                            </item>
                          </channel>
                        </rss>
                        """);
            }
            return new FetchedArticle(200, "text/html", """
                    <!doctype html>
                    <html lang="ko"><head><title>테스트 기사</title></head><body>본문</body></html>
                    """);
        }
    }

    private static class FailOnceArticleFetcher extends FakeArticleFetcher {
        private final AtomicInteger articleFetches = new AtomicInteger();

        FailOnceArticleFetcher(AppProperties properties) {
            super(properties);
        }

        @Override
        public FetchedArticle fetch(String url) {
            if (url.contains("/articles/1")) {
                if (articleFetches.incrementAndGet() == 1) {
                    return new FetchedArticle(503, "text/html", "");
                }
            }
            return super.fetch(url);
        }
    }

    private static class AlwaysFailArticleFetcher extends FakeArticleFetcher {
        private final AtomicInteger articleFetches = new AtomicInteger();

        AlwaysFailArticleFetcher(AppProperties properties) {
            super(properties);
        }

        @Override
        public FetchedArticle fetch(String url) {
            if (url.contains("/articles/1")) {
                articleFetches.incrementAndGet();
                return new FetchedArticle(503, "text/html", "");
            }
            return super.fetch(url);
        }
    }
}
