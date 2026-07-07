package com.newswiki.application;

import com.newswiki.service.ArticleImportService;
import com.newswiki.service.NewsViewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ImportController {
    private final ArticleImportService articleImportService;
    private final NewsViewService newsViewService;

    public ImportController(ArticleImportService articleImportService, NewsViewService newsViewService) {
        this.articleImportService = articleImportService;
        this.newsViewService = newsViewService;
    }

    @GetMapping("/imports")
    public String imports(Model model) {
        model.addAttribute("categoryNav", newsViewService.categoryNav(""));
        model.addAttribute("activeCategorySlug", "");
        model.addAttribute("jobs", articleImportService.findRecentJobs());
        return "pages/imports";
    }

    @PostMapping("/imports")
    public String submit(@RequestParam("urls") String urls) {
        long jobId = articleImportService.submitUrls(urls);
        articleImportService.runImportJobAsync(jobId);
        return "redirect:/imports/" + jobId;
    }

    @GetMapping("/imports/{jobId}")
    public String detail(@PathVariable long jobId, Model model) {
        model.addAttribute("categoryNav", newsViewService.categoryNav(""));
        model.addAttribute("activeCategorySlug", "");
        model.addAttribute("progress", articleImportService.findProgress(jobId));
        return "pages/import-detail";
    }

    @GetMapping("/imports/{jobId}/fragment")
    public String detailFragment(@PathVariable long jobId, Model model) {
        model.addAttribute("progress", articleImportService.findProgress(jobId));
        return "pages/import-detail :: progress";
    }
}
