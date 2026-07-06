package com.newswiki.service;

import com.newswiki.repository.ArticleRepository;
import com.newswiki.repository.JobRunRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StartupRecoveryService {
    private final ArticleRepository articleRepository;
    private final JobRunRepository jobRunRepository;
    private final JobLockService jobLockService;
    private final ScheduledJobs scheduledJobs;
    private final TaskExecutor jobTaskExecutor;

    public StartupRecoveryService(
            ArticleRepository articleRepository,
            JobRunRepository jobRunRepository,
            JobLockService jobLockService,
            ScheduledJobs scheduledJobs,
            @Qualifier("jobTaskExecutor") TaskExecutor jobTaskExecutor
    ) {
        this.articleRepository = articleRepository;
        this.jobRunRepository = jobRunRepository;
        this.jobLockService = jobLockService;
        this.scheduledJobs = scheduledJobs;
        this.jobTaskExecutor = jobTaskExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedWork() {
        List<String> interruptedTypes = jobRunRepository.findRunningJobTypes();
        int interruptedJobs = jobRunRepository.interruptRunningJobs("Server restarted before job finished");
        int recoveredWikiArticles = articleRepository.recoverInterruptedWikiRunning();
        int expiredLocks = jobLockService.expireAllActiveLocks();

        if (interruptedJobs > 0 || recoveredWikiArticles > 0 || expiredLocks > 0) {
            jobRunRepository.appendLog(null, "WARN", "서버 재시작 복구: 미완료 job "
                    + interruptedJobs + "개 중단 처리, WIKI_RUNNING 기사 "
                    + recoveredWikiArticles + "개 재대기, active lock "
                    + expiredLocks + "개 만료");
        } else {
            jobRunRepository.appendLog(null, "INFO", "서버 재시작 복구: 복구할 미완료 작업 없음");
        }
        resumeRecoveredWork(interruptedTypes, recoveredWikiArticles);
    }

    private void resumeRecoveredWork(List<String> interruptedTypes, int recoveredWikiArticles) {
        boolean resumeRebuild = recoveredWikiArticles > 0 || interruptedTypes.contains("DAILY_REBUILD");
        if (!resumeRebuild) {
            return;
        }
        jobTaskExecutor.execute(() -> {
            if (resumeRebuild) {
                jobRunRepository.appendLog(null, "INFO", "서버 재시작 복구: 미완료 wiki backlog 재빌드 실행");
                scheduledJobs.runDailyRebuildNow();
            }
        });
    }
}
