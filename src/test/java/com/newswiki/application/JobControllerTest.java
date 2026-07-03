package com.newswiki.application;

import com.newswiki.config.AppProperties;
import com.newswiki.dto.SectionNavItem;
import com.newswiki.repository.JobRunRepository;
import com.newswiki.service.JobService;
import com.newswiki.service.NewsViewService;
import com.newswiki.service.ScheduledJobs;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobControllerTest {
    @Test
    void forceRunIngestTriggersSchedulerAndRedirectsToJobs() {
        var scheduler = new RecordingScheduledJobs();
        var controller = new JobController(newsViewService(), jobService(scheduler));

        String view = controller.runIngestNow();

        assertThat(view).isEqualTo("redirect:/jobs");
        assertThat(scheduler.ingestRuns).isEqualTo(1);
    }

    @Test
    void logsEndpointReturnsRecentLogsAsPlainText() {
        var controller = new JobController(newsViewService(), jobService(new RecordingScheduledJobs()));

        String logs = controller.logs();

        assertThat(logs).contains("[INFO] 수집 시작");
    }

    @Test
    void jobsPageAddsLogsToModel() {
        var controller = new JobController(newsViewService(), jobService(new RecordingScheduledJobs()));
        var model = new ExtendedModelMap();

        String view = controller.jobs(model);

        assertThat(view).isEqualTo("pages/jobs");
        assertThat(model.get("jobLogs").toString()).contains("수집 시작");
    }

    private NewsViewService newsViewService() {
        return new NewsViewService(null, null) {
            @Override
            public List<SectionNavItem> sectionNav(String activeSlug) {
                return List.of(new SectionNavItem("industry-ai", "산업·AI", false));
            }
        };
    }

    private JobService jobService(RecordingScheduledJobs scheduler) {
        return new JobService(jobRunRepository(), scheduler, Runnable::run);
    }

    private JobRunRepository jobRunRepository() {
        return new JobRunRepository(null) {
            @Override
            public List<JobRunView> findRecent(int limit) {
                return List.of();
            }

            @Override
            public List<JobLogView> findRecentLogs(int limit) {
                return List.of(new JobLogView(1, 1L, "INFO", "수집 시작", "2026-07-03T00:00:00Z"));
            }
        };
    }

    private static class RecordingScheduledJobs extends ScheduledJobs {
        int ingestRuns;

        RecordingScheduledJobs() {
            super(null, null, null, null, null, new AppProperties(
                    "./rss-sources.yaml",
                    "./data",
                    "/tmp/codex",
                    "gpt-5.5",
                    "workspace-write",
                    "0 0 * * * *",
                    "0 30 3 * * *",
                    1,
                    1,
                    80,
                    1800,
                    15,
                    10,
                    1,
                    1,
                    false,
                    "DB_GZIP"
            ));
        }

        @Override
        public void runIngestNow() {
            ingestRuns++;
        }
    }
}
