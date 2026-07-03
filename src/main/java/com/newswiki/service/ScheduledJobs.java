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
    private final AiOrchestrationService aiService;
    private final ArticleRepository articleRepository;
    private final int dailyRebuildMaxBatches;
    private final int aiMaxRetries;

    public ScheduledJobs(
            JobLockService locks,
            JobRunRepository runs,
            RssIngestService ingestService,
            AiOrchestrationService aiService,
            ArticleRepository articleRepository,
            AppProperties properties
    ) {
        this.locks = locks;
        this.runs = runs;
        this.ingestService = ingestService;
        this.aiService = aiService;
        this.articleRepository = articleRepository;
        this.dailyRebuildMaxBatches = properties.dailyRebuildMaxBatches();
        this.aiMaxRetries = properties.aiMaxRetries();
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
        if (result.articlesSaved() > 0) {
            runs.appendLog(runId, "INFO", "AI 작업 시작: 신규 기사 " + result.articlesSaved() + "개");
            var aiResult = aiService.runPendingArticleBatch(runId, (level, message) -> runs.appendLog(runId, level, message));
            runs.appendLog(runId, "INFO", "AI 작업 완료: 입력 " + aiResult.inputCount()
                    + "개, 성공 " + aiResult.succeeded()
                    + "개, 실패 " + aiResult.failed()
                    + "개, detail=" + aiResult.detail());
        } else {
            runs.appendLog(runId, "INFO", "신규 기사가 없어 AI 작업을 건너뜀");
        }
        return result.articlesSaved();
    }

    private int dailyRebuild(long runId) {
        int resetFailed = articleRepository.resetRetryableAiFailures(aiMaxRetries);
        runs.appendLog(runId, "INFO", "DAILY_REBUILD 재시도 대상 AI_FAILED 복구: " + resetFailed + "개");

        int totalSucceeded = 0;
        int totalInput = 0;
        int totalFailed = 0;
        for (int batch = 1; batch <= dailyRebuildMaxBatches; batch++) {
            int batchNo = batch;
            runs.appendLog(runId, "INFO", "DAILY_REBUILD AI batch " + batchNo + " 시작");
            var aiResult = aiService.runPendingArticleBatch(runId, (level, message) -> runs.appendLog(runId, level, "DAILY_REBUILD AI batch " + batchNo + " - " + message));
            if (aiResult.inputCount() == 0) {
                runs.appendLog(runId, "INFO", "DAILY_REBUILD 처리할 AI backlog가 없음");
                break;
            }
            totalInput += aiResult.inputCount();
            totalSucceeded += aiResult.succeeded();
            totalFailed += aiResult.failed();
            runs.appendLog(runId, "INFO", "DAILY_REBUILD AI batch " + batch
                    + ": 입력 " + aiResult.inputCount()
                    + "개, 성공 " + aiResult.succeeded()
                    + "개, 실패 " + aiResult.failed()
                    + "개, detail=" + aiResult.detail());
        }
        runs.appendLog(runId, "INFO", "DAILY_REBUILD AI backlog 요약: 입력 " + totalInput
                + "개, 성공 " + totalSucceeded
                + "개, 실패 " + totalFailed
                + "개, maxBatches=" + dailyRebuildMaxBatches);
        return totalSucceeded;
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
