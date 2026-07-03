package com.newswiki.service;

import com.newswiki.repository.JobRunRepository;
import com.newswiki.repository.ArticleRepository;
import com.newswiki.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class ScheduledJobs {
    private final JobLockService locks;
    private final JobRunRepository runs;
    private final RssIngestService ingestService;
    private final CodexWikiService wikiService;
    private final ArticleRepository articleRepository;
    private final int dailyRebuildMaxBatches;
    private final int wikiMaxRetries;

    public ScheduledJobs(
            JobLockService locks,
            JobRunRepository runs,
            RssIngestService ingestService,
            CodexWikiService wikiService,
            ArticleRepository articleRepository,
            AppProperties properties
    ) {
        this.locks = locks;
        this.runs = runs;
        this.ingestService = ingestService;
        this.wikiService = wikiService;
        this.articleRepository = articleRepository;
        this.dailyRebuildMaxBatches = properties.dailyRebuildMaxBatches();
        this.wikiMaxRetries = properties.aiMaxRetries();
    }

    @Scheduled(cron = "${newswiki.ingest-cron}")
    public void hourlyIngest() {
        runIngestNow();
    }

    public void runIngestNow() {
        runLocked("hourly-ingest", "INGEST", this::ingest);
    }

    @Scheduled(cron = "${newswiki.daily-rebuild-cron}")
    public void dailyRebuild() {
        runDailyRebuildNow();
    }

    public void runDailyRebuildNow() {
        runLocked("daily-rebuild", "DAILY_REBUILD", this::dailyRebuild);
    }

    private int ingest(long runId) {
        var result = ingestService.ingest((level, message) -> runs.appendLog(runId, level, message));
        runs.appendLog(runId, "INFO", "RSS source 확인: enabled " + result.feedsSeen()
                + "개, 직접 RSS " + result.directRssFeeds()
                + "개, RSS index " + result.rssIndexes()
                + "개, 신규 기사 " + result.articlesSaved()
                + "개, feed 오류 " + result.feedErrors()
                + "개, article 오류 " + result.articleErrors() + "개");
        int pendingWikiArticles = articleRepository.countPendingWikiArticles();
        if (pendingWikiArticles > 0) {
            runs.appendLog(runId, "INFO", "위키 작업 시작: pending " + pendingWikiArticles + "개");
            var wikiResult = wikiService.runPendingWikiBatch(runId, (level, message) -> runs.appendLog(runId, level, message));
            runs.appendLog(runId, "INFO", "위키 작업 완료: 입력 " + wikiResult.inputCount()
                    + "개, detail=" + wikiResult.detail());
        } else {
            runs.appendLog(runId, "INFO", "위키 처리 대기 기사가 없어 위키 작업을 건너뜀");
        }
        return result.articlesSaved();
    }

    private int dailyRebuild(long runId) {
        int resetFailed = articleRepository.resetRetryableWikiFailures(wikiMaxRetries);
        runs.appendLog(runId, "INFO", "DAILY_REBUILD 재시도 대상 WIKI_FAILED 복구: " + resetFailed + "개");

        int totalInput = 0;
        for (int batch = 1; batch <= dailyRebuildMaxBatches; batch++) {
            int batchNo = batch;
            runs.appendLog(runId, "INFO", "DAILY_REBUILD wiki batch " + batchNo + " 시작");
            var wikiResult = wikiService.runPendingWikiBatch(runId, (level, message) -> runs.appendLog(runId, level, "DAILY_REBUILD wiki batch " + batchNo + " - " + message));
            if (wikiResult.inputCount() == 0) {
                runs.appendLog(runId, "INFO", "DAILY_REBUILD 처리할 wiki backlog가 없음");
                break;
            }
            totalInput += wikiResult.inputCount();
            runs.appendLog(runId, "INFO", "DAILY_REBUILD wiki batch " + batch
                    + ": 입력 " + wikiResult.inputCount()
                    + "개, detail=" + wikiResult.detail());
        }
        runs.appendLog(runId, "INFO", "DAILY_REBUILD wiki backlog 요약: 입력 " + totalInput
                + "개, maxBatches=" + dailyRebuildMaxBatches);
        return totalInput;
    }

    private void runLocked(String lockName, String jobType, JobBody body) {
        String owner = UUID.randomUUID().toString();
        if (!locks.tryAcquire(lockName, owner, Duration.ofHours(2))) {
            runs.appendLog(null, "WARN", jobType + " 작업이 이미 실행 중이라 건너뜀");
            return;
        }
        long runId = runs.start(jobType, 0);
        runs.appendLog(runId, "INFO", jobType + " 작업 시작");
        try {
            int outputCount = body.run(runId);
            runs.finish(runId, "SUCCESS", outputCount, 0, null);
            runs.appendLog(runId, "INFO", jobType + " 작업 성공: " + outputCount + " outputs");
        } catch (Exception e) {
            runs.finish(runId, "FAILED", 0, 1, e.getMessage());
            runs.appendLog(runId, "ERROR", jobType + " 작업 실패: " + e.getMessage());
        } finally {
            locks.release(lockName, owner);
        }
    }

    private interface JobBody {
        int run(long runId);
    }
}
