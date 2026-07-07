package com.newswiki.service;

import com.newswiki.dto.ArticleImportProgress;
import com.newswiki.infrastructure.http.ArticleFetcher;
import com.newswiki.infrastructure.text.ArticleDateExtractor;
import com.newswiki.infrastructure.text.UrlCanonicalizer;
import com.newswiki.repository.ArticleImportRepository;
import com.newswiki.repository.ArticleRepository;
import com.newswiki.repository.JobRunRepository;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleImportService {
    private final ArticleImportRepository importRepository;
    private final ArticleRepository articleRepository;
    private final ArticleFetcher articleFetcher;
    private final ArticleService articleService;
    private final CodexWikiService codexWikiService;
    private final JobRunRepository jobRunRepository;
    private final TaskExecutor jobTaskExecutor;
    private final ArticleDateExtractor articleDateExtractor;
    private final UrlCanonicalizer canonicalizer = new UrlCanonicalizer();

    public long submitUrls(String rawUrls) {
        List<String> urls = parseUrls(rawUrls);
        return importRepository.createJob(urls);
    }

    public void runImportJobAsync(long jobId) {
        jobTaskExecutor.execute(() -> runImportJob(jobId));
    }

    public void runImportJob(long jobId) {
        var progress = findProgress(jobId);
        long runId = jobRunRepository.start("URL_IMPORT", progress.job().totalCount());
        jobRunRepository.appendLog(runId, "INFO", "URL 가져오기 작업 시작: " + progress.job().totalCount() + "개");
        importRepository.startJob(jobId, "URL 원문 수집 시작");
        int fetched = 0;
        int failed = 0;
        try {
            var items = importRepository.claimPendingItems(jobId, Math.max(1, progress.job().totalCount()));
            for (var item : items) {
                try {
                    String canonicalUrl = canonicalizer.canonicalize(item.inputUrl());
                    Long articleId = articleRepository.findIdByCanonicalUrl(canonicalUrl).orElse(null);
                    if (articleId == null) {
                        var fetchedArticle = articleFetcher.fetch(canonicalUrl);
                        if (fetchedArticle.statusCode() < 200 || fetchedArticle.statusCode() >= 300) {
                            throw new IllegalStateException("HTTP status " + fetchedArticle.statusCode());
                        }
                        String html = fetchedArticle.html();
                        String hash = articleService.sha256(canonicalUrl + "\n" + html);
                        articleId = articleRepository.saveManualRawArticle(
                                canonicalUrl,
                                extractTitle(html, canonicalUrl),
                                html,
                                fetchedArticle.statusCode(),
                                hash,
                                articleDateExtractor.extractPublishedAt(html).orElse(null)
                        );
                        jobRunRepository.appendLog(runId, "INFO", "기사 원문 저장: " + canonicalUrl);
                    } else {
                        jobRunRepository.appendLog(runId, "INFO", "기존 기사 재사용: " + canonicalUrl);
                    }
                    importRepository.markFetched(item.id(), canonicalUrl, articleId);
                    importRepository.markWikiPending(item.id());
                    fetched++;
                } catch (Exception e) {
                    failed++;
                    importRepository.markFailed(item.id(), e.getMessage());
                    jobRunRepository.appendLog(runId, "WARN", "URL 가져오기 실패: " + item.inputUrl() + " - " + e.getMessage());
                }
            }
            if (fetched > 0) {
                jobRunRepository.appendLog(runId, "INFO", "위키 생성 시작: pending 기사 " + articleRepository.countPendingWikiArticles() + "개");
                var wikiResult = codexWikiService.runPendingWikiBatch(runId, (level, message) -> jobRunRepository.appendLog(runId, level, message));
                importRepository.markDoneForCompletedWiki(jobId);
                jobRunRepository.appendLog(runId, "INFO", "위키 생성 완료: 입력 " + wikiResult.inputCount() + "개, detail=" + wikiResult.detail());
            }
            importRepository.finishJob(jobId, "URL 가져오기 완료");
            jobRunRepository.finish(runId, "SUCCESS", fetched, failed, null);
            jobRunRepository.appendLog(runId, "INFO", "URL 가져오기 작업 성공: fetch " + fetched + "개, 실패 " + failed + "개");
        } catch (Exception e) {
            importRepository.finishJob(jobId, "URL 가져오기 실패: " + e.getMessage());
            jobRunRepository.finish(runId, "FAILED", fetched, 1, e.getMessage());
            jobRunRepository.appendLog(runId, "ERROR", "URL 가져오기 작업 실패: " + e.getMessage());
        }
    }

    public ArticleImportProgress findProgress(long jobId) {
        var job = importRepository.findJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Import job not found: " + jobId));
        return new ArticleImportProgress(job, importRepository.findItems(jobId));
    }

    public List<com.newswiki.dto.ArticleImportJob> findRecentJobs() {
        return importRepository.findRecentJobs(20);
    }

    private List<String> parseUrls(String rawUrls) {
        if (rawUrls == null || rawUrls.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawUrls.split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String extractTitle(String html, String fallback) {
        String title = Jsoup.parse(html).title();
        return title == null || title.isBlank() ? fallback : title.trim();
    }
}
