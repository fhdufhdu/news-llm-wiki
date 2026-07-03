package com.newswiki.service;

import com.newswiki.config.AppProperties;
import com.newswiki.dto.RssEntry;
import com.newswiki.dto.RssSource;
import com.newswiki.infrastructure.http.ArticleFetcher;
import com.newswiki.infrastructure.rss.RssIndexExpander;
import com.newswiki.infrastructure.rss.RssParser;
import com.newswiki.infrastructure.rss.RssSourceLoader;
import com.newswiki.infrastructure.text.UrlCanonicalizer;
import com.newswiki.repository.ArticleFetchFailureRepository;
import com.newswiki.repository.ArticleRepository;
import com.newswiki.repository.WikiRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RssIngestService {
    private final AppProperties properties;
    private final RssSourceLoader sourceLoader;
    private final ArticleRepository articleRepository;
    private final ArticleService articleService;
    private final RssParser rssParser;
    private final RssIndexExpander rssIndexExpander;
    private final ArticleFetcher articleFetcher;
    private final ArticleFetchFailureRepository failureRepository;
    private final WikiRepository wikiRepository;
    private final UrlCanonicalizer canonicalizer = new UrlCanonicalizer();

    public RssIngestService(
            AppProperties properties,
            RssSourceLoader sourceLoader,
            ArticleRepository articleRepository,
            ArticleService articleService,
            RssParser rssParser,
            RssIndexExpander rssIndexExpander,
            ArticleFetcher articleFetcher,
            ArticleFetchFailureRepository failureRepository,
            WikiRepository wikiRepository
    ) {
        this.properties = properties;
        this.sourceLoader = sourceLoader;
        this.articleRepository = articleRepository;
        this.articleService = articleService;
        this.rssParser = rssParser;
        this.rssIndexExpander = rssIndexExpander;
        this.articleFetcher = articleFetcher;
        this.failureRepository = failureRepository;
        this.wikiRepository = wikiRepository;
    }

    public Result ingest() {
        return ingest((level, message) -> {
        });
    }

    public Result ingest(IngestLogger logger) {
        var sources = sourceLoader.load(Path.of(properties.rssSourcesPath()));
        AtomicInteger discovered = new AtomicInteger();
        AtomicInteger rssFeeds = new AtomicInteger();
        AtomicInteger rssIndexes = new AtomicInteger();
        AtomicInteger saved = new AtomicInteger();
        AtomicInteger feedErrors = new AtomicInteger();
        AtomicInteger articleErrors = new AtomicInteger();
        int parallelism = Math.max(1, properties.ingestParallelism());
        logger.log("INFO", "RSS 수집 준비: source " + sources.size() + "개, parallelism " + parallelism);
        retryFailedArticles(logger, saved, articleErrors);
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (RssSource source : sources) {
                if (!source.enabled()) {
                    continue;
                }
                futures.add(executor.submit(() -> processSource(
                        source,
                        logger,
                        discovered,
                        rssFeeds,
                        rssIndexes,
                        saved,
                        feedErrors,
                        articleErrors
                )));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    feedErrors.incrementAndGet();
                    logger.log("ERROR", "RSS 병렬 작업 실패: " + e.getMessage());
                }
            }
        } finally {
            executor.shutdown();
        }
        return new Result(discovered.get(), rssFeeds.get(), rssIndexes.get(), saved.get(), feedErrors.get(), articleErrors.get());
    }

    private void processSource(
            RssSource source,
            IngestLogger logger,
            AtomicInteger discovered,
            AtomicInteger rssFeeds,
            AtomicInteger rssIndexes,
            AtomicInteger saved,
            AtomicInteger feedErrors,
            AtomicInteger articleErrors
    ) {
        discovered.incrementAndGet();
        logger.log("INFO", "RSS source 시작: " + source.name() + " (" + source.type() + ")");
        int sourceSaved = 0;
        if ("rss_index".equalsIgnoreCase(source.type())) {
            rssIndexes.incrementAndGet();
            try {
                var index = articleFetcher.fetch(source.url());
                if (index.statusCode() < 200 || index.statusCode() >= 300) {
                    feedErrors.incrementAndGet();
                    logger.log("WARN", "RSS index 응답 오류: " + source.name() + " status=" + index.statusCode());
                    return;
                }
                List<String> feedUrls = rssIndexExpander.extractFeedUrls(source.url(), index.html());
                logger.log("INFO", "RSS index 확장: " + source.name() + " feed " + feedUrls.size() + "개");
                for (String feedUrl : feedUrls) {
                    rssFeeds.incrementAndGet();
                    int feedSaved = processFeed(source, feedUrl, logger, feedErrors, articleErrors);
                    saved.addAndGet(feedSaved);
                    sourceSaved += feedSaved;
                }
            } catch (Exception e) {
                feedErrors.incrementAndGet();
                logger.log("ERROR", "RSS index 처리 실패: " + source.name() + " - " + e.getMessage());
            }
        } else if ("rss".equalsIgnoreCase(source.type())) {
            rssFeeds.incrementAndGet();
            int feedSaved = processFeed(source, source.url(), logger, feedErrors, articleErrors);
            saved.addAndGet(feedSaved);
            sourceSaved += feedSaved;
        }
        logger.log("INFO", "RSS source 완료: " + source.name() + ", 신규 " + sourceSaved + "개");
    }

    private int processFeed(RssSource source, String feedUrl, IngestLogger logger, AtomicInteger feedErrors, AtomicInteger articleErrors) {
        try {
            logger.log("INFO", "RSS feed 확인: " + source.name() + " - " + feedUrl);
            var feed = articleFetcher.fetch(feedUrl);
            if (feed.statusCode() < 200 || feed.statusCode() >= 300) {
                feedErrors.incrementAndGet();
                logger.log("WARN", "RSS feed 응답 오류: " + feedUrl + " status=" + feed.statusCode());
                return 0;
            }
            int saved = 0;
            for (RssEntry entry : rssParser.parse(feed.html())) {
                try {
                    if (saveEntry(source, feedUrl, entry, logger, articleErrors)) {
                        saved++;
                        logger.log("INFO", "기사 저장: " + source.name() + " - " + entry.title());
                    }
                } catch (Exception e) {
                    articleErrors.incrementAndGet();
                    logger.log("WARN", "기사 처리 실패: " + feedUrl + " - " + e.getMessage());
                }
            }
            logger.log("INFO", "RSS feed 완료: " + source.name() + ", 신규 " + saved + "개");
            return saved;
        } catch (Exception e) {
            feedErrors.incrementAndGet();
            logger.log("ERROR", "RSS feed 처리 실패: " + feedUrl + " - " + e.getMessage());
            return 0;
        }
    }

    private void retryFailedArticles(IngestLogger logger, AtomicInteger saved, AtomicInteger articleErrors) {
        List<ArticleFetchFailureRepository.FailureItem> failures = failureRepository.findRetryable(500);
        if (failures.isEmpty()) {
            return;
        }
        logger.log("INFO", "실패 기사 재처리 시작: " + failures.size() + "개");
        for (ArticleFetchFailureRepository.FailureItem failure : failures) {
            try {
                if (saveArticle(
                        failure.canonicalUrl(),
                        failure.sourceKey(),
                        failure.sourceName(),
                        failure.feedUrl(),
                        failure.title(),
                        failure.publishedAt(),
                        logger,
                        articleErrors
                )) {
                    saved.incrementAndGet();
                    logger.log("INFO", "실패 기사 재처리 성공: " + failure.title());
                }
            } catch (Exception e) {
                articleErrors.incrementAndGet();
                logger.log("WARN", "실패 기사 재처리 오류: " + failure.canonicalUrl() + " - " + e.getMessage());
            }
        }
        logger.log("INFO", "실패 기사 재처리 완료");
    }

    private boolean saveEntry(RssSource source, String feedUrl, RssEntry entry, IngestLogger logger, AtomicInteger articleErrors) {
        if (entry.url() == null || entry.url().isBlank()) {
            return false;
        }
        String canonicalUrl = canonicalizer.canonicalize(entry.url());
        if (failureRepository.isIgnored(canonicalUrl)) {
            logger.log("INFO", "기사 원문 fetch 무시: 재시도 한도 초과 - " + entry.title());
            return false;
        }
        return saveArticle(
                canonicalUrl,
                source.sourceKey(),
                source.name(),
                feedUrl,
                entry.title(),
                entry.published(),
                logger,
                articleErrors
        );
    }

    private boolean saveArticle(
            String canonicalUrl,
            String sourceKey,
            String sourceName,
            String feedUrl,
            String title,
            String published,
            IngestLogger logger,
            AtomicInteger articleErrors
    ) {
        if (articleRepository.existsByCanonicalUrl(canonicalUrl)) {
            failureRepository.deleteByCanonicalUrl(canonicalUrl);
            return false;
        }

        ArticleFetcher.FetchedArticle article;
        try {
            article = articleFetcher.fetch(canonicalUrl);
        } catch (Exception e) {
            articleErrors.incrementAndGet();
            recordArticleFetchFailure(canonicalUrl, sourceKey, sourceName, feedUrl, title, published, describe(e), logger);
            return false;
        }
        if (article.statusCode() < 200 || article.statusCode() >= 300 || article.html().isBlank()) {
            String error = "status=" + article.statusCode() + ", blank=" + article.html().isBlank();
            articleErrors.incrementAndGet();
            recordArticleFetchFailure(canonicalUrl, sourceKey, sourceName, feedUrl, title, published, error, logger);
            return false;
        }

        Instant publishedAt = parseInstant(published);
        String contentHash = articleService.sha256(article.html());
        String sourceId = sourceKey + "-" + articleService.sha256(canonicalUrl).substring(0, 16);
        long providerId = wikiRepository.upsertProviderByName(sourceName);
        long articleId = articleRepository.insertArticleIfAbsent(
                sourceId,
                canonicalUrl,
                providerId,
                title == null || title.isBlank() ? canonicalUrl : title,
                feedUrl,
                publishedAt,
                contentHash
        );
        Path rawPath = articleService.writeGzipRaw(Path.of(properties.dataDir()), sourceId, publishedAt, article.html());
        articleService.recordRawFile(articleId, rawPath, article.contentType(), article.statusCode());
        failureRepository.deleteByCanonicalUrl(canonicalUrl);
        return true;
    }

    private void recordArticleFetchFailure(
            String canonicalUrl,
            String sourceKey,
            String sourceName,
            String feedUrl,
            String title,
            String published,
            String error,
            IngestLogger logger
    ) {
        failureRepository.recordFailure(
                new ArticleFetchFailureRepository.FailureInput(
                        canonicalUrl,
                        sourceKey,
                        sourceName,
                        feedUrl,
                        title,
                        published
                ),
                error,
                properties.articleFetchMaxRetries()
        );
        if (failureRepository.isIgnored(canonicalUrl)) {
            logger.log("WARN", "기사 원문 fetch 실패: 재시도 한도 도달, 이후 무시 - " + title + " (" + error + ")");
        } else {
            logger.log("WARN", "기사 원문 fetch 실패: 재처리 큐 등록 - " + title + " (" + error + ")");
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String describe(Throwable error) {
        String message = error.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        Throwable cursor = error;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String causeMessage = cursor.getMessage();
        return cursor.getClass().getSimpleName() + (causeMessage == null || causeMessage.isBlank() ? "" : " - " + causeMessage);
    }

    public record Result(
            int feedsSeen,
            int directRssFeeds,
            int rssIndexes,
            int articlesSaved,
            int feedErrors,
            int articleErrors
    ) {
    }

    public interface IngestLogger {
        void log(String level, String message);
    }
}
