package com.newswiki.service;

import com.newswiki.repository.JobRunRepository;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class JobService {
    private final JobRunRepository jobRunRepository;
    private final ScheduledJobs scheduledJobs;
    private final TaskExecutor jobTaskExecutor;

    public JobService(JobRunRepository jobRunRepository, ScheduledJobs scheduledJobs, TaskExecutor jobTaskExecutor) {
        this.jobRunRepository = jobRunRepository;
        this.scheduledJobs = scheduledJobs;
        this.jobTaskExecutor = jobTaskExecutor;
    }

    public JobsView jobsView() {
        List<JobRunRepository.JobLogView> logs = jobRunRepository.findRecentLogs(1000);
        return new JobsView(
                jobRunRepository.findRecent(20),
                logs,
                logs.stream().map(JobRunRepository.JobLogView::line).collect(Collectors.joining("\n"))
        );
    }

    public void runRebuildNow() {
        jobTaskExecutor.execute(scheduledJobs::runDailyRebuildNow);
    }

    public String recentLogText() {
        return jobRunRepository.findRecentLogs(1000).stream()
                .map(JobRunRepository.JobLogView::line)
                .collect(Collectors.joining("\n"));
    }

    public record JobsView(
            List<JobRunRepository.JobRunView> runs,
            List<JobRunRepository.JobLogView> logs,
            String logText
    ) {
    }
}
