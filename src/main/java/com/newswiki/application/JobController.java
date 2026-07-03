package com.newswiki.application;

import com.newswiki.service.JobService;
import com.newswiki.service.NewsViewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class JobController {
    private final NewsViewService newsViewService;
    private final JobService jobService;

    public JobController(NewsViewService newsViewService, JobService jobService) {
        this.newsViewService = newsViewService;
        this.jobService = jobService;
    }

    @GetMapping("/jobs")
    public String jobs(Model model) {
        var view = jobService.jobsView();
        model.addAttribute("sectionNav", newsViewService.sectionNav(""));
        model.addAttribute("activeSectionSlug", "");
        model.addAttribute("jobRuns", view.runs());
        model.addAttribute("jobLogs", view.logs());
        model.addAttribute("jobLogText", view.logText());
        return "pages/jobs";
    }

    @PostMapping("/jobs/run/ingest")
    public String runIngestNow() {
        jobService.runIngestNow();
        return "redirect:/jobs";
    }

    @PostMapping("/jobs/run/rebuild")
    public String runRebuildNow() {
        jobService.runRebuildNow();
        return "redirect:/jobs";
    }

    @GetMapping(value = "/jobs/logs", produces = "text/plain; charset=UTF-8")
    @ResponseBody
    public String logs() {
        return jobService.recentLogText();
    }
}
