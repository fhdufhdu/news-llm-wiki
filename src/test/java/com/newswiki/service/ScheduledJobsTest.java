package com.newswiki.service;

import com.newswiki.config.AppProperties;
import com.newswiki.repository.ArticleRepository;
import com.newswiki.repository.JobRunRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledJobsTest {
    @Test
    void dailyRebuildRetriesFailedAiAndProcessesBacklogBatches() {
        var locks = new RecordingJobLockService();
        var runs = new RecordingJobRunRepository();
        var articles = new RecordingArticleRepository();
        var ai = new RecordingAiOrchestrationService(
                new AiOrchestrationService.BatchResult(80, 70, 10, "batch-1"),
                new AiOrchestrationService.BatchResult(20, 20, 0, "batch-2"),
                new AiOrchestrationService.BatchResult(0, 0, 0, "done")
        );
        var jobs = new ScheduledJobs(locks, runs, null, ai, articles, properties());

        jobs.runDailyRebuildNow();

        assertThat(articles.resetFailedCalls).isEqualTo(1);
        assertThat(ai.calls).isEqualTo(3);
        assertThat(runs.finishedOutputCount).isEqualTo(90);
        assertThat(runs.logs).anySatisfy(line -> assertThat(line).contains("DAILY_REBUILD AI batch 1"));
        assertThat(runs.logs).anySatisfy(line -> assertThat(line).contains("test-progress"));
        assertThat(runs.logs).anySatisfy(line -> assertThat(line).contains("DAILY_REBUILD 재시도 대상 AI_FAILED 복구: 12개"));
        assertThat(runs.logs).anySatisfy(line -> assertThat(line).contains("DAILY_REBUILD 처리할 AI backlog가 없음"));
    }

    private AppProperties properties() {
        return new AppProperties(
                "./rss-sources.yaml",
                "./data",
                "/tmp/codex",
                "gpt-5.5",
                "workspace-write",
                "0 0 * * * *",
                "0 30 3 * * *",
                5,
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

    private static class RecordingJobLockService extends JobLockService {
        RecordingJobLockService() {
            super(null);
        }

        @Override
        public boolean tryAcquire(String name, String owner, Duration ttl) {
            return true;
        }

        @Override
        public void release(String name, String owner) {
        }
    }

    private static class RecordingJobRunRepository extends JobRunRepository {
        final List<String> logs = new ArrayList<>();
        int finishedOutputCount;

        RecordingJobRunRepository() {
            super(null);
        }

        @Override
        public long start(String jobType, int inputCount) {
            return 10L;
        }

        @Override
        public void finish(long id, String status, int outputCount, Integer exitCode, String errorMessage) {
            this.finishedOutputCount = outputCount;
        }

        @Override
        public void appendLog(Long jobRunId, String level, String message) {
            logs.add(level + " " + message);
        }
    }

    private static class RecordingArticleRepository extends ArticleRepository {
        int resetFailedCalls;

        RecordingArticleRepository() {
            super(null);
        }

        @Override
        public int resetRetryableAiFailures(int maxRetries) {
            resetFailedCalls++;
            return 12;
        }
    }

    private static class RecordingAiOrchestrationService extends AiOrchestrationService {
        private final List<BatchResult> results;
        int calls;

        RecordingAiOrchestrationService(BatchResult... results) {
            super(null, null, null, null, null, null, null);
            this.results = List.of(results);
        }

        @Override
        public BatchResult runPendingArticleBatch(long jobRunId) {
            return results.get(calls++);
        }

        @Override
        public BatchResult runPendingArticleBatch(long jobRunId, RssIngestService.IngestLogger logger) {
            logger.log("INFO", "test-progress");
            return results.get(calls++);
        }
    }
}
