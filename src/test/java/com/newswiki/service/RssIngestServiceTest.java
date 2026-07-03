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
        jdbcTemplate.update("delete from article_raw_sources");
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
        assertThat(jdbcTemplate.queryForObject("select count(*) from article_raw_sources", Integer.class)).isEqualTo(1);
        String rawHtml = jdbcTemplate.queryForObject("select raw_html from article_raw_sources", String.class);
        assertThat(rawHtml).contains("<body>본문</body>");
        assertThat(Files.exists(tempDir.resolve("data").resolve("raw"))).isFalse();
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

    @Test
    void limitsUnreadEntriesPerFeedInsteadOfTopEntries() throws Exception {
        Path rssSources = writeSingleSource();
        AppProperties props = properties(rssSources, 2);
        long providerId = wikiRepository.upsertProviderByName("샘플뉴스");
        for (int i = 1; i <= 3; i++) {
            articleRepository.insertArticleIfAbsent(
                    "existing-" + i,
                    "https://example.com/articles/" + i,
                    providerId,
                    "기존 기사 " + i,
                    "https://example.com/rss.xml",
                    null,
                    "hash-existing-" + i
            );
        }
        var service = new RssIngestService(
                props,
                new RssSourceLoader(),
                articleRepository,
                new ArticleService(articleRepository),
                new RssParser(),
                new RssIndexExpander(),
                new ManyArticleFetcher(props),
                failureRepository,
                wikiRepository
        );

        var result = service.ingest();

        assertThat(result.articlesSaved()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from articles", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from articles where canonical_url = 'https://example.com/articles/4'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from articles where canonical_url = 'https://example.com/articles/5'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from articles where canonical_url = 'https://example.com/articles/6'",
                Integer.class
        )).isZero();
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
        return properties(rssSources, 5);
    }

    private AppProperties properties(Path rssSources, int rssFeedEntryLimit) {
        return new AppProperties(
                rssSources.toString(),
                tempDir.resolve("data").toString(),
                "/tmp/codex",
                "gpt-5.5",
                "workspace-write",
                "0 */5 * * * *",
                "0 30 3 * * *",
                25,
                3,
                80,
                1800,
                15,
                10,
                2,
                rssFeedEntryLimit,
                2,
                false,
                "SQLITE_TEXT"
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

    private static class ManyArticleFetcher extends ArticleFetcher {
        ManyArticleFetcher(AppProperties properties) {
            super(properties);
        }

        @Override
        public FetchedArticle fetch(String url) {
            if (url.endsWith("rss.xml")) {
                StringBuilder items = new StringBuilder();
                for (int i = 1; i <= 6; i++) {
                    items.append("""
                            <item>
                              <title>테스트 기사 %d</title>
                              <link>https://example.com/articles/%d</link>
                              <guid>article-%d</guid>
                              <pubDate>Thu, 02 Jul 2026 12:0%d:00 +0900</pubDate>
                              <description>테스트 기사 설명</description>
                            </item>
                            """.formatted(i, i, i, i));
                }
                return new FetchedArticle(200, "application/rss+xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <rss version="2.0">
                          <channel>
                            <title>Sample</title>
                            %s
                          </channel>
                        </rss>
                        """.formatted(items));
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
