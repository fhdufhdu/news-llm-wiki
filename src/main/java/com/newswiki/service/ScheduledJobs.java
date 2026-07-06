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
    private final CodexWikiService wikiService;
    private final ArticleRepository articleRepository;
    private final int dailyRebuildMaxBatches;
    private final int wikiMaxRetries;

    public ScheduledJobs(
            JobLockService locks,
            JobRunRepository runs,
            CodexWikiService wikiService,
            ArticleRepository articleRepository,
            AppProperties properties
    ) {
        this.locks = locks;
        this.runs = runs;
        this.wikiService = wikiService;
        this.articleRepository = articleRepository;
        this.dailyRebuildMaxBatches = properties.dailyRebuildMaxBatches();
        this.wikiMaxRetries = properties.aiMaxRetries();
    }

    @Scheduled(cron = "${newswiki.daily-rebuild-cron}")
    public void dailyRebuild() {
        runDailyRebuildNow();
    }

    public void runDailyRebuildNow() {
        runLocked("daily-rebuild", "DAILY_REBUILD", this::dailyRebuild);
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
