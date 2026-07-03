package com.newswiki.service;

import com.newswiki.config.AppProperties;
import com.newswiki.repository.ArticleRepository;
import com.newswiki.repository.JobRunRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StartupRecoveryServiceTest {
    @Test
    void recoversInterruptedWorkAndWritesSummaryLog() {
        var articles = new RecordingArticleRepository(12);
        var jobs = new RecordingJobRunRepository(2, List.of("INGEST", "DAILY_REBUILD"));
        var locks = new RecordingJobLockService(3);
        var scheduledJobs = new RecordingScheduledJobs();
        var service = new StartupRecoveryService(articles, jobs, locks, scheduledJobs, Runnable::run);

        service.recoverInterruptedWork();

        assertThat(jobs.interruptCalled).isTrue();
        assertThat(articles.recoverCalled).isTrue();
        assertThat(locks.expireCalled).isTrue();
        assertThat(jobs.logs).anySatisfy(log -> assertThat(log)
                .contains("미완료 job 2개 중단 처리")
                .contains("WIKI_RUNNING 기사 12개 재대기")
                .contains("active lock 3개 만료"));
        assertThat(scheduledJobs.ingestCalls).isEqualTo(1);
        assertThat(scheduledJobs.rebuildCalls).isEqualTo(1);
        assertThat(jobs.logs).anySatisfy(log -> assertThat(log).contains("중단된 INGEST 작업 재실행"));
        assertThat(jobs.logs).anySatisfy(log -> assertThat(log).contains("미완료 wiki backlog 재빌드 실행"));
    }

    private static class RecordingArticleRepository extends ArticleRepository {
        private final int recovered;
        boolean recoverCalled;

        RecordingArticleRepository(int recovered) {
            super(null);
            this.recovered = recovered;
        }

        @Override
        public int recoverInterruptedWikiRunning() {
            recoverCalled = true;
            return recovered;
        }
    }

    private static class RecordingJobRunRepository extends JobRunRepository {
        private final int interrupted;
        private final List<String> runningJobTypes;
        final List<String> logs = new ArrayList<>();
        boolean interruptCalled;

        RecordingJobRunRepository(int interrupted, List<String> runningJobTypes) {
            super(null);
            this.interrupted = interrupted;
            this.runningJobTypes = runningJobTypes;
        }

        @Override
        public List<String> findRunningJobTypes() {
            return runningJobTypes;
        }

        @Override
        public int interruptRunningJobs(String errorMessage) {
            interruptCalled = true;
            return interrupted;
        }

        @Override
        public void appendLog(Long jobRunId, String level, String message) {
            logs.add(level + " " + message);
        }
    }

    private static class RecordingJobLockService extends JobLockService {
        private final int expired;
        boolean expireCalled;

        RecordingJobLockService(int expired) {
            super(null);
            this.expired = expired;
        }

        @Override
        public int expireAllActiveLocks() {
            expireCalled = true;
            return expired;
        }
    }

    private static class RecordingScheduledJobs extends ScheduledJobs {
        int ingestCalls;
        int rebuildCalls;

        RecordingScheduledJobs() {
            super(null, null, null, null, null, properties());
        }

        @Override
        public void runIngestNow() {
            ingestCalls++;
        }

        @Override
        public void runDailyRebuildNow() {
            rebuildCalls++;
        }

        private static AppProperties properties() {
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
                    "SQLITE_TEXT"
            );
        }
    }
}
