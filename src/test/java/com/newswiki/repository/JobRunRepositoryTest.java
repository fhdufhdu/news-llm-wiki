package com.newswiki.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:job_run_repository_test?mode=memory&cache=shared",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=none"
})
class JobRunRepositoryTest {
    @Autowired
    JobRunRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from job_logs");
        jdbcTemplate.update("delete from job_runs");
    }

    @Test
    void appendsAndReadsRecentLogs() {
        long runId = repository.start("INGEST", 0);

        repository.appendLog(runId, "INFO", "수집 시작");
        repository.appendLog(runId, "INFO", "AI 작업 대기");

        assertThat(repository.findRecentLogs(10))
                .extracting(JobRunRepository.JobLogView::message)
                .containsExactly("수집 시작", "AI 작업 대기");
    }

    @Test
    void appendsGlobalLogWithoutJobRunId() {
        repository.appendLog(null, "WARN", "이미 실행 중");

        assertThat(repository.findRecentLogs(10))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.jobRunId()).isNull();
                    assertThat(log.message()).isEqualTo("이미 실행 중");
                });
    }

    @Test
    void finishesWithNullExitCodeAndErrorMessage() {
        long runId = repository.start("DAILY_REBUILD", 0);

        repository.finish(runId, "SUCCESS", 0, null, null);

        assertThat(repository.findRecent(10))
                .singleElement()
                .satisfies(run -> {
                    assertThat(run.status()).isEqualTo("SUCCESS");
                    assertThat(run.exitCode()).isNull();
                    assertThat(run.errorMessage()).isNull();
                });
    }

    @Test
    void interruptsRunningJobsAfterRestart() {
        repository.start("INGEST", 0);
        long finishedRunId = repository.start("DAILY_REBUILD", 0);
        repository.finish(finishedRunId, "SUCCESS", 10, 0, null);

        int interrupted = repository.interruptRunningJobs("Server restarted before job finished");

        assertThat(interrupted).isEqualTo(1);
        assertThat(repository.findRecent(10))
                .filteredOn(run -> run.jobType().equals("INGEST"))
                .singleElement()
                .satisfies(run -> {
                    assertThat(run.status()).isEqualTo("INTERRUPTED");
                    assertThat(run.exitCode()).isEqualTo(130);
                    assertThat(run.errorMessage()).isEqualTo("Server restarted before job finished");
                    assertThat(run.finishedAt()).isNotBlank();
                });
        assertThat(repository.findRecent(10))
                .filteredOn(run -> run.jobType().equals("DAILY_REBUILD"))
                .singleElement()
                .satisfies(run -> assertThat(run.status()).isEqualTo("SUCCESS"));
    }
}
