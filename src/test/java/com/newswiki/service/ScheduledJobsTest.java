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
    void dailyRebuildRetriesFailedWikiAndProcessesBacklogBatches() {
        var locks = new RecordingJobLockService();
        var runs = new RecordingJobRunRepository();
        var articles = new RecordingArticleRepository();
        var wiki = new RecordingCodexWikiService(
                new com.newswiki.dto.WikiJobResult(80, 0, 0, "batch-1"),
                new com.newswiki.dto.WikiJobResult(20, 0, 0, "batch-2"),
                new com.newswiki.dto.WikiJobResult(0, 0, 0, "done")
        );
        var jobs = new ScheduledJobs(locks, runs, null, wiki, articles, properties());

        jobs.runDailyRebuildNow();

        assertThat(articles.resetFailedCalls).isEqualTo(1);
        assertThat(wiki.calls).isEqualTo(3);
        assertThat(runs.finishedOutputCount).isEqualTo(100);
        assertThat(runs.logs).anySatisfy(line -> assertThat(line).contains("DAILY_REBUILD wiki batch 1"));
        assertThat(runs.logs).anySatisfy(line -> assertThat(line).contains("test-progress"));
        assertThat(runs.logs).anySatisfy(line -> assertThat(line).contains("DAILY_REBUILD 재시도 대상 WIKI_FAILED 복구: 12개"));
        assertThat(runs.logs).anySatisfy(line -> assertThat(line).contains("DAILY_REBUILD 처리할 wiki backlog가 없음"));
    }

    private AppProperties properties() {
        return new AppProperties(
                "./rss-sources.yaml",
                "./data",
                "/tmp/codex",
                "gpt-5.5",
                "workspace-write",
                "0 */5 * * * *",
                "0 30 3 * * *",
                5,
                3,
                80,
                1800,
                15,
                10,
                2,
                5,
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
        public int resetRetryableWikiFailures(int maxRetries) {
            resetFailedCalls++;
            return 12;
        }
    }

    private static class RecordingCodexWikiService extends CodexWikiService {
        private final List<com.newswiki.dto.WikiJobResult> results;
        int calls;

        RecordingCodexWikiService(com.newswiki.dto.WikiJobResult... results) {
            super(null, null, null, null);
            this.results = List.of(results);
        }

        @Override
        public com.newswiki.dto.WikiJobResult runPendingWikiBatch(long jobRunId, RssIngestService.IngestLogger logger) {
            logger.log("INFO", "test-progress");
            return results.get(calls++);
        }
    }
}
